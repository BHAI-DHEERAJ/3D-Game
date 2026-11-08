package com.simplevideomaker.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * A real editing timeline: a time ruler, clip blocks sized by their duration, an audio lane,
 * and a playhead you can drag to scrub. Pinch to zoom.
 */
public class TimelineView extends View {

    public interface Listener {
        /** Playhead moved (scrubbing). */
        void onSeek(long timeMs);

        /** A clip was tapped. */
        void onSelectClip(int index);

        /** Finger lifted after scrubbing — good moment to render a frame. */
        void onScrubEnd(long timeMs);
    }

    private final Paint background = new Paint();
    private final Paint blockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rulerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint playPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final RectF block = new RectF();
    private final Rect src = new Rect();
    private final Rect dst = new Rect();
    private final Path path = new Path();
    private final ScaleGestureDetector scaleDetector;

    private Project project;
    private int selected = -1;
    private long playhead;
    private float zoom = 1f;
    private float scrollMs;
    private Listener listener;

    private float downX;
    private float downY;
    private boolean dragging;
    private int tapCandidate = -1;
    private final int touchSlop;
    private final float density;

    public TimelineView(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setBackgroundColor(0xFF120E0C);

        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(dp(10));

        rulerPaint.setColor(0xFF9E8F87);
        rulerPaint.setTextSize(dp(8.5f));

        playPaint.setColor(0xFFFF6B35);
        playPaint.setStyle(Paint.Style.FILL);

        thumbPaint.setFilterBitmap(true);

        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(2f));

        scaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        if (project == null) {
                            return false;
                        }
                        float focusMs = xToTime(detector.getFocusX());
                        float next = Math.max(1f, Math.min(10f, zoom * detector.getScaleFactor()));
                        if (Math.abs(next - zoom) < 0.001f) {
                            return true;
                        }
                        zoom = next;
                        float usable = Math.max(1f, getWidth() - dp(2) * 2);
                        float pxPerMs = usable / Math.max(1f, totalMs()) * zoom;
                        // keep the pinch centre in place
                        scrollMs = focusMs - (detector.getFocusX() - dp(2)) / pxPerMs;
                        clampScroll();
                        invalidate();
                        return true;
                    }
                });
    }

    /* ------------------------------------------------------------------ setup */

    public void setListener(Listener l) {
        listener = l;
    }

    public void setProject(Project p) {
        project = p;
        clampScroll();
        invalidate();
    }

    public void setSelected(int index) {
        selected = index;
        invalidate();
    }

    public void setPlayhead(long ms) {
        playhead = Math.max(0, ms);
        invalidate();
    }

    public long getPlayhead() {
        return playhead;
    }

    private long totalMs() {
        return project == null ? 1 : Math.max(1, project.totalMs());
    }

    /* -------------------------------------------------------------- geometry */

    private float dp(float value) {
        return value * density;
    }

    private float usableWidth() {
        return Math.max(1f, getWidth() - dp(2) * 2);
    }

    private float pxPerMs() {
        return usableWidth() / totalMs() * zoom;
    }

    private float timeToX(float ms) {
        return dp(2) + (ms - scrollMs) * pxPerMs();
    }

    private float xToTime(float x) {
        return scrollMs + (x - dp(2)) / pxPerMs();
    }

    private void clampScroll() {
        float visible = usableWidth() / pxPerMs();
        float max = Math.max(0, totalMs() - visible);
        if (scrollMs > max) {
            scrollMs = max;
        }
        if (scrollMs < 0) {
            scrollMs = 0;
        }
    }

    /* ------------------------------------------------------------------- draw */

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        background.setColor(0xFF120E0C);
        c.drawRect(0, 0, getWidth(), getHeight(), background);
        if (project == null) {
            return;
        }

        float rulerH = dp(16);
        float clipTop = rulerH + dp(4);
        float clipH = Math.max(dp(30), getHeight() - clipTop - dp(22));
        float audioTop = clipTop + clipH + dp(3);
        float audioH = dp(12);

        drawRuler(c, rulerH);

        // ---- clips
        long acc = 0;
        for (int i = 0; i < project.clips.size(); i++) {
            Clip clip = project.clips.get(i);
            long dur = Math.max(200, clip.durationMs);
            float x0 = timeToX(acc);
            float x1 = timeToX(acc + dur);
            acc += dur;
            if (x1 < -dp(20) || x0 > getWidth() + dp(20)) {
                continue;
            }
            block.set(x0, clipTop, Math.max(x0 + dp(3), x1) - dp(1), clipTop + clipH);

            blockPaint.setColor(i == selected ? 0xFF3A2B24 : 0xFF261D18);
            c.drawRoundRect(block, dp(5), dp(5), blockPaint);

            // filmstrip of thumbnails
            c.save();
            path.reset();
            path.addRoundRect(block, dp(5), dp(5), Path.Direction.CW);
            c.clipPath(path);
            if (clip.thumb != null && !clip.thumb.isRecycled()) {
                float tile = block.height() * 0.95f;
                if (tile < dp(8)) {
                    tile = dp(8);
                }
                src.set(0, 0, clip.thumb.getWidth(), clip.thumb.getHeight());
                for (float x = block.left; x < block.right; x += tile) {
                    dst.set((int) x, (int) block.top, (int) (x + tile), (int) block.bottom);
                    c.drawBitmap(clip.thumb, src, dst, thumbPaint);
                }
                blockPaint.setColor(0x66000000);
                c.drawRect(block, blockPaint);
            }
            c.restore();

            // type + duration
            textPaint.setColor(0xFFFFFFFF);
            String label = (clip.isVideo() ? "🎬 " : "🖼 ") + Project.seconds(dur);
            if (block.width() > dp(46)) {
                c.drawText(label, block.left + dp(4), block.bottom - dp(4), textPaint);
            }
            if (clip.transition != Clip.T_NONE) {
                textPaint.setColor(0xFFFFC107);
                c.drawText("✨", block.left + dp(3), block.top + dp(11), textPaint);
            }
            if (clip.speed != 1f) {
                textPaint.setColor(0xFF80DEEA);
                c.drawText(clip.speed + "×", block.right - dp(22), block.bottom - dp(4), textPaint);
            }

            borderPaint.setColor(i == selected ? 0xFFFF6B35 : 0x55FFFFFF);
            c.drawRoundRect(block, dp(5), dp(5), borderPaint);
        }

        // ---- audio lane
        rect.set(dp(2), audioTop, getWidth() - dp(2), audioTop + audioH);
        blockPaint.setColor(0xFF2B1F18);
        c.drawRoundRect(rect, dp(4), dp(4), blockPaint);
        if (project.musicUri != null) {
            textPaint.setColor(0xFFFFB020);
            textPaint.setTextSize(dp(8));
            c.drawText("♪ " + (project.clipsMuted ? "music only" : "music"), rect.left + dp(4),
                    rect.centerY() + dp(3), textPaint);
            blockPaint.setColor(0x33FFB020);
            c.drawRoundRect(rect, dp(4), dp(4), blockPaint);
            textPaint.setTextSize(dp(10));
        } else {
            textPaint.setColor(0xFF6B5B52);
            textPaint.setTextSize(dp(8));
            c.drawText("♪  no music", rect.left + dp(4), rect.centerY() + dp(3), textPaint);
            textPaint.setTextSize(dp(10));
        }

        // ---- playhead
        float px = timeToX(playhead);
        if (px >= 0 && px <= getWidth()) {
            playPaint.setColor(0xFFFF6B35);
            c.drawRect(px - dp(1), 0, px + dp(1), getHeight(), playPaint);
            rect.set(px - dp(6), 0, px + dp(6), rulerH - dp(1));
            c.drawRoundRect(rect, dp(3), dp(3), playPaint);
            playPaint.setColor(0xFFFFFFFF);
            c.drawCircle(px, rulerH / 2f - dp(0.5f), dp(2), playPaint);
        }
    }

    private void drawRuler(Canvas c, float rulerH) {
        rulerPaint.setColor(0xFF1E1815);
        c.drawRect(0, 0, getWidth(), rulerH, rulerPaint);
        rulerPaint.setColor(0xFF9E8F87);

        long total = totalMs();
        float pxMs = pxPerMs();
        long[] steps = {1000, 2000, 5000, 10000, 15000, 30000, 60000, 120000, 300000};
        long step = steps[steps.length - 1];
        for (long candidate : steps) {
            if (candidate * pxMs >= dp(52)) {
                step = candidate;
                break;
            }
        }
        long first = (long) (Math.floor(scrollMs / step) * step);
        for (long t = first; t <= Math.min(total, (long) xToTime(getWidth())) + step; t += step) {
            if (t < 0) {
                continue;
            }
            float x = timeToX(t);
            if (x < 0 || x > getWidth()) {
                continue;
            }
            rulerPaint.setColor(0xFF4A3B33);
            c.drawRect(x, rulerH - dp(4), x + dp(1), rulerH, rulerPaint);
            rulerPaint.setColor(0xFF9E8F87);
            c.drawText(Project.seconds(t), x + dp(2), rulerH - dp(5), rulerPaint);
        }
    }

    /* ------------------------------------------------------------------ touch */

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (project == null) {
            return false;
        }
        scaleDetector.onTouchEvent(e);

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                dragging = false;
                tapCandidate = clipAt(e.getX(), e.getY());
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return true;

            case MotionEvent.ACTION_MOVE:
                if (e.getPointerCount() > 1) {
                    return true;
                }
                if (!dragging && Math.abs(e.getX() - downX) > touchSlop) {
                    dragging = true;
                }
                if (dragging) {
                    long t = (long) Math.max(0, Math.min(totalMs(), xToTime(e.getX())));
                    playhead = t;
                    if (listener != null) {
                        listener.onSeek(t);
                    }
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (!dragging) {
                    if (tapCandidate >= 0) {
                        selected = tapCandidate;
                        if (listener != null) {
                            listener.onSelectClip(tapCandidate);
                        }
                        // also move the playhead to where the finger is
                        long t = (long) Math.max(0, Math.min(totalMs(), xToTime(e.getX())));
                        playhead = t;
                        if (listener != null) {
                            listener.onSeek(t);
                        }
                    } else {
                        long t = (long) Math.max(0, Math.min(totalMs(), xToTime(e.getX())));
                        playhead = t;
                        if (listener != null) {
                            listener.onSeek(t);
                        }
                    }
                    invalidate();
                }
                if (listener != null) {
                    listener.onScrubEnd(playhead);
                }
                dragging = false;
                tapCandidate = -1;
                return true;
        }
        return true;
    }

    private int clipAt(float x, float y) {
        if (project == null) {
            return -1;
        }
        float rulerH = dp(16);
        float clipTop = rulerH + dp(4);
        float clipH = Math.max(dp(30), getHeight() - clipTop - dp(22));
        if (y < clipTop || y > clipTop + clipH) {
            return -1;
        }
        long acc = 0;
        for (int i = 0; i < project.clips.size(); i++) {
            long dur = Math.max(200, project.clips.get(i).durationMs);
            float x0 = timeToX(acc);
            float x1 = timeToX(acc + dur);
            if (x >= x0 && x <= Math.max(x0 + dp(3), x1)) {
                return i;
            }
            acc += dur;
        }
        return -1;
    }
}
