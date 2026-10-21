package com.kts.fileexplorer.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.kts.fileexplorer.model.SortMode;

/**
 * Manages persistent sorting preferences:
 * - Global sorting mode for files and folders across the app
 * - Folder-specific sorting mode overrides
 * - Category-specific sorting mode overrides
 * - Installed applications sorting mode
 *
 * All sorting selections persist across app restarts until explicitly changed by the user.
 */
public class SortPreferences {

    private static final String PREF_NAME = "kts_sort_preferences";

    private static final String KEY_GLOBAL_SORT_MODE = "pref_global_sort_mode";
    private static final String KEY_INSTALLED_APPS_SORT_MODE = "pref_installed_apps_sort_mode";
    private static final String KEY_FAVORITES_SORT_MODE = "pref_favorites_sort_mode";
    private static final String KEY_FAVORITES_HAS_OVERRIDE = "pref_favorites_has_override";

    private static final String PREFIX_FOLDER_SORT = "folder_sort_";
    private static final String PREFIX_CATEGORY_SORT = "category_sort_";

    private static SharedPreferences getPrefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static SortMode getGlobalSortMode(Context context) {
        String name = getPrefs(context).getString(KEY_GLOBAL_SORT_MODE, SortMode.NAME_ASC.name());
        try {
            return SortMode.valueOf(name);
        } catch (Exception e) {
            return SortMode.NAME_ASC;
        }
    }

    public static void setGlobalSortMode(Context context, SortMode mode) {
        if (mode == null) return;
        getPrefs(context).edit().putString(KEY_GLOBAL_SORT_MODE, mode.name()).apply();
    }

    public static boolean hasFolderSortOverride(Context context, String folderPath) {
        if (folderPath == null) return false;
        return getPrefs(context).contains(PREFIX_FOLDER_SORT + folderPath);
    }

    public static SortMode getSortModeForFolder(Context context, String folderPath) {
        if (folderPath != null && hasFolderSortOverride(context, folderPath)) {
            String name = getPrefs(context).getString(PREFIX_FOLDER_SORT + folderPath, null);
            if (name != null) {
                try {
                    return SortMode.valueOf(name);
                } catch (Exception ignored) {}
            }
        }
        return getGlobalSortMode(context);
    }

    public static void setSortModeForFolder(Context context, String folderPath, SortMode mode) {
        if (folderPath == null || mode == null) return;
        getPrefs(context).edit().putString(PREFIX_FOLDER_SORT + folderPath, mode.name()).apply();
    }

    public static void clearFolderSortMode(Context context, String folderPath) {
        if (folderPath == null) return;
        getPrefs(context).edit().remove(PREFIX_FOLDER_SORT + folderPath).apply();
    }

    public static boolean hasCategorySortOverride(Context context, String categoryKey) {
        if (categoryKey == null) return false;
        return getPrefs(context).contains(PREFIX_CATEGORY_SORT + categoryKey);
    }

    public static SortMode getCategorySortMode(Context context, String categoryKey) {
        if (categoryKey != null && hasCategorySortOverride(context, categoryKey)) {
            String name = getPrefs(context).getString(PREFIX_CATEGORY_SORT + categoryKey, null);
            if (name != null) {
                try {
                    return SortMode.valueOf(name);
                } catch (Exception ignored) {}
            }
        }
        return getGlobalSortMode(context);
    }

    public static void setCategorySortMode(Context context, String categoryKey, SortMode mode) {
        if (categoryKey == null || mode == null) return;
        getPrefs(context).edit().putString(PREFIX_CATEGORY_SORT + categoryKey, mode.name()).apply();
    }

    public static void clearCategorySortMode(Context context, String categoryKey) {
        if (categoryKey == null) return;
        getPrefs(context).edit().remove(PREFIX_CATEGORY_SORT + categoryKey).apply();
    }

    public static boolean hasFavoriteSortOverride(Context context) {
        return getPrefs(context).getBoolean(KEY_FAVORITES_HAS_OVERRIDE, false);
    }

    public static SortMode getFavoriteSortMode(Context context) {
        if (hasFavoriteSortOverride(context)) {
            String name = getPrefs(context).getString(KEY_FAVORITES_SORT_MODE, null);
            if (name != null) {
                try {
                    return SortMode.valueOf(name);
                } catch (Exception ignored) {}
            }
        }
        return getGlobalSortMode(context);
    }

    public static void setFavoriteSortMode(Context context, SortMode mode) {
        if (mode == null) return;
        getPrefs(context).edit()
                .putString(KEY_FAVORITES_SORT_MODE, mode.name())
                .putBoolean(KEY_FAVORITES_HAS_OVERRIDE, true)
                .apply();
    }

    public static void clearFavoriteSortMode(Context context) {
        getPrefs(context).edit()
                .remove(KEY_FAVORITES_SORT_MODE)
                .remove(KEY_FAVORITES_HAS_OVERRIDE)
                .apply();
    }

    public static int getInstalledAppSortMode(Context context) {
        return getPrefs(context).getInt(KEY_INSTALLED_APPS_SORT_MODE, 0);
    }

    public static void setInstalledAppSortMode(Context context, int mode) {
        getPrefs(context).edit().putInt(KEY_INSTALLED_APPS_SORT_MODE, mode).apply();
    }
}
