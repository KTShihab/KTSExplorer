package com.kts.fileexplorer.util;

import android.app.Dialog;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.pdf.PdfRenderer;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.kts.fileexplorer.R;
import com.kts.fileexplorer.model.FileItem;
import com.kts.fileexplorer.network.NetworkItem;

import java.io.File;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Dialog displaying comprehensive file and folder properties line by line,
 * including rich media and document metadata, fully accessible for TalkBack and Jieshuo screen readers.
 */
public class FilePropertiesDialog {

    private static final Set<String> AUDIO_EXTENSIONS = new HashSet<>(Arrays.asList(
            "mp3", "wav", "m4a", "aac", "flac", "ogg", "opus", "wma", "mid", "amr"
    ));
    private static final Set<String> VIDEO_EXTENSIONS = new HashSet<>(Arrays.asList(
            "mp4", "mkv", "webm", "avi", "3gp", "mov", "flv", "wmv", "ts"
    ));
    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif"
    ));

    public interface DialogRegistrar {
        void registerDialog(Dialog dialog);
    }

    public static void show(@NonNull Context context, @NonNull FileItem item, DialogRegistrar registrar) {
        File file = item.getFile();
        if (file == null || !file.exists()) {
            return;
        }

        View view = LayoutInflater.from(context).inflate(R.layout.dialog_properties, null);

        LinearLayout rowName = view.findViewById(R.id.row_prop_name);
        TextView tvName = view.findViewById(R.id.tv_prop_name);

        LinearLayout rowType = view.findViewById(R.id.row_prop_type);
        TextView tvType = view.findViewById(R.id.tv_prop_type);

        LinearLayout rowLocation = view.findViewById(R.id.row_prop_location);
        TextView tvLocation = view.findViewById(R.id.tv_prop_location);

        LinearLayout rowSize = view.findViewById(R.id.row_prop_size);
        TextView tvSize = view.findViewById(R.id.tv_prop_size);

        LinearLayout rowContains = view.findViewById(R.id.row_prop_contains);
        TextView tvContains = view.findViewById(R.id.tv_prop_contains);

        LinearLayout rowModified = view.findViewById(R.id.row_prop_modified);
        TextView tvModified = view.findViewById(R.id.tv_prop_modified);

        LinearLayout rowPermissions = view.findViewById(R.id.row_prop_permissions);
        TextView tvPermissions = view.findViewById(R.id.tv_prop_permissions);

        LinearLayout containerExtra = view.findViewById(R.id.container_extra_properties);

        // Name
        String nameStr = file.getName();
        tvName.setText(nameStr);
        rowName.setContentDescription(context.getString(R.string.prop_name) + ": " + nameStr);

        // Type
        String typeStr;
        if (file.isDirectory()) {
            typeStr = context.getString(R.string.prop_type_folder);
        } else {
            String ext = item.getExtension();
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase(Locale.ENGLISH));
            if (mime != null) {
                typeStr = ext.toUpperCase(Locale.ENGLISH) + " (" + mime + ")";
            } else if (!ext.isEmpty()) {
                typeStr = ext.toUpperCase(Locale.ENGLISH) + " file";
            } else {
                typeStr = "File";
            }
        }
        tvType.setText(typeStr);
        rowType.setContentDescription(context.getString(R.string.prop_type) + ": " + typeStr);

        // Location
        String locationStr = file.getParent() != null ? file.getParent() : file.getAbsolutePath();
        tvLocation.setText(locationStr);
        rowLocation.setContentDescription(context.getString(R.string.prop_location) + ": " + locationStr);

        // Last Modified
        String modifiedStr = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(new Date(file.lastModified()));
        tvModified.setText(modifiedStr);
        rowModified.setContentDescription(context.getString(R.string.prop_modified) + ": " + modifiedStr);

        // Permissions
        String readYesNo = file.canRead() ? "Yes" : "No";
        String writeYesNo = file.canWrite() ? "Yes" : "No";
        String hiddenYesNo = file.isHidden() ? "Yes" : "No";
        String permsStr = context.getString(R.string.prop_permissions_format, readYesNo, writeYesNo, hiddenYesNo);
        tvPermissions.setText(permsStr);
        rowPermissions.setContentDescription(context.getString(R.string.prop_permissions) + ": " + permsStr);

        ExecutorService executor = null;
        Future<?> calculationTask = null;
        Handler mainHandler = new Handler(Looper.getMainLooper());

        if (file.isDirectory()) {
            rowContains.setVisibility(View.VISIBLE);
            tvSize.setText(R.string.prop_calculating);
            tvContains.setText(R.string.prop_calculating);
            rowSize.setContentDescription(context.getString(R.string.prop_size) + ": " + context.getString(R.string.prop_calculating));
            rowContains.setContentDescription(context.getString(R.string.prop_contains) + ": " + context.getString(R.string.prop_calculating));

            executor = Executors.newSingleThreadExecutor();
            final ExecutorService finalExecutor = executor;

            calculationTask = executor.submit(() -> {
                FolderStats stats = new FolderStats();
                calculateFolderStats(file, stats);

                if (Thread.currentThread().isInterrupted()) return;

                mainHandler.post(() -> {
                    String formattedSize = formatFileSize(stats.totalBytes);
                    String byteCountStr = NumberFormat.getNumberInstance(Locale.getDefault()).format(stats.totalBytes);
                    String fullSizeStr = formattedSize + " (" + byteCountStr + " bytes)";
                    tvSize.setText(fullSizeStr);
                    rowSize.setContentDescription(context.getString(R.string.prop_size) + ": " + fullSizeStr);

                    String containsStr = context.getString(R.string.prop_contains_format, stats.fileCount, stats.folderCount);
                    tvContains.setText(containsStr);
                    rowContains.setContentDescription(context.getString(R.string.prop_contains) + ": " + containsStr);
                });
            });
        } else {
            rowContains.setVisibility(View.GONE);
            long bytes = file.length();
            String formattedSize = formatFileSize(bytes);
            String byteCountStr = NumberFormat.getNumberInstance(Locale.getDefault()).format(bytes);
            String fullSizeStr = formattedSize + " (" + byteCountStr + " bytes)";
            tvSize.setText(fullSizeStr);
            rowSize.setContentDescription(context.getString(R.string.prop_size) + ": " + fullSizeStr);

            // Populate rich media and document metadata
            String ext = item.getExtension().toLowerCase(Locale.ENGLISH);
            if (AUDIO_EXTENSIONS.contains(ext)) {
                populateAudioProperties(context, file, containerExtra);
            } else if (VIDEO_EXTENSIONS.contains(ext)) {
                populateVideoProperties(context, file, containerExtra);
            } else if (IMAGE_EXTENSIONS.contains(ext)) {
                populateImageProperties(context, file, containerExtra);
            } else if ("pdf".equals(ext)) {
                populatePdfProperties(context, file, containerExtra);
            } else if ("apk".equals(ext)) {
                populateApkProperties(context, file, containerExtra);
            }
        }

        final ExecutorService finalExecutorToShutdown = executor;
        final Future<?> finalTaskToCancel = calculationTask;

        Dialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.properties_dialog_title))
                .setView(view)
                .setPositiveButton(R.string.btn_close, (d, which) -> d.dismiss())
                .setOnDismissListener(d -> {
                    if (finalTaskToCancel != null) {
                        finalTaskToCancel.cancel(true);
                    }
                    if (finalExecutorToShutdown != null) {
                        finalExecutorToShutdown.shutdownNow();
                    }
                })
                .create();

        if (registrar != null) {
            registrar.registerDialog(dialog);
        } else {
            dialog.show();
        }
    }

    public static void show(@NonNull Context context, @NonNull NetworkItem item, DialogRegistrar registrar) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_properties, null);

        LinearLayout rowName = view.findViewById(R.id.row_prop_name);
        TextView tvName = view.findViewById(R.id.tv_prop_name);

        LinearLayout rowType = view.findViewById(R.id.row_prop_type);
        TextView tvType = view.findViewById(R.id.tv_prop_type);

        LinearLayout rowLocation = view.findViewById(R.id.row_prop_location);
        TextView tvLocation = view.findViewById(R.id.tv_prop_location);

        LinearLayout rowSize = view.findViewById(R.id.row_prop_size);
        TextView tvSize = view.findViewById(R.id.tv_prop_size);

        LinearLayout rowContains = view.findViewById(R.id.row_prop_contains);

        LinearLayout rowModified = view.findViewById(R.id.row_prop_modified);
        TextView tvModified = view.findViewById(R.id.tv_prop_modified);

        LinearLayout rowPermissions = view.findViewById(R.id.row_prop_permissions);
        TextView tvPermissions = view.findViewById(R.id.tv_prop_permissions);

        LinearLayout containerExtra = view.findViewById(R.id.container_extra_properties);

        // Name
        String nameStr = item.getName();
        tvName.setText(nameStr);
        rowName.setContentDescription(context.getString(R.string.prop_name) + ": " + nameStr);

        // Type
        String typeStr;
        if (item.isDirectory()) {
            typeStr = context.getString(R.string.prop_type_folder);
        } else {
            String ext = item.getExtension();
            if (ext.startsWith(".")) {
                ext = ext.substring(1);
            }
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.toLowerCase(Locale.ENGLISH));
            if (mime != null) {
                typeStr = ext.toUpperCase(Locale.ENGLISH) + " (" + mime + ")";
            } else if (!ext.isEmpty()) {
                typeStr = ext.toUpperCase(Locale.ENGLISH) + " file";
            } else {
                typeStr = "File";
            }
        }
        tvType.setText(typeStr);
        rowType.setContentDescription(context.getString(R.string.prop_type) + ": " + typeStr);

        // Location
        String locationStr = item.getPath();
        if (item.getServerInfo() != null) {
            String serverName = item.getServerInfo().getName();
            if (item.getShareName() != null && !item.getShareName().isEmpty()) {
                locationStr = serverName + " / " + item.getShareName() + " / " + locationStr;
            } else {
                locationStr = serverName + " / " + locationStr;
            }
        }
        tvLocation.setText(locationStr);
        rowLocation.setContentDescription(context.getString(R.string.prop_location) + ": " + locationStr);

        // Size
        rowContains.setVisibility(View.GONE);
        if (item.isDirectory()) {
            tvSize.setText(context.getString(R.string.prop_type_folder));
            rowSize.setContentDescription(context.getString(R.string.prop_size) + ": " + context.getString(R.string.prop_type_folder));
        } else {
            long bytes = item.getSize();
            String formattedSize = formatFileSize(bytes);
            String byteCountStr = NumberFormat.getNumberInstance(Locale.getDefault()).format(bytes);
            String fullSizeStr = formattedSize + " (" + byteCountStr + " bytes)";
            tvSize.setText(fullSizeStr);
            rowSize.setContentDescription(context.getString(R.string.prop_size) + ": " + fullSizeStr);
        }

        // Last Modified
        String modifiedStr;
        if (item.getLastModified() > 0) {
            modifiedStr = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(new Date(item.getLastModified()));
        } else {
            modifiedStr = "-";
        }
        tvModified.setText(modifiedStr);
        rowModified.setContentDescription(context.getString(R.string.prop_modified) + ": " + modifiedStr);

        // Storage / Permissions
        String storageType;
        if (item.getServerInfo() != null) {
            String type = item.getServerInfo().getType();
            if ("GOOGLE_DRIVE".equalsIgnoreCase(type)) {
                storageType = "Google Drive (Cloud)";
            } else if ("SMB".equalsIgnoreCase(type)) {
                storageType = "SMB (Windows Network Share)";
            } else if ("FTP".equalsIgnoreCase(type)) {
                storageType = "FTP Server";
            } else {
                storageType = type;
            }
        } else {
            storageType = "Remote Storage";
        }
        tvPermissions.setText(storageType);
        rowPermissions.setContentDescription(context.getString(R.string.prop_permissions) + ": " + storageType);

        if (item.getServerInfo() != null && item.getServerInfo().getHost() != null && !item.getServerInfo().getHost().isEmpty()) {
            addPropertyRow(context, containerExtra, "Host / Address", item.getServerInfo().getHost());
        }

        Dialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.properties_dialog_title))
                .setView(view)
                .setPositiveButton(R.string.btn_close, (d, which) -> d.dismiss())
                .create();

        if (registrar != null) {
            registrar.registerDialog(dialog);
        } else {
            dialog.show();
        }
    }

    private static void populateAudioProperties(Context context, File file, LinearLayout container) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(file.getAbsolutePath());

            String durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (durationMs != null) {
                try {
                    long ms = Long.parseLong(durationMs);
                    addPropertyRow(context, container, context.getString(R.string.prop_duration), formatDuration(ms));
                } catch (NumberFormatException ignored) {}
            }

            String title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            if (title != null && !title.trim().isEmpty()) {
                addPropertyRow(context, container, context.getString(R.string.prop_title), title);
            }

            String artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
            if (artist != null && !artist.trim().isEmpty()) {
                addPropertyRow(context, container, context.getString(R.string.prop_artist), artist);
            }

            String album = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
            if (album != null && !album.trim().isEmpty()) {
                addPropertyRow(context, container, context.getString(R.string.prop_album), album);
            }

            String bitrate = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
            if (bitrate != null) {
                try {
                    long bps = Long.parseLong(bitrate);
                    addPropertyRow(context, container, context.getString(R.string.prop_bitrate), formatBitrate(bps));
                } catch (NumberFormatException ignored) {}
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                String sampleRate = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE);
                if (sampleRate != null && !sampleRate.trim().isEmpty()) {
                    addPropertyRow(context, container, context.getString(R.string.prop_samplerate), sampleRate + " Hz");
                }
            }

            String genre = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE);
            if (genre != null && !genre.trim().isEmpty()) {
                addPropertyRow(context, container, context.getString(R.string.prop_genre), genre);
            }

            String year = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR);
            if (year == null || year.trim().isEmpty()) {
                year = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE);
            }
            if (year != null && !year.trim().isEmpty()) {
                addPropertyRow(context, container, context.getString(R.string.prop_year), year);
            }
        } catch (Exception ignored) {
        } finally {
            try {
                mmr.release();
            } catch (Exception ignored) {}
        }
    }

    private static void populateVideoProperties(Context context, File file, LinearLayout container) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(file.getAbsolutePath());

            String durationMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (durationMs != null) {
                try {
                    long ms = Long.parseLong(durationMs);
                    addPropertyRow(context, container, context.getString(R.string.prop_duration), formatDuration(ms));
                } catch (NumberFormatException ignored) {}
            }

            String width = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String height = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            if (width != null && height != null) {
                String rotation = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
                if ("90".equals(rotation) || "270".equals(rotation)) {
                    addPropertyRow(context, container, context.getString(R.string.prop_resolution), height + " x " + width);
                } else {
                    addPropertyRow(context, container, context.getString(R.string.prop_resolution), width + " x " + height);
                }
            }

            String bitrate = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
            if (bitrate != null) {
                try {
                    long bps = Long.parseLong(bitrate);
                    addPropertyRow(context, container, context.getString(R.string.prop_bitrate), formatBitrate(bps));
                } catch (NumberFormatException ignored) {}
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                String frameRate = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE);
                if (frameRate != null && !frameRate.trim().isEmpty()) {
                    try {
                        float fps = Float.parseFloat(frameRate);
                        if (fps > 0) {
                            addPropertyRow(context, container, context.getString(R.string.prop_framerate), String.format(Locale.getDefault(), "%.1f fps", fps));
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }

            String mimeType = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
            if (mimeType != null && !mimeType.trim().isEmpty()) {
                addPropertyRow(context, container, context.getString(R.string.prop_codec), mimeType);
            }
        } catch (Exception ignored) {
        } finally {
            try {
                mmr.release();
            } catch (Exception ignored) {}
        }
    }

    private static void populateImageProperties(Context context, File file, LinearLayout container) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);

        if (options.outWidth > 0 && options.outHeight > 0) {
            String dimensions = options.outWidth + " x " + options.outHeight;
            addPropertyRow(context, container, context.getString(R.string.prop_resolution), dimensions);

            float mp = (options.outWidth * (float) options.outHeight) / 1000000.0f;
            addPropertyRow(context, container, "Megapixels", String.format(Locale.getDefault(), "%.1f MP", mp));
        }

        try {
            ExifInterface exif = new ExifInterface(file.getAbsolutePath());

            String make = exif.getAttribute(ExifInterface.TAG_MAKE);
            String model = exif.getAttribute(ExifInterface.TAG_MODEL);
            if (model != null && !model.trim().isEmpty()) {
                String cam = (make != null && !model.toLowerCase().contains(make.toLowerCase())) ? (make + " " + model) : model;
                addPropertyRow(context, container, context.getString(R.string.prop_camera), cam);
            }

            String dateTaken = exif.getAttribute(ExifInterface.TAG_DATETIME);
            if (dateTaken != null && !dateTaken.trim().isEmpty()) {
                addPropertyRow(context, container, context.getString(R.string.prop_date_taken), dateTaken);
            }

            String iso = exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS);
            if (iso != null && !iso.trim().isEmpty()) {
                addPropertyRow(context, container, context.getString(R.string.prop_iso), "ISO " + iso);
            }

            String fNumber = exif.getAttribute(ExifInterface.TAG_F_NUMBER);
            if (fNumber != null && !fNumber.trim().isEmpty()) {
                addPropertyRow(context, container, context.getString(R.string.prop_aperture), "f/" + fNumber);
            }

            double focalLength = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, -1);
            if (focalLength > 0) {
                addPropertyRow(context, container, context.getString(R.string.prop_focal_length), String.format(Locale.getDefault(), "%.1f mm", focalLength));
            }

            double exposureTime = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, -1);
            if (exposureTime > 0) {
                String expStr;
                if (exposureTime < 1.0) {
                    expStr = "1/" + Math.round(1.0 / exposureTime) + " s";
                } else {
                    expStr = String.format(Locale.getDefault(), "%.1f s", exposureTime);
                }
                addPropertyRow(context, container, context.getString(R.string.prop_exposure), expStr);
            }
        } catch (Exception ignored) {}
    }

    private static void populatePdfProperties(Context context, File file, LinearLayout container) {
        try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(pfd)) {
            int pageCount = renderer.getPageCount();
            addPropertyRow(context, container, context.getString(R.string.prop_page_count),
                    context.getString(R.string.prop_page_count_format, pageCount));
        } catch (Exception ignored) {}
    }

    private static void populateApkProperties(Context context, File file, LinearLayout container) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pkgInfo = pm.getPackageArchiveInfo(file.getAbsolutePath(), 0);
            if (pkgInfo != null) {
                pkgInfo.applicationInfo.sourceDir = file.getAbsolutePath();
                pkgInfo.applicationInfo.publicSourceDir = file.getAbsolutePath();

                CharSequence appName = pm.getApplicationLabel(pkgInfo.applicationInfo);
                if (appName != null && !appName.toString().trim().isEmpty()) {
                    addPropertyRow(context, container, "App Name", appName.toString());
                }

                if (pkgInfo.packageName != null && !pkgInfo.packageName.trim().isEmpty()) {
                    addPropertyRow(context, container, context.getString(R.string.prop_package_name), pkgInfo.packageName);
                }

                long versionCode;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    versionCode = pkgInfo.getLongVersionCode();
                } else {
                    versionCode = pkgInfo.versionCode;
                }
                String versionStr = (pkgInfo.versionName != null ? pkgInfo.versionName : "") + " (" + versionCode + ")";
                addPropertyRow(context, container, context.getString(R.string.prop_version), versionStr);

                if (pkgInfo.applicationInfo.targetSdkVersion > 0) {
                    addPropertyRow(context, container, context.getString(R.string.prop_sdk), "API " + pkgInfo.applicationInfo.targetSdkVersion);
                }
            }
        } catch (Exception ignored) {}
    }

    private static void addPropertyRow(Context context, LinearLayout container, String label, String value) {
        if (value == null || value.trim().isEmpty()) return;

        View rowView = LayoutInflater.from(context).inflate(R.layout.item_property_row, container, false);
        LinearLayout rowLayout = rowView.findViewById(R.id.row_prop_layout);
        TextView tvLabel = rowView.findViewById(R.id.tv_prop_row_label);
        TextView tvValue = rowView.findViewById(R.id.tv_prop_row_value);

        tvLabel.setText(label);
        tvValue.setText(value);
        rowLayout.setContentDescription(label + ": " + value);
        container.addView(rowView);
    }

    private static String formatDuration(long ms) {
        if (ms <= 0) return "00:00";
        long totalSec = ms / 1000;
        long hours = totalSec / 3600;
        long minutes = (totalSec % 3600) / 60;
        long seconds = totalSec % 60;
        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
    }

    private static String formatBitrate(long bps) {
        if (bps <= 0) return "";
        if (bps >= 1_000_000) {
            return String.format(Locale.getDefault(), "%.1f Mbps", bps / 1_000_000.0f);
        } else {
            return (bps / 1000) + " kbps";
        }
    }

    private static class FolderStats {
        long totalBytes = 0;
        int fileCount = 0;
        int folderCount = 0;
    }

    private static void calculateFolderStats(File dir, FolderStats stats) {
        if (dir == null || !dir.exists() || Thread.currentThread().isInterrupted()) return;
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (Thread.currentThread().isInterrupted()) return;
            if (f.isDirectory()) {
                stats.folderCount++;
                calculateFolderStats(f, stats);
            } else {
                stats.fileCount++;
                stats.totalBytes += f.length();
            }
        }
    }

    private static String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        if (digitGroups >= units.length) digitGroups = units.length - 1;
        double value = bytes / Math.pow(1024, digitGroups);
        if (value >= 1023.95 && digitGroups < units.length - 1) {
            digitGroups++;
            value = bytes / Math.pow(1024, digitGroups);
        }
        return new DecimalFormat("#,##0.#").format(value) + " " + units[digitGroups];
    }
}
