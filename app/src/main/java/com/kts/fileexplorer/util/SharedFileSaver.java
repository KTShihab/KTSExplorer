package com.kts.fileexplorer.util;

import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility to extract shared files from incoming intents (WhatsApp, Telegram, etc.)
 * and save them to user-selected folders.
 */
public class SharedFileSaver {

    /**
     * Extracts all shared file URIs from an incoming SEND or SEND_MULTIPLE intent.
     */
    @NonNull
    public static List<Uri> extractUrisFromIntent(@Nullable Intent intent) {
        List<Uri> uris = new ArrayList<>();
        if (intent == null) return uris;

        String action = intent.getAction();
        if (!Intent.ACTION_SEND.equals(action) && !Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            return uris;
        }

        if (Intent.ACTION_SEND.equals(action)) {
            Uri streamUri = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                streamUri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
            } else {
                streamUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            }

            if (streamUri != null) {
                uris.add(streamUri);
            } else if (intent.getData() != null) {
                uris.add(intent.getData());
            } else if (intent.getClipData() != null && intent.getClipData().getItemCount() > 0) {
                Uri clipUri = intent.getClipData().getItemAt(0).getUri();
                if (clipUri != null) {
                    uris.add(clipUri);
                }
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            ArrayList<Uri> extraUris = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extraUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri.class);
            } else {
                extraUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            }

            if (extraUris != null && !extraUris.isEmpty()) {
                uris.addAll(extraUris);
            } else if (intent.getClipData() != null) {
                ClipData clipData = intent.getClipData();
                for (int i = 0; i < clipData.getItemCount(); i++) {
                    Uri clipUri = clipData.getItemAt(i).getUri();
                    if (clipUri != null && !uris.contains(clipUri)) {
                        uris.add(clipUri);
                    }
                }
            }
        }

        return uris;
    }

    /**
     * Retrieves the file name for a given URI.
     */
    @NonNull
    public static String getFileNameFromUri(@NonNull Context context, @NonNull Uri uri) {
        String result = null;
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception ignored) {}
        }

        if (result == null || result.trim().isEmpty()) {
            String path = uri.getPath();
            if (path != null) {
                int lastSlash = path.lastIndexOf('/');
                if (lastSlash != -1 && lastSlash < path.length() - 1) {
                    result = path.substring(lastSlash + 1);
                } else {
                    result = path;
                }
            }
        }

        if (result == null || result.trim().isEmpty()) {
            result = "shared_file_" + System.currentTimeMillis();
        }

        return result;
    }

    /**
     * Generates a collision-free file in destination directory.
     */
    @NonNull
    public static File getUniqueDestinationFile(@NonNull File dir, @NonNull String fileName) {
        File file = new File(dir, fileName);
        if (!file.exists()) return file;

        String baseName = fileName;
        String extension = "";
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            baseName = fileName.substring(0, dotIndex);
            extension = fileName.substring(dotIndex);
        }

        int counter = 1;
        while (file.exists()) {
            file = new File(dir, baseName + " (" + counter + ")" + extension);
            counter++;
        }
        return file;
    }

    /**
     * Copies content from the source URI into the destination file.
     */
    public static boolean copyUriToFile(@NonNull Context context, @NonNull Uri sourceUri, @NonNull File destFile) {
        try (InputStream in = context.getContentResolver().openInputStream(sourceUri);
             OutputStream out = new FileOutputStream(destFile)) {
            if (in == null) return false;
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            out.flush();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
