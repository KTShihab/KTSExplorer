package com.kts.fileexplorer.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.kts.fileexplorer.R;
import com.kts.fileexplorer.model.InstalledAppItem;

import java.util.ArrayList;
import java.util.List;

/**
 * RecyclerView adapter for displaying installed applications with screen reader accessibility.
 */
public class InstalledAppAdapter extends RecyclerView.Adapter<InstalledAppAdapter.AppViewHolder> {

    private static final int ACTION_ID_OPEN = 0x7f0a0201;
    private static final int ACTION_ID_INFO = 0x7f0a0202;
    private static final int ACTION_ID_UNINSTALL = 0x7f0a0203;
    private static final int ACTION_ID_SHARE = 0x7f0a0204;

    public interface OnInstalledAppActionListener {
        void onAppClick(InstalledAppItem item, int position);
        void onAppLongClick(InstalledAppItem item, int position);
        void onAppOpen(InstalledAppItem item);
        void onAppInfo(InstalledAppItem item);
        void onAppUninstall(InstalledAppItem item);
        void onAppShareApk(InstalledAppItem item);
    }

    private final List<InstalledAppItem> items = new ArrayList<>();
    private final OnInstalledAppActionListener actionListener;

    public InstalledAppAdapter(OnInstalledAppActionListener actionListener) {
        this.actionListener = actionListener;
    }

    public void updateList(List<InstalledAppItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    public List<InstalledAppItem> getItems() {
        return items;
    }

    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_installed_app, parent, false);
        return new AppViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        InstalledAppItem item = items.get(position);

        holder.tvAppName.setText(item.getAppName());
        holder.tvPackageVersion.setText(item.getPackageName() + " • v" + item.getVersionName());

        String typeStr = item.isSystemApp() ? "System App" : "User App";
        holder.tvAppDetails.setText(item.getFormattedSize() + " • " + typeStr);

        if (item.getIcon() != null) {
            holder.ivAppIcon.setImageDrawable(item.getIcon());
        } else {
            holder.ivAppIcon.setImageResource(R.drawable.ic_cat_apk);
        }

        // Accessibility configuration
        holder.itemView.setContentDescription(item.getAccessibilityDescription());

        // Custom actions for TalkBack & Jieshuo screen readers (swipe up/down)
        ViewCompat.setAccessibilityDelegate(holder.itemView, new androidx.core.view.AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);

                info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        ACTION_ID_OPEN,
                        "Open " + item.getAppName()
                ));

                info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        ACTION_ID_INFO,
                        "App Info for " + item.getAppName()
                ));

                if (!item.isSystemApp()) {
                    info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            ACTION_ID_UNINSTALL,
                            "Uninstall " + item.getAppName()
                    ));
                }

                info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        ACTION_ID_SHARE,
                        "Share APK for " + item.getAppName()
                ));
            }

            @Override
            public boolean performAccessibilityAction(@NonNull View host, int action, android.os.Bundle args) {
                int pos = holder.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return super.performAccessibilityAction(host, action, args);
                InstalledAppItem currentItem = items.get(pos);

                if (action == ACTION_ID_OPEN) {
                    if (actionListener != null) actionListener.onAppOpen(currentItem);
                    return true;
                } else if (action == ACTION_ID_INFO) {
                    if (actionListener != null) actionListener.onAppInfo(currentItem);
                    return true;
                } else if (action == ACTION_ID_UNINSTALL) {
                    if (actionListener != null) actionListener.onAppUninstall(currentItem);
                    return true;
                } else if (action == ACTION_ID_SHARE) {
                    if (actionListener != null) actionListener.onAppShareApk(currentItem);
                    return true;
                }
                return super.performAccessibilityAction(host, action, args);
            }
        });

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && actionListener != null) {
                actionListener.onAppClick(items.get(pos), pos);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && actionListener != null) {
                actionListener.onAppLongClick(items.get(pos), pos);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class AppViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivAppIcon;
        final TextView tvAppName;
        final TextView tvPackageVersion;
        final TextView tvAppDetails;

        public AppViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAppIcon = itemView.findViewById(R.id.iv_app_icon);
            tvAppName = itemView.findViewById(R.id.tv_app_name);
            tvPackageVersion = itemView.findViewById(R.id.tv_app_package_version);
            tvAppDetails = itemView.findViewById(R.id.tv_app_details);
        }
    }
}
