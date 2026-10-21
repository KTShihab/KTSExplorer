package com.kts.fileexplorer.network;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.UUID;

/**
 * Model representing a network server connection (SMB Windows Share or FTP).
 */
public class NetworkServerInfo implements Serializable {

    public static final String TYPE_SMB = "SMB";
    public static final String TYPE_FTP = "FTP";
    public static final String TYPE_GOOGLE_DRIVE = "GDRIVE";
    public static final String TYPE_WEBDAV = "WEBDAV";

    private String id;
    private String name;
    private String type; // SMB or FTP
    private String host;
    private int port;
    private String username;
    private String password;
    private String domain;
    private String shareName; // Optional pre-set share for SMB

    public NetworkServerInfo() {
        this.id = UUID.randomUUID().toString();
        this.type = TYPE_SMB;
        this.port = 445;
        this.domain = "WORKGROUP";
    }

    public NetworkServerInfo(String name, String type, String host, int port, String username, String password, String domain, String shareName) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.type = type;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.domain = domain;
        this.shareName = shareName;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return (name != null && !name.trim().isEmpty()) ? name : (host != null ? host : "Computer");
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type != null ? type : TYPE_SMB;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getHost() {
        return host != null ? host : "";
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        if (port <= 0) {
            return TYPE_FTP.equalsIgnoreCase(type) ? 21 : 445;
        }
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username != null ? username : "";
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password != null ? password : "";
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDomain() {
        return (domain != null && !domain.trim().isEmpty()) ? domain : "WORKGROUP";
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getShareName() {
        return shareName != null ? shareName : "";
    }

    public void setShareName(String shareName) {
        this.shareName = shareName;
    }

    public JSONObject toJsonObject() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("name", name);
            json.put("type", type);
            json.put("host", host);
            json.put("port", port);
            json.put("username", username);
            json.put("password", password);
            json.put("domain", domain);
            json.put("shareName", shareName);
        } catch (JSONException ignored) {}
        return json;
    }

    public static NetworkServerInfo fromJsonObject(JSONObject json) {
        NetworkServerInfo info = new NetworkServerInfo();
        if (json == null) return info;
        info.setId(json.optString("id", UUID.randomUUID().toString()));
        info.setName(json.optString("name", ""));
        info.setType(json.optString("type", TYPE_SMB));
        info.setHost(json.optString("host", ""));
        info.setPort(json.optInt("port", 445));
        info.setUsername(json.optString("username", ""));
        info.setPassword(json.optString("password", ""));
        info.setDomain(json.optString("domain", "WORKGROUP"));
        info.setShareName(json.optString("shareName", ""));
        return info;
    }
}
