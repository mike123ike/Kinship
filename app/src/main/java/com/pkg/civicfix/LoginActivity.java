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
import androidx.appcompat.app.AppCompatDelegate;
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
import com.google.firebase.firestore.DocumentSnapshot;
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


        // footer links
        findViewById(
                R.id.tvPrivacyPolicy
        ).setOnClickListener(
                v -> openLegalPage(
                        LegalActivity.PAGE_PRIVACY
                )
        );


        findViewById(
                R.id.tvTerms
        ).setOnClickListener(
                v -> openLegalPage(
                        LegalActivity.PAGE_TERMS
                )
        );


        findViewById(
                R.id.tvSupport
        ).setOnClickListener(
                v -> openSupportEmail()
        );


        // already signed in
        if (
                auth.getCurrentUser()
                        != null
        ) {

            btnSignin.setEnabled(
                    false
            );

            applyDarkMode();

            return;
        }


        // google sign in
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
                GoogleAuthProvider
                        .getCredential(
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


    private void resetUI() {

        btnSignin.setEnabled(
                true
        );
    }


    private void onSigninComplete(
            Task<AuthResult> task
    ) {

        if (
                task.isSuccessful()
        ) {

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


            if (
                    result.getAdditionalUserInfo()
                            != null

                            && result
                            .getAdditionalUserInfo()
                            .isNewUser()
            ) {

                User profile =
                        new User(
                                user.getUid(),
                                user.getDisplayName(),
                                user.getEmail()
                        );


                db.collection("users")
                        .document(
                                user.getUid()
                        )
                        .set(
                                profile
                        )
                        .addOnSuccessListener(
                                this,

                                ignored -> {

                                    showToast(
                                            "Account Created Successfully"
                                    );

                                    goToMainScreen();
                                }
                        )
                        .addOnFailureListener(
                                this,

                                error -> {

                                    resetUI();

                                    showToast(
                                            "Account Creation Failure: "
                                                    + error.getLocalizedMessage()
                                    );
                                }
                        );

            } else {

                applyDarkMode();
            }

        } else {

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
        }
    }


    private void goToMainScreen() {

        goToActivity(
                MainActivity.class,
                true
        );
    }


    private void applyDarkMode() {

        String uid =
                auth.getUid();


        if (
                uid == null
        ) {

            showToast(
                    "Welcome Back"
            );

            goToMainScreen();

            return;
        }


        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(
                        this,

                        (DocumentSnapshot snapshot) -> {

                            if (
                                    snapshot.exists()
                            ) {

                                User profile =
                                        snapshot.toObject(
                                                User.class
                                        );


                                if (
                                        profile != null
                                                && profile.isDarkMode()
                                                && AppCompatDelegate
                                                .getDefaultNightMode()
                                                != AppCompatDelegate.MODE_NIGHT_YES
                                ) {

                                    AppCompatDelegate
                                            .setDefaultNightMode(
                                                    AppCompatDelegate.MODE_NIGHT_YES
                                            );
                                }
                            }


                            showToast(
                                    "Welcome Back"
                            );

                            goToMainScreen();
                        }
                )
                .addOnFailureListener(
                        this,

                        error -> {

                            showToast(
                                    "Welcome Back"
                            );

                            goToMainScreen();
                        }
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