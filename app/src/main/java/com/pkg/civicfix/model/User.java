package com.pkg.civicfix.model;

import com.google.firebase.firestore.DocumentId;

public class User {
    @DocumentId
    private String uid;
    private boolean anonymousReporting;
    private boolean darkMode;
    private boolean isOfficial;
    private String email;
    private String displayName;

    public User() {}
    public User(String uid, String displayName, String email) {
        this.uid = uid;
        this.displayName = displayName;
        this.email = email;
    }

    public boolean isAnonymousReporting() {return anonymousReporting;}
    public void setAnonymousReporting(boolean anonymousReporting) {this.anonymousReporting = anonymousReporting;}
    public void toggleAnonymousReporting() {anonymousReporting = !anonymousReporting;}

    public boolean isDarkMode() {return darkMode;}
    public void setDarkMode(boolean darkMode) {this.darkMode = darkMode;}
    public void toggleDarkMode() {darkMode = !darkMode;}

    public boolean isOfficial() {return isOfficial;}
    public void setOfficial(boolean official) {isOfficial = official;}

    public String getDisplayName() {return displayName;}
    public void setDisplayName(String displayName) {this.displayName = displayName;}

    public String getEmail() {return email;}
    public void setEmail(String email) {this.email = email;}

    public String getUid() {return uid;}
    public void setUid(String uid) {this.uid = uid;}
}
