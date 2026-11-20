package io.github.wumisc.hotwinter.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import io.github.wumisc.hotwinter.core.Preset;

/** 设置持久化。所有数值在写入前先夹紧到合法区间，避免异常值进到温控循环。 */
public final class SettingsStore {

    public static final float MIN_TARGET_C = 30f;
    public static final float MAX_TARGET_C = 43f;

    public static final int MIN_AUTO_OFF_MIN = 10;
    public static final int MAX_AUTO_OFF_MIN = 45;

    public static final int MIN_FLOOR = 10;
    public static final int MAX_FLOOR = 40;

    private static final String PREFS = "hotwinter_settings";
    private static final String KEY_PRESET = "preset";
    private static final String KEY_TARGET = "target_c";
    private static final String KEY_AUTO_OFF = "auto_off_min";
    private static final String KEY_FLOOR = "battery_floor";
    private static final String KEY_ECO = "eco_mode";
    private static final String KEY_KEEP_ALIVE = "keep_alive";
    private static final String KEY_LOG = "log_enabled";
    private static final String KEY_RECEIVE = "couple_receive";
    private static final String KEY_PEER_ADDRESS = "couple_peer_address";
    private static final String KEY_PEER_NAME = "couple_peer_name";
    private static final String KEY_COUPLE = "couple_enabled";
    private static final String KEY_CHANNEL_AUTO = "channel_auto_accept";
    private static final String KEY_CHANNEL_NICK = "channel_nick";
    private static final String KEY_CHANNEL_DEVICE = "channel_device_id";

    private static volatile SettingsStore instance;

    private final SharedPreferences prefs;

    private SettingsStore(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static SettingsStore get(Context context) {
        SettingsStore local = instance;
        if (local == null) {
            synchronized (SettingsStore.class) {
                local = instance;
                if (local == null) {
                    local = new SettingsStore(context);
                    instance = local;
                }
            }
        }
        return local;
    }

    public Preset preset() {
        return Preset.of(prefs.getString(KEY_PRESET, Preset.BALANCED.key));
    }

    public void setPreset(Preset preset) {
        prefs.edit().putString(KEY_PRESET, preset.key).apply();
    }

    public float targetC() {
        return clampTarget(prefs.getFloat(KEY_TARGET, Preset.BALANCED.defaultTargetC));
    }

    public void setTargetC(float value) {
        prefs.edit().putFloat(KEY_TARGET, clampTarget(value)).apply();
    }

    public static float clampTarget(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return MIN_TARGET_C;
        return Math.max(MIN_TARGET_C, Math.min(MAX_TARGET_C, value));
    }

    public int autoOffMinutes() {
        int value = prefs.getInt(KEY_AUTO_OFF, 20);
        if (value < MIN_AUTO_OFF_MIN) value = MIN_AUTO_OFF_MIN;
        if (value > MAX_AUTO_OFF_MIN) value = MAX_AUTO_OFF_MIN;
        return value;
    }

    public void setAutoOffMinutes(int value) {
        prefs.edit().putInt(KEY_AUTO_OFF, clampAutoOff(value)).apply();
    }

    public static int clampAutoOff(int value) {
        return Math.max(MIN_AUTO_OFF_MIN, Math.min(MAX_AUTO_OFF_MIN, value));
    }

    public int batteryFloor() {
        int value = prefs.getInt(KEY_FLOOR, 20);
        if (value < MIN_FLOOR) value = MIN_FLOOR;
        if (value > MAX_FLOOR) value = MAX_FLOOR;
        return value;
    }

    public void setBatteryFloor(int value) {
        prefs.edit().putInt(KEY_FLOOR, clampFloor(value)).apply();
    }

    public static int clampFloor(int value) {
        return Math.max(MIN_FLOOR, Math.min(MAX_FLOOR, value));
    }

    public boolean ecoMode() {
        return prefs.getBoolean(KEY_ECO, false);
    }

    public void setEcoMode(boolean value) {
        prefs.edit().putBoolean(KEY_ECO, value).apply();
    }

    /** 运行日志开关：写到 /Android/NBB/hotwinter.log，默认关闭，避免白白占空间。 */
    public boolean logEnabled() {
        return prefs.getBoolean(KEY_LOG, false);
    }

    public void setLogEnabled(boolean value) {
        prefs.edit().putBoolean(KEY_LOG, value).apply();
    }

    /** 允许已配对的那台手机通过蓝牙让本机开始发热。 */
    public boolean coupleReceive() {
        return prefs.getBoolean(KEY_RECEIVE, false);
    }

    public void setCoupleReceive(boolean value) {
        prefs.edit().putBoolean(KEY_RECEIVE, value).apply();
    }

    public String peerAddress() {
        return prefs.getString(KEY_PEER_ADDRESS, "");
    }

    public String peerName() {
        return prefs.getString(KEY_PEER_NAME, "");
    }

    public void setPeer(String address, String name) {
        prefs.edit()
                .putString(KEY_PEER_ADDRESS, address == null ? "" : address)
                .putString(KEY_PEER_NAME, name == null ? "" : name)
                .apply();
    }

    public boolean coupleEnabled() {
        return prefs.getBoolean(KEY_COUPLE, true);
    }

    public void setCoupleEnabled(boolean value) {
        prefs.edit().putBoolean(KEY_COUPLE, value).apply();
    }

    /** 通知保后台：开着一个低优先级的常驻通知，让系统少在后台清理这个进程。 */
    /** 频道：每次加入都要房主确认；这个开关只决定“前台时是否自动同意”。 */
    public boolean channelAutoAccept() {
        return prefs.getBoolean(KEY_CHANNEL_AUTO, false);
    }

    public void setChannelAutoAccept(boolean value) {
        prefs.edit().putBoolean(KEY_CHANNEL_AUTO, value).apply();
    }

    /** 附近列表里显示的名字，默认用机型，避免要求用户起名。 */
    public String channelNick() {
        String saved = prefs.getString(KEY_CHANNEL_NICK, "");
        if (saved != null && !saved.trim().isEmpty()) return saved.trim();
        String model = Build.MODEL == null ? "我的手机" : Build.MODEL.trim();
        return model.isEmpty() ? "我的手机" : model;
    }

    public void setChannelNick(String value) {
        if (value == null) value = "";
        prefs.edit().putString(KEY_CHANNEL_NICK, value.trim()).apply();
    }

    /** 安装级设备 ID：只用于去重/记住设备，不展示给用户。 */
    public String channelDeviceId() {
        String saved = prefs.getString(KEY_CHANNEL_DEVICE, "");
        if (saved != null && !saved.isEmpty()) return saved;
        String value = java.util.UUID.randomUUID().toString().replace("-", "");
        prefs.edit().putString(KEY_CHANNEL_DEVICE, value).apply();
        return value;
    }

    public boolean keepAlive() {
        return prefs.getBoolean(KEY_KEEP_ALIVE, true);
    }

    public void setKeepAlive(boolean value) {
        prefs.edit().putBoolean(KEY_KEEP_ALIVE, value).apply();
    }
}
