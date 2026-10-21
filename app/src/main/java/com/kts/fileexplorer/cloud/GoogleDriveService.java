package com.kts.fileexplorer.cloud;

import android.accounts.Account;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.kts.fileexplorer.network.NetworkItem;
import com.kts.fileexplorer.network.NetworkServerInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Authenticator;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.Route;

/**
 * Native Google Drive REST API v3 Client with Google Sign-In OAuth2 integration.
 */
public class GoogleDriveService {

    private static final String TAG = "GoogleDriveService";
    public static final String SCOPE_DRIVE = "https://www.googleapis.com/auth/drive";
    public static final String MIME_TYPE_FOLDER = "application/vnd.google-apps.folder";

    private static GoogleDriveService instance;
    private final Context appContext;
    private final OkHttpClient httpClient;

    private GoogleDriveService(Context context) {
        this.appContext = context.getApplicationContext();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .authenticator(new Authenticator() {
                    @Nullable
                    @Override
                    public Request authenticate(@Nullable Route route, @NonNull Response response) throws IOException {
                        if (responseCount(response) >= 3) {
                            Log.w(TAG, "Authenticator exceeded retry limit");
                            return null;
                        }

                        synchronized (GoogleDriveService.this) {
                            CloudStorageManager csm = CloudStorageManager.getInstance(appContext);
                            String currentToken = csm.getGoogleDriveToken();
                            String authHeader = response.request().header("Authorization");
                            String failedToken = (authHeader != null && authHeader.startsWith("Bearer "))
                                    ? authHeader.substring(7) : null;

                            // If another concurrent request has already refreshed the token, retry with currentToken
                            if (!TextUtils.isEmpty(currentToken) && !currentToken.equals(failedToken)) {
                                return response.request().newBuilder()
                                        .header("Authorization", "Bearer " + currentToken)
                                        .build();
                            }

                            // Invalidate the failed token in Google Play Services cache
                            if (!TextUtils.isEmpty(failedToken)) {
                                clearTokenCache(failedToken);
                            }

                            // Fetch a fresh OAuth2 access token
                            String freshToken = refreshAccessToken();
                            if (!TextUtils.isEmpty(freshToken)) {
                                return response.request().newBuilder()
                                        .header("Authorization", "Bearer " + freshToken)
                                        .build();
                            }

                            return null;
                        }
                    }
                })
                .build();
    }

    private int responseCount(Response response) {
        int count = 1;
        while ((response = response.priorResponse()) != null) {
            count++;
        }
        return count;
    }

    public synchronized String refreshAccessToken() {
        CloudStorageManager csm = CloudStorageManager.getInstance(appContext);
        String oldToken = csm.getGoogleDriveToken();
        if (!TextUtils.isEmpty(oldToken)) {
            clearTokenCache(oldToken);
        }

        Account account = null;
        try {
            GoogleSignInAccount gsa = GoogleSignIn.getLastSignedInAccount(appContext);
            if (gsa != null && gsa.getAccount() != null) {
                account = gsa.getAccount();
            }
        } catch (Exception e) {
            Log.w(TAG, "Error getting last signed-in Google account: " + e.getMessage());
        }

        if (account == null) {
            String email = csm.getGoogleDriveAccountEmail();
            if (!TextUtils.isEmpty(email)) {
                account = new Account(email, "com.google");
            }
        }

        if (account == null) {
            Log.e(TAG, "Cannot refresh token: No Google Account registered");
            return null;
        }

        try {
            String scope = "oauth2:" + SCOPE_DRIVE;
            String newToken = GoogleAuthUtil.getToken(appContext, account, scope);
            if (!TextUtils.isEmpty(newToken)) {
                csm.updateGoogleDriveToken(newToken);
                Log.i(TAG, "Successfully refreshed Google Drive access token for " + account.name);
                return newToken;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to refresh Google Drive access token: " + e.getMessage(), e);
        }
        return null;
    }

    public synchronized String getOrRefreshAccessToken() throws IOException {
        CloudStorageManager csm = CloudStorageManager.getInstance(appContext);
        String token = csm.getGoogleDriveToken();
        if (!TextUtils.isEmpty(token)) {
            return token;
        }
        token = refreshAccessToken();
        if (!TextUtils.isEmpty(token)) {
            return token;
        }
        throw new IOException("Google Drive authentication required. Please connect your Google account.");
    }

    public static synchronized GoogleDriveService getInstance(Context context) {
        if (instance == null) {
            instance = new GoogleDriveService(context);
        }
        return instance;
    }

    public static GoogleSignInClient getGoogleSignInClient(Context context) {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(SCOPE_DRIVE))
                .build();
        return GoogleSignIn.getClient(context, gso);
    }

    public String fetchAccessToken(GoogleSignInAccount account) throws Exception {
        if (account == null || account.getAccount() == null) {
            throw new IllegalStateException("Google Account is null");
        }
        String scope = "oauth2:" + SCOPE_DRIVE;
        return GoogleAuthUtil.getToken(appContext, account.getAccount(), scope);
    }

    public void clearTokenCache(String token) {
        if (!TextUtils.isEmpty(token)) {
            try {
                GoogleAuthUtil.clearToken(appContext, token);
            } catch (Exception ignored) {}
        }
    }

    /**
     * List files and folders in a Google Drive folder.
     * @param folderId 'root' or specific folder ID
     * @param accessToken OAuth2 Bearer token
     */
    public List<NetworkItem> listFiles(String folderId, String accessToken) throws Exception {
        return listFiles(folderId, accessToken, null);
    }

    public List<NetworkItem> listFiles(String folderId, String accessToken, NetworkServerInfo serverInfo) throws Exception {
        if (TextUtils.isEmpty(folderId) || "/".equals(folderId)) {
            folderId = "root";
        }
        if (TextUtils.isEmpty(accessToken)) {
            accessToken = getOrRefreshAccessToken();
        }

        String query = "'" + folderId + "' in parents and trashed = false";
        String encodedQuery = URLEncoder.encode(query, "UTF-8");
        String url = "https://www.googleapis.com/drive/v3/files"
                + "?q=" + encodedQuery
                + "&fields=" + URLEncoder.encode("files(id,name,mimeType,size,modifiedTime)", "UTF-8")
                + "&pageSize=1000"
                + "&orderBy=" + URLEncoder.encode("folder,name", "UTF-8");

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + accessToken)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                Log.e(TAG, "List files failed: code=" + response.code() + ", error=" + errorBody);
                throw new IOException("Google Drive error (" + response.code() + "): " + errorBody);
            }

            String jsonStr = response.body() != null ? response.body().string() : "{}";
            JSONObject root = new JSONObject(jsonStr);
            JSONArray filesArray = root.optJSONArray("files");

            List<NetworkItem> items = new ArrayList<>();
            if (filesArray != null) {
                for (int i = 0; i < filesArray.length(); i++) {
                    JSONObject obj = filesArray.getJSONObject(i);
                    String id = obj.optString("id");
                    String name = obj.optString("name", "Untitled");
                    String mimeType = obj.optString("mimeType", "");
                    long size = obj.optLong("size", 0L);
                    String modifiedTimeStr = obj.optString("modifiedTime", "");

                    boolean isDir = MIME_TYPE_FOLDER.equalsIgnoreCase(mimeType);
                    long lastModified = parseDateToMillis(modifiedTimeStr);

                    items.add(new NetworkItem(name, id, "Google Drive", isDir, size, lastModified, serverInfo));
                }
            }

            // Sort: folders first, then alphabetical
            Collections.sort(items, (a, b) -> {
                if (a.isDirectory() != b.isDirectory()) {
                    return a.isDirectory() ? -1 : 1;
                }
                return a.getName().compareToIgnoreCase(b.getName());
            });

            return items;
        }
    }

    private long parseDateToMillis(String isoDate) {
        if (TextUtils.isEmpty(isoDate)) return System.currentTimeMillis();
        try {
            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            Date date = isoFormat.parse(isoDate);
            if (date != null) return date.getTime();
        } catch (Exception ignored) {}
        return System.currentTimeMillis();
    }

    /**
     * Download a file from Google Drive.
     */
    public boolean downloadFile(String fileId, File destFile, String accessToken) throws Exception {
        if (TextUtils.isEmpty(accessToken)) {
            accessToken = getOrRefreshAccessToken();
        }
        String url = "https://www.googleapis.com/drive/v3/files/" + fileId + "?alt=media";
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + accessToken)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Download failed (" + response.code() + ")");
            }

            ResponseBody body = response.body();
            if (body == null) return false;

            if (destFile.getParentFile() != null && !destFile.getParentFile().exists()) {
                destFile.getParentFile().mkdirs();
            }

            try (InputStream in = body.byteStream();
                 FileOutputStream out = new FileOutputStream(destFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
            }
            return true;
        }
    }

    public interface UploadProgressListener {
        void onProgress(long bytesWritten, long totalBytes);
    }

    public static class ProgressRequestBody extends RequestBody {
        private final File file;
        private final MediaType contentType;
        private final UploadProgressListener listener;
        private final java.util.concurrent.atomic.AtomicBoolean isCancelled;

        public ProgressRequestBody(File file, MediaType contentType, UploadProgressListener listener, java.util.concurrent.atomic.AtomicBoolean isCancelled) {
            this.file = file;
            this.contentType = contentType;
            this.listener = listener;
            this.isCancelled = isCancelled;
        }

        @Override
        public MediaType contentType() {
            return contentType;
        }

        @Override
        public long contentLength() {
            return file.length();
        }

        @Override
        public void writeTo(okio.BufferedSink sink) throws IOException {
            long totalBytes = file.length();
            long bytesWritten = 0;
            try (okio.Source source = okio.Okio.source(file)) {
                okio.Buffer buffer = new okio.Buffer();
                long read;
                long lastTime = 0;
                while ((read = source.read(buffer, 16384)) != -1) {
                    if (isCancelled != null && isCancelled.get()) {
                        throw new IOException("Upload cancelled by user");
                    }
                    sink.write(buffer, read);
                    bytesWritten += read;
                    long now = System.currentTimeMillis();
                    if (listener != null && (now - lastTime >= 100 || bytesWritten == totalBytes)) {
                        lastTime = now;
                        listener.onProgress(bytesWritten, totalBytes);
                    }
                }
            }
        }
    }

    /**
     * Upload a local file to Google Drive folder.
     */
    public boolean uploadFile(String parentFolderId, File localFile, String accessToken) throws Exception {
        return uploadFile(parentFolderId, localFile, accessToken, null, null);
    }

    public boolean uploadFile(String parentFolderId, File localFile, String accessToken, UploadProgressListener listener, java.util.concurrent.atomic.AtomicBoolean isCancelled) throws Exception {
        if (isCancelled != null && isCancelled.get()) return false;
        if (TextUtils.isEmpty(parentFolderId) || "/".equals(parentFolderId)) {
            parentFolderId = "root";
        }
        if (TextUtils.isEmpty(accessToken)) {
            accessToken = getOrRefreshAccessToken();
        }

        String metadataJson = "{\"name\":\"" + escapeJson(localFile.getName()) + "\",\"parents\":[\"" + parentFolderId + "\"]}";
        MediaType jsonType = MediaType.parse("application/json; charset=UTF-8");
        RequestBody metadataBody = RequestBody.create(metadataJson, jsonType);

        MediaType mediaType = MediaType.parse("application/octet-stream");
        RequestBody fileBody = (listener != null || isCancelled != null)
                ? new ProgressRequestBody(localFile, mediaType, listener, isCancelled)
                : RequestBody.create(localFile, mediaType);

        MultipartBody multipartBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("metadata", null, metadataBody)
                .addFormDataPart("file", localFile.getName(), fileBody)
                .build();

        String url = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart";
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + accessToken)
                .post(multipartBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            return response.isSuccessful();
        }
    }

    /**
     * Create a new folder inside parentFolderId and return its Google Drive ID.
     */
    public String createFolderAndGetId(String parentFolderId, String folderName, String accessToken) throws Exception {
        if (TextUtils.isEmpty(parentFolderId) || "/".equals(parentFolderId)) {
            parentFolderId = "root";
        }
        if (TextUtils.isEmpty(accessToken)) {
            accessToken = getOrRefreshAccessToken();
        }

        String json = "{\"name\":\"" + escapeJson(folderName) + "\",\"mimeType\":\"" + MIME_TYPE_FOLDER + "\",\"parents\":[\"" + parentFolderId + "\"]}";
        MediaType jsonType = MediaType.parse("application/json; charset=UTF-8");
        RequestBody body = RequestBody.create(json, jsonType);

        String url = "https://www.googleapis.com/drive/v3/files";
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + accessToken)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                String respStr = response.body().string();
                JSONObject obj = new JSONObject(respStr);
                return obj.optString("id", null);
            }
            return null;
        }
    }

    /**
     * Upload a local file or recursive directory to Google Drive folder.
     */
    public boolean uploadFileOrDirectory(String parentFolderId, File localFileOrDir, String accessToken) throws Exception {
        return uploadFileOrDirectory(parentFolderId, localFileOrDir, accessToken, null, null);
    }

    public boolean uploadFileOrDirectory(String parentFolderId, File localFileOrDir, String accessToken, UploadProgressListener listener, java.util.concurrent.atomic.AtomicBoolean isCancelled) throws Exception {
        if (isCancelled != null && isCancelled.get()) return false;
        if (localFileOrDir == null || !localFileOrDir.exists()) return false;
        if (!localFileOrDir.isDirectory()) {
            return uploadFile(parentFolderId, localFileOrDir, accessToken, listener, isCancelled);
        }

        String newFolderId = createFolderAndGetId(parentFolderId, localFileOrDir.getName(), accessToken);
        if (TextUtils.isEmpty(newFolderId)) return false;

        File[] children = localFileOrDir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (isCancelled != null && isCancelled.get()) return false;
                uploadFileOrDirectory(newFolderId, child, accessToken, listener, isCancelled);
            }
        }
        return true;
    }

    /**
     * Create a new folder inside parentFolderId.
     */
    public boolean createFolder(String parentFolderId, String folderName, String accessToken) throws Exception {
        if (TextUtils.isEmpty(parentFolderId) || "/".equals(parentFolderId)) {
            parentFolderId = "root";
        }
        if (TextUtils.isEmpty(accessToken)) {
            accessToken = getOrRefreshAccessToken();
        }

        String json = "{\"name\":\"" + escapeJson(folderName) + "\",\"mimeType\":\"" + MIME_TYPE_FOLDER + "\",\"parents\":[\"" + parentFolderId + "\"]}";
        MediaType jsonType = MediaType.parse("application/json; charset=UTF-8");
        RequestBody body = RequestBody.create(json, jsonType);

        String url = "https://www.googleapis.com/drive/v3/files";
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + accessToken)
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            return response.isSuccessful();
        }
    }

    /**
     * Delete a file or folder by ID.
     */
    public boolean deleteItem(String fileId, String accessToken) throws Exception {
        if (TextUtils.isEmpty(accessToken)) {
            accessToken = getOrRefreshAccessToken();
        }
        String url = "https://www.googleapis.com/drive/v3/files/" + fileId;
        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + accessToken)
                .delete()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            return response.isSuccessful();
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String formatFileSize(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        if (digitGroups >= units.length) digitGroups = units.length - 1;
        return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    private String formatDate(String isoDate) {
        if (TextUtils.isEmpty(isoDate)) return "";
        try {
            // ISO 8601: 2026-10-19T14:32:00.000Z
            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            Date date = isoFormat.parse(isoDate);
            if (date != null) {
                SimpleDateFormat outFormat = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
                return outFormat.format(date);
            }
        } catch (Exception ignored) {}
        return isoDate;
    }
}
