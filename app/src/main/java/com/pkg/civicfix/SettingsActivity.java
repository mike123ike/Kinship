package com.pkg.civicfix;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.pkg.civicfix.base.CivicFixActivity;

public class SettingsActivity
        extends CivicFixActivity {

    private static final String SUPPORT_EMAIL =
            "civicfixtestadmin01@gmail.com";


    private FirebaseAuth auth;

    private FirebaseFirestore db;

    private String uid;


    private View btnLogout;

    private View btnPrivacySafety;

    private View btnTerms;

    private View btnSupport;

    private View btnCredits;


    private Toolbar toolbar;

    private SwitchCompat toggleDarkMode;


    @Override
    protected void onCreate(
            @Nullable Bundle savedInstanceState
    ) {

        super.onCreate(
                savedInstanceState
        );


        auth =
                FirebaseAuth.getInstance();

        db =
                FirebaseFirestore.getInstance();


        setContentView(
                R.layout.settings_activity
        );


        toolbar =
                findViewById(
                        R.id.toolbar
                );

        btnLogout =
                findViewById(
                        R.id.btn_logout
                );

        btnPrivacySafety =
                findViewById(
                        R.id.btn_privacy_safety
                );

        btnTerms =
                findViewById(
                        R.id.btn_terms
                );

        btnSupport =
                findViewById(
                        R.id.btn_support
                );

        btnCredits =
                findViewById(
                        R.id.btn_credits
                );

        toggleDarkMode =
                findViewById(
                        R.id.toggle_dark_mode
                );


        setSupportActionBar(
                toolbar
        );


        uid =
                auth.getUid();


        if (
                uid == null
        ) {

            showToast(
                    "Session expired, login again"
            );

            goToActivity(
                    LoginActivity.class,
                    true
            );

            return;
        }


        // theme
        toggleDarkMode.setChecked(
                ThemeManager.isDarkMode(
                        this
                )
        );


        toggleDarkMode
                .setOnCheckedChangeListener(
                        (button, isChecked) -> {
                            ThemeManager.saveChoice(
                                    this,
                                    isChecked
                            );

                            db.collection("users")
                                    .document(uid)
                                    .update(
                                            "darkMode",
                                            isChecked
                                    )
                                    .addOnFailureListener(
                                            error ->

                                                    showToast(
                                                            "Theme saved on this device, but cloud sync failed"
                                                    )
                                    );

                            ThemeManager
                                    .applySavedTheme(
                                            this
                                    );
                        }
                );


        btnPrivacySafety.setOnClickListener(
                v -> {

                    Intent intent =
                            new Intent(
                                    this,
                                    PrivacySafetyActivity.class
                            );

                    startActivity(
                            intent
                    );
                }
        );

        btnTerms.setOnClickListener(
                v ->

                        openLegalPage(
                                LegalActivity.PAGE_TERMS
                        )
        );

        btnSupport.setOnClickListener(
                v ->

                        openSupportEmail()
        );

        btnCredits.setOnClickListener(
                v ->

                        showCredits()
        );


        // logout
        btnLogout.setOnClickListener(
                (View v) -> {

                    btnLogout.setEnabled(
                            false
                    );

                    auth.signOut();

                    showToast(
                            "Logging out"
                    );

                    goToActivity(
                            LoginActivity.class,
                            true
                    );
                }
        );


        // toolbar
        toolbar.setNavigationOnClickListener(
                v -> finish()
        );


        if (
                toolbar.getNavigationIcon()
                        != null
        ) {

            toolbar.getNavigationIcon()
                    .setTintList(
                            ColorStateList.valueOf(
                                    ContextCompat.getColor(
                                            this,
                                            R.color.tertiary
                                    )
                            )
                    );
        }
    }


    private void openLegalPage(
            String page
    ) {

        Intent intent =
                new Intent(
                        this,
                        LegalActivity.class
                );


        intent.putExtra(
                LegalActivity.EXTRA_PAGE,
                page
        );


        startActivity(
                intent
        );
    }


    private void openSupportEmail() {

        String subject =
                "Kinship Support";


        String body =
                "Please describe what you need help with:\n\n";


        Uri uri =
                Uri.parse(
                        "mailto:"
                                + SUPPORT_EMAIL

                                + "?subject="
                                + Uri.encode(
                                subject
                        )

                                + "&body="
                                + Uri.encode(
                                body
                        )
                );


        Intent intent =
                new Intent(
                        Intent.ACTION_SENDTO,
                        uri
                );


        try {

            startActivity(
                    intent
            );

        } catch (
                ActivityNotFoundException error
        ) {

            showToast(
                    "No email app is available"
            );
        }
    }


    private void showCredits() {

        String credits =
                "Kinship is built using:\n\n"

                        + "• Firebase Authentication and Firestore\n"
                        + "• Google Maps Platform\n"
                        + "• Cloudinary\n"
                        + "• NewsData.io\n"
                        + "• Android and Material Components\n\n"

                        + "Kinship\n"
                        + "© 2026";


        new AlertDialog.Builder(
                this
        )
                .setTitle(
                        "Credits"
                )
                .setMessage(
                        credits
                )
                .setPositiveButton(
                        "Close",
                        null
                )
                .show();
    }
}