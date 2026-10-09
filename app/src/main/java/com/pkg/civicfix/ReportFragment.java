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
import android.widget.PopupWindow;
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
import com.firebase.geofire.GeoFireUtils;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQueryBounds;
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
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;
import com.pkg.civicfix.model.Event;
import com.pkg.civicfix.model.Report;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ReportFragment extends Fragment
        implements OnMapReadyCallback {

    private static final double EVENT_MATCH_RADIUS_METERS =
            322.0;

    private static final long PUBLIC_EVENT_THRESHOLD =
            3;

    private static final String ERROR_ALREADY_REPORTED =
            "ALREADY_REPORTED";


    private ImageView imgPreview;

    private MapView mapPreview;

    private GoogleMap previewMap;

    private TextView tvSelectedAddress;

    private Uri cameraImageUri;

    private Uri selectedImageUri;


    private double selectedLat =
            0;

    private double selectedLng =
            0;


    private AutoCompleteTextView dropdown;

    private Slider slider;
    private PopupWindow severityTooltip;

    private TextView tvDescription;

    private MaterialButton btnSubmit;


    private FirebaseFirestore db;

    private FirebaseAuth auth;


    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {

        return inflater.inflate(
                R.layout.fragment_report,
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


        slider =
                view.findViewById(
                        R.id.slider_severity
                );


        TextView tvSeverity =
                view.findViewById(
                        R.id.tv_severity_value
                );


        view.findViewById(R.id.btn_severity_info)
                .setOnClickListener(this::showSeverityTooltip);

        slider.addOnChangeListener(
                (
                        s,
                        value,
                        fromUser
                ) ->

                        tvSeverity.setText(
                                String.valueOf(
                                        (int) value
                                )
                        )
        );


        String[] categories = {
                "Road",
                "Lighting",
                "Hazards",
                "Vandalism",
                "Safety",
                "Other"
        };


        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        requireContext(),
                        android.R.layout
                                .simple_dropdown_item_1line,
                        categories
                );


        dropdown =
                view.findViewById(
                        R.id.dropdown_category
                );


        dropdown.setAdapter(
                adapter
        );


        dropdown.setOnClickListener(
                v ->
                        dropdown.showDropDown()
        );


        tvDescription =
                view.findViewById(
                        R.id.et_description
                );


        imgPreview =
                view.findViewById(
                        R.id.iv_photo_preview
                );


        view.findViewById(
                R.id.btn_library
        ).setOnClickListener(
                v ->
                        launchLibrary.launch(
                                "image/*"
                        )
        );


        view.findViewById(
                R.id.btn_camera
        ).setOnClickListener(
                v -> {

                    if (
                            ActivityCompat
                                    .checkSelfPermission(
                                            requireContext(),
                                            Manifest.permission.CAMERA
                                    )
                                    == PackageManager
                                    .PERMISSION_GRANTED
                    ) {

                        openCamera();

                    } else {

                        requestCameraPermission
                                .launch(
                                        Manifest.permission.CAMERA
                                );
                    }
                }
        );


        tvSelectedAddress =
                view.findViewById(
                        R.id.tv_selected_address
                );


        mapPreview =
                view.findViewById(
                        R.id.map_preview
                );


        mapPreview.onCreate(
                savedInstanceState
        );


        mapPreview.getMapAsync(
                this
        );


        view.findViewById(
                R.id.btn_pinpoint_location
        ).setOnClickListener(
                v ->
                        locationPicker.launch(
                                new Intent(
                                        requireContext(),
                                        LocationPickerActivity.class
                                )
                        )
        );


        view.findViewById(
                R.id.btn_use_location
        ).setOnClickListener(
                v ->
                        useCurrentLocation()
        );


        btnSubmit =
                view.findViewById(
                        R.id.btn_submit
                );


        btnSubmit.setOnClickListener(
                v ->
                        submitReport()
        );
    }


    private void submitReport() {

        if (
                dropdown.getText()
                        .toString()
                        .isEmpty()
        ) {

            Toast.makeText(
                    requireContext(),
                    "Please select a category",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        if (
                selectedLat == 0
                        && selectedLng == 0
        ) {

            Toast.makeText(
                    requireContext(),
                    "Please set a location",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        btnSubmit.setEnabled(
                false
        );

        btnSubmit.setText(
                "Submitting..."
        );


        if (
                selectedImageUri != null
        ) {

            uploadImageThenSubmit();

        } else {

            runClusteringLogic(
                    ""
            );
        }
    }


    private void uploadImageThenSubmit() {

        MediaManager.get()
                .upload(
                        selectedImageUri
                )
                .unsigned(
                        BuildConfig.UPLOAD_PRESET
                )
                .callback(
                        new UploadCallback() {

                            @Override
                            public void onStart(
                                    String requestId
                            ) {
                            }


                            @Override
                            public void onProgress(
                                    String requestId,
                                    long bytes,
                                    long totalBytes
                            ) {
                            }


                            @Override
                            public void onSuccess(
                                    String requestId,
                                    Map resultData
                            ) {

                                String imageUrl =
                                        (String)
                                                resultData.get(
                                                        "secure_url"
                                                );


                                runClusteringLogic(
                                        imageUrl
                                );
                            }


                            @Override
                            public void onError(
                                    String requestId,
                                    ErrorInfo error
                            ) {

                                if (
                                        getContext()
                                                == null
                                ) {

                                    return;
                                }


                                resetSubmitButton();


                                Toast.makeText(
                                        requireContext(),
                                        "Image upload failed: "
                                                + error.getDescription(),
                                        Toast.LENGTH_SHORT
                                ).show();
                            }


                            @Override
                            public void onReschedule(
                                    String requestId,
                                    ErrorInfo error
                            ) {
                            }
                        }
                )
                .dispatch();
    }


    private void runClusteringLogic(
            String imageUrl
    ) {

        if (
                getContext() == null
                        || auth.getCurrentUser()
                        == null
        ) {

            return;
        }


        String uid =
                auth.getCurrentUser()
                        .getUid();


        String categoryStr =
                dropdown.getText()
                        .toString()
                        .toUpperCase(
                                Locale.US
                        );


        int severity =
                (int) slider.getValue();


        String description =
                tvDescription.getText()
                        .toString()
                        .trim();


        String geohash =
                GeoFireUtils
                        .getGeoHashForLocation(
                                new GeoLocation(
                                        selectedLat,
                                        selectedLng
                                )
                        );


        List<GeoQueryBounds> bounds =
                GeoFireUtils
                        .getGeoHashQueryBounds(
                                new GeoLocation(
                                        selectedLat,
                                        selectedLng
                                ),

                                EVENT_MATCH_RADIUS_METERS
                        );


        List<
                com.google.android.gms.tasks
                        .Task<QuerySnapshot>
                >
                tasks =
                new ArrayList<>();


        for (
                GeoQueryBounds bound
                : bounds
        ) {

            Query query =
                    db.collection(
                                    "events"
                            )
                            .whereEqualTo(
                                    "category",
                                    categoryStr
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
                        ignored -> {

                            if (
                                    getContext()
                                            == null
                            ) {

                                return;
                            }


                            DocumentSnapshot matchedEvent =
                                    null;


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
                                        DocumentSnapshot document
                                        : task.getResult()
                                        .getDocuments()
                                ) {

                                    String eventStatus =
                                            document.getString(
                                                    "status"
                                            );


                                    // fixed events are historical; do not attach new report
                                    if (
                                            "FIXED"
                                                    .equalsIgnoreCase(
                                                            eventStatus
                                                    )
                                    ) {

                                        continue;
                                    }


                                    Double eventLat =
                                            document.getDouble(
                                                    "latitude"
                                            );

                                    Double eventLng =
                                            document.getDouble(
                                                    "longitude"
                                            );


                                    if (
                                            eventLat == null
                                                    || eventLng == null
                                    ) {

                                        continue;
                                    }


                                    double distanceMeters =
                                            GeoFireUtils
                                                    .getDistanceBetween(
                                                            new GeoLocation(
                                                                    selectedLat,
                                                                    selectedLng
                                                            ),

                                                            new GeoLocation(
                                                                    eventLat,
                                                                    eventLng
                                                            )
                                                    );


                                    if (
                                            distanceMeters
                                                    <= EVENT_MATCH_RADIUS_METERS
                                    ) {

                                        matchedEvent =
                                                document;

                                        break;
                                    }
                                }


                                if (
                                        matchedEvent
                                                != null
                                ) {

                                    break;
                                }
                            }


                            if (
                                    matchedEvent
                                            != null
                            ) {

                                @SuppressWarnings(
                                        "unchecked"
                                )
                                List<String> reporterIds =
                                        (List<String>)
                                                matchedEvent.get(
                                                        "reporterIds"
                                                );


                                // fast ux check
                                if (
                                        reporterIds
                                                != null

                                                && reporterIds
                                                .contains(
                                                        uid
                                                )
                                ) {

                                    resetSubmitButton();


                                    Toast.makeText(
                                            requireContext(),
                                            "You have already reported this issue",
                                            Toast.LENGTH_SHORT
                                    ).show();

                                    return;
                                }


                                updateExistingEvent(
                                        matchedEvent,
                                        uid,
                                        severity,
                                        imageUrl,
                                        categoryStr,
                                        description,
                                        geohash
                                );

                            } else {

                                createNewEvent(
                                        uid,
                                        categoryStr,
                                        severity,
                                        imageUrl,
                                        geohash,
                                        description
                                );
                            }
                        }
                );
    }


    private void updateExistingEvent(
            DocumentSnapshot eventDoc,
            String uid,
            int severity,
            String imageUrl,
            String category,
            String description,
            String geohash
    ) {

        String eventId =
                eventDoc.getId();


        DocumentReference eventRef =
                db.collection(
                                "events"
                        )
                        .document(
                                eventId
                        );


        DocumentReference userRef =
                db.collection(
                                "users"
                        )
                        .document(
                                uid
                        );


        DocumentReference reportRef =
                db.collection(
                                "reports"
                        )
                        .document();


        final double reportLat =
                selectedLat;

        final double reportLng =
                selectedLng;


        db.runTransaction(
                        transaction -> {

                            // re-read event inside transaction
                            DocumentSnapshot currentEvent =
                                    transaction.get(
                                            eventRef
                                    );


                            DocumentSnapshot userSnapshot =
                                    transaction.get(
                                            userRef
                                    );


                            if (
                                    !currentEvent.exists()
                            ) {

                                throw new FirebaseFirestoreException(
                                        "Event no longer exists",
                                        FirebaseFirestoreException
                                                .Code.ABORTED
                                );
                            }


                            @SuppressWarnings(
                                    "unchecked"
                            )
                            List<String> existingReporterIds =
                                    (List<String>)
                                            currentEvent.get(
                                                    "reporterIds"
                                            );


                            List<String> updatedReporterIds =
                                    new ArrayList<>();


                            if (
                                    existingReporterIds
                                            != null
                            ) {

                                updatedReporterIds
                                        .addAll(
                                                existingReporterIds
                                        );
                            }


                            // protects against race conditions
                            if (
                                    updatedReporterIds
                                            .contains(
                                                    uid
                                            )
                            ) {

                                throw new FirebaseFirestoreException(
                                        ERROR_ALREADY_REPORTED,
                                        FirebaseFirestoreException
                                                .Code.ABORTED
                                );
                            }


                            updatedReporterIds.add(
                                    uid
                            );


                            Long storedUniqueCount =
                                    currentEvent.getLong(
                                            "uniqueUserCount"
                                    );


                            Long storedReportCount =
                                    currentEvent.getLong(
                                            "reportCount"
                                    );


                            Double storedSeveritySum =
                                    currentEvent.getDouble(
                                            "totalSeveritySum"
                                    );


                            long currentUniqueCount =
                                    storedUniqueCount != null

                                            ? storedUniqueCount

                                            : updatedReporterIds
                                              .size() - 1L;


                            long currentReportCount =
                                    storedReportCount != null

                                            ? storedReportCount

                                            : currentUniqueCount;


                            double currentSeveritySum =
                                    storedSeveritySum != null

                                            ? storedSeveritySum

                                            : 0.0;


                            long newUniqueCount =
                                    currentUniqueCount
                                            + 1L;


                            long newReportCount =
                                    currentReportCount
                                            + 1L;


                            double newSeveritySum =
                                    currentSeveritySum
                                            + severity;


                            double newAverageSeverity =
                                    newSeveritySum
                                            / newUniqueCount;


                            Map<String, Object> updates =
                                    new HashMap<>();


                            updates.put(
                                    "reporterIds",
                                    updatedReporterIds
                            );


                            updates.put(
                                    "uniqueUserCount",
                                    newUniqueCount
                            );


                            updates.put(
                                    "reportCount",
                                    newReportCount
                            );


                            updates.put(
                                    "totalSeveritySum",
                                    newSeveritySum
                            );


                            updates.put(
                                    "averageSeverity",
                                    newAverageSeverity
                            );


                            updates.put(
                                    "updatedAt",
                                    FieldValue.serverTimestamp()
                            );


                            if (
                                    imageUrl != null
                                            && !imageUrl.isEmpty()
                            ) {

                                updates.put(
                                        "photoGallery",
                                        FieldValue.arrayUnion(
                                                imageUrl
                                        )
                                );
                            }


                            String currentStatus =
                                    currentEvent.getString(
                                            "status"
                                    );


                            // only pending events cross public threshold
                            if (
                                    "PENDING"
                                            .equalsIgnoreCase(
                                                    currentStatus
                                            )

                                            && newUniqueCount
                                            >= PUBLIC_EVENT_THRESHOLD
                            ) {

                                updates.put(
                                        "status",
                                        "ACTIVE"
                                );
                            }


                            boolean isAnonymous =
                                    userSnapshot.exists()

                                            && Boolean.TRUE
                                            .equals(
                                                    userSnapshot
                                                            .getBoolean(
                                                                    "anonymousReporting"
                                                            )
                                            );


                            Report report =
                                    new Report(
                                            uid,
                                            isAnonymous,
                                            Report.Category
                                                    .valueOf(
                                                            category
                                                    ),
                                            severity,
                                            description,
                                            imageUrl,
                                            reportLat,
                                            reportLng,
                                            geohash
                                    );


                            report.setEventId(
                                    eventId
                            );


                            // event aggregate and user report commit together
                            transaction.update(
                                    eventRef,
                                    updates
                            );


                            transaction.set(
                                    reportRef,
                                    report
                            );


                            return null;
                        }
                )
                .addOnSuccessListener(
                        unused -> {

                            if (
                                    getContext()
                                            == null
                            ) {

                                return;
                            }


                            Toast.makeText(
                                    requireContext(),
                                    "Report submitted!",
                                    Toast.LENGTH_SHORT
                            ).show();


                            resetForm();
                        }
                )
                .addOnFailureListener(
                        error -> {

                            if (
                                    getContext()
                                            == null
                            ) {

                                return;
                            }


                            resetSubmitButton();


                            if (
                                    ERROR_ALREADY_REPORTED
                                            .equals(
                                                    error.getMessage()
                                            )
                            ) {

                                Toast.makeText(
                                        requireContext(),
                                        "You have already reported this issue",
                                        Toast.LENGTH_SHORT
                                ).show();

                                return;
                            }


                            Toast.makeText(
                                    requireContext(),
                                    "Failed to update event: "
                                            + error.getMessage(),
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                );
    }


    private void createNewEvent(
            String uid,
            String category,
            int severity,
            String imageUrl,
            String geohash,
            String description
    ) {

        final double reportLat =
                selectedLat;

        final double reportLng =
                selectedLng;


        db.collection(
                        "users"
                )
                .document(
                        uid
                )
                .get()
                .addOnSuccessListener(
                        userSnapshot -> {

                            if (
                                    getContext()
                                            == null
                            ) {

                                return;
                            }


                            boolean isAnonymous =
                                    userSnapshot.exists()

                                            && Boolean.TRUE
                                            .equals(
                                                    userSnapshot
                                                            .getBoolean(
                                                                    "anonymousReporting"
                                                            )
                                            );


                            DocumentReference eventRef =
                                    db.collection(
                                                    "events"
                                            )
                                            .document();


                            DocumentReference reportRef =
                                    db.collection(
                                                    "reports"
                                            )
                                            .document();


                            Event event =
                                    new Event(
                                            category,
                                            severity,
                                            uid,
                                            imageUrl,
                                            reportLat,
                                            reportLng,
                                            geohash
                                    );


                            Report report =
                                    new Report(
                                            uid,
                                            isAnonymous,
                                            Report.Category
                                                    .valueOf(
                                                            category
                                                    ),
                                            severity,
                                            description,
                                            imageUrl,
                                            reportLat,
                                            reportLng,
                                            geohash
                                    );


                            report.setEventId(
                                    eventRef.getId()
                            );


                            // atomic batch is enough as no shared state is read
                            WriteBatch batch =
                                    db.batch();


                            batch.set(
                                    eventRef,
                                    event
                            );


                            batch.set(
                                    reportRef,
                                    report
                            );


                            batch.commit()
                                    .addOnSuccessListener(
                                            unused -> {

                                                if (
                                                        getContext()
                                                                == null
                                                ) {

                                                    return;
                                                }


                                                Toast.makeText(
                                                        requireContext(),
                                                        "Report submitted!",
                                                        Toast.LENGTH_SHORT
                                                ).show();


                                                resetForm();
                                            }
                                    )
                                    .addOnFailureListener(
                                            error -> {

                                                if (
                                                        getContext()
                                                                == null
                                                ) {

                                                    return;
                                                }


                                                resetSubmitButton();


                                                Toast.makeText(
                                                        requireContext(),
                                                        "Failed to create event: "
                                                                + error.getMessage(),
                                                        Toast.LENGTH_SHORT
                                                ).show();
                                            }
                                    );
                        }
                )
                .addOnFailureListener(
                        error -> {

                            if (
                                    getContext()
                                            == null
                            ) {

                                return;
                            }


                            resetSubmitButton();


                            Toast.makeText(
                                    requireContext(),
                                    "Failed to fetch user settings: "
                                            + error.getMessage(),
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                );
    }


    private void resetSubmitButton() {

        if (
                btnSubmit == null
        ) {

            return;
        }


        btnSubmit.setEnabled(
                true
        );


        btnSubmit.setText(
                "Submit Report"
        );
    }


    private void resetForm() {

        selectedImageUri =
                null;

        selectedLat =
                0;

        selectedLng =
                0;


        imgPreview.setVisibility(
                View.GONE
        );


        dropdown.setText(
                ""
        );


        slider.setValue(
                5
        );


        tvDescription.setText(
                ""
        );


        tvSelectedAddress.setVisibility(
                View.GONE
        );


        if (
                previewMap != null
        ) {

            previewMap.clear();
        }


        if (
                getView() != null
        ) {

            getView()
                    .findViewById(
                            R.id.layout_upload_text
                    )
                    .setVisibility(
                            View.VISIBLE
                    );


            getView()
                    .findViewById(
                            R.id.layout_upload_prompt
                    )
                    .setBackgroundResource(
                            R.drawable.bg_dashed_border
                    );
        }


        resetSubmitButton();
    }


    @Override
    public void onMapReady(
            @NonNull GoogleMap googleMap
    ) {

        previewMap =
                googleMap;


        previewMap.getUiSettings()
                .setAllGesturesEnabled(
                        false
                );


        previewMap.getUiSettings()
                .setZoomControlsEnabled(
                        false
                );


        previewMap.getUiSettings()
                .setMapToolbarEnabled(
                        false
                );


        applyMapStyle(
                previewMap
        );
    }


    private final ActivityResultLauncher<String>
            launchLibrary =

            registerForActivityResult(
                    new ActivityResultContracts
                            .GetContent(),

                    uri -> {

                        if (
                                uri != null
                                        && getView()
                                        != null
                        ) {

                            selectedImageUri =
                                    uri;


                            imgPreview.setImageURI(
                                    uri
                            );


                            imgPreview.setVisibility(
                                    View.VISIBLE
                            );


                            getView()
                                    .findViewById(
                                            R.id.layout_upload_prompt
                                    )
                                    .setBackgroundResource(
                                            R.drawable
                                                    .bg_dashed_border_transparent
                                    );
                        }
                    }
            );


    private final ActivityResultLauncher<Uri>
            launchCamera =

            registerForActivityResult(
                    new ActivityResultContracts
                            .TakePicture(),

                    success -> {

                        if (
                                Boolean.TRUE.equals(
                                        success
                                )

                                        && cameraImageUri
                                        != null

                                        && getView()
                                        != null
                        ) {

                            selectedImageUri =
                                    cameraImageUri;


                            imgPreview.setImageURI(
                                    cameraImageUri
                            );


                            imgPreview.setVisibility(
                                    View.VISIBLE
                            );


                            getView()
                                    .findViewById(
                                            R.id.layout_upload_prompt
                                    )
                                    .setBackgroundResource(
                                            R.drawable
                                                    .bg_dashed_border_transparent
                                    );
                        }
                    }
            );


    private final ActivityResultLauncher<String>
            requestCameraPermission =

            registerForActivityResult(
                    new ActivityResultContracts
                            .RequestPermission(),

                    granted -> {

                        if (
                                Boolean.TRUE.equals(
                                        granted
                                )
                        ) {

                            openCamera();

                        } else {

                            Toast.makeText(
                                    requireContext(),
                                    "Camera permission is required",
                                    Toast.LENGTH_SHORT
                            ).show();
                        }
                    }
            );


    private void openCamera() {

        try {

            File photoFile =
                    File.createTempFile(
                            "photo_",
                            ".jpg",
                            requireContext()
                                    .getCacheDir()
                    );


            cameraImageUri =
                    FileProvider.getUriForFile(
                            requireContext(),
                            requireContext()
                                    .getPackageName()
                                    + ".provider",
                            photoFile
                    );


            launchCamera.launch(
                    cameraImageUri
            );

        } catch (
                IOException error
        ) {

            error.printStackTrace();
        }
    }


    private final ActivityResultLauncher<Intent>
            locationPicker =

            registerForActivityResult(
                    new ActivityResultContracts
                            .StartActivityForResult(),

                    result -> {

                        if (
                                result.getResultCode()
                                        == Activity.RESULT_OK

                                        && result.getData()
                                        != null
                        ) {

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


                            updateMapPreview(
                                    selectedLat,
                                    selectedLng
                            );


                            reverseGeocode(
                                    selectedLat,
                                    selectedLng
                            );
                        }
                    }
            );


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

            requestPermissions(
                    new String[] {
                            Manifest.permission
                                    .ACCESS_FINE_LOCATION
                    },
                    1001
            );

            return;
        }


        FusedLocationProviderClient client =
                LocationServices
                        .getFusedLocationProviderClient(
                                requireContext()
                        );


        client.getLastLocation()
                .addOnSuccessListener(
                        location -> {

                            if (
                                    location == null
                            ) {

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
                        }
                );
    }


    private void updateMapPreview(
            double lat,
            double lng
    ) {

        if (
                previewMap == null
        ) {

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
                        .position(
                                location
                        )
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

                String address =
                        addresses.get(
                                        0
                                )
                                .getAddressLine(
                                        0
                                );


                tvSelectedAddress.setText(
                        address
                );


                tvSelectedAddress.setVisibility(
                        View.VISIBLE
                );
            }

        } catch (
                IOException error
        ) {

            error.printStackTrace();
        }
    }


    private void applyMapStyle(
            GoogleMap map
    ) {

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

            map.setMapStyle(
                    MapStyleOptions
                            .loadRawResourceStyle(
                                    requireContext(),
                                    R.raw.map_style_dark
                            )
            );
        }
    }


    private void showSeverityTooltip(View anchor) {
        dismissSeverityTooltip();
        float density = getResources().getDisplayMetrics().density;
        int width = Math.min((int) (280 * density),
                getResources().getDisplayMetrics().widthPixels - (int) (32 * density));
        View content = getLayoutInflater().inflate(R.layout.tooltip_severity, null);
        severityTooltip = new PopupWindow(content, width,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        severityTooltip.setBackgroundDrawable(
                androidx.core.content.ContextCompat.getDrawable(
                        requireContext(), R.drawable.bg_severity_tooltip));
        severityTooltip.setElevation(6 * density);
        severityTooltip.setOutsideTouchable(true);
        content.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int height = content.getMeasuredHeight();
        severityTooltip.setHeight(height);
        android.graphics.Rect visibleFrame = new android.graphics.Rect();
        anchor.getWindowVisibleDisplayFrame(visibleFrame);
        int[] anchorPosition = new int[2];
        anchor.getLocationOnScreen(anchorPosition);
        int centeredLeft = visibleFrame.left + (visibleFrame.width() - width) / 2;
        severityTooltip.showAsDropDown(anchor, centeredLeft - anchorPosition[0],
                -anchor.getHeight() - height - (int) (6 * density));
    }

    private void dismissSeverityTooltip() {
        if (severityTooltip != null) {
            severityTooltip.dismiss();
            severityTooltip = null;
        }
    }

    @Override
    public void onDestroyView() {
        dismissSeverityTooltip();
        super.onDestroyView();
    }

    @Override
    public void onResume() {

        super.onResume();


        if (
                mapPreview != null
        ) {

            mapPreview.onResume();
        }
    }


    @Override
    public void onPause() {

        dismissSeverityTooltip();

        super.onPause();


        if (
                mapPreview != null
        ) {

            mapPreview.onPause();
        }
    }


    @Override
    public void onDestroy() {

        super.onDestroy();


        if (
                mapPreview != null
        ) {

            mapPreview.onDestroy();
        }
    }


    @Override
    public void onLowMemory() {

        super.onLowMemory();


        if (
                mapPreview != null
        ) {

            mapPreview.onLowMemory();
        }
    }


    @Override
    public void onSaveInstanceState(
            @NonNull Bundle outState
    ) {

        super.onSaveInstanceState(
                outState
        );


        if (
                mapPreview != null
        ) {

            mapPreview.onSaveInstanceState(
                    outState
            );
        }
    }
}
