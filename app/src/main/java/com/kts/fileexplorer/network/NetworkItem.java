package com.kts.fileexplorer.network;

import java.io.Serializable;
import java.util.Locale;

/**
 * Model representing a file or folder on a remote network computer (SMB/FTP).
 */
public class NetworkItem implements Serializable {

    private String name;
    private String path; // relative path within the share/server
    private String shareName; // SMB share name (e.g. "Users", "Shared", "C$")
    private boolean isDirectory;
    private long size;
    private long lastModified;
    private NetworkServerInfo serverInfo;

    public NetworkItem(String name, String path, String shareName, boolean isDirectory, long size, long lastModified, NetworkServerInfo serverInfo) {
        this.name = name;
        this.path = path;
        this.shareName = shareName;
        this.isDirectory = isDirectory;
        this.size = size;
        this.lastModified = lastModified;
        this.serverInfo = serverInfo;
    }

    public String getName() {
        return name != null ? name : "";
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path != null ? path : "";
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getShareName() {
        return shareName != null ? shareName : "";
    }

    public void setShareName(String shareName) {
        this.shareName = shareName;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public void setDirectory(boolean directory) {
        isDirectory = directory;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getLastModified() {
        return lastModified;
    }

    public void setLastModified(long lastModified) {
        this.lastModified = lastModified;
    }

    public NetworkServerInfo getServerInfo() {
        return serverInfo;
    }

    public void setServerInfo(NetworkServerInfo serverInfo) {
        this.serverInfo = serverInfo;
    }

    public String getExtension() {
        if (isDirectory || name == null) return "";
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            return name.substring(dot).toLowerCase(Locale.ENGLISH);
        }
        return "";
    }

    public String getFormattedSize() {
        if (isDirectory) return "";
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        if (digitGroups >= units.length) digitGroups = units.length - 1;
        return String.format(Locale.ENGLISH, "%.1f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    public org.json.JSONObject toJsonObject() {
        org.json.JSONObject json = new org.json.JSONObject();
        try {
            json.put("name", name);
            json.put("path", path);
            json.put("shareName", shareName);
            json.put("isDirectory", isDirectory);
            json.put("size", size);
            json.put("lastModified", lastModified);
            if (serverInfo != null) {
                json.put("serverInfo", serverInfo.toJsonObject());
            }
        } catch (org.json.JSONException ignored) {}
        return json;
    }

    public static NetworkItem fromJsonObject(org.json.JSONObject json) {
        if (json == null) return null;
        String name = json.optString("name", "");
        String path = json.optString("path", "");
        String shareName = json.optString("shareName", "");
        boolean isDir = json.optBoolean("isDirectory", false);
        long size = json.optLong("size", 0L);
        long lastMod = json.optLong("lastModified", 0L);
        NetworkServerInfo serverInfo = null;
        if (json.has("serverInfo")) {
            serverInfo = NetworkServerInfo.fromJsonObject(json.optJSONObject("serverInfo"));
        }
        return new NetworkItem(name, path, shareName, isDir, size, lastMod, serverInfo);
    }
}
