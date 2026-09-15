package com.otakuhoarder.mushokufoldroxy;

import android.content.Context;
import android.graphics.*;
import android.hardware.*;
import android.view.*;
import java.util.Random;

/** First launcher-owned renderer: same Roxy scene survives compact/expanded Fold layout changes. */
public class RoxyHomeView extends View implements SensorEventListener, Choreographer.FrameCallback {
    private final Bitmap source;
    private Bitmap cache;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Paint mana=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SensorManager sensors;
    private Sensor rotation;
    private boolean running=false, wide=false;
    private float tx=0,ty=0,cx=0,cy=0;
    private long transitionStart=0;
    private final Random random=new Random(23);
    private final float[] px=new float[24],py=new float[24],ps=new float[24];

    public RoxyHomeView(Context c){super(c);setFocusable(true);source=BitmapFactory.decodeResource(getResources(),R.drawable.roxy_wallpaper);
        sensors=(SensorManager)c.getSystemService(Context.SENSOR_SERVICE);rotation=sensors.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);if(rotation==null)rotation=sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        for(int i=0;i<px.length;i++){px[i]=random.nextFloat();py[i]=random.nextFloat();ps[i]=1.5f+random.nextFloat()*4f;} mana.setColor(Color.argb(180,115,225,255));}

    public void start(){if(running)return;running=true;if(rotation!=null)sensors.registerListener(this,rotation,SensorManager.SENSOR_DELAY_GAME);Choreographer.getInstance().postFrameCallback(this);}
    public void stop(){running=false;sensors.unregisterListener(this);Choreographer.getInstance().removeFrameCallback(this);}
    public void onFoldConfigurationChanged(){boolean nowWide=getResources().getConfiguration().smallestScreenWidthDp>=600;if(nowWide!=wide){wide=nowWide;transitionStart=System.currentTimeMillis();rebuild();}invalidate();}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){boolean nowWide=w>0&&((float)w/getResources().getDisplayMetrics().density)>=650f;if(nowWide!=wide){wide=nowWide;transitionStart=System.currentTimeMillis();}rebuild();}
    private void rebuild(){int w=getWidth(),h=getHeight();if(w<=0||h<=0||source==null)return;float over=1.09f;float scale=Math.max(w/(float)source.getWidth(),h/(float)source.getHeight())*over;int bw=Math.max(w,Math.round(source.getWidth()*scale)),bh=Math.max(h,Math.round(source.getHeight()*scale));if(cache!=null)cache.recycle();cache=Bitmap.createScaledBitmap(source,bw,bh,true);}

    @Override protected void onDraw(Canvas c){super.onDraw(c);c.drawColor(Color.BLACK);if(cache==null)return;cx+=(tx-cx)*.22f;cy+=(ty-cy)*.22f;float focalX=wide?.54f:.50f,focalY=wide?.47f:.50f;float left=(getWidth()-cache.getWidth())*.5f+(focalX-.5f)*(getWidth()-cache.getWidth())+cx;float top=(getHeight()-cache.getHeight())*.5f+(focalY-.5f)*(getHeight()-cache.getHeight())+cy;c.drawBitmap(cache,left,top,paint);
        for(int i=0;i<px.length;i++){float x=px[i]*getWidth()+cx*.25f,y=py[i]*getHeight()+cy*.18f;mana.setAlpha(70+(i%5)*20);c.drawCircle(x,y,ps[i],mana);}
        long age=System.currentTimeMillis()-transitionStart;if(transitionStart>0&&age<850){float p=age/850f;float r=(float)Math.sqrt(getWidth()*getWidth()+getHeight()*getHeight())*.62f*p;mana.setStyle(Paint.Style.STROKE);mana.setStrokeWidth(3f+8f*(1-p));mana.setAlpha((int)(220*(1-p)));c.drawCircle(getWidth()/2f,getHeight()/2f,r,mana);mana.setStyle(Paint.Style.FILL);mana.setAlpha((int)(80*(1-p)));c.drawCircle(getWidth()/2f,getHeight()/2f,r*.38f,mana);}}

    @Override public void doFrame(long t){if(!running)return;invalidate();Choreographer.getInstance().postFrameCallback(this);}
    @Override public void onSensorChanged(SensorEvent e){float[] rm=new float[9],o=new float[3];SensorManager.getRotationMatrixFromVector(rm,e.values);SensorManager.getOrientation(rm,o);tx=Math.max(-52,Math.min(52,o[2]*145f));ty=Math.max(-38,Math.min(38,-o[1]*115f));}
    @Override public void onAccuracyChanged(Sensor s,int a){}
    @Override protected void onDetachedFromWindow(){stop();if(cache!=null){cache.recycle();cache=null;}super.onDetachedFromWindow();}
}
