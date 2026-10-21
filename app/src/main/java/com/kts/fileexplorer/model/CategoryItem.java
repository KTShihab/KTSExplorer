package com.kts.fileexplorer.model;

import com.kts.fileexplorer.R;

/**
 * Model representing a file category (Audio, Images, Videos, Documents, Downloads, Apps).
 */
public class CategoryItem {

    public enum Type {
        NEW_FILES,
        AUDIO,
        IMAGES,
        VIDEOS,
        DOCUMENTS,
        DOWNLOADS,
        APPS
    }

    private final Type type;
    private final int titleResId;
    private final int subtitleResId;
    private final int iconResId;

    public CategoryItem(Type type, int titleResId, int subtitleResId, int iconResId) {
        this.type = type;
        this.titleResId = titleResId;
        this.subtitleResId = subtitleResId;
        this.iconResId = iconResId;
    }

    public Type getType() {
        return type;
    }

    public int getTitleResId() {
        return titleResId;
    }

    public int getSubtitleResId() {
        return subtitleResId;
    }

    public int getIconResId() {
        return iconResId;
    }

    private long totalSizeBytes = -1;
    private int totalItemCount = 0;

    public long getTotalSizeBytes() {
        return totalSizeBytes;
    }

    public void setTotalSizeBytes(long totalSizeBytes) {
        this.totalSizeBytes = totalSizeBytes;
    }

    public int getTotalItemCount() {
        return totalItemCount;
    }

    public void setTotalItemCount(int totalItemCount) {
        this.totalItemCount = totalItemCount;
    }

    public static CategoryItem[] getDefaultCategories() {
        return new CategoryItem[]{
                new CategoryItem(Type.NEW_FILES, R.string.category_new_files, R.string.category_new_files_sub, R.drawable.ic_cat_new_files),
                new CategoryItem(Type.AUDIO, R.string.category_audio, R.string.category_audio_sub, R.drawable.ic_cat_audio),
                new CategoryItem(Type.IMAGES, R.string.category_images, R.string.category_images_sub, R.drawable.ic_cat_image),
                new CategoryItem(Type.VIDEOS, R.string.category_videos, R.string.category_videos_sub, R.drawable.ic_cat_video),
                new CategoryItem(Type.DOCUMENTS, R.string.category_documents, R.string.category_documents_sub, R.drawable.ic_cat_doc),
                new CategoryItem(Type.DOWNLOADS, R.string.category_downloads, R.string.category_downloads_sub, R.drawable.ic_cat_download),
                new CategoryItem(Type.APPS, R.string.category_apps, R.string.category_apps_sub, R.drawable.ic_cat_apk)
        };
    }
}
