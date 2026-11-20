package io.github.wumisc.hotwinter.service;

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
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import io.github.wumisc.hotwinter.R;
import io.github.wumisc.hotwinter.core.HeatEngine;
import io.github.wumisc.hotwinter.core.HeatPolicy;
import io.github.wumisc.hotwinter.core.HeatRuntime;
import io.github.wumisc.hotwinter.core.HeatSample;
import io.github.wumisc.hotwinter.core.HeatStatus;
import io.github.wumisc.hotwinter.core.Preset;
import io.github.wumisc.hotwinter.core.SocProfile;
import io.github.wumisc.hotwinter.data.Logbook;
import io.github.wumisc.hotwinter.data.SocDetector;
import io.github.wumisc.hotwinter.core.ThermalMonitor;
import io.github.wumisc.hotwinter.data.SettingsStore;
import io.github.wumisc.hotwinter.tile.HeatTileService;
import io.github.wumisc.hotwinter.ui.MainActivity;

/**
 * 前台服务：持有 PARTIAL_WAKE_LOCK 让 CPU 在息屏下持续发热，
 * 每 2.5 秒跑一次温控决策，温度到点降档、到限立刻停。
 */
public class HeatingService extends Service {

    public static final String ACTION_START = "io.github.wumisc.hotwinter.action.START";
    public static final String ACTION_STOP = "io.github.wumisc.hotwinter.action.STOP";
    public static final String ACTION_KEEP_ALIVE = "io.github.wumisc.hotwinter.action.KEEP_ALIVE";
    public static final String ACTION_RELEASE = "io.github.wumisc.hotwinter.action.RELEASE";

    private static final String CHANNEL_ID = "heat_status";
    private static final int NOTIF_ID = 1001;
    private static final int REQ_STOP = 11;
    private static final int REQ_OPEN = 12;
    private static final long TICK_MS = 2500L;

    private ThermalMonitor monitor;
    private PowerManager.WakeLock wakeLock;
    private NotificationManager notificationManager;

    private HandlerThread loopThread;
    private Handler loopHandler;

    private volatile boolean running;
    private volatile boolean idleForeground;
    private long startedAt;
    private float integral;
    private float lastDuty;
    private float startTempC;
    private SocProfile soc;
    private int cores;
    private int lowRiseTicks;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            try {
                step();
            } catch (Throwable error) {
                Logbook.log("heat", "控制循环异常：" + android.util.Log.getStackTraceString(error));
                stopHeating("控制循环异常，已自动停止");
            }
            if (running && loopHandler != null) {
                loopHandler.postDelayed(this, TICK_MS);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            monitor = new ThermalMonitor(this);
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "hotwinter:heat");
                wakeLock.setReferenceCounted(false);
            }
            cores = Runtime.getRuntime().availableProcessors();
            soc = SocDetector.detect(this);
            createChannel();
        } catch (Throwable error) {
            Logbook.log("heat", "服务初始化失败：" + android.util.Log.getStackTraceString(error));
            monitor = null;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopHeating("已手动停止");
        } else if (ACTION_RELEASE.equals(action)) {
            stopHeating("已关闭通知保后台");
            releaseForeground();
        } else if (ACTION_KEEP_ALIVE.equals(action)) {
            startIdleForeground();
        } else if (action == null) {
            // 系统重启服务时没有 action，不要擅自开始发热，只保留待机前台。
            startIdleForeground();
        } else {
            try {
                startHeating();
            } catch (Throwable error) {
                Logbook.log("heat", "开始加热失败：" + android.util.Log.getStackTraceString(error));
                running = false;
                shutdown();
                try {
                    stopForeground(Service.STOP_FOREGROUND_REMOVE);
                } catch (RuntimeException ignored) {
                    // 服务没有进入前台
                }
                stopSelf();
            }
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        shutdown();
        super.onDestroy();
    }

    /** SoC 方案给的线程配额：老芯片少开，旗舰按热预算开。 */
    private int plannedThreads(Preset preset) {
        return soc == null ? preset.threadCount(cores) : soc.planThreads(preset, cores);
    }

    /** 不发热时也挂一个前台通知：进程优先级更高，息屏后不容易被系统清掉。 */
    private void startIdleForeground() {
        if (running) return;
        try {
            startForegroundCompat(buildIdleNotification());
            idleForeground = true;
        } catch (Throwable error) {
            Logbook.log("heat", "待机前台通知失败：" + error.getClass().getSimpleName());
            stopSelf();
        }
    }

    private void startHeating() {
        if (running) return;
        idleForeground = false;
        if (monitor == null) {
            throw new IllegalStateException("温控采样器未初始化");
        }

        SettingsStore store = SettingsStore.get(this);
        running = true;
        startedAt = System.currentTimeMillis();
        integral = 0f;
        lastDuty = store.preset().dutyCap * 0.25f;
        startTempC = monitor.sample().batteryTempC;
        lowRiseTicks = 0;

        if (wakeLock != null && !wakeLock.isHeld()) {
            // 兜底超时：即使异常路径漏掉 stopSelf，最多一小时后系统自动释放
            wakeLock.acquire(60L * 60L * 1000L);
        }
        monitor.start();
        HeatEngine.start(plannedThreads(store.preset()));
        HeatEngine.setDuty(lastDuty);

        startForegroundCompat(buildNotification(store, monitor.sample().batteryTempC, lastDuty, 0L));

        publish(store, monitor.sample(), lastDuty, "正在升温");

        loopThread = new HandlerThread("hotwinter-control");
        loopThread.start();
        loopHandler = new Handler(loopThread.getLooper());
        loopHandler.postDelayed(tick, TICK_MS);

        HeatTileService.refresh(this);
        Logbook.log("heat", "开始加热 · " + store.preset().label
                + " · 线程 " + HeatEngine.threadCount()
                + " · 目标 " + Math.round(store.targetC()) + "°C");
    }

    private void step() {
        SettingsStore store = SettingsStore.get(this);
        HeatSample sample = monitor.sample();
        long elapsed = System.currentTimeMillis() - startedAt;

        if (elapsed >= store.autoOffMinutes() * 60_000L) {
            stopHeating("已到达设定时长 " + store.autoOffMinutes() + " 分钟");
            return;
        }

        HeatPolicy.Output output = HeatPolicy.evaluate(
                sample,
                new HeatPolicy.Args(store.preset(), store.targetC(), store.batteryFloor(), store.ecoMode()),
                lastDuty,
                integral,
                TICK_MS / 1000f
        );

        if (output.shouldHalt()) {
            stopHeating(output.haltReason);
            return;
        }

        // 档位可能中途被改，线程池按 SoC 方案重建；重建后必须重新写入占空比
        Preset preset = store.preset();
        float cap = soc != null ? soc.dutyCap(preset) : preset.dutyCap;
        float duty = Math.min(output.duty, cap);
        HeatEngine.start(plannedThreads(preset));
        HeatEngine.setDuty(duty);
        lastDuty = duty;
        integral = output.integral;

        String note = output.note;
        if (sample.batteryTempC - startTempC < 0.4f && output.duty >= 0.5f) {
            lowRiseTicks++;
        } else {
            lowRiseTicks = 0;
        }
        if (lowRiseTicks >= 40) {
            note = "升温偏慢：把手机从厚壳里拿出来，或保持亮屏会更热";
        }

        publish(store, sample, duty, note);
        updateNotification(store, sample.batteryTempC, duty, elapsed);
    }

    private void publish(SettingsStore store, HeatSample sample, float duty, String note) {
        HeatRuntime.publish(new HeatStatus(
                true,
                duty,
                HeatEngine.threadCount(),
                sample.batteryTempC,
                sample.thermalStatus,
                sample.batteryLevel,
                sample.charging,
                startedAt,
                store.preset(),
                store.targetC(),
                note
        ));
        HeatTileService.refresh(this);
    }

    private void stopHeating(String reason) {
        Logbook.log("heat", "停止加热：" + reason);
        SettingsStore store = SettingsStore.get(this);
        HeatSample sample = monitor == null ? HeatSample.empty() : monitor.sample();
        HeatRuntime.publish(new HeatStatus(
                false,
                0f,
                0,
                sample.batteryTempC,
                sample.thermalStatus,
                sample.batteryLevel,
                sample.charging,
                0L,
                store.preset(),
                store.targetC(),
                reason
        ));
        HeatTileService.refresh(this);
        shutdown();

        // 关了「通知保后台」才真正退出；开着就退回待机前台通知
        if (SettingsStore.get(this).keepAlive()) {
            try {
                startForegroundCompat(buildIdleNotification());
                idleForeground = true;
            } catch (Throwable error) {
                Logbook.log("heat", "回到待机通知失败：" + error.getClass().getSimpleName());
                stopForeground(Service.STOP_FOREGROUND_REMOVE);
                stopSelf();
            }
        } else {
            stopForeground(Service.STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    private void releaseForeground() {
        idleForeground = false;
        if (!running) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    private void shutdown() {
        running = false;
        if (loopHandler != null) {
            loopHandler.removeCallbacks(tick);
            loopHandler = null;
        }
        if (loopThread != null) {
            loopThread.quitSafely();
            loopThread = null;
        }
        HeatEngine.stop();
        if (monitor != null) {
            monitor.stop();
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    // --- 通知 ---

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(getString(R.string.channel_desc));
        channel.setShowBadge(false);
        channel.enableVibration(false);
        notificationManager.createNotificationChannel(channel);
    }

    private Notification buildNotification(SettingsStore store, float tempC, float duty, long elapsedMs) {
        Intent stopIntent = new Intent(this, HeatingService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(
                this, REQ_STOP, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent openPending = PendingIntent.getActivity(
                this, REQ_OPEN, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String summary = store.preset().label
                + " · 目标 " + Math.round(store.targetC()) + "°C"
                + " · 实际 " + Math.round(tempC) + "°C";
        String detail = "负载 " + Math.round(duty * 100) + "%"
                + " · 已运行 " + formatDuration(elapsedMs);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tile_heat)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(summary)
                .setSubText(detail)
                .setContentIntent(openPending)
                .setOngoing(true)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .addAction(0, getString(R.string.notif_stop), stopPending)
                .build();
    }

    /** 待机保活通知：文案就是「爱情加热中」，一眼知道是哪个应用在后台。 */
    private Notification buildIdleNotification() {
        PendingIntent openPending = PendingIntent.getActivity(
                this, REQ_OPEN, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_tile_heat)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_idle_text))
                .setSubText(getString(R.string.notif_idle_sub))
                .setContentIntent(openPending)
                .setOngoing(true)
                .setSilent(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build();
    }

    private void updateNotification(SettingsStore store, float tempC, float duty, long elapsedMs) {
        try {
            notificationManager.notify(NOTIF_ID, buildNotification(store, tempC, duty, elapsedMs));
        } catch (RuntimeException ignored) {
            // 通知被系统限制时不影响发热
        }
    }

    private void startForegroundCompat(Notification notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, notification);
        }
    }

    private String formatDuration(long ms) {
        long totalSec = Math.max(0L, ms / 1000L);
        long min = totalSec / 60;
        long sec = totalSec % 60;
        return String.format(java.util.Locale.CHINA, "%02d:%02d", min, sec);
    }

    public static void start(Context context) {
        try {
            ContextCompat.startForegroundService(context,
                    new Intent(context, HeatingService.class).setAction(ACTION_START));
        } catch (RuntimeException e) {
            Logbook.log("heat", "启动被系统拒绝：" + e.getClass().getSimpleName());
        }
    }

    public static void stop(Context context) {
        try {
            context.startService(new Intent(context, HeatingService.class).setAction(ACTION_STOP));
        } catch (RuntimeException ignored) {
            // 服务已不存在
        }
    }

    /** 保持一个「爱情加热中」的前台通知，仅保活、不发热。 */
    public static void keepAlive(Context context) {
        try {
            ContextCompat.startForegroundService(context,
                    new Intent(context, HeatingService.class).setAction(ACTION_KEEP_ALIVE));
        } catch (RuntimeException ignored) {
            // 系统限制了后台启动，界面下次打开时会再试
        }
    }

    /** 关闭保活：正在发热就等停止后再退，没发热就立刻退。 */
    public static void release(Context context) {
        try {
            context.startService(new Intent(context, HeatingService.class).setAction(ACTION_RELEASE));
        } catch (RuntimeException ignored) {
            // 服务已不存在
        }
    }
}
