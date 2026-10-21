package com.kts.fileexplorer.fragment;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.app.Activity;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.InputMethodManager;
import android.text.format.Formatter;
import com.kts.fileexplorer.model.SortMode;

import androidx.appcompat.app.AlertDialog;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.kts.fileexplorer.R;
import com.kts.fileexplorer.adapter.NetworkFileAdapter;
import com.kts.fileexplorer.adapter.NetworkServerAdapter;
import com.kts.fileexplorer.cloud.CloudStorageManager;
import com.kts.fileexplorer.cloud.DocumentFileTreeHelper;
import com.kts.fileexplorer.cloud.GoogleDriveService;
import com.kts.fileexplorer.network.FtpClientHelper;
import com.kts.fileexplorer.network.LanScanner;
import com.kts.fileexplorer.network.NetworkItem;
import com.kts.fileexplorer.network.NetworkServerInfo;
import com.kts.fileexplorer.network.NetworkServerManager;
import com.kts.fileexplorer.network.SmbClientHelper;
import com.kts.fileexplorer.network.WirelessTransferServer;
import com.kts.fileexplorer.util.AppClipboard;
import com.kts.fileexplorer.util.FavoriteManager;
import com.kts.fileexplorer.util.FileOpener;
import com.kts.fileexplorer.util.FilePropertiesDialog;
import androidx.core.content.FileProvider;

import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * OnlineFragment for local network file sharing and PC integration:
 * 1. Access Computer Folders (SMB / Windows Share & FTP):
 *    Browse PC files, download/copy from PC to phone, upload/paste from phone to PC.
 * 2. Access Phone from PC (Wi-Fi Transfer Server):
 *    Built-in wireless web server and FTP server to browse and manage phone from computer.
 */
public class OnlineFragment extends Fragment {

    // Views - Main Server List & PC Access
    private View layoutServerListContainer;
    private MaterialButton btnScanNetwork;
    private ImageButton btnAddServer;
    private TextView tvPcAccessStatus;
    private MaterialButton btnToggleServer;
    private LinearLayout layoutServerDetails;
    private TextView tvBrowserUrl;
    private TextView tvExplorerUrl;
    private MaterialButton btnCopyFtpUrl;
    private MaterialButton btnCopyIpOnly;
    private MaterialButton btnCopyBrowserUrl;
    private LinearLayout layoutScanningProgress;
    private RecyclerView recyclerServers;
    private TextView tvEmptyServers;

    // Views - Cloud Storage
    private MaterialCardView cardGoogleDrive;
    private TextView tvGdriveTitle;
    private TextView tvGdriveStatus;
    private MaterialButton btnGdriveAction;
    private ImageButton btnGdriveMenu;
    private MaterialButton btnAddCloud;

    // Views - Remote Computer File Browser
    private View layoutNetworkBrowser;
    private View layoutBrowserHeader;
    private ImageButton btnBrowserBack;
    private TextView tvBrowserServerName;
    private TextView tvBrowserCurrentPath;
    private ImageButton btnBrowserSearch;
    private ImageButton btnBrowserSort;
    private ImageButton btnBrowserNewFolder;
    private ImageButton btnBrowserUpload;
    private View layoutBrowserSearch;
    private EditText etBrowserSearch;
    private ImageButton btnBrowserCloseSearch;
    private ImageButton btnBrowserClearSearch;
    private ProgressBar progressNetwork;
    private RecyclerView recyclerNetworkFiles;
    private LinearLayout layoutBrowserEmpty;
    private TextView tvBrowserEmpty;
    private MaterialButton btnBrowserLogin;
    private ExtendedFloatingActionButton fabPasteToPc;
    private FloatingActionButton fabCancelPasteOnline;
    private boolean isBrowserSearchActive = false;
    private String currentBrowserSearchQuery = "";
    private final List<NetworkItem> filteredNetworkItems = new ArrayList<>();
    private com.kts.fileexplorer.model.SortMode currentBrowserSortMode = com.kts.fileexplorer.model.SortMode.NAME_ASC;

    // Transfer Progress UI
    private MaterialCardView cardMiniTransferProgress;
    private ProgressBar pbMiniTransfer;
    private TextView tvMiniTransferTitle;
    private TextView tvMiniTransferPercent;
    private AlertDialog transferProgressDialog;
    private TextView tvTransferTitle;
    private TextView tvTransferFileName;
    private TextView tvTransferDetails;
    private ProgressBar pbTransferProgress;
    private final AtomicBoolean isTransferCancelled = new AtomicBoolean(false);
    private boolean isTransferRunning = false;
    private boolean isTransferInBackground = false;
    private long lastSpeedCalculationTime = 0;
    private long lastSpeedBytes = 0;
    private String currentTransferSpeed = "";

    // Multi-Selection Views
    private LinearLayout selectionBottomBarOnline;
    private LinearLayout selectionHeaderLayoutOnline;
    private ImageButton btnCloseSelectionOnline;
    private TextView tvSelectionCountOnline;
    private ImageButton btnSelectAllOnline;
    private View btnShareSelectionOnline;
    private View btnCutSelectionOnline;
    private View btnCopySelectionOnline;
    private View btnDeleteSelectionOnline;
    private View btnFavoriteSelectionOnline;

    // Adapters & Managers
    private NetworkServerAdapter serverAdapter;
    private NetworkFileAdapter fileAdapter;
    private NetworkServerManager serverManager;
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Remote Browser Navigation State
    private NetworkServerInfo activeServer;
    private String currentSmbShare = ""; // for SMB
    private String currentFolderPath = ""; // relative path inside share or FTP
    private boolean isBrowsingSharesList = false; // SMB top-level shares
    private final List<NetworkItem> currentNetworkItems = new ArrayList<>();

    // Cloud Navigation State
    private Uri currentCloudFolderUri;
    private final List<Uri> cloudNavigationStack = new ArrayList<>();
    private final List<String> cloudPathStack = new ArrayList<>();
    private String currentCloudFolderId = "root";
    private final List<String> cloudFolderIdStack = new ArrayList<>();

    private OnBackPressedCallback backPressedCallback;
    private ActivityResultLauncher<String> filePickerLauncher;
    private ActivityResultLauncher<Intent> googleDrivePickerLauncher;
    private ActivityResultLauncher<Intent> googleSignInLauncher;
    private ActivityResultLauncher<Intent> googleConsentLauncher;

    public OnlineFragment() {
        // Required empty constructor
    }

    public static OnlineFragment newInstance() {
        return new OnlineFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        serverManager = NetworkServerManager.getInstance(requireContext());

        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetMultipleContents(),
                uris -> {
                    if (uris != null && !uris.isEmpty()) {
                        uploadSelectedUrisToPc(uris);
                    }
                }
        );

        googleDrivePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            CloudStorageManager.getInstance(requireContext()).saveGoogleDrive(uri, "Google Drive");
                            Toast.makeText(requireContext(), R.string.toast_gdrive_connected, Toast.LENGTH_SHORT).show();
                            updateCloudStorageUI();
                            openGoogleDriveBrowser();
                        }
                    }
                }
        );

        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getData() != null) {
                        Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                        handleGoogleSignInResult(task);
                    } else {
                        Toast.makeText(requireContext(), "Sign in cancelled", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        googleConsentLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(requireContext());
                        if (account != null) {
                            fetchTokenAndConnect(account);
                        }
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_online, container, false);
        initViews(view);
        setupListeners();
        setupBackNavigation();
        loadSavedServers();
        updatePcAccessUI();
        updateCloudStorageUI();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        updatePcAccessUI();
        updateCloudStorageUI();
        updatePasteToPcFabVisibility();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }

    private void initViews(View view) {
        // Main Server List
        layoutServerListContainer = view.findViewById(R.id.layout_server_list_container);
        btnScanNetwork = view.findViewById(R.id.btn_scan_network);
        btnAddServer = view.findViewById(R.id.btn_add_server);
        tvPcAccessStatus = view.findViewById(R.id.tv_pc_access_status);
        btnToggleServer = view.findViewById(R.id.btn_toggle_server);
        layoutServerDetails = view.findViewById(R.id.layout_server_details);
        tvBrowserUrl = view.findViewById(R.id.tv_browser_url);
        tvExplorerUrl = view.findViewById(R.id.tv_explorer_url);
        btnCopyFtpUrl = view.findViewById(R.id.btn_copy_ftp_url);
        btnCopyIpOnly = view.findViewById(R.id.btn_copy_ip_only);
        btnCopyBrowserUrl = view.findViewById(R.id.btn_copy_browser_url);
        layoutScanningProgress = view.findViewById(R.id.layout_scanning_progress);
        recyclerServers = view.findViewById(R.id.recycler_servers);
        tvEmptyServers = view.findViewById(R.id.tv_empty_servers);

        // Cloud Storage
        cardGoogleDrive = view.findViewById(R.id.card_google_drive);
        tvGdriveTitle = view.findViewById(R.id.tv_gdrive_title);
        tvGdriveStatus = view.findViewById(R.id.tv_gdrive_status);
        btnGdriveAction = view.findViewById(R.id.btn_gdrive_action);
        btnGdriveMenu = view.findViewById(R.id.btn_gdrive_menu);
        btnAddCloud = view.findViewById(R.id.btn_add_cloud);

        // Remote Computer File Browser
        layoutNetworkBrowser = view.findViewById(R.id.layout_network_browser);
        layoutBrowserHeader = view.findViewById(R.id.layout_browser_header);
        btnBrowserBack = view.findViewById(R.id.btn_browser_back);
        tvBrowserServerName = view.findViewById(R.id.tv_browser_server_name);
        tvBrowserCurrentPath = view.findViewById(R.id.tv_browser_current_path);
        btnBrowserSearch = view.findViewById(R.id.btn_browser_search);
        btnBrowserSort = view.findViewById(R.id.btn_browser_sort);
        btnBrowserNewFolder = view.findViewById(R.id.btn_browser_new_folder);
        btnBrowserUpload = view.findViewById(R.id.btn_browser_upload);
        layoutBrowserSearch = view.findViewById(R.id.layout_browser_search);
        etBrowserSearch = view.findViewById(R.id.et_browser_search);
        btnBrowserCloseSearch = view.findViewById(R.id.btn_browser_close_search);
        btnBrowserClearSearch = view.findViewById(R.id.btn_browser_clear_search);
        progressNetwork = view.findViewById(R.id.progress_network);
        recyclerNetworkFiles = view.findViewById(R.id.recycler_network_files);
        layoutBrowserEmpty = view.findViewById(R.id.layout_browser_empty);
        tvBrowserEmpty = view.findViewById(R.id.tv_browser_empty);
        btnBrowserLogin = view.findViewById(R.id.btn_browser_login);
        fabPasteToPc = view.findViewById(R.id.fab_paste_to_pc);
        fabCancelPasteOnline = view.findViewById(R.id.fab_cancel_paste_online);
        cardMiniTransferProgress = view.findViewById(R.id.card_mini_transfer_progress);
        pbMiniTransfer = view.findViewById(R.id.pb_mini_transfer);
        tvMiniTransferTitle = view.findViewById(R.id.tv_mini_transfer_title);
        tvMiniTransferPercent = view.findViewById(R.id.tv_mini_transfer_percent);
        if (cardMiniTransferProgress != null) {
            cardMiniTransferProgress.setOnClickListener(v -> {
                if (isTransferRunning && !isTransferCancelled.get()) {
                    isTransferInBackground = false;
                    cardMiniTransferProgress.setVisibility(View.GONE);
                    if (transferProgressDialog != null && !transferProgressDialog.isShowing()) {
                        transferProgressDialog.show();
                    }
                }
            });
        }

        // Multi-Selection Views
        selectionBottomBarOnline = view.findViewById(R.id.selection_bottom_bar_online);
        selectionHeaderLayoutOnline = view.findViewById(R.id.selection_header_layout_online);
        btnCloseSelectionOnline = view.findViewById(R.id.btn_close_selection_online);
        tvSelectionCountOnline = view.findViewById(R.id.tv_selection_count_online);
        btnSelectAllOnline = view.findViewById(R.id.btn_select_all_online);
        btnShareSelectionOnline = view.findViewById(R.id.btn_share_selection_online);
        btnCutSelectionOnline = view.findViewById(R.id.btn_cut_selection_online);
        btnCopySelectionOnline = view.findViewById(R.id.btn_copy_selection_online);
        btnDeleteSelectionOnline = view.findViewById(R.id.btn_delete_selection_online);
        btnFavoriteSelectionOnline = view.findViewById(R.id.btn_favorite_selection_online);

        // Setup Server RecyclerView
        serverAdapter = new NetworkServerAdapter(new NetworkServerAdapter.OnServerClickListener() {
            @Override
            public void onServerClick(NetworkServerInfo server) {
                openServerBrowser(server);
            }

            @Override
            public void onServerMenuClick(NetworkServerInfo server, View anchor) {
                showServerContextMenu(server, anchor);
            }
        });
        recyclerServers.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerServers.setAdapter(serverAdapter);
        recyclerServers.addItemDecoration(new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));

        // Setup Remote File RecyclerView
        fileAdapter = new NetworkFileAdapter(new NetworkFileAdapter.OnNetworkItemClickListener() {
            @Override
            public void onItemClick(NetworkItem item, int position) {
                handleNetworkItemClick(item);
            }

            @Override
            public void onItemMenuClick(NetworkItem item, View anchor, int position) {
                showNetworkItemContextMenu(item, anchor, position);
            }

            @Override
            public void onItemAction(NetworkItem item, int actionId, int position) {
                handleNetworkItemAction(item, actionId, position);
            }

            @Override
            public void onSelectionCountChanged(int count) {
                updateSelectionTitle(count);
                if (count == 0 && fileAdapter.isSelectionMode()) {
                    exitSelectionMode();
                }
            }
        });
        recyclerNetworkFiles.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerNetworkFiles.setAdapter(fileAdapter);
        recyclerNetworkFiles.addItemDecoration(new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));
    }

    private void setupListeners() {
        btnToggleServer.setOnClickListener(v -> togglePcAccessServer());
        if (btnCopyFtpUrl != null) {
            btnCopyFtpUrl.setOnClickListener(v -> copyFtpUrlOnly());
        }
        if (btnCopyIpOnly != null) {
            btnCopyIpOnly.setOnClickListener(v -> copyIpOnly());
        }
        if (btnCopyBrowserUrl != null) {
            btnCopyBrowserUrl.setOnClickListener(v -> copyBrowserUrlOnly());
        }
        if (btnBrowserLogin != null) {
            btnBrowserLogin.setOnClickListener(v -> {
                if (activeServer != null) {
                    showAddServerDialog(activeServer);
                }
            });
        }

        // Selection listeners
        if (btnCloseSelectionOnline != null) {
            btnCloseSelectionOnline.setOnClickListener(v -> exitSelectionMode());
        }
        if (btnSelectAllOnline != null) {
            btnSelectAllOnline.setOnClickListener(v -> {
                if (fileAdapter == null) return;
                if (fileAdapter.getSelectedPaths().size() == fileAdapter.getItemCount()) {
                    fileAdapter.deselectAll();
                } else {
                    fileAdapter.selectAll();
                }
            });
        }
        if (btnShareSelectionOnline != null) {
            btnShareSelectionOnline.setOnClickListener(v -> shareSelectedItems());
        }
        if (btnCutSelectionOnline != null) {
            btnCutSelectionOnline.setOnClickListener(v -> cutSelectedItems());
        }
        if (btnCopySelectionOnline != null) {
            btnCopySelectionOnline.setOnClickListener(v -> copySelectedItems());
        }
        if (btnDeleteSelectionOnline != null) {
            btnDeleteSelectionOnline.setOnClickListener(v -> deleteSelectedItems());
        }
        if (btnFavoriteSelectionOnline != null) {
            btnFavoriteSelectionOnline.setOnClickListener(v -> favoriteSelectedItems());
        }

        // Cloud Storage listeners
        if (btnGdriveAction != null) {
            btnGdriveAction.setOnClickListener(v -> handleGoogleDriveAction());
        }
        if (cardGoogleDrive != null) {
            cardGoogleDrive.setOnClickListener(v -> handleGoogleDriveAction());
        }
        if (btnGdriveMenu != null) {
            btnGdriveMenu.setOnClickListener(this::showGoogleDriveContextMenu);
        }
        if (btnAddCloud != null) {
            btnAddCloud.setOnClickListener(v -> showAddCloudDialog());
        }

        btnScanNetwork.setOnClickListener(v -> startNetworkScan());
        btnAddServer.setOnClickListener(v -> showAddServerDialog(null));

        btnBrowserBack.setOnClickListener(v -> navigateBrowserBack());
        if (btnBrowserSearch != null) {
            btnBrowserSearch.setOnClickListener(v -> openBrowserSearch());
        }
        if (btnBrowserSort != null) {
            btnBrowserSort.setOnClickListener(v -> showBrowserSortDialog());
        }
        btnBrowserNewFolder.setOnClickListener(v -> showBrowserNewDialog());
        btnBrowserUpload.setOnClickListener(v -> filePickerLauncher.launch("*/*"));

        if (btnBrowserCloseSearch != null) {
            btnBrowserCloseSearch.setOnClickListener(v -> closeBrowserSearch());
        }
        if (btnBrowserClearSearch != null) {
            btnBrowserClearSearch.setOnClickListener(v -> {
                if (etBrowserSearch != null) etBrowserSearch.setText("");
            });
        }
        setupBrowserSearchLogic();

        fabPasteToPc.setOnClickListener(v -> pasteFilesToCurrentRemoteFolder());
        if (fabCancelPasteOnline != null) {
            fabCancelPasteOnline.setOnClickListener(v -> {
                AppClipboard.getInstance().clear();
                updatePasteToPcFabVisibility();
                Toast.makeText(requireContext(), R.string.clipboard_cancelled, Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void setupBackNavigation() {
        backPressedCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                if (fileAdapter != null && fileAdapter.isSelectionMode()) {
                    exitSelectionMode();
                    return;
                }
                if (isBrowserSearchActive) {
                    closeBrowserSearch();
                    return;
                }
                if (layoutNetworkBrowser != null && layoutNetworkBrowser.getVisibility() == View.VISIBLE) {
                    navigateBrowserBack();
                } else {
                    setEnabled(false);
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), backPressedCallback);
    }

    // --- Feature 2: Access Phone from PC (Wi-Fi Transfer Server) ---

    private void togglePcAccessServer() {
        WirelessTransferServer server = WirelessTransferServer.getInstance();
        if (server.isRunning()) {
            server.stopServer();
            Toast.makeText(requireContext(), R.string.server_stopped, Toast.LENGTH_SHORT).show();
        } else {
            try {
                server.startServer(requireContext());
                Toast.makeText(requireContext(), R.string.server_running, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Failed to start server: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
        updatePcAccessUI();
    }

    private void updatePcAccessUI() {
        WirelessTransferServer server = WirelessTransferServer.getInstance();
        if (server.isRunning()) {
            tvPcAccessStatus.setText(R.string.server_running);
            btnToggleServer.setText(R.string.btn_stop_server);
            layoutServerDetails.setVisibility(View.VISIBLE);

            String httpUrl = server.getHttpUrl(requireContext());
            String ftpUrl = server.getFtpUrl(requireContext());

            tvBrowserUrl.setText(getString(R.string.label_browser_url, httpUrl));
            tvExplorerUrl.setText(getString(R.string.label_explorer_url, ftpUrl));
        } else {
            tvPcAccessStatus.setText(R.string.server_stopped);
            btnToggleServer.setText(R.string.btn_start_server);
            layoutServerDetails.setVisibility(View.GONE);
        }
    }

    private void copyFtpUrlOnly() {
        WirelessTransferServer server = WirelessTransferServer.getInstance();
        if (!server.isRunning()) return;

        String ftpUrl = server.getFtpUrl(requireContext());
        copyAddressToClipboard("FTP Address", ftpUrl);
    }

    private void copyBrowserUrlOnly() {
        WirelessTransferServer server = WirelessTransferServer.getInstance();
        if (!server.isRunning()) return;

        String httpUrl = server.getHttpUrl(requireContext());
        copyAddressToClipboard("Browser Address", httpUrl);
    }

    private void copyIpOnly() {
        String ip = LanScanner.getLocalIpAddress(requireContext());
        if (!TextUtils.isEmpty(ip)) {
            copyAddressToClipboard("IP Address", ip);
        } else {
            Toast.makeText(requireContext(), "Wi-Fi not connected", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyAddressToClipboard(String label, String address) {
        ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(label, address));
            Toast.makeText(requireContext(), R.string.toast_address_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isAuthError(String errorMsg) {
        if (errorMsg == null) return false;
        String lower = errorMsg.toLowerCase();
        return lower.contains("login failed") ||
                lower.contains("logon_failure") ||
                lower.contains("logon failure") ||
                lower.contains("anonymous") ||
                lower.contains("access_denied") ||
                lower.contains("access denied") ||
                lower.contains("authentication") ||
                lower.contains("credentials") ||
                lower.contains("unauthorized") ||
                lower.contains("530");
    }

    // --- Feature 1: Computers / Network Share Management ---

    private void loadSavedServers() {
        List<NetworkServerInfo> list = serverManager.getServers();
        serverAdapter.updateList(list);
        tvEmptyServers.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void startNetworkScan() {
        layoutScanningProgress.setVisibility(View.VISIBLE);
        Toast.makeText(requireContext(), R.string.scanning_network, Toast.LENGTH_SHORT).show();

        LanScanner.scanLocalNetwork(requireContext(), new LanScanner.ScanCallback() {
            @Override
            public void onDeviceFound(NetworkServerInfo server) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    serverManager.saveServer(server);
                    loadSavedServers();
                });
            }

            @Override
            public void onScanComplete(List<NetworkServerInfo> foundServers) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    layoutScanningProgress.setVisibility(View.GONE);
                    String msg = getString(R.string.scan_complete, foundServers.size());
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                    loadSavedServers();
                });
            }
        });
    }

    private void showAddServerDialog(@Nullable NetworkServerInfo existingServer) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_network_server, null);
        RadioGroup rgType = dialogView.findViewById(R.id.rg_server_type);
        RadioButton rbSmb = dialogView.findViewById(R.id.rb_type_smb);
        RadioButton rbFtp = dialogView.findViewById(R.id.rb_type_ftp);
        TextInputEditText etName = dialogView.findViewById(R.id.et_server_name);
        TextInputEditText etHost = dialogView.findViewById(R.id.et_server_host);
        TextInputEditText etPort = dialogView.findViewById(R.id.et_server_port);
        TextInputEditText etUser = dialogView.findViewById(R.id.et_server_user);
        TextInputEditText etPass = dialogView.findViewById(R.id.et_server_pass);
        TextInputEditText etDomain = dialogView.findViewById(R.id.et_server_domain);
        TextInputEditText etShare = dialogView.findViewById(R.id.et_server_share);
        TextInputLayout tilDomain = dialogView.findViewById(R.id.til_server_domain);
        TextInputLayout tilShare = dialogView.findViewById(R.id.til_server_share);

        rgType.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isSmb = checkedId == R.id.rb_type_smb;
            etPort.setText(isSmb ? "445" : "21");
            tilDomain.setVisibility(isSmb ? View.VISIBLE : View.GONE);
            tilShare.setVisibility(isSmb ? View.VISIBLE : View.GONE);
        });

        if (existingServer != null) {
            etName.setText(existingServer.getName());
            etHost.setText(existingServer.getHost());
            etPort.setText(String.valueOf(existingServer.getPort()));
            etUser.setText(existingServer.getUsername());
            etPass.setText(existingServer.getPassword());
            etDomain.setText(existingServer.getDomain());
            etShare.setText(existingServer.getShareName());
            if (NetworkServerInfo.TYPE_FTP.equalsIgnoreCase(existingServer.getType())) {
                rbFtp.setChecked(true);
            } else {
                rbSmb.setChecked(true);
            }
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(existingServer != null ? "Edit Computer" : getString(R.string.dialog_add_computer_title))
                .setView(dialogView)
                .setPositiveButton(R.string.btn_save_connect, (dialog, which) -> {
                    String name = etName.getText() != null ? etName.getText().toString().trim() : "";
                    String host = etHost.getText() != null ? etHost.getText().toString().trim() : "";
                    String portStr = etPort.getText() != null ? etPort.getText().toString().trim() : "";
                    String user = etUser.getText() != null ? etUser.getText().toString().trim() : "";
                    String pass = etPass.getText() != null ? etPass.getText().toString().trim() : "";
                    String domain = etDomain.getText() != null ? etDomain.getText().toString().trim() : "WORKGROUP";
                    String share = etShare.getText() != null ? etShare.getText().toString().trim() : "";
                    String type = rbFtp.isChecked() ? NetworkServerInfo.TYPE_FTP : NetworkServerInfo.TYPE_SMB;

                    if (TextUtils.isEmpty(host)) {
                        Toast.makeText(requireContext(), "Computer IP or Hostname is required", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    int port = 445;
                    try {
                        port = Integer.parseInt(portStr);
                    } catch (Exception ignored) {}

                    NetworkServerInfo server = existingServer != null ? existingServer : new NetworkServerInfo();
                    server.setName(name);
                    server.setHost(host);
                    server.setPort(port);
                    server.setType(type);
                    server.setUsername(user);
                    server.setPassword(pass);
                    server.setDomain(domain);
                    server.setShareName(share);

                    serverManager.saveServer(server);
                    loadSavedServers();
                    openServerBrowser(server);
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void showServerContextMenu(NetworkServerInfo server, View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenu().add(0, 1, 0, "Connect");
        popup.getMenu().add(0, 2, 1, "Edit");
        popup.getMenu().add(0, 4, 2, "Copy IP Address");
        popup.getMenu().add(0, 3, 3, "Delete");

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                openServerBrowser(server);
            } else if (item.getItemId() == 2) {
                showAddServerDialog(server);
            } else if (item.getItemId() == 4) {
                copyAddressToClipboard("IP Address", server.getHost());
            } else if (item.getItemId() == 3) {
                serverManager.deleteServer(server.getId());
                loadSavedServers();
                Toast.makeText(requireContext(), "Computer removed", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        popup.show();
    }

    // --- Feature 3: Cloud Storage Management (Google Drive) ---

    private void updateCloudStorageUI() {
        if (!isAdded()) return;
        CloudStorageManager csm = CloudStorageManager.getInstance(requireContext());
        if (csm.isGoogleDriveConnected()) {
            tvGdriveStatus.setText(getString(R.string.gdrive_connected, csm.getGoogleDriveDisplayName()));
            btnGdriveAction.setText(R.string.btn_open);
            btnGdriveMenu.setVisibility(View.VISIBLE);
        } else {
            tvGdriveStatus.setText(R.string.gdrive_not_connected);
            btnGdriveAction.setText(R.string.btn_connect);
            btnGdriveMenu.setVisibility(View.GONE);
        }
    }

    private void handleGoogleDriveAction() {
        CloudStorageManager csm = CloudStorageManager.getInstance(requireContext());
        if (csm.isGoogleDriveConnected()) {
            openGoogleDriveBrowser();
        } else {
            showConnectChoiceDialog();
        }
    }

    private void showConnectChoiceDialog() {
        String[] options = new String[]{
                "Google Account (Sign in directly)",
                "Folder Selector (Android SAF System Picker)"
        };
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Connect Google Drive")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        signInWithGoogle();
                    } else {
                        launchGoogleDrivePicker();
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void signInWithGoogle() {
        try {
            GoogleSignInClient client = GoogleDriveService.getGoogleSignInClient(requireContext());
            client.signOut().addOnCompleteListener(task -> {
                Intent signInIntent = client.getSignInIntent();
                googleSignInLauncher.launch(signInIntent);
            });
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Error starting Google Sign-In: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void handleGoogleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            if (account != null) {
                fetchTokenAndConnect(account);
            }
        } catch (ApiException e) {
            int statusCode = e.getStatusCode();
            Log.e("OnlineFragment", "Google Sign-In failed, code=" + statusCode, e);
            showGoogleConfigHelpDialog(statusCode, e.getMessage());
        }
    }

    private void fetchTokenAndConnect(GoogleSignInAccount account) {
        String email = account.getEmail();
        String displayName = account.getDisplayName();
        progressNetwork.setVisibility(View.VISIBLE);
        Toast.makeText(requireContext(), "Connecting Google Drive: " + email, Toast.LENGTH_SHORT).show();

        executorService.execute(() -> {
            try {
                String token = GoogleDriveService.getInstance(requireContext()).fetchAccessToken(account);
                CloudStorageManager.getInstance(requireContext()).saveGoogleDriveAccount(email, displayName, token);
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    progressNetwork.setVisibility(View.GONE);
                    updateCloudStorageUI();
                    Toast.makeText(requireContext(), "Google Drive connected: " + (displayName != null ? displayName : email), Toast.LENGTH_SHORT).show();
                    openGoogleDriveBrowser();
                });
            } catch (UserRecoverableAuthException ure) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    progressNetwork.setVisibility(View.GONE);
                    googleConsentLauncher.launch(ure.getIntent());
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    progressNetwork.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), "Auth token error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showGoogleConfigHelpDialog(int statusCode, String rawMessage) {
        String statusName = GoogleSignInStatusCodes.getStatusCodeString(statusCode);
        String details = "Google Sign-In Error Code: " + statusCode + " (" + statusName + ")\n\n"
                + "To enable direct Google Sign-In on Android, this app's SHA-1 fingerprint must be added to your Google Cloud Console project:\n\n"
                + "Package Name:\ncom.kts.fileexplorer\n\n"
                + "SHA-1 Certificate Fingerprint:\nD8:B8:E4:2A:59:95:64:E9:C4:50:DF:9A:E3:ED:D5:20:16:E6:06:1C\n\n"
                + "If you haven't set up Google Cloud Console yet, you can also use Android's folder selector as a temporary option.";

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Google Drive Setup Required")
                .setMessage(details)
                .setPositiveButton("Copy SHA-1 & Info", (dialog, which) -> {
                    ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        String clip = "Package Name: com.kts.fileexplorer\nSHA-1: D8:B8:E4:2A:59:95:64:E9:C4:50:DF:9A:E3:ED:D5:20:16:E6:06:1C";
                        cm.setPrimaryClip(ClipData.newPlainText("Google OAuth Config", clip));
                        Toast.makeText(requireContext(), "SHA-1 and Package Name copied to clipboard!", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("Use Folder Picker", (dialog, which) -> {
                    launchGoogleDrivePicker();
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void launchGoogleDrivePicker() {
        Toast.makeText(requireContext(), R.string.cloud_gdrive_instruction, Toast.LENGTH_LONG).show();
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        googleDrivePickerLauncher.launch(intent);
    }

    private void showGoogleDriveContextMenu(View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        popup.getMenu().add(0, 1, 0, "Open Google Drive");
        popup.getMenu().add(0, 2, 1, "Connect via Google Sign-In");
        popup.getMenu().add(0, 3, 2, "Connect via Folder Selector (SAF)");
        popup.getMenu().add(0, 4, 3, "Disconnect");

        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                openGoogleDriveBrowser();
            } else if (item.getItemId() == 2) {
                signInWithGoogle();
            } else if (item.getItemId() == 3) {
                launchGoogleDrivePicker();
            } else if (item.getItemId() == 4) {
                CloudStorageManager.getInstance(requireContext()).disconnectGoogleDrive();
                updateCloudStorageUI();
                Toast.makeText(requireContext(), R.string.toast_gdrive_disconnected, Toast.LENGTH_SHORT).show();
            }
            return true;
        });
        popup.show();
    }

    private void showAddCloudDialog() {
        String[] cloudOptions = new String[]{"Google Drive (Sign in)", "Google Drive (Folder Picker SAF)", "Add WebDAV / Nextcloud Server"};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_connect_cloud_title)
                .setItems(cloudOptions, (dialog, which) -> {
                    if (which == 0) {
                        signInWithGoogle();
                    } else if (which == 1) {
                        launchGoogleDrivePicker();
                    } else {
                        showAddServerDialog(null);
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void openGoogleDriveBrowser() {
        CloudStorageManager csm = CloudStorageManager.getInstance(requireContext());
        activeServer = csm.getGoogleDriveServerInfo();

        if (csm.isOAuthConnected()) {
            currentCloudFolderId = "root";
            cloudFolderIdStack.clear();
            cloudFolderIdStack.add("root");
            cloudPathStack.clear();
            cloudPathStack.add("Google Drive");
        } else {
            Uri rootUri = csm.getGoogleDriveUri();
            if (rootUri == null) {
                signInWithGoogle();
                return;
            }
            cloudNavigationStack.clear();
            cloudPathStack.clear();
            currentCloudFolderUri = rootUri;
            cloudNavigationStack.add(rootUri);
            cloudPathStack.add(activeServer.getName());
        }

        openServerBrowser(activeServer);
    }

    private void loadCloudDirectory() {
        isBrowsingSharesList = false;
        progressNetwork.setVisibility(View.VISIBLE);
        if (layoutBrowserEmpty != null) layoutBrowserEmpty.setVisibility(View.GONE);
        if (btnBrowserLogin != null) btnBrowserLogin.setVisibility(View.GONE);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cloudPathStack.size(); i++) {
            if (i > 0) sb.append(" / ");
            sb.append(cloudPathStack.get(i));
        }
        tvBrowserCurrentPath.setText(sb.toString());

        CloudStorageManager csm = CloudStorageManager.getInstance(requireContext());
        if (csm.isOAuthConnected()) {
            String token = csm.getGoogleDriveToken();
            executorService.execute(() -> {
                try {
                    List<NetworkItem> items = GoogleDriveService.getInstance(requireContext())
                            .listFiles(currentCloudFolderId, token, activeServer);
                    mainHandler.post(() -> {
                        if (!isAdded()) return;
                        progressNetwork.setVisibility(View.GONE);
                        currentNetworkItems.clear();
                        currentNetworkItems.addAll(items);
                        applyBrowserSortingAndDisplay();
                    });
                } catch (Exception e) {
                    mainHandler.post(() -> {
                        if (!isAdded()) return;
                        progressNetwork.setVisibility(View.GONE);
                        String errorMsg = e.getMessage() != null ? e.getMessage() : "Failed to load Google Drive";
                        layoutBrowserEmpty.setVisibility(View.VISIBLE);
                        if (errorMsg.contains("401") || errorMsg.contains("UNAUTHENTICATED") || errorMsg.contains("Invalid Credentials")) {
                            tvBrowserEmpty.setText("Google Drive session expired. Please reconnect.");
                            if (btnBrowserLogin != null) {
                                btnBrowserLogin.setText("Reconnect Google Drive");
                                btnBrowserLogin.setVisibility(View.VISIBLE);
                                btnBrowserLogin.setOnClickListener(v -> signInWithGoogle());
                            }
                        } else {
                            tvBrowserEmpty.setText(errorMsg);
                        }
                        Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show();
                    });
                }
            });
        } else {
            executorService.execute(() -> {
                try {
                    List<NetworkItem> items = DocumentFileTreeHelper.listFiles(requireContext(), currentCloudFolderUri, activeServer);
                    mainHandler.post(() -> {
                        if (!isAdded()) return;
                        progressNetwork.setVisibility(View.GONE);
                        currentNetworkItems.clear();
                        currentNetworkItems.addAll(items);
                        applyBrowserSortingAndDisplay();
                    });
                } catch (Exception e) {
                    mainHandler.post(() -> {
                        if (!isAdded()) return;
                        progressNetwork.setVisibility(View.GONE);
                        String errorMsg = e.getMessage() != null ? e.getMessage() : "Failed to load Google Drive folder";
                        layoutBrowserEmpty.setVisibility(View.VISIBLE);
                        tvBrowserEmpty.setText(errorMsg);
                        Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_LONG).show();
                    });
                }
            });
        }
    }

    // --- Remote File Browser Navigation ---

    private void openServerBrowser(NetworkServerInfo server) {
        activeServer = server;
        layoutServerListContainer.setVisibility(View.GONE);
        layoutNetworkBrowser.setVisibility(View.VISIBLE);
        backPressedCallback.setEnabled(true);

        if (layoutBrowserEmpty != null) layoutBrowserEmpty.setVisibility(View.GONE);
        if (btnBrowserLogin != null) btnBrowserLogin.setVisibility(View.GONE);

        if (NetworkServerInfo.TYPE_GOOGLE_DRIVE.equalsIgnoreCase(server.getType())) {
            tvBrowserServerName.setText(server.getName());
            loadCloudDirectory();
            updatePasteToPcFabVisibility();
            return;
        }

        tvBrowserServerName.setText(server.getName() + " (" + server.getHost() + ")");

        if (NetworkServerInfo.TYPE_SMB.equalsIgnoreCase(server.getType())) {
            if (!TextUtils.isEmpty(server.getShareName())) {
                currentSmbShare = server.getShareName();
                currentFolderPath = "";
                isBrowsingSharesList = false;
                loadRemoteDirectory();
            } else {
                loadSmbSharesList();
            }
        } else {
            // FTP starts from root /
            currentFolderPath = "/";
            isBrowsingSharesList = false;
            loadRemoteDirectory();
        }

        updatePasteToPcFabVisibility();
    }

    private void navigateBrowserBack() {
        if (fileAdapter != null && fileAdapter.isSelectionMode()) {
            exitSelectionMode();
            return;
        }
        if (activeServer == null) {
            closeServerBrowser();
            return;
        }

        if (NetworkServerInfo.TYPE_GOOGLE_DRIVE.equalsIgnoreCase(activeServer.getType())) {
            CloudStorageManager csm = CloudStorageManager.getInstance(requireContext());
            if (csm.isOAuthConnected()) {
                if (cloudFolderIdStack.size() > 1) {
                    cloudFolderIdStack.remove(cloudFolderIdStack.size() - 1);
                    cloudPathStack.remove(cloudPathStack.size() - 1);
                    currentCloudFolderId = cloudFolderIdStack.get(cloudFolderIdStack.size() - 1);
                    loadCloudDirectory();
                } else {
                    closeServerBrowser();
                }
            } else {
                if (cloudNavigationStack.size() > 1) {
                    cloudNavigationStack.remove(cloudNavigationStack.size() - 1);
                    cloudPathStack.remove(cloudPathStack.size() - 1);
                    currentCloudFolderUri = cloudNavigationStack.get(cloudNavigationStack.size() - 1);
                    loadCloudDirectory();
                } else {
                    closeServerBrowser();
                }
            }
            return;
        }

        if (NetworkServerInfo.TYPE_SMB.equalsIgnoreCase(activeServer.getType())) {
            if (isBrowsingSharesList) {
                closeServerBrowser();
            } else if (!TextUtils.isEmpty(currentFolderPath)) {
                // Navigate up inside folder
                int lastSep = currentFolderPath.lastIndexOf('\\');
                if (lastSep > 0) {
                    currentFolderPath = currentFolderPath.substring(0, lastSep);
                } else {
                    currentFolderPath = "";
                }
                loadRemoteDirectory();
            } else if (TextUtils.isEmpty(activeServer.getShareName())) {
                // Return to shares list
                loadSmbSharesList();
            } else {
                closeServerBrowser();
            }
        } else {
            // FTP
            if ("/".equals(currentFolderPath) || currentFolderPath.isEmpty()) {
                closeServerBrowser();
            } else {
                int lastSlash = currentFolderPath.lastIndexOf('/');
                if (lastSlash > 0) {
                    currentFolderPath = currentFolderPath.substring(0, lastSlash);
                } else {
                    currentFolderPath = "/";
                }
                loadRemoteDirectory();
            }
        }
    }

    private void closeServerBrowser() {
        if (fileAdapter != null && fileAdapter.isSelectionMode()) {
            exitSelectionMode();
        }
        layoutNetworkBrowser.setVisibility(View.GONE);
        layoutServerListContainer.setVisibility(View.VISIBLE);
        activeServer = null;
        backPressedCallback.setEnabled(false);
        updatePasteToPcFabVisibility();
    }

    private void loadSmbSharesList() {
        isBrowsingSharesList = true;
        currentSmbShare = "";
        currentFolderPath = "";
        tvBrowserCurrentPath.setText("Select Shared Folder");
        progressNetwork.setVisibility(View.VISIBLE);
        if (layoutBrowserEmpty != null) layoutBrowserEmpty.setVisibility(View.GONE);
        if (btnBrowserLogin != null) btnBrowserLogin.setVisibility(View.GONE);

        executorService.execute(() -> {
            try {
                List<String> shares = SmbClientHelper.listShares(activeServer);
                List<NetworkItem> items = new ArrayList<>();
                for (String share : shares) {
                    items.add(new NetworkItem(share, "", share, true, 0, 0, activeServer));
                }
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    progressNetwork.setVisibility(View.GONE);
                    currentNetworkItems.clear();
                    currentNetworkItems.addAll(items);
                    fileAdapter.updateList(items);
                    if (items.isEmpty()) {
                        layoutBrowserEmpty.setVisibility(View.VISIBLE);
                        tvBrowserEmpty.setText("No shared folders found on computer");
                        btnBrowserLogin.setVisibility(View.GONE);
                    } else {
                        layoutBrowserEmpty.setVisibility(View.GONE);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    progressNetwork.setVisibility(View.GONE);
                    String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
                    layoutBrowserEmpty.setVisibility(View.VISIBLE);

                    if (isAuthError(errorMsg)) {
                        Toast.makeText(requireContext(), "Login failed: Use your Windows Account Password (not PIN)", Toast.LENGTH_LONG).show();
                        tvBrowserEmpty.setText(R.string.login_required_desc);
                        btnBrowserLogin.setVisibility(View.VISIBLE);
                        showAddServerDialog(activeServer);
                    } else {
                        Toast.makeText(requireContext(), "Failed to connect to computer: " + errorMsg, Toast.LENGTH_LONG).show();
                        tvBrowserEmpty.setText("Connection failed: " + errorMsg);
                        btnBrowserLogin.setVisibility(View.GONE);
                    }
                });
            }
        });
    }

    private void loadRemoteDirectory() {
        if (activeServer != null && NetworkServerInfo.TYPE_GOOGLE_DRIVE.equalsIgnoreCase(activeServer.getType())) {
            loadCloudDirectory();
            return;
        }

        isBrowsingSharesList = false;
        progressNetwork.setVisibility(View.VISIBLE);
        if (layoutBrowserEmpty != null) layoutBrowserEmpty.setVisibility(View.GONE);
        if (btnBrowserLogin != null) btnBrowserLogin.setVisibility(View.GONE);

        String displayPath;
        if (NetworkServerInfo.TYPE_SMB.equalsIgnoreCase(activeServer.getType())) {
            displayPath = currentSmbShare + (currentFolderPath.isEmpty() ? "" : " / " + currentFolderPath.replace('\\', '/'));
        } else {
            displayPath = currentFolderPath;
        }
        tvBrowserCurrentPath.setText(displayPath);

        executorService.execute(() -> {
            try {
                List<NetworkItem> items;
                if (NetworkServerInfo.TYPE_SMB.equalsIgnoreCase(activeServer.getType())) {
                    items = SmbClientHelper.listFiles(activeServer, currentSmbShare, currentFolderPath);
                } else {
                    items = FtpClientHelper.listFiles(activeServer, currentFolderPath);
                }

                // Sort: directories first, then alphabetically
                Collections.sort(items, (a, b) -> {
                    if (a.isDirectory() && !b.isDirectory()) return -1;
                    if (!a.isDirectory() && b.isDirectory()) return 1;
                    return a.getName().compareToIgnoreCase(b.getName());
                });

                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    progressNetwork.setVisibility(View.GONE);
                    currentNetworkItems.clear();
                    currentNetworkItems.addAll(items);
                    applyBrowserSortingAndDisplay();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    progressNetwork.setVisibility(View.GONE);
                    String errorMsg = e.getMessage() != null ? e.getMessage() : "Unknown error";
                    layoutBrowserEmpty.setVisibility(View.VISIBLE);

                    if (isAuthError(errorMsg)) {
                        Toast.makeText(requireContext(), "Login failed: Use your Windows Account Password (not PIN)", Toast.LENGTH_LONG).show();
                        tvBrowserEmpty.setText(R.string.login_required_desc);
                        btnBrowserLogin.setVisibility(View.VISIBLE);
                        showAddServerDialog(activeServer);
                    } else {
                        Toast.makeText(requireContext(), "Error: " + errorMsg, Toast.LENGTH_LONG).show();
                        tvBrowserEmpty.setText("Error: " + errorMsg);
                        btnBrowserLogin.setVisibility(View.GONE);
                    }
                });
            }
        });
    }

    private void handleNetworkItemClick(NetworkItem item) {
        if (activeServer != null && NetworkServerInfo.TYPE_GOOGLE_DRIVE.equalsIgnoreCase(activeServer.getType())) {
            CloudStorageManager csm = CloudStorageManager.getInstance(requireContext());
            if (csm.isOAuthConnected()) {
                if (item.isDirectory()) {
                    cloudFolderIdStack.add(item.getPath());
                    cloudPathStack.add(item.getName());
                    currentCloudFolderId = item.getPath();
                    loadCloudDirectory();
                } else {
                    showDownloadOrOpenDialog(item);
                }
            } else {
                if (item.isDirectory()) {
                    Uri folderUri = Uri.parse(item.getPath());
                    cloudNavigationStack.add(folderUri);
                    cloudPathStack.add(item.getName());
                    currentCloudFolderUri = folderUri;
                    loadCloudDirectory();
                } else {
                    showDownloadOrOpenDialog(item);
                }
            }
            return;
        }

        if (isBrowsingSharesList) {
            // Tapped a share
            currentSmbShare = item.getName();
            currentFolderPath = "";
            loadRemoteDirectory();
            return;
        }

        if (item.isDirectory()) {
            if (NetworkServerInfo.TYPE_SMB.equalsIgnoreCase(activeServer.getType())) {
                currentFolderPath = item.getPath();
            } else {
                currentFolderPath = item.getPath();
            }
            loadRemoteDirectory();
        } else {
            // Tapped a file -> Download and Open
            showDownloadOrOpenDialog(item);
        }
    }

    private void showDownloadOrOpenDialog(NetworkItem item) {
        String[] options = new String[]{"Open / Stream", "Download to Phone"};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(item.getName())
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        downloadAndOpenFile(item);
                    } else {
                        downloadFileToPhone(item, false);
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void showNetworkItemContextMenu(NetworkItem item, View anchor, int position) {
        Context ctx = requireContext();
        boolean isFav = FavoriteManager.getInstance(ctx).isFavorite(item.getPath());
        List<String> options = new ArrayList<>();
        List<Integer> actionIds = new ArrayList<>();

        if (!item.isDirectory()) {
            options.add("Open / Stream");
            actionIds.add(NetworkFileAdapter.ACTION_OPEN);
            options.add("Download to Phone");
            actionIds.add(NetworkFileAdapter.ACTION_DOWNLOAD);
        }
        options.add(getString(R.string.action_select));
        actionIds.add(NetworkFileAdapter.ACTION_SELECT);
        options.add(getString(R.string.action_copy));
        actionIds.add(NetworkFileAdapter.ACTION_COPY);
        options.add(getString(R.string.action_cut));
        actionIds.add(NetworkFileAdapter.ACTION_CUT);
        if (!item.isDirectory()) {
            options.add(getString(R.string.action_share));
            actionIds.add(NetworkFileAdapter.ACTION_SHARE);
        }
        options.add(isFav ? getString(R.string.action_remove_favorite) : getString(R.string.action_add_favorite));
        actionIds.add(NetworkFileAdapter.ACTION_FAVORITE);
        options.add(getString(R.string.action_delete));
        actionIds.add(NetworkFileAdapter.ACTION_DELETE);
        options.add(getString(R.string.action_properties));
        actionIds.add(NetworkFileAdapter.ACTION_PROPERTIES);

        String subtitle = item.isDirectory() ? getString(R.string.prop_type_folder) : item.getFormattedSize();
        new MaterialAlertDialogBuilder(ctx)
                .setTitle(item.getName() + " (" + subtitle + ")")
                .setItems(options.toArray(new String[0]), (d, which) -> {
                    int action = actionIds.get(which);
                    if (action == NetworkFileAdapter.ACTION_OPEN) {
                        downloadAndOpenFile(item);
                    } else {
                        handleNetworkItemAction(item, action, position);
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void handleNetworkItemAction(NetworkItem item, int actionId, int position) {
        if (actionId == NetworkFileAdapter.ACTION_SELECT) {
            enterSelectionMode(position);
        } else if (actionId == NetworkFileAdapter.ACTION_PROPERTIES) {
            FilePropertiesDialog.show(requireContext(), item, null);
        } else if (actionId == NetworkFileAdapter.ACTION_COPY) {
            AppClipboard.getInstance().setNetworkItems(Collections.singletonList(item), false);
            Toast.makeText(requireContext(), getString(R.string.toast_copied, item.getName()), Toast.LENGTH_SHORT).show();
            updatePasteToPcFabVisibility();
        } else if (actionId == NetworkFileAdapter.ACTION_CUT) {
            AppClipboard.getInstance().setNetworkItems(Collections.singletonList(item), true);
            Toast.makeText(requireContext(), getString(R.string.toast_cut, item.getName()), Toast.LENGTH_SHORT).show();
            updatePasteToPcFabVisibility();
        } else if (actionId == NetworkFileAdapter.ACTION_DOWNLOAD) {
            downloadFileToPhone(item, false);
        } else if (actionId == NetworkFileAdapter.ACTION_SHARE) {
            shareNetworkItem(item);
        } else if (actionId == NetworkFileAdapter.ACTION_FAVORITE) {
            toggleFavoriteNetworkItem(item);
        } else if (actionId == NetworkFileAdapter.ACTION_DELETE) {
            confirmDeleteNetworkItem(item);
        }
    }

    // --- Multi-Selection Mode for Network & Cloud Files ---

    public void enterSelectionMode(int initialPosition) {
        if (isBrowserSearchActive) {
            closeBrowserSearch();
        }
        if (fileAdapter != null) {
            fileAdapter.setSelectionMode(true);
            fileAdapter.toggleSelection(initialPosition);
        }
        if (selectionBottomBarOnline != null) {
            selectionBottomBarOnline.setVisibility(View.VISIBLE);
        }
        if (fabPasteToPc != null) {
            fabPasteToPc.setVisibility(View.GONE);
        }
        updateSelectionTitle(1);
        updateBackCallbackState();
    }

    public void exitSelectionMode() {
        if (fileAdapter != null) {
            fileAdapter.setSelectionMode(false);
        }
        if (selectionBottomBarOnline != null) {
            selectionBottomBarOnline.setVisibility(View.GONE);
        }
        updatePasteToPcFabVisibility();
        updateBackCallbackState();
        if (recyclerNetworkFiles != null) {
            recyclerNetworkFiles.announceForAccessibility(getString(R.string.btn_exit_selection));
        }
    }

    private void updateSelectionTitle(int count) {
        String title = getString(R.string.selection_mode_title, count);
        if (tvSelectionCountOnline != null) {
            tvSelectionCountOnline.setText(title);
            tvSelectionCountOnline.setContentDescription(title);
        }
    }

    private void copySelectedItems() {
        if (fileAdapter == null) return;
        List<NetworkItem> selected = fileAdapter.getSelectedItems();
        if (selected.isEmpty()) return;

        AppClipboard.getInstance().setNetworkItems(selected, false);
        exitSelectionMode();

        String msg;
        if (selected.size() == 1) {
            msg = getString(R.string.toast_copied, selected.get(0).getName());
        } else {
            msg = getString(R.string.toast_copied_multiple, selected.size());
        }
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
        updatePasteToPcFabVisibility();
        if (recyclerNetworkFiles != null) {
            recyclerNetworkFiles.announceForAccessibility(msg);
        }
    }

    private void cutSelectedItems() {
        if (fileAdapter == null) return;
        List<NetworkItem> selected = fileAdapter.getSelectedItems();
        if (selected.isEmpty()) return;

        AppClipboard.getInstance().setNetworkItems(selected, true);
        exitSelectionMode();

        String msg;
        if (selected.size() == 1) {
            msg = getString(R.string.toast_cut, selected.get(0).getName());
        } else {
            msg = getString(R.string.toast_cut_multiple, selected.size());
        }
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
        updatePasteToPcFabVisibility();
        if (recyclerNetworkFiles != null) {
            recyclerNetworkFiles.announceForAccessibility(msg);
        }
    }

    private void favoriteSelectedItems() {
        if (fileAdapter == null) return;
        List<NetworkItem> selected = fileAdapter.getSelectedItems();
        if (selected.isEmpty()) return;

        FavoriteManager favMgr = FavoriteManager.getInstance(requireContext());
        for (NetworkItem item : selected) {
            if (!favMgr.isFavorite(item.getPath())) {
                favMgr.toggleFavorite(item);
            }
        }
        exitSelectionMode();
        String msg = getString(R.string.toast_favorite_multiple, selected.size());
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
        fileAdapter.notifyDataSetChanged();
        if (recyclerNetworkFiles != null) {
            recyclerNetworkFiles.announceForAccessibility(msg);
        }
    }

    private void shareSelectedItems() {
        if (fileAdapter == null) return;
        List<NetworkItem> selected = fileAdapter.getSelectedItems();
        if (selected.isEmpty()) return;

        List<NetworkItem> filesToShare = new ArrayList<>();
        for (NetworkItem it : selected) {
            if (!it.isDirectory()) {
                filesToShare.add(it);
            }
        }

        if (filesToShare.isEmpty()) {
            Toast.makeText(requireContext(), "Folders cannot be shared directly", Toast.LENGTH_SHORT).show();
            return;
        }

        if (filesToShare.size() == 1) {
            shareNetworkItem(filesToShare.get(0));
            exitSelectionMode();
            return;
        }

        progressNetwork.setVisibility(View.VISIBLE);
        Toast.makeText(requireContext(), "Preparing " + filesToShare.size() + " files for sharing...", Toast.LENGTH_SHORT).show();

        executorService.execute(() -> {
            ArrayList<Uri> uris = new ArrayList<>();
            CloudStorageManager csm = CloudStorageManager.getInstance(requireContext());
            for (NetworkItem item : filesToShare) {
                try {
                    File cacheFile = new File(requireContext().getCacheDir(), item.getName());
                    if (activeServer != null && NetworkServerInfo.TYPE_GOOGLE_DRIVE.equalsIgnoreCase(activeServer.getType())) {
                        if (item.getPath() != null && item.getPath().startsWith("content://")) {
                            DocumentFileTreeHelper.downloadFile(requireContext(), Uri.parse(item.getPath()), cacheFile, null);
                        } else {
                            GoogleDriveService.getInstance(requireContext())
                                    .downloadFile(item.getPath(), cacheFile, csm.getGoogleDriveToken());
                        }
                    } else if (activeServer != null && NetworkServerInfo.TYPE_SMB.equalsIgnoreCase(activeServer.getType())) {
                        SmbClientHelper.downloadFile(activeServer, item, cacheFile, null);
                    } else if (activeServer != null) {
                        FtpClientHelper.downloadFile(activeServer, item, cacheFile, null);
                    }
                    if (cacheFile.exists() && cacheFile.length() > 0) {
                        Uri contentUri = FileProvider.getUriForFile(
                                requireContext(),
                                requireContext().getPackageName() + ".provider",
                                cacheFile
                        );
                        uris.add(contentUri);
                    }
                } catch (Exception ignored) {}
            }

            mainHandler.post(() -> {
                if (!isAdded()) return;
                progressNetwork.setVisibility(View.GONE);
                exitSelectionMode();
                if (!uris.isEmpty()) {
                    try {
                        Intent shareIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
                        shareIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
                        shareIntent.setType("*/*");
                        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(shareIntent, "Share " + uris.size() + " files"));
                    } catch (Exception e) {
                        Toast.makeText(requireContext(), "Failed to share: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(requireContext(), "Could not prepare files for sharing", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void deleteSelectedItems() {
        if (fileAdapter == null) return;
        List<NetworkItem> selected = fileAdapter.getSelectedItems();
        if (selected.isEmpty()) return;

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_delete_title)
                .setMessage(getString(R.string.delete_selected_confirmation, selected.size()))
                .setPositiveButton(R.string.btn_delete, (d, which) -> {
                    progressNetwork.setVisibility(View.VISIBLE);
                    executorService.execute(() -> {
                        int success = 0;
                        CloudStorageManager csm = CloudStorageManager.getInstance(requireContext());
                        for (NetworkItem item : selected) {
                            try {
                                if (activeServer != null && NetworkServerInfo.TYPE_GOOGLE_DRIVE.equalsIgnoreCase(activeServer.getType())) {
                                    if (csm.isOAuthConnected()) {
                                        if (GoogleDriveService.getInstance(requireContext()).deleteItem(item.getPath(), csm.getGoogleDriveToken())) {
                                            success++;
                                        }
                                    } else {
                                        if (DocumentFileTreeHelper.deleteItem(requireContext(), Uri.parse(item.getPath()))) {
                                            success++;
                                        }
                                    }
                                } else if (activeServer != null && NetworkServerInfo.TYPE_SMB.equalsIgnoreCase(activeServer.getType())) {
                                    SmbClientHelper.deleteItem(activeServer, item);
                                    success++;
                                } else if (activeServer != null) {
                                    FtpClientHelper.deleteItem(activeServer, item);
                                    success++;
                                }
                            } catch (Exception ignored) {}
                        }
                        int finalSuccess = success;
                        mainHandler.post(() -> {
                            if (!isAdded()) return;
                            progressNetwork.setVisibility(View.GONE);
                            exitSelectionMode();
                            Toast.makeText(requireContext(), "Deleted " + finalSuccess + " items", Toast.LENGTH_SHORT).show();
                            loadRemoteDirectory();
                        });
                    });
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void shareNetworkItem(NetworkItem item) {
        if (item.isDirectory()) {
            Toast.makeText(requireContext(), "Folders cannot be shared directly", Toast.LENGTH_SHORT).show();
            return;
        }
        progressNetwork.setVisibility(View.VISIBLE);
        Toast.makeText(requireContext(), "Preparing " + item.getName() + " for sharing...", Toast.LENGTH_SHORT).show();

        executorService.execute(() -> {
            try {
                File cacheFile = new File(requireContext().getCacheDir(), item.getName());
                CloudStorageManager csm = CloudStorageManager.getInstance(requireContext());
                if (activeServer != null && NetworkServerInfo.TYPE_GOOGLE_DRIVE.equalsIgnoreCase(activeServer.getType())) {
                    if (item.getPath() != null && item.getPath().startsWith("content://")) {
                        DocumentFileTreeHelper.downloadFile(requireContext(), Uri.parse(item.getPath()), cacheFile, null);
                    } else {
                        GoogleDriveService.getInstance(requireContext())
                                .downloadFile(item.getPath(), cacheFile, csm.getGoogleDriveToken());
                    }
                } else if (activeServer != null && NetworkServerInfo.TYPE_SMB.equalsIgnoreCase(activeServer.getType())) {
                    SmbClientHelper.downloadFile(activeServer, item, cacheFile, null);
                } else if (activeServer != null) {
                    FtpClientHelper.downloadFile(activeServer, item, cacheFile, null);
                }

                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    progressNetwork.setVisibility(View.GONE);
                    if (cacheFile.exists() && cacheFile.length() > 0) {
                        try {
                            Uri contentUri = FileProvider.getUriForFile(
                                    requireContext(),
                                    requireContext().getPackageName() + ".provider",
                                    cacheFile
                            );
                            Intent shareIntent = new Intent(Intent.ACTION_SEND);
                            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                            shareIntent.setType("*/*");
                            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(Intent.createChooser(shareIntent, "Share " + item.getName()));
                        } catch (Exception e) {
                            Toast.makeText(requireContext(), "Failed to share: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Toast.makeText(requireContext(), "Could not prepare file for sharing", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    progressNetwork.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), "Share error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void toggleFavoriteNetworkItem(NetworkItem item) {
        boolean isFav = FavoriteManager.getInstance(requireContext()).toggleFavorite(item);
        String msg = isFav ?
                getString(R.string.toast_added_favorite, item.getName()) :
                getString(R.string.toast_removed_favorite, item.getName());
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
        fileAdapter.notifyDataSetChanged();
    }

    // --- Transfer from PC to Phone (Download / Copy) ---

    private void downloadFileToPhone(NetworkItem item, boolean openAfterDownload) {
        File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File destFile = new File(downloadsDir, item.getName());

        String startMsg = getString(R.string.toast_downloading, item.getName());
        Toast.makeText(requireContext(), startMsg, Toast.LENGTH_SHORT).show();
        progressNetwork.setVisibility(View.VISIBLE);

        executorService.execute(() -> {
            try {
                if (NetworkServerInfo.TYPE_GOOGLE_DRIVE.equalsIgnoreCase(activeServer.getType())) {
                    CloudStorageManager csm = CloudStorageManager.getInstance(requireContext());
                    if (csm.isOAuthConnected()) {
                        GoogleDriveService.getInstance(requireContext())
                                .downloadFile(item.getPath(), destFile, csm.getGoogleDriveToken());
                    } else {
                        DocumentFileTreeHelper.downloadFile(requireContext(), Uri.parse(item.getPath()), destFile, null);
                    }
                } else if (NetworkServerInfo.TYPE_SMB.equalsIgnoreCase(activeServer.getType())) {
                    SmbClientHelper.downloadFile(activeServer, item, destFile, null);
                } else {
                    FtpClientHelper.downloadFile(activeServer, item, destFile, null);
                }

                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    progressNetwork.setVisibility(View.GONE);
                    String successMsg = getString(R.string.toast_download_complete, item.getName());
                    Toast.makeText(requireContext(), successMsg, Toast.LENGTH_LONG).show();

                    if (openAfterDownload && destFile.exists()) {
                        FileOpener.openFile(requireContext(), destFile, Collections.emptyList());
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    progressNetwork.setVisibility(View.GONE);
                    Toast.makeText(requireContext(), "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void downloadAndOpenFile(NetworkItem item) {
        downloadFileToPhone(item, true);
    }

    // --- Transfer from Phone to PC / Cloud (Upload / Paste) ---

    private void updatePasteToPcFabVisibility() {
        if (layoutNetworkBrowser != null && layoutNetworkBrowser.getVisibility() == View.VISIBLE && !isBrowsingSharesList) {
            if (AppClipboard.getInstance().hasLocalFiles()) {
                int count = AppClipboard.getInstance().getLocalFiles().size();
                String text = "Paste (" + count + ")";
                fabPasteToPc.setText(text);
                fabPasteToPc.setContentDescription(text);
                fabPasteToPc.setVisibility(View.VISIBLE);
                if (fabCancelPasteOnline != null) {
                    fabCancelPasteOnline.setVisibility(View.VISIBLE);
                }
                return;
            } else if (AppClipboard.getInstance().hasNetworkItems()) {
                int count = AppClipboard.getInstance().getNetworkItems().size();
                String text = "Paste (" + count + ")";
                fabPasteToPc.setText(text);
                fabPasteToPc.setContentDescription(text);
                fabPasteToPc.setVisibility(View.VISIBLE);
                if (fabCancelPasteOnline != null) {
                    fabCancelPasteOnline.setVisibility(View.VISIBLE);
                }
                return;
            }
        }
        if (fabPasteToPc != null) fabPasteToPc.setVisibility(View.GONE);
        if (fabCancelPasteOnline != null) fabCancelPasteOnline.setVisibility(View.GONE);
    }

    private void pasteFilesToCurrentRemoteFolder() {
        if (activeServer == null || isBrowsingSharesList) return;

        if (AppClipboard.getInstance().hasLocalFiles()) {
            pastePhoneFilesToPc();
            return;
        }

        if (AppClipboard.getInstance().hasNetworkItems()) {
            pasteNetworkItemsToCurrentRemoteFolder();
            return;
        }
    }

    private void pasteNetworkItemsToCurrentRemoteFolder() {
        List<NetworkItem> itemsToPaste = AppClipboard.getInstance().getNetworkItems();
        if (itemsToPaste.isEmpty() || activeServer == null || isBrowsingSharesList) return;

        progressNetwork.setVisibility(View.VISIBLE);
        Toast.makeText(requireContext(), "Pasting " + itemsToPaste.size() + " items...", Toast.LENGTH_SHORT).show();

        executorService.execute(() -> {
            int successCount = 0;
            CloudStorageManager csm = CloudStorageManager.getInstance(requireContext());
            for (NetworkItem item : itemsToPaste) {
                File tempFile = new File(requireContext().getCacheDir(), item.getName());
                try {
                    // 1. Download item to temp file
                    if (item.getServerInfo() != null && NetworkServerInfo.TYPE_GOOGLE_DRIVE.equalsIgnoreCase(item.getServerInfo().getType())) {
                        if (csm.isOAuthConnected()) {
                            GoogleDriveService.getInstance(requireContext())
                                    .downloadFile(item.getPath(), tempFile, csm.getGoogleDriveToken());
                        } else {
                            DocumentFileTreeHelper.downloadFile(requireContext(), Uri.parse(item.getPath()), tempFile, null);
                        }
                    } else if (item.getServerInfo() != null && NetworkServerInfo.TYPE_SMB.equalsIgnoreCase(item.getServerInfo().getType())) {
                        SmbClientHelper.downloadFile(item.getServerInfo(), item, tempFile, null);
                    } else if (item.getServerInfo() != null) {
                        FtpClientHelper.downloadFile(item.getServerInfo(), item, tempFile, null);
                    }

                    // 2. Upload temp file to destination current folder
                    if (NetworkServerInfo.TYPE_GOOGLE_DRIVE.equalsIgnoreCase(activeServer.getType())) {
                        if (csm.isOAuthConnected()) {
                            GoogleDriveService.getInstance(requireContext())
                                    .uploadFile(currentCloudFolderId, tempFile, csm.getGoogleDriveToken());
                        } else {
                            DocumentFileTreeHelper.uploadFile(requireContext(), currentCloudFolderUri, tempFile, null);
                        }
                    } else if (NetworkServerInfo.TYPE_SMB.equalsIgnoreCase(activeServer.getType())) {
                        SmbClientHelper.uploadFile(activeServer, currentSmbShare, currentFolderPath, tempFile, null);
                    } else {
                        FtpClientHelper.uploadFile(activeServer, currentFolderPath, tempFile, null);
                    }

                    // 3. Delete source if cut
                    if (AppClipboard.getInstance().isCut()) {
                        try {
                            if (item.getServerInfo() != null && NetworkServerInfo.TYPE_GOOGLE_DRIVE.equalsIgnoreCase(item.getServerInfo().getType())) {
                                if (csm.isOAuthConnected()) {
                                    GoogleDriveService.getInstance(requireContext()).deleteItem(item.getPath(), csm.getGoogleDriveToken());
                                } else {
                                    DocumentFileTreeHelper.deleteItem(requireContext(), Uri.parse(item.getPath()));
                                }
                            } else if (item.getServerInfo() != null && NetworkServerInfo.TYPE_SMB.equalsIgnoreCase(item.getServerInfo().getType())) {
                                SmbClientHelper.deleteItem(item.getServerInfo(), item);
                            } else if (item.getServerInfo() != null) {
                                FtpClientHelper.deleteItem(item.getServerInfo(), item);
                            }
                        } catch (Exception ignored) {}
                    }

                    tempFile.delete();
                    successCount++;
                } catch (Exception e) {
                    tempFile.delete();
                }
            }

            final int finalSuccess = successCount;
            mainHandler.post(() -> {
                if (!isAdded()) return;
                progressNetwork.setVisibility(View.GONE);
                if (finalSuccess > 0) {
                    String msg = finalSuccess + " items pasted successfully";
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                    if (AppClipboard.getInstance().isCut()) {
                        AppClipboard.getInstance().clear();
                    }
                    updatePasteToPcFabVisibility();
                    loadRemoteDirectory();
                } else {
                    Toast.makeText(requireContext(), "Failed to paste items", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    private void collectAllFiles(List<File> inputList, List<File> outFiles, long[] outTotalBytes) {
        if (inputList == null) return;
        for (File f : inputList) {
            if (f == null || !f.exists()) continue;
            if (f.isDirectory()) {
                File[] children = f.listFiles();
                if (children != null) {
                    collectAllFiles(Arrays.asList(children), outFiles, outTotalBytes);
                }
            } else {
                outFiles.add(f);
                outTotalBytes[0] += f.length();
            }
        }
    }

    private void showTransferProgressDialog(int totalFiles, long totalBytes) {
        if (!isAdded()) return;
        dismissTransferProgressUI();

        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_transfer_progress, null);
        tvTransferTitle = dialogView.findViewById(R.id.tv_transfer_title);
        tvTransferFileName = dialogView.findViewById(R.id.tv_transfer_filename);
        tvTransferDetails = dialogView.findViewById(R.id.tv_transfer_details);
        pbTransferProgress = dialogView.findViewById(R.id.pb_transfer_progress);
        MaterialButton btnBackground = dialogView.findViewById(R.id.btn_transfer_background);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btn_transfer_cancel);

        String targetName = activeServer != null ? activeServer.getName() : "Remote Storage";
        tvTransferTitle.setText("Pasting to " + targetName);
        tvTransferFileName.setText("Preparing transfer...");
        pbTransferProgress.setProgress(0);
        tvTransferDetails.setText("0% • 0 B / " + Formatter.formatFileSize(requireContext(), totalBytes));

        lastSpeedCalculationTime = System.currentTimeMillis();
        lastSpeedBytes = 0;
        currentTransferSpeed = "";

        btnBackground.setOnClickListener(v -> {
            isTransferInBackground = true;
            if (transferProgressDialog != null && transferProgressDialog.isShowing()) {
                transferProgressDialog.dismiss();
            }
            if (cardMiniTransferProgress != null) {
                cardMiniTransferProgress.setVisibility(View.VISIBLE);
            }
        });

        btnCancel.setOnClickListener(v -> {
            isTransferCancelled.set(true);
            dismissTransferProgressUI();
            Toast.makeText(requireContext(), R.string.paste_cancelled, Toast.LENGTH_SHORT).show();
        });

        transferProgressDialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .setCancelable(false)
                .create();

        transferProgressDialog.show();
    }

    private void dismissTransferProgressUI() {
        if (transferProgressDialog != null && transferProgressDialog.isShowing()) {
            transferProgressDialog.dismiss();
        }
        if (cardMiniTransferProgress != null) {
            cardMiniTransferProgress.setVisibility(View.GONE);
        }
    }

    private void postTransferProgress(String fileName, int fileIndex, int totalFiles, long currentBytes, long totalBytes, int percent) {
        long now = System.currentTimeMillis();
        long elapsedSinceSpeed = now - lastSpeedCalculationTime;
        if (elapsedSinceSpeed >= 600 || currentTransferSpeed.isEmpty()) {
            if (elapsedSinceSpeed > 0 && currentBytes >= lastSpeedBytes) {
                long bytesDiff = currentBytes - lastSpeedBytes;
                double bytesPerSec = (bytesDiff * 1000.0) / elapsedSinceSpeed;
                if (bytesPerSec > 0) {
                    Context ctx = getContext();
                    if (ctx != null) {
                        currentTransferSpeed = Formatter.formatFileSize(ctx, (long) bytesPerSec) + "/s";
                    }
                }
            }
            lastSpeedCalculationTime = now;
            lastSpeedBytes = currentBytes;
        }

        mainHandler.post(() -> {
            if (!isAdded() || !isTransferRunning || isTransferCancelled.get()) return;

            Context ctx = getContext();
            if (ctx == null) return;

            String speedText = currentTransferSpeed.isEmpty() ? "" : " • " + currentTransferSpeed;

            if (isTransferInBackground) {
                if (cardMiniTransferProgress != null) {
                    cardMiniTransferProgress.setVisibility(View.VISIBLE);
                    if (tvMiniTransferPercent != null) {
                        tvMiniTransferPercent.setText(percent + "%" + speedText);
                    }
                    if (tvMiniTransferTitle != null) {
                        tvMiniTransferTitle.setText("Pasting (" + fileIndex + "/" + totalFiles + ")");
                    }
                    cardMiniTransferProgress.setContentDescription("Pasting in background: " + percent + " percent" + (currentTransferSpeed.isEmpty() ? "" : " at " + currentTransferSpeed) + ". Tap to view details.");
                }
            } else {
                if (pbTransferProgress != null) {
                    pbTransferProgress.setProgress(percent);
                }
                if (tvTransferFileName != null) {
                    tvTransferFileName.setText(fileName + " (" + fileIndex + "/" + totalFiles + ")");
                }
                if (tvTransferDetails != null) {
                    String formattedCurrent = Formatter.formatFileSize(ctx, currentBytes);
                    String formattedTotal = Formatter.formatFileSize(ctx, totalBytes);
                    tvTransferDetails.setText(percent + "% • " + formattedCurrent + " / " + formattedTotal + speedText);
                }
            }
        });
    }

    private void uploadFileOrDirectoryWithProgress(
            File file,
            String targetFolderId,
            Uri targetFolderUri,
            String smbFolder,
            String ftpFolder,
            long[] transferredBytesSoFar,
            int[] currentFileIndex,
            int totalFiles,
            long totalBytesAllFiles
    ) throws Exception {
        if (isTransferCancelled.get() || file == null || !file.exists()) return;

        if (!file.isDirectory()) {
            currentFileIndex[0]++;
            long fileLength = file.length();

            GoogleDriveService.UploadProgressListener gdriveListener = (bytesWritten, total) -> {
                long currentOverallBytes = transferredBytesSoFar[0] + bytesWritten;
                int percent = totalBytesAllFiles > 0 ? (int) ((currentOverallBytes * 100) / totalBytesAllFiles) : 0;
                if (percent > 100) percent = 100;
                postTransferProgress(file.getName(), currentFileIndex[0], totalFiles, currentOverallBytes, totalBytesAllFiles, percent);
            };

            if (NetworkServerInfo.TYPE_GOOGLE_DRIVE.equalsIgnoreCase(activeServer.getType())) {
                CloudStorageManager csm = CloudStorageManager.getInstance(requireContext());
                if (csm.isOAuthConnected()) {
                    GoogleDriveService.getInstance(requireContext())
                            .uploadFile(targetFolderId, file, csm.getGoogleDriveToken(), gdriveListener, isTransferCancelled);
                } else {
                    DocumentFileTreeHelper.uploadFile(requireContext(), targetFolderUri, file, (transferred, total) -> {
                        long currentOverallBytes = transferredBytesSoFar[0] + transferred;
                        int percent = totalBytesAllFiles > 0 ? (int) ((currentOverallBytes * 100) / totalBytesAllFiles) : 0;
                        if (percent > 100) percent = 100;
                        postTransferProgress(file.getName(), currentFileIndex[0], totalFiles, currentOverallBytes, totalBytesAllFiles, percent);
                    });
                }
            } else if (NetworkServerInfo.TYPE_SMB.equalsIgnoreCase(activeServer.getType())) {
                SmbClientHelper.uploadFile(activeServer, currentSmbShare, smbFolder, file, (transferred, total) -> {
                    long currentOverallBytes = transferredBytesSoFar[0] + transferred;
                    int percent = totalBytesAllFiles > 0 ? (int) ((currentOverallBytes * 100) / totalBytesAllFiles) : 0;
                    if (percent > 100) percent = 100;
                    postTransferProgress(file.getName(), currentFileIndex[0], totalFiles, currentOverallBytes, totalBytesAllFiles, percent);
                });
            } else {
                FtpClientHelper.uploadFile(activeServer, ftpFolder, file, (transferred, total) -> {
                    long currentOverallBytes = transferredBytesSoFar[0] + transferred;
                    int percent = totalBytesAllFiles > 0 ? (int) ((currentOverallBytes * 100) / totalBytesAllFiles) : 0;
                    if (percent > 100) percent = 100;
                    postTransferProgress(file.getName(), currentFileIndex[0], totalFiles, currentOverallBytes, totalBytesAllFiles, percent);
                });
            }

            transferredBytesSoFar[0] += fileLength;
            int percent = totalBytesAllFiles > 0 ? (int) ((transferredBytesSoFar[0] * 100) / totalBytesAllFiles) : 0;
            if (percent > 100) percent = 100;
            postTransferProgress(file.getName(), currentFileIndex[0], totalFiles, transferredBytesSoFar[0], totalBytesAllFiles, percent);
            return;
        }

        // It is a directory:
        String newFolderId = targetFolderId;
        Uri newFolderUri = targetFolderUri;
        String newSmbFolder = smbFolder;
        String newFtpFolder = ftpFolder;

        if (NetworkServerInfo.TYPE_GOOGLE_DRIVE.equalsIgnoreCase(activeServer.getType())) {
            CloudStorageManager csm = CloudStorageManager.getInstance(requireContext());
            if (csm.isOAuthConnected()) {
                newFolderId = GoogleDriveService.getInstance(requireContext())
                        .createFolderAndGetId(targetFolderId, file.getName(), csm.getGoogleDriveToken());
                if (TextUtils.isEmpty(newFolderId)) return;
            } else {
                androidx.documentfile.provider.DocumentFile parent = androidx.documentfile.provider.DocumentFile.fromTreeUri(requireContext(), targetFolderUri);
                if (parent != null) {
                    androidx.documentfile.provider.DocumentFile newDir = parent.createDirectory(file.getName());
                    if (newDir != null) newFolderUri = newDir.getUri();
                }
            }
        } else if (NetworkServerInfo.TYPE_SMB.equalsIgnoreCase(activeServer.getType())) {
            newSmbFolder = smbFolder.isEmpty() ? file.getName() : smbFolder + "\\" + file.getName();
            SmbClientHelper.createDirectory(activeServer, currentSmbShare, smbFolder, file.getName());
        } else {
            newFtpFolder = "/".equals(ftpFolder) ? "/" + file.getName() : ftpFolder + "/" + file.getName();
            FtpClientHelper.createDirectory(activeServer, ftpFolder, file.getName());
        }

        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                if (isTransferCancelled.get()) return;
                uploadFileOrDirectoryWithProgress(
                        child,
                        newFolderId,
                        newFolderUri,
                        newSmbFolder,
                        newFtpFolder,
                        transferredBytesSoFar,
                        currentFileIndex,
                        totalFiles,
                        totalBytesAllFiles
                );
            }
        }
    }

    private void pastePhoneFilesToPc() {
        List<File> filesToUpload = AppClipboard.getInstance().getLocalFiles();
        if (filesToUpload.isEmpty() || activeServer == null || isBrowsingSharesList) return;

        List<File> allFiles = new ArrayList<>();
        long[] totalBytes = new long[]{0};
        collectAllFiles(filesToUpload, allFiles, totalBytes);
        int totalFilesCount = Math.max(allFiles.size(), filesToUpload.size());

        isTransferCancelled.set(false);
        isTransferRunning = true;
        isTransferInBackground = false;

        showTransferProgressDialog(totalFilesCount, totalBytes[0]);

        executorService.execute(() -> {
            long[] transferredSoFar = new long[]{0};
            int[] currentIdx = new int[]{0};
            boolean completedSuccessfully = true;

            for (File file : filesToUpload) {
                if (isTransferCancelled.get()) {
                    completedSuccessfully = false;
                    break;
                }
                try {
                    uploadFileOrDirectoryWithProgress(
                            file,
                            currentCloudFolderId,
                            currentCloudFolderUri,
                            currentFolderPath,
                            currentFolderPath,
                            transferredSoFar,
                            currentIdx,
                            totalFilesCount,
                            totalBytes[0]
                    );
                } catch (Exception e) {
                    if (isTransferCancelled.get()) {
                        completedSuccessfully = false;
                        break;
                    }
                    Log.e("OnlineFragment", "Upload failed: " + e.getMessage(), e);
                }
            }

            final boolean isCut = AppClipboard.getInstance().isCut();
            final boolean success = completedSuccessfully && !isTransferCancelled.get();

            mainHandler.post(() -> {
                if (!isAdded()) return;
                isTransferRunning = false;
                dismissTransferProgressUI();

                if (isTransferCancelled.get()) {
                    Toast.makeText(requireContext(), R.string.paste_cancelled, Toast.LENGTH_SHORT).show();
                    loadRemoteDirectory();
                    return;
                }

                if (success) {
                    String msg = getString(R.string.toast_paste_pc_success, filesToUpload.size());
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                    if (recyclerNetworkFiles != null) {
                        recyclerNetworkFiles.announceForAccessibility(msg);
                    }
                    if (isCut) {
                        for (File f : filesToUpload) {
                            deleteRecursively(f);
                        }
                        AppClipboard.getInstance().clear();
                    }
                    updatePasteToPcFabVisibility();
                    loadRemoteDirectory();
                } else {
                    Toast.makeText(requireContext(), "Failed to paste some items", Toast.LENGTH_SHORT).show();
                    loadRemoteDirectory();
                }
            });
        });
    }

    private void uploadSelectedUrisToPc(List<Uri> uris) {
        if (uris == null || uris.isEmpty() || activeServer == null || isBrowsingSharesList) return;
        progressNetwork.setVisibility(View.VISIBLE);

        executorService.execute(() -> {
            int successCount = 0;
            for (Uri uri : uris) {
                try {
                    String fileName = getFileNameFromUri(uri);
                    File tempFile = new File(requireContext().getCacheDir(), fileName);
                    try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
                         FileOutputStream out = new FileOutputStream(tempFile)) {
                        byte[] buf = new byte[8192];
                        int r;
                        while ((r = in.read(buf)) > 0) {
                            out.write(buf, 0, r);
                        }
                    }

                    if (NetworkServerInfo.TYPE_GOOGLE_DRIVE.equalsIgnoreCase(activeServer.getType())) {
                        CloudStorageManager csm = CloudStorageManager.getInstance(requireContext());
                        if (csm.isOAuthConnected()) {
                            GoogleDriveService.getInstance(requireContext())
                                    .uploadFile(currentCloudFolderId, tempFile, csm.getGoogleDriveToken());
                        } else {
                            DocumentFileTreeHelper.uploadFile(requireContext(), currentCloudFolderUri, tempFile, null);
                        }
                    } else if (NetworkServerInfo.TYPE_SMB.equalsIgnoreCase(activeServer.getType())) {
                        SmbClientHelper.uploadFile(activeServer, currentSmbShare, currentFolderPath, tempFile, null);
                    } else {
                        FtpClientHelper.uploadFile(activeServer, currentFolderPath, tempFile, null);
                    }
                    tempFile.delete();
                    successCount++;
                } catch (Exception ignored) {}
            }

            final int finalSuccess = successCount;
            mainHandler.post(() -> {
                if (!isAdded()) return;
                progressNetwork.setVisibility(View.GONE);
                if (finalSuccess > 0) {
                    Toast.makeText(requireContext(), "Uploaded " + finalSuccess + " files", Toast.LENGTH_SHORT).show();
                    loadRemoteDirectory();
                } else {
                    Toast.makeText(requireContext(), "Failed to upload files", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private String getFileNameFromUri(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null || name.isEmpty()) {
            name = "file_" + System.currentTimeMillis();
        }
        if (name.contains("/")) {
            name = name.substring(name.lastIndexOf('/') + 1);
        }
        return name;
    }

    private void showCreateFolderOnPcDialog() {
        if (activeServer == null || isBrowsingSharesList) return;

        EditText input = new EditText(requireContext());
        input.setSingleLine(true);
        input.setHint("Folder name");

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_new_folder_pc)
                .setView(input)
                .setPositiveButton(R.string.btn_create, (d, which) -> {
                    String folderName = input.getText().toString().trim();
                    if (TextUtils.isEmpty(folderName)) return;

                    progressNetwork.setVisibility(View.VISIBLE);
                    executorService.execute(() -> {
                        try {
                            if (NetworkServerInfo.TYPE_GOOGLE_DRIVE.equalsIgnoreCase(activeServer.getType())) {
                                CloudStorageManager csm = CloudStorageManager.getInstance(requireContext());
                                if (csm.isOAuthConnected()) {
                                    boolean created = GoogleDriveService.getInstance(requireContext())
                                            .createFolder(currentCloudFolderId, folderName, csm.getGoogleDriveToken());
                                    if (!created) throw new Exception("Could not create folder in Google Drive");
                                } else {
                                    boolean created = DocumentFileTreeHelper.createFolder(requireContext(), currentCloudFolderUri, folderName);
                                    if (!created) throw new Exception("Could not create folder in Google Drive");
                                }
                            } else if (NetworkServerInfo.TYPE_SMB.equalsIgnoreCase(activeServer.getType())) {
                                SmbClientHelper.createDirectory(activeServer, currentSmbShare, currentFolderPath, folderName);
                            } else {
                                FtpClientHelper.createDirectory(activeServer, currentFolderPath, folderName);
                            }
                            mainHandler.post(() -> {
                                if (!isAdded()) return;
                                progressNetwork.setVisibility(View.GONE);
                                Toast.makeText(requireContext(), "Folder created successfully", Toast.LENGTH_SHORT).show();
                                loadRemoteDirectory();
                            });
                        } catch (Exception e) {
                            mainHandler.post(() -> {
                                if (!isAdded()) return;
                                progressNetwork.setVisibility(View.GONE);
                                Toast.makeText(requireContext(), "Failed to create folder: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                        }
                    });
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void confirmDeleteNetworkItem(NetworkItem item) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete")
                .setMessage(getString(R.string.dialog_delete_pc_item, item.getName()))
                .setPositiveButton(R.string.btn_delete, (d, which) -> {
                    progressNetwork.setVisibility(View.VISIBLE);
                    executorService.execute(() -> {
                        try {
                            if (NetworkServerInfo.TYPE_GOOGLE_DRIVE.equalsIgnoreCase(activeServer.getType())) {
                                CloudStorageManager csm = CloudStorageManager.getInstance(requireContext());
                                if (csm.isOAuthConnected()) {
                                    boolean deleted = GoogleDriveService.getInstance(requireContext())
                                            .deleteItem(item.getPath(), csm.getGoogleDriveToken());
                                    if (!deleted) throw new Exception("Could not delete from Google Drive");
                                } else {
                                    boolean deleted = DocumentFileTreeHelper.deleteItem(requireContext(), Uri.parse(item.getPath()));
                                    if (!deleted) throw new Exception("Could not delete from Google Drive");
                                }
                            } else if (NetworkServerInfo.TYPE_SMB.equalsIgnoreCase(activeServer.getType())) {
                                SmbClientHelper.deleteItem(activeServer, item);
                            } else {
                                FtpClientHelper.deleteItem(activeServer, item);
                            }
                            mainHandler.post(() -> {
                                if (!isAdded()) return;
                                progressNetwork.setVisibility(View.GONE);
                                Toast.makeText(requireContext(), "Deleted " + item.getName(), Toast.LENGTH_SHORT).show();
                                loadRemoteDirectory();
                            });
                        } catch (Exception e) {
                            mainHandler.post(() -> {
                                if (!isAdded()) return;
                                progressNetwork.setVisibility(View.GONE);
                                Toast.makeText(requireContext(), "Delete failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                        }
                    });
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void showBrowserNewDialog() {
        if (activeServer == null || isBrowsingSharesList) return;

        List<String> options = new ArrayList<>();
        options.add(getString(R.string.new_folder));
        options.add(getString(R.string.new_file));
        if (AppClipboard.getInstance().hasLocalFiles() || AppClipboard.getInstance().hasNetworkItems()) {
            int count = AppClipboard.getInstance().hasLocalFiles() ?
                    AppClipboard.getInstance().getLocalFiles().size() :
                    AppClipboard.getInstance().getNetworkItems().size();
            options.add(getString(R.string.action_paste) + " (" + count + ")");
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.dialog_new_title)
                .setItems(options.toArray(new String[0]), (d, which) -> {
                    if (which == 0) {
                        showCreateFolderOnPcDialog();
                    } else if (which == 1) {
                        showCreateFileOnPcDialog();
                    } else if (which == 2) {
                        pasteFilesToCurrentRemoteFolder();
                    }
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void showCreateFileOnPcDialog() {
        if (activeServer == null || isBrowsingSharesList) return;

        EditText input = new EditText(requireContext());
        input.setSingleLine(true);
        input.setHint(R.string.hint_file_title);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.new_file)
                .setView(input)
                .setPositiveButton(R.string.btn_create, (d, which) -> {
                    String fileName = input.getText().toString().trim();
                    if (TextUtils.isEmpty(fileName)) return;
                    if (!fileName.contains(".")) {
                        fileName += ".txt";
                    }

                    final String finalFileName = fileName;
                    progressNetwork.setVisibility(View.VISIBLE);
                    executorService.execute(() -> {
                        File tempFile = new File(requireContext().getCacheDir(), finalFileName);
                        try {
                            if (!tempFile.exists()) {
                                tempFile.createNewFile();
                            }
                            if (NetworkServerInfo.TYPE_GOOGLE_DRIVE.equalsIgnoreCase(activeServer.getType())) {
                                CloudStorageManager csm = CloudStorageManager.getInstance(requireContext());
                                if (csm.isOAuthConnected()) {
                                    boolean created = GoogleDriveService.getInstance(requireContext())
                                            .uploadFile(currentCloudFolderId, tempFile, csm.getGoogleDriveToken());
                                    if (!created) throw new Exception("Could not create file in Google Drive");
                                } else {
                                    DocumentFileTreeHelper.uploadFile(requireContext(), currentCloudFolderUri, tempFile, null);
                                }
                            } else if (NetworkServerInfo.TYPE_SMB.equalsIgnoreCase(activeServer.getType())) {
                                SmbClientHelper.uploadFile(activeServer, currentSmbShare, currentFolderPath, tempFile, null);
                            } else {
                                FtpClientHelper.uploadFile(activeServer, currentFolderPath, tempFile, null);
                            }
                            tempFile.delete();

                            mainHandler.post(() -> {
                                if (!isAdded()) return;
                                progressNetwork.setVisibility(View.GONE);
                                Toast.makeText(requireContext(), "File created successfully", Toast.LENGTH_SHORT).show();
                                loadRemoteDirectory();
                            });
                        } catch (Exception e) {
                            tempFile.delete();
                            mainHandler.post(() -> {
                                if (!isAdded()) return;
                                progressNetwork.setVisibility(View.GONE);
                                Toast.makeText(requireContext(), "Failed to create file: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                        }
                    });
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void openBrowserSearch() {
        isBrowserSearchActive = true;
        if (layoutBrowserHeader != null) layoutBrowserHeader.setVisibility(View.GONE);
        if (layoutBrowserSearch != null) layoutBrowserSearch.setVisibility(View.VISIBLE);
        if (etBrowserSearch != null) {
            etBrowserSearch.requestFocus();
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(etBrowserSearch, InputMethodManager.SHOW_IMPLICIT);
            }
        }
        if (recyclerNetworkFiles != null) {
            recyclerNetworkFiles.announceForAccessibility(getString(R.string.search_opened_announcement));
        }
        updateBackCallbackState();
    }

    private void closeBrowserSearch() {
        isBrowserSearchActive = false;
        currentBrowserSearchQuery = "";
        if (etBrowserSearch != null) {
            etBrowserSearch.setText("");
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(etBrowserSearch.getWindowToken(), 0);
            }
        }
        if (layoutBrowserSearch != null) layoutBrowserSearch.setVisibility(View.GONE);
        if (layoutBrowserHeader != null) layoutBrowserHeader.setVisibility(View.VISIBLE);

        applyBrowserSortingAndDisplay();
        if (recyclerNetworkFiles != null) {
            recyclerNetworkFiles.announceForAccessibility(getString(R.string.search_closed));
        }
        updateBackCallbackState();
    }

    private void setupBrowserSearchLogic() {
        if (etBrowserSearch == null) return;
        etBrowserSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentBrowserSearchQuery = (s != null) ? s.toString().trim() : "";
                filterBrowserItemsRealTime(currentBrowserSearchQuery);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void filterBrowserItemsRealTime(String query) {
        if (TextUtils.isEmpty(query)) {
            applyBrowserSortingAndDisplay();
            return;
        }

        filteredNetworkItems.clear();
        String lower = query.toLowerCase(Locale.ENGLISH);
        for (NetworkItem item : currentNetworkItems) {
            if (item.getName().toLowerCase(Locale.ENGLISH).contains(lower)) {
                filteredNetworkItems.add(item);
            }
        }

        Collections.sort(filteredNetworkItems, currentBrowserSortMode.getNetworkItemComparator());
        fileAdapter.updateList(filteredNetworkItems);

        if (filteredNetworkItems.isEmpty()) {
            layoutBrowserEmpty.setVisibility(View.VISIBLE);
            String noResultsText = getString(R.string.search_no_results, query);
            tvBrowserEmpty.setText(noResultsText);
            tvBrowserEmpty.announceForAccessibility(noResultsText);
        } else {
            layoutBrowserEmpty.setVisibility(View.GONE);
            String countText = getString(R.string.search_results_count, filteredNetworkItems.size(), query);
            recyclerNetworkFiles.announceForAccessibility(countText);
        }
    }

    private void applyBrowserSortingAndDisplay() {
        Collections.sort(currentNetworkItems, currentBrowserSortMode.getNetworkItemComparator());
        if (isBrowserSearchActive && !TextUtils.isEmpty(currentBrowserSearchQuery)) {
            filterBrowserItemsRealTime(currentBrowserSearchQuery);
        } else {
            fileAdapter.updateList(currentNetworkItems);
            if (currentNetworkItems.isEmpty()) {
                layoutBrowserEmpty.setVisibility(View.VISIBLE);
                tvBrowserEmpty.setText(R.string.folder_empty);
            } else {
                layoutBrowserEmpty.setVisibility(View.GONE);
            }
        }
        updatePasteToPcFabVisibility();
    }

    private void showBrowserSortDialog() {
        if (!isAdded()) return;
        String[] options = new String[]{
                getString(R.string.sort_name_asc),
                getString(R.string.sort_name_desc),
                getString(R.string.sort_date_desc),
                getString(R.string.sort_date_asc),
                getString(R.string.sort_size_asc),
                getString(R.string.sort_size_desc),
                getString(R.string.sort_type_asc)
        };
        SortMode[] modes = new SortMode[]{
                SortMode.NAME_ASC,
                SortMode.NAME_DESC,
                SortMode.DATE_DESC,
                SortMode.DATE_ASC,
                SortMode.SIZE_ASC,
                SortMode.SIZE_DESC,
                SortMode.TYPE_ASC
        };

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.sort_dialog_title)
                .setItems(options, (d, which) -> {
                    currentBrowserSortMode = modes[which];
                    applyBrowserSortingAndDisplay();
                    String announcement = getString(R.string.sorted_favorites_announcement, options[which]);
                    Toast.makeText(requireContext(), announcement, Toast.LENGTH_SHORT).show();
                    recyclerNetworkFiles.announceForAccessibility(announcement);
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    public void updateBackCallbackState() {
        if (backPressedCallback != null) {
            boolean inBrowser = (layoutNetworkBrowser != null && layoutNetworkBrowser.getVisibility() == View.VISIBLE);
            boolean inSelection = (fileAdapter != null && fileAdapter.isSelectionMode());
            backPressedCallback.setEnabled(inBrowser || isBrowserSearchActive || inSelection);
        }
    }

    public void navigateToNetworkItem(NetworkItem item) {
        if (item == null) return;
        activeServer = item.getServerInfo();
        if (activeServer == null && "Google Drive".equalsIgnoreCase(item.getShareName())) {
            activeServer = new NetworkServerInfo("Google Drive", NetworkServerInfo.TYPE_GOOGLE_DRIVE, "google.com", 443, "", "", "", "Google Drive");
        }
        if (activeServer == null) return;

        layoutServerListContainer.setVisibility(View.GONE);
        layoutNetworkBrowser.setVisibility(View.VISIBLE);
        tvBrowserServerName.setText(activeServer.getName());

        if (NetworkServerInfo.TYPE_GOOGLE_DRIVE.equalsIgnoreCase(activeServer.getType())) {
            CloudStorageManager csm = CloudStorageManager.getInstance(requireContext());
            if (csm.isOAuthConnected()) {
                currentCloudFolderId = item.getPath();
                cloudFolderIdStack.clear();
                cloudFolderIdStack.add(item.getPath());
                cloudPathStack.clear();
                cloudPathStack.add("Google Drive");
                cloudPathStack.add(item.getName());
                loadCloudDirectory();
            } else {
                currentCloudFolderUri = Uri.parse(item.getPath());
                cloudNavigationStack.clear();
                cloudNavigationStack.add(currentCloudFolderUri);
                cloudPathStack.clear();
                cloudPathStack.add(item.getName());
                loadCloudDirectory();
            }
        } else if (NetworkServerInfo.TYPE_SMB.equalsIgnoreCase(activeServer.getType())) {
            isBrowsingSharesList = false;
            currentSmbShare = item.getShareName();
            currentFolderPath = item.getPath();
            loadRemoteDirectory();
        } else {
            isBrowsingSharesList = false;
            currentFolderPath = item.getPath();
            loadRemoteDirectory();
        }
        updatePasteToPcFabVisibility();
        updateBackCallbackState();
    }
}
