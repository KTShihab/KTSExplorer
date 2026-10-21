package com.kts.fileexplorer.cloud;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import androidx.documentfile.provider.DocumentFile;

import com.kts.fileexplorer.network.NetworkItem;
import com.kts.fileexplorer.network.NetworkServerInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Helper to interact with Google Drive and cloud storages exposed via
 * Android Storage Access Framework (SAF) DocumentFile trees.
 */
public class DocumentFileTreeHelper {

    public interface ProgressListener {
        void onProgress(long bytesTransferred, long totalBytes);
    }

    public static List<NetworkItem> listFiles(Context context, Uri currentFolderUri, NetworkServerInfo serverInfo) throws Exception {
        List<NetworkItem> items = new ArrayList<>();
        DocumentFile folder = DocumentFile.fromTreeUri(context, currentFolderUri);
        if (folder == null || !folder.canRead()) {
            throw new Exception("Unable to open Google Drive folder. Permission may have been revoked.");
        }

        DocumentFile[] files = folder.listFiles();
        if (files != null) {
            for (DocumentFile file : files) {
                String name = file.getName();
                if (TextUtils.isEmpty(name)) continue;

                boolean isDir = file.isDirectory();
                long size = file.length();
                long lastModified = file.lastModified();
                String uriStr = file.getUri().toString();

                NetworkItem item = new NetworkItem(
                        name,
                        uriStr, // Store document URI in path
                        serverInfo.getName(),
                        isDir,
                        size,
                        lastModified,
                        serverInfo
                );
                items.add(item);
            }
        }

        // Sort: folders first, then alphabetical
        Collections.sort(items, (a, b) -> {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        return items;
    }

    public static void downloadFile(Context context, Uri fileUri, File destFile, ProgressListener listener) throws Exception {
        if (destFile.getParentFile() != null && !destFile.getParentFile().exists()) {
            destFile.getParentFile().mkdirs();
        }

        try (InputStream in = context.getContentResolver().openInputStream(fileUri);
             OutputStream out = new FileOutputStream(destFile)) {

            if (in == null) {
                throw new Exception("Cannot read Google Drive file.");
            }

            byte[] buffer = new byte[64 * 1024];
            int read;
            long transferred = 0;
            DocumentFile df = DocumentFile.fromSingleUri(context, fileUri);
            long total = df != null ? df.length() : -1;

            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                transferred += read;
                if (listener != null) {
                    listener.onProgress(transferred, total);
                }
            }
            out.flush();
        }
    }

    public static void uploadFileOrDirectory(Context context, Uri parentFolderUri, File sourceFileOrDir, ProgressListener listener) throws Exception {
        if (sourceFileOrDir == null || !sourceFileOrDir.exists()) return;
        if (!sourceFileOrDir.isDirectory()) {
            uploadFile(context, parentFolderUri, sourceFileOrDir, listener);
            return;
        }

        DocumentFile parent = DocumentFile.fromTreeUri(context, parentFolderUri);
        if (parent == null || !parent.canWrite()) {
            throw new Exception("Cannot write to target Google Drive folder.");
        }

        DocumentFile newDir = parent.createDirectory(sourceFileOrDir.getName());
        if (newDir == null) {
            throw new Exception("Failed to create folder in Google Drive: " + sourceFileOrDir.getName());
        }

        File[] children = sourceFileOrDir.listFiles();
        if (children != null) {
            for (File child : children) {
                uploadFileOrDirectory(context, newDir.getUri(), child, listener);
            }
        }
    }

    public static void uploadFile(Context context, Uri parentFolderUri, File sourceFile, ProgressListener listener) throws Exception {
        DocumentFile parent = DocumentFile.fromTreeUri(context, parentFolderUri);
        if (parent == null || !parent.canWrite()) {
            throw new Exception("Cannot write to target Google Drive folder.");
        }

        String mimeType = getMimeType(sourceFile.getName());
        DocumentFile targetFile = parent.createFile(mimeType, sourceFile.getName());
        if (targetFile == null) {
            throw new Exception("Failed to create file in Google Drive: " + sourceFile.getName());
        }

        try (InputStream in = new FileInputStream(sourceFile);
             OutputStream out = context.getContentResolver().openOutputStream(targetFile.getUri())) {

            if (out == null) {
                throw new Exception("Cannot write to Google Drive file stream.");
            }

            byte[] buffer = new byte[64 * 1024];
            int read;
            long transferred = 0;
            long total = sourceFile.length();

            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                transferred += read;
                if (listener != null) {
                    listener.onProgress(transferred, total);
                }
            }
            out.flush();
        }
    }

    public static boolean createFolder(Context context, Uri parentFolderUri, String folderName) {
        DocumentFile parent = DocumentFile.fromTreeUri(context, parentFolderUri);
        if (parent == null || !parent.canWrite()) return false;
        DocumentFile newDir = parent.createDirectory(folderName);
        return newDir != null && newDir.exists();
    }

    public static boolean deleteItem(Context context, Uri itemUri) {
        DocumentFile file = DocumentFile.fromSingleUri(context, itemUri);
        if (file == null || !file.exists()) {
            file = DocumentFile.fromTreeUri(context, itemUri);
        }
        return file != null && file.delete();
    }

    private static String getMimeType(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0) {
            String ext = fileName.substring(dot + 1).toLowerCase();
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (!TextUtils.isEmpty(mime)) {
                return mime;
            }
        }
        return "application/octet-stream";
    }
}
