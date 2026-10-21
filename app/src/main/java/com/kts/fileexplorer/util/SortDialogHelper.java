package com.kts.fileexplorer.util;

import android.app.Dialog;
import android.content.Context;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.radiobutton.MaterialRadioButton;
import com.kts.fileexplorer.R;
import com.kts.fileexplorer.model.SortMode;

/**
 * Helper to display accessible Sort dialog with:
 * - Single-choice radio buttons for all 8 sort modes
 * - Checkbox allowing user to specify whether sorting applies only to the current location or globally
 * - Apply and Cancel action buttons
 */
public class SortDialogHelper {

    public interface OnSortAppliedListener {
        void onSortApplied(SortMode selectedMode, boolean isCurrentLocationOnly);
    }

    public static Dialog createSortDialog(Context context,
                                          SortMode currentMode,
                                          boolean initialCurrentOnly,
                                          String checkboxText,
                                          String checkboxHint,
                                          OnSortAppliedListener listener) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_sort, null, false);
        RadioGroup rgSort = dialogView.findViewById(R.id.rg_sort_options);
        MaterialCheckBox cbCurrentOnly = dialogView.findViewById(R.id.cb_apply_current_only);
        TextView tvHint = dialogView.findViewById(R.id.tv_checkbox_hint);

        cbCurrentOnly.setText(checkboxText);
        tvHint.setText(checkboxHint);
        cbCurrentOnly.setContentDescription(checkboxText + ". " + checkboxHint);
        cbCurrentOnly.setChecked(initialCurrentOnly);

        SortMode[] modes = SortMode.values();
        int density48dp = (int) (48 * context.getResources().getDisplayMetrics().density);

        for (SortMode mode : modes) {
            MaterialRadioButton rb = new MaterialRadioButton(context);
            rb.setId(View.generateViewId());
            rb.setText(mode.getTitleResId());
            rb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            rb.setMinHeight(density48dp);
            rb.setTag(mode);
            rb.setLayoutParams(new RadioGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            if (mode == currentMode) {
                rb.setChecked(true);
            }
            rgSort.addView(rb);
        }

        return new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.sort_dialog_title)
                .setView(dialogView)
                .setPositiveButton(R.string.btn_apply, (dialog, which) -> {
                    int checkedId = rgSort.getCheckedRadioButtonId();
                    SortMode selectedMode = currentMode;
                    if (checkedId != -1) {
                        RadioButton checkedRb = dialogView.findViewById(checkedId);
                        if (checkedRb != null && checkedRb.getTag() instanceof SortMode) {
                            selectedMode = (SortMode) checkedRb.getTag();
                        }
                    }
                    if (listener != null) {
                        listener.onSortApplied(selectedMode, cbCurrentOnly.isChecked());
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .create();
    }
}
