package com.otakuhoarder.mushokufoldroxy;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class MagicOverlayView extends View {
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long startTime = System.currentTimeMillis();
    private float burst = 0f;

    public MagicOverlayView(Context context) {
        super(context);
        init();
    }

    public MagicOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(3f);
        particlePaint.setStyle(Paint.Style.FILL);
    }

    public void triggerBurst() {
        burst = 1f;
        startTime = System.currentTimeMillis();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() == 0 || getHeight() == 0) return;

        long now = System.currentTimeMillis();
        float t = ((now - startTime) % 6000L) / 6000f;
        float cx = getWidth() * 0.5f;
        float cy = getHeight() * 0.36f;
        float base = Math.min(getWidth(), getHeight()) * 0.16f;

        for (int i = 0; i < 3; i++) {
            float phase = (t + i * 0.28f) % 1f;
            float radius = base * (0.75f + phase * 1.9f);
            int alpha = (int) (125 * (1f - phase));
            ringPaint.setColor((alpha << 24) | 0x7EDBFF);
            ringPaint.setStrokeWidth(2f + 3f * (1f - phase));
            canvas.drawCircle(cx, cy, radius, ringPaint);
        }

        for (int i = 0; i < 18; i++) {
            double angle = (Math.PI * 2d * i / 18d) + t * Math.PI * 2d;
            float orbit = base * (1.0f + (i % 4) * 0.22f);
            float x = cx + (float) Math.cos(angle) * orbit;
            float y = cy + (float) Math.sin(angle) * orbit * 0.62f;
            int alpha = 80 + (i % 3) * 35;
            particlePaint.setColor((alpha << 24) | 0xB6EEFF);
            canvas.drawCircle(x, y, 2.5f + (i % 3), particlePaint);
        }

        if (burst > 0f) {
            float elapsed = (now - startTime) / 900f;
            burst = Math.max(0f, 1f - elapsed);
            float r = base * (1f + (1f - burst) * 4.5f);
            int alpha = (int) (210 * burst);
            ringPaint.setColor((alpha << 24) | 0xDDF8FF);
            ringPaint.setStrokeWidth(8f * burst + 1f);
            canvas.drawCircle(cx, cy, r, ringPaint);
        }

        postInvalidateDelayed(33);
    }
}
