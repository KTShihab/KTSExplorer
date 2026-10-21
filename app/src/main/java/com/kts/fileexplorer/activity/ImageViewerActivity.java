package com.kts.fileexplorer.activity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.MimeTypeMap;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.kts.fileexplorer.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Built-in accessible Image Viewer supporting previous, next, rotate, share, and safe bitmap decoding.
 */
public class ImageViewerActivity extends AppCompatActivity {

    public static final String EXTRA_CURRENT_PATH = "extra_current_path";
    public static final String EXTRA_IMAGE_PATHS = "extra_image_paths";
    public static final String EXTRA_START_INDEX = "extra_start_index";

    private static final Set<String> IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "svg"
    ));

    private final List<String> imageList = new ArrayList<>();
    private int currentIndex = 0;
    private int currentRotationDegrees = 0;

    private TextView tvTitle;
    private TextView tvSubtitle;
    private ImageView ivDisplay;
    private ImageButton btnBack;
    private ImageButton btnRotate;
    private ImageButton btnShare;
    private ImageButton btnPrevious;
    private ImageButton btnNext;

    private Bitmap currentBitmap = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);

        initViews();
        parseIntentData();

        if (imageList.isEmpty()) {
            Toast.makeText(this, R.string.cannot_open_file, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        displayImageAtIndex(currentIndex);
    }

    private void initViews() {
        tvTitle = findViewById(R.id.tv_image_title);
        tvSubtitle = findViewById(R.id.tv_image_subtitle);
        tvTitle.setSelected(true);

        ivDisplay = findViewById(R.id.iv_image_display);
        btnBack = findViewById(R.id.btn_image_back);
        btnRotate = findViewById(R.id.btn_image_rotate);
        btnShare = findViewById(R.id.btn_image_share);
        btnPrevious = findViewById(R.id.btn_image_previous);
        btnNext = findViewById(R.id.btn_image_next);

        btnBack.setOnClickListener(v -> finish());
        btnRotate.setOnClickListener(v -> rotateImage());
        btnShare.setOnClickListener(v -> shareCurrentImage());
        btnPrevious.setOnClickListener(v -> showPrevious());
        btnNext.setOnClickListener(v -> showNext());
    }

    private void parseIntentData() {
        Intent intent = getIntent();
        if (intent == null) return;

        String currentPath = intent.getStringExtra(EXTRA_CURRENT_PATH);
        ArrayList<String> paths = intent.getStringArrayListExtra(EXTRA_IMAGE_PATHS);
        int startIndex = intent.getIntExtra(EXTRA_START_INDEX, 0);

        if (paths != null && !paths.isEmpty()) {
            imageList.addAll(paths);
            currentIndex = Math.max(0, Math.min(startIndex, imageList.size() - 1));
        } else if (currentPath != null && !currentPath.isEmpty()) {
            File currentFile = new File(currentPath);
            File parentDir = currentFile.getParentFile();
            if (parentDir != null && parentDir.exists()) {
                File[] siblings = parentDir.listFiles();
                if (siblings != null) {
                    Arrays.sort(siblings, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                    for (File f : siblings) {
                        if (!f.isDirectory() && isImageFile(f)) {
                            imageList.add(f.getAbsolutePath());
                        }
                    }
                }
            }
            if (imageList.isEmpty()) {
                imageList.add(currentFile.getAbsolutePath());
            }
            currentIndex = imageList.indexOf(currentFile.getAbsolutePath());
            if (currentIndex < 0) currentIndex = 0;
        }
    }

    private void displayImageAtIndex(int index) {
        if (index < 0 || index >= imageList.size()) return;
        currentIndex = index;
        currentRotationDegrees = 0;

        String path = imageList.get(currentIndex);
        File file = new File(path);
        if (!file.exists()) {
            Toast.makeText(this, R.string.cannot_open_file, Toast.LENGTH_SHORT).show();
            return;
        }

        tvTitle.setText(file.getName());
        tvSubtitle.setText(getString(R.string.image_position_format, currentIndex + 1, imageList.size()));

        btnPrevious.setEnabled(currentIndex > 0);
        btnPrevious.setAlpha(currentIndex > 0 ? 1.0f : 0.3f);
        btnNext.setEnabled(currentIndex < imageList.size() - 1);
        btnNext.setAlpha(currentIndex < imageList.size() - 1 ? 1.0f : 0.3f);

        loadBitmapSafely(file);

        String announcement = getString(R.string.image_position_format, currentIndex + 1, imageList.size()) + ": " + file.getName();
        tvTitle.announceForAccessibility(announcement);
    }

    private void loadBitmapSafely(File file) {
        if (currentBitmap != null && !currentBitmap.isRecycled()) {
            currentBitmap.recycle();
            currentBitmap = null;
        }

        try {
            // First decode with inJustDecodeBounds=true to check dimensions
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), options);

            // Calculate sample size for target 2048 max dimension
            int maxDim = Math.max(options.outWidth, options.outHeight);
            int sampleSize = 1;
            while (maxDim / (sampleSize * 2) >= 1080) {
                sampleSize *= 2;
            }

            options.inJustDecodeBounds = false;
            options.inSampleSize = sampleSize;
            options.inPreferredConfig = Bitmap.Config.RGB_565;

            currentBitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            ivDisplay.setImageBitmap(currentBitmap);
            ivDisplay.setRotation(0);
        } catch (Throwable t) {
            Toast.makeText(this, "Could not render image: " + t.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void rotateImage() {
        currentRotationDegrees = (currentRotationDegrees + 90) % 360;
        ivDisplay.setRotation(currentRotationDegrees);
        ivDisplay.announceForAccessibility(getString(R.string.btn_rotate) + " " + currentRotationDegrees + "°");
    }

    private void showPrevious() {
        if (currentIndex > 0) {
            displayImageAtIndex(currentIndex - 1);
        }
    }

    private void showNext() {
        if (currentIndex < imageList.size() - 1) {
            displayImageAtIndex(currentIndex + 1);
        }
    }

    private void shareCurrentImage() {
        if (currentIndex < 0 || currentIndex >= imageList.size()) return;
        File file = new File(imageList.get(currentIndex));
        if (!file.exists()) return;

        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
            String ext = getExtension(file.getName());
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (mime == null) mime = "image/*";

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType(mime);
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, getString(R.string.action_share) + " " + file.getName()));
        } catch (Exception e) {
            Toast.makeText(this, "Share failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public static boolean isImageFile(File file) {
        if (file == null || file.isDirectory()) return false;
        String ext = getExtension(file.getName());
        return IMAGE_EXTENSIONS.contains(ext);
    }

    private static String getExtension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1) {
            return name.substring(dot + 1).toLowerCase(Locale.ENGLISH);
        }
        return "";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (currentBitmap != null && !currentBitmap.isRecycled()) {
            currentBitmap.recycle();
            currentBitmap = null;
        }
    }
}
