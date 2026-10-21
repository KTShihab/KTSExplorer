package com.kts.fileexplorer.util;

import android.content.Context;
import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Backward compatibility wrapper delegating to universal {@link ArchiveViewerHelper}.
 */
public class ZipViewerHelper {

    @NonNull
    public static File extractZipForBrowsing(@NonNull Context context, @NonNull InputStream zipInputStream, @NonNull String zipFileName) throws IOException {
        try {
            return ArchiveViewerHelper.extractArchiveForBrowsing(context, zipInputStream, zipFileName);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    public static boolean deleteRecursively(File file) {
        return ArchiveViewerHelper.deleteRecursively(file);
    }
}
