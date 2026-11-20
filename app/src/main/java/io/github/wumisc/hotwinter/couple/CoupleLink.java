package io.github.wumisc.hotwinter.couple;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import io.github.wumisc.hotwinter.danmaku.DmBus;
import io.github.wumisc.hotwinter.danmaku.DmMessage;
import io.github.wumisc.hotwinter.data.SettingsStore;

import io.github.wumisc.hotwinter.data.Logbook;

/**
 * 蓝牙情侣链路：一台开「被远程加热」当服务端，另一台连上去发一条命令。
 *
 * 安全边界：只接受系统蓝牙里已经配对（bonded）的设备；配对本身就是授权，
 * 不在应用内再存密码。链路只承载三条纯文本命令，不传任何个人数据。
 */
public final class CoupleLink {

    public static final UUID SERVICE_UUID =
            UUID.fromString("6f2a9d4e-1c3b-4a77-9e21-8b0f5d3c7a10");

    public static final String CMD_HEAT_ON = "HEAT_ON";
    public static final String CMD_HEAT_OFF = "HEAT_OFF";
    public static final String CMD_PING = "PING";

    /** 界面观察者：只在主线程回调，不持有 Activity。 */
    public interface Listener {
        void onState(String text, boolean connected);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile String status = "还没有连接";
    private static volatile boolean connected;

    private static BluetoothServerSocket serverSocket;
    private static BluetoothSocket socket;
    private static Thread acceptThread;
    private static Thread readThread;
    private static Thread connectThread;
    private static Context appContext;

    private CoupleLink() {
    }

    public static void addListener(Listener value) {
        if (value == null) return;
        if (!LISTENERS.contains(value)) LISTENERS.add(value);
        value.onState(status, connected);
    }

    public static void removeListener(Listener value) {
        LISTENERS.remove(value);
    }

    public static synchronized boolean isServerRunning() {
        return acceptThread != null && acceptThread.isAlive();
    }

    public static String status() {
        return status;
    }

    public static boolean isConnected() {
        return connected;
    }

    /** 服务端：等她来连。重复调用是安全的。 */
    public static synchronized void startServer(Context context) {
        appContext = context.getApplicationContext();
        BluetoothAdapter adapter = adapter();
        if (adapter == null) {
            publish("这台手机没有蓝牙", false);
            return;
        }
        if (!hasPermission(appContext)) {
            publish("缺少蓝牙权限，去系统设置里允许「附近的设备」", false);
            return;
        }
        if (!adapter.isEnabled()) {
            publish("蓝牙没开，先在控制中心打开蓝牙", false);
            return;
        }
        if (acceptThread != null && acceptThread.isAlive()) {
            publish("正在等她连接…", connected);
            return;
        }

        acceptThread = new Thread(() -> {
            try {
                serverSocket = adapter.listenUsingRfcommWithServiceRecord("HotWinter", SERVICE_UUID);
                publish("正在等她连接…（保持两台手机蓝牙都开着）", false);
                while (serverSocket != null) {
                    BluetoothSocket incoming = serverSocket.accept();
                    if (incoming == null) break;
                    BluetoothDevice remote = incoming.getRemoteDevice();
                    if (!isBonded(remote)) {
                        closeQuietly(incoming);
                        publish("拒接了一台未配对的设备", connected);
                        continue;
                    }
                    String allowed = SettingsStore.get(appContext).peerAddress();
                    if (!allowed.isEmpty() && !allowed.equalsIgnoreCase(remote.getAddress())) {
                        closeQuietly(incoming);
                        publish("拒接了一台不是她/他的设备", connected);
                        Logbook.log("bt", "拒绝未授权设备 " + remote.getAddress());
                        continue;
                    }
                    attach(incoming, "她连上了");
                }
            } catch (IOException | SecurityException e) {
                publish("等待连接结束：" + shortReason(e), false);
            } finally {
                closeQuietly(serverSocket);
                serverSocket = null;
                acceptThread = null;
            }
        }, "hotwinter-bt-accept");
        acceptThread.start();
    }

    public static synchronized void stopServer() {
        closeQuietly(serverSocket);
        serverSocket = null;
        if (acceptThread != null) {
            acceptThread.interrupt();
            acceptThread = null;
        }
        disconnect();
    }

    /** 客户端：连到她，连上后立刻发 pending（可为空）。 */
    public static synchronized void connect(Context context, String address, String name, String pending) {
        appContext = context.getApplicationContext();
        BluetoothAdapter adapter = adapter();
        if (adapter == null) {
            publish("这台手机没有蓝牙", false);
            return;
        }
        if (!hasPermission(appContext)) {
            publish("缺少蓝牙权限，去系统设置里允许「附近的设备」", false);
            return;
        }
        if (!adapter.isEnabled()) {
            publish("蓝牙没开，先在控制中心打开蓝牙", false);
            return;
        }
        if (address == null || address.isEmpty()) {
            publish("还没有选她的手机，先点「连接她的手机」", false);
            return;
        }

        disconnect();
        final BluetoothDevice device;
        try {
            device = adapter.getRemoteDevice(address);
        } catch (IllegalArgumentException e) {
            publish("这个蓝牙地址无效，重新选一次", false);
            return;
        }

        publish("正在连接 " + (name == null || name.isEmpty() ? address : name) + "…", false);
        connectThread = new Thread(() -> {
            BluetoothSocket client = null;
            try {
                client = device.createRfcommSocketToServiceRecord(SERVICE_UUID);
                client.connect();
                attach(client, "已经连上她的手机");
                if (pending != null && !pending.isEmpty()) {
                    send(pending);
                }
            } catch (IOException | SecurityException e) {
                closeQuietly(client);
                publish("连不上：" + shortReason(e) + "（两台手机都要装这个应用，她的那台要开「被远程加热」）", false);
            }
        }, "hotwinter-bt-connect");
        connectThread.start();
    }

    public static synchronized void disconnect() {
        closeQuietly(socket);
        socket = null;
        connected = false;
        if (readThread != null) {
            readThread.interrupt();
            readThread = null;
        }
        publish(acceptThread != null ? "正在等她连接…" : "还没有连接", false);
    }

    /** 发送一条命令；未连接时只提示，不排队。 */
    public static synchronized boolean send(String command) {
        BluetoothSocket current = socket;
        if (current == null || !connected) {
            publish("还没连上她的手机", false);
            return false;
        }
        try {
            OutputStream out = current.getOutputStream();
            out.write((command + "\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            Logbook.log("bt", "发送 " + command);
            return true;
        } catch (IOException | SecurityException e) {
            publish("发送失败：" + shortReason(e), false);
            disconnect();
            return false;
        }
    }

    private static void attach(BluetoothSocket incoming, String message) {
        closeQuietly(socket);
        socket = incoming;
        connected = true;
        publish(message, true);
        readThread = new Thread(() -> {
            try {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(incoming.getInputStream(), StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    handle(line.trim());
                }
            } catch (IOException | SecurityException ignored) {
                // 对方断开或蓝牙关闭
            } finally {
                synchronized (CoupleLink.class) {
                    if (socket == incoming) {
                        closeQuietly(incoming);
                        socket = null;
                        connected = false;
                        publish(acceptThread != null ? "她断开了，继续等待…" : "连接已断开", false);
                    }
                }
            }
        }, "hotwinter-bt-read");
        readThread.start();
    }

    /** 收到命令后的业务动作交给 Host（服务）。 */
    public interface Host {
        void onHeatOn();

        void onHeatOff();
    }

    private static volatile Host host;

    public static void setHost(Host value) {
        host = value;
    }

    private static void handle(String command) {
        Logbook.log("bt", "收到 " + command);
        if (command.startsWith(DmMessage.PREFIX + "\t")) {
            DmMessage message = DmMessage.decode(command);
            if (message != null) DmBus.publish(message);
            return;
        }
        switch (command) {
            case CMD_HEAT_ON:
                if (host != null) host.onHeatOn();
                send("OK:HEAT_ON");
                break;
            case CMD_HEAT_OFF:
                if (host != null) host.onHeatOff();
                send("OK:HEAT_OFF");
                break;
            case CMD_PING:
                send("OK:PONG");
                break;
            default:
                if (command.startsWith("OK:HEAT_ON")) {
                    publish("她已开始加热", true);
                } else if (command.startsWith("OK:HEAT_OFF")) {
                    publish("她已停止加热", true);
                }
                break;
        }
    }

    private static void publish(String text, boolean isConnected) {
        status = text;
        connected = isConnected;
        MAIN.post(() -> {
            for (Listener value : LISTENERS) {
                try {
                    value.onState(text, isConnected);
                } catch (Throwable error) {
                    Logbook.log("bt", "蓝牙状态回调异常：" + error.getClass().getSimpleName());
                }
            }
        });
    }

    private static BluetoothAdapter adapter() {
        Context context = appContext;
        if (context == null) return null;
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        return manager == null ? null : manager.getAdapter();
    }

    public static boolean hasPermission(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true;
        return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean isBonded(BluetoothDevice device) {
        if (device == null) return false;
        try {
            return device.getBondState() == BluetoothDevice.BOND_BONDED;
        } catch (SecurityException e) {
            return false;
        }
    }

    private static String shortReason(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isEmpty()) return e.getClass().getSimpleName();
        return message.length() > 80 ? message.substring(0, 80) : message;
    }

    private static void closeQuietly(BluetoothSocket target) {
        if (target == null) return;
        try {
            target.close();
        } catch (IOException ignored) {
            // 忽略
        }
    }

    private static void closeQuietly(BluetoothServerSocket target) {
        if (target == null) return;
        try {
            target.close();
        } catch (IOException ignored) {
            // 忽略
        }
    }
}
