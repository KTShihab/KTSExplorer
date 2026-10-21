package com.kts.fileexplorer.util;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.webkit.MimeTypeMap;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.kts.fileexplorer.R;
import com.kts.fileexplorer.activity.ImageViewerActivity;
import com.kts.fileexplorer.activity.MediaPlayerActivity;
import com.kts.fileexplorer.model.FileItem;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Utility helper to open files with built-in accessible media player and image viewer,
 * or delegating to standard external handlers or system chooser ("Open with").
 */
public class FileOpener {

    public static void openFile(Context context, File file) {
        openFile(context, file, null);
    }

    public static void openFile(Context context, File file, List<FileItem> visibleItems) {
        if (file == null || !file.exists()) {
            Toast.makeText(context, R.string.cannot_open_file, Toast.LENGTH_SHORT).show();
            return;
        }

        if (MediaPlayerActivity.isAudioFile(file) || MediaPlayerActivity.isVideoFile(file)) {
            openMediaPlayer(context, file, visibleItems);
            return;
        }

        if (ImageViewerActivity.isImageFile(file)) {
            openImageViewer(context, file, visibleItems);
            return;
        }

        openExternal(context, file, false);
    }

    public static void openMediaPlayer(Context context, File file, List<FileItem> visibleItems) {
        Intent intent = new Intent(context, MediaPlayerActivity.class);
        intent.putExtra(MediaPlayerActivity.EXTRA_CURRENT_PATH, file.getAbsolutePath());

        boolean isTargetAudio = MediaPlayerActivity.isAudioFile(file);
        ArrayList<String> playlist = new ArrayList<>();
        int startIndex = 0;

        if (visibleItems != null && !visibleItems.isEmpty()) {
            for (FileItem item : visibleItems) {
                if (item.getFile() != null && !item.isDirectory()) {
                    boolean match = isTargetAudio ? MediaPlayerActivity.isAudioFile(item.getFile())
                            : MediaPlayerActivity.isVideoFile(item.getFile());
                    if (match) {
                        if (isSameFile(item.getFile(), file)) {
                            startIndex = playlist.size();
                        }
                        playlist.add(item.getFile().getAbsolutePath());
                    }
                }
            }
        }

        // If visible items had <= 1 item, scan parent directory so playlist has siblings for Next/Previous
        if (playlist.size() <= 1 && file.getParentFile() != null && file.getParentFile().exists()) {
            File[] siblings = file.getParentFile().listFiles();
            if (siblings != null && siblings.length > 0) {
                playlist.clear();
                Arrays.sort(siblings, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                for (File f : siblings) {
                    if (!f.isDirectory()) {
                        boolean match = isTargetAudio ? MediaPlayerActivity.isAudioFile(f)
                                : MediaPlayerActivity.isVideoFile(f);
                        if (match) {
                            if (isSameFile(f, file)) {
                                startIndex = playlist.size();
                            }
                            playlist.add(f.getAbsolutePath());
                        }
                    }
                }
            }
        }

        if (playlist.isEmpty()) {
            playlist.add(file.getAbsolutePath());
            startIndex = 0;
        }

        intent.putStringArrayListExtra(MediaPlayerActivity.EXTRA_PLAYLIST_PATHS, playlist);
        intent.putExtra(MediaPlayerActivity.EXTRA_START_INDEX, startIndex);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static void openImageViewer(Context context, File file, List<FileItem> visibleItems) {
        Intent intent = new Intent(context, ImageViewerActivity.class);
        intent.putExtra(ImageViewerActivity.EXTRA_CURRENT_PATH, file.getAbsolutePath());

        ArrayList<String> imageList = new ArrayList<>();
        int startIndex = 0;

        if (visibleItems != null && !visibleItems.isEmpty()) {
            for (FileItem item : visibleItems) {
                if (item.getFile() != null && !item.isDirectory() && ImageViewerActivity.isImageFile(item.getFile())) {
                    if (isSameFile(item.getFile(), file)) {
                        startIndex = imageList.size();
                    }
                    imageList.add(item.getFile().getAbsolutePath());
                }
            }
        }

        if (imageList.size() <= 1 && file.getParentFile() != null && file.getParentFile().exists()) {
            File[] siblings = file.getParentFile().listFiles();
            if (siblings != null && siblings.length > 0) {
                imageList.clear();
                Arrays.sort(siblings, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                for (File f : siblings) {
                    if (!f.isDirectory() && ImageViewerActivity.isImageFile(f)) {
                        if (isSameFile(f, file)) {
                            startIndex = imageList.size();
                        }
                        imageList.add(f.getAbsolutePath());
                    }
                }
            }
        }

        if (imageList.isEmpty()) {
            imageList.add(file.getAbsolutePath());
            startIndex = 0;
        }

        intent.putStringArrayListExtra(ImageViewerActivity.EXTRA_IMAGE_PATHS, imageList);
        intent.putExtra(ImageViewerActivity.EXTRA_START_INDEX, startIndex);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static boolean isSameFile(File f1, File f2) {
        if (f1 == null || f2 == null) return false;
        if (f1.equals(f2)) return true;
        try {
            if (f1.getCanonicalPath().equals(f2.getCanonicalPath())) return true;
        } catch (Exception ignored) {}
        return f1.getAbsolutePath().equalsIgnoreCase(f2.getAbsolutePath());
    }

    public static void openFileWith(Context context, File file) {
        openExternal(context, file, true);
    }

    private static void openExternal(Context context, File file, boolean forceChooser) {
        if (file == null || !file.exists()) {
            Toast.makeText(context, R.string.cannot_open_file, Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Uri fileUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".provider",
                    file
            );

            String extension = getFileExtension(file.getName());
            String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase(Locale.ENGLISH));
            if (mimeType == null || mimeType.trim().isEmpty()) {
                mimeType = "*/*";
            }

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (forceChooser) {
                Intent chooser = Intent.createChooser(intent, context.getString(R.string.action_open_with));
                chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(chooser);
            } else {
                context.startActivity(intent);
            }
        } catch (ActivityNotFoundException e) {
            // If specific MIME type has no handler, try generic fallback chooser
            try {
                Uri fileUri = FileProvider.getUriForFile(
                        context,
                        context.getPackageName() + ".provider",
                        file
                );
                Intent fallback = new Intent(Intent.ACTION_VIEW);
                fallback.setDataAndType(fileUri, "*/*");
                fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                Intent chooser = Intent.createChooser(fallback, context.getString(R.string.action_open_with));
                chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(chooser);
            } catch (Exception ex) {
                Toast.makeText(context, R.string.cannot_open_file, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(context, "Could not open file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public static String getFileExtension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            return name.substring(dot + 1);
        }
        return "";
    }
}
