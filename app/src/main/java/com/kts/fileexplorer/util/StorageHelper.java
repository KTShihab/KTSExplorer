package com.kts.fileexplorer.util;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;

import com.kts.fileexplorer.R;
import com.kts.fileexplorer.model.FileItem;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Helper class for discovering, querying, and formatting storage volumes
 * (Internal shared storage, SD Cards, USB OTG drives).
 */
public class StorageHelper {

    public static class StorageVolumeInfo {
        private final String id;
        private final String name;
        private final File directory;
        private final boolean isPrimary;
        private final boolean isRemovable;
        private final long totalBytes;
        private final long freeBytes;
        private final long usedBytes;
        private final int usedPercent;

        public StorageVolumeInfo(String id, String name, File directory, boolean isPrimary, boolean isRemovable, long totalBytes, long freeBytes) {
            this.id = id;
            this.name = name;
            this.directory = directory;
            this.isPrimary = isPrimary;
            this.isRemovable = isRemovable;
            this.totalBytes = totalBytes;
            this.freeBytes = freeBytes;
            this.usedBytes = Math.max(0, totalBytes - freeBytes);
            this.usedPercent = totalBytes > 0 ? (int) Math.min(100, Math.max(0, (this.usedBytes * 100) / totalBytes)) : 0;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public File getDirectory() {
            return directory;
        }

        public boolean isPrimary() {
            return isPrimary;
        }

        public boolean isRemovable() {
            return isRemovable;
        }

        public long getTotalBytes() {
            return totalBytes;
        }

        public long getFreeBytes() {
            return freeBytes;
        }

        public long getUsedBytes() {
            return usedBytes;
        }

        public int getUsedPercent() {
            return usedPercent;
        }

        public String getFormattedUsed() {
            return FileItem.formatFileSize(usedBytes);
        }

        public String getFormattedTotal() {
            return FileItem.formatFileSize(totalBytes);
        }

        public String getFormattedFree() {
            return FileItem.formatFileSize(freeBytes);
        }

        /**
         * Concise visual status for the toolbar header.
         * e.g. "348.7 GB used • 109.5 GB free"
         */
        public String getShortStatus() {
            if (totalBytes <= 0) return "";
            return getFormattedUsed() + " used • " + getFormattedFree() + " free";
        }

        /**
         * Full readable summary for selection dialogs.
         * e.g. "348.7 GB used / 458.2 GB (109.5 GB free)"
         */
        public String getFullStatus() {
            if (totalBytes <= 0) return "";
            return getFormattedUsed() + " used of " + getFormattedTotal() + " (" + getFormattedFree() + " free)";
        }
    }

    /**
     * Discovers all available storage volumes on the device.
     */
    public static List<StorageVolumeInfo> getStorageVolumes(Context context) {
        List<StorageVolumeInfo> result = new ArrayList<>();
        Set<String> seenPaths = new HashSet<>();

        // 1. Primary Internal Storage via Environment
        File primaryRoot = Environment.getExternalStorageDirectory();
        if (primaryRoot != null && primaryRoot.exists()) {
            String path = primaryRoot.getAbsolutePath();
            seenPaths.add(path);
            long total = 0, free = 0;
            try {
                StatFs stat = new StatFs(path);
                total = stat.getTotalBytes();
                free = stat.getAvailableBytes();
            } catch (Exception ignored) {}

            String internalName = context.getString(R.string.storage_title);
            result.add(new StorageVolumeInfo("internal", internalName, primaryRoot, true, false, total, free));
        }

        // 2. StorageManager volumes (API 30+)
        try {
            StorageManager sm = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
            if (sm != null) {
                List<StorageVolume> volumes = sm.getStorageVolumes();
                for (StorageVolume vol : volumes) {
                    if (vol.isPrimary()) continue; // Already added primary
                    String state = vol.getState();
                    if (!Environment.MEDIA_MOUNTED.equals(state) && !Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
                        continue;
                    }

                    File dir = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        dir = vol.getDirectory();
                    } else {
                        try {
                            Method getPathFile = vol.getClass().getMethod("getPathFile");
                            dir = (File) getPathFile.invoke(vol);
                        } catch (Exception e) {
                            try {
                                Method getPath = vol.getClass().getMethod("getPath");
                                String p = (String) getPath.invoke(vol);
                                if (p != null) dir = new File(p);
                            } catch (Exception ignored) {}
                        }
                    }

                    if (dir != null && dir.exists() && !seenPaths.contains(dir.getAbsolutePath())) {
                        seenPaths.add(dir.getAbsolutePath());
                        String name = vol.getDescription(context);
                        if (name == null || name.trim().isEmpty()) {
                            name = vol.isRemovable() ? "SD Card" : "External Storage";
                        }
                        long total = 0, free = 0;
                        try {
                            StatFs stat = new StatFs(dir.getAbsolutePath());
                            total = stat.getTotalBytes();
                            free = stat.getAvailableBytes();
                        } catch (Exception ignored) {}

                        String id = vol.getUuid() != null ? vol.getUuid() : dir.getName();
                        result.add(new StorageVolumeInfo(id, name, dir, false, vol.isRemovable(), total, free));
                    }
                }
            }
        } catch (Exception ignored) {}

        // 3. Fallback discovery via Context.getExternalFilesDirs(null)
        try {
            File[] extDirs = context.getExternalFilesDirs(null);
            if (extDirs != null) {
                for (File ext : extDirs) {
                    if (ext == null) continue;
                    String p = ext.getAbsolutePath();
                    int androidIdx = p.indexOf("/Android/");
                    if (androidIdx > 0) {
                        String rootP = p.substring(0, androidIdx);
                        if (!seenPaths.contains(rootP)) {
                            File rootF = new File(rootP);
                            if (rootF.exists() && rootF.canRead()) {
                                seenPaths.add(rootP);
                                long total = 0, free = 0;
                                try {
                                    StatFs stat = new StatFs(rootP);
                                    total = stat.getTotalBytes();
                                    free = stat.getAvailableBytes();
                                } catch (Exception ignored) {}

                                result.add(new StorageVolumeInfo(rootF.getName(), "SD Card (" + rootF.getName() + ")", rootF, false, true, total, free));
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return result;
    }

    /**
     * Identifies which storage volume the given directory belongs to.
     */
    public static StorageVolumeInfo getVolumeForDirectory(Context context, File directory) {
        List<StorageVolumeInfo> volumes = getStorageVolumes(context);
        if (directory == null || volumes.isEmpty()) {
            return volumes.isEmpty() ? null : volumes.get(0);
        }

        String targetPath = directory.getAbsolutePath();
        for (StorageVolumeInfo vol : volumes) {
            String rootPath = vol.getDirectory().getAbsolutePath();
            if (targetPath.equals(rootPath) || targetPath.startsWith(rootPath + File.separator) || targetPath.startsWith(rootPath + "/")) {
                return vol;
            }
        }

        return volumes.get(0);
    }
}
