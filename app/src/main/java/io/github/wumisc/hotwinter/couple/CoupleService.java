package io.github.wumisc.hotwinter.couple;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import io.github.wumisc.hotwinter.R;
import io.github.wumisc.hotwinter.data.Logbook;
import io.github.wumisc.hotwinter.data.SettingsStore;
import io.github.wumisc.hotwinter.service.HeatingService;
import io.github.wumisc.hotwinter.ui.MainActivity;

/**
 * 情侣蓝牙链路的前台宿主：对外暴露「她被远程点热」的服务端，
 * 也负责本机主动连过去发命令。链路的收发都交给 {@link CoupleLink}。
 */
public class CoupleService extends Service implements CoupleLink.Host {

    public static final String ACTION_START_SERVER = "io.github.wumisc.hotwinter.couple.SERVER";
    public static final String ACTION_CONNECT = "io.github.wumisc.hotwinter.couple.CONNECT";
    public static final String ACTION_SEND = "io.github.wumisc.hotwinter.couple.SEND";
    public static final String ACTION_RELEASE = "io.github.wumisc.hotwinter.couple.RELEASE";

    public static final String EXTRA_ADDRESS = "address";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_COMMAND = "command";

    private static final String CHANNEL_ID = "couple_link";
    private static final int NOTIF_ID = 1002;
    private static final int REQ_OPEN = 21;

    private NotificationManager notificationManager;
    private CoupleLink.Listener listener;
    private boolean serverMode;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable autoRelease = () -> {
        if (!serverMode) {
            CoupleLink.removeListener(listener);
            CoupleLink.disconnect();
            stopForeground(Service.STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createChannel();
        CoupleLink.setHost(this);
        listener = (text, connected) -> {
            updateNotification(text);
            Logbook.log("couple", text);
        };
        CoupleLink.addListener(listener);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (action == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            startForegroundCompat(buildNotification(CoupleLink.status()));
        } catch (Throwable error) {
            Logbook.log("couple", "情侣服务前台通知失败：" + error.getClass().getSimpleName());
            stopSelf();
            return START_NOT_STICKY;
        }

        switch (action) {
            case ACTION_START_SERVER:
                handler.removeCallbacks(autoRelease);
                serverMode = true;
                CoupleLink.startServer(this);
                Logbook.log("couple", "开启被远程加热");
                break;
            case ACTION_CONNECT:
                String address = intent.getStringExtra(EXTRA_ADDRESS);
                String name = intent.getStringExtra(EXTRA_NAME);
                String pending = intent.getStringExtra(EXTRA_COMMAND);
                CoupleLink.connect(this, address, name, pending);
                scheduleAutoRelease();
                break;
            case ACTION_SEND:
                CoupleLink.send(intent.getStringExtra(EXTRA_COMMAND));
                if (!CoupleLink.isServerRunning()) {
                    scheduleAutoRelease();
                }
                break;
            case ACTION_RELEASE:
                serverMode = false;
                CoupleLink.stopServer();
                if (listener != null) CoupleLink.removeListener(listener);
                stopForeground(Service.STOP_FOREGROUND_REMOVE);
                stopSelf();
                break;
            default:
                break;
        }
        return START_NOT_STICKY;
    }

    private void scheduleAutoRelease() {
        handler.removeCallbacks(autoRelease);
        handler.postDelayed(autoRelease, 8000L);
    }

    @Override
    public void onHeatOn() {
        Logbook.log("couple", "她的手机请求开始加热");
        HeatingService.start(this);
    }

    @Override
    public void onHeatOff() {
        Logbook.log("couple", "她的手机请求停止加热");
        HeatingService.stop(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(autoRelease);
        CoupleLink.setHost(null);
        if (listener != null) CoupleLink.removeListener(listener);
        CoupleLink.stopServer();
        super.onDestroy();
    }

    // --- 通知 ---

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.couple_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.couple_channel_desc));
        channel.setShowBadge(false);
        channel.enableVibration(false);
        notificationManager.createNotificationChannel(channel);
    }

    private Notification buildNotification(String status) {
        PendingIntent open = PendingIntent.getActivity(this, REQ_OPEN,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tile_heat)
                .setContentTitle(getString(R.string.couple_notif_title))
                .setContentText(status == null || status.isEmpty()
                        ? getString(R.string.couple_status_default) : status)
                .setContentIntent(open)
                .setOngoing(true)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build();
    }

    private void updateNotification(String status) {
        try {
            notificationManager.notify(NOTIF_ID, buildNotification(status));
        } catch (RuntimeException ignored) {
            // 通知被限制不影响蓝牙
        }
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, notification);
        }
    }

    // --- 静态入口 ---

    public static void startServer(Context context) {
        try {
            ContextCompat.startForegroundService(context,
                    new Intent(context, CoupleService.class).setAction(ACTION_START_SERVER));
        } catch (RuntimeException ignored) {
            // 系统限制后台启动时，界面会提示用户重试
        }
    }

    public static void connect(Context context, String address, String name, String pendingCommand) {
        try {
            Intent intent = new Intent(context, CoupleService.class)
                    .setAction(ACTION_CONNECT)
                    .putExtra(EXTRA_ADDRESS, address)
                    .putExtra(EXTRA_NAME, name)
                    .putExtra(EXTRA_COMMAND, pendingCommand);
            ContextCompat.startForegroundService(context, intent);
        } catch (RuntimeException ignored) {
            // 同上
        }
    }

    public static void send(Context context, String command) {
        try {
            context.startService(new Intent(context, CoupleService.class)
                    .setAction(ACTION_SEND)
                    .putExtra(EXTRA_COMMAND, command));
        } catch (RuntimeException ignored) {
            // 服务已不存在
        }
    }

    public static void release(Context context) {
        try {
            context.startService(new Intent(context, CoupleService.class).setAction(ACTION_RELEASE));
        } catch (RuntimeException ignored) {
            // 服务已不存在
        }
    }
}
