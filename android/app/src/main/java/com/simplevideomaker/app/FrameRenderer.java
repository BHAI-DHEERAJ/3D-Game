package com.simplevideomaker.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

/**
 * Draws one composed frame (picture + text + stickers) at 720x1280.
 * Used by both the on-screen preview and the video encoder, so what you see is what you get.
 */
public final class FrameRenderer {

    public static final int OUT_W = 720;
    public static final int OUT_H = 1280;

    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint TEXT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Typeface BOLD = Typeface.create(Typeface.DEFAULT, Typeface.BOLD);
    private static final Path CLIP_PATH = new Path();
    private static final RectF R = new RectF();

    private FrameRenderer() {
    }

    public static void drawFrame(Canvas c, Clip clip, Bitmap media, float zoom, Overlay selected) {
        c.drawColor(0xFF0D0A09);
        if (clip == null) {
            return;
        }
        Bitmap bmp = media != null && !media.isRecycled() ? media : clip.image;
        if (bmp != null && !bmp.isRecycled()) {
            drawMedia(c, bmp, clip.kind == Clip.VIDEO ? clip.videoRotation : 0, zoom);
        }
        for (int i = 0; i < clip.overlays.size(); i++) {
            drawOverlay(c, clip.overlays.get(i));
        }
        if (selected != null) {
            drawHandle(c, selected);
        }
    }

    /* ------------------------------------------------------------------ media */

    private static void drawMedia(Canvas c, Bitmap bmp, int rotation, float zoom) {
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

        // 1) background: same picture, zoomed to fill, dimmed — keeps tall/wide photos full-frame
        float bgW;
        float bgH;
        if (aspect > frameAspect) {
            bgH = OUT_H;
            bgW = OUT_H * aspect;
        } else {
            bgW = OUT_W;
            bgH = OUT_W / aspect;
        }
        bgW *= zoom;
        bgH *= zoom;
        P.reset();
        P.setAntiAlias(true);
        P.setFilterBitmap(true);
        P.setAlpha(110);
        drawFit(c, bmp, rot, bgW, bgH, P);

        // 2) the real picture, fitted inside the frame
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
        CLIP_PATH.reset();
        float boxW = rot ? fgH : fgW;
        float boxH = rot ? fgW : fgH;
        float left = (OUT_W - boxW) / 2f;
        float top = (OUT_H - boxH) / 2f;
        R.set(left, top, left + boxW, top + boxH);
        CLIP_PATH.addRoundRect(R, 18, 18, Path.Direction.CW);
        c.save();
        c.clipPath(CLIP_PATH);
        c.drawBitmap(bmp, null, R, P);
        c.restore();

        c.restoreToCount(save);
    }

    private static void drawFit(Canvas c, Bitmap bmp, boolean rot, float w, float h, Paint p) {
        // w/h are the size the picture should have on screen; canvas may be rotated 90 degrees
        float boxW = rot ? h : w;
        float boxH = rot ? w : h;
        float left = (OUT_W - boxW) / 2f;
        float top = (OUT_H - boxH) / 2f;
        R.set(left, top, left + boxW, top + boxH);
        c.drawBitmap(bmp, null, R, p);
    }

    /* --------------------------------------------------------------- overlays */

    public static void drawOverlay(Canvas c, Overlay o) {
        float size = o.size * OUT_H;
        float x = o.x * OUT_W;
        float y = o.y * OUT_H;

        TEXT.reset();
        TEXT.setAntiAlias(true);
        TEXT.setTextAlign(Paint.Align.CENTER);
        TEXT.setTypeface(BOLD);
        TEXT.setTextSize(size);

        if (o.type == Overlay.EMOJI) {
            TEXT.setStyle(Paint.Style.FILL);
            TEXT.setColor(Color.WHITE);
            TEXT.setShadowLayer(size * 0.18f, 0, size * 0.05f, 0x66000000);
            c.drawText(o.text, x, baseline(y), TEXT);
            TEXT.clearShadowLayer();
            return;
        }

        if (o.type == Overlay.BADGE) {
            float tw = TEXT.measureText(o.text);
            float bw = tw + size * 1.1f;
            float bh = size * 1.55f;
            R.set(x - bw / 2f, y - bh / 2f, x + bw / 2f, y + bh / 2f);
            Paint box = P;
            box.reset();
            box.setAntiAlias(true);
            box.setStyle(Paint.Style.FILL);
            box.setColor(o.color);
            box.setShadowLayer(size * 0.16f, 0, size * 0.05f, 0x59000000);
            c.drawRoundRect(R, bh * 0.32f, bh * 0.32f, box);
            box.clearShadowLayer();
            TEXT.setStyle(Paint.Style.FILL);
            TEXT.setColor(Color.WHITE);
            c.drawText(o.text, x, baseline(y), TEXT);
            return;
        }

        // plain text: black outline + white fill, readable on any photo
        float bl = baseline(y);
        TEXT.setStyle(Paint.Style.STROKE);
        TEXT.setStrokeWidth(size * 0.18f);
        TEXT.setStrokeJoin(Paint.Join.ROUND);
        TEXT.setStrokeMiter(2f);
        TEXT.setColor(0xCC000000);
        c.drawText(o.text, x, bl, TEXT);

        TEXT.setStyle(Paint.Style.FILL);
        TEXT.setColor(o.color == 0 ? Color.WHITE : o.color);
        TEXT.setShadowLayer(size * 0.12f, 0, size * 0.04f, 0x55000000);
        c.drawText(o.text, x, bl, TEXT);
        TEXT.clearShadowLayer();
    }

    private static float baseline(float centreY) {
        Paint.FontMetrics fm = TEXT.getFontMetrics();
        return centreY - (fm.ascent + fm.descent) / 2f;
    }

    private static void drawHandle(Canvas c, Overlay o) {
        RectF b = boundsOf(o);
        Paint p = P;
        p.reset();
        p.setAntiAlias(true);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4f);
        p.setColor(0xFF00DCFF);
        p.setPathEffect(new android.graphics.DashPathEffect(new float[]{14f, 10f}, 0f));
        R.set(b.left - 10f, b.top - 10f, b.right + 10f, b.bottom + 10f);
        c.drawRoundRect(R, 16f, 16f, p);
        p.setPathEffect(null);
    }

    public static RectF boundsOf(Overlay o) {
        float size = o.size * OUT_H;
        float x = o.x * OUT_W;
        float y = o.y * OUT_H;
        if (o.type == Overlay.EMOJI) {
            return new RectF(x - size * 0.58f, y - size * 0.58f, x + size * 0.58f, y + size * 0.58f);
        }
        TEXT.reset();
        TEXT.setAntiAlias(true);
        TEXT.setTypeface(BOLD);
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
