package com.simplevideomaker.app;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Builds the movie's soundtrack: the phone's background music (looped) mixed with the original
 * sound of the video clips, re-encoded to AAC stereo 44.1 kHz.
 *
 * Everything is streamed block by block, so a 5 minute movie never loads minutes of audio into RAM.
 */
public final class AudioMixer {

    public interface PacketSink {
        void onAudioPacket(byte[] data, long ptsUs, int flags);
    }

    static final int RATE = 44100;
    static final int CHANNELS = 2;
    private static final int BLOCK = 1024;
    private static final long BLOCK_US = BLOCK * 1000000L / RATE;

    private final Context context;
    private final MediaCodec encoder;
    private final MediaFormat format;
    private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

    private final Uri musicUri;
    private final float musicVolume;
    private final List<Segment> segments = new ArrayList<>();

    private PcmSource music;
    private PcmSource activeSegmentSource;
    private Segment activeSegment;

    private final float[] mix = new float[BLOCK * CHANNELS];
    private final float[] segmentBuffer = new float[BLOCK * CHANNELS];
    private final short[] pcm = new short[BLOCK * CHANNELS];

    private long producedFrames;
    private PacketSink sink;
    private boolean released;

    private static final class Segment {
        final long startUs;
        final long endUs;
        final Uri uri;
        final long sourceStartMs;
        final long sourceEndMs;
        final float volume;

        Segment(long startUs, long endUs, Uri uri, long sourceStartMs, long sourceEndMs,
                float volume) {
            this.startUs = startUs;
            this.endUs = endUs;
            this.uri = uri;
            this.sourceStartMs = sourceStartMs;
            this.sourceEndMs = sourceEndMs;
            this.volume = volume;
        }
    }

    public AudioMixer(Context context, Project project) throws Exception {
        this.context = context;
        this.musicUri = project.musicUri;
        this.musicVolume = Math.max(0f, Math.min(1f, project.musicVolume));

        long offsetUs = 0;
        for (Clip c : project.clips) {
            long clipUs = Math.max(200, c.durationMs) * 1000L;
            if (!project.clipsMuted && c.isVideo() && c.volume > 0.01f
                    && Math.abs(c.speed - 1f) < 0.01f) {
                segments.add(new Segment(offsetUs, offsetUs + clipUs, c.uri, c.startMs,
                        c.sourceEndMs(), c.volume));
            }
            offsetUs += clipUs;
        }

        MediaFormat fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, RATE,
                CHANNELS);
        fmt.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
        fmt.setInteger(MediaFormat.KEY_AAC_PROFILE,
                MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        fmt.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);

        encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        encoder.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoder.start();

        // Feed a little silence so the encoder reveals its real output format (needed to mux).
        MediaFormat out = null;
        Arrays.fill(pcm, (short) 0);
        queueBlock(0);
        for (int attempt = 0; attempt < 60 && out == null; attempt++) {
            int index = encoder.dequeueOutputBuffer(info, 20000);
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                out = encoder.getOutputFormat();
            } else if (index >= 0) {
                encoder.releaseOutputBuffer(index, false);
            }
        }
        if (out == null) {
            try {
                out = encoder.getOutputFormat();
            } catch (Exception e) {
                throw new IllegalStateException("audio encoder did not start");
            }
        }
        format = out;
    }

    public MediaFormat format() {
        return format;
    }

    public void setSink(PacketSink sink) {
        this.sink = sink;
    }

    /** Encodes everything up to the given point of the movie. */
    public void writeUntil(long timeUs) {
        if (released) {
            return;
        }
        int guard = 0;
        while (producedFrames * BLOCK_US < timeUs && guard++ < 100000) {
            long blockStartUs = producedFrames * BLOCK_US;
            renderBlock(blockStartUs);
            queueBlock((producedFrames + 1) * BLOCK_US);
            producedFrames++;
            drain(false);
        }
    }

    public void finish(long totalUs) {
        if (released) {
            return;
        }
        writeUntil(totalUs);
        try {
            int index = encoder.dequeueInputBuffer(100000);
            if (index >= 0) {
                encoder.queueInputBuffer(index, 0, 0, producedFrames * BLOCK_US,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            }
        } catch (Exception ignored) {
        }
        drain(true);
        release();
    }

    private void renderBlock(long blockStartUs) {
        Arrays.fill(mix, 0f);

        // background music (loops until the end of the movie)
        if (musicUri != null && musicVolume > 0.001f) {
            int filled = 0;
            int loops = 0;
            while (filled < BLOCK && loops < 12) {
                if (music == null) {
                    music = open(musicUri, 0, -1);
                    if (music == null) {
                        break;
                    }
                }
                int got = music.read(mix, filled * CHANNELS, BLOCK - filled);
                if (got <= 0) {
                    music.release();
                    music = null;
                    loops++;
                    continue;
                }
                filled += got;
            }
            applyGain(mix, 0, filled, musicVolume);
        }

        // the sound of the video that is playing right now
        Segment seg = segmentAt(blockStartUs);
        if (seg != null) {
            if (seg != activeSegment) {
                if (activeSegmentSource != null) {
                    activeSegmentSource.release();
                }
                activeSegmentSource = open(seg.uri, seg.sourceStartMs, seg.sourceEndMs);
                activeSegment = seg;
            }
            if (activeSegmentSource != null) {
                long into = Math.max(0, blockStartUs - seg.startUs);
                int skipFrames = (int) (into * RATE / 1000000L);
                float[] temp = segmentBuffer;
                Arrays.fill(temp, 0f);
                int got = activeSegmentSource.read(temp, 0, BLOCK);
                // first block of a segment may need skipping (rare, only when clips are long)
                if (skipFrames > 0 && skipFrames < BLOCK) {
                    int keep = BLOCK - skipFrames;
                    if (got > skipFrames) {
                        for (int i = 0; i < Math.min(keep, got - skipFrames); i++) {
                            mix[i * 2] += temp[(i + skipFrames) * 2] * seg.volume;
                            mix[i * 2 + 1] += temp[(i + skipFrames) * 2 + 1] * seg.volume;
                        }
                    }
                } else {
                    for (int i = 0; i < got; i++) {
                        mix[i * 2] += temp[i * 2] * seg.volume;
                        mix[i * 2 + 1] += temp[i * 2 + 1] * seg.volume;
                    }
                }
            }
        } else if (activeSegmentSource != null) {
            activeSegmentSource.release();
            activeSegmentSource = null;
            activeSegment = null;
        }

        // soft clip and convert
        for (int i = 0; i < mix.length; i++) {
            float v = mix[i];
            if (v > 1f) {
                v = 1f;
            } else if (v < -1f) {
                v = -1f;
            }
            pcm[i] = (short) (v * 32000f);
        }
    }

    private Segment segmentAt(long timeUs) {
        for (Segment s : segments) {
            if (timeUs >= s.startUs - BLOCK_US && timeUs < s.endUs) {
                return s;
            }
        }
        return null;
    }

    private static void applyGain(float[] data, int frameOffset, int frames, float gain) {
        for (int i = 0; i < frames * CHANNELS; i++) {
            data[frameOffset * CHANNELS + i] *= gain;
        }
    }

    private PcmSource open(Uri uri, long startMs, long endMs) {
        try {
            return new PcmSource(context, uri, startMs, endMs);
        } catch (Throwable t) {
            return null;
        }
    }

    private void queueBlock(long ptsUs) {
        int guard = 0;
        while (guard++ < 200) {
            int index;
            try {
                index = encoder.dequeueInputBuffer(10000);
            } catch (Exception e) {
                return;
            }
            if (index >= 0) {
                ByteBuffer buffer = encoder.getInputBuffer(index);
                if (buffer == null) {
                    return;
                }
                buffer.clear();
                buffer.order(ByteOrder.nativeOrder());
                buffer.asShortBuffer().put(pcm);
                try {
                    encoder.queueInputBuffer(index, 0, pcm.length * 2, ptsUs, 0);
                } catch (Exception ignored) {
                }
                return;
            }
        }
    }

    private void drain(boolean endOfStream) {
        int guard = 0;
        while (guard++ < 400) {
            int index;
            try {
                index = encoder.dequeueOutputBuffer(info, endOfStream ? 20000 : 0);
            } catch (Exception e) {
                return;
            }
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) {
                    return;
                }
            } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // format already captured while priming
            } else if (index >= 0) {
                boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                if (info.size > 0 && sink != null) {
                    ByteBuffer buffer = encoder.getOutputBuffer(index);
                    if (buffer != null) {
                        byte[] data = new byte[info.size];
                        buffer.position(info.offset);
                        buffer.limit(info.offset + info.size);
                        buffer.get(data);
                        long pts = Math.max(0, info.presentationTimeUs - BLOCK_US);
                        sink.onAudioPacket(data, pts, info.flags);
                    }
                }
                encoder.releaseOutputBuffer(index, false);
                if (eos) {
                    return;
                }
            } else if (!endOfStream) {
                return;
            }
        }
    }

    private void release() {
        released = true;
        if (music != null) {
            music.release();
            music = null;
        }
        if (activeSegmentSource != null) {
            activeSegmentSource.release();
            activeSegmentSource = null;
        }
        try {
            encoder.stop();
        } catch (Exception ignored) {
        }
        try {
            encoder.release();
        } catch (Exception ignored) {
        }
    }

    /* ------------------------------------------------------------------ source */

    /** Streams one audio file (or a range of it) as 44.1 kHz stereo floats. */
    static final class PcmSource {
        private final MediaExtractor extractor;
        private final MediaCodec decoder;
        private final long endMs;

        private boolean inputDone;
        private boolean eos;

        private int sampleRate = RATE;
        private int channels = 2;
        private boolean floatOutput;

        private float[] buffer = new float[32768];
        private int length;
        private int position;
        private double cursor;

        private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

        PcmSource(Context context, Uri uri, long startMs, long endMs) throws Exception {
            this.endMs = endMs;
            extractor = new MediaExtractor();
            extractor.setDataSource(context, uri, null);

            int track = -1;
            MediaFormat fmt = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    track = i;
                    fmt = f;
                    break;
                }
            }
            if (track < 0 || fmt == null) {
                extractor.release();
                throw new IllegalStateException("no audio track");
            }
            extractor.selectTrack(track);
            sampleRate = fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                    ? fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE) : RATE;
            channels = fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                    ? fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 2;
            if (sampleRate <= 0) {
                sampleRate = RATE;
            }
            if (channels <= 0) {
                channels = 2;
            }

            String mime = fmt.getString(MediaFormat.KEY_MIME);
            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(fmt, null, null, 0);
            decoder.start();

            if (startMs > 50) {
                extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            }
        }

        /** Adds up to {@code frames} stereo frames of audio into {@code out}. */
        int read(float[] out, int outOffsetFrames, int frames) {
            if (eos && position >= length) {
                return 0;
            }
            float ratio = (float) sampleRate / RATE;
            int produced = 0;

            while (produced < frames) {
                int index = (int) cursor;
                if (length - index < 2) {
                    if (!fill()) {
                        break;
                    }
                    continue;
                }
                float frac = (float) (cursor - index);
                float l = buffer[index * 2] * (1 - frac) + buffer[(index + 1) * 2] * frac;
                float r = buffer[index * 2 + 1] * (1 - frac) + buffer[(index + 1) * 2 + 1] * frac;
                int target = (outOffsetFrames + produced) * 2;
                if (target + 1 < out.length) {
                    out[target] += l;
                    out[target + 1] += r;
                }
                produced++;
                cursor += ratio;
                position = (int) cursor;
            }
            if (position > 8192) {
                compact();
            }
            return produced;
        }

        private void compact() {
            int drop = position - 1;
            if (drop <= 0) {
                return;
            }
            int remaining = length - drop;
            if (remaining > 0) {
                System.arraycopy(buffer, drop * 2, buffer, 0, remaining * 2);
            }
            length = Math.max(0, remaining);
            cursor -= drop;
            position = 0;
        }

        private boolean fill() {
            int guard = 0;
            while (!eos && guard++ < 200) {
                if (!inputDone) {
                    int inIndex = decoder.dequeueInputBuffer(10000);
                    if (inIndex >= 0) {
                        ByteBuffer input = decoder.getInputBuffer(inIndex);
                        int size = input == null ? -1 : extractor.readSampleData(input, 0);
                        if (size < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            long pts = extractor.getSampleTime();
                            if (endMs > 0 && pts > endMs * 1000L) {
                                decoder.queueInputBuffer(inIndex, 0, 0, 0,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                                inputDone = true;
                            } else {
                                decoder.queueInputBuffer(inIndex, 0, size, pts, 0);
                                extractor.advance();
                            }
                        }
                    }
                }

                int outIndex = decoder.dequeueOutputBuffer(info, 10000);
                if (outIndex >= 0) {
                    boolean end = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    ByteBuffer output = decoder.getOutputBuffer(outIndex);
                    if (output != null && info.size > 0) {
                        append(output, info.offset, info.size);
                    }
                    decoder.releaseOutputBuffer(outIndex, false);
                    if (end) {
                        eos = true;
                    }
                    if (length > position + 2) {
                        return true;
                    }
                    if (eos) {
                        return false;
                    }
                } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    readOutputFormat(decoder.getOutputFormat());
                } else if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER && inputDone) {
                    if (eos) {
                        return false;
                    }
                }
            }
            return length > position + 2;
        }

        private void readOutputFormat(MediaFormat fmt) {
            try {
                if (fmt.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                    floatOutput = fmt.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            == android.media.AudioFormat.ENCODING_PCM_FLOAT;
                }
            } catch (Exception ignored) {
            }
        }

        private void append(ByteBuffer output, int offset, int size) {
            output.position(offset);
            output.limit(offset + size);
            int bytesPerSample = floatOutput ? 4 : 2;
            int frameBytes = bytesPerSample * channels;
            int frames = size / frameBytes;
            ensure(length + frames + 4);

            if (floatOutput) {
                java.nio.FloatBuffer fb = output.asFloatBuffer();
                for (int f = 0; f < frames; f++) {
                    float l = fb.get(f * channels);
                    float r = channels > 1 ? fb.get(f * channels + 1) : l;
                    buffer[(length + f) * 2] = l;
                    buffer[(length + f) * 2 + 1] = r;
                }
            } else {
                java.nio.ShortBuffer sb = output.asShortBuffer();
                for (int f = 0; f < frames; f++) {
                    float l = sb.get(f * channels) / 32768f;
                    float r = channels > 1 ? sb.get(f * channels + 1) / 32768f : l;
                    buffer[(length + f) * 2] = l;
                    buffer[(length + f) * 2 + 1] = r;
                }
            }
            length += frames;
        }

        private void ensure(int capacity) {
            if (buffer.length < capacity * 2) {
                float[] bigger = new float[Math.max(capacity, buffer.length) * 2];
                System.arraycopy(buffer, 0, bigger, 0, length * 2);
                buffer = bigger;
            }
        }

        void release() {
            try {
                decoder.stop();
            } catch (Exception ignored) {
            }
            try {
                decoder.release();
            } catch (Exception ignored) {
            }
            try {
                extractor.release();
            } catch (Exception ignored) {
            }
        }
    }
}
