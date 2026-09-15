package com.otakuhoarder.mushokufoldroxy;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

/** Launcher-owned Roxy surface. Rebinds the renderer whenever Samsung hands Home to another display/configuration. */
public class RoxyHomeActivity extends Activity {
    private static final String TAG="RoxyFoldHome";
    private RoxyHomeView roxyView;
    private int lastWidthDp=-1;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        Log.i(TAG,"onCreate display="+getDisplayId()+" config="+getResources().getConfiguration());
        bindRenderer(true);
    }

    private void bindRenderer(boolean handoff) {
        if(roxyView!=null) roxyView.stop();
        roxyView=new RoxyHomeView(this);
        setContentView(roxyView);
        if(handoff) roxyView.triggerDisplayHandoff();
        if(hasWindowFocus()) roxyView.start();
    }

    @Override protected void onResume(){super.onResume();Log.i(TAG,"onResume display="+getDisplayId());if(roxyView==null)bindRenderer(true);roxyView.start();}
    @Override protected void onPause(){Log.i(TAG,"onPause display="+getDisplayId());if(roxyView!=null)roxyView.stop();super.onPause();}
    @Override protected void onDestroy(){Log.i(TAG,"onDestroy display="+getDisplayId()+" changing="+isChangingConfigurations());super.onDestroy();}

    @Override public void onConfigurationChanged(Configuration c){
        super.onConfigurationChanged(c);
        int width=c.screenWidthDp;
        Log.i(TAG,"onConfigurationChanged display="+getDisplayId()+" widthDp="+width+" smallest="+c.smallestScreenWidthDp);
        boolean majorHandoff=lastWidthDp>0 && Math.abs(width-lastWidthDp)>180;
        lastWidthDp=width;
        if(majorHandoff) bindRenderer(true); else if(roxyView!=null) roxyView.onFoldConfigurationChanged();
    }

    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);Log.i(TAG,"focus="+focus+" display="+getDisplayId());if(focus&&roxyView!=null)roxyView.start();}
    @Override public void onBackPressed(){ }

    public void openLauncherSettings(){try{startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));}catch(Exception ignored){startActivity(new Intent(Settings.ACTION_SETTINGS));}}
}
