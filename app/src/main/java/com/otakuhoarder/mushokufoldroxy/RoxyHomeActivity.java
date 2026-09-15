package com.otakuhoarder.mushokufoldroxy;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Collections;
import java.util.List;

/** Proven Fold handoff with a launcher UI layered above the renderer. */
public class RoxyHomeActivity extends Activity {
    private static final String TAG="RoxyFoldHome";
    private RoxyHomeView roxyView;
    private FrameLayout root;
    private LinearLayout dock;
    private int lastWidthDp=-1;
    private int displayId(){Display d=getDisplay();return d!=null?d.getDisplayId():-1;}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}

    @Override protected void onCreate(Bundle state){super.onCreate(state);requestWindowFeature(Window.FEATURE_NO_TITLE);getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);lastWidthDp=getResources().getConfiguration().screenWidthDp;Log.i(TAG,"onCreate display="+displayId()+" widthDp="+lastWidthDp);bindRenderer(true);}

    private void bindRenderer(boolean handoff){
        if(roxyView!=null)roxyView.stop();
        roxyView=new RoxyHomeView(this);
        root=new FrameLayout(this);
        root.addView(roxyView,new FrameLayout.LayoutParams(-1,-1));
        setContentView(root); // preserve Build #47 lifecycle ordering
        if(handoff)roxyView.triggerDisplayHandoff();
        addLauncherLayer();
        if(hasWindowFocus())roxyView.start();
    }

    private void addLauncherLayer(){
        dock=new LinearLayout(this);dock.setOrientation(LinearLayout.VERTICAL);dock.setGravity(Gravity.CENTER);dock.setPadding(dp(7),dp(10),dp(7),dp(10));
        GradientDrawable bg=new GradientDrawable();bg.setColor(Color.argb(190,7,14,28));bg.setCornerRadius(dp(28));bg.setStroke(dp(2),Color.rgb(91,218,255));dock.setBackground(bg);dock.setElevation(dp(14));
        List<ResolveInfo> apps=getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0);Collections.sort(apps,new ResolveInfo.DisplayNameComparator(getPackageManager()));
        int count=0;for(ResolveInfo app:apps){if(app.activityInfo.packageName.equals(getPackageName()))continue;if(count++>=6)break;ImageButton icon=new ImageButton(this);icon.setImageDrawable(app.loadIcon(getPackageManager()));icon.setBackgroundColor(Color.TRANSPARENT);icon.setPadding(dp(6),dp(6),dp(6),dp(6));icon.setContentDescription(app.loadLabel(getPackageManager()));final String pkg=app.activityInfo.packageName,cls=app.activityInfo.name;icon.setOnClickListener(v->{try{startActivity(new Intent().setClassName(pkg,cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(Exception ignored){}});dock.addView(icon,new LinearLayout.LayoutParams(dp(54),dp(54)));}
        TextView marker=new TextView(this);marker.setText("ROXY\nDUO");marker.setTextColor(Color.rgb(137,232,255));marker.setTextSize(9);marker.setGravity(Gravity.CENTER);marker.setOnClickListener(v->openLauncherSettings());dock.addView(marker,new LinearLayout.LayoutParams(dp(54),dp(46)));
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(70),-2,Gravity.END|Gravity.CENTER_VERTICAL);lp.rightMargin=dp(12);root.addView(dock,lp);
    }

    @Override protected void onResume(){super.onResume();Log.i(TAG,"onResume display="+displayId());if(roxyView==null)bindRenderer(true);roxyView.start();}
    @Override protected void onPause(){Log.i(TAG,"onPause display="+displayId());if(roxyView!=null)roxyView.stop();super.onPause();}
    @Override protected void onDestroy(){Log.i(TAG,"onDestroy display="+displayId()+" changing="+isChangingConfigurations());super.onDestroy();}
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);int width=c.screenWidthDp;Log.i(TAG,"onConfigurationChanged display="+displayId()+" widthDp="+width);boolean majorHandoff=lastWidthDp>0&&Math.abs(width-lastWidthDp)>180;lastWidthDp=width;if(majorHandoff)bindRenderer(true);else if(roxyView!=null)roxyView.onFoldConfigurationChanged();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus&&roxyView!=null)roxyView.start();}
    @Override public void onBackPressed(){}
    public void openLauncherSettings(){try{startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));}catch(Exception ignored){startActivity(new Intent(Settings.ACTION_SETTINGS));}}
}
