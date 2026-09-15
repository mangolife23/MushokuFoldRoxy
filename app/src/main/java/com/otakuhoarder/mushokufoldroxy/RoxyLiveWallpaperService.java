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
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class RoxyLiveWallpaperService extends WallpaperService {
    @Override public Engine onCreateEngine() { return new RoxyEngine(); }

    private class RoxyEngine extends Engine implements SensorEventListener, Choreographer.FrameCallback {
        private static final long AMBIENT_PULSE_INTERVAL_MS = 9000L;
        private static final int PARTICLE_COUNT = 22;
        private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint effectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final List<Pulse> pulses = new ArrayList<>();
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final float[] rotationMatrix = new float[9];
        private final float[] orientation = new float[3];

        private Bitmap sourceRoxy, cachedRoxy;
        private SensorManager sensorManager;
        private Sensor rotationVector, accelerometer;
        private boolean visible, framePosted;
        private long start = System.currentTimeMillis(), lastAmbientPulse;
        private int ambientPulseIndex, width, height;
        private float neutralPitch = Float.NaN, neutralRoll = Float.NaN;
        private float targetTiltX, targetTiltY, renderedTiltX, renderedTiltY;
        private final float[] pulseX = {0.50f, 0.34f, 0.67f, 0.51f};
        private final float[] pulseY = {0.30f, 0.62f, 0.48f, 0.74f};

        @Override public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            setTouchEventsEnabled(true);
            sourceRoxy = BitmapFactory.decodeResource(getResources(), R.drawable.roxy_wallpaper);
            sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            if (sensorManager != null) {
                rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
                if (rotationVector == null) rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
                accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            }
        }

        @Override public void onDestroy() {
            stopFrames(); unregisterSensors(); recycleCachedRoxy();
            if (sourceRoxy != null && !sourceRoxy.isRecycled()) sourceRoxy.recycle();
            super.onDestroy();
        }

        @Override public void onVisibilityChanged(boolean isVisible) {
            visible = isVisible;
            if (visible) {
                neutralPitch = Float.NaN; neutralRoll = Float.NaN;
                registerSensors();
                long now = System.currentTimeMillis(); lastAmbientPulse = now;
                addPulseInternal(width * .5f, height * .38f, now, 1.15f, 1);
                requestFrame();
            } else { stopFrames(); unregisterSensors(); }
        }

        @Override public void onSurfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            width = w; height = h; super.onSurfaceChanged(holder, format, w, h);
            rebuildCachedRoxy(); requestFrame();
        }

        @Override public void onSurfaceDestroyed(SurfaceHolder holder) {
            visible = false; stopFrames(); unregisterSensors(); super.onSurfaceDestroyed(holder);
        }

        @Override public void onTouchEvent(MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) addPulse(event.getX(), event.getY(), 1f, 0);
            super.onTouchEvent(event);
        }

        @Override public Bundle onCommand(String action, int x, int y, int z, Bundle extras, boolean resultRequested) {
            if (WallpaperManager.COMMAND_TAP.equals(action) || WallpaperManager.COMMAND_SECONDARY_TAP.equals(action))
                addPulse(x >= 0 ? x : width*.5f, y >= 0 ? y : height*.5f, 1.15f, 1);
            return super.onCommand(action, x, y, z, extras, resultRequested);
        }

        private void registerSensors() {
            if (sensorManager == null) return;
            if (rotationVector != null) sensorManager.registerListener(this, rotationVector, 16666);
            else if (accelerometer != null) sensorManager.registerListener(this, accelerometer, 20000);
        }
        private void unregisterSensors() { if (sensorManager != null) sensorManager.unregisterListener(this); }

        @Override public void onSensorChanged(SensorEvent event) {
            if (event.sensor == rotationVector) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
                SensorManager.getOrientation(rotationMatrix, orientation);
                float pitch = orientation[1], roll = orientation[2];
                if (Float.isNaN(neutralPitch)) { neutralPitch = pitch; neutralRoll = roll; }
                float dx = wrapAngle(roll - neutralRoll), dy = wrapAngle(pitch - neutralPitch);
                targetTiltX = clamp(dx * 145f, -52f, 52f);
                targetTiltY = clamp(-dy * 115f, -38f, 38f);
            } else if (event.sensor == accelerometer) {
                targetTiltX = clamp((-event.values[0] / SensorManager.GRAVITY_EARTH) * 46f, -46f, 46f);
                targetTiltY = clamp((event.values[1] / SensorManager.GRAVITY_EARTH) * 30f, -30f, 30f);
            }
        }
        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        private float wrapAngle(float a) { while (a > Math.PI) a -= (float)(Math.PI*2); while (a < -Math.PI) a += (float)(Math.PI*2); return a; }
        private float clamp(float v,float min,float max){return Math.max(min,Math.min(max,v));}

        private void requestFrame() {
            if (!framePosted) { framePosted = true; Choreographer.getInstance().postFrameCallback(this); }
        }
        private void stopFrames() {
            Choreographer.getInstance().removeFrameCallback(this); framePosted = false; handler.removeCallbacksAndMessages(null);
        }
        @Override public void doFrame(long frameTimeNanos) { framePosted = false; drawFrame(); if (visible) requestFrame(); }

        private void addPulse(float x,float y,float power,int type){addPulseInternal(x,y,System.currentTimeMillis(),power,type);requestFrame();}
        private void addPulseInternal(float x,float y,long born,float power,int type){if(width>0&&height>0)synchronized(pulses){pulses.add(new Pulse(x,y,born,power,type));}}
        private void maybeAddAmbientPulse(long now){if(!visible||width<=0||height<=0||now-lastAmbientPulse<AMBIENT_PULSE_INTERVAL_MS)return;int i=ambientPulseIndex++%pulseX.length;lastAmbientPulse=now;addPulseInternal(width*pulseX[i],height*pulseY[i],now,.72f,2);}

        private void rebuildCachedRoxy(){
            if(sourceRoxy==null||width<=0||height<=0)return;
            float scale=Math.max((float)width/sourceRoxy.getWidth(),(float)height/sourceRoxy.getHeight())*1.075f;
            Bitmap b=Bitmap.createScaledBitmap(sourceRoxy,Math.max(1,Math.round(sourceRoxy.getWidth()*scale)),Math.max(1,Math.round(sourceRoxy.getHeight()*scale)),true);
            recycleCachedRoxy(); cachedRoxy=b;
        }
        private void recycleCachedRoxy(){if(cachedRoxy!=null&&cachedRoxy!=sourceRoxy&&!cachedRoxy.isRecycled())cachedRoxy.recycle();cachedRoxy=null;}

        private void drawFrame(){
            if(cachedRoxy==null)return; long now=System.currentTimeMillis(); maybeAddAmbientPulse(now);
            renderedTiltX += (targetTiltX-renderedTiltX)*.22f;
            renderedTiltY += (targetTiltY-renderedTiltY)*.22f;
            SurfaceHolder holder=getSurfaceHolder(); Canvas c=null;
            try{c=holder.lockCanvas();if(c!=null){width=c.getWidth();height=c.getHeight();drawRoxy(c);drawAmbientMana(c,now);drawPulses(c,now);}}
            finally{if(c!=null)holder.unlockCanvasAndPost(c);}
        }
        private void drawRoxy(Canvas c){c.drawColor(0xFF050A16);float left=(width-cachedRoxy.getWidth())*.5f+renderedTiltX,top=(height-cachedRoxy.getHeight())*.5f+renderedTiltY;c.drawBitmap(cachedRoxy,left,top,bitmapPaint);effectPaint.setStyle(Paint.Style.FILL);effectPaint.setColor(0x14020A1B);c.drawRect(0,0,width,height,effectPaint);}
        private void drawAmbientMana(Canvas c,long now){float t=((now-start)%16000L)/16000f;for(int i=0;i<PARTICLE_COUNT;i++){float seed=(i*.6180339f)%1f;float x=((seed+t*(.025f+(i%4)*.008f))%1f)*width+renderedTiltX*.34f;float y=height-(((i*.137f+t*(.24f+(i%3)*.055f))%1f)*height)+renderedTiltY*.22f;float shimmer=.62f+.38f*(float)Math.sin(t*6.283185f+i*.72f);int alpha=24+(int)(56*shimmer);effectPaint.setStyle(Paint.Style.FILL);effectPaint.setColor((alpha<<24)|0xA7EDFF);c.drawCircle(x,y,1.7f+(i%3),effectPaint);}}
        private void drawPulses(Canvas c,long now){synchronized(pulses){Iterator<Pulse>it=pulses.iterator();while(it.hasNext()){Pulse p=it.next();float duration=p.type==2?2.2f:1.55f,age=(now-p.born)/1000f;if(age>duration){it.remove();continue;}float progress=age/duration,eased=1f-(1f-progress)*(1f-progress);int alpha=(int)((p.type==2?105:205)*(1f-progress));float radius=(24f+eased*Math.min(width,height)*.29f)*p.power;effectPaint.setStyle(Paint.Style.STROKE);effectPaint.setStrokeWidth((p.type==2?4f:7f)*(1f-progress)+1.2f);effectPaint.setColor((alpha<<24)|(p.type==2?0x9FEAFF:0xD7F7FF));c.drawCircle(p.x,p.y,radius,effectPaint);c.drawCircle(p.x,p.y,radius*.62f,effectPaint);if(p.type==1)for(int i=0;i<6;i++){double a=i*Math.PI/3d+eased*1.2f;float sx=p.x+(float)Math.cos(a)*radius*.76f,sy=p.y+(float)Math.sin(a)*radius*.76f;effectPaint.setStyle(Paint.Style.FILL);c.drawCircle(sx,sy,4.5f*(1f-progress)+1f,effectPaint);}}}effectPaint.setStyle(Paint.Style.FILL);}
    }
    private static class Pulse{final float x,y,power;final long born;final int type;Pulse(float x,float y,long born,float power,int type){this.x=x;this.y=y;this.born=born;this.power=power;this.type=type;}}
}
