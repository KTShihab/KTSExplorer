package com.kts.fileexplorer.model;

import com.kts.fileexplorer.R;

import java.util.Comparator;

/**
 * Enum defining the 8 supported sorting algorithms for files and folders.
 */
public enum SortMode {
    NAME_ASC(R.string.sort_name_asc),
    NAME_DESC(R.string.sort_name_desc),
    SIZE_ASC(R.string.sort_size_asc),
    SIZE_DESC(R.string.sort_size_desc),
    DATE_DESC(R.string.sort_date_desc),
    DATE_ASC(R.string.sort_date_asc),
    TYPE_ASC(R.string.sort_type_asc),
    TYPE_DESC(R.string.sort_type_desc);

    private final int titleResId;

    SortMode(int titleResId) {
        this.titleResId = titleResId;
    }

    public int getTitleResId() {
        return titleResId;
    }

    /**
     * Returns a comparator that keeps directories grouped at the top
     * and sorts elements according to the chosen criteria.
     */
    public Comparator<FileItem> getComparator() {
        return (a, b) -> {
            // Keep folders grouped on top
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;

            int result;
            switch (this) {
                case NAME_ASC:
                    result = a.getName().compareToIgnoreCase(b.getName());
                    break;
                case NAME_DESC:
                    result = b.getName().compareToIgnoreCase(a.getName());
                    break;
                case SIZE_ASC:
                    if (a.isDirectory()) {
                        result = Integer.compare(a.getChildCount(), b.getChildCount());
                    } else {
                        result = Long.compare(a.getLength(), b.getLength());
                    }
                    if (result == 0) result = a.getName().compareToIgnoreCase(b.getName());
                    break;
                case SIZE_DESC:
                    if (a.isDirectory()) {
                        result = Integer.compare(b.getChildCount(), a.getChildCount());
                    } else {
                        result = Long.compare(b.getLength(), a.getLength());
                    }
                    if (result == 0) result = a.getName().compareToIgnoreCase(b.getName());
                    break;
                case DATE_DESC:
                    result = Long.compare(b.getLastModified(), a.getLastModified());
                    if (result == 0) result = a.getName().compareToIgnoreCase(b.getName());
                    break;
                case DATE_ASC:
                    result = Long.compare(a.getLastModified(), b.getLastModified());
                    if (result == 0) result = a.getName().compareToIgnoreCase(b.getName());
                    break;
                case TYPE_ASC:
                    result = a.getExtension().compareToIgnoreCase(b.getExtension());
                    if (result == 0) result = a.getName().compareToIgnoreCase(b.getName());
                    break;
                case TYPE_DESC:
                    result = b.getExtension().compareToIgnoreCase(a.getExtension());
                    if (result == 0) result = a.getName().compareToIgnoreCase(b.getName());
                    break;
                default:
                    result = a.getName().compareToIgnoreCase(b.getName());
            }
            return result;
        };
    }

    /**
     * Returns a comparator that keeps directories grouped at the top
     * and sorts NetworkItem elements according to the chosen criteria.
     */
    public Comparator<com.kts.fileexplorer.network.NetworkItem> getNetworkItemComparator() {
        return (a, b) -> {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;

            int result;
            switch (this) {
                case NAME_ASC:
                    result = a.getName().compareToIgnoreCase(b.getName());
                    break;
                case NAME_DESC:
                    result = b.getName().compareToIgnoreCase(a.getName());
                    break;
                case SIZE_ASC:
                    result = Long.compare(a.getSize(), b.getSize());
                    if (result == 0) result = a.getName().compareToIgnoreCase(b.getName());
                    break;
                case SIZE_DESC:
                    result = Long.compare(b.getSize(), a.getSize());
                    if (result == 0) result = a.getName().compareToIgnoreCase(b.getName());
                    break;
                case DATE_DESC:
                    result = Long.compare(b.getLastModified(), a.getLastModified());
                    if (result == 0) result = a.getName().compareToIgnoreCase(b.getName());
                    break;
                case DATE_ASC:
                    result = Long.compare(a.getLastModified(), b.getLastModified());
                    if (result == 0) result = a.getName().compareToIgnoreCase(b.getName());
                    break;
                case TYPE_ASC:
                    result = a.getExtension().compareToIgnoreCase(b.getExtension());
                    if (result == 0) result = a.getName().compareToIgnoreCase(b.getName());
                    break;
                case TYPE_DESC:
                    result = b.getExtension().compareToIgnoreCase(a.getExtension());
                    if (result == 0) result = a.getName().compareToIgnoreCase(b.getName());
                    break;
                default:
                    result = a.getName().compareToIgnoreCase(b.getName());
            }
            return result;
        };
    }
}
