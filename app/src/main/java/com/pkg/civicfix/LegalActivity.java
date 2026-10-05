package com.pkg.civicfix;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.pkg.civicfix.base.CivicFixActivity;

public class LegalActivity
        extends CivicFixActivity {

    public static final String EXTRA_PAGE =
            "legal_page";

    public static final String PAGE_TERMS =
            "terms";

    public static final String PAGE_PRIVACY =
            "privacy";


    @Override
    protected void onCreate(
            @Nullable Bundle savedInstanceState
    ) {

        super.onCreate(
                savedInstanceState
        );

        setContentView(
                R.layout.legal_activity
        );


        Toolbar toolbar =
                findViewById(
                        R.id.toolbar
                );

        TextView tvTitle =
                findViewById(
                        R.id.tv_legal_title
                );

        TextView tvUpdated =
                findViewById(
                        R.id.tv_legal_updated
                );

        TextView tvContent =
                findViewById(
                        R.id.tv_legal_content
                );


        setSupportActionBar(
                toolbar
        );


        toolbar.setNavigationOnClickListener(
                v -> finish()
        );


        if (
                toolbar.getNavigationIcon()
                        != null
        ) {

            toolbar.getNavigationIcon()
                    .setTintList(
                            ColorStateList.valueOf(
                                    ContextCompat.getColor(
                                            this,
                                            R.color.tertiary
                                    )
                            )
                    );
        }


        String page =
                getIntent()
                        .getStringExtra(
                                EXTRA_PAGE
                        );


        if (
                PAGE_PRIVACY.equals(
                        page
                )
        ) {

            toolbar.setTitle(
                    "Privacy Policy"
            );

            tvTitle.setText(
                    "Privacy Policy"
            );

            tvUpdated.setText(
                    "Last updated October 2026"
            );

            tvContent.setText(
                    buildPrivacyPolicy()
            );

        } else {

            toolbar.setTitle(
                    "Terms of Use"
            );

            tvTitle.setText(
                    "Terms of Use"
            );

            tvUpdated.setText(
                    "Last updated October 2026"
            );

            tvContent.setText(
                    buildTerms()
            );
        }
    }


    private String buildTerms() {

        return
                "1. Purpose of Kinship\n\n"
                        + "Kinship is a community reporting application that allows users to report local issues, view community-confirmed events, discuss those events, and follow issues near places they care about.\n\n"

                        + "2. Not an Emergency Service\n\n"
                        + "Kinship is not an emergency response service and should not be used to request police, fire, medical, or other emergency assistance. If there is an immediate threat to life or safety, contact the appropriate emergency service.\n\n"

                        + "3. User Accounts\n\n"
                        + "Some Kinship features require a signed-in account. You are responsible for activity performed through your account and for providing accurate information when using the service.\n\n"

                        + "4. Reports and Community Content\n\n"
                        + "Users may submit descriptions, locations, photos, comments, votes, and other information about community issues. Content should be reasonably accurate, relevant to the issue being discussed, and submitted in good faith.\n\n"

                        + "5. Anonymous Reporting\n\n"
                        + "Kinship may allow a report to appear anonymously to other users. Anonymous reporting does not mean that the report is technically disconnected from the account that submitted it. Kinship may retain the user identifier associated with a report for integrity, abuse prevention, and application functionality.\n\n"

                        + "6. Prohibited Conduct\n\n"
                        + "Do not knowingly submit false reports, impersonate another person, harass or threaten others, post discriminatory or abusive content, expose private personal information, interfere with the operation of the service, or attempt to manipulate community voting or reporting systems.\n\n"

                        + "7. Photos and Personal Information\n\n"
                        + "When uploading photos or writing reports and comments, avoid including unnecessary personal information about other people. When practical, keep photographs focused on the civic issue rather than identifiable bystanders.\n\n"

                        + "8. Community Information May Be Incomplete\n\n"
                        + "Kinship relies in part on information submitted by users. Reports, comments, status information, locations, and community confirmations may be incomplete, inaccurate, delayed, or outdated. Kinship does not guarantee that every reported issue will be investigated, repaired, or acted upon by a government agency or other organization.\n\n"

                        + "9. Third-Party Services\n\n"
                        + "Kinship uses third-party services to provide features such as authentication, maps, image hosting, data storage, and local news. Those services may operate under their own terms and privacy policies.\n\n"

                        + "10. Availability\n\n"
                        + "Kinship may change, suspend, remove, or add features as the application develops. Access to the service may occasionally be interrupted or unavailable.\n\n"

                        + "11. Changes to These Terms\n\n"
                        + "These Terms of Use may be updated as Kinship develops. Continued use of the application after an updated version is made available means that the updated terms will apply to future use.\n\n"

                        + "12. Support\n\n"
                        + "Questions about Kinship can be submitted through the Support option in the application.";
    }


    private String buildPrivacyPolicy() {

        return
                "1. Overview\n\n"
                        + "This Privacy Policy explains how Kinship uses information needed to provide its community reporting features.\n\n"

                        + "2. Account Information\n\n"
                        + "When you sign in, Kinship may receive account information such as your user identifier, display name, email address, and profile information through the authentication service used by the application.\n\n"

                        + "3. Reports\n\n"
                        + "A report may include the issue category, severity, description, photograph, report location, timestamps, and the identifier of the account that submitted it. Reports may later be associated with a shared community event.\n\n"

                        + "4. Anonymous Reporting\n\n"
                        + "If Anonymous Reporting is enabled, Kinship can hide your identity from other users when displaying your report. The application may still store your account identifier with the report so it can enforce reporting rules and maintain the integrity of community data.\n\n"

                        + "5. Important Locations\n\n"
                        + "Kinship lets you save places such as Home, Work, School, and custom Important Locations. These saved locations are stored with your account and are not intended to be displayed publicly to other Kinship users. They are used for features such as identifying events near places you care about.\n\n"

                        + "6. Device Location\n\n"
                        + "Kinship may request location permission to help select report locations, show relevant map information, determine the city used for local news, and support other location-based features. For local news, Kinship uses your device location to determine a city and sends the city name, rather than your precise coordinates, to the news service.\n\n"

                        + "7. Camera and Photos\n\n"
                        + "Kinship may request camera access so you can take a photo for a report. You may also choose an existing image from your device. Images submitted with reports may be uploaded to an image-hosting service so they can be displayed with the report or related event.\n\n"

                        + "8. Community Activity\n\n"
                        + "Kinship may store comments, votes, status confirmations, report history, timestamps, and similar activity needed to operate community features and prevent duplicate or inconsistent actions.\n\n"

                        + "9. Services Used by Kinship\n\n"
                        + "Kinship currently uses services including Firebase for authentication and application data, Google Maps Platform for mapping, Cloudinary for report images, and NewsData.io for local news. These services may process information according to their own privacy policies.\n\n"

                        + "10. Public Information\n\n"
                        + "Information intentionally submitted to public community areas, such as event descriptions, photos, comments, event locations, and community activity, may be visible to other users. Do not include private information that you do not want others to see.\n\n"

                        + "11. Permissions\n\n"
                        + "You can review or change Kinship's Camera and Location permissions at any time through Android's application settings. Some Kinship features may not work when the permissions they require are disabled.\n\n"

                        + "12. Security\n\n"
                        + "Kinship uses authentication and access controls intended to limit access to account-specific information. No online or mobile system can guarantee complete security.\n\n"

                        + "13. Changes to This Policy\n\n"
                        + "This Privacy Policy may be updated as Kinship's features and data practices change.\n\n"

                        + "14. Questions\n\n"
                        + "Privacy questions can be submitted through the Support option in the application.";
    }
}