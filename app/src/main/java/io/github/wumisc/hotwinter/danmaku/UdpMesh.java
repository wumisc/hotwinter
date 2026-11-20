package io.github.wumisc.hotwinter.danmaku;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Build;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.github.wumisc.hotwinter.data.Logbook;

/**
 * 局域网 UDP 广播：同房间设备互发弹幕。
 * 收发用两个 socket——接收口固定、发送口临时——避免部分机型固定口发广播失败；
 * 同时发有限广播、各网卡子网广播、DHCP 广播地址和可选的对方 IP。
 */
public final class UdpMesh {

    private static DatagramSocket receiver;
    private static DatagramSocket sender;
    private static Thread thread;
    private static WifiManager.MulticastLock multicastLock;
    private static Context appContext;
    private static volatile int port = 47821;
    private static volatile String room = "love";
    private static volatile String peerIp = "";
    private static volatile String localIp = "";
    /** 自己发出的包也会被本机收到，按消息 id 短期去重，避免同一条弹幕显示多遍。 */
    private static final Map<String, Long> RECENT = new ConcurrentHashMap<>();

    private UdpMesh() {
    }

    /** Android 17（API 37）起，局域网访问必须拿到这个运行时权限，否则 UDP 会被网络栈直接拦掉。 */
    public static boolean hasPermission(Context context) {
        if (Build.VERSION.SDK_INT < 37) return true;
        try {
            return context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static synchronized void start(Context context, int newPort, String newRoom, String newPeerIp) {
        if (!hasPermission(context)) {
            DmBus.publishStatus("需要「本地网络」权限才能收发局域网弹幕");
            return;
        }
        if (thread != null && thread.isAlive() && port == newPort) {
            room = newRoom;
            peerIp = newPeerIp;
            return;
        }
        stop();
        port = newPort;
        room = newRoom;
        peerIp = newPeerIp;
        appContext = context.getApplicationContext();
        acquireMulticastLock(appContext);

        try {
            DatagramSocket recv = new DatagramSocket(null);
            recv.setReuseAddress(true);
            recv.setBroadcast(true);
            recv.bind(new InetSocketAddress(InetAddress.getByName("0.0.0.0"), newPort));
            receiver = recv;

            DatagramSocket send = new DatagramSocket();
            send.setBroadcast(true);
            sender = send;
        } catch (IOException | RuntimeException e) {
            DmBus.publishStatus("UDP 端口被占用或不可用：" + e.getMessage());
            closeQuietly();
            releaseMulticastLock();
            return;
        }

        localIp = findLocalIpv4();
        final DatagramSocket bound = receiver;
        thread = new Thread(() -> {
            byte[] buffer = new byte[2048];
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    bound.receive(packet);
                    String raw = new String(packet.getData(), packet.getOffset(), packet.getLength(),
                            StandardCharsets.UTF_8);
                    DmMessage message = DmMessage.decode(raw);
                    if (message != null && (room.isEmpty() || message.room.equals(room))) {
                        Long sentAt = RECENT.get(message.id);
                        if (sentAt != null
                                && android.os.SystemClock.elapsedRealtime() - sentAt < 3000) {
                            continue;
                        }
                        DmBus.publish(message);
                        Logbook.log("dm", "收到 UDP 弹幕 " + message.id + " " + message.text);
                    }
                } catch (IOException e) {
                    if (!bound.isClosed()) {
                        DmBus.publishStatus("UDP 接收失败：" + e.getMessage());
                    }
                    break;
                }
            }
        }, "hotwinter-udp");
        thread.start();
        DmBus.publishStatus("局域网监听中 · 端口 " + port + " · 本机 " + localIp);
        Logbook.log("dm", "UDP 监听 " + port + " 房间 " + room + " 本机 " + localIp);
    }

    public static synchronized void stop() {
        closeQuietly();
        releaseMulticastLock();
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
        localIp = "";
    }

    private static void closeQuietly() {
        if (sender != null) {
            sender.close();
            sender = null;
        }
        if (receiver != null) {
            receiver.close();
            receiver = null;
        }
    }

    public static boolean active() {
        return receiver != null && !receiver.isClosed() && thread != null && thread.isAlive();
    }

    public static String localIp() {
        return localIp;
    }

    /** 返回 true 表示至少从一个出口发出去了。 */
    public static boolean send(DmMessage message) {
        DatagramSocket out = sender;
        if (out == null || out.isClosed() || message == null) return false;
        if (RECENT.size() > 80) pruneRecent();
        RECENT.put(message.id, android.os.SystemClock.elapsedRealtime());

        byte[] data = message.encode().getBytes(StandardCharsets.UTF_8);
        List<InetAddress> targets = targets();
        int ok = 0;
        for (InetAddress address : targets) {
            try {
                out.send(new DatagramPacket(data, data.length, address, port));
                ok++;
            } catch (IOException | RuntimeException e) {
                Logbook.log("dm", "广播到 " + address.getHostAddress() + " 失败：" + e.getMessage());
            }
        }
        Logbook.log("dm", "广播弹幕 " + message.id + " 出口 " + ok + "/" + targets.size());
        return ok > 0;
    }

    private static List<InetAddress> targets() {
        Set<InetAddress> targets = new LinkedHashSet<>();
        try {
            targets.add(InetAddress.getByName("255.255.255.255"));
        } catch (IOException ignored) {
            // 忽略
        }
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback()) continue;
                for (InterfaceAddress ia : nif.getInterfaceAddresses()) {
                    InetAddress broadcast = ia.getBroadcast();
                    if (broadcast != null) targets.add(broadcast);
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // 读不到网卡就只发有限广播
        }
        InetAddress dhcp = dhcpBroadcast();
        if (dhcp != null) targets.add(dhcp);
        InetAddress peer = parseIp(peerIp);
        if (peer != null) targets.add(peer);
        return new ArrayList<>(targets);
    }

    /** Wi-Fi 的 DHCP 信息里带子网掩码，补上部分机型 InterfaceAddress 拿不到广播地址的情况。 */
    private static InetAddress dhcpBroadcast() {
        try {
            if (appContext == null) return null;
            WifiManager wifi = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
            if (wifi == null) return null;
            android.net.DhcpInfo info = wifi.getDhcpInfo();
            if (info == null || info.ipAddress == 0 || info.netmask == 0) return null;
            int broadcast = (info.ipAddress & info.netmask) | ~info.netmask;
            return intToAddress(broadcast);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static InetAddress intToAddress(int value) {
        try {
            byte[] bytes = new byte[]{
                    (byte) (value & 0xff),
                    (byte) ((value >> 8) & 0xff),
                    (byte) ((value >> 16) & 0xff),
                    (byte) ((value >> 24) & 0xff)
            };
            return InetAddress.getByAddress(bytes);
        } catch (IOException e) {
            return null;
        }
    }

    private static InetAddress parseIp(String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        String value = raw.trim();
        String[] parts = value.split("\\.");
        if (parts.length != 4) return null;
        try {
            for (String part : parts) {
                int number = Integer.parseInt(part);
                if (number < 0 || number > 255) return null;
            }
            return InetAddress.getByName(value);
        } catch (IOException | NumberFormatException e) {
            return null;
        }
    }

    private static String findLocalIpv4() {
        try {
            for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nif.isUp() || nif.isLoopback()) continue;
                for (InterfaceAddress ia : nif.getInterfaceAddresses()) {
                    InetAddress address = ia.getAddress();
                    if (address instanceof java.net.Inet4Address && !address.isLoopbackAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (IOException ignored) {
            // 忽略
        }
        return "未知";
    }

    private static void acquireMulticastLock(Context context) {
        try {
            WifiManager wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            if (wifi == null) return;
            multicastLock = wifi.createMulticastLock("hotwinter-danmaku");
            multicastLock.setReferenceCounted(false);
            multicastLock.acquire();
        } catch (RuntimeException ignored) {
            // 没有权限或没有 Wi-Fi 时忽略，不影响单播
        }
    }

    private static void releaseMulticastLock() {
        try {
            if (multicastLock != null) {
                multicastLock.release();
                multicastLock = null;
            }
        } catch (RuntimeException ignored) {
            // 忽略
        }
    }

    private static void pruneRecent() {
        long now = android.os.SystemClock.elapsedRealtime();
        RECENT.entrySet().removeIf(entry -> now - entry.getValue() > 6000);
    }
}
