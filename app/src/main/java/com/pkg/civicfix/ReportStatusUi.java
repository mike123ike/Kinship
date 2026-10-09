package com.pkg.civicfix;

import java.util.Locale;

public final class ReportStatusUi {

    private ReportStatusUi() {
    }

    private static String normalize(
            String status
    ) {

        if (status == null) {
            return "PENDING";
        }

        return status
                .trim()
                .toUpperCase(Locale.US);
    }

    public static int getBackgroundColorRes(
            String status
    ) {

        switch (normalize(status)) {

            case "ACTIVE":
                return R.color.reportActiveBg;

            case "IN_PROGRESS":
                return R.color.reportInProgressBg;

            case "FIXED":
                return R.color.fixedStatusBg;

            case "PENDING":
            default:
                return R.color.reportPendingBg;
        }
    }

    public static int getTextColorRes(
            String status
    ) {

        switch (normalize(status)) {

            case "ACTIVE":
                return R.color.reportActiveText;

            case "IN_PROGRESS":
                return R.color.reportInProgressText;

            case "FIXED":
                return R.color.fixedStatusText;

            case "PENDING":
            default:
                return R.color.reportPendingText;
        }
    }

    public static int getStrokeColorRes(
            String status
    ) {

        switch (normalize(status)) {

            case "ACTIVE":
                return R.color.reportActiveStroke;

            case "IN_PROGRESS":
                return R.color.reportInProgressStroke;

            case "FIXED":
                return R.color.fixedStatusStroke;

            case "PENDING":
            default:
                return R.color.reportPendingStroke;
        }
    }

    public static boolean canViewOnMap(
            String status
    ) {

        String normalized =
                normalize(status);

        return normalized.equals("ACTIVE")
                || normalized.equals("IN_PROGRESS");
    }

    public static String getExplanationTitle(
            String status
    ) {

        switch (normalize(status)) {

            case "ACTIVE":
                return "Visible on the map";

            case "IN_PROGRESS":
                return "Work in progress";

            case "FIXED":
                return "Issue resolved";

            case "PENDING":
            default:
                return "Awaiting report threshold";
        }
    }

    public static String getExplanationMessage(
            String status
    ) {

        switch (normalize(status)) {

            case "ACTIVE":
                return "This issue has received enough reports and is currently active.";

            case "IN_PROGRESS":
                return "This issue is currently being addressed.";

            case "FIXED":
                return "This issue has been marked as fixed.";

            case "PENDING":
            default:
                return "This issue needs more independent reports before it appears publicly on the map.";
        }
    }
}
