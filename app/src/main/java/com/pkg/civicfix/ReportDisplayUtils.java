package com.pkg.civicfix;

import com.google.firebase.Timestamp;
import com.pkg.civicfix.model.Report;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class ReportDisplayUtils {

    private ReportDisplayUtils() {
    }

    public static String categoryLabel(
            Report.Category category
    ) {

        if (category == null) {
            return "Report";
        }

        switch (category) {

            case ROAD:
                return "Road";

            case LIGHTING:
                return "Lighting";

            case HAZARDS:
                return "Hazards";

            case VANDALISM:
                return "Vandalism";

            case SAFETY:
                return "Safety";

            default:
                return "Other";
        }
    }

    public static String statusLabel(
            String status
    ) {

        if (status == null) {
            return "Pending";
        }

        switch (
                status.toUpperCase(
                        Locale.US
                )
        ) {

            case "ACTIVE":
                return "Active";

            case "IN_PROGRESS":
                return "In Progress";

            case "FIXED":
                return "Fixed";

            case "PENDING":
            default:
                return "Pending";
        }
    }

    public static String description(
            String description
    ) {

        if (
                description == null
                        || description.trim()
                        .isEmpty()
        ) {

            return "No description provided.";
        }

        return description.trim();
    }

    public static String formatRelativeTime(
            Timestamp timestamp
    ) {

        if (timestamp == null) {
            return "Unknown date";
        }

        return formatRelativeTime(
                timestamp.toDate()
                        .getTime()
        );
    }

    public static String formatRelativeTime(
            long timestampMillis
    ) {

        long now =
                System.currentTimeMillis();

        long difference =
                now - timestampMillis;

        if (difference < 0) {

            return formatDate(
                    new Date(timestampMillis)
            );
        }

        long seconds =
                difference / 1000;

        if (seconds < 60) {
            return "Just now";
        }

        long minutes =
                seconds / 60;

        if (minutes < 60) {

            return minutes
                    + (
                    minutes == 1
                            ? " min ago"
                            : " mins ago"
            );
        }

        long hours =
                minutes / 60;

        if (hours < 24) {

            return hours
                    + (
                    hours == 1
                            ? " hour ago"
                            : " hours ago"
            );
        }

        long days =
                hours / 24;

        // keep relative dates for the last week
        if (days < 7) {

            return days
                    + (
                    days == 1
                            ? " day ago"
                            : " days ago"
            );
        }

        return formatDate(
                new Date(timestampMillis)
        );
    }

    private static String formatDate(
            Date date
    ) {

        Calendar now =
                Calendar.getInstance();

        Calendar reportDate =
                Calendar.getInstance();

        reportDate.setTime(date);

        String pattern =
                now.get(Calendar.YEAR)
                        == reportDate.get(
                        Calendar.YEAR
                )
                        ? "MMM d"
                        : "MMM d, yyyy";

        SimpleDateFormat formatter =
                new SimpleDateFormat(
                        pattern,
                        Locale.getDefault()
                );

        return formatter.format(date);
    }
}