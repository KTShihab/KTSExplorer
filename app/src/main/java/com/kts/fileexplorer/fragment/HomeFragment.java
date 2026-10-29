package com.kts.fileexplorer.fragment;

import android.Manifest;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import com.kts.fileexplorer.MainActivity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import com.google.android.material.textfield.TextInputEditText;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import android.widget.TextView;
import android.widget.Toast;
import com.kts.fileexplorer.util.StorageHelper;
import com.kts.fileexplorer.util.BatchRenameHelper;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.kts.fileexplorer.R;
import com.kts.fileexplorer.adapter.FileAdapter;
import com.kts.fileexplorer.model.ActionConfig;
import android.os.StatFs;
import com.kts.fileexplorer.model.FileItem;
import com.kts.fileexplorer.model.SortMode;
import com.kts.fileexplorer.util.ActionMenuManager;
import com.kts.fileexplorer.util.AppSettings;
import com.kts.fileexplorer.util.EditActionHelper;
import com.kts.fileexplorer.util.FavoriteManager;
import com.kts.fileexplorer.util.ArchiveViewerHelper;
import com.kts.fileexplorer.util.FileOpener;
import com.kts.fileexplorer.util.FilePropertiesDialog;
import com.kts.fileexplorer.util.SharedFileSaver;
import com.kts.fileexplorer.util.SortDialogHelper;
import com.kts.fileexplorer.util.SortPreferences;
import com.kts.fileexplorer.util.ZipViewerHelper;
import com.kts.fileexplorer.util.AppClipboard;
import com.kts.fileexplorer.network.NetworkItem;
import com.kts.fileexplorer.network.NetworkServerInfo;
import com.kts.fileexplorer.network.SmbClientHelper;
import com.kts.fileexplorer.network.FtpClientHelper;
import com.kts.fileexplorer.cloud.CloudStorageManager;
import com.kts.fileexplorer.cloud.DocumentFileTreeHelper;
import com.kts.fileexplorer.cloud.GoogleDriveService;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * HomeFragment displays storage folders and files,
 * supports native screen reader custom actions (swipe up/down in TalkBack & Jieshuo),
 * Multi-Selection Mode, Double-Tap-Hold Range Selection, Add to Favorites,
 * real-time live search, 8 sorting methods, folder ZIP sharing, and bottom-right Paste FAB.
 */
public class HomeFragment extends Fragment implements FileAdapter.OnFileItemActionListener {

    private RecyclerView recyclerView;
    private FileAdapter fileAdapter;
    private ProgressBar progressBar;
    private TextView tvEmptyFolder;
    private TextView tvCurrentPath;
    private ImageButton btnUp;
    private ImageButton btnSort;
    private ImageButton btnSearch;
    private ImageButton btnNew;
    private ImageButton btnRefresh;
    private LinearLayout headerLayout;

    // Selection Mode Header & Bottom Bar Views
    private LinearLayout selectionHeaderLayout;
    private View selectionBottomBar;
    private ImageButton btnCloseSelection;
    private TextView tvSelectionCount;
    private ImageButton btnSelectAll;
    private View btnFavoriteSelection;
    private View btnCopySelection;
    private View btnRenameSelection;
    private View btnCutSelection;
    private View btnShareSelection;
    private View btnDeleteSelection;

    // Live Search Views
    private LinearLayout searchLayout;
    private EditText etSearch;
    private ImageButton btnCloseSearch;
    private ImageButton btnClearSearch;
    private boolean isSearchActive = false;
    private String currentSearchQuery = "";

    private LinearLayout layoutPermissionDenied;
    private Button btnGrantStorage;

    // Paste Action FAB (positioned at bottom right corner, above Favorite tab)
    private ExtendedFloatingActionButton fabPaste;
    private FloatingActionButton fabCancelPaste;

    // Save Shared Files Mode (WhatsApp, Telegram, etc.)
    private LinearLayout layoutSaveHere;
    private TextView tvSaveHereTitle;
    private TextView tvSaveHereFolder;
    private com.google.android.material.button.MaterialButton btnCancelSaveHere;
    private com.google.android.material.button.MaterialButton btnConfirmSaveHere;
    private final List<Uri> pendingSharedUris = new ArrayList<>();
    private boolean isSaveSharedMode = false;

    // Storage Selector & Status (in Header)
    private View layoutStorageSelector;
    private TextView tvStorageStatus;
    private ImageView ivStorageDropdown;
    private ImageButton btnDrawer;
    private StorageHelper.StorageVolumeInfo activeVolume;

    private File rootDirectory;
    private File currentDirectory;
    private final List<FileItem> currentLoadedItems = new ArrayList<>();
    private final List<FileItem> filteredDisplayItems = new ArrayList<>();

    // Active Archive (ZIP) state
    private File activeArchiveRoot = null;
    private String activeArchiveName = null;

    // Active sorting mode (default: Name A to Z)
    private SortMode currentSortMode = SortMode.NAME_ASC;

    // Clipboard reference for copy/cut operations (supports single and multiple items)
    private final List<File> clipboardFiles = new ArrayList<>();
    private boolean isCutOperation = false;

    // Reference to currently showing dialog for accessible back navigation
    private Dialog currentActiveDialog = null;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ActivityResultLauncher<Intent> manageAllFilesLauncher;
    private ActivityResultLauncher<String[]> legacyStorageLauncher;
    private OnBackPressedCallback backPressedCallback;

    public HomeFragment() {
        // Required empty public constructor
    }

    public static HomeFragment newInstance() {
        return new HomeFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        manageAllFilesLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (hasStoragePermission()) {
                        onPermissionGranted();
                    } else {
                        onPermissionDenied();
                    }
                }
        );

        legacyStorageLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean readGranted = Boolean.TRUE.equals(result.get(Manifest.permission.READ_EXTERNAL_STORAGE));
                    if (readGranted) {
                        onPermissionGranted();
                    } else {
                        onPermissionDenied();
                    }
                }
        );

        rootDirectory = Environment.getExternalStorageDirectory();
        currentDirectory = rootDirectory;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        initViews(view);
        setupRecyclerView();
        setupListeners();
        setupSearchLogic();
        setupBackNavigation();

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        checkAndRequestStoragePermission();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (hasStoragePermission()) {
            if (layoutPermissionDenied.getVisibility() == View.VISIBLE) {
                onPermissionGranted();
            } else {
                updateStorageInfoBar();
                if (!isSearchActive && (fileAdapter == null || !fileAdapter.isSelectionMode()) && currentDirectory != null) {
                    loadDirectory(currentDirectory);
                } else if (fileAdapter != null) {
                    fileAdapter.notifyDataSetChanged();
                }
            }
        }
        updatePasteButtonsVisibility();
    }

    public void setClipboardFiles(List<File> files, boolean isCut) {
        clipboardFiles.clear();
        clipboardFiles.addAll(files);
        isCutOperation = isCut;
        AppClipboard.getInstance().setLocalFiles(files, isCut);
        if (!clipboardFiles.isEmpty()) {
            if (clipboardFiles.size() == 1) {
                showPasteButtons(clipboardFiles.get(0).getName());
            } else {
                showPasteButtons(getString(R.string.selection_mode_title, clipboardFiles.size()));
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }

    private void initViews(View view) {
        headerLayout = view.findViewById(R.id.header_layout);
        recyclerView = view.findViewById(R.id.recycler_files);
        progressBar = view.findViewById(R.id.progress_bar);
        tvEmptyFolder = view.findViewById(R.id.tv_empty_folder);
        tvCurrentPath = view.findViewById(R.id.tv_current_path);
        btnUp = view.findViewById(R.id.btn_up);
        btnSort = view.findViewById(R.id.btn_sort);
        btnSearch = view.findViewById(R.id.btn_search);
        btnNew = view.findViewById(R.id.btn_new);
        btnRefresh = view.findViewById(R.id.btn_refresh);

        btnDrawer = view.findViewById(R.id.btn_drawer);
        if (btnDrawer != null) {
            btnDrawer.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).openDrawer();
                }
            });
        }

        layoutStorageSelector = view.findViewById(R.id.layout_storage_selector);
        tvStorageStatus = view.findViewById(R.id.tv_storage_status);
        ivStorageDropdown = view.findViewById(R.id.iv_storage_dropdown);
        if (layoutStorageSelector != null) {
            layoutStorageSelector.setOnClickListener(v -> showStorageSwitcherDialog());
        }

        selectionHeaderLayout = view.findViewById(R.id.selection_header_layout);
        selectionBottomBar = view.findViewById(R.id.selection_bottom_bar);
        btnCloseSelection = view.findViewById(R.id.btn_close_selection);
        tvSelectionCount = view.findViewById(R.id.tv_selection_count);
        btnSelectAll = view.findViewById(R.id.btn_select_all);
        btnFavoriteSelection = view.findViewById(R.id.btn_favorite_selection);
        btnCopySelection = view.findViewById(R.id.btn_copy_selection);
        btnRenameSelection = view.findViewById(R.id.btn_rename_selection);
        btnCutSelection = view.findViewById(R.id.btn_cut_selection);
        btnShareSelection = view.findViewById(R.id.btn_share_selection);
        btnDeleteSelection = view.findViewById(R.id.btn_delete_selection);

        searchLayout = view.findViewById(R.id.search_layout);
        etSearch = view.findViewById(R.id.et_search);
        btnCloseSearch = view.findViewById(R.id.btn_close_search);
        btnClearSearch = view.findViewById(R.id.btn_clear_search);

        layoutPermissionDenied = view.findViewById(R.id.layout_permission_denied);
        btnGrantStorage = view.findViewById(R.id.btn_grant_storage);

        fabPaste = view.findViewById(R.id.fab_paste);
        fabCancelPaste = view.findViewById(R.id.fab_cancel_paste);

        layoutSaveHere = view.findViewById(R.id.layout_save_here);
        tvSaveHereTitle = view.findViewById(R.id.tv_save_here_title);
        tvSaveHereFolder = view.findViewById(R.id.tv_save_here_folder);
        btnCancelSaveHere = view.findViewById(R.id.btn_cancel_save_here);
        btnConfirmSaveHere = view.findViewById(R.id.btn_confirm_save_here);
    }

    private void setupRecyclerView() {
        fileAdapter = new FileAdapter(this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(fileAdapter);

        DividerItemDecoration divider = new DividerItemDecoration(requireContext(), layoutManager.getOrientation());
        recyclerView.addItemDecoration(divider);
    }

    private void setupListeners() {
        btnUp.setOnClickListener(v -> navigateUp());
        btnSort.setOnClickListener(v -> showSortDialog());
        btnSearch.setOnClickListener(v -> openSearch());
        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> {
                btnRefresh.announceForAccessibility(getString(R.string.msg_refreshing));
                if (currentDirectory != null && hasStoragePermission()) {
                    loadDirectory(currentDirectory);
                    updateStorageInfoBar();
                }
                btnRefresh.announceForAccessibility(getString(R.string.msg_refreshed));
            });
        }
        if (btnNew != null) {
            btnNew.setOnClickListener(v -> showNewItemDialog());
        }
        btnGrantStorage.setOnClickListener(v -> requestStoragePermission());

        fabPaste.setOnClickListener(v -> handlePaste());
        fabCancelPaste.setOnClickListener(v -> cancelClipboard());

        btnCancelSaveHere.setOnClickListener(v -> cancelSaveSharedFilesMode());
        btnConfirmSaveHere.setOnClickListener(v -> confirmSaveSharedFiles());

        // Selection header actions
        btnCloseSelection.setOnClickListener(v -> exitSelectionMode());
        btnSelectAll.setOnClickListener(v -> {
            if (fileAdapter.getSelectedPaths().size() == fileAdapter.getCurrentList().size()) {
                fileAdapter.deselectAll();
            } else {
                fileAdapter.selectAll();
            }
        });
        btnFavoriteSelection.setOnClickListener(v -> favoriteSelectedItems());
        btnCopySelection.setOnClickListener(v -> copySelectedItems());
        if (btnRenameSelection != null) {
            btnRenameSelection.setOnClickListener(v -> renameSelectedItems());
        }
        btnCutSelection.setOnClickListener(v -> cutSelectedItems());
        btnShareSelection.setOnClickListener(v -> shareSelectedItems());
        btnDeleteSelection.setOnClickListener(v -> deleteSelectedItems());
    }

    private void setupSearchLogic() {
        btnCloseSearch.setOnClickListener(v -> closeSearch());
        btnClearSearch.setOnClickListener(v -> etSearch.setText(""));

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = (s != null) ? s.toString().trim() : "";
                filterFilesRealTime(currentSearchQuery);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void openSearch() {
        if (fileAdapter.isSelectionMode()) {
            exitSelectionMode();
        }
        isSearchActive = true;
        headerLayout.setVisibility(View.GONE);
        searchLayout.setVisibility(View.VISIBLE);
        etSearch.requestFocus();
        updateBackCallbackState();

        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT);
        }

        recyclerView.announceForAccessibility(getString(R.string.search_opened_announcement));
    }

    private void closeSearch() {
        isSearchActive = false;
        currentSearchQuery = "";
        etSearch.setText("");
        updateBackCallbackState();

        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
        }

        searchLayout.setVisibility(View.GONE);
        headerLayout.setVisibility(View.VISIBLE);

        applySortingAndDisplay();
        recyclerView.announceForAccessibility(getString(R.string.search_closed));
    }

    private void filterFilesRealTime(String query) {
        if (TextUtils.isEmpty(query)) {
            applySortingAndDisplay();
            return;
        }

        filteredDisplayItems.clear();
        String lowerQuery = query.toLowerCase(Locale.ENGLISH);

        for (FileItem item : currentLoadedItems) {
            if (item.getName().toLowerCase(Locale.ENGLISH).contains(lowerQuery)) {
                filteredDisplayItems.add(item);
            }
        }

        fileAdapter.updateList(filteredDisplayItems);

        if (filteredDisplayItems.isEmpty()) {
            tvEmptyFolder.setVisibility(View.VISIBLE);
            String noResultsText = getString(R.string.search_no_results, query);
            tvEmptyFolder.setText(noResultsText);
            tvEmptyFolder.announceForAccessibility(noResultsText);
        } else {
            tvEmptyFolder.setVisibility(View.GONE);
            String countText = getString(R.string.search_results_count, filteredDisplayItems.size(), query);
            tvCurrentPath.announceForAccessibility(countText);
        }
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
                if (fileAdapter != null && fileAdapter.isSelectionMode()) {
                    exitSelectionMode();
                    return;
                }
                if (isSearchActive) {
                    closeSearch();
                    return;
                }
                if (isSaveSharedMode) {
                    if (canNavigateUp()) {
                        navigateUp();
                    } else {
                        cancelSaveSharedFilesMode();
                    }
                    return;
                }
                if (activeArchiveRoot != null) {
                    if (currentDirectory != null && currentDirectory.getAbsolutePath().equals(activeArchiveRoot.getAbsolutePath())) {
                        exitArchive();
                    } else if (canNavigateUp()) {
                        navigateUp();
                    } else {
                        exitArchive();
                    }
                    return;
                }
                if (canNavigateUp()) {
                    navigateUp();
                    return;
                }
                setEnabled(false);
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backPressedCallback);
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
        boolean hasSelection = (fileAdapter != null && fileAdapter.isSelectionMode());
        boolean hasSearch = isSearchActive;
        boolean hasSaveShared = isSaveSharedMode;
        boolean hasArchive = (activeArchiveRoot != null);
        boolean canGoUp = canNavigateUp();
        if (backPressedCallback != null) {
            backPressedCallback.setEnabled(hasDialog || hasSelection || hasSearch || hasSaveShared || hasArchive || canGoUp);
        }
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
            updatePasteButtonsVisibility();
            if (hasStoragePermission() && !isSearchActive && (fileAdapter == null || !fileAdapter.isSelectionMode()) && currentDirectory != null) {
                loadDirectory(currentDirectory);
            }
        }
        updateBackCallbackState();
    }

    // --- Multi-Selection Mode Logic ---

    public void enterSelectionMode(int initialPosition) {
        if (isSearchActive) {
            closeSearch();
        }
        fileAdapter.setSelectionMode(true);
        fileAdapter.toggleSelection(initialPosition);

        headerLayout.setVisibility(View.VISIBLE);
        if (selectionHeaderLayout != null) {
            selectionHeaderLayout.setVisibility(View.VISIBLE);
        }
        if (selectionBottomBar != null) {
            selectionBottomBar.setVisibility(View.VISIBLE);
        }
        updateSelectionTitle(1);
        updateBackCallbackState();
    }

    public void exitSelectionMode() {
        fileAdapter.setSelectionMode(false);
        if (selectionHeaderLayout != null) {
            selectionHeaderLayout.setVisibility(View.GONE);
        }
        if (selectionBottomBar != null) {
            selectionBottomBar.setVisibility(View.GONE);
        }
        headerLayout.setVisibility(View.VISIBLE);
        updateBackCallbackState();
        recyclerView.announceForAccessibility(getString(R.string.btn_exit_selection));
    }

    private void updateSelectionTitle(int count) {
        String title = getString(R.string.selection_mode_title, count);
        tvSelectionCount.setText(title);
        tvSelectionCount.setContentDescription(title);
    }

    @Override
    public void onSelectionCountChanged(int count) {
        updateSelectionTitle(count);
        if (count == 0 && fileAdapter.isSelectionMode()) {
            exitSelectionMode();
        }
    }

    @Override
    public void onRangeSelected(int start, int end, int count, FileItem first, FileItem last) {
        String announcement = getString(R.string.range_selected_announcement, count, first.getName(), last.getName(), fileAdapter.getSelectedPaths().size());
        recyclerView.announceForAccessibility(announcement);
        Toast.makeText(requireContext(), announcement, Toast.LENGTH_SHORT).show();
    }

    private void copySelectedItems() {
        List<FileItem> selected = fileAdapter.getSelectedItems();
        if (selected.isEmpty()) return;

        clipboardFiles.clear();
        for (FileItem item : selected) {
            clipboardFiles.add(item.getFile());
        }
        isCutOperation = false;
        AppClipboard.getInstance().setLocalFiles(clipboardFiles, false);
        exitSelectionMode();

        String msg;
        if (clipboardFiles.size() == 1) {
            msg = getString(R.string.toast_copied, clipboardFiles.get(0).getName());
            showPasteButtons(clipboardFiles.get(0).getName());
        } else {
            msg = getString(R.string.toast_copied_multiple, clipboardFiles.size());
            showPasteButtons(getString(R.string.selection_mode_title, clipboardFiles.size()));
        }
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
        recyclerView.announceForAccessibility(msg);
    }

    private void renameSelectedItems() {
        List<FileItem> selected = fileAdapter.getSelectedItems();
        if (selected.isEmpty()) return;

        if (selected.size() == 1) {
            showRenameDialog(selected.get(0));
            exitSelectionMode();
        } else {
            List<File> files = new ArrayList<>();
            for (FileItem item : selected) {
                files.add(item.getFile());
            }
            BatchRenameHelper.showDialog(requireActivity(), files, (successCount, failedCount) -> {
                exitSelectionMode();
                if (currentDirectory != null) {
                    loadDirectory(currentDirectory);
                }
                String announcement = getString(R.string.toast_batch_rename_success, successCount);
                recyclerView.announceForAccessibility(announcement);
            });
        }
    }

    private void cutSelectedItems() {
        List<FileItem> selected = fileAdapter.getSelectedItems();
        if (selected.isEmpty()) return;

        clipboardFiles.clear();
        for (FileItem item : selected) {
            clipboardFiles.add(item.getFile());
        }
        isCutOperation = true;
        AppClipboard.getInstance().setLocalFiles(clipboardFiles, true);
        exitSelectionMode();

        String msg;
        if (clipboardFiles.size() == 1) {
            msg = getString(R.string.toast_cut, clipboardFiles.get(0).getName());
            showPasteButtons(clipboardFiles.get(0).getName());
        } else {
            msg = getString(R.string.toast_cut_multiple, clipboardFiles.size());
            showPasteButtons(getString(R.string.selection_mode_title, clipboardFiles.size()));
        }
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
        recyclerView.announceForAccessibility(msg);
    }

    private void favoriteSelectedItems() {
        List<FileItem> selected = fileAdapter.getSelectedItems();
        if (selected.isEmpty()) return;

        int count = 0;
        for (FileItem item : selected) {
            FavoriteManager.getInstance(requireContext()).addFavorite(item.getPath());
            count++;
        }
        exitSelectionMode();
        fileAdapter.notifyDataSetChanged();

        String msg = getString(R.string.toast_favorite_multiple, count);
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
        recyclerView.announceForAccessibility(msg);
    }

    private void deleteSelectedItems() {
        List<FileItem> selected = fileAdapter.getSelectedItems();
        if (selected.isEmpty()) return;

        Dialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_delete_title)
                .setMessage(getString(R.string.delete_selected_confirmation, selected.size()))
                .setPositiveButton(R.string.btn_delete, (d, which) -> {
                    progressBar.setVisibility(View.VISIBLE);
                    executorService.execute(() -> {
                        int deletedCount = 0;
                        for (FileItem item : selected) {
                            if (deleteRecursively(item.getFile())) {
                                deletedCount++;
                            }
                        }
                        final int finalCount = deletedCount;
                        mainHandler.post(() -> {
                            if (!isAdded()) return;
                            progressBar.setVisibility(View.GONE);
                            exitSelectionMode();
                            String successMsg = getString(R.string.toast_delete_multiple_success, finalCount);
                            Toast.makeText(requireContext(), successMsg, Toast.LENGTH_SHORT).show();
                            recyclerView.announceForAccessibility(successMsg);
                            loadDirectory(currentDirectory);
                        });
                    });
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .create();
        setActiveDialog(dialog);
    }

    private void shareSelectedItems() {
        List<FileItem> selected = fileAdapter.getSelectedItems();
        if (selected.isEmpty()) return;

        if (selected.size() == 1 && selected.get(0).isDirectory()) {
            exitSelectionMode();
            handleShare(selected.get(0));
            return;
        }

        ArrayList<Uri> uris = new ArrayList<>();
        for (FileItem item : selected) {
            if (!item.isDirectory()) {
                uris.add(FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".provider", item.getFile()));
            }
        }

        if (uris.isEmpty()) {
            Toast.makeText(requireContext(), "Select files or a folder to share", Toast.LENGTH_SHORT).show();
            return;
        }

        exitSelectionMode();
        Intent shareIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
        shareIntent.setType("*/*");
        shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share)));
    }

    public boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            int readCheck = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE);
            return readCheck == PackageManager.PERMISSION_GRANTED;
        }
    }

    public void checkAndRequestStoragePermission() {
        if (hasStoragePermission()) {
            onPermissionGranted();
        } else {
            showPermissionDeniedUI();
            requestStoragePermission();
        }
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
                manageAllFilesLauncher.launch(intent);
            } catch (Exception e) {
                Intent fallbackIntent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                manageAllFilesLauncher.launch(fallbackIntent);
            }
        } else {
            legacyStorageLauncher.launch(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            });
        }
    }

    private void onPermissionGranted() {
        layoutPermissionDenied.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
        btnSort.setVisibility(View.VISIBLE);
        btnSearch.setVisibility(View.VISIBLE);
        if (btnNew != null) {
            btnNew.setVisibility(View.VISIBLE);
        }
        if (currentDirectory == null) {
            currentDirectory = rootDirectory;
        }
        loadDirectory(currentDirectory);
    }

    private void onPermissionDenied() {
        showPermissionDeniedUI();
        if (isAdded()) {
            Toast.makeText(requireContext(), R.string.permission_denied_message, Toast.LENGTH_LONG).show();
            layoutPermissionDenied.announceForAccessibility(getString(R.string.permission_denied_message));
        }
    }

    private void showPermissionDeniedUI() {
        layoutPermissionDenied.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);
        tvEmptyFolder.setVisibility(View.GONE);
        btnSort.setVisibility(View.GONE);
        btnSearch.setVisibility(View.GONE);
        if (btnNew != null) {
            btnNew.setVisibility(View.GONE);
        }
        hidePasteButtons();
        layoutPermissionDenied.announceForAccessibility(getString(R.string.permission_rationale_announcement));
    }

    private void loadDirectory(File directory) {
        if (directory == null || !directory.exists()) {
            return;
        }

        currentDirectory = directory;
        if (isAdded()) {
            currentSortMode = SortPreferences.getSortModeForFolder(requireContext(), directory.getAbsolutePath());
        }
        updateHeaderUI();
        updateSaveHereUI();
        updateStorageInfoBar();
        updatePasteButtonsVisibility();

        progressBar.setVisibility(View.VISIBLE);
        tvEmptyFolder.setVisibility(View.GONE);

        executorService.execute(() -> {
            File[] files = directory.listFiles();
            List<FileItem> fileItems = new ArrayList<>();

            if (files != null) {
                for (File file : files) {
                    if (!file.getName().startsWith(".")) {
                        fileItems.add(new FileItem(file));
                    }
                }
            }

            mainHandler.post(() -> {
                if (!isAdded()) return;

                currentLoadedItems.clear();
                currentLoadedItems.addAll(fileItems);

                progressBar.setVisibility(View.GONE);

                if (isSearchActive && !TextUtils.isEmpty(currentSearchQuery)) {
                    filterFilesRealTime(currentSearchQuery);
                } else {
                    applySortingAndDisplay();

                    if (fileItems.isEmpty()) {
                        tvEmptyFolder.setVisibility(View.VISIBLE);
                        tvEmptyFolder.setText(R.string.folder_empty);
                        tvEmptyFolder.announceForAccessibility(getString(R.string.folder_empty));
                    } else {
                        tvEmptyFolder.setVisibility(View.GONE);
                        String speechAnnouncement;
                        if (activeArchiveRoot != null && directory.getAbsolutePath().equals(activeArchiveRoot.getAbsolutePath())) {
                            speechAnnouncement = getString(R.string.zip_opened_announcement, activeArchiveName != null ? activeArchiveName : "Archive", fileItems.size());
                        } else {
                            speechAnnouncement = getString(
                                    R.string.folder_loaded_announcement,
                                    getDisplayDirectoryName(directory),
                                    fileItems.size()
                            );
                        }
                        tvCurrentPath.announceForAccessibility(speechAnnouncement);
                    }
                }
            });
        });
    }

    private void applySortingAndDisplay() {
        Collections.sort(currentLoadedItems, currentSortMode.getComparator());
        fileAdapter.updateList(currentLoadedItems);

        if (currentLoadedItems.isEmpty()) {
            tvEmptyFolder.setVisibility(View.VISIBLE);
            tvEmptyFolder.setText(R.string.folder_empty);
        } else {
            tvEmptyFolder.setVisibility(View.GONE);
        }
    }

    private void showSortDialog() {
        if (currentDirectory == null || !isAdded()) return;
        boolean hasOverride = SortPreferences.hasFolderSortOverride(requireContext(), currentDirectory.getAbsolutePath());

        Dialog dialog = SortDialogHelper.createSortDialog(
                requireContext(),
                currentSortMode,
                hasOverride,
                getString(R.string.sort_apply_this_folder_only),
                getString(R.string.sort_apply_this_folder_only_hint),
                (selectedMode, isCurrentOnly) -> {
                    currentSortMode = selectedMode;
                    String sortName = getString(selectedMode.getTitleResId());
                    String announcement;

                    if (isCurrentOnly) {
                        SortPreferences.setSortModeForFolder(requireContext(), currentDirectory.getAbsolutePath(), selectedMode);
                        announcement = getString(R.string.sorted_folder_announcement, sortName);
                    } else {
                        SortPreferences.setGlobalSortMode(requireContext(), selectedMode);
                        SortPreferences.clearFolderSortMode(requireContext(), currentDirectory.getAbsolutePath());
                        announcement = getString(R.string.sorted_globally_announcement, sortName);
                    }

                    if (isSearchActive && !TextUtils.isEmpty(currentSearchQuery)) {
                        filterFilesRealTime(currentSearchQuery);
                    } else {
                        applySortingAndDisplay();
                    }

                    recyclerView.announceForAccessibility(announcement);
                    Toast.makeText(requireContext(), announcement, Toast.LENGTH_SHORT).show();
                }
        );
        setActiveDialog(dialog);
    }

    // --- Screen Reader Custom Actions (Swipe up/down) & Long Click ---

    @Override
    public void onActionSelect(FileItem item, int position) {
        enterSelectionMode(position);
    }

    @Override
    public void onActionFavorite(FileItem item) {
        boolean isFav = FavoriteManager.getInstance(requireContext()).toggleFavorite(item.getPath());
        String msg = isFav ?
                getString(R.string.toast_added_favorite, item.getName()) :
                getString(R.string.toast_removed_favorite, item.getName());
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
        recyclerView.announceForAccessibility(msg);
        fileAdapter.notifyDataSetChanged();
    }

    @Override
    public void onActionCopy(FileItem item) {
        setClipboardFiles(Collections.singletonList(item.getFile()), false);

        String message = getString(R.string.toast_copied, item.getName());
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        recyclerView.announceForAccessibility(message);
    }

    @Override
    public void onActionCut(FileItem item) {
        setClipboardFiles(Collections.singletonList(item.getFile()), true);

        String message = getString(R.string.toast_cut, item.getName());
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        recyclerView.announceForAccessibility(message);
    }

    @Override
    public void onActionRename(FileItem item) {
        showRenameDialog(item);
    }

    @Override
    public void onActionShare(FileItem item) {
        handleShare(item);
    }

    @Override
    public void onActionDelete(FileItem item) {
        showDeleteConfirmationDialog(item);
    }

    @Override
    public void onActionOpenWith(FileItem item) {
        if (item.isDirectory()) return;
        FileOpener.openFileWith(requireContext(), item.getFile());
    }

    @Override
    public void onActionProperties(FileItem item) {
        FilePropertiesDialog.show(requireContext(), item, this::setActiveDialog);
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
                loadDirectory(currentDirectory);
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
                loadDirectory(currentDirectory);
            }
        });
    }

    @Override
    public void onItemClick(FileItem item, int position) {
        if (item.isDirectory()) {
            if (isSearchActive) {
                closeSearch();
            }
            loadDirectory(item.getFile());
        } else if (ArchiveViewerHelper.isArchive(item.getName())) {
            if (isSearchActive) {
                closeSearch();
            }
            openArchiveFile(item.getFile());
        } else {
            openFile(item.getFile());
        }
    }

    @Override
    public void onItemLongClick(FileItem item, int position) {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(requireContext());
        View sheetView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_bottom_sheet_actions, null);
        bottomSheetDialog.setContentView(sheetView);

        TextView tvTitle = sheetView.findViewById(R.id.tv_sheet_title);
        View actionSelect = sheetView.findViewById(R.id.action_select);
        View actionFavorite = sheetView.findViewById(R.id.action_favorite);
        TextView tvFav = sheetView.findViewById(R.id.tv_action_favorite);

        View actionOpenWith = sheetView.findViewById(R.id.action_open_with);
        View actionCopy = sheetView.findViewById(R.id.action_copy);
        View actionCut = sheetView.findViewById(R.id.action_cut);
        View actionRename = sheetView.findViewById(R.id.action_rename);
        View actionShare = sheetView.findViewById(R.id.action_share);
        View actionEdit = sheetView.findViewById(R.id.action_edit);
        TextView tvEdit = sheetView.findViewById(R.id.tv_action_edit);
        if (tvEdit != null) {
            tvEdit.setText(item.isVideo() ? R.string.action_convert_to_audio : R.string.action_edit);
        }
        actionEdit.setContentDescription(getString(item.isVideo() ? R.string.action_convert_to_audio : R.string.action_edit));
        View actionDelete = sheetView.findViewById(R.id.action_delete);
        View actionProperties = sheetView.findViewById(R.id.action_properties);
        View actionBatchConvert = sheetView.findViewById(R.id.action_batch_convert);

        String itemTitle = getString(R.string.action_menu_title, item.getName());
        tvTitle.setText(itemTitle);
        tvTitle.setContentDescription(itemTitle);

        boolean isFav = FavoriteManager.getInstance(requireContext()).isFavorite(item.getPath());
        tvFav.setText(isFav ? R.string.action_remove_favorite : R.string.action_add_favorite);

        actionSelect.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            enterSelectionMode(position);
        });

        actionFavorite.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            onActionFavorite(item);
        });

        actionOpenWith.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            onActionOpenWith(item);
        });

        actionCopy.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            onActionCopy(item);
        });

        actionCut.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            onActionCut(item);
        });

        actionRename.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            onActionRename(item);
        });

        actionShare.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            onActionShare(item);
        });

        actionEdit.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            onActionEdit(item);
        });

        actionDelete.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            onActionDelete(item);
        });

        actionProperties.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            onActionProperties(item);
        });

        if (actionBatchConvert != null) {
            actionBatchConvert.setOnClickListener(v -> {
                bottomSheetDialog.dismiss();
                onActionBatchConvert(item);
            });
        }

        // Dynamically reorder and filter action views based on ActionMenuManager preferences
        LinearLayout sheetLayout = (LinearLayout) sheetView;
        sheetLayout.removeViews(2, sheetLayout.getChildCount() - 2);

        List<ActionConfig> configs = ActionMenuManager.getInstance(requireContext()).getActionConfigs();
        for (ActionConfig config : configs) {
            if (!config.isEnabled()) continue;
            switch (config.getId()) {
                case ActionConfig.ACTION_SELECT:
                    sheetLayout.addView(actionSelect);
                    break;
                case ActionConfig.ACTION_FAVORITE:
                    sheetLayout.addView(actionFavorite);
                    break;
                case ActionConfig.ACTION_OPEN_WITH:
                    if (!item.isDirectory()) {
                        sheetLayout.addView(actionOpenWith);
                    }
                    break;
                case ActionConfig.ACTION_COPY:
                    sheetLayout.addView(actionCopy);
                    break;
                case ActionConfig.ACTION_CUT:
                    sheetLayout.addView(actionCut);
                    break;
                case ActionConfig.ACTION_RENAME:
                    sheetLayout.addView(actionRename);
                    break;
                case ActionConfig.ACTION_SHARE:
                    sheetLayout.addView(actionShare);
                    break;
                case ActionConfig.ACTION_EDIT:
                    if (!item.isDirectory() && (item.isAudio() || item.isVideo())) {
                        sheetLayout.addView(actionEdit);
                    }
                    break;
                case ActionConfig.ACTION_BATCH_CONVERT:
                    if (item.isDirectory() && actionBatchConvert != null) {
                        sheetLayout.addView(actionBatchConvert);
                    }
                    break;
                case ActionConfig.ACTION_DELETE:
                    sheetLayout.addView(actionDelete);
                    break;
                case ActionConfig.ACTION_PROPERTIES:
                    sheetLayout.addView(actionProperties);
                    break;
            }
        }

        setActiveDialog(bottomSheetDialog);
    }

    // --- Paste Implementation (Bottom Right Corner FAB) ---

    private void showPasteButtons(String itemName) {
        fabPaste.setVisibility(View.VISIBLE);
        fabCancelPaste.setVisibility(View.VISIBLE);
        String desc = getString(R.string.action_paste_desc, itemName);
        fabPaste.setContentDescription(desc);
    }

    private void hidePasteButtons() {
        fabPaste.setVisibility(View.GONE);
        fabCancelPaste.setVisibility(View.GONE);
    }

    public void updatePasteButtonsVisibility() {
        if (!clipboardFiles.isEmpty()) {
            if (clipboardFiles.size() == 1) {
                showPasteButtons(clipboardFiles.get(0).getName());
            } else {
                showPasteButtons(getString(R.string.selection_mode_title, clipboardFiles.size()));
            }
        } else if (AppClipboard.getInstance().hasNetworkItems()) {
            showPasteButtons(AppClipboard.getInstance().getSummary());
        } else if (AppClipboard.getInstance().hasLocalFiles()) {
            showPasteButtons(AppClipboard.getInstance().getSummary());
        } else {
            hidePasteButtons();
        }
    }

    private void cancelClipboard() {
        clipboardFiles.clear();
        isCutOperation = false;
        AppClipboard.getInstance().clear();
        hidePasteButtons();
        String msg = getString(R.string.clipboard_cancelled);
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
        recyclerView.announceForAccessibility(msg);
    }

    private void handlePaste() {
        if (AppClipboard.getInstance().hasNetworkItems()) {
            pasteNetworkItemsToCurrentFolder();
            return;
        }

        if (clipboardFiles.isEmpty()) {
            cancelClipboard();
            return;
        }

        for (File file : clipboardFiles) {
            if (file.isDirectory() && currentDirectory.getAbsolutePath().startsWith(file.getAbsolutePath())) {
                Toast.makeText(requireContext(), R.string.cannot_paste_inside_itself, Toast.LENGTH_LONG).show();
                recyclerView.announceForAccessibility(getString(R.string.cannot_paste_inside_itself));
                return;
            }
        }

        List<File> sourcesToPaste = new ArrayList<>(clipboardFiles);
        boolean cut = isCutOperation;
        boolean fromArchive = isSourceFromArchive(sourcesToPaste);

        progressBar.setVisibility(View.VISIBLE);

        executorService.execute(() -> {
            int successCount = 0;
            for (File source : sourcesToPaste) {
                if (!source.exists()) continue;
                File destination = getUniqueDestination(currentDirectory, source.getName());
                boolean success;
                if (cut) {
                    success = moveFileOrDirectory(source, destination);
                } else {
                    success = copyFileOrDirectory(source, destination);
                }
                if (success) {
                    successCount++;
                }
            }

            final int finalSuccessCount = successCount;
            mainHandler.post(() -> {
                if (!isAdded()) return;
                progressBar.setVisibility(View.GONE);

                if (finalSuccessCount > 0) {
                    String msg = (sourcesToPaste.size() == 1) ?
                            getString(R.string.toast_pasted, sourcesToPaste.get(0).getName()) :
                            getString(R.string.toast_pasted_multiple, finalSuccessCount);
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                    recyclerView.announceForAccessibility(msg);

                    if (cut || fromArchive) {
                        clipboardFiles.clear();
                        hidePasteButtons();
                    }
                    loadDirectory(currentDirectory);
                } else {
                    Toast.makeText(requireContext(), R.string.toast_paste_failed, Toast.LENGTH_SHORT).show();
                    recyclerView.announceForAccessibility(getString(R.string.toast_paste_failed));
                }
            });
        });
    }

    private void pasteNetworkItemsToCurrentFolder() {
        List<NetworkItem> networkItems = AppClipboard.getInstance().getNetworkItems();
        if (networkItems.isEmpty() || currentDirectory == null) return;

        progressBar.setVisibility(View.VISIBLE);
        Toast.makeText(requireContext(), "Downloading " + networkItems.size() + " items from remote storage...", Toast.LENGTH_SHORT).show();

        executorService.execute(() -> {
            int successCount = 0;
            CloudStorageManager csm = CloudStorageManager.getInstance(requireContext());
            for (NetworkItem item : networkItems) {
                try {
                    File dest = getUniqueDestination(currentDirectory, item.getName());
                    boolean isGoogleDrive = (item.getServerInfo() != null && NetworkServerInfo.TYPE_GOOGLE_DRIVE.equalsIgnoreCase(item.getServerInfo().getType()))
                            || "Google Drive".equalsIgnoreCase(item.getShareName())
                            || (item.getPath() != null && item.getPath().startsWith("content://"));
                    if (isGoogleDrive) {
                        if (item.getPath() != null && item.getPath().startsWith("content://")) {
                            DocumentFileTreeHelper.downloadFile(requireContext(), Uri.parse(item.getPath()), dest, null);
                        } else {
                            GoogleDriveService.getInstance(requireContext())
                                    .downloadFile(item.getPath(), dest, csm.getGoogleDriveToken());
                        }
                    } else if (item.getServerInfo() != null && NetworkServerInfo.TYPE_SMB.equalsIgnoreCase(item.getServerInfo().getType())) {
                        SmbClientHelper.downloadFile(item.getServerInfo(), item, dest, null);
                    } else if (item.getServerInfo() != null) {
                        FtpClientHelper.downloadFile(item.getServerInfo(), item, dest, null);
                    }

                    if (dest.exists() && dest.length() > 0) {
                        successCount++;
                        if (AppClipboard.getInstance().isCut()) {
                            try {
                                if (isGoogleDrive) {
                                    if (item.getPath() != null && item.getPath().startsWith("content://")) {
                                        DocumentFileTreeHelper.deleteItem(requireContext(), Uri.parse(item.getPath()));
                                    } else {
                                        GoogleDriveService.getInstance(requireContext()).deleteItem(item.getPath(), csm.getGoogleDriveToken());
                                    }
                                } else if (item.getServerInfo() != null && NetworkServerInfo.TYPE_SMB.equalsIgnoreCase(item.getServerInfo().getType())) {
                                    SmbClientHelper.deleteItem(item.getServerInfo(), item);
                                } else if (item.getServerInfo() != null) {
                                    FtpClientHelper.deleteItem(item.getServerInfo(), item);
                                }
                            } catch (Exception ignored) {}
                        }
                        try {
                            android.media.MediaScannerConnection.scanFile(requireContext(), new String[]{dest.getAbsolutePath()}, null, null);
                        } catch (Exception ignored) {}
                    }
                } catch (Exception ignored) {}
            }

            final int finalSuccess = successCount;
            mainHandler.post(() -> {
                if (!isAdded()) return;
                progressBar.setVisibility(View.GONE);
                if (finalSuccess > 0) {
                    String msg = getString(R.string.toast_pasted_multiple, finalSuccess);
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                    recyclerView.announceForAccessibility(msg);
                    AppClipboard.getInstance().clear();
                    clipboardFiles.clear();
                    hidePasteButtons();
                    loadDirectory(currentDirectory);
                } else {
                    Toast.makeText(requireContext(), R.string.toast_paste_failed, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private boolean isSourceFromArchive(List<File> files) {
        if (files == null || files.isEmpty()) return false;
        try {
            File cacheDir = requireContext().getCacheDir();
            String cachePath = cacheDir.getCanonicalPath();
            for (File f : files) {
                if (f != null && f.getCanonicalPath().startsWith(cachePath)) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private File getUniqueDestination(File folder, String name) {
        File file = new File(folder, name);
        if (!file.exists()) {
            return file;
        }

        String baseName = name;
        String extension = "";
        int dotIndex = name.lastIndexOf('.');
        if (dotIndex > 0 && !file.isDirectory()) {
            baseName = name.substring(0, dotIndex);
            extension = name.substring(dotIndex);
        }

        int count = 1;
        while (file.exists()) {
            file = new File(folder, baseName + " (" + count + ")" + extension);
            count++;
        }
        return file;
    }

    private boolean copyFileOrDirectory(File src, File dest) {
        if (src.isDirectory()) {
            if (!dest.exists() && !dest.mkdirs()) {
                return false;
            }
            File[] files = src.listFiles();
            if (files != null) {
                for (File child : files) {
                    if (!copyFileOrDirectory(child, new File(dest, child.getName()))) {
                        return false;
                    }
                }
            }
            return true;
        } else {
            try (InputStream in = new FileInputStream(src);
                 OutputStream out = new FileOutputStream(dest)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) > 0) {
                    out.write(buffer, 0, bytesRead);
                }
                return true;
            } catch (IOException e) {
                return false;
            }
        }
    }

    private boolean moveFileOrDirectory(File src, File dest) {
        if (src.renameTo(dest)) {
            return true;
        }
        if (copyFileOrDirectory(src, dest)) {
            return deleteRecursively(src);
        }
        return false;
    }

    // --- Save Shared Files Implementation (WhatsApp, Telegram, etc.) ---

    public void startSaveSharedFilesMode(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) return;
        if (isSearchActive) closeSearch();
        if (fileAdapter != null && fileAdapter.isSelectionMode()) exitSelectionMode();

        pendingSharedUris.clear();
        pendingSharedUris.addAll(uris);
        isSaveSharedMode = true;

        if (layoutSaveHere != null) {
            layoutSaveHere.setVisibility(View.VISIBLE);
            updateSaveHereUI();
            String infoText = tvSaveHereTitle.getText().toString();
            String announcement = getString(R.string.save_shared_mode_announcement);
            layoutSaveHere.announceForAccessibility(infoText + ". " + announcement);
            Toast.makeText(requireContext(), announcement, Toast.LENGTH_SHORT).show();
        }
        updateBackCallbackState();
    }

    private void updateSaveHereUI() {
        if (!isSaveSharedMode || layoutSaveHere == null || !isAdded()) return;

        if (pendingSharedUris.size() == 1) {
            String fileName = SharedFileSaver.getFileNameFromUri(requireContext(), pendingSharedUris.get(0));
            tvSaveHereTitle.setText(getString(R.string.save_shared_file_title, fileName));
        } else {
            tvSaveHereTitle.setText(getString(R.string.save_shared_files_title, pendingSharedUris.size()));
        }

        String destName = (currentDirectory != null) ? currentDirectory.getName() : "Internal Storage";
        tvSaveHereFolder.setText(getString(R.string.save_shared_destination, destName));
    }

    public void cancelSaveSharedFilesMode() {
        isSaveSharedMode = false;
        pendingSharedUris.clear();
        if (layoutSaveHere != null) {
            layoutSaveHere.setVisibility(View.GONE);
        }
        updateBackCallbackState();
    }

    private void confirmSaveSharedFiles() {
        if (pendingSharedUris.isEmpty() || currentDirectory == null) {
            cancelSaveSharedFilesMode();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        String progressMsg = getString(R.string.saving_files_progress);
        Toast.makeText(requireContext(), progressMsg, Toast.LENGTH_SHORT).show();
        layoutSaveHere.announceForAccessibility(progressMsg);

        List<Uri> urisToSave = new ArrayList<>(pendingSharedUris);
        File targetDir = currentDirectory;
        Context context = requireContext().getApplicationContext();

        executorService.execute(() -> {
            int savedCount = 0;
            String lastSavedName = "";

            for (Uri uri : urisToSave) {
                String origName = SharedFileSaver.getFileNameFromUri(context, uri);
                File destFile = SharedFileSaver.getUniqueDestinationFile(targetDir, origName);
                boolean ok = SharedFileSaver.copyUriToFile(context, uri, destFile);
                if (ok) {
                    savedCount++;
                    lastSavedName = destFile.getName();
                }
            }

            final int finalSavedCount = savedCount;
            final String finalSavedName = lastSavedName;

            mainHandler.post(() -> {
                if (!isAdded()) return;
                progressBar.setVisibility(View.GONE);
                cancelSaveSharedFilesMode();
                loadDirectory(targetDir);

                if (finalSavedCount == 1) {
                    String msg = getString(R.string.toast_save_shared_success, finalSavedName, targetDir.getName());
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                    recyclerView.announceForAccessibility(msg);
                } else if (finalSavedCount > 1) {
                    String msg = getString(R.string.toast_save_shared_multiple_success, finalSavedCount, targetDir.getName());
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                    recyclerView.announceForAccessibility(msg);
                } else {
                    String failMsg = getString(R.string.toast_save_shared_failed, finalSavedName);
                    Toast.makeText(requireContext(), failMsg, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    // --- Share Implementation (Folders zipped into .zip archive) ---

    private void handleShare(FileItem item) {
        if (item.isDirectory()) {
            showFolderCompressAndShareDialog(item);
        } else {
            shareSingleFile(item.getFile(), item.getExtension(), item.getName());
        }
    }

    private void shareSingleFile(File file, String extension, String displayName) {
        try {
            Uri fileUri = FileProvider.getUriForFile(
                    requireContext(),
                    requireContext().getPackageName() + ".provider",
                    file
            );

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase(Locale.ENGLISH));
            if (mimeType == null) {
                mimeType = "*/*";
            }

            shareIntent.setType(mimeType);
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share) + " " + displayName));
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Share failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showFolderCompressAndShareDialog(FileItem folderItem) {
        ArchiveViewerHelper.ArchiveFormat[] formats = ArchiveViewerHelper.ArchiveFormat.values();
        String[] options = new String[formats.length];
        options[0] = getString(R.string.format_archive_zip);
        options[1] = getString(R.string.format_archive_7z);
        options[2] = getString(R.string.format_archive_targz);
        options[3] = getString(R.string.format_archive_tar);

        Dialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_select_archive_format)
                .setItems(options, (d, which) -> {
                    compressAndShareFolder(folderItem, formats[which]);
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .create();
        setActiveDialog(dialog);
    }

    private void compressAndShareFolder(FileItem folderItem, ArchiveViewerHelper.ArchiveFormat format) {
        String progressMsg = getString(R.string.compressing_folder_progress, folderItem.getName(), format.getDisplayName());
        Toast.makeText(requireContext(), progressMsg, Toast.LENGTH_SHORT).show();
        recyclerView.announceForAccessibility(progressMsg);
        progressBar.setVisibility(View.VISIBLE);

        executorService.execute(() -> {
            try {
                File archiveFile = new File(requireContext().getCacheDir(), folderItem.getName() + format.getExtension());
                ArchiveViewerHelper.createArchive(folderItem.getFile(), archiveFile, format);

                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    progressBar.setVisibility(View.GONE);
                    String successMsg = getString(R.string.folder_compressed_success, folderItem.getName(), format.getExtension());
                    recyclerView.announceForAccessibility(successMsg);
                    String extClean = format.getExtension().replace(".", "");
                    shareSingleFile(archiveFile, extClean, archiveFile.getName());
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    progressBar.setVisibility(View.GONE);
                    String failMsg = getString(R.string.folder_compress_failed, folderItem.getName());
                    Toast.makeText(requireContext(), failMsg, Toast.LENGTH_SHORT).show();
                    recyclerView.announceForAccessibility(failMsg);
                });
            }
        });
    }

    // --- Rename & Delete Dialogs ---

    private void showRenameDialog(FileItem item) {
        EditText input = new EditText(requireContext());
        input.setSingleLine(true);
        input.setText(item.getName());
        input.setHint(R.string.dialog_rename_hint);
        int dot = item.getName().lastIndexOf('.');
        input.setSelection(0, dot > 0 ? dot : item.getName().length());

        FrameLayout container = new FrameLayout(requireContext());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.leftMargin = 60;
        params.rightMargin = 60;
        input.setLayoutParams(params);
        container.addView(input);

        Dialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.dialog_rename_title) + " " + item.getName())
                .setView(container)
                .setPositiveButton(R.string.btn_rename, (d, which) -> {
                    String newName = input.getText().toString().trim();
                    if (TextUtils.isEmpty(newName)) {
                        Toast.makeText(requireContext(), R.string.toast_name_empty, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (newName.equals(item.getName())) {
                        return;
                    }
                    File originalFile = item.getFile();
                    File destinationFile = new File(originalFile.getParentFile(), newName);
                    if (originalFile.renameTo(destinationFile)) {
                        String successMsg = getString(R.string.toast_rename_success, newName);
                        Toast.makeText(requireContext(), successMsg, Toast.LENGTH_SHORT).show();
                        recyclerView.announceForAccessibility(successMsg);
                        loadDirectory(currentDirectory);
                    } else {
                        String failMsg = getString(R.string.toast_rename_failed, item.getName());
                        Toast.makeText(requireContext(), failMsg, Toast.LENGTH_SHORT).show();
                        recyclerView.announceForAccessibility(failMsg);
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .create();
        setActiveDialog(dialog);
    }

    private void showDeleteConfirmationDialog(FileItem item) {
        Dialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_delete_title)
                .setMessage(getString(R.string.dialog_delete_message, item.getName()))
                .setPositiveButton(R.string.btn_delete, (d, which) -> {
                    executorService.execute(() -> {
                        boolean deleted = deleteRecursively(item.getFile());
                        mainHandler.post(() -> {
                            if (!isAdded()) return;
                            if (deleted) {
                                String successMsg = getString(R.string.toast_delete_success, item.getName());
                                Toast.makeText(requireContext(), successMsg, Toast.LENGTH_SHORT).show();
                                recyclerView.announceForAccessibility(successMsg);
                                loadDirectory(currentDirectory);
                            } else {
                                String failMsg = getString(R.string.toast_delete_failed, item.getName());
                                Toast.makeText(requireContext(), failMsg, Toast.LENGTH_SHORT).show();
                                recyclerView.announceForAccessibility(failMsg);
                            }
                        });
                    });
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .create();
        setActiveDialog(dialog);
    }

    private boolean deleteRecursively(File fileOrDirectory) {
        if (fileOrDirectory == null || !fileOrDirectory.exists()) {
            return false;
        }
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        return fileOrDirectory.delete();
    }

    // --- Navigation and Display Helpers ---

    /**
     * Shows dialog offering "New Folder" and "New File" options.
     */
    public void showNewItemDialog() {
        List<String> options = new ArrayList<>();
        options.add(getString(R.string.new_folder));
        options.add(getString(R.string.new_file));
        boolean hasItems = !clipboardFiles.isEmpty() || AppClipboard.getInstance().hasNetworkItems() || AppClipboard.getInstance().hasLocalFiles();
        if (hasItems) {
            int count = !clipboardFiles.isEmpty() ? clipboardFiles.size() :
                    (AppClipboard.getInstance().hasNetworkItems() ? AppClipboard.getInstance().getNetworkItems().size() : AppClipboard.getInstance().getLocalFiles().size());
            options.add(getString(R.string.action_paste) + " (" + count + ")");
        }

        Dialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_new_title)
                .setItems(options.toArray(new String[0]), (d, which) -> {
                    if (which == 0) {
                        showCreateFolderDialog();
                    } else if (which == 1) {
                        showCreateFileDialog();
                    } else if (which == 2) {
                        handlePaste();
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .create();
        setActiveDialog(dialog);
    }

    /**
     * Dialog to create a new directory inside currentDirectory.
     */
    private void showCreateFolderDialog() {
        EditText input = new EditText(requireContext());
        input.setSingleLine(true);
        input.setHint(R.string.dialog_new_folder_hint);
        input.setContentDescription(getString(R.string.dialog_new_folder_hint));

        FrameLayout container = new FrameLayout(requireContext());
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.leftMargin = 60;
        params.rightMargin = 60;
        input.setLayoutParams(params);
        container.addView(input);

        Dialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_new_folder_title)
                .setView(container)
                .setPositiveButton(R.string.btn_create, (d, which) -> {
                    String folderName = input.getText().toString().trim();
                    if (TextUtils.isEmpty(folderName)) {
                        Toast.makeText(requireContext(), R.string.toast_name_empty, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    File newDir = new File(currentDirectory, folderName);
                    if (newDir.exists()) {
                        Toast.makeText(requireContext(), "Folder already exists", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (newDir.mkdir() || newDir.mkdirs()) {
                        String msg = getString(R.string.toast_folder_created, folderName);
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                        recyclerView.announceForAccessibility(msg);
                        loadDirectory(currentDirectory);
                    } else {
                        String failMsg = getString(R.string.toast_folder_create_failed, folderName);
                        Toast.makeText(requireContext(), failMsg, Toast.LENGTH_SHORT).show();
                        recyclerView.announceForAccessibility(failMsg);
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .create();
        setActiveDialog(dialog);
    }

    /**
     * Accessible dialog to create a new file with Title, Body Text, and Category format (TXT, DOC, PDF, HTML).
     */
    private void showCreateFileDialog() {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_new_file, null);
        TextInputEditText etTitle = dialogView.findViewById(R.id.et_new_file_title);
        TextInputEditText etBody = dialogView.findViewById(R.id.et_new_file_body);
        RadioGroup rgFormat = dialogView.findViewById(R.id.rg_file_format);

        Dialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_new_file_title)
                .setView(dialogView)
                .setPositiveButton(R.string.btn_save, (d, which) -> {
                    String title = (etTitle.getText() != null) ? etTitle.getText().toString().trim() : "";
                    String body = (etBody.getText() != null) ? etBody.getText().toString() : "";

                    if (TextUtils.isEmpty(title)) {
                        Toast.makeText(requireContext(), R.string.toast_title_empty, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    int selectedId = rgFormat.getCheckedRadioButtonId();
                    String extension;
                    if (selectedId == R.id.rb_format_doc) {
                        extension = ".doc";
                    } else if (selectedId == R.id.rb_format_pdf) {
                        extension = ".pdf";
                    } else if (selectedId == R.id.rb_format_html) {
                        extension = ".html";
                    } else {
                        extension = ".txt";
                    }

                    String fileName = title.toLowerCase(Locale.ENGLISH).endsWith(extension) ? title : (title + extension);
                    File targetFile = getUniqueDestination(currentDirectory, fileName);

                    progressBar.setVisibility(View.VISIBLE);
                    executorService.execute(() -> {
                        boolean created = writeNewFileContent(targetFile, title, body, extension);
                        mainHandler.post(() -> {
                            if (!isAdded()) return;
                            progressBar.setVisibility(View.GONE);
                            if (created) {
                                String msg = getString(R.string.toast_file_created, targetFile.getName());
                                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                                recyclerView.announceForAccessibility(msg);
                                loadDirectory(currentDirectory);
                            } else {
                                String failMsg = getString(R.string.toast_file_create_failed, targetFile.getName());
                                Toast.makeText(requireContext(), failMsg, Toast.LENGTH_SHORT).show();
                                recyclerView.announceForAccessibility(failMsg);
                            }
                        });
                    });
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .create();
        setActiveDialog(dialog);
    }

    private boolean writeNewFileContent(File file, String title, String body, String extension) {
        try {
            if (extension.equalsIgnoreCase(".pdf")) {
                PdfDocument document = new PdfDocument();
                // Standard A4 size: 595 x 842 points
                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
                PdfDocument.Page page = document.startPage(pageInfo);
                Canvas canvas = page.getCanvas();

                // Draw Title
                Paint titlePaint = new Paint();
                titlePaint.setColor(Color.BLACK);
                titlePaint.setTextSize(20f);
                titlePaint.setFakeBoldText(true);
                canvas.drawText(title, 40f, 60f, titlePaint);

                // Draw Separator Line
                Paint linePaint = new Paint();
                linePaint.setColor(Color.LTGRAY);
                linePaint.setStrokeWidth(1.5f);
                canvas.drawLine(40f, 75f, 555f, 75f, linePaint);

                // Draw Body Text using StaticLayout for multiline wrapping
                TextPaint bodyPaint = new TextPaint();
                bodyPaint.setColor(Color.DKGRAY);
                bodyPaint.setTextSize(13f);

                int printableWidth = 515;
                StaticLayout staticLayout;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    staticLayout = StaticLayout.Builder.obtain(body, 0, body.length(), bodyPaint, printableWidth)
                            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                            .setLineSpacing(0f, 1.2f)
                            .setIncludePad(true)
                            .build();
                } else {
                    staticLayout = new StaticLayout(body, bodyPaint, printableWidth, Layout.Alignment.ALIGN_NORMAL, 1.2f, 0f, true);
                }

                canvas.save();
                canvas.translate(40f, 95f);
                staticLayout.draw(canvas);
                canvas.restore();

                document.finishPage(page);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    document.writeTo(fos);
                }
                document.close();
                return true;
            } else if (extension.equalsIgnoreCase(".html")) {
                String html = "<!DOCTYPE html>\n<html>\n<head>\n<meta charset=\"UTF-8\">\n<title>"
                        + TextUtils.htmlEncode(title) + "</title>\n"
                        + "<style>body{font-family:sans-serif;padding:20px;line-height:1.6;}h1{color:#1976D2;}</style>\n"
                        + "</head>\n<body>\n<h1>"
                        + TextUtils.htmlEncode(title) + "</h1>\n<p>"
                        + TextUtils.htmlEncode(body).replace("\n", "<br/>\n")
                        + "</p>\n</body>\n</html>";
                try (FileOutputStream fos = new FileOutputStream(file);
                     OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                    writer.write(html);
                }
                return true;
            } else if (extension.equalsIgnoreCase(".doc")) {
                // RTF document standard for .doc format
                String rtf = "{\\rtf1\\ansi\\deff0\n"
                        + "{\\fonttbl{\\f0\\fnil\\fcharset0 Arial;}}\n"
                        + "\\viewkind4\\uc1\\pard\\lang1033\\f0\\fs28\\b " + title + "\\b0\\fs20\\par\\par\n"
                        + body.replace("\n", "\\par\n") + "\n}";
                try (FileOutputStream fos = new FileOutputStream(file);
                     OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                    writer.write(rtf);
                }
                return true;
            } else {
                // Plain text .txt
                try (FileOutputStream fos = new FileOutputStream(file);
                     OutputStreamWriter writer = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                    writer.write(body);
                }
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Programmatic navigation to a specific folder (e.g. from Favorites tab).
     */
    public void navigateToFolder(File folder) {
        if (folder != null && folder.exists() && folder.isDirectory()) {
            if (activeArchiveRoot != null && !folder.getAbsolutePath().startsWith(activeArchiveRoot.getAbsolutePath())) {
                activeArchiveRoot = null;
                activeArchiveName = null;
            }
            if (isSearchActive) {
                closeSearch();
            }
            if (fileAdapter != null && fileAdapter.isSelectionMode()) {
                exitSelectionMode();
            }
            loadDirectory(folder);
        }
    }

    public void openArchiveUri(Uri archiveUri) {
        if (archiveUri == null || !isAdded()) return;

        if (isSearchActive) closeSearch();
        if (fileAdapter != null && fileAdapter.isSelectionMode()) exitSelectionMode();

        progressBar.setVisibility(View.VISIBLE);
        String archiveName = SharedFileSaver.getFileNameFromUri(requireContext(), archiveUri);
        if (TextUtils.isEmpty(archiveName) || "unknown_file".equals(archiveName)) {
            archiveName = "archive.zip";
        }
        final String finalArchiveName = archiveName;

        String progressMsg = getString(R.string.zip_opening_progress);
        Toast.makeText(requireContext(), progressMsg, Toast.LENGTH_SHORT).show();
        recyclerView.announceForAccessibility(progressMsg);

        Context appContext = requireContext().getApplicationContext();

        executorService.execute(() -> {
            try (InputStream is = appContext.getContentResolver().openInputStream(archiveUri)) {
                if (is == null) {
                    throw new IOException("Unable to open stream for URI: " + archiveUri);
                }
                File extractedDir = ArchiveViewerHelper.extractArchiveForBrowsing(appContext, is, finalArchiveName);

                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    activeArchiveRoot = extractedDir;
                    activeArchiveName = finalArchiveName;
                    loadDirectory(extractedDir);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    progressBar.setVisibility(View.GONE);
                    String failMsg = getString(R.string.zip_open_failed, finalArchiveName);
                    Toast.makeText(requireContext(), failMsg, Toast.LENGTH_SHORT).show();
                    recyclerView.announceForAccessibility(failMsg);
                });
            }
        });
    }

    public void openZipUri(Uri zipUri) {
        openArchiveUri(zipUri);
    }

    public void openArchiveFile(File archiveFile) {
        if (archiveFile == null || !archiveFile.exists() || !isAdded()) return;

        if (isSearchActive) closeSearch();
        if (fileAdapter != null && fileAdapter.isSelectionMode()) exitSelectionMode();

        progressBar.setVisibility(View.VISIBLE);
        final String archiveName = archiveFile.getName();

        String progressMsg = getString(R.string.zip_opening_progress);
        Toast.makeText(requireContext(), progressMsg, Toast.LENGTH_SHORT).show();
        recyclerView.announceForAccessibility(progressMsg);

        Context appContext = requireContext().getApplicationContext();

        executorService.execute(() -> {
            try {
                File extractedDir = ArchiveViewerHelper.extractArchiveForBrowsing(appContext, archiveFile);

                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    activeArchiveRoot = extractedDir;
                    activeArchiveName = archiveName;
                    loadDirectory(extractedDir);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    progressBar.setVisibility(View.GONE);
                    String failMsg = getString(R.string.zip_open_failed, archiveName);
                    Toast.makeText(requireContext(), failMsg, Toast.LENGTH_SHORT).show();
                    recyclerView.announceForAccessibility(failMsg);
                });
            }
        });
    }

    public void openZipFile(File zipFile) {
        openArchiveFile(zipFile);
    }

    private void exitArchive() {
        activeArchiveRoot = null;
        activeArchiveName = null;
        loadDirectory(rootDirectory);
        String announcement = getString(R.string.archive_exited_announcement, getString(R.string.storage_title));
        tvCurrentPath.announceForAccessibility(announcement);
        Toast.makeText(requireContext(), announcement, Toast.LENGTH_SHORT).show();
    }

    private void updateHeaderUI() {
        if (!isAdded()) return;

        boolean canGoUp = canNavigateUp();
        if (btnDrawer != null) {
            btnDrawer.setVisibility(canGoUp ? View.GONE : View.VISIBLE);
        }
        if (btnUp != null) {
            btnUp.setVisibility(canGoUp ? View.VISIBLE : View.GONE);
            btnUp.setEnabled(canGoUp);
            btnUp.setAlpha(canGoUp ? 1.0f : 0.3f);
        }

        File targetDir = (currentDirectory != null && currentDirectory.exists()) ? currentDirectory : rootDirectory;
        activeVolume = StorageHelper.getVolumeForDirectory(requireContext(), targetDir);

        String storageName = activeVolume != null ? activeVolume.getName() : getString(R.string.storage_title);
        String friendlyPath = formatFriendlyPath(currentDirectory);
        tvCurrentPath.setText(friendlyPath);

        String shortStatus = activeVolume != null ? activeVolume.getShortStatus() : "";
        if (tvStorageStatus != null) {
            if (!shortStatus.isEmpty()) {
                tvStorageStatus.setText(shortStatus);
                tvStorageStatus.setVisibility(View.VISIBLE);
            } else {
                tvStorageStatus.setVisibility(View.GONE);
            }
        }

        if (layoutStorageSelector != null) {
            String usedStr = activeVolume != null ? activeVolume.getFormattedUsed() : "";
            String freeStr = activeVolume != null ? activeVolume.getFormattedFree() : "";
            String a11yDesc;
            if (currentDirectory != null && rootDirectory != null && !currentDirectory.getAbsolutePath().equals(rootDirectory.getAbsolutePath())) {
                a11yDesc = getString(R.string.storage_selector_folder_desc, storageName, currentDirectory.getName(), usedStr, freeStr);
            } else {
                a11yDesc = getString(R.string.storage_selector_root_desc, storageName, usedStr, freeStr);
            }
            layoutStorageSelector.setContentDescription(a11yDesc);
        }

        updateBackCallbackState();
    }

    private void updateStorageInfoBar() {
        // Redundant storage card eliminated; storage status now seamlessly integrated into the top header
        updateHeaderUI();
    }

    public void showStorageSwitcherDialog() {
        if (!isAdded()) return;

        List<StorageHelper.StorageVolumeInfo> volumes = StorageHelper.getStorageVolumes(requireContext());
        if (volumes.isEmpty()) {
            Toast.makeText(requireContext(), R.string.storage_no_external, Toast.LENGTH_SHORT).show();
            return;
        }

        String[] itemTitles = new String[volumes.size()];
        int selectedIndex = 0;

        for (int i = 0; i < volumes.size(); i++) {
            StorageHelper.StorageVolumeInfo vol = volumes.get(i);
            String status = vol.getFullStatus();
            itemTitles[i] = vol.getName() + (status.isEmpty() ? "" : "\n" + status);
            if (rootDirectory != null && vol.getDirectory().getAbsolutePath().equals(rootDirectory.getAbsolutePath())) {
                selectedIndex = i;
            }
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.title_select_storage)
                .setSingleChoiceItems(itemTitles, selectedIndex, (dialog, which) -> {
                    dialog.dismiss();
                    if (which >= 0 && which < volumes.size()) {
                        switchStorageVolume(volumes.get(which));
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null);

        Dialog dialog = builder.create();
        setActiveDialog(dialog);
    }

    private void switchStorageVolume(StorageHelper.StorageVolumeInfo volume) {
        if (volume == null || !volume.getDirectory().exists()) {
            Toast.makeText(requireContext(), R.string.storage_no_external, Toast.LENGTH_SHORT).show();
            return;
        }

        if (activeArchiveRoot != null) {
            activeArchiveRoot = null;
            activeArchiveName = null;
        }

        rootDirectory = volume.getDirectory();
        currentDirectory = rootDirectory;
        activeVolume = volume;

        loadDirectory(currentDirectory);

        String announcement = getString(R.string.storage_switch_success, volume.getName());
        Toast.makeText(requireContext(), announcement, Toast.LENGTH_SHORT).show();
        if (recyclerView != null) {
            recyclerView.announceForAccessibility(announcement);
        }
    }

    private String formatFriendlyPath(File directory) {
        if (directory == null) return "";
        if (activeArchiveRoot != null && directory.getAbsolutePath().startsWith(activeArchiveRoot.getAbsolutePath())) {
            String archiveLabel = getString(R.string.archive_path_prefix, activeArchiveName != null ? activeArchiveName : "Archive");
            if (directory.getAbsolutePath().equals(activeArchiveRoot.getAbsolutePath())) {
                return archiveLabel;
            } else {
                String relative = directory.getAbsolutePath().substring(activeArchiveRoot.getAbsolutePath().length());
                if (relative.startsWith(File.separator)) {
                    relative = relative.substring(1);
                }
                return archiveLabel + " / " + relative.replace(File.separator, " / ");
            }
        }

        String rootPath = rootDirectory != null ? rootDirectory.getAbsolutePath() : "";
        String currentPath = directory.getAbsolutePath();
        String storageName = activeVolume != null ? activeVolume.getName() : getString(R.string.storage_title);

        if (!rootPath.isEmpty() && currentPath.equals(rootPath)) {
            return storageName;
        } else if (!rootPath.isEmpty() && currentPath.startsWith(rootPath)) {
            String relative = currentPath.substring(rootPath.length());
            if (relative.startsWith(File.separator)) {
                relative = relative.substring(1);
            }
            return storageName + " / " + relative.replace(File.separator, " / ");
        }
        return currentPath;
    }

    private String getDisplayDirectoryName(File directory) {
        if (directory == null) return "";
        if (activeArchiveRoot != null && directory.getAbsolutePath().equals(activeArchiveRoot.getAbsolutePath())) {
            return getString(R.string.archive_path_prefix, activeArchiveName != null ? activeArchiveName : "Archive");
        }
        if (rootDirectory != null && directory.getAbsolutePath().equals(rootDirectory.getAbsolutePath())) {
            return activeVolume != null ? activeVolume.getName() : getString(R.string.storage_title);
        }
        return directory.getName();
    }

    private boolean canNavigateUp() {
        if (currentDirectory == null) return false;
        if (activeArchiveRoot != null) {
            return true;
        }
        return !currentDirectory.getAbsolutePath().equals(rootDirectory.getAbsolutePath())
                && currentDirectory.getParentFile() != null;
    }

    private void navigateUp() {
        if (activeArchiveRoot != null && currentDirectory != null && currentDirectory.getAbsolutePath().equals(activeArchiveRoot.getAbsolutePath())) {
            exitArchive();
            return;
        }
        if (canNavigateUp()) {
            File parent = currentDirectory.getParentFile();
            if (parent != null) {
                loadDirectory(parent);
                String announcement = getString(R.string.navigated_up_announcement, getDisplayDirectoryName(parent));
                tvCurrentPath.announceForAccessibility(announcement);
            }
        }
    }

    private void openFile(File file) {
        FileOpener.openFile(requireContext(), file, isSearchActive ? filteredDisplayItems : currentLoadedItems);
    }
}
