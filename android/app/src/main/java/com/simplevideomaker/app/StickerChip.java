package com.simplevideomaker.app;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

/** Little square that shows a real preview of a vector sticker in the picker. */
public class StickerChip extends View {

    private final String key;

    public StickerChip(Context context, String key) {
        super(context);
        this.key = key;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight()) * 0.82f;
        StickerArt.draw(canvas, key, getWidth() / 2f, getHeight() / 2f, size, false);
    }
}
