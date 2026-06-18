package com.pkg.civicfix;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.appcompat.widget.SwitchCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.pkg.civicfix.base.CivicFixActivity;
import com.pkg.civicfix.model.User;


public class SettingsActivity extends CivicFixActivity {
    private FirebaseAuth auth;
    private String uid;
    private FirebaseFirestore db;
    private View btnLogout;

    private Toolbar toolbar;
    private SwitchCompat toggleDarkMode;
    private SwitchCompat toggleAnonymousReporting;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        setContentView(R.layout.settings_activity);
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        btnLogout = findViewById(R.id.btn_logout);
        toggleDarkMode = findViewById(R.id.toggle_dark_mode);
        toggleAnonymousReporting = findViewById(R.id.toggle_anonymous_reporting);
        uid = auth.getUid();
        if (uid == null) {
            showToast("Session expired, login again");
            goToActivity(LoginActivity.class, true);
            return;
        }
        btnLogout.setOnClickListener((View v) -> {
            btnLogout.setEnabled(false);
            auth.signOut();
            showToast("Logging out");
            goToActivity(LoginActivity.class, true);
        });
        toolbar.setNavigationOnClickListener((View v) -> {
            finish();
        });
        db.collection("users").document(uid).get()
                .addOnSuccessListener(this, (DocumentSnapshot s) -> {
                    if (s.exists()) {
                        User user = s.toObject(User.class);
                        initializeWidgetStates(user);
                    } else {
                        showToast("Profile not found, login again");
                        goToActivity(LoginActivity.class, true);
                    }
                });
    }

    private void initializeWidgetStates(User user) {
        toggleDarkMode.setChecked(user.isDarkMode());
        toggleAnonymousReporting.setChecked(user.isAnonymousReporting());
        toggleDarkMode.setOnCheckedChangeListener((CompoundButton b, boolean isChecked) -> {
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
            db.collection("users").document(uid)
                    .update("darkMode", isChecked)
                    .addOnFailureListener(this, (Exception e) -> {
                        showToast("Failed to sync dark mode setting with database");
                    });
        });
        toggleAnonymousReporting.setOnCheckedChangeListener((CompoundButton b, boolean isChecked) -> {
            db.collection("users").document(uid)
                    .update("anonymousReporting", isChecked)
                    .addOnFailureListener(this, (Exception e) -> {
                        showToast("Failed to sync anonymous reporting setting with database");
                    });
        });
    }
}