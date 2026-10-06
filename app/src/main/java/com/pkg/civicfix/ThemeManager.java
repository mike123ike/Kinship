package com.pkg.civicfix;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

public final class ThemeManager {

    private static final String PREFS_NAME =
            "kinship_theme_preferences";

    private static final String KEY_DARK_MODE =
            "dark_mode";


    private ThemeManager() {
    }


    private static SharedPreferences prefs(
            Context context
    ) {

        return context
                .getApplicationContext()
                .getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                );
    }


    public static boolean isDarkMode(
            Context context
    ) {
        return prefs(context)
                .getBoolean(
                        KEY_DARK_MODE,
                        false
                );
    }


    public static void saveChoice(
            Context context,
            boolean darkMode
    ) {

        prefs(context)
                .edit()
                .putBoolean(
                        KEY_DARK_MODE,
                        darkMode
                )
                .apply();
    }


    public static void applySavedTheme(
            Context context
    ) {

        applyMode(
                isDarkMode(context)
        );
    }


    public static void saveAndApply(
            Context context,
            boolean darkMode
    ) {

        saveChoice(
                context,
                darkMode
        );

        applyMode(
                darkMode
        );
    }


    private static void applyMode(
            boolean darkMode
    ) {

        int desiredMode =
                darkMode

                        ? AppCompatDelegate.MODE_NIGHT_YES

                        : AppCompatDelegate.MODE_NIGHT_NO;


        if (
                AppCompatDelegate
                        .getDefaultNightMode()

                        != desiredMode
        ) {

            AppCompatDelegate
                    .setDefaultNightMode(
                            desiredMode
                    );
        }
    }
}