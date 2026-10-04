package com.pkg.civicfix;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.firebase.geofire.GeoFireUtils;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQueryBounds;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.pkg.civicfix.model.ImportantLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EventsFragment extends Fragment {

    private static final String TAG =
            "EventsFragment";

    private static final int TAB_NEARBY =
            0;

    private static final int TAB_LOCAL_NEWS =
            1;

    // 3 miles.
    private static final double NEARBY_RADIUS_METERS =
            4828.032;

    private static final double METERS_PER_MILE =
            1609.344;

    private static final float MAX_NEWS_LOCATION_ACCURACY_METERS =
            2000f;

    private static final long MAX_LAST_LOCATION_AGE_MS =
            6L * 60L * 60L * 1000L;

    private static final String SORT_RECENT =
            "Most Recent";

    private static final String SORT_SEVERITY =
            "Severity";

    private static final String SORT_CLOSEST =
            "Closest";

    private static final String SORT_REPORTS =
            "Most Reports";

    private FirebaseAuth auth;

    private FirebaseFirestore db;

    private FusedLocationProviderClient
            fusedLocationClient;

    private LocalNewsRepository
            localNewsRepository;

    private final ExecutorService geocoderExecutor =
            Executors.newSingleThreadExecutor();

    private final Handler mainHandler =
            new Handler(
                    Looper.getMainLooper()
            );

    private View nearbyContent;

    private View localNewsContent;

    private MaterialButton btnTabNearby;

    private MaterialButton btnTabLocalNews;

    private MaterialButton btnLocationFilter;

    private MaterialButton btnSort;

    private LinearLayout nearbyEventsContainer;

    private View nearbyEmptyLayout;

    private TextView tvNearbyEventCount;

    private LinearLayout localNewsContainer;

    private View localNewsEmptyLayout;

    private TextView tvLocalNewsLocation;

    private TextView tvLocalNewsCount;

    private final List<ImportantLocation>
            importantLocations =
            new ArrayList<>();

    private final Map<String, NearbyEventItem>
            nearbyEventsById =
            new HashMap<>();

    @Nullable
    private String selectedLocationId;

    private String selectedSort =
            SORT_RECENT;

    private int nearbyLoadGeneration =
            0;

    private boolean localNewsLoaded =
            false;

    private boolean localNewsLoading =
            false;

    private int currentTab =
            TAB_NEARBY;


    // location permission

    private final ActivityResultLauncher<String[]>
            localNewsLocationPermissionLauncher =

            registerForActivityResult(
                    new ActivityResultContracts
                            .RequestMultiplePermissions(),

                    result -> {

                        boolean fineGranted =
                                Boolean.TRUE.equals(
                                        result.get(
                                                Manifest.permission
                                                        .ACCESS_FINE_LOCATION
                                        )
                                );

                        if (fineGranted) {

                            loadLocalNews();

                        } else {

                            showLocalNewsError(
                                    "Turn on Precise Location to show news for your actual city."
                            );
                        }
                    }
            );


    // fragment

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {

        return inflater.inflate(
                R.layout.fragment_events,
                container,
                false
        );
    }


    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {

        super.onViewCreated(
                view,
                savedInstanceState
        );

        auth =
                FirebaseAuth.getInstance();

        db =
                FirebaseFirestore.getInstance();

        fusedLocationClient =
                LocationServices
                        .getFusedLocationProviderClient(
                                requireContext()
                        );

        localNewsRepository =
                new LocalNewsRepository();

        nearbyContent =
                view.findViewById(
                        R.id.events_nearby_content
                );

        localNewsContent =
                view.findViewById(
                        R.id.events_local_news_content
                );

        btnTabNearby =
                view.findViewById(
                        R.id.btn_tab_nearby
                );

        btnTabLocalNews =
                view.findViewById(
                        R.id.btn_tab_local_news
                );

        btnLocationFilter =
                view.findViewById(
                        R.id.btn_events_location_filter
                );

        btnSort =
                view.findViewById(
                        R.id.btn_events_sort
                );

        nearbyEventsContainer =
                view.findViewById(
                        R.id.nearby_events_container
                );

        nearbyEmptyLayout =
                view.findViewById(
                        R.id.layout_nearby_events_empty
                );

        tvNearbyEventCount =
                view.findViewById(
                        R.id.tv_nearby_event_count
                );

        localNewsContainer =
                view.findViewById(
                        R.id.local_news_container
                );

        localNewsEmptyLayout =
                view.findViewById(
                        R.id.layout_local_news_empty
                );

        tvLocalNewsLocation =
                view.findViewById(
                        R.id.tv_local_news_location
                );

        tvLocalNewsCount =
                view.findViewById(
                        R.id.tv_local_news_count
                );

        btnTabNearby.setOnClickListener(
                v -> selectTab(
                        TAB_NEARBY
                )
        );

        btnTabLocalNews.setOnClickListener(
                v -> selectTab(
                        TAB_LOCAL_NEWS
                )
        );

        btnLocationFilter.setOnClickListener(
                v -> showLocationMenu()
        );

        btnSort.setOnClickListener(
                v -> showSortMenu()
        );

        selectTab(
                TAB_NEARBY
        );
    }


    @Override
    public void onResume() {

        super.onResume();

        if (
                getView() != null
        ) {

            loadImportantLocationsAndNearbyEvents();
        }
    }


    @Override
    public void onDestroy() {

        super.onDestroy();

        nearbyLoadGeneration++;

        if (
                localNewsRepository != null
        ) {

            localNewsRepository.shutdown();
        }

        geocoderExecutor.shutdownNow();
    }


    // tabs

    private void selectTab(
            int tab
    ) {

        currentTab =
                tab;

        boolean showNearby =
                tab == TAB_NEARBY;

        nearbyContent.setVisibility(
                showNearby
                        ? View.VISIBLE
                        : View.GONE
        );

        localNewsContent.setVisibility(
                showNearby
                        ? View.GONE
                        : View.VISIBLE
        );

        updateTabStyles();

        if (
                tab == TAB_LOCAL_NEWS
        ) {

            loadLocalNewsIfNeeded();
        }
    }


    private void updateTabStyles() {

        styleTabButton(
                btnTabNearby,
                currentTab == TAB_NEARBY
        );

        styleTabButton(
                btnTabLocalNews,
                currentTab == TAB_LOCAL_NEWS
        );
    }


    private void styleTabButton(
            @NonNull MaterialButton button,
            boolean selected
    ) {

        int primary =
                ContextCompat.getColor(
                        requireContext(),
                        R.color.primary
                );

        int cardBackground =
                ContextCompat.getColor(
                        requireContext(),
                        R.color.cardBackground
                );

        int normalText =
                ContextCompat.getColor(
                        requireContext(),
                        R.color.tertiary
                );

        int selectedText =
                isDarkMode()
                        ? Color.parseColor(
                        "#142218"
                )
                        : Color.WHITE;

        button.setBackgroundTintList(
                ColorStateList.valueOf(
                        selected
                                ? primary
                                : cardBackground
                )
        );

        button.setTextColor(
                selected
                        ? selectedText
                        : normalText
        );

        button.setStrokeWidth(
                0
        );

        button.setElevation(
                0f
        );
    }


    private boolean isDarkMode() {

        int nightMode =
                getResources()
                        .getConfiguration()
                        .uiMode
                        & Configuration.UI_MODE_NIGHT_MASK;

        return nightMode
                == Configuration.UI_MODE_NIGHT_YES;
    }


    // important locations

    private void loadImportantLocationsAndNearbyEvents() {

        FirebaseUser user =
                auth.getCurrentUser();

        if (user == null) {

            showNearbyEmptyState();

            return;
        }

        btnLocationFilter.setEnabled(
                false
        );

        btnSort.setEnabled(
                false
        );

        nearbyEventsContainer
                .removeAllViews();

        nearbyEmptyLayout
                .setVisibility(
                        View.GONE
                );

        tvNearbyEventCount
                .setText(
                        "Loading..."
                );

        db.collection(
                        "users"
                )
                .document(
                        user.getUid()
                )
                .collection(
                        "importantLocations"
                )
                .get()
                .addOnSuccessListener(
                        snapshot -> {

                            if (!isAdded()) {
                                return;
                            }

                            importantLocations
                                    .clear();

                            importantLocations
                                    .addAll(
                                            snapshot
                                                    .toObjects(
                                                            ImportantLocation.class
                                                    )
                                    );

                            sortImportantLocationsForMenu();

                            if (
                                    selectedLocationId != null

                                            && findImportantLocationById(
                                            selectedLocationId
                                    ) == null
                            ) {

                                selectedLocationId =
                                        null;
                            }

                            if (
                                    importantLocations
                                            .isEmpty()
                            ) {

                                selectedLocationId =
                                        null;

                                btnLocationFilter
                                        .setText(
                                                "No Locations"
                                        );

                                btnLocationFilter
                                        .setEnabled(
                                                false
                                        );

                                btnSort
                                        .setEnabled(
                                                false
                                        );

                                nearbyEventsById
                                        .clear();

                                showNearbyEmptyState();

                                return;
                            }

                            updateLocationFilterButtonText();

                            btnLocationFilter
                                    .setEnabled(
                                            true
                                    );

                            btnSort
                                    .setEnabled(
                                            true
                                    );

                            loadNearbyEvents();
                        }
                )
                .addOnFailureListener(
                        error -> {

                            if (!isAdded()) {
                                return;
                            }

                            Log.e(
                                    TAG,
                                    "Failed to load Important Locations",
                                    error
                            );

                            showNearbyEmptyState();

                            Toast.makeText(
                                    requireContext(),
                                    "Failed to load saved locations",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                );
    }


    // nearby events

    private void loadNearbyEvents() {

        final int generation =
                ++nearbyLoadGeneration;

        nearbyEventsById
                .clear();

        nearbyEventsContainer
                .removeAllViews();

        nearbyEmptyLayout
                .setVisibility(
                        View.GONE
                );

        tvNearbyEventCount
                .setText(
                        "Loading..."
                );

        List<Task<QuerySnapshot>>
                queryTasks =
                new ArrayList<>();

        Set<String> queryKeys =
                new HashSet<>();

        String[] statuses = {
                "ACTIVE",
                "IN_PROGRESS"
        };

        for (
                ImportantLocation location
                : importantLocations
        ) {

            GeoLocation center =
                    new GeoLocation(
                            location
                                    .getLatitude(),

                            location
                                    .getLongitude()
                    );

            List<GeoQueryBounds> bounds =
                    GeoFireUtils
                            .getGeoHashQueryBounds(
                                    center,
                                    NEARBY_RADIUS_METERS
                            );

            for (
                    String status
                    : statuses
            ) {

                for (
                        GeoQueryBounds bound
                        : bounds
                ) {

                    String queryKey =
                            status
                                    + "|"
                                    + bound.startHash
                                    + "|"
                                    + bound.endHash;

                    if (
                            !queryKeys.add(
                                    queryKey
                            )
                    ) {

                        continue;
                    }

                    Query query =
                            db.collection(
                                            "events"
                                    )
                                    .whereEqualTo(
                                            "status",
                                            status
                                    )
                                    .orderBy(
                                            "geohash"
                                    )
                                    .startAt(
                                            bound.startHash
                                    )
                                    .endAt(
                                            bound.endHash
                                    );

                    queryTasks.add(
                            query.get()
                    );
                }
            }
        }

        if (
                queryTasks.isEmpty()
        ) {

            showNearbyEmptyState();

            return;
        }

        Tasks.whenAllComplete(
                queryTasks
        ).addOnCompleteListener(
                ignored -> {

                    if (
                            !isAdded()

                                    || generation
                                    != nearbyLoadGeneration
                    ) {

                        return;
                    }

                    int successfulQueries =
                            0;

                    Exception firstFailure =
                            null;

                    for (
                            Task<QuerySnapshot> task
                            : queryTasks
                    ) {

                        if (
                                !task.isSuccessful()

                                        || task.getResult()
                                        == null
                        ) {

                            if (
                                    firstFailure == null

                                            && task.getException()
                                            != null
                            ) {

                                firstFailure =
                                        task.getException();
                            }

                            continue;
                        }

                        successfulQueries++;

                        for (
                                DocumentSnapshot document
                                : task
                                .getResult()
                                .getDocuments()
                        ) {

                            addDocumentIfNearby(
                                    document
                            );
                        }
                    }

                    if (
                            successfulQueries
                                    == 0
                    ) {

                        if (
                                firstFailure != null
                        ) {

                            Log.e(
                                    TAG,
                                    "Nearby event queries failed",
                                    firstFailure
                            );
                        }

                        showNearbyEmptyState();

                        Toast.makeText(
                                requireContext(),
                                "Failed to load nearby events",
                                Toast.LENGTH_SHORT
                        ).show();

                        return;
                    }

                    renderNearbyEvents();
                }
        );
    }


    private void addDocumentIfNearby(
            @NonNull DocumentSnapshot document
    ) {

        String status =
                document.getString(
                        "status"
                );

        if (
                !"ACTIVE"
                        .equalsIgnoreCase(
                                status
                        )

                        && !"IN_PROGRESS"
                        .equalsIgnoreCase(
                                status
                        )
        ) {

            return;
        }

        Double latitude =
                document.getDouble(
                        "latitude"
                );

        Double longitude =
                document.getDouble(
                        "longitude"
                );

        if (
                latitude == null
                        || longitude == null
        ) {

            return;
        }

        // geofire returns rectangular geohash bounds; exact distance check guarantees event is within 3 miles.
        DistanceMatch nearest =
                findNearestLocation(
                        latitude,
                        longitude,
                        importantLocations
                );

        if (
                nearest == null

                        || nearest.distanceMeters
                        > NEARBY_RADIUS_METERS
        ) {

            return;
        }

        if (
                nearbyEventsById
                        .containsKey(
                                document.getId()
                        )
        ) {

            return;
        }

        Double averageSeverity =
                document.getDouble(
                        "averageSeverity"
                );

        Long uniqueUserCount =
                document.getLong(
                        "uniqueUserCount"
                );

        Long commentCount =
                document.getLong(
                        "commentCount"
                );

        NearbyEventItem item =
                new NearbyEventItem(

                        document.getId(),

                        document.getString(
                                "category"
                        ),

                        status,

                        averageSeverity == null
                                ? 0
                                : averageSeverity,

                        uniqueUserCount == null
                                ? 0
                                : uniqueUserCount
                                  .intValue(),

                        commentCount == null
                                ? 0
                                : commentCount
                                  .intValue(),

                        latitude,

                        longitude,

                        document.getTimestamp(
                                "createdAt"
                        ),

                        document.getTimestamp(
                                "updatedAt"
                        )
                );

        nearbyEventsById.put(
                item.eventId,
                item
        );
    }


    private void renderNearbyEvents() {

        if (!isAdded()) {
            return;
        }

        List<NearbyEventItem>
                visibleItems =
                new ArrayList<>();

        ImportantLocation selectedLocation =
                selectedLocationId == null

                        ? null

                        : findImportantLocationById(
                        selectedLocationId
                );

        for (
                NearbyEventItem item
                : nearbyEventsById.values()
        ) {

            DistanceMatch match;

            if (
                    selectedLocation
                            != null
            ) {

                double distance =
                        distanceMeters(
                                item.latitude,
                                item.longitude,
                                selectedLocation
                        );

                if (
                        distance
                                > NEARBY_RADIUS_METERS
                ) {

                    continue;
                }

                match =
                        new DistanceMatch(
                                selectedLocation,
                                distance
                        );

            } else {

                match =
                        findNearestLocation(
                                item.latitude,
                                item.longitude,
                                importantLocations
                        );

                if (
                        match == null

                                || match.distanceMeters
                                > NEARBY_RADIUS_METERS
                ) {

                    continue;
                }
            }

            item.displayDistanceMeters =
                    match.distanceMeters;

            item.displayLocationName =
                    getImportantLocationDisplayName(
                            match.location
                    );

            visibleItems.add(
                    item
            );
        }

        Collections.sort(
                visibleItems,
                this::compareNearbyEvents
        );

        nearbyEventsContainer
                .removeAllViews();

        for (
                NearbyEventItem item
                : visibleItems
        ) {

            addNearbyEventCard(
                    item
            );
        }

        int count =
                visibleItems.size();

        tvNearbyEventCount.setText(
                count
                        + (
                        count == 1
                                ? " event"
                                : " events"
                )
        );

        nearbyEmptyLayout.setVisibility(
                count == 0
                        ? View.VISIBLE
                        : View.GONE
        );
    }


    private int compareNearbyEvents(
            @NonNull NearbyEventItem a,
            @NonNull NearbyEventItem b
    ) {

        int comparison;

        switch (
                selectedSort
        ) {

            case SORT_SEVERITY:

                comparison =
                        Double.compare(
                                b.averageSeverity,
                                a.averageSeverity
                        );

                break;


            case SORT_CLOSEST:

                comparison =
                        Double.compare(
                                a.displayDistanceMeters,
                                b.displayDistanceMeters
                        );

                break;


            case SORT_REPORTS:

                comparison =
                        Integer.compare(
                                b.uniqueUserCount,
                                a.uniqueUserCount
                        );

                break;


            case SORT_RECENT:
            default:

                comparison =
                        Long.compare(
                                getActivityTimeMillis(
                                        b
                                ),

                                getActivityTimeMillis(
                                        a
                                )
                        );

                break;
        }

        if (
                comparison != 0
        ) {

            return comparison;
        }

        return Long.compare(
                getActivityTimeMillis(
                        b
                ),

                getActivityTimeMillis(
                        a
                )
        );
    }


    private void addNearbyEventCard(
            @NonNull NearbyEventItem item
    ) {

        View card =
                LayoutInflater
                        .from(
                                requireContext()
                        )
                        .inflate(
                                R.layout.item_nearby_event,
                                nearbyEventsContainer,
                                false
                        );

        TextView categoryView =
                card.findViewById(
                        R.id.tv_nearby_event_category
                );

        TextView descriptionView =
                card.findViewById(
                        R.id.tv_nearby_event_description
                );

        TextView distanceView =
                card.findViewById(
                        R.id.tv_nearby_event_distance
                );

        TextView timeView =
                card.findViewById(
                        R.id.tv_nearby_event_time
                );

        TextView statsView =
                card.findViewById(
                        R.id.tv_nearby_event_stats
                );

        TextView severityView =
                card.findViewById(
                        R.id.tv_nearby_event_severity
                );

        MaterialCardView statusCard =
                card.findViewById(
                        R.id.card_nearby_event_status
                );

        TextView statusView =
                card.findViewById(
                        R.id.tv_nearby_event_status
                );

        categoryView.setText(
                formatCategory(
                        item.category
                )
        );

        descriptionView.setText(
                buildCategorySummary(
                        item.category
                )
        );

        distanceView.setText(
                formatDistanceFromLocation(
                        item.displayDistanceMeters,
                        item.displayLocationName
                )
        );

        Timestamp activityTime =
                item.updatedAt != null

                        ? item.updatedAt

                        : item.createdAt;

        timeView.setText(
                activityTime == null

                        ? "Updated recently"

                        : "Updated "
                          + ReportDisplayUtils
                            .formatRelativeTime(
                                    activityTime
                            )
        );

        statsView.setText(
                formatStats(
                        item.uniqueUserCount,
                        item.commentCount
                )
        );

        severityView.setText(
                "Severity "
                        + formatSeverityValue(
                        item.averageSeverity
                )
        );

        if (
                severityView.getBackground()
                        instanceof GradientDrawable
        ) {

            GradientDrawable background =
                    (GradientDrawable)
                            severityView
                                    .getBackground()
                                    .mutate();

            background.setColor(
                    getSeverityColor(
                            item.averageSeverity
                    )
            );
        }

        statusView.setText(
                ReportDisplayUtils
                        .statusLabel(
                                item.status
                        )
        );

        statusCard.setCardBackgroundColor(
                ContextCompat.getColor(
                        requireContext(),

                        ReportStatusUi
                                .getBackgroundColorRes(
                                        item.status
                                )
                )
        );

        statusCard.setStrokeColor(
                ContextCompat.getColor(
                        requireContext(),

                        ReportStatusUi
                                .getStrokeColorRes(
                                        item.status
                                )
                )
        );

        statusView.setTextColor(
                ContextCompat.getColor(
                        requireContext(),

                        ReportStatusUi
                                .getTextColorRes(
                                        item.status
                                )
                )
        );

        card.setOnClickListener(
                v -> {

                    if (
                            getActivity()
                                    instanceof MainActivity
                    ) {

                        ((MainActivity)
                                getActivity())
                                .openMapAtEvent(
                                        item.eventId
                                );
                    }
                }
        );

        nearbyEventsContainer.addView(
                card
        );
    }


    // location filter

    private void showLocationMenu() {

        if (
                importantLocations
                        .isEmpty()
        ) {

            return;
        }

        PopupMenu popupMenu =
                new PopupMenu(
                        requireContext(),
                        btnLocationFilter
                );

        Menu menu =
                popupMenu.getMenu();

        final int allLocationsId =
                1000;

        menu.add(
                Menu.NONE,
                allLocationsId,
                Menu.NONE,
                "All Locations"
        );

        Map<Integer, String>
                locationIdByMenuItem =
                new HashMap<>();

        for (
                int i = 0;
                i < importantLocations.size();
                i++
        ) {

            ImportantLocation location =
                    importantLocations.get(
                            i
                    );

            int menuId =
                    2000 + i;

            menu.add(
                    Menu.NONE,
                    menuId,
                    Menu.NONE,
                    getImportantLocationDisplayName(
                            location
                    )
            );

            locationIdByMenuItem.put(
                    menuId,
                    location.getId()
            );
        }

        popupMenu.setOnMenuItemClickListener(
                item -> {

                    if (
                            item.getItemId()
                                    == allLocationsId
                    ) {

                        selectedLocationId =
                                null;

                    } else {

                        selectedLocationId =
                                locationIdByMenuItem.get(
                                        item.getItemId()
                                );
                    }

                    updateLocationFilterButtonText();

                    renderNearbyEvents();

                    return true;
                }
        );

        popupMenu.show();
    }


    private void updateLocationFilterButtonText() {

        if (
                selectedLocationId
                        == null
        ) {

            btnLocationFilter.setText(
                    "All Locations"
            );

            return;
        }

        ImportantLocation selected =
                findImportantLocationById(
                        selectedLocationId
                );

        if (
                selected == null
        ) {

            selectedLocationId =
                    null;

            btnLocationFilter.setText(
                    "All Locations"
            );

            return;
        }

        btnLocationFilter.setText(
                getImportantLocationDisplayName(
                        selected
                )
        );
    }


    @Nullable
    private ImportantLocation findImportantLocationById(
            @Nullable String locationId
    ) {

        if (
                locationId == null
        ) {

            return null;
        }

        for (
                ImportantLocation location
                : importantLocations
        ) {

            if (
                    locationId.equals(
                            location.getId()
                    )
            ) {

                return location;
            }
        }

        return null;
    }


    // sort

    private void showSortMenu() {

        PopupMenu popupMenu =
                new PopupMenu(
                        requireContext(),
                        btnSort
                );

        Menu menu =
                popupMenu.getMenu();

        menu.add(
                SORT_RECENT
        );

        menu.add(
                SORT_SEVERITY
        );

        menu.add(
                SORT_CLOSEST
        );

        menu.add(
                SORT_REPORTS
        );

        popupMenu.setOnMenuItemClickListener(
                item -> {

                    selectedSort =
                            item
                                    .getTitle()
                                    .toString();

                    btnSort.setText(
                            selectedSort
                    );

                    renderNearbyEvents();

                    return true;
                }
        );

        popupMenu.show();
    }


    // location helpers

    @Nullable
    private DistanceMatch findNearestLocation(
            double latitude,
            double longitude,
            @NonNull List<ImportantLocation> locations
    ) {

        ImportantLocation nearestLocation =
                null;

        double nearestDistance =
                Double.MAX_VALUE;

        for (
                ImportantLocation location
                : locations
        ) {

            double distance =
                    distanceMeters(
                            latitude,
                            longitude,
                            location
                    );

            if (
                    distance
                            < nearestDistance
            ) {

                nearestDistance =
                        distance;

                nearestLocation =
                        location;
            }
        }

        if (
                nearestLocation
                        == null
        ) {

            return null;
        }

        return new DistanceMatch(
                nearestLocation,
                nearestDistance
        );
    }


    private double distanceMeters(
            double eventLatitude,
            double eventLongitude,
            @NonNull ImportantLocation location
    ) {

        return GeoFireUtils
                .getDistanceBetween(

                        new GeoLocation(
                                eventLatitude,
                                eventLongitude
                        ),

                        new GeoLocation(
                                location.getLatitude(),
                                location.getLongitude()
                        )
                );
    }


    private void sortImportantLocationsForMenu() {

        Collections.sort(
                importantLocations,

                (a, b) -> {

                    int rankComparison =
                            Integer.compare(

                                    getLocationTypeRank(
                                            a.getType()
                                    ),

                                    getLocationTypeRank(
                                            b.getType()
                                    )
                            );

                    if (
                            rankComparison
                                    != 0
                    ) {

                        return rankComparison;
                    }

                    if (
                            ImportantLocation.TYPE_CUSTOM
                                    .equals(
                                            a.getType()
                                    )

                                    && ImportantLocation.TYPE_CUSTOM
                                    .equals(
                                            b.getType()
                                    )
                    ) {

                        Timestamp aUpdated =
                                a.getUpdatedAt();

                        Timestamp bUpdated =
                                b.getUpdatedAt();

                        if (
                                aUpdated != null
                                        && bUpdated != null
                        ) {

                            int timestampComparison =
                                    bUpdated.compareTo(
                                            aUpdated
                                    );

                            if (
                                    timestampComparison
                                            != 0
                            ) {

                                return timestampComparison;
                            }
                        }
                    }

                    return getImportantLocationDisplayName(
                            a
                    ).compareToIgnoreCase(
                            getImportantLocationDisplayName(
                                    b
                            )
                    );
                }
        );
    }


    private int getLocationTypeRank(
            @Nullable String type
    ) {

        if (
                ImportantLocation.TYPE_HOME
                        .equals(
                                type
                        )
        ) {

            return 0;
        }

        if (
                ImportantLocation.TYPE_WORK
                        .equals(
                                type
                        )
        ) {

            return 1;
        }

        if (
                ImportantLocation.TYPE_SCHOOL
                        .equals(
                                type
                        )
        ) {

            return 2;
        }

        return 3;
    }


    private String getImportantLocationDisplayName(
            @NonNull ImportantLocation location
    ) {

        String type =
                location.getType();

        if (
                ImportantLocation.TYPE_HOME
                        .equals(
                                type
                        )
        ) {

            return "Home";
        }

        if (
                ImportantLocation.TYPE_WORK
                        .equals(
                                type
                        )
        ) {

            return "Work";
        }

        if (
                ImportantLocation.TYPE_SCHOOL
                        .equals(
                                type
                        )
        ) {

            return "School";
        }

        String customName =
                location.getName();

        if (
                customName == null

                        || customName
                        .trim()
                        .isEmpty()
        ) {

            return "Saved Location";
        }

        return customName.trim();
    }


    // nearby formatters

    private void showNearbyEmptyState() {

        if (
                nearbyEventsContainer == null

                        || nearbyEmptyLayout == null

                        || tvNearbyEventCount == null
        ) {

            return;
        }

        nearbyEventsContainer
                .removeAllViews();

        tvNearbyEventCount
                .setText(
                        "0 events"
                );

        nearbyEmptyLayout
                .setVisibility(
                        View.VISIBLE
                );
    }


    private long getActivityTimeMillis(
            @NonNull NearbyEventItem item
    ) {

        Timestamp timestamp =
                item.updatedAt != null

                        ? item.updatedAt

                        : item.createdAt;

        if (
                timestamp == null
        ) {

            return 0L;
        }

        return timestamp
                .toDate()
                .getTime();
    }


    private String formatDistanceFromLocation(
            double distanceMeters,
            @Nullable String locationName
    ) {

        double miles =
                distanceMeters
                        / METERS_PER_MILE;

        String distanceText =
                miles < 0.1

                        ? "<0.1 mi"

                        : String.format(
                        Locale.getDefault(),
                        "%.1f mi",
                        miles
                );

        if (
                locationName == null

                        || locationName
                        .trim()
                        .isEmpty()
        ) {

            return distanceText;
        }

        return distanceText
                + " from "
                + locationName;
    }


    private String formatStats(
            int reporterCount,
            int commentCount
    ) {

        String reporters =
                reporterCount
                        + (
                        reporterCount == 1

                                ? " reporter"

                                : " reporters"
                );

        String comments =
                commentCount
                        + (
                        commentCount == 1

                                ? " comment"

                                : " comments"
                );

        return reporters
                + " • "
                + comments;
    }


    private String formatSeverityValue(
            double severity
    ) {

        double rounded =
                Math.rint(
                        severity
                );

        if (
                Math.abs(
                        severity - rounded
                ) < 0.05
        ) {

            return String.valueOf(
                    (int) rounded
            );
        }

        return String.format(
                Locale.getDefault(),
                "%.1f",
                severity
        );
    }


    private int getSeverityColor(
            double severity
    ) {

        if (
                severity <= 3
        ) {

            return 0xFF4CAF50;
        }

        if (
                severity <= 6
        ) {

            return 0xFFFFC107;
        }

        if (
                severity <= 9
        ) {

            return 0xFFFF5722;
        }

        return 0xFFF44336;
    }


    private String formatCategory(
            @Nullable String category
    ) {

        if (
                category == null

                        || category
                        .trim()
                        .isEmpty()
        ) {

            return "Community Report";
        }

        switch (
                category
                        .trim()
                        .toUpperCase(
                                Locale.US
                        )
        ) {

            case "ROAD":
                return "Road Damage";

            case "LIGHTING":
                return "Lighting";

            case "HAZARDS":
                return "Hazard";

            case "VANDALISM":
                return "Vandalism";

            case "SAFETY":
                return "Safety";

            default:
                return "Other";
        }
    }


    private String buildCategorySummary(
            @Nullable String category
    ) {

        if (
                category == null
        ) {

            return "Community-reported issue near one of your saved places.";
        }

        switch (
                category
                        .trim()
                        .toUpperCase(
                                Locale.US
                        )
        ) {

            case "ROAD":

                return "Community-reported road issue near a saved location.";

            case "LIGHTING":

                return "Community-reported lighting issue near a saved location.";

            case "HAZARDS":

                return "Community-reported hazard near a saved location.";

            case "VANDALISM":

                return "Community-reported vandalism near a saved location.";

            case "SAFETY":

                return "Community-reported safety concern near a saved location.";

            default:

                return "Community-reported issue near one of your saved places.";
        }
    }


    // local news

    private void loadLocalNewsIfNeeded() {

        if (
                localNewsLoaded
                        || localNewsLoading
        ) {

            return;
        }

        loadLocalNews();
    }


    private void loadLocalNews() {

        if (
                BuildConfig.NEWSDATA_API_KEY == null

                        || BuildConfig.NEWSDATA_API_KEY
                        .trim()
                        .isEmpty()
        ) {

            showLocalNewsError(
                    "NewsData API key is missing."
            );

            return;
        }

        if (
                !hasFineLocationPermission()
        ) {

            localNewsLocationPermissionLauncher
                    .launch(
                            new String[] {

                                    Manifest.permission
                                            .ACCESS_COARSE_LOCATION,

                                    Manifest.permission
                                            .ACCESS_FINE_LOCATION
                            }
                    );

            return;
        }

        localNewsLoading =
                true;

        localNewsContainer
                .removeAllViews();

        localNewsEmptyLayout
                .setVisibility(
                        View.GONE
                );

        tvLocalNewsCount
                .setText(
                        "Loading..."
                );

        tvLocalNewsLocation
                .setText(
                        "Finding your precise location..."
                );

        CancellationTokenSource tokenSource =
                new CancellationTokenSource();

        try {

            fusedLocationClient
                    .getCurrentLocation(

                            Priority
                                    .PRIORITY_HIGH_ACCURACY,

                            tokenSource
                                    .getToken()
                    )
                    .addOnSuccessListener(
                            location -> {

                                if (!isAdded()) {
                                    return;
                                }

                                if (
                                        isPreciseEnough(
                                                location
                                        )
                                ) {

                                    resolveNewsArea(
                                            location
                                    );

                                } else {

                                    loadLastKnownNewsLocation();
                                }
                            }
                    )
                    .addOnFailureListener(
                            error -> {

                                if (!isAdded()) {
                                    return;
                                }

                                loadLastKnownNewsLocation();
                            }
                    );

        } catch (
                SecurityException error
        ) {

            showLocalNewsError(
                    "Precise location permission is needed for local news."
            );
        }
    }


    private boolean hasFineLocationPermission() {

        return ContextCompat
                .checkSelfPermission(

                        requireContext(),

                        Manifest.permission
                                .ACCESS_FINE_LOCATION
                )
                == PackageManager
                .PERMISSION_GRANTED;
    }


    private boolean isPreciseEnough(
            @Nullable Location location
    ) {

        if (
                location == null
        ) {

            return false;
        }

        if (
                location.hasAccuracy()

                        && location
                        .getAccuracy()
                        > MAX_NEWS_LOCATION_ACCURACY_METERS
        ) {

            return false;
        }

        return true;
    }


    private void loadLastKnownNewsLocation() {

        try {

            fusedLocationClient
                    .getLastLocation()
                    .addOnSuccessListener(
                            location -> {

                                if (!isAdded()) {
                                    return;
                                }

                                if (
                                        !isUsableLastKnownLocation(
                                                location
                                        )
                                ) {

                                    showLocalNewsError(
                                            "Could not get a precise location. Make sure Precise Location is enabled."
                                    );

                                    return;
                                }

                                resolveNewsArea(
                                        location
                                );
                            }
                    )
                    .addOnFailureListener(
                            error -> {

                                if (!isAdded()) {
                                    return;
                                }

                                showLocalNewsError(
                                        "Could not get a precise location."
                                );
                            }
                    );

        } catch (
                SecurityException error
        ) {

            showLocalNewsError(
                    "Precise location permission is needed for local news."
            );
        }
    }


    private boolean isUsableLastKnownLocation(
            @Nullable Location location
    ) {

        if (
                !isPreciseEnough(
                        location
                )
        ) {

            return false;
        }

        if (
                location == null
        ) {

            return false;
        }

        long age =
                System.currentTimeMillis()
                        - location
                        .getTime();

        return age >= 0

                && age
                <= MAX_LAST_LOCATION_AGE_MS;
    }


    // current location -> city

    private void resolveNewsArea(
            @NonNull Location location
    ) {

        final android.content.Context appContext =
                requireContext()
                        .getApplicationContext();

        geocoderExecutor.execute(
                () -> {

                    String city =
                            null;

                    String state =
                            null;

                    try {

                        Geocoder geocoder =
                                new Geocoder(
                                        appContext,
                                        Locale.US
                                );

                        List<Address> addresses =
                                geocoder
                                        .getFromLocation(

                                                location
                                                        .getLatitude(),

                                                location
                                                        .getLongitude(),

                                                5
                                        );

                        Address address =
                                findBestNewsAddress(
                                        addresses
                                );

                        if (
                                address != null
                        ) {

                            city =
                                    cleanLocationPart(
                                            address
                                                    .getLocality()
                                    );

                            if (
                                    city == null
                            ) {

                                city =
                                        cleanLocationPart(
                                                address
                                                        .getSubLocality()
                                        );
                            }

                            state =
                                    cleanLocationPart(
                                            address
                                                    .getAdminArea()
                                    );
                        }

                    } catch (
                            Exception error
                    ) {

                        Log.e(
                                TAG,
                                "Could not reverse-geocode current location",
                                error
                        );
                    }

                    final String finalCity =
                            city;

                    final String finalState =
                            state;

                    mainHandler.post(
                            () -> {

                                if (!isAdded()) {
                                    return;
                                }

                                if (
                                        finalCity == null

                                                || finalCity
                                                .trim()
                                                .isEmpty()
                                ) {

                                    showLocalNewsError(
                                            "Could not determine your city."
                                    );

                                    return;
                                }

                                String displayLocation =
                                        finalState == null

                                                ? finalCity

                                                : finalCity
                                                  + ", "
                                                  + finalState;

                                fetchNewsForArea(
                                        finalCity,
                                        displayLocation
                                );
                            }
                    );
                }
        );
    }


    @Nullable
    private Address findBestNewsAddress(
            @Nullable List<Address> addresses
    ) {

        if (
                addresses == null

                        || addresses
                        .isEmpty()
        ) {

            return null;
        }

        for (
                Address address
                : addresses
        ) {

            if (
                    cleanLocationPart(
                            address.getLocality()
                    ) != null
            ) {

                return address;
            }

        }

        for (
                Address address
                : addresses
        ) {

            if (
                    cleanLocationPart(
                            address.getSubLocality()
                    ) != null
            ) {

                return address;
            }
        }

        return addresses.get(
                0
        );
    }


    @Nullable
    private String cleanLocationPart(
            @Nullable String value
    ) {

        if (
                value == null
        ) {

            return null;
        }

        String cleaned =
                value.trim();

        return cleaned
                .isEmpty()

                ? null

                : cleaned;
    }


    // newsdata

    private void fetchNewsForArea(
            @NonNull String city,
            @NonNull String displayLocation
    ) {

        tvLocalNewsLocation
                .setText(
                        displayLocation
                );

        localNewsRepository
                .fetchLocalNews(

                        city,

                        new LocalNewsRepository.Callback() {

                            @Override
                            public void onSuccess(
                                    @NonNull
                                    List<LocalNewsRepository.Article>
                                            articles
                            ) {

                                if (!isAdded()) {
                                    return;
                                }

                                localNewsLoading =
                                        false;

                                localNewsLoaded =
                                        true;

                                renderLocalNews(
                                        articles
                                );
                            }


                            @Override
                            public void onError(
                                    @NonNull String message
                            ) {

                                if (!isAdded()) {
                                    return;
                                }

                                showLocalNewsError(
                                        message
                                );
                            }
                        }
                );
    }


    private void renderLocalNews(
            @NonNull
            List<LocalNewsRepository.Article> articles
    ) {

        localNewsContainer
                .removeAllViews();

        int count =
                articles.size();

        tvLocalNewsCount
                .setText(

                        count
                                + (
                                count == 1

                                        ? " story"

                                        : " stories"
                        )
                );

        localNewsEmptyLayout
                .setVisibility(

                        count == 0

                                ? View.VISIBLE

                                : View.GONE
                );

        for (
                LocalNewsRepository.Article article
                : articles
        ) {

            addLocalNewsCard(
                    article
            );
        }
    }


    private void addLocalNewsCard(
            @NonNull LocalNewsRepository.Article article
    ) {

        View cardView =
                LayoutInflater
                        .from(
                                requireContext()
                        )
                        .inflate(
                                R.layout.item_local_news,
                                localNewsContainer,
                                false
                        );

        MaterialCardView card =
                cardView.findViewById(
                        R.id.card_local_news
                );

        MaterialCardView iconCard =
                cardView.findViewById(
                        R.id.card_local_news_icon
                );

        ImageView icon =
                cardView.findViewById(
                        R.id.iv_local_news_icon
                );

        ImageView chevron =
                cardView.findViewById(
                        R.id.iv_local_news_chevron
                );

        TextView title =
                cardView.findViewById(
                        R.id.tv_local_news_title
                );

        TextView summary =
                cardView.findViewById(
                        R.id.tv_local_news_summary
                );

        TextView source =
                cardView.findViewById(
                        R.id.tv_local_news_source
                );

        TextView time =
                cardView.findViewById(
                        R.id.tv_local_news_time
                );

        title.setText(
                article.getTitle()
        );

        String description =
                article.getDescription();

        summary.setText(

                description == null

                        || description
                        .trim()
                        .isEmpty()

                        ? "Tap to read the full story."

                        : description.trim()
        );

        source.setText(
                article.getSourceName()
        );

        if (
                article.getPublishedAtMillis()
                        > 0
        ) {

            time.setText(
                    ReportDisplayUtils
                            .formatRelativeTime(
                                    article
                                            .getPublishedAtMillis()
                            )
            );

        } else {

            time.setText(
                    "Recently"
            );
        }

        LocalNewsType newsType =
                classifyLocalNews(
                        article
                );

        applyLocalNewsStyle(
                newsType,
                card,
                iconCard,
                icon,
                chevron,
                title,
                summary,
                source,
                time
        );

        cardView.setOnClickListener(
                v -> openNewsArticle(
                        article.getLink()
                )
        );

        localNewsContainer
                .addView(
                        cardView
                );
    }


    private LocalNewsType classifyLocalNews(
            @NonNull LocalNewsRepository.Article article
    ) {

        String content =
                (
                        safeLower(
                                article.getTitle()
                        )
                                + " "
                                + safeLower(
                                article.getDescription()
                        )
                );

        if (
                containsAny(
                        content,

                        "police",
                        "shooting",
                        "shot",
                        "arrest",
                        "arrests",
                        "suspect",
                        "crime",
                        "sheriff",
                        "firefighter",
                        "firefighters",
                        "structure fire",
                        "house fire",
                        "public safety",
                        "emergency"
                )
        ) {

            return LocalNewsType.PUBLIC_SAFETY;
        }

        if (
                containsAny(
                        content,

                        "power outage",
                        "outage",
                        "electric",
                        "electricity",
                        "water main",
                        "water outage",
                        "boil water",
                        "sewer",
                        "wastewater",
                        "utility",
                        "utilities"
                )
        ) {

            return LocalNewsType.UTILITIES;
        }

        if (
                containsAny(
                        content,

                        "crash",
                        "collision",
                        "accident",
                        "traffic",
                        "road",
                        "roadway",
                        "highway",
                        "interstate",
                        "i-35",
                        "lane",
                        "lanes",
                        "street",
                        "bridge",
                        "closure",
                        "closed",
                        "construction",
                        "roadwork",
                        "transit"
                )
        ) {

            return LocalNewsType.TRAFFIC;
        }

        if (
                containsAny(
                        content,

                        "storm",
                        "thunderstorm",
                        "tornado",
                        "hail",
                        "flood",
                        "flooding",
                        "weather",
                        "freeze",
                        "freezing",
                        "heat advisory",
                        "heat warning"
                )
        ) {

            return LocalNewsType.WEATHER;
        }

        if (
                containsAny(
                        content,

                        "environment",
                        "environmental",
                        "pollution",
                        "air quality",
                        "park",
                        "parks",
                        "trail",
                        "trails",
                        "conservation",
                        "drought",
                        "recycling",
                        "trash",
                        "waste",
                        "wildlife"
                )
        ) {

            return LocalNewsType.ENVIRONMENT;
        }

        return LocalNewsType.CIVIC;
    }


    private String safeLower(
            @Nullable String value
    ) {

        return value == null

                ? ""

                : value
                  .toLowerCase(
                          Locale.US
                  );
    }


    private boolean containsAny(
            @NonNull String content,
            @NonNull String... values
    ) {

        for (
                String value
                : values
        ) {

            if (
                    content.contains(
                            value
                    )
            ) {

                return true;
            }
        }

        return false;
    }


    private void applyLocalNewsStyle(
            @NonNull LocalNewsType type,
            @NonNull MaterialCardView card,
            @NonNull MaterialCardView iconCard,
            @NonNull ImageView icon,
            @NonNull ImageView chevron,
            @NonNull TextView title,
            @NonNull TextView summary,
            @NonNull TextView source,
            @NonNull TextView time
    ) {

        boolean dark =
                isDarkMode();

        int cardBackground =
                dark
                        ? Color.parseColor(
                        "#263449"
                )
                        : Color.WHITE;

        int cardStroke =
                dark
                        ? Color.parseColor(
                        "#43536B"
                )
                        : Color.parseColor(
                        "#DEE5ED"
                );

        int titleColor =
                dark
                        ? Color.parseColor(
                        "#F4F7FB"
                )
                        : Color.parseColor(
                        "#1F2937"
                );

        int bodyColor =
                dark
                        ? Color.parseColor(
                        "#CBD5E1"
                )
                        : Color.parseColor(
                        "#667085"
                );

        int metaColor =
                dark
                        ? Color.parseColor(
                        "#AEBBCB"
                )
                        : Color.parseColor(
                        "#7C8798"
                );

        int chevronColor =
                dark
                        ? Color.parseColor(
                        "#CAD4E0"
                )
                        : Color.parseColor(
                        "#98A2B3"
                );

        int iconBackground;

        int iconColor;

        int iconResource;

        switch (
                type
        ) {

            case PUBLIC_SAFETY:

                iconBackground =
                        dark
                                ? Color.parseColor(
                                "#4B282C"
                        )
                                : Color.parseColor(
                                "#FDE8E8"
                        );

                iconColor =
                        dark
                                ? Color.parseColor(
                                "#FF9D98"
                        )
                                : Color.parseColor(
                                "#B42318"
                        );

                iconResource =
                        R.drawable.ic_severity;

                break;


            case TRAFFIC:

                iconBackground =
                        dark
                                ? Color.parseColor(
                                "#4A351F"
                        )
                                : Color.parseColor(
                                "#FFF0D9"
                        );

                iconColor =
                        dark
                                ? Color.parseColor(
                                "#FFC46B"
                        )
                                : Color.parseColor(
                                "#A75A00"
                        );

                iconResource =
                        R.drawable.ic_news_traffic;

                break;


            case WEATHER:

                iconBackground =
                        dark
                                ? Color.parseColor(
                                "#213A59"
                        )
                                : Color.parseColor(
                                "#E5F0FF"
                        );

                iconColor =
                        dark
                                ? Color.parseColor(
                                "#8FBCFF"
                        )
                                : Color.parseColor(
                                "#2B63A8"
                        );

                iconResource =
                        R.drawable.ic_news_weather;

                break;


            case UTILITIES:

                iconBackground =
                        dark
                                ? Color.parseColor(
                                "#382D55"
                        )
                                : Color.parseColor(
                                "#EEE8FF"
                        );

                iconColor =
                        dark
                                ? Color.parseColor(
                                "#C5ACFF"
                        )
                                : Color.parseColor(
                                "#6941C6"
                        );

                iconResource =
                        R.drawable.ic_news_utility;

                break;


            case ENVIRONMENT:

                iconBackground =
                        dark
                                ? Color.parseColor(
                                "#233F2D"
                        )
                                : Color.parseColor(
                                "#E3F4E8"
                        );

                iconColor =
                        dark
                                ? Color.parseColor(
                                "#8CD69D"
                        )
                                : Color.parseColor(
                                "#27763D"
                        );

                iconResource =
                        R.drawable.ic_news_environment;

                break;


            case CIVIC:
            default:

                iconBackground =
                        dark
                                ? Color.parseColor(
                                "#303B4C"
                        )
                                : Color.parseColor(
                                "#E9EEF5"
                        );

                iconColor =
                        dark
                                ? Color.parseColor(
                                "#C3CEDC"
                        )
                                : Color.parseColor(
                                "#475467"
                        );

                iconResource =
                        R.drawable.ic_events;

                break;
        }

        card.setCardBackgroundColor(
                cardBackground
        );

        card.setStrokeColor(
                cardStroke
        );

        card.setStrokeWidth(
                dpToPx(
                        1
                )
        );

        iconCard.setCardBackgroundColor(
                iconBackground
        );

        icon.setImageResource(
                iconResource
        );

        icon.setImageTintList(
                ColorStateList.valueOf(
                        iconColor
                )
        );

        title.setTextColor(
                titleColor
        );

        summary.setTextColor(
                bodyColor
        );

        source.setTextColor(
                iconColor
        );

        time.setTextColor(
                metaColor
        );

        chevron.setImageTintList(
                ColorStateList.valueOf(
                        chevronColor
                )
        );
    }


    private int dpToPx(
            int dp
    ) {

        float density =
                getResources()
                        .getDisplayMetrics()
                        .density;

        return Math.round(
                dp * density
        );
    }


    private void openNewsArticle(
            @Nullable String link
    ) {

        if (
                link == null

                        || link
                        .trim()
                        .isEmpty()
        ) {

            return;
        }

        try {

            Intent intent =
                    new Intent(

                            Intent.ACTION_VIEW,

                            Uri.parse(
                                    link
                            )
                    );

            startActivity(
                    intent
            );

        } catch (
                Exception error
        ) {

            Toast.makeText(
                    requireContext(),
                    "Could not open article",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }


    private void showLocalNewsError(
            @NonNull String message
    ) {

        localNewsLoading =
                false;

        localNewsLoaded =
                false;

        if (
                localNewsContainer == null

                        || tvLocalNewsCount == null

                        || tvLocalNewsLocation == null

                        || localNewsEmptyLayout == null
        ) {

            return;
        }

        localNewsContainer
                .removeAllViews();

        tvLocalNewsCount
                .setText(
                        "0 stories"
                );

        tvLocalNewsLocation
                .setText(
                        message
                );

        localNewsEmptyLayout
                .setVisibility(
                        View.VISIBLE
                );
    }


    // internal models

    private enum LocalNewsType {

        PUBLIC_SAFETY,

        TRAFFIC,

        WEATHER,

        UTILITIES,

        ENVIRONMENT,

        CIVIC
    }


    private static class DistanceMatch {

        final ImportantLocation location;

        final double distanceMeters;

        DistanceMatch(
                ImportantLocation location,
                double distanceMeters
        ) {

            this.location =
                    location;

            this.distanceMeters =
                    distanceMeters;
        }
    }


    private static class NearbyEventItem {

        final String eventId;

        final String category;

        final String status;

        final double averageSeverity;

        final int uniqueUserCount;

        final int commentCount;

        final double latitude;

        final double longitude;

        final Timestamp createdAt;

        final Timestamp updatedAt;

        double displayDistanceMeters;

        String displayLocationName;

        NearbyEventItem(
                String eventId,
                String category,
                String status,
                double averageSeverity,
                int uniqueUserCount,
                int commentCount,
                double latitude,
                double longitude,
                Timestamp createdAt,
                Timestamp updatedAt
        ) {

            this.eventId =
                    eventId;

            this.category =
                    category;

            this.status =
                    status;

            this.averageSeverity =
                    averageSeverity;

            this.uniqueUserCount =
                    uniqueUserCount;

            this.commentCount =
                    commentCount;

            this.latitude =
                    latitude;

            this.longitude =
                    longitude;

            this.createdAt =
                    createdAt;

            this.updatedAt =
                    updatedAt;
        }
    }
}