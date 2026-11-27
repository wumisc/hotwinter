package io.github.wumisc.hotwinter.cloud;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;

import org.json.JSONException;
import org.json.JSONObject;

import io.github.wumisc.hotwinter.core.HeatRuntime;

/**
 * 只读五项：电量、充电、网络类型、是否正在加热、采集时间。
 * 不读前台 App、不读通知、不读位置。
 */
public final class CloudStatusReader {

    private CloudStatusReader() {
    }

    public static JSONObject read(Context context) throws JSONException {
        int battery = batteryPercent(context);
        boolean charging = charging(context);
        String network = network(context);
        JSONObject json = new JSONObject();
        json.put("type", "status");
        json.put("battery", battery);
        json.put("charging", charging);
        json.put("network", network);
        json.put("heating", HeatRuntime.isActive());
        return json;
    }

    private static int batteryPercent(Context context) {
        BatteryManager manager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (manager != null) {
            int value = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            if (value >= 0) return Math.max(0, Math.min(100, value));
        }
        Intent intent = context.registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent == null) return -1;
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        if (level < 0 || scale <= 0) return -1;
        return Math.max(0, Math.min(100, Math.round(level * 100f / scale)));
    }

    private static boolean charging(Context context) {
        BatteryManager manager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (manager != null) {
            int status = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS);
            if (status == BatteryManager.BATTERY_STATUS_CHARGING
                    || status == BatteryManager.BATTERY_STATUS_FULL) {
                return true;
            }
        }
        Intent intent = context.registerReceiver(null,
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent == null) return false;
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        return status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;
    }

    private static String network(Context context) {
        ConnectivityManager manager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return "none";
        Network active = manager.getActiveNetwork();
        if (active == null) return "none";
        NetworkCapabilities caps = manager.getNetworkCapabilities(active);
        if (caps == null) return "none";
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "wifi";
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "mobile";
        return "none";
    }
}
