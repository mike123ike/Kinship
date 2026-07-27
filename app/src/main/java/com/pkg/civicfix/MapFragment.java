package com.pkg.civicfix;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapFragment extends Fragment
        implements OnMapReadyCallback {

    private GoogleMap mMap;
    private FirebaseFirestore db;
    private FusedLocationProviderClient fusedClient;

    // Cache to avoid redrawing markers already on the map.
    private final Map<String, Marker> drawnMarkers =
            new HashMap<>();

    // Store raw event data for zoom-based redrawing
    // without re-querying Firestore.
    private final Map<String, DocumentSnapshot> cachedEvents =
            new HashMap<>();

    private static final double QUERY_RADIUS_METERS = 5000;

    // Zoom thresholds for marker scaling.
    private static final float ZOOM_MIN = 10f;
    private static final float ZOOM_MAX = 15f;
    private static final int MARKER_MAX_SIZE = 120;
    private static final int MARKER_MIN_SIZE = 40;

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
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();

        fusedClient =
                LocationServices.getFusedLocationProviderClient(
                        requireContext()
                );

        SupportMapFragment mapFragment =
                (SupportMapFragment)
                        getChildFragmentManager()
                                .findFragmentById(R.id.map);

        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(
            @NonNull GoogleMap googleMap
    ) {
        mMap = googleMap;

        applyMapStyle();

        mMap.getUiSettings()
                .setMapToolbarEnabled(false);

        mMap.getUiSettings()
                .setMyLocationButtonEnabled(false);

        // Show popup when a marker is tapped.
        mMap.setOnMarkerClickListener(marker -> {
            String eventId = (String) marker.getTag();

            if (eventId != null) {
                showEventPopup(eventId);
            }

            return true;
        });

        // Requery events when the user stops moving the map.
        mMap.setOnCameraIdleListener(
                this::loadEventsInView
        );

        // Rescale markers while the zoom level changes.
        mMap.setOnCameraMoveListener(
                this::updateMarkerScales
        );

        centerOnUserLocation();
    }

    public void showEventPopup(String eventId) {
        if (!isAdded()
                || eventId == null
                || eventId.trim().isEmpty()) {
            return;
        }

        Fragment existingPopup =
                getChildFragmentManager()
                        .findFragmentByTag("event_popup");

        if (existingPopup != null) {
            return;
        }

        EventPopupFragment popup =
                EventPopupFragment.newInstance(eventId);

        popup.show(
                getChildFragmentManager(),
                "event_popup"
        );
    }

    // Centers the map on the GPS location, then loads events.
    private void centerOnUserLocation() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED) {

            mMap.moveCamera(
                    CameraUpdateFactory.newLatLngZoom(
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

        mMap.setMyLocationEnabled(true);

        fusedClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (getContext() == null) {
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
                                                14
                                        )
                        );
                    } else {
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
                    }

                    loadEventsInView();
                })
                .addOnFailureListener(error -> {
                    if (getContext() == null) {
                        return;
                    }

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
                });
    }

    // Queries Firestore for active events in the current map area.
    private void loadEventsInView() {
        if (mMap == null || getContext() == null) {
            return;
        }

        LatLng center =
                mMap.getCameraPosition().target;

        GeoLocation centerGeo =
                new GeoLocation(
                        center.latitude,
                        center.longitude
                );

        List<GeoQueryBounds> bounds =
                GeoFireUtils.getGeoHashQueryBounds(
                        centerGeo,
                        QUERY_RADIUS_METERS
                );

        List<com.google.android.gms.tasks.Task<QuerySnapshot>>
                tasks = new ArrayList<>();

        for (GeoQueryBounds bound : bounds) {
            Query query = db.collection("events")
                    .whereEqualTo("status", "ACTIVE")
                    .orderBy("geohash")
                    .startAt(bound.startHash)
                    .endAt(bound.endHash);

            tasks.add(query.get());
        }

        com.google.android.gms.tasks.Tasks
                .whenAllComplete(tasks)
                .addOnCompleteListener(completedTask -> {
                    if (getContext() == null) {
                        return;
                    }

                    for (
                            com.google.android.gms.tasks
                                    .Task<QuerySnapshot> task
                            : tasks
                    ) {
                        if (!task.isSuccessful()
                                || task.getResult() == null) {
                            continue;
                        }

                        for (
                                DocumentSnapshot doc
                                : task.getResult()
                                .getDocuments()
                        ) {
                            if (drawnMarkers.containsKey(
                                    doc.getId()
                            )) {
                                continue;
                            }

                            Double lat =
                                    doc.getDouble("latitude");

                            Double lng =
                                    doc.getDouble("longitude");

                            if (lat == null || lng == null) {
                                continue;
                            }

                            //trims geohash's rectangular bounds to a circle
                            double distance =
                                    GeoFireUtils
                                            .getDistanceBetween(
                                                    centerGeo,
                                                    new GeoLocation(
                                                            lat,
                                                            lng
                                                    )
                                            );

                            if (distance
                                    > QUERY_RADIUS_METERS) {
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
                });
    }

    // Draws one event marker at the supplied coordinates.
    private void drawEventMarker(
            DocumentSnapshot doc,
            double lat,
            double lng
    ) {
        String eventId = doc.getId();

        String category =
                doc.getString("category");

        Double averageSeverity =
                doc.getDouble("averageSeverity");

        if (averageSeverity == null) {
            averageSeverity = 1.0;
        }

        int circleColor =
                getSeverityColor(averageSeverity);

        int iconRes =
                getCategoryIcon(category);

        float zoom =
                mMap.getCameraPosition().zoom;

        int markerSize =
                getMarkerSize(zoom);

        // Do not draw markers below the minimum zoom.
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
                                new LatLng(lat, lng)
                        )
                        .icon(markerBitmap)
                        .anchor(0.5f, 0.5f);

        Marker marker =
                mMap.addMarker(options);

        if (marker != null) {
            marker.setTag(eventId);
            drawnMarkers.put(eventId, marker);
        }
    }

    // Rescales all visible markers as the user zooms.
    private void updateMarkerScales() {
        if (mMap == null || getContext() == null) {
            return;
        }

        float zoom =
                mMap.getCameraPosition().zoom;

        int markerSize =
                getMarkerSize(zoom);

        for (
                Map.Entry<String, Marker> entry
                : drawnMarkers.entrySet()
        ) {
            Marker marker = entry.getValue();

            DocumentSnapshot doc =
                    cachedEvents.get(entry.getKey());

            if (doc == null) {
                continue;
            }

            if (markerSize <= 0) {
                marker.setVisible(false);
                continue;
            }

            marker.setVisible(true);

            String category =
                    doc.getString("category");

            Double averageSeverity =
                    doc.getDouble("averageSeverity");

            if (averageSeverity == null) {
                averageSeverity = 1.0;
            }

            int circleColor =
                    getSeverityColor(averageSeverity);

            int iconRes =
                    getCategoryIcon(category);

            marker.setIcon(
                    createMarkerBitmap(
                            circleColor,
                            iconRes,
                            markerSize
                    )
            );
        }
    }

    //Calculates marker size based on zoom
    private int getMarkerSize(float zoom) {
        if (zoom < ZOOM_MIN) {
            return 0;
        }

        if (zoom >= ZOOM_MAX) {
            return MARKER_MAX_SIZE;
        }

        float progress =
                (zoom - ZOOM_MIN)
                        / (ZOOM_MAX - ZOOM_MIN);

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
                (int) (size * 0.47f);

        Bitmap bitmap =
                Bitmap.createBitmap(
                        size,
                        size,
                        Bitmap.Config.ARGB_8888
                );

        Canvas canvas =
                new Canvas(bitmap);

        Paint circlePaint =
                new Paint(Paint.ANTI_ALIAS_FLAG);

        circlePaint.setColor(circleColor);
        circlePaint.setStyle(Paint.Style.FILL);

        canvas.drawCircle(
                size / 2f,
                size / 2f,
                size / 2f,
                circlePaint
        );

        Paint borderPaint =
                new Paint(Paint.ANTI_ALIAS_FLAG);

        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(size * 0.05f);

        canvas.drawCircle(
                size / 2f,
                size / 2f,
                (size / 2f) - 3,
                borderPaint
        );

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
                    (size - iconSize) / 2;

            int top =
                    (size - iconSize) / 2;

            icon.setBounds(
                    left,
                    top,
                    left + iconSize,
                    top + iconSize
            );

            icon.draw(canvas);
        }

        return BitmapDescriptorFactory
                .fromBitmap(bitmap);
    }

    // Maps the severity value to a marker color.
    private int getSeverityColor(double severity) {
        if (severity <= 3) {
            return Color.parseColor("#4CAF50");
        }

        if (severity <= 6) {
            return Color.parseColor("#FFC107");
        }

        if (severity <= 9) {
            return Color.parseColor("#FF5722");
        }

        return Color.parseColor("#F44336");
    }

    private int getCategoryIcon(String category) {
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
                        & android.content.res.Configuration
                        .UI_MODE_NIGHT_MASK;

        if (
                nightMode
                        == android.content.res.Configuration
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