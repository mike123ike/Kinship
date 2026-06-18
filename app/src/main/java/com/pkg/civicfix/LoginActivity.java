package com.pkg.civicfix;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.credentials.CredentialManager;

import androidx.credentials.exceptions.GetCredentialException;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.View;

import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;

import com.google.android.gms.tasks.Task;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.pkg.civicfix.base.CivicFixActivity;
import com.pkg.civicfix.model.User;

public class LoginActivity extends CivicFixActivity {
    private CredentialManager manager;
    private GetCredentialRequest request;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private CancellationSignal cancel;

    private Button btnSignin;

    private class Callback implements CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
        @Override
        public void onResult(GetCredentialResponse result) {
            try {
                linkToFirebase(GoogleIdTokenCredential.createFrom(result.getCredential().getData()).getIdToken());
            } catch (Exception e) {
                showToast("Token Parsing Error");
                resetUI();
            }
        }

        @Override
        public void onError(@NonNull GetCredentialException e) {
            resetUI();
            showToast("Sign-In Error: " + e.getLocalizedMessage());
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_activity);
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        btnSignin = findViewById(R.id.btn_signin);
        if (auth.getCurrentUser() != null) {
            btnSignin.setEnabled(false);
            applyDarkMode();
            return;
        }
        manager = CredentialManager.create(this);
        GetGoogleIdOption options = new GetGoogleIdOption
                .Builder()
                .setServerClientId("389842618012-hhsqficg7ubd1psh60t9vmvj2nq2o3g3.apps.googleusercontent.com")
                .setFilterByAuthorizedAccounts(false).build();
        request = new GetCredentialRequest.Builder().addCredentialOption(options).build();
        btnSignin.setOnClickListener(this::executeSignin);
    }

    private void executeSignin(View v) {
        if (cancel != null) cancel.cancel();
        cancel = new CancellationSignal();
        btnSignin.setEnabled(false);
        manager.getCredentialAsync(this, request, cancel, getMainExecutor(), new Callback());
    }

    private void linkToFirebase(String token) {
        AuthCredential credential = GoogleAuthProvider.getCredential(token, null);
        auth.signInWithCredential(credential).addOnCompleteListener(this, this::onSigninComplete);
    }

    private void resetUI() {
        btnSignin.setEnabled(true);
    }

    private void onSigninComplete(Task<AuthResult> task) {
        if (task.isSuccessful()) {
            AuthResult result = task.getResult();
            FirebaseUser user = result.getUser();
            if (result.getAdditionalUserInfo().isNewUser()) {
                User profile = new User(user.getUid(), user.getDisplayName(), user.getEmail());
                db.collection("users").document(user.getUid()).set(profile)
                        .addOnSuccessListener(this, (Void v) -> {
                            showToast("Account Created Successfully");
                            goToMainScreen();
                        })
                        .addOnFailureListener(this, (Exception e) -> {
                            resetUI();
                            showToast("Account Creation Failure: " + e.getLocalizedMessage());
                        });
            } else {
                applyDarkMode();
            }
        } else {
            resetUI();
            Exception e = task.getException();
            showToast("Authentication Failed: " + (e != null ? e.getLocalizedMessage() : "Unknown Error"));
        }
    }

    private void goToMainScreen() {
        goToActivity(MainActivity.class, true);
    }

    private void applyDarkMode() {
        String uid = auth.getUid();
        if (uid == null) {
            showToast("Welcome Back");
            goToMainScreen();
            return;
        }
        db.collection("users").document(uid).get().addOnSuccessListener(this, (DocumentSnapshot s) -> {
            if (s.exists()) {
                User profile = s.toObject(User.class);
                if (profile != null && profile.isDarkMode() && AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_YES) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                }
            }
            showToast("Welcome Back");
            goToMainScreen();
        }).addOnFailureListener(this, (Exception e) -> {
            showToast("Welcome Back");
            goToMainScreen();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cancel != null) cancel.cancel();
    }
}