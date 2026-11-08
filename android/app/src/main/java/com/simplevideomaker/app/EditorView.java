package com.simplevideomaker.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** The preview: shows the selected clip exactly as it will appear in the finished video. */
public class EditorView extends View {

    public interface Listener {
        void onOverlayTouched(Overlay o);
    }

    private Clip clip;
    private Overlay selected;
    private Listener listener;

    private float scale = 1f;
    private float dx;
    private float dy;

    private Overlay dragging;
    private float dragDx;
    private float dragDy;

    private final RectF bounds = new RectF();

    public EditorView(Context context) {
        super(context);
        setBackgroundColor(0xFF1B1512);
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    public void setClip(Clip c) {
        this.clip = c;
        invalidate();
    }

    public void setSelected(Overlay o) {
        this.selected = o;
        invalidate();
    }

    public Overlay getSelected() {
        return selected;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        scale = Math.min((float) w / FrameRenderer.OUT_W, (float) h / FrameRenderer.OUT_H);
        dx = (w - FrameRenderer.OUT_W * scale) / 2f;
        dy = (h - FrameRenderer.OUT_H * scale) / 2f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.translate(dx, dy);
        canvas.scale(scale, scale);
        FrameRenderer.drawFrame(canvas, clip, clip == null ? null : clip.image, 1f, selected);
        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (clip == null) {
            return false;
        }
        float fx = (e.getX() - dx) / scale;
        float fy = (e.getY() - dy) / scale;

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                Overlay hit = FrameRenderer.hitTest(clip, fx, fy);
                selected = hit;
                invalidate();
                if (listener != null) {
                    listener.onOverlayTouched(hit);
                }
                if (hit != null) {
                    dragging = hit;
                    dragDx = hit.x * FrameRenderer.OUT_W - fx;
                    dragDy = hit.y * FrameRenderer.OUT_H - fy;
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (dragging != null) {
                    float nx = (fx + dragDx) / FrameRenderer.OUT_W;
                    float ny = (fy + dragDy) / FrameRenderer.OUT_H;
                    dragging.x = Math.max(0.02f, Math.min(0.98f, nx));
                    dragging.y = Math.max(0.03f, Math.min(0.97f, ny));
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                dragging = null;
                return true;
            }
        }
        return true;
    }
}
