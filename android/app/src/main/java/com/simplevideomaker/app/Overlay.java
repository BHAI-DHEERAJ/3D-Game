package com.simplevideomaker.app;

import org.json.JSONObject;

/** A text line, a coloured badge ("SUBSCRIBE") or an emoji sticker on top of a clip. */
public class Overlay {

    public static final int TEXT = 0;
    public static final int BADGE = 1;
    public static final int EMOJI = 2;
    /** A drawn vector sticker (see StickerArt). */
    public static final int ART = 3;

    /** 0 bold, 1 serif, 2 handwriting, 3 mono, 4 condensed, 5 thin. */
    public int font = 0;

    /** Let the user put the same sticker on every clip at once. */
    public boolean allClips = false;

    public int type;
    public String text;

    /** ARGB colour used by text and badges. */
    public int color;

    /** Soft glow around text/stickers. */
    public boolean glow;

    /** Centre position, 0..1 of the frame width/height. */
    public float x;
    public float y;

    /** Font size as a fraction of the frame height. */
    public float size;

    public Overlay(int type, String text, int color, float x, float y, float size) {
        this.type = type;
        this.text = text;
        this.color = color;
        this.x = x;
        this.y = y;
        this.size = size;
    }

    /** A detached copy, for undo snapshots. */
    public Overlay copy() {
        Overlay ov = new Overlay(type, text, color, x, y, size);
        ov.glow = glow;
        ov.font = font;
        ov.allClips = allClips;
        return ov;
    }

    public JSONObject toJson() throws Exception {
        JSONObject o = new JSONObject();
        o.put("t", type);
        o.put("text", text);
        o.put("color", color);
        o.put("glow", glow);
        o.put("x", x);
        o.put("y", y);
        o.put("size", size);
        o.put("font", font);
        return o;
    }

    public static Overlay fromJson(JSONObject o) {
        Overlay ov = new Overlay(o.optInt("t", TEXT), o.optString("text", ""),
                o.optInt("color", 0xFFFFFFFF),
                (float) o.optDouble("x", 0.5), (float) o.optDouble("y", 0.8),
                (float) o.optDouble("size", 0.06));
        ov.glow = o.optBoolean("glow", false);
        ov.font = o.optInt("font", 0);
        return ov;
    }
}
