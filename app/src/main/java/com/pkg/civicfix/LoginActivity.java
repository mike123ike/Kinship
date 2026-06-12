package com.pkg.civicfix;

import androidx.annotation.NonNull;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;

import androidx.credentials.exceptions.GetCredentialException;
import android.os.Bundle;
import android.content.Intent;
import android.os.CancellationSignal;
import android.view.View;

import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class LoginActivity extends AppCompatActivity {
    private CredentialManager manager;
    private GetCredentialRequest request;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private CancellationSignal cancel;

    private Button btnSignin;
    private ProgressBar progressBar;

    private class Callback implements CredentialManagerCallback<GetCredentialResponse, GetCredentialException> {
        @Override
        public void onResult(GetCredentialResponse result) {
            try {
                CustomCredential cred = (CustomCredential) result.getCredential();
                linkToFirebase(GoogleIdTokenCredential.createFrom(cred.getData()).getIdToken());
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
        auth = FirebaseAuth.getInstance();
        auth.signOut();
        if (auth.getCurrentUser() != null) {
            goToMainScreen();
            return;
        }
        setContentView(R.layout.login_activity);
        btnSignin = findViewById(R.id.btn_signin);
        progressBar = findViewById(R.id.progress_bar);
        progressBar.setVisibility(View.GONE);
        manager = CredentialManager.create(this);
        GetGoogleIdOption options = new GetGoogleIdOption
                .Builder()
                .setServerClientId("389842618012-hhsqficg7ubd1psh60t9vmvj2nq2o3g3.apps.googleusercontent.com")
                .setFilterByAuthorizedAccounts(false).build();
        request = new GetCredentialRequest.Builder().addCredentialOption(options).build();
        db = FirebaseFirestore.getInstance();
        btnSignin.setOnClickListener((View v) -> executeSignin());
        cancel = new CancellationSignal();
    }

    private void executeSignin() {
        cancel.cancel();
        cancel = new CancellationSignal();
        progressBar.setVisibility(View.VISIBLE);
        btnSignin.setEnabled(false);
        manager.getCredentialAsync(this, request, cancel, getMainExecutor(), new Callback());
    }

    private void linkToFirebase(String token) {
        AuthCredential credential = GoogleAuthProvider.getCredential(token, null);
        auth.signInWithCredential(credential).addOnCompleteListener(this::onSigninComplete);
    }

    private void resetUI() {
        progressBar.setVisibility(View.GONE);
        btnSignin.setEnabled(true);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void onSigninComplete(Task<AuthResult> task) {
        if (task.isSuccessful()) {
            AuthResult result = task.getResult();
            FirebaseUser user = auth.getCurrentUser();
            if (result.getAdditionalUserInfo().isNewUser()) {
                Map<String, Object> map = new HashMap<>();
                map.put("email", user.getEmail());
                map.put("isOfficial", false);
                map.put("displayName", user.getDisplayName());
                db.collection("users").document(user.getUid()).set(map)
                        .addOnSuccessListener((Void v) -> {
                            showToast("Account Created Successfully");
                            goToMainScreen();
                        })
                        .addOnFailureListener((Exception e) -> {
                            resetUI();
                            showToast("Account Creation Failure: " + e.getLocalizedMessage());
                        });
            } else {
                showToast("Welcome Back");
                goToMainScreen();
            }
        } else {
            resetUI();
            Exception e = task.getException();
            showToast("Authentication Failed: " + (e != null ? e.getLocalizedMessage() : "Unknown Error"));
        }
    }

    private void goToMainScreen() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancel.cancel();
    }
}