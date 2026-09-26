package com.pkg.civicfix;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.pkg.civicfix.model.User;

public class ProfileFragment extends Fragment {

    private static final boolean DUMMY_HAS_SAVED_LOCATION = false;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private ImageView ivProfilePicture;

    private TextView tvProfileName;
    private TextView tvReportCount;
    private TextView tvFixedCount;

    private TextView tvImportantLocationsEdit;

    private View layoutDummyHomeLocation;
    private View layoutAddImportantLocationEmpty;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {

        return inflater.inflate(
                R.layout.fragment_profile,
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



        ivProfilePicture =
                view.findViewById(
                        R.id.iv_profile_picture
                );

        tvProfileName =
                view.findViewById(
                        R.id.tv_profile_name
                );

        tvReportCount =
                view.findViewById(
                        R.id.tv_profile_report_count
                );

        tvFixedCount =
                view.findViewById(
                        R.id.tv_profile_fixed_count
                );



        tvImportantLocationsEdit =
                view.findViewById(
                        R.id.tv_important_locations_edit
                );

        layoutDummyHomeLocation =
                view.findViewById(
                        R.id.layout_dummy_home_location
                );

        layoutAddImportantLocationEmpty =
                view.findViewById(
                        R.id.layout_add_important_location_empty
                );


        tvProfileName.setText("...");
        tvReportCount.setText("—");
        tvFixedCount.setText("—");


        setupImportantLocationsUi();
    }

    private void setupImportantLocationsUi() {

        if (!DUMMY_HAS_SAVED_LOCATION) {

            tvImportantLocationsEdit.setVisibility(
                    View.GONE
            );

            layoutDummyHomeLocation.setVisibility(
                    View.GONE
            );

            layoutAddImportantLocationEmpty.setVisibility(
                    View.VISIBLE
            );

        }

        else {

            tvImportantLocationsEdit.setVisibility(
                    View.VISIBLE
            );

            layoutDummyHomeLocation.setVisibility(
                    View.VISIBLE
            );

            layoutAddImportantLocationEmpty.setVisibility(
                    View.GONE
            );
        }


        tvImportantLocationsEdit.setOnClickListener(v ->
                openImportantLocationsActivity()
        );


        layoutAddImportantLocationEmpty.setOnClickListener(v ->
                openImportantLocationsActivity()
        );


        layoutDummyHomeLocation.setOnClickListener(v -> {

            ImportantLocationBottomSheet
                    .newPreset(
                            "Home",
                            true,
                            "123 Main Street, Round Rock, TX",
                            30.5083,
                            -97.6789
                    )
                    .show(
                            getParentFragmentManager(),
                            "edit_home"
                    );
        });
    }

    private void openImportantLocationsActivity() {

        Intent intent =
                new Intent(
                        requireContext(),
                        ImportantLocationsActivity.class
                );

        startActivity(intent);
    }



    @Override
    public void onResume() {
        super.onResume();

        loadProfileData();
    }

    private void loadProfileData() {

        FirebaseUser firebaseUser =
                auth.getCurrentUser();

        if (firebaseUser == null) {

            showSignedOutState();

            return;
        }

        String uid =
                firebaseUser.getUid();

        loadGoogleProfilePicture(
                firebaseUser
        );

        loadUsername(
                uid,
                firebaseUser
        );

        loadReportCount(
                uid
        );

        loadFixedReportCount(
                uid
        );
    }



    private void loadGoogleProfilePicture(
            FirebaseUser firebaseUser
    ) {

        if (!isViewAlive()) {
            return;
        }

        Uri photoUrl =
                firebaseUser.getPhotoUrl();

        if (photoUrl == null) {

            showDefaultProfilePicture();

            return;
        }

        Glide.with(this)
                .load(photoUrl)
                .centerCrop()
                .placeholder(
                        R.drawable.ic_profile_placeholder
                )
                .error(
                        R.drawable.ic_profile_placeholder
                )
                .into(
                        ivProfilePicture
                );
    }

    private void showDefaultProfilePicture() {

        if (!isViewAlive()) {
            return;
        }

        Glide.with(this)
                .load(
                        R.drawable.ic_profile_placeholder
                )
                .into(
                        ivProfilePicture
                );
    }



    private void loadUsername(
            String uid,
            FirebaseUser firebaseUser
    ) {

        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(
                        documentSnapshot -> {

                            if (!isViewAlive()) {
                                return;
                            }

                            String name = null;

                            if (documentSnapshot.exists()) {

                                User user =
                                        documentSnapshot
                                                .toObject(
                                                        User.class
                                                );

                                if (user != null) {

                                    if (user.isDeleted()) {

                                        name =
                                                "Deleted User";

                                    } else if (
                                            user.getDisplayName() != null
                                                    && !user
                                                    .getDisplayName()
                                                    .trim()
                                                    .isEmpty()
                                    ) {

                                        name =
                                                user
                                                        .getDisplayName()
                                                        .trim();
                                    }
                                }
                            }

                            if (
                                    name == null
                                            || name.isEmpty()
                            ) {

                                name =
                                        getFirebaseDisplayName(
                                                firebaseUser
                                        );
                            }

                            tvProfileName.setText(
                                    name
                            );
                        }
                )
                .addOnFailureListener(e -> {

                    if (!isViewAlive()) {
                        return;
                    }

                    tvProfileName.setText(
                            getFirebaseDisplayName(
                                    firebaseUser
                            )
                    );
                });
    }



    private void loadReportCount(
            String uid
    ) {

        db.collection("reports")
                .whereEqualTo(
                        "userId",
                        uid
                )
                .get()
                .addOnSuccessListener(
                        querySnapshot -> {

                            if (!isViewAlive()) {
                                return;
                            }

                            tvReportCount.setText(
                                    String.valueOf(
                                            querySnapshot.size()
                                    )
                            );
                        }
                )
                .addOnFailureListener(e -> {

                    if (!isViewAlive()) {
                        return;
                    }

                    tvReportCount.setText(
                            "—"
                    );
                });
    }



    private void loadFixedReportCount(
            String uid
    ) {

        db.collection("events")
                .whereArrayContains(
                        "reporterIds",
                        uid
                )
                .get()
                .addOnSuccessListener(
                        querySnapshot -> {

                            if (!isViewAlive()) {
                                return;
                            }

                            int fixedCount = 0;

                            for (
                                    QueryDocumentSnapshot document
                                    : querySnapshot
                            ) {

                                Object statusObject =
                                        document.get(
                                                "status"
                                        );

                                if (statusObject == null) {
                                    continue;
                                }

                                String status =
                                        statusObject
                                                .toString();

                                if (
                                        "FIXED"
                                                .equalsIgnoreCase(
                                                        status
                                                )
                                ) {

                                    fixedCount++;
                                }
                            }

                            tvFixedCount.setText(
                                    String.valueOf(
                                            fixedCount
                                    )
                            );
                        }
                )
                .addOnFailureListener(e -> {

                    if (!isViewAlive()) {
                        return;
                    }

                    tvFixedCount.setText(
                            "—"
                    );
                });
    }



    private String getFirebaseDisplayName(
            FirebaseUser firebaseUser
    ) {

        String displayName =
                firebaseUser.getDisplayName();

        if (
                displayName != null
                        && !displayName
                        .trim()
                        .isEmpty()
        ) {

            return displayName.trim();
        }

        String email =
                firebaseUser.getEmail();

        if (
                email != null
                        && email.contains("@")
        ) {

            return email.substring(
                    0,
                    email.indexOf("@")
            );
        }

        return "User";
    }



    private void showSignedOutState() {

        if (!isViewAlive()) {
            return;
        }

        tvProfileName.setText(
                "User"
        );

        tvReportCount.setText(
                "0"
        );

        tvFixedCount.setText(
                "0"
        );

        showDefaultProfilePicture();
    }



    private boolean isViewAlive() {

        return ivProfilePicture != null
                && tvProfileName != null
                && tvReportCount != null
                && tvFixedCount != null;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        ivProfilePicture = null;

        tvProfileName = null;
        tvReportCount = null;
        tvFixedCount = null;

        tvImportantLocationsEdit = null;

        layoutDummyHomeLocation = null;
        layoutAddImportantLocationEmpty = null;
    }
}