package com.kts.fileexplorer;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import java.util.List;
import com.kts.fileexplorer.util.SharedFileSaver;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;
import com.kts.fileexplorer.databinding.ActivityMainBinding;
import com.kts.fileexplorer.fragment.CategoryFragment;
import com.kts.fileexplorer.fragment.FavoriteFragment;
import com.kts.fileexplorer.fragment.HomeFragment;
import com.kts.fileexplorer.fragment.OnlineFragment;
import com.kts.fileexplorer.util.AppUpdateManager;

/**
 * MainActivity hosting the Navigation Drawer, BottomNavigationView, and the 4 primary tabs:
 * Home, Category, Online, Favorite.
 */
public class MainActivity extends AppCompatActivity {

    // Load native library if bundled
    static {
        try {
            System.loadLibrary("ktsexplorer");
        } catch (UnsatisfiedLinkError ignored) {
            // Optional fallback
        }
    }

    private ActivityMainBinding binding;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;

    private HomeFragment homeFragment;
    private CategoryFragment categoryFragment;
    private OnlineFragment onlineFragment;
    private FavoriteFragment favoriteFragment;

    private Fragment activeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        drawerLayout = binding.drawerLayout;
        navigationView = binding.navView;

        initFragments(savedInstanceState);
        setupBottomNavigation();
        setupNavigationDrawer();
        setupDrawerBackCallback();

        handleIncomingIntent(getIntent());

        AppUpdateManager.getInstance().checkOnStartup(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();

        if (Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            List<Uri> uris = SharedFileSaver.extractUrisFromIntent(intent);
            if (!uris.isEmpty()) {
                switchFragment(homeFragment);
                binding.bottomNavigation.setSelectedItemId(R.id.nav_home);
                binding.getRoot().post(() -> {
                    if (homeFragment != null && homeFragment.isAdded()) {
                        homeFragment.startSaveSharedFilesMode(uris);
                    }
                });
            }
        } else if (Intent.ACTION_VIEW.equals(action)) {
            Uri data = intent.getData();
            if (data != null) {
                switchFragment(homeFragment);
                binding.bottomNavigation.setSelectedItemId(R.id.nav_home);
                binding.getRoot().post(() -> {
                    if (homeFragment != null && homeFragment.isAdded()) {
                        homeFragment.openArchiveUri(data);
                    }
                });
            }
        }
    }

    private void initFragments(Bundle savedInstanceState) {
        FragmentManager fm = getSupportFragmentManager();

        if (savedInstanceState == null) {
            homeFragment = HomeFragment.newInstance();
            categoryFragment = CategoryFragment.newInstance();
            onlineFragment = OnlineFragment.newInstance();
            favoriteFragment = FavoriteFragment.newInstance();

            fm.beginTransaction()
                    .add(R.id.fragment_container, favoriteFragment, "TAG_FAVORITE").hide(favoriteFragment)
                    .add(R.id.fragment_container, onlineFragment, "TAG_ONLINE").hide(onlineFragment)
                    .add(R.id.fragment_container, categoryFragment, "TAG_CATEGORY").hide(categoryFragment)
                    .add(R.id.fragment_container, homeFragment, "TAG_HOME")
                    .commit();

            activeFragment = homeFragment;
        } else {
            homeFragment = (HomeFragment) fm.findFragmentByTag("TAG_HOME");
            categoryFragment = (CategoryFragment) fm.findFragmentByTag("TAG_CATEGORY");
            onlineFragment = (OnlineFragment) fm.findFragmentByTag("TAG_ONLINE");
            favoriteFragment = (FavoriteFragment) fm.findFragmentByTag("TAG_FAVORITE");

            if (homeFragment == null) homeFragment = HomeFragment.newInstance();
            if (categoryFragment == null) categoryFragment = CategoryFragment.newInstance();
            if (onlineFragment == null) onlineFragment = OnlineFragment.newInstance();
            if (favoriteFragment == null) favoriteFragment = FavoriteFragment.newInstance();

            activeFragment = fm.findFragmentById(R.id.fragment_container);
            if (activeFragment == null) {
                activeFragment = homeFragment;
            }
        }
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = binding.bottomNavigation;

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                switchFragment(homeFragment);
                updateDrawerSelection(R.id.drawer_home);
                return true;
            } else if (itemId == R.id.nav_category) {
                switchFragment(categoryFragment);
                updateDrawerSelection(R.id.drawer_category);
                return true;
            } else if (itemId == R.id.nav_online) {
                switchFragment(onlineFragment);
                updateDrawerSelection(R.id.drawer_online);
                return true;
            } else if (itemId == R.id.nav_favorite) {
                switchFragment(favoriteFragment);
                updateDrawerSelection(R.id.drawer_favorite);
                return true;
            }
            return false;
        });

        bottomNav.setOnItemReselectedListener(item -> {
            bottomNav.announceForAccessibility(item.getTitle() + " tab selected");
        });
    }

    private void setupNavigationDrawer() {
        if (navigationView == null) return;

        navigationView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.drawer_home) {
                binding.bottomNavigation.setSelectedItemId(R.id.nav_home);
            } else if (id == R.id.drawer_category) {
                binding.bottomNavigation.setSelectedItemId(R.id.nav_category);
            } else if (id == R.id.drawer_favorite) {
                binding.bottomNavigation.setSelectedItemId(R.id.nav_favorite);
            } else if (id == R.id.drawer_online) {
                binding.bottomNavigation.setSelectedItemId(R.id.nav_online);
            } else if (id == R.id.drawer_check_updates) {
                AppUpdateManager.getInstance().checkForUpdates(this, true);
            } else if (id == R.id.drawer_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
            } else if (id == R.id.drawer_about) {
                showAboutDialog();
            }

            closeDrawer();
            return true;
        });

        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(android.view.View drawerView) {
                drawerView.announceForAccessibility(getString(R.string.drawer_opened_announcement));
            }

            @Override
            public void onDrawerClosed(android.view.View drawerView) {
                drawerView.announceForAccessibility(getString(R.string.drawer_closed_announcement));
            }
        });
    }

    private void updateDrawerSelection(int drawerItemId) {
        if (navigationView != null) {
            navigationView.setCheckedItem(drawerItemId);
        }
    }

    private void setupDrawerBackCallback() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });
    }

    public void openDrawer() {
        if (drawerLayout != null) {
            drawerLayout.openDrawer(GravityCompat.START);
        }
    }

    public void closeDrawer() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }
    }

    private void showAboutDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.drawer_header_title)
                .setMessage(R.string.about_dialog_message)
                .setPositiveButton(R.string.btn_close, null)
                .show();
    }

    private void switchFragment(Fragment targetFragment) {
        if (targetFragment == null || targetFragment == activeFragment) {
            return;
        }

        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();

        if (!targetFragment.isAdded()) {
            ft.add(R.id.fragment_container, targetFragment);
        }

        if (activeFragment != null) {
            ft.hide(activeFragment);
        }

        ft.show(targetFragment);
        ft.commit();

        activeFragment = targetFragment;
    }

    public void openFolderInHome(java.io.File folder) {
        if (folder == null) return;
        if (homeFragment == null) {
            homeFragment = HomeFragment.newInstance();
        }
        switchFragment(homeFragment);
        binding.bottomNavigation.setSelectedItemId(R.id.nav_home);
        homeFragment.navigateToFolder(folder);
    }

    public void openArchiveFile(java.io.File archiveFile) {
        if (archiveFile == null) return;
        if (homeFragment == null) {
            homeFragment = HomeFragment.newInstance();
        }
        switchFragment(homeFragment);
        binding.bottomNavigation.setSelectedItemId(R.id.nav_home);
        homeFragment.openArchiveFile(archiveFile);
    }

    public void openZipFile(java.io.File zipFile) {
        openArchiveFile(zipFile);
    }

    public void copyToClipboard(java.util.List<java.io.File> files, boolean isCut) {
        if (files == null || files.isEmpty()) return;
        com.kts.fileexplorer.util.AppClipboard.getInstance().setLocalFiles(files, isCut);
        if (homeFragment == null) {
            homeFragment = HomeFragment.newInstance();
        }
        homeFragment.setClipboardFiles(files, isCut);
    }

    public void openNetworkFolder(com.kts.fileexplorer.network.NetworkItem folderItem) {
        if (folderItem == null) return;
        if (onlineFragment == null) {
            onlineFragment = OnlineFragment.newInstance();
        }
        switchFragment(onlineFragment);
        binding.bottomNavigation.setSelectedItemId(R.id.nav_online);
        onlineFragment.navigateToNetworkItem(folderItem);
    }
}