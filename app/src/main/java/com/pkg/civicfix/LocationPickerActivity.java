package com.pkg.civicfix;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.pkg.civicfix.base.CivicFixActivity;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class LocationPickerActivity
        extends CivicFixActivity
        implements OnMapReadyCallback {

    private GoogleMap mMap;

    private TextView tvAddress;


    private double selectedLat =
            30.2672;

    private double selectedLng =
            -97.7431;

    private String selectedAddress =
            "";


    private boolean hasInitialLocation =
            false;


    @Override
    protected void onCreate(
            Bundle savedInstanceState
    ) {

        super.onCreate(
                savedInstanceState
        );


        setContentView(
                R.layout.location_picker_activity
        );


        tvAddress =
                findViewById(
                        R.id.tv_address
                );


        Intent intent =
                getIntent();


        if (
                intent.hasExtra(
                        "initialLat"
                )

                        && intent.hasExtra(
                        "initialLng"
                )
        ) {

            selectedLat =
                    intent.getDoubleExtra(
                            "initialLat",
                            selectedLat
                    );


            selectedLng =
                    intent.getDoubleExtra(
                            "initialLng",
                            selectedLng
                    );


            hasInitialLocation =
                    true;
        }


        findViewById(
                R.id.btn_back
        ).setOnClickListener(
                v -> finish()
        );


        findViewById(
                R.id.btn_confirm
        ).setOnClickListener(
                v -> {

                    Intent result =
                            new Intent();


                    result.putExtra(
                            "lat",
                            selectedLat
                    );


                    result.putExtra(
                            "lng",
                            selectedLng
                    );


                    result.putExtra(
                            "address",
                            selectedAddress
                    );


                    setResult(
                            RESULT_OK,
                            result
                    );


                    finish();
                }
        );


        SupportMapFragment mapFragment =
                (SupportMapFragment)
                        getSupportFragmentManager()
                                .findFragmentById(
                                        R.id.map_picker
                                );


        if (
                mapFragment != null
        ) {

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


        mMap.getUiSettings()
                .setZoomControlsEnabled(
                        true
                );


        mMap.getUiSettings()
                .setMapToolbarEnabled(
                        false
                );


        mMap.getUiSettings()
                .setMyLocationButtonEnabled(
                        false
                );


        mMap.setOnCameraIdleListener(
                () -> {

                    LatLng centre =
                            mMap
                                    .getCameraPosition()
                                    .target;


                    selectedLat =
                            centre.latitude;


                    selectedLng =
                            centre.longitude;


                    reverseGeocode(
                            selectedLat,
                            selectedLng
                    );
                }
        );


        if (
                hasInitialLocation
        ) {

            LatLng initial =
                    new LatLng(
                            selectedLat,
                            selectedLng
                    );


            mMap.moveCamera(
                    CameraUpdateFactory
                            .newLatLngZoom(
                                    initial,
                                    16
                            )
            );


            reverseGeocode(
                    selectedLat,
                    selectedLng
            );


        } else {

            moveToCurrentLocationOrDefault();
        }


        applyMapStyle(
                mMap
        );
    }


    private void moveToCurrentLocationOrDefault() {

        if (
                ActivityCompat
                        .checkSelfPermission(
                                this,
                                Manifest.permission
                                        .ACCESS_FINE_LOCATION
                        )
                        == PackageManager
                        .PERMISSION_GRANTED
        ) {

            FusedLocationProviderClient client =
                    LocationServices
                            .getFusedLocationProviderClient(
                                    this
                            );


            client.getLastLocation()
                    .addOnSuccessListener(
                            location -> {

                                if (
                                        location != null
                                ) {

                                    selectedLat =
                                            location
                                                    .getLatitude();


                                    selectedLng =
                                            location
                                                    .getLongitude();


                                    LatLng current =
                                            new LatLng(
                                                    selectedLat,
                                                    selectedLng
                                            );


                                    mMap.moveCamera(
                                            CameraUpdateFactory
                                                    .newLatLngZoom(
                                                            current,
                                                            16
                                                    )
                                    );


                                } else {

                                    moveToDefault();
                                }
                            }
                    );


        } else {

            moveToDefault();
        }
    }


    private void moveToDefault() {

        mMap.moveCamera(
                CameraUpdateFactory
                        .newLatLngZoom(
                                new LatLng(
                                        selectedLat,
                                        selectedLng
                                ),
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
                            this,
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

                selectedAddress =
                        addresses
                                .get(0)
                                .getAddressLine(
                                        0
                                );


                tvAddress.setText(
                        selectedAddress
                );


            } else {

                selectedAddress =
                        "";


                tvAddress.setText(
                        "Selected location"
                );
            }


        } catch (
                IOException e
        ) {

            e.printStackTrace();


            selectedAddress =
                    "";


            tvAddress.setText(
                    "Selected location"
            );
        }
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
                                    this,
                                    R.raw.map_style_dark
                            )
            );
        }
    }
}