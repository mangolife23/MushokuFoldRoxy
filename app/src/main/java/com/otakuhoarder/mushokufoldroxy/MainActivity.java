package com.otakuhoarder.mushokufoldroxy;

import android.app.Activity;
import android.app.WallpaperManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button apply = findViewById(R.id.applyWallpaper);
        apply.setOnClickListener(v -> applyRoxyWallpaper());
    }

    private void applyRoxyWallpaper() {
        WallpaperManager manager = WallpaperManager.getInstance(this);
        try (InputStream image = getResources().openRawResource(R.drawable.roxy_wallpaper)) {
            manager.setStream(image);
            Toast.makeText(this, "Roxy wallpaper applied", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Toast.makeText(this, "Could not apply wallpaper", Toast.LENGTH_LONG).show();
        }
    }
}
