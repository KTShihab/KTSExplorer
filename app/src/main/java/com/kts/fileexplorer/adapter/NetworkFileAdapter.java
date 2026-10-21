package com.kts.fileexplorer.adapter;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.kts.fileexplorer.R;
import com.kts.fileexplorer.network.NetworkItem;
import com.kts.fileexplorer.util.FavoriteManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class NetworkFileAdapter extends RecyclerView.Adapter<NetworkFileAdapter.ViewHolder> {

    public static final int ACTION_OPEN = 1001;
    public static final int ACTION_DOWNLOAD = 1002;
    public static final int ACTION_COPY = 1003;
    public static final int ACTION_CUT = 1004;
    public static final int ACTION_SHARE = 1005;
    public static final int ACTION_FAVORITE = 1006;
    public static final int ACTION_DELETE = 1007;
    public static final int ACTION_SELECT = 1008;
    public static final int ACTION_PROPERTIES = 1009;

    public interface OnNetworkItemClickListener {
        void onItemClick(NetworkItem item, int position);
        void onItemMenuClick(NetworkItem item, View anchor, int position);
        void onItemAction(NetworkItem item, int actionId, int position);
        void onSelectionCountChanged(int count);
    }

    private final List<NetworkItem> items = new ArrayList<>();
    private final Set<String> selectedPaths = new HashSet<>();
    private boolean isSelectionMode = false;
    private final OnNetworkItemClickListener listener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH);

    public NetworkFileAdapter(OnNetworkItemClickListener listener) {
        this.listener = listener;
    }

    public void updateList(List<NetworkItem> list) {
        items.clear();
        if (list != null) {
            items.addAll(list);
        }
        notifyDataSetChanged();
    }

    public boolean isSelectionMode() {
        return isSelectionMode;
    }

    public void setSelectionMode(boolean selectionMode) {
        this.isSelectionMode = selectionMode;
        if (!selectionMode) {
            selectedPaths.clear();
            if (listener != null) {
                listener.onSelectionCountChanged(0);
            }
        }
        notifyDataSetChanged();
    }

    public Set<String> getSelectedPaths() {
        return selectedPaths;
    }

    public List<NetworkItem> getSelectedItems() {
        List<NetworkItem> selected = new ArrayList<>();
        for (NetworkItem item : items) {
            if (selectedPaths.contains(item.getPath())) {
                selected.add(item);
            }
        }
        return selected;
    }

    public boolean toggleSelection(int position) {
        if (position < 0 || position >= items.size()) return false;
        NetworkItem item = items.get(position);
        boolean isNowSelected;
        if (selectedPaths.contains(item.getPath())) {
            selectedPaths.remove(item.getPath());
            isNowSelected = false;
        } else {
            selectedPaths.add(item.getPath());
            isNowSelected = true;
        }
        notifyItemChanged(position);
        if (listener != null) {
            listener.onSelectionCountChanged(selectedPaths.size());
        }
        return isNowSelected;
    }

    public void selectAll() {
        selectedPaths.clear();
        for (NetworkItem item : items) {
            selectedPaths.add(item.getPath());
        }
        notifyDataSetChanged();
        if (listener != null) {
            listener.onSelectionCountChanged(selectedPaths.size());
        }
    }

    public void deselectAll() {
        selectedPaths.clear();
        notifyDataSetChanged();
        if (listener != null) {
            listener.onSelectionCountChanged(0);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_network_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NetworkItem item = items.get(position);
        Context context = holder.itemView.getContext();
        boolean isSelected = selectedPaths.contains(item.getPath());
        boolean isFavorite = FavoriteManager.getInstance(context).isFavorite(item.getPath());
        String favSuffix = isFavorite ? ", Favorited" : "";
        String typeDesc = item.isDirectory() ? "Folder" : "File";

        holder.tvName.setText(item.getName());

        if (item.isDirectory()) {
            holder.ivIcon.setImageResource(R.drawable.ic_folder);
            String dateStr = item.getLastModified() > 0 ? dateFormat.format(new Date(item.getLastModified())) : "";
            holder.tvDetails.setText(dateStr.isEmpty() ? "Folder" : "Folder • " + dateStr);
        } else {
            holder.ivIcon.setImageResource(getFileIconRes(item.getExtension()));
            String sizeStr = item.getFormattedSize();
            String dateStr = item.getLastModified() > 0 ? dateFormat.format(new Date(item.getLastModified())) : "";
            String details = sizeStr;
            if (!dateStr.isEmpty()) {
                details += " • " + dateStr;
            }
            holder.tvDetails.setText(details);
        }

        // Selection Visual State
        if (isSelectionMode) {
            holder.cbSelect.setVisibility(View.VISIBLE);
            holder.cbSelect.setChecked(isSelected);
            holder.btnMenu.setVisibility(View.GONE);
            holder.itemView.setBackgroundColor(isSelected ? Color.parseColor("#331976D2") : Color.TRANSPARENT);

            String status = isSelected ? (item.getName() + ", Selected") : (item.getName() + ", Not selected");
            holder.itemView.setContentDescription(status + ", " + typeDesc + ", " + holder.tvDetails.getText() + favSuffix);
        } else {
            holder.cbSelect.setVisibility(View.GONE);
            holder.btnMenu.setVisibility(View.VISIBLE);
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
            holder.itemView.setContentDescription(item.getName() + ", " + typeDesc + ", " + holder.tvDetails.getText() + favSuffix);
        }

        holder.btnMenu.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            if (isSelectionMode) {
                toggleSelection(pos);
            } else if (listener != null) {
                listener.onItemClick(item, pos);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return false;
            if (isSelectionMode) {
                toggleSelection(pos);
                return true;
            } else if (listener != null) {
                listener.onItemMenuClick(item, holder.itemView, pos);
                return true;
            }
            return false;
        });

        holder.btnMenu.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            if (listener != null) {
                listener.onItemMenuClick(item, v, pos);
            }
        });

        ViewCompat.setAccessibilityDelegate(holder.itemView, new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                if (isSelectionMode) {
                    info.setCheckable(true);
                    info.setChecked(isSelected);
                    info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(ACTION_SELECT, isSelected ? "Deselect" : "Select"));
                } else {
                    info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(ACTION_SELECT, "Select"));
                    info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(ACTION_PROPERTIES, "Properties"));
                    info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(ACTION_COPY, "Copy"));
                    info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(ACTION_CUT, "Cut"));
                    if (!item.isDirectory()) {
                        info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(ACTION_DOWNLOAD, "Download to Phone"));
                        info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(ACTION_SHARE, "Share"));
                    }
                    info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(ACTION_FAVORITE, isFavorite ? "Remove from Favorites" : "Add to Favorites"));
                    info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(ACTION_DELETE, "Delete"));
                }
            }

            @Override
            public boolean performAccessibilityAction(@NonNull View host, int action, @Nullable Bundle args) {
                int pos = holder.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return false;

                if (action == ACTION_SELECT) {
                    if (isSelectionMode) {
                        toggleSelection(pos);
                    } else if (listener != null) {
                        listener.onItemAction(item, ACTION_SELECT, pos);
                    }
                    return true;
                }
                if (listener != null && (action == ACTION_PROPERTIES || action == ACTION_COPY || action == ACTION_CUT ||
                        action == ACTION_DOWNLOAD || action == ACTION_SHARE || action == ACTION_FAVORITE || action == ACTION_DELETE)) {
                    listener.onItemAction(item, action, pos);
                    return true;
                }
                return super.performAccessibilityAction(host, action, args);
            }
        });
    }

    private int getFileIconRes(String ext) {
        if (ext == null) return R.drawable.ic_file;
        switch (ext.toLowerCase(Locale.ENGLISH)) {
            case ".mp3":
            case ".wav":
            case ".m4a":
            case ".aac":
            case ".ogg":
            case ".flac":
                return R.drawable.ic_cat_audio;
            case ".mp4":
            case ".mkv":
            case ".webm":
            case ".3gp":
            case ".avi":
            case ".mov":
                return R.drawable.ic_cat_video;
            case ".jpg":
            case ".jpeg":
            case ".png":
            case ".gif":
            case ".webp":
            case ".bmp":
                return R.drawable.ic_cat_image;
            case ".pdf":
            case ".doc":
            case ".docx":
            case ".txt":
            case ".xls":
            case ".xlsx":
            case ".ppt":
            case ".pptx":
                return R.drawable.ic_cat_doc;
            case ".apk":
                return R.drawable.ic_cat_apk;
            default:
                return R.drawable.ic_file;
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CheckBox cbSelect;
        ImageView ivIcon;
        TextView tvName;
        TextView tvDetails;
        ImageButton btnMenu;

        ViewHolder(View v) {
            super(v);
            cbSelect = v.findViewById(R.id.cb_select_network);
            ivIcon = v.findViewById(R.id.iv_file_icon);
            tvName = v.findViewById(R.id.tv_file_name);
            tvDetails = v.findViewById(R.id.tv_file_details);
            btnMenu = v.findViewById(R.id.btn_file_menu);
        }
    }
}
