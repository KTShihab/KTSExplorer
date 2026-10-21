package com.kts.fileexplorer.network;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * FTP client helper using Apache Commons Net.
 */
public class FtpClientHelper {

    public interface ProgressListener {
        void onProgress(long bytesTransferred, long totalBytes);
    }

    private static FTPClient createAndConnect(NetworkServerInfo server) throws Exception {
        FTPClient ftp = new FTPClient();
        ftp.setConnectTimeout(15000);
        ftp.setDataTimeout(20000);
        ftp.connect(server.getHost(), server.getPort());

        String username = server.getUsername();
        String password = server.getPassword();
        if (username == null || username.trim().isEmpty()) {
            username = "anonymous";
            password = "";
        }

        boolean login = ftp.login(username, password);
        if (!login) {
            ftp.disconnect();
            throw new Exception("FTP Login failed for user: " + username);
        }

        ftp.enterLocalPassiveMode();
        ftp.setFileType(FTP.BINARY_FILE_TYPE);
        return ftp;
    }

    public static List<NetworkItem> listFiles(NetworkServerInfo server, String folderPath) throws Exception {
        List<NetworkItem> items = new ArrayList<>();
        FTPClient ftp = createAndConnect(server);
        try {
            String path = (folderPath == null || folderPath.isEmpty()) ? "/" : folderPath;
            FTPFile[] files = ftp.listFiles(path);
            if (files != null) {
                for (FTPFile file : files) {
                    String name = file.getName();
                    if (".".equals(name) || "..".equals(name)) {
                        continue;
                    }
                    boolean isDir = file.isDirectory();
                    long size = file.getSize();
                    long lastModified = file.getTimestamp() != null ? file.getTimestamp().getTimeInMillis() : 0;
                    String itemPath = path.endsWith("/") ? path + name : path + "/" + name;
                    items.add(new NetworkItem(name, itemPath, "", isDir, size, lastModified, server));
                }
            }
        } finally {
            try {
                ftp.logout();
                ftp.disconnect();
            } catch (Exception ignored) {}
        }
        return items;
    }

    public static void downloadFile(NetworkServerInfo server, NetworkItem item, java.io.File localDestFile, ProgressListener listener) throws Exception {
        FTPClient ftp = createAndConnect(server);
        try {
            try (InputStream in = ftp.retrieveFileStream(item.getPath());
                 OutputStream out = new FileOutputStream(localDestFile)) {
                if (in == null) {
                    throw new Exception("Failed to open remote FTP file stream: " + item.getPath());
                }
                byte[] buffer = new byte[64 * 1024];
                int bytesRead;
                long totalTransferred = 0;
                long totalBytes = item.getSize();

                while ((bytesRead = in.read(buffer)) > 0) {
                    out.write(buffer, 0, bytesRead);
                    totalTransferred += bytesRead;
                    if (listener != null) {
                        listener.onProgress(totalTransferred, totalBytes);
                    }
                }
                out.flush();
            }
            ftp.completePendingCommand();
        } finally {
            try {
                ftp.logout();
                ftp.disconnect();
            } catch (Exception ignored) {}
        }
    }

    public static void uploadFile(NetworkServerInfo server, String remoteFolderPath, java.io.File localFile, ProgressListener listener) throws Exception {
        FTPClient ftp = createAndConnect(server);
        try {
            String targetPath = (remoteFolderPath == null || remoteFolderPath.isEmpty() || "/".equals(remoteFolderPath)) ?
                    "/" + localFile.getName() :
                    (remoteFolderPath.endsWith("/") ? remoteFolderPath + localFile.getName() : remoteFolderPath + "/" + localFile.getName());

            try (InputStream in = new FileInputStream(localFile);
                 OutputStream out = ftp.storeFileStream(targetPath)) {
                if (out == null) {
                    throw new Exception("Failed to open remote FTP store stream: " + targetPath);
                }
                byte[] buffer = new byte[64 * 1024];
                int bytesRead;
                long totalTransferred = 0;
                long totalBytes = localFile.length();

                while ((bytesRead = in.read(buffer)) > 0) {
                    out.write(buffer, 0, bytesRead);
                    totalTransferred += bytesRead;
                    if (listener != null) {
                        listener.onProgress(totalTransferred, totalBytes);
                    }
                }
                out.flush();
            }
            ftp.completePendingCommand();
        } finally {
            try {
                ftp.logout();
                ftp.disconnect();
            } catch (Exception ignored) {}
        }
    }

    public static void deleteItem(NetworkServerInfo server, NetworkItem item) throws Exception {
        FTPClient ftp = createAndConnect(server);
        try {
            if (item.isDirectory()) {
                ftp.removeDirectory(item.getPath());
            } else {
                ftp.deleteFile(item.getPath());
            }
        } finally {
            try {
                ftp.logout();
                ftp.disconnect();
            } catch (Exception ignored) {}
        }
    }

    public static void createDirectory(NetworkServerInfo server, String parentFolder, String newFolderName) throws Exception {
        FTPClient ftp = createAndConnect(server);
        try {
            String targetPath = (parentFolder == null || parentFolder.isEmpty() || "/".equals(parentFolder)) ?
                    "/" + newFolderName :
                    (parentFolder.endsWith("/") ? parentFolder + newFolderName : parentFolder + "/" + newFolderName);
            ftp.makeDirectory(targetPath);
        } finally {
            try {
                ftp.logout();
                ftp.disconnect();
            } catch (Exception ignored) {}
        }
    }
}
