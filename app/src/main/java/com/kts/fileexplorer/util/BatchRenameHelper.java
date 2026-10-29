package com.kts.fileexplorer.util;

import android.app.Activity;
import android.app.Dialog;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.kts.fileexplorer.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class that provides flexible batch renaming for selected files.
 * Supports renaming positions:
 * 1. Beginning (Prefix)
 * 2. End (Suffix)
 * 3. Center / Text (Find & Replace)
 * 4. Custom Base Pattern with Sequential Numbering
 */
public class BatchRenameHelper {

    public interface OnRenameCompleteListener {
        void onRenameComplete(int successCount, int failedCount);
    }

    private static final int MODE_BEGINNING = 0;
    private static final int MODE_END = 1;
    private static final int MODE_CENTER = 2;
    private static final int MODE_PATTERN = 3;

    public static void showDialog(Activity activity, List<File> files, OnRenameCompleteListener listener) {
        if (activity == null || activity.isFinishing() || files == null || files.isEmpty()) {
            return;
        }

        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_batch_rename, null);

        TextView tvCount = view.findViewById(R.id.tv_batch_rename_count);
        tvCount.setText(activity.getString(R.string.dialog_batch_rename_count, files.size()));

        RadioGroup rgModes = view.findViewById(R.id.rg_rename_modes);
        RadioButton rbBeginning = view.findViewById(R.id.rb_mode_beginning);
        RadioButton rbEnd = view.findViewById(R.id.rb_mode_end);
        RadioButton rbCenter = view.findViewById(R.id.rb_mode_center);
        RadioButton rbPattern = view.findViewById(R.id.rb_mode_pattern);

        View panelBeginning = view.findViewById(R.id.panel_mode_beginning);
        View panelEnd = view.findViewById(R.id.panel_mode_end);
        View panelCenter = view.findViewById(R.id.panel_mode_center);
        View panelPattern = view.findViewById(R.id.panel_mode_pattern);

        // Beginning panel views
        EditText etPrefix = view.findViewById(R.id.et_prefix_text);
        CheckBox cbPrefixNumbering = view.findViewById(R.id.cb_prefix_numbering);
        View layoutPrefixNumOpts = view.findViewById(R.id.layout_prefix_num_opts);
        EditText etPrefixStartNum = view.findViewById(R.id.et_prefix_start_num);

        // End panel views
        EditText etSuffix = view.findViewById(R.id.et_suffix_text);
        CheckBox cbSuffixNumbering = view.findViewById(R.id.cb_suffix_numbering);
        View layoutSuffixNumOpts = view.findViewById(R.id.layout_suffix_num_opts);
        EditText etSuffixStartNum = view.findViewById(R.id.et_suffix_start_num);

        // Center panel views
        EditText etFindText = view.findViewById(R.id.et_find_text);
        EditText etReplaceText = view.findViewById(R.id.et_replace_text);
        CheckBox cbCaseSensitive = view.findViewById(R.id.cb_case_sensitive);

        // Pattern panel views
        EditText etBaseName = view.findViewById(R.id.et_base_name);
        EditText etPatternStartNum = view.findViewById(R.id.et_pattern_start_num);
        Spinner spinnerDigits = view.findViewById(R.id.spinner_digits);
        Spinner spinnerSeparator = view.findViewById(R.id.spinner_separator);

        CheckBox cbKeepExtension = view.findViewById(R.id.cb_keep_extension);

        TextView tvPreviewLine1 = view.findViewById(R.id.tv_preview_line1);
        TextView tvPreviewLine2 = view.findViewById(R.id.tv_preview_line2);
        TextView tvPreviewMore = view.findViewById(R.id.tv_preview_more);

        MaterialButton btnCancel = view.findViewById(R.id.btn_cancel_rename);
        MaterialButton btnConfirm = view.findViewById(R.id.btn_confirm_rename);
        btnConfirm.setText(activity.getString(R.string.btn_execute_rename, files.size()));

        // Setup Spinners for digits and separators
        String[] digitOptions = new String[]{"1, 2, 3...", "01, 02, 03...", "001, 002, 003..."};
        ArrayAdapter<String> digitsAdapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, digitOptions);
        spinnerDigits.setAdapter(digitsAdapter);
        spinnerDigits.setSelection(1); // default 01, 02

        String[] separatorOptions = new String[]{"_ (Underscore)", "- (Hyphen)", "Space ( )", "None"};
        ArrayAdapter<String> separatorAdapter = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, separatorOptions);
        spinnerSeparator.setAdapter(separatorAdapter);
        spinnerSeparator.setSelection(0); // default "_"

        // Default base name suggestion based on first file
        String firstFileName = files.get(0).getName();
        int dotPos = firstFileName.lastIndexOf('.');
        String sampleBase = dotPos > 0 ? firstFileName.substring(0, dotPos) : firstFileName;
        etBaseName.setText(sampleBase);

        // Mode toggling logic
        final int[] currentMode = {MODE_BEGINNING};

        Runnable updatePreview = () -> {
            boolean keepExt = cbKeepExtension.isChecked();

            int countToShow = Math.min(files.size(), 2);
            for (int i = 0; i < countToShow; i++) {
                File f = files.get(i);
                String origName = f.getName();
                String newName = computeNewName(
                        origName,
                        f.isDirectory(),
                        currentMode[0],
                        etPrefix.getText().toString(),
                        cbPrefixNumbering.isChecked(),
                        parseStartNumber(etPrefixStartNum.getText().toString()),
                        etSuffix.getText().toString(),
                        cbSuffixNumbering.isChecked(),
                        parseStartNumber(etSuffixStartNum.getText().toString()),
                        etFindText.getText().toString(),
                        etReplaceText.getText().toString(),
                        cbCaseSensitive.isChecked(),
                        etBaseName.getText().toString(),
                        parseStartNumber(etPatternStartNum.getText().toString()),
                        spinnerDigits.getSelectedItemPosition(),
                        getSeparatorString(spinnerSeparator.getSelectedItemPosition()),
                        keepExt,
                        i
                );

                String previewText = origName + " → " + newName;
                if (i == 0) {
                    tvPreviewLine1.setText(previewText);
                } else {
                    tvPreviewLine2.setText(previewText);
                }
            }

            if (files.size() > 2) {
                tvPreviewMore.setVisibility(View.VISIBLE);
                tvPreviewMore.setText("... and " + (files.size() - 2) + " more files");
            } else if (files.size() == 2) {
                tvPreviewLine2.setVisibility(View.VISIBLE);
                tvPreviewMore.setVisibility(View.GONE);
            } else {
                tvPreviewLine2.setVisibility(View.GONE);
                tvPreviewMore.setVisibility(View.GONE);
            }
        };

        rgModes.setOnCheckedChangeListener((group, checkedId) -> {
            panelBeginning.setVisibility(View.GONE);
            panelEnd.setVisibility(View.GONE);
            panelCenter.setVisibility(View.GONE);
            panelPattern.setVisibility(View.GONE);

            if (checkedId == R.id.rb_mode_beginning) {
                currentMode[0] = MODE_BEGINNING;
                panelBeginning.setVisibility(View.VISIBLE);
            } else if (checkedId == R.id.rb_mode_end) {
                currentMode[0] = MODE_END;
                panelEnd.setVisibility(View.VISIBLE);
            } else if (checkedId == R.id.rb_mode_center) {
                currentMode[0] = MODE_CENTER;
                panelCenter.setVisibility(View.VISIBLE);
            } else if (checkedId == R.id.rb_mode_pattern) {
                currentMode[0] = MODE_PATTERN;
                panelPattern.setVisibility(View.VISIBLE);
            }
            updatePreview.run();
        });

        cbPrefixNumbering.setOnCheckedChangeListener((buttonView, isChecked) -> {
            layoutPrefixNumOpts.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            updatePreview.run();
        });

        cbSuffixNumbering.setOnCheckedChangeListener((buttonView, isChecked) -> {
            layoutSuffixNumOpts.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            updatePreview.run();
        });

        cbKeepExtension.setOnCheckedChangeListener((buttonView, isChecked) -> updatePreview.run());
        cbCaseSensitive.setOnCheckedChangeListener((buttonView, isChecked) -> updatePreview.run());

        TextWatcher previewWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePreview.run();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };

        etPrefix.addTextChangedListener(previewWatcher);
        etPrefixStartNum.addTextChangedListener(previewWatcher);
        etSuffix.addTextChangedListener(previewWatcher);
        etSuffixStartNum.addTextChangedListener(previewWatcher);
        etFindText.addTextChangedListener(previewWatcher);
        etReplaceText.addTextChangedListener(previewWatcher);
        etBaseName.addTextChangedListener(previewWatcher);
        etPatternStartNum.addTextChangedListener(previewWatcher);

        AdapterView.OnItemSelectedListener spinnerWatcher = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                updatePreview.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };

        spinnerDigits.setOnItemSelectedListener(spinnerWatcher);
        spinnerSeparator.setOnItemSelectedListener(spinnerWatcher);

        // Initial preview computation
        updatePreview.run();

        Dialog dialog = new MaterialAlertDialogBuilder(activity)
                .setView(view)
                .setCancelable(true)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            // Validation
            if (currentMode[0] == MODE_CENTER && TextUtils.isEmpty(etFindText.getText().toString())) {
                Toast.makeText(activity, R.string.toast_find_text_empty, Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentMode[0] == MODE_PATTERN && TextUtils.isEmpty(etBaseName.getText().toString().trim())) {
                Toast.makeText(activity, R.string.toast_base_name_empty, Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentMode[0] == MODE_BEGINNING && TextUtils.isEmpty(etPrefix.getText().toString()) && !cbPrefixNumbering.isChecked()) {
                Toast.makeText(activity, R.string.toast_no_changes_specified, Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentMode[0] == MODE_END && TextUtils.isEmpty(etSuffix.getText().toString()) && !cbSuffixNumbering.isChecked()) {
                Toast.makeText(activity, R.string.toast_no_changes_specified, Toast.LENGTH_SHORT).show();
                return;
            }

            dialog.dismiss();
            executeRename(
                    activity,
                    files,
                    currentMode[0],
                    etPrefix.getText().toString(),
                    cbPrefixNumbering.isChecked(),
                    parseStartNumber(etPrefixStartNum.getText().toString()),
                    etSuffix.getText().toString(),
                    cbSuffixNumbering.isChecked(),
                    parseStartNumber(etSuffixStartNum.getText().toString()),
                    etFindText.getText().toString(),
                    etReplaceText.getText().toString(),
                    cbCaseSensitive.isChecked(),
                    etBaseName.getText().toString(),
                    parseStartNumber(etPatternStartNum.getText().toString()),
                    spinnerDigits.getSelectedItemPosition(),
                    getSeparatorString(spinnerSeparator.getSelectedItemPosition()),
                    cbKeepExtension.isChecked(),
                    listener
            );
        });

        dialog.show();
    }

    private static String computeNewName(
            String originalName,
            boolean isDirectory,
            int mode,
            String prefix,
            boolean prefixNumbering,
            int prefixStartNum,
            String suffix,
            boolean suffixNumbering,
            int suffixStartNum,
            String findText,
            String replaceText,
            boolean caseSensitive,
            String basePattern,
            int patternStartNum,
            int digitsOption,
            String separator,
            boolean keepExtension,
            int index
    ) {
        String baseName = originalName;
        String extension = "";

        if (keepExtension && !isDirectory) {
            int dotIndex = originalName.lastIndexOf('.');
            if (dotIndex > 0) {
                baseName = originalName.substring(0, dotIndex);
                extension = originalName.substring(dotIndex);
            }
        }

        switch (mode) {
            case MODE_BEGINNING: {
                String numStr = prefixNumbering ? formatNumber(prefixStartNum + index, 1) + "_" : "";
                return (prefix != null ? prefix : "") + numStr + baseName + extension;
            }
            case MODE_END: {
                String numStr = suffixNumbering ? "_" + formatNumber(suffixStartNum + index, 1) : "";
                return baseName + (suffix != null ? suffix : "") + numStr + extension;
            }
            case MODE_CENTER: {
                String replacedBase = baseName;
                if (!TextUtils.isEmpty(findText)) {
                    if (caseSensitive) {
                        replacedBase = baseName.replace(findText, replaceText != null ? replaceText : "");
                    } else {
                        replacedBase = Pattern.compile(Pattern.quote(findText), Pattern.CASE_INSENSITIVE)
                                .matcher(baseName)
                                .replaceAll(Matcher.quoteReplacement(replaceText != null ? replaceText : ""));
                    }
                }
                return replacedBase + extension;
            }
            case MODE_PATTERN: {
                String numStr = formatNumber(patternStartNum + index, digitsOption);
                String cleanBase = basePattern != null ? basePattern.trim() : "";
                if (cleanBase.isEmpty()) {
                    return numStr + extension;
                } else {
                    return cleanBase + separator + numStr + extension;
                }
            }
            default:
                return originalName;
        }
    }

    private static void executeRename(
            Activity activity,
            List<File> files,
            int mode,
            String prefix,
            boolean prefixNumbering,
            int prefixStartNum,
            String suffix,
            boolean suffixNumbering,
            int suffixStartNum,
            String findText,
            String replaceText,
            boolean caseSensitive,
            String basePattern,
            int patternStartNum,
            int digitsOption,
            String separator,
            boolean keepExtension,
            OnRenameCompleteListener listener
    ) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            int successCount = 0;
            int failedCount = 0;

            // Generate target names for each file
            List<String> newNames = new ArrayList<>();
            for (int i = 0; i < files.size(); i++) {
                File f = files.get(i);
                String newName = computeNewName(
                        f.getName(),
                        f.isDirectory(),
                        mode,
                        prefix,
                        prefixNumbering,
                        prefixStartNum,
                        suffix,
                        suffixNumbering,
                        suffixStartNum,
                        findText,
                        replaceText,
                        caseSensitive,
                        basePattern,
                        patternStartNum,
                        digitsOption,
                        separator,
                        keepExtension,
                        i
                );
                newNames.add(newName);
            }

            // Perform rename operation
            for (int i = 0; i < files.size(); i++) {
                File originalFile = files.get(i);
                String newName = newNames.get(i);

                if (newName.equals(originalFile.getName())) {
                    successCount++;
                    continue;
                }

                File parentDir = originalFile.getParentFile();
                if (parentDir == null) {
                    failedCount++;
                    continue;
                }

                File destinationFile = new File(parentDir, newName);
                if (destinationFile.exists() && !destinationFile.equals(originalFile)) {
                    failedCount++;
                    continue;
                }

                boolean renamed = originalFile.renameTo(destinationFile);
                if (renamed) {
                    successCount++;
                } else {
                    failedCount++;
                }
            }

            final int finalSuccess = successCount;
            final int finalFailed = failedCount;

            activity.runOnUiThread(() -> {
                if (finalFailed == 0) {
                    String msg = activity.getString(R.string.toast_batch_rename_success, finalSuccess);
                    Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
                } else {
                    String msg = activity.getString(R.string.toast_batch_rename_failed, finalFailed, files.size());
                    Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
                }

                if (listener != null) {
                    listener.onRenameComplete(finalSuccess, finalFailed);
                }
            });
        });
    }

    private static String formatNumber(int number, int digitsOption) {
        if (digitsOption == 1) { // 01, 02
            return String.format("%02d", number);
        } else if (digitsOption == 2) { // 001, 002
            return String.format("%03d", number);
        } else { // 1, 2
            return String.valueOf(number);
        }
    }

    private static String getSeparatorString(int position) {
        switch (position) {
            case 1:
                return "-";
            case 2:
                return " ";
            case 3:
                return "";
            case 0:
            default:
                return "_";
        }
    }

    private static int parseStartNumber(String text) {
        try {
            int val = Integer.parseInt(text.trim());
            return Math.max(0, val);
        } catch (Exception e) {
            return 1;
        }
    }
}
