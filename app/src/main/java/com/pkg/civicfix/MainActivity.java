package com.pkg.civicfix;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.pkg.civicfix.base.CivicFixActivity;

public class MainActivity extends CivicFixActivity {

    public static final String EXTRA_OPEN_EVENT_ID =
            "open_event_id";

    private static final String PREFS_NAME =
            "kinship_prefs";

    private static final String KEY_LOCATION_PERMISSION_ASKED =
            "location_permission_asked";

    private View topBar;

    private View fragmentContainer;

    private BottomNavigationView bottomNav;

    private String pendingPopupEventId;

    private String pendingMapEventId;

    private boolean mainChromeVisible =
            true;


    private final ActivityResultLauncher<String[]>
            locationPermissionLauncher =

            registerForActivityResult(
                    new ActivityResultContracts
                            .RequestMultiplePermissions(),

                    result -> {

                        getSharedPreferences(
                                PREFS_NAME,
                                MODE_PRIVATE
                        )
                                .edit()
                                .putBoolean(
                                        KEY_LOCATION_PERMISSION_ASKED,
                                        true
                                )
                                .apply();

                        if (
                                bottomNav != null
                        ) {

                            bottomNav.setSelectedItemId(
                                    R.id.nav_map
                            );
                        }
                    }
            );


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

        fragmentContainer =
                findViewById(
                        R.id.fragment_container
                );

        bottomNav =
                findViewById(
                        R.id.bottom_nav
                );

        configureSubpageInsets();

        bottomNav
                .setItemActiveIndicatorEnabled(
                        true
                );

        findViewById(
                R.id.btn_settings
        ).setOnClickListener(
                v ->
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
                                    id == R.id.nav_map
                            ) {

                                selected =
                                        new MapFragment();

                            } else if (
                                    id == R.id.nav_events
                            ) {

                                selected =
                                        new EventsFragment();

                            } else if (
                                    id == R.id.nav_report
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

                                if (
                                        mapView != null
                                ) {

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

            openInitialMapAndRequestLocationIfNeeded();
        }
    }


    private void configureSubpageInsets() {

        ViewCompat.setOnApplyWindowInsetsListener(
                fragmentContainer,

                (view, windowInsets) -> {

                    Insets statusBars =
                            windowInsets.getInsets(
                                    WindowInsetsCompat
                                            .Type
                                            .statusBars()
                            );

                    Insets navigationBars =
                            windowInsets.getInsets(
                                    WindowInsetsCompat
                                            .Type
                                            .navigationBars()
                            );

                    int topPadding =
                            mainChromeVisible
                                    ? 0
                                    : statusBars.top / 3;

                    int bottomPadding =
                            mainChromeVisible
                                    ? 0
                                    : navigationBars.bottom;

                    view.setPadding(
                            0,
                            topPadding,
                            0,
                            bottomPadding
                    );

                    return windowInsets;
                }
        );

        fragmentContainer.post(
                () ->
                        ViewCompat.requestApplyInsets(
                                fragmentContainer
                        )
        );
    }


    private void openInitialMapAndRequestLocationIfNeeded() {

        if (
                hasLocationPermission()
        ) {

            bottomNav.setSelectedItemId(
                    R.id.nav_map
            );

            return;
        }

        boolean alreadyAsked =
                getSharedPreferences(
                        PREFS_NAME,
                        MODE_PRIVATE
                )
                        .getBoolean(
                                KEY_LOCATION_PERMISSION_ASKED,
                                false
                        );

        if (
                alreadyAsked
        ) {

            bottomNav.setSelectedItemId(
                    R.id.nav_map
            );

            return;
        }

        locationPermissionLauncher.launch(
                new String[] {
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                }
        );
    }


    private boolean hasLocationPermission() {

        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        )
                == PackageManager.PERMISSION_GRANTED

                ||

                ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                )
                        == PackageManager.PERMISSION_GRANTED;
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

        mainChromeVisible =
                visible;

        int visibility =
                visible
                        ? View.VISIBLE
                        : View.GONE;

        if (
                topBar != null
        ) {

            topBar.setVisibility(
                    visibility
            );
        }

        if (
                bottomNav != null
        ) {

            bottomNav.setVisibility(
                    visibility
            );
        }

        if (
                fragmentContainer != null
        ) {

            ViewCompat.requestApplyInsets(
                    fragmentContainer
            );
        }
    }
}