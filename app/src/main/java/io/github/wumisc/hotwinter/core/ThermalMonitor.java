package io.github.wumisc.hotwinter.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;

import androidx.core.content.ContextCompat;

/** 电池温度 / 电量 / 充电状态 / 系统热状态采样。 */
public final class ThermalMonitor {

    private final Context app;
    private final PowerManager powerManager;

    private volatile HeatSample sample = HeatSample.empty();

    private BroadcastReceiver receiver;
    private PowerManager.OnThermalStatusChangedListener thermalListener;

    public ThermalMonitor(Context context) {
        this.app = context.getApplicationContext();
        this.powerManager = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
    }

    public HeatSample sample() {
        return sample;
    }

    public void start() {
        if (receiver != null) return;

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                sample = parse(context, intent, sample);
            }
        };
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            thermalListener = status -> {
                HeatSample old = sample;
                sample = new HeatSample(old.batteryTempC, old.batteryLevel, old.charging, status);
            };
            powerManager.addThermalStatusListener(thermalListener);
        }
        refresh();
    }

    public void stop() {
        if (receiver != null) {
            try {
                app.unregisterReceiver(receiver);
            } catch (IllegalArgumentException ignored) {
                // 已注销
            }
            receiver = null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null && thermalListener != null) {
            try {
                powerManager.removeThermalStatusListener(thermalListener);
            } catch (RuntimeException ignored) {
                // 监听器可能已随进程销毁
            }
            thermalListener = null;
        }
    }

    public void refresh() {
        Intent sticky = null;
        try {
            sticky = app.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        } catch (RuntimeException ignored) {
            // 个别 ROM 限制空 receiver 读取 sticky
        }
        sample = parse(app, sticky, HeatSample.empty());
    }

    /** 一次性读取，界面空闲时也能显示当前温度。 */
    public static HeatSample read(Context context) {
        Intent sticky = null;
        try {
            sticky = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        } catch (RuntimeException ignored) {
            // 同上
        }
        return parse(context, sticky, HeatSample.empty());
    }

    private static HeatSample parse(Context context, Intent intent, HeatSample fallback) {
        float tempC = fallback.batteryTempC;
        int level = fallback.batteryLevel;
        boolean charging = fallback.charging;

        if (intent != null) {
            int tenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
            if (tenths != Integer.MIN_VALUE && tenths > 0) {
                tempC = tenths / 10f;
            }
            int rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (rawLevel >= 0 && scale > 0) {
                level = rawLevel * 100 / scale;
            }
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL;
        }

        return new HeatSample(tempC, level, charging, thermalStatus(context));
    }

    private static int thermalStatus(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return HeatSample.THERMAL_NONE;
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return pm == null ? HeatSample.THERMAL_NONE : pm.getCurrentThermalStatus();
    }
}
