package com.kts.fileexplorer.util;

import com.kts.fileexplorer.network.NetworkItem;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Universal app clipboard to coordinate copy/paste operations across:
 * - Local phone storage (Home tab)
 * - Remote computer network shares (SMB/FTP in Online tab)
 */
public class AppClipboard {

    private static AppClipboard instance;

    private final List<File> localFiles = new ArrayList<>();
    private final List<NetworkItem> networkItems = new ArrayList<>();
    private boolean isCut = false;

    private AppClipboard() {}

    public static synchronized AppClipboard getInstance() {
        if (instance == null) {
            instance = new AppClipboard();
        }
        return instance;
    }

    public synchronized void setLocalFiles(List<File> files, boolean cut) {
        clear();
        if (files != null) {
            localFiles.addAll(files);
        }
        isCut = cut;
    }

    public synchronized void setNetworkItems(List<NetworkItem> items, boolean cut) {
        clear();
        if (items != null) {
            networkItems.addAll(items);
        }
        isCut = cut;
    }

    public synchronized List<File> getLocalFiles() {
        return new ArrayList<>(localFiles);
    }

    public synchronized List<NetworkItem> getNetworkItems() {
        return new ArrayList<>(networkItems);
    }

    public synchronized boolean hasLocalFiles() {
        return !localFiles.isEmpty();
    }

    public synchronized boolean hasNetworkItems() {
        return !networkItems.isEmpty();
    }

    public synchronized boolean isEmpty() {
        return localFiles.isEmpty() && networkItems.isEmpty();
    }

    public synchronized boolean isCut() {
        return isCut;
    }

    public synchronized void clear() {
        localFiles.clear();
        networkItems.clear();
        isCut = false;
    }

    public synchronized String getSummary() {
        if (hasLocalFiles()) {
            return localFiles.size() == 1 ? localFiles.get(0).getName() : localFiles.size() + " items";
        }
        if (hasNetworkItems()) {
            return networkItems.size() == 1 ? networkItems.get(0).getName() : networkItems.size() + " items";
        }
        return "";
    }
}
