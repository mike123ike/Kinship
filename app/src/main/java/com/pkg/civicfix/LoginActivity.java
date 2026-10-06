package com.pkg.civicfix;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.gms.tasks.Task;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.pkg.civicfix.base.CivicFixActivity;
import com.pkg.civicfix.model.User;

public class LoginActivity
        extends CivicFixActivity {

    private static final String SUPPORT_EMAIL =
            "civicfixtestadmin01@gmail.com";


    private CredentialManager manager;

    private GetCredentialRequest request;

    private FirebaseAuth auth;

    private FirebaseFirestore db;

    private CancellationSignal cancel;

    private Button btnSignin;


    private class Callback
            implements CredentialManagerCallback<
            GetCredentialResponse,
            GetCredentialException> {

        @Override
        public void onResult(
                GetCredentialResponse result
        ) {

            try {

                String token =
                        GoogleIdTokenCredential
                                .createFrom(
                                        result.getCredential()
                                                .getData()
                                )
                                .getIdToken();


                linkToFirebase(
                        token
                );

            } catch (
                    Exception error
            ) {

                showToast(
                        "Token Parsing Error"
                );

                resetUI();
            }
        }


        @Override
        public void onError(
                @NonNull GetCredentialException error
        ) {

            resetUI();


            showToast(
                    "Sign-In Error: "
                            + error.getLocalizedMessage()
            );
        }
    }


    @Override
    protected void onCreate(
            @Nullable Bundle savedInstanceState
    ) {

        super.onCreate(
                savedInstanceState
        );


        setContentView(
                R.layout.login_activity
        );


        auth =
                FirebaseAuth.getInstance();

        db =
                FirebaseFirestore.getInstance();


        btnSignin =
                findViewById(
                        R.id.btn_signin
                );


        // footer

        findViewById(
                R.id.tvPrivacyPolicy
        ).setOnClickListener(
                v ->

                        openLegalPage(
                                LegalActivity.PAGE_PRIVACY
                        )
        );


        findViewById(
                R.id.tvTerms
        ).setOnClickListener(
                v ->

                        openLegalPage(
                                LegalActivity.PAGE_TERMS
                        )
        );


        findViewById(
                R.id.tvSupport
        ).setOnClickListener(
                v ->

                        openSupportEmail()
        );


        // existing auth session

        FirebaseUser currentUser =
                auth.getCurrentUser();


        if (
                currentUser != null
        ) {

            btnSignin.setEnabled(
                    false
            );


            // an auth account can exist even if profile creation previously failed. always verify /users/{uid}.
            ensureUserProfile(
                    currentUser,
                    false
            );


            return;
        }


        prepareGoogleSignIn();
    }


    private void prepareGoogleSignIn() {

        manager =
                CredentialManager.create(
                        this
                );


        GetGoogleIdOption options =
                new GetGoogleIdOption
                        .Builder()
                        .setServerClientId(
                                "389842618012-hhsqficg7ubd1psh60t9vmvj2nq2o3g3.apps.googleusercontent.com"
                        )
                        .setFilterByAuthorizedAccounts(
                                false
                        )
                        .build();


        request =
                new GetCredentialRequest
                        .Builder()
                        .addCredentialOption(
                                options
                        )
                        .build();


        btnSignin.setOnClickListener(
                this::executeSignin
        );
    }


    private void executeSignin(
            View view
    ) {

        if (
                manager == null
                        || request == null
        ) {

            prepareGoogleSignIn();
        }


        if (
                cancel != null
        ) {

            cancel.cancel();
        }


        cancel =
                new CancellationSignal();


        btnSignin.setEnabled(
                false
        );


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


        auth.signInWithCredential(
                        credential
                )
                .addOnCompleteListener(
                        this,
                        this::onSigninComplete
                );
    }


    private void onSigninComplete(
            Task<AuthResult> task
    ) {

        if (
                !task.isSuccessful()
        ) {

            resetUI();


            Exception error =
                    task.getException();


            showToast(
                    "Authentication Failed: "
                            + (
                            error != null
                                    ? error.getLocalizedMessage()
                                    : "Unknown Error"
                    )
            );


            return;
        }


        AuthResult result =
                task.getResult();


        FirebaseUser user =
                result.getUser();


        if (
                user == null
        ) {

            resetUI();


            showToast(
                    "Authentication Failed"
            );


            return;
        }


        boolean newAuthUser =
                result.getAdditionalUserInfo()
                        != null

                        && result
                        .getAdditionalUserInfo()
                        .isNewUser();


        ensureUserProfile(
                user,
                newAuthUser
        );
    }


    private void ensureUserProfile(
            FirebaseUser firebaseUser,
            boolean newAuthUser
    ) {

        DocumentReference userRef =
                db.collection(
                                "users"
                        )
                        .document(
                                firebaseUser.getUid()
                        );


        userRef.get()
                .addOnSuccessListener(
                        this,

                        snapshot -> {

                            if (
                                    snapshot.exists()
                            ) {

                                // local kinship theme is authoritative. keep firestore's backup in sync.
                                Boolean storedDark =
                                        snapshot.getBoolean(
                                                "darkMode"
                                        );


                                boolean localDark =
                                        ThemeManager.isDarkMode(
                                                this
                                        );


                                if (
                                        storedDark == null
                                                || storedDark != localDark
                                ) {

                                    userRef.update(
                                            "darkMode",
                                            localDark
                                    );
                                }


                                showToast(
                                        "Welcome Back"
                                );


                                goToMainScreen();

                                return;
                            }


                            // firebase auth succeeded but firestore profile is missing. repair it automatically.
                            createUserProfile(
                                    userRef,
                                    firebaseUser,
                                    newAuthUser
                            );
                        }
                )
                .addOnFailureListener(
                        this,

                        error -> {

                            resetUI();


                            showToast(
                                    "Could not load your profile: "
                                            + error.getLocalizedMessage()
                            );
                        }
                );
    }


    private void createUserProfile(
            DocumentReference userRef,
            FirebaseUser firebaseUser,
            boolean newAuthUser
    ) {

        User profile =
                new User(
                        firebaseUser.getUid(),
                        firebaseUser.getDisplayName(),
                        firebaseUser.getEmail()
                );


        // store the same theme kinship is actually using.
        profile.setDarkMode(
                ThemeManager.isDarkMode(
                        this
                )
        );


        userRef.set(
                        profile
                )
                .addOnSuccessListener(
                        this,

                        ignored -> {

                            showToast(
                                    newAuthUser
                                            ? "Account Created Successfully"
                                            : "Profile restored"
                            );


                            goToMainScreen();
                        }
                )
                .addOnFailureListener(
                        this,

                        error -> {

                            // important: do not leave a firebase auth session active if its firestore profile could not be created.
                            auth.signOut();


                            resetUI();


                            if (
                                    manager == null
                                            || request == null
                            ) {

                                prepareGoogleSignIn();
                            }


                            showToast(
                                    "Could not create your profile: "
                                            + error.getLocalizedMessage()
                            );
                        }
                );
    }


    private void resetUI() {

        btnSignin.setEnabled(
                true
        );
    }


    private void goToMainScreen() {

        goToActivity(
                MainActivity.class,
                true
        );
    }


    private void openLegalPage(
            String page
    ) {

        Intent intent =
                new Intent(
                        this,
                        LegalActivity.class
                );


        intent.putExtra(
                LegalActivity.EXTRA_PAGE,
                page
        );


        startActivity(
                intent
        );
    }


    private void openSupportEmail() {

        String subject =
                "Kinship Support";


        String body =
                "Please describe what you need help with:\n\n";


        Uri uri =
                Uri.parse(
                        "mailto:"
                                + SUPPORT_EMAIL

                                + "?subject="
                                + Uri.encode(
                                subject
                        )

                                + "&body="
                                + Uri.encode(
                                body
                        )
                );


        Intent intent =
                new Intent(
                        Intent.ACTION_SENDTO,
                        uri
                );


        try {

            startActivity(
                    intent
            );

        } catch (
                ActivityNotFoundException error
        ) {

            showToast(
                    "No email app is available"
            );
        }
    }


    @Override
    protected void onDestroy() {

        super.onDestroy();


        if (
                cancel != null
        ) {

            cancel.cancel();
        }
    }
}