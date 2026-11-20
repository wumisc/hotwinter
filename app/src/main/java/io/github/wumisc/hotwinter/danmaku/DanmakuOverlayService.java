package io.github.wumisc.hotwinter.danmaku;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import io.github.wumisc.hotwinter.R;
import io.github.wumisc.hotwinter.data.Logbook;
import io.github.wumisc.hotwinter.ui.MainActivity;

/** 悬浮弹幕引擎：全屏透明、不吃触摸，只在上方一条区域里滚弹幕。 */
public class DanmakuOverlayService extends Service {

    public static final String ACTION_STOP = "io.github.wumisc.hotwinter.dm.STOP";

    private static final String CHANNEL_ID = "danmaku_overlay";
    private static final int NOTIF_ID = 1003;
    private static final int REQ_OPEN = 31;
    private static final int REQ_STOP = 32;

    public static volatile boolean RUNNING = false;

    private WindowManager windowManager;
    private DanmakuView view;
    private final DmBus.Listener listener = new DmBus.Listener() {
        @Override
        public void onDanmaku(DmMessage message) {
            if (view != null) view.push(message);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            createChannel();
            // Android 14+ 用 startForegroundService 起的服务，必须在 5 秒内先 startForeground，
            // 否则会被系统判成 RemoteServiceException 直接闪退；所以先挂通知，再判权限。
            startForegroundCompat(buildNotification());
        } catch (Throwable error) {
            Logbook.log("dm", "悬浮弹幕前台通知失败：" + error.getClass().getSimpleName());
            stopSelf();
            return;
        }

        if (!Settings.canDrawOverlays(this)) {
            Logbook.log("dm", "没有悬浮窗权限，停止");
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return;
        }
        RUNNING = true;
        DmBus.addListener(listener);
        if (!showOverlay()) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
            return;
        }

        DmSettings settings = new DmSettings(this);
        if (settings.udpEnabled()) {
            try {
                UdpMesh.start(this, settings.port(), settings.room(), settings.peerIp());
            } catch (RuntimeException e) {
                Logbook.log("dm", "启动局域网失败：" + e.getClass().getSimpleName());
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        DmBus.removeListener(listener);
        if (view != null) {
            try {
                windowManager.removeView(view);
            } catch (RuntimeException ignored) {
                // 视图已经被系统移除
            }
            view = null;
        }
        // 这里不停 UDP：局域网开关归应用管，关悬浮弹幕不该顺便断掉弹幕接收
        RUNNING = false;
        super.onDestroy();
    }

    private boolean showOverlay() {
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        view = new DanmakuView(this, null);
        int height = Math.max((int) (getResources().getDisplayMetrics().heightPixels * 0.42f),
                (int) (160 * getResources().getDisplayMetrics().density));
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                height,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP;
        params.y = (int) (56 * getResources().getDisplayMetrics().density);
        try {
            windowManager.addView(view, params);
            return true;
        } catch (RuntimeException e) {
            Logbook.log("dm", "添加悬浮窗失败：" + e.getClass().getSimpleName());
            return false;
        }
    }

    private void createChannel() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                getString(R.string.dm_notif_channel), NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        channel.enableVibration(false);
        manager.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(this, REQ_OPEN, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stopPending = PendingIntent.getService(this, REQ_STOP,
                new Intent(this, DanmakuOverlayService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tile_heat)
                .setContentTitle(getString(R.string.dm_notif_title))
                .setContentText(getString(R.string.dm_notif_text))
                .setContentIntent(openPending)
                .setOngoing(true)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .addAction(0, getString(R.string.dm_stop), stopPending)
                .build();
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
                    new Intent(context, DanmakuOverlayService.class));
        } catch (RuntimeException ignored) {
            // 系统限制后台启动时，界面会提示重试
        }
    }

    public static void stop(Context context) {
        try {
            context.startService(new Intent(context, DanmakuOverlayService.class)
                    .setAction(ACTION_STOP));
        } catch (RuntimeException ignored) {
            // 服务已不存在
        }
    }
}
