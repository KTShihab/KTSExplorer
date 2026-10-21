package com.kts.fileexplorer.util;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.webkit.MimeTypeMap;
import androidx.core.content.FileProvider;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.kts.fileexplorer.R;
import com.kts.fileexplorer.model.FileItem;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Controller for the Edit Submenu, Convert, Split, Trim, Save, and Progress Dialogs.
 */
public class EditActionHelper {

    public interface DialogCallback {
        void onActiveDialogChanged(Dialog dialog);
        void onFilesChanged();
    }

    private static class StepOption {
        final long millis;
        final String label;
        final int actionId;

        StepOption(long millis, String label, int actionId) {
            this.millis = millis;
            this.label = label;
            this.actionId = actionId;
        }
    }

    private static final StepOption[] STEP_OPTIONS = new StepOption[] {
            new StepOption(100L, "100 milliseconds", R.id.action_step_100ms),
            new StepOption(200L, "200 milliseconds", R.id.action_step_200ms),
            new StepOption(500L, "500 milliseconds", R.id.action_step_500ms),
            new StepOption(1000L, "1 second", R.id.action_step_1s),
            new StepOption(5000L, "5 seconds", R.id.action_step_5s),
            new StepOption(10000L, "10 seconds", R.id.action_step_10s),
            new StepOption(30000L, "30 seconds", R.id.action_step_30s),
            new StepOption(60000L, "1 minute", R.id.action_step_1m),
            new StepOption(300000L, "5 minutes", R.id.action_step_5m),
            new StepOption(600000L, "10 minutes", R.id.action_step_10m)
    };

    private static class PreviewOption {
        final long millis;
        final String label;
        final int actionId;

        PreviewOption(long millis, String label, int actionId) {
            this.millis = millis;
            this.label = label;
            this.actionId = actionId;
        }
    }

    private static final PreviewOption[] PREVIEW_OPTIONS = new PreviewOption[] {
            new PreviewOption(5000L, "5 seconds", R.id.action_preview_5s),
            new PreviewOption(10000L, "10 seconds", R.id.action_preview_10s),
            new PreviewOption(15000L, "15 seconds", R.id.action_preview_15s),
            new PreviewOption(20000L, "20 seconds", R.id.action_preview_20s),
            new PreviewOption(30000L, "30 seconds", R.id.action_preview_30s),
            new PreviewOption(60000L, "1 minute", R.id.action_preview_1m)
    };

    private static class SeekOption {
        final long millis;
        final String label;
        final int actionId;

        SeekOption(long millis, String label, int actionId) {
            this.millis = millis;
            this.label = label;
            this.actionId = actionId;
        }
    }

    private static final SeekOption[] SEEK_OPTIONS = new SeekOption[] {
            new SeekOption(5000L, "5 seconds", R.id.action_seek_5s),
            new SeekOption(10000L, "10 seconds", R.id.action_seek_10s),
            new SeekOption(15000L, "15 seconds", R.id.action_seek_15s),
            new SeekOption(20000L, "20 seconds", R.id.action_seek_20s),
            new SeekOption(30000L, "30 seconds", R.id.action_seek_30s),
            new SeekOption(60000L, "1 minute", R.id.action_seek_1m)
    };

    /**
     * Shows the Edit Submenu Bottom Sheet containing Convert, Split, and Trim options.
     */
    public static void showEditSubmenu(Context context, FileItem item, DialogCallback callback) {
        if (item.isVideo() && !item.isAudio()) {
            // For video files, directly open Convert Video to Audio dialog
            showConvertDialog(context, item, callback);
            return;
        }

        BottomSheetDialog bottomSheet = new BottomSheetDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_bottom_sheet_edit, null);
        bottomSheet.setContentView(view);

        TextView tvTitle = view.findViewById(R.id.tv_edit_sheet_title);
        String titleStr = context.getString(R.string.title_edit, item.getName());
        tvTitle.setText(titleStr);
        tvTitle.setContentDescription(titleStr);

        View actionConvert = view.findViewById(R.id.action_submenu_convert);
        View actionSplit = view.findViewById(R.id.action_submenu_split);
        View actionTrim = view.findViewById(R.id.action_submenu_trim);

        actionConvert.setOnClickListener(v -> {
            bottomSheet.dismiss();
            showConvertDialog(context, item, callback);
        });

        actionSplit.setOnClickListener(v -> {
            bottomSheet.dismiss();
            showSplitDialog(context, item, callback);
        });

        actionTrim.setOnClickListener(v -> {
            bottomSheet.dismiss();
            showTrimDialog(context, item, callback);
        });

        bottomSheet.show();
        if (callback != null) {
            callback.onActiveDialogChanged(bottomSheet);
        }
    }

    /**
     * Shows the Convert Dialog with Combo Boxes (Spinners) for Format, Bitrate, Sampling Rate, Channels.
     */
    public static void showConvertDialog(Context context, FileItem item, DialogCallback callback) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_convert_file, null);

        Spinner spinnerFormat = dialogView.findViewById(R.id.spinner_format);
        View audioContainer = dialogView.findViewById(R.id.container_audio_options);
        Spinner spinnerBitrate = dialogView.findViewById(R.id.spinner_bitrate);
        Spinner spinnerSampleRate = dialogView.findViewById(R.id.spinner_sample_rate);
        Spinner spinnerChannels = dialogView.findViewById(R.id.spinner_channels);
        View progressBar = dialogView.findViewById(R.id.progress_convert);
        if (progressBar != null) progressBar.setVisibility(View.GONE);

        String ext = item.getExtension().toLowerCase(Locale.ENGLISH);
        boolean isImage = ext.matches("jpg|jpeg|png|webp|bmp|gif");
        boolean isVideo = item.isVideo();

        if (isImage) {
            audioContainer.setVisibility(View.GONE);
            List<String> imageFormats = Arrays.asList("JPG", "PNG", "WEBP");
            ArrayAdapter<String> formatAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, imageFormats);
            spinnerFormat.setAdapter(formatAdapter);
        } else {
            audioContainer.setVisibility(View.VISIBLE);
            List<String> audioFormats = Arrays.asList("MP3", "WAV", "M4A (AAC)", "FLAC", "OGG");
            ArrayAdapter<String> formatAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, audioFormats);
            spinnerFormat.setAdapter(formatAdapter);

            List<String> bitrates = Arrays.asList("64 kbps", "128 kbps", "192 kbps", "256 kbps", "320 kbps");
            ArrayAdapter<String> bitrateAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, bitrates);
            spinnerBitrate.setAdapter(bitrateAdapter);
            spinnerBitrate.setSelection(2); // 192 kbps default

            List<String> sampleRates = Arrays.asList(
                    context.getString(R.string.auto_original),
                    "48000 Hz",
                    "44100 Hz",
                    "32000 Hz",
                    "22050 Hz",
                    "16000 Hz"
            );
            ArrayAdapter<String> sampleRateAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, sampleRates);
            spinnerSampleRate.setAdapter(sampleRateAdapter);
            spinnerSampleRate.setSelection(0); // Auto default

            List<String> channels = Arrays.asList(
                    context.getString(R.string.auto_original),
                    context.getString(R.string.channel_stereo),
                    context.getString(R.string.channel_mono)
            );
            ArrayAdapter<String> channelsAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, channels);
            spinnerChannels.setAdapter(channelsAdapter);
            spinnerChannels.setSelection(0); // Auto default
        }

        String dialogTitle = isVideo ?
                context.getString(R.string.title_convert_video_to_audio, item.getName()) :
                context.getString(R.string.title_convert, item.getName());

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(dialogTitle)
                .setView(dialogView)
                .setPositiveButton(R.string.btn_convert, (d, which) -> {
                    String selectedFormat = (String) spinnerFormat.getSelectedItem();
                    String targetExt;
                    if (isImage) {
                        targetExt = "." + selectedFormat.toLowerCase(Locale.ENGLISH);
                    } else {
                        if (selectedFormat.contains("MP3")) targetExt = ".mp3";
                        else if (selectedFormat.contains("WAV")) targetExt = ".wav";
                        else if (selectedFormat.contains("M4A")) targetExt = ".m4a";
                        else if (selectedFormat.contains("FLAC")) targetExt = ".flac";
                        else targetExt = ".ogg";
                    }

                    File srcFile = new File(item.getPath());
                    String baseName = item.getName();
                    int dotIdx = baseName.lastIndexOf('.');
                    if (dotIdx > 0) baseName = baseName.substring(0, dotIdx);
                    String defaultFileName = isVideo ?
                            baseName + "_audio" + targetExt :
                            baseName + "_converted" + targetExt;
                    File defaultFolder = srcFile.getParentFile() != null ? srcFile.getParentFile() : Environment.getExternalStorageDirectory();

                    int bitrate = 192;
                    try {
                        String bStr = (String) spinnerBitrate.getSelectedItem();
                        bitrate = Integer.parseInt(bStr.split(" ")[0]);
                    } catch (Exception ignored) {}

                    int sampleRate = 0; // 0 indicates Auto (keep source)
                    int srPos = spinnerSampleRate.getSelectedItemPosition();
                    if (srPos > 0) {
                        try {
                            String sStr = (String) spinnerSampleRate.getSelectedItem();
                            sampleRate = Integer.parseInt(sStr.split(" ")[0]);
                        } catch (Exception ignored) {}
                    }

                    int channels = 0; // 0 indicates Auto (keep source)
                    int chPos = spinnerChannels.getSelectedItemPosition();
                    if (chPos == 1) {
                        channels = 2; // Stereo
                    } else if (chPos == 2) {
                        channels = 1; // Mono
                    }

                    int finalBitrate = bitrate;
                    int finalSampleRate = sampleRate;
                    int finalChannels = channels;

                    // Step 2: Open Save Option Dialog
                    showSaveDialog(context, defaultFileName, defaultFolder, callback, (destFolder, chosenFileName) -> {
                        File outFile = new File(destFolder, chosenFileName);
                        AlertDialog progressDialog = showModalProgressDialog(context, context.getString(R.string.converting_file, item.getName()), callback);

                        if (isImage) {
                            AudioConverter.convertImage(srcFile, outFile, selectedFormat, 90, new AudioConverter.ConvertListener() {
                                @Override
                                public void onSuccess(File file) {
                                    dismissProgressAndNotify(context, progressDialog, file, callback, context.getString(R.string.conversion_success, file.getName()));
                                }

                                @Override
                                public void onError(String errorMessage) {
                                    progressDialog.dismiss();
                                    Toast.makeText(context, context.getString(R.string.conversion_failed, errorMessage), Toast.LENGTH_LONG).show();
                                }
                            });
                        } else {
                            AudioConverter.convertAudio(context, srcFile, outFile, selectedFormat, finalBitrate, finalSampleRate, finalChannels, new AudioConverter.ConvertListener() {
                                @Override
                                public void onSuccess(File file) {
                                    dismissProgressAndNotify(context, progressDialog, file, callback, context.getString(R.string.conversion_success, file.getName()));
                                }

                                @Override
                                public void onError(String errorMessage) {
                                    progressDialog.dismiss();
                                    Toast.makeText(context, context.getString(R.string.conversion_failed, errorMessage), Toast.LENGTH_LONG).show();
                                }

                                @Override
                                public void onProgress(int percent) {
                                    if (progressDialog != null && progressDialog.isShowing()) {
                                        progressDialog.setMessage(context.getString(R.string.converting_file, item.getName()) + " (" + percent + "%)");
                                    }
                                }
                            });
                        }
                    });
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .create();

        dialog.show();
        if (callback != null) {
            callback.onActiveDialogChanged(dialog);
        }
    }

    /**
     * Checks if a file is an audio or video media file suitable for batch audio conversion.
     */
    public static boolean isConvertibleMediaFile(File file) {
        if (file == null || !file.isFile()) return false;
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot >= name.length() - 1) return false;
        String ext = name.substring(dot + 1).toLowerCase(Locale.ENGLISH);
        return ext.matches("mp4|mkv|webm|avi|3gp|mov|flv|wmv|ts|m4v|mp3|wav|m4a|aac|flac|ogg|opus|wma|amr|m4r");
    }

    /**
     * Retrieves all convertible audio and video files inside the specified folder (non-recursive).
     */
    public static List<File> getConvertibleFiles(File folder) {
        List<File> result = new ArrayList<>();
        if (folder == null || !folder.isDirectory()) return result;
        File[] files = folder.listFiles();
        if (files == null) return result;
        for (File f : files) {
            if (isConvertibleMediaFile(f)) {
                result.add(f);
            }
        }
        result.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return result;
    }

    /**
     * Shows the Batch Convert Dialog for a selected folder.
     */
    public static void showBatchConvertDialog(Context context, File folder, DialogCallback callback) {
        if (folder == null || !folder.isDirectory()) return;

        List<File> mediaFiles = getConvertibleFiles(folder);
        if (mediaFiles.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.batch_convert_no_files, folder.getName()), Toast.LENGTH_LONG).show();
            return;
        }

        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_batch_convert, null);
        TextView tvSummary = dialogView.findViewById(R.id.tv_batch_summary);
        Spinner spinnerFormat = dialogView.findViewById(R.id.spinner_format);
        Spinner spinnerBitrate = dialogView.findViewById(R.id.spinner_bitrate);
        Spinner spinnerSampleRate = dialogView.findViewById(R.id.spinner_sample_rate);
        Spinner spinnerChannels = dialogView.findViewById(R.id.spinner_channels);
        android.widget.CheckBox cbSaveSubfolder = dialogView.findViewById(R.id.cb_save_subfolder);

        tvSummary.setText(context.getString(R.string.batch_convert_summary, mediaFiles.size(), folder.getName()));

        List<String> audioFormats = Arrays.asList("MP3", "WAV", "M4A (AAC)", "FLAC", "OGG");
        ArrayAdapter<String> formatAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, audioFormats);
        spinnerFormat.setAdapter(formatAdapter);

        List<String> bitrates = Arrays.asList("64 kbps", "128 kbps", "192 kbps", "256 kbps", "320 kbps");
        ArrayAdapter<String> bitrateAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, bitrates);
        spinnerBitrate.setAdapter(bitrateAdapter);
        spinnerBitrate.setSelection(2); // 192 kbps default

        List<String> sampleRates = Arrays.asList(
                context.getString(R.string.auto_original),
                "48000 Hz",
                "44100 Hz",
                "32000 Hz",
                "22050 Hz",
                "16000 Hz"
        );
        ArrayAdapter<String> sampleRateAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, sampleRates);
        spinnerSampleRate.setAdapter(sampleRateAdapter);
        spinnerSampleRate.setSelection(0); // Auto default

        List<String> channels = Arrays.asList(
                context.getString(R.string.auto_original),
                context.getString(R.string.channel_stereo),
                context.getString(R.string.channel_mono)
        );
        ArrayAdapter<String> channelsAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, channels);
        spinnerChannels.setAdapter(channelsAdapter);
        spinnerChannels.setSelection(0); // Auto default

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.batch_convert_title, folder.getName()))
                .setView(dialogView)
                .setPositiveButton(R.string.btn_convert, (d, which) -> {
                    String selectedFormat = (String) spinnerFormat.getSelectedItem();
                    int bitrate = 192;
                    try {
                        String bStr = (String) spinnerBitrate.getSelectedItem();
                        bitrate = Integer.parseInt(bStr.split(" ")[0]);
                    } catch (Exception ignored) {}

                    int sampleRate = 0;
                    int srPos = spinnerSampleRate.getSelectedItemPosition();
                    if (srPos > 0) {
                        try {
                            String sStr = (String) spinnerSampleRate.getSelectedItem();
                            sampleRate = Integer.parseInt(sStr.split(" ")[0]);
                        } catch (Exception ignored) {}
                    }

                    int ch = 0;
                    int chPos = spinnerChannels.getSelectedItemPosition();
                    if (chPos == 1) {
                        ch = 2; // Stereo
                    } else if (chPos == 2) {
                        ch = 1; // Mono
                    }

                    boolean saveInSubfolder = cbSaveSubfolder.isChecked();

                    startBatchConversion(
                            context,
                            folder,
                            mediaFiles,
                            selectedFormat,
                            bitrate,
                            sampleRate,
                            ch,
                            saveInSubfolder,
                            callback
                    );
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .create();

        dialog.show();
        if (callback != null) {
            callback.onActiveDialogChanged(dialog);
        }
    }

    /**
     * Executes batch conversion file-by-file sequentially with a cancellable modal progress dialog.
     */
    private static void startBatchConversion(
            Context context,
            File folder,
            List<File> mediaFiles,
            String selectedFormat,
            int bitrate,
            int sampleRate,
            int channels,
            boolean saveInSubfolder,
            DialogCallback callback
    ) {
        String targetExt;
        if (selectedFormat.contains("MP3")) targetExt = ".mp3";
        else if (selectedFormat.contains("WAV")) targetExt = ".wav";
        else if (selectedFormat.contains("M4A")) targetExt = ".m4a";
        else if (selectedFormat.contains("FLAC")) targetExt = ".flac";
        else targetExt = ".ogg";

        File destFolder = saveInSubfolder ? new File(folder, "Converted_Files") : folder;
        if (saveInSubfolder && !destFolder.exists()) {
            destFolder.mkdirs();
        }

        AtomicBoolean isCancelled = new AtomicBoolean(false);

        FrameLayout container = new FrameLayout(context);
        ProgressBar pb = new ProgressBar(context);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.gravity = Gravity.CENTER;
        container.addView(pb, lp);
        container.setPadding(0, 36, 0, 36);

        AlertDialog progressDialog = new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.batch_convert_progress_title, 1, mediaFiles.size()))
                .setMessage(context.getString(R.string.batch_convert_file_msg, mediaFiles.get(0).getName(), 0))
                .setView(container)
                .setCancelable(false)
                .setNegativeButton(R.string.btn_cancel, (d, which) -> {
                    isCancelled.set(true);
                })
                .create();

        progressDialog.show();
        if (callback != null) {
            callback.onActiveDialogChanged(progressDialog);
        }

        int total = mediaFiles.size();
        int[] index = new int[]{0};
        int[] successCount = new int[]{0};
        List<File> convertedFiles = new ArrayList<>();

        Runnable[] processNext = new Runnable[1];
        processNext[0] = () -> {
            if (isCancelled.get() || index[0] >= total) {
                if (progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
                if (!convertedFiles.isEmpty()) {
                    String[] paths = new String[convertedFiles.size()];
                    for (int k = 0; k < convertedFiles.size(); k++) {
                        paths[k] = convertedFiles.get(k).getAbsolutePath();
                    }
                    try {
                        MediaScannerConnection.scanFile(context.getApplicationContext(), paths, null, null);
                    } catch (Exception ignored) {}
                }
                if (callback != null) {
                    callback.onFilesChanged();
                }
                String message = isCancelled.get() ?
                        context.getString(R.string.batch_convert_stopped, successCount[0]) :
                        context.getString(R.string.batch_convert_completed, successCount[0]);
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                return;
            }

            int currentIdx = index[0];
            File srcFile = mediaFiles.get(currentIdx);
            String baseName = srcFile.getName();
            int dotIdx = baseName.lastIndexOf('.');
            if (dotIdx > 0) baseName = baseName.substring(0, dotIdx);

            File outFile;
            if (destFolder.equals(folder)) {
                outFile = new File(destFolder, baseName + "_converted" + targetExt);
            } else {
                outFile = new File(destFolder, baseName + targetExt);
                if (outFile.getAbsolutePath().equals(srcFile.getAbsolutePath())) {
                    outFile = new File(destFolder, baseName + "_converted" + targetExt);
                }
            }

            if (progressDialog.isShowing()) {
                progressDialog.setTitle(context.getString(R.string.batch_convert_progress_title, currentIdx + 1, total));
                progressDialog.setMessage(context.getString(R.string.batch_convert_file_msg, srcFile.getName(), 0));
            }

            AudioConverter.convertAudio(context, srcFile, outFile, selectedFormat, bitrate, sampleRate, channels, new AudioConverter.ConvertListener() {
                @Override
                public boolean isCancelled() {
                    return isCancelled.get();
                }

                @Override
                public void onSuccess(File file) {
                    successCount[0]++;
                    convertedFiles.add(file);
                    index[0]++;
                    processNext[0].run();
                }

                @Override
                public void onError(String errorMessage) {
                    index[0]++;
                    processNext[0].run();
                }

                @Override
                public void onProgress(int percent) {
                    if (progressDialog.isShowing() && !isCancelled.get()) {
                        progressDialog.setMessage(context.getString(R.string.batch_convert_file_msg, srcFile.getName(), percent));
                    }
                }
            });
        };

        processNext[0].run();
    }

    /**
     * Shows the Split Dialog.
     */
    public static void showSplitDialog(Context context, FileItem item, DialogCallback callback) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_split_file, null);

        Spinner spinnerMethod = dialogView.findViewById(R.id.spinner_split_method);
        TextView tvLabelValue = dialogView.findViewById(R.id.tv_label_split_value);
        EditText etValue = dialogView.findViewById(R.id.et_split_value);
        View progressBar = dialogView.findViewById(R.id.progress_split);
        if (progressBar != null) progressBar.setVisibility(View.GONE);

        String ext = item.getExtension().toLowerCase(Locale.ENGLISH);
        boolean isMedia = ext.matches("mp3|m4a|aac|wav|flac|ogg|opus|mp4|mkv|3gp|webm");

        List<String> methods;
        if (isMedia) {
            methods = Arrays.asList(
                    context.getString(R.string.split_by_duration),
                    context.getString(R.string.split_by_size),
                    context.getString(R.string.split_into_equal_parts)
            );
        } else {
            methods = Arrays.asList(
                    context.getString(R.string.split_by_size),
                    context.getString(R.string.split_into_equal_parts)
            );
        }

        ArrayAdapter<String> methodAdapter = new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, methods);
        spinnerMethod.setAdapter(methodAdapter);

        spinnerMethod.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = methods.get(position);
                if (selected.equals(context.getString(R.string.split_by_duration))) {
                    tvLabelValue.setText(R.string.label_split_duration);
                    etValue.setHint("60");
                    etValue.setText("60");
                } else if (selected.equals(context.getString(R.string.split_by_size))) {
                    tvLabelValue.setText(R.string.label_split_size);
                    etValue.setHint("10");
                    etValue.setText("10");
                } else {
                    tvLabelValue.setText(R.string.label_num_parts);
                    etValue.setHint("2");
                    etValue.setText("2");
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.title_split, item.getName()))
                .setView(dialogView)
                .setPositiveButton(R.string.btn_split, (d, which) -> {
                    String inputStr = etValue.getText().toString().trim();
                    if (inputStr.isEmpty()) return;

                    int value;
                    try {
                        value = Integer.parseInt(inputStr);
                        if (value <= 0) return;
                    } catch (Exception e) {
                        return;
                    }

                    File srcFile = new File(item.getPath());
                    File defaultFolder = srcFile.getParentFile() != null ? srcFile.getParentFile() : Environment.getExternalStorageDirectory();
                    String selectedMethod = (String) spinnerMethod.getSelectedItem();

                    showSaveDialog(context, item.getName(), defaultFolder, callback, (destFolder, chosenFileName) -> {
                        AlertDialog progressDialog = showModalProgressDialog(context, context.getString(R.string.splitting_file, item.getName()), callback);
                        MediaSplitter.SplitListener splitListener = new MediaSplitter.SplitListener() {
                            @Override
                            public void onSuccess(List<File> outputFiles) {
                                File first = (outputFiles != null && !outputFiles.isEmpty()) ? outputFiles.get(0) : null;
                                dismissProgressAndNotify(context, progressDialog, first, callback, context.getString(R.string.split_success, outputFiles != null ? outputFiles.size() : 0));
                            }

                            @Override
                            public void onError(String errorMessage) {
                                progressDialog.dismiss();
                                Toast.makeText(context, context.getString(R.string.split_failed, errorMessage), Toast.LENGTH_LONG).show();
                            }
                        };

                        if (selectedMethod.equals(context.getString(R.string.split_by_duration))) {
                            MediaSplitter.splitMediaByDuration(srcFile, value, splitListener);
                        } else if (selectedMethod.equals(context.getString(R.string.split_by_size))) {
                            long sizeBytes = (long) value * 1024 * 1024;
                            MediaSplitter.splitFileBySize(srcFile, sizeBytes, splitListener);
                        } else {
                            MediaSplitter.splitFileByParts(srcFile, value, splitListener);
                        }
                    });
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .create();

        dialog.show();
        if (callback != null) {
            callback.onActiveDialogChanged(dialog);
        }
    }

    /**
     * Shows the Trim Dialog with Start and End marks, hours/mins/secs/millis inputs,
     * Increase/Decrease buttons, and Screen Reader Custom Action Menus for setting step interval.
     */
    public static void showTrimDialog(Context context, FileItem item, DialogCallback callback) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_trim_media, null);

        // Start time views
        EditText etStartHh = dialogView.findViewById(R.id.et_start_hh);
        EditText etStartMm = dialogView.findViewById(R.id.et_start_mm);
        EditText etStartSs = dialogView.findViewById(R.id.et_start_ss);
        EditText etStartMs = dialogView.findViewById(R.id.et_start_ms);
        Button btnStartDec = dialogView.findViewById(R.id.btn_start_decrease);
        Button btnStartStep = dialogView.findViewById(R.id.btn_start_step);
        Button btnStartInc = dialogView.findViewById(R.id.btn_start_increase);
        MaterialButton btnPlayStart = dialogView.findViewById(R.id.btn_play_start);

        // End time views
        EditText etEndHh = dialogView.findViewById(R.id.et_end_hh);
        EditText etEndMm = dialogView.findViewById(R.id.et_end_mm);
        EditText etEndSs = dialogView.findViewById(R.id.et_end_ss);
        EditText etEndMs = dialogView.findViewById(R.id.et_end_ms);
        Button btnEndDec = dialogView.findViewById(R.id.btn_end_decrease);
        Button btnEndStep = dialogView.findViewById(R.id.btn_end_step);
        Button btnEndInc = dialogView.findViewById(R.id.btn_end_increase);
        MaterialButton btnPlayEnd = dialogView.findViewById(R.id.btn_play_end);

        // Selection preview views (Rewind, Play/Pause, Fast-Forward)
        MaterialButton btnPreviewRewind = dialogView.findViewById(R.id.btn_preview_rewind);
        MaterialButton btnPlaySelection = dialogView.findViewById(R.id.btn_play_selection);
        MaterialButton btnPreviewForward = dialogView.findViewById(R.id.btn_preview_forward);

        // Ensure TalkBack announces column labels when edit texts are focused
        setupEditTextAccessibility(etStartHh, context.getString(R.string.desc_start_hours));
        setupEditTextAccessibility(etStartMm, context.getString(R.string.desc_start_minutes));
        setupEditTextAccessibility(etStartSs, context.getString(R.string.desc_start_seconds));
        setupEditTextAccessibility(etStartMs, context.getString(R.string.desc_start_millis));
        setupEditTextAccessibility(etEndHh, context.getString(R.string.desc_end_hours));
        setupEditTextAccessibility(etEndMm, context.getString(R.string.desc_end_minutes));
        setupEditTextAccessibility(etEndSs, context.getString(R.string.desc_end_seconds));
        setupEditTextAccessibility(etEndMs, context.getString(R.string.desc_end_millis));

        // Preview player state
        final int PREVIEW_MODE_NONE = 0;
        final int PREVIEW_MODE_START = 1;
        final int PREVIEW_MODE_END = 2;
        final int PREVIEW_MODE_SELECTION = 3;
        final int[] activePreviewMode = new int[] { PREVIEW_MODE_NONE };
        final boolean[] isSelectionPaused = new boolean[] { false };

        final long[] startPreviewDuration = new long[] { 5000L };
        final long[] endPreviewDuration = new long[] { 5000L };
        final long[] selectionSeekStep = new long[] { 10000L }; // Default 10 seconds

        final MediaPlayer[] previewPlayer = new MediaPlayer[1];
        final Handler previewHandler = new Handler(Looper.getMainLooper());
        final Runnable[] stopRunnable = new Runnable[1];

        final Runnable checkSelectionPositionRunnable = new Runnable() {
            @Override
            public void run() {
                if (activePreviewMode[0] == PREVIEW_MODE_SELECTION && previewPlayer[0] != null) {
                    try {
                        if (previewPlayer[0].isPlaying()) {
                            long curEnd = getTimeFieldsMs(etEndHh, etEndMm, etEndSs, etEndMs);
                            long currentPos = previewPlayer[0].getCurrentPosition();
                            if (currentPos >= curEnd) {
                                if (stopRunnable[0] != null) {
                                    previewHandler.removeCallbacks(stopRunnable[0]);
                                    stopRunnable[0] = null;
                                }
                                if (previewPlayer[0] != null) {
                                    try {
                                        if (previewPlayer[0].isPlaying()) previewPlayer[0].stop();
                                        previewPlayer[0].release();
                                    } catch (Exception ignored) {}
                                    previewPlayer[0] = null;
                                }
                                activePreviewMode[0] = PREVIEW_MODE_NONE;
                                isSelectionPaused[0] = false;
                                if (btnPlaySelection != null) {
                                    btnPlaySelection.setText(R.string.btn_play_selection);
                                    btnPlaySelection.setContentDescription(context.getString(R.string.desc_play_selection));
                                    btnPlaySelection.setIconResource(R.drawable.ic_action_play);
                                    btnPlaySelection.announceForAccessibility(context.getString(R.string.selection_playback_completed));
                                }
                                return;
                            }
                        }
                        previewHandler.postDelayed(this, 100);
                    } catch (Exception e) {
                        activePreviewMode[0] = PREVIEW_MODE_NONE;
                        isSelectionPaused[0] = false;
                    }
                }
            }
        };

        Runnable updateButtons = () -> {
            boolean isStartPlaying = (activePreviewMode[0] == PREVIEW_MODE_START);
            boolean isEndPlaying = (activePreviewMode[0] == PREVIEW_MODE_END);
            boolean isSelectionPlaying = (activePreviewMode[0] == PREVIEW_MODE_SELECTION && !isSelectionPaused[0]);

            updatePreviewButton(context, btnPlayStart, true, startPreviewDuration[0], isStartPlaying);
            updatePreviewButton(context, btnPlayEnd, false, endPreviewDuration[0], isEndPlaying);

            if (btnPlaySelection != null) {
                if (isSelectionPlaying) {
                    btnPlaySelection.setText(R.string.btn_pause);
                    btnPlaySelection.setContentDescription(context.getString(R.string.desc_stop_selection));
                    btnPlaySelection.setIconResource(R.drawable.ic_pause);
                } else {
                    btnPlaySelection.setText(R.string.btn_play_selection);
                    btnPlaySelection.setContentDescription(context.getString(R.string.desc_play_selection));
                    btnPlaySelection.setIconResource(R.drawable.ic_action_play);
                }
            }

            String seekLabel = getPreviewDurationLabel(selectionSeekStep[0]);
            if (btnPreviewRewind != null) {
                btnPreviewRewind.setText(context.getString(R.string.btn_rewind_fmt, seekLabel));
                btnPreviewRewind.setContentDescription(context.getString(R.string.desc_preview_rewind, seekLabel));
            }
            if (btnPreviewForward != null) {
                btnPreviewForward.setText(context.getString(R.string.btn_forward_fmt, seekLabel));
                btnPreviewForward.setContentDescription(context.getString(R.string.desc_preview_forward, seekLabel));
            }
        };

        Runnable stopPlayback = () -> {
            if (stopRunnable[0] != null) {
                previewHandler.removeCallbacks(stopRunnable[0]);
                stopRunnable[0] = null;
            }
            previewHandler.removeCallbacks(checkSelectionPositionRunnable);
            if (previewPlayer[0] != null) {
                try {
                    if (previewPlayer[0].isPlaying()) {
                        previewPlayer[0].stop();
                    }
                    previewPlayer[0].release();
                } catch (Exception ignored) {}
                previewPlayer[0] = null;
            }
            activePreviewMode[0] = PREVIEW_MODE_NONE;
            isSelectionPaused[0] = false;
            updateButtons.run();
        };

        updateButtons.run();

        setupPreviewButtonAccessibility(context, btnPlayStart, true, startPreviewDuration, updateButtons);
        setupPreviewButtonAccessibility(context, btnPlayEnd, false, endPreviewDuration, updateButtons);
        setupSeekButtonAccessibility(context, btnPreviewRewind, true, selectionSeekStep, updateButtons);
        setupSeekButtonAccessibility(context, btnPreviewForward, false, selectionSeekStep, updateButtons);

        if (btnPlayStart != null) {
            btnPlayStart.setOnClickListener(v -> {
                if (activePreviewMode[0] != PREVIEW_MODE_NONE) {
                    stopPlayback.run();
                    return;
                }

                long startMs = getTimeFieldsMs(etStartHh, etStartMm, etStartSs, etStartMs);
                long endMs = getTimeFieldsMs(etEndHh, etEndMm, etEndSs, etEndMs);
                if (startMs >= endMs) {
                    Toast.makeText(context, "Start time must be less than End time", Toast.LENGTH_SHORT).show();
                    return;
                }

                long playDuration = Math.min(startPreviewDuration[0], endMs - startMs);
                if (playDuration <= 0) playDuration = 1000L;

                try {
                    MediaPlayer player = new MediaPlayer();
                    player.setDataSource(item.getPath());
                    player.prepare();
                    player.seekTo((int) startMs);
                    player.setOnCompletionListener(mp -> stopPlayback.run());
                    player.start();
                    previewPlayer[0] = player;
                    activePreviewMode[0] = PREVIEW_MODE_START;

                    updateButtons.run();

                    stopRunnable[0] = stopPlayback;
                    previewHandler.postDelayed(stopPlayback, playDuration);
                } catch (Exception e) {
                    Toast.makeText(context, "Playback error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    stopPlayback.run();
                }
            });
        }

        if (btnPlayEnd != null) {
            btnPlayEnd.setOnClickListener(v -> {
                if (activePreviewMode[0] != PREVIEW_MODE_NONE) {
                    stopPlayback.run();
                    return;
                }

                long endMs = getTimeFieldsMs(etEndHh, etEndMm, etEndSs, etEndMs);
                long previewStart = Math.max(0, endMs - endPreviewDuration[0]);
                long playDuration = endMs - previewStart;
                if (playDuration <= 0) playDuration = 1000L;

                try {
                    MediaPlayer player = new MediaPlayer();
                    player.setDataSource(item.getPath());
                    player.prepare();
                    player.seekTo((int) previewStart);
                    player.setOnCompletionListener(mp -> stopPlayback.run());
                    player.start();
                    previewPlayer[0] = player;
                    activePreviewMode[0] = PREVIEW_MODE_END;

                    updateButtons.run();

                    stopRunnable[0] = stopPlayback;
                    previewHandler.postDelayed(stopPlayback, playDuration);
                } catch (Exception e) {
                    Toast.makeText(context, "Playback error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    stopPlayback.run();
                }
            });
        }

        if (btnPlaySelection != null) {
            btnPlaySelection.setOnClickListener(v -> {
                long startMs = getTimeFieldsMs(etStartHh, etStartMm, etStartSs, etStartMs);
                long endMs = getTimeFieldsMs(etEndHh, etEndMm, etEndSs, etEndMs);
                if (startMs >= endMs) {
                    Toast.makeText(context, "Start time must be less than End time", Toast.LENGTH_SHORT).show();
                    return;
                }

                // If currently playing in SELECTION mode, pause
                if (activePreviewMode[0] == PREVIEW_MODE_SELECTION && !isSelectionPaused[0] && previewPlayer[0] != null) {
                    try {
                        previewPlayer[0].pause();
                        isSelectionPaused[0] = true;
                        previewHandler.removeCallbacks(checkSelectionPositionRunnable);
                        updateButtons.run();
                        btnPlaySelection.announceForAccessibility(context.getString(R.string.media_paused));
                    } catch (Exception e) {
                        stopPlayback.run();
                    }
                    return;
                }

                // If currently paused in SELECTION mode, resume
                if (activePreviewMode[0] == PREVIEW_MODE_SELECTION && isSelectionPaused[0] && previewPlayer[0] != null) {
                    try {
                        long currentPos = previewPlayer[0].getCurrentPosition();
                        if (currentPos < startMs || currentPos >= endMs) {
                            previewPlayer[0].seekTo((int) startMs);
                        }
                        previewPlayer[0].start();
                        isSelectionPaused[0] = false;
                        updateButtons.run();
                        previewHandler.removeCallbacks(checkSelectionPositionRunnable);
                        previewHandler.post(checkSelectionPositionRunnable);
                        btnPlaySelection.announceForAccessibility(context.getString(R.string.media_playing_selection));
                    } catch (Exception e) {
                        stopPlayback.run();
                    }
                    return;
                }

                // Fresh start of selection playback
                stopPlayback.run();
                try {
                    MediaPlayer player = new MediaPlayer();
                    player.setDataSource(item.getPath());
                    player.prepare();
                    player.seekTo((int) startMs);
                    player.setOnCompletionListener(mp -> {
                        stopPlayback.run();
                        if (btnPlaySelection != null) {
                            btnPlaySelection.announceForAccessibility(context.getString(R.string.selection_playback_completed));
                        }
                    });
                    player.start();
                    previewPlayer[0] = player;
                    activePreviewMode[0] = PREVIEW_MODE_SELECTION;
                    isSelectionPaused[0] = false;
                    updateButtons.run();

                    previewHandler.removeCallbacks(checkSelectionPositionRunnable);
                    previewHandler.post(checkSelectionPositionRunnable);

                    btnPlaySelection.announceForAccessibility(context.getString(R.string.media_playing_selection));
                } catch (Exception e) {
                    Toast.makeText(context, "Playback error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    stopPlayback.run();
                }
            });
        }

        if (btnPreviewRewind != null) {
            btnPreviewRewind.setOnClickListener(v -> {
                long startMs = getTimeFieldsMs(etStartHh, etStartMm, etStartSs, etStartMs);
                long endMs = getTimeFieldsMs(etEndHh, etEndMm, etEndSs, etEndMs);
                if (startMs >= endMs) {
                    Toast.makeText(context, "Start time must be less than End time", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (previewPlayer[0] == null || activePreviewMode[0] != PREVIEW_MODE_SELECTION) {
                    if (btnPlaySelection != null) btnPlaySelection.performClick();
                    return;
                }

                try {
                    long currentPos = previewPlayer[0].getCurrentPosition();
                    long step = selectionSeekStep[0];
                    long target = Math.max(startMs, currentPos - step);
                    previewPlayer[0].seekTo((int) target);

                    long offsetFromStart = target - startMs;
                    String announcement = context.getString(R.string.btn_rewind_fmt, getPreviewDurationLabel(step)) + ", " + formatTimeClock(offsetFromStart);
                    btnPreviewRewind.announceForAccessibility(announcement);
                    Toast.makeText(context, announcement, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    stopPlayback.run();
                }
            });
        }

        if (btnPreviewForward != null) {
            btnPreviewForward.setOnClickListener(v -> {
                long startMs = getTimeFieldsMs(etStartHh, etStartMm, etStartSs, etStartMs);
                long endMs = getTimeFieldsMs(etEndHh, etEndMm, etEndSs, etEndMs);
                if (startMs >= endMs) {
                    Toast.makeText(context, "Start time must be less than End time", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (previewPlayer[0] == null || activePreviewMode[0] != PREVIEW_MODE_SELECTION) {
                    if (btnPlaySelection != null) btnPlaySelection.performClick();
                    return;
                }

                try {
                    long currentPos = previewPlayer[0].getCurrentPosition();
                    long step = selectionSeekStep[0];
                    long target = currentPos + step;

                    if (target >= endMs) {
                        stopPlayback.run();
                        String msg = context.getString(R.string.reached_selection_end);
                        btnPreviewForward.announceForAccessibility(msg);
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
                    } else {
                        previewPlayer[0].seekTo((int) target);
                        long offsetFromStart = target - startMs;
                        String announcement = context.getString(R.string.btn_forward_fmt, getPreviewDurationLabel(step)) + ", " + formatTimeClock(offsetFromStart);
                        btnPreviewForward.announceForAccessibility(announcement);
                        Toast.makeText(context, announcement, Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    stopPlayback.run();
                }
            });
        }

        // Live playback adjustment listeners
        TimeAdjustCallback onStartAdjusted = updatedMs -> {
            if (activePreviewMode[0] == PREVIEW_MODE_START && previewPlayer[0] != null) {
                try {
                    previewPlayer[0].seekTo((int) updatedMs);
                    if (!previewPlayer[0].isPlaying()) {
                        previewPlayer[0].start();
                    }
                    long curEnd = getTimeFieldsMs(etEndHh, etEndMm, etEndSs, etEndMs);
                    long playDuration = Math.min(startPreviewDuration[0], Math.max(1000L, curEnd - updatedMs));
                    if (stopRunnable[0] != null) {
                        previewHandler.removeCallbacks(stopRunnable[0]);
                    }
                    stopRunnable[0] = stopPlayback;
                    previewHandler.postDelayed(stopPlayback, playDuration);
                } catch (Exception ignored) {}
            } else if (activePreviewMode[0] == PREVIEW_MODE_SELECTION && previewPlayer[0] != null) {
                try {
                    if (previewPlayer[0].getCurrentPosition() < updatedMs) {
                        previewPlayer[0].seekTo((int) updatedMs);
                    }
                } catch (Exception ignored) {}
            }
        };

        TimeAdjustCallback onEndAdjusted = updatedMs -> {
            if (activePreviewMode[0] == PREVIEW_MODE_END && previewPlayer[0] != null) {
                long previewStart = Math.max(0, updatedMs - endPreviewDuration[0]);
                long playDuration = updatedMs - previewStart;
                if (playDuration <= 0) playDuration = 1000L;
                try {
                    previewPlayer[0].seekTo((int) previewStart);
                    if (!previewPlayer[0].isPlaying()) {
                        previewPlayer[0].start();
                    }
                    if (stopRunnable[0] != null) {
                        previewHandler.removeCallbacks(stopRunnable[0]);
                    }
                    stopRunnable[0] = stopPlayback;
                    previewHandler.postDelayed(stopPlayback, playDuration);
                } catch (Exception ignored) {}
            } else if (activePreviewMode[0] == PREVIEW_MODE_SELECTION && previewPlayer[0] != null) {
                try {
                    if (previewPlayer[0].getCurrentPosition() >= updatedMs) {
                        stopPlayback.run();
                    }
                } catch (Exception ignored) {}
            }
        };

        // Determine total duration
        long totalDurationMs = 60000L; // default fallback 1 min
        try {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            mmr.setDataSource(item.getPath());
            String dur = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (dur != null) {
                totalDurationMs = Long.parseLong(dur);
            }
            mmr.release();
        } catch (Exception ignored) {}

        final long maxDuration = totalDurationMs;

        // Initial values: Start = 0, End = total duration
        setTimeFields(etStartHh, etStartMm, etStartSs, etStartMs, 0);
        setTimeFields(etEndHh, etEndMm, etEndSs, etEndMs, maxDuration);

        // Step holders (default 1000ms = 1s)
        final long[] startStepMs = new long[] { 1000L };
        final long[] endStepMs = new long[] { 1000L };

        updateStepButtonLabel(btnStartStep, btnStartDec, btnStartInc, startStepMs[0]);
        updateStepButtonLabel(btnEndStep, btnEndDec, btnEndInc, endStepMs[0]);

        // Attach Accessibility Custom Actions and Click/LongClick to Increase/Decrease buttons + Live adjustment
        setupTimeAdjustControls(context, etStartHh, etStartMm, etStartSs, etStartMs, btnStartDec, btnStartStep, btnStartInc, startStepMs, maxDuration, onStartAdjusted);
        setupTimeAdjustControls(context, etEndHh, etEndMm, etEndSs, etEndMs, btnEndDec, btnEndStep, btnEndInc, endStepMs, maxDuration, onEndAdjusted);

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.title_trim, item.getName()))
                .setView(dialogView)
                .setPositiveButton(R.string.btn_trim, (d, which) -> {
                    stopPlayback.run();
                    long startMs = getTimeFieldsMs(etStartHh, etStartMm, etStartSs, etStartMs);
                    long endMs = getTimeFieldsMs(etEndHh, etEndMm, etEndSs, etEndMs);

                    if (startMs >= endMs) {
                        Toast.makeText(context, "Start time must be less than End time", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    File srcFile = new File(item.getPath());
                    String baseName = item.getName();
                    String ext = "";
                    int dotIdx = baseName.lastIndexOf('.');
                    if (dotIdx > 0) {
                        ext = baseName.substring(dotIdx);
                        baseName = baseName.substring(0, dotIdx);
                    }
                    String defaultFileName = baseName + "_trimmed" + ext;
                    File defaultFolder = srcFile.getParentFile() != null ? srcFile.getParentFile() : Environment.getExternalStorageDirectory();

                    showSaveDialog(context, defaultFileName, defaultFolder, callback, (destFolder, chosenFileName) -> {
                        File outFile = new File(destFolder, chosenFileName);
                        AlertDialog progressDialog = showModalProgressDialog(context, context.getString(R.string.trimming_file, item.getName()), callback);

                        AudioConverter.trimAudio(context, srcFile, outFile, startMs, endMs, new AudioConverter.ConvertListener() {
                            @Override
                            public void onSuccess(File resultFile) {
                                dismissProgressAndNotify(context, progressDialog, resultFile, callback, context.getString(R.string.trim_success, resultFile.getName()));
                            }

                            @Override
                            public void onError(String errorMessage) {
                                progressDialog.dismiss();
                                Toast.makeText(context, context.getString(R.string.trim_failed, errorMessage), Toast.LENGTH_LONG).show();
                            }
                        });
                    });
                })
                .setNeutralButton(R.string.btn_delete_marked, (d, which) -> {
                    stopPlayback.run();
                    long startMs = getTimeFieldsMs(etStartHh, etStartMm, etStartSs, etStartMs);
                    long endMs = getTimeFieldsMs(etEndHh, etEndMm, etEndSs, etEndMs);

                    if (startMs >= endMs) {
                        Toast.makeText(context, "Start time must be less than End time", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    File srcFile = new File(item.getPath());
                    String baseName = item.getName();
                    String ext = "";
                    int dotIdx = baseName.lastIndexOf('.');
                    if (dotIdx > 0) {
                        ext = baseName.substring(dotIdx);
                        baseName = baseName.substring(0, dotIdx);
                    }
                    String defaultFileName = baseName + "_cut" + ext;
                    File defaultFolder = srcFile.getParentFile() != null ? srcFile.getParentFile() : Environment.getExternalStorageDirectory();

                    showSaveDialog(context, defaultFileName, defaultFolder, callback, (destFolder, chosenFileName) -> {
                        File outFile = new File(destFolder, chosenFileName);
                        AlertDialog progressDialog = showModalProgressDialog(context, context.getString(R.string.deleting_file, item.getName()), callback);

                        AudioConverter.deleteAudioSegment(context, srcFile, outFile, startMs, endMs, maxDuration, new AudioConverter.ConvertListener() {
                            @Override
                            public void onSuccess(File resultFile) {
                                dismissProgressAndNotify(context, progressDialog, resultFile, callback, context.getString(R.string.delete_marked_success, resultFile.getName()));
                            }

                            @Override
                            public void onError(String errorMessage) {
                                progressDialog.dismiss();
                                Toast.makeText(context, context.getString(R.string.delete_marked_failed, errorMessage), Toast.LENGTH_LONG).show();
                            }
                        });
                    });
                })
                .setNegativeButton(R.string.btn_cancel, (d, which) -> stopPlayback.run())
                .create();

        dialog.setOnDismissListener(d -> stopPlayback.run());

        dialog.show();
        if (callback != null) {
            callback.onActiveDialogChanged(dialog);
        }
    }

    private static void updatePreviewButton(Context context, MaterialButton btn, boolean isStart, long durationMs, boolean isPlaying) {
        if (btn == null) return;
        if (isPlaying) {
            btn.setText(R.string.btn_stop);
            btn.setContentDescription(context.getString(R.string.desc_stop_playback));
            btn.setIconResource(R.drawable.ic_action_stop);
        } else {
            String durLabel = getPreviewDurationLabel(durationMs);
            if (isStart) {
                btn.setText(context.getString(R.string.btn_play_start_fmt, durLabel));
                btn.setContentDescription(context.getString(R.string.desc_play_start_fmt, durLabel));
            } else {
                btn.setText(context.getString(R.string.btn_play_end_fmt, durLabel));
                btn.setContentDescription(context.getString(R.string.desc_play_end_fmt, durLabel));
            }
            btn.setIconResource(R.drawable.ic_action_play);
        }
    }

    private static String getPreviewDurationLabel(long durationMs) {
        if (durationMs >= 60000L) {
            return (durationMs / 60000L) + "m";
        } else {
            return (durationMs / 1000L) + "s";
        }
    }

    private interface DurationSelectCallback {
        void onDurationSelected(long durationMs);
    }

    private static void setupPreviewButtonAccessibility(
            Context context,
            MaterialButton btn,
            boolean isStart,
            long[] durationHolder,
            Runnable onDurationChanged
    ) {
        if (btn == null) return;
        ViewCompat.setAccessibilityDelegate(btn, new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                for (PreviewOption opt : PREVIEW_OPTIONS) {
                    info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            opt.actionId,
                            "Set preview duration to " + opt.label
                    ));
                }
            }

            @Override
            public boolean performAccessibilityAction(@NonNull View host, int action, @Nullable Bundle args) {
                for (PreviewOption opt : PREVIEW_OPTIONS) {
                    if (action == opt.actionId) {
                        durationHolder[0] = opt.millis;
                        if (onDurationChanged != null) onDurationChanged.run();
                        String msg = context.getString(R.string.preview_duration_set_message, opt.label);
                        host.announceForAccessibility(msg);
                        return true;
                    }
                }
                return super.performAccessibilityAction(host, action, args);
            }
        });

        btn.setOnLongClickListener(v -> {
            showPreviewDurationPicker(context, durationHolder[0], newDuration -> {
                durationHolder[0] = newDuration;
                if (onDurationChanged != null) onDurationChanged.run();
                String msg = context.getString(R.string.preview_duration_set_message, getPreviewDurationLabel(newDuration));
                btn.announceForAccessibility(msg);
            });
            return true;
        });
    }

    private static void showPreviewDurationPicker(Context context, long currentDuration, DurationSelectCallback callback) {
        String[] labels = new String[PREVIEW_OPTIONS.length];
        int checkedItem = 0;
        for (int i = 0; i < PREVIEW_OPTIONS.length; i++) {
            labels[i] = PREVIEW_OPTIONS[i].label;
            if (PREVIEW_OPTIONS[i].millis == currentDuration) {
                checkedItem = i;
            }
        }

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.preview_duration_picker_title)
                .setSingleChoiceItems(labels, checkedItem, (dialog, which) -> {
                    dialog.dismiss();
                    callback.onDurationSelected(PREVIEW_OPTIONS[which].millis);
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private static void setupSeekButtonAccessibility(
            Context context,
            MaterialButton btn,
            boolean isRewind,
            long[] seekStepHolder,
            Runnable onSeekStepChanged
    ) {
        if (btn == null) return;
        ViewCompat.setAccessibilityDelegate(btn, new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                for (SeekOption opt : SEEK_OPTIONS) {
                    info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            opt.actionId,
                            "Set seek time to " + opt.label
                    ));
                }
            }

            @Override
            public boolean performAccessibilityAction(@NonNull View host, int action, @Nullable Bundle args) {
                for (SeekOption opt : SEEK_OPTIONS) {
                    if (action == opt.actionId) {
                        seekStepHolder[0] = opt.millis;
                        if (onSeekStepChanged != null) onSeekStepChanged.run();
                        String msg = context.getString(R.string.seek_time_set_message, opt.label);
                        host.announceForAccessibility(msg);
                        return true;
                    }
                }
                return super.performAccessibilityAction(host, action, args);
            }
        });

        btn.setOnLongClickListener(v -> {
            showSeekTimePicker(context, seekStepHolder[0], newStep -> {
                seekStepHolder[0] = newStep;
                if (onSeekStepChanged != null) onSeekStepChanged.run();
                String msg = context.getString(R.string.seek_time_set_message, getPreviewDurationLabel(newStep));
                btn.announceForAccessibility(msg);
            });
            return true;
        });
    }

    private static void showSeekTimePicker(Context context, long currentStep, DurationSelectCallback callback) {
        String[] labels = new String[SEEK_OPTIONS.length];
        int checkedItem = 1; // 10s default
        for (int i = 0; i < SEEK_OPTIONS.length; i++) {
            labels[i] = SEEK_OPTIONS[i].label;
            if (SEEK_OPTIONS[i].millis == currentStep) {
                checkedItem = i;
            }
        }

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.seek_time_picker_title)
                .setSingleChoiceItems(labels, checkedItem, (dialog, which) -> {
                    dialog.dismiss();
                    callback.onDurationSelected(SEEK_OPTIONS[which].millis);
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private static String formatTimeClock(long millis) {
        int seconds = (int) ((millis / 1000) % 60);
        int minutes = (int) ((millis / (1000 * 60)) % 60);
        int hours = (int) (millis / (1000 * 60 * 60));
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
    }

    private static void setupEditTextAccessibility(EditText editText, String label) {
        if (editText == null) return;
        editText.setContentDescription(label);
        ViewCompat.setAccessibilityDelegate(editText, new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setContentDescription(label);
                info.setHintText(label);
            }
        });
    }

    private interface TimeAdjustCallback {
        void onTimeAdjusted(long updatedMs);
    }

    private static void setupTimeAdjustControls(
            Context context,
            EditText etHh,
            EditText etMm,
            EditText etSs,
            EditText etMs,
            Button btnDec,
            Button btnStep,
            Button btnInc,
            long[] stepMsHolder,
            long maxDurationMs,
            TimeAdjustCallback onTimeAdjusted
    ) {
        // Step Picker Dialog trigger on Step button or Long Press on Inc/Dec
        View.OnClickListener stepClickListener = v -> {
            showStepPickerDialog(context, stepMsHolder[0], newStep -> {
                stepMsHolder[0] = newStep;
                updateStepButtonLabel(btnStep, btnDec, btnInc, newStep);
                String msg = context.getString(R.string.step_set_message, getStepLabel(newStep));
                btnStep.announceForAccessibility(msg);
            });
        };

        btnStep.setOnClickListener(stepClickListener);
        btnDec.setOnLongClickListener(v -> { stepClickListener.onClick(v); return true; });
        btnInc.setOnLongClickListener(v -> { stepClickListener.onClick(v); return true; });

        // Normal Click: Increase / Decrease by step
        btnDec.setOnClickListener(v -> {
            long current = getTimeFieldsMs(etHh, etMm, etSs, etMs);
            long updated = Math.max(0, current - stepMsHolder[0]);
            setTimeFields(etHh, etMm, etSs, etMs, updated);
            btnDec.announceForAccessibility(formatTimeAccessible(updated));
            if (onTimeAdjusted != null) onTimeAdjusted.onTimeAdjusted(updated);
        });

        btnInc.setOnClickListener(v -> {
            long current = getTimeFieldsMs(etHh, etMm, etSs, etMs);
            long updated = Math.min(maxDurationMs, current + stepMsHolder[0]);
            setTimeFields(etHh, etMm, etSs, etMs, updated);
            btnInc.announceForAccessibility(formatTimeAccessible(updated));
            if (onTimeAdjusted != null) onTimeAdjusted.onTimeAdjusted(updated);
        });

        // TextWatcher for keyboard changes
        android.text.TextWatcher watcher = new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (onTimeAdjusted != null && (etHh.hasFocus() || etMm.hasFocus() || etSs.hasFocus() || etMs.hasFocus())) {
                    long current = getTimeFieldsMs(etHh, etMm, etSs, etMs);
                    onTimeAdjusted.onTimeAdjusted(current);
                }
            }
        };
        etHh.addTextChangedListener(watcher);
        etMm.addTextChangedListener(watcher);
        etSs.addTextChangedListener(watcher);
        etMs.addTextChangedListener(watcher);

        // Accessibility Delegate with Custom Actions for all 10 step intervals
        AccessibilityDelegateCompat delegate = new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                for (StepOption opt : STEP_OPTIONS) {
                    info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            opt.actionId,
                            "Set step to " + opt.label
                    ));
                }
            }

            @Override
            public boolean performAccessibilityAction(@NonNull View host, int action, @Nullable Bundle args) {
                for (StepOption opt : STEP_OPTIONS) {
                    if (action == opt.actionId) {
                        stepMsHolder[0] = opt.millis;
                        updateStepButtonLabel(btnStep, btnDec, btnInc, opt.millis);
                        String msg = context.getString(R.string.step_set_message, opt.label);
                        host.announceForAccessibility(msg);
                        return true;
                    }
                }
                return super.performAccessibilityAction(host, action, args);
            }
        };

        ViewCompat.setAccessibilityDelegate(btnDec, delegate);
        ViewCompat.setAccessibilityDelegate(btnInc, delegate);
        ViewCompat.setAccessibilityDelegate(btnStep, delegate);
    }

    private static void updateStepButtonLabel(Button btnStep, Button btnDec, Button btnInc, long stepMs) {
        String label = getStepLabel(stepMs);
        btnStep.setText(label);
        btnStep.setContentDescription("Current step: " + label + ". Tap to change step interval.");
        btnDec.setContentDescription("Decrease time by " + label);
        btnInc.setContentDescription("Increase time by " + label);
    }

    private static String getStepLabel(long stepMs) {
        for (StepOption opt : STEP_OPTIONS) {
            if (opt.millis == stepMs) return opt.label;
        }
        if (stepMs < 1000) return stepMs + "ms";
        if (stepMs < 60000) return (stepMs / 1000) + "s";
        return (stepMs / 60000) + "m";
    }

    private static void showStepPickerDialog(Context context, long currentStepMs, StepSelectListener listener) {
        String[] options = new String[STEP_OPTIONS.length];
        int checkedItem = 0;
        for (int i = 0; i < STEP_OPTIONS.length; i++) {
            options[i] = STEP_OPTIONS[i].label;
            if (STEP_OPTIONS[i].millis == currentStepMs) checkedItem = i;
        }

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.step_picker_title)
                .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
                    dialog.dismiss();
                    listener.onStepSelected(STEP_OPTIONS[which].millis);
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    interface StepSelectListener {
        void onStepSelected(long stepMs);
    }

    private static void setTimeFields(EditText etHh, EditText etMm, EditText etSs, EditText etMs, long millis) {
        long h = millis / 3600000L;
        long rem = millis % 3600000L;
        long m = rem / 60000L;
        rem = rem % 60000L;
        long s = rem / 1000L;
        long ms = rem % 1000L;

        etHh.setText(String.format(Locale.ENGLISH, "%02d", h));
        etMm.setText(String.format(Locale.ENGLISH, "%02d", m));
        etSs.setText(String.format(Locale.ENGLISH, "%02d", s));
        etMs.setText(String.format(Locale.ENGLISH, "%03d", ms));
    }

    private static long getTimeFieldsMs(EditText etHh, EditText etMm, EditText etSs, EditText etMs) {
        long h = parseLongSafe(etHh.getText().toString());
        long m = parseLongSafe(etMm.getText().toString());
        long s = parseLongSafe(etSs.getText().toString());
        long ms = parseLongSafe(etMs.getText().toString());
        return h * 3600000L + m * 60000L + s * 1000L + ms;
    }

    private static long parseLongSafe(String s) {
        if (TextUtils.isEmpty(s)) return 0;
        try {
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static String formatTimeAccessible(long millis) {
        long h = millis / 3600000L;
        long rem = millis % 3600000L;
        long m = rem / 60000L;
        rem = rem % 60000L;
        long s = rem / 1000L;
        long ms = rem % 1000L;
        return h + " hours, " + m + " minutes, " + s + " seconds, " + ms + " milliseconds";
    }

    public interface SaveConfirmListener {
        void onSaveConfirmed(File destinationFolder, String chosenFileName);
    }

    /**
     * Shows Save Dialog with File Name and Location (with folder picker).
     */
    public static void showSaveDialog(
            Context context,
            String initialFileName,
            File initialFolder,
            DialogCallback callback,
            SaveConfirmListener saveListener
    ) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_save_file, null);
        EditText etFileName = dialogView.findViewById(R.id.et_save_filename);
        TextView tvLocation = dialogView.findViewById(R.id.tv_save_location);
        Button btnChangeLocation = dialogView.findViewById(R.id.btn_change_location);

        etFileName.setText(initialFileName);
        int dot = initialFileName.lastIndexOf('.');
        if (dot > 0) etFileName.setSelection(0, dot);

        final File[] targetFolder = new File[] { initialFolder };
        tvLocation.setText(targetFolder[0].getAbsolutePath());
        tvLocation.setContentDescription("Save location: " + targetFolder[0].getAbsolutePath());

        btnChangeLocation.setOnClickListener(v -> {
            showFolderPickerDialog(context, targetFolder[0], chosenFolder -> {
                targetFolder[0] = chosenFolder;
                tvLocation.setText(chosenFolder.getAbsolutePath());
                tvLocation.setContentDescription("Save location: " + chosenFolder.getAbsolutePath());
            });
        });

        AlertDialog saveDialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.dialog_save_title)
                .setView(dialogView)
                .setPositiveButton(R.string.btn_save_ok, (d, which) -> {
                    String name = etFileName.getText().toString().trim();
                    if (TextUtils.isEmpty(name)) {
                        Toast.makeText(context, "File name cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int initialDot = initialFileName.lastIndexOf('.');
                    if (initialDot > 0) {
                        String requiredExt = initialFileName.substring(initialDot);
                        if (!name.contains(".")) {
                            name = name + requiredExt;
                        }
                    }
                    saveListener.onSaveConfirmed(targetFolder[0], name);
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .create();

        saveDialog.show();
        if (callback != null) {
            callback.onActiveDialogChanged(saveDialog);
        }
    }

    /**
     * Shows a folder navigation picker so the user can select any directory.
     */
    private static void showFolderPickerDialog(Context context, File currentDir, FolderSelectedListener listener) {
        File[] subDirs = currentDir.listFiles(File::isDirectory);
        List<String> names = new ArrayList<>();
        List<File> files = new ArrayList<>();

        if (currentDir.getParentFile() != null) {
            names.add(".. (Parent Folder)");
            files.add(currentDir.getParentFile());
        }

        if (subDirs != null) {
            for (File d : subDirs) {
                if (!d.getName().startsWith(".")) {
                    names.add(d.getName());
                    files.add(d);
                }
            }
        }

        new MaterialAlertDialogBuilder(context)
                .setTitle(currentDir.getName().isEmpty() ? "/" : currentDir.getName())
                .setItems(names.toArray(new String[0]), (dialog, which) -> {
                    File next = files.get(which);
                    showFolderPickerDialog(context, next, listener);
                })
                .setPositiveButton("Select This Folder", (dialog, which) -> {
                    listener.onFolderSelected(currentDir);
                })
                .setNeutralButton(R.string.btn_create_new_folder, (dialog, which) -> {
                    showCreateFolderDialog(context, currentDir, listener);
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private static void showCreateFolderDialog(Context context, File parentDir, FolderSelectedListener listener) {
        EditText etFolderName = new EditText(context);
        etFolderName.setHint(R.string.dialog_new_folder_hint);
        etFolderName.setSingleLine(true);

        FrameLayout container = new FrameLayout(context);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        int margin = (int) (16 * context.getResources().getDisplayMetrics().density);
        lp.setMargins(margin, margin / 2, margin, margin / 2);
        container.addView(etFolderName, lp);

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.dialog_new_folder_title)
                .setView(container)
                .setPositiveButton(R.string.btn_create, (d, which) -> {
                    String folderName = etFolderName.getText().toString().trim();
                    if (TextUtils.isEmpty(folderName)) {
                        Toast.makeText(context, "Folder name cannot be empty", Toast.LENGTH_SHORT).show();
                        showFolderPickerDialog(context, parentDir, listener);
                        return;
                    }
                    File newFolder = new File(parentDir, folderName);
                    if (!newFolder.exists()) {
                        boolean created = newFolder.mkdirs();
                        if (created) {
                            Toast.makeText(context, context.getString(R.string.toast_folder_created, folderName), Toast.LENGTH_SHORT).show();
                            // Automatically open / navigate into the newly created folder!
                            showFolderPickerDialog(context, newFolder, listener);
                        } else {
                            Toast.makeText(context, context.getString(R.string.toast_folder_create_failed, folderName), Toast.LENGTH_SHORT).show();
                            showFolderPickerDialog(context, parentDir, listener);
                        }
                    } else {
                        // Already exists, navigate into it directly!
                        showFolderPickerDialog(context, newFolder, listener);
                    }
                })
                .setNegativeButton(R.string.btn_cancel, (d, which) -> {
                    showFolderPickerDialog(context, parentDir, listener);
                })
                .show();
    }

    interface FolderSelectedListener {
        void onFolderSelected(File folder);
    }

    /**
     * Shows a modal progress dialog.
     */
    private static AlertDialog showModalProgressDialog(Context context, String message, DialogCallback callback) {
        FrameLayout container = new FrameLayout(context);
        ProgressBar pb = new ProgressBar(context);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.gravity = Gravity.CENTER;
        container.addView(pb, lp);
        container.setPadding(0, 36, 0, 36);

        AlertDialog progressDialog = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.dialog_processing_title)
                .setMessage(message)
                .setView(container)
                .setCancelable(false)
                .create();

        progressDialog.show();
        if (callback != null) {
            callback.onActiveDialogChanged(progressDialog);
        }
        return progressDialog;
    }

    /**
     * Dismisses progress dialog and presents Completion Dialog with Play, Share, and Close options.
     */
    private static void dismissProgressAndNotify(
            Context context,
            AlertDialog progressDialog,
            File resultFile,
            DialogCallback callback,
            String successMessage
    ) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        if (resultFile != null && resultFile.exists()) {
            try {
                MediaScannerConnection.scanFile(context.getApplicationContext(), new String[]{resultFile.getAbsolutePath()}, null, null);
            } catch (Exception ignored) {}
        }
        if (callback != null) {
            callback.onFilesChanged(); // returns to normal folder state & reloads directory
        }
        showCompletionDialog(context, resultFile, callback, successMessage);
    }

    private static void showCompletionDialog(
            Context context,
            File resultFile,
            DialogCallback callback,
            String successMessage
    ) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.dialog_completed_title)
                .setMessage(successMessage)
                .setNegativeButton(R.string.btn_close, (d, which) -> d.dismiss());

        if (resultFile != null && resultFile.exists()) {
            builder.setPositiveButton(R.string.btn_play, (d, which) -> {
                FileOpener.openFile(context, resultFile);
            });
            builder.setNeutralButton(R.string.btn_share, (d, which) -> {
                shareFile(context, resultFile);
            });
        }

        AlertDialog dialog = builder.create();
        dialog.show();
        if (callback != null) {
            callback.onActiveDialogChanged(dialog);
        }
    }

    public static void shareFile(Context context, File file) {
        if (file == null || !file.exists()) {
            Toast.makeText(context, "File does not exist", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri fileUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".provider",
                    file
            );
            String name = file.getName();
            String ext = "";
            int dot = name.lastIndexOf('.');
            if (dot >= 0) ext = name.substring(dot + 1).toLowerCase(Locale.ENGLISH);

            String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (mimeType == null || mimeType.isEmpty()) mimeType = "*/*";

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType(mimeType);
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Intent chooser = Intent.createChooser(shareIntent, context.getString(R.string.action_share) + " " + file.getName());
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
        } catch (Exception e) {
            Toast.makeText(context, "Share failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
