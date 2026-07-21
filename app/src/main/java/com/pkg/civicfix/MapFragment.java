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


public class MapFragment extends Fragment implements OnMapReadyCallback {


    private GoogleMap mMap;
    private FirebaseFirestore db;
    private FusedLocationProviderClient fusedClient;


    // cache to avoid redrawing markers that are already on the map
    private final Map<String, Marker> drawnMarkers = new HashMap<>();


    // store raw event data so we can redraw markers when zoom changes
    private final Map<String, DocumentSnapshot> cachedEvents = new HashMap<>();


    private static final double QUERY_RADIUS_METERS = 5000;


    // zoom thresholds for marker scaling
    private static final float ZOOM_MIN = 9f;   // threshold for when markers are hidden
    private static final float ZOOM_MAX = 15f;   // threshold for when markers are full size
    private static final int MARKER_MAX_SIZE = 120; // max marker size in pixels
    private static final int MARKER_MIN_SIZE = 40;  // min marker size before hiding


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_map, container, false);
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        db = FirebaseFirestore.getInstance();
        fusedClient = LocationServices.getFusedLocationProviderClient(requireContext());


        SupportMapFragment mapFragment = (SupportMapFragment)
                getChildFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }


    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;


        applyMapStyle();


        mMap.getUiSettings().setMapToolbarEnabled(false);
        mMap.getUiSettings().setMyLocationButtonEnabled(false);


        // center on user location and also triggers first event load
        centerOnUserLocation();


        // requery events whenever the user stops moving the map
        mMap.setOnCameraIdleListener(this::loadEventsInView);


        // rescale markers when zoom changes
        mMap.setOnCameraMoveListener(this::updateMarkerScales);
    }


    // centers map on gps location
    private void centerOnUserLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(37.4219983, -122.084), 13));
            loadEventsInView();
            return;
        }


        mMap.setMyLocationEnabled(true);


        fusedClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (getContext() == null) return;
                    if (location != null) {
                        LatLng userLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 14));
                    } else {
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                new LatLng(37.4219983, -122.084), 13));
                    }
                    loadEventsInView();
                })
                .addOnFailureListener(e -> {
                    if (getContext() == null) return;
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                            new LatLng(37.4219983, -122.084), 13));
                    loadEventsInView();
                });
    }


    // queries db for events in current map view
    private void loadEventsInView() {
        if (mMap == null || getContext() == null) return;


        LatLng center = mMap.getCameraPosition().target;
        GeoLocation centerGeo = new GeoLocation(center.latitude, center.longitude);


        List<GeoQueryBounds> bounds =
                GeoFireUtils.getGeoHashQueryBounds(centerGeo, QUERY_RADIUS_METERS);


        List<com.google.android.gms.tasks.Task<QuerySnapshot>> tasks = new ArrayList<>();


        for (GeoQueryBounds bound : bounds) {
            // query both ACTIVE and PENDING events
            Query q = db.collection("events")
                    .orderBy("geohash")
                    .startAt(bound.startHash)
                    .endAt(bound.endHash);
            tasks.add(q.get());
        }


        com.google.android.gms.tasks.Tasks.whenAllComplete(tasks)
                .addOnCompleteListener(t -> {
                    if (getContext() == null) return;


                    for (com.google.android.gms.tasks.Task<QuerySnapshot> task : tasks) {
                        if (!task.isSuccessful()) continue;
                        for (DocumentSnapshot doc : task.getResult().getDocuments()) {


                            // skip if already drawn
                            if (drawnMarkers.containsKey(doc.getId())) continue;


                            Double lat = doc.getDouble("latitude");
                            Double lng = doc.getDouble("longitude");
                            if (lat == null || lng == null) continue;


                            // filter to actual radius and since geohash gives a square so trim to circle
                            double distance = GeoFireUtils.getDistanceBetween(
                                    centerGeo, new GeoLocation(lat, lng));
                            if (distance > QUERY_RADIUS_METERS) continue;


                            // cache event data for  redrawing
                            cachedEvents.put(doc.getId(), doc);


                            drawEventMarker(doc, lat, lng);
                        }
                    }
                });
    }


    // draws a single event marker at the given coordinates
    private void drawEventMarker(DocumentSnapshot doc, double lat, double lng) {
        String eventId = doc.getId();
        String status = doc.getString("status");
        String category = doc.getString("category");
        Double avgSeverity = doc.getDouble("averageSeverity");
        if (avgSeverity == null) avgSeverity = 1.0;


        boolean isPending = "PENDING".equals(status);


        int circleColor = isPending ? Color.parseColor("#555555") : getSeverityColor(avgSeverity);
        int iconRes = getCategoryIcon(category);
        float zoom = mMap.getCameraPosition().zoom;
        int markerSize = getMarkerSize(zoom);


        // hide marker if below zoom threshold
        if (markerSize <= 0) return;


        BitmapDescriptor markerBitmap = createMarkerBitmap(circleColor, iconRes, markerSize);


        MarkerOptions options = new MarkerOptions()
                .position(new LatLng(lat, lng))
                .icon(markerBitmap)
                .anchor(0.5f, 0.5f);


        Marker marker = mMap.addMarker(options);
        if (marker != null) {
            marker.setTag(eventId);
            drawnMarkers.put(eventId, marker);
        }
    }


    // rescales all visible markers when the user zooms in or out
    private void updateMarkerScales() {
        if (mMap == null || getContext() == null) return;


        float zoom = mMap.getCameraPosition().zoom;
        int markerSize = getMarkerSize(zoom);


        for (Map.Entry<String, Marker> entry : drawnMarkers.entrySet()) {
            Marker marker = entry.getValue();
            DocumentSnapshot doc = cachedEvents.get(entry.getKey());
            if (doc == null) continue;


            if (markerSize <= 0) {
                // hide below minimum zoom
                marker.setVisible(false);
            } else {
                marker.setVisible(true);
                String status = doc.getString("status");
                String category = doc.getString("category");
                Double avgSeverity = doc.getDouble("averageSeverity");
                if (avgSeverity == null) avgSeverity = 1.0;


                boolean isPending = "PENDING".equals(status);
                int circleColor = isPending
                        ? Color.parseColor("#555555")
                        : getSeverityColor(avgSeverity);
                int iconRes = getCategoryIcon(category);


                marker.setIcon(createMarkerBitmap(circleColor, iconRes, markerSize));
            }
        }
    }


    // calculates marker size based on zoom level
    // returns 0 if zoom is below minimum (marker should be hidden)
    private int getMarkerSize(float zoom) {
        if (zoom < ZOOM_MIN) return 0;
        if (zoom >= ZOOM_MAX) return MARKER_MAX_SIZE;


        // scale linearly between min and max zoom
        float t = (zoom - ZOOM_MIN) / (ZOOM_MAX - ZOOM_MIN);
        return (int) (MARKER_MIN_SIZE + t * (MARKER_MAX_SIZE - MARKER_MIN_SIZE));
    }


    // creates the circular marker with category icon inside
    private BitmapDescriptor createMarkerBitmap(int circleColor, int iconRes, int size) {
        int iconSize = (int) (size * 0.47f); // icon is ~47% of marker size


        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);


        Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setColor(circleColor);
        circlePaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, circlePaint);


        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(Color.WHITE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(size * 0.05f);
        canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - 3, borderPaint);


        Drawable icon = ContextCompat.getDrawable(requireContext(), iconRes);
        if (icon != null) {
            icon.mutate();
            icon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
            int left = (size - iconSize) / 2;
            int top = (size - iconSize) / 2;
            icon.setBounds(left, top, left + iconSize, top + iconSize);
            icon.draw(canvas);
        }


        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }


    // assigns color based on severity level
    private int getSeverityColor(double severity) {
        if (severity <= 3) return Color.parseColor("#4CAF50");
        else if (severity <= 6) return Color.parseColor("#FFC107");
        else if (severity <= 9) return Color.parseColor("#FF5722");
        else return Color.parseColor("#F44336");
    }


    private int getCategoryIcon(String category) {
        if (category == null) return R.drawable.ic_report;
        switch (category) {
            case "ROAD":      return R.drawable.ic_road;
            case "LIGHTING":  return R.drawable.ic_lighting;
            case "HAZARDS":   return R.drawable.ic_hazards;
            case "VANDALISM": return R.drawable.ic_vandalism;
            case "SAFETY":    return R.drawable.ic_shield;
            default:          return R.drawable.ic_report;
        }
    }

    private void applyMapStyle() {
        int nightMode = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        if (nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
            mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(
                    requireContext(), R.raw.map_style_dark));
        }
    }
}
