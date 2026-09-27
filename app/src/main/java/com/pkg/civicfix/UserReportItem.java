package com.pkg.civicfix.model;

public class UserReportItem {

    private final Report report;
    private final String eventStatus;

    public UserReportItem(
            Report report,
            String eventStatus
    ) {
        this.report = report;
        this.eventStatus = eventStatus;
    }

    public Report getReport() {
        return report;
    }

    public String getEventStatus() {
        return eventStatus;
    }

    public boolean isFixed() {
        return "FIXED".equalsIgnoreCase(
                eventStatus
        );
    }
}