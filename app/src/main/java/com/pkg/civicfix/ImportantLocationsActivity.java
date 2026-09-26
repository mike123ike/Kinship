package com.pkg.civicfix;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.pkg.civicfix.base.CivicFixActivity;

public class ImportantLocationsActivity extends CivicFixActivity {

    private Toolbar toolbar;

    @Override
    protected void onCreate(
            @Nullable Bundle savedInstanceState
    ) {
        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.important_locations_activity
        );

        toolbar =
                findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);

        toolbar.setNavigationOnClickListener(v ->
                finish()
        );

        if (toolbar.getNavigationIcon() != null) {

            toolbar.getNavigationIcon().setTint(
                    ContextCompat.getColor(
                            this,
                            R.color.tertiary
                    )
            );
        }

        findViewById(
                R.id.btn_add_location
        ).setOnClickListener(v ->

                ImportantLocationBottomSheet
                        .newCustom(
                                false,
                                "",
                                "",
                                0,
                                0
                        )
                        .show(
                                getSupportFragmentManager(),
                                "add_custom_location"
                        )
        );

        findViewById(
                R.id.card_home
        ).setOnClickListener(v ->

                ImportantLocationBottomSheet
                        .newPreset(
                                "Home",
                                true,
                                "123 Main Street, Round Rock, TX",
                                30.5083,
                                -97.6789
                        )
                        .show(
                                getSupportFragmentManager(),
                                "edit_home"
                        )
        );

        findViewById(
                R.id.card_work
        ).setOnClickListener(v ->

                ImportantLocationBottomSheet
                        .newPreset(
                                "Work",
                                false,
                                "",
                                0,
                                0
                        )
                        .show(
                                getSupportFragmentManager(),
                                "add_work"
                        )
        );

        findViewById(
                R.id.card_school
        ).setOnClickListener(v ->

                ImportantLocationBottomSheet
                        .newPreset(
                                "School",
                                false,
                                "",
                                0,
                                0
                        )
                        .show(
                                getSupportFragmentManager(),
                                "add_school"
                        )
        );

        findViewById(
                R.id.card_gym
        ).setOnClickListener(v ->

                ImportantLocationBottomSheet
                        .newCustom(
                                true,
                                "Gym",
                                "456 Example Road, Round Rock, TX",
                                30.5150,
                                -97.6800
                        )
                        .show(
                                getSupportFragmentManager(),
                                "edit_gym"
                        )
        );
    }
}