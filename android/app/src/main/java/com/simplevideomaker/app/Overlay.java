package com.simplevideomaker.app;

/** A text line, a coloured badge ("SUBSCRIBE") or an emoji sticker on top of a clip. */
public class Overlay {

    public static final int TEXT = 0;
    public static final int BADGE = 1;
    public static final int EMOJI = 2;

    public int type;
    public String text;

    /** ARGB colour used by badges and text. */
    public int color;

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
}
