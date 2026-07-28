package com.pkg.civicfix;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EventCommentsFragment extends Fragment {

    private static final String ARG_EVENT_ID = "event_id";
    private static final int PAGE_SIZE = 20;
    private static final int MAX_COMMENT_LENGTH = 1000;

    private enum CommentSort {
        TOP,
        NEWEST
    }

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String eventId;

    private LinearLayout commentsList;
    private TextView countView;
    private TextView emptyView;
    private TextView characterCountView;
    private TextInputEditText commentInput;
    private MaterialButton sendButton;
    private MaterialButton loadMoreButton;
    private MaterialButtonToggleGroup sortGroup;
    private ProgressBar loadingProgress;

    private CommentSort currentSort = CommentSort.TOP;
    private DocumentSnapshot lastVisibleComment;
    private boolean isLoading;
    private boolean hasMoreComments = true;
    private long currentCommentCount;

    public static EventCommentsFragment newInstance(String eventId) {
        EventCommentsFragment fragment = new EventCommentsFragment();
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

        commentsList = view.findViewById(R.id.layout_comments_list);
        countView = view.findViewById(R.id.tv_comments_count);
        emptyView = view.findViewById(R.id.tv_comments_empty);
        characterCountView = view.findViewById(
                R.id.tv_comment_character_count
        );
        commentInput = view.findViewById(R.id.et_new_comment);
        sendButton = view.findViewById(R.id.btn_send_comment);
        loadMoreButton = view.findViewById(R.id.btn_load_more_comments);
        sortGroup = view.findViewById(R.id.group_comment_sort);
        loadingProgress = view.findViewById(R.id.progress_comments);

        view.findViewById(R.id.btn_comments_back)
                .setOnClickListener(v -> requireActivity()
                        .getSupportFragmentManager()
                        .popBackStack());

        sendButton.setEnabled(false);
        sendButton.setOnClickListener(v -> submitComment());
        loadMoreButton.setOnClickListener(v -> loadCommentsPage());

        commentInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence text,
                    int start,
                    int count,
                    int after
            ) {
                // no action needed
            }

            @Override
            public void onTextChanged(
                    CharSequence text,
                    int start,
                    int before,
                    int count
            ) {
                int length = text == null ? 0 : text.length();
                characterCountView.setText(
                        length + "/" + MAX_COMMENT_LENGTH
                );
            }

            @Override
            public void afterTextChanged(Editable editable) {
                // no action needed
            }
        });

        sortGroup.check(R.id.btn_sort_top);
        sortGroup.addOnButtonCheckedListener(
                (group, checkedId, isChecked) -> {
                    if (!isChecked) {
                        return;
                    }

                    CommentSort selectedSort = checkedId == R.id.btn_sort_newest
                            ? CommentSort.NEWEST
                            : CommentSort.TOP;

                    if (selectedSort != currentSort) {
                        currentSort = selectedSort;
                        resetAndLoadComments();
                    }
                }
        );

        if (eventId != null) {
            loadCommentCount();
            resetAndLoadComments();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity())
                    .setMainChromeVisible(false);
        }
    }

    @Override
    public void onDestroyView() {
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity())
                    .setMainChromeVisible(true);
        }

        super.onDestroyView();
    }

    private CollectionReference commentsReference() {
        return db.collection("events")
                .document(eventId)
                .collection("comments");
    }

    private void resetAndLoadComments() {
        lastVisibleComment = null;
        hasMoreComments = true;
        isLoading = false;

        commentsList.removeAllViews();
        emptyView.setText("No comments yet. Start the conversation below.");
        emptyView.setVisibility(View.GONE);
        loadMoreButton.setVisibility(View.GONE);
        loadingProgress.setVisibility(View.GONE);

        loadCommentsPage();
    }

    private void loadCommentsPage() {
        if (eventId == null || isLoading || !hasMoreComments) {
            return;
        }

        isLoading = true;
        loadingProgress.setVisibility(View.VISIBLE);
        loadMoreButton.setEnabled(false);

        buildCommentsQuery()
                .get()
                .addOnSuccessListener(this::handleCommentsPage)
                .addOnFailureListener(this::handleCommentsFailure);
    }

    private Query buildCommentsQuery() {
        Query query;

        if (currentSort == CommentSort.TOP) {
            query = commentsReference()
                    .orderBy(
                            "heartCount",
                            Query.Direction.DESCENDING
                    )
                    .orderBy(
                            "createdAt",
                            Query.Direction.DESCENDING
                    );
        } else {
            query = commentsReference()
                    .orderBy(
                            "createdAt",
                            Query.Direction.DESCENDING
                    );
        }

        if (lastVisibleComment != null) {
            query = query.startAfter(lastVisibleComment);
        }

        return query.limit(PAGE_SIZE);
    }

    private void handleCommentsPage(QuerySnapshot snapshot) {
        if (!isAdded()) {
            return;
        }

        List<DocumentSnapshot> documents = snapshot.getDocuments();

        if (!documents.isEmpty()) {
            lastVisibleComment = documents.get(documents.size() - 1);
        }

        hasMoreComments = documents.size() == PAGE_SIZE;
        appendComments(documents);

        isLoading = false;
        loadingProgress.setVisibility(View.GONE);
        loadMoreButton.setEnabled(true);
        loadMoreButton.setVisibility(
                hasMoreComments ? View.VISIBLE : View.GONE
        );

        emptyView.setVisibility(
                commentsList.getChildCount() == 0
                        ? View.VISIBLE
                        : View.GONE
        );
    }

    private void handleCommentsFailure(Exception error) {
        if (!isAdded()) {
            return;
        }

        isLoading = false;
        loadingProgress.setVisibility(View.GONE);
        loadMoreButton.setEnabled(true);

        if (currentSort == CommentSort.TOP
                && error instanceof FirebaseFirestoreException
                && ((FirebaseFirestoreException) error).getCode()
                == FirebaseFirestoreException.Code.FAILED_PRECONDITION) {

            Toast.makeText(
                    requireContext(),
                    "Top sorting needs the Firestore index. Showing newest comments.",
                    Toast.LENGTH_LONG
            ).show();

            sortGroup.check(R.id.btn_sort_newest);
            return;
        }

        if (commentsList.getChildCount() == 0) {
            emptyView.setText("Could not load comments.");
            emptyView.setVisibility(View.VISIBLE);
        }

        Toast.makeText(
                requireContext(),
                "Could not load comments",
                Toast.LENGTH_SHORT
        ).show();
    }

    private void appendComments(List<DocumentSnapshot> comments) {
        LayoutInflater inflater = LayoutInflater.from(requireContext());

        for (DocumentSnapshot comment : comments) {
            String status = comment.getString("status");

            if ("deleted".equalsIgnoreCase(status)
                    || "hidden".equalsIgnoreCase(status)) {
                continue;
            }

            View item = inflater.inflate(
                    R.layout.item_event_comment,
                    commentsList,
                    false
            );

            bindComment(item, comment);
            commentsList.addView(item);
        }
    }

    private void bindComment(View item, DocumentSnapshot comment) {
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
        MaterialButton heartButton = item.findViewById(
                R.id.btn_item_comment_heart
        );

        String commentText = comment.getString("text");
        text.setText(
                commentText == null || commentText.trim().isEmpty()
                        ? "Comment has no text"
                        : commentText
        );

        com.google.firebase.Timestamp createdAt =
                comment.getTimestamp("createdAt");

        time.setText(
                createdAt == null
                        ? ""
                        : formatTimeAgo(createdAt.toDate())
        );

        bindCommentAuthor(comment, avatar, user);
        bindHeartButton(comment, heartButton);
    }

    private void bindCommentAuthor(
            DocumentSnapshot comment,
            TextView avatar,
            TextView user
    ) {
        Boolean isAnonymous = comment.getBoolean("isAnonymous");
        String storedDisplayName = comment.getString("displayName");

        if (Boolean.TRUE.equals(isAnonymous)) {
            showAuthor(avatar, user, "Anonymous");
            return;
        }

        if (storedDisplayName != null
                && !storedDisplayName.trim().isEmpty()) {
            showAuthor(avatar, user, storedDisplayName.trim());
            return;
        }

        String userId = comment.getString("userId");

        if (userId == null || userId.trim().isEmpty()) {
            showAuthor(avatar, user, "Community member");
            return;
        }

        db.collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener(userDoc -> {
                    if (!isAdded()) {
                        return;
                    }

                    String displayName = userDoc.getString("displayName");
                    showAuthor(
                            avatar,
                            user,
                            displayName == null
                                    || displayName.trim().isEmpty()
                                    ? "Community member"
                                    : displayName.trim()
                    );
                })
                .addOnFailureListener(error -> {
                    if (isAdded()) {
                        showAuthor(
                                avatar,
                                user,
                                "Community member"
                        );
                    }
                });
    }

    private void showAuthor(
            TextView avatar,
            TextView user,
            String displayName
    ) {
        String safeName = displayName == null
                || displayName.trim().isEmpty()
                ? "Community member"
                : displayName.trim();

        user.setText(safeName);
        avatar.setText(
                String.valueOf(safeName.charAt(0))
                        .toUpperCase(Locale.getDefault())
        );
    }

    private void bindHeartButton(
            DocumentSnapshot comment,
            MaterialButton heartButton
    ) {
        String uid = auth.getUid();

        if (uid == null) {
            heartButton.setEnabled(false);
            return;
        }

        long heartCount = getHeartCount(comment);
        DocumentReference commentRef = comment.getReference();
        DocumentReference heartRef = commentRef
                .collection("hearts")
                .document(uid);

        heartButton.setEnabled(false);
        updateHeartButton(heartButton, heartCount, false);

        heartRef.get()
                .addOnSuccessListener(heartSnapshot -> {
                    if (!isAdded()) {
                        return;
                    }

                    boolean isHearted = heartSnapshot.exists();
                    updateHeartButton(
                            heartButton,
                            heartCount,
                            isHearted
                    );

                    heartButton.setEnabled(true);
                    heartButton.setOnClickListener(v ->
                            toggleHeart(
                                    commentRef,
                                    heartRef,
                                    heartButton
                            )
                    );
                })
                .addOnFailureListener(error -> {
                    if (isAdded()) {
                        heartButton.setEnabled(false);
                    }
                });
    }

    private void toggleHeart(
            DocumentReference commentRef,
            DocumentReference heartRef,
            MaterialButton heartButton
    ) {
        String uid = auth.getUid();

        if (uid == null || !heartButton.isEnabled()) {
            return;
        }

        heartButton.setEnabled(false);

        db.runTransaction(transaction -> {
            DocumentSnapshot commentSnapshot =
                    transaction.get(commentRef);
            DocumentSnapshot heartSnapshot =
                    transaction.get(heartRef);

            if (!commentSnapshot.exists()) {
                throw new IllegalStateException(
                        "The comment no longer exists."
                );
            }

            long oldCount = getHeartCount(commentSnapshot);
            boolean wasHearted = heartSnapshot.exists();
            long newCount;
            boolean isHearted;

            if (wasHearted) {
                transaction.delete(heartRef);
                newCount = Math.max(0, oldCount - 1);
                isHearted = false;
            } else {
                Map<String, Object> heartData = new HashMap<>();
                heartData.put("userId", uid);
                heartData.put(
                        "createdAt",
                        FieldValue.serverTimestamp()
                );

                transaction.set(heartRef, heartData);
                newCount = oldCount + 1;
                isHearted = true;
            }

            transaction.update(
                    commentRef,
                    "heartCount",
                    newCount
            );

            return new HeartResult(isHearted, newCount);
        }).addOnSuccessListener(result -> {
            if (!isAdded()) {
                return;
            }

            updateHeartButton(
                    heartButton,
                    result.heartCount,
                    result.isHearted
            );
            heartButton.setEnabled(true);
        }).addOnFailureListener(error -> {
            if (!isAdded()) {
                return;
            }

            heartButton.setEnabled(true);
            Toast.makeText(
                    requireContext(),
                    "Could not update heart",
                    Toast.LENGTH_SHORT
            ).show();
        });
    }

    private void updateHeartButton(
            MaterialButton button,
            long heartCount,
            boolean isHearted
    ) {
        button.setText(
                (isHearted ? "♥ " : "♡ ") + heartCount
        );

        int color = ContextCompat.getColor(
                requireContext(),
                isHearted ? R.color.heart : R.color.textSecondary
        );

        button.setTextColor(ColorStateList.valueOf(color));
        button.setContentDescription(
                isHearted
                        ? "Remove heart. " + heartCount + " hearts"
                        : "Add heart. " + heartCount + " hearts"
        );
    }

    private long getHeartCount(DocumentSnapshot comment) {
        Long heartCount = comment.getLong("heartCount");
        return heartCount == null ? 0 : heartCount;
    }

    private void submitComment() {
        if (eventId == null) {
            return;
        }

        String uid = auth.getUid();

        // authentication before MainActivity is shown.
        if (uid == null) {
            return;
        }

        String text = commentInput.getText() == null
                ? ""
                : commentInput.getText().toString().trim();

        if (text.isEmpty()) {
            commentInput.setError("Enter a comment");
            return;
        }

        if (text.length() > MAX_COMMENT_LENGTH) {
            commentInput.setError(
                    "Comments are limited to "
                            + MAX_COMMENT_LENGTH
                            + " characters"
            );
            return;
        }

        sendButton.setEnabled(false);

        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(userDoc -> {
                    if (!isAdded()) {
                        return;
                    }

                    Boolean storedAnonymous =
                            userDoc.getBoolean("anonymousReporting");

                    boolean isAnonymous =
                            Boolean.TRUE.equals(storedAnonymous);

                    String displayName = isAnonymous
                            ? "Anonymous"
                            : userDoc.getString("displayName");

                    if (!isAnonymous
                            && (displayName == null
                            || displayName.trim().isEmpty())
                            && auth.getCurrentUser() != null) {
                        displayName = auth.getCurrentUser()
                                .getDisplayName();
                    }

                    if (displayName == null
                            || displayName.trim().isEmpty()) {
                        displayName = isAnonymous
                                ? "Anonymous"
                                : "Community member";
                    }

                    createComment(
                            uid,
                            displayName.trim(),
                            isAnonymous,
                            text
                    );
                })
                .addOnFailureListener(error -> {
                    if (!isAdded()) {
                        return;
                    }

                    sendButton.setEnabled(true);
                    Toast.makeText(
                            requireContext(),
                            "Could not load your profile",
                            Toast.LENGTH_SHORT
                    ).show();
                });
    }

    private void createComment(
            String uid,
            String displayName,
            boolean isAnonymous,
            String text
    ) {
        DocumentReference eventRef = db.collection("events")
                .document(eventId);
        DocumentReference commentRef = eventRef
                .collection("comments")
                .document();

        Map<String, Object> data = new HashMap<>();
        data.put("userId", uid);
        data.put("displayName", displayName);
        data.put("isAnonymous", isAnonymous);
        data.put("text", text);
        data.put("heartCount", 0L);
        data.put("status", "active");
        data.put("edited", false);
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("updatedAt", FieldValue.serverTimestamp());

        WriteBatch batch = db.batch();
        batch.set(commentRef, data);
        batch.update(
                eventRef,
                "commentCount",
                FieldValue.increment(1)
        );

        batch.commit()
                .addOnSuccessListener(unused -> {
                    if (!isAdded()) {
                        return;
                    }

                    commentInput.setText("");
                    sendButton.setEnabled(true);
                    currentCommentCount++;
                    updateCommentCountText(currentCommentCount);
                    resetAndLoadComments();
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

    private void loadCommentCount() {
        DocumentReference eventRef = db.collection("events")
                .document(eventId);

        eventRef.get()
                .addOnSuccessListener(event -> {
                    if (!isAdded()) {
                        return;
                    }

                    Long storedCount = event.getLong("commentCount");

                    if (storedCount != null) {
                        currentCommentCount = storedCount;
                        updateCommentCountText(storedCount);
                        sendButton.setEnabled(true);
                    } else {
                        loadCommentCountFallback();
                    }
                })
                .addOnFailureListener(error ->
                        loadCommentCountFallback()
                );
    }

    private void loadCommentCountFallback() {
        commentsReference()
                .count()
                .get(AggregateSource.SERVER)
                .addOnSuccessListener(result -> {
                    if (!isAdded()) {
                        return;
                    }

                    currentCommentCount = result.getCount();
                    updateCommentCountText(currentCommentCount);

                    db.collection("events")
                            .document(eventId)
                            .update("commentCount", currentCommentCount);

                    sendButton.setEnabled(true);
                })
                .addOnFailureListener(error -> {
                    if (isAdded()) {
                        countView.setText("Comments");
                        sendButton.setEnabled(true);
                    }
                });
    }

    private void updateCommentCountText(long count) {
        countView.setText(
                count == 1
                        ? "1 comment"
                        : count + " comments"
        );
    }

    private String formatTimeAgo(Date date) {
        long diff = Math.max(
                0,
                System.currentTimeMillis() - date.getTime()
        );

        long minutes = diff / (60 * 1000);
        long hours = diff / (60 * 60 * 1000);
        long days = diff / (24 * 60 * 60 * 1000);

        if (minutes < 1) {
            return "just now";
        }

        if (minutes < 60) {
            return minutes
                    + (minutes == 1
                    ? " minute ago"
                    : " minutes ago");
        }

        if (hours < 24) {
            return hours
                    + (hours == 1
                    ? " hour ago"
                    : " hours ago");
        }

        if (days < 7) {
            return days
                    + (days == 1
                    ? " day ago"
                    : " days ago");
        }

        return new SimpleDateFormat(
                "MMM d, yyyy",
                Locale.getDefault()
        ).format(date);
    }

    private static class HeartResult {
        private final boolean isHearted;
        private final long heartCount;

        private HeartResult(
                boolean isHearted,
                long heartCount
        ) {
            this.isHearted = isHearted;
            this.heartCount = heartCount;
        }
    }
}