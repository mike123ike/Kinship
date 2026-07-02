package com.pkg.civicfix;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setItemActiveIndicatorEnabled(true);
        findViewById(R.id.btn_settings).setOnClickListener((View v) ->
                startActivity(new Intent(this, SettingsActivity.class))
        );

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selected;
            int id = item.getItemId();
            if (id == R.id.nav_map) {
                selected = new MapFragment();
            } else if (id == R.id.nav_events) {
                selected = new EventsFragment();
            } else if (id == R.id.nav_report) {
                selected = new ReportFragment();
            } else {
                selected = new ProfileFragment();
            }
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, selected)
                    .commitAllowingStateLoss();
            return true;
        });

        bottomNav.setSelectedItemId(R.id.nav_map);
    }
}