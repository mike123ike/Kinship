package com.pkg.civicfix;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.pkg.civicfix.base.CivicFixActivity;

public class PrivacySafetyActivity
        extends CivicFixActivity {

    private FirebaseAuth auth;

    private FirebaseFirestore db;

    private String uid;

    private Toolbar toolbar;

    private SwitchCompat toggleAnonymousReporting;

    private TextView tvPermissionStatus;

    private View btnAppPermissions;

    private View btnCommunitySafety;

    private boolean suppressAnonymousListener =
            false;


    @Override
    protected void onCreate(
            @Nullable Bundle savedInstanceState
    ) {

        super.onCreate(
                savedInstanceState
        );


        setContentView(
                R.layout.privacy_safety_activity
        );


        auth =
                FirebaseAuth.getInstance();

        db =
                FirebaseFirestore.getInstance();

        uid =
                auth.getUid();


        toolbar =
                findViewById(
                        R.id.toolbar
                );

        toggleAnonymousReporting =
                findViewById(
                        R.id.toggle_anonymous_reporting
                );

        tvPermissionStatus =
                findViewById(
                        R.id.tv_permission_status
                );

        btnAppPermissions =
                findViewById(
                        R.id.btn_app_permissions
                );

        btnCommunitySafety =
                findViewById(
                        R.id.btn_community_safety
                );


        setSupportActionBar(
                toolbar
        );


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


        // anonymous reporting
        toggleAnonymousReporting.setEnabled(
                false
        );


        toggleAnonymousReporting
                .setOnCheckedChangeListener(
                        (button, isChecked) -> {

                            if (
                                    suppressAnonymousListener
                            ) {
                                return;
                            }


                            saveAnonymousReporting(
                                    isChecked
                            );
                        }
                );


        loadAnonymousReporting();


        // permissions
        btnAppPermissions.setOnClickListener(
                v -> openAppSettings()
        );


        // community safety
        btnCommunitySafety.setOnClickListener(
                v -> showCommunitySafetyDialog()
        );


        updatePermissionStatus();
    }


    @Override
    protected void onResume() {

        super.onResume();


        if (
                tvPermissionStatus != null
        ) {

            updatePermissionStatus();
        }
    }


    // anonymous reporting
    private void loadAnonymousReporting() {

        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(
                        document -> {

                            boolean enabled =
                                    Boolean.TRUE.equals(
                                            document.getBoolean(
                                                    "anonymousReporting"
                                            )
                                    );


                            suppressAnonymousListener =
                                    true;


                            toggleAnonymousReporting
                                    .setChecked(
                                            enabled
                                    );


                            suppressAnonymousListener =
                                    false;


                            toggleAnonymousReporting
                                    .setEnabled(
                                            true
                                    );
                        }
                )
                .addOnFailureListener(
                        error -> {

                            toggleAnonymousReporting
                                    .setEnabled(
                                            true
                                    );


                            showToast(
                                    "Could not load privacy setting"
                            );
                        }
                );
    }


    private void saveAnonymousReporting(
            boolean enabled
    ) {

        final boolean previousValue =
                !enabled;


        toggleAnonymousReporting.setEnabled(
                false
        );


        db.collection("users")
                .document(uid)
                .update(
                        "anonymousReporting",
                        enabled
                )
                .addOnSuccessListener(
                        ignored ->

                                toggleAnonymousReporting
                                        .setEnabled(
                                                true
                                        )
                )
                .addOnFailureListener(
                        error -> {

                            suppressAnonymousListener =
                                    true;


                            toggleAnonymousReporting
                                    .setChecked(
                                            previousValue
                                    );


                            suppressAnonymousListener =
                                    false;


                            toggleAnonymousReporting
                                    .setEnabled(
                                            true
                                    );


                            showToast(
                                    "Could not save anonymous reporting setting"
                            );
                        }
                );
    }


    // permissions
    private void updatePermissionStatus() {

        boolean locationAllowed =
                ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                )
                        == PackageManager.PERMISSION_GRANTED;


        boolean cameraAllowed =
                ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.CAMERA
                )
                        == PackageManager.PERMISSION_GRANTED;


        String locationText =
                locationAllowed
                        ? "Location: allowed"
                        : "Location: not allowed";


        String cameraText =
                cameraAllowed
                        ? "Camera: allowed"
                        : "Camera: not allowed";


        tvPermissionStatus.setText(
                locationText
                        + "  •  "
                        + cameraText
        );
    }


    private void openAppSettings() {

        Intent intent =
                new Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                );


        intent.setData(
                Uri.fromParts(
                        "package",
                        getPackageName(),
                        null
                )
        );


        startActivity(
                intent
        );
    }


    // community safety
    private void showCommunitySafetyDialog() {

        new AlertDialog.Builder(
                this
        )
                .setTitle(
                        "Community Safety"
                )
                .setMessage(
                        getString(
                                R.string.community_safety_body
                        )
                )
                .setPositiveButton(
                        "Got it",
                        null
                )
                .show();
    }
}