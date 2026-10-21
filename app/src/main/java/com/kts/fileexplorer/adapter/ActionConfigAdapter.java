package com.kts.fileexplorer.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.recyclerview.widget.RecyclerView;

import androidx.appcompat.widget.SwitchCompat;
import com.kts.fileexplorer.R;
import com.kts.fileexplorer.model.ActionConfig;
import com.kts.fileexplorer.util.ActionMenuManager;

import java.util.Collections;
import java.util.List;

public class ActionConfigAdapter extends RecyclerView.Adapter<ActionConfigAdapter.ConfigViewHolder> {

    private final Context context;
    private final List<ActionConfig> items;

    public ActionConfigAdapter(Context context, List<ActionConfig> items) {
        this.context = context;
        this.items = items;
    }

    public void updateItems(List<ActionConfig> newItems) {
        this.items.clear();
        this.items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ConfigViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_action_config, parent, false);
        return new ConfigViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ConfigViewHolder holder, int position) {
        ActionConfig config = items.get(position);
        String name = context.getString(config.getTitleResId());

        holder.tvPosition.setText(String.valueOf(position + 1));
        holder.imgIcon.setImageResource(config.getIconResId());
        holder.tvName.setText(name);

        holder.switchEnabled.setOnCheckedChangeListener(null);
        holder.switchEnabled.setChecked(config.isEnabled());

        boolean canMoveUp = position > 0;
        boolean canMoveDown = position < items.size() - 1;

        holder.btnMoveUp.setEnabled(canMoveUp);
        holder.btnMoveUp.setAlpha(canMoveUp ? 1.0f : 0.25f);
        holder.btnMoveUp.setContentDescription(context.getString(R.string.btn_move_up_desc, name, position + 1, items.size()));

        holder.btnMoveDown.setEnabled(canMoveDown);
        holder.btnMoveDown.setAlpha(canMoveDown ? 1.0f : 0.25f);
        holder.btnMoveDown.setContentDescription(context.getString(R.string.btn_move_down_desc, name, position + 1, items.size()));

        String statusDesc = (config.isEnabled() ? "Enabled" : "Disabled") + ", position " + (position + 1) + " of " + items.size();
        holder.itemView.setContentDescription(name + ", " + statusDesc);

        holder.btnMoveUp.setOnClickListener(v -> {
            int currentPos = holder.getBindingAdapterPosition();
            if (currentPos > 0) {
                Collections.swap(items, currentPos, currentPos - 1);
                ActionMenuManager.getInstance(context).saveActionConfigs(items);
                notifyItemMoved(currentPos, currentPos - 1);
                notifyItemRangeChanged(Math.min(currentPos, currentPos - 1), 2);
                String ann = context.getString(R.string.action_moved_up, name, currentPos);
                holder.itemView.announceForAccessibility(ann);
                Toast.makeText(context, ann, Toast.LENGTH_SHORT).show();
            }
        });

        holder.btnMoveDown.setOnClickListener(v -> {
            int currentPos = holder.getBindingAdapterPosition();
            if (currentPos < items.size() - 1) {
                Collections.swap(items, currentPos, currentPos + 1);
                ActionMenuManager.getInstance(context).saveActionConfigs(items);
                notifyItemMoved(currentPos, currentPos + 1);
                notifyItemRangeChanged(Math.min(currentPos, currentPos + 1), 2);
                String ann = context.getString(R.string.action_moved_down, name, currentPos + 2);
                holder.itemView.announceForAccessibility(ann);
                Toast.makeText(context, ann, Toast.LENGTH_SHORT).show();
            }
        });

        holder.switchEnabled.setContentDescription(context.getString(R.string.switch_action_desc, name));
        holder.switchEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int currentPos = holder.getBindingAdapterPosition();
            if (currentPos != RecyclerView.NO_POSITION) {
                items.get(currentPos).setEnabled(isChecked);
                ActionMenuManager.getInstance(context).saveActionConfigs(items);
                String ann = isChecked ?
                        context.getString(R.string.action_enabled, name) :
                        context.getString(R.string.action_disabled, name);
                holder.itemView.announceForAccessibility(ann);
                Toast.makeText(context, ann, Toast.LENGTH_SHORT).show();
            }
        });

        // Add accessibility actions for screen readers
        ViewCompat.setAccessibilityDelegate(holder.itemView, new androidx.core.view.AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                if (canMoveUp) {
                    info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            0x7f0a0301,
                            context.getString(R.string.btn_move_up_desc, name, position + 1, items.size())
                    ));
                }
                if (canMoveDown) {
                    info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            0x7f0a0302,
                            context.getString(R.string.btn_move_down_desc, name, position + 1, items.size())
                    ));
                }
            }

            @Override
            public boolean performAccessibilityAction(@NonNull View host, int action, android.os.Bundle args) {
                int currentPos = holder.getBindingAdapterPosition();
                if (action == 0x7f0a0301 && currentPos > 0) {
                    holder.btnMoveUp.performClick();
                    return true;
                } else if (action == 0x7f0a0302 && currentPos < items.size() - 1) {
                    holder.btnMoveDown.performClick();
                    return true;
                }
                return super.performAccessibilityAction(host, action, args);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ConfigViewHolder extends RecyclerView.ViewHolder {
        final TextView tvPosition;
        final ImageView imgIcon;
        final TextView tvName;
        final ImageButton btnMoveUp;
        final ImageButton btnMoveDown;
        final SwitchCompat switchEnabled;

        ConfigViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPosition = itemView.findViewById(R.id.tv_action_position);
            imgIcon = itemView.findViewById(R.id.img_action_icon);
            tvName = itemView.findViewById(R.id.tv_action_name);
            btnMoveUp = itemView.findViewById(R.id.btn_move_up);
            btnMoveDown = itemView.findViewById(R.id.btn_move_down);
            switchEnabled = itemView.findViewById(R.id.switch_action_enabled);
        }
    }
}
