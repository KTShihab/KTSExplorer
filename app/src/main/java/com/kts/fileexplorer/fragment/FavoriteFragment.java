package com.kts.fileexplorer.fragment;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import com.kts.fileexplorer.MainActivity;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.kts.fileexplorer.R;
import com.kts.fileexplorer.adapter.FileAdapter;
import com.kts.fileexplorer.model.ActionConfig;
import com.kts.fileexplorer.model.FileItem;
import com.kts.fileexplorer.model.SortMode;
import com.kts.fileexplorer.util.ArchiveViewerHelper;
import com.kts.fileexplorer.util.ActionMenuManager;
import com.kts.fileexplorer.util.EditActionHelper;
import com.kts.fileexplorer.util.FavoriteManager;
import com.kts.fileexplorer.util.FileOpener;
import com.kts.fileexplorer.util.FilePropertiesDialog;
import com.kts.fileexplorer.util.SortDialogHelper;
import com.kts.fileexplorer.util.SortPreferences;
import com.kts.fileexplorer.cloud.CloudStorageManager;
import com.kts.fileexplorer.cloud.DocumentFileTreeHelper;
import com.kts.fileexplorer.cloud.GoogleDriveService;
import com.kts.fileexplorer.network.FtpClientHelper;
import com.kts.fileexplorer.network.NetworkItem;
import com.kts.fileexplorer.network.NetworkServerInfo;
import com.kts.fileexplorer.network.SmbClientHelper;
import com.kts.fileexplorer.util.AppClipboard;

import android.os.Handler;
import android.os.Looper;
import android.os.Environment;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * FavoriteFragment displays bookmarked files and folders,
 * with search, sorting, and screen reader accessibility support.
 */
public class FavoriteFragment extends Fragment implements FileAdapter.OnFileItemActionListener {

    private LinearLayout favHeaderLayout;
    private ImageButton btnFavSearch;
    private ImageButton btnFavSort;

    private LinearLayout favSearchLayout;
    private EditText etFavSearch;
    private ImageButton btnFavCloseSearch;
    private ImageButton btnFavClearSearch;

    private RecyclerView recyclerFavorites;
    private TextView tvEmptyFavorites;

    private FileAdapter fileAdapter;
    private final List<FileItem> allFavorites = new ArrayList<>();
    private final List<FileItem> filteredFavorites = new ArrayList<>();
    private SortMode currentSortMode = SortMode.NAME_ASC;
    private boolean isSearchActive = false;
    private String currentSearchQuery = "";
    private OnBackPressedCallback backPressedCallback;

    private Dialog currentActiveDialog = null;
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public FavoriteFragment() {
        // Required empty public constructor
    }

    public static FavoriteFragment newInstance() {
        return new FavoriteFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_favorite, container, false);

        initViews(view);
        setupRecyclerView();
        setupSearchLogic();
        setupBackNavigation();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadFavorites();
        updateBackCallbackState();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) {
            if (currentActiveDialog != null && currentActiveDialog.isShowing()) {
                currentActiveDialog.dismiss();
                currentActiveDialog = null;
            }
        } else {
            if (isAdded() && recyclerFavorites != null) {
                loadFavorites();
            }
        }
        updateBackCallbackState();
    }

    private void setActiveDialog(Dialog dialog) {
        this.currentActiveDialog = dialog;
        dialog.setOnDismissListener(d -> {
            if (currentActiveDialog == d) {
                currentActiveDialog = null;
            }
            updateBackCallbackState();
        });
        updateBackCallbackState();
        dialog.show();
    }

    public void updateBackCallbackState() {
        if (!isAdded() || isHidden()) {
            if (backPressedCallback != null) {
                backPressedCallback.setEnabled(false);
            }
            return;
        }
        boolean hasDialog = (currentActiveDialog != null && currentActiveDialog.isShowing());
        boolean hasSearch = isSearchActive;
        if (backPressedCallback != null) {
            backPressedCallback.setEnabled(hasDialog || hasSearch);
        }
    }

    private void initViews(View view) {
        favHeaderLayout = view.findViewById(R.id.fav_header_layout);
        btnFavSearch = view.findViewById(R.id.btn_fav_search);
        btnFavSort = view.findViewById(R.id.btn_fav_sort);

        favSearchLayout = view.findViewById(R.id.fav_search_layout);
        etFavSearch = view.findViewById(R.id.et_fav_search);
        btnFavCloseSearch = view.findViewById(R.id.btn_fav_close_search);
        btnFavClearSearch = view.findViewById(R.id.btn_fav_clear_search);

        recyclerFavorites = view.findViewById(R.id.recycler_favorites);
        tvEmptyFavorites = view.findViewById(R.id.tv_empty_favorites);

        ImageButton btnDrawerFav = view.findViewById(R.id.btn_drawer_fav);
        if (btnDrawerFav != null) {
            btnDrawerFav.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).openDrawer();
                }
            });
        }

        btnFavSort.setOnClickListener(v -> showSortDialog());
        btnFavSearch.setOnClickListener(v -> openSearch());
    }

    private void setupRecyclerView() {
        fileAdapter = new FileAdapter(this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        recyclerFavorites.setLayoutManager(layoutManager);
        recyclerFavorites.setAdapter(fileAdapter);

        DividerItemDecoration divider = new DividerItemDecoration(requireContext(), layoutManager.getOrientation());
        recyclerFavorites.addItemDecoration(divider);
    }

    private void setupSearchLogic() {
        btnFavCloseSearch.setOnClickListener(v -> closeSearch());
        btnFavClearSearch.setOnClickListener(v -> etFavSearch.setText(""));

        etFavSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = (s != null) ? s.toString().trim() : "";
                filterFavoritesRealTime(currentSearchQuery);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void setupBackNavigation() {
        backPressedCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                if (currentActiveDialog != null && currentActiveDialog.isShowing()) {
                    currentActiveDialog.dismiss();
                    currentActiveDialog = null;
                    updateBackCallbackState();
                    return;
                }
                if (isSearchActive) {
                    closeSearch();
                    return;
                }
                setEnabled(false);
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backPressedCallback);
    }

    private void openSearch() {
        isSearchActive = true;
        favHeaderLayout.setVisibility(View.GONE);
        favSearchLayout.setVisibility(View.VISIBLE);
        etFavSearch.requestFocus();
        updateBackCallbackState();

        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(etFavSearch, InputMethodManager.SHOW_IMPLICIT);
        }

        recyclerFavorites.announceForAccessibility(getString(R.string.search_opened_announcement));
    }

    private void closeSearch() {
        isSearchActive = false;
        currentSearchQuery = "";
        etFavSearch.setText("");
        updateBackCallbackState();

        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etFavSearch.getWindowToken(), 0);
        }

        favSearchLayout.setVisibility(View.GONE);
        favHeaderLayout.setVisibility(View.VISIBLE);

        applySortingAndDisplay();
        recyclerFavorites.announceForAccessibility(getString(R.string.search_closed));
    }

    private void loadFavorites() {
        allFavorites.clear();
        allFavorites.addAll(FavoriteManager.getInstance(requireContext()).getFavoriteItems());

        if (isAdded()) {
            currentSortMode = SortPreferences.getFavoriteSortMode(requireContext());
        }

        if (isSearchActive && !TextUtils.isEmpty(currentSearchQuery)) {
            filterFavoritesRealTime(currentSearchQuery);
        } else {
            applySortingAndDisplay();
        }
    }

    private void applySortingAndDisplay() {
        Collections.sort(allFavorites, currentSortMode.getComparator());
        fileAdapter.updateList(allFavorites);

        if (allFavorites.isEmpty()) {
            tvEmptyFavorites.setVisibility(View.VISIBLE);
            tvEmptyFavorites.setText(R.string.no_favorites_yet);
        } else {
            tvEmptyFavorites.setVisibility(View.GONE);
        }
    }

    private void filterFavoritesRealTime(String query) {
        if (TextUtils.isEmpty(query)) {
            applySortingAndDisplay();
            return;
        }

        filteredFavorites.clear();
        String lower = query.toLowerCase(Locale.ENGLISH);

        for (FileItem item : allFavorites) {
            if (item.getName().toLowerCase(Locale.ENGLISH).contains(lower)) {
                filteredFavorites.add(item);
            }
        }

        fileAdapter.updateList(filteredFavorites);

        if (filteredFavorites.isEmpty()) {
            tvEmptyFavorites.setVisibility(View.VISIBLE);
            String noResultsText = getString(R.string.search_no_results, query);
            tvEmptyFavorites.setText(noResultsText);
            tvEmptyFavorites.announceForAccessibility(noResultsText);
        } else {
            tvEmptyFavorites.setVisibility(View.GONE);
            String countText = getString(R.string.search_results_count, filteredFavorites.size(), query);
            recyclerFavorites.announceForAccessibility(countText);
        }
    }

    private void showSortDialog() {
        if (!isAdded()) return;
        boolean hasOverride = SortPreferences.hasFavoriteSortOverride(requireContext());

        Dialog dialog = SortDialogHelper.createSortDialog(
                requireContext(),
                currentSortMode,
                hasOverride,
                getString(R.string.sort_apply_to_favorites_only),
                getString(R.string.sort_apply_to_favorites_only_hint),
                (selectedMode, isCurrentOnly) -> {
                    currentSortMode = selectedMode;
                    String sortName = getString(selectedMode.getTitleResId());
                    String announcement;

                    if (isCurrentOnly) {
                        SortPreferences.setFavoriteSortMode(requireContext(), selectedMode);
                        announcement = getString(R.string.sorted_favorites_announcement, sortName);
                    } else {
                        SortPreferences.setGlobalSortMode(requireContext(), selectedMode);
                        SortPreferences.clearFavoriteSortMode(requireContext());
                        announcement = getString(R.string.sorted_globally_announcement, sortName);
                    }

                    if (isSearchActive && !TextUtils.isEmpty(currentSearchQuery)) {
                        filterFavoritesRealTime(currentSearchQuery);
                    } else {
                        applySortingAndDisplay();
                    }

                    recyclerFavorites.announceForAccessibility(announcement);
                    Toast.makeText(requireContext(), announcement, Toast.LENGTH_SHORT).show();
                }
        );
        setActiveDialog(dialog);
    }

    @Override
    public void onItemClick(FileItem item, int position) {
        if (item.isNetworkItem()) {
            NetworkItem netItem = item.getNetworkItem();
            if (netItem.isDirectory()) {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).openNetworkFolder(netItem);
                }
            } else {
                showNetworkItemClickDialog(netItem);
            }
            return;
        }
        if (item.isDirectory()) {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openFolderInHome(item.getFile());
            }
        } else if (ArchiveViewerHelper.isArchive(item.getName())) {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openArchiveFile(item.getFile());
            } else {
                openFile(item.getFile());
            }
        } else {
            openFile(item.getFile());
        }
    }

    @Override
    public void onActionOpenWith(FileItem item) {
        if (item.isDirectory()) return;
        if (item.isNetworkItem()) {
            downloadAndOpenNetworkFile(item.getNetworkItem());
            return;
        }
        FileOpener.openFileWith(requireContext(), item.getFile());
    }

    @Override
    public void onActionProperties(FileItem item) {
        if (item.isNetworkItem()) {
            NetworkItem netItem = item.getNetworkItem();
            String info = "Name: " + netItem.getName() + "\n"
                    + "Type: " + (netItem.isDirectory() ? "Folder" : "File") + "\n"
                    + "Size: " + netItem.getFormattedSize() + "\n"
                    + "Location: " + (netItem.getServerInfo() != null ? netItem.getServerInfo().getName() : "Google Drive");
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(netItem.getName())
                    .setMessage(info)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        FilePropertiesDialog.show(requireContext(), item, this::setActiveDialog);
    }

    @Override
    public void onItemLongClick(FileItem item, int position) {
        if (item.isNetworkItem()) {
            showNetworkItemFavoriteMenu(item.getNetworkItem());
            return;
        }
        List<ActionConfig> configs = ActionMenuManager.getInstance(requireContext()).getActionConfigs();
        List<String> options = new ArrayList<>();
        List<String> actionIds = new ArrayList<>();

        for (ActionConfig config : configs) {
            if (!config.isEnabled()) continue;
            if (ActionConfig.ACTION_SELECT.equals(config.getId())) continue;
            if (ActionConfig.ACTION_OPEN_WITH.equals(config.getId()) && item.isDirectory()) continue;
            if (ActionConfig.ACTION_EDIT.equals(config.getId()) && (item.isDirectory() || (!item.isAudio() && !item.isVideo()))) continue;
            if (ActionConfig.ACTION_BATCH_CONVERT.equals(config.getId()) && !item.isDirectory()) continue;

            if (ActionConfig.ACTION_FAVORITE.equals(config.getId())) {
                options.add(getString(R.string.action_remove_favorite));
            } else if (ActionConfig.ACTION_EDIT.equals(config.getId()) && item.isVideo()) {
                options.add(getString(R.string.action_convert_to_audio));
            } else {
                options.add(getString(config.getTitleResId()));
            }
            actionIds.add(config.getId());
        }

        if (options.isEmpty()) return;

        String[] optionsArray = options.toArray(new String[0]);

        Dialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(item.getName())
                .setItems(optionsArray, (d, which) -> {
                    String actionId = actionIds.get(which);
                    switch (actionId) {
                        case ActionConfig.ACTION_OPEN_WITH:
                            onActionOpenWith(item);
                            break;
                        case ActionConfig.ACTION_FAVORITE:
                            onActionFavorite(item);
                            break;
                        case ActionConfig.ACTION_COPY:
                            onActionCopy(item);
                            break;
                        case ActionConfig.ACTION_CUT:
                            onActionCut(item);
                            break;
                        case ActionConfig.ACTION_RENAME:
                            onActionRename(item);
                            break;
                        case ActionConfig.ACTION_SHARE:
                            onActionShare(item);
                            break;
                        case ActionConfig.ACTION_EDIT:
                            onActionEdit(item);
                            break;
                        case ActionConfig.ACTION_BATCH_CONVERT:
                            onActionBatchConvert(item);
                            break;
                        case ActionConfig.ACTION_DELETE:
                            onActionDelete(item);
                            break;
                        case ActionConfig.ACTION_PROPERTIES:
                            onActionProperties(item);
                            break;
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .create();
        setActiveDialog(dialog);
    }

    @Override
    public void onActionEdit(FileItem item) {
        EditActionHelper.showEditSubmenu(requireContext(), item, new EditActionHelper.DialogCallback() {
            @Override
            public void onActiveDialogChanged(Dialog dialog) {
                setActiveDialog(dialog);
            }

            @Override
            public void onFilesChanged() {
                loadFavorites();
            }
        });
    }

    @Override
    public void onActionBatchConvert(FileItem item) {
        if (!item.isDirectory()) return;
        EditActionHelper.showBatchConvertDialog(requireContext(), item.getFile(), new EditActionHelper.DialogCallback() {
            @Override
            public void onActiveDialogChanged(Dialog dialog) {
                setActiveDialog(dialog);
            }

            @Override
            public void onFilesChanged() {
                loadFavorites();
            }
        });
    }

    @Override
    public void onActionSelect(FileItem item, int position) {
        fileAdapter.setSelectionMode(true);
        fileAdapter.toggleSelection(position);
    }

    @Override
    public void onActionFavorite(FileItem item) {
        FavoriteManager.getInstance(requireContext()).removeFavorite(item.getPath());
        String msg = getString(R.string.toast_removed_favorite, item.getName());
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
        recyclerFavorites.announceForAccessibility(msg);
        loadFavorites();
    }

    @Override
    public void onActionCopy(FileItem item) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).copyToClipboard(java.util.Collections.singletonList(item.getFile()), false);
        }
        String msg = getString(R.string.toast_copied, item.getName());
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
        recyclerFavorites.announceForAccessibility(msg);
    }

    @Override
    public void onActionCut(FileItem item) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).copyToClipboard(java.util.Collections.singletonList(item.getFile()), true);
        }
        String msg = getString(R.string.toast_cut, item.getName());
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
        recyclerFavorites.announceForAccessibility(msg);
    }

    @Override
    public void onActionRename(FileItem item) {
        Toast.makeText(requireContext(), "Use Home tab to rename items", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onActionShare(FileItem item) {
        shareFile(item.getFile(), item.getExtension(), item.getName());
    }

    @Override
    public void onActionDelete(FileItem item) {
        Dialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_delete_title)
                .setMessage(getString(R.string.dialog_delete_message, item.getName()))
                .setPositiveButton(R.string.btn_delete, (d, w) -> {
                    if (item.getFile().delete()) {
                        FavoriteManager.getInstance(requireContext()).removeFavorite(item.getPath());
                        loadFavorites();
                        Toast.makeText(requireContext(), R.string.toast_delete_success, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .create();
        setActiveDialog(dialog);
    }

    @Override
    public void onSelectionCountChanged(int count) {}

    @Override
    public void onRangeSelected(int start, int end, int count, FileItem first, FileItem last) {}

    private void openFile(File file) {
        FileOpener.openFile(requireContext(), file, isSearchActive ? filteredFavorites : allFavorites);
    }

    private void shareFile(File file, String extension, String displayName) {
        try {
            Uri fileUri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".provider",
                    file
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase(Locale.ENGLISH));
            if (mimeType == null) mimeType = "*/*";

            shareIntent.setType(mimeType);
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share) + " " + displayName));
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Share failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showNetworkItemClickDialog(NetworkItem netItem) {
        String[] options = new String[]{"Open / Stream", "Download to Phone"};
        Dialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(netItem.getName())
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        downloadAndOpenNetworkFile(netItem);
                    } else {
                        downloadNetworkFileToDownloads(netItem);
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .create();
        setActiveDialog(dialog);
    }

    private void showNetworkItemFavoriteMenu(NetworkItem netItem) {
        List<String> options = new ArrayList<>();
        List<Integer> actionIds = new ArrayList<>();

        if (netItem.isDirectory()) {
            options.add("Open Folder");
            actionIds.add(1);
        } else {
            options.add("Open / Stream");
            actionIds.add(2);
            options.add("Download to Phone");
            actionIds.add(3);
        }

        options.add("Copy");
        actionIds.add(4);

        options.add("Cut");
        actionIds.add(5);

        if (!netItem.isDirectory()) {
            options.add("Share");
            actionIds.add(6);
        }

        options.add("Remove from Favorites");
        actionIds.add(7);

        options.add("Delete");
        actionIds.add(8);

        Dialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(netItem.getName())
                .setItems(options.toArray(new String[0]), (d, which) -> {
                    int action = actionIds.get(which);
                    if (action == 1) {
                        if (getActivity() instanceof MainActivity) {
                            ((MainActivity) getActivity()).openNetworkFolder(netItem);
                        }
                    } else if (action == 2) {
                        downloadAndOpenNetworkFile(netItem);
                    } else if (action == 3) {
                        downloadNetworkFileToDownloads(netItem);
                    } else if (action == 4) {
                        AppClipboard.getInstance().setNetworkItems(Collections.singletonList(netItem), false);
                        Toast.makeText(requireContext(), netItem.getName() + " copied", Toast.LENGTH_SHORT).show();
                    } else if (action == 5) {
                        AppClipboard.getInstance().setNetworkItems(Collections.singletonList(netItem), true);
                        Toast.makeText(requireContext(), netItem.getName() + " cut", Toast.LENGTH_SHORT).show();
                    } else if (action == 6) {
                        shareNetworkFile(netItem);
                    } else if (action == 7) {
                        FavoriteManager.getInstance(requireContext()).removeFavorite(netItem.getPath());
                        loadFavorites();
                        Toast.makeText(requireContext(), getString(R.string.toast_removed_favorite, netItem.getName()), Toast.LENGTH_SHORT).show();
                    } else if (action == 8) {
                        confirmDeleteNetworkItem(netItem);
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .create();
        setActiveDialog(dialog);
    }

    private void downloadAndOpenNetworkFile(NetworkItem item) {
        Toast.makeText(requireContext(), "Opening " + item.getName() + "...", Toast.LENGTH_SHORT).show();
        executorService.execute(() -> {
            try {
                File tempFile = new File(requireContext().getCacheDir(), item.getName());
                downloadNetworkItemToFile(item, tempFile);
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    if (tempFile.exists()) {
                        FileOpener.openFile(requireContext(), tempFile, Collections.emptyList());
                    } else {
                        Toast.makeText(requireContext(), "Failed to open file", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), "Failed to open: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void downloadNetworkFileToDownloads(NetworkItem item) {
        Toast.makeText(requireContext(), getString(R.string.toast_downloading, item.getName()), Toast.LENGTH_SHORT).show();
        executorService.execute(() -> {
            try {
                File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File destFile = new File(downloadsDir, item.getName());
                downloadNetworkItemToFile(item, destFile);
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    if (destFile.exists()) {
                        Toast.makeText(requireContext(), getString(R.string.toast_download_complete, item.getName()), Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(requireContext(), "Download failed", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void shareNetworkFile(NetworkItem item) {
        Toast.makeText(requireContext(), "Preparing " + item.getName() + " for sharing...", Toast.LENGTH_SHORT).show();
        executorService.execute(() -> {
            try {
                File tempFile = new File(requireContext().getCacheDir(), item.getName());
                downloadNetworkItemToFile(item, tempFile);
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    if (tempFile.exists()) {
                        shareFile(tempFile, item.getExtension(), item.getName());
                    } else {
                        Toast.makeText(requireContext(), "Could not prepare file for sharing", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), "Share error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void downloadNetworkItemToFile(NetworkItem item, File destFile) throws Exception {
        NetworkServerInfo serverInfo = item.getServerInfo();
        CloudStorageManager csm = CloudStorageManager.getInstance(requireContext());
        if (serverInfo != null && NetworkServerInfo.TYPE_GOOGLE_DRIVE.equalsIgnoreCase(serverInfo.getType()) || "Google Drive".equalsIgnoreCase(item.getShareName())) {
            if (csm.isOAuthConnected()) {
                GoogleDriveService.getInstance(requireContext()).downloadFile(item.getPath(), destFile, csm.getGoogleDriveToken());
            } else {
                DocumentFileTreeHelper.downloadFile(requireContext(), Uri.parse(item.getPath()), destFile, null);
            }
        } else if (serverInfo != null && NetworkServerInfo.TYPE_SMB.equalsIgnoreCase(serverInfo.getType())) {
            SmbClientHelper.downloadFile(serverInfo, item, destFile, null);
        } else if (serverInfo != null) {
            FtpClientHelper.downloadFile(serverInfo, item, destFile, null);
        }
    }

    private void confirmDeleteNetworkItem(NetworkItem item) {
        Dialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_delete_title)
                .setMessage(getString(R.string.dialog_delete_message, item.getName()))
                .setPositiveButton(R.string.btn_delete, (d, which) -> {
                    executorService.execute(() -> {
                        try {
                            NetworkServerInfo serverInfo = item.getServerInfo();
                            CloudStorageManager csm = CloudStorageManager.getInstance(requireContext());
                            if (serverInfo != null && NetworkServerInfo.TYPE_GOOGLE_DRIVE.equalsIgnoreCase(serverInfo.getType()) || "Google Drive".equalsIgnoreCase(item.getShareName())) {
                                if (csm.isOAuthConnected()) {
                                    GoogleDriveService.getInstance(requireContext()).deleteItem(item.getPath(), csm.getGoogleDriveToken());
                                } else {
                                    DocumentFileTreeHelper.deleteItem(requireContext(), Uri.parse(item.getPath()));
                                }
                            } else if (serverInfo != null && NetworkServerInfo.TYPE_SMB.equalsIgnoreCase(serverInfo.getType())) {
                                SmbClientHelper.deleteItem(serverInfo, item);
                            } else if (serverInfo != null) {
                                FtpClientHelper.deleteItem(serverInfo, item);
                            }
                            mainHandler.post(() -> {
                                if (!isAdded()) return;
                                FavoriteManager.getInstance(requireContext()).removeFavorite(item.getPath());
                                loadFavorites();
                                Toast.makeText(requireContext(), R.string.toast_delete_success, Toast.LENGTH_SHORT).show();
                            });
                        } catch (Exception e) {
                            mainHandler.post(() -> {
                                if (!isAdded()) return;
                                Toast.makeText(requireContext(), "Delete failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                        }
                    });
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .create();
        setActiveDialog(dialog);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}
