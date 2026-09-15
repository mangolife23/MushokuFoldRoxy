package com.otakuhoarder.mushokufoldroxy;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends Activity {
    private MagicOverlayView magicOverlay;
    private TextView deviceState, rotationStatus, versionText;
    private SharedPreferences prefs;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_main);
        prefs=getSharedPreferences("roxy_control",MODE_PRIVATE);
        magicOverlay=findViewById(R.id.magicOverlay); deviceState=findViewById(R.id.deviceState);
        rotationStatus=findViewById(R.id.rotationStatus); versionText=findViewById(R.id.versionText);
        Switch rotationSwitch=findViewById(R.id.rotationSwitch);
        findViewById(R.id.applyHome).setOnClickListener(v->applyWallpaper(WallpaperManager.FLAG_SYSTEM,"Home screen"));
        findViewById(R.id.applyLock).setOnClickListener(v->applyWallpaper(WallpaperManager.FLAG_LOCK,"Lock screen"));
        findViewById(R.id.applyBoth).setOnClickListener(v->applyBoth());
        findViewById(R.id.magicPulse).setOnClickListener(v->magicOverlay.triggerBurst());
        findViewById(R.id.liveWallpaper).setOnClickListener(v->openLiveWallpaper());
        boolean rotation=prefs.getBoolean("rotation_enabled",true); rotationSwitch.setChecked(rotation); updateRotationText(rotation);
        rotationSwitch.setOnCheckedChangeListener((button,enabled)->{prefs.edit().putBoolean("rotation_enabled",enabled).apply();updateRotationText(enabled);magicOverlay.triggerBurst();});
        versionText.setText("v"+BuildConfig.VERSION_NAME+" • Roxy Live Mana Edition"); updateDeviceState(); magicOverlay.triggerBurst();
    }
    private void openLiveWallpaper(){try{Intent i=new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);i.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,new ComponentName(this,RoxyLiveWallpaperService.class));startActivity(i);}catch(Exception e){startActivity(new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));}}
    private void applyWallpaper(int flag,String target){WallpaperManager m=WallpaperManager.getInstance(this);try(InputStream image=getResources().openRawResource(R.drawable.roxy_wallpaper)){m.setStream(image,null,true,flag);magicOverlay.triggerBurst();Toast.makeText(this,"Roxy applied to "+target,Toast.LENGTH_SHORT).show();}catch(IOException|SecurityException e){Toast.makeText(this,"Could not apply Roxy to "+target,Toast.LENGTH_LONG).show();}}
    private void applyBoth(){applyWallpaper(WallpaperManager.FLAG_SYSTEM,"Home screen");applyWallpaper(WallpaperManager.FLAG_LOCK,"Lock screen");}
    private void updateRotationText(boolean enabled){rotationStatus.setText(enabled?"5-scene gallery active • magical transitions enabled":"Rotation paused • current Roxy scene stays active");}
    private void updateDeviceState(){int s=getResources().getConfiguration().smallestScreenWidthDp;deviceState.setText(s>=600?"Fold open • Inner display mana mode":"Cover display • Compact mana mode");}
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);updateDeviceState();magicOverlay.triggerBurst();}
}
