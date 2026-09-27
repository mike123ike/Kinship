package com.pkg.civicfix;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;
import com.pkg.civicfix.model.Report;
import com.pkg.civicfix.model.UserReportItem;

public final class ReportCardBinder {

    private ReportCardBinder() {
    }

    public static void bind(
            Context context,
            View card,
            UserReportItem item
    ) {

        Report report =
                item.getReport();

        String rawStatus =
                item.getEventStatus();

        TextView tvCategory =
                card.findViewById(
                        R.id.tv_report_category
                );

        TextView tvDescription =
                card.findViewById(
                        R.id.tv_report_description
                );

        TextView tvTime =
                card.findViewById(
                        R.id.tv_report_time
                );

        TextView tvStatus =
                card.findViewById(
                        R.id.tv_report_status
                );

        MaterialCardView statusCard =
                card.findViewById(
                        R.id.card_report_status
                );

        View accent =
                card.findViewById(
                        R.id.view_report_accent
                );


        tvCategory.setText(
                ReportDisplayUtils
                        .categoryLabel(
                                report.getCategory()
                        )
        );

        tvDescription.setText(
                ReportDisplayUtils
                        .description(
                                report.getDescription()
                        )
        );

        tvTime.setText(
                "Reported "
                        + ReportDisplayUtils
                        .formatRelativeTime(
                                report.getCreatedAt()
                        )
        );

        tvStatus.setText(
                ReportDisplayUtils
                        .statusLabel(
                                rawStatus
                        )
        );


        int backgroundColor =
                ContextCompat.getColor(
                        context,
                        ReportStatusUi
                                .getBackgroundColorRes(
                                        rawStatus
                                )
                );

        int textColor =
                ContextCompat.getColor(
                        context,
                        ReportStatusUi
                                .getTextColorRes(
                                        rawStatus
                                )
                );

        int strokeColor =
                ContextCompat.getColor(
                        context,
                        ReportStatusUi
                                .getStrokeColorRes(
                                        rawStatus
                                )
                );

        statusCard.setCardBackgroundColor(
                backgroundColor
        );

        statusCard.setStrokeColor(
                strokeColor
        );

        tvStatus.setTextColor(
                textColor
        );

        // left edge of card uses same status color
        accent.setBackgroundColor(
                textColor
        );
    }
}