package com.pkg.civicfix;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EventPhotosFragment extends Fragment {

    private static final String ARG_EVENT_ID =
            "event_id";

    private FirebaseFirestore db;
    private String eventId;

    private GridLayout photoGrid;
    private TextView titleView;
    private TextView countView;
    private TextView emptyView;

    public static EventPhotosFragment newInstance(
            String eventId
    ) {
        EventPhotosFragment fragment =
                new EventPhotosFragment();

        Bundle args = new Bundle();
        args.putString(ARG_EVENT_ID, eventId);
        fragment.setArguments(args);

        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(
                R.layout.fragment_event_photos,
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

        eventId = getArguments() != null
                ? getArguments().getString(ARG_EVENT_ID)
                : null;

        photoGrid = view.findViewById(
                R.id.grid_event_photos
        );

        titleView = view.findViewById(
                R.id.tv_photos_title
        );

        countView = view.findViewById(
                R.id.tv_photos_count
        );

        emptyView = view.findViewById(
                R.id.tv_photos_empty
        );

        view.findViewById(R.id.btn_photos_back)
                .setOnClickListener(v ->
                        requireActivity()
                                .getSupportFragmentManager()
                                .popBackStack()
                );

        if (eventId != null) {
            loadEvent();
        } else {
            showEmptyState();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (requireActivity()
                instanceof MainActivity) {

            ((MainActivity) requireActivity())
                    .setMainChromeVisible(false);
        }
    }

    @Override
    public void onDestroyView() {
        if (requireActivity()
                instanceof MainActivity) {

            ((MainActivity) requireActivity())
                    .setMainChromeVisible(true);
        }

        super.onDestroyView();
    }

    @SuppressWarnings("unchecked")
    private void loadEvent() {
        db.collection("events")
                .document(eventId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!isAdded()
                            || !doc.exists()) {
                        return;
                    }

                    String title =
                            formatCategory(
                                    doc.getString("category")
                            );

                    titleView.setText(
                            title + " photos"
                    );

                    List<String> photos =
                            (List<String>)
                                    doc.get("photoGallery");

                    if (photos == null
                            || photos.isEmpty()) {

                        showEmptyState();
                        return;
                    }

                    List<String> newestFirst =
                            new ArrayList<>(photos);

                    Collections.reverse(newestFirst);

                    countView.setText(
                            newestFirst.size() == 1
                                    ? "1 photo"
                                    : newestFirst.size()
                                      + " photos"
                    );

                    emptyView.setVisibility(View.GONE);

                    addPhotoTiles(newestFirst);
                })
                .addOnFailureListener(
                        error -> showEmptyState()
                );
    }

    private void addPhotoTiles(
            List<String> photos
    ) {
        photoGrid.removeAllViews();

        int spacing = dp(6);

        int width =
                (
                        getResources()
                                .getDisplayMetrics()
                                .widthPixels
                                - dp(36)
                ) / 2;

        for (String photoUrl : photos) {
            MaterialCardView card =
                    new MaterialCardView(
                            requireContext()
                    );

            GridLayout.LayoutParams params =
                    new GridLayout.LayoutParams();

            params.width = width;
            params.height = width;

            params.setMargins(
                    spacing,
                    spacing,
                    spacing,
                    spacing
            );

            card.setLayoutParams(params);
            card.setRadius(dp(10));
            card.setCardElevation(0);

            ImageView image =
                    new ImageView(
                            requireContext()
                    );

            image.setLayoutParams(
                    new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams
                                    .MATCH_PARENT,
                            ViewGroup.LayoutParams
                                    .MATCH_PARENT
                    )
            );

            image.setScaleType(
                    ImageView.ScaleType.CENTER_CROP
            );

            image.setContentDescription(
                    "Uploaded event photo"
            );

            card.addView(image);

            Glide.with(this)
                    .load(photoUrl)
                    .centerCrop()
                    .into(image);

            photoGrid.addView(card);
        }
    }

    private void showEmptyState() {
        if (!isAdded()) {
            return;
        }

        countView.setText("0 photos");
        emptyView.setVisibility(View.VISIBLE);
        photoGrid.removeAllViews();
    }

    private int dp(int value) {
        return Math.round(
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    private String formatCategory(
            String category
    ) {
        if (category == null) {
            return "Event";
        }

        switch (category) {
            case "ROAD":
                return "Road issue";

            case "LIGHTING":
                return "Lighting issue";

            case "HAZARDS":
                return "Hazard report";

            case "VANDALISM":
                return "Vandalism report";

            case "SAFETY":
                return "Safety concern";

            default:
                return "Event";
        }
    }
}