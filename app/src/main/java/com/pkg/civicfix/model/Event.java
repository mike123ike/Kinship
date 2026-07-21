package com.pkg.civicfix.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;
import java.util.ArrayList;
import java.util.List;

public class Event {

    public enum Status {
        PENDING, ACTIVE, IN_PROGRESS, FIXED
    }

    @DocumentId
    private String id;
    private String category;
    private Status status;

    // Severity
    private double averageSeverity;
    private double totalSeveritySum;

    // Reporting
    private int uniqueUserCount;
    private int reportCount;
    private List<String> reporterIds;

    // Voting
    private int voteScore;

    // Location
    private String locationAccuracy;  // "pinpoint" or "general"
    private double radiusMeters;
    private double latitude;
    private double longitude;
    private String geohash;

    // Cached for map popup
    private List<String> photoGallery;
    private String topCommentText;
    private String topCommentUserId;
    private int commentCount;

    // Resolution — admin only
    private String resolvedBy;
    private Timestamp resolvedAt;

    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Event() {}

    public Event(String category, double severity, String uid,
                 String imageUrl, double latitude, double longitude,
                 String geohash) {
        this.category = category;
        this.status = Status.PENDING;
        this.averageSeverity = severity;
        this.totalSeveritySum = severity;
        this.uniqueUserCount = 1;
        this.reportCount = 1;
        this.reporterIds = new ArrayList<>();
        this.reporterIds.add(uid);
        this.voteScore = 0;
        this.locationAccuracy = "pinpoint";
        this.radiusMeters = 50;
        this.latitude = latitude;
        this.longitude = longitude;
        this.geohash = geohash;
        this.photoGallery = new ArrayList<>();
        if (imageUrl != null && !imageUrl.isEmpty()) {
            this.photoGallery.add(imageUrl);
        }
        this.topCommentText = null;
        this.topCommentUserId = null;
        this.commentCount = 0;
        this.resolvedBy = null;
        this.resolvedAt = null;
        this.createdAt = Timestamp.now();
        this.updatedAt = Timestamp.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public double getAverageSeverity() { return averageSeverity; }
    public void setAverageSeverity(double averageSeverity) { this.averageSeverity = averageSeverity; }

    public double getTotalSeveritySum() { return totalSeveritySum; }
    public void setTotalSeveritySum(double totalSeveritySum) { this.totalSeveritySum = totalSeveritySum; }

    public int getUniqueUserCount() { return uniqueUserCount; }
    public void setUniqueUserCount(int uniqueUserCount) { this.uniqueUserCount = uniqueUserCount; }

    public int getReportCount() { return reportCount; }
    public void setReportCount(int reportCount) { this.reportCount = reportCount; }

    public List<String> getReporterIds() { return reporterIds; }
    public void setReporterIds(List<String> reporterIds) { this.reporterIds = reporterIds; }

    public int getVoteScore() { return voteScore; }
    public void setVoteScore(int voteScore) { this.voteScore = voteScore; }

    public String getLocationAccuracy() { return locationAccuracy; }
    public void setLocationAccuracy(String locationAccuracy) { this.locationAccuracy = locationAccuracy; }

    public double getRadiusMeters() { return radiusMeters; }
    public void setRadiusMeters(double radiusMeters) { this.radiusMeters = radiusMeters; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getGeohash() { return geohash; }
    public void setGeohash(String geohash) { this.geohash = geohash; }

    public List<String> getPhotoGallery() { return photoGallery; }
    public void setPhotoGallery(List<String> photoGallery) { this.photoGallery = photoGallery; }

    public String getTopCommentText() { return topCommentText; }
    public void setTopCommentText(String topCommentText) { this.topCommentText = topCommentText; }

    public String getTopCommentUserId() { return topCommentUserId; }
    public void setTopCommentUserId(String topCommentUserId) { this.topCommentUserId = topCommentUserId; }

    public int getCommentCount() { return commentCount; }
    public void setCommentCount(int commentCount) { this.commentCount = commentCount; }

    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }

    public Timestamp getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Timestamp resolvedAt) { this.resolvedAt = resolvedAt; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}