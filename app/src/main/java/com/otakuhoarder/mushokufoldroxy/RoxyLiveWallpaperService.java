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
        private static final long FRAME_DELAY_MS = 16L;
        private static final long AMBIENT_PULSE_INTERVAL_MS = 9000L;
        private static final int PARTICLE_COUNT = 22;

        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint effectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<Pulse> pulses = new ArrayList<>();

        private Bitmap sourceRoxy;
        private Bitmap cachedRoxy;
        private SensorManager sensorManager;
        private Sensor accelerometer;
        private boolean visible;
        private long start = System.currentTimeMillis();
        private long lastAmbientPulse;
        private int ambientPulseIndex;
        private int width;
        private int height;
        private float targetTiltX;
        private float targetTiltY;
        private float renderedTiltX;
        private float renderedTiltY;

        private final float[] pulseX = {0.50f, 0.34f, 0.67f, 0.51f};
        private final float[] pulseY = {0.30f, 0.62f, 0.48f, 0.74f};

        private final Runnable drawRunner = new Runnable() {
            @Override
            public void run() {
                drawFrame();
            }
        };

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            setTouchEventsEnabled(true);
            sourceRoxy = BitmapFactory.decodeResource(getResources(), R.drawable.roxy_wallpaper);
            sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            if (sensorManager != null) {
                accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            }
        }

        @Override
        public void onDestroy() {
            handler.removeCallbacks(drawRunner);
            unregisterSensors();
            recycleCachedRoxy();
            if (sourceRoxy != null && !sourceRoxy.isRecycled()) {
                sourceRoxy.recycle();
            }
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean isVisible) {
            visible = isVisible;
            handler.removeCallbacks(drawRunner);
            if (visible) {
                registerSensors();
                long now = System.currentTimeMillis();
                lastAmbientPulse = now;
                addPulseInternal(width * 0.5f, height * 0.38f, now, 1.15f, 1);
                drawFrame();
            } else {
                unregisterSensors();
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            width = w;
            height = h;
            super.onSurfaceChanged(holder, format, w, h);
            rebuildCachedRoxy();
            drawFrame();
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            visible = false;
            handler.removeCallbacks(drawRunner);
            unregisterSensors();
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
                addPulse(px, py, 1.15f, 1);
            }
            return super.onCommand(action, x, y, z, extras, resultRequested);
        }

        private void registerSensors() {
            if (sensorManager != null && accelerometer != null) {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
            }
        }

        private void unregisterSensors() {
            if (sensorManager != null) {
                sensorManager.unregisterListener(this);
            }
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;
            targetTiltX = clamp((-event.values[0] / SensorManager.GRAVITY_EARTH) * 28f, -28f, 28f);
            targetTiltY = clamp((event.values[1] / SensorManager.GRAVITY_EARTH) * 16f, -16f, 16f);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) { }

        private float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }

        private void addPulse(float x, float y, float power, int type) {
            addPulseInternal(x, y, System.currentTimeMillis(), power, type);
            drawFrame();
        }

        private void addPulseInternal(float x, float y, long born, float power, int type) {
            if (width <= 0 || height <= 0) return;
            synchronized (pulses) {
                pulses.add(new Pulse(x, y, born, power, type));
            }
        }

        private void maybeAddAmbientPulse(long now) {
            if (!visible || width <= 0 || height <= 0 || now - lastAmbientPulse < AMBIENT_PULSE_INTERVAL_MS) {
                return;
            }
            int index = ambientPulseIndex % pulseX.length;
            ambientPulseIndex++;
            lastAmbientPulse = now;
            addPulseInternal(width * pulseX[index], height * pulseY[index], now, 0.72f, 2);
        }

        private void rebuildCachedRoxy() {
            if (sourceRoxy == null || width <= 0 || height <= 0) return;

            float overscan = 1.035f;
            float scale = Math.max((float) width / sourceRoxy.getWidth(),
                    (float) height / sourceRoxy.getHeight()) * overscan;
            int scaledWidth = Math.max(1, Math.round(sourceRoxy.getWidth() * scale));
            int scaledHeight = Math.max(1, Math.round(sourceRoxy.getHeight() * scale));

            Bitmap newCache = Bitmap.createScaledBitmap(sourceRoxy, scaledWidth, scaledHeight, true);
            recycleCachedRoxy();
            cachedRoxy = newCache;
        }

        private void recycleCachedRoxy() {
            if (cachedRoxy != null && cachedRoxy != sourceRoxy && !cachedRoxy.isRecycled()) {
                cachedRoxy.recycle();
            }
            cachedRoxy = null;
        }

        private void drawFrame() {
            handler.removeCallbacks(drawRunner);
            if (!visible && cachedRoxy == null) return;

            long now = System.currentTimeMillis();
            maybeAddAmbientPulse(now);

            renderedTiltX += (targetTiltX - renderedTiltX) * 0.085f;
            renderedTiltY += (targetTiltY - renderedTiltY) * 0.085f;

            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas != null && cachedRoxy != null) {
                    width = canvas.getWidth();
                    height = canvas.getHeight();
                    drawRoxy(canvas);
                    drawAmbientMana(canvas, now);
                    drawPulses(canvas, now);
                }
            } finally {
                if (canvas != null) {
                    holder.unlockCanvasAndPost(canvas);
                }
            }

            if (visible) {
                handler.postDelayed(drawRunner, FRAME_DELAY_MS);
            }
        }

        private void drawRoxy(Canvas canvas) {
            canvas.drawColor(0xFF050A16);

            float left = (width - cachedRoxy.getWidth()) * 0.5f + renderedTiltX;
            float top = (height - cachedRoxy.getHeight()) * 0.5f + renderedTiltY;
            canvas.drawBitmap(cachedRoxy, left, top, bitmapPaint);

            effectPaint.setStyle(Paint.Style.FILL);
            effectPaint.setColor(0x16020A1B);
            canvas.drawRect(0, 0, width, height, effectPaint);
        }

        private void drawAmbientMana(Canvas canvas, long now) {
            float t = ((now - start) % 16000L) / 16000f;
            for (int i = 0; i < PARTICLE_COUNT; i++) {
                float seed = (i * 0.6180339f) % 1f;
                float speed = 0.025f + (i % 4) * 0.008f;
                float x = ((seed + t * speed) % 1f) * width + renderedTiltX * 0.22f;
                float y = height - (((i * 0.137f + t * (0.24f + (i % 3) * 0.055f)) % 1f) * height)
                        + renderedTiltY * 0.12f;
                float shimmer = 0.62f + 0.38f * (float) Math.sin((t * 6.283185f) + i * 0.72f);
                int alpha = 24 + (int) (56 * shimmer);

                effectPaint.setStyle(Paint.Style.FILL);
                effectPaint.setColor((alpha << 24) | 0xA7EDFF);
                canvas.drawCircle(x, y, 1.7f + (i % 3), effectPaint);
            }
        }

        private void drawPulses(Canvas canvas, long now) {
            synchronized (pulses) {
                Iterator<Pulse> it = pulses.iterator();
                while (it.hasNext()) {
                    Pulse p = it.next();
                    float duration = p.type == 2 ? 2.2f : 1.55f;
                    float age = (now - p.born) / 1000f;
                    if (age > duration) {
                        it.remove();
                        continue;
                    }

                    float progress = age / duration;
                    float eased = 1f - (1f - progress) * (1f - progress);
                    int baseAlpha = p.type == 2 ? 105 : 205;
                    int alpha = (int) (baseAlpha * (1f - progress));
                    float radius = (24f + eased * Math.min(width, height) * 0.29f) * p.power;

                    effectPaint.setStyle(Paint.Style.STROKE);
                    effectPaint.setStrokeWidth((p.type == 2 ? 4f : 7f) * (1f - progress) + 1.2f);
                    int rgb = p.type == 2 ? 0x9FEAFF : 0xD7F7FF;
                    effectPaint.setColor((alpha << 24) | rgb);
                    canvas.drawCircle(p.x, p.y, radius, effectPaint);
                    canvas.drawCircle(p.x, p.y, radius * 0.62f, effectPaint);

                    if (p.type == 1) {
                        for (int i = 0; i < 6; i++) {
                            double angle = i * Math.PI / 3d + eased * 1.2f;
                            float sx = p.x + (float) Math.cos(angle) * radius * 0.76f;
                            float sy = p.y + (float) Math.sin(angle) * radius * 0.76f;
                            effectPaint.setStyle(Paint.Style.FILL);
                            canvas.drawCircle(sx, sy, 4.5f * (1f - progress) + 1f, effectPaint);
                        }
                    }
                }
            }
            effectPaint.setStyle(Paint.Style.FILL);
        }
    }

    private static class Pulse {
        final float x;
        final float y;
        final float power;
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
