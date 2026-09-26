package com.pkg.civicfix;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;

import com.firebase.geofire.GeoFireUtils;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.pkg.civicfix.model.ImportantLocation;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ImportantLocationBottomSheet
        extends BottomSheetDialogFragment
        implements OnMapReadyCallback {

    public static final String RESULT_KEY =
            "important_location_changed";

    private static final String ARG_PRESET =
            "preset";

    private static final String ARG_EDITING =
            "editing";

    private static final String ARG_LOCATION_ID =
            "locationId";

    private static final String ARG_TYPE =
            "type";

    private static final String ARG_NAME =
            "name";

    private static final String ARG_ADDRESS =
            "address";

    private static final String ARG_LAT =
            "lat";

    private static final String ARG_LNG =
            "lng";

    private static final String ARG_HAS_LOCATION =
            "hasLocation";

    private boolean isPreset;
    private boolean isEditing;
    private boolean hasSelectedLocation;

    private String locationId;
    private String locationType;
    private String locationName;
    private String address;

    private double selectedLat;
    private double selectedLng;

    private MapView mapPreview;
    private GoogleMap previewMap;

    private View layoutName;

    private EditText etName;

    private TextView tvTitle;
    private TextView tvAddress;

    private TextView btnRemove;
    private TextView btnSave;

    // factory for preset location types
    public static ImportantLocationBottomSheet
    newPreset(
            String type,
            @Nullable ImportantLocation existing
    ) {

        ImportantLocationBottomSheet sheet =
                new ImportantLocationBottomSheet();

        Bundle args =
                new Bundle();

        args.putBoolean(
                ARG_PRESET,
                true
        );

        args.putBoolean(
                ARG_EDITING,
                existing != null
        );

        args.putString(
                ARG_TYPE,
                type
        );

        args.putString(
                ARG_LOCATION_ID,
                existing != null
                        ? existing.getId()
                        : type.toLowerCase(
                        Locale.US
                )
        );

        args.putString(
                ARG_NAME,
                getPresetName(type)
        );

        args.putString(
                ARG_ADDRESS,
                existing != null
                        ? existing.getDisplayAddress()
                        : ""
        );

        args.putDouble(
                ARG_LAT,
                existing != null
                        ? existing.getLatitude()
                        : 0
        );

        args.putDouble(
                ARG_LNG,
                existing != null
                        ? existing.getLongitude()
                        : 0
        );

        args.putBoolean(
                ARG_HAS_LOCATION,
                existing != null
        );

        sheet.setArguments(args);

        return sheet;
    }

    // factory for custom location types
    public static ImportantLocationBottomSheet
    newCustom(
            @Nullable ImportantLocation existing
    ) {

        ImportantLocationBottomSheet sheet =
                new ImportantLocationBottomSheet();

        Bundle args =
                new Bundle();

        args.putBoolean(
                ARG_PRESET,
                false
        );

        args.putBoolean(
                ARG_EDITING,
                existing != null
        );

        args.putString(
                ARG_TYPE,
                ImportantLocation.TYPE_CUSTOM
        );

        args.putString(
                ARG_LOCATION_ID,
                existing != null
                        ? existing.getId()
                        : null
        );

        args.putString(
                ARG_NAME,
                existing != null
                        ? existing.getName()
                        : ""
        );

        args.putString(
                ARG_ADDRESS,
                existing != null
                        ? existing.getDisplayAddress()
                        : ""
        );

        args.putDouble(
                ARG_LAT,
                existing != null
                        ? existing.getLatitude()
                        : 0
        );

        args.putDouble(
                ARG_LNG,
                existing != null
                        ? existing.getLongitude()
                        : 0
        );

        args.putBoolean(
                ARG_HAS_LOCATION,
                existing != null
        );

        sheet.setArguments(args);

        return sheet;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {

        return inflater.inflate(
                R.layout.bottom_sheet_important_location,
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

        readArguments();

        tvTitle =
                view.findViewById(
                        R.id.tv_location_editor_title
                );

        tvAddress =
                view.findViewById(
                        R.id.tv_location_address
                );

        layoutName =
                view.findViewById(
                        R.id.layout_location_name
                );

        etName =
                view.findViewById(
                        R.id.et_location_name
                );

        btnRemove =
                view.findViewById(
                        R.id.btn_remove_location
                );

        btnSave =
                view.findViewById(
                        R.id.btn_save_location
                );

        mapPreview =
                view.findViewById(
                        R.id.map_location_preview
                );

        configureHeader();
        configureNameField();
        configureAddress();

        mapPreview.onCreate(
                savedInstanceState
        );

        mapPreview.getMapAsync(
                this
        );

        view.findViewById(
                R.id.map_click_overlay
        ).setOnClickListener(v ->
                openLocationPicker()
        );

        view.findViewById(
                R.id.btn_use_current_location
        ).setOnClickListener(v ->
                useCurrentLocation()
        );

        view.findViewById(
                R.id.btn_cancel_location
        ).setOnClickListener(v ->
                dismiss()
        );

        btnRemove.setVisibility(
                isEditing
                        ? View.VISIBLE
                        : View.GONE
        );

        btnRemove.setOnClickListener(v ->
                removeLocation()
        );

        btnSave.setOnClickListener(v ->
                saveLocation()
        );
    }

    private void readArguments() {

        Bundle args =
                getArguments();

        if (args == null) {
            return;
        }

        isPreset =
                args.getBoolean(
                        ARG_PRESET
                );

        isEditing =
                args.getBoolean(
                        ARG_EDITING
                );

        locationId =
                args.getString(
                        ARG_LOCATION_ID
                );

        locationType =
                args.getString(
                        ARG_TYPE
                );

        locationName =
                args.getString(
                        ARG_NAME,
                        ""
                );

        address =
                args.getString(
                        ARG_ADDRESS,
                        ""
                );

        selectedLat =
                args.getDouble(
                        ARG_LAT
                );

        selectedLng =
                args.getDouble(
                        ARG_LNG
                );

        hasSelectedLocation =
                args.getBoolean(
                        ARG_HAS_LOCATION
                );
    }

    private void configureHeader() {

        if (isPreset) {

            tvTitle.setText(
                    (
                            isEditing
                                    ? "Edit "
                                    : "Add "
                    )
                            + locationName
            );

        } else {

            tvTitle.setText(
                    isEditing
                            ? "Edit Location"
                            : "Add Location"
            );
        }
    }

    private void configureNameField() {

        if (isPreset) {

            layoutName.setVisibility(
                    View.GONE
            );

        } else {

            layoutName.setVisibility(
                    View.VISIBLE
            );

            etName.setText(
                    locationName
            );
        }
    }

    private void configureAddress() {

        if (
                address == null
                        || address.trim().isEmpty()
        ) {

            tvAddress.setText(
                    "No location selected"
            );

        } else {

            tvAddress.setText(
                    address
            );
        }
    }

    // save location details to firestore

    private void saveLocation() {

        FirebaseUser user =
                FirebaseAuth
                        .getInstance()
                        .getCurrentUser();

        if (user == null) {

            Toast.makeText(
                    requireContext(),
                    "You must be signed in",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (!isPreset) {

            String enteredName =
                    etName.getText() == null
                            ? ""
                            : etName
                              .getText()
                              .toString()
                              .trim();

            if (enteredName.isEmpty()) {

                etName.setError(
                        "Enter a location name"
                );

                return;
            }

            locationName =
                    enteredName;
        }

        if (!hasSelectedLocation) {

            Toast.makeText(
                    requireContext(),
                    "Choose a location first",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        setSavingState(true);

        FirebaseFirestore db =
                FirebaseFirestore
                        .getInstance();

        CollectionReference collection =
                db.collection("users")
                        .document(user.getUid())
                        .collection(
                                "importantLocations"
                        );

        DocumentReference document;

        if (isPreset) {

            document =
                    collection.document(
                            locationType
                                    .toLowerCase(
                                            Locale.US
                                    )
                    );

        } else if (
                isEditing
                        && locationId != null
        ) {

            document =
                    collection.document(
                            locationId
                    );

        } else {

            document =
                    collection.document();

            locationId =
                    document.getId();
        }

        String geohash =
                GeoFireUtils
                        .getGeoHashForLocation(
                                new GeoLocation(
                                        selectedLat,
                                        selectedLng
                                )
                        );

        Map<String, Object> data =
                new HashMap<>();

        data.put(
                "name",
                locationName
        );

        data.put(
                "type",
                locationType
        );

        data.put(
                "latitude",
                selectedLat
        );

        data.put(
                "longitude",
                selectedLng
        );

        data.put(
                "geohash",
                geohash
        );

        data.put(
                "displayAddress",
                address == null
                        ? ""
                        : address
        );

        data.put(
                "updatedAt",
                FieldValue.serverTimestamp()
        );

        if (!isEditing) {

            data.put(
                    "createdAt",
                    FieldValue.serverTimestamp()
            );
        }

        document.set(
                        data,
                        SetOptions.merge()
                )
                .addOnSuccessListener(unused -> {

                    if (getContext() == null) {
                        return;
                    }

                    notifyLocationChanged();

                    Toast.makeText(
                            requireContext(),
                            locationName + " saved",
                            Toast.LENGTH_SHORT
                    ).show();

                    dismiss();
                })
                .addOnFailureListener(e -> {

                    if (getContext() == null) {
                        return;
                    }

                    setSavingState(false);

                    Toast.makeText(
                            requireContext(),
                            "Failed to save location: "
                                    + e.getMessage(),
                            Toast.LENGTH_SHORT
                    ).show();
                });
    }

    // delete location document from firestore

    private void removeLocation() {

        if (
                !isEditing
                        || locationId == null
        ) {
            return;
        }

        FirebaseUser user =
                FirebaseAuth
                        .getInstance()
                        .getCurrentUser();

        if (user == null) {
            return;
        }

        setSavingState(true);

        FirebaseFirestore
                .getInstance()
                .collection("users")
                .document(user.getUid())
                .collection(
                        "importantLocations"
                )
                .document(locationId)
                .delete()
                .addOnSuccessListener(unused -> {

                    if (getContext() == null) {
                        return;
                    }

                    notifyLocationChanged();

                    Toast.makeText(
                            requireContext(),
                            locationName + " removed",
                            Toast.LENGTH_SHORT
                    ).show();

                    dismiss();
                })
                .addOnFailureListener(e -> {

                    if (getContext() == null) {
                        return;
                    }

                    setSavingState(false);

                    Toast.makeText(
                            requireContext(),
                            "Failed to remove location: "
                                    + e.getMessage(),
                            Toast.LENGTH_SHORT
                    ).show();
                });
    }

    private void setSavingState(
            boolean saving
    ) {

        btnSave.setEnabled(
                !saving
        );

        btnRemove.setEnabled(
                !saving
        );

        btnSave.setAlpha(
                saving
                        ? 0.5f
                        : 1f
        );

        btnRemove.setAlpha(
                saving
                        ? 0.5f
                        : 1f
        );
    }

    private void notifyLocationChanged() {

        Bundle result =
                new Bundle();

        result.putBoolean(
                "changed",
                true
        );

        getParentFragmentManager()
                .setFragmentResult(
                        RESULT_KEY,
                        result
                );
    }

    // open map picker activity

    private void openLocationPicker() {

        Intent intent =
                new Intent(
                        requireContext(),
                        LocationPickerActivity.class
                );

        // center picker on existing location if present
        if (hasSelectedLocation) {

            intent.putExtra(
                    "initialLat",
                    selectedLat
            );

            intent.putExtra(
                    "initialLng",
                    selectedLng
            );
        }

        locationPicker.launch(
                intent
        );
    }

    private final ActivityResultLauncher<Intent>
            locationPicker =
            registerForActivityResult(

                    new ActivityResultContracts
                            .StartActivityForResult(),

                    result -> {

                        if (
                                result.getResultCode()
                                        != Activity.RESULT_OK
                                        || result.getData()
                                        == null
                        ) {

                            return;
                        }

                        selectedLat =
                                result.getData()
                                        .getDoubleExtra(
                                                "lat",
                                                0
                                        );

                        selectedLng =
                                result.getData()
                                        .getDoubleExtra(
                                                "lng",
                                                0
                                        );

                        hasSelectedLocation =
                                true;

                        String returnedAddress =
                                result.getData()
                                        .getStringExtra(
                                                "address"
                                        );

                        updateMapPreview(
                                selectedLat,
                                selectedLng
                        );

                        if (
                                returnedAddress != null
                                        && !returnedAddress
                                        .isEmpty()
                        ) {

                            address =
                                    returnedAddress;

                            tvAddress.setText(
                                    address
                            );

                        } else {

                            reverseGeocode(
                                    selectedLat,
                                    selectedLng
                            );
                        }
                    }
            );

    // fetch device current location

    private void useCurrentLocation() {

        if (
                ActivityCompat
                        .checkSelfPermission(
                                requireContext(),
                                Manifest.permission
                                        .ACCESS_FINE_LOCATION
                        )
                        != PackageManager
                        .PERMISSION_GRANTED
        ) {

            locationPermission.launch(
                    Manifest.permission
                            .ACCESS_FINE_LOCATION
            );

            return;
        }

        fetchCurrentLocation();
    }

    private final ActivityResultLauncher<String>
            locationPermission =
            registerForActivityResult(

                    new ActivityResultContracts
                            .RequestPermission(),

                    granted -> {

                        if (granted) {

                            fetchCurrentLocation();

                        } else {

                            Toast.makeText(
                                    requireContext(),
                                    "Location permission is required",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    }
            );

    private void fetchCurrentLocation() {

        if (
                ActivityCompat
                        .checkSelfPermission(
                                requireContext(),
                                Manifest.permission
                                        .ACCESS_FINE_LOCATION
                        )
                        != PackageManager
                        .PERMISSION_GRANTED
        ) {

            return;
        }

        FusedLocationProviderClient client =
                LocationServices
                        .getFusedLocationProviderClient(
                                requireContext()
                        );

        client.getLastLocation()
                .addOnSuccessListener(location -> {

                    if (location == null) {

                        Toast.makeText(
                                requireContext(),
                                "Current location unavailable",
                                Toast.LENGTH_SHORT
                        ).show();

                        return;
                    }

                    selectedLat =
                            location.getLatitude();

                    selectedLng =
                            location.getLongitude();

                    hasSelectedLocation =
                            true;

                    updateMapPreview(
                            selectedLat,
                            selectedLng
                    );

                    reverseGeocode(
                            selectedLat,
                            selectedLng
                    );
                });
    }

    // map preview and geocoding logic

    @Override
    public void onMapReady(
            @NonNull GoogleMap googleMap
    ) {

        previewMap =
                googleMap;

        previewMap
                .getUiSettings()
                .setAllGesturesEnabled(
                        false
                );

        previewMap
                .getUiSettings()
                .setZoomControlsEnabled(
                        false
                );

        previewMap
                .getUiSettings()
                .setMapToolbarEnabled(
                        false
                );

        applyMapStyle(
                previewMap
        );

        if (hasSelectedLocation) {

            updateMapPreview(
                    selectedLat,
                    selectedLng
            );
        }
    }

    private void updateMapPreview(
            double lat,
            double lng
    ) {

        if (previewMap == null) {
            return;
        }

        LatLng location =
                new LatLng(
                        lat,
                        lng
                );

        previewMap.clear();

        previewMap.addMarker(
                new MarkerOptions()
                        .position(location)
        );

        previewMap.moveCamera(
                CameraUpdateFactory
                        .newLatLngZoom(
                                location,
                                15
                        )
        );
    }

    private void reverseGeocode(
            double lat,
            double lng
    ) {

        try {

            Geocoder geocoder =
                    new Geocoder(
                            requireContext(),
                            Locale.getDefault()
                    );

            List<Address> addresses =
                    geocoder.getFromLocation(
                            lat,
                            lng,
                            1
                    );

            if (
                    addresses != null
                            && !addresses.isEmpty()
            ) {

                address =
                        addresses
                                .get(0)
                                .getAddressLine(0);

                tvAddress.setText(
                        address
                );
            }

        } catch (IOException e) {

            e.printStackTrace();

            address = "";

            tvAddress.setText(
                    "Selected location"
            );
        }
    }

    private static String getPresetName(
            String type
    ) {

        if (
                ImportantLocation.TYPE_HOME
                        .equals(type)
        ) {
            return "Home";
        }

        if (
                ImportantLocation.TYPE_WORK
                        .equals(type)
        ) {
            return "Work";
        }

        return "School";
    }

    private void applyMapStyle(
            GoogleMap map
    ) {

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

            map.setMapStyle(
                    MapStyleOptions
                            .loadRawResourceStyle(
                                    requireContext(),
                                    R.raw.map_style_dark
                            )
            );
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        Dialog dialog =
                getDialog();

        if (
                dialog instanceof BottomSheetDialog
        ) {

            View bottomSheet =
                    ((BottomSheetDialog) dialog)
                            .findViewById(
                                    com.google.android.material
                                            .R.id
                                            .design_bottom_sheet
                            );

            if (bottomSheet != null) {

                BottomSheetBehavior<View> behavior =
                        BottomSheetBehavior.from(
                                bottomSheet
                        );

                behavior.setState(
                        BottomSheetBehavior
                                .STATE_EXPANDED
                );

                behavior.setSkipCollapsed(
                        true
                );
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mapPreview != null) {
            mapPreview.onResume();
        }
    }

    @Override
    public void onPause() {

        if (mapPreview != null) {
            mapPreview.onPause();
        }

        super.onPause();
    }

    @Override
    public void onDestroyView() {

        if (mapPreview != null) {
            mapPreview.onDestroy();
        }

        mapPreview = null;
        previewMap = null;

        super.onDestroyView();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();

        if (mapPreview != null) {
            mapPreview.onLowMemory();
        }
    }
}