package com.kts.fileexplorer.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.kts.fileexplorer.model.FileItem;
import com.kts.fileexplorer.network.NetworkItem;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Utility to manage starred and favorite files and directories persistently,
 * supporting both local device storage and remote / Google Drive cloud items.
 */
public class FavoriteManager {

    private static final String PREF_NAME = "kts_favorites_pref";
    private static final String KEY_FAVORITE_PATHS = "favorite_paths";
    private static final String KEY_FAVORITE_NETWORK = "favorite_network_items";

    private static FavoriteManager instance;
    private final SharedPreferences preferences;

    private FavoriteManager(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized FavoriteManager getInstance(Context context) {
        if (instance == null) {
            instance = new FavoriteManager(context);
        }
        return instance;
    }

    public synchronized boolean isFavorite(String path) {
        if (path == null) return false;
        Set<String> set = preferences.getStringSet(KEY_FAVORITE_PATHS, Collections.emptySet());
        if (set.contains(path)) return true;

        Set<String> netSet = preferences.getStringSet(KEY_FAVORITE_NETWORK, Collections.emptySet());
        for (String jsonStr : netSet) {
            try {
                JSONObject obj = new JSONObject(jsonStr);
                if (path.equals(obj.optString("path"))) {
                    return true;
                }
            } catch (Exception ignored) {}
        }
        return false;
    }

    public synchronized boolean toggleFavorite(String path) {
        if (path == null) return false;
        Set<String> set = new HashSet<>(preferences.getStringSet(KEY_FAVORITE_PATHS, Collections.emptySet()));
        boolean isNowFavorite;
        if (set.contains(path)) {
            set.remove(path);
            isNowFavorite = false;
        } else {
            set.add(path);
            isNowFavorite = true;
        }
        preferences.edit().putStringSet(KEY_FAVORITE_PATHS, set).apply();
        return isNowFavorite;
    }

    public synchronized boolean toggleFavorite(NetworkItem item) {
        if (item == null || item.getPath() == null) return false;
        Set<String> netSet = new HashSet<>(preferences.getStringSet(KEY_FAVORITE_NETWORK, Collections.emptySet()));
        String foundJson = null;
        for (String jsonStr : netSet) {
            try {
                JSONObject obj = new JSONObject(jsonStr);
                if (item.getPath().equals(obj.optString("path"))) {
                    foundJson = jsonStr;
                    break;
                }
            } catch (Exception ignored) {}
        }

        boolean isNowFavorite;
        if (foundJson != null) {
            netSet.remove(foundJson);
            isNowFavorite = false;
        } else {
            netSet.add(item.toJsonObject().toString());
            isNowFavorite = true;
        }
        preferences.edit().putStringSet(KEY_FAVORITE_NETWORK, netSet).apply();
        return isNowFavorite;
    }

    public synchronized void addFavorite(String path) {
        if (path == null) return;
        Set<String> set = new HashSet<>(preferences.getStringSet(KEY_FAVORITE_PATHS, Collections.emptySet()));
        set.add(path);
        preferences.edit().putStringSet(KEY_FAVORITE_PATHS, set).apply();
    }

    public synchronized void addFavorite(NetworkItem item) {
        if (item == null || item.getPath() == null) return;
        Set<String> netSet = new HashSet<>(preferences.getStringSet(KEY_FAVORITE_NETWORK, Collections.emptySet()));
        netSet.removeIf(s -> {
            try {
                return item.getPath().equals(new JSONObject(s).optString("path"));
            } catch (Exception e) {
                return false;
            }
        });
        netSet.add(item.toJsonObject().toString());
        preferences.edit().putStringSet(KEY_FAVORITE_NETWORK, netSet).apply();
    }

    public synchronized void removeFavorite(String path) {
        if (path == null) return;
        // 1. Remove from local paths
        Set<String> set = new HashSet<>(preferences.getStringSet(KEY_FAVORITE_PATHS, Collections.emptySet()));
        if (set.remove(path)) {
            preferences.edit().putStringSet(KEY_FAVORITE_PATHS, set).apply();
        }

        // 2. Remove from network items
        Set<String> netSet = new HashSet<>(preferences.getStringSet(KEY_FAVORITE_NETWORK, Collections.emptySet()));
        boolean removedFromNet = netSet.removeIf(s -> {
            try {
                return path.equals(new JSONObject(s).optString("path"));
            } catch (Exception e) {
                return false;
            }
        });
        if (removedFromNet) {
            preferences.edit().putStringSet(KEY_FAVORITE_NETWORK, netSet).apply();
        }
    }

    public synchronized List<FileItem> getFavoriteItems() {
        Set<String> set = preferences.getStringSet(KEY_FAVORITE_PATHS, Collections.emptySet());
        List<FileItem> items = new ArrayList<>();
        Set<String> validPaths = new HashSet<>();

        for (String path : set) {
            File file = new File(path);
            if (file.exists()) {
                items.add(new FileItem(file));
                validPaths.add(path);
            }
        }

        // Clean up deleted files if any
        if (validPaths.size() != set.size()) {
            preferences.edit().putStringSet(KEY_FAVORITE_PATHS, validPaths).apply();
        }

        // Load network and cloud favorites
        Set<String> netSet = preferences.getStringSet(KEY_FAVORITE_NETWORK, Collections.emptySet());
        for (String jsonStr : netSet) {
            try {
                JSONObject obj = new JSONObject(jsonStr);
                NetworkItem netItem = NetworkItem.fromJsonObject(obj);
                if (netItem != null) {
                    items.add(new FileItem(netItem));
                }
            } catch (Exception ignored) {}
        }

        Collections.sort(items);
        return items;
    }
}
