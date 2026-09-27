package com.pkg.civicfix;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.pkg.civicfix.model.Report;
import com.pkg.civicfix.model.UserReportItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserReportsRepository {

    public interface Callback {

        void onSuccess(
                List<UserReportItem> reports
        );

        void onError(
                Exception error
        );
    }

    private final FirebaseFirestore db;

    public UserReportsRepository(
            FirebaseFirestore db
    ) {
        this.db = db;
    }

    public void loadReportsForUser(
            String uid,
            Callback callback
    ) {

        db.collection("reports")
                .whereEqualTo(
                        "userId",
                        uid
                )
                .get()
                .addOnSuccessListener(snapshot -> {

                    List<Report> reports =
                            snapshot.toObjects(
                                    Report.class
                            );

                    // newest first
                    reports.sort(
                            (a, b) ->
                                    compareTimestampsDescending(
                                            a.getCreatedAt(),
                                            b.getCreatedAt()
                                    )
                    );

                    // fetch each event once
                    Map<String, Task<DocumentSnapshot>>
                            eventTasks =
                            new HashMap<>();

                    for (Report report : reports) {

                        String eventId =
                                report.getEventId();

                        if (
                                eventId == null
                                        || eventId.isEmpty()
                                        || eventTasks
                                        .containsKey(eventId)
                        ) {
                            continue;
                        }

                        eventTasks.put(
                                eventId,
                                db.collection("events")
                                        .document(eventId)
                                        .get()
                        );
                    }

                    // no linked events
                    if (eventTasks.isEmpty()) {

                        callback.onSuccess(
                                buildResult(
                                        reports,
                                        new HashMap<>()
                                )
                        );

                        return;
                    }

                    List<Task<DocumentSnapshot>>
                            tasks =
                            new ArrayList<>(
                                    eventTasks.values()
                            );

                    Tasks.whenAllComplete(tasks)
                            .addOnCompleteListener(
                                    ignored -> {

                                        Map<String, String>
                                                eventStatuses =
                                                new HashMap<>();

                                        for (
                                                Map.Entry<
                                                        String,
                                                        Task<DocumentSnapshot>
                                                        > entry
                                                : eventTasks.entrySet()
                                        ) {

                                            Task<DocumentSnapshot>
                                                    task =
                                                    entry.getValue();

                                            if (
                                                    !task.isSuccessful()
                                                            || task.getResult()
                                                            == null
                                                            || !task.getResult()
                                                            .exists()
                                            ) {
                                                continue;
                                            }

                                            String status =
                                                    task.getResult()
                                                            .getString(
                                                                    "status"
                                                            );

                                            if (status != null) {

                                                eventStatuses.put(
                                                        entry.getKey(),
                                                        status
                                                );
                                            }
                                        }

                                        callback.onSuccess(
                                                buildResult(
                                                        reports,
                                                        eventStatuses
                                                )
                                        );
                                    }
                            );
                })
                .addOnFailureListener(
                        callback::onError
                );
    }

    private List<UserReportItem> buildResult(
            List<Report> reports,
            Map<String, String> eventStatuses
    ) {

        List<UserReportItem> result =
                new ArrayList<>();

        for (Report report : reports) {

            String status = null;

            if (
                    report.getEventId() != null
            ) {

                status =
                        eventStatuses.get(
                                report.getEventId()
                        );
            }

            // fallback if event cannot be loaded
            if (status == null) {

                if (report.getStatus() != null) {

                    status =
                            report.getStatus().name();

                } else {

                    status =
                            "PENDING";
                }
            }

            result.add(
                    new UserReportItem(
                            report,
                            status
                    )
            );
        }

        return result;
    }

    private int compareTimestampsDescending(
            Timestamp a,
            Timestamp b
    ) {

        if (a == null && b == null) {
            return 0;
        }

        if (a == null) {
            return 1;
        }

        if (b == null) {
            return -1;
        }

        return b.toDate()
                .compareTo(
                        a.toDate()
                );
    }
}