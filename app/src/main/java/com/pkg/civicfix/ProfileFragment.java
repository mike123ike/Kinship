package com.pkg.civicfix;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.pkg.civicfix.model.ImportantLocation;
import com.pkg.civicfix.model.User;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ProfileFragment extends Fragment {

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private ImageView ivProfilePicture;

    private TextView tvProfileName;
    private TextView tvReportCount;
    private TextView tvFixedCount;

    private TextView tvImportantLocationsEdit;

    private View layoutAddImportantLocationEmpty;

    private LinearLayout profileLocationsContainer;

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
        super.onViewCreated(view, savedInstanceState);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        ivProfilePicture =
                view.findViewById(R.id.iv_profile_picture);

        tvProfileName =
                view.findViewById(R.id.tv_profile_name);

        tvReportCount =
                view.findViewById(R.id.tv_profile_report_count);

        tvFixedCount =
                view.findViewById(R.id.tv_profile_fixed_count);

        tvImportantLocationsEdit =
                view.findViewById(
                        R.id.tv_important_locations_edit
                );

        layoutAddImportantLocationEmpty =
                view.findViewById(
                        R.id.layout_add_important_location_empty
                );

        profileLocationsContainer =
                view.findViewById(
                        R.id.profile_locations_container
                );

        tvProfileName.setText("...");
        tvReportCount.setText("—");
        tvFixedCount.setText("—");

        tvImportantLocationsEdit.setOnClickListener(v ->
                openImportantLocationsActivity()
        );

        layoutAddImportantLocationEmpty.setOnClickListener(v ->
                openImportantLocationsActivity()
        );

        // refresh cards when bottom sheet updates a location
        getParentFragmentManager()
                .setFragmentResultListener(
                        ImportantLocationBottomSheet.RESULT_KEY,
                        getViewLifecycleOwner(),
                        (requestKey, result) ->
                                loadImportantLocations()
                );
    }

    @Override
    public void onResume() {
        super.onResume();

        loadProfileData();

        // refresh locations when returning to screen
        loadImportantLocations();
    }

    private void openImportantLocationsActivity() {

        Intent intent =
                new Intent(
                        requireContext(),
                        ImportantLocationsActivity.class
                );

        startActivity(intent);
    }

    // important locations logic

    private void loadImportantLocations() {

        FirebaseUser firebaseUser =
                auth.getCurrentUser();

        if (firebaseUser == null) {
            showNoImportantLocations();
            return;
        }

        tvImportantLocationsEdit.setVisibility(
                View.GONE
        );

        layoutAddImportantLocationEmpty.setVisibility(
                View.GONE
        );

        profileLocationsContainer.removeAllViews();

        db.collection("users")
                .document(firebaseUser.getUid())
                .collection("importantLocations")
                .get()
                .addOnSuccessListener(snapshot -> {

                    if (getView() == null) {
                        return;
                    }

                    List<ImportantLocation> locations =
                            new ArrayList<>();

                    for (
                            QueryDocumentSnapshot document
                            : snapshot
                    ) {

                        ImportantLocation location =
                                document.toObject(
                                        ImportantLocation.class
                                );

                        locations.add(location);
                    }

                    if (locations.isEmpty()) {

                        showNoImportantLocations();

                    } else {

                        showImportantLocations(
                                locations
                        );
                    }
                })
                .addOnFailureListener(e -> {

                    if (getContext() == null) {
                        return;
                    }

                    showNoImportantLocations();

                    Toast.makeText(
                            requireContext(),
                            "Failed to load important locations",
                            Toast.LENGTH_SHORT
                    ).show();
                });
    }

    private void showNoImportantLocations() {

        if (profileLocationsContainer == null) {
            return;
        }

        profileLocationsContainer.removeAllViews();

        tvImportantLocationsEdit.setVisibility(
                View.GONE
        );

        layoutAddImportantLocationEmpty.setVisibility(
                View.VISIBLE
        );
    }

    private void showImportantLocations(
            List<ImportantLocation> locations
    ) {

        layoutAddImportantLocationEmpty.setVisibility(
                View.GONE
        );

        tvImportantLocationsEdit.setVisibility(
                View.VISIBLE
        );

        profileLocationsContainer.removeAllViews();

        List<ImportantLocation> selected =
                chooseProfileLocations(
                        locations
                );

        for (
                ImportantLocation location
                : selected
        ) {

            addProfileLocationCard(
                    location
            );
        }
    }

    // show max 3 locations, prioritizing presets then newest custom
    private List<ImportantLocation>
    chooseProfileLocations(
            List<ImportantLocation> locations
    ) {

        List<ImportantLocation> sorted =
                new ArrayList<>(locations);

        sorted.sort(
                this::compareLocations
        );

        if (sorted.size() <= 3) {
            return sorted;
        }

        return new ArrayList<>(
                sorted.subList(
                        0,
                        3
                )
        );
    }

    private int compareLocations(
            ImportantLocation a,
            ImportantLocation b
    ) {

        int aPriority =
                getPresetPriority(
                        a.getType()
                );

        int bPriority =
                getPresetPriority(
                        b.getType()
                );

        if (aPriority != bPriority) {

            return Integer.compare(
                    aPriority,
                    bPriority
            );
        }

        // custom locations sorted by newest first
        Timestamp aTime =
                a.getUpdatedAt();

        Timestamp bTime =
                b.getUpdatedAt();

        if (aTime == null && bTime == null) {
            return 0;
        }

        if (aTime == null) {
            return 1;
        }

        if (bTime == null) {
            return -1;
        }

        return bTime.compareTo(aTime);
    }

    private int getPresetPriority(
            String type
    ) {

        if (
                ImportantLocation.TYPE_HOME
                        .equals(type)
        ) {
            return 0;
        }

        if (
                ImportantLocation.TYPE_WORK
                        .equals(type)
        ) {
            return 1;
        }

        if (
                ImportantLocation.TYPE_SCHOOL
                        .equals(type)
        ) {
            return 2;
        }

        return 3;
    }

    private void addProfileLocationCard(
            ImportantLocation location
    ) {

        View card =
                getLayoutInflater()
                        .inflate(
                                R.layout.item_important_location,
                                profileLocationsContainer,
                                false
                        );

        bindLocationCard(
                card,
                location
        );

        // profile view does not need chevron
        card.findViewById(
                R.id.iv_location_chevron
        ).setVisibility(
                View.GONE
        );

        card.setOnClickListener(v ->
                openLocationEditor(
                        location
                )
        );

        profileLocationsContainer.addView(
                card
        );
    }

    private void openLocationEditor(
            ImportantLocation location
    ) {

        if (
                isPresetType(
                        location.getType()
                )
        ) {

            ImportantLocationBottomSheet
                    .newPreset(
                            location.getType(),
                            location
                    )
                    .show(
                            getParentFragmentManager(),
                            "edit_profile_location"
                    );

        } else {

            ImportantLocationBottomSheet
                    .newCustom(
                            location
                    )
                    .show(
                            getParentFragmentManager(),
                            "edit_profile_location"
                    );
        }
    }

    private void bindLocationCard(
            View card,
            ImportantLocation location
    ) {

        TextView tvName =
                card.findViewById(
                        R.id.tv_location_name
                );

        TextView tvAddress =
                card.findViewById(
                        R.id.tv_location_address
                );

        tvName.setText(
                location.getName()
        );

        String displayAddress =
                location.getDisplayAddress();

        tvAddress.setText(
                displayAddress == null
                        || displayAddress.isEmpty()
                        ? "Selected location"
                        : displayAddress
        );

        configureLocationIcon(
                card,
                location.getType()
        );
    }

    private void configureLocationIcon(
            View card,
            String type
    ) {

        FrameLayout container =
                card.findViewById(
                        R.id.location_icon_container
                );

        ImageView icon =
                card.findViewById(
                        R.id.iv_location_icon
                );

        int iconColor;

        if (
                ImportantLocation.TYPE_HOME
                        .equals(type)
        ) {

            container.setBackgroundResource(
                    R.drawable.ic_setting_bg_green
            );

            icon.setImageResource(
                    R.drawable.ic_profile_home
            );

            iconColor =
                    R.color.profileLocationHomeIcon;

        } else if (
                ImportantLocation.TYPE_WORK
                        .equals(type)
        ) {

            container.setBackgroundResource(
                    R.drawable.ic_setting_bg_gray
            );

            icon.setImageResource(
                    R.drawable.ic_profile_work
            );

            iconColor =
                    R.color.profileLocationNeutralIcon;

        } else if (
                ImportantLocation.TYPE_SCHOOL
                        .equals(type)
        ) {

            container.setBackgroundResource(
                    R.drawable.ic_setting_bg_orange
            );

            icon.setImageResource(
                    R.drawable.ic_profile_school
            );

            iconColor =
                    R.color.profileLocationNeutralIcon;

        } else {

            container.setBackgroundResource(
                    R.drawable.ic_setting_bg_gray
            );

            icon.setImageResource(
                    R.drawable.ic_location
            );

            iconColor =
                    R.color.profileLocationNeutralIcon;
        }

        ImageViewCompat.setImageTintList(
                icon,
                ColorStateList.valueOf(
                        ContextCompat.getColor(
                                requireContext(),
                                iconColor
                        )
                )
        );
    }

    private boolean isPresetType(
            String type
    ) {

        return ImportantLocation.TYPE_HOME
                .equals(type)
                || ImportantLocation.TYPE_WORK
                .equals(type)
                || ImportantLocation.TYPE_SCHOOL
                .equals(type);
    }

    // load user profile info

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
                .into(ivProfilePicture);
    }

    private void showDefaultProfilePicture() {

        if (!isViewAlive()) {
            return;
        }

        Glide.with(this)
                .load(
                        R.drawable.ic_profile_placeholder
                )
                .into(ivProfilePicture);
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

                            if (
                                    documentSnapshot
                                            .exists()
                            ) {

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
                                            user.getDisplayName()
                                                    != null
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

                    tvReportCount.setText("—");
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

                                if (
                                        statusObject != null
                                                && "FIXED"
                                                .equalsIgnoreCase(
                                                        statusObject
                                                                .toString()
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

                    tvFixedCount.setText("—");
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

        tvProfileName.setText("User");
        tvReportCount.setText("0");
        tvFixedCount.setText("0");

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

        layoutAddImportantLocationEmpty = null;

        profileLocationsContainer = null;
    }
}