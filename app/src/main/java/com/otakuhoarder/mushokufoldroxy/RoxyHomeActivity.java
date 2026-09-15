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
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Collections;
import java.util.List;

public class RoxyHomeActivity extends Activity {
    private static final String TAG="RoxyFoldHome";
    private RoxyHomeView roxyView;
    private FrameLayout root;
    private int lastWidthDp=-1;
    private int displayId(){Display d=getDisplay();return d!=null?d.getDisplayId():-1;}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}

    @Override protected void onCreate(Bundle state){super.onCreate(state);requestWindowFeature(Window.FEATURE_NO_TITLE);getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);lastWidthDp=getResources().getConfiguration().screenWidthDp;bindRenderer(true);}

    private void bindRenderer(boolean handoff){
        if(roxyView!=null)roxyView.stop();
        roxyView=new RoxyHomeView(this);
        root=new FrameLayout(this);
        root.setClipChildren(false);root.setClipToPadding(false);
        root.addView(roxyView,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        if(handoff)roxyView.triggerDisplayHandoff();
        root.post(this::addLauncherLayer);
        if(hasWindowFocus())roxyView.start();
    }

    private void addLauncherLayer(){
        if(root==null)return;
        LinearLayout dock=new LinearLayout(this);dock.setOrientation(LinearLayout.VERTICAL);dock.setGravity(Gravity.CENTER_HORIZONTAL);dock.setPadding(dp(8),dp(12),dp(8),dp(12));dock.setClickable(true);dock.setFocusable(true);
        GradientDrawable bg=new GradientDrawable();bg.setColor(Color.argb(235,4,10,24));bg.setCornerRadius(dp(26));bg.setStroke(dp(3),Color.rgb(74,224,255));dock.setBackground(bg);dock.setElevation(dp(30));
        TextView title=new TextView(this);title.setText("ROXY\nDUO");title.setTextColor(Color.WHITE);title.setTextSize(11);title.setGravity(Gravity.CENTER);title.setBackgroundColor(Color.rgb(0,135,190));dock.addView(title,new LinearLayout.LayoutParams(dp(62),dp(58)));
        List<ResolveInfo> apps=getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0);Collections.sort(apps,new ResolveInfo.DisplayNameComparator(getPackageManager()));
        int count=0;for(ResolveInfo app:apps){if(app.activityInfo.packageName.equals(getPackageName()))continue;if(count++>=5)break;ImageButton icon=new ImageButton(this);icon.setImageDrawable(app.loadIcon(getPackageManager()));icon.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);icon.setBackgroundColor(Color.TRANSPARENT);icon.setPadding(dp(7),dp(7),dp(7),dp(7));final String pkg=app.activityInfo.packageName,cls=app.activityInfo.name;icon.setOnClickListener(v->{try{startActivity(new Intent().setClassName(pkg,cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}catch(Exception ignored){}});dock.addView(icon,new LinearLayout.LayoutParams(dp(62),dp(62)));}
        TextView home=new TextView(this);home.setText("HOME");home.setTextColor(Color.rgb(137,232,255));home.setTextSize(10);home.setGravity(Gravity.CENTER);home.setOnClickListener(v->openLauncherSettings());dock.addView(home,new LinearLayout.LayoutParams(dp(62),dp(48)));
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(82),ViewGroup.LayoutParams.WRAP_CONTENT,Gravity.RIGHT|Gravity.CENTER_VERTICAL);lp.rightMargin=dp(8);root.addView(dock,lp);dock.bringToFront();dock.setVisibility(LinearLayout.VISIBLE);dock.setAlpha(1f);dock.setTranslationZ(dp(40));Log.i(TAG,"dock attached children="+dock.getChildCount()+" display="+displayId());
    }

    @Override protected void onResume(){super.onResume();if(roxyView==null)bindRenderer(true);roxyView.start();}
    @Override protected void onPause(){if(roxyView!=null)roxyView.stop();super.onPause();}
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);int width=c.screenWidthDp;boolean major=lastWidthDp>0&&Math.abs(width-lastWidthDp)>180;lastWidthDp=width;if(major)bindRenderer(true);else if(roxyView!=null)roxyView.onFoldConfigurationChanged();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus&&roxyView!=null)roxyView.start();}
    @Override public void onBackPressed(){}
    public void openLauncherSettings(){try{startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));}catch(Exception ignored){startActivity(new Intent(Settings.ACTION_SETTINGS));}}
}
