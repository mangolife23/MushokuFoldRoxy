package com.otakuhoarder.mushokufoldroxy;

import android.content.Context;
import android.graphics.*;
import android.hardware.*;
import android.view.*;
import java.util.Random;

/** Launcher-owned renderer with process-level visual continuity across cover/inner activity recreation. */
public class RoxyHomeView extends View implements SensorEventListener, Choreographer.FrameCallback {
    private static float savedX=0,savedY=0;
    private static long lastHandoff=0;
    private final Bitmap source; private Bitmap cache;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG),mana=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SensorManager sensors; private Sensor rotation; private boolean running=false,wide=false;
    private float tx=savedX,ty=savedY,cx=savedX,cy=savedY; private long transitionStart=0;
    private final Random random=new Random(23); private final float[] px=new float[24],py=new float[24],ps=new float[24];

    public RoxyHomeView(Context c){super(c);setFocusable(true);setKeepScreenOn(false);source=BitmapFactory.decodeResource(getResources(),R.drawable.roxy_wallpaper);
        sensors=(SensorManager)c.getSystemService(Context.SENSOR_SERVICE);rotation=sensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);if(rotation==null)rotation=sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        for(int i=0;i<px.length;i++){px[i]=random.nextFloat();py[i]=random.nextFloat();ps[i]=1.5f+random.nextFloat()*4f;}mana.setColor(Color.argb(180,115,225,255));}
    public void triggerDisplayHandoff(){transitionStart=System.currentTimeMillis();lastHandoff=transitionStart;invalidate();}
    public void start(){if(running)return;running=true;if(rotation!=null)sensors.registerListener(this,rotation,SensorManager.SENSOR_DELAY_GAME);Choreographer.getInstance().postFrameCallback(this);}
    public void stop(){savedX=cx;savedY=cy;running=false;sensors.unregisterListener(this);Choreographer.getInstance().removeFrameCallback(this);}
    public void onFoldConfigurationChanged(){boolean n=getResources().getConfiguration().screenWidthDp>=650;if(n!=wide){wide=n;triggerDisplayHandoff();rebuild();}invalidate();}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){boolean n=w>0&&w/getResources().getDisplayMetrics().density>=650f;if(n!=wide){wide=n;triggerDisplayHandoff();}rebuild();}
    private void rebuild(){int w=getWidth(),h=getHeight();if(w<=0||h<=0||source==null)return;float scale=Math.max(w/(float)source.getWidth(),h/(float)source.getHeight())*1.09f;int bw=Math.max(w,Math.round(source.getWidth()*scale)),bh=Math.max(h,Math.round(source.getHeight()*scale));if(cache!=null)cache.recycle();cache=Bitmap.createScaledBitmap(source,bw,bh,true);}
    @Override protected void onDraw(Canvas c){super.onDraw(c);c.drawColor(Color.BLACK);if(cache==null)return;cx+=(tx-cx)*.22f;cy+=(ty-cy)*.22f;float fx=wide?.54f:.50f,fy=wide?.47f:.50f;float left=(getWidth()-cache.getWidth())*.5f+(fx-.5f)*(getWidth()-cache.getWidth())+cx,top=(getHeight()-cache.getHeight())*.5f+(fy-.5f)*(getHeight()-cache.getHeight())+cy;c.drawBitmap(cache,left,top,paint);
        for(int i=0;i<px.length;i++){mana.setStyle(Paint.Style.FILL);mana.setAlpha(70+(i%5)*20);c.drawCircle(px[i]*getWidth()+cx*.25f,py[i]*getHeight()+cy*.18f,ps[i],mana);}long age=System.currentTimeMillis()-transitionStart;if(transitionStart>0&&age<1000){float p=age/1000f,r=(float)Math.hypot(getWidth(),getHeight())*.68f*p;mana.setStyle(Paint.Style.STROKE);mana.setStrokeWidth(4f+10f*(1-p));mana.setAlpha((int)(235*(1-p)));c.drawCircle(getWidth()/2f,getHeight()/2f,r,mana);mana.setStyle(Paint.Style.FILL);mana.setAlpha((int)(95*(1-p)));c.drawCircle(getWidth()/2f,getHeight()/2f,r*.42f,mana);}}
    @Override public void doFrame(long t){if(!running)return;invalidate();Choreographer.getInstance().postFrameCallback(this);}
    @Override public void onSensorChanged(SensorEvent e){float[] rm=new float[9],o=new float[3];SensorManager.getRotationMatrixFromVector(rm,e.values);SensorManager.getOrientation(rm,o);tx=Math.max(-52,Math.min(52,o[2]*145f));ty=Math.max(-38,Math.min(38,-o[1]*115f));savedX=tx;savedY=ty;}
    @Override public void onAccuracyChanged(Sensor s,int a){}
    @Override protected void onDetachedFromWindow(){stop();if(cache!=null){cache.recycle();cache=null;}super.onDetachedFromWindow();}
}
