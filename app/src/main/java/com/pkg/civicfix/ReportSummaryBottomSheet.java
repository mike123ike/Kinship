package com.pkg.civicfix;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.card.MaterialCardView;
import com.pkg.civicfix.model.Report;
import com.pkg.civicfix.model.UserReportItem;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class ReportSummaryBottomSheet
        extends BottomSheetDialogFragment {

    private static final String ARG_CATEGORY =
            "category";

    private static final String ARG_EVENT_STATUS =
            "eventStatus";

    private static final String ARG_DESCRIPTION =
            "description";

    private static final String ARG_CREATED_AT =
            "createdAt";

    private static final String ARG_SEVERITY =
            "severity";

    private static final String ARG_LAT =
            "lat";

    private static final String ARG_LNG =
            "lng";

    private static final String ARG_EVENT_ID =
            "eventId";


    public static ReportSummaryBottomSheet newInstance(
            UserReportItem item
    ) {

        Report report =
                item.getReport();

        ReportSummaryBottomSheet sheet =
                new ReportSummaryBottomSheet();

        Bundle args =
                new Bundle();

        args.putString(
                ARG_CATEGORY,
                ReportDisplayUtils.categoryLabel(
                        report.getCategory()
                )
        );

        args.putString(
                ARG_EVENT_STATUS,
                item.getEventStatus()
        );

        args.putString(
                ARG_DESCRIPTION,
                ReportDisplayUtils.description(
                        report.getDescription()
                )
        );

        args.putLong(
                ARG_CREATED_AT,
                report.getCreatedAt() == null
                        ? 0
                        : report
                          .getCreatedAt()
                          .toDate()
                          .getTime()
        );

        args.putInt(
                ARG_SEVERITY,
                report.getSeverity()
        );

        args.putDouble(
                ARG_LAT,
                report.getLatitude()
        );

        args.putDouble(
                ARG_LNG,
                report.getLongitude()
        );

        args.putString(
                ARG_EVENT_ID,
                report.getEventId()
        );

        sheet.setArguments(
                args
        );

        return sheet;
    }


    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {

        return inflater.inflate(
                R.layout.bottom_sheet_report_summary,
                container,
                false
        );
    }


    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(
                view,
                savedInstanceState
        );

        Bundle args =
                getArguments();

        if (args == null) {

            dismiss();

            return;
        }


        // read arguments

        String category =
                args.getString(
                        ARG_CATEGORY,
                        "Report"
                );

        String rawStatus =
                args.getString(
                        ARG_EVENT_STATUS,
                        "PENDING"
                );

        String statusLabel =
                ReportDisplayUtils.statusLabel(
                        rawStatus
                );

        String description =
                args.getString(
                        ARG_DESCRIPTION,
                        "No description provided."
                );

        long createdAt =
                args.getLong(
                        ARG_CREATED_AT,
                        0
                );

        int severity =
                args.getInt(
                        ARG_SEVERITY,
                        1
                );

        double latitude =
                args.getDouble(
                        ARG_LAT,
                        0
                );

        double longitude =
                args.getDouble(
                        ARG_LNG,
                        0
                );

        String eventId =
                args.getString(
                        ARG_EVENT_ID
                );


        // views

        TextView tvCategory =
                view.findViewById(
                        R.id.tv_summary_category
                );

        TextView tvStatus =
                view.findViewById(
                        R.id.tv_summary_status
                );

        MaterialCardView statusCard =
                view.findViewById(
                        R.id.card_summary_status
                );

        TextView tvReported =
                view.findViewById(
                        R.id.tv_summary_reported
                );

        TextView tvSeverity =
                view.findViewById(
                        R.id.tv_summary_severity
                );

        TextView tvDescription =
                view.findViewById(
                        R.id.tv_summary_description
                );

        TextView tvLocation =
                view.findViewById(
                        R.id.tv_summary_location
                );

        MaterialCardView statusInfoCard =
                view.findViewById(
                        R.id.card_summary_status_info
                );

        TextView tvStatusInfoTitle =
                view.findViewById(
                        R.id.tv_summary_status_info_title
                );

        TextView tvStatusInfoMessage =
                view.findViewById(
                        R.id.tv_summary_status_info_message
                );

        View btnViewOnMap =
                view.findViewById(
                        R.id.btn_view_report_on_map
                );


        // report content

        tvCategory.setText(
                category
        );

        tvStatus.setText(
                statusLabel
        );

        tvReported.setText(
                createdAt == 0
                        ? "Unknown date"
                        : ReportDisplayUtils
                          .formatRelativeTime(
                                  createdAt
                          )
        );

        tvSeverity.setText(
                severity + " / 10"
        );

        tvDescription.setText(
                description
        );


        // status colors

        int statusBackground =
                ContextCompat.getColor(
                        requireContext(),
                        ReportStatusUi
                                .getBackgroundColorRes(
                                        rawStatus
                                )
                );

        int statusText =
                ContextCompat.getColor(
                        requireContext(),
                        ReportStatusUi
                                .getTextColorRes(
                                        rawStatus
                                )
                );

        int statusStroke =
                ContextCompat.getColor(
                        requireContext(),
                        ReportStatusUi
                                .getStrokeColorRes(
                                        rawStatus
                                )
                );

        statusCard.setCardBackgroundColor(
                statusBackground
        );

        statusCard.setStrokeColor(
                statusStroke
        );

        tvStatus.setTextColor(
                statusText
        );


        // status explanation

        statusInfoCard.setCardBackgroundColor(
                statusBackground
        );

        statusInfoCard.setStrokeColor(
                statusStroke
        );

        tvStatusInfoTitle.setTextColor(
                statusText
        );

        tvStatusInfoTitle.setText(
                ReportStatusUi
                        .getExplanationTitle(
                                rawStatus
                        )
        );

        tvStatusInfoMessage.setText(
                ReportStatusUi
                        .getExplanationMessage(
                                rawStatus
                        )
        );


        // location

        loadAddress(
                latitude,
                longitude,
                tvLocation
        );


        // view on map

        boolean canViewOnMap =
                ReportStatusUi
                        .canViewOnMap(
                                rawStatus
                        );

        btnViewOnMap.setVisibility(
                canViewOnMap
                        ? View.VISIBLE
                        : View.GONE
        );

        if (canViewOnMap) {

            btnViewOnMap
                    .setOnClickListener(v -> {

                        if (
                                eventId == null
                                        || eventId.isEmpty()
                        ) {

                            Toast.makeText(
                                    requireContext(),
                                    "This report is not linked to an event",
                                    Toast.LENGTH_SHORT
                            ).show();

                            return;
                        }

                        Activity activity =
                                getActivity();

                        dismiss();


                        if (
                                activity
                                        instanceof MainActivity
                        ) {

                            ((MainActivity) activity)
                                    .openMapAtEvent(
                                            eventId
                                    );

                            return;
                        }


                        Intent intent =
                                new Intent(
                                        requireContext(),
                                        MainActivity.class
                                );

                        intent.putExtra(
                                MainActivity.EXTRA_OPEN_EVENT_ID,
                                eventId
                        );

                        intent.addFlags(
                                Intent.FLAG_ACTIVITY_CLEAR_TOP
                                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        );

                        startActivity(
                                intent
                        );

                        if (activity != null) {
                            activity.finish();
                        }
                    });
        }


        // close

        view.findViewById(
                R.id.btn_close_report_summary
        ).setOnClickListener(v ->
                dismiss()
        );
    }


    // address

    private void loadAddress(
            double latitude,
            double longitude,
            TextView target
    ) {

        if (
                latitude == 0
                        && longitude == 0
        ) {

            target.setText(
                    "Location unavailable"
            );

            return;
        }

        target.setText(
                "Loading location..."
        );

        new Thread(() -> {

            String addressText =
                    latitude
                            + ", "
                            + longitude;

            try {

                Geocoder geocoder =
                        new Geocoder(
                                requireContext(),
                                Locale.getDefault()
                        );

                List<Address> addresses =
                        geocoder.getFromLocation(
                                latitude,
                                longitude,
                                1
                        );

                if (
                        addresses != null
                                && !addresses.isEmpty()
                ) {

                    addressText =
                            addresses
                                    .get(0)
                                    .getAddressLine(0);
                }

            } catch (IOException ignored) {
            }

            String finalAddressText =
                    addressText;

            if (getActivity() == null) {
                return;
            }

            requireActivity()
                    .runOnUiThread(() -> {

                        if (isAdded()) {

                            target.setText(
                                    finalAddressText
                            );
                        }
                    });

        }).start();
    }


    // expand sheet

    @Override
    public void onStart() {
        super.onStart();

        Dialog dialog =
                getDialog();

        if (
                dialog
                        instanceof BottomSheetDialog
        ) {

            View bottomSheet =
                    ((BottomSheetDialog) dialog)
                            .findViewById(
                                    com.google.android.material
                                            .R.id
                                            .design_bottom_sheet
                            );

            if (bottomSheet != null) {

                BottomSheetBehavior<View> behavior =
                        BottomSheetBehavior
                                .from(
                                        bottomSheet
                                );

                behavior.setState(
                        BottomSheetBehavior
                                .STATE_EXPANDED
                );

                behavior.setSkipCollapsed(
                        true
                );
            }
        }
    }
}