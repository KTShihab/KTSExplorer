package com.kts.fileexplorer.cloud;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;

import androidx.documentfile.provider.DocumentFile;

import com.kts.fileexplorer.network.NetworkServerInfo;

/**
 * Manager for Cloud Storages (Google Drive, WebDAV, etc.).
 * Persists connected cloud accounts, access tokens, and provides easy helper methods.
 */
public class CloudStorageManager {

    private static final String PREF_NAME = "kts_cloud_storage";
    private static final String KEY_GDRIVE_URI = "gdrive_tree_uri";
    private static final String KEY_GDRIVE_NAME = "gdrive_display_name";
    private static final String KEY_GDRIVE_EMAIL = "gdrive_account_email";
    private static final String KEY_GDRIVE_TOKEN = "gdrive_access_token";

    private static CloudStorageManager instance;
    private final Context appContext;
    private final SharedPreferences prefs;

    private CloudStorageManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized CloudStorageManager getInstance(Context context) {
        if (instance == null) {
            instance = new CloudStorageManager(context);
        }
        return instance;
    }

    public boolean isGoogleDriveConnected() {
        // Connected via OAuth Account
        if (!TextUtils.isEmpty(getGoogleDriveAccountEmail())) {
            return true;
        }
        // Fallback: connected via SAF Tree
        String uriStr = prefs.getString(KEY_GDRIVE_URI, null);
        if (TextUtils.isEmpty(uriStr)) {
            return false;
        }
        try {
            Uri uri = Uri.parse(uriStr);
            DocumentFile df = DocumentFile.fromTreeUri(appContext, uri);
            return df != null && df.canRead();
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isOAuthConnected() {
        return !TextUtils.isEmpty(getGoogleDriveAccountEmail());
    }

    public String getGoogleDriveAccountEmail() {
        return prefs.getString(KEY_GDRIVE_EMAIL, null);
    }

    public String getGoogleDriveToken() {
        return prefs.getString(KEY_GDRIVE_TOKEN, null);
    }

    public void saveGoogleDriveAccount(String email, String displayName, String token) {
        prefs.edit()
                .putString(KEY_GDRIVE_EMAIL, email)
                .putString(KEY_GDRIVE_NAME, displayName)
                .putString(KEY_GDRIVE_TOKEN, token)
                .apply();
    }

    public void updateGoogleDriveToken(String token) {
        prefs.edit().putString(KEY_GDRIVE_TOKEN, token).apply();
    }

    public Uri getGoogleDriveUri() {
        String uriStr = prefs.getString(KEY_GDRIVE_URI, null);
        if (TextUtils.isEmpty(uriStr)) return null;
        try {
            return Uri.parse(uriStr);
        } catch (Exception e) {
            return null;
        }
    }

    public String getGoogleDriveDisplayName() {
        String email = getGoogleDriveAccountEmail();
        if (!TextUtils.isEmpty(email)) {
            String name = prefs.getString(KEY_GDRIVE_NAME, null);
            if (!TextUtils.isEmpty(name)) {
                return name + " (" + email + ")";
            }
            return email;
        }
        String name = prefs.getString(KEY_GDRIVE_NAME, null);
        if (!TextUtils.isEmpty(name)) return name;
        Uri uri = getGoogleDriveUri();
        if (uri != null) {
            try {
                DocumentFile df = DocumentFile.fromTreeUri(appContext, uri);
                if (df != null && !TextUtils.isEmpty(df.getName())) {
                    return df.getName();
                }
            } catch (Exception ignored) {}
        }
        return "Google Drive";
    }

    public void saveGoogleDrive(Uri treeUri, String displayName) {
        if (treeUri == null) return;
        try {
            appContext.getContentResolver().takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            );
        } catch (Exception ignored) {}

        prefs.edit()
                .putString(KEY_GDRIVE_URI, treeUri.toString())
                .putString(KEY_GDRIVE_NAME, displayName)
                .apply();
    }

    public void disconnectGoogleDrive() {
        // Clear SAF permissions if any
        Uri uri = getGoogleDriveUri();
        if (uri != null) {
            try {
                appContext.getContentResolver().releasePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                );
            } catch (Exception ignored) {}
        }

        // Clear OAuth token & Google Sign-In
        try {
            GoogleDriveService.getGoogleSignInClient(appContext).signOut();
        } catch (Exception ignored) {}

        prefs.edit()
                .remove(KEY_GDRIVE_URI)
                .remove(KEY_GDRIVE_NAME)
                .remove(KEY_GDRIVE_EMAIL)
                .remove(KEY_GDRIVE_TOKEN)
                .apply();
    }

    public NetworkServerInfo getGoogleDriveServerInfo() {
        String email = getGoogleDriveAccountEmail();
        Uri uri = getGoogleDriveUri();
        String host = !TextUtils.isEmpty(email) ? email : (uri != null ? uri.toString() : "Google Drive");

        NetworkServerInfo info = new NetworkServerInfo(
                getGoogleDriveDisplayName(),
                NetworkServerInfo.TYPE_GOOGLE_DRIVE,
                host,
                0,
                "",
                "",
                "",
                ""
        );
        info.setId("cloud_gdrive");
        return info;
    }
}
