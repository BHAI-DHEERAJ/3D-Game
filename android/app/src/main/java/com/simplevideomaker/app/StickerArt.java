package com.simplevideomaker.app;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * Real stickers, drawn with paths and gradients instead of emoji fonts.
 * Everything is drawn inside a 100x100 box, so a sticker stays sharp whether it is
 * 20 pixels or 900 pixels tall.
 */
public final class StickerArt {

    public static final String[] KEYS = {
            "star", "heart", "flower", "butterfly", "sparkle", "glow",
            "sun", "crown", "balloon", "bow", "bell", "rainbow",
            "leaf", "party", "yt", "subscribe"
    };

    public static final String[] LABELS = {
            "Star", "Heart", "Flower", "Butterfly", "Sparkle", "Glow",
            "Sun", "Crown", "Balloon", "Bow", "Bell", "Rainbow",
            "Leaf", "Party", "YouTube", "Subscribe"
    };

    private static final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path PATH = new Path();
    private static final RectF R = new RectF();

    private StickerArt() {
    }

    /** Draws one sticker centred on (cx, cy) with the given height in pixels. */
    public static void draw(Canvas c, String key, float cx, float cy, float size, boolean glow) {
        if (key == null || size <= 1f) {
            return;
        }
        c.save();
        c.translate(cx, cy);
        float s = size / 100f;
        c.scale(s, s);
        c.translate(-50, -50);
        try {
            switch (key) {
                case "star":
                    star(c, glow);
                    break;
                case "heart":
                    heart(c, glow);
                    break;
                case "flower":
                    flower(c, glow);
                    break;
                case "butterfly":
                    butterfly(c, glow);
                    break;
                case "sparkle":
                    sparkle(c, glow);
                    break;
                case "glow":
                    glow(c);
                    break;
                case "sun":
                    sun(c, glow);
                    break;
                case "crown":
                    crown(c, glow);
                    break;
                case "balloon":
                    balloon(c, glow);
                    break;
                case "bow":
                    bow(c, glow);
                    break;
                case "bell":
                    bell(c, glow);
                    break;
                case "rainbow":
                    rainbow(c);
                    break;
                case "leaf":
                    leaf(c, glow);
                    break;
                case "party":
                    party(c);
                    break;
                case "yt":
                    youtube(c);
                    break;
                case "subscribe":
                    subscribe(c);
                    break;
                default:
                    star(c, glow);
                    break;
            }
        } catch (Throwable ignored) {
        }
        P.reset();
        c.restore();
    }

    /* ------------------------------------------------------------------ common */

    private static void shadow(Paint p, boolean glow, float blur, int color) {
        p.setShadowLayer(blur, 0, 2f, color);
    }

    private static void stroke(Paint p, float width, int color) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(width);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(color);
        p.setShader(null);
        p.setShadowLayer(0, 0, 0, 0);
    }

    private static void fill(Paint p, int color) {
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        p.setShader(null);
        p.setShadowLayer(0, 0, 0, 0);
    }

    /* ----------------------------------------------------------------- shapes */

    private static void star(Canvas c, boolean glow) {
        PATH.reset();
        for (int i = 0; i < 10; i++) {
            float r = (i % 2 == 0) ? 48f : 20f;
            double a = -Math.PI / 2 + i * Math.PI / 5;
            float x = (float) (50 + r * Math.cos(a));
            float y = (float) (52 + r * Math.sin(a));
            if (i == 0) {
                PATH.moveTo(x, y);
            } else {
                PATH.lineTo(x, y);
            }
        }
        PATH.close();
        P.reset();
        P.setAntiAlias(true);
        P.setStyle(Paint.Style.FILL);
        P.setShader(new LinearGradient(0, 6, 0, 96, 0xFFFFE873, 0xFFF5A623, Shader.TileMode.CLAMP));
        shadow(P, glow, glow ? 14f : 5f, glow ? 0xAAFFE873 : 0x55000000);
        c.drawPath(PATH, P);
        P.setShader(null);
        stroke(P, 4f, 0xFFFFF7C2);
        c.drawPath(PATH, P);
        P.setShadowLayer(0, 0, 0, 0);
    }

    private static void heart(Canvas c, boolean glow) {
        PATH.reset();
        PATH.moveTo(50, 88);
        PATH.cubicTo(10, 60, 4, 32, 26, 20);
        PATH.cubicTo(39, 13, 48, 21, 50, 32);
        PATH.cubicTo(52, 21, 61, 13, 74, 20);
        PATH.cubicTo(96, 32, 90, 60, 50, 88);
        PATH.close();
        P.reset();
        P.setAntiAlias(true);
        P.setStyle(Paint.Style.FILL);
        P.setShader(new LinearGradient(0, 10, 0, 90, 0xFFFF7A8A, 0xFFE01D3C, Shader.TileMode.CLAMP));
        shadow(P, glow, glow ? 16f : 6f, glow ? 0xAAFF8095 : 0x55000000);
        c.drawPath(PATH, P);
        P.setShader(null);
        P.setShadowLayer(0, 0, 0, 0);
        fill(P, 0x55FFFFFF);
        R.set(24, 22, 44, 34);
        c.drawOval(R, P);
    }

    private static void flower(Canvas c, boolean glow) {
        P.reset();
        P.setAntiAlias(true);
        P.setStyle(Paint.Style.FILL);
        P.setShader(new LinearGradient(0, 0, 100, 100, 0xFFFFB3D9, 0xFFE8419E, Shader.TileMode.CLAMP));
        shadow(P, glow, glow ? 14f : 5f, glow ? 0xAAFFB3D9 : 0x55000000);
        for (int i = 0; i < 6; i++) {
            int save = c.save();
            c.rotate(i * 60f, 50, 50);
            R.set(37, 4, 63, 52);
            c.drawOval(R, P);
            c.restoreToCount(save);
        }
        P.setShader(null);
        P.setShadowLayer(0, 0, 0, 0);
        P.setShader(new RadialGradient(50, 50, 18, 0xFFFFF3A0, 0xFFFFC107, Shader.TileMode.CLAMP));
        c.drawCircle(50, 50, 15, P);
        P.setShader(null);
        fill(P, 0x99C48A00);
        c.drawCircle(45, 47, 2.4f, P);
        c.drawCircle(55, 47, 2.4f, P);
        c.drawCircle(50, 55, 2.4f, P);
    }

    private static void butterfly(Canvas c, boolean glow) {
        P.reset();
        P.setAntiAlias(true);
        P.setStyle(Paint.Style.FILL);
        shadow(P, glow, glow ? 14f : 6f, glow ? 0xAA9C6BFF : 0x55000000);

        // upper wings
        P.setShader(new LinearGradient(10, 10, 90, 60, 0xFF9C6BFF, 0xFFE56BFF, Shader.TileMode.CLAMP));
        PATH.reset();
        PATH.moveTo(50, 54);
        PATH.cubicTo(30, 14, 6, 16, 8, 38);
        PATH.cubicTo(9, 52, 30, 58, 50, 54);
        PATH.close();
        c.drawPath(PATH, P);
        PATH.reset();
        PATH.moveTo(50, 54);
        PATH.cubicTo(70, 14, 94, 16, 92, 38);
        PATH.cubicTo(91, 52, 70, 58, 50, 54);
        PATH.close();
        c.drawPath(PATH, P);

        // lower wings
        P.setShader(new LinearGradient(10, 60, 90, 96, 0xFFFF9AD5, 0xFFB06BFF, Shader.TileMode.CLAMP));
        PATH.reset();
        PATH.moveTo(50, 56);
        PATH.cubicTo(28, 62, 16, 88, 30, 92);
        PATH.cubicTo(42, 95, 48, 76, 50, 56);
        PATH.close();
        c.drawPath(PATH, P);
        PATH.reset();
        PATH.moveTo(50, 56);
        PATH.cubicTo(72, 62, 84, 88, 70, 92);
        PATH.cubicTo(58, 95, 52, 76, 50, 56);
        PATH.close();
        c.drawPath(PATH, P);

        P.setShader(null);
        P.setShadowLayer(0, 0, 0, 0);
        // wing spots
        fill(P, 0x66FFFFFF);
        c.drawCircle(26, 36, 6, P);
        c.drawCircle(74, 36, 6, P);
        c.drawCircle(33, 80, 4.5f, P);
        c.drawCircle(67, 80, 4.5f, P);

        // body + antennae
        fill(P, 0xFF3B2A55);
        R.set(45.5f, 34, 54.5f, 76);
        c.drawRoundRect(R, 5f, 5f, P);
        stroke(P, 2.4f, 0xFF3B2A55);
        PATH.reset();
        PATH.moveTo(48, 36);
        PATH.cubicTo(42, 24, 36, 18, 30, 14);
        c.drawPath(PATH, P);
        PATH.reset();
        PATH.moveTo(52, 36);
        PATH.cubicTo(58, 24, 64, 18, 70, 14);
        c.drawPath(PATH, P);
        fill(P, 0xFF3B2A55);
        c.drawCircle(30, 14, 3, P);
        c.drawCircle(70, 14, 3, P);
    }

    private static void sparkle(Canvas c, boolean glow) {
        P.reset();
        P.setAntiAlias(true);
        P.setStyle(Paint.Style.FILL);
        P.setShader(new LinearGradient(0, 0, 100, 100, 0xFFFFFFFF, 0xFFFFE066, Shader.TileMode.CLAMP));
        shadow(P, true, glow ? 18f : 8f, 0xAAFFF3B0);
        PATH.reset();
        PATH.moveTo(50, 2);
        PATH.quadTo(58, 42, 98, 50);
        PATH.quadTo(58, 58, 50, 98);
        PATH.quadTo(42, 58, 2, 50);
        PATH.quadTo(42, 42, 50, 2);
        PATH.close();
        c.drawPath(PATH, P);

        P.setShader(null);
        P.setShadowLayer(0, 0, 0, 0);
        P.setShader(new LinearGradient(60, 60, 96, 96, 0xFFFFF3B0, 0xFFFFC107, Shader.TileMode.CLAMP));
        PATH.reset();
        PATH.moveTo(78, 58);
        PATH.quadTo(82, 76, 96, 80);
        PATH.quadTo(82, 84, 78, 98);
        PATH.quadTo(74, 84, 60, 80);
        PATH.quadTo(74, 76, 78, 58);
        PATH.close();
        c.drawPath(PATH, P);
        P.setShader(null);
        fill(P, 0xFFFFFFFF);
        c.drawCircle(20, 76, 3.5f, P);
        c.drawCircle(28, 88, 2.5f, P);
        c.drawCircle(14, 62, 2.5f, P);
    }

    private static void glow(Canvas c) {
        P.reset();
        P.setAntiAlias(true);
        P.setStyle(Paint.Style.FILL);
        P.setShader(new RadialGradient(50, 50, 50,
                new int[]{0xFFFFFFFF, 0x88FFE066, 0x00FFB020},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(50, 50, 50, P);
        P.setShader(null);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(3f);
        for (int i = 0; i < 12; i++) {
            int save = c.save();
            c.rotate(i * 30f, 50, 50);
            P.setColor(i % 2 == 0 ? 0xCCFFF3B0 : 0x88FFFFFF);
            c.drawLine(50, 6, 50, 20, P);
            c.restoreToCount(save);
        }
    }

    private static void sun(Canvas c, boolean glow) {
        P.reset();
        P.setAntiAlias(true);
        stroke(P, 6f, 0xFFFFC107);
        P.setShadowLayer(glow ? 12f : 4f, 0, 2, glow ? 0xAAFFE066 : 0x44000000);
        for (int i = 0; i < 8; i++) {
            int save = c.save();
            c.rotate(i * 45f, 50, 50);
            c.drawLine(50, 8, 50, 24, P);
            c.restoreToCount(save);
        }
        P.setShadowLayer(0, 0, 0, 0);
        P.setStyle(Paint.Style.FILL);
        P.setShader(new RadialGradient(42, 40, 40, 0xFFFFF9C4, 0xFFFF9800, Shader.TileMode.CLAMP));
        c.drawCircle(50, 50, 28, P);
        P.setShader(null);
        fill(P, 0x33000000);
        c.drawCircle(42, 48, 3f, P);
        c.drawCircle(58, 48, 3f, P);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(3f);
        P.setColor(0x99000000);
        R.set(42, 54, 58, 64);
        c.drawArc(R, 0, 180, false, P);
    }

    private static void crown(Canvas c, boolean glow) {
        PATH.reset();
        PATH.moveTo(12, 78);
        PATH.lineTo(16, 30);
        PATH.lineTo(32, 52);
        PATH.lineTo(50, 20);
        PATH.lineTo(68, 52);
        PATH.lineTo(84, 30);
        PATH.lineTo(88, 78);
        PATH.close();
        P.reset();
        P.setAntiAlias(true);
        P.setStyle(Paint.Style.FILL);
        P.setShader(new LinearGradient(0, 20, 0, 80, 0xFFFFE873, 0xFFE8A200, Shader.TileMode.CLAMP));
        shadow(P, glow, glow ? 14f : 5f, glow ? 0xAAFFF3B0 : 0x55000000);
        c.drawPath(PATH, P);
        P.setShader(null);
        P.setShadowLayer(0, 0, 0, 0);
        stroke(P, 3.4f, 0xFFFFF7C2);
        c.drawPath(PATH, P);
        fill(P, 0xFFFF5252);
        c.drawCircle(50, 62, 5f, P);
        fill(P, 0xFF4FC3F7);
        c.drawCircle(30, 68, 4f, P);
        c.drawCircle(70, 68, 4f, P);
    }

    private static void balloon(Canvas c, boolean glow) {
        P.reset();
        P.setAntiAlias(true);
        P.setStyle(Paint.Style.FILL);
        P.setShader(new RadialGradient(40, 32, 60, 0xFFFF8A9B, 0xFFD81B3C, Shader.TileMode.CLAMP));
        shadow(P, glow, glow ? 14f : 5f, glow ? 0xAAFF8A9B : 0x55000000);
        R.set(22, 6, 78, 72);
        c.drawOval(R, P);
        P.setShader(null);
        P.setShadowLayer(0, 0, 0, 0);
        fill(P, 0x55FFFFFF);
        R.set(34, 16, 48, 34);
        c.drawOval(R, P);
        fill(P, 0xFFB0122A);
        PATH.reset();
        PATH.moveTo(46, 72);
        PATH.lineTo(54, 72);
        PATH.lineTo(50, 80);
        PATH.close();
        c.drawPath(PATH, P);
        stroke(P, 2.6f, 0xFF7A6C64);
        PATH.reset();
        PATH.moveTo(50, 80);
        PATH.quadTo(38, 90, 52, 97);
        c.drawPath(PATH, P);
    }

    private static void bow(Canvas c, boolean glow) {
        P.reset();
        P.setAntiAlias(true);
        P.setStyle(Paint.Style.FILL);
        P.setShader(new LinearGradient(0, 0, 100, 100, 0xFFFF80AB, 0xFFE91E63, Shader.TileMode.CLAMP));
        shadow(P, glow, glow ? 14f : 5f, glow ? 0xAAFF80AB : 0x55000000);

        PATH.reset();
        PATH.moveTo(48, 50);
        PATH.cubicTo(30, 24, 4, 26, 8, 48);
        PATH.cubicTo(10, 66, 32, 62, 48, 50);
        PATH.close();
        c.drawPath(PATH, P);
        PATH.reset();
        PATH.moveTo(52, 50);
        PATH.cubicTo(70, 24, 96, 26, 92, 48);
        PATH.cubicTo(90, 66, 68, 62, 52, 50);
        PATH.close();
        c.drawPath(PATH, P);
        P.setShader(null);
        P.setShadowLayer(0, 0, 0, 0);

        fill(P, 0xFFF06292);
        PATH.reset();
        PATH.moveTo(46, 50);
        PATH.lineTo(30, 84);
        PATH.lineTo(46, 78);
        PATH.close();
        c.drawPath(PATH, P);
        PATH.reset();
        PATH.moveTo(54, 50);
        PATH.lineTo(70, 84);
        PATH.lineTo(54, 78);
        PATH.close();
        c.drawPath(PATH, P);

        fill(P, 0xFFC2185B);
        c.drawCircle(50, 50, 9, P);
        fill(P, 0x55FFFFFF);
        c.drawCircle(47, 47, 3.4f, P);
    }

    private static void bell(Canvas c, boolean glow) {
        PATH.reset();
        PATH.moveTo(24, 74);
        PATH.cubicTo(30, 66, 30, 52, 30, 44);
        PATH.cubicTo(30, 26, 70, 26, 70, 44);
        PATH.cubicTo(70, 52, 70, 66, 76, 74);
        PATH.close();
        P.reset();
        P.setAntiAlias(true);
        P.setStyle(Paint.Style.FILL);
        P.setShader(new LinearGradient(0, 26, 0, 78, 0xFFFFE082, 0xFFF9A825, Shader.TileMode.CLAMP));
        shadow(P, glow, glow ? 16f : 6f, glow ? 0xAAFFF3B0 : 0x55000000);
        c.drawPath(PATH, P);
        P.setShader(null);
        P.setShadowLayer(0, 0, 0, 0);
        stroke(P, 3f, 0xFFFFF7C2);
        c.drawPath(PATH, P);
        fill(P, 0xFFF9A825);
        c.drawCircle(50, 22, 5f, P);
        c.drawCircle(50, 82, 7f, P);
        fill(P, 0x55FFFFFF);
        R.set(36, 40, 46, 60);
        c.drawRoundRect(R, 5f, 5f, P);
    }

    private static void rainbow(Canvas c) {
        int[] colors = {0xFFFF5A5F, 0xFFFFB020, 0xFFFFE44D, 0xFF4CD964, 0xFF34AADC, 0xFF9B59B6};
        P.reset();
        P.setAntiAlias(true);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(7f);
        P.setStrokeCap(Paint.Cap.BUTT);
        for (int i = 0; i < colors.length; i++) {
            P.setColor(colors[i]);
            P.setShadowLayer(i == 0 ? 4f : 0f, 0, 0, 0x33000000);
            float inset = 8 + i * 7f;
            R.set(inset, inset + 12, 100 - inset, 100 - inset + 12);
            c.drawArc(R, 180, 180, false, P);
        }
        P.setShadowLayer(0, 0, 0, 0);
        fill(P, 0xFFFFFFFF);
        c.drawCircle(18, 86, 12, P);
        c.drawCircle(32, 88, 9, P);
        c.drawCircle(82, 86, 12, P);
        c.drawCircle(68, 88, 9, P);
    }

    private static void leaf(Canvas c, boolean glow) {
        PATH.reset();
        PATH.moveTo(50, 96);
        PATH.cubicTo(10, 74, 12, 20, 50, 4);
        PATH.cubicTo(88, 20, 90, 74, 50, 96);
        PATH.close();
        P.reset();
        P.setAntiAlias(true);
        P.setStyle(Paint.Style.FILL);
        P.setShader(new LinearGradient(0, 4, 100, 96, 0xFF9CCC65, 0xFF2E7D32, Shader.TileMode.CLAMP));
        shadow(P, glow, glow ? 14f : 5f, glow ? 0xAA9CCC65 : 0x55000000);
        c.drawPath(PATH, P);
        P.setShader(null);
        P.setShadowLayer(0, 0, 0, 0);
        P.setStyle(Paint.Style.STROKE);
        P.setStrokeWidth(2.6f);
        P.setColor(0x99FFFFFF);
        PATH.reset();
        PATH.moveTo(50, 92);
        PATH.lineTo(50, 14);
        c.drawPath(PATH, P);
        for (int i = 1; i <= 4; i++) {
            float y = 20 + i * 15f;
            PATH.reset();
            PATH.moveTo(50, y);
            PATH.quadTo(38, y - 6, 34, y - 14);
            c.drawPath(PATH, P);
            PATH.reset();
            PATH.moveTo(50, y);
            PATH.quadTo(62, y - 6, 66, y - 14);
            c.drawPath(PATH, P);
        }
    }

    private static void party(Canvas c) {
        int[][] bits = {
                {24, 26, 0xFFFF5252}, {52, 16, 0xFFFFE44D}, {76, 30, 0xFF4FC3F7},
                {16, 58, 0xFFAB47BC}, {44, 46, 0xFF4CD964}, {72, 62, 0xFFFF9800},
                {30, 82, 0xFF29B6F6}, {62, 84, 0xFFFF4081}, {86, 48, 0xFF9CCC65}
        };
        P.reset();
        P.setAntiAlias(true);
        P.setStyle(Paint.Style.FILL);
        for (int i = 0; i < bits.length; i++) {
            P.setColor(bits[i][2]);
            int save = c.save();
            c.rotate(i * 37f, bits[i][0], bits[i][1]);
            R.set(bits[i][0] - 7, bits[i][1] - 3.5f, bits[i][0] + 7, bits[i][1] + 3.5f);
            c.drawRoundRect(R, 2f, 2f, P);
            c.restoreToCount(save);
        }
        fill(P, 0xFFFFD54F);
        c.drawCircle(50, 50, 7, P);
    }

    private static void youtube(Canvas c) {
        P.reset();
        P.setAntiAlias(true);
        P.setStyle(Paint.Style.FILL);
        P.setColor(0xFFFF0000);
        P.setShadowLayer(6f, 0, 2f, 0x55000000);
        R.set(4, 22, 96, 78);
        c.drawRoundRect(R, 16f, 16f, P);
        P.setShadowLayer(0, 0, 0, 0);
        fill(P, 0xFFFFFFFF);
        PATH.reset();
        PATH.moveTo(38, 34);
        PATH.lineTo(70, 50);
        PATH.lineTo(38, 66);
        PATH.close();
        c.drawPath(PATH, P);
    }

    private static void subscribe(Canvas c) {
        P.reset();
        P.setAntiAlias(true);
        P.setStyle(Paint.Style.FILL);
        P.setColor(0xFFFF0000);
        P.setShadowLayer(8f, 0, 3f, 0x66000000);
        R.set(2, 28, 98, 72);
        c.drawRoundRect(R, 22f, 22f, P);
        P.setShadowLayer(0, 0, 0, 0);

        fill(P, 0xFFFFFFFF);
        PATH.reset();
        PATH.moveTo(14, 40);
        PATH.lineTo(34, 50);
        PATH.lineTo(14, 60);
        PATH.close();
        c.drawPath(PATH, P);

        P.setStyle(Paint.Style.FILL);
        P.setColor(Color.WHITE);
        P.setFakeBoldText(true);
        P.setTextAlign(Paint.Align.CENTER);
        float textSize = 11f;
        // shrink until the word fits inside the pill
        while (textSize > 6f && P.measureText("SUBSCRIBE") > 56f) {
            textSize -= 0.5f;
            P.setTextSize(textSize);
        }
        P.setTextSize(textSize);
        c.drawText("SUBSCRIBE", 67, 54, P);
        P.setFakeBoldText(false);
        P.setTextAlign(Paint.Align.LEFT);
    }

    public static String labelFor(String key) {
        for (int i = 0; i < KEYS.length; i++) {
            if (KEYS[i].equals(key)) {
                return LABELS[i];
            }
        }
        return key;
    }
}
