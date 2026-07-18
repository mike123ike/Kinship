package com.pkg.civicfix;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.pkg.civicfix.model.Report;
import com.pkg.civicfix.BuildConfig;
import com.firebase.geofire.GeoFireUtils;
import com.firebase.geofire.GeoLocation;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ReportFragment extends Fragment implements OnMapReadyCallback {

    private ImageView imgPreview;
    private MapView mapPreview;
    private GoogleMap previewMap;
    private TextView tvSelectedAddress;
    private Uri cameraImageUri;
    private Uri selectedImageUri;

    private double selectedLat = 0;
    private double selectedLng = 0;

    private AutoCompleteTextView dropdown;
    private Slider slider;
    private TextView tvDescription;
    private MaterialButton btnSubmit;

    private FirebaseFirestore db;
    private FirebaseStorage storage;
    private StorageReference storageRef;
    private FirebaseAuth auth;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_report, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();
        storageRef = storage.getReference();
        auth = FirebaseAuth.getInstance();

        slider = view.findViewById(R.id.slider_severity);
        TextView tvSeverity = view.findViewById(R.id.tv_severity_value);
        slider.addOnChangeListener((s, value, fromUser) ->
                tvSeverity.setText(String.valueOf((int) value))
        );

        String[] categories = {"Road", "Lighting", "Hazards", "Vandalism", "Safety", "Other"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                categories
        );
        dropdown = view.findViewById(R.id.dropdown_category);
        dropdown.setAdapter(adapter);
        dropdown.setOnClickListener(v -> dropdown.showDropDown());

        tvDescription = view.findViewById(R.id.et_description);

        imgPreview = view.findViewById(R.id.iv_photo_preview);
        view.findViewById(R.id.btn_library).setOnClickListener(v ->
                launchLibrary.launch("image/*")
        );

        view.findViewById(R.id.btn_camera).setOnClickListener(v -> {
            if (ActivityCompat.checkSelfPermission(requireContext(),
                    Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                requestCameraPermission.launch(Manifest.permission.CAMERA);
            }
        });

        tvSelectedAddress = view.findViewById(R.id.tv_selected_address);
        mapPreview = view.findViewById(R.id.map_preview);
        mapPreview.onCreate(savedInstanceState);
        mapPreview.getMapAsync(this);

        view.findViewById(R.id.btn_pinpoint_location).setOnClickListener(v ->
                locationPicker.launch(new Intent(requireContext(), LocationPickerActivity.class))
        );
        view.findViewById(R.id.btn_use_location).setOnClickListener(v -> useCurrentLocation());

        btnSubmit = view.findViewById(R.id.btn_submit);
        btnSubmit.setOnClickListener(v -> submitReport());
    }

    private void submitReport() {
        if (dropdown.getText().toString().isEmpty()) {
            Toast.makeText(requireContext(), "Please select a category", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedLat == 0 && selectedLng == 0) {
            Toast.makeText(requireContext(), "Please set a location", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSubmit.setEnabled(false);
        btnSubmit.setText("Submitting...");

        if (selectedImageUri != null) {
            uploadImageThenSubmit();
        } else {
            writeReportToFirestore("");
        }
    }

    // uploads image to cloudinary
    private void uploadImageThenSubmit() {
        MediaManager.get()
                .upload(selectedImageUri)
                .unsigned(BuildConfig.UPLOAD_PRESET)
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) {}

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {}

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        String imageUrl = (String) resultData.get("secure_url");
                        writeReportToFirestore(imageUrl);
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        if (getContext() == null) return;
                        btnSubmit.setEnabled(true);
                        btnSubmit.setText("Submit Report");
                        Toast.makeText(requireContext(),
                                "Image upload failed: " + error.getDescription(),
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {}
                })
                .dispatch();
    }

    // writes to report document
    private void writeReportToFirestore(String imageUrl) {
        String uid = auth.getCurrentUser().getUid();
        String categoryStr = dropdown.getText().toString().toUpperCase();
        Report.Category category = Report.Category.valueOf(categoryStr);
        int severity = (int) slider.getValue();
        String description = tvDescription.getText().toString().trim();
        String geohash = GeoFireUtils.getGeoHashForLocation(
                new GeoLocation(selectedLat, selectedLng)
        );

        Report report = new Report(
                uid,
                false,
                category,
                severity,
                description,
                imageUrl,
                selectedLat,
                selectedLng,
                geohash
        );

        db.collection("reports")
                .add(report)
                .addOnSuccessListener(docRef -> {
                    if (getContext() == null) return;
                    Toast.makeText(requireContext(),
                            "Report submitted!", Toast.LENGTH_SHORT).show();
                    resetForm();
                })
                .addOnFailureListener(e -> {
                    if (getContext() == null) return;
                    btnSubmit.setEnabled(true);
                    btnSubmit.setText("Submit Report");
                    Toast.makeText(requireContext(),
                            "Failed to submit: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    // resets the form after sumbission
    private void resetForm() {
        selectedImageUri = null;
        selectedLat = 0;
        selectedLng = 0;
        imgPreview.setVisibility(View.GONE);
        dropdown.setText("");
        slider.setValue(5);
        tvDescription.setText("");
        tvSelectedAddress.setVisibility(View.GONE);
        if (previewMap != null) previewMap.clear();
        if (getView() != null) {
            getView().findViewById(R.id.layout_upload_text).setVisibility(View.VISIBLE);
            getView().findViewById(R.id.layout_upload_prompt)
                    .setBackgroundResource(R.drawable.bg_dashed_border);
        }
        btnSubmit.setEnabled(true);
        btnSubmit.setText("Submit Report");
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        previewMap = googleMap;
        previewMap.getUiSettings().setAllGesturesEnabled(false);
        previewMap.getUiSettings().setZoomControlsEnabled(false);
        previewMap.getUiSettings().setMapToolbarEnabled(false);
        applyMapStyle(previewMap);
    }

    // stores image directory, and alters ui accordingly
    private final ActivityResultLauncher<String> launchLibrary = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null && getView() != null) {
                    selectedImageUri = uri;
                    imgPreview.setImageURI(uri);
                    imgPreview.setVisibility(View.VISIBLE);
                    getView().findViewById(R.id.layout_upload_prompt)
                            .setBackgroundResource(R.drawable.bg_dashed_border_transparent);
                }
            }
    );

    // stores image directory from camera, and alters ui accordingly
    private final ActivityResultLauncher<Uri> launchCamera = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            success -> {
                if (success && cameraImageUri != null && getView() != null) {
                    selectedImageUri = cameraImageUri;
                    imgPreview.setImageURI(cameraImageUri);
                    imgPreview.setVisibility(View.VISIBLE);
                    getView().findViewById(R.id.layout_upload_prompt)
                            .setBackgroundResource(R.drawable.bg_dashed_border_transparent);
                }
            }
    );

    private final ActivityResultLauncher<String> requestCameraPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> {
                if (granted) {
                    openCamera();
                } else {
                    Toast.makeText(requireContext(),
                            "Camera permission is required", Toast.LENGTH_SHORT).show();
                }
            }
    );

    private void openCamera() {
        try {
            File photoFile = File.createTempFile("photo_", ".jpg",
                    requireContext().getCacheDir());
            cameraImageUri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".provider",
                    photoFile
            );
            launchCamera.launch(cameraImageUri);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // location picker
    private final ActivityResultLauncher<Intent> locationPicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    selectedLat = result.getData().getDoubleExtra("lat", 0);
                    selectedLng = result.getData().getDoubleExtra("lng", 0);
                    updateMapPreview(selectedLat, selectedLng);
                    reverseGeocode(selectedLat, selectedLng);
                }
            }
    );

    // current location
    private void useCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1001);
            return;
        }
        FusedLocationProviderClient client =
                LocationServices.getFusedLocationProviderClient(requireContext());
        client.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                selectedLat = location.getLatitude();
                selectedLng = location.getLongitude();
                updateMapPreview(selectedLat, selectedLng);
                reverseGeocode(selectedLat, selectedLng);
            }
        });
    }

    private void updateMapPreview(double lat, double lng) {
        if (previewMap != null) {
            LatLng location = new LatLng(lat, lng);
            previewMap.clear();
            previewMap.addMarker(new MarkerOptions().position(location));
            previewMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 15));
        }
    }

    private void reverseGeocode(double lat, double lng) {
        try {
            Geocoder geocoder = new Geocoder(requireContext(), Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
            if (addresses != null && !addresses.isEmpty()) {
                String address = addresses.get(0).getAddressLine(0);
                tvSelectedAddress.setText(address);
                tvSelectedAddress.setVisibility(View.VISIBLE);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void applyMapStyle(GoogleMap map) {
        int nightMode = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        if (nightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
            map.setMapStyle(MapStyleOptions.loadRawResourceStyle(
                    requireContext(), R.raw.map_style_dark));
        }
    }

    @Override public void onResume() { super.onResume(); if (mapPreview != null) mapPreview.onResume(); }
    @Override public void onPause() { super.onPause(); if (mapPreview != null) mapPreview.onPause(); }
    @Override public void onDestroy() { super.onDestroy(); if (mapPreview != null) mapPreview.onDestroy(); }
    @Override public void onLowMemory() { super.onLowMemory(); if (mapPreview != null) mapPreview.onLowMemory(); }
    @Override public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapPreview != null) mapPreview.onSaveInstanceState(outState);
    }
}