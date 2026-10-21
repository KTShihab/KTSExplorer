package com.kts.fileexplorer.util;

import android.content.Context;
import androidx.annotation.NonNull;

import com.github.junrar.Archive;
import com.github.junrar.rarfile.FileHeader;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Universal archive helper to extract and browse compressed archives
 * (ZIP, RAR, 7Z, TAR, TAR.GZ, TAR.BZ2, TAR.XZ) inside KTS Explorer,
 * and create compressed archives (ZIP, 7Z, TAR.GZ, TAR) for folder sharing.
 */
public class ArchiveViewerHelper {

    public static final String ARCHIVE_VIEW_DIR = "archive_viewer";
    public static final String ARCHIVE_TEMP_DIR = "archive_temp";

    public enum ArchiveFormat {
        ZIP(".zip", "ZIP (.zip)"),
        SEVEN_Z(".7z", "7Z (.7z)"),
        TAR_GZ(".tar.gz", "TAR.GZ (.tar.gz)"),
        TAR(".tar", "TAR (.tar)");

        private final String extension;
        private final String displayName;

        ArchiveFormat(String extension, String displayName) {
            this.extension = extension;
            this.displayName = displayName;
        }

        public String getExtension() {
            return extension;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Checks whether the given filename is a supported archive.
     */
    public static boolean isArchive(@NonNull String name) {
        String lower = name.toLowerCase(Locale.ENGLISH);
        return lower.endsWith(".zip") ||
                lower.endsWith(".rar") ||
                lower.endsWith(".7z") ||
                lower.endsWith(".tar") ||
                lower.endsWith(".tar.gz") ||
                lower.endsWith(".tgz") ||
                lower.endsWith(".tar.bz2") ||
                lower.endsWith(".tbz2") ||
                lower.endsWith(".tar.xz") ||
                lower.endsWith(".txz");
    }

    /**
     * Extracts an archive from an InputStream into cache for browsing.
     */
    @NonNull
    public static File extractArchiveForBrowsing(@NonNull Context context, @NonNull InputStream inputStream, @NonNull String archiveName) throws Exception {
        File cacheDir = context.getCacheDir();
        File baseDir = new File(cacheDir, ARCHIVE_VIEW_DIR);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }

        cleanOldExtractions(baseDir, 2);

        String folderName = cleanFolderName(archiveName);
        File targetDir = new File(baseDir, folderName);
        if (targetDir.exists()) {
            deleteRecursively(targetDir);
        }
        targetDir.mkdirs();

        String lowerName = archiveName.toLowerCase(Locale.ENGLISH);

        if (lowerName.endsWith(".zip")) {
            extractZipStream(inputStream, targetDir);
        } else if (lowerName.endsWith(".tar") || lowerName.endsWith(".tar.gz") || lowerName.endsWith(".tgz")
                || lowerName.endsWith(".tar.bz2") || lowerName.endsWith(".tbz2")
                || lowerName.endsWith(".tar.xz") || lowerName.endsWith(".txz")) {
            extractTarStream(inputStream, lowerName, targetDir);
        } else {
            // RAR and 7Z require seekable access; stream input into a temp cache file
            File tempFile = saveStreamToTempFile(context, inputStream, archiveName);
            try {
                if (lowerName.endsWith(".rar")) {
                    extractRarFile(tempFile, targetDir);
                } else if (lowerName.endsWith(".7z")) {
                    extract7zFile(tempFile, targetDir);
                } else {
                    // Fallback to zip attempt
                    extractZipFile(tempFile, targetDir);
                }
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete();
                }
            }
        }

        return targetDir;
    }

    /**
     * Extracts an archive from a local File into cache for browsing.
     */
    @NonNull
    public static File extractArchiveForBrowsing(@NonNull Context context, @NonNull File archiveFile) throws Exception {
        String name = archiveFile.getName();
        String lowerName = name.toLowerCase(Locale.ENGLISH);

        File cacheDir = context.getCacheDir();
        File baseDir = new File(cacheDir, ARCHIVE_VIEW_DIR);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }

        cleanOldExtractions(baseDir, 2);

        String folderName = cleanFolderName(name);
        File targetDir = new File(baseDir, folderName);
        if (targetDir.exists()) {
            deleteRecursively(targetDir);
        }
        targetDir.mkdirs();

        if (lowerName.endsWith(".rar")) {
            extractRarFile(archiveFile, targetDir);
        } else if (lowerName.endsWith(".7z")) {
            extract7zFile(archiveFile, targetDir);
        } else if (lowerName.endsWith(".tar") || lowerName.endsWith(".tar.gz") || lowerName.endsWith(".tgz")
                || lowerName.endsWith(".tar.bz2") || lowerName.endsWith(".tbz2")
                || lowerName.endsWith(".tar.xz") || lowerName.endsWith(".txz")) {
            try (InputStream fis = new FileInputStream(archiveFile)) {
                extractTarStream(fis, lowerName, targetDir);
            }
        } else {
            // ZIP or default
            try (InputStream fis = new FileInputStream(archiveFile)) {
                extractZipStream(fis, targetDir);
            }
        }

        return targetDir;
    }

    private static void extractZipStream(InputStream inputStream, File targetDir) throws IOException {
        String canonicalTargetRoot = targetDir.getCanonicalPath();
        byte[] buffer = new byte[8192];

        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(inputStream))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                File destFile = new File(targetDir, entryName);
                String canonicalDest = destFile.getCanonicalPath();

                // Prevent Zip Slip vulnerability
                if (!canonicalDest.startsWith(canonicalTargetRoot + File.separator) && !canonicalDest.equals(canonicalTargetRoot)) {
                    zis.closeEntry();
                    continue;
                }

                if (entry.isDirectory()) {
                    destFile.mkdirs();
                } else {
                    File parent = destFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(destFile))) {
                        int count;
                        while ((count = zis.read(buffer)) != -1) {
                            bos.write(buffer, 0, count);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private static void extractZipFile(File zipFile, File targetDir) throws IOException {
        try (InputStream fis = new FileInputStream(zipFile)) {
            extractZipStream(fis, targetDir);
        }
    }

    private static void extractRarFile(File rarFile, File targetDir) throws Exception {
        String canonicalTargetRoot = targetDir.getCanonicalPath();
        try (Archive archive = new Archive(rarFile)) {
            FileHeader fh;
            while ((fh = archive.nextFileHeader()) != null) {
                if (fh.isEncrypted()) continue;

                String entryName = fh.getFileName().trim().replace('\\', '/');
                File destFile = new File(targetDir, entryName);
                String canonicalDest = destFile.getCanonicalPath();

                if (!canonicalDest.startsWith(canonicalTargetRoot + File.separator) && !canonicalDest.equals(canonicalTargetRoot)) {
                    continue;
                }

                if (fh.isDirectory()) {
                    destFile.mkdirs();
                } else {
                    File parent = destFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    try (FileOutputStream fos = new FileOutputStream(destFile)) {
                        archive.extractFile(fh, fos);
                    }
                }
            }
        }
    }

    private static void extract7zFile(File sevenZFile, File targetDir) throws IOException {
        String canonicalTargetRoot = targetDir.getCanonicalPath();
        byte[] buffer = new byte[8192];

        try (SevenZFile archive = SevenZFile.builder().setFile(sevenZFile).get()) {
            SevenZArchiveEntry entry;
            while ((entry = archive.getNextEntry()) != null) {
                String entryName = entry.getName();
                File destFile = new File(targetDir, entryName);
                String canonicalDest = destFile.getCanonicalPath();

                if (!canonicalDest.startsWith(canonicalTargetRoot + File.separator) && !canonicalDest.equals(canonicalTargetRoot)) {
                    continue;
                }

                if (entry.isDirectory()) {
                    destFile.mkdirs();
                } else {
                    File parent = destFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    try (FileOutputStream fos = new FileOutputStream(destFile)) {
                        int count;
                        while ((count = archive.read(buffer, 0, buffer.length)) != -1) {
                            fos.write(buffer, 0, count);
                        }
                    }
                }
            }
        }
    }

    private static void extractTarStream(InputStream inputStream, String nameLower, File targetDir) throws IOException {
        String canonicalTargetRoot = targetDir.getCanonicalPath();
        byte[] buffer = new byte[8192];

        InputStream bis = new BufferedInputStream(inputStream);
        if (nameLower.endsWith(".tar.gz") || nameLower.endsWith(".tgz")) {
            bis = new GzipCompressorInputStream(bis);
        } else if (nameLower.endsWith(".tar.bz2") || nameLower.endsWith(".tbz2")) {
            bis = new BZip2CompressorInputStream(bis);
        } else if (nameLower.endsWith(".tar.xz") || nameLower.endsWith(".txz")) {
            bis = new XZCompressorInputStream(bis);
        }

        try (TarArchiveInputStream tais = new TarArchiveInputStream(bis)) {
            TarArchiveEntry entry;
            while ((entry = tais.getNextEntry()) != null) {
                String entryName = entry.getName();
                File destFile = new File(targetDir, entryName);
                String canonicalDest = destFile.getCanonicalPath();

                if (!canonicalDest.startsWith(canonicalTargetRoot + File.separator) && !canonicalDest.equals(canonicalTargetRoot)) {
                    continue;
                }

                if (entry.isDirectory()) {
                    destFile.mkdirs();
                } else {
                    File parent = destFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    try (FileOutputStream fos = new FileOutputStream(destFile)) {
                        int count;
                        while ((count = tais.read(buffer)) != -1) {
                            fos.write(buffer, 0, count);
                        }
                    }
                }
            }
        }
    }

    private static File saveStreamToTempFile(Context context, InputStream is, String originalName) throws IOException {
        File tempDir = new File(context.getCacheDir(), ARCHIVE_TEMP_DIR);
        if (!tempDir.exists()) tempDir.mkdirs();

        String ext = ".tmp";
        int dot = originalName.lastIndexOf('.');
        if (dot > 0) ext = originalName.substring(dot);

        File tempFile = new File(tempDir, "stream_" + System.currentTimeMillis() + ext);
        byte[] buffer = new byte[8192];
        try (BufferedInputStream bis = new BufferedInputStream(is);
             FileOutputStream fos = new FileOutputStream(tempFile)) {
            int len;
            while ((len = bis.read(buffer)) != -1) {
                fos.write(buffer, 0, len);
            }
        }
        return tempFile;
    }

    private static String cleanFolderName(String fileName) {
        String folderName = fileName;
        int dot = folderName.lastIndexOf('.');
        if (dot > 0) {
            folderName = folderName.substring(0, dot);
        }
        folderName = folderName.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (folderName.trim().isEmpty()) {
            folderName = "archive_" + System.currentTimeMillis();
        }
        return folderName;
    }

    // --- Compression Creation Helpers for Folder Sharing ---

    public static void createArchive(@NonNull File sourceFolder, @NonNull File destArchive, @NonNull ArchiveFormat format) throws IOException {
        if (destArchive.exists()) {
            destArchive.delete();
        }

        switch (format) {
            case SEVEN_Z:
                create7zArchive(sourceFolder, destArchive);
                break;
            case TAR_GZ:
                createTarGzArchive(sourceFolder, destArchive);
                break;
            case TAR:
                createTarArchive(sourceFolder, destArchive);
                break;
            case ZIP:
            default:
                createZipArchive(sourceFolder, destArchive);
                break;
        }
    }

    private static void createZipArchive(File sourceFolder, File destFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(destFile)))) {
            zipSubFolder(zos, sourceFolder, sourceFolder.getAbsolutePath().length() + 1);
        }
    }

    private static void zipSubFolder(ZipOutputStream zos, File folder, int basePathLength) throws IOException {
        File[] fileList = folder.listFiles();
        if (fileList == null) return;
        byte[] buffer = new byte[8192];
        for (File file : fileList) {
            if (file.isDirectory()) {
                zipSubFolder(zos, file, basePathLength);
            } else {
                String relativePath = file.getAbsolutePath().substring(basePathLength).replace('\\', '/');
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

    private static void createTarGzArchive(File sourceFolder, File destFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(destFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos);
             GzipCompressorOutputStream gzos = new GzipCompressorOutputStream(bos);
             TarArchiveOutputStream taos = new TarArchiveOutputStream(gzos)) {
            taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            tarSubFolder(taos, sourceFolder, sourceFolder.getAbsolutePath().length() + 1);
            taos.finish();
        }
    }

    private static void createTarArchive(File sourceFolder, File destFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(destFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos);
             TarArchiveOutputStream taos = new TarArchiveOutputStream(bos)) {
            taos.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            tarSubFolder(taos, sourceFolder, sourceFolder.getAbsolutePath().length() + 1);
            taos.finish();
        }
    }

    private static void tarSubFolder(TarArchiveOutputStream taos, File folder, int basePathLength) throws IOException {
        File[] fileList = folder.listFiles();
        if (fileList == null) return;
        byte[] buffer = new byte[8192];
        for (File file : fileList) {
            String relativePath = file.getAbsolutePath().substring(basePathLength).replace('\\', '/');
            TarArchiveEntry entry = new TarArchiveEntry(file, relativePath);
            taos.putArchiveEntry(entry);
            if (file.isDirectory()) {
                taos.closeArchiveEntry();
                tarSubFolder(taos, file, basePathLength);
            } else {
                try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
                    int count;
                    while ((count = bis.read(buffer)) != -1) {
                        taos.write(buffer, 0, count);
                    }
                }
                taos.closeArchiveEntry();
            }
        }
    }

    private static void create7zArchive(File sourceFolder, File destFile) throws IOException {
        try (SevenZOutputFile sevenZOutput = new SevenZOutputFile(destFile)) {
            addFolderTo7z(sevenZOutput, sourceFolder, sourceFolder.getAbsolutePath().length() + 1);
        }
    }

    private static void addFolderTo7z(SevenZOutputFile sevenZOutput, File folder, int basePathLength) throws IOException {
        File[] fileList = folder.listFiles();
        if (fileList == null) return;
        byte[] buffer = new byte[8192];
        for (File file : fileList) {
            String relativePath = file.getAbsolutePath().substring(basePathLength).replace('\\', '/');
            SevenZArchiveEntry entry = sevenZOutput.createArchiveEntry(file, relativePath);
            sevenZOutput.putArchiveEntry(entry);
            if (file.isDirectory()) {
                sevenZOutput.closeArchiveEntry();
                addFolderTo7z(sevenZOutput, file, basePathLength);
            } else {
                try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
                    int count;
                    while ((count = bis.read(buffer)) != -1) {
                        sevenZOutput.write(buffer, 0, count);
                    }
                }
                sevenZOutput.closeArchiveEntry();
            }
        }
    }

    private static void cleanOldExtractions(File baseDir, int maxToKeep) {
        try {
            File[] dirs = baseDir.listFiles(File::isDirectory);
            if (dirs != null && dirs.length > maxToKeep) {
                Arrays.sort(dirs, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
                int deleteCount = dirs.length - maxToKeep;
                for (int i = 0; i < deleteCount; i++) {
                    deleteRecursively(dirs[i]);
                }
            }
        } catch (Exception ignored) {}
    }

    public static boolean deleteRecursively(File file) {
        if (file == null || !file.exists()) return true;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        return file.delete();
    }
}
