package com.kts.fileexplorer.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.kts.fileexplorer.R;
import com.kts.fileexplorer.model.CategoryItem;

/**
 * Adapter for displaying category overview cards with full TalkBack/Jieshuo accessibility.
 */
public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    public interface OnCategoryClickListener {
        void onCategoryClick(CategoryItem item);
    }

    private final CategoryItem[] categories;
    private final OnCategoryClickListener listener;

    public CategoryAdapter(CategoryItem[] categories, OnCategoryClickListener listener) {
        this.categories = categories;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category_card, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        CategoryItem item = categories[position];
        Context context = holder.itemView.getContext();

        String title = context.getString(item.getTitleResId());
        String subtitle = context.getString(item.getSubtitleResId());

        holder.tvTitle.setText(title);
        holder.tvSubtitle.setText(subtitle);
        holder.imgIcon.setImageResource(item.getIconResId());

        boolean announceDesc = com.kts.fileexplorer.util.AppSettings.getInstance(context).isAnnounceCategoryDescription();
        boolean showUsageHints = com.kts.fileexplorer.util.AppSettings.getInstance(context).isShowUsageHints();

        String baseDesc;
        if (item.getTotalSizeBytes() >= 0) {
            String formattedSize = com.kts.fileexplorer.model.FileItem.formatFileSize(item.getTotalSizeBytes());
            holder.tvSize.setVisibility(View.VISIBLE);
            holder.tvSize.setText(formattedSize);

            baseDesc = announceDesc ?
                    context.getString(R.string.category_card_desc_with_size_full, title, formattedSize, subtitle) :
                    context.getString(R.string.category_card_desc_with_size_concise, title, formattedSize);
        } else {
            holder.tvSize.setVisibility(View.GONE);
            baseDesc = announceDesc ?
                    context.getString(R.string.category_card_desc_full, title, subtitle) :
                    context.getString(R.string.category_card_desc_concise, title);
        }

        if (showUsageHints) {
            baseDesc = baseDesc + ". " + context.getString(R.string.category_hint_tap, title);
        }
        holder.itemView.setContentDescription(baseDesc);

        if (!showUsageHints) {
            androidx.core.view.ViewCompat.setAccessibilityDelegate(holder.itemView, new androidx.core.view.AccessibilityDelegateCompat() {
                @Override
                public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull androidx.core.view.accessibility.AccessibilityNodeInfoCompat info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.addAction(new androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                            androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_CLICK, null));
                }
            });
        } else {
            androidx.core.view.ViewCompat.setAccessibilityDelegate(holder.itemView, null);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCategoryClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return categories.length;
    }

    public static class CategoryViewHolder extends RecyclerView.ViewHolder {
        final ImageView imgIcon;
        final TextView tvTitle;
        final TextView tvSubtitle;
        final TextView tvSize;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            imgIcon = itemView.findViewById(R.id.img_category_icon);
            tvTitle = itemView.findViewById(R.id.tv_category_title);
            tvSubtitle = itemView.findViewById(R.id.tv_category_subtitle);
            tvSize = itemView.findViewById(R.id.tv_category_size);
        }
    }
}
