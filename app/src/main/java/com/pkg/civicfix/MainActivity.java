package com.pkg.civicfix;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private View topBar;
    private BottomNavigationView bottomNav;

    // Event whose popup should be restored after leaving photos/comments.
    private String pendingPopupEventId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        topBar = findViewById(R.id.top_bar);
        bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setItemActiveIndicatorEnabled(true);

        findViewById(R.id.btn_settings).setOnClickListener((View v) ->
                startActivity(new Intent(this, SettingsActivity.class))
        );

        bottomNav.setOnItemSelectedListener(item -> {
            pendingPopupEventId = null;

            //removes any remaining photos
            getSupportFragmentManager().popBackStack(
                    null,
                    FragmentManager.POP_BACK_STACK_INCLUSIVE
            );

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
                    .replace(
                            R.id.fragment_container,
                            selected
                    )
                    .commitAllowingStateLoss();

            return true;
        });

        // photos and comments are added over the existing map rather than replacing it.
        getSupportFragmentManager()
                .addOnBackStackChangedListener(() -> {

                    if (getSupportFragmentManager()
                            .getBackStackEntryCount() != 0
                            || pendingPopupEventId == null) {
                        return;
                    }

                    Fragment visibleFragment =
                            getSupportFragmentManager()
                                    .findFragmentById(
                                            R.id.fragment_container
                                    );

                    if (visibleFragment instanceof MapFragment) {
                        String eventId = pendingPopupEventId;
                        pendingPopupEventId = null;

                        View mapView = visibleFragment.getView();

                        if (mapView != null) {
                            mapView.post(() ->
                                    ((MapFragment) visibleFragment)
                                            .showEventPopup(eventId)
                            );
                        }
                    }
                });

        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_map);
        }
    }

    public void openEventPhotos(String eventId) {
        pendingPopupEventId = eventId;
        getSupportFragmentManager()
                .beginTransaction()
                .add(
                        R.id.fragment_container,
                        EventPhotosFragment.newInstance(eventId),
                        "event_photos"
                )
                .addToBackStack("event_photos")
                .commit();
    }

    public void openEventComments(String eventId) {
        pendingPopupEventId = eventId;
        getSupportFragmentManager()
                .beginTransaction()
                .add(
                        R.id.fragment_container,
                        EventCommentsFragment.newInstance(eventId),
                        "event_comments"
                )
                .addToBackStack("event_comments")
                .commit();
    }

    public void setMainChromeVisible(boolean visible) {
        int visibility = visible
                ? View.VISIBLE
                : View.GONE;

        if (topBar != null) {
            topBar.setVisibility(visibility);
        }

        if (bottomNav != null) {
            bottomNav.setVisibility(visibility);
        }
    }
}