package com.frank.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** A frosted, glassy capsule: translucent body, bright rim, and a soft highlight along the top. */
final class GlassPill extends View {
    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float stroke;

    GlassPill(Context c, float dp) {
        super(c);
        stroke = Math.max(1.5f, 1.5f * dp);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(stroke);
        rim.setColor(0xB8FFFFFF);
        shine.setStyle(Paint.Style.STROKE);
        shine.setStrokeWidth(stroke);
        shine.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        body.setShader(new LinearGradient(0, 0, 0, h, 0x70FFFFFF, 0x24FFFFFF, Shader.TileMode.CLAMP));
        shine.setShader(new LinearGradient(0, 0, w, 0, 0x00FFFFFF, 0xCCFFFFFF, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float r = h / 2f;
        rect.set(stroke, stroke, w - stroke, h - stroke);
        canvas.drawRoundRect(rect, r, r, body);
        canvas.drawRoundRect(rect, r, r, rim);
        // Specular glint along the upper edge, brightest toward the right.
        float inset = h * 0.30f;
        canvas.drawLine(inset, stroke * 2.2f, w - inset, stroke * 2.2f, shine);
    }
}
