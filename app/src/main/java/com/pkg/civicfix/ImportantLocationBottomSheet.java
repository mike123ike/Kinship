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

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class ImportantLocationBottomSheet
        extends BottomSheetDialogFragment
        implements OnMapReadyCallback {

    private static final String ARG_PRESET = "preset";
    private static final String ARG_EDITING = "editing";
    private static final String ARG_NAME = "name";
    private static final String ARG_ADDRESS = "address";
    private static final String ARG_LAT = "lat";
    private static final String ARG_LNG = "lng";

    private boolean isPreset;
    private boolean isEditing;

    private String locationName = "";
    private String address = "";

    private double selectedLat = 0;
    private double selectedLng = 0;

    private MapView mapPreview;
    private GoogleMap previewMap;

    private View layoutName;
    private EditText etName;

    private TextView tvTitle;
    private TextView tvAddress;

    public static ImportantLocationBottomSheet newPreset(
            String presetName,
            boolean editing,
            String address,
            double lat,
            double lng
    ) {

        ImportantLocationBottomSheet sheet =
                new ImportantLocationBottomSheet();

        Bundle args = new Bundle();

        args.putBoolean(ARG_PRESET, true);
        args.putBoolean(ARG_EDITING, editing);
        args.putString(ARG_NAME, presetName);
        args.putString(ARG_ADDRESS, address);
        args.putDouble(ARG_LAT, lat);
        args.putDouble(ARG_LNG, lng);

        sheet.setArguments(args);

        return sheet;
    }

    public static ImportantLocationBottomSheet newCustom(
            boolean editing,
            String name,
            String address,
            double lat,
            double lng
    ) {

        ImportantLocationBottomSheet sheet =
                new ImportantLocationBottomSheet();

        Bundle args = new Bundle();

        args.putBoolean(ARG_PRESET, false);
        args.putBoolean(ARG_EDITING, editing);
        args.putString(ARG_NAME, name);
        args.putString(ARG_ADDRESS, address);
        args.putDouble(ARG_LAT, lat);
        args.putDouble(ARG_LNG, lng);

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
        super.onViewCreated(view, savedInstanceState);

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

        mapPreview =
                view.findViewById(
                        R.id.map_location_preview
                );

        configureHeader();
        configureNameField();
        configureAddress();

        mapPreview.onCreate(savedInstanceState);
        mapPreview.getMapAsync(this);

        view.findViewById(
                        R.id.map_click_overlay
                )
                .setOnClickListener(v ->
                        openLocationPicker()
                );

        view.findViewById(
                        R.id.btn_use_current_location
                )
                .setOnClickListener(v ->
                        useCurrentLocation()
                );

        view.findViewById(
                        R.id.btn_cancel_location
                )
                .setOnClickListener(v ->
                        dismiss()
                );

        View removeButton =
                view.findViewById(
                        R.id.btn_remove_location
                );

        removeButton.setVisibility(
                isEditing
                        ? View.VISIBLE
                        : View.GONE
        );

        removeButton.setOnClickListener(v -> {

            Toast.makeText(
                    requireContext(),
                    "UI test: location removed",
                    Toast.LENGTH_SHORT
            ).show();

            dismiss();
        });

        view.findViewById(
                        R.id.btn_save_location
                )
                .setOnClickListener(v ->
                        validateAndSave()
                );
    }

    private void readArguments() {

        Bundle args = getArguments();

        if (args == null) {
            return;
        }

        isPreset =
                args.getBoolean(
                        ARG_PRESET,
                        false
                );

        isEditing =
                args.getBoolean(
                        ARG_EDITING,
                        false
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
                        ARG_LAT,
                        0
                );

        selectedLng =
                args.getDouble(
                        ARG_LNG,
                        0
                );
    }

    private void configureHeader() {

        if (isPreset) {

            if (isEditing) {

                tvTitle.setText(
                        "Edit " + locationName
                );

            } else {

                tvTitle.setText(
                        "Add " + locationName
                );
            }

        } else {

            if (isEditing) {

                tvTitle.setText(
                        "Edit Location"
                );

            } else {

                tvTitle.setText(
                        "Add Location"
                );
            }
        }
    }

    private void configureNameField() {

        if (isPreset) {

            layoutName.setVisibility(
                    View.GONE
            );

            return;
        }

        layoutName.setVisibility(
                View.VISIBLE
        );

        etName.setText(
                locationName
        );
    }

    private void configureAddress() {

        if (address == null
                || address.trim().isEmpty()) {

            tvAddress.setText(
                    "No location selected"
            );

        } else {

            tvAddress.setText(
                    address
            );
        }
    }

    private void openLocationPicker() {

        Intent intent =
                new Intent(
                        requireContext(),
                        LocationPickerActivity.class
                );

        locationPicker.launch(intent);
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
                                result
                                        .getData()
                                        .getDoubleExtra(
                                                "lat",
                                                0
                                        );

                        selectedLng =
                                result
                                        .getData()
                                        .getDoubleExtra(
                                                "lng",
                                                0
                                        );

                        updateMapPreview(
                                selectedLat,
                                selectedLng
                        );

                        reverseGeocode(
                                selectedLat,
                                selectedLng
                        );
                    }
            );

    private void useCurrentLocation() {

        if (
                ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION
                )
                        != PackageManager.PERMISSION_GRANTED
        ) {

            locationPermission.launch(
                    Manifest.permission.ACCESS_FINE_LOCATION
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
                ActivityCompat.checkSelfPermission(
                        requireContext(),
                        Manifest.permission.ACCESS_FINE_LOCATION
                )
                        != PackageManager.PERMISSION_GRANTED
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

    @Override
    public void onMapReady(
            @NonNull GoogleMap googleMap
    ) {

        previewMap = googleMap;

        previewMap
                .getUiSettings()
                .setAllGesturesEnabled(false);

        previewMap
                .getUiSettings()
                .setZoomControlsEnabled(false);

        previewMap
                .getUiSettings()
                .setMapToolbarEnabled(false);

        applyMapStyle(
                previewMap
        );

        if (
                selectedLat != 0
                        || selectedLng != 0
        ) {

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

            tvAddress.setText(
                    "Selected location"
            );
        }
    }

    private void validateAndSave() {

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

                etName.requestFocus();

                return;
            }

            etName.setError(null);

            locationName =
                    enteredName;
        }

        if (
                selectedLat == 0
                        && selectedLng == 0
        ) {

            Toast.makeText(
                    requireContext(),
                    "Choose a location first",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        Toast.makeText(
                requireContext(),
                "UI test: "
                        + locationName
                        + " saved",
                Toast.LENGTH_SHORT
        ).show();

        dismiss();
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

            BottomSheetDialog bottomSheetDialog =
                    (BottomSheetDialog) dialog;

            View bottomSheet =
                    bottomSheetDialog
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
                        BottomSheetBehavior.STATE_EXPANDED
                );

                behavior.setSkipCollapsed(true);
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

        previewMap = null;
        mapPreview = null;

        tvTitle = null;
        tvAddress = null;
        layoutName = null;
        etName = null;

        super.onDestroyView();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();

        if (mapPreview != null) {
            mapPreview.onLowMemory();
        }
    }

    @Override
    public void onSaveInstanceState(
            @NonNull Bundle outState
    ) {
        super.onSaveInstanceState(outState);

        if (mapPreview != null) {
            mapPreview.onSaveInstanceState(outState);
        }
    }
}