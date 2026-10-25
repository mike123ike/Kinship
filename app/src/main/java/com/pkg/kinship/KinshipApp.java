package com.pkg.kinship;

import android.app.Application;

import com.cloudinary.android.MediaManager;

import java.util.HashMap;
import java.util.Map;

public class KinshipApp extends Application {

    @Override
    public void onCreate() {

        super.onCreate();

        ThemeManager.applySavedTheme(
                this
        );


        Map<String, String> config =
                new HashMap<>();


        config.put(
                "cloud_name",
                BuildConfig.CLOUD_NAME
        );


        MediaManager.init(
                this,
                config
        );
    }
}