package com.kts.fileexplorer.model;

import android.content.Context;

import com.kts.fileexplorer.util.AppSettings;

import java.io.File;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Model class representing a file or directory with accessibility-ready descriptions.
 * Supports custom category file count and category storage size when viewed inside Categories.
 */
public class FileItem implements Comparable<FileItem> {

    private final File file;
    private final String name;
    private final String path;
    private final boolean isDirectory;
    private final long length;
    private final long lastModified;
    private final int childCount;

    // Optional category-scoped metrics & disambiguated display name
    private final int customChildCount;
    private final long customLength;
    private final String customCategoryName;
    private final String disambiguatedName;
    private final com.kts.fileexplorer.network.NetworkItem networkItem;

    public FileItem(File file) {
        this.file = file;
        this.name = file.getName();
        this.path = file.getAbsolutePath();
        this.isDirectory = file.isDirectory();
        this.lastModified = file.lastModified();
        this.customChildCount = -1;
        this.customLength = -1;
        this.customCategoryName = null;
        this.disambiguatedName = null;
        this.networkItem = null;

        if (this.isDirectory) {
            String[] list = file.list();
            this.childCount = (list != null) ? list.length : 0;
            this.length = 0;
        } else {
            this.childCount = 0;
            this.length = file.length();
        }
    }

    /**
     * Constructor for category-scoped folder items displaying ONLY that category's file count & size.
     */
    public FileItem(File file, int categoryChildCount, long categoryTotalSize, String categoryName, String disambiguatedName) {
        this(file, categoryChildCount, categoryTotalSize, categoryName, disambiguatedName, file.lastModified());
    }

    /**
     * Fast constructor for category-scoped folder items avoiding disk syscalls.
     */
    public FileItem(File file, int categoryChildCount, long categoryTotalSize, String categoryName, String disambiguatedName, long lastModified) {
        this.file = file;
        this.name = file.getName();
        this.path = file.getAbsolutePath();
        this.isDirectory = true;
        this.lastModified = lastModified;
        this.customChildCount = categoryChildCount;
        this.customLength = categoryTotalSize;
        this.customCategoryName = categoryName;
        this.disambiguatedName = disambiguatedName;
        this.childCount = categoryChildCount;
        this.length = categoryTotalSize; // Allows sorting by category size
        this.networkItem = null;
    }

    /**
     * Fast constructor for files queried via MediaStore avoiding disk syscalls.
     */
    public FileItem(File file, long length, long lastModified) {
        this.file = file;
        this.name = file.getName();
        this.path = file.getAbsolutePath();
        this.isDirectory = false;
        if (length <= 0 && file != null && file.exists()) {
            this.length = file.length();
        } else {
            this.length = Math.max(0, length);
        }
        if (lastModified <= 0 && file != null && file.exists()) {
            this.lastModified = file.lastModified();
        } else {
            this.lastModified = Math.max(0, lastModified);
        }
        this.childCount = 0;
        this.customChildCount = -1;
        this.customLength = -1;
        this.customCategoryName = null;
        this.disambiguatedName = null;
        this.networkItem = null;
    }

    /**
     * Constructor for remote network and cloud storage items (Google Drive, SMB, FTP).
     */
    public FileItem(com.kts.fileexplorer.network.NetworkItem networkItem) {
        this.networkItem = networkItem;
        this.file = null;
        this.name = networkItem.getName();
        this.path = networkItem.getPath();
        this.isDirectory = networkItem.isDirectory();
        this.lastModified = networkItem.getLastModified();
        this.length = networkItem.getSize();
        this.childCount = 0;
        this.customChildCount = -1;
        this.customLength = -1;
        String serverDesc = "Cloud";
        if (networkItem.getServerInfo() != null && networkItem.getServerInfo().getName() != null) {
            serverDesc = networkItem.getServerInfo().getName();
        } else if (networkItem.getShareName() != null && !networkItem.getShareName().isEmpty()) {
            serverDesc = networkItem.getShareName();
        }
        this.customCategoryName = serverDesc;
        this.disambiguatedName = null;
    }

    public boolean isNetworkItem() {
        return networkItem != null;
    }

    public com.kts.fileexplorer.network.NetworkItem getNetworkItem() {
        return networkItem;
    }

    public File getFile() {
        return file != null ? file : new File(path);
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return (disambiguatedName != null && !disambiguatedName.isEmpty()) ? disambiguatedName : name;
    }

    public String getPath() {
        return path;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public long getLength() {
        return length;
    }

    public long getLastModified() {
        return lastModified;
    }

    public int getChildCount() {
        return childCount;
    }

    /**
     * Extracts file extension for sorting and type inspection.
     */
    public String getExtension() {
        if (isDirectory) return "";
        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0 && lastDot < name.length() - 1) {
            return name.substring(lastDot + 1).toLowerCase(Locale.ENGLISH);
        }
        return "";
    }

    /**
     * Checks if this item is a recognized audio file format.
     */
    public boolean isAudio() {
        if (isDirectory) return false;
        String ext = getExtension();
        return ext.equals("mp3") || ext.equals("wav") || ext.equals("m4a") ||
               ext.equals("aac") || ext.equals("flac") || ext.equals("ogg") ||
               ext.equals("opus") || ext.equals("wma") || ext.equals("mid") ||
               ext.equals("amr") || ext.equals("m4b") || ext.equals("aiff");
    }

    /**
     * Checks if this item is a recognized video file format.
     */
    public boolean isVideo() {
        if (isDirectory) return false;
        String ext = getExtension();
        return ext.equals("mp4") || ext.equals("mkv") || ext.equals("webm") ||
               ext.equals("avi") || ext.equals("3gp") || ext.equals("mov") ||
               ext.equals("flv") || ext.equals("wmv") || ext.equals("ts") ||
               ext.equals("m4v") || ext.equals("ogv") || ext.equals("asf");
    }

    /**
     * Formats file size or folder contents count for UI presentation.
     */
    public String getSubtitleText() {
        return getSubtitleText(null);
    }

    public String getSubtitleText(Context context) {
        if (isNetworkItem()) {
            String origin = customCategoryName != null ? customCategoryName : "Cloud";
            return isDirectory ? (origin + " folder") : (formatFileSize(length) + " \u2022 " + origin);
        }
        if (isDirectory) {
            boolean showCount = true;
            boolean showSize = true;
            int order = AppSettings.ORDER_NAME_COUNT_SIZE;
            if (context != null) {
                AppSettings settings = AppSettings.getInstance(context);
                showCount = settings.isAnnounceFolderCount();
                showSize = settings.isAnnounceFolderSize();
                order = settings.getFolderAnnounceOrder();
            }

            String countStr = null;
            if (showCount) {
                if (customChildCount >= 0) {
                    if (customCategoryName != null && !customCategoryName.isEmpty()) {
                        countStr = (customChildCount == 1) ? ("1 " + customCategoryName) : (customChildCount + " " + getPluralCategoryNoun(customCategoryName));
                    } else {
                        countStr = (customChildCount == 1) ? "1 item" : (customChildCount + " items");
                    }
                } else if (childCount >= 0) {
                    countStr = (childCount == 1) ? "1 item" : (childCount + " items");
                }
            }

            String sizeStr = null;
            if (showSize) {
                if (customLength >= 0) {
                    sizeStr = formatFileSize(customLength);
                } else if (length > 0) {
                    sizeStr = formatFileSize(length);
                }
            }

            List<String> parts = new ArrayList<>();
            boolean sizeBeforeCount = (order == AppSettings.ORDER_SIZE_NAME_COUNT ||
                    order == AppSettings.ORDER_SIZE_COUNT_NAME ||
                    order == AppSettings.ORDER_NAME_SIZE_COUNT);
            if (sizeBeforeCount) {
                if (sizeStr != null) parts.add(sizeStr);
                if (countStr != null) parts.add(countStr);
            } else {
                if (countStr != null) parts.add(countStr);
                if (sizeStr != null) parts.add(sizeStr);
            }

            if (parts.isEmpty()) {
                return "Folder";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.size(); i++) {
                if (i > 0) sb.append(" \u2022 ");
                sb.append(parts.get(i));
            }
            return sb.toString();
        } else {
            return formatFileSize(length);
        }
    }

    private static String getPluralCategoryNoun(String noun) {
        if (noun == null) return "items";
        String lower = noun.toLowerCase(Locale.ENGLISH);
        if (lower.equals("audio")) return "audio files";
        if (lower.equals("video")) return "videos";
        if (lower.equals("image")) return "images";
        if (lower.equals("document")) return "documents";
        if (lower.equals("apk")) return "APKs";
        if (lower.equals("file")) return "files";
        return noun + "s";
    }

    /**
     * Formats the last modified date into a human-readable English string.
     */
    public String getFormattedDate() {
        DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.ENGLISH);
        return dateFormat.format(new Date(lastModified));
    }

    /**
     * Formats raw bytes into standard human-readable sizes (B, KB, MB, GB).
     */
    public static String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        if (digitGroups >= units.length) {
            digitGroups = units.length - 1;
        }
        double value = bytes / Math.pow(1024, digitGroups);
        if (value >= 1023.95 && digitGroups < units.length - 1) {
            digitGroups++;
            value = bytes / Math.pow(1024, digitGroups);
        }
        return new DecimalFormat("#,##0.#").format(value) + " " + units[digitGroups];
    }

    /**
     * Generates a complete accessible speech string optimized for screen readers like TalkBack and Jieshuo.
     */
    public String getAccessibleDescription() {
        return getAccessibleDescription(null);
    }

    public String getAccessibleDescription(Context context) {
        String displayName = getDisplayName();
        if (isDirectory) {
            boolean showName = true;
            boolean showCount = true;
            boolean showSize = true;
            int order = AppSettings.ORDER_NAME_COUNT_SIZE;
            if (context != null) {
                AppSettings settings = AppSettings.getInstance(context);
                showName = settings.isAnnounceFolderName();
                showCount = settings.isAnnounceFolderCount();
                showSize = settings.isAnnounceFolderSize();
                order = settings.getFolderAnnounceOrder();
            }

            String countStr = null;
            if (showCount) {
                if (customChildCount >= 0) {
                    if (customCategoryName != null && !customCategoryName.isEmpty()) {
                        countStr = (customChildCount == 1) ? ("1 " + customCategoryName) : (customChildCount + " " + getPluralCategoryNoun(customCategoryName));
                    } else {
                        countStr = (customChildCount == 1) ? "1 item" : (customChildCount + " items");
                    }
                } else if (childCount >= 0) {
                    countStr = (childCount == 1) ? "1 item" : (childCount + " items");
                }
            }

            String sizeStr = null;
            if (showSize) {
                if (customLength >= 0) {
                    sizeStr = formatFileSize(customLength);
                } else if (length > 0) {
                    sizeStr = formatFileSize(length);
                }
            }

            String nameStr = showName ? displayName : null;

            List<String> segments = new ArrayList<>();
            switch (order) {
                case AppSettings.ORDER_NAME_SIZE_COUNT:
                    if (nameStr != null) segments.add(nameStr);
                    if (sizeStr != null) segments.add(sizeStr);
                    if (countStr != null) segments.add(countStr);
                    break;
                case AppSettings.ORDER_COUNT_NAME_SIZE:
                    if (countStr != null) segments.add(countStr);
                    if (nameStr != null) segments.add(nameStr);
                    if (sizeStr != null) segments.add(sizeStr);
                    break;
                case AppSettings.ORDER_COUNT_SIZE_NAME:
                    if (countStr != null) segments.add(countStr);
                    if (sizeStr != null) segments.add(sizeStr);
                    if (nameStr != null) segments.add(nameStr);
                    break;
                case AppSettings.ORDER_SIZE_NAME_COUNT:
                    if (sizeStr != null) segments.add(sizeStr);
                    if (nameStr != null) segments.add(nameStr);
                    if (countStr != null) segments.add(countStr);
                    break;
                case AppSettings.ORDER_SIZE_COUNT_NAME:
                    if (sizeStr != null) segments.add(sizeStr);
                    if (countStr != null) segments.add(countStr);
                    if (nameStr != null) segments.add(nameStr);
                    break;
                case AppSettings.ORDER_NAME_COUNT_SIZE:
                default:
                    if (nameStr != null) segments.add(nameStr);
                    if (countStr != null) segments.add(countStr);
                    if (sizeStr != null) segments.add(sizeStr);
                    break;
            }

            StringBuilder sb = new StringBuilder("Folder");
            for (String seg : segments) {
                sb.append(", ").append(seg);
            }
            sb.append(", Modified on ").append(getFormattedDate());

            boolean showUsageHints = (context == null) || AppSettings.getInstance(context).isShowUsageHints();
            if (showUsageHints) {
                sb.append(". Double-tap to open folder, double-tap and hold for more options.");
            }
            return sb.toString();
        } else {
            boolean showUsageHints = (context == null) || AppSettings.getInstance(context).isShowUsageHints();
            String desc = "File, " + displayName + ", Size: " + formatFileSize(length) + ", Modified on " + getFormattedDate();
            if (showUsageHints) {
                desc += ". Double-tap to open file, double-tap and hold for more options.";
            }
            return desc;
        }
    }

    @Override
    public int compareTo(FileItem other) {
        // Default sort: Folders first, then names A-Z
        if (this.isDirectory && !other.isDirectory) {
            return -1;
        } else if (!this.isDirectory && other.isDirectory) {
            return 1;
        }
        return this.getDisplayName().compareToIgnoreCase(other.getDisplayName());
    }
}
