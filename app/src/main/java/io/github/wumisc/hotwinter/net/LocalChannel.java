package io.github.wumisc.hotwinter.net;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.wumisc.hotwinter.data.Logbook;
import io.github.wumisc.hotwinter.data.SettingsStore;

/**
 * 本地频道：一台手机开房，其他手机在附近列表里选择加入；不展示 IP/端口。
 *
 * 发现：UDP 广播（每 3 秒一个 beacon），口令加入时发 lookup，房主回 found。
 * 控制：TCP 一行一个 JSON，房主每次收到 join 都要 UI 确认（除非前台自动同意开关打开）。
 * 第一版只做“创建/发现/加入/成员列表”，聊天和远程加热在后续阶段接同一套通道。
 */
public final class LocalChannel {

    public static final int DISCOVERY_PORT = 47830;
    public static final int DEFAULT_TCP_PORT = 47822;
    public static final int MAX_MEMBERS = 8;

    private static final String TAG = "channel";
    private static final LocalChannel INSTANCE = new LocalChannel();

    public interface Listener {
        void onPeers(List<Peer> peers);

        void onJoinRequest(JoinRequest request);

        void onMembers(List<String> members);

        void onStatus(String text);

        void onJoined(String channelName);
    }

    public static final class Peer {
        public final String id;
        public final String hostName;
        public final String channelName;
        public final String address;
        public final int port;
        public final int memberCount;
        public final int maxMembers;
        public final long lastSeen;

        Peer(String id, String hostName, String channelName, String address, int port,
             int memberCount, int maxMembers, long lastSeen) {
            this.id = id;
            this.hostName = hostName;
            this.channelName = channelName;
            this.address = address;
            this.port = port;
            this.memberCount = memberCount;
            this.maxMembers = maxMembers;
            this.lastSeen = lastSeen;
        }
    }

    public static final class JoinRequest {
        public final String id;
        public final String name;
        public final String via;

        JoinRequest(String id, String name, String via) {
            this.id = id;
            this.name = name;
            this.via = via;
        }
    }

    private static final class Member {
        final String id;
        final String name;
        final Socket socket;
        final BufferedWriter out;

        Member(String id, String name, Socket socket, BufferedWriter out) {
            this.id = id;
            this.name = name;
            this.socket = socket;
            this.out = out;
        }
    }

    private static final class Pending {
        final String id;
        final String name;
        final String via;
        final Socket socket;
        final BufferedReader reader;
        final BufferedWriter out;
        final ScheduledFuture<?> timeout;

        Pending(String id, String name, String via, Socket socket,
                BufferedReader reader, BufferedWriter out, ScheduledFuture<?> timeout) {
            this.id = id;
            this.name = name;
            this.via = via;
            this.socket = socket;
            this.reader = reader;
            this.out = out;
            this.timeout = timeout;
        }
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, Peer> peers = new ConcurrentHashMap<>();
    private final Map<String, Member> members = new ConcurrentHashMap<>();
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private final AtomicInteger requestSeq = new AtomicInteger();

    private volatile Listener listener;
    private volatile Context appContext;
    private volatile String selfId = "";
    private volatile String selfName = "我的手机";

    private volatile boolean hosting;
    private volatile String channelId = "";
    private volatile String channelName = "我们的冬天";
    private volatile String channelCode = "";
    private volatile int tcpPort = DEFAULT_TCP_PORT;

    private volatile ServerSocket serverSocket;
    private volatile Thread acceptThread;
    private volatile Thread discoveryThread;
    private volatile Thread beaconThread;
    private volatile DatagramSocket discoverySocket;
    private WifiManager.MulticastLock multicastLock;
    private volatile Socket guestSocket;
    private volatile Thread guestReader;
    private volatile String guestChannelName = "";
    private volatile String pendingCode = "";
    private volatile boolean scanning;
    private volatile ScheduledFuture<?> pruneFuture;

    private LocalChannel() {
    }

    public static LocalChannel get(Context context) {
        if (context != null) INSTANCE.appContext = context.getApplicationContext();
        return INSTANCE;
    }

    public static boolean hasPermission(Context context) {
        if (Build.VERSION.SDK_INT < 37) return true;
        try {
            return context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static String randomCode() {
        int value = 1000 + new java.security.SecureRandom().nextInt(9000);
        return String.valueOf(value);
    }

    public void setListener(Listener value) {
        listener = value;
    }

    public void clearListener(Listener value) {
        if (listener == value) listener = null;
    }

    public boolean hosting() {
        return hosting;
    }

    public boolean joined() {
        return guestSocket != null && guestSocket.isConnected() && !guestSocket.isClosed();
    }

    public String channelName() {
        return hosting ? channelName : guestChannelName;
    }

    public String channelCode() {
        return hosting ? channelCode : "";
    }

    public String statusText() {
        if (hosting) {
            return "频道已创建 · " + channelName
                    + " · " + (members.size() + 1) + "/" + MAX_MEMBERS + " 人";
        }
        if (joined()) return "已加入 · " + guestChannelName;
        if (scanning) return "正在找附近的人…";
        return "还没有创建或加入频道";
    }

    /** 房主：开频道。 */
    public synchronized void host(Context context, String name, String code, String nick) {
        stopAllInternal();
        if (!hasPermission(context)) {
            postStatus("需要「附近连接」权限才能创建频道");
            return;
        }
        appContext = context.getApplicationContext();
        SettingsStore store = SettingsStore.get(context);
        selfId = store.channelDeviceId();
        selfName = nick == null || nick.trim().isEmpty() ? store.channelNick() : nick.trim();
        channelId = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        channelName = name == null || name.trim().isEmpty() ? "我们的冬天" : name.trim();
        channelCode = code == null || !code.matches("\\d{4}") ? randomCode() : code;
        hosting = true;
        peers.clear();
        startServer();
        startDiscovery();
        startBeacon();
        postStatus(statusText());
        postMembers();
        Logbook.log(TAG, "开频道 " + channelName + " 口令 " + channelCode + " tcp=" + tcpPort);
    }

    /** 房主：同意/拒绝某一次加入。 */
    public void approve(String requestId, boolean accept) {
        Pending p = pending.remove(requestId);
        if (p == null) return;
        if (p.timeout != null) p.timeout.cancel(false);
        if (!accept) {
            sendLine(p.out, error("房主未同意"));
            closeQuietly(p.socket);
            postStatus("已拒绝 " + p.name);
            return;
        }
        try {
            p.socket.setSoTimeout(0);
        } catch (Exception ignored) {
            // 读线程不应被 10 秒握手超时打断
        }
        Member member = new Member(p.id, p.name, p.socket, p.out);
        members.put(member.id, member);
        sendLine(member.out, welcome());
        startMemberReader(member, p.reader);
        broadcastMembers();
        postStatus(p.name + " 加入了频道");
        Logbook.log(TAG, "同意加入 " + p.name + " " + p.id);
    }

    /** 访客：在附近列表里点某个人。 */
    public void join(Peer peer, String code, String via) {
        if (peer == null) return;
        if (!hasPermission(appContext)) {
            postStatus("需要「附近连接」权限才能加入频道");
            return;
        }
        stopGuest();
        postStatus("正在加入…");
        Thread thread = new Thread(() -> joinInternal(peer, code, via), "channel-guest-join");
        thread.start();
    }

    /** 访客：输入 4 位口令 / 扫码后调用。 */
    public void joinByCode(String code) {
        if (code == null || !code.matches("\\d{4}")) {
            postStatus("口令是 4 位数字");
            return;
        }
        if (!hasPermission(appContext)) {
            postStatus("需要「附近连接」权限才能加入频道");
            return;
        }
        pendingCode = code;
        startScan();
        postStatus("正在找口令 " + code + " 的频道…");
        sendLookup(code);
        scheduler.schedule(() -> {
            if (pendingCode.equals(code) && !joined()) {
                pendingCode = "";
                postStatus("没找到这个频道，确认对方已创建频道");
            }
        }, 4, TimeUnit.SECONDS);
    }

    /** 打开频道页时调用；只负责发现，不影响已经建立的 TCP。 */
    public synchronized void startScan() {
        if (appContext == null) return;
        if (!hasPermission(appContext)) {
            postStatus("需要「附近连接」权限才能扫描附近");
            return;
        }
        scanning = true;
        startDiscovery();
        ensurePruner();
    }

    public synchronized void stopScan() {
        scanning = false;
        if (!hosting) {
            closeDiscovery();
        }
    }

    public synchronized void stopAll() {
        stopAllInternal();
        postStatus(statusText());
    }

    public void leave() {
        stopGuest();
        postStatus("已离开频道");
    }

    private void joinInternal(Peer peer, String code, String via) {
        Socket socket = new Socket();
        BufferedReader reader = null;
        try {
            socket.connect(new InetSocketAddress(peer.address, peer.port), 5000);
            socket.setSoTimeout(70000);
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8));
            JSONObject join = new JSONObject();
            join.put("t", "join");
            join.put("id", SettingsStore.get(appContext).channelDeviceId());
            join.put("name", SettingsStore.get(appContext).channelNick());
            join.put("code", code == null ? "" : code);
            join.put("via", via == null ? "nearby" : via);
            sendLine(out, join);

            reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            String line = reader.readLine();
            if (line == null) throw new IOException("对方断开了连接");
            JSONObject response = new JSONObject(line);
            if (!"welcome".equals(response.optString("t"))) {
                throw new IOException(response.optString("msg", "加入失败"));
            }
            socket.setSoTimeout(0);
            guestSocket = socket;
            guestChannelName = response.optString("channel", "频道");
            startGuestReader(reader);
            postJoined(guestChannelName);
            postMembers(parseMembers(response));
            postStatus("已加入 · " + guestChannelName);
            Logbook.log(TAG, "加入成功 " + peer.hostName + " " + guestChannelName);
        } catch (Exception e) {
            closeQuietly(socket);
            String message = e.getMessage() == null ? "连不上对方的频道" : e.getMessage();
            postStatus("加入失败：" + message);
            Logbook.log(TAG, "加入失败 " + message);
        }
    }

    private void startServer() {
        try {
            ServerSocket socket = new ServerSocket();
            socket.setReuseAddress(true);
            try {
                socket.bind(new InetSocketAddress(DEFAULT_TCP_PORT));
            } catch (IOException busy) {
                socket.bind(new InetSocketAddress(0));
            }
            serverSocket = socket;
            tcpPort = socket.getLocalPort();
            acceptThread = new Thread(() -> {
                while (hosting && !socket.isClosed()) {
                    try {
                        Socket incoming = socket.accept();
                        Thread handler = new Thread(() -> handleJoinSocket(incoming),
                                "channel-join");
                        handler.setDaemon(true);
                        handler.start();
                    } catch (IOException e) {
                        if (hosting) Logbook.log(TAG, "频道监听结束：" + e.getMessage());
                        break;
                    }
                }
            }, "channel-accept");
            acceptThread.setDaemon(true);
            acceptThread.start();
        } catch (IOException e) {
            hosting = false;
            postStatus("创建频道失败：" + e.getMessage());
        }
    }

    private void handleJoinSocket(Socket socket) {
        try {
            socket.setSoTimeout(10000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8));
            String line = reader.readLine();
            if (line == null) {
                closeQuietly(socket);
                return;
            }
            JSONObject object = new JSONObject(line);
            if (!"join".equals(object.optString("t"))) {
                sendLine(out, error("不是加入请求"));
                closeQuietly(socket);
                return;
            }
            String code = object.optString("code", "");
            if (!code.isEmpty() && !code.equals(channelCode)) {
                sendLine(out, error("口令不对"));
                closeQuietly(socket);
                return;
            }
            if (!hosting) {
                sendLine(out, error("频道已关闭"));
                closeQuietly(socket);
                return;
            }
            if (members.size() + 1 >= MAX_MEMBERS) {
                sendLine(out, error("频道已满（最多 " + MAX_MEMBERS + " 人）"));
                closeQuietly(socket);
                return;
            }
            String id = object.optString("id", "");
            if (id.isEmpty() || members.containsKey(id) || pending.containsKey(id)) {
                sendLine(out, error("这台手机已经在频道里"));
                closeQuietly(socket);
                return;
            }
            String name = object.optString("name", "附近的人");
            String via = object.optString("via", "nearby");
            String requestId = "r" + requestSeq.incrementAndGet();
            ScheduledFuture<?> timeout = scheduler.schedule(() -> {
                Pending p = pending.remove(requestId);
                if (p != null) {
                    sendLine(p.out, error("房主未同意"));
                    closeQuietly(p.socket);
                    postStatus("加入请求超时");
                }
            }, 30, TimeUnit.SECONDS);
            Pending p = new Pending(requestId, name, via, socket, reader, out, timeout);
            pending.put(requestId, p);
            postJoinRequest(new JoinRequest(requestId, name, via));
            Logbook.log(TAG, "收到加入请求 " + name + " via=" + via);
        } catch (Exception e) {
            closeQuietly(socket);
            Logbook.log(TAG, "处理加入请求失败：" + e.getMessage());
        }
    }

    private void startMemberReader(Member member, BufferedReader reader) {
        Thread thread = new Thread(() -> {
            try {
                String line;
                while (hosting && (line = reader.readLine()) != null) {
                    JSONObject object = new JSONObject(line);
                    String type = object.optString("t");
                    if ("leave".equals(type)) break;
                    if ("ping".equals(type)) sendLine(member.out, simple("pong"));
                }
            } catch (Exception ignored) {
                // 对方断开
            } finally {
                members.remove(member.id);
                closeQuietly(member.socket);
                broadcastMembers();
                postStatus(member.name + " 离开了频道");
            }
        }, "channel-member-" + member.id);
        thread.setDaemon(true);
        thread.start();
    }

    private void startGuestReader(BufferedReader reader) {
        Thread thread = new Thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    JSONObject object = new JSONObject(line);
                    String type = object.optString("t");
                    if ("members".equals(type)) {
                        postMembers(parseMembers(object));
                    } else if ("error".equals(type)) {
                        postStatus(object.optString("msg", "频道出错"));
                    } else if ("ping".equals(type)) {
                        sendLine(new BufferedWriter(new OutputStreamWriter(
                                guestSocket.getOutputStream(), StandardCharsets.UTF_8)), simple("pong"));
                    }
                }
            } catch (Exception ignored) {
                // 断开
            } finally {
                stopGuest();
                postStatus("频道已断开");
            }
        }, "channel-guest-reader");
        thread.setDaemon(true);
        guestReader = thread;
        thread.start();
    }

    private void closeDiscovery() {
        try {
            if (discoverySocket != null) discoverySocket.close();
        } catch (Exception ignored) {
            // 忽略
        }
        discoverySocket = null;
        try {
            if (multicastLock != null && multicastLock.isHeld()) multicastLock.release();
        } catch (Exception ignored) {
            // 忽略
        }
        multicastLock = null;
        Thread thread = discoveryThread;
        discoveryThread = null;
        if (thread != null) thread.interrupt();
    }

    private void startDiscovery() {
        if (discoverySocket != null && !discoverySocket.isClosed()) return;
        try {
            WifiManager wifi = (WifiManager) appContext.getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                multicastLock = wifi.createMulticastLock("hotwinter-channel");
                multicastLock.setReferenceCounted(false);
                multicastLock.acquire();
            }
            DatagramSocket socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.setBroadcast(true);
            socket.bind(new InetSocketAddress(DISCOVERY_PORT));
            discoverySocket = socket;
            discoveryThread = new Thread(() -> discoveryLoop(socket), "channel-discovery");
            discoveryThread.setDaemon(true);
            discoveryThread.start();
            ensurePruner();
        } catch (Exception e) {
            postStatus("本地发现启动失败：" + e.getMessage());
            Logbook.log(TAG, "发现失败 " + e.getMessage());
        }
    }

    private void discoveryLoop(DatagramSocket socket) {
        byte[] buffer = new byte[4096];
        while (!socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                String raw = new String(packet.getData(), packet.getOffset(), packet.getLength(),
                        StandardCharsets.UTF_8);
                JSONObject object = new JSONObject(raw);
                String type = object.optString("t");
                if ("beacon".equals(type)) {
                    if (!hosting) {
                        Peer peer = parsePeer(object, packet.getAddress().getHostAddress());
                        if (peer != null) addPeer(peer);
                    }
                } else if ("found".equals(type)) {
                    Peer peer = parsePeer(object, packet.getAddress().getHostAddress());
                    if (peer != null) {
                        addPeer(peer);
                        String code = pendingCode;
                        if (!code.isEmpty() && code.equals(object.optString("code", ""))) {
                            pendingCode = "";
                            join(peer, code, "code");
                        }
                    }
                } else if ("lookup".equals(type) && hosting) {
                    String code = object.optString("code", "");
                    if (code.equals(channelCode)) {
                        JSONObject reply = beacon();
                        reply.put("t", "found");
                        reply.put("code", code);
                        sendPacket(socket, reply.toString(), packet.getAddress(), packet.getPort());
                    }
                }
            } catch (Exception e) {
                if (!socket.isClosed()) Logbook.log(TAG, "发现包处理失败：" + e.getMessage());
            }
        }
    }

    private void startBeacon() {
        beaconThread = new Thread(() -> {
            while (hosting) {
                try {
                    String data = beacon().toString();
                    for (InetAddress address : broadcastAddresses()) {
                        sendPacket(discoverySocket, data, address, DISCOVERY_PORT);
                    }
                } catch (Exception e) {
                    Logbook.log(TAG, "广播失败：" + e.getMessage());
                }
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "channel-beacon");
        beaconThread.setDaemon(true);
        beaconThread.start();
    }

    private JSONObject beacon() throws Exception {
        JSONObject object = new JSONObject();
        object.put("t", "beacon");
        object.put("ch", channelId);
        object.put("name", channelName);
        object.put("host", selfId);
        object.put("hostName", selfName);
        object.put("port", tcpPort);
        object.put("count", members.size() + 1);
        object.put("max", MAX_MEMBERS);
        return object;
    }

    private Peer parsePeer(JSONObject object, String address) {
        String hostId = object.optString("host", "");
        if (hostId.isEmpty() || hostId.equals(selfId)) return null;
        return new Peer(hostId,
                object.optString("hostName", "附近的人"),
                object.optString("name", "频道"),
                address,
                object.optInt("port", DEFAULT_TCP_PORT),
                object.optInt("count", 1),
                object.optInt("max", MAX_MEMBERS),
                System.currentTimeMillis());
    }

    private void addPeer(Peer peer) {
        peers.put(peer.id, peer);
        postPeers();
    }

    private void ensurePruner() {
        if (pruneFuture != null && !pruneFuture.isCancelled()) return;
        pruneFuture = scheduler.scheduleWithFixedDelay(() -> {
            long now = System.currentTimeMillis();
            boolean changed = peers.entrySet().removeIf(entry -> now - entry.getValue().lastSeen > 20000);
            if (changed) postPeers();
        }, 5, 5, TimeUnit.SECONDS);
    }

    private void sendLookup(String code) {
        try {
            JSONObject object = new JSONObject();
            object.put("t", "lookup");
            object.put("code", code);
            object.put("from", selfId);
            String data = object.toString();
            for (InetAddress address : broadcastAddresses()) {
                sendPacket(discoverySocket, data, address, DISCOVERY_PORT);
            }
        } catch (Exception e) {
            Logbook.log(TAG, "口令查找失败：" + e.getMessage());
        }
    }

    private void sendPacket(DatagramSocket socket, String data, InetAddress address, int port) {
        if (socket == null || socket.isClosed()) return;
        try {
            byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(bytes, bytes.length, address, port));
        } catch (Exception ignored) {
            // 单个地址发不出去不影响其他地址
        }
    }

    private List<InetAddress> broadcastAddresses() {
        Set<InetAddress> targets = new LinkedHashSet<>();
        try {
            targets.add(InetAddress.getByName("255.255.255.255"));
        } catch (Exception ignored) {
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
        } catch (Exception ignored) {
            // 只发有限广播
        }
        return new ArrayList<>(targets);
    }

    private JSONObject welcome() {
        JSONObject object = new JSONObject();
        try {
            object.put("t", "welcome");
            object.put("channel", channelName);
            object.put("id", channelId);
            object.put("name", selfName);
            object.put("members", membersArray());
        } catch (Exception ignored) {
            // 字段都是常量
        }
        return object;
    }

    private JSONArray membersArray() {
        JSONArray array = new JSONArray();
        try {
            JSONObject self = new JSONObject();
            self.put("id", selfId);
            self.put("name", selfName);
            array.put(self);
        } catch (Exception ignored) {
            // 忽略
        }
        for (Member member : members.values()) {
            try {
                JSONObject object = new JSONObject();
                object.put("id", member.id);
                object.put("name", member.name);
                array.put(object);
            } catch (Exception ignored) {
                // 跳过
            }
        }
        return array;
    }

    private void broadcastMembers() {
        JSONObject object = new JSONObject();
        try {
            object.put("t", "members");
            object.put("list", membersArray());
        } catch (Exception ignored) {
            return;
        }
        for (Member member : members.values()) {
            sendLine(member.out, object);
        }
        postMembers();
    }

    private List<String> parseMembers(JSONObject object) {
        List<String> list = new ArrayList<>();
        JSONArray array = object.optJSONArray("members");
        if (array == null) array = object.optJSONArray("list");
        if (array == null) return list;
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null) list.add(item.optString("name", "成员"));
        }
        return list;
    }

    private JSONObject error(String message) {
        JSONObject object = new JSONObject();
        try {
            object.put("t", "error");
            object.put("msg", message);
        } catch (Exception ignored) {
            // 忽略
        }
        return object;
    }

    private JSONObject simple(String type) {
        JSONObject object = new JSONObject();
        try {
            object.put("t", type);
        } catch (Exception ignored) {
            // 忽略
        }
        return object;
    }

    private void sendLine(BufferedWriter out, JSONObject object) {
        if (out == null) return;
        try {
            synchronized (out) {
                out.write(object.toString());
                out.newLine();
                out.flush();
            }
        } catch (Exception ignored) {
            // 对方已断开
        }
    }

    private void stopGuest() {
        Socket socket = guestSocket;
        guestSocket = null;
        guestChannelName = "";
        if (socket != null) {
            try {
                BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
                        socket.getOutputStream(), StandardCharsets.UTF_8));
                sendLine(out, simple("leave"));
            } catch (Exception ignored) {
                // 忽略
            }
            closeQuietly(socket);
        }
        Thread reader = guestReader;
        guestReader = null;
        if (reader != null) reader.interrupt();
    }

    private synchronized void stopAllInternal() {
        hosting = false;
        closeQuietly(serverSocket);
        serverSocket = null;
        closeDiscovery();
        if (beaconThread != null) beaconThread.interrupt();
        beaconThread = null;
        if (acceptThread != null) acceptThread.interrupt();
        acceptThread = null;
        if (discoveryThread != null) discoveryThread.interrupt();
        discoveryThread = null;
        for (Pending p : pending.values()) {
            closeQuietly(p.socket);
        }
        pending.clear();
        for (Member member : members.values()) {
            closeQuietly(member.socket);
        }
        members.clear();
        stopGuest();
        peers.clear();
        pendingCode = "";
        scanning = false;
        if (pruneFuture != null) {
            pruneFuture.cancel(false);
            pruneFuture = null;
        }
    }

    private void closeQuietly(ServerSocket socket) {
        if (socket == null) return;
        try {
            socket.close();
        } catch (IOException ignored) {
            // 忽略
        }
    }

    private void closeQuietly(Socket socket) {
        if (socket == null) return;
        try {
            socket.close();
        } catch (IOException ignored) {
            // 忽略
        }
    }

    private void postStatus(String text) {
        main.post(() -> {
            Listener value = listener;
            if (value != null) value.onStatus(text);
        });
    }

    private void postPeers() {
        List<Peer> copy = new ArrayList<>(peers.values());
        copy.sort(Comparator.comparingLong((Peer p) -> p.lastSeen).reversed());
        main.post(() -> {
            Listener value = listener;
            if (value != null) value.onPeers(copy);
        });
    }

    private void postJoinRequest(JoinRequest request) {
        main.post(() -> {
            Listener value = listener;
            if (value != null) value.onJoinRequest(request);
        });
    }

    private void postMembers() {
        List<String> names = new ArrayList<>();
        if (hosting && !selfName.isEmpty()) names.add(selfName + "（我）");
        for (Member member : members.values()) names.add(member.name);
        main.post(() -> {
            Listener value = listener;
            if (value != null) value.onMembers(names);
        });
    }

    private void postMembers(List<String> names) {
        main.post(() -> {
            Listener value = listener;
            if (value != null) value.onMembers(names);
        });
    }

    private void postJoined(String name) {
        main.post(() -> {
            Listener value = listener;
            if (value != null) value.onJoined(name);
        });
    }
}
