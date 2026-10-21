package com.kts.fileexplorer.adapter;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.kts.fileexplorer.R;
import com.kts.fileexplorer.model.ActionConfig;
import com.kts.fileexplorer.model.FileItem;
import com.kts.fileexplorer.util.ActionMenuManager;
import com.kts.fileexplorer.util.AppSettings;
import com.kts.fileexplorer.util.FavoriteManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * RecyclerView adapter supporting screen reader custom actions,
 * Multi-Selection Mode, and Double-Tap-Hold Range Selection.
 */
public class FileAdapter extends RecyclerView.Adapter<FileAdapter.FileViewHolder> {

    // Unique action IDs for accessibility node actions
    private static final int ACCESSIBILITY_ACTION_SELECT = R.id.accessibility_action_select;
    private static final int ACCESSIBILITY_ACTION_FAVORITE = R.id.accessibility_action_favorite;
    private static final int ACCESSIBILITY_ACTION_OPEN_WITH = R.id.accessibility_action_open_with;
    private static final int ACCESSIBILITY_ACTION_COPY = R.id.accessibility_action_copy;
    private static final int ACCESSIBILITY_ACTION_CUT = R.id.accessibility_action_cut;
    private static final int ACCESSIBILITY_ACTION_RENAME = R.id.accessibility_action_rename;
    private static final int ACCESSIBILITY_ACTION_SHARE = R.id.accessibility_action_share;
    private static final int ACCESSIBILITY_ACTION_EDIT = R.id.accessibility_action_edit;
    private static final int ACCESSIBILITY_ACTION_DELETE = R.id.accessibility_action_delete;
    private static final int ACCESSIBILITY_ACTION_PROPERTIES = R.id.accessibility_action_properties;
    private static final int ACCESSIBILITY_ACTION_BATCH_CONVERT = R.id.accessibility_action_batch_convert;

    public interface OnFileItemActionListener {
        void onItemClick(FileItem item, int position);
        void onItemLongClick(FileItem item, int position);
        void onActionSelect(FileItem item, int position);
        void onActionFavorite(FileItem item);
        void onActionOpenWith(FileItem item);
        void onActionCopy(FileItem item);
        void onActionCut(FileItem item);
        void onActionRename(FileItem item);
        void onActionShare(FileItem item);
        void onActionEdit(FileItem item);
        void onActionBatchConvert(FileItem item);
        void onActionDelete(FileItem item);
        void onActionProperties(FileItem item);
        void onSelectionCountChanged(int count);
        void onRangeSelected(int start, int end, int count, FileItem first, FileItem last);
    }

    private final List<FileItem> fileList = new ArrayList<>();
    private final Set<String> selectedPaths = new HashSet<>();
    private boolean isSelectionMode = false;
    private int lastSelectedPosition = -1;

    private final OnFileItemActionListener actionListener;

    public FileAdapter(OnFileItemActionListener actionListener) {
        this.actionListener = actionListener;
    }

    public void updateList(List<FileItem> newItems) {
        fileList.clear();
        if (newItems != null) {
            fileList.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    public List<FileItem> getCurrentList() {
        return fileList;
    }

    public boolean isSelectionMode() {
        return isSelectionMode;
    }

    public void setSelectionMode(boolean selectionMode) {
        this.isSelectionMode = selectionMode;
        if (!selectionMode) {
            selectedPaths.clear();
            lastSelectedPosition = -1;
            if (actionListener != null) {
                actionListener.onSelectionCountChanged(0);
            }
        }
        notifyDataSetChanged();
    }

    public Set<String> getSelectedPaths() {
        return selectedPaths;
    }

    public List<FileItem> getSelectedItems() {
        List<FileItem> selected = new ArrayList<>();
        for (FileItem item : fileList) {
            if (selectedPaths.contains(item.getPath())) {
                selected.add(item);
            }
        }
        return selected;
    }

    public void selectAll() {
        selectedPaths.clear();
        for (FileItem item : fileList) {
            selectedPaths.add(item.getPath());
        }
        lastSelectedPosition = fileList.size() - 1;
        notifyDataSetChanged();
        if (actionListener != null) {
            actionListener.onSelectionCountChanged(selectedPaths.size());
        }
    }

    public void deselectAll() {
        selectedPaths.clear();
        lastSelectedPosition = -1;
        notifyDataSetChanged();
        if (actionListener != null) {
            actionListener.onSelectionCountChanged(0);
        }
    }

    public boolean toggleSelection(int position) {
        if (position < 0 || position >= fileList.size()) return false;
        FileItem item = fileList.get(position);
        boolean isNowSelected;

        if (selectedPaths.contains(item.getPath())) {
            selectedPaths.remove(item.getPath());
            isNowSelected = false;
        } else {
            selectedPaths.add(item.getPath());
            lastSelectedPosition = position;
            isNowSelected = true;
        }

        notifyItemChanged(position);

        if (actionListener != null) {
            actionListener.onSelectionCountChanged(selectedPaths.size());
        }
        return isNowSelected;
    }

    /**
     * Selects all items from the last selected position to target position inclusive.
     */
    public void selectRange(int targetPosition) {
        if (targetPosition < 0 || targetPosition >= fileList.size()) return;

        if (lastSelectedPosition == -1) {
            toggleSelection(targetPosition);
            return;
        }

        int start = Math.min(lastSelectedPosition, targetPosition);
        int end = Math.max(lastSelectedPosition, targetPosition);

        for (int i = start; i <= end; i++) {
            selectedPaths.add(fileList.get(i).getPath());
        }

        FileItem firstItem = fileList.get(start);
        FileItem lastItem = fileList.get(end);
        int countInRange = end - start + 1;

        lastSelectedPosition = targetPosition;
        notifyItemRangeChanged(start, countInRange);

        if (actionListener != null) {
            actionListener.onSelectionCountChanged(selectedPaths.size());
            actionListener.onRangeSelected(start, end, countInRange, firstItem, lastItem);
        }
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        FileItem item = fileList.get(position);
        Context context = holder.itemView.getContext();
        boolean isSelected = selectedPaths.contains(item.getPath());
        boolean isFavorite = FavoriteManager.getInstance(context).isFavorite(item.getPath());

        holder.tvFileName.setText(item.getDisplayName());
        String details = item.getSubtitleText(context) + " \u2022 " + item.getFormattedDate();
        holder.tvFileDetails.setText(details);

        if (item.isDirectory()) {
            holder.imgFileIcon.setImageResource(R.drawable.ic_folder);
        } else {
            holder.imgFileIcon.setImageResource(R.drawable.ic_file);
        }

        boolean showUsageHints = AppSettings.getInstance(context).isShowUsageHints();

        // Selection Visual State
        if (isSelectionMode) {
            holder.cbSelect.setVisibility(View.VISIBLE);
            holder.cbSelect.setChecked(isSelected);
            holder.itemView.setBackgroundColor(isSelected ? Color.parseColor("#331976D2") : Color.TRANSPARENT);

            String statusPrefix;
            if (showUsageHints) {
                statusPrefix = isSelected ?
                        context.getString(R.string.selected_state_prefix, item.getName()) :
                        context.getString(R.string.not_selected_state_prefix, item.getName());
            } else {
                statusPrefix = isSelected ?
                        (item.getName() + ", Selected") :
                        (item.getName() + ", Not selected");
            }
            holder.itemView.setContentDescription(statusPrefix + ", " + details);
        } else {
            holder.cbSelect.setVisibility(View.GONE);
            holder.itemView.setBackgroundResource(R.drawable.ic_launcher_background); // reset or transparent
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);

            String favSuffix = isFavorite ? ", Favorited" : "";
            holder.itemView.setContentDescription(item.getAccessibleDescription(context) + favSuffix);
        }

        // Normal Click: toggles selection in selection mode, else opens item
        holder.itemView.setOnClickListener(v -> {
            if (isSelectionMode) {
                toggleSelection(holder.getBindingAdapterPosition());
            } else if (actionListener != null) {
                actionListener.onItemClick(item, holder.getBindingAdapterPosition());
            }
        });

        // Long Press: range selection in selection mode, else opens actions bottom sheet
        holder.itemView.setOnLongClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (isSelectionMode) {
                selectRange(pos);
                return true;
            } else if (actionListener != null) {
                actionListener.onItemLongClick(item, pos);
                return true;
            }
            return false;
        });

        // TalkBack Accessibility Delegate with Native Custom Actions
        ViewCompat.setAccessibilityDelegate(holder.itemView, new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);

                if (isSelectionMode) {
                    info.setCheckable(true);
                    info.setChecked(isSelected);
                }

                List<ActionConfig> enabledActions = ActionMenuManager.getInstance(context).getEnabledActions();
                for (ActionConfig config : enabledActions) {
                    switch (config.getId()) {
                        case ActionConfig.ACTION_SELECT:
                            info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                    ACCESSIBILITY_ACTION_SELECT,
                                    context.getString(R.string.action_select)
                            ));
                            break;
                        case ActionConfig.ACTION_FAVORITE:
                            String favLabel = isFavorite ?
                                    context.getString(R.string.action_remove_favorite) :
                                    context.getString(R.string.action_add_favorite);
                            info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                    ACCESSIBILITY_ACTION_FAVORITE,
                                    favLabel
                            ));
                            break;
                        case ActionConfig.ACTION_OPEN_WITH:
                            if (!item.isDirectory()) {
                                info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                        ACCESSIBILITY_ACTION_OPEN_WITH,
                                        context.getString(R.string.action_open_with)
                                    ));
                            }
                            break;
                        case ActionConfig.ACTION_COPY:
                            info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                    ACCESSIBILITY_ACTION_COPY,
                                    context.getString(R.string.action_copy)
                            ));
                            break;
                        case ActionConfig.ACTION_CUT:
                            info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                    ACCESSIBILITY_ACTION_CUT,
                                    context.getString(R.string.action_cut)
                            ));
                            break;
                        case ActionConfig.ACTION_RENAME:
                            info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                    ACCESSIBILITY_ACTION_RENAME,
                                    context.getString(R.string.action_rename)
                            ));
                            break;
                        case ActionConfig.ACTION_SHARE:
                            info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                    ACCESSIBILITY_ACTION_SHARE,
                                    context.getString(R.string.action_share)
                            ));
                            break;
                        case ActionConfig.ACTION_EDIT:
                            if (!item.isDirectory() && (item.isAudio() || item.isVideo())) {
                                String label = item.isVideo() ?
                                        context.getString(R.string.action_convert_to_audio) :
                                        context.getString(R.string.action_edit);
                                info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                        ACCESSIBILITY_ACTION_EDIT,
                                        label
                                ));
                            }
                            break;
                        case ActionConfig.ACTION_DELETE:
                            info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                    ACCESSIBILITY_ACTION_DELETE,
                                    context.getString(R.string.action_delete)
                            ));
                            break;
                        case ActionConfig.ACTION_PROPERTIES:
                            info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                    ACCESSIBILITY_ACTION_PROPERTIES,
                                    context.getString(R.string.action_properties)
                            ));
                            break;
                        case ActionConfig.ACTION_BATCH_CONVERT:
                            if (item.isDirectory()) {
                                info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                                        ACCESSIBILITY_ACTION_BATCH_CONVERT,
                                        context.getString(R.string.action_batch_convert)
                                ));
                            }
                            break;
                    }
                }
            }

            @Override
            public boolean performAccessibilityAction(@NonNull View host, int action, @Nullable Bundle args) {
                int pos = holder.getBindingAdapterPosition();
                if (action == ACCESSIBILITY_ACTION_SELECT) {
                    if (actionListener != null) actionListener.onActionSelect(item, pos);
                    return true;
                } else if (action == ACCESSIBILITY_ACTION_FAVORITE) {
                    if (actionListener != null) actionListener.onActionFavorite(item);
                    return true;
                } else if (action == ACCESSIBILITY_ACTION_OPEN_WITH) {
                    if (actionListener != null) actionListener.onActionOpenWith(item);
                    return true;
                } else if (action == ACCESSIBILITY_ACTION_COPY) {
                    if (actionListener != null) actionListener.onActionCopy(item);
                    return true;
                } else if (action == ACCESSIBILITY_ACTION_CUT) {
                    if (actionListener != null) actionListener.onActionCut(item);
                    return true;
                } else if (action == ACCESSIBILITY_ACTION_RENAME) {
                    if (actionListener != null) actionListener.onActionRename(item);
                    return true;
                } else if (action == ACCESSIBILITY_ACTION_SHARE) {
                    if (actionListener != null) actionListener.onActionShare(item);
                    return true;
                } else if (action == ACCESSIBILITY_ACTION_EDIT) {
                    if (actionListener != null) actionListener.onActionEdit(item);
                    return true;
                } else if (action == ACCESSIBILITY_ACTION_BATCH_CONVERT) {
                    if (actionListener != null) actionListener.onActionBatchConvert(item);
                    return true;
                } else if (action == ACCESSIBILITY_ACTION_DELETE) {
                    if (actionListener != null) actionListener.onActionDelete(item);
                    return true;
                } else if (action == ACCESSIBILITY_ACTION_PROPERTIES) {
                    if (actionListener != null) actionListener.onActionProperties(item);
                    return true;
                }
                return super.performAccessibilityAction(host, action, args);
            }
        });
    }

    @Override
    public int getItemCount() {
        return fileList.size();
    }

    public static class FileViewHolder extends RecyclerView.ViewHolder {
        final CheckBox cbSelect;
        final ImageView imgFileIcon;
        final TextView tvFileName;
        final TextView tvFileDetails;

        public FileViewHolder(@NonNull View itemView) {
            super(itemView);
            cbSelect = itemView.findViewById(R.id.cb_select);
            imgFileIcon = itemView.findViewById(R.id.img_file_icon);
            tvFileName = itemView.findViewById(R.id.tv_file_name);
            tvFileDetails = itemView.findViewById(R.id.tv_file_details);
        }
    }
}
