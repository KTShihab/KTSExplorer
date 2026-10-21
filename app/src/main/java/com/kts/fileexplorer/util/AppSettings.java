package com.kts.fileexplorer.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.kts.fileexplorer.R;

/**
 * Central settings manager for user preferences:
 * - Storage usage bar on main screen
 * - Category announcement verbosity
 * - Folder information announcement (Name, Count, Size, and Order)
 */
public class AppSettings {

    private static final String PREF_NAME = "kts_app_settings";

    public static final String KEY_SHOW_STORAGE_INFO = "pref_show_storage_info";
    public static final String KEY_ANNOUNCE_CATEGORY_DESC = "pref_announce_category_desc";
    public static final String KEY_SHOW_USAGE_HINTS = "pref_show_usage_hints";
    public static final String KEY_FOLDER_ANNOUNCE_NAME = "pref_folder_announce_name";
    public static final String KEY_FOLDER_ANNOUNCE_COUNT = "pref_folder_announce_count";
    public static final String KEY_FOLDER_ANNOUNCE_SIZE = "pref_folder_announce_size";
    public static final String KEY_FOLDER_ANNOUNCE_ORDER = "pref_folder_announce_order";

    public static final int ORDER_NAME_COUNT_SIZE = 0;
    public static final int ORDER_NAME_SIZE_COUNT = 1;
    public static final int ORDER_COUNT_NAME_SIZE = 2;
    public static final int ORDER_COUNT_SIZE_NAME = 3;
    public static final int ORDER_SIZE_NAME_COUNT = 4;
    public static final int ORDER_SIZE_COUNT_NAME = 5;

    private static AppSettings instance;
    private final SharedPreferences prefs;

    private AppSettings(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized AppSettings getInstance(Context context) {
        if (instance == null) {
            instance = new AppSettings(context);
        }
        return instance;
    }

    public boolean isShowStorageInfo() {
        return prefs.getBoolean(KEY_SHOW_STORAGE_INFO, true);
    }

    public void setShowStorageInfo(boolean value) {
        prefs.edit().putBoolean(KEY_SHOW_STORAGE_INFO, value).apply();
    }

    public boolean isAnnounceCategoryDescription() {
        return prefs.getBoolean(KEY_ANNOUNCE_CATEGORY_DESC, true);
    }

    public void setAnnounceCategoryDescription(boolean value) {
        prefs.edit().putBoolean(KEY_ANNOUNCE_CATEGORY_DESC, value).apply();
    }

    public boolean isShowUsageHints() {
        return prefs.getBoolean(KEY_SHOW_USAGE_HINTS, true);
    }

    public void setShowUsageHints(boolean value) {
        prefs.edit().putBoolean(KEY_SHOW_USAGE_HINTS, value).apply();
    }

    public boolean isAnnounceFolderName() {
        return prefs.getBoolean(KEY_FOLDER_ANNOUNCE_NAME, true);
    }

    public void setAnnounceFolderName(boolean value) {
        prefs.edit().putBoolean(KEY_FOLDER_ANNOUNCE_NAME, value).apply();
    }

    public boolean isAnnounceFolderCount() {
        return prefs.getBoolean(KEY_FOLDER_ANNOUNCE_COUNT, true);
    }

    public void setAnnounceFolderCount(boolean value) {
        prefs.edit().putBoolean(KEY_FOLDER_ANNOUNCE_COUNT, value).apply();
    }

    public boolean isAnnounceFolderSize() {
        return prefs.getBoolean(KEY_FOLDER_ANNOUNCE_SIZE, true);
    }

    public void setAnnounceFolderSize(boolean value) {
        prefs.edit().putBoolean(KEY_FOLDER_ANNOUNCE_SIZE, value).apply();
    }

    public int getFolderAnnounceOrder() {
        return prefs.getInt(KEY_FOLDER_ANNOUNCE_ORDER, ORDER_NAME_COUNT_SIZE);
    }

    public void setFolderAnnounceOrder(int order) {
        prefs.edit().putInt(KEY_FOLDER_ANNOUNCE_ORDER, order).apply();
    }

    public static String getOrderSummary(Context context, int order) {
        switch (order) {
            case ORDER_NAME_SIZE_COUNT:
                return context.getString(R.string.folder_order_name_size_count);
            case ORDER_COUNT_NAME_SIZE:
                return context.getString(R.string.folder_order_count_name_size);
            case ORDER_COUNT_SIZE_NAME:
                return context.getString(R.string.folder_order_count_size_name);
            case ORDER_SIZE_NAME_COUNT:
                return context.getString(R.string.folder_order_size_name_count);
            case ORDER_SIZE_COUNT_NAME:
                return context.getString(R.string.folder_order_size_count_name);
            case ORDER_NAME_COUNT_SIZE:
            default:
                return context.getString(R.string.folder_order_name_count_size);
        }
    }
}
