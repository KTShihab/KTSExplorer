package com.kts.fileexplorer.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Local Area Network (LAN) Scanner to discover PCs and devices with SMB (port 445)
 * or FTP (port 21) enabled on the same Wi-Fi network.
 */
public class LanScanner {

    public interface ScanCallback {
        void onDeviceFound(NetworkServerInfo server);
        void onScanComplete(List<NetworkServerInfo> foundServers);
    }

    public static String getLocalIpAddress(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                Network activeNetwork = cm.getActiveNetwork();
                if (activeNetwork != null) {
                    LinkProperties lp = cm.getLinkProperties(activeNetwork);
                    if (lp != null) {
                        for (LinkAddress la : lp.getLinkAddresses()) {
                            InetAddress addr = la.getAddress();
                            if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                                return addr.getHostAddress();
                            }
                        }
                    }
                }
            }

            // Fallback: network interfaces
            for (NetworkInterface intf : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress addr : Collections.list(intf.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }

    public static List<String> getLocalSubnets() {
        List<String> subnets = new ArrayList<>();
        try {
            for (NetworkInterface intf : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (intf.isLoopback() || !intf.isUp()) continue;
                for (InetAddress addr : Collections.list(intf.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        String ip = addr.getHostAddress();
                        int lastDot = ip.lastIndexOf('.');
                        if (lastDot > 0) {
                            String sub = ip.substring(0, lastDot);
                            if (!subnets.contains(sub) && !sub.startsWith("127.")) {
                                subnets.add(sub);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return subnets;
    }

    public static List<String> getAllLocalIps() {
        List<String> ips = new ArrayList<>();
        try {
            for (NetworkInterface intf : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (intf.isLoopback() || !intf.isUp()) continue;
                for (InetAddress addr : Collections.list(intf.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        ips.add(addr.getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {}
        return ips;
    }

    public static void scanLocalNetwork(Context context, ScanCallback callback) {
        List<String> subnets = getLocalSubnets();
        List<String> myIps = getAllLocalIps();
        if (subnets.isEmpty()) {
            String defaultIp = getLocalIpAddress(context);
            int lastDot = defaultIp.lastIndexOf('.');
            if (lastDot > 0) {
                subnets.add(defaultIp.substring(0, lastDot));
            }
        }

        if (subnets.isEmpty()) {
            if (callback != null) callback.onScanComplete(new ArrayList<>());
            return;
        }

        ExecutorService pool = Executors.newFixedThreadPool(48);
        List<NetworkServerInfo> foundList = Collections.synchronizedList(new ArrayList<>());

        for (String subnet : subnets) {
            for (int i = 1; i <= 254; i++) {
                final String targetIp = subnet + "." + i;
                if (myIps.contains(targetIp)) {
                    continue;
                }

                pool.execute(() -> {
                    // Check SMB ports 445 (Direct SMB) and 139 (NetBIOS Session)
                    if (isPortOpen(targetIp, 445, 800) || isPortOpen(targetIp, 139, 800)) {
                        String name = resolveHostName(targetIp);
                        NetworkServerInfo server = new NetworkServerInfo(
                                name,
                                NetworkServerInfo.TYPE_SMB,
                                targetIp,
                                445,
                                "",
                                "",
                                "WORKGROUP",
                                ""
                        );
                        foundList.add(server);
                        if (callback != null) {
                            callback.onDeviceFound(server);
                        }
                        return;
                    }

                    // Check FTP port 21 (skip standard router gateways ending in .1 if SMB not present)
                    if (!targetIp.endsWith(".1") && isPortOpen(targetIp, 21, 800)) {
                        String name = resolveHostName(targetIp);
                        NetworkServerInfo server = new NetworkServerInfo(
                                name + " (FTP)",
                                NetworkServerInfo.TYPE_FTP,
                                targetIp,
                                21,
                                "",
                                "",
                                "",
                                ""
                        );
                        foundList.add(server);
                        if (callback != null) {
                            callback.onDeviceFound(server);
                        }
                    }
                });
            }
        }

        pool.shutdown();
        new Thread(() -> {
            try {
                pool.awaitTermination(8, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}
            if (callback != null) {
                callback.onScanComplete(new ArrayList<>(foundList));
            }
        }).start();
    }

    private static boolean isPortOpen(String ip, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String resolveHostName(String ip) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            String host = addr.getCanonicalHostName();
            if (host != null && !host.equals(ip)) {
                return host;
            }
        } catch (Exception ignored) {}
        return "Computer (" + ip + ")";
    }
}
