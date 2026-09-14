package com.otakuhoarder.mushokufoldroxy;

import android.app.WallpaperManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class RoxyLiveWallpaperService extends WallpaperService {
    @Override
    public Engine onCreateEngine() {
        return new RoxyEngine();
    }

    private class RoxyEngine extends Engine {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint effectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Random random = new Random();
        private final List<Pulse> pulses = new ArrayList<>();
        private Bitmap roxy;
        private boolean visible;
        private long start = System.currentTimeMillis();
        private int width;
        private int height;

        private final Runnable drawRunner = new Runnable() {
            @Override public void run() { drawFrame(); }
        };

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            setTouchEventsEnabled(true);
            roxy = BitmapFactory.decodeResource(getResources(), R.drawable.roxy_wallpaper);
        }

        @Override
        public void onDestroy() {
            handler.removeCallbacks(drawRunner);
            if (roxy != null) roxy.recycle();
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean isVisible) {
            visible = isVisible;
            if (visible) drawFrame();
            else handler.removeCallbacks(drawRunner);
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            width = w;
            height = h;
            super.onSurfaceChanged(holder, format, w, h);
            drawFrame();
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            visible = false;
            handler.removeCallbacks(drawRunner);
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                addPulse(event.getX(), event.getY());
            }
            super.onTouchEvent(event);
        }

        @Override
        public Bundle onCommand(String action, int x, int y, int z, Bundle extras, boolean resultRequested) {
            if (WallpaperManager.COMMAND_TAP.equals(action)
                    || WallpaperManager.COMMAND_SECONDARY_TAP.equals(action)) {
                float px = x >= 0 ? x : width * 0.5f;
                float py = y >= 0 ? y : height * 0.5f;
                addPulse(px, py);
            }
            return super.onCommand(action, x, y, z, extras, resultRequested);
        }

        private void addPulse(float x, float y) {
            float power = 1f;
            int type = 0;
            if (y < height * 0.42f) {
                power = 1.55f;
                type = 1;
            } else if (x > width * 0.65f) {
                power = 1.3f;
                type = 2;
            }
            synchronized (pulses) {
                pulses.add(new Pulse(x, y, System.currentTimeMillis(), power, type));
            }
            drawFrame();
        }

        private void drawFrame() {
            handler.removeCallbacks(drawRunner);
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas != null && roxy != null) {
                    width = canvas.getWidth();
                    height = canvas.getHeight();
                    drawRoxy(canvas);
                    drawAmbientMana(canvas);
                    drawPulses(canvas);
                }
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas);
            }
            if (visible) handler.postDelayed(drawRunner, 33);
        }

        private void drawRoxy(Canvas canvas) {
            float scale = Math.max((float) width / roxy.getWidth(), (float) height / roxy.getHeight());
            float dw = roxy.getWidth() * scale;
            float dh = roxy.getHeight() * scale;
            float left = (width - dw) / 2f;
            float top = (height - dh) / 2f;
            canvas.drawColor(0xFF050A16);
            canvas.save();
            canvas.translate(left, top);
            canvas.scale(scale, scale);
            canvas.drawBitmap(roxy, 0, 0, paint);
            canvas.restore();
            effectPaint.setColor(0x25020A1B);
            canvas.drawRect(0, 0, width, height, effectPaint);
        }

        private void drawAmbientMana(Canvas canvas) {
            float t = ((System.currentTimeMillis() - start) % 10000L) / 10000f;
            for (int i = 0; i < 28; i++) {
                float seed = (i * 0.6180339f) % 1f;
                float x = ((seed + t * (0.04f + (i % 4) * 0.012f)) % 1f) * width;
                float y = height - (((i * 0.137f + t * (0.35f + (i % 3) * 0.08f)) % 1f) * height);
                float shimmer = 0.55f + 0.45f * (float)Math.sin((t * 6.283f) + i);
                int alpha = 35 + (int)(75 * shimmer);
                effectPaint.setStyle(Paint.Style.FILL);
                effectPaint.setColor((alpha << 24) | 0x9FEAFF);
                canvas.drawCircle(x, y, 2f + (i % 4), effectPaint);
            }
        }

        private void drawPulses(Canvas canvas) {
            long now = System.currentTimeMillis();
            synchronized (pulses) {
                Iterator<Pulse> it = pulses.iterator();
                while (it.hasNext()) {
                    Pulse p = it.next();
                    float age = (now - p.born) / 1000f;
                    if (age > 1.25f) {
                        it.remove();
                        continue;
                    }
                    float progress = age / 1.25f;
                    int alpha = (int)(235 * (1f - progress));
                    float radius = (35f + progress * Math.min(width, height) * 0.38f) * p.power;
                    effectPaint.setStyle(Paint.Style.STROKE);
                    effectPaint.setStrokeWidth(9f * (1f - progress) + 2f);
                    int rgb = p.type == 1 ? 0xD7F7FF : (p.type == 2 ? 0x8EBBFF : 0x7EDBFF);
                    effectPaint.setColor((alpha << 24) | rgb);
                    canvas.drawCircle(p.x, p.y, radius, effectPaint);
                    canvas.drawCircle(p.x, p.y, radius * 0.62f, effectPaint);

                    if (p.type == 1) {
                        for (int i = 0; i < 8; i++) {
                            double a = i * Math.PI / 4d + progress;
                            float sx = p.x + (float)Math.cos(a) * radius * 0.78f;
                            float sy = p.y + (float)Math.sin(a) * radius * 0.78f;
                            effectPaint.setStyle(Paint.Style.FILL);
                            canvas.drawCircle(sx, sy, 6f * (1f - progress) + 1f, effectPaint);
                        }
                    }
                    effectPaint.setStyle(Paint.Style.FILL);
                }
            }
        }
    }

    private static class Pulse {
        final float x, y, power;
        final long born;
        final int type;
        Pulse(float x, float y, long born, float power, int type) {
            this.x = x; this.y = y; this.born = born; this.power = power; this.type = type;
        }
    }
}
