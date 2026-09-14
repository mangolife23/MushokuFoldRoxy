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
    private TextView deviceState;
    private TextView rotationStatus;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("roxy_control", MODE_PRIVATE);
        magicOverlay = findViewById(R.id.magicOverlay);
        deviceState = findViewById(R.id.deviceState);
        rotationStatus = findViewById(R.id.rotationStatus);
        Switch rotationSwitch = findViewById(R.id.rotationSwitch);

        findViewById(R.id.applyHome).setOnClickListener(v -> applyWallpaper(WallpaperManager.FLAG_SYSTEM, "Home screen"));
        findViewById(R.id.applyLock).setOnClickListener(v -> applyWallpaper(WallpaperManager.FLAG_LOCK, "Lock screen"));
        findViewById(R.id.applyBoth).setOnClickListener(v -> applyBoth());
        findViewById(R.id.magicPulse).setOnClickListener(v -> magicOverlay.triggerBurst());
        findViewById(R.id.liveWallpaper).setOnClickListener(v -> openLiveWallpaper());

        boolean rotation = prefs.getBoolean("rotation_enabled", false);
        rotationSwitch.setChecked(rotation);
        updateRotationText(rotation);
        rotationSwitch.setOnCheckedChangeListener((button, enabled) -> {
            prefs.edit().putBoolean("rotation_enabled", enabled).apply();
            updateRotationText(enabled);
            magicOverlay.triggerBurst();
        });

        updateDeviceState();
        magicOverlay.triggerBurst();
    }

    private void openLiveWallpaper() {
        try {
            Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
            intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    new ComponentName(this, RoxyLiveWallpaperService.class));
            startActivity(intent);
        } catch (Exception e) {
            Intent chooser = new Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER);
            startActivity(chooser);
        }
    }

    private void applyWallpaper(int flag, String target) {
        WallpaperManager manager = WallpaperManager.getInstance(this);
        try (InputStream image = getResources().openRawResource(R.drawable.roxy_wallpaper)) {
            manager.setStream(image, null, true, flag);
            magicOverlay.triggerBurst();
            Toast.makeText(this, "Roxy applied to " + target, Toast.LENGTH_SHORT).show();
        } catch (IOException | SecurityException e) {
            Toast.makeText(this, "Could not apply Roxy to " + target, Toast.LENGTH_LONG).show();
        }
    }

    private void applyBoth() {
        applyWallpaper(WallpaperManager.FLAG_SYSTEM, "Home screen");
        applyWallpaper(WallpaperManager.FLAG_LOCK, "Lock screen");
    }

    private void updateRotationText(boolean enabled) {
        rotationStatus.setText(enabled
                ? "Rotation armed • add more Roxy scenes to activate the gallery cycle"
                : "Ready for additional Roxy scenes");
    }

    private void updateDeviceState() {
        int smallest = getResources().getConfiguration().smallestScreenWidthDp;
        if (smallest >= 600) deviceState.setText("Fold open • Inner display mana mode");
        else deviceState.setText("Cover display • Compact mana mode");
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateDeviceState();
        magicOverlay.triggerBurst();
    }
}
