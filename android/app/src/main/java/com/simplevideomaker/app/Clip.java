package com.simplevideomaker.app;

import android.graphics.Bitmap;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

/** One item on the timeline: a photo or a video, plus the text/stickers on top of it. */
public class Clip {

    public static final int PHOTO = 0;
    public static final int VIDEO = 1;

    public int kind;
    public Uri uri;

    /** How long this clip plays in the finished movie (milliseconds). */
    public long durationMs;

    /** The real length of the source video (milliseconds). */
    public long videoDurationMs;

    public int videoRotation;

    /** Picture used for previewing: the photo itself or the first video frame (not rotated). */
    public Bitmap image;

    /** Small square picture for the timeline strip (rotation already applied). */
    public Bitmap thumb;

    public final List<Overlay> overlays = new ArrayList<>();

    public Clip(int kind, Uri uri) {
        this.kind = kind;
        this.uri = uri;
    }
}
