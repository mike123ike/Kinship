package com.pkg.civicfix.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;

import java.util.HashMap;
import java.util.Map;

public class Issue {
    public static enum Status {
        REPORTED, IN_PROGRESS, RESOLVED;
    }
    public static enum Category {
        ROAD, LIGHTING, HAZARDS, VANDALISM, SAFTEY, OTHER;
    }
    @DocumentId
    private String id;
    private String title;
    private String description;
    private String reportedBy;
    private Status status;
    private Category category;
    private String photoURL;
    private double latitude;
    private double longitude;
    private long confirmationCount;
    private long resolvedCount;
    private Map<String, Boolean> votes;

    public Issue() {
        votes = new HashMap<>();
    }

    public Issue(String t, String d, String r, Status s, Category c, String p, double lat, double lon) {
        this();
        title = t;
        description = d;
        reportedBy = r;
        status = s;
        category = c;
        photoURL = p;
        latitude = lat;
        longitude = lon;
        confirmationCount = 0;
        resolvedCount = 0;
    }

    public String getId() {return id;}
    public void setId(String newId) {id = newId;}

    public String getTitle() {return title;}
    public void setTitle(String newTitle) {title = newTitle;}

    public String getDescription() {return description;}
    public void setDescription(String description) {this.description = description;}

    public String getReportedBy() {return reportedBy;}
    public void setReportedBy(String reportedBy) {this.reportedBy = reportedBy;}

    public Status getStatus() {return status;}
    public void setStatus(Status status) {this.status = status;}

    public Category getCategory() {return category;}
    public void setCategory(Category category) {this.category = category;}

    public String getPhotoURL() {return photoURL;}
    public void setPhotoURL(String photoURL) {this.photoURL = photoURL;}

    public double getLatitude() {return latitude;}
    public void setLatitude(double latitude) {this.latitude = latitude;}

    public double getLongitude() {return longitude;}
    public void setLongitude(double longitude) {this.longitude = longitude;}

    public long getConfirmationCount() {return confirmationCount;}
    public void setConfirmationCount(long confirmationCount) {this.confirmationCount = confirmationCount;}

    public long getResolvedCount() {return resolvedCount;}
    public void setResolvedCount(long resolvedCount) {this.resolvedCount = resolvedCount;}

    public Map<String, Boolean> getVotes() {return votes;}
    public void setVotes(Map<String, Boolean> votes) {this.votes = votes;}

    @Exclude
    public Boolean getUserVote(String uid) {
        return votes.get(uid);
    }
}
