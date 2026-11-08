package com.simplevideomaker.app;

import android.content.Context;
import android.graphics.Bitmap;

/** Grabs a single frame of a video for the preview while you drag the playhead. */
public final class Scrubber {

    private final Context context;
    private Clip clip;
    private VideoExporter.VideoReader reader;
    private long lastUs = -1;

    public Scrubber(Context context) {
        this.context = context;
    }

    public Bitmap frameFor(Clip target, long sourceUs) {
        if (target == null) {
            return null;
        }
        if (!target.isVideo()) {
            return target.image;
        }
        try {
            if (reader == null || clip != target || sourceUs < lastUs - 1000L) {
                release();
                reader = new VideoExporter.VideoReader(context, target);
                clip = target;
                lastUs = -1;
            }
            Bitmap b = reader.frameAt(sourceUs);
            lastUs = sourceUs;
            if (b == null) {
                return target.image;
            }
            // the reader reuses its bitmap, the UI needs its own copy
            return b.copy(Bitmap.Config.ARGB_8888, false);
        } catch (Throwable t) {
            release();
            return target.image;
        }
    }

    public void release() {
        if (reader != null) {
            try {
                reader.release();
            } catch (Throwable ignored) {
            }
            reader = null;
        }
        clip = null;
        lastUs = -1;
    }
}
