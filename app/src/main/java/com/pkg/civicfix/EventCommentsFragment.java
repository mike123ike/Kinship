package com.pkg.civicfix;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EventCommentsFragment extends Fragment {

    private static final String ARG_EVENT_ID =
            "event_id";

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String eventId;

    private LinearLayout commentsList;
    private TextView countView;
    private TextView emptyView;
    private EditText commentInput;
    private MaterialButton sendButton;

    public static EventCommentsFragment newInstance(
            String eventId
    ) {
        EventCommentsFragment fragment =
                new EventCommentsFragment();

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
                R.layout.fragment_event_comments,
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

        commentsList = view.findViewById(
                R.id.layout_comments_list
        );

        countView = view.findViewById(
                R.id.tv_comments_count
        );

        emptyView = view.findViewById(
                R.id.tv_comments_empty
        );

        commentInput = view.findViewById(
                R.id.et_new_comment
        );

        sendButton = view.findViewById(
                R.id.btn_send_comment
        );

        view.findViewById(R.id.btn_comments_back)
                .setOnClickListener(v ->
                        requireActivity()
                                .getSupportFragmentManager()
                                .popBackStack()
                );

        sendButton.setOnClickListener(
                v -> submitComment()
        );

        if (eventId != null) {
            loadComments();
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

    private void loadComments() {
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

                    renderComments(
                            snapshot.getDocuments()
                    );
                })
                .addOnFailureListener(error -> {
                    if (!isAdded()) {
                        return;
                    }

                    countView.setText(
                            "Comments unavailable"
                    );

                    emptyView.setText(
                            "Could not load comments."
                    );

                    emptyView.setVisibility(
                            View.VISIBLE
                    );
                });
    }

    private void renderComments(
            List<DocumentSnapshot> comments
    ) {
        commentsList.removeAllViews();

        if (comments.isEmpty()) {
            countView.setText("0 comments");
            commentsList.addView(emptyView);
            emptyView.setVisibility(View.VISIBLE);

            return;
        }

        countView.setText(
                comments.size() == 1
                        ? "1 comment"
                        : comments.size()
                          + " comments"
        );

        LayoutInflater inflater =
                LayoutInflater.from(
                        requireContext()
                );

        for (DocumentSnapshot comment : comments) {
            View item = inflater.inflate(
                    R.layout.item_event_comment,
                    commentsList,
                    false
            );

            bindComment(item, comment);
            commentsList.addView(item);
        }
    }

    private void bindComment(
            View item,
            DocumentSnapshot comment
    ) {
        TextView avatar = item.findViewById(
                R.id.tv_item_comment_avatar
        );

        TextView user = item.findViewById(
                R.id.tv_item_comment_user
        );

        TextView time = item.findViewById(
                R.id.tv_item_comment_time
        );

        TextView text = item.findViewById(
                R.id.tv_item_comment_text
        );

        TextView hearts = item.findViewById(
                R.id.tv_item_comment_hearts
        );

        String commentText =
                comment.getString("text");

        text.setText(
                commentText == null
                        || commentText.trim().isEmpty()
                        ? "Comment has no text"
                        : commentText
        );

        Timestamp createdAt =
                comment.getTimestamp("createdAt");

        time.setText(
                createdAt == null
                        ? ""
                        : formatTimeAgo(
                        createdAt.toDate()
                )
        );

        Long heartCount =
                comment.getLong("heartCount");

        if (heartCount != null
                && heartCount > 0) {

            hearts.setText(
                    "♥ " + heartCount
            );

            hearts.setVisibility(View.VISIBLE);
        } else {
            hearts.setVisibility(View.GONE);
        }

        Boolean anonymous =
                comment.getBoolean("isAnonymous");

        String userId =
                comment.getString("userId");

        if (Boolean.TRUE.equals(anonymous)
                || userId == null) {

            user.setText("Anonymous");
            avatar.setText("A");
            return;
        }

        db.collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener(userDoc -> {
                    if (!isAdded()) {
                        return;
                    }

                    String displayName =
                            userDoc.getString(
                                    "displayName"
                            );

                    if (displayName == null
                            || displayName
                            .trim()
                            .isEmpty()) {

                        user.setText("Anonymous");
                        avatar.setText("A");
                    } else {
                        String trimmedName =
                                displayName.trim();

                        user.setText(trimmedName);

                        avatar.setText(
                                String.valueOf(
                                                trimmedName
                                                        .charAt(0)
                                        )
                                        .toUpperCase(
                                                Locale.getDefault()
                                        )
                        );
                    }
                });
    }

    private void submitComment() {
        String uid =
                auth.getCurrentUser() != null
                        ? auth.getCurrentUser()
                          .getUid()
                        : null;

        if (uid == null) {
            Toast.makeText(
                    requireContext(),
                    "Sign in to comment",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        String text =
                commentInput.getText() == null
                        ? ""
                        : commentInput
                          .getText()
                          .toString()
                          .trim();

        if (text.isEmpty()) {
            commentInput.setError(
                    "Enter a comment"
            );
            return;
        }

        sendButton.setEnabled(false);

        Map<String, Object> data =
                new HashMap<>();

        data.put("userId", uid);
        data.put("text", text);
        data.put("isAnonymous", false);
        data.put("heartCount", 0);
        data.put("createdAt", Timestamp.now());

        db.collection("events")
                .document(eventId)
                .collection("comments")
                .add(data)
                .addOnSuccessListener(ref -> {
                    if (!isAdded()) {
                        return;
                    }

                    commentInput.setText("");
                    sendButton.setEnabled(true);
                    loadComments();
                })
                .addOnFailureListener(error -> {
                    if (!isAdded()) {
                        return;
                    }

                    sendButton.setEnabled(true);

                    Toast.makeText(
                            requireContext(),
                            "Could not post comment",
                            Toast.LENGTH_SHORT
                    ).show();
                });
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
            return minutes
                    + (
                    minutes == 1
                            ? " minute ago"
                            : " minutes ago"
            );
        }

        if (hours < 24) {
            return hours
                    + (
                    hours == 1
                            ? " hour ago"
                            : " hours ago"
            );
        }

        if (days < 7) {
            return days
                    + (
                    days == 1
                            ? " day ago"
                            : " days ago"
            );
        }

        return new SimpleDateFormat(
                "MMM d, yyyy",
                Locale.getDefault()
        ).format(date);
    }
}