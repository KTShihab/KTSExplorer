package com.kts.fileexplorer.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.kts.fileexplorer.model.ActionConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages user preferences for action menu items:
 * which actions are enabled/disabled and their custom order.
 */
public class ActionMenuManager {

    private static final String PREF_NAME = "kts_action_menu_prefs";
    private static final String KEY_ACTION_ORDER = "action_order_and_state";

    private static final List<String> DEFAULT_ORDER = Arrays.asList(
            ActionConfig.ACTION_SELECT,
            ActionConfig.ACTION_FAVORITE,
            ActionConfig.ACTION_OPEN_WITH,
            ActionConfig.ACTION_COPY,
            ActionConfig.ACTION_CUT,
            ActionConfig.ACTION_RENAME,
            ActionConfig.ACTION_SHARE,
            ActionConfig.ACTION_EDIT,
            ActionConfig.ACTION_BATCH_CONVERT,
            ActionConfig.ACTION_DELETE,
            ActionConfig.ACTION_PROPERTIES
    );

    private static ActionMenuManager instance;
    private final SharedPreferences prefs;

    private ActionMenuManager(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized ActionMenuManager getInstance(Context context) {
        if (instance == null) {
            instance = new ActionMenuManager(context);
        }
        return instance;
    }

    public List<ActionConfig> getActionConfigs() {
        String saved = prefs.getString(KEY_ACTION_ORDER, null);
        if (saved == null || saved.trim().isEmpty()) {
            return getDefaultConfigs();
        }

        List<ActionConfig> list = new ArrayList<>();
        String[] tokens = saved.split(",");
        Set<String> seen = new HashSet<>();

        for (String token : tokens) {
            String[] parts = token.split(":");
            if (parts.length == 2) {
                String id = parts[0];
                boolean enabled = Boolean.parseBoolean(parts[1]);
                if (isValidActionId(id)) {
                    list.add(new ActionConfig(id, ActionConfig.getDefaultTitleResId(id), ActionConfig.getDefaultIconResId(id), enabled));
                    seen.add(id);
                }
            }
        }

        // Add any missing new action IDs at the end
        for (String defId : DEFAULT_ORDER) {
            if (!seen.contains(defId)) {
                list.add(new ActionConfig(defId, ActionConfig.getDefaultTitleResId(defId), ActionConfig.getDefaultIconResId(defId), true));
            }
        }

        return list;
    }

    public List<ActionConfig> getEnabledActions() {
        List<ActionConfig> all = getActionConfigs();
        List<ActionConfig> enabled = new ArrayList<>();
        for (ActionConfig ac : all) {
            if (ac.isEnabled()) {
                enabled.add(ac);
            }
        }
        return enabled;
    }

    public void saveActionConfigs(List<ActionConfig> configs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < configs.size(); i++) {
            ActionConfig ac = configs.get(i);
            sb.append(ac.getId()).append(":").append(ac.isEnabled());
            if (i < configs.size() - 1) {
                sb.append(",");
            }
        }
        prefs.edit().putString(KEY_ACTION_ORDER, sb.toString()).apply();
    }

    public void resetToDefaults() {
        prefs.edit().remove(KEY_ACTION_ORDER).apply();
    }

    private List<ActionConfig> getDefaultConfigs() {
        List<ActionConfig> list = new ArrayList<>();
        for (String id : DEFAULT_ORDER) {
            list.add(new ActionConfig(id, ActionConfig.getDefaultTitleResId(id), ActionConfig.getDefaultIconResId(id), true));
        }
        return list;
    }

    private boolean isValidActionId(String id) {
        return DEFAULT_ORDER.contains(id);
    }
}
