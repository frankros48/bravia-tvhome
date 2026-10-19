package com.frank.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** A weather symbol drawn with plain shapes: no image files. Pick it with a WMO weather code. */
final class WeatherIcon extends View {
    private static final int SUN = 0, MOON = 1, PARTLY = 2, CLOUD = 3, RAIN = 4, SNOW = 5, STORM = 6, FOG = 7;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private int kind = CLOUD;

    WeatherIcon(Context c) {
        super(c);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
    }

    void setWeather(int wmoCode, boolean isDay) {
        if (wmoCode <= 1) kind = isDay ? SUN : MOON;
        else if (wmoCode == 2) kind = isDay ? PARTLY : CLOUD;
        else if (wmoCode == 3) kind = CLOUD;
        else if (wmoCode == 45 || wmoCode == 48) kind = FOG;
        else if (wmoCode >= 71 && wmoCode <= 77 || wmoCode == 85 || wmoCode == 86) kind = SNOW;
        else if (wmoCode >= 95) kind = STORM;
        else kind = RAIN;
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        int size = Math.min(MeasureSpec.getSize(w), MeasureSpec.getSize(h));
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float s = getWidth() / 100f;
        canvas.save();
        canvas.scale(s, s);
        switch (kind) {
            case SUN: sun(canvas, 50, 50, 1f); break;
            case MOON: moon(canvas); break;
            case PARTLY:
                sun(canvas, 38, 38, 0.7f);
                canvas.save();
                canvas.translate(12, 8);
                cloud(canvas);
                canvas.restore();
                break;
            case CLOUD: canvas.save(); canvas.translate(0, 4); cloud(canvas); canvas.restore(); break;
            case RAIN:
                canvas.save(); canvas.translate(0, -8); cloud(canvas); canvas.restore();
                stroke(0xFF6FB6FF, 5f);
                for (int i = 0; i < 3; i++) canvas.drawLine(36 + i * 15, 70, 31 + i * 15, 84, line);
                break;
            case SNOW:
                canvas.save(); canvas.translate(0, -8); cloud(canvas); canvas.restore();
                fill.setColor(0xFFFFFFFF);
                for (int i = 0; i < 3; i++) canvas.drawCircle(36 + i * 15, 78 + (i == 1 ? 6 : 0), 4f, fill);
                break;
            case STORM:
                canvas.save(); canvas.translate(0, -8); cloud(canvas); canvas.restore();
                fill.setColor(0xFFFFD84D);
                path.reset();
                path.moveTo(54, 62); path.lineTo(40, 80); path.lineTo(50, 80);
                path.lineTo(44, 94); path.lineTo(64, 72); path.lineTo(53, 72); path.close();
                canvas.drawPath(path, fill);
                break;
            default: // FOG
                canvas.save(); canvas.translate(0, -8); cloud(canvas); canvas.restore();
                stroke(0xFFC8CDD6, 5f);
                canvas.drawLine(24, 74, 76, 74, line);
                canvas.drawLine(32, 85, 84, 85, line);
        }
        canvas.restore();
    }

    private void stroke(int color, float width) {
        line.setColor(color);
        line.setStrokeWidth(width);
    }

    private void sun(Canvas c, float cx, float cy, float scale) {
        fill.setColor(0xFFFFC94D);
        c.drawCircle(cx, cy, 19 * scale, fill);
        stroke(0xFFFFC94D, 6f * scale);
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(i * 45);
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            c.drawLine(cx + cos * 28 * scale, cy + sin * 28 * scale, cx + cos * 40 * scale, cy + sin * 40 * scale, line);
        }
    }

    private void moon(Canvas c) {
        path.reset();
        path.addCircle(50, 50, 30, Path.Direction.CW);
        Path bite = new Path();
        bite.addCircle(63, 42, 26, Path.Direction.CW);
        path.op(bite, Path.Op.DIFFERENCE);
        fill.setColor(0xFFF1E9B8);
        c.drawPath(path, fill);
    }

    private void cloud(Canvas c) {
        fill.setColor(0xFFEAF0F7);
        c.drawCircle(36, 58, 15, fill);
        c.drawCircle(54, 50, 20, fill);
        c.drawCircle(72, 60, 14, fill);
        c.drawRoundRect(22, 56, 86, 76, 10, 10, fill);
    }
}
