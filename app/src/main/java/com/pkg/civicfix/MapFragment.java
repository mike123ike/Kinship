package com.pkg.civicfix;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.firebase.geofire.GeoFireUtils;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQueryBounds;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.pkg.civicfix.model.ImportantLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapFragment extends Fragment
        implements OnMapReadyCallback {

    private GoogleMap mMap;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    private FusedLocationProviderClient fusedClient;

    // event markers

    private final Map<String, Marker> drawnMarkers =
            new HashMap<>();

    private final Map<String, DocumentSnapshot> cachedEvents =
            new HashMap<>();

    private static final double QUERY_RADIUS_METERS =
            5000;

    private static final float ZOOM_MIN =
            10f;

    private static final float ZOOM_MAX =
            15f;

    private static final int MARKER_MAX_SIZE =
            120;

    private static final int MARKER_MIN_SIZE =
            40;

    // important location markers

    private static final String TAG_EVENT_PREFIX =
            "event:";

    private static final String TAG_LOCATION_PREFIX =
            "location:";

    private final Map<String, Marker> importantLocationMarkers =
            new HashMap<>();

    private final Map<String, ImportantLocation> cachedImportantLocations =
            new HashMap<>();

    // location markers resize instead of hiding when zooming out
    private static final float LOCATION_ZOOM_MIN =
            8f;

    private static final float LOCATION_ZOOM_MAX =
            16f;

    private static final float LOCATION_MIN_SIZE_DP =
            22f;

    private static final float LOCATION_MAX_SIZE_DP =
            40f;

    private static final float LOCATION_SELECTED_EXTRA_DP =
            6f;

    // prevent recreating bitmaps on tiny zoom changes
    private int lastImportantLocationBaseSizePx =
            -1;

    private String selectedImportantLocationId =
            null;
    private String pendingFocusEventId;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {

        return inflater.inflate(
                R.layout.fragment_map,
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

        db =
                FirebaseFirestore.getInstance();

        auth =
                FirebaseAuth.getInstance();

        fusedClient =
                LocationServices
                        .getFusedLocationProviderClient(
                                requireContext()
                        );

        SupportMapFragment mapFragment =
                (SupportMapFragment)
                        getChildFragmentManager()
                                .findFragmentById(
                                        R.id.map
                                );

        if (mapFragment != null) {

            mapFragment.getMapAsync(
                    this
            );
        }
    }

    @Override
    public void onMapReady(
            @NonNull GoogleMap googleMap
    ) {

        mMap =
                googleMap;

        applyMapStyle();

        mMap.getUiSettings()
                .setMapToolbarEnabled(
                        false
                );

        mMap.getUiSettings()
                .setMyLocationButtonEnabled(
                        false
                );

        // custom info window adapter for locations
        mMap.setInfoWindowAdapter(
                new ImportantLocationInfoWindowAdapter()
        );

        // handle marker clicks
        mMap.setOnMarkerClickListener(marker -> {

            Object rawTag =
                    marker.getTag();

            if (!(rawTag instanceof String)) {

                return true;
            }

            String tag =
                    (String) rawTag;

            // handle event click
            if (
                    tag.startsWith(
                            TAG_EVENT_PREFIX
                    )
            ) {

                clearImportantLocationSelection();

                String eventId =
                        tag.substring(
                                TAG_EVENT_PREFIX.length()
                        );

                showEventPopup(
                        eventId
                );

                return true;
            }

            // handle location click
            if (
                    tag.startsWith(
                            TAG_LOCATION_PREFIX
                    )
            ) {

                String locationId =
                        tag.substring(
                                TAG_LOCATION_PREFIX.length()
                        );

                selectImportantLocation(
                        locationId,
                        marker
                );

                marker.showInfoWindow();

                return true;
            }

            return true;
        });

        // handle map clicks outside markers
        mMap.setOnMapClickListener(latLng -> {

            clearImportantLocationSelection();

            Fragment eventPopup =
                    getChildFragmentManager()
                            .findFragmentByTag(
                                    "event_popup"
                            );

            if (
                    eventPopup
                            instanceof DialogFragment
            ) {

                ((DialogFragment) eventPopup)
                        .dismissAllowingStateLoss();
            }
        });

        // refresh events when camera stops moving
        mMap.setOnCameraIdleListener(
                this::loadEventsInView
        );

        // adjust marker sizes on zoom
        mMap.setOnCameraMoveListener(() -> {

            updateMarkerScales();

            updateImportantLocationMarkerScales();
        });

        loadImportantLocations();

        if (
                pendingFocusEventId != null
        ) {

            String eventId =
                    pendingFocusEventId;

            pendingFocusEventId =
                    null;

            focusOnEvent(
                    eventId
            );

        } else {

            centerOnUserLocation();
        }
    }

    public void showEventPopup(
            String eventId
    ) {

        if (
                !isAdded()
                        || eventId == null
                        || eventId.trim().isEmpty()
        ) {

            return;
        }

        Fragment existingPopup =
                getChildFragmentManager()
                        .findFragmentByTag(
                                "event_popup"
                        );

        if (existingPopup != null) {

            return;
        }

        EventPopupFragment popup =
                EventPopupFragment
                        .newInstance(
                                eventId
                        );

        popup.show(
                getChildFragmentManager(),
                "event_popup"
        );
    }

    private void loadImportantLocations() {

        if (
                mMap == null
                        || auth == null
        ) {

            return;
        }

        FirebaseUser user =
                auth.getCurrentUser();

        if (user == null) {

            clearImportantLocationMarkers();

            return;
        }

        db.collection("users")
                .document(
                        user.getUid()
                )
                .collection(
                        "importantLocations"
                )
                .get()
                .addOnSuccessListener(snapshot -> {

                    if (
                            getContext() == null
                                    || mMap == null
                    ) {

                        return;
                    }

                    clearImportantLocationMarkers();

                    for (
                            QueryDocumentSnapshot document
                            : snapshot
                    ) {

                        ImportantLocation location =
                                document.toObject(
                                        ImportantLocation.class
                                );

                        if (
                                location.getId() == null
                                        || location
                                        .getId()
                                        .isEmpty()
                        ) {

                            location.setId(
                                    document.getId()
                            );
                        }

                        cachedImportantLocations.put(
                                location.getId(),
                                location
                        );

                        drawImportantLocationMarker(
                                location,
                                false
                        );
                    }
                });
    }

    private void clearImportantLocationMarkers() {

        for (
                Marker marker
                : importantLocationMarkers.values()
        ) {

            marker.remove();
        }

        importantLocationMarkers.clear();

        cachedImportantLocations.clear();

        selectedImportantLocationId =
                null;

        lastImportantLocationBaseSizePx =
                -1;
    }

    private void drawImportantLocationMarker(
            ImportantLocation location,
            boolean selected
    ) {

        if (
                mMap == null
                        || location == null
                        || location.getId() == null
        ) {

            return;
        }

        int size =
                getImportantLocationMarkerSizePx(
                        selected
                );

        BitmapDescriptor icon =
                createImportantLocationMarkerBitmap(
                        location.getType(),
                        size
                );

        Marker marker =
                mMap.addMarker(
                        new MarkerOptions()
                                .position(
                                        new LatLng(
                                                location.getLatitude(),
                                                location.getLongitude()
                                        )
                                )
                                .icon(icon)
                                .anchor(
                                        0.5f,
                                        0.5f
                                )
                                .title(
                                        location.getName()
                                )
                                .snippet(
                                        getLocationAddress(
                                                location
                                        )
                                )
                                .zIndex(
                                        2f
                                )
                );

        if (marker == null) {

            return;
        }

        marker.setTag(
                TAG_LOCATION_PREFIX
                        + location.getId()
        );

        importantLocationMarkers.put(
                location.getId(),
                marker
        );
    }

    private void updateImportantLocationMarkerScales() {

        if (
                mMap == null
                        || importantLocationMarkers.isEmpty()
        ) {

            return;
        }

        int currentBaseSize =
                getImportantLocationBaseSizePx();

        // prevent recreating bitmaps on tiny zoom changes
        if (
                currentBaseSize
                        == lastImportantLocationBaseSizePx
        ) {

            return;
        }

        lastImportantLocationBaseSizePx =
                currentBaseSize;

        for (
                Map.Entry<String, Marker> entry
                : importantLocationMarkers.entrySet()
        ) {

            String locationId =
                    entry.getKey();

            Marker marker =
                    entry.getValue();

            ImportantLocation location =
                    cachedImportantLocations.get(
                            locationId
                    );

            if (location == null) {

                continue;
            }

            boolean selected =
                    locationId.equals(
                            selectedImportantLocationId
                    );

            marker.setIcon(
                    createImportantLocationMarkerBitmap(
                            location.getType(),
                            getImportantLocationMarkerSizePx(
                                    selected
                            )
                    )
            );
        }
    }

    private int getImportantLocationBaseSizePx() {

        if (mMap == null) {

            return dpToPx(
                    LOCATION_MAX_SIZE_DP
            );
        }

        float zoom =
                mMap.getCameraPosition()
                        .zoom;

        float sizeDp;

        if (
                zoom <= LOCATION_ZOOM_MIN
        ) {

            sizeDp =
                    LOCATION_MIN_SIZE_DP;

        } else if (
                zoom >= LOCATION_ZOOM_MAX
        ) {

            sizeDp =
                    LOCATION_MAX_SIZE_DP;

        } else {

            float progress =
                    (
                            zoom
                                    - LOCATION_ZOOM_MIN
                    )
                            / (
                            LOCATION_ZOOM_MAX
                                    - LOCATION_ZOOM_MIN
                    );

            sizeDp =
                    LOCATION_MIN_SIZE_DP
                            + progress
                            * (
                            LOCATION_MAX_SIZE_DP
                                    - LOCATION_MIN_SIZE_DP
                    );
        }

        return dpToPx(
                sizeDp
        );
    }

    private int getImportantLocationMarkerSizePx(
            boolean selected
    ) {

        int base =
                getImportantLocationBaseSizePx();

        if (!selected) {

            return base;
        }

        return base
                + dpToPx(
                LOCATION_SELECTED_EXTRA_DP
        );
    }

    private void selectImportantLocation(
            String locationId,
            Marker marker
    ) {

        // reset previously selected marker
        if (
                selectedImportantLocationId != null
                        && !selectedImportantLocationId
                        .equals(
                                locationId
                        )
        ) {

            Marker previousMarker =
                    importantLocationMarkers.get(
                            selectedImportantLocationId
                    );

            ImportantLocation previousLocation =
                    cachedImportantLocations.get(
                            selectedImportantLocationId
                    );

            if (
                    previousMarker != null
                            && previousLocation != null
            ) {

                previousMarker.hideInfoWindow();

                previousMarker.setIcon(
                        createImportantLocationMarkerBitmap(
                                previousLocation.getType(),
                                getImportantLocationMarkerSizePx(
                                        false
                                )
                        )
                );
            }
        }

        ImportantLocation selectedLocation =
                cachedImportantLocations.get(
                        locationId
                );

        if (selectedLocation == null) {

            return;
        }

        marker.setIcon(
                createImportantLocationMarkerBitmap(
                        selectedLocation.getType(),
                        getImportantLocationMarkerSizePx(
                                true
                        )
                )
        );

        selectedImportantLocationId =
                locationId;
    }

    private void clearImportantLocationSelection() {

        if (
                selectedImportantLocationId == null
        ) {

            return;
        }

        Marker marker =
                importantLocationMarkers.get(
                        selectedImportantLocationId
                );

        ImportantLocation location =
                cachedImportantLocations.get(
                        selectedImportantLocationId
                );

        if (
                marker != null
                        && location != null
        ) {

            marker.hideInfoWindow();

            marker.setIcon(
                    createImportantLocationMarkerBitmap(
                            location.getType(),
                            getImportantLocationMarkerSizePx(
                                    false
                            )
                    )
            );
        }

        selectedImportantLocationId =
                null;
    }

    private BitmapDescriptor
    createImportantLocationMarkerBitmap(
            String type,
            int size
    ) {

        int backgroundColor =
                getImportantLocationBackgroundColor(
                        type
                );

        int iconColor =
                getImportantLocationIconColor(
                        type
                );

        int iconRes =
                getImportantLocationIcon(
                        type
                );

        Bitmap bitmap =
                Bitmap.createBitmap(
                        size,
                        size,
                        Bitmap.Config.ARGB_8888
                );

        Canvas canvas =
                new Canvas(
                        bitmap
                );

        Paint fillPaint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                );

        fillPaint.setColor(
                backgroundColor
        );

        fillPaint.setStyle(
                Paint.Style.FILL
        );

        float inset =
                size * 0.04f;

        RectF rect =
                new RectF(
                        inset,
                        inset,
                        size - inset,
                        size - inset
                );

        float radius =
                size * 0.22f;

        canvas.drawRoundRect(
                rect,
                radius,
                radius,
                fillPaint
        );

        // draw border
        Paint borderPaint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                );

        borderPaint.setColor(
                Color.argb(
                        70,
                        0,
                        0,
                        0
                )
        );

        borderPaint.setStyle(
                Paint.Style.STROKE
        );

        borderPaint.setStrokeWidth(
                Math.max(
                        1f,
                        size * 0.025f
                )
        );

        canvas.drawRoundRect(
                rect,
                radius,
                radius,
                borderPaint
        );

        // draw icon
        Drawable icon =
                ContextCompat.getDrawable(
                        requireContext(),
                        iconRes
                );

        if (icon != null) {

            icon.mutate();

            icon.setColorFilter(
                    iconColor,
                    PorterDuff.Mode.SRC_IN
            );

            int iconSize =
                    (int) (
                            size * 0.52f
                    );

            int left =
                    (
                            size
                                    - iconSize
                    ) / 2;

            int top =
                    (
                            size
                                    - iconSize
                    ) / 2;

            icon.setBounds(
                    left,
                    top,
                    left + iconSize,
                    top + iconSize
            );

            icon.draw(
                    canvas
            );
        }

        return BitmapDescriptorFactory
                .fromBitmap(
                        bitmap
                );
    }

    private int getImportantLocationBackgroundColor(
            String type
    ) {

        if (
                ImportantLocation.TYPE_HOME
                        .equals(type)
        ) {

            return Color.parseColor(
                    "#BCF0AE"
            );
        }

        if (
                ImportantLocation.TYPE_SCHOOL
                        .equals(type)
        ) {

            return Color.parseColor(
                    "#FFDDB8"
            );
        }

        // work and custom default
        return Color.parseColor(
                "#DDE5DB"
        );
    }

    private int getImportantLocationIconColor(
            String type
    ) {

        if (
                ImportantLocation.TYPE_HOME
                        .equals(type)
        ) {

            return ContextCompat.getColor(
                    requireContext(),
                    R.color.profileLocationHomeIcon
            );
        }

        return ContextCompat.getColor(
                requireContext(),
                R.color.profileLocationNeutralIcon
        );
    }

    private int getImportantLocationIcon(
            String type
    ) {

        if (
                ImportantLocation.TYPE_HOME
                        .equals(type)
        ) {

            return R.drawable.ic_profile_home;
        }

        if (
                ImportantLocation.TYPE_WORK
                        .equals(type)
        ) {

            return R.drawable.ic_profile_work;
        }

        if (
                ImportantLocation.TYPE_SCHOOL
                        .equals(type)
        ) {

            return R.drawable.ic_profile_school;
        }

        return R.drawable.ic_location;
    }

    private String getLocationAddress(
            ImportantLocation location
    ) {

        String address =
                location.getDisplayAddress();

        if (
                address == null
                        || address.trim()
                        .isEmpty()
        ) {

            return "Saved location";
        }

        return address;
    }

    private int dpToPx(
            float dp
    ) {

        float density =
                getResources()
                        .getDisplayMetrics()
                        .density;

        return Math.round(
                dp * density
        );
    }

    private class ImportantLocationInfoWindowAdapter
            implements GoogleMap.InfoWindowAdapter {

        @Nullable
        @Override
        public View getInfoWindow(
                @NonNull Marker marker
        ) {

            Object rawTag =
                    marker.getTag();

            if (!(rawTag instanceof String)) {

                return null;
            }

            String tag =
                    (String) rawTag;

            if (
                    !tag.startsWith(
                            TAG_LOCATION_PREFIX
                    )
            ) {

                // skip events
                return null;
            }

            View view =
                    getLayoutInflater()
                            .inflate(
                                    R.layout.map_important_location_info_window,
                                    null
                            );

            TextView tvName =
                    view.findViewById(
                            R.id.tv_info_location_name
                    );

            TextView tvAddress =
                    view.findViewById(
                            R.id.tv_info_location_address
                    );

            tvName.setText(
                    marker.getTitle()
            );

            String snippet =
                    marker.getSnippet();

            if (
                    snippet == null
                            || snippet.isEmpty()
            ) {

                tvAddress.setVisibility(
                        View.GONE
                );

            } else {

                tvAddress.setVisibility(
                        View.VISIBLE
                );

                tvAddress.setText(
                        snippet
                );
            }

            return view;
        }

        // keep null to avoid default frame overlay
        @Nullable
        @Override
        public View getInfoContents(
                @NonNull Marker marker
        ) {

            return null;
        }
    }

    private void centerOnUserLocation() {

        if (
                ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION
                )
                        != PackageManager.PERMISSION_GRANTED
        ) {

            mMap.moveCamera(
                    CameraUpdateFactory
                            .newLatLngZoom(
                                    new LatLng(
                                            37.4219983,
                                            -122.084
                                    ),
                                    13
                            )
            );

            loadEventsInView();

            return;
        }

        mMap.setMyLocationEnabled(
                true
        );

        fusedClient.getLastLocation()
                .addOnSuccessListener(location -> {

                    if (
                            getContext() == null
                    ) {

                        return;
                    }

                    if (location != null) {

                        LatLng userLatLng =
                                new LatLng(
                                        location.getLatitude(),
                                        location.getLongitude()
                                );

                        mMap.moveCamera(
                                CameraUpdateFactory
                                        .newLatLngZoom(
                                                userLatLng,
                                                13
                                        )
                        );

                        loadEventsInView();
                    }
                });
    }

    public void focusOnEvent(
            String eventId
    ) {

        if (
                eventId == null
                        || eventId.isEmpty()
        ) {

            return;
        }

        // map may not have completed onmapready yet
        if (mMap == null) {

            pendingFocusEventId =
                    eventId;

            return;
        }

        clearImportantLocationSelection();

        db.collection("events")
                .document(eventId)
                .get()
                .addOnSuccessListener(document -> {

                    if (
                            !isAdded()
                                    || mMap == null
                                    || !document.exists()
                    ) {

                        return;
                    }


                    // pending events are unconfirmed and should not be shown on the map
                    String status =
                            document.getString(
                                    "status"
                            );

                    if (
                            status == null
                                    || "PENDING"
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

                    LatLng eventLocation =
                            new LatLng(
                                    latitude,
                                    longitude
                            );


                    // zoom close enough for the event marker to be visible
                    mMap.moveCamera(
                            CameraUpdateFactory
                                    .newLatLngZoom(
                                            eventLocation,
                                            16f
                                    )
                    );


                    cachedEvents.put(
                            eventId,
                            document
                    );


                    // draw explicit marker if not already present
                    if (
                            !drawnMarkers
                                    .containsKey(
                                            eventId
                                    )
                    ) {

                        drawEventMarker(
                                document,
                                latitude,
                                longitude
                        );

                    } else {

                        Marker existing =
                                drawnMarkers.get(
                                        eventId
                                );

                        if (existing != null) {

                            existing.setVisible(
                                    true
                            );
                        }
                    }


                    showEventPopup(
                            eventId
                    );
                });
    }

    private void loadEventsInView() {

        if (
                mMap == null
                        || getContext() == null
        ) {
            return;
        }

        LatLng center =
                mMap.getCameraPosition()
                        .target;

        GeoLocation centerGeo =
                new GeoLocation(
                        center.latitude,
                        center.longitude
                );

        List<GeoQueryBounds> bounds =
                GeoFireUtils
                        .getGeoHashQueryBounds(
                                centerGeo,
                                QUERY_RADIUS_METERS
                        );

        List<com.google.android.gms.tasks.Task<QuerySnapshot>>
                tasks =
                new ArrayList<>();

        for (
                GeoQueryBounds bound
                : bounds
        ) {

            Query query =
                    db.collection("events")
                            .whereEqualTo(
                                    "status",
                                    "ACTIVE"
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

            tasks.add(
                    query.get()
            );
        }

        com.google.android.gms.tasks.Tasks
                .whenAllComplete(
                        tasks
                )
                .addOnCompleteListener(
                        completedTask -> {

                            if (
                                    getContext() == null
                            ) {
                                return;
                            }

                            for (
                                    com.google.android.gms.tasks
                                            .Task<QuerySnapshot> task
                                    : tasks
                            ) {

                                if (
                                        !task.isSuccessful()
                                                || task.getResult()
                                                == null
                                ) {
                                    continue;
                                }

                                for (
                                        DocumentSnapshot doc
                                        : task.getResult()
                                        .getDocuments()
                                ) {

                                    // already on map
                                    if (
                                            drawnMarkers
                                                    .containsKey(
                                                            doc.getId()
                                                    )
                                    ) {
                                        continue;
                                    }

                                    Double lat =
                                            doc.getDouble(
                                                    "latitude"
                                            );

                                    Double lng =
                                            doc.getDouble(
                                                    "longitude"
                                            );

                                    if (
                                            lat == null
                                                    || lng == null
                                    ) {
                                        continue;
                                    }

                                    // trim rectangular bounds to circular radius
                                    double distance =
                                            GeoFireUtils
                                                    .getDistanceBetween(
                                                            centerGeo,
                                                            new GeoLocation(
                                                                    lat,
                                                                    lng
                                                            )
                                                    );

                                    if (
                                            distance
                                                    > QUERY_RADIUS_METERS
                                    ) {
                                        continue;
                                    }

                                    cachedEvents.put(
                                            doc.getId(),
                                            doc
                                    );

                                    drawEventMarker(
                                            doc,
                                            lat,
                                            lng
                                    );
                                }
                            }
                        }
                );
    }

    private void drawEventMarker(
            DocumentSnapshot doc,
            double lat,
            double lng
    ) {

        if (mMap == null) {
            return;
        }

        String eventId =
                doc.getId();

        String category =
                doc.getString(
                        "category"
                );

        Double averageSeverity =
                doc.getDouble(
                        "averageSeverity"
                );

        if (averageSeverity == null) {

            averageSeverity =
                    1.0;
        }

        int circleColor =
                getSeverityColor(
                        averageSeverity
                );

        int iconRes =
                getCategoryIcon(
                        category
                );

        float zoom =
                mMap.getCameraPosition()
                        .zoom;

        int markerSize =
                getMarkerSize(
                        zoom
                );

        // hide marker when zoomed out
        if (markerSize <= 0) {
            return;
        }

        BitmapDescriptor markerBitmap =
                createMarkerBitmap(
                        circleColor,
                        iconRes,
                        markerSize
                );

        MarkerOptions options =
                new MarkerOptions()
                        .position(
                                new LatLng(
                                        lat,
                                        lng
                                )
                        )
                        .icon(
                                markerBitmap
                        )
                        .anchor(
                                0.5f,
                                0.5f
                        )
                        // keep event markers below important locations
                        .zIndex(
                                1f
                        );

        Marker marker =
                mMap.addMarker(
                        options
                );

        if (marker != null) {

            // tag prefix distinguishes event markers
            marker.setTag(
                    TAG_EVENT_PREFIX
                            + eventId
            );

            drawnMarkers.put(
                    eventId,
                    marker
            );
        }
    }

    private void updateMarkerScales() {

        if (
                mMap == null
                        || getContext() == null
        ) {
            return;
        }

        float zoom =
                mMap.getCameraPosition()
                        .zoom;

        int markerSize =
                getMarkerSize(
                        zoom
                );

        for (
                Map.Entry<String, Marker> entry
                : drawnMarkers.entrySet()
        ) {

            Marker marker =
                    entry.getValue();

            DocumentSnapshot doc =
                    cachedEvents.get(
                            entry.getKey()
                    );

            if (doc == null) {
                continue;
            }

            // hide event markers when zoomed too far out
            if (markerSize <= 0) {

                marker.setVisible(
                        false
                );

                continue;
            }

            marker.setVisible(
                    true
            );

            String category =
                    doc.getString(
                            "category"
                    );

            Double averageSeverity =
                    doc.getDouble(
                            "averageSeverity"
                    );

            if (averageSeverity == null) {

                averageSeverity =
                        1.0;
            }

            int circleColor =
                    getSeverityColor(
                            averageSeverity
                    );

            int iconRes =
                    getCategoryIcon(
                            category
                    );

            marker.setIcon(
                    createMarkerBitmap(
                            circleColor,
                            iconRes,
                            markerSize
                    )
            );
        }
    }

    private int getMarkerSize(
            float zoom
    ) {

        if (
                zoom < ZOOM_MIN
        ) {

            return 0;
        }

        if (
                zoom >= ZOOM_MAX
        ) {

            return MARKER_MAX_SIZE;
        }

        float progress =
                (
                        zoom
                                - ZOOM_MIN
                )
                        / (
                        ZOOM_MAX
                                - ZOOM_MIN
                );

        return (int) (
                MARKER_MIN_SIZE
                        + progress
                        * (
                        MARKER_MAX_SIZE
                                - MARKER_MIN_SIZE
                )
        );
    }

    private BitmapDescriptor createMarkerBitmap(
            int circleColor,
            int iconRes,
            int size
    ) {

        int iconSize =
                (int) (
                        size * 0.47f
                );

        Bitmap bitmap =
                Bitmap.createBitmap(
                        size,
                        size,
                        Bitmap.Config.ARGB_8888
                );

        Canvas canvas =
                new Canvas(
                        bitmap
                );


        // circular severity background
        Paint circlePaint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                );

        circlePaint.setColor(
                circleColor
        );

        circlePaint.setStyle(
                Paint.Style.FILL
        );

        canvas.drawCircle(
                size / 2f,
                size / 2f,
                size / 2f,
                circlePaint
        );


        // white outline
        Paint borderPaint =
                new Paint(
                        Paint.ANTI_ALIAS_FLAG
                );

        borderPaint.setColor(
                Color.WHITE
        );

        borderPaint.setStyle(
                Paint.Style.STROKE
        );

        borderPaint.setStrokeWidth(
                size * 0.05f
        );

        canvas.drawCircle(
                size / 2f,
                size / 2f,
                (
                        size / 2f
                ) - 3,
                borderPaint
        );


        // event category icon
        Drawable icon =
                ContextCompat.getDrawable(
                        requireContext(),
                        iconRes
                );

        if (icon != null) {

            icon.mutate();

            icon.setColorFilter(
                    Color.WHITE,
                    PorterDuff.Mode.SRC_IN
            );

            int left =
                    (
                            size
                                    - iconSize
                    ) / 2;

            int top =
                    (
                            size
                                    - iconSize
                    ) / 2;

            icon.setBounds(
                    left,
                    top,
                    left + iconSize,
                    top + iconSize
            );

            icon.draw(
                    canvas
            );
        }

        return BitmapDescriptorFactory
                .fromBitmap(
                        bitmap
                );
    }

    private int getSeverityColor(
            double severity
    ) {

        if (
                severity <= 3
        ) {

            return Color.parseColor(
                    "#4CAF50"
            );
        }

        if (
                severity <= 6
        ) {

            return Color.parseColor(
                    "#FFC107"
            );
        }

        if (
                severity <= 9
        ) {

            return Color.parseColor(
                    "#FF5722"
            );
        }

        return Color.parseColor(
                "#F44336"
        );
    }

    private int getCategoryIcon(
            String category
    ) {

        if (category == null) {

            return R.drawable.ic_report;
        }

        switch (category) {

            case "ROAD":

                return R.drawable.ic_road;

            case "LIGHTING":

                return R.drawable.ic_lighting;

            case "HAZARDS":

                return R.drawable.ic_hazards;

            case "VANDALISM":

                return R.drawable.ic_vandalism;

            case "SAFETY":

                return R.drawable.ic_shield;

            default:

                return R.drawable.ic_report;
        }
    }

    private void applyMapStyle() {

        int nightMode =
                getResources()
                        .getConfiguration()
                        .uiMode
                        & android.content.res
                        .Configuration
                        .UI_MODE_NIGHT_MASK;

        if (
                nightMode
                        == android.content.res
                        .Configuration
                        .UI_MODE_NIGHT_YES
        ) {

            mMap.setMapStyle(
                    MapStyleOptions
                            .loadRawResourceStyle(
                                    requireContext(),
                                    R.raw.map_style_dark
                            )
            );
        }
    }
}