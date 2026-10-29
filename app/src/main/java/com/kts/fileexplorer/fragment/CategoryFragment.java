package com.kts.fileexplorer.fragment;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import com.kts.fileexplorer.MainActivity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.kts.fileexplorer.R;
import com.kts.fileexplorer.util.BatchRenameHelper;
import android.widget.FrameLayout;
import com.kts.fileexplorer.adapter.CategoryAdapter;
import com.kts.fileexplorer.adapter.FileAdapter;
import com.kts.fileexplorer.adapter.InstalledAppAdapter;
import com.kts.fileexplorer.model.ActionConfig;
import com.kts.fileexplorer.model.CategoryItem;
import com.kts.fileexplorer.model.FileItem;
import com.kts.fileexplorer.model.InstalledAppItem;
import com.kts.fileexplorer.model.SortMode;
import com.kts.fileexplorer.util.ActionMenuManager;
import com.kts.fileexplorer.util.EditActionHelper;
import com.kts.fileexplorer.util.FavoriteManager;
import com.kts.fileexplorer.util.FileOpener;
import com.kts.fileexplorer.util.FilePropertiesDialog;
import com.kts.fileexplorer.util.SortDialogHelper;
import com.kts.fileexplorer.util.SortPreferences;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.provider.Settings;
import android.content.ContentResolver;
import android.database.Cursor;
import android.provider.MediaStore;
import android.media.MediaScannerConnection;
import com.kts.fileexplorer.util.ArchiveViewerHelper;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * CategoryFragment allows users to browse files and containing folders organized by type:
 * Audio, Images, Videos, Documents, Downloads, and Apps.
 * Features real-time live search, 8 sorting methods, folder navigation, and TalkBack custom actions.
 */
public class CategoryFragment extends Fragment implements CategoryAdapter.OnCategoryClickListener, FileAdapter.OnFileItemActionListener, InstalledAppAdapter.OnInstalledAppActionListener {

    private LinearLayout layoutCategoriesOverview;
    private RecyclerView recyclerCategories;
    private CategoryItem[] categories;
    private CategoryAdapter categoryAdapter;

    private ConstraintLayout layoutCategoryDetail;
    private LinearLayout categoryDetailHeader;
    private ImageButton btnCategoryBack;
    private TextView tvCategoryDetailTitle;
    private ImageButton btnCategorySearch;
    private ImageButton btnCategorySort;
    private ImageButton btnCategoryRefresh;

    // Tabs for Apps Category (0: APK Files, 1: Installed Apps)
    private TabLayout tabLayoutApps;
    private int currentAppsMode = 0;
    private InstalledAppAdapter installedAppAdapter;
    private final List<InstalledAppItem> allInstalledApps = new ArrayList<>();
    private final List<InstalledAppItem> filteredInstalledApps = new ArrayList<>();
    private int installedAppSortMode = 0; // 0: Name A-Z, 1: Name Z-A, 2: Size Largest, 3: Size Smallest, 4: User apps first, 5: System apps first

    // Search bar views
    private LinearLayout categorySearchLayout;
    private EditText etCategorySearch;
    private ImageButton btnCategoryCloseSearch;
    private ImageButton btnCategoryClearSearch;
    private boolean isSearchActive = false;
    private String currentSearchQuery = "";

    // Selection mode views
    private LinearLayout categorySelectionHeaderLayout;
    private View categorySelectionBottomBar;
    private ImageButton btnCategoryCloseSelection;
    private TextView tvCategorySelectionCount;
    private ImageButton btnCategorySelectAll;
    private View btnCategoryShareSelection;
    private View btnCategoryCutSelection;
    private View btnCategoryCopySelection;
    private View btnCategoryRenameSelection;
    private View btnCategoryDeleteSelection;
    private View btnCategoryFavoriteSelection;

    private RecyclerView recyclerCategoryFiles;
    private TextView tvCategoryEmpty;
    private ProgressBar progressCategory;

    private FileAdapter fileAdapter;
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private OnBackPressedCallback backPressedCallback;
    private Dialog currentActiveDialog = null;

    // State for category hierarchy
    private CategoryItem.Type activeCategoryType;
    private String activeCategoryTitle = "";
    private File activeCategoryFolder = null; // null represents the Category Root showing folders

    // Caches discovered category items
    private static final Map<CategoryItem.Type, Map<File, List<FileItem>>> categoryFolderCache = new ConcurrentHashMap<>();
    private static final List<FileItem> cachedNewFiles = new ArrayList<>();
    private final Map<File, List<FileItem>> folderToCategoryFilesMap = new HashMap<>();
    private final List<FileItem> currentVisibleItems = new ArrayList<>();
    private final List<FileItem> filteredItems = new ArrayList<>();

    // Active sorting mode (default: Name A to Z)
    private SortMode currentSortMode = SortMode.NAME_ASC;

    // Extensions per category
    private static final Set<String> AUDIO_EXTENSIONS = new HashSet<>(Arrays.asList(
            "mp3", "wav", "m4a", "aac", "flac", "ogg", "opus", "wma", "mid", "amr"
    ));
    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "svg"
    ));
    private static final Set<String> VIDEO_EXTENSIONS = new HashSet<>(Arrays.asList(
            "mp4", "mkv", "webm", "avi", "3gp", "mov", "flv", "wmv", "ts"
    ));
    private static final Set<String> DOC_EXTENSIONS = new HashSet<>(Arrays.asList(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "csv", "epub"
    ));
    private static final Set<String> APP_EXTENSIONS = new HashSet<>(Arrays.asList(
            "apk", "xapk", "apks"
    ));

    public CategoryFragment() {
        // Required empty public constructor
    }

    public static CategoryFragment newInstance() {
        return new CategoryFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_category, container, false);

        initViews(view);
        setupCategoriesRecycler();
        setupFilesRecycler();
        setupSearchLogic();
        setupBackNavigation();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (layoutCategoriesOverview != null && layoutCategoriesOverview.getVisibility() == View.VISIBLE) {
            loadCategoryStorageSizes();
            if (categoryAdapter != null) {
                categoryAdapter.notifyDataSetChanged();
            }
        }
        if (layoutCategoryDetail != null && layoutCategoryDetail.getVisibility() == View.VISIBLE && !isSearchActive && (fileAdapter == null || !fileAdapter.isSelectionMode())) {
            if (activeCategoryFolder != null) {
                refreshFolderFilesFromDisk(activeCategoryFolder);
            } else if (activeCategoryType == CategoryItem.Type.NEW_FILES) {
                scanNewFilesContent();
            } else if (activeCategoryType != null && activeCategoryType != CategoryItem.Type.APPS) {
                scanCategoryContent(activeCategoryType);
            }
        }
        if (fileAdapter != null) {
            fileAdapter.notifyDataSetChanged();
        }
        if (activeCategoryType == CategoryItem.Type.APPS && currentAppsMode == 1 && !isSearchActive) {
            loadInstalledApplications();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }

    private void initViews(View view) {
        layoutCategoriesOverview = view.findViewById(R.id.layout_categories_overview);
        recyclerCategories = view.findViewById(R.id.recycler_categories);

        layoutCategoryDetail = view.findViewById(R.id.layout_category_detail);
        categoryDetailHeader = view.findViewById(R.id.category_detail_header);
        btnCategoryBack = view.findViewById(R.id.btn_category_back);
        tvCategoryDetailTitle = view.findViewById(R.id.tv_category_detail_title);
        btnCategorySearch = view.findViewById(R.id.btn_category_search);
        btnCategorySort = view.findViewById(R.id.btn_category_sort);
        btnCategoryRefresh = view.findViewById(R.id.btn_category_refresh);

        categorySearchLayout = view.findViewById(R.id.category_search_layout);
        etCategorySearch = view.findViewById(R.id.et_category_search);
        btnCategoryCloseSearch = view.findViewById(R.id.btn_category_close_search);
        btnCategoryClearSearch = view.findViewById(R.id.btn_category_clear_search);

        categorySelectionHeaderLayout = view.findViewById(R.id.category_selection_header_layout);
        categorySelectionBottomBar = view.findViewById(R.id.category_selection_bottom_bar);
        btnCategoryCloseSelection = view.findViewById(R.id.btn_category_close_selection);
        tvCategorySelectionCount = view.findViewById(R.id.tv_category_selection_count);
        btnCategorySelectAll = view.findViewById(R.id.btn_category_select_all);
        btnCategoryShareSelection = view.findViewById(R.id.btn_category_share_selection);
        btnCategoryCutSelection = view.findViewById(R.id.btn_category_cut_selection);
        btnCategoryCopySelection = view.findViewById(R.id.btn_category_copy_selection);
        btnCategoryRenameSelection = view.findViewById(R.id.btn_category_rename_selection);
        btnCategoryDeleteSelection = view.findViewById(R.id.btn_category_delete_selection);
        btnCategoryFavoriteSelection = view.findViewById(R.id.btn_category_favorite_selection);

        recyclerCategoryFiles = view.findViewById(R.id.recycler_category_files);
        tvCategoryEmpty = view.findViewById(R.id.tv_category_empty);
        progressCategory = view.findViewById(R.id.progress_category);
        tabLayoutApps = view.findViewById(R.id.tab_layout_apps);

        ImageButton btnDrawerCategory = view.findViewById(R.id.btn_drawer_category);
        if (btnDrawerCategory != null) {
            btnDrawerCategory.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).openDrawer();
                }
            });
        }

        btnCategoryBack.setOnClickListener(v -> handleCategoryBack());
        btnCategorySort.setOnClickListener(v -> showSortDialog());
        btnCategorySearch.setOnClickListener(v -> openSearch());

        if (btnCategoryRefresh != null) {
            btnCategoryRefresh.setOnClickListener(v -> {
                btnCategoryRefresh.announceForAccessibility(getString(R.string.msg_refreshing));
                if (activeCategoryFolder != null) {
                    refreshFolderFilesFromDisk(activeCategoryFolder);
                } else if (activeCategoryType == CategoryItem.Type.NEW_FILES) {
                    scanNewFilesContent();
                } else if (activeCategoryType != null) {
                    scanCategoryContent(activeCategoryType);
                }
                btnCategoryRefresh.announceForAccessibility(getString(R.string.msg_refreshed));
            });
        }

        if (btnCategoryCloseSelection != null) {
            btnCategoryCloseSelection.setOnClickListener(v -> exitSelectionMode());
        }
        if (btnCategorySelectAll != null) {
            btnCategorySelectAll.setOnClickListener(v -> {
                if (fileAdapter != null) {
                    if (fileAdapter.getSelectedPaths().size() == fileAdapter.getCurrentList().size()) {
                        fileAdapter.deselectAll();
                    } else {
                        fileAdapter.selectAll();
                    }
                }
            });
        }
        if (btnCategoryShareSelection != null) {
            btnCategoryShareSelection.setOnClickListener(v -> shareSelectedItems());
        }
        if (btnCategoryCutSelection != null) {
            btnCategoryCutSelection.setOnClickListener(v -> cutSelectedItems());
        }
        if (btnCategoryCopySelection != null) {
            btnCategoryCopySelection.setOnClickListener(v -> copySelectedItems());
        }
        if (btnCategoryRenameSelection != null) {
            btnCategoryRenameSelection.setOnClickListener(v -> renameSelectedItems());
        }
        if (btnCategoryDeleteSelection != null) {
            btnCategoryDeleteSelection.setOnClickListener(v -> deleteSelectedItems());
        }
        if (btnCategoryFavoriteSelection != null) {
            btnCategoryFavoriteSelection.setOnClickListener(v -> favoriteSelectedItems());
        }
    }

    private void setupCategoriesRecycler() {
        categories = CategoryItem.getDefaultCategories();
        categoryAdapter = new CategoryAdapter(categories, this);
        recyclerCategories.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerCategories.setAdapter(categoryAdapter);
        loadCategoryStorageSizes();
    }

    private void setupFilesRecycler() {
        fileAdapter = new FileAdapter(this);
        installedAppAdapter = new InstalledAppAdapter(this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        recyclerCategoryFiles.setLayoutManager(layoutManager);
        recyclerCategoryFiles.setAdapter(fileAdapter);

        DividerItemDecoration divider = new DividerItemDecoration(requireContext(), layoutManager.getOrientation());
        recyclerCategoryFiles.addItemDecoration(divider);
    }

    private void setupSearchLogic() {
        btnCategoryCloseSearch.setOnClickListener(v -> closeSearch());
        btnCategoryClearSearch.setOnClickListener(v -> etCategorySearch.setText(""));

        etCategorySearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentSearchQuery = (s != null) ? s.toString().trim() : "";
                filterCategoryFilesRealTime(currentSearchQuery);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void openSearch() {
        isSearchActive = true;
        categoryDetailHeader.setVisibility(View.GONE);
        categorySearchLayout.setVisibility(View.VISIBLE);
        etCategorySearch.requestFocus();
        updateBackCallbackState();

        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(etCategorySearch, InputMethodManager.SHOW_IMPLICIT);
        }

        recyclerCategoryFiles.announceForAccessibility(getString(R.string.search_opened_announcement));
    }

    private void closeSearch() {
        isSearchActive = false;
        currentSearchQuery = "";
        etCategorySearch.setText("");
        updateBackCallbackState();

        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etCategorySearch.getWindowToken(), 0);
        }

        categorySearchLayout.setVisibility(View.GONE);
        categoryDetailHeader.setVisibility(View.VISIBLE);

        if (activeCategoryType == CategoryItem.Type.APPS && currentAppsMode == 1) {
            applyInstalledAppSortAndDisplay();
        } else {
            applySortingAndDisplay();
        }
        recyclerCategoryFiles.announceForAccessibility(getString(R.string.search_closed));
    }

    private void filterCategoryFilesRealTime(String query) {
        if (activeCategoryType == CategoryItem.Type.APPS && currentAppsMode == 1) {
            if (TextUtils.isEmpty(query)) {
                applyInstalledAppSortAndDisplay();
                return;
            }
            filteredInstalledApps.clear();
            String lower = query.toLowerCase(Locale.ENGLISH);
            for (InstalledAppItem item : allInstalledApps) {
                if (item.getAppName().toLowerCase(Locale.ENGLISH).contains(lower)
                        || item.getPackageName().toLowerCase(Locale.ENGLISH).contains(lower)) {
                    filteredInstalledApps.add(item);
                }
            }
            installedAppAdapter.updateList(filteredInstalledApps);
            if (filteredInstalledApps.isEmpty()) {
                tvCategoryEmpty.setVisibility(View.VISIBLE);
                String noResultsText = getString(R.string.search_no_results, query);
                tvCategoryEmpty.setText(noResultsText);
                tvCategoryEmpty.announceForAccessibility(noResultsText);
            } else {
                tvCategoryEmpty.setVisibility(View.GONE);
                String countText = getString(R.string.search_results_count, filteredInstalledApps.size(), query);
                tvCategoryDetailTitle.announceForAccessibility(countText);
            }
            return;
        }

        if (TextUtils.isEmpty(query)) {
            applySortingAndDisplay();
            return;
        }

        filteredItems.clear();
        String lower = query.toLowerCase(Locale.ENGLISH);

        for (FileItem item : currentVisibleItems) {
            if (item.getName().toLowerCase(Locale.ENGLISH).contains(lower)) {
                filteredItems.add(item);
            }
        }

        fileAdapter.updateList(filteredItems);

        if (filteredItems.isEmpty()) {
            tvCategoryEmpty.setVisibility(View.VISIBLE);
            String noResultsText = getString(R.string.search_no_results, query);
            tvCategoryEmpty.setText(noResultsText);
            tvCategoryEmpty.announceForAccessibility(noResultsText);
        } else {
            tvCategoryEmpty.setVisibility(View.GONE);
            String countText = getString(R.string.search_results_count, filteredItems.size(), query);
            tvCategoryDetailTitle.announceForAccessibility(countText);
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
                if (isSearchActive) {
                    closeSearch();
                    return;
                }
                if (layoutCategoryDetail != null && layoutCategoryDetail.getVisibility() == View.VISIBLE) {
                    handleCategoryBack();
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
        boolean hasDetail = (layoutCategoryDetail != null && layoutCategoryDetail.getVisibility() == View.VISIBLE);
        boolean hasSearch = isSearchActive;
        boolean hasSelection = (fileAdapter != null && fileAdapter.isSelectionMode());
        if (backPressedCallback != null) {
            backPressedCallback.setEnabled(hasDialog || hasDetail || hasSearch || hasSelection);
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
            if (layoutCategoriesOverview != null && layoutCategoriesOverview.getVisibility() == View.VISIBLE) {
                loadCategoryStorageSizes();
            } else if (layoutCategoryDetail != null && layoutCategoryDetail.getVisibility() == View.VISIBLE && !isSearchActive && (fileAdapter == null || !fileAdapter.isSelectionMode())) {
                if (activeCategoryFolder != null) {
                    refreshFolderFilesFromDisk(activeCategoryFolder);
                } else if (activeCategoryType == CategoryItem.Type.NEW_FILES) {
                    scanNewFilesContent();
                } else if (activeCategoryType != null && activeCategoryType != CategoryItem.Type.APPS) {
                    scanCategoryContent(activeCategoryType);
                }
            }
        }
        updateBackCallbackState();
    }

    private void handleCategoryBack() {
        if (fileAdapter != null && fileAdapter.isSelectionMode()) {
            exitSelectionMode();
            return;
        }

        if (isSearchActive) {
            closeSearch();
            return;
        }

        if (activeCategoryType == CategoryItem.Type.APPS && currentAppsMode == 1) {
            tabLayoutApps.setVisibility(View.GONE);
            currentAppsMode = 0;
            layoutCategoryDetail.setVisibility(View.GONE);
            layoutCategoriesOverview.setVisibility(View.VISIBLE);
            updateBackCallbackState();
            recyclerCategories.announceForAccessibility(getString(R.string.category_list_label));
            return;
        }

        if (activeCategoryFolder != null) {
            // Navigate back to the Category Folders list / New Files list
            activeCategoryFolder = null;
            tvCategoryDetailTitle.setText(activeCategoryTitle);
            if (activeCategoryType == CategoryItem.Type.NEW_FILES) {
                scanNewFilesContent();
            } else {
                displayCategoryFoldersOverview();
            }
        } else {
            // Navigate back to the Categories list
            tabLayoutApps.setVisibility(View.GONE);
            currentAppsMode = 0;
            layoutCategoryDetail.setVisibility(View.GONE);
            layoutCategoriesOverview.setVisibility(View.VISIBLE);
            updateBackCallbackState();
            recyclerCategories.announceForAccessibility(getString(R.string.category_list_label));
        }
    }

    @Override
    public void onCategoryClick(CategoryItem item) {
        if (fileAdapter != null && fileAdapter.isSelectionMode()) {
            exitSelectionMode();
        }
        activeCategoryType = item.getType();
        activeCategoryTitle = getString(item.getTitleResId());
        activeCategoryFolder = null;
        currentAppsMode = 0;

        tvCategoryDetailTitle.setText(activeCategoryTitle);
        layoutCategoriesOverview.setVisibility(View.GONE);
        layoutCategoryDetail.setVisibility(View.VISIBLE);
        updateBackCallbackState();
        backPressedCallback.setEnabled(true);

        if (activeCategoryType == CategoryItem.Type.APPS) {
            tabLayoutApps.setVisibility(View.VISIBLE);
            setupAppsTabs();
            recyclerCategoryFiles.setAdapter(fileAdapter);
        } else {
            tabLayoutApps.setVisibility(View.GONE);
            recyclerCategoryFiles.setAdapter(fileAdapter);
        }

        if (activeCategoryType == CategoryItem.Type.NEW_FILES) {
            scanNewFilesContent();
        } else {
            scanCategoryContent(activeCategoryType);
        }
    }

    /**
     * Discovers all newly added files and folders across device storage.
     */
    private void scanNewFilesContent() {
        if (isAdded()) {
            currentSortMode = SortPreferences.getCategorySortMode(requireContext(), CategoryItem.Type.NEW_FILES.name());
        } else {
            currentSortMode = SortMode.DATE_DESC;
        }

        boolean hasCached = !cachedNewFiles.isEmpty();
        if (hasCached) {
            currentVisibleItems.clear();
            currentVisibleItems.addAll(cachedNewFiles);
            progressCategory.setVisibility(View.GONE);
            applySortingAndDisplay();
        } else {
            progressCategory.setVisibility(View.VISIBLE);
            tvCategoryEmpty.setVisibility(View.GONE);
            fileAdapter.updateList(new ArrayList<>());
            currentVisibleItems.clear();
        }

        executorService.execute(() -> {
            List<FileItem> newItems = queryNewFiles();

            mainHandler.post(() -> {
                if (!isAdded() || activeCategoryType != CategoryItem.Type.NEW_FILES) return;
                cachedNewFiles.clear();
                cachedNewFiles.addAll(newItems);
                progressCategory.setVisibility(View.GONE);
                currentVisibleItems.clear();
                currentVisibleItems.addAll(newItems);
                applySortingAndDisplay();

                if (currentVisibleItems.isEmpty()) {
                    tvCategoryEmpty.setVisibility(View.VISIBLE);
                    tvCategoryEmpty.setText(R.string.category_no_files);
                    tvCategoryEmpty.announceForAccessibility(getString(R.string.category_no_files));
                } else {
                    tvCategoryEmpty.setVisibility(View.GONE);
                    String announcement = getString(R.string.category_opened_announcement, activeCategoryTitle, currentVisibleItems.size());
                    tvCategoryDetailTitle.announceForAccessibility(announcement);
                }
            });
        });
    }

    private List<FileItem> queryNewFiles() {
        List<FileItem> newItems = new ArrayList<>();
        Context context = getContext();
        if (context == null) return newItems;

        ContentResolver cr = context.getContentResolver();
        Uri uri = MediaStore.Files.getContentUri("external");
        long thirtyDaysAgoSec = (System.currentTimeMillis() - (30L * 86400000L)) / 1000L;
        String selection = MediaStore.MediaColumns.DATE_MODIFIED + " >= " + thirtyDaysAgoSec;
        String sortOrder = MediaStore.MediaColumns.DATE_MODIFIED + " DESC LIMIT 300";
        String[] projection = new String[]{
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED
        };

        Set<String> indexedPaths = new HashSet<>();

        try (Cursor cursor = cr.query(uri, projection, selection, null, sortOrder)) {
            if (cursor != null) {
                int dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA);
                int sizeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE);
                int dateIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED);

                while (cursor.moveToNext() && newItems.size() < 300) {
                    String path = cursor.getString(dataIdx);
                    if (path == null || path.isEmpty()) continue;
                    if (path.contains("/.") || path.contains("\\.")) continue;

                    File file = new File(path);
                    if (!file.exists() || file.isDirectory()) continue;

                    long size = sizeIdx != -1 ? cursor.getLong(sizeIdx) : 0;
                    if (size <= 0 && file.exists()) size = file.length();
                    long dateModified = dateIdx != -1 ? cursor.getLong(dateIdx) * 1000L : 0;
                    if (dateModified <= 0 && file.exists()) dateModified = file.lastModified();

                    indexedPaths.add(file.getAbsolutePath());
                    newItems.add(new FileItem(file, size, dateModified));
                }
            }
        } catch (Exception ignored) {}

        // Fast shallow check of standard recording folders for recently recorded files not yet in MediaStore
        long now = System.currentTimeMillis();
        long thirtyDaysAgo = now - (30L * 24 * 60 * 60 * 1000L);
        File root = Environment.getExternalStorageDirectory();
        List<File> recentDirs = new ArrayList<>();
        recentDirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RECORDINGS));
        recentDirs.add(new File(root, "Recordings"));
        recentDirs.add(new File(root, "MIUI/sound_recorder"));
        recentDirs.add(new File(root, "VoiceRecorder"));
        recentDirs.add(new File(root, "Sounds"));
        recentDirs.add(new File(root, "Music/Recordings"));

        List<String> unindexedFiles = new ArrayList<>();
        for (File dir : recentDirs) {
            if (dir != null && dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files == null) continue;
                for (File f : files) {
                    if (f.isFile() && !f.getName().startsWith(".") && f.lastModified() >= thirtyDaysAgo) {
                        String abs = f.getAbsolutePath();
                        if (!indexedPaths.contains(abs)) {
                            indexedPaths.add(abs);
                            unindexedFiles.add(abs);
                            newItems.add(new FileItem(f));
                        }
                    }
                }
            }
        }

        if (!unindexedFiles.isEmpty()) {
            try {
                MediaScannerConnection.scanFile(
                        context.getApplicationContext(),
                        unindexedFiles.toArray(new String[0]),
                        null,
                        null
                );
            } catch (Exception ignored) {}
        }

        if (newItems.isEmpty()) {
            collectNewFilesAndFolders(root, thirtyDaysAgo, newItems, 300);
        }

        Collections.sort(newItems, (a, b) -> Long.compare(b.getLastModified(), a.getLastModified()));
        if (newItems.size() > 300) {
            newItems = new ArrayList<>(newItems.subList(0, 300));
        }

        return newItems;
    }

    private void collectNewFilesAndFolders(File folder, long cutoffTime, List<FileItem> resultList, int maxLimit) {
        if (folder == null || !folder.exists() || resultList.size() >= maxLimit) return;

        File[] files = folder.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (resultList.size() >= maxLimit) return;
            String name = file.getName();
            if (name.startsWith(".") || name.equalsIgnoreCase("Android")) {
                continue;
            }

            if (file.lastModified() >= cutoffTime) {
                resultList.add(new FileItem(file));
            }

            if (file.isDirectory()) {
                collectNewFilesAndFolders(file, cutoffTime, resultList, maxLimit);
            }
        }
    }

    /**
     * Discovers all folders and files belonging to this category across device storage.
     * Uses in-memory cache for instant 0ms display and indexed MediaStore query for ultra-fast background sync.
     */
    private void scanCategoryContent(CategoryItem.Type type) {
        Map<File, List<FileItem>> cached = categoryFolderCache.get(type);
        boolean hasCachedData = (cached != null && !cached.isEmpty());

        if (hasCachedData) {
            folderToCategoryFilesMap.clear();
            folderToCategoryFilesMap.putAll(cached);
            progressCategory.setVisibility(View.GONE);
            displayCategoryFoldersOverview();
        } else {
            progressCategory.setVisibility(View.VISIBLE);
            tvCategoryEmpty.setVisibility(View.GONE);
            fileAdapter.updateList(new ArrayList<>());
            folderToCategoryFilesMap.clear();
        }

        executorService.execute(() -> {
            Map<File, List<FileItem>> freshMap = queryCategoryFiles(type);

            mainHandler.post(() -> {
                if (!isAdded() || activeCategoryType != type || activeCategoryFolder != null) return;
                categoryFolderCache.put(type, freshMap);

                if (!hasCachedData || !areMapsEqual(cached, freshMap)) {
                    folderToCategoryFilesMap.clear();
                    folderToCategoryFilesMap.putAll(freshMap);
                    progressCategory.setVisibility(View.GONE);
                    displayCategoryFoldersOverview();
                } else {
                    progressCategory.setVisibility(View.GONE);
                }
            });
        });
    }

    private boolean areMapsEqual(Map<File, List<FileItem>> m1, Map<File, List<FileItem>> m2) {
        if (m1 == null || m2 == null) return false;
        if (m1.size() != m2.size()) return false;
        for (Map.Entry<File, List<FileItem>> e : m1.entrySet()) {
            List<FileItem> l2 = m2.get(e.getKey());
            if (l2 == null || l2.size() != e.getValue().size()) {
                return false;
            }
        }
        return true;
    }

    private Map<File, List<FileItem>> queryCategoryFiles(CategoryItem.Type type) {
        Map<File, List<FileItem>> resultMap = new HashMap<>();
        Context context = getContext();
        if (context == null) return resultMap;

        if (type == CategoryItem.Type.DOWNLOADS) {
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (downloadDir != null && downloadDir.exists()) {
                collectDownloadsFolder(downloadDir, resultMap);
            }
            return resultMap;
        }

        ContentResolver cr = context.getContentResolver();
        Uri uri;
        String selection = null;

        switch (type) {
            case AUDIO:
                uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                break;
            case IMAGES:
                uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                break;
            case VIDEOS:
                uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                break;
            case DOCUMENTS:
                uri = MediaStore.Files.getContentUri("external");
                selection = MediaStore.Files.FileColumns.DATA + " LIKE '%.pdf' OR " +
                        MediaStore.Files.FileColumns.DATA + " LIKE '%.doc' OR " +
                        MediaStore.Files.FileColumns.DATA + " LIKE '%.docx' OR " +
                        MediaStore.Files.FileColumns.DATA + " LIKE '%.xls' OR " +
                        MediaStore.Files.FileColumns.DATA + " LIKE '%.xlsx' OR " +
                        MediaStore.Files.FileColumns.DATA + " LIKE '%.ppt' OR " +
                        MediaStore.Files.FileColumns.DATA + " LIKE '%.pptx' OR " +
                        MediaStore.Files.FileColumns.DATA + " LIKE '%.txt' OR " +
                        MediaStore.Files.FileColumns.DATA + " LIKE '%.epub' OR " +
                        MediaStore.Files.FileColumns.DATA + " LIKE '%.rtf' OR " +
                        MediaStore.Files.FileColumns.DATA + " LIKE '%.csv'";
                break;
            case APPS:
                uri = MediaStore.Files.getContentUri("external");
                selection = MediaStore.Files.FileColumns.DATA + " LIKE '%.apk' OR " +
                        MediaStore.Files.FileColumns.DATA + " LIKE '%.xapk' OR " +
                        MediaStore.Files.FileColumns.DATA + " LIKE '%.apks'";
                break;
            default:
                return resultMap;
        }

        String[] projection = new String[]{
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED
        };

        Map<String, File> canonicalParentCache = new HashMap<>();
        Set<String> indexedPaths = new HashSet<>();

        try (Cursor cursor = cr.query(uri, projection, selection, null, null)) {
            if (cursor != null) {
                int dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA);
                int sizeIdx = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE);
                int dateIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED);

                while (cursor.moveToNext()) {
                    String path = cursor.getString(dataIdx);
                    if (path == null || path.isEmpty()) continue;
                    if (path.contains("/.") || path.contains("\\.")) continue;

                    int lastSlash = path.lastIndexOf('/');
                    if (lastSlash <= 0) continue;

                    String parentPath = path.substring(0, lastSlash);
                    File canonicalParent = canonicalParentCache.get(parentPath);
                    if (canonicalParent == null) {
                        File p = new File(parentPath);
                        if (!p.exists()) continue;
                        try {
                            canonicalParent = p.getCanonicalFile();
                        } catch (IOException e) {
                            canonicalParent = p;
                        }
                        canonicalParentCache.put(parentPath, canonicalParent);
                    }

                    File file = new File(path);
                    long size = sizeIdx != -1 ? cursor.getLong(sizeIdx) : 0;
                    if (size <= 0 && file.exists()) size = file.length();
                    long dateModified = dateIdx != -1 ? cursor.getLong(dateIdx) * 1000L : 0;
                    if (dateModified <= 0 && file.exists()) dateModified = file.lastModified();

                    List<FileItem> list = resultMap.get(canonicalParent);
                    if (list == null) {
                        list = new ArrayList<>();
                        resultMap.put(canonicalParent, list);
                    }
                    list.add(new FileItem(file, size, dateModified));
                    indexedPaths.add(file.getAbsolutePath());
                }
            }
        } catch (Exception ignored) {}

        // Scan standard physical directories directly to instantly surface newly recorded/created files
        scanPhysicalDirectoriesForCategory(type, indexedPaths, resultMap, context);

        if (resultMap.isEmpty()) {
            File root = Environment.getExternalStorageDirectory();
            Set<String> extensions = getExtensionsForType(type);
            collectCategoryFilesFallback(root, extensions, resultMap, 0);
        }

        return resultMap;
    }

    private void scanPhysicalDirectoriesForCategory(
            CategoryItem.Type type,
            Set<String> indexedPaths,
            Map<File, List<FileItem>> resultMap,
            Context context
    ) {
        if (type != CategoryItem.Type.AUDIO || context == null) return;
        List<File> candidateDirs = new ArrayList<>();
        File storageRoot = Environment.getExternalStorageDirectory();

        candidateDirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RECORDINGS));
        candidateDirs.add(new File(storageRoot, "Recordings"));
        candidateDirs.add(new File(storageRoot, "MIUI/sound_recorder"));
        candidateDirs.add(new File(storageRoot, "VoiceRecorder"));
        candidateDirs.add(new File(storageRoot, "Sounds"));
        candidateDirs.add(new File(storageRoot, "Music/Recordings"));

        Set<String> extensions = getExtensionsForType(type);
        List<String> unindexedFilesToScan = new ArrayList<>();

        for (File dir : candidateDirs) {
            if (dir != null && dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files == null) continue;
                for (File f : files) {
                    if (f.isFile() && !f.getName().startsWith(".")) {
                        String ext = getFileExtension(f.getName()).toLowerCase(Locale.ENGLISH);
                        if (extensions == null || extensions.contains(ext)) {
                            String absPath = f.getAbsolutePath();
                            if (!indexedPaths.contains(absPath)) {
                                indexedPaths.add(absPath);
                                unindexedFilesToScan.add(absPath);

                                File parent = f.getParentFile();
                                if (parent != null) {
                                    try {
                                        parent = parent.getCanonicalFile();
                                    } catch (IOException ignored) {}

                                    List<FileItem> list = resultMap.get(parent);
                                    if (list == null) {
                                        list = new ArrayList<>();
                                        resultMap.put(parent, list);
                                    }
                                    list.add(new FileItem(f));
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!unindexedFilesToScan.isEmpty()) {
            try {
                MediaScannerConnection.scanFile(
                        context.getApplicationContext(),
                        unindexedFilesToScan.toArray(new String[0]),
                        null,
                        null
                );
            } catch (Exception ignored) {}
        }
    }

    private void collectDownloadsFolder(File folder, Map<File, List<FileItem>> resultMap) {
        if (folder == null || !folder.exists()) return;
        File[] files = folder.listFiles();
        if (files == null) return;

        File canonicalFolder = folder;
        try {
            canonicalFolder = folder.getCanonicalFile();
        } catch (IOException ignored) {}

        List<FileItem> folderFiles = new ArrayList<>();
        for (File f : files) {
            if (f.getName().startsWith(".")) continue;
            if (f.isDirectory()) {
                collectDownloadsFolder(f, resultMap);
            } else {
                folderFiles.add(new FileItem(f));
            }
        }
        if (!folderFiles.isEmpty()) {
            resultMap.put(canonicalFolder, folderFiles);
        }
    }

    private void collectCategoryFilesFallback(File folder, @Nullable Set<String> extensions, Map<File, List<FileItem>> resultMap, int depth) {
        if (folder == null || !folder.exists() || depth > 5) return;
        File[] files = folder.listFiles();
        if (files == null) return;

        for (File file : files) {
            String name = file.getName();
            if (name.startsWith(".") || name.equalsIgnoreCase("Android")) {
                continue;
            }

            if (file.isDirectory()) {
                collectCategoryFilesFallback(file, extensions, resultMap, depth + 1);
            } else {
                String ext = getFileExtension(name);
                if (extensions == null || extensions.contains(ext.toLowerCase(Locale.ENGLISH))) {
                    File parent = file.getParentFile();
                    if (parent != null) {
                        try {
                            parent = parent.getCanonicalFile();
                        } catch (IOException ignored) {}

                        List<FileItem> list = resultMap.get(parent);
                        if (list == null) {
                            list = new ArrayList<>();
                            resultMap.put(parent, list);
                        }
                        list.add(new FileItem(file));
                    }
                }
            }
        }
    }

    /**
     * Displays all folders on the device that contain files in this category.
     * Each folder displays ONLY the count and size of files belonging to this category,
     * and identical folder names are clearly disambiguated with their parent directory.
     */
    private void displayCategoryFoldersOverview() {
        currentVisibleItems.clear();

        // 1. Detect duplicate folder names across device storage to disambiguate
        Map<String, Integer> nameFrequencyMap = new HashMap<>();
        for (File folder : folderToCategoryFilesMap.keySet()) {
            nameFrequencyMap.put(folder.getName(), nameFrequencyMap.getOrDefault(folder.getName(), 0) + 1);
        }

        File root = Environment.getExternalStorageDirectory();
        String categoryNoun = getCategoryNoun(activeCategoryType);

        // 2. Build FileItem with category-scoped count and category storage size avoiding disk I/O
        for (Map.Entry<File, List<FileItem>> entry : folderToCategoryFilesMap.entrySet()) {
            File folder = entry.getKey();
            List<FileItem> filesInFolder = entry.getValue();
            int catCount = (filesInFolder != null) ? filesInFolder.size() : 0;
            long catSize = 0;
            long maxModified = 0;
            if (filesInFolder != null) {
                for (FileItem fi : filesInFolder) {
                    catSize += fi.getLength();
                    if (fi.getLastModified() > maxModified) {
                        maxModified = fi.getLastModified();
                    }
                }
            }

            String disambiguatedName = null;
            if (nameFrequencyMap.getOrDefault(folder.getName(), 0) > 1) {
                File p = folder.getParentFile();
                if (p == null || p.equals(root) || p.getName().equals("0") || p.getName().equalsIgnoreCase("emulated")) {
                    disambiguatedName = folder.getName() + " (Main Storage)";
                } else {
                    disambiguatedName = folder.getName() + " (in " + p.getName() + ")";
                }
            }

            currentVisibleItems.add(new FileItem(folder, catCount, catSize, categoryNoun, disambiguatedName, maxModified));
        }

        if (activeCategoryType != null && isAdded()) {
            currentSortMode = SortPreferences.getCategorySortMode(requireContext(), activeCategoryType.name());
        }
        applySortingAndDisplay();

        if (currentVisibleItems.isEmpty()) {
            tvCategoryEmpty.setVisibility(View.VISIBLE);
            tvCategoryEmpty.setText(R.string.category_no_files);
            tvCategoryEmpty.announceForAccessibility(getString(R.string.category_no_files));
        } else {
            tvCategoryEmpty.setVisibility(View.GONE);
            String announcement = getString(R.string.category_opened_announcement, activeCategoryTitle, currentVisibleItems.size());
            tvCategoryDetailTitle.announceForAccessibility(announcement);
        }
    }

    /**
     * Displays ONLY files belonging to the active category within the selected folder.
     */
    private void displayFolderFiles(File folder) {
        File canonicalFolder = folder;
        try {
            canonicalFolder = folder.getCanonicalFile();
        } catch (IOException ignored) {}

        activeCategoryFolder = canonicalFolder;
        tvCategoryDetailTitle.setText(activeCategoryTitle + " / " + canonicalFolder.getName());

        currentVisibleItems.clear();
        List<FileItem> filesInFolder = folderToCategoryFilesMap.get(canonicalFolder);
        if (filesInFolder != null) {
            currentVisibleItems.addAll(filesInFolder);
        } else {
            // Safe fallback: strictly filter by category extensions
            File[] files = canonicalFolder.listFiles();
            Set<String> extensions = getExtensionsForType(activeCategoryType);
            if (files != null) {
                for (File f : files) {
                    if (!f.getName().startsWith(".") && !f.isDirectory()) {
                        String ext = getFileExtension(f.getName());
                        if (extensions == null || extensions.contains(ext.toLowerCase(Locale.ENGLISH))) {
                            currentVisibleItems.add(new FileItem(f));
                        }
                    }
                }
            }
        }

        if (isAdded()) {
            currentSortMode = SortPreferences.getSortModeForFolder(requireContext(), canonicalFolder.getAbsolutePath());
        }
        applySortingAndDisplay();

        if (currentVisibleItems.isEmpty()) {
            tvCategoryEmpty.setVisibility(View.VISIBLE);
            tvCategoryEmpty.setText(R.string.category_no_files);
        } else {
            tvCategoryEmpty.setVisibility(View.GONE);
            String announcement = getString(R.string.folder_loaded_announcement, canonicalFolder.getName(), currentVisibleItems.size());
            tvCategoryDetailTitle.announceForAccessibility(announcement);
        }
    }

    public void refreshFolderFilesFromDisk(File folder) {
        if (folder == null) return;
        File canonicalFolder = folder;
        try {
            canonicalFolder = folder.getCanonicalFile();
        } catch (IOException ignored) {}

        File[] files = canonicalFolder.listFiles();
        List<FileItem> freshList = new ArrayList<>();
        Set<String> extensions = getExtensionsForType(activeCategoryType);
        if (files != null) {
            for (File f : files) {
                if (!f.getName().startsWith(".") && !f.isDirectory()) {
                    String ext = getFileExtension(f.getName());
                    if (extensions == null || extensions.contains(ext.toLowerCase(Locale.ENGLISH))) {
                        freshList.add(new FileItem(f));
                    }
                }
            }
        }
        folderToCategoryFilesMap.put(canonicalFolder, freshList);
        if (activeCategoryType != null) {
            categoryFolderCache.remove(activeCategoryType);
        }
        displayFolderFiles(canonicalFolder);
    }

    private String getCategoryNoun(CategoryItem.Type type) {
        if (type == null) return "item";
        switch (type) {
            case AUDIO:
                return "audio";
            case IMAGES:
                return "image";
            case VIDEOS:
                return "video";
            case DOCUMENTS:
                return "document";
            case APPS:
                return "APK";
            case DOWNLOADS:
            case NEW_FILES:
            default:
                return "file";
        }
    }

    /**
     * Calculates storage size for each category asynchronously via MediaStore and pre-caches category files.
     */
    private void loadCategoryStorageSizes() {
        if (categories == null || !isAdded()) return;

        executorService.execute(() -> {
            Context context = getContext();
            if (context == null) return;

            for (CategoryItem cat : categories) {
                if (cat == null) continue;
                CategoryItem.Type type = cat.getType();
                long totalSize = 0;

                if (type == CategoryItem.Type.NEW_FILES) {
                    List<FileItem> newFiles = queryNewFiles();
                    cachedNewFiles.clear();
                    cachedNewFiles.addAll(newFiles);
                    for (FileItem fi : newFiles) {
                        totalSize += fi.getLength();
                    }
                } else {
                    Map<File, List<FileItem>> catMap = queryCategoryFiles(type);
                    categoryFolderCache.put(type, catMap);
                    for (List<FileItem> files : catMap.values()) {
                        for (FileItem fi : files) {
                            totalSize += fi.getLength();
                        }
                    }
                }

                final long finalSize = totalSize;
                cat.setTotalSizeBytes(finalSize);

                mainHandler.post(() -> {
                    if (isAdded() && categoryAdapter != null) {
                        categoryAdapter.notifyDataSetChanged();
                    }
                });
            }
        });
    }

    private long queryContentUriSize(ContentResolver cr, Uri uri, String selection, String[] selectionArgs) {
        long total = 0;
        try (Cursor cursor = cr.query(uri, new String[]{ MediaStore.MediaColumns.SIZE }, selection, selectionArgs, null)) {
            if (cursor != null) {
                int sizeIndex = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE);
                if (sizeIndex != -1) {
                    while (cursor.moveToNext()) {
                        long s = cursor.getLong(sizeIndex);
                        if (s > 0) {
                            total += s;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return total;
    }

    private long getDirectoryTotalSize(File dir) {
        if (dir == null || !dir.exists()) return 0;
        long total = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    total += getDirectoryTotalSize(f);
                } else {
                    total += f.length();
                }
            }
        }
        return total;
    }

    private void applySortingAndDisplay() {
        Collections.sort(currentVisibleItems, currentSortMode.getComparator());
        fileAdapter.updateList(currentVisibleItems);
    }

    private void showSortDialog() {
        if (activeCategoryType == CategoryItem.Type.APPS && currentAppsMode == 1) {
            showInstalledAppSortDialog();
            return;
        }

        if (!isAdded()) return;

        boolean isInsideFolder = (activeCategoryFolder != null);
        String checkboxText;
        String checkboxHint;
        boolean hasOverride;

        if (isInsideFolder) {
            hasOverride = SortPreferences.hasFolderSortOverride(requireContext(), activeCategoryFolder.getAbsolutePath());
            checkboxText = getString(R.string.sort_apply_this_folder_only);
            checkboxHint = getString(R.string.sort_apply_this_folder_only_hint);
        } else {
            String categoryKey = activeCategoryType != null ? activeCategoryType.name() : "GENERAL";
            hasOverride = SortPreferences.hasCategorySortOverride(requireContext(), categoryKey);
            checkboxText = getString(R.string.sort_apply_this_category_only);
            checkboxHint = getString(R.string.sort_apply_this_category_only_hint);
        }

        Dialog dialog = SortDialogHelper.createSortDialog(
                requireContext(),
                currentSortMode,
                hasOverride,
                checkboxText,
                checkboxHint,
                (selectedMode, isCurrentOnly) -> {
                    currentSortMode = selectedMode;
                    String sortName = getString(selectedMode.getTitleResId());
                    String announcement;

                    if (isInsideFolder) {
                        if (isCurrentOnly) {
                            SortPreferences.setSortModeForFolder(requireContext(), activeCategoryFolder.getAbsolutePath(), selectedMode);
                            announcement = getString(R.string.sorted_folder_announcement, sortName);
                        } else {
                            SortPreferences.setGlobalSortMode(requireContext(), selectedMode);
                            SortPreferences.clearFolderSortMode(requireContext(), activeCategoryFolder.getAbsolutePath());
                            announcement = getString(R.string.sorted_globally_announcement, sortName);
                        }
                    } else {
                        String categoryKey = activeCategoryType != null ? activeCategoryType.name() : "GENERAL";
                        if (isCurrentOnly) {
                            SortPreferences.setCategorySortMode(requireContext(), categoryKey, selectedMode);
                            announcement = getString(R.string.sorted_category_announcement, sortName);
                        } else {
                            SortPreferences.setGlobalSortMode(requireContext(), selectedMode);
                            SortPreferences.clearCategorySortMode(requireContext(), categoryKey);
                            announcement = getString(R.string.sorted_globally_announcement, sortName);
                        }
                    }

                    if (isSearchActive && !TextUtils.isEmpty(currentSearchQuery)) {
                        filterCategoryFilesRealTime(currentSearchQuery);
                    } else {
                        applySortingAndDisplay();
                    }

                    recyclerCategoryFiles.announceForAccessibility(announcement);
                    Toast.makeText(requireContext(), announcement, Toast.LENGTH_SHORT).show();
                }
        );
        setActiveDialog(dialog);
    }

    private void showInstalledAppSortDialog() {
        String[] options = new String[]{
                getString(R.string.sort_name_asc),
                getString(R.string.sort_name_desc),
                getString(R.string.sort_size_desc),
                getString(R.string.sort_size_asc),
                getString(R.string.sort_user_apps_first),
                getString(R.string.sort_system_apps_first)
        };

        Dialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.sort_dialog_title)
                .setSingleChoiceItems(options, installedAppSortMode, (d, which) -> {
                    installedAppSortMode = which;
                    SortPreferences.setInstalledAppSortMode(requireContext(), which);
                    applyInstalledAppSortAndDisplay();
                    String announcement = getString(R.string.sorted_by_announcement, options[which]);
                    recyclerCategoryFiles.announceForAccessibility(announcement);
                    Toast.makeText(requireContext(), announcement, Toast.LENGTH_SHORT).show();
                    d.dismiss();
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .create();
        setActiveDialog(dialog);
    }

    private void applyInstalledAppSortAndDisplay() {
        Collections.sort(allInstalledApps, (a, b) -> {
            switch (installedAppSortMode) {
                case 1: // Name Z-A
                    return b.getAppName().compareToIgnoreCase(a.getAppName());
                case 2: // Size Largest
                    return Long.compare(b.getApkSize(), a.getApkSize());
                case 3: // Size Smallest
                    return Long.compare(a.getApkSize(), b.getApkSize());
                case 4: // User apps first
                    if (a.isSystemApp() != b.isSystemApp()) {
                        return a.isSystemApp() ? 1 : -1;
                    }
                    return a.getAppName().compareToIgnoreCase(b.getAppName());
                case 5: // System apps first
                    if (a.isSystemApp() != b.isSystemApp()) {
                        return a.isSystemApp() ? -1 : 1;
                    }
                    return a.getAppName().compareToIgnoreCase(b.getAppName());
                case 0: // Name A-Z
                default:
                    return a.getAppName().compareToIgnoreCase(b.getAppName());
            }
        });

        if (isSearchActive && !TextUtils.isEmpty(currentSearchQuery)) {
            filterCategoryFilesRealTime(currentSearchQuery);
        } else {
            installedAppAdapter.updateList(allInstalledApps);
            if (allInstalledApps.isEmpty()) {
                tvCategoryEmpty.setVisibility(View.VISIBLE);
                tvCategoryEmpty.setText(R.string.category_no_files);
            } else {
                tvCategoryEmpty.setVisibility(View.GONE);
            }
        }
    }

    private void setupAppsTabs() {
        tabLayoutApps.clearOnTabSelectedListeners();
        tabLayoutApps.removeAllTabs();
        tabLayoutApps.addTab(tabLayoutApps.newTab().setText(R.string.tab_apk_files), currentAppsMode == 0);
        tabLayoutApps.addTab(tabLayoutApps.newTab().setText(R.string.tab_installed_apps), currentAppsMode == 1);
        tabLayoutApps.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentAppsMode = tab.getPosition();
                switchAppsMode(currentAppsMode);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void switchAppsMode(int mode) {
        if (fileAdapter != null && fileAdapter.isSelectionMode()) {
            exitSelectionMode();
        }
        if (isSearchActive) {
            closeSearch();
        }
        if (mode == 0) {
            recyclerCategoryFiles.setAdapter(fileAdapter);
            activeCategoryFolder = null;
            tvCategoryDetailTitle.setText(activeCategoryTitle);
            scanCategoryContent(CategoryItem.Type.APPS);
        } else {
            recyclerCategoryFiles.setAdapter(installedAppAdapter);
            activeCategoryFolder = null;
            tvCategoryDetailTitle.setText(getString(R.string.tab_installed_apps));
            loadInstalledApplications();
        }
    }

    private void loadInstalledApplications() {
        progressCategory.setVisibility(View.VISIBLE);
        tvCategoryEmpty.setVisibility(View.GONE);
        installedAppAdapter.updateList(new ArrayList<>());
        allInstalledApps.clear();

        executorService.execute(() -> {
            PackageManager pm = requireContext().getPackageManager();
            List<PackageInfo> packages;
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packages = pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0));
                } else {
                    packages = pm.getInstalledPackages(0);
                }
            } catch (Exception e) {
                packages = new ArrayList<>();
            }

            List<InstalledAppItem> appList = new ArrayList<>();
            for (PackageInfo pkg : packages) {
                ApplicationInfo appInfo = pkg.applicationInfo;
                if (appInfo == null) continue;

                String appName = appInfo.loadLabel(pm).toString();
                Drawable appIcon = appInfo.loadIcon(pm);
                String packageName = pkg.packageName;
                String versionName = pkg.versionName != null ? pkg.versionName : "1.0";
                boolean isSystemApp = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;

                long apkSize = 0;
                File apkFile = null;
                if (appInfo.sourceDir != null) {
                    apkFile = new File(appInfo.sourceDir);
                    if (apkFile.exists()) {
                        apkSize = apkFile.length();
                    }
                }

                InstalledAppItem item = new InstalledAppItem(
                        appName,
                        packageName,
                        versionName,
                        apkSize,
                        isSystemApp,
                        appInfo.sourceDir,
                        appIcon
                );
                appList.add(item);
            }

            mainHandler.post(() -> {
                if (!isAdded()) return;
                progressCategory.setVisibility(View.GONE);
                allInstalledApps.clear();
                allInstalledApps.addAll(appList);
                installedAppSortMode = SortPreferences.getInstalledAppSortMode(requireContext());
                applyInstalledAppSortAndDisplay();

                String countAnnouncement = getString(R.string.installed_apps_count, allInstalledApps.size());
                tvCategoryDetailTitle.announceForAccessibility(countAnnouncement);
            });
        });
    }

    // --- InstalledAppAdapter.OnInstalledAppActionListener Implementation ---

    @Override
    public void onAppClick(InstalledAppItem item, int position) {
        showAppActionsDialog(item);
    }

    @Override
    public void onAppLongClick(InstalledAppItem item, int position) {
        showAppActionsDialog(item);
    }

    @Override
    public void onAppOpen(InstalledAppItem item) {
        try {
            Intent launchIntent = requireContext().getPackageManager().getLaunchIntentForPackage(item.getPackageName());
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(launchIntent);
            } else {
                Toast.makeText(requireContext(), getString(R.string.cannot_open_app, item.getAppName()), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Could not open: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onAppInfo(InstalledAppItem item) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + item.getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Could not open App Info", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onAppUninstall(InstalledAppItem item) {
        if (item.isSystemApp()) {
            Toast.makeText(requireContext(), R.string.cannot_uninstall_system_app, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_DELETE);
            intent.setData(Uri.parse("package:" + item.getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Uninstall error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onAppShareApk(InstalledAppItem item) {
        if (item.getApkFile() != null && item.getApkFile().exists()) {
            shareFile(item.getApkFile(), "apk", item.getAppName() + ".apk");
        } else {
            Toast.makeText(requireContext(), "APK file not found", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareAppPlayStoreLink(InstalledAppItem item) {
        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            String playStoreUrl = "https://play.google.com/store/apps/details?id=" + item.getPackageName();
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, item.getAppName());
            shareIntent.putExtra(Intent.EXTRA_TEXT, playStoreUrl);
            startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share_link)));
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Failed to share link: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void extractApkToDownloads(InstalledAppItem item) {
        if (item.getApkFile() == null || !item.getApkFile().exists()) {
            Toast.makeText(requireContext(), "APK file not found", Toast.LENGTH_SHORT).show();
            return;
        }

        String toastStart = getString(R.string.toast_extracting_apk, item.getAppName());
        Toast.makeText(requireContext(), toastStart, Toast.LENGTH_SHORT).show();
        recyclerCategoryFiles.announceForAccessibility(toastStart);
        progressCategory.setVisibility(View.VISIBLE);

        executorService.execute(() -> {
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs();
            }
            String safeName = item.getAppName().replaceAll("[\\\\/:*?\"<>|]", "_");
            File destFile = new File(downloadsDir, safeName + "_" + item.getVersionName() + ".apk");

            boolean success = false;
            try (FileInputStream in = new FileInputStream(item.getApkFile());
                 FileOutputStream out = new FileOutputStream(destFile)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                success = true;
            } catch (Exception e) {
                success = false;
            }

            final boolean finalSuccess = success;
            mainHandler.post(() -> {
                if (!isAdded()) return;
                progressCategory.setVisibility(View.GONE);
                if (finalSuccess) {
                    String msg = getString(R.string.toast_apk_extracted, item.getAppName());
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                    recyclerCategoryFiles.announceForAccessibility(msg);
                } else {
                    String msg = getString(R.string.toast_apk_extract_failed, item.getAppName());
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                    recyclerCategoryFiles.announceForAccessibility(msg);
                }
            });
        });
    }

    private void showAppActionsDialog(InstalledAppItem item) {
        List<String> options = new ArrayList<>();
        options.add(getString(R.string.action_open_app));
        options.add(getString(R.string.action_app_info));
        if (!item.isSystemApp()) {
            options.add(getString(R.string.action_uninstall));
        }
        options.add(getString(R.string.action_share_apk));
        options.add(getString(R.string.action_share_link));
        options.add(getString(R.string.action_extract_apk));

        String[] optionsArray = options.toArray(new String[0]);

        Dialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(item.getAppName())
                .setItems(optionsArray, (d, which) -> {
                    String selected = optionsArray[which];
                    if (selected.equals(getString(R.string.action_open_app))) {
                        onAppOpen(item);
                    } else if (selected.equals(getString(R.string.action_app_info))) {
                        onAppInfo(item);
                    } else if (selected.equals(getString(R.string.action_uninstall))) {
                        onAppUninstall(item);
                    } else if (selected.equals(getString(R.string.action_share_apk))) {
                        onAppShareApk(item);
                    } else if (selected.equals(getString(R.string.action_share_link))) {
                        shareAppPlayStoreLink(item);
                    } else if (selected.equals(getString(R.string.action_extract_apk))) {
                        extractApkToDownloads(item);
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .create();
        setActiveDialog(dialog);
    }

    private Set<String> getExtensionsForType(CategoryItem.Type type) {
        switch (type) {
            case AUDIO:
                return AUDIO_EXTENSIONS;
            case IMAGES:
                return IMAGE_EXTENSIONS;
            case VIDEOS:
                return VIDEO_EXTENSIONS;
            case DOCUMENTS:
                return DOC_EXTENSIONS;
            case APPS:
                return APP_EXTENSIONS;
            default:
                return null;
        }
    }

    private String getFileExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            return name.substring(dot + 1);
        }
        return "";
    }

    // --- File & Folder Click and Action Handlers ---

    @Override
    public void onItemClick(FileItem item, int position) {
        if (fileAdapter != null && fileAdapter.isSelectionMode()) {
            fileAdapter.toggleSelection(position);
            return;
        }
        if (item.isDirectory()) {
            if (isSearchActive) {
                closeSearch();
            }
            displayFolderFiles(item.getFile());
        } else if (ArchiveViewerHelper.isArchive(item.getName())) {
            if (isSearchActive) {
                closeSearch();
            }
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
        FileOpener.openFileWith(requireContext(), item.getFile());
    }

    @Override
    public void onActionProperties(FileItem item) {
        FilePropertiesDialog.show(requireContext(), item, this::setActiveDialog);
    }

    @Override
    public void onItemLongClick(FileItem item, int position) {
        List<ActionConfig> configs = ActionMenuManager.getInstance(requireContext()).getActionConfigs();
        List<String> options = new ArrayList<>();
        List<String> actionIds = new ArrayList<>();
        boolean isFav = FavoriteManager.getInstance(requireContext()).isFavorite(item.getPath());

        for (ActionConfig config : configs) {
            if (!config.isEnabled()) continue;
            if (ActionConfig.ACTION_OPEN_WITH.equals(config.getId()) && item.isDirectory()) continue;
            if (ActionConfig.ACTION_EDIT.equals(config.getId()) && (item.isDirectory() || (!item.isAudio() && !item.isVideo()))) continue;
            if (ActionConfig.ACTION_BATCH_CONVERT.equals(config.getId()) && !item.isDirectory()) continue;

            if (ActionConfig.ACTION_FAVORITE.equals(config.getId())) {
                options.add(getString(isFav ? R.string.action_remove_favorite : R.string.action_add_favorite));
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
                        case ActionConfig.ACTION_SELECT:
                            onActionSelect(item, position);
                            break;
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

    public void enterSelectionMode(int initialPosition) {
        if (isSearchActive) {
            closeSearch();
        }
        fileAdapter.setSelectionMode(true);
        fileAdapter.toggleSelection(initialPosition);

        categoryDetailHeader.setVisibility(View.VISIBLE);
        if (categorySelectionHeaderLayout != null) {
            categorySelectionHeaderLayout.setVisibility(View.VISIBLE);
        }
        if (categorySelectionBottomBar != null) {
            categorySelectionBottomBar.setVisibility(View.VISIBLE);
        }
        updateSelectionTitle(1);
        updateBackCallbackState();
    }

    public void exitSelectionMode() {
        if (fileAdapter != null) {
            fileAdapter.setSelectionMode(false);
        }
        if (categorySelectionHeaderLayout != null) {
            categorySelectionHeaderLayout.setVisibility(View.GONE);
        }
        if (categorySelectionBottomBar != null) {
            categorySelectionBottomBar.setVisibility(View.GONE);
        }
        categoryDetailHeader.setVisibility(View.VISIBLE);
        updateBackCallbackState();
        recyclerCategoryFiles.announceForAccessibility(getString(R.string.btn_exit_selection));
    }

    private void updateSelectionTitle(int count) {
        if (tvCategorySelectionCount != null) {
            String title = getString(R.string.selection_mode_title, count);
            tvCategorySelectionCount.setText(title);
            tvCategorySelectionCount.setContentDescription(title);
        }
    }

    @Override
    public void onSelectionCountChanged(int count) {
        updateSelectionTitle(count);
        if (count == 0 && fileAdapter != null && fileAdapter.isSelectionMode()) {
            exitSelectionMode();
        }
    }

    @Override
    public void onRangeSelected(int start, int end, int count, FileItem first, FileItem last) {
        String announcement = getString(R.string.range_selected_announcement, count, first.getName(), last.getName(), fileAdapter.getSelectedPaths().size());
        recyclerCategoryFiles.announceForAccessibility(announcement);
        Toast.makeText(requireContext(), announcement, Toast.LENGTH_SHORT).show();
    }

    private void deleteSelectedItems() {
        List<FileItem> selected = fileAdapter.getSelectedItems();
        if (selected.isEmpty()) return;

        Dialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_delete_title)
                .setMessage(getString(R.string.delete_selected_confirmation, selected.size()))
                .setPositiveButton(R.string.btn_delete, (d, which) -> {
                    progressCategory.setVisibility(View.VISIBLE);
                    executorService.execute(() -> {
                        int deletedCount = 0;
                        List<String> deletedPaths = new ArrayList<>();
                        for (FileItem item : selected) {
                            if (deleteRecursively(item.getFile())) {
                                deletedCount++;
                                deletedPaths.add(item.getPath());
                            }
                        }
                        final int finalCount = deletedCount;
                        mainHandler.post(() -> {
                            if (!isAdded()) return;
                            progressCategory.setVisibility(View.GONE);
                            exitSelectionMode();

                            if (activeCategoryFolder != null) {
                                refreshFolderFilesFromDisk(activeCategoryFolder);
                            } else if (activeCategoryType != null) {
                                categoryFolderCache.remove(activeCategoryType);
                                if (activeCategoryType == CategoryItem.Type.NEW_FILES) {
                                    scanNewFilesContent();
                                } else {
                                    scanCategoryContent(activeCategoryType);
                                }
                            }

                            try {
                                if (!deletedPaths.isEmpty()) {
                                    MediaScannerConnection.scanFile(requireContext().getApplicationContext(), deletedPaths.toArray(new String[0]), null, null);
                                }
                            } catch (Exception ignored) {}

                            String successMsg = getString(R.string.toast_delete_multiple_success, finalCount);
                            Toast.makeText(requireContext(), successMsg, Toast.LENGTH_SHORT).show();
                            recyclerCategoryFiles.announceForAccessibility(successMsg);
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
            shareFolderAsZip(selected.get(0));
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

    private void copySelectedItems() {
        List<FileItem> selected = fileAdapter.getSelectedItems();
        if (selected.isEmpty()) return;
        List<File> files = new ArrayList<>();
        for (FileItem item : selected) files.add(item.getFile());
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).copyToClipboard(files, false);
        }
        exitSelectionMode();
        String msg = (files.size() == 1) ?
                getString(R.string.toast_copied, files.get(0).getName()) :
                getString(R.string.toast_copied_multiple, files.size());
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
        recyclerCategoryFiles.announceForAccessibility(msg);
    }

    private void cutSelectedItems() {
        List<FileItem> selected = fileAdapter.getSelectedItems();
        if (selected.isEmpty()) return;
        List<File> files = new ArrayList<>();
        for (FileItem item : selected) files.add(item.getFile());
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).copyToClipboard(files, true);
        }
        exitSelectionMode();
        String msg = (files.size() == 1) ?
                getString(R.string.toast_cut, files.get(0).getName()) :
                getString(R.string.toast_cut_multiple, files.size());
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
        recyclerCategoryFiles.announceForAccessibility(msg);
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
        recyclerCategoryFiles.announceForAccessibility(msg);
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

    @Override
    public void onActionEdit(FileItem item) {
        EditActionHelper.showEditSubmenu(requireContext(), item, new EditActionHelper.DialogCallback() {
            @Override
            public void onActiveDialogChanged(Dialog dialog) {
                setActiveDialog(dialog);
            }

            @Override
            public void onFilesChanged() {
                if (activeCategoryFolder != null) {
                    refreshFolderFilesFromDisk(activeCategoryFolder);
                } else if (activeCategoryType != null) {
                    categoryFolderCache.remove(activeCategoryType);
                    if (activeCategoryType == CategoryItem.Type.NEW_FILES) {
                        scanNewFilesContent();
                    } else {
                        scanCategoryContent(activeCategoryType);
                    }
                }
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
                if (activeCategoryFolder != null) {
                    refreshFolderFilesFromDisk(activeCategoryFolder);
                } else if (activeCategoryType != null) {
                    categoryFolderCache.remove(activeCategoryType);
                    if (activeCategoryType == CategoryItem.Type.NEW_FILES) {
                        scanNewFilesContent();
                    } else {
                        scanCategoryContent(activeCategoryType);
                    }
                }
            }
        });
    }

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
        fileAdapter.notifyDataSetChanged();
    }

    @Override
    public void onActionCopy(FileItem item) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).copyToClipboard(java.util.Collections.singletonList(item.getFile()), false);
        }
        String msg = getString(R.string.toast_copied, item.getName());
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
        recyclerCategoryFiles.announceForAccessibility(msg);
    }

    @Override
    public void onActionCut(FileItem item) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).copyToClipboard(java.util.Collections.singletonList(item.getFile()), true);
        }
        String msg = getString(R.string.toast_cut, item.getName());
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
        recyclerCategoryFiles.announceForAccessibility(msg);
    }

    @Override
    public void onActionRename(FileItem item) {
        showRenameDialog(item);
    }

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

        new MaterialAlertDialogBuilder(requireContext())
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
                        recyclerCategoryFiles.announceForAccessibility(successMsg);
                        refreshCurrentCategory();
                    } else {
                        String failMsg = getString(R.string.toast_rename_failed, item.getName());
                        Toast.makeText(requireContext(), failMsg, Toast.LENGTH_SHORT).show();
                        recyclerCategoryFiles.announceForAccessibility(failMsg);
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void refreshCurrentCategory() {
        if (activeCategoryFolder != null) {
            refreshFolderFilesFromDisk(activeCategoryFolder);
        } else if (activeCategoryType != null) {
            categoryFolderCache.remove(activeCategoryType);
            if (activeCategoryType == CategoryItem.Type.NEW_FILES) {
                scanNewFilesContent();
            } else {
                scanCategoryContent(activeCategoryType);
            }
        }
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
                refreshCurrentCategory();
                String announcement = getString(R.string.toast_batch_rename_success, successCount);
                recyclerCategoryFiles.announceForAccessibility(announcement);
            });
        }
    }

    @Override
    public void onActionShare(FileItem item) {
        if (item.isDirectory()) {
            shareFolderAsZip(item);
        } else {
            shareFile(item.getFile(), item.getExtension(), item.getName());
        }
    }

    @Override
    public void onActionDelete(FileItem item) {
        Dialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_delete_title)
                .setMessage(getString(R.string.dialog_delete_message, item.getName()))
                .setPositiveButton(R.string.btn_delete, (d, w) -> {
                    progressCategory.setVisibility(View.VISIBLE);
                    executorService.execute(() -> {
                        boolean deleted = deleteRecursively(item.getFile());
                        mainHandler.post(() -> {
                            if (!isAdded()) return;
                            progressCategory.setVisibility(View.GONE);
                            if (deleted) {
                                if (activeCategoryFolder != null) {
                                    refreshFolderFilesFromDisk(activeCategoryFolder);
                                } else if (activeCategoryType != null) {
                                    categoryFolderCache.remove(activeCategoryType);
                                    if (activeCategoryType == CategoryItem.Type.NEW_FILES) {
                                        scanNewFilesContent();
                                    } else {
                                        scanCategoryContent(activeCategoryType);
                                    }
                                }
                                try {
                                    MediaScannerConnection.scanFile(requireContext().getApplicationContext(), new String[]{item.getPath()}, null, null);
                                } catch (Exception ignored) {}
                                String successMsg = getString(R.string.toast_delete_success, item.getName());
                                Toast.makeText(requireContext(), successMsg, Toast.LENGTH_SHORT).show();
                                recyclerCategoryFiles.announceForAccessibility(successMsg);
                            } else {
                                String failMsg = getString(R.string.toast_delete_failed, item.getName());
                                Toast.makeText(requireContext(), failMsg, Toast.LENGTH_SHORT).show();
                                recyclerCategoryFiles.announceForAccessibility(failMsg);
                            }
                        });
                    });
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .create();
        setActiveDialog(dialog);
    }

    private void shareFolderAsZip(FileItem folderItem) {
        String progressMsg = getString(R.string.zipping_folder_progress, folderItem.getName());
        Toast.makeText(requireContext(), progressMsg, Toast.LENGTH_SHORT).show();
        recyclerCategoryFiles.announceForAccessibility(progressMsg);
        progressCategory.setVisibility(View.VISIBLE);

        executorService.execute(() -> {
            try {
                File zipFile = new File(requireContext().getCacheDir(), folderItem.getName() + ".zip");
                zipDirectory(folderItem.getFile(), zipFile);

                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    progressCategory.setVisibility(View.GONE);
                    String successMsg = getString(R.string.folder_zipped_success, folderItem.getName());
                    recyclerCategoryFiles.announceForAccessibility(successMsg);
                    shareFile(zipFile, "zip", folderItem.getName() + ".zip");
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    progressCategory.setVisibility(View.GONE);
                    String failMsg = getString(R.string.folder_zip_failed, folderItem.getName());
                    Toast.makeText(requireContext(), failMsg, Toast.LENGTH_SHORT).show();
                    recyclerCategoryFiles.announceForAccessibility(failMsg);
                });
            }
        });
    }

    private void zipDirectory(File sourceFolder, File zipFile) throws IOException {
        if (zipFile.exists()) {
            zipFile.delete();
        }
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile)))) {
            zipSubFolder(zos, sourceFolder, sourceFolder.getAbsolutePath().length() + 1);
        }
    }

    private void zipSubFolder(ZipOutputStream zos, File folder, int basePathLength) throws IOException {
        File[] fileList = folder.listFiles();
        if (fileList == null) return;
        byte[] buffer = new byte[8192];
        for (File file : fileList) {
            if (file.isDirectory()) {
                zipSubFolder(zos, file, basePathLength);
            } else {
                String relativePath = file.getAbsolutePath().substring(basePathLength);
                ZipEntry entry = new ZipEntry(relativePath);
                zos.putNextEntry(entry);
                try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
                    int read;
                    while ((read = bis.read(buffer)) != -1) {
                        zos.write(buffer, 0, read);
                    }
                }
                zos.closeEntry();
            }
        }
    }

    private void openFile(File file) {
        FileOpener.openFile(requireContext(), file, currentVisibleItems);
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
}
