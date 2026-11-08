package com.simplevideomaker.app;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

/**
 * Draws one composed frame (picture + filter + text + stickers) at 720x1280, and blends
 * transitions between two frames. Used by the preview and by the encoder, so what she sees
 * is exactly what she gets.
 */
public final class FrameRenderer {

    /** Output size of the movie — changed by the project settings. */
    public static int OUT_W = 720;
    public static int OUT_H = 1280;

    public static void setOutputSize(int width, int height) {
        OUT_W = Math.max(2, (Math.max(2, width) / 2) * 2);
        OUT_H = Math.max(2, (Math.max(2, height) / 2) * 2);
    }

    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint TEXT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Typeface BOLD = Typeface.create(Typeface.DEFAULT, Typeface.BOLD);
    private static final Typeface[] FONTS = new Typeface[6];
    private static final String[] FONT_LABELS = {"Bold", "Serif", "Hand", "Mono", "Narrow", "Thin"};

    public static String fontName(int id) {
        return FONT_LABELS[Math.max(0, Math.min(FONT_LABELS.length - 1, id))];
    }

    public static Typeface typefaceFor(int id) {
        int i = Math.max(0, Math.min(FONTS.length - 1, id));
        if (FONTS[i] == null) {
            String family;
            switch (i) {
                case 1:
                    family = "serif";
                    break;
                case 2:
                    family = "casual";
                    break;
                case 3:
                    family = "monospace";
                    break;
                case 4:
                    family = "sans-serif-condensed";
                    break;
                case 5:
                    family = "sans-serif-light";
                    break;
                default:
                    family = "sans-serif-black";
                    break;
            }
            Typeface tf;
            try {
                tf = Typeface.create(family, i == 5 ? Typeface.NORMAL : Typeface.BOLD);
            } catch (Throwable t) {
                tf = Typeface.create(Typeface.DEFAULT, Typeface.BOLD);
            }
            FONTS[i] = tf;
        }
        return FONTS[i];
    }
    private static final Path CLIP_PATH = new Path();
    private static final RectF R = new RectF();

    private static final ColorFilter[] FILTERS = new ColorFilter[6];

    private FrameRenderer() {
    }

    /* ---------------------------------------------------------------- filters */

    public static ColorFilter filterFor(int id) {
        if (id <= 0 || id >= FILTERS.length) {
            return null;
        }
        if (FILTERS[id] == null) {
            FILTERS[id] = buildFilter(id);
        }
        return FILTERS[id];
    }

    public static String filterName(int id) {
        switch (id) {
            case Clip.F_WARM:
                return "Warm";
            case Clip.F_COOL:
                return "Cool";
            case Clip.F_MONO:
                return "B&W";
            case Clip.F_VIVID:
                return "Vivid";
            case Clip.F_SEPIA:
                return "Sepia";
            default:
                return "None";
        }
    }

    private static ColorFilter buildFilter(int id) {
        ColorMatrix m = new ColorMatrix();
        switch (id) {
            case Clip.F_WARM: {
                ColorMatrix w = new ColorMatrix(new float[]{
                        1.15f, 0, 0, 0, 12,
                        0, 1.03f, 0, 0, 6,
                        0, 0, 0.88f, 0, -6,
                        0, 0, 0, 1, 0});
                m.postConcat(w);
                break;
            }
            case Clip.F_COOL: {
                ColorMatrix c = new ColorMatrix(new float[]{
                        0.9f, 0, 0, 0, -6,
                        0, 1.0f, 0, 0, 2,
                        0, 0, 1.15f, 0, 12,
                        0, 0, 0, 1, 0});
                m.postConcat(c);
                break;
            }
            case Clip.F_MONO: {
                m.setSaturation(0f);
                break;
            }
            case Clip.F_VIVID: {
                m.setSaturation(1.45f);
                ColorMatrix boost = new ColorMatrix(new float[]{
                        1.08f, 0, 0, 0, 0,
                        0, 1.08f, 0, 0, 0,
                        0, 0, 1.08f, 0, 0,
                        0, 0, 0, 1, 0});
                m.postConcat(boost);
                break;
            }
            case Clip.F_SEPIA: {
                ColorMatrix s = new ColorMatrix(new float[]{
                        0.393f, 0.769f, 0.189f, 0, 0,
                        0.349f, 0.686f, 0.168f, 0, 0,
                        0.272f, 0.534f, 0.131f, 0, 0,
                        0, 0, 0, 1, 0});
                m.postConcat(s);
                break;
            }
            default:
                return null;
        }
        return new ColorMatrixColorFilter(m);
    }

    /* ------------------------------------------------------------------ frame */

    public static void drawFrame(Canvas c, Clip clip, Bitmap media, float zoom, Overlay selected) {
        c.drawColor(0xFF0D0A09);
        if (clip == null) {
            return;
        }
        Bitmap bmp = media != null && !media.isRecycled() ? media : clip.image;
        if (bmp != null && !bmp.isRecycled()) {
            drawMedia(c, bmp, clip.isVideo() ? clip.videoRotation : 0, zoom,
                    filterFor(clip.filter));
        }
        for (int i = 0; i < clip.overlays.size(); i++) {
            drawOverlay(c, clip.overlays.get(i));
        }
        if (selected != null) {
            drawHandle(c, selected);
        }
    }

    private static void drawMedia(Canvas c, Bitmap bmp, int rotation, float zoom,
                                  ColorFilter filter) {
        boolean rot = rotation == 90 || rotation == 270;
        float srcW = bmp.getWidth();
        float srcH = bmp.getHeight();
        float effW = rot ? srcH : srcW;
        float effH = rot ? srcW : srcH;
        float aspect = effW / effH;
        float frameAspect = (float) OUT_W / (float) OUT_H;

        int save = c.save();
        if (rotation != 0) {
            c.rotate(rotation, OUT_W / 2f, OUT_H / 2f);
        }

        // background: the same picture filling the frame, dimmed
        float bgW;
        float bgH;
        if (aspect > frameAspect) {
            bgH = OUT_H;
            bgW = OUT_H * aspect;
        } else {
            bgW = OUT_W;
            bgH = OUT_W / aspect;
        }
        P.reset();
        P.setAntiAlias(true);
        P.setFilterBitmap(true);
        P.setAlpha(110);
        P.setColorFilter(filter);
        fill(c, bmp, rot, bgW * zoom, bgH * zoom, P);

        // the picture itself, fitted
        float fgW;
        float fgH;
        if (aspect > frameAspect) {
            fgW = OUT_W;
            fgH = OUT_W / aspect;
        } else {
            fgH = OUT_H;
            fgW = OUT_H * aspect;
        }
        fgW *= zoom;
        fgH *= zoom;

        P.reset();
        P.setAntiAlias(true);
        P.setFilterBitmap(true);
        P.setAlpha(255);
        P.setColorFilter(filter);
        float boxW = rot ? fgH : fgW;
        float boxH = rot ? fgW : fgH;
        float left = (OUT_W - boxW) / 2f;
        float top = (OUT_H - boxH) / 2f;
        R.set(left, top, left + boxW, top + boxH);
        CLIP_PATH.reset();
        CLIP_PATH.addRoundRect(R, 18, 18, Path.Direction.CW);
        c.save();
        c.clipPath(CLIP_PATH);
        c.drawBitmap(bmp, null, R, P);
        c.restore();

        c.restoreToCount(save);
    }

    private static void fill(Canvas c, Bitmap bmp, boolean rot, float w, float h, Paint p) {
        float boxW = rot ? h : w;
        float boxH = rot ? w : h;
        float left = (OUT_W - boxW) / 2f;
        float top = (OUT_H - boxH) / 2f;
        R.set(left, top, left + boxW, top + boxH);
        c.drawBitmap(bmp, null, R, p);
    }

    /* ------------------------------------------------------------ transitions */

    /**
     * Blends the outgoing last frame with the incoming first frames.
     * p goes 0 (still the old clip) to 1 (fully the new clip).
     */
    public static void composeTransition(Canvas c, Bitmap outgoing, Bitmap incoming, int type,
                                         float p, Bitmap scratch) {
        if (outgoing == null || p >= 1f) {
            if (incoming != null) {
                c.drawBitmap(incoming, 0, 0, null);
            }
            return;
        }
        float t = Math.max(0f, Math.min(1f, p));
        P.reset();
        P.setAntiAlias(true);
        P.setFilterBitmap(true);

        switch (type) {
            case Clip.T_SLIDE: {
                c.drawBitmap(outgoing, 0, 0, null);
                float dx = OUT_W * (1f - ease(t));
                c.drawBitmap(incoming, dx, 0, null);
                break;
            }
            case Clip.T_ZOOM: {
                c.drawBitmap(outgoing, 0, 0, null);
                float scale = 0.62f + 0.38f * ease(t);
                float w = OUT_W * scale;
                float h = OUT_H * scale;
                R.set((OUT_W - w) / 2f, (OUT_H - h) / 2f, (OUT_W + w) / 2f, (OUT_H + h) / 2f);
                int alpha = (int) (255 * Math.min(1f, t * 1.6f));
                P.setAlpha(alpha);
                c.drawBitmap(incoming, null, R, P);
                break;
            }
            case Clip.T_CIRCLE: {
                c.drawBitmap(outgoing, 0, 0, null);
                float radius = ease(t) * (float) Math.hypot(OUT_W, OUT_H) * 0.55f;
                c.save();
                c.clipPath(circlePath(OUT_W / 2f, OUT_H / 2f, radius));
                c.drawBitmap(incoming, 0, 0, null);
                c.restore();
                break;
            }
            case Clip.T_FADE:
            default: {
                c.drawBitmap(incoming, 0, 0, null);
                P.setAlpha((int) (255 * (1f - t)));
                c.drawBitmap(outgoing, 0, 0, P);
                break;
            }
        }
        P.setAlpha(255);
    }

    private static Path circlePath(float cx, float cy, float radius) {
        Path p = new Path();
        p.addCircle(cx, cy, Math.max(1f, radius), Path.Direction.CW);
        return p;
    }

    private static float ease(float x) {
        return 1f - (1f - x) * (1f - x);
    }

    public static String transitionName(int id) {
        switch (id) {
            case Clip.T_FADE:
                return "Fade";
            case Clip.T_SLIDE:
                return "Slide";
            case Clip.T_ZOOM:
                return "Zoom";
            case Clip.T_CIRCLE:
                return "Circle";
            default:
                return "None";
        }
    }

    /* --------------------------------------------------------------- overlays */

    public static void drawOverlay(Canvas c, Overlay o) {
        float size = o.size * OUT_H;
        float x = o.x * OUT_W;
        float y = o.y * OUT_H;

        if (o.type == Overlay.ART) {
            StickerArt.draw(c, o.text, x, y, size, o.glow);
            return;
        }

        TEXT.reset();
        TEXT.setAntiAlias(true);
        TEXT.setTextAlign(Paint.Align.CENTER);
        TEXT.setTypeface(o.type == Overlay.EMOJI ? Typeface.DEFAULT : typefaceFor(o.font));
        TEXT.setTextSize(size);

        float base = baseline(y);

        if (o.type == Overlay.EMOJI) {
            if (o.glow) {
                glowPass(c, o.text, x, base, size, 0xCCFFFFFF);
            }
            TEXT.setStyle(Paint.Style.FILL);
            TEXT.setColor(Color.WHITE);
            TEXT.setShadowLayer(size * 0.16f, 0, size * 0.05f, 0x66000000);
            c.drawText(o.text, x, base, TEXT);
            TEXT.clearShadowLayer();
            return;
        }

        if (o.type == Overlay.BADGE) {
            float tw = TEXT.measureText(o.text);
            float bw = tw + size * 1.1f;
            float bh = size * 1.55f;
            R.set(x - bw / 2f, y - bh / 2f, x + bw / 2f, y + bh / 2f);
            P.reset();
            P.setAntiAlias(true);
            P.setStyle(Paint.Style.FILL);
            P.setColor(o.color == 0 ? 0xFFFF0000 : o.color);
            P.setShadowLayer(size * (o.glow ? 0.4f : 0.16f), 0, size * 0.05f,
                    o.glow ? 0xAAFFFFFF : 0x59000000);
            c.drawRoundRect(R, bh * 0.32f, bh * 0.32f, P);
            P.clearShadowLayer();
            TEXT.setStyle(Paint.Style.FILL);
            TEXT.setColor(Color.WHITE);
            c.drawText(o.text, x, base, TEXT);
            return;
        }

        // plain text
        if (o.glow) {
            glowPass(c, o.text, x, base, size, o.color);
        }
        TEXT.setStyle(Paint.Style.STROKE);
        TEXT.setStrokeWidth(size * 0.18f);
        TEXT.setStrokeJoin(Paint.Join.ROUND);
        TEXT.setStrokeMiter(2f);
        TEXT.setColor(0xCC000000);
        c.drawText(o.text, x, base, TEXT);

        TEXT.setStyle(Paint.Style.FILL);
        TEXT.setColor(o.color == 0 ? Color.WHITE : o.color);
        TEXT.setShadowLayer(size * 0.12f, 0, size * 0.04f, 0x55000000);
        c.drawText(o.text, x, base, TEXT);
        TEXT.clearShadowLayer();
    }

    private static void glowPass(Canvas c, String text, float x, float base, float size, int color) {
        try {
            Paint glow = new Paint(TEXT);
            glow.setStyle(Paint.Style.FILL);
            glow.setColor(color);
            glow.setMaskFilter(new BlurMaskFilter(size * 0.22f, BlurMaskFilter.Blur.NORMAL));
            c.drawText(text, x, base, glow);
            glow.setMaskFilter(null);
        } catch (Throwable ignored) {
        }
    }

    private static float baseline(float centreY) {
        Paint.FontMetrics fm = TEXT.getFontMetrics();
        return centreY - (fm.ascent + fm.descent) / 2f;
    }

    private static void drawHandle(Canvas c, Overlay o) {
        RectF b = boundsOf(o);
        P.reset();
        P.setAntiAlias(true);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(4f);
        P.setColor(0xFF00DCFF);
        P.setPathEffect(new android.graphics.DashPathEffect(new float[]{14f, 10f}, 0f));
        R.set(b.left - 10f, b.top - 10f, b.right + 10f, b.bottom + 10f);
        c.drawRoundRect(R, 16f, 16f, P);
        P.setPathEffect(null);
    }

    public static RectF boundsOf(Overlay o) {
        float size = o.size * OUT_H;
        float x = o.x * OUT_W;
        float y = o.y * OUT_H;
        if (o.type == Overlay.EMOJI) {
            return new RectF(x - size * 0.58f, y - size * 0.58f, x + size * 0.58f, y + size * 0.58f);
        }
        if (o.type == Overlay.ART) {
            return new RectF(x - size * 0.55f, y - size * 0.55f, x + size * 0.55f, y + size * 0.55f);
        }
        TEXT.reset();
        TEXT.setAntiAlias(true);
        TEXT.setTypeface(typefaceFor(o.font));
        TEXT.setTextSize(size);
        float tw = TEXT.measureText(o.text == null ? "" : o.text);
        if (o.type == Overlay.BADGE) {
            float bw = tw + size * 1.1f;
            float bh = size * 1.55f;
            return new RectF(x - bw / 2f, y - bh / 2f, x + bw / 2f, y + bh / 2f);
        }
        float hw = Math.max(tw, size * 0.6f) / 2f;
        return new RectF(x - hw, y - size * 0.72f, x + hw, y + size * 0.72f);
    }

    /** Topmost overlay under the given point (frame coordinates), or null. */
    public static Overlay hitTest(Clip clip, float fx, float fy) {
        if (clip == null) {
            return null;
        }
        for (int i = clip.overlays.size() - 1; i >= 0; i--) {
            Overlay o = clip.overlays.get(i);
            RectF b = boundsOf(o);
            float pad = 20f;
            if (fx >= b.left - pad && fx <= b.right + pad && fy >= b.top - pad && fy <= b.bottom + pad) {
                return o;
            }
        }
        return null;
    }
}
