package com.pkg.civicfix.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;

import java.util.Date;

@IgnoreExtraProperties
public class User {

    @DocumentId
    private String uid;

    private boolean anonymousReporting;
    private boolean darkMode;
    private boolean isOfficial;
    private boolean isAdmin;
    private boolean isDeleted;

    private String email;
    private String displayName;

    private String pfpUrl;

    private Date timestamp;

    public User() {
    }

    public User(
            String uid,
            String displayName,
            String email
    ) {
        this.uid = uid;
        this.displayName = displayName;
        this.email = email;

        this.timestamp = new Date();

        this.isDeleted = false;
        this.isAdmin = false;
        this.isOfficial = false;
        this.darkMode = false;
        this.anonymousReporting = false;

        this.pfpUrl = null;
    }

    public boolean isAnonymousReporting() {
        return anonymousReporting;
    }

    public void setAnonymousReporting(boolean anonymousReporting) {
        this.anonymousReporting = anonymousReporting;
    }

    public void toggleAnonymousReporting() {
        anonymousReporting = !anonymousReporting;
    }

    public boolean isDarkMode() {
        return darkMode;
    }

    public void setDarkMode(boolean darkMode) {
        this.darkMode = darkMode;
    }

    public void toggleDarkMode() {
        darkMode = !darkMode;
    }

    public boolean isOfficial() {
        return isOfficial;
    }

    public void setOfficial(boolean official) {
        isOfficial = official;
    }

    public boolean isAdmin() {
        return isAdmin;
    }

    public void setAdmin(boolean isAdmin) {
        this.isAdmin = isAdmin;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public void delete(boolean isDeleted) {
        this.isDeleted = isDeleted;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPfpUrl() {
        return pfpUrl;
    }

    public void setPfpUrl(String pfpUrl) {
        this.pfpUrl = pfpUrl;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }
}