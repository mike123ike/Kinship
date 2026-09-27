package com.pkg.civicfix;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_OPEN_EVENT_ID =
            "open_event_id";

    private View topBar;

    private BottomNavigationView bottomNav;

    // event whose popup should be restored after leaving photos/comments
    private String pendingPopupEventId;

    // event requested by my reports -> view on map
    private String pendingMapEventId;

    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {
        super.onCreate(
                savedInstanceState
        );

        setContentView(
                R.layout.main_activity
        );

        topBar =
                findViewById(
                        R.id.top_bar
                );

        bottomNav =
                findViewById(
                        R.id.bottom_nav
                );

        bottomNav
                .setItemActiveIndicatorEnabled(
                        true
                );

        findViewById(
                R.id.btn_settings
        ).setOnClickListener(v ->

                startActivity(
                        new Intent(
                                this,
                                SettingsActivity.class
                        )
                )
        );

        bottomNav
                .setOnItemSelectedListener(
                        item -> {

                            pendingPopupEventId =
                                    null;

                            getSupportFragmentManager()
                                    .popBackStack(
                                            null,
                                            FragmentManager
                                                    .POP_BACK_STACK_INCLUSIVE
                                    );

                            Fragment selected;

                            int id =
                                    item.getItemId();

                            if (
                                    id
                                            == R.id.nav_map
                            ) {

                                selected =
                                        new MapFragment();

                            } else if (
                                    id
                                            == R.id.nav_events
                            ) {

                                selected =
                                        new EventsFragment();

                            } else if (
                                    id
                                            == R.id.nav_report
                            ) {

                                selected =
                                        new ReportFragment();

                            } else {

                                selected =
                                        new ProfileFragment();
                            }

                            Fragment finalSelected =
                                    selected;

                            FragmentTransaction transaction =
                                    getSupportFragmentManager()
                                            .beginTransaction()
                                            .replace(
                                                    R.id.fragment_container,
                                                    selected
                                            );

                            if (
                                    finalSelected
                                            instanceof MapFragment
                            ) {

                                transaction.runOnCommit(
                                        () -> {

                                            if (
                                                    pendingMapEventId
                                                            == null
                                            ) {
                                                return;
                                            }

                                            String eventId =
                                                    pendingMapEventId;

                                            pendingMapEventId =
                                                    null;

                                            ((MapFragment)
                                                    finalSelected)
                                                    .focusOnEvent(
                                                            eventId
                                                    );
                                        }
                                );
                            }

                            transaction
                                    .commitAllowingStateLoss();

                            return true;
                        }
                );

        getSupportFragmentManager()
                .addOnBackStackChangedListener(
                        () -> {

                            if (
                                    getSupportFragmentManager()
                                            .getBackStackEntryCount()
                                            != 0

                                            || pendingPopupEventId
                                            == null
                            ) {

                                return;
                            }

                            Fragment visibleFragment =
                                    getSupportFragmentManager()
                                            .findFragmentById(
                                                    R.id.fragment_container
                                            );

                            if (
                                    visibleFragment
                                            instanceof MapFragment
                            ) {

                                String eventId =
                                        pendingPopupEventId;

                                pendingPopupEventId =
                                        null;

                                View mapView =
                                        visibleFragment
                                                .getView();

                                if (mapView != null) {

                                    mapView.post(
                                            () ->
                                                    ((MapFragment)
                                                            visibleFragment)
                                                            .showEventPopup(
                                                                    eventId
                                                            )
                                    );
                                }
                            }
                        }
                );

        if (
                savedInstanceState == null
        ) {

            String eventId =
                    getIntent()
                            .getStringExtra(
                                    EXTRA_OPEN_EVENT_ID
                            );

            if (
                    eventId != null
                            && !eventId.isEmpty()
            ) {

                pendingMapEventId =
                        eventId;
            }

            bottomNav
                    .setSelectedItemId(
                            R.id.nav_map
                    );
        }
    }

    @Override
    protected void onNewIntent(
            Intent intent
    ) {
        super.onNewIntent(
                intent
        );

        setIntent(
                intent
        );

        String eventId =
                intent.getStringExtra(
                        EXTRA_OPEN_EVENT_ID
                );

        if (
                eventId != null
                        && !eventId.isEmpty()
        ) {

            openMapAtEvent(
                    eventId
            );
        }
    }

    // my reports -> view on map

    public void openMapAtEvent(
            String eventId
    ) {

        if (
                eventId == null
                        || eventId.isEmpty()
        ) {
            return;
        }

        pendingPopupEventId =
                null;

        if (
                bottomNav
                        .getSelectedItemId()
                        == R.id.nav_map
        ) {

            Fragment fragment =
                    getSupportFragmentManager()
                            .findFragmentById(
                                    R.id.fragment_container
                            );

            if (
                    fragment
                            instanceof MapFragment
            ) {

                ((MapFragment) fragment)
                        .focusOnEvent(
                                eventId
                        );

                return;
            }
        }

        // switching tabs invokes navigation listener above
        pendingMapEventId =
                eventId;

        bottomNav
                .setSelectedItemId(
                        R.id.nav_map
                );
    }

    public void openEventPhotos(
            String eventId
    ) {

        pendingPopupEventId =
                eventId;

        getSupportFragmentManager()
                .beginTransaction()
                .add(
                        R.id.fragment_container,
                        EventPhotosFragment
                                .newInstance(
                                        eventId
                                ),
                        "event_photos"
                )
                .addToBackStack(
                        "event_photos"
                )
                .commit();
    }

    public void openEventComments(
            String eventId
    ) {

        pendingPopupEventId =
                eventId;

        getSupportFragmentManager()
                .beginTransaction()
                .add(
                        R.id.fragment_container,
                        EventCommentsFragment
                                .newInstance(
                                        eventId
                                ),
                        "event_comments"
                )
                .addToBackStack(
                        "event_comments"
                )
                .commit();
    }

    public void setMainChromeVisible(
            boolean visible
    ) {

        int visibility =
                visible
                        ? View.VISIBLE
                        : View.GONE;

        if (topBar != null) {

            topBar.setVisibility(
                    visibility
            );
        }

        if (bottomNav != null) {

            bottomNav.setVisibility(
                    visibility
            );
        }
    }
}