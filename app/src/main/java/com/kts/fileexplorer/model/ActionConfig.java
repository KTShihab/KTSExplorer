package com.kts.fileexplorer.model;

import com.kts.fileexplorer.R;

/**
 * Model representing an action in the action menu with customizable order and toggle state.
 */
public class ActionConfig {
    public static final String ACTION_SELECT = "ACTION_SELECT";
    public static final String ACTION_FAVORITE = "ACTION_FAVORITE";
    public static final String ACTION_OPEN_WITH = "ACTION_OPEN_WITH";
    public static final String ACTION_COPY = "ACTION_COPY";
    public static final String ACTION_CUT = "ACTION_CUT";
    public static final String ACTION_RENAME = "ACTION_RENAME";
    public static final String ACTION_SHARE = "ACTION_SHARE";
    public static final String ACTION_EDIT = "ACTION_EDIT";
    public static final String ACTION_BATCH_CONVERT = "ACTION_BATCH_CONVERT";
    public static final String ACTION_DELETE = "ACTION_DELETE";
    public static final String ACTION_PROPERTIES = "ACTION_PROPERTIES";

    private final String id;
    private final int titleResId;
    private final int iconResId;
    private boolean enabled;

    public ActionConfig(String id, int titleResId, int iconResId, boolean enabled) {
        this.id = id;
        this.titleResId = titleResId;
        this.iconResId = iconResId;
        this.enabled = enabled;
    }

    public String getId() {
        return id;
    }

    public int getTitleResId() {
        return titleResId;
    }

    public int getIconResId() {
        return iconResId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public static int getDefaultTitleResId(String id) {
        switch (id) {
            case ACTION_SELECT: return R.string.action_select;
            case ACTION_FAVORITE: return R.string.action_add_favorite;
            case ACTION_OPEN_WITH: return R.string.action_open_with;
            case ACTION_COPY: return R.string.action_copy;
            case ACTION_CUT: return R.string.action_cut;
            case ACTION_RENAME: return R.string.action_rename;
            case ACTION_SHARE: return R.string.action_share;
            case ACTION_EDIT: return R.string.action_edit;
            case ACTION_BATCH_CONVERT: return R.string.action_batch_convert;
            case ACTION_DELETE: return R.string.action_delete;
            case ACTION_PROPERTIES: return R.string.action_properties;
            default: return R.string.action_properties;
        }
    }

    public static int getDefaultIconResId(String id) {
        switch (id) {
            case ACTION_SELECT: return R.drawable.ic_action_select;
            case ACTION_FAVORITE: return R.drawable.ic_action_favorite;
            case ACTION_OPEN_WITH: return R.drawable.ic_action_open_with;
            case ACTION_COPY: return R.drawable.ic_action_copy;
            case ACTION_CUT: return R.drawable.ic_action_cut;
            case ACTION_RENAME: return R.drawable.ic_action_rename;
            case ACTION_SHARE: return R.drawable.ic_action_share;
            case ACTION_EDIT: return R.drawable.ic_action_edit;
            case ACTION_BATCH_CONVERT: return R.drawable.ic_action_edit;
            case ACTION_DELETE: return R.drawable.ic_action_delete;
            case ACTION_PROPERTIES: return R.drawable.ic_action_properties;
            default: return R.drawable.ic_action_properties;
        }
    }
}
