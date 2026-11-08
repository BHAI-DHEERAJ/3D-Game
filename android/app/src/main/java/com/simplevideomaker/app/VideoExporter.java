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
 * Real on-device video making:
 *
 *   photos / video frames  ->  drawn with Canvas (text + stickers)  ->  MediaCodec H.264  ->  MP4
 *
 * Video clips are decoded with MediaCodec, their sound is copied (passed through) into the MP4,
 * and everything is muxed with MediaMuxer. No internet, no ffmpeg, no server.
 */
public final class VideoExporter {

    public interface Listener {
        void onProgress(int percent);
    }

    private static final int FPS = 30;
    private static final long FRAME_US = 1000000L / FPS;
    private static final int W = FrameRenderer.OUT_W;
    private static final int H = FrameRenderer.OUT_H;
    private static final int MAX_FRAMES = 30 * 60 * 5; // 5 minutes is plenty

    private VideoExporter() {
    }

    /* ------------------------------------------------------------------ export */

    public static void export(Context ctx, List<Clip> clips, File outFile, Listener listener)
            throws Exception {
        if (clips == null || clips.isEmpty()) {
            throw new IOException("empty timeline");
        }
        long totalUs = 0;
        for (Clip c : clips) {
            totalUs += Math.max(200, c.durationMs) * 1000L;
        }
        int frames = (int) Math.max(1, totalUs / FRAME_US);
        if (frames > MAX_FRAMES) {
            throw new IOException("video is too long for one movie");
        }

        ArrayList<Sample> audio = new ArrayList<>();
        MediaFormat audioFormat = collectAudio(ctx, clips, audio);

        Bitmap frame = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(frame);
        Encoder encoder = new Encoder(outFile, audioFormat, audio);
        encoder.start();

        int clipIndex = 0;
        long clipStartUs = 0;
        VideoReader reader = null;
        try {
            for (int f = 0; f < frames; f++) {
                long tUs = f * FRAME_US;
                while (clipIndex < clips.size() - 1
                        && tUs >= clipStartUs + Math.max(200, clips.get(clipIndex).durationMs) * 1000L) {
                    clipStartUs += Math.max(200, clips.get(clipIndex).durationMs) * 1000L;
                    clipIndex++;
                }
                Clip clip = clips.get(clipIndex);
                long localUs = tUs - clipStartUs;
                Bitmap media = clip.image;

                if (clip.kind == Clip.VIDEO) {
                    if (reader == null || reader.clip != clip) {
                        if (reader != null) {
                            reader.release();
                        }
                        reader = new VideoReader(ctx, clip);
                    }
                    Bitmap decoded = reader.frameAt(localUs);
                    if (decoded != null) {
                        media = decoded;
                    }
                }

                float zoom = 1f + 0.07f * easeOut(Math.min(1f,
                        localUs / (Math.max(200, clip.durationMs) * 1000f)));
                FrameRenderer.drawFrame(canvas, clip, media, zoom, null);
                encoder.feed(frame, tUs);

                if (listener != null && f % 5 == 0) {
                    listener.onProgress((int) ((f + 1) * 100L / frames));
                }
            }
        } finally {
            if (reader != null) {
                reader.release();
            }
        }

        encoder.finish();
        if (listener != null) {
            listener.onProgress(100);
        }
        if (!outFile.exists() || outFile.length() < 1024) {
            throw new IOException("could not write the video file");
        }
    }

    private static float easeOut(float x) {
        return 1f - (1f - x) * (1f - x);
    }

    /** First decoded frame of a video, used for the preview and the thumbnails. */
    public static Bitmap firstFrame(Context ctx, Clip clip) {
        VideoReader reader = null;
        try {
            reader = new VideoReader(ctx, clip);
            Bitmap b = reader.frameAt(0);
            return b == null ? null : b.copy(Bitmap.Config.ARGB_8888, false);
        } catch (Exception e) {
            return null;
        } finally {
            if (reader != null) {
                reader.release();
            }
        }
    }

    /* -------------------------------------------------------------- audio pass */

    private static final class Sample {
        final long ptsUs;
        final byte[] data;
        final int flags;

        Sample(long ptsUs, byte[] data, int flags) {
            this.ptsUs = ptsUs;
            this.data = data;
            this.flags = flags;
        }
    }

    /**
     * Copies the already-compressed sound of every video clip into the output timeline.
     * Only clips matching the first audio format are used (an MP4 has one audio track).
     */
    private static MediaFormat collectAudio(Context ctx, List<Clip> clips, List<Sample> out) {
        MediaFormat chosen = null;
        long offsetUs = 0;
        for (Clip c : clips) {
            long clipUs = Math.max(200, c.durationMs) * 1000L;
            if (c.kind == Clip.VIDEO && out.size() < 300000) {
                MediaExtractor ex = new MediaExtractor();
                try {
                    ex.setDataSource(ctx, c.uri, null);
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
                    if (track >= 0 && fmt != null && (chosen == null || sameAudio(chosen, fmt))) {
                        if (chosen == null) {
                            chosen = fmt;
                        }
                        ex.selectTrack(track);
                        ByteBuffer buf = ByteBuffer.allocateDirect(512 * 1024);
                        long firstPts = -1;
                        while (true) {
                            int size = ex.readSampleData(buf, 0);
                            if (size < 0) {
                                break;
                            }
                            long pts = ex.getSampleTime();
                            if (firstPts < 0) {
                                firstPts = pts;
                            }
                            long rel = pts - firstPts;
                            if (rel > clipUs) {
                                break;
                            }
                            byte[] data = new byte[size];
                            buf.position(0);
                            buf.get(data, 0, size);
                            int flags = ex.getSampleFlags() & MediaCodec.BUFFER_FLAG_KEY_FRAME;
                            out.add(new Sample(offsetUs + Math.max(0, rel), data,
                                    flags == 0 ? MediaCodec.BUFFER_FLAG_KEY_FRAME : flags));
                            ex.advance();
                        }
                    }
                } catch (Exception ignored) {
                    // clip has no usable sound — movie just stays silent for this clip
                } finally {
                    ex.release();
                }
            }
            offsetUs += clipUs;
        }
        return out.isEmpty() ? null : chosen;
    }

    private static boolean sameAudio(MediaFormat a, MediaFormat b) {
        try {
            return a.getString(MediaFormat.KEY_MIME).equals(b.getString(MediaFormat.KEY_MIME))
                    && a.getInteger(MediaFormat.KEY_SAMPLE_RATE) == b.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    && a.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == b.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        } catch (Exception e) {
            return false;
        }
    }

    /* ----------------------------------------------------------------- encoder */

    private static final class Encoder {
        private final File outFile;
        private final MediaFormat audioFormat;
        private final ArrayList<Sample> audio;

        private MediaCodec codec;
        private MediaMuxer muxer;
        private int videoTrack = -1;
        private int audioTrack = -1;
        private boolean muxStarted;
        private int audioIndex;
        private long lastPtsUs;
        private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        private final int[] row = new int[W];

        Encoder(File outFile, MediaFormat audioFormat, ArrayList<Sample> audio) {
            this.outFile = outFile;
            this.audioFormat = audioFormat;
            this.audio = audio;
        }

        void start() throws IOException {
            MediaFormat fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, W, H);
            fmt.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            fmt.setInteger(MediaFormat.KEY_BIT_RATE, 6000000);
            fmt.setInteger(MediaFormat.KEY_FRAME_RATE, FPS);
            fmt.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            codec.start();
            muxer = new MediaMuxer(outFile.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        }

        void feed(Bitmap bitmap, long ptsUs) {
            int guard = 0;
            while (guard++ < 400) {
                int inIndex = codec.dequeueInputBuffer(10000);
                if (inIndex >= 0) {
                    try {
                        Image img = codec.getInputImage(inIndex);
                        if (img != null) {
                            fillYuv(img, bitmap);
                        }
                    } catch (Exception ignored) {
                    }
                    codec.queueInputBuffer(inIndex, 0, W * H * 3 / 2, ptsUs, 0);
                    lastPtsUs = ptsUs;
                    drain(false);
                    return;
                }
                drain(false);
            }
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
                    if (info.size > 0 && muxStarted && videoTrack >= 0) {
                        ByteBuffer bb = codec.getOutputBuffer(outIndex);
                        if (bb != null) {
                            bb.position(info.offset);
                            bb.limit(info.offset + info.size);
                            muxer.writeSampleData(videoTrack, bb, info);
                        }
                        writeAudioUpTo(info.presentationTimeUs);
                    }
                    boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
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
            if (audioFormat != null) {
                try {
                    audioTrack = muxer.addTrack(audioFormat);
                } catch (Exception e) {
                    audioTrack = -1;
                }
            }
            muxer.start();
            muxStarted = true;
        }

        private void writeAudioUpTo(long ptsUs) {
            if (!muxStarted || audioTrack < 0) {
                return;
            }
            while (audioIndex < audio.size()) {
                Sample s = audio.get(audioIndex);
                if (s.ptsUs > ptsUs) {
                    return;
                }
                ByteBuffer bb = ByteBuffer.wrap(s.data);
                info.set(0, s.data.length, s.ptsUs, s.flags);
                muxer.writeSampleData(audioTrack, bb, info);
                audioIndex++;
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
                writeAudioUpTo(Long.MAX_VALUE);
            } catch (Exception ignored) {
            }
            try {
                codec.stop();
            } catch (Exception ignored) {
            }
            try {
                codec.release();
            } catch (Exception ignored) {
            }
            if (muxStarted) {
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

        /** ARGB bitmap -> the encoder's YUV planes (handles both planar and semi-planar layouts). */
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

            for (int y = 0; y < H; y++) {
                bitmap.getPixels(row, 0, W, 0, y, W, 1);
                int yOffset = y * yRow;
                int uvOffset = (y / 2) * uRow;
                int vvOffset = (y / 2) * vRow;
                for (int x = 0; x < W; x++) {
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

    /* ----------------------------------------------------------------- decoder */

    /** Reads a video file frame by frame, in order, as ARGB bitmaps. */
    public static final class VideoReader {
        final Clip clip;

        private MediaExtractor extractor;
        private MediaCodec decoder;
        private boolean eos;
        private boolean inputDone;

        private Bitmap current;
        private Bitmap next;
        private boolean haveCurrent;
        private boolean haveNext;
        private long nextPts;
        private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        private int[] pixels = new int[0];
        private final int maxSide;

        public VideoReader(Context ctx, Clip clip) throws IOException {
            this.clip = clip;
            this.maxSide = 960;
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
            extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        }

        /** Frame that should be visible {@code localUs} microseconds into the clip. */
        public Bitmap frameAt(long localUs) {
            int guard = 0;
            while (guard++ < 4000) {
                if (!haveNext) {
                    if (!produceNext()) {
                        break;
                    }
                }
                if (!haveNext) {
                    break;
                }
                if (nextPts > localUs) {
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
            while (!eos && guard++ < 200) {
                if (!inputDone) {
                    int inIndex = decoder.dequeueInputBuffer(10000);
                    if (inIndex >= 0) {
                        ByteBuffer buffer = decoder.getInputBuffer(inIndex);
                        int size = extractor.readSampleData(buffer, 0);
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
                    } catch (Exception ignored) {
                    }
                    if (image != null && info.size > 0) {
                        Bitmap target = nextFrameBitmap(image.getWidth(), image.getHeight());
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
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // output size may be known only now; nothing to do, images carry it
                }
            }
            return haveNext;
        }

        private Bitmap nextFrameBitmap(int srcW, int srcH) {
            float scale = 1f;
            int longest = Math.max(srcW, srcH);
            if (longest > maxSide) {
                scale = (float) maxSide / longest;
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

        /** YUV_420_888 -> ARGB, with nearest-neighbour scaling down to save memory. */
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
                if (decoder != null) {
                    decoder.stop();
                }
            } catch (Exception ignored) {
            }
            try {
                if (decoder != null) {
                    decoder.release();
                }
            } catch (Exception ignored) {
            }
            try {
                if (extractor != null) {
                    extractor.release();
                }
            } catch (Exception ignored) {
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
