package io.github.wumisc.hotwinter.cloud;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import io.github.wumisc.hotwinter.R;
import io.github.wumisc.hotwinter.ui.MainActivity;

/**
 * 云端同步的前台宿主。WebSocket 的连接与重连在 {@link CloudSync} 里，
 * 这个服务只负责让进程在息屏时不被随手清掉，并把连接状态放到常驻通知。
 */
public class CloudService extends Service implements CloudSync.Listener {

    public static final String ACTION_START = "io.github.wumisc.hotwinter.cloud.START";
    public static final String ACTION_STOP = "io.github.wumisc.hotwinter.cloud.STOP";

    private static final String CHANNEL = "cloud_sync";
    private static final int NOTIF_ID = 1004;
    private static final int REQ_OPEN = 61;

    private NotificationManager manager;
    private CloudSync sync;

    @Override
    public void onCreate() {
        super.onCreate();
        manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        sync = CloudSync.get(this);
        sync.addListener(this);
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            sync.stop();
            stopForeground(Service.STOP_FOREGROUND_REMOVE);
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            startForegroundCompat(buildNotification("正在连接云端…"));
        } catch (Throwable error) {
            stopSelf();
            return START_NOT_STICKY;
        }
        sync.start();
        return START_STICKY;
    }

    @Override
    public void onCloudState() {
        CloudSync.Snapshot snapshot = sync.snapshot();
        updateNotification(snapshot.connectionText);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        sync.removeListener(this);
        sync.stop();
        super.onDestroy();
    }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL, "云端情侣同步", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("异地时同步电量、充电、网络、加热状态和弹幕");
        channel.setShowBadge(false);
        channel.enableVibration(false);
        manager.createNotificationChannel(channel);
    }

    private Notification buildNotification(String status) {
        PendingIntent open = PendingIntent.getActivity(this, REQ_OPEN,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_tile_heat)
                .setContentTitle("云端情侣同步")
                .setContentText(status == null || status.isEmpty() ? "正在连接云端…" : status)
                .setContentIntent(open)
                .setOngoing(true)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build();
    }

    private void updateNotification(String status) {
        if (manager == null) return;
        try {
            manager.notify(NOTIF_ID, buildNotification(status));
        } catch (RuntimeException ignored) {
            // 通知受限不影响连接
        }
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, notification);
        }
    }

    public static void start(Context context) {
        try {
            ContextCompat.startForegroundService(context,
                    new Intent(context, CloudService.class).setAction(ACTION_START));
        } catch (RuntimeException ignored) {
            // 后台启动被系统拒绝，界面会提示重试
        }
    }

    public static void stop(Context context) {
        try {
            context.startService(new Intent(context, CloudService.class).setAction(ACTION_STOP));
        } catch (RuntimeException ignored) {
            // 服务已经不存在
        }
    }
}
