package io.github.wumisc.hotwinter.cloud;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.wumisc.hotwinter.danmaku.DanmakuOverlayService;
import io.github.wumisc.hotwinter.danmaku.DmBus;
import io.github.wumisc.hotwinter.danmaku.DmMessage;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * 一条 WebSocket 的云端情侣房间。连接、心跳、断线重连、状态与弹幕解析都在这里，
 * 界面只读快照，不直接摸 socket。
 */
public final class CloudSync {

    private static final String TAG = "CloudSync";
    private static final long HEARTBEAT_MS = 20_000L;
    private static final long STATUS_MS = 60_000L;
    private static final long RECONNECT_BASE_MS = 5_000L;
    private static final long RECONNECT_MAX_MS = 60_000L;
    private static final long PRESENCE_TIMEOUT_MS = 45_000L;

    public interface Listener {
        void onCloudState();

        default void onCloudDanmaku(String name, String text) {
        }
    }

    public static final class Partner {
        public String id = "";
        public String name = "";
        public boolean online;
        public boolean hasStatus;
        public int battery = -1;
        public boolean charging;
        public String network = "none";
        public boolean heating;
        public long statusAt;
        public long presenceAt;

        public Partner copy() {
            Partner out = new Partner();
            out.id = id;
            out.name = name;
            out.online = online;
            out.hasStatus = hasStatus;
            out.battery = battery;
            out.charging = charging;
            out.network = network;
            out.heating = heating;
            out.statusAt = statusAt;
            out.presenceAt = presenceAt;
            return out;
        }
    }

    public static final class Snapshot {
        public boolean connected;
        public String connectionText = "未连接";
        public String lastError = "";
        public Partner partner = new Partner();

        public boolean partnerOnline() {
            return partner.online
                    && partner.presenceAt > 0
                    && System.currentTimeMillis() - partner.presenceAt < PRESENCE_TIMEOUT_MS;
        }
    }

    private static volatile CloudSync instance;

    private final Context app;
    private final CloudPrefs prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private final Set<String> seenDm = Collections.synchronizedSet(new HashSet<>());
    private final ScheduledExecutorService heartbeat =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "hotwinter-cloud-heartbeat");
                thread.setDaemon(true);
                return thread;
            });

    private volatile WebSocket socket;
    private volatile boolean wantConnection;
    private volatile boolean connected;
    private volatile String lastError = "";
    private volatile int generation;
    private final Partner partner = new Partner();
    private final AtomicInteger reconnectAttempt = new AtomicInteger();
    private long lastStatusCheckAt;
    private long lastBatteryCareAt;
    private String lastStatusPayload = "";
    private ConnectivityManager.NetworkCallback networkCallback;

    private CloudSync(Context context) {
        this.app = context.getApplicationContext();
        this.prefs = CloudPrefs.get(context);
        heartbeat.scheduleAtFixedRate(this::heartbeatTick, HEARTBEAT_MS, HEARTBEAT_MS,
                TimeUnit.MILLISECONDS);
        ConnectivityManager manager = (ConnectivityManager)
                app.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager != null) {
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    reconnectAttempt.set(0);
                    if (wantConnection) main.post(CloudSync.this::openSocket);
                }
            };
            try {
                manager.registerDefaultNetworkCallback(networkCallback);
            } catch (Throwable ignored) {
                // 没有网络权限或系统不支持时继续用定时重连
            }
        }
    }

    public static CloudSync get(Context context) {
        CloudSync local = instance;
        if (local == null) {
            synchronized (CloudSync.class) {
                local = instance;
                if (local == null) {
                    local = new CloudSync(context);
                    instance = local;
                }
            }
        }
        return local;
    }

    public void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public synchronized Snapshot snapshot() {
        Snapshot out = new Snapshot();
        out.connected = connected;
        out.lastError = lastError;
        out.connectionText = connectionText();
        out.partner = partner.copy();
        return out;
    }

    public boolean isConnected() {
        return connected;
    }

    /** 由前台服务或页面调用。重复调用安全。 */
    public void start() {
        wantConnection = true;
        reconnectAttempt.set(0);
        openSocket();
    }

    public void stop() {
        wantConnection = false;
        reconnectAttempt.set(0);
        lastStatusPayload = "";
        generation++;
        WebSocket current = socket;
        socket = null;
        if (current != null) {
            try {
                current.close(1000, "client stop");
            } catch (Throwable ignored) {
                // 已经断开
            }
        }
        connected = false;
        main.post(this::notifyState);
    }

    public boolean sendStatus() {
        return sendStatus(false);
    }

    public boolean sendStatus(boolean force) {
        if (!prefs.sharing()) {
            lastStatusPayload = "";
            return false;
        }
        WebSocket current = socket;
        if (current == null || !connected) return false;
        try {
            JSONObject json = CloudStatusReader.read(app);
            String payload = json.toString();
            if (!force && payload.equals(lastStatusPayload)) return false;
            boolean sent = current.send(payload);
            if (sent) lastStatusPayload = payload;
            return sent;
        } catch (Throwable error) {
            Log.w(TAG, "状态发送失败", error);
            return false;
        }
    }

    /** 发云端弹幕，返回消息 id；未连接返回空。 */
    public String sendDanmaku(String text) {
        String value = text == null ? "" : text.trim().replaceAll("\\s+", " ");
        if (value.isEmpty()) return "";
        if (value.length() > 200) value = value.substring(0, 200);
        WebSocket current = socket;
        if (current == null || !connected) return "";
        String id = "c" + Long.toHexString(System.currentTimeMillis())
                + Integer.toHexString((int) (Math.random() * 0x7fffffff));
        try {
            JSONObject json = new JSONObject();
            json.put("type", "dm");
            json.put("id", id);
            json.put("text", value);
            if (current.send(json.toString())) return id;
        } catch (Throwable error) {
            Log.w(TAG, "弹幕发送失败", error);
        }
        return "";
    }

    public boolean sendNudge(String kind) {
        WebSocket current = socket;
        if (current == null || !connected) return false;
        if (!"sleep".equals(kind) && !"water".equals(kind)) return false;
        try {
            JSONObject json = new JSONObject();
            json.put("type", "nudge");
            json.put("kind", kind);
            return current.send(json.toString());
        } catch (Throwable error) {
            return false;
        }
    }

    public void refreshPresence() {
        main.post(this::notifyState);
    }

    private void openSocket() {
        if (!wantConnection || socket != null) return;
        String token = prefs.token();
        if (token.isEmpty()) {
            connected = false;
            lastError = "还没有登录";
            notifyState();
            return;
        }
        int gen = ++generation;
        String base = prefs.baseUrl();
        WebSocket ws;
        try {
            ws = CloudApi.http().newWebSocket(
                    CloudApi.websocketRequest(base, token),
                    new SocketListener(gen));
        } catch (Throwable error) {
            lastError = "连接失败：" + shortError(error);
            connected = false;
            notifyState();
            scheduleReconnect(gen);
            return;
        }
        socket = ws;
        notifyState();
    }

    static long reconnectDelay(int attempt) {
        return Math.min(RECONNECT_MAX_MS, RECONNECT_BASE_MS << Math.min(attempt, 4));
    }

    private void scheduleReconnect(int gen) {
        int attempt = reconnectAttempt.getAndIncrement();
        long delay = reconnectDelay(attempt) + ThreadLocalRandom.current().nextLong(1_000L);
        main.postDelayed(() -> {
            if (wantConnection && gen == generation && socket == null) openSocket();
        }, delay);
    }

    private void heartbeatTick() {
        if (!wantConnection) return;
        WebSocket current = socket;
        if (current == null) return;
        try {
            JSONObject ping = new JSONObject();
            ping.put("type", "ping");
            current.send(ping.toString());
            long now = System.currentTimeMillis();
            if (prefs.sharing() && now - lastStatusCheckAt >= STATUS_MS) {
                lastStatusCheckAt = now;
                sendStatus();
            }
        } catch (Throwable error) {
            Log.w(TAG, "心跳失败", error);
        }
    }

    private String connectionText() {
        if (connected) return "已连接云端";
        if (!prefs.hasToken()) return "未登录";
        if (!lastError.isEmpty()) return lastError;
        return wantConnection ? "正在连接云端…" : "未连接";
    }

    private void notifyState() {
        for (Listener listener : listeners) {
            try {
                listener.onCloudState();
            } catch (Throwable error) {
                Log.w(TAG, "云端界面回调异常", error);
            }
        }
    }

    private void notifyDanmaku(String name, String text) {
        for (Listener listener : listeners) {
            try {
                listener.onCloudDanmaku(name, text);
            } catch (Throwable error) {
                Log.w(TAG, "云端弹幕回调异常", error);
            }
        }
        // 没有悬浮弹幕时，至少让通知栏接住这条消息，不让它只躺在最近弹幕里。
        if (!DanmakuOverlayService.RUNNING) {
            String from = name == null || name.isEmpty() ? "TA" : name;
            CloudNotifier.remind(app, from + " 发来一条云端弹幕", text, 80);
        }
    }

    private synchronized void updatePartner(JSONObject status) {
        if (status == null) return;
        boolean hadStatus = partner.hasStatus;
        boolean wasHeating = partner.heating;
        boolean wasCharging = partner.charging;
        int lastBattery = partner.battery;
        partner.hasStatus = true;
        partner.battery = status.optInt("battery", -1);
        partner.charging = status.optBoolean("charging", false);
        String network = status.optString("network", "none");
        partner.network = "wifi".equals(network) || "mobile".equals(network) ? network : "none";
        partner.heating = status.optBoolean("heating", false);
        partner.statusAt = status.optLong("at", 0L);
        String name = status.optString("name", "");
        if (!name.isEmpty()) partner.name = name;
        String id = status.optString("userId", "");
        if (!id.isEmpty()) partner.id = id;
        maybeCare(hadStatus, wasHeating, lastBattery, wasCharging);
    }

    private void maybeCare(boolean hadStatus, boolean wasHeating,
                           int lastBattery, boolean wasCharging) {
        if (!hadStatus) return;
        String name = partner.name == null || partner.name.isEmpty() ? "TA" : partner.name;
        if (prefs.careHeat() && CloudCouple.shouldAlertHeat(hadStatus, wasHeating, partner.heating)) {
            CloudNotifier.remind(app, name + "开始加热了",
                    "TA 的手机开始发热，记得提醒别烫手。", 71);
        }
        long now = System.currentTimeMillis();
        if (prefs.careBattery()
                && CloudCouple.shouldAlertBattery(hadStatus, lastBattery, wasCharging,
                partner.battery, partner.charging, now, lastBatteryCareAt)) {
            lastBatteryCareAt = now;
            CloudNotifier.remind(app, name + "电量只剩 " + partner.battery + "%",
                    "还没充电，提醒 TA 充上电或少用一点。", 72);
        }
    }

    private void onDm(JSONObject message) {
        if (message == null) return;
        String id = message.optString("id", "");
        if (!id.isEmpty() && !seenDm.add(id)) return;
        if (seenDm.size() > 200) seenDm.clear();
        String name = message.optString("name", "TA");
        String text = message.optString("text", "");
        if (text.isEmpty()) return;
        if (!DmBus.publish(new DmMessage("cloud", name, text, id))) return;
        notifyDanmaku(name, text);
    }

    private void onNudge(JSONObject json) {
        String kind = json.optString("kind", "");
        JSONObject from = json.optJSONObject("from");
        String name = from == null ? "TA" : from.optString("name", "TA");
        if ("sleep".equals(kind)) {
            CloudNotifier.remind(app, name + " 提醒你：早点睡", "晚安，别熬太晚。", 41);
            DmBus.publishStatus(name + " 提醒你早点睡");
        } else if ("water".equals(kind)) {
            CloudNotifier.remind(app, name + " 提醒你：喝口热水", "手边有热水就喝一口。", 42);
            DmBus.publishStatus(name + " 提醒你喝口热水");
        }
    }

    private final class SocketListener extends WebSocketListener {
        private final int gen;

        SocketListener(int gen) {
            this.gen = gen;
        }

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            if (gen != generation) return;
            connected = true;
            lastError = "";
            reconnectAttempt.set(0);
            lastStatusPayload = "";
            lastStatusCheckAt = System.currentTimeMillis();
            if (prefs.sharing()) sendStatus(true);
            notifyState();
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            if (gen != generation) return;
            try {
                JSONObject json = new JSONObject(text);
                String type = json.optString("type", "");
                switch (type) {
                    case "hello":
                        handleHello(json);
                        break;
                    case "status":
                        updatePartner(json.optJSONObject("status"));
                        notifyState();
                        break;
                    case "dm":
                        onDm(json.optJSONObject("message"));
                        break;
                    case "nudge":
                        onNudge(json);
                        break;
                    case "presence":
                        String userId = json.optString("userId", "");
                        if (updatePresence(userId, json)) notifyState();
                        break;
                    case "error":
                        lastError = json.optString("message", "云端返回了一个错误");
                        notifyState();
                        break;
                    default:
                        // pong / ack 不需要改变界面
                        break;
                }
            } catch (Throwable error) {
                Log.w(TAG, "解析云端消息失败", error);
            }
        }

        private void handleHello(JSONObject json) {
            JSONObject partnerJson = json.optJSONObject("partner");
            if (partnerJson != null) {
                setPartnerIdentity(partnerJson.optString("id", ""),
                        partnerJson.optBoolean("online", false),
                        partnerJson.optLong("at", 0L));
                updatePartner(partnerJson.optJSONObject("status"));
            }
            JSONArray recent = json.optJSONArray("recent");
            if (recent != null) {
                for (int i = 0; i < recent.length(); i++) {
                    JSONObject item = recent.optJSONObject(i);
                    if (item != null) onDm(item);
                }
            }
            notifyState();
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable error, Response response) {
            if (gen != generation) return;
            socket = null;
            connected = false;
            lastStatusPayload = "";
            if (response != null && response.code() == 401) {
                wantConnection = false;
                lastError = "登录已过期，请重新登录";
            } else if (response != null && response.code() == 409) {
                wantConnection = false;
                lastError = "还没有绑定伴侣";
            } else {
                lastError = shortError(error);
            }
            notifyState();
            if (wantConnection && gen == generation) scheduleReconnect(gen);
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            if (gen != generation) return;
            socket = null;
            connected = false;
            if (code == 1000 && ("unpaired".equals(reason) || "client stop".equals(reason))) {
                lastError = "未绑定伴侣或连接已关闭";
                notifyState();
                return;
            }
            lastError = "连接已断开";
            notifyState();
            if (wantConnection && gen == generation) scheduleReconnect(gen);
        }
    }

    private synchronized void setPartnerIdentity(String id, boolean online, long at) {
        String next = id == null ? "" : id;
        if (!partner.id.equals(next)) {
            partner.hasStatus = false;
            partner.battery = -1;
            partner.charging = false;
            partner.network = "none";
            partner.heating = false;
            partner.statusAt = 0L;
            partner.name = "";
        }
        partner.id = next;
        partner.online = online;
        partner.presenceAt = at;
    }

    private synchronized boolean updatePresence(String userId, JSONObject json) {
        if (partner.id.isEmpty() || !partner.id.equals(userId)) return false;
        partner.online = json.optBoolean("online", false);
        partner.presenceAt = json.optLong("at", System.currentTimeMillis());
        String name = json.optString("name", "");
        if (!name.isEmpty()) partner.name = name;
        return true;
    }

    private static String shortError(Throwable error) {
        if (error == null) return "网络错误";
        String message = error.getMessage();
        return message == null || message.isEmpty()
                ? error.getClass().getSimpleName() : message;
    }
}
