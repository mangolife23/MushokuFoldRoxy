package com.otakuhoarder.mushokufoldroxy;

import android.app.WallpaperManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class RoxyLiveWallpaperService extends WallpaperService {
    @Override
    public Engine onCreateEngine() {
        return new RoxyEngine();
    }

    private class RoxyEngine extends Engine implements SensorEventListener {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint effectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<Pulse> pulses = new ArrayList<>();

        private Bitmap roxy;
        private SensorManager sensorManager;
        private Sensor accelerometer;
        private boolean visible;
        private long start = System.currentTimeMillis();
        private long lastAutoPulse;
        private int autoPulseIndex;
        private int width;
        private int height;
        private float tiltX;
        private float tiltY;

        private final Runnable drawRunner = new Runnable() {
            @Override public void run() { drawFrame(); }
        };

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            setTouchEventsEnabled(true);
            roxy = BitmapFactory.decodeResource(getResources(), R.drawable.roxy_wallpaper);
            sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            if (sensorManager != null) {
                accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            }
            lastAutoPulse = System.currentTimeMillis() - 2500L;
        }

        @Override
        public void onDestroy() {
            handler.removeCallbacks(drawRunner);
            unregisterSensors();
            if (roxy != null) roxy.recycle();
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean isVisible) {
            visible = isVisible;
            if (visible) {
                registerSensors();
                drawFrame();
            } else {
                unregisterSensors();
                handler.removeCallbacks(drawRunner);
            }
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
            unregisterSensors();
            handler.removeCallbacks(drawRunner);
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                addPulse(event.getX(), event.getY(), 1.0f, 0);
            }
            super.onTouchEvent(event);
        }

        @Override
        public Bundle onCommand(String action, int x, int y, int z, Bundle extras, boolean resultRequested) {
            if (WallpaperManager.COMMAND_TAP.equals(action)
                    || WallpaperManager.COMMAND_SECONDARY_TAP.equals(action)) {
                float px = x >= 0 ? x : width * 0.5f;
                float py = y >= 0 ? y : height * 0.5f;
                addPulse(px, py, 1.25f, 1);
            }
            return super.onCommand(action, x, y, z, extras, resultRequested);
        }

        private void registerSensors() {
            if (sensorManager != null && accelerometer != null) {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
            }
        }

        private void unregisterSensors() {
            if (sensorManager != null) sensorManager.unregisterListener(this);
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
            float targetX = clamp((-event.values[0] / SensorManager.GRAVITY_EARTH) * 34f, -34f, 34f);
            float targetY = clamp((event.values[1] / SensorManager.GRAVITY_EARTH) * 22f, -22f, 22f);
            tiltX = tiltX * 0.86f + targetX * 0.14f;
            tiltY = tiltY * 0.86f + targetY * 0.14f;
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) { }

        private float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }

        private void addPulse(float x, float y, float power, int type) {
            synchronized (pulses) {
                pulses.add(new Pulse(x, y, System.currentTimeMillis(), power, type));
            }
            drawFrame();
        }

        private void maybeAddAutoPulse(long now) {
            if (!visible || width <= 0 || height <= 0 || now - lastAutoPulse < 3600L) return;
            float[][] spots = {
                    {0.50f, 0.28f},
                    {0.33f, 0.58f},
                    {0.68f, 0.46f},
                    {0.50f, 0.72f}
            };
            float[] spot = spots[autoPulseIndex % spots.length];
            autoPulseIndex++;
            lastAutoPulse = now;
            synchronized (pulses) {
                pulses.add(new Pulse(width * spot[0], height * spot[1], now, 1.45f, 1));
            }
        }

        private void drawFrame() {
            handler.removeCallbacks(drawRunner);
            long now = System.currentTimeMillis();
            maybeAddAutoPulse(now);

            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas != null && roxy != null) {
                    width = canvas.getWidth();
                    height = canvas.getHeight();
                    drawRoxy(canvas);
                    drawAmbientMana(canvas);
                    drawPulses(canvas, now);
                }
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas);
            }
            if (visible) handler.postDelayed(drawRunner, 33);
        }

        private void drawRoxy(Canvas canvas) {
            float scale = Math.max((float) width / roxy.getWidth(), (float) height / roxy.getHeight()) * 1.045f;
            float dw = roxy.getWidth() * scale;
            float dh = roxy.getHeight() * scale;
            float left = (width - dw) / 2f + tiltX;
            float top = (height - dh) / 2f + tiltY;

            canvas.drawColor(0xFF050A16);
            canvas.save();
            canvas.translate(left, top);
            canvas.scale(scale, scale);
            canvas.drawBitmap(roxy, 0, 0, paint);
            canvas.restore();

            effectPaint.setStyle(Paint.Style.FILL);
            effectPaint.setColor(0x20020A1B);
            canvas.drawRect(0, 0, width, height, effectPaint);
        }

        private void drawAmbientMana(Canvas canvas) {
            float t = ((System.currentTimeMillis() - start) % 10000L) / 10000f;
            for (int i = 0; i < 32; i++) {
                float seed = (i * 0.6180339f) % 1f;
                float x = ((seed + t * (0.04f + (i % 4) * 0.012f)) % 1f) * width + tiltX * 0.35f;
                float y = height - (((i * 0.137f + t * (0.35f + (i % 3) * 0.08f)) % 1f) * height) + tiltY * 0.2f;
                float shimmer = 0.55f + 0.45f * (float)Math.sin((t * 6.283f) + i);
                int alpha = 45 + (int)(90 * shimmer);
                effectPaint.setStyle(Paint.Style.FILL);
                effectPaint.setColor((alpha << 24) | 0x9FEAFF);
                canvas.drawCircle(x, y, 2.5f + (i % 4), effectPaint);
            }
        }

        private void drawPulses(Canvas canvas, long now) {
            synchronized (pulses) {
                Iterator<Pulse> it = pulses.iterator();
                while (it.hasNext()) {
                    Pulse p = it.next();
                    float age = (now - p.born) / 1000f;
                    if (age > 1.45f) {
                        it.remove();
                        continue;
                    }
                    float progress = age / 1.45f;
                    int alpha = (int)(245 * (1f - progress));
                    float radius = (30f + progress * Math.min(width, height) * 0.40f) * p.power;

                    effectPaint.setStyle(Paint.Style.STROKE);
                    effectPaint.setStrokeWidth(10f * (1f - progress) + 2f);
                    int rgb = p.type == 1 ? 0xD7F7FF : 0x7EDBFF;
                    effectPaint.setColor((alpha << 24) | rgb);
                    canvas.drawCircle(p.x, p.y, radius, effectPaint);
                    canvas.drawCircle(p.x, p.y, radius * 0.64f, effectPaint);
                    canvas.drawCircle(p.x, p.y, radius * 0.32f, effectPaint);

                    if (p.type == 1) {
                        for (int i = 0; i < 8; i++) {
                            double a = i * Math.PI / 4d + progress * 1.7f;
                            float sx = p.x + (float)Math.cos(a) * radius * 0.78f;
                            float sy = p.y + (float)Math.sin(a) * radius * 0.78f;
                            effectPaint.setStyle(Paint.Style.FILL);
                            canvas.drawCircle(sx, sy, 7f * (1f - progress) + 1.5f, effectPaint);
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
            this.x = x;
            this.y = y;
            this.born = born;
            this.power = power;
            this.type = type;
        }
    }
}
