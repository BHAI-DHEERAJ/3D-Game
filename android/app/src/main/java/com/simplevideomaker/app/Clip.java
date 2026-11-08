package com.simplevideomaker.app;

import android.graphics.Bitmap;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** One item on the timeline: a photo or a video, plus the text/stickers on top of it. */
public class Clip {

    public static final int PHOTO = 0;
    public static final int VIDEO = 1;

    /** Transition used when this clip starts. */
    public static final int T_NONE = 0;
    public static final int T_FADE = 1;
    public static final int T_SLIDE = 2;
    public static final int T_ZOOM = 3;
    public static final int T_CIRCLE = 4;

    /** Colour looks. */
    public static final int F_NONE = 0;
    public static final int F_WARM = 1;
    public static final int F_COOL = 2;
    public static final int F_MONO = 3;
    public static final int F_VIVID = 4;
    public static final int F_SEPIA = 5;

    public int kind;
    public Uri uri;
    public String uriString;

    /** Where this clip starts inside the source video (milliseconds). */
    public long startMs;

    /** How long this clip plays in the finished movie (milliseconds). */
    public long durationMs;

    /** Real length of the source file (milliseconds). */
    public long sourceDurationMs;

    /** 0.5 = slow motion, 2 = fast forward. */
    public float speed = 1f;

    /** 0..1 volume of the clip's own sound. */
    public float volume = 1f;

    public int videoRotation;
    public int transition = T_NONE;
    public int filter = F_NONE;

    /** Picture used for previewing: the photo itself or the first video frame (not rotated). */
    public Bitmap image;

    /** Small square picture for the timeline strip and the project card (rotation applied). */
    public Bitmap thumb;

    public final List<Overlay> overlays = new ArrayList<>();

    public Clip(int kind, Uri uri) {
        this.kind = kind;
        this.uri = uri;
        this.uriString = uri == null ? "" : uri.toString();
    }

    public long sourceEndMs() {
        return startMs + Math.round(durationMs * speed);
    }

    public boolean isVideo() {
        return kind == VIDEO;
    }

    /* ------------------------------------------------------------------- json */

    public JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("kind", kind);
        o.put("uri", uriString);
        o.put("start", startMs);
        o.put("dur", durationMs);
        o.put("srcDur", sourceDurationMs);
        o.put("speed", speed);
        o.put("vol", volume);
        o.put("rot", videoRotation);
        o.put("trans", transition);
        o.put("filter", filter);
        JSONArray arr = new JSONArray();
        for (Overlay ov : overlays) {
            arr.put(ov.toJson());
        }
        o.put("ovs", arr);
        return o;
    }

    public static Clip fromJson(JSONObject o) throws Exception {
        Clip c = new Clip(o.optInt("kind", PHOTO), Uri.parse(o.optString("uri", "")));
        c.startMs = o.optLong("start", 0);
        c.durationMs = o.optLong("dur", 3000);
        c.sourceDurationMs = o.optLong("srcDur", c.durationMs);
        c.speed = (float) o.optDouble("speed", 1);
        if (c.speed <= 0.05f) {
            c.speed = 1f;
        }
        c.volume = (float) o.optDouble("vol", 1);
        c.videoRotation = o.optInt("rot", 0);
        c.transition = o.optInt("trans", T_NONE);
        c.filter = o.optInt("filter", F_NONE);
        JSONArray arr = o.optJSONArray("ovs");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                c.overlays.add(Overlay.fromJson(arr.getJSONObject(i)));
            }
        }
        return c;
    }
}
