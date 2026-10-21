package com.kts.fileexplorer.network;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages saved network connections (SMB & FTP servers).
 */
public class NetworkServerManager {

    private static final String PREF_NAME = "kts_network_servers";
    private static final String KEY_SERVERS = "saved_servers";

    private static NetworkServerManager instance;
    private final SharedPreferences prefs;

    private NetworkServerManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized NetworkServerManager getInstance(Context context) {
        if (instance == null) {
            instance = new NetworkServerManager(context);
        }
        return instance;
    }

    public List<NetworkServerInfo> getServers() {
        List<NetworkServerInfo> list = new ArrayList<>();
        String jsonStr = prefs.getString(KEY_SERVERS, null);
        if (jsonStr != null && !jsonStr.trim().isEmpty()) {
            try {
                JSONArray array = new JSONArray(jsonStr);
                for (int i = 0; i < array.length(); i++) {
                    list.add(NetworkServerInfo.fromJsonObject(array.getJSONObject(i)));
                }
            } catch (JSONException ignored) {}
        }
        return list;
    }

    public void saveServer(NetworkServerInfo server) {
        if (server == null) return;
        List<NetworkServerInfo> list = getServers();
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(server.getId())) {
                list.set(i, server);
                found = true;
                break;
            }
        }
        if (!found) {
            list.add(server);
        }
        saveAll(list);
    }

    public void deleteServer(String serverId) {
        if (serverId == null) return;
        List<NetworkServerInfo> list = getServers();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(serverId)) {
                list.remove(i);
                break;
            }
        }
        saveAll(list);
    }

    private void saveAll(List<NetworkServerInfo> list) {
        JSONArray array = new JSONArray();
        for (NetworkServerInfo s : list) {
            array.put(s.toJsonObject());
        }
        prefs.edit().putString(KEY_SERVERS, array.toString()).apply();
    }
}
