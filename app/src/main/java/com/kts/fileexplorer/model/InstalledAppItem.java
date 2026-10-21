package com.kts.fileexplorer.model;

import android.graphics.drawable.Drawable;

import java.io.File;
import java.text.DecimalFormat;

/**
 * Model representing an installed application on the device.
 */
public class InstalledAppItem implements Comparable<InstalledAppItem> {

    private final String appName;
    private final String packageName;
    private final String versionName;
    private final long appSize;
    private final boolean isSystemApp;
    private final String sourceDir;
    private Drawable icon;

    public InstalledAppItem(String appName, String packageName, String versionName,
                            long appSize, boolean isSystemApp, String sourceDir, Drawable icon) {
        this.appName = (appName != null && !appName.trim().isEmpty()) ? appName : packageName;
        this.packageName = packageName;
        this.versionName = (versionName != null) ? versionName : "1.0";
        this.appSize = appSize;
        this.isSystemApp = isSystemApp;
        this.sourceDir = sourceDir;
        this.icon = icon;
    }

    public String getAppName() {
        return appName;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getVersionName() {
        return versionName;
    }

    public long getAppSize() {
        return appSize;
    }

    public long getApkSize() {
        return appSize;
    }

    public boolean isSystemApp() {
        return isSystemApp;
    }

    public String getSourceDir() {
        return sourceDir;
    }

    public Drawable getIcon() {
        return icon;
    }

    public void setIcon(Drawable icon) {
        this.icon = icon;
    }

    public File getApkFile() {
        if (sourceDir != null) {
            File f = new File(sourceDir);
            if (f.exists()) return f;
        }
        return null;
    }

    public String getFormattedSize() {
        if (appSize <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(appSize) / Math.log10(1024));
        if (digitGroups >= units.length) digitGroups = units.length - 1;
        return new DecimalFormat("#,##0.#").format(appSize / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    public String getAccessibilityDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(appName).append(". ");
        sb.append(isSystemApp ? "System application. " : "User application. ");
        sb.append("Version ").append(versionName).append(". ");
        sb.append("Size ").append(getFormattedSize()).append(". ");
        sb.append("Double-tap to open actions menu.");
        return sb.toString();
    }

    @Override
    public int compareTo(InstalledAppItem o) {
        return this.appName.compareToIgnoreCase(o.appName);
    }
}
