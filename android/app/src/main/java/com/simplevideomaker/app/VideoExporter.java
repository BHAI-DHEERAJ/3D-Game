package com.simplevideomaker.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * The video engine.
 *
 *   photos + decoded video frames  ->  Canvas (filters, text, stickers, transitions)  ->  H.264  ->  MP4
 *
 * Soundtrack: background music + clip sound mixed by {@link AudioMixer} (or, if that is not
 * available, the clips' original compressed audio copied straight into the MP4).
 * No internet, no ffmpeg, no server — everything happens on the phone.
 */
public final class VideoExporter {

    public interface Listener {
        void onProgress(int percent);
    }

    /** Called for every finished frame. Return false to stop early (used by the preview). */
    public interface FrameSink {
        boolean onFrame(Bitmap composed, long ptsUs);
    }

    public static final int FPS = 30;
    private static final long FRAME_US = 1000000L / FPS;
    /* output size comes from FrameRenderer (project settings) */
    private static final int MAX_FRAMES = 30 * 60 * 5;
    private static final long TRANSITION_US = 500000L; // half a second

    private VideoExporter() {
    }

    public static int frameCount(List<Clip> clips) {
        int total = 0;
        for (Clip c : clips) {
            total += framesOf(c);
        }
        return total;
    }

    private static int framesOf(Clip c) {
        return Math.max(1, Math.round(Math.max(200, c.durationMs) * FPS / 1000f));
    }

    /* ---------------------------------------------------------------- composing */

    /**
     * Draws every frame of the movie. The sink decides what happens with it: encode it,
     * or show it on screen for the preview.
     */
    public static void renderFrames(Context ctx, List<Clip> clips, FrameSink sink,
                                    Listener listener) throws Exception {
        if (clips == null || clips.isEmpty()) {
            throw new IOException("empty timeline");
        }
        int totalFrames = frameCount(clips);
        if (totalFrames > MAX_FRAMES) {
            throw new IOException("video is too long for one movie");
        }

        int W = FrameRenderer.OUT_W;
        int H = FrameRenderer.OUT_H;
        Bitmap frame = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Bitmap scratch = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Bitmap snapshot = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas out = new Canvas(frame);
        Canvas incoming = new Canvas(scratch);
        Canvas snap = new Canvas(snapshot);
        boolean haveSnapshot = false;

        long ptsUs = 0;
        int done = 0;
        VideoReader reader = null;

        try {
            for (int ci = 0; ci < clips.size(); ci++) {
                Clip clip = clips.get(ci);
                int frames = framesOf(clip);
                long clipUs = Math.max(200, clip.durationMs) * 1000L;
                boolean video = clip.isVideo();

                if (video && (reader == null || reader.clip != clip)) {
                    if (reader != null) {
                        reader.release();
                    }
                    reader = new VideoReader(ctx, clip);
                }

                int transitionFrames = (clip.transition != Clip.T_NONE && ci > 0 && haveSnapshot)
                        ? (int) Math.max(1, TRANSITION_US * FPS / 1000000L) : 0;

                for (int f = 0; f < frames; f++) {
                    float localSec = (float) f / FPS;
                    long srcUs = (long) ((clip.startMs + localSec * 1000f * clip.speed) * 1000f);

                    Bitmap media = clip.image;
                    if (video && reader != null) {
                        Bitmap decoded = reader.frameAt(srcUs);
                        if (decoded != null) {
                            media = decoded;
                        }
                    }
                    float zoom = 1f + 0.07f * easeOut(Math.min(1f, localSec * 1000f
                            / Math.max(200, clip.durationMs)));

                    boolean inTransition = f < transitionFrames;
                    Canvas target = inTransition ? incoming : out;
                    FrameRenderer.drawFrame(target, clip, media, zoom, null);

                    if (inTransition) {
                        float p = (float) f / transitionFrames;
                        FrameRenderer.composeTransition(out, snapshot, scratch,
                                clip.transition, p, scratch);
                    } else if (f == 0 && clip.transition != Clip.T_NONE && ci > 0 && haveSnapshot) {
                        // transition already applied on the first frame
                    }

                    if (!sink.onFrame(frame, ptsUs)) {
                        return;
                    }
                    ptsUs += FRAME_US;
                    done++;
                    if (listener != null && (f % 5 == 0)) {
                        listener.onProgress((int) (done * 100L / totalFrames));
                    }
                }

                // remember this clip's last frame, it becomes the "outgoing" side of the transition
                snap.drawBitmap(frame, 0, 0, null);
                haveSnapshot = true;
                if (listener != null) {
                    listener.onProgress((int) (done * 100L / totalFrames));
                }
            }
        } finally {
            if (reader != null) {
                reader.release();
            }
            frame.recycle();
            scratch.recycle();
            snapshot.recycle();
        }
    }

    private static float easeOut(float x) {
        return 1f - (1f - x) * (1f - x);
    }

    public static long totalUs(List<Clip> clips) {
        long total = 0;
        for (Clip c : clips) {
            total += Math.max(200, c.durationMs) * 1000L;
        }
        return Math.max(FRAME_US, total);
    }

    /* ------------------------------------------------------------------ export */

    public static void export(Context ctx, Project project, File outFile, Listener listener)
            throws Exception {
        if (project.clips.isEmpty()) {
            throw new IOException("empty timeline");
        }
        if (frameCount(project.clips) > MAX_FRAMES) {
            throw new IOException("video is too long for one movie");
        }
        FrameRenderer.setOutputSize(project.outWidth, project.outHeight);
        long total = totalUs(project.clips);

        AudioMixer mixer = null;
        try {
            mixer = new AudioMixer(ctx, project);
        } catch (Throwable t) {
            mixer = null;
        }

        PassthroughAudio passthrough = null;
        if (mixer == null) {
            passthrough = PassthroughAudio.collect(ctx, project.clips);
        }

        Encoder encoder = new Encoder(outFile, mixer, passthrough, total,
                FrameRenderer.OUT_W, FrameRenderer.OUT_H,
                Math.max(1000000, project.bitrate));
        encoder.start();
        try {
            renderFrames(ctx, project.clips, new FrameSink() {
                @Override
                public boolean onFrame(Bitmap composed, long ptsUs) {
                    return encoder.feed(composed, ptsUs);
                }
            }, listener);
        } finally {
            encoder.finish();
        }

        if (!outFile.exists() || outFile.length() < 1024) {
            throw new IOException("could not write the video file");
        }
        if (listener != null) {
            listener.onProgress(100);
        }
    }

    /** First decoded frame of a video, used for the preview picture and the thumbnails. */
    public static Bitmap firstFrame(Context ctx, Clip clip) {
        VideoReader reader = null;
        try {
            reader = new VideoReader(ctx, clip);
            Bitmap b = reader.frameAt(clip.startMs * 1000L + 10000L);
            return b == null ? null : b.copy(Bitmap.Config.ARGB_8888, false);
        } catch (Throwable t) {
            return null;
        } finally {
            if (reader != null) {
                reader.release();
            }
        }
    }

    /* ----------------------------------------------------------------- encoder */

    private static final class Encoder {
        private final File outFile;
        private final AudioMixer mixer;
        private final PassthroughAudio passthrough;
        private final long totalUs;
        private final int width;
        private final int height;
        private final int bitrate;

        private MediaCodec codec;
        private MediaMuxer muxer;
        private int videoTrack = -1;
        private int audioTrack = -1;
        private boolean started;
        private long lastPtsUs;
        private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        Encoder(File outFile, AudioMixer mixer, PassthroughAudio passthrough, long totalUs,
                int width, int height, int bitrate) {
            this.outFile = outFile;
            this.mixer = mixer;
            this.passthrough = passthrough;
            this.totalUs = totalUs;
            this.width = width;
            this.height = height;
            this.bitrate = bitrate;
        }

        void start() throws IOException {
            int[] formats = {
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
            };
            Throwable last = null;
            for (int colorFormat : formats) {
                try {
                    MediaFormat fmt = MediaFormat.createVideoFormat(
                            MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
                    fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
                    fmt.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
                    fmt.setInteger(MediaFormat.KEY_FRAME_RATE, FPS);
                    fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
                    codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
                    codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                    codec.start();
                    last = null;
                    break;
                } catch (Throwable t) {
                    last = t;
                    try {
                        if (codec != null) {
                            codec.release();
                        }
                    } catch (Throwable ignored) {
                    }
                    codec = null;
                }
            }
            if (codec == null) {
                throw new IOException("this phone cannot make videos"
                        + (last == null ? "" : ": " + last.getMessage()));
            }
            muxer = new MediaMuxer(outFile.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        }

        boolean feed(Bitmap bitmap, long ptsUs) {
            int guard = 0;
            while (guard++ < 400) {
                int inIndex = codec.dequeueInputBuffer(10000);
                if (inIndex >= 0) {
                    try {
                        Image img = codec.getInputImage(inIndex);
                        if (img != null) {
                            fillYuv(img, bitmap);
                        }
                    } catch (Throwable ignored) {
                    }
                    codec.queueInputBuffer(inIndex, 0, width * height * 3 / 2, ptsUs, 0);
                    lastPtsUs = ptsUs;
                    drain(false);
                    return true;
                }
                drain(false);
            }
            return true;
        }

        void drain(boolean endOfStream) {
            int guard = 0;
            while (guard++ < 2000) {
                int outIndex = codec.dequeueOutputBuffer(info, endOfStream ? 20000 : 0);
                if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    if (!endOfStream) {
                        return;
                    }
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    onFormat(codec.getOutputFormat());
                } else if (outIndex >= 0) {
                    boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    long pts = info.presentationTimeUs;
                    int size = info.size;
                    int offset = info.offset;
                    if (size > 0 && started) {
                        ByteBuffer bb = codec.getOutputBuffer(outIndex);
                        if (bb != null) {
                            bb.position(offset);
                            bb.limit(offset + size);
                            try {
                                muxer.writeSampleData(videoTrack, bb, info);
                            } catch (Exception ignored) {
                            }
                        }
                        if (mixer != null) {
                            try {
                                mixer.writeUntil(pts);
                            } catch (Throwable ignored) {
                            }
                        } else if (passthrough != null) {
                            passthrough.writeUntil(this, pts);
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false);
                    if (eos) {
                        return;
                    }
                } else if (!endOfStream) {
                    return;
                }
            }
        }

        private void onFormat(MediaFormat videoFormat) {
            videoTrack = muxer.addTrack(videoFormat);
            if (mixer != null) {
                try {
                    audioTrack = muxer.addTrack(mixer.format());
                } catch (Throwable t) {
                    audioTrack = -1;
                }
            } else if (passthrough != null && passthrough.format != null) {
                try {
                    audioTrack = muxer.addTrack(passthrough.format);
                } catch (Throwable t) {
                    audioTrack = -1;
                }
            }
            muxer.start();
            started = true;
            if (mixer != null) {
                if (audioTrack >= 0) {
                    mixer.setSink(new AudioMixer.PacketSink() {
                        @Override
                        public void onAudioPacket(byte[] data, long ptsUs, int flags) {
                            writeAudio(data, ptsUs, flags);
                        }
                    });
                } else {
                    mixer.setSink(null);
                }
            }
        }

        void writeRaw(ByteBuffer data, MediaCodec.BufferInfo bufferInfo) {
            if (!started || audioTrack < 0) {
                return;
            }
            try {
                muxer.writeSampleData(audioTrack, data, bufferInfo);
            } catch (Exception ignored) {
            }
        }

        private void writeAudio(byte[] data, long ptsUs, int flags) {
            if (!started || audioTrack < 0) {
                return;
            }
            MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
            bi.set(0, data.length, ptsUs, flags & MediaCodec.BUFFER_FLAG_KEY_FRAME);
            try {
                muxer.writeSampleData(audioTrack, ByteBuffer.wrap(data), bi);
            } catch (Exception ignored) {
            }
        }

        void finish() {
            try {
                int inIndex = codec.dequeueInputBuffer(100000);
                if (inIndex >= 0) {
                    codec.queueInputBuffer(inIndex, 0, 0, lastPtsUs + FRAME_US,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                }
                drain(true);
            } catch (Exception ignored) {
            }
            if (mixer != null) {
                try {
                    mixer.finish(totalUs);
                } catch (Throwable ignored) {
                }
            } else if (passthrough != null) {
                passthrough.writeRest(this);
            }
            try {
                codec.stop();
            } catch (Exception ignored) {
            }
            try {
                codec.release();
            } catch (Exception ignored) {
            }
            if (started) {
                try {
                    muxer.stop();
                } catch (Exception ignored) {
                }
            }
            try {
                muxer.release();
            } catch (Exception ignored) {
            }
        }

        /** ARGB bitmap -> the encoder's YUV planes (planar and semi-planar both handled). */
        private void fillYuv(Image image, Bitmap bitmap) {
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer yBuf = planes[0].getBuffer();
            int yRow = planes[0].getRowStride();
            int yPixel = planes[0].getPixelStride();
            ByteBuffer uBuf = planes[1].getBuffer();
            int uRow = planes[1].getRowStride();
            int uPixel = planes[1].getPixelStride();
            ByteBuffer vBuf = planes[2].getBuffer();
            int vRow = planes[2].getRowStride();
            int vPixel = planes[2].getPixelStride();

            int[] row = new int[width];
            for (int y = 0; y < height; y++) {
                bitmap.getPixels(row, 0, width, 0, y, width, 1);
                int yOffset = y * yRow;
                int uvOffset = (y / 2) * uRow;
                int vvOffset = (y / 2) * vRow;
                for (int x = 0; x < width; x++) {
                    int c = row[x];
                    int r = (c >> 16) & 0xFF;
                    int g = (c >> 8) & 0xFF;
                    int b = c & 0xFF;

                    int yv = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
                    int pos = yOffset + x * yPixel;
                    if (pos < yBuf.limit()) {
                        yBuf.put(pos, (byte) clamp(yv));
                    }
                    if ((x & 1) == 0) {
                        int uv = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                        int vv = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
                        int up = uvOffset + (x / 2) * uPixel;
                        int vp = vvOffset + (x / 2) * vPixel;
                        if (up < uBuf.limit()) {
                            uBuf.put(up, (byte) clamp(uv));
                        }
                        if (vp < vBuf.limit()) {
                            vBuf.put(vp, (byte) clamp(vv));
                        }
                    }
                }
            }
        }

        private static int clamp(int v) {
            return v < 0 ? 0 : (v > 255 ? 255 : v);
        }
    }

    /* ------------------------------------------------- audio fallback (passthru) */

    /** Used when the mixer is unavailable: copies the compressed sound of the clips. */
    private static final class PassthroughAudio {
        MediaFormat format;
        final ArrayList<long[]> meta = new ArrayList<>();
        final ArrayList<byte[]> data = new ArrayList<>();
        int index;

        static PassthroughAudio collect(Context ctx, List<Clip> clips) {
            try {
                PassthroughAudio p = new PassthroughAudio();
                long offsetUs = 0;
                for (Clip c : clips) {
                    long clipUs = Math.max(200, c.durationMs) * 1000L;
                    if (c.isVideo()) {
                        p.collectClip(ctx, c, offsetUs);
                    }
                    offsetUs += clipUs;
                }
                return p.data.isEmpty() ? null : p;
            } catch (Throwable t) {
                return null;
            }
        }

        private void collectClip(Context ctx, Clip clip, long offsetUs) {
            MediaExtractor ex = new MediaExtractor();
            try {
                ex.setDataSource(ctx, clip.uri, null);
                int track = -1;
                MediaFormat fmt = null;
                for (int i = 0; i < ex.getTrackCount(); i++) {
                    MediaFormat f = ex.getTrackFormat(i);
                    String mime = f.getString(MediaFormat.KEY_MIME);
                    if (mime != null && mime.startsWith("audio/")) {
                        track = i;
                        fmt = f;
                        break;
                    }
                }
                if (track < 0 || fmt == null) {
                    return;
                }
                if (format == null) {
                    format = fmt;
                } else if (!format.getString(MediaFormat.KEY_MIME)
                        .equals(fmt.getString(MediaFormat.KEY_MIME))) {
                    return;
                }
                ex.selectTrack(track);
                if (clip.startMs > 50) {
                    ex.seekTo(clip.startMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                }
                ByteBuffer buf = ByteBuffer.allocateDirect(512 * 1024);
                long firstPts = -1;
                long endUs = offsetUs + Math.max(200, clip.durationMs) * 1000L;
                while (data.size() < 300000) {
                    int size = ex.readSampleData(buf, 0);
                    if (size < 0) {
                        break;
                    }
                    long pts = ex.getSampleTime();
                    if (firstPts < 0) {
                        firstPts = pts;
                    }
                    long outPts = offsetUs + (pts - firstPts);
                    if (outPts > endUs) {
                        break;
                    }
                    byte[] bytes = new byte[size];
                    buf.position(0);
                    buf.get(bytes, 0, size);
                    data.add(bytes);
                    meta.add(new long[]{outPts, ex.getSampleFlags()});
                    ex.advance();
                }
            } catch (Exception ignored) {
            } finally {
                ex.release();
            }
        }

        void writeUntil(Encoder encoder, long ptsUs) {
            while (index < data.size() && meta.get(index)[0] <= ptsUs) {
                long[] m = meta.get(index);
                MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
                bi.set(0, data.get(index).length, m[0],
                        (int) m[1] & MediaCodec.BUFFER_FLAG_KEY_FRAME);
                encoder.writeRaw(ByteBuffer.wrap(data.get(index)), bi);
                index++;
            }
        }

        void writeRest(Encoder encoder) {
            while (index < data.size()) {
                long[] m = meta.get(index);
                MediaCodec.BufferInfo bi = new MediaCodec.BufferInfo();
                bi.set(0, data.get(index).length, m[0],
                        (int) m[1] & MediaCodec.BUFFER_FLAG_KEY_FRAME);
                encoder.writeRaw(ByteBuffer.wrap(data.get(index)), bi);
                index++;
            }
        }
    }

    /* ----------------------------------------------------------------- decoder */

    /** Reads a video file frame by frame, in order, as ARGB bitmaps. */
    public static final class VideoReader {
        final Clip clip;

        private final MediaExtractor extractor;
        private final MediaCodec decoder;
        private boolean eos;
        private boolean inputDone;

        private Bitmap current;
        private Bitmap next;
        private boolean haveCurrent;
        private boolean haveNext;
        private long nextPts;
        private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        private int[] pixels = new int[0];
        private static int maxSide() {
            return Math.max(960, FrameRenderer.OUT_H);
        }

        public VideoReader(Context ctx, Clip clip) throws IOException {
            this.clip = clip;
            extractor = new MediaExtractor();
            extractor.setDataSource(ctx, clip.uri, null);

            int track = -1;
            MediaFormat fmt = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("video/")) {
                    track = i;
                    fmt = f;
                    break;
                }
            }
            if (track < 0 || fmt == null) {
                extractor.release();
                throw new IOException("no video track");
            }
            extractor.selectTrack(track);
            String mime = fmt.getString(MediaFormat.KEY_MIME);
            decoder = MediaCodec.createDecoderByType(mime);
            try {
                fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                        MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            } catch (Exception ignored) {
            }
            decoder.configure(fmt, null, null, 0);
            decoder.start();
            if (clip.startMs > 50) {
                extractor.seekTo(clip.startMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            } else {
                extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            }
        }

        /** Frame that should be visible at the given absolute source timestamp (µs). */
        public Bitmap frameAt(long sourceUs) {
            int guard = 0;
            while (guard++ < 8000) {
                if (!haveNext) {
                    if (!produceNext()) {
                        break;
                    }
                }
                if (!haveNext) {
                    break;
                }
                if (nextPts > sourceUs) {
                    break;
                }
                Bitmap swap = current;
                current = next;
                next = swap;
                haveCurrent = true;
                haveNext = false;
            }
            return haveCurrent ? current : null;
        }

        private boolean produceNext() {
            int guard = 0;
            while (!eos && guard++ < 300) {
                if (!inputDone) {
                    int inIndex = decoder.dequeueInputBuffer(10000);
                    if (inIndex >= 0) {
                        ByteBuffer buffer = decoder.getInputBuffer(inIndex);
                        int size = buffer == null ? -1 : extractor.readSampleData(buffer, 0);
                        if (size < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }

                int outIndex = decoder.dequeueOutputBuffer(info, 10000);
                if (outIndex >= 0) {
                    boolean end = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    Image image = null;
                    try {
                        image = decoder.getOutputImage(outIndex);
                    } catch (Throwable ignored) {
                    }
                    if (image != null && info.size > 0) {
                        Bitmap target = nextBitmap(image.getWidth(), image.getHeight());
                        yuvToBitmap(image, target);
                        next = target;
                        nextPts = info.presentationTimeUs;
                        haveNext = true;
                    }
                    decoder.releaseOutputBuffer(outIndex, false);
                    if (end) {
                        eos = true;
                        return haveNext;
                    }
                    if (haveNext) {
                        return true;
                    }
                }
            }
            return haveNext;
        }

        private Bitmap nextBitmap(int srcW, int srcH) {
            float scale = 1f;
            int longest = Math.max(srcW, srcH);
            int limit = maxSide();
            if (longest > limit) {
                scale = (float) limit / longest;
            }
            int w = Math.max(2, Math.round(srcW * scale));
            int h = Math.max(2, Math.round(srcH * scale));
            if (next == null || next.getWidth() != w || next.getHeight() != h) {
                if (next != null) {
                    next.recycle();
                }
                next = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            }
            return next;
        }

        /** YUV_420_888 -> ARGB, scaled down to keep memory low on cheap phones. */
        private void yuvToBitmap(Image image, Bitmap out) {
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer yBuf = planes[0].getBuffer();
            int yRow = planes[0].getRowStride();
            ByteBuffer uBuf = planes[1].getBuffer();
            int uRow = planes[1].getRowStride();
            int uPixel = planes[1].getPixelStride();
            ByteBuffer vBuf = planes[2].getBuffer();
            int vRow = planes[2].getRowStride();
            int vPixel = planes[2].getPixelStride();

            int srcW = image.getWidth();
            int srcH = image.getHeight();
            int dstW = out.getWidth();
            int dstH = out.getHeight();
            if (pixels.length < dstW * dstH) {
                pixels = new int[dstW * dstH];
            }
            float stepX = (float) srcW / dstW;
            float stepY = (float) srcH / dstH;

            for (int y = 0; y < dstH; y++) {
                int sy = Math.min(srcH - 1, (int) (y * stepY));
                int yOffset = sy * yRow;
                int uvOffset = (sy / 2) * uRow;
                int vvOffset = (sy / 2) * vRow;
                int dstOffset = y * dstW;
                for (int x = 0; x < dstW; x++) {
                    int sx = Math.min(srcW - 1, (int) (x * stepX));
                    int yv = yBuf.get(yOffset + sx) & 0xFF;
                    int u = (uBuf.get(uvOffset + (sx / 2) * uPixel) & 0xFF) - 128;
                    int v = (vBuf.get(vvOffset + (sx / 2) * vPixel) & 0xFF) - 128;

                    int r = yv + (int) (1.402f * v);
                    int g = yv - (int) (0.344136f * u) - (int) (0.714136f * v);
                    int b = yv + (int) (1.772f * u);
                    pixels[dstOffset + x] = 0xFF000000
                            | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
                }
            }
            out.setPixels(pixels, 0, dstW, 0, 0, dstW, dstH);
        }

        private static int clamp(int v) {
            return v < 0 ? 0 : (v > 255 ? 255 : v);
        }

        public void release() {
            try {
                decoder.stop();
            } catch (Throwable ignored) {
            }
            try {
                decoder.release();
            } catch (Throwable ignored) {
            }
            try {
                extractor.release();
            } catch (Throwable ignored) {
            }
            if (current != null && !current.isRecycled()) {
                current.recycle();
            }
            if (next != null && !next.isRecycled()) {
                next.recycle();
            }
            current = null;
            next = null;
        }
    }
}
