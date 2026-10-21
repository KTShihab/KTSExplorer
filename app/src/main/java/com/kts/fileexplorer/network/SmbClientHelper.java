package com.kts.fileexplorer.network;

import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import com.hierynomus.protocol.commons.EnumWithValue;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import com.hierynomus.smbj.share.File;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Robust SMB 2/3 client helper using smbj library.
 * Handles connecting to Windows / Mac / Linux shared folders,
 * browsing shares and directories, downloading, uploading, deleting, and renaming.
 */
public class SmbClientHelper {

    public interface ProgressListener {
        void onProgress(long bytesTransferred, long totalBytes);
    }

    private static SmbConfig createConfig() {
        return SmbConfig.builder()
                .withTimeout(15, TimeUnit.SECONDS)
                .withSoTimeout(25, TimeUnit.SECONDS)
                .build();
    }

    private static AuthenticationContext createAuthContext(NetworkServerInfo server) {
        String username = server.getUsername();
        String password = server.getPassword();
        String domain = server.getDomain();

        if (username == null || username.trim().isEmpty()) {
            return AuthenticationContext.anonymous();
        }
        return new AuthenticationContext(username, password.toCharArray(), domain);
    }

    /**
     * Lists available shares on the SMB server.
     */
    public static List<String> listShares(NetworkServerInfo server) throws Exception {
        List<String> shares = new ArrayList<>();
        if (server.getShareName() != null && !server.getShareName().trim().isEmpty()) {
            shares.add(server.getShareName().trim());
            return shares;
        }

        SMBClient client = new SMBClient(createConfig());
        try (Connection connection = client.connect(server.getHost(), server.getPort())) {
            AuthenticationContext ac = createAuthContext(server);
            try (Session session = connection.authenticate(ac)) {
                // Try testing common share names that Windows/Linux typically expose
                List<String> probeShares = Arrays.asList(
                        "Users", "Shared", "Public", "C$", "D$", "E$", "share", "data", "documents"
                );
                Set<String> validShares = new HashSet<>();
                for (String probe : probeShares) {
                    try (DiskShare share = (DiskShare) session.connectShare(probe)) {
                        if (share != null && share.isConnected()) {
                            validShares.add(probe);
                        }
                    } catch (Exception ignored) {
                        // Share not found or not accessible, continue probing
                    }
                }
                shares.addAll(validShares);
            }
        }

        // If no shares discovered, add a default fallback prompt
        if (shares.isEmpty()) {
            shares.add("Users");
            shares.add("C$");
        }
        return shares;
    }

    /**
     * Lists files and subdirectories inside a specific share and folder path.
     */
    public static List<NetworkItem> listFiles(NetworkServerInfo server, String shareName, String folderPath) throws Exception {
        List<NetworkItem> items = new ArrayList<>();
        SMBClient client = new SMBClient(createConfig());

        try (Connection connection = client.connect(server.getHost(), server.getPort())) {
            AuthenticationContext ac = createAuthContext(server);
            try (Session session = connection.authenticate(ac)) {
                try (DiskShare share = (DiskShare) session.connectShare(shareName)) {
                    String smbPath = normalizeSmbPath(folderPath);
                    List<FileIdBothDirectoryInformation> list = share.list(smbPath);

                    for (FileIdBothDirectoryInformation info : list) {
                        String name = info.getFileName();
                        if (".".equals(name) || "..".equals(name)) {
                            continue;
                        }

                        boolean isDir = EnumWithValue.EnumUtils.isSet(
                                info.getFileAttributes(),
                                FileAttributes.FILE_ATTRIBUTE_DIRECTORY
                        );

                        long size = info.getEndOfFile();
                        long lastModified = info.getChangeTime().toEpochMillis();

                        String itemPath = smbPath.isEmpty() ? name : smbPath + "\\" + name;
                        items.add(new NetworkItem(name, itemPath, shareName, isDir, size, lastModified, server));
                    }
                }
            }
        }
        return items;
    }

    /**
     * Downloads a remote file from the SMB share to a local file on phone.
     */
    public static void downloadFile(NetworkServerInfo server, NetworkItem item, java.io.File localDestFile, ProgressListener listener) throws Exception {
        SMBClient client = new SMBClient(createConfig());
        try (Connection connection = client.connect(server.getHost(), server.getPort())) {
            AuthenticationContext ac = createAuthContext(server);
            try (Session session = connection.authenticate(ac)) {
                try (DiskShare share = (DiskShare) session.connectShare(item.getShareName())) {
                    try (File smbFile = share.openFile(
                            item.getPath(),
                            EnumSet.of(AccessMask.GENERIC_READ),
                            null,
                            SMB2ShareAccess.ALL,
                            SMB2CreateDisposition.FILE_OPEN,
                            null)) {

                        try (InputStream in = smbFile.getInputStream();
                             OutputStream out = new FileOutputStream(localDestFile)) {
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
                    }
                }
            }
        }
    }

    /**
     * Uploads a local file from the phone to a remote SMB share folder.
     */
    public static void uploadFile(NetworkServerInfo server, String shareName, String remoteFolderPath, java.io.File localFile, ProgressListener listener) throws Exception {
        SMBClient client = new SMBClient(createConfig());
        try (Connection connection = client.connect(server.getHost(), server.getPort())) {
            AuthenticationContext ac = createAuthContext(server);
            try (Session session = connection.authenticate(ac)) {
                try (DiskShare share = (DiskShare) session.connectShare(shareName)) {
                    String smbFolder = normalizeSmbPath(remoteFolderPath);
                    String remoteFilePath = smbFolder.isEmpty() ? localFile.getName() : smbFolder + "\\" + localFile.getName();

                    try (File smbFile = share.openFile(
                            remoteFilePath,
                            EnumSet.of(AccessMask.GENERIC_WRITE, AccessMask.GENERIC_READ),
                            null,
                            SMB2ShareAccess.ALL,
                            SMB2CreateDisposition.FILE_OVERWRITE_IF,
                            null)) {

                        try (InputStream in = new FileInputStream(localFile);
                             OutputStream out = smbFile.getOutputStream()) {
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
                    }
                }
            }
        }
    }

    /**
     * Deletes a file or directory on the SMB share.
     */
    public static void deleteItem(NetworkServerInfo server, NetworkItem item) throws Exception {
        SMBClient client = new SMBClient(createConfig());
        try (Connection connection = client.connect(server.getHost(), server.getPort())) {
            AuthenticationContext ac = createAuthContext(server);
            try (Session session = connection.authenticate(ac)) {
                try (DiskShare share = (DiskShare) session.connectShare(item.getShareName())) {
                    if (item.isDirectory()) {
                        share.rmdir(item.getPath(), true);
                    } else {
                        share.rm(item.getPath());
                    }
                }
            }
        }
    }

    /**
     * Creates a new folder on the SMB share.
     */
    public static void createDirectory(NetworkServerInfo server, String shareName, String parentFolder, String newFolderName) throws Exception {
        SMBClient client = new SMBClient(createConfig());
        try (Connection connection = client.connect(server.getHost(), server.getPort())) {
            AuthenticationContext ac = createAuthContext(server);
            try (Session session = connection.authenticate(ac)) {
                try (DiskShare share = (DiskShare) session.connectShare(shareName)) {
                    String parent = normalizeSmbPath(parentFolder);
                    String newDirPath = parent.isEmpty() ? newFolderName : parent + "\\" + newFolderName;
                    share.mkdir(newDirPath);
                }
            }
        }
    }

    private static String normalizeSmbPath(String path) {
        if (path == null) return "";
        path = path.replace('/', '\\');
        while (path.startsWith("\\")) {
            path = path.substring(1);
        }
        while (path.endsWith("\\")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }
}
