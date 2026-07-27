package com.pkg.civicfix;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EventPopupFragment extends BottomSheetDialogFragment {

    private static final String ARG_EVENT_ID = "event_id";

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String eventId;

    private ImageView ivPhoto;
    private View layoutNoImage;
    private TextView tvPhotoCount;
    private TextView tvTitle;
    private TextView tvSeverityBadge;
    private TextView tvAddress;
    private TextView tvTimeAgo;
    private TextView tvVoteScore;
    private TextView tvCommentCount;
    private TextView tvCommentAvatar;
    private TextView tvCommentUser;
    private TextView tvCommentTime;
    private TextView tvCommentText;
    private TextView tvCommentHearts;
    private View layoutTopComment;
    private View layoutCommentAuthor;
    private MaterialButton btnUpvote;
    private MaterialButton btnDownvote;
    private View btnClose;

    private int currentVoteScore = 0;
    private int userVote = 0; // 0 = no vote, 1 = upvote, -1 = downvote

    public static EventPopupFragment newInstance(String eventId) {
        EventPopupFragment fragment = new EventPopupFragment();
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
                R.layout.fragment_event_popup,
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
        auth = FirebaseAuth.getInstance();

        eventId = getArguments() != null
                ? getArguments().getString(ARG_EVENT_ID)
                : null;

        ivPhoto = view.findViewById(R.id.iv_popup_photo);
        layoutNoImage = view.findViewById(R.id.layout_no_image);
        tvPhotoCount = view.findViewById(R.id.tv_photo_count);
        tvTitle = view.findViewById(R.id.tv_popup_title);
        tvSeverityBadge = view.findViewById(R.id.tv_severity_badge);
        tvAddress = view.findViewById(R.id.tv_popup_address);
        tvTimeAgo = view.findViewById(R.id.tv_popup_time);
        tvVoteScore = view.findViewById(R.id.tv_vote_score);
        tvCommentCount = view.findViewById(R.id.tv_comment_count);
        tvCommentAvatar = view.findViewById(R.id.tv_comment_avatar);
        tvCommentUser = view.findViewById(R.id.tv_comment_user);
        tvCommentTime = view.findViewById(R.id.tv_comment_time);
        tvCommentText = view.findViewById(R.id.tv_comment_text);
        tvCommentHearts = view.findViewById(R.id.tv_comment_hearts);
        layoutTopComment = view.findViewById(R.id.layout_top_comment);
        layoutCommentAuthor = view.findViewById(
                R.id.layout_comment_author
        );
        btnUpvote = view.findViewById(R.id.btn_upvote);
        btnDownvote = view.findViewById(R.id.btn_downvote);
        btnClose = view.findViewById(R.id.btn_close);

        btnClose.setOnClickListener(v -> dismiss());

        btnUpvote.setOnClickListener(v -> castVote(1));
        btnDownvote.setOnClickListener(v -> castVote(-1));

        View.OnClickListener openPhotos =
                v -> openPhotosPage();

        ivPhoto.setOnClickListener(openPhotos);
        tvPhotoCount.setOnClickListener(openPhotos);

        layoutTopComment.setOnClickListener(
                v -> openCommentsPage()
        );

        if (eventId != null) {
            loadEventData();
            loadUserVote();
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        if (getDialog() instanceof BottomSheetDialog) {
            BottomSheetBehavior<?> behavior =
                    ((BottomSheetDialog) getDialog()).getBehavior();

            behavior.setSkipCollapsed(true);
            behavior.setState(
                    BottomSheetBehavior.STATE_EXPANDED
            );
        }
    }

    private void loadEventData() {
        db.collection("events")
                .document(eventId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!isAdded()
                            || doc == null
                            || !doc.exists()) {
                        return;
                    }

                    populateViews(doc);
                    loadCommunityPreview();
                });
    }

    private void populateViews(DocumentSnapshot doc) {
        showEventPhoto(doc);

        String category = doc.getString("category");
        tvTitle.setText(formatCategory(category));

        Double severity = doc.getDouble("averageSeverity");

        if (severity != null) {
            String severityText =
                    getSeverityLabel(severity)
                            + " • "
                            + String.format(
                            Locale.getDefault(),
                            "%.1f",
                            severity
                    );

            tvSeverityBadge.setText(severityText);
            setSeverityBadgeColor(
                    getSeverityColor(severity)
            );
        } else {
            tvSeverityBadge.setText("Not rated");
            setSeverityBadgeColor(0xFF9CA3AF);
        }

        Double lat = doc.getDouble("latitude");
        Double lng = doc.getDouble("longitude");

        if (lat != null && lng != null) {
            reverseGeocode(lat, lng);
        } else {
            tvAddress.setText("Location unavailable");
        }

        com.google.firebase.Timestamp createdAt =
                doc.getTimestamp("createdAt");

        if (createdAt != null) {
            tvTimeAgo.setText(
                    "Posted "
                            + formatTimeAgo(
                            createdAt.toDate()
                    )
            );
        } else {
            tvTimeAgo.setText(
                    "Posted date unavailable"
            );
        }

        Long voteScore = doc.getLong("voteScore");

        currentVoteScore = voteScore != null
                ? voteScore.intValue()
                : 0;

        tvVoteScore.setText(
                String.valueOf(currentVoteScore)
        );
    }

    @SuppressWarnings("unchecked")
    private void showEventPhoto(DocumentSnapshot doc) {
        List<String> photos =
                (List<String>) doc.get("photoGallery");

        tvPhotoCount.setVisibility(View.GONE);

        if (photos != null && !photos.isEmpty()) {
            ivPhoto.setVisibility(View.VISIBLE);
            layoutNoImage.setVisibility(View.GONE);

            /*
             * New photo URLs are appended to photoGallery,
             * so the last URL is the most recently uploaded.
             */
            String newestPhotoUrl =
                    photos.get(photos.size() - 1);

            Glide.with(this)
                    .load(newestPhotoUrl)
                    .centerCrop()
                    .into(ivPhoto);

            tvPhotoCount.setVisibility(View.VISIBLE);

            tvPhotoCount.setText(
                    photos.size() == 1
                            ? "View photo"
                            : "View "
                              + photos.size()
                              + " photos"
            );
        } else {
            ivPhoto.setVisibility(View.GONE);
            layoutNoImage.setVisibility(View.VISIBLE);
        }
    }

    private void setSeverityBadgeColor(int color) {
        if (tvSeverityBadge.getBackground()
                instanceof GradientDrawable) {

            GradientDrawable background =
                    (GradientDrawable)
                            tvSeverityBadge
                                    .getBackground()
                                    .mutate();

            background.setColor(color);
        }
    }

    private void loadCommunityPreview() {
        db.collection("events")
                .document(eventId)
                .collection("comments")
                .orderBy(
                        "createdAt",
                        Query.Direction.DESCENDING
                )
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!isAdded()) {
                        return;
                    }

                    showCommentCount(snapshot.size());
                    showLatestComment(snapshot);
                })
                .addOnFailureListener(error -> {
                    if (!isAdded()) {
                        return;
                    }

                    tvCommentCount.setText(
                            "Comments unavailable"
                    );

                    showNoCommentsState(
                            "Could not load community comments"
                    );
                });
    }

    private void showCommentCount(int count) {
        String text = count == 1
                ? "1 comment"
                : count + " comments";

        tvCommentCount.setText(text + "  ›");
    }

    private void showLatestComment(
            QuerySnapshot snapshot
    ) {
        if (snapshot.isEmpty()) {
            showNoCommentsState(
                    "No comments yet — start the conversation"
            );
            return;
        }

        // results are newest-first.
        // prefer the comment with the highest heartCount.
        // if every comment has zero hearts, the initially selected document will be the newest comment.
        DocumentSnapshot selected =
                snapshot.getDocuments().get(0);

        long highestHeartCount = 0;

        for (DocumentSnapshot candidate
                : snapshot.getDocuments()) {

            Long storedHeartCount =
                    candidate.getLong("heartCount");

            long candidateHeartCount =
                    storedHeartCount == null
                            ? 0
                            : storedHeartCount;

            if (candidateHeartCount
                    > highestHeartCount) {

                highestHeartCount =
                        candidateHeartCount;

                selected = candidate;
            }
        }

        String text = selected.getString("text");
        String userId =
                selected.getString("userId");

        Boolean isAnonymous =
                selected.getBoolean("isAnonymous");

        com.google.firebase.Timestamp commentTime =
                selected.getTimestamp("createdAt");

        layoutCommentAuthor.setVisibility(View.VISIBLE);

        tvCommentText.setText(
                text == null || text.trim().isEmpty()
                        ? "Comment has no text"
                        : text
        );

        tvCommentTime.setText(
                commentTime == null
                        ? ""
                        : formatTimeAgo(
                        commentTime.toDate()
                )
        );

        if (highestHeartCount > 0) {
            tvCommentHearts.setText(
                    "♥ " + highestHeartCount
            );

            tvCommentHearts.setVisibility(
                    View.VISIBLE
            );
        } else {
            tvCommentHearts.setVisibility(
                    View.GONE
            );
        }

        if (Boolean.TRUE.equals(isAnonymous)
                || userId == null) {

            showAnonymousCommentAuthor();
        } else {
            loadCommentUser(userId);
        }
    }

    private void showNoCommentsState(
            String message
    ) {
        layoutCommentAuthor.setVisibility(View.GONE);
        tvCommentHearts.setVisibility(View.GONE);
        tvCommentText.setText(message);
    }

    private void openPhotosPage() {
        if (eventId == null
                || !(requireActivity()
                instanceof MainActivity)) {
            return;
        }

        dismiss();

        ((MainActivity) requireActivity())
                .openEventPhotos(eventId);
    }

    private void openCommentsPage() {
        if (eventId == null
                || !(requireActivity()
                instanceof MainActivity)) {
            return;
        }

        dismiss();

        ((MainActivity) requireActivity())
                .openEventComments(eventId);
    }

    private void showAnonymousCommentAuthor() {
        tvCommentUser.setText("Anonymous");
        tvCommentAvatar.setText("A");
    }

    private void loadCommentUser(String uid) {
        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!isAdded()) {
                        return;
                    }

                    String name =
                            doc.getString("displayName");

                    if (name != null
                            && !name.trim().isEmpty()) {

                        String trimmedName =
                                name.trim();

                        tvCommentUser.setText(
                                trimmedName
                        );

                        tvCommentAvatar.setText(
                                String.valueOf(
                                                trimmedName
                                                        .charAt(0)
                                        )
                                        .toUpperCase(
                                                Locale.getDefault()
                                        )
                        );
                    } else {
                        showAnonymousCommentAuthor();
                    }
                })
                .addOnFailureListener(error -> {
                    if (isAdded()) {
                        showAnonymousCommentAuthor();
                    }
                });
    }

    private void loadUserVote() {
        String uid = auth.getCurrentUser() != null
                ? auth.getCurrentUser().getUid()
                : null;

        if (uid == null) {
            return;
        }

        db.collection("events")
                .document(eventId)
                .collection("votes")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!isAdded() || !doc.exists()) {
                        return;
                    }

                    Long vote = doc.getLong("vote");

                    if (vote != null) {
                        userVote = vote.intValue();
                        updateVoteButtonStyles();
                    }
                });
    }

    private void castVote(int vote) {
        String uid = auth.getCurrentUser() != null
                ? auth.getCurrentUser().getUid()
                : null;

        if (uid == null) {
            Toast.makeText(
                    requireContext(),
                    "Sign in to vote",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        int previousVote = userVote;
        int scoreDelta;

        if (userVote == vote) {
            userVote = 0;
            scoreDelta = -vote;
        } else {
            scoreDelta = vote - previousVote;
            userVote = vote;
        }

        currentVoteScore += scoreDelta;

        tvVoteScore.setText(
                String.valueOf(currentVoteScore)
        );

        updateVoteButtonStyles();

        if (userVote == 0) {
            db.collection("events")
                    .document(eventId)
                    .collection("votes")
                    .document(uid)
                    .delete();
        } else {
            Map<String, Object> voteData =
                    new HashMap<>();

            voteData.put("vote", userVote);
            voteData.put(
                    "createdAt",
                    com.google.firebase.Timestamp.now()
            );

            db.collection("events")
                    .document(eventId)
                    .collection("votes")
                    .document(uid)
                    .set(voteData);
        }

        db.collection("events")
                .document(eventId)
                .update(
                        "voteScore",
                        FieldValue.increment(scoreDelta)
                );
    }

    private void updateVoteButtonStyles() {
        int activeColor =
                requireContext()
                        .getColor(R.color.primary);

        int inactiveColor =
                requireContext()
                        .getColor(
                                R.color.textSecondary
                        );

        btnUpvote.setIconTint(
                android.content.res.ColorStateList
                        .valueOf(
                                userVote == 1
                                        ? activeColor
                                        : inactiveColor
                        )
        );

        btnDownvote.setIconTint(
                android.content.res.ColorStateList
                        .valueOf(
                                userVote == -1
                                        ? activeColor
                                        : inactiveColor
                        )
        );
    }

    private void reverseGeocode(
            double lat,
            double lng
    ) {
        new Thread(() -> {
            try {
                android.location.Geocoder geocoder =
                        new android.location.Geocoder(
                                requireContext(),
                                Locale.getDefault()
                        );

                List<android.location.Address> addresses =
                        geocoder.getFromLocation(
                                lat,
                                lng,
                                1
                        );

                String address = null;

                if (addresses != null
                        && !addresses.isEmpty()) {

                    address = addresses
                            .get(0)
                            .getAddressLine(0);
                }

                final String resolvedAddress =
                        address == null
                                || address.trim().isEmpty()
                                ? String.format(
                                Locale.getDefault(),
                                "%.5f, %.5f",
                                lat,
                                lng
                        )
                                : address;

                requireActivity().runOnUiThread(() -> {
                    if (isAdded()) {
                        tvAddress.setText(
                                resolvedAddress
                        );
                    }
                });
            } catch (Exception error) {
                final String coordinates =
                        String.format(
                                Locale.getDefault(),
                                "%.5f, %.5f",
                                lat,
                                lng
                        );

                if (isAdded()) {
                    requireActivity()
                            .runOnUiThread(() -> {
                                if (isAdded()) {
                                    tvAddress.setText(
                                            coordinates
                                    );
                                }
                            });
                }
            }
        }).start();
    }

    private String formatCategory(String category) {
        if (category == null) {
            return "Community Report";
        }

        switch (category) {
            case "ROAD":
                return "Road Issue";

            case "LIGHTING":
                return "Lighting Issue";

            case "HAZARDS":
                return "Hazard Report";

            case "VANDALISM":
                return "Vandalism Report";

            case "SAFETY":
                return "Safety Concern";

            default:
                return "Community Report";
        }
    }

    private String getSeverityLabel(
            double severity
    ) {
        if (severity <= 3) {
            return "Low";
        }

        if (severity <= 6) {
            return "Moderate";
        }

        if (severity <= 9) {
            return "High";
        }

        return "Critical";
    }

    private int getSeverityColor(
            Double severity
    ) {
        if (severity == null) {
            return 0xFF9CA3AF;
        }

        if (severity <= 3) {
            return 0xFF4CAF50;
        }

        if (severity <= 6) {
            return 0xFFFFC107;
        }

        if (severity <= 9) {
            return 0xFFFF5722;
        }

        return 0xFFF44336;
    }

    private String formatTimeAgo(Date date) {
        long diff = Math.max(
                0,
                System.currentTimeMillis()
                        - date.getTime()
        );

        long minutes =
                diff / (60 * 1000);

        long hours =
                diff / (60 * 60 * 1000);

        long days =
                diff / (24 * 60 * 60 * 1000);

        if (minutes < 1) {
            return "just now";
        }

        if (minutes < 60) {
            return pluralize(
                    minutes,
                    "minute"
            ) + " ago";
        }

        if (hours < 24) {
            return pluralize(
                    hours,
                    "hour"
            ) + " ago";
        }

        if (days < 7) {
            return pluralize(
                    days,
                    "day"
            ) + " ago";
        }

        return new SimpleDateFormat(
                "MMM d, yyyy",
                Locale.getDefault()
        ).format(date);
    }

    private String pluralize(
            long value,
            String unit
    ) {
        return value
                + " "
                + unit
                + (value == 1 ? "" : "s");
    }
}