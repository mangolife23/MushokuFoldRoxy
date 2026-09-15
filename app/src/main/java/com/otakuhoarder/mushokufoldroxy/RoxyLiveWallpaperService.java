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
import android.service.wallpaper.WallpaperService;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class RoxyLiveWallpaperService extends WallpaperService {
 @Override public Engine onCreateEngine(){return new RoxyEngine();}
 private class RoxyEngine extends Engine implements SensorEventListener,Choreographer.FrameCallback{
  private static final long PULSE_MS=9000L,SCENE_MS=30000L,FADE_MS=1800L;
  private static final int PARTICLES=22;
  private final int[] scenes={R.drawable.roxy_wallpaper,R.drawable.roxy_scene_02,R.drawable.roxy_scene_03,R.drawable.roxy_scene_04,R.drawable.roxy_scene_05};
  private final Paint bitmapPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG),effectPaint=new Paint(Paint.ANTI_ALIAS_FLAG);
  private final List<Pulse> pulses=new ArrayList<>(); private final float[] rm=new float[9],ori=new float[3];
  private Bitmap currentSource,nextSource,currentCache,nextCache; private int sceneIndex;
  private SensorManager sm; private Sensor rv,acc; private boolean visible,posted,transitioning;
  private int width,height,ambientIndex; private long start=System.currentTimeMillis(),lastPulse,lastScene,transitionStart;
  private float neutralPitch=Float.NaN,neutralRoll=Float.NaN,targetX,targetY,renderX,renderY;
  private final float[] px={.50f,.34f,.67f,.51f},py={.30f,.62f,.48f,.74f};
  @Override public void onCreate(SurfaceHolder h){super.onCreate(h);setTouchEventsEnabled(true);currentSource=BitmapFactory.decodeResource(getResources(),scenes[0]);sm=(SensorManager)getSystemService(SENSOR_SERVICE);if(sm!=null){rv=sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);if(rv==null)rv=sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);acc=sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);}}
  @Override public void onDestroy(){stop();unregister();recycle(currentCache);recycle(nextCache);recycle(currentSource);recycle(nextSource);super.onDestroy();}
  @Override public void onVisibilityChanged(boolean v){visible=v;if(v){neutralPitch=neutralRoll=Float.NaN;register();long n=System.currentTimeMillis();lastPulse=n;if(lastScene==0)lastScene=n;addPulse(width*.5f,height*.38f,n,1.15f,1);request();}else{stop();unregister();}}
  @Override public void onSurfaceChanged(SurfaceHolder h,int f,int w,int he){width=w;height=he;super.onSurfaceChanged(h,f,w,he);rebuild();request();}
  @Override public void onSurfaceDestroyed(SurfaceHolder h){visible=false;stop();unregister();super.onSurfaceDestroyed(h);}
  @Override public void onTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN)addPulse(e.getX(),e.getY(),System.currentTimeMillis(),1f,0);super.onTouchEvent(e);}
  @Override public Bundle onCommand(String a,int x,int y,int z,Bundle b,boolean r){if(WallpaperManager.COMMAND_TAP.equals(a)||WallpaperManager.COMMAND_SECONDARY_TAP.equals(a))addPulse(x>=0?x:width*.5f,y>=0?y:height*.5f,System.currentTimeMillis(),1.15f,1);return super.onCommand(a,x,y,z,b,r);}
  private void register(){if(sm==null)return;if(rv!=null)sm.registerListener(this,rv,16666);else if(acc!=null)sm.registerListener(this,acc,20000);}
  private void unregister(){if(sm!=null)sm.unregisterListener(this);}
  @Override public void onSensorChanged(SensorEvent e){if(e.sensor==rv){SensorManager.getRotationMatrixFromVector(rm,e.values);SensorManager.getOrientation(rm,ori);float p=ori[1],r=ori[2];if(Float.isNaN(neutralPitch)){neutralPitch=p;neutralRoll=r;}targetX=clamp(wrap(r-neutralRoll)*145f,-52,52);targetY=clamp(-wrap(p-neutralPitch)*115f,-38,38);}else if(e.sensor==acc){targetX=clamp((-e.values[0]/SensorManager.GRAVITY_EARTH)*46,-46,46);targetY=clamp((e.values[1]/SensorManager.GRAVITY_EARTH)*30,-30,30);}}
  @Override public void onAccuracyChanged(Sensor s,int a){}
  private float wrap(float a){while(a>Math.PI)a-=(float)(Math.PI*2);while(a<-Math.PI)a+=(float)(Math.PI*2);return a;} private float clamp(float v,float a,float b){return Math.max(a,Math.min(b,v));}
  private void request(){if(!posted){posted=true;Choreographer.getInstance().postFrameCallback(this);}} private void stop(){Choreographer.getInstance().removeFrameCallback(this);posted=false;}
  @Override public void doFrame(long n){posted=false;draw();if(visible)request();}
  private void maybeScene(long n){if(!transitioning&&n-lastScene>=SCENE_MS){int ni=(sceneIndex+1)%scenes.length;nextSource=BitmapFactory.decodeResource(getResources(),scenes[ni]);nextCache=scale(nextSource);if(nextCache!=null){transitioning=true;transitionStart=n;addPulse(width*.5f,height*.5f,n,1.5f,1);}else recycle(nextSource);}}
  private void finishScene(long n){recycle(currentCache);recycle(currentSource);currentCache=nextCache;currentSource=nextSource;nextCache=nextSource=null;sceneIndex=(sceneIndex+1)%scenes.length;transitioning=false;lastScene=n;}
  private Bitmap scale(Bitmap s){if(s==null||width<=0||height<=0)return null;float z=Math.max((float)width/s.getWidth(),(float)height/s.getHeight())*1.075f;return Bitmap.createScaledBitmap(s,Math.max(1,Math.round(s.getWidth()*z)),Math.max(1,Math.round(s.getHeight()*z)),true);}
  private void rebuild(){Bitmap b=scale(currentSource);recycle(currentCache);currentCache=b;if(transitioning){Bitmap nb=scale(nextSource);recycle(nextCache);nextCache=nb;}}
  private void recycle(Bitmap b){if(b!=null&&!b.isRecycled())b.recycle();}
  private void addPulse(float x,float y,long born,float power,int type){if(width>0&&height>0)synchronized(pulses){pulses.add(new Pulse(x,y,born,power,type));}request();}
  private void ambient(long n){if(n-lastPulse<PULSE_MS)return;int i=ambientIndex++%px.length;lastPulse=n;addPulse(width*px[i],height*py[i],n,.72f,2);}
  private void draw(){if(currentCache==null)return;long n=System.currentTimeMillis();maybeScene(n);ambient(n);renderX+=(targetX-renderX)*.22f;renderY+=(targetY-renderY)*.22f;Canvas c=null;try{c=getSurfaceHolder().lockCanvas();if(c!=null){width=c.getWidth();height=c.getHeight();c.drawColor(0xFF050A16);drawBitmap(c,currentCache,255);if(transitioning&&nextCache!=null){float p=Math.min(1f,(n-transitionStart)/(float)FADE_MS);float eased=p*p*(3f-2f*p);drawBitmap(c,nextCache,(int)(255*eased));if(p>=1f)finishScene(n);}effectPaint.setStyle(Paint.Style.FILL);effectPaint.setColor(0x14020A1B);c.drawRect(0,0,width,height,effectPaint);mana(c,n);drawPulses(c,n);}}finally{if(c!=null)getSurfaceHolder().unlockCanvasAndPost(c);}}
  private void drawBitmap(Canvas c,Bitmap b,int alpha){bitmapPaint.setAlpha(alpha);float l=(width-b.getWidth())*.5f+renderX,t=(height-b.getHeight())*.5f+renderY;c.drawBitmap(b,l,t,bitmapPaint);bitmapPaint.setAlpha(255);}
  private void mana(Canvas c,long n){float t=((n-start)%16000L)/16000f;for(int i=0;i<PARTICLES;i++){float seed=(i*.6180339f)%1f,x=((seed+t*(.025f+(i%4)*.008f))%1f)*width+renderX*.34f,y=height-(((i*.137f+t*(.24f+(i%3)*.055f))%1f)*height)+renderY*.22f,sh=.62f+.38f*(float)Math.sin(t*6.283185f+i*.72f);int a=24+(int)(56*sh);effectPaint.setStyle(Paint.Style.FILL);effectPaint.setColor((a<<24)|0xA7EDFF);c.drawCircle(x,y,1.7f+(i%3),effectPaint);}}
  private void drawPulses(Canvas c,long n){synchronized(pulses){Iterator<Pulse>it=pulses.iterator();while(it.hasNext()){Pulse p=it.next();float d=p.type==2?2.2f:1.55f,age=(n-p.born)/1000f;if(age>d){it.remove();continue;}float q=age/d,e=1-(1-q)*(1-q);int a=(int)((p.type==2?105:205)*(1-q));float rad=(24+e*Math.min(width,height)*.29f)*p.power;effectPaint.setStyle(Paint.Style.STROKE);effectPaint.setStrokeWidth((p.type==2?4:7)*(1-q)+1.2f);effectPaint.setColor((a<<24)|(p.type==2?0x9FEAFF:0xD7F7FF));c.drawCircle(p.x,p.y,rad,effectPaint);c.drawCircle(p.x,p.y,rad*.62f,effectPaint);}}effectPaint.setStyle(Paint.Style.FILL);}
 }
 private static class Pulse{final float x,y,power;final long born;final int type;Pulse(float x,float y,long b,float p,int t){this.x=x;this.y=y;born=b;power=p;type=t;}}
}
