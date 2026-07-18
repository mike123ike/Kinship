package com.pkg.civicfix.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

public class Report {

    public enum Status {
        PENDING, ACTIVE, IN_PROGRESS, FIXED
    }

    public enum Category {
        ROAD, LIGHTING, HAZARDS, VANDALISM, SAFETY, OTHER
    }

    @DocumentId
    private String id;
    private String userId;
    private boolean isAnonymous;
    private String eventId;
    private Category category;
    private int severity;
    private String description;
    private String photoURL;
    private double latitude;
    private double longitude;
    private String geohash;
    private Status status;
    private Timestamp createdAt;

    public Report() {}

    public Report(String userId, boolean isAnonymous, Category category,
                  int severity, String description, String photoURL,
                  double latitude, double longitude, String geohash) {
        this.userId = userId;
        this.isAnonymous = isAnonymous;
        this.eventId = null;        // set after clustering
        this.category = category;
        this.severity = severity;
        this.description = description;
        this.photoURL = photoURL;
        this.latitude = latitude;
        this.longitude = longitude;
        this.geohash = geohash;
        this.status = Status.PENDING;
        this.createdAt = Timestamp.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public boolean isAnonymous() { return isAnonymous; }
    public void setAnonymous(boolean anonymous) { isAnonymous = anonymous; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    public int getSeverity() { return severity; }
    public void setSeverity(int severity) { this.severity = severity; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPhotoURL() { return photoURL; }
    public void setPhotoURL(String photoURL) { this.photoURL = photoURL; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public String getGeohash() { return geohash; }
    public void setGeohash(String geohash) { this.geohash = geohash; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}