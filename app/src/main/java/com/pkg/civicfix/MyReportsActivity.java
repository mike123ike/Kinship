package com.pkg.civicfix;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.pkg.civicfix.base.CivicFixActivity;
import com.pkg.civicfix.model.UserReportItem;

import java.util.ArrayList;
import java.util.List;

public class MyReportsActivity
        extends CivicFixActivity {

    private FirebaseAuth auth;

    private UserReportsRepository
            reportsRepository;

    private LinearLayout reportsContainer;

    private TextView tvTabActive;
    private TextView tvTabFixed;

    private View indicatorActive;
    private View indicatorFixed;

    private TextView tvEmpty;

    private boolean showingFixed =
            false;

    private final List<UserReportItem>
            allReports =
            new ArrayList<>();

    @Override
    protected void onCreate(
            @Nullable Bundle savedInstanceState
    ) {
        super.onCreate(
                savedInstanceState
        );

        setContentView(
                R.layout.my_reports_activity
        );

        auth =
                FirebaseAuth.getInstance();

        reportsRepository =
                new UserReportsRepository(
                        FirebaseFirestore
                                .getInstance()
                );

        Toolbar toolbar =
                findViewById(
                        R.id.toolbar
                );

        toolbar.setNavigationOnClickListener(v ->
                finish()
        );

        if (
                toolbar.getNavigationIcon()
                        != null
        ) {

            toolbar.getNavigationIcon()
                    .setTint(
                            ContextCompat.getColor(
                                    this,
                                    R.color.tertiary
                            )
                    );
        }

        reportsContainer =
                findViewById(
                        R.id.reports_container
                );

        tvTabActive =
                findViewById(
                        R.id.tv_tab_active
                );

        tvTabFixed =
                findViewById(
                        R.id.tv_tab_fixed
                );

        indicatorActive =
                findViewById(
                        R.id.indicator_active
                );

        indicatorFixed =
                findViewById(
                        R.id.indicator_fixed
                );

        tvEmpty =
                findViewById(
                        R.id.tv_reports_empty
                );

        findViewById(
                R.id.tab_active
        ).setOnClickListener(v -> {

            showingFixed =
                    false;

            renderCurrentTab();
        });

        findViewById(
                R.id.tab_fixed
        ).setOnClickListener(v -> {

            showingFixed =
                    true;

            renderCurrentTab();
        });

        loadReports();
    }

    private void loadReports() {

        FirebaseUser user =
                auth.getCurrentUser();

        if (user == null) {

            finish();

            return;
        }

        reportsContainer
                .removeAllViews();

        tvEmpty.setText(
                "Loading reports..."
        );

        tvEmpty.setVisibility(
                View.VISIBLE
        );

        reportsRepository
                .loadReportsForUser(
                        user.getUid(),
                        new UserReportsRepository.Callback() {

                            @Override
                            public void onSuccess(
                                    List<UserReportItem> reports
                            ) {

                                allReports.clear();

                                allReports.addAll(
                                        reports
                                );

                                renderCurrentTab();
                            }

                            @Override
                            public void onError(
                                    Exception error
                            ) {

                                reportsContainer
                                        .removeAllViews();

                                tvEmpty.setText(
                                        "Could not load reports"
                                );

                                tvEmpty.setVisibility(
                                        View.VISIBLE
                                );
                            }
                        }
                );
    }

    private void renderCurrentTab() {

        setTabSelected(
                !showingFixed
        );

        reportsContainer
                .removeAllViews();

        int displayed =
                0;

        for (
                UserReportItem item
                : allReports
        ) {

            if (
                    showingFixed
                            != item.isFixed()
            ) {
                continue;
            }

            addReportCard(
                    item
            );

            displayed++;
        }

        if (displayed == 0) {

            tvEmpty.setText(
                    showingFixed
                            ? "No fixed reports"
                            : "No active reports"
            );

            tvEmpty.setVisibility(
                    View.VISIBLE
            );

        } else {

            tvEmpty.setVisibility(
                    View.GONE
            );
        }
    }

    private void addReportCard(
            UserReportItem item
    ) {

        View card =
                getLayoutInflater()
                        .inflate(
                                R.layout.item_my_report,
                                reportsContainer,
                                false
                        );

        ReportCardBinder.bind(
                this,
                card,
                item
        );

        card.setOnClickListener(v ->

                ReportSummaryBottomSheet
                        .newInstance(
                                item
                        )
                        .show(
                                getSupportFragmentManager(),
                                "report_summary"
                        )
        );

        reportsContainer.addView(
                card
        );
    }

    private void setTabSelected(
            boolean activeSelected
    ) {

        int primary =
                ContextCompat.getColor(
                        this,
                        R.color.primary
                );

        int secondary =
                ContextCompat.getColor(
                        this,
                        R.color.textSecondary
                );

        tvTabActive.setTextColor(
                activeSelected
                        ? primary
                        : secondary
        );

        tvTabFixed.setTextColor(
                activeSelected
                        ? secondary
                        : primary
        );

        indicatorActive.setVisibility(
                activeSelected
                        ? View.VISIBLE
                        : View.INVISIBLE
        );

        indicatorFixed.setVisibility(
                activeSelected
                        ? View.INVISIBLE
                        : View.VISIBLE
        );
    }
}