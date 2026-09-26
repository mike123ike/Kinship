package com.pkg.civicfix;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.pkg.civicfix.base.CivicFixActivity;
import com.pkg.civicfix.model.ImportantLocation;

import java.util.ArrayList;
import java.util.List;

public class ImportantLocationsActivity extends CivicFixActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private LinearLayout presetLocationsContainer;
    private LinearLayout customLocationsContainer;
    private TextView tvOtherLocationsHeader;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.important_locations_activity);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        if (toolbar.getNavigationIcon() != null) {
            toolbar.getNavigationIcon().setTint(
                    ContextCompat.getColor(this, R.color.tertiary)
            );
        }

        presetLocationsContainer = findViewById(R.id.preset_locations_container);
        customLocationsContainer = findViewById(R.id.custom_locations_container);
        tvOtherLocationsHeader = findViewById(R.id.tv_other_locations_header);

        findViewById(R.id.btn_add_location).setOnClickListener(v ->
                ImportantLocationBottomSheet
                        .newCustom(null)
                        .show(getSupportFragmentManager(), "add_custom_location")
        );

        getSupportFragmentManager().setFragmentResultListener(
                ImportantLocationBottomSheet.RESULT_KEY,
                this,
                (requestKey, result) -> loadLocations()
        );

        loadLocations();
    }

    private void loadLocations() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "You must be signed in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        db.collection("users")
                .document(user.getUid())
                .collection("importantLocations")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<ImportantLocation> locations = snapshot.toObjects(ImportantLocation.class);
                    renderLocations(locations);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(
                                this,
                                "Failed to load locations: " + e.getMessage(),
                                Toast.LENGTH_SHORT
                        ).show()
                );
    }

    private void renderLocations(List<ImportantLocation> locations) {
        presetLocationsContainer.removeAllViews();
        customLocationsContainer.removeAllViews();

        ImportantLocation home = null;
        ImportantLocation work = null;
        ImportantLocation school = null;
        List<ImportantLocation> custom = new ArrayList<>();

        for (ImportantLocation location : locations) {
            if (ImportantLocation.TYPE_HOME.equals(location.getType())) {
                home = location;
            } else if (ImportantLocation.TYPE_WORK.equals(location.getType())) {
                work = location;
            } else if (ImportantLocation.TYPE_SCHOOL.equals(location.getType())) {
                school = location;
            } else {
                custom.add(location);
            }
        }

        addPresetCard(ImportantLocation.TYPE_HOME, home);
        addPresetCard(ImportantLocation.TYPE_WORK, work);
        addPresetCard(ImportantLocation.TYPE_SCHOOL, school);

        custom.sort((a, b) -> compareUpdatedAt(b, a));

        tvOtherLocationsHeader.setVisibility(custom.isEmpty() ? View.GONE : View.VISIBLE);

        for (ImportantLocation location : custom) {
            addCustomCard(location);
        }
    }

    private void addPresetCard(String type, @Nullable ImportantLocation location) {
        View card = getLayoutInflater().inflate(
                R.layout.item_important_location,
                presetLocationsContainer,
                false
        );

        String name = getPresetDisplayName(type);

        TextView tvName = card.findViewById(R.id.tv_location_name);
        TextView tvAddress = card.findViewById(R.id.tv_location_address);

        tvName.setText(name);

        if (location == null) {
            tvAddress.setText("Add " + name.toLowerCase() + " location");
            tvAddress.setTextColor(ContextCompat.getColor(this, R.color.primary));
        } else {
            tvAddress.setText(location.getDisplayAddress());
            tvAddress.setTextColor(ContextCompat.getColor(this, R.color.textSecondary));
        }

        configureLocationIcon(card, type);

        card.setOnClickListener(v ->
                ImportantLocationBottomSheet
                        .newPreset(type, location)
                        .show(getSupportFragmentManager(), "preset_location")
        );

        presetLocationsContainer.addView(card);
    }

    private void addCustomCard(ImportantLocation location) {
        View card = getLayoutInflater().inflate(
                R.layout.item_important_location,
                customLocationsContainer,
                false
        );

        TextView tvName = card.findViewById(R.id.tv_location_name);
        TextView tvAddress = card.findViewById(R.id.tv_location_address);

        tvName.setText(location.getName());
        tvAddress.setText(location.getDisplayAddress());

        configureLocationIcon(card, ImportantLocation.TYPE_CUSTOM);

        card.setOnClickListener(v ->
                ImportantLocationBottomSheet
                        .newCustom(location)
                        .show(getSupportFragmentManager(), "custom_location")
        );

        customLocationsContainer.addView(card);
    }

    private void configureLocationIcon(View card, String type) {
        FrameLayout container = card.findViewById(R.id.location_icon_container);
        ImageView icon = card.findViewById(R.id.iv_location_icon);

        int iconColor;

        if (ImportantLocation.TYPE_HOME.equals(type)) {
            container.setBackgroundResource(R.drawable.ic_setting_bg_green);
            icon.setImageResource(R.drawable.ic_profile_home);
            iconColor = R.color.profileLocationHomeIcon;
        } else if (ImportantLocation.TYPE_WORK.equals(type)) {
            container.setBackgroundResource(R.drawable.ic_setting_bg_gray);
            icon.setImageResource(R.drawable.ic_profile_work);
            iconColor = R.color.profileLocationNeutralIcon;
        } else if (ImportantLocation.TYPE_SCHOOL.equals(type)) {
            container.setBackgroundResource(R.drawable.ic_setting_bg_orange);
            icon.setImageResource(R.drawable.ic_profile_school);
            iconColor = R.color.profileLocationNeutralIcon;
        } else {
            container.setBackgroundResource(R.drawable.ic_setting_bg_gray);
            icon.setImageResource(R.drawable.ic_location);
            iconColor = R.color.profileLocationNeutralIcon;
        }

        ImageViewCompat.setImageTintList(
                icon,
                ColorStateList.valueOf(ContextCompat.getColor(this, iconColor))
        );
    }

    private String getPresetDisplayName(String type) {
        if (ImportantLocation.TYPE_HOME.equals(type)) {
            return "Home";
        }
        if (ImportantLocation.TYPE_WORK.equals(type)) {
            return "Work";
        }
        return "School";
    }

    private int compareUpdatedAt(ImportantLocation a, ImportantLocation b) {
        Timestamp aTimestamp = a.getUpdatedAt();
        Timestamp bTimestamp = b.getUpdatedAt();

        if (aTimestamp == null && bTimestamp == null) {
            return 0;
        }
        if (aTimestamp == null) {
            return -1;
        }
        if (bTimestamp == null) {
            return 1;
        }

        return aTimestamp.compareTo(bTimestamp);
    }
}