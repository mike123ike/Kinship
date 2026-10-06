package com.pkg.civicfix.base;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.pkg.civicfix.R;

public abstract class CivicFixActivity extends AppCompatActivity {

    @Override
    public void setContentView(
            int layoutResID
    ) {

        super.setContentView(
                layoutResID
        );

        applyTopNavigationInset();
    }


    private void applyTopNavigationInset() {

        View topNavigation =
                findViewById(
                        R.id.top_bar
                );


        if (
                topNavigation == null
        ) {

            topNavigation =
                    findViewById(
                            R.id.toolbar
                    );
        }


        if (
                topNavigation == null
        ) {

            return;
        }


        final View target =
                topNavigation;


        final int originalHeight =
                target.getLayoutParams()
                        .height;


        final int originalPaddingLeft =
                target.getPaddingLeft();


        final int originalPaddingTop =
                target.getPaddingTop();


        final int originalPaddingRight =
                target.getPaddingRight();


        final int originalPaddingBottom =
                target.getPaddingBottom();


        ViewCompat.setOnApplyWindowInsetsListener(
                target,

                (view, windowInsets) -> {

                    Insets statusBars =
                            windowInsets.getInsets(
                                    WindowInsetsCompat
                                            .Type
                                            .statusBars()
                            );


                    int smallInset =
                            statusBars.top / 3;


                    ViewGroup.LayoutParams params =
                            view.getLayoutParams();


                    params.height =
                            originalHeight
                                    + smallInset;


                    view.setLayoutParams(
                            params
                    );


                    view.setPadding(
                            originalPaddingLeft,

                            originalPaddingTop
                                    + smallInset,

                            originalPaddingRight,

                            originalPaddingBottom
                    );


                    return windowInsets;
                }
        );


        ViewCompat.requestApplyInsets(
                target
        );
    }


    protected void showToast(
            String message
    ) {

        Toast.makeText(
                this,
                message,
                Toast.LENGTH_SHORT
        ).show();
    }


    protected void goToActivity(
            Class<? extends Activity> newActivity,
            boolean finishCurrent
    ) {

        Intent intent =
                new Intent(
                        this,
                        newActivity
                );


        startActivity(
                intent
        );


        if (
                finishCurrent
        ) {

            finish();
        }
    }
}