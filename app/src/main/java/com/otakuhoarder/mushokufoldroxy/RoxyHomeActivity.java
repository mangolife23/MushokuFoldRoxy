package com.otakuhoarder.mushokufoldroxy;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Window;
import android.view.WindowManager;

/** Launcher-owned Roxy surface. Inspired by DuoLauncher's compact/expanded workspace split. */
public class RoxyHomeActivity extends Activity {
    private RoxyHomeView roxyView;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        roxyView = new RoxyHomeView(this);
        setContentView(roxyView);
    }

    @Override protected void onResume() { super.onResume(); roxyView.start(); }
    @Override protected void onPause() { roxyView.stop(); super.onPause(); }
    @Override public void onConfigurationChanged(Configuration c) { super.onConfigurationChanged(c); roxyView.onFoldConfigurationChanged(); }

    @Override public void onBackPressed() { /* Home owns the root surface. */ }

    public void openLauncherSettings() {
        try { startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)); }
        catch (Exception ignored) { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
    }
}
