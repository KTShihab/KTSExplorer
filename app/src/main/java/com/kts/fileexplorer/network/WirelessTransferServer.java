package com.kts.fileexplorer.network;

import android.content.Context;
import android.os.Environment;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

/**
 * Embedded wireless transfer server allowing PCs on the same Wi-Fi network
 * to browse phone files, download files, and upload files to phone
 * via any browser (HTTP port 8080) and Windows File Explorer (FTP port 2121).
 */
public class WirelessTransferServer {

    public static final int HTTP_PORT = 8080;
    public static final int FTP_PORT = 2121;

    private static WirelessTransferServer instance;
    private ServerSocket httpServerSocket;
    private ServerSocket ftpServerSocket;
    private boolean isRunning = false;
    private File rootDir;

    private WirelessTransferServer() {
        rootDir = Environment.getExternalStorageDirectory();
    }

    public static synchronized WirelessTransferServer getInstance() {
        if (instance == null) {
            instance = new WirelessTransferServer();
        }
        return instance;
    }

    public synchronized boolean isRunning() {
        return isRunning;
    }

    public synchronized void startServer(Context context) throws Exception {
        if (isRunning) return;

        rootDir = Environment.getExternalStorageDirectory();
        httpServerSocket = new ServerSocket();
        httpServerSocket.setReuseAddress(true);
        httpServerSocket.bind(new InetSocketAddress(HTTP_PORT));

        try {
            ftpServerSocket = new ServerSocket();
            ftpServerSocket.setReuseAddress(true);
            ftpServerSocket.bind(new InetSocketAddress(FTP_PORT));
        } catch (Exception ignored) {}

        isRunning = true;

        // HTTP Server Thread
        new Thread(() -> {
            while (isRunning && httpServerSocket != null && !httpServerSocket.isClosed()) {
                try {
                    Socket client = httpServerSocket.accept();
                    new Thread(() -> handleHttpClient(client)).start();
                } catch (Exception ignored) {
                    break;
                }
            }
        }, "KTS-HttpServer").start();

        // FTP Server Thread
        if (ftpServerSocket != null) {
            new Thread(() -> {
                while (isRunning && ftpServerSocket != null && !ftpServerSocket.isClosed()) {
                    try {
                        Socket client = ftpServerSocket.accept();
                        new Thread(() -> handleFtpClient(client)).start();
                    } catch (Exception ignored) {
                        break;
                    }
                }
            }, "KTS-FtpServer").start();
        }
    }

    public synchronized void stopServer() {
        isRunning = false;
        try {
            if (httpServerSocket != null && !httpServerSocket.isClosed()) {
                httpServerSocket.close();
            }
        } catch (Exception ignored) {}
        try {
            if (ftpServerSocket != null && !ftpServerSocket.isClosed()) {
                ftpServerSocket.close();
            }
        } catch (Exception ignored) {}
        httpServerSocket = null;
        ftpServerSocket = null;
    }

    public String getHttpUrl(Context context) {
        return "http://" + LanScanner.getLocalIpAddress(context) + ":" + HTTP_PORT;
    }

    public String getFtpUrl(Context context) {
        return "ftp://" + LanScanner.getLocalIpAddress(context) + ":" + FTP_PORT;
    }

    // --- HTTP Web Server Handler ---

    private void handleHttpClient(Socket socket) {
        try (InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line = reader.readLine();
            if (line == null) return;

            String[] parts = line.split(" ");
            if (parts.length < 2) return;

            String method = parts[0];
            String uri = parts[1];

            // Parse headers
            int contentLength = 0;
            String contentType = "";
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                if (line.toLowerCase(Locale.ENGLISH).startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.substring(15).trim());
                } else if (line.toLowerCase(Locale.ENGLISH).startsWith("content-type:")) {
                    contentType = line.substring(13).trim();
                }
            }

            if ("POST".equalsIgnoreCase(method) && uri.startsWith("/upload")) {
                handleHttpUpload(uri, contentType, contentLength, in, out);
            } else if ("POST".equalsIgnoreCase(method) && uri.startsWith("/mkdir")) {
                handleHttpMkdir(uri, out);
            } else if ("POST".equalsIgnoreCase(method) && uri.startsWith("/delete")) {
                handleHttpDelete(uri, out);
            } else {
                handleHttpGet(uri, out);
            }

        } catch (Exception ignored) {}
    }

    private void handleHttpGet(String uri, OutputStream out) throws Exception {
        String decodedPath = URLDecoder.decode(uri, "UTF-8");
        if (decodedPath.contains("?")) {
            decodedPath = decodedPath.substring(0, decodedPath.indexOf('?'));
        }

        File target = new File(rootDir, decodedPath.startsWith("/") ? decodedPath.substring(1) : decodedPath);
        if (!target.exists()) {
            sendHttpResponse(out, 404, "text/html; charset=utf-8", "<h1>404 Not Found</h1>".getBytes(StandardCharsets.UTF_8));
            return;
        }

        if (target.isFile()) {
            // Download file
            String mime = "application/octet-stream";
            byte[] header = ("HTTP/1.1 200 OK\r\n" +
                    "Content-Type: " + mime + "\r\n" +
                    "Content-Length: " + target.length() + "\r\n" +
                    "Content-Disposition: attachment; filename=\"" + target.getName() + "\"\r\n" +
                    "Connection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8);
            out.write(header);

            try (FileInputStream fis = new FileInputStream(target)) {
                byte[] buf = new byte[64 * 1024];
                int read;
                while ((read = fis.read(buf)) > 0) {
                    out.write(buf, 0, read);
                }
            }
            out.flush();
        } else {
            // Render directory HTML
            String relPath = target.getAbsolutePath().substring(rootDir.getAbsolutePath().length());
            if (relPath.isEmpty()) relPath = "/";
            String html = generateDirectoryHtml(target, relPath);
            sendHttpResponse(out, 200, "text/html; charset=utf-8", html.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void handleHttpUpload(String uri, String contentType, int contentLength, InputStream in, OutputStream out) throws Exception {
        String folderParam = "";
        if (uri.contains("folder=")) {
            folderParam = uri.substring(uri.indexOf("folder=") + 7);
            if (folderParam.contains("&")) folderParam = folderParam.substring(0, folderParam.indexOf('&'));
            folderParam = URLDecoder.decode(folderParam, "UTF-8");
        }

        File targetFolder = new File(rootDir, folderParam.startsWith("/") ? folderParam.substring(1) : folderParam);
        if (!targetFolder.exists()) targetFolder.mkdirs();

        if (contentType.contains("boundary=")) {
            String boundary = contentType.substring(contentType.indexOf("boundary=") + 9);
            if (boundary.startsWith("\"") && boundary.endsWith("\"")) {
                boundary = boundary.substring(1, boundary.length() - 1);
            }
            parseMultipartUpload(in, boundary, targetFolder);
        }

        String redirect = "<script>window.location.href='" + (folderParam.isEmpty() ? "/" : folderParam) + "';</script>";
        sendHttpResponse(out, 200, "text/html; charset=utf-8", redirect.getBytes(StandardCharsets.UTF_8));
    }

    private void parseMultipartUpload(InputStream in, String boundary, File targetFolder) throws Exception {
        byte[] boundaryBytes = ("--" + boundary).getBytes(StandardCharsets.UTF_8);
        BufferedInputStream bis = new BufferedInputStream(in);

        // Simple multipart parser for single/multiple file uploads
        ByteArrayOutputStream lineBuf = new ByteArrayOutputStream();
        String filename = null;
        boolean inHeader = true;
        FileOutputStream fos = null;

        int b;
        while ((b = bis.read()) != -1) {
            lineBuf.write(b);
            byte[] raw = lineBuf.toByteArray();

            if (raw.length >= 2 && raw[raw.length - 2] == '\r' && raw[raw.length - 1] == '\n') {
                String line = new String(raw, 0, raw.length - 2, StandardCharsets.UTF_8);
                lineBuf.reset();

                if (line.contains("filename=\"")) {
                    int start = line.indexOf("filename=\"") + 10;
                    int end = line.indexOf("\"", start);
                    if (end > start) {
                        filename = line.substring(start, end);
                    }
                } else if (line.isEmpty() && filename != null) {
                    // Body starts
                    File dest = new File(targetFolder, filename);
                    fos = new FileOutputStream(dest);
                    readMultipartBody(bis, boundaryBytes, fos);
                    fos.close();
                    fos = null;
                    filename = null;
                }
            }
        }
    }

    private void readMultipartBody(BufferedInputStream bis, byte[] boundaryBytes, OutputStream out) throws Exception {
        byte[] buf = new byte[8192];
        int matchIdx = 0;
        ByteArrayOutputStream matchBuf = new ByteArrayOutputStream();

        int b;
        while ((b = bis.read()) != -1) {
            if (b == boundaryBytes[matchIdx]) {
                matchBuf.write(b);
                matchIdx++;
                if (matchIdx == boundaryBytes.length) {
                    // Boundary matched, finish body
                    break;
                }
            } else {
                if (matchIdx > 0) {
                    out.write(matchBuf.toByteArray());
                    matchBuf.reset();
                    matchIdx = 0;
                }
                out.write(b);
            }
        }
    }

    private void handleHttpMkdir(String uri, OutputStream out) throws Exception {
        // e.g. /mkdir?folder=/Downloads&name=NewFolder
        String folder = "";
        String name = "";
        if (uri.contains("folder=")) {
            folder = uri.substring(uri.indexOf("folder=") + 7);
            if (folder.contains("&")) folder = folder.substring(0, folder.indexOf('&'));
            folder = URLDecoder.decode(folder, "UTF-8");
        }
        if (uri.contains("name=")) {
            name = uri.substring(uri.indexOf("name=") + 5);
            if (name.contains("&")) name = name.substring(0, name.indexOf('&'));
            name = URLDecoder.decode(name, "UTF-8");
        }

        if (!name.isEmpty()) {
            File parent = new File(rootDir, folder.startsWith("/") ? folder.substring(1) : folder);
            new File(parent, name).mkdirs();
        }

        String redirect = "<script>window.location.href='" + (folder.isEmpty() ? "/" : folder) + "';</script>";
        sendHttpResponse(out, 200, "text/html; charset=utf-8", redirect.getBytes(StandardCharsets.UTF_8));
    }

    private void handleHttpDelete(String uri, OutputStream out) throws Exception {
        String path = "";
        if (uri.contains("path=")) {
            path = uri.substring(uri.indexOf("path=") + 5);
            if (path.contains("&")) path = path.substring(0, path.indexOf('&'));
            path = URLDecoder.decode(path, "UTF-8");
        }

        File target = new File(rootDir, path.startsWith("/") ? path.substring(1) : path);
        if (target.exists()) {
            deleteRecursively(target);
        }

        String parentPath = target.getParentFile() != null ?
                target.getParentFile().getAbsolutePath().substring(rootDir.getAbsolutePath().length()) : "/";
        if (parentPath.isEmpty()) parentPath = "/";

        String redirect = "<script>window.location.href='" + parentPath + "';</script>";
        sendHttpResponse(out, 200, "text/html; charset=utf-8", redirect.getBytes(StandardCharsets.UTF_8));
    }

    private boolean deleteRecursively(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursively(child);
                }
            }
        }
        return file.delete();
    }

    private void sendHttpResponse(OutputStream out, int statusCode, String contentType, byte[] data) throws Exception {
        String header = "HTTP/1.1 " + statusCode + " OK\r\n" +
                "Content-Type: " + contentType + "\r\n" +
                "Content-Length: " + data.length + "\r\n" +
                "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.write(data);
        out.flush();
    }

    private String generateDirectoryHtml(File folder, String relPath) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width, initial-scale=1'>");
        sb.append("<title>KTS File Explorer - PC Wireless Transfer</title>");
        sb.append("<style>");
        sb.append("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; margin: 0; padding: 20px; background: #f4f6f9; color: #333; }");
        sb.append(".container { max-width: 900px; margin: 0 auto; background: #fff; padding: 24px; border-radius: 12px; box-shadow: 0 4px 16px rgba(0,0,0,0.08); }");
        sb.append(".header { display: flex; align-items: center; justify-content: space-between; border-bottom: 2px solid #eef2f5; padding-bottom: 16px; margin-bottom: 16px; }");
        sb.append(".title { font-size: 20px; font-weight: bold; color: #1976d2; }");
        sb.append(".path-bar { font-size: 15px; background: #e3f2fd; color: #0d47a1; padding: 8px 12px; border-radius: 6px; margin-bottom: 16px; word-break: break-all; }");
        sb.append(".toolbar { display: flex; gap: 12px; margin-bottom: 16px; flex-wrap: wrap; }");
        sb.append("button, .btn { background: #1976d2; color: #fff; border: none; padding: 8px 16px; border-radius: 6px; font-size: 14px; cursor: pointer; text-decoration: none; display: inline-flex; align-items: center; }");
        sb.append("button:hover, .btn:hover { background: #1565c0; }");
        sb.append(".file-table { width: 100%; border-collapse: collapse; margin-top: 12px; }");
        sb.append(".file-table th { text-align: left; padding: 10px; background: #fafbfc; border-bottom: 2px solid #eaedf0; font-size: 13px; color: #666; }");
        sb.append(".file-table td { padding: 12px 10px; border-bottom: 1px solid #f0f2f5; font-size: 14px; }");
        sb.append(".file-table tr:hover { background: #f8fafc; }");
        sb.append("a.item-link { color: #212121; text-decoration: none; font-weight: 500; display: flex; align-items: center; gap: 8px; }");
        sb.append("a.item-link:hover { color: #1976d2; text-decoration: underline; }");
        sb.append(".icon { font-size: 18px; }");
        sb.append(".badge { font-size: 11px; padding: 2px 6px; border-radius: 4px; background: #e0e0e0; }");
        sb.append(".upload-card { background: #fafbfc; border: 2px dashed #90caf9; border-radius: 8px; padding: 16px; text-align: center; margin-bottom: 16px; }");
        sb.append("</style></head><body>");

        sb.append("<div class='container'>");
        sb.append("<div class='header'>");
        sb.append("<div class='title'>📁 KTS File Explorer - PC Transfer</div>");
        sb.append("<div><span class='badge'>Connected to Phone</span></div>");
        sb.append("</div>");

        sb.append("<div class='path-bar'>📂 Path: <strong>").append(relPath).append("</strong></div>");

        // Upload Form
        sb.append("<div class='upload-card'>");
        sb.append("<form method='POST' action='/upload?folder=").append(relPath).append("' enctype='multipart/form-data'>");
        sb.append("<span style='font-size: 14px; margin-right: 12px; font-weight: 500;'>📤 Upload file from PC to phone:</span>");
        sb.append("<input type='file' name='file' required style='margin-right: 12px;'>");
        sb.append("<button type='submit'>Upload</button>");
        sb.append("</form>");
        sb.append("</div>");

        // Action Toolbar
        sb.append("<div class='toolbar'>");
        if (!"/".equals(relPath)) {
            String parent = folder.getParentFile().getAbsolutePath().substring(rootDir.getAbsolutePath().length());
            if (parent.isEmpty()) parent = "/";
            sb.append("<a href='").append(parent).append("' class='btn'>⬆️ Up to Parent Folder</a>");
        }
        sb.append("<button onclick='createFolder()'>➕ New Folder</button>");
        sb.append("</div>");

        // File List
        sb.append("<table class='file-table'>");
        sb.append("<thead><tr><th>Name</th><th>Size</th><th>Date Modified</th><th>Action</th></tr></thead>");
        sb.append("<tbody>");

        File[] files = folder.listFiles();
        if (files != null) {
            Arrays.sort(files, (a, b) -> {
                if (a.isDirectory() && !b.isDirectory()) return -1;
                if (!a.isDirectory() && b.isDirectory()) return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH);

            for (File f : files) {
                if (f.getName().startsWith(".")) continue;
                String fRel = f.getAbsolutePath().substring(rootDir.getAbsolutePath().length());
                boolean isDir = f.isDirectory();

                sb.append("<tr>");
                sb.append("<td>");
                sb.append("<a class='item-link' href='").append(fRel).append("'>");
                sb.append("<span class='icon'>").append(isDir ? "📁" : "📄").append("</span> ");
                sb.append(f.getName());
                sb.append("</a>");
                sb.append("</td>");

                sb.append("<td>").append(isDir ? "--" : formatSize(f.length())).append("</td>");
                sb.append("<td>").append(sdf.format(new Date(f.lastModified()))).append("</td>");
                sb.append("<td>");
                if (!isDir) {
                    sb.append("<a href='").append(fRel).append("' class='btn' style='padding: 4px 8px; font-size: 12px;'>⬇️ Download</a> ");
                }
                sb.append("<button onclick=\"deleteItem('").append(fRel.replace("\\", "\\\\")).append("')\" style='background:#e53935; padding: 4px 8px; font-size: 12px;'>🗑️</button>");
                sb.append("</td>");
                sb.append("</tr>");
            }
        }
        sb.append("</tbody></table>");

        // JS for New Folder & Delete
        sb.append("<script>");
        sb.append("function createFolder() {");
        sb.append("  var name = prompt('Enter new folder name:');");
        sb.append("  if(name && name.trim()){");
        sb.append("    var form = document.createElement('form');");
        sb.append("    form.method = 'POST';");
        sb.append("    form.action = '/mkdir?folder=' + encodeURIComponent('").append(relPath).append("') + '&name=' + encodeURIComponent(name.trim());");
        sb.append("    document.body.appendChild(form); form.submit();");
        sb.append("  }");
        sb.append("}");
        sb.append("function deleteItem(path) {");
        sb.append("  if(confirm('Are you sure you want to delete this item from phone?')){");
        sb.append("    var form = document.createElement('form');");
        sb.append("    form.method = 'POST';");
        sb.append("    form.action = '/delete?path=' + encodeURIComponent(path);");
        sb.append("    document.body.appendChild(form); form.submit();");
        sb.append("  }");
        sb.append("}");
        sb.append("</script>");

        sb.append("</div></body></html>");
        return sb.toString();
    }

    private String formatSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        if (digitGroups >= units.length) digitGroups = units.length - 1;
        return String.format(Locale.ENGLISH, "%.1f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    // --- Lightweight FTP Server Handler (for Windows File Explorer) ---

    private void handleFtpClient(Socket client) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
             OutputStream writer = client.getOutputStream()) {

            sendFtpReply(writer, 220, "KTS File Explorer FTP Server ready");

            File currentFtpDir = rootDir;
            ServerSocket pasvServer = null;

            String line;
            while ((line = reader.readLine()) != null) {
                int space = line.indexOf(' ');
                String cmd = (space != -1 ? line.substring(0, space) : line).toUpperCase(Locale.ENGLISH).trim();
                String arg = space != -1 ? line.substring(space + 1).trim() : "";

                switch (cmd) {
                    case "USER":
                        sendFtpReply(writer, 331, "User name okay, need password");
                        break;
                    case "PASS":
                        sendFtpReply(writer, 230, "User logged in, proceed");
                        break;
                    case "SYST":
                        sendFtpReply(writer, 215, "UNIX Type: L8");
                        break;
                    case "FEAT":
                        sendFtpReply(writer, 211, "Extensions supported:\n UTF8\n211 END");
                        break;
                    case "OPTS":
                        sendFtpReply(writer, 200, "OPTS UTF8 ON");
                        break;
                    case "PWD":
                        String rel = currentFtpDir.getAbsolutePath().substring(rootDir.getAbsolutePath().length());
                        if (rel.isEmpty()) rel = "/";
                        sendFtpReply(writer, 257, "\"" + rel.replace('\\', '/') + "\" is current directory");
                        break;
                    case "CWD":
                        File target;
                        if (arg.startsWith("/")) {
                            target = new File(rootDir, arg.substring(1));
                        } else {
                            target = new File(currentFtpDir, arg);
                        }
                        if (target.exists() && target.isDirectory()) {
                            currentFtpDir = target;
                            sendFtpReply(writer, 250, "Directory changed to " + arg);
                        } else {
                            sendFtpReply(writer, 550, "Directory not found");
                        }
                        break;
                    case "CDUP":
                        if (!currentFtpDir.equals(rootDir) && currentFtpDir.getParentFile() != null) {
                            currentFtpDir = currentFtpDir.getParentFile();
                        }
                        sendFtpReply(writer, 250, "Directory changed to parent");
                        break;
                    case "TYPE":
                        sendFtpReply(writer, 200, "Type set to " + arg);
                        break;
                    case "PASV":
                        if (pasvServer != null && !pasvServer.isClosed()) {
                            pasvServer.close();
                        }
                        pasvServer = new ServerSocket(0);
                        int pPort = pasvServer.getLocalPort();
                        String localIp = client.getLocalAddress().getHostAddress();
                        String[] ipParts = localIp.split("\\.");
                        int p1 = pPort / 256;
                        int p2 = pPort % 256;
                        sendFtpReply(writer, 227, String.format(Locale.ENGLISH, "Entering Passive Mode (%s,%s,%s,%s,%d,%d)",
                                ipParts[0], ipParts[1], ipParts[2], ipParts[3], p1, p2));
                        break;
                    case "LIST":
                    case "NLST":
                        sendFtpReply(writer, 150, "Opening ASCII mode data connection for file list");
                        if (pasvServer != null) {
                            try (Socket dataSocket = pasvServer.accept();
                                 OutputStream dataOut = dataSocket.getOutputStream()) {
                                SimpleDateFormat ftpDate = new SimpleDateFormat("MMM dd HH:mm", Locale.ENGLISH);
                                File[] fList = currentFtpDir.listFiles();
                                if (fList != null) {
                                    for (File f : fList) {
                                        String perms = f.isDirectory() ? "drwxr-xr-x" : "-rw-r--r--";
                                        String lineEntry = String.format(Locale.ENGLISH, "%s 1 owner group %d %s %s\r\n",
                                                perms, f.length(), ftpDate.format(new Date(f.lastModified())), f.getName());
                                        dataOut.write(lineEntry.getBytes(StandardCharsets.UTF_8));
                                    }
                                }
                                dataOut.flush();
                            }
                            pasvServer.close();
                            pasvServer = null;
                            sendFtpReply(writer, 226, "Transfer complete");
                        } else {
                            sendFtpReply(writer, 425, "Can't open data connection");
                        }
                        break;
                    case "RETR":
                        File fToDownload = new File(currentFtpDir, arg);
                        if (!fToDownload.exists() || !fToDownload.isFile()) {
                            sendFtpReply(writer, 550, "File not found");
                            break;
                        }
                        sendFtpReply(writer, 150, "Opening BINARY mode data connection for " + arg);
                        if (pasvServer != null) {
                            try (Socket dataSocket = pasvServer.accept();
                                 OutputStream dataOut = dataSocket.getOutputStream();
                                 FileInputStream fis = new FileInputStream(fToDownload)) {
                                byte[] buf = new byte[64 * 1024];
                                int read;
                                while ((read = fis.read(buf)) > 0) {
                                    dataOut.write(buf, 0, read);
                                }
                                dataOut.flush();
                            }
                            pasvServer.close();
                            pasvServer = null;
                            sendFtpReply(writer, 226, "Transfer complete");
                        } else {
                            sendFtpReply(writer, 425, "Can't open data connection");
                        }
                        break;
                    case "STOR":
                        File fToUpload = new File(currentFtpDir, arg);
                        sendFtpReply(writer, 150, "Ready to receive data");
                        if (pasvServer != null) {
                            try (Socket dataSocket = pasvServer.accept();
                                 InputStream dataIn = dataSocket.getInputStream();
                                 FileOutputStream fos = new FileOutputStream(fToUpload)) {
                                byte[] buf = new byte[64 * 1024];
                                int read;
                                while ((read = dataIn.read(buf)) > 0) {
                                    fos.write(buf, 0, read);
                                }
                                fos.flush();
                            }
                            pasvServer.close();
                            pasvServer = null;
                            sendFtpReply(writer, 226, "Transfer complete");
                        } else {
                            sendFtpReply(writer, 425, "Can't open data connection");
                        }
                        break;
                    case "DELE":
                        File fToDel = new File(currentFtpDir, arg);
                        if (fToDel.delete()) {
                            sendFtpReply(writer, 250, "File deleted");
                        } else {
                            sendFtpReply(writer, 550, "Failed to delete file");
                        }
                        break;
                    case "MKD":
                        File newFolder = new File(currentFtpDir, arg);
                        if (newFolder.mkdirs()) {
                            sendFtpReply(writer, 257, "\"" + arg + "\" created");
                        } else {
                            sendFtpReply(writer, 550, "Failed to create directory");
                        }
                        break;
                    case "RMD":
                        File delDir = new File(currentFtpDir, arg);
                        if (delDir.delete()) {
                            sendFtpReply(writer, 250, "Directory removed");
                        } else {
                            sendFtpReply(writer, 550, "Failed to remove directory");
                        }
                        break;
                    case "QUIT":
                        sendFtpReply(writer, 221, "Goodbye");
                        return;
                    default:
                        sendFtpReply(writer, 502, "Command not implemented");
                        break;
                }
            }
        } catch (Exception ignored) {}
    }

    private void sendFtpReply(OutputStream out, int code, String message) throws Exception {
        String reply = code + " " + message + "\r\n";
        out.write(reply.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
}
