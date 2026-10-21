package com.kts.fileexplorer.util;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Manages GitHub Releases In-App Updates for KTS File Explorer.
 * Checks for updates from GitHub repository KTShihab/KTSExplorer,
 * downloads release APKs, and guides the user through installation.
 */
public class AppUpdateManager {

    private static final String TAG = "AppUpdateManager";
    private static final String PREF_NAME = "kts_explorer_update_prefs";
    private static final String KEY_REPO_SLUG = "github_update_repo_slug";
    private static final String KEY_AUTO_CHECK = "auto_check_updates_enabled";
    private static final String KEY_LAST_CHECK = "last_update_check_time_ms";

    public static final String DEFAULT_REPO_SLUG = "KTShihab/KTSExplorer";

    private static AppUpdateManager instance;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private AppUpdateManager() {}

    public static synchronized AppUpdateManager getInstance() {
        if (instance == null) {
            instance = new AppUpdateManager();
        }
        return instance;
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static String getRepoSlug(Context context) {
        if (context != null) {
            try {
                return getPrefs(context).getString(KEY_REPO_SLUG, DEFAULT_REPO_SLUG);
            } catch (Exception ignored) {}
        }
        return DEFAULT_REPO_SLUG;
    }

    public static void setRepoSlug(Context context, String slug) {
        if (context == null) return;
        try {
            getPrefs(context).edit().putString(KEY_REPO_SLUG, slug != null ? slug.trim() : DEFAULT_REPO_SLUG).apply();
        } catch (Exception ignored) {}
    }

    public static boolean isAutoCheckEnabled(Context context) {
        if (context == null) return true;
        try {
            return getPrefs(context).getBoolean(KEY_AUTO_CHECK, true);
        } catch (Exception e) {
            return true;
        }
    }

    public static void setAutoCheckEnabled(Context context, boolean enabled) {
        if (context == null) return;
        try {
            getPrefs(context).edit().putBoolean(KEY_AUTO_CHECK, enabled).apply();
        } catch (Exception ignored) {}
    }

    public static String getCurrentVersionName(Context context) {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pInfo.versionName != null ? pInfo.versionName : "1.0";
        } catch (Exception e) {
            return "1.0";
        }
    }

    public static class ReleaseInfo {
        public String tagName;
        public String title;
        public String body;
        public String apkUrl;
        public long apkSize;
        public boolean isNewer;
    }

    /**
     * Checks for updates silently in the background on startup (once every 24 hours).
     */
    public void checkOnStartup(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        if (!isAutoCheckEnabled(activity)) return;

        long lastCheck = getPrefs(activity).getLong(KEY_LAST_CHECK, 0);
        long now = System.currentTimeMillis();
        // Check once every 24 hours
        if (now - lastCheck < 24 * 60 * 60 * 1000L) {
            return;
        }

        checkForUpdates(activity, false);
    }

    /**
     * Checks for updates from GitHub releases.
     * @param activity current Activity
     * @param isManualCheck true if triggered explicitly by user action
     */
    public void checkForUpdates(Activity activity, boolean isManualCheck) {
        if (activity == null || activity.isFinishing()) return;

        ProgressDialog progressDialog;
        if (isManualCheck) {
            progressDialog = new ProgressDialog(activity);
            progressDialog.setTitle("Checking for Updates");
            progressDialog.setMessage("Connecting to GitHub to check for the latest release...");
            progressDialog.setCancelable(false);
            progressDialog.show();
        } else {
            progressDialog = null;
        }

        String repoSlug = getRepoSlug(activity);
        String currentVersion = getCurrentVersionName(activity);

        executor.execute(() -> {
            try {
                String apiUrl = "https://api.github.com/repos/" + repoSlug + "/releases/latest";
                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "KTS-FileExplorer");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                int responseCode = conn.getResponseCode();

                if (responseCode == 404) {
                    mainHandler.post(() -> {
                        if (progressDialog != null && progressDialog.isShowing()) progressDialog.dismiss();
                        if (isManualCheck && !activity.isFinishing()) {
                            Toast.makeText(activity, "No releases found on GitHub repository.", Toast.LENGTH_SHORT).show();
                        }
                    });
                    return;
                }

                if (responseCode != 200) {
                    mainHandler.post(() -> {
                        if (progressDialog != null && progressDialog.isShowing()) progressDialog.dismiss();
                        if (isManualCheck && !activity.isFinishing()) {
                            Toast.makeText(activity, "GitHub API returned response code: " + responseCode, Toast.LENGTH_LONG).show();
                        }
                    });
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                JSONObject json = new JSONObject(sb.toString());
                String tagName = json.optString("tag_name", "");
                String releaseTitle = json.optString("name", tagName);
                String releaseBody = json.optString("body", "Bug fixes and improvements.");

                JSONArray assets = json.optJSONArray("assets");
                String apkDownloadUrl = null;
                long apkSize = 0;

                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        String name = asset.optString("name", "");
                        if (name.toLowerCase().endsWith(".apk")) {
                            apkDownloadUrl = asset.optString("browser_download_url", null);
                            apkSize = asset.optLong("size", 0);
                            break;
                        }
                    }
                }

                ReleaseInfo info = new ReleaseInfo();
                info.tagName = tagName;
                info.title = releaseTitle;
                info.body = releaseBody;
                info.apkUrl = apkDownloadUrl;
                info.apkSize = apkSize;
                info.isNewer = isVersionNewer(tagName, currentVersion);

                try {
                    getPrefs(activity).edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply();
                } catch (Exception ignored) {}

                mainHandler.post(() -> {
                    if (progressDialog != null && progressDialog.isShowing()) progressDialog.dismiss();
                    if (activity.isFinishing()) return;

                    if (info.isNewer) {
                        showUpdateAvailableDialog(activity, info, currentVersion);
                    } else {
                        if (isManualCheck) {
                            String title = "App Up To Date";
                            String msg = "You are using the latest version of KTS File Explorer!\n\nInstalled Version: v" + currentVersion + "\nLatest Release: " + tagName;

                            new MaterialAlertDialogBuilder(activity)
                                    .setTitle(title)
                                    .setMessage(msg)
                                    .setPositiveButton("OK", null)
                                    .show();
                        }
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Update check failed", e);
                mainHandler.post(() -> {
                    if (progressDialog != null && progressDialog.isShowing()) progressDialog.dismiss();
                    if (isManualCheck && !activity.isFinishing()) {
                        Toast.makeText(activity, "Could not check updates: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void showUpdateAvailableDialog(Activity activity, ReleaseInfo info, String currentVersion) {
        StringBuilder msg = new StringBuilder();
        msg.append("A new version of KTS File Explorer is available!\n\n");
        msg.append("Current Version: v").append(currentVersion).append("\n");
        msg.append("Latest Version: ").append(info.tagName).append("\n");
        if (info.apkSize > 0) {
            msg.append("Download Size: ").append(formatFileSize(info.apkSize)).append("\n");
        }
        msg.append("\nChangelog:\n").append(info.body).append("\n");

        new MaterialAlertDialogBuilder(activity)
                .setTitle("Update Available: " + info.tagName)
                .setMessage(msg.toString())
                .setPositiveButton("Download & Install", (dialog, which) -> {
                    if (info.apkUrl != null && !info.apkUrl.isEmpty()) {
                        downloadAndInstallApk(activity, info.apkUrl, info.tagName);
                    } else {
                        Toast.makeText(activity, "No APK asset attached to this GitHub release.", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Later", null)
                .show();
    }

    public void downloadAndInstallApk(Activity activity, String downloadUrl, String versionName) {
        if (activity == null || activity.isFinishing()) return;

        ProgressDialog downloadDialog = new ProgressDialog(activity);
        downloadDialog.setTitle("Downloading Update");
        downloadDialog.setMessage("Downloading KTS File Explorer " + versionName + "...\nPlease wait...");
        downloadDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        downloadDialog.setMax(100);
        downloadDialog.setProgress(0);
        downloadDialog.setCancelable(false);
        downloadDialog.show();

        executor.execute(() -> {
            File targetApk = null;
            try {
                URL url = new URL(downloadUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "KTS-FileExplorer");
                conn.setInstanceFollowRedirects(true);
                conn.connect();

                int status = conn.getResponseCode();
                if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
                    String newUrl = conn.getHeaderField("Location");
                    conn.disconnect();
                    conn = (HttpURLConnection) new URL(newUrl).openConnection();
                    conn.setRequestProperty("User-Agent", "KTS-FileExplorer");
                    conn.connect();
                }

                int fileLength = conn.getContentLength();
                File downloadDir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (downloadDir == null) downloadDir = activity.getCacheDir();
                targetApk = new File(downloadDir, "KTSExplorer-" + versionName.replaceAll("[^a-zA-Z0-9.-]", "_") + ".apk");

                InputStream input = conn.getInputStream();
                FileOutputStream output = new FileOutputStream(targetApk);

                byte[] data = new byte[8192];
                long total = 0;
                int count;
                long lastProgressTime = 0;

                while ((count = input.read(data)) != -1) {
                    total += count;
                    output.write(data, 0, count);

                    long now = System.currentTimeMillis();
                    if (fileLength > 0 && now - lastProgressTime > 150) {
                        lastProgressTime = now;
                        int progress = (int) (total * 100 / fileLength);
                        mainHandler.post(() -> downloadDialog.setProgress(progress));
                    }
                }

                output.flush();
                output.close();
                input.close();
                conn.disconnect();

                File finalFile = targetApk;
                mainHandler.post(() -> {
                    if (downloadDialog.isShowing()) downloadDialog.dismiss();
                    installApk(activity, finalFile);
                });

            } catch (Exception e) {
                Log.e(TAG, "Download error", e);
                mainHandler.post(() -> {
                    if (downloadDialog.isShowing()) downloadDialog.dismiss();
                    Toast.makeText(activity, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    public static void installApk(Activity activity, File apkFile) {
        if (activity == null || apkFile == null || !apkFile.exists()) {
            Toast.makeText(activity, "APK file not found.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!activity.getPackageManager().canRequestPackageInstalls()) {
                    new MaterialAlertDialogBuilder(activity)
                            .setTitle("Permission Required")
                            .setMessage("To install the update, please enable 'Allow from this source' for KTS File Explorer in Settings.")
                            .setPositiveButton("Open Settings", (dialog, which) -> {
                                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                                activity.startActivity(intent);
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                    return;
                }
            }

            Uri apkUri = FileProvider.getUriForFile(
                    activity,
                    activity.getPackageName() + ".provider",
                    apkFile
            );

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            activity.startActivity(intent);

        } catch (Exception e) {
            Log.e(TAG, "Install launch failed", e);
            Toast.makeText(activity, "Install error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    public static boolean isVersionNewer(String remoteTag, String currentVersion) {
        if (remoteTag == null || currentVersion == null) return false;

        String cleanRemote = remoteTag.replaceAll("^[vV]", "").trim();
        String cleanCurrent = currentVersion.replaceAll("^[vV]", "").trim();

        String[] remoteParts = cleanRemote.split("[.-]");
        String[] currentParts = cleanCurrent.split("[.-]");

        int length = Math.max(remoteParts.length, currentParts.length);
        for (int i = 0; i < length; i++) {
            int remoteNum = 0;
            int currentNum = 0;

            if (i < remoteParts.length) {
                try { remoteNum = Integer.parseInt(remoteParts[i]); } catch (Exception ignored) {}
            }
            if (i < currentParts.length) {
                try { currentNum = Integer.parseInt(currentParts[i]); } catch (Exception ignored) {}
            }

            if (remoteNum > currentNum) return true;
            if (remoteNum < currentNum) return false;
        }

        return false;
    }

    private static String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
