package com.pkg.civicfix;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.slider.Slider;
import com.pkg.civicfix.model.Issue;

public class ReportFragment extends Fragment {
    private static String[] categories;

    static {
        Issue.Category[] cats = Issue.Category.values();
        categories = new String[cats.length];
        for (int i = 0; i < cats.length; i++) {
            categories[i] = cats[i].name();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_report, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Slider slider = view.findViewById(R.id.slider_severity);
        TextView tvSeverity = view.findViewById(R.id.tv_severity_value);

        slider.addOnChangeListener((s, value, fromUser) ->
                tvSeverity.setText(String.valueOf((int) value))
        );

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                categories
        );

        AutoCompleteTextView dropdown = view.findViewById(R.id.dropdown_category);
        dropdown.setAdapter(adapter);
        dropdown.setOnClickListener(v -> dropdown.showDropDown());
    }
}