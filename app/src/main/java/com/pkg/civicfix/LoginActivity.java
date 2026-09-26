package com.pkg.civicfix;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.View;
import android.widget.Button;

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

    private class Callback
            implements CredentialManagerCallback<
            GetCredentialResponse,
            GetCredentialException
            > {

        @Override
        public void onResult(GetCredentialResponse result) {

            try {

                String token =
                        GoogleIdTokenCredential
                                .createFrom(
                                        result.getCredential().getData()
                                )
                                .getIdToken();

                linkToFirebase(token);

            } catch (Exception e) {

                showToast("Token Parsing Error");
                resetUI();
            }
        }

        @Override
        public void onError(
                @NonNull GetCredentialException e
        ) {

            resetUI();

            showToast(
                    "Sign-In Error: "
                            + e.getLocalizedMessage()
            );
        }
    }

    @Override
    protected void onCreate(
            @Nullable Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.login_activity);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        btnSignin =
                findViewById(R.id.btn_signin);

        /*
         * If the user is already signed in, apply their
         * saved theme and continue to MainActivity.
         */
        if (auth.getCurrentUser() != null) {

            btnSignin.setEnabled(false);

            applyDarkMode();

            return;
        }

        manager =
                CredentialManager.create(this);

        GetGoogleIdOption options =
                new GetGoogleIdOption.Builder()
                        .setServerClientId(
                                "389842618012-hhsqficg7ubd1psh60t9vmvj2nq2o3g3.apps.googleusercontent.com"
                        )
                        .setFilterByAuthorizedAccounts(false)
                        .build();

        request =
                new GetCredentialRequest.Builder()
                        .addCredentialOption(options)
                        .build();

        btnSignin.setOnClickListener(
                this::executeSignin
        );
    }

    private void executeSignin(View v) {

        if (cancel != null) {
            cancel.cancel();
        }

        cancel =
                new CancellationSignal();

        btnSignin.setEnabled(false);

        manager.getCredentialAsync(
                this,
                request,
                cancel,
                getMainExecutor(),
                new Callback()
        );
    }

    private void linkToFirebase(
            String token
    ) {

        AuthCredential credential =
                GoogleAuthProvider.getCredential(
                        token,
                        null
                );

        auth.signInWithCredential(credential)
                .addOnCompleteListener(
                        this,
                        this::onSigninComplete
                );
    }

    private void resetUI() {

        btnSignin.setEnabled(true);
    }

    private void onSigninComplete(
            Task<AuthResult> task
    ) {

        if (!task.isSuccessful()) {

            resetUI();

            Exception e =
                    task.getException();

            showToast(
                    "Authentication Failed: "
                            + (
                            e != null
                                    ? e.getLocalizedMessage()
                                    : "Unknown Error"
                    )
            );

            return;
        }

        AuthResult result =
                task.getResult();

        FirebaseUser user =
                result.getUser();

        if (user == null) {

            resetUI();

            showToast(
                    "Authentication Failed: No user returned"
            );

            return;
        }

        if (result.getAdditionalUserInfo() != null
                && result.getAdditionalUserInfo().isNewUser()) {

            createNewUserProfile(user);

        } else {

            applyDarkMode();
        }
    }

    private void createNewUserProfile(
            FirebaseUser firebaseUser
    ) {

        User profile =
                new User(
                        firebaseUser.getUid(),
                        firebaseUser.getDisplayName(),
                        firebaseUser.getEmail()
                );

        db.collection("users")
                .document(firebaseUser.getUid())
                .set(profile)
                .addOnSuccessListener(
                        this,
                        v -> {

                            showToast(
                                    "Account Created Successfully"
                            );

                            goToMainScreen();
                        }
                )
                .addOnFailureListener(
                        this,
                        e -> {

                            resetUI();

                            showToast(
                                    "Account Creation Failure: "
                                            + e.getLocalizedMessage()
                            );
                        }
                );
    }

    private void goToMainScreen() {

        goToActivity(
                MainActivity.class,
                true
        );
    }

    /*
     * Only read the field that this method actually needs.
     *
     * Previously this converted the entire Firestore document
     * to User.class. That meant an unrelated malformed field
     * such as savedLocations could crash the app before login
     * finished.
     */
    private void applyDarkMode() {

        String uid =
                auth.getUid();

        if (uid == null) {

            showToast("Welcome Back");

            goToMainScreen();

            return;
        }

        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(
                        this,
                        (DocumentSnapshot snapshot) -> {

                            if (snapshot.exists()) {

                                Boolean darkMode =
                                        snapshot.getBoolean(
                                                "darkMode"
                                        );

                                if (
                                        Boolean.TRUE.equals(darkMode)
                                                && AppCompatDelegate
                                                .getDefaultNightMode()
                                                != AppCompatDelegate
                                                .MODE_NIGHT_YES
                                ) {

                                    AppCompatDelegate
                                            .setDefaultNightMode(
                                                    AppCompatDelegate
                                                            .MODE_NIGHT_YES
                                            );
                                }

                                /*
                                 * If the user prefers light mode
                                 * but the app is currently forced
                                 * into dark mode, restore light.
                                 */
                                if (
                                        Boolean.FALSE.equals(darkMode)
                                                && AppCompatDelegate
                                                .getDefaultNightMode()
                                                == AppCompatDelegate
                                                .MODE_NIGHT_YES
                                ) {

                                    AppCompatDelegate
                                            .setDefaultNightMode(
                                                    AppCompatDelegate
                                                            .MODE_NIGHT_NO
                                            );
                                }
                            }

                            showToast("Welcome Back");

                            goToMainScreen();
                        }
                )
                .addOnFailureListener(
                        this,
                        e -> {

                            /*
                             * Theme loading should never prevent
                             * the user from entering the app.
                             */
                            showToast("Welcome Back");

                            goToMainScreen();
                        }
                );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (cancel != null) {
            cancel.cancel();
        }
    }
}