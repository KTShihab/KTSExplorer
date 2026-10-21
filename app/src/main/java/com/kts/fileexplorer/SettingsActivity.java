package com.kts.fileexplorer;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.kts.fileexplorer.adapter.ActionConfigAdapter;
import com.kts.fileexplorer.model.ActionConfig;
import com.kts.fileexplorer.util.ActionMenuManager;
import com.kts.fileexplorer.util.AppSettings;
import com.kts.fileexplorer.util.AppUpdateManager;

import java.util.List;

public class SettingsActivity extends AppCompatActivity {

    private ActionConfigAdapter adapter;
    private ActionMenuManager menuManager;
    private AppSettings appSettings;

    private TextView tvFolderOrderSummary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        menuManager = ActionMenuManager.getInstance(this);
        appSettings = AppSettings.getInstance(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar_settings);
        toolbar.setNavigationOnClickListener(v -> finish());

        setupGeneralSwitches();
        setupFolderAnnouncementSettings();
        setupUpdateSettings();
        setupActionMenuConfig();
    }

    private void setupUpdateSettings() {
        View rowCheck = findViewById(R.id.row_check_updates);
        TextView tvDesc = findViewById(R.id.tv_update_status_desc);
        String currentVersion = AppUpdateManager.getCurrentVersionName(this);
        if (tvDesc != null) {
            tvDesc.setText("Current Version: v" + currentVersion + " • Tap to check for updates");
        }
        if (rowCheck != null) {
            rowCheck.setContentDescription("Check for updates. Current version " + currentVersion + ". Double tap to check.");
            rowCheck.setOnClickListener(v -> AppUpdateManager.getInstance().checkForUpdates(this, true));
        }

        View rowAuto = findViewById(R.id.row_auto_check_updates);
        SwitchMaterial switchAuto = findViewById(R.id.switch_auto_check_updates);
        if (switchAuto != null && rowAuto != null) {
            boolean autoEnabled = AppUpdateManager.isAutoCheckEnabled(this);
            switchAuto.setChecked(autoEnabled);
            updateSwitchRowAccessibility(rowAuto, getString(R.string.settings_auto_check_updates),
                    getString(R.string.settings_auto_check_updates_desc), autoEnabled);

            rowAuto.setOnClickListener(v -> switchAuto.toggle());
            switchAuto.setOnCheckedChangeListener((buttonView, isChecked) -> {
                AppUpdateManager.setAutoCheckEnabled(this, isChecked);
                updateSwitchRowAccessibility(rowAuto, getString(R.string.settings_auto_check_updates),
                        getString(R.string.settings_auto_check_updates_desc), isChecked);
                rowAuto.announceForAccessibility(getString(R.string.settings_auto_check_updates) + " " + (isChecked ? "On" : "Off"));
            });
        }
    }

    private void setupGeneralSwitches() {
        // Storage info switch
        View rowStorage = findViewById(R.id.row_show_storage_info);
        SwitchMaterial switchStorage = findViewById(R.id.switch_show_storage_info);
        switchStorage.setChecked(appSettings.isShowStorageInfo());
        updateSwitchRowAccessibility(rowStorage, getString(R.string.settings_show_storage_info),
                getString(R.string.settings_show_storage_info_desc), switchStorage.isChecked());

        rowStorage.setOnClickListener(v -> switchStorage.toggle());
        switchStorage.setOnCheckedChangeListener((buttonView, isChecked) -> {
            appSettings.setShowStorageInfo(isChecked);
            updateSwitchRowAccessibility(rowStorage, getString(R.string.settings_show_storage_info),
                    getString(R.string.settings_show_storage_info_desc), isChecked);
            rowStorage.announceForAccessibility(getString(R.string.settings_show_storage_info) + " " + (isChecked ? "On" : "Off"));
        });

        // Category description announcement switch
        View rowCategory = findViewById(R.id.row_announce_category_desc);
        SwitchMaterial switchCategory = findViewById(R.id.switch_announce_category_desc);
        switchCategory.setChecked(appSettings.isAnnounceCategoryDescription());
        updateSwitchRowAccessibility(rowCategory, getString(R.string.settings_announce_category_desc),
                getString(R.string.settings_announce_category_desc_desc), switchCategory.isChecked());

        rowCategory.setOnClickListener(v -> switchCategory.toggle());
        switchCategory.setOnCheckedChangeListener((buttonView, isChecked) -> {
            appSettings.setAnnounceCategoryDescription(isChecked);
            updateSwitchRowAccessibility(rowCategory, getString(R.string.settings_announce_category_desc),
                    getString(R.string.settings_announce_category_desc_desc), isChecked);
            rowCategory.announceForAccessibility(getString(R.string.settings_announce_category_desc) + " " + (isChecked ? "On" : "Off"));
        });

        // Accessibility Usage Hints switch
        View rowHints = findViewById(R.id.row_show_usage_hints);
        SwitchMaterial switchHints = findViewById(R.id.switch_show_usage_hints);
        switchHints.setChecked(appSettings.isShowUsageHints());
        updateSwitchRowAccessibility(rowHints, getString(R.string.settings_show_usage_hints),
                getString(R.string.settings_show_usage_hints_desc), switchHints.isChecked());

        rowHints.setOnClickListener(v -> switchHints.toggle());
        switchHints.setOnCheckedChangeListener((buttonView, isChecked) -> {
            appSettings.setShowUsageHints(isChecked);
            updateSwitchRowAccessibility(rowHints, getString(R.string.settings_show_usage_hints),
                    getString(R.string.settings_show_usage_hints_desc), isChecked);
            rowHints.announceForAccessibility(getString(R.string.settings_show_usage_hints) + " " + (isChecked ? "On" : "Off"));
            // Refresh accessibility descriptions for all rows
            updateAllRowDescriptions();
        });
    }

    private void updateAllRowDescriptions() {
        View rowStorage = findViewById(R.id.row_show_storage_info);
        SwitchMaterial switchStorage = findViewById(R.id.switch_show_storage_info);
        updateSwitchRowAccessibility(rowStorage, getString(R.string.settings_show_storage_info),
                getString(R.string.settings_show_storage_info_desc), switchStorage.isChecked());

        View rowCategory = findViewById(R.id.row_announce_category_desc);
        SwitchMaterial switchCategory = findViewById(R.id.switch_announce_category_desc);
        updateSwitchRowAccessibility(rowCategory, getString(R.string.settings_announce_category_desc),
                getString(R.string.settings_announce_category_desc_desc), switchCategory.isChecked());

        View rowName = findViewById(R.id.row_folder_name);
        SwitchMaterial switchName = findViewById(R.id.switch_folder_name);
        updateSwitchRowAccessibility(rowName, getString(R.string.settings_folder_announce_name),
                getString(R.string.settings_folder_announce_name_desc), switchName.isChecked());

        View rowCount = findViewById(R.id.row_folder_count);
        SwitchMaterial switchCount = findViewById(R.id.switch_folder_count);
        updateSwitchRowAccessibility(rowCount, getString(R.string.settings_folder_announce_count),
                getString(R.string.settings_folder_announce_count_desc), switchCount.isChecked());

        View rowSize = findViewById(R.id.row_folder_size);
        SwitchMaterial switchSize = findViewById(R.id.switch_folder_size);
        updateSwitchRowAccessibility(rowSize, getString(R.string.settings_folder_announce_size),
                getString(R.string.settings_folder_announce_size_desc), switchSize.isChecked());

        updateOrderSummaryText();
    }

    private void setupFolderAnnouncementSettings() {
        // Folder Name
        View rowName = findViewById(R.id.row_folder_name);
        SwitchMaterial switchName = findViewById(R.id.switch_folder_name);
        switchName.setChecked(appSettings.isAnnounceFolderName());
        updateSwitchRowAccessibility(rowName, getString(R.string.settings_folder_announce_name),
                getString(R.string.settings_folder_announce_name_desc), switchName.isChecked());

        rowName.setOnClickListener(v -> switchName.toggle());
        switchName.setOnCheckedChangeListener((buttonView, isChecked) -> {
            appSettings.setAnnounceFolderName(isChecked);
            updateSwitchRowAccessibility(rowName, getString(R.string.settings_folder_announce_name),
                    getString(R.string.settings_folder_announce_name_desc), isChecked);
            rowName.announceForAccessibility(getString(R.string.settings_folder_announce_name) + " " + (isChecked ? "On" : "Off"));
        });

        // Folder Item Count
        View rowCount = findViewById(R.id.row_folder_count);
        SwitchMaterial switchCount = findViewById(R.id.switch_folder_count);
        switchCount.setChecked(appSettings.isAnnounceFolderCount());
        updateSwitchRowAccessibility(rowCount, getString(R.string.settings_folder_announce_count),
                getString(R.string.settings_folder_announce_count_desc), switchCount.isChecked());

        rowCount.setOnClickListener(v -> switchCount.toggle());
        switchCount.setOnCheckedChangeListener((buttonView, isChecked) -> {
            appSettings.setAnnounceFolderCount(isChecked);
            updateSwitchRowAccessibility(rowCount, getString(R.string.settings_folder_announce_count),
                    getString(R.string.settings_folder_announce_count_desc), isChecked);
            rowCount.announceForAccessibility(getString(R.string.settings_folder_announce_count) + " " + (isChecked ? "On" : "Off"));
        });

        // Folder Size
        View rowSize = findViewById(R.id.row_folder_size);
        SwitchMaterial switchSize = findViewById(R.id.switch_folder_size);
        switchSize.setChecked(appSettings.isAnnounceFolderSize());
        updateSwitchRowAccessibility(rowSize, getString(R.string.settings_folder_announce_size),
                getString(R.string.settings_folder_announce_size_desc), switchSize.isChecked());

        rowSize.setOnClickListener(v -> switchSize.toggle());
        switchSize.setOnCheckedChangeListener((buttonView, isChecked) -> {
            appSettings.setAnnounceFolderSize(isChecked);
            updateSwitchRowAccessibility(rowSize, getString(R.string.settings_folder_announce_size),
                    getString(R.string.settings_folder_announce_size_desc), isChecked);
            rowSize.announceForAccessibility(getString(R.string.settings_folder_announce_size) + " " + (isChecked ? "On" : "Off"));
        });

        // Folder Announcement Order
        View rowOrder = findViewById(R.id.row_folder_order);
        tvFolderOrderSummary = findViewById(R.id.tv_folder_order_summary);
        updateOrderSummaryText();

        rowOrder.setOnClickListener(v -> showOrderSelectionDialog());
    }

    private void updateOrderSummaryText() {
        int currentOrder = appSettings.getFolderAnnounceOrder();
        String summary = AppSettings.getOrderSummary(this, currentOrder);
        tvFolderOrderSummary.setText(summary);

        View rowOrder = findViewById(R.id.row_folder_order);
        String text = getString(R.string.settings_folder_announce_order) + ", " + summary;
        if (appSettings.isShowUsageHints()) {
            text += ". Double tap to change announcement order.";
        }
        rowOrder.setContentDescription(text);
    }

    private void showOrderSelectionDialog() {
        String[] options = new String[]{
                getString(R.string.folder_order_name_count_size),
                getString(R.string.folder_order_name_size_count),
                getString(R.string.folder_order_count_name_size),
                getString(R.string.folder_order_count_size_name),
                getString(R.string.folder_order_size_name_count),
                getString(R.string.folder_order_size_count_name)
        };

        int currentOrder = appSettings.getFolderAnnounceOrder();

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_select_folder_order)
                .setSingleChoiceItems(options, currentOrder, (dialog, which) -> {
                    appSettings.setFolderAnnounceOrder(which);
                    updateOrderSummaryText();
                    String selectedSummary = options[which];
                    findViewById(R.id.row_folder_order).announceForAccessibility(getString(R.string.settings_folder_announce_order) + " set to " + selectedSummary);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void updateSwitchRowAccessibility(View row, String title, String desc, boolean isChecked) {
        String text = title + ", " + desc + ", " + (isChecked ? "On" : "Off");
        if (appSettings.isShowUsageHints()) {
            text += ". Double tap to toggle.";
        }
        row.setContentDescription(text);
    }

    private void setupActionMenuConfig() {
        RecyclerView recyclerView = findViewById(R.id.recycler_action_configs);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.addItemDecoration(new DividerItemDecoration(this, layoutManager.getOrientation()));

        List<ActionConfig> items = menuManager.getActionConfigs();
        adapter = new ActionConfigAdapter(this, items);
        recyclerView.setAdapter(adapter);

        MaterialButton btnReset = findViewById(R.id.btn_reset_defaults);
        btnReset.setOnClickListener(v -> showResetConfirmationDialog());
    }

    private void showResetConfirmationDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dialog_reset_confirm_title)
                .setMessage(R.string.dialog_reset_confirm_message)
                .setPositiveButton(R.string.btn_reset_defaults, (dialog, which) -> {
                    menuManager.resetToDefaults();
                    List<ActionConfig> resetList = menuManager.getActionConfigs();
                    adapter.updateItems(resetList);
                    Toast.makeText(this, R.string.toast_reset_defaults, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }
}
