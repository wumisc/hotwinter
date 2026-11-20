package io.github.wumisc.hotwinter.danmaku;

import android.content.Context;

/** 弹幕设置：沿用「弹幕人生」的那一套参数，换成本应用的 SharedPreferences。 */
public final class DmSettings {

    private static final String PREFS = "hotwinter_danmaku";

    private final android.content.SharedPreferences prefs;

    public DmSettings(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean overlayEnabled() {
        return prefs.getBoolean("overlay", false);
    }

    public void setOverlayEnabled(boolean value) {
        prefs.edit().putBoolean("overlay", value).apply();
    }

    public boolean udpEnabled() {
        return prefs.getBoolean("udp", false);
    }

    public void setUdpEnabled(boolean value) {
        prefs.edit().putBoolean("udp", value).apply();
    }

    public int port() {
        int value = prefs.getInt("port", 47821);
        return value < 1024 || value > 65535 ? 47821 : value;
    }

    public void setPort(int value) {
        prefs.edit().putInt("port", value < 1024 || value > 65535 ? 47821 : value).apply();
    }

    public String room() {
        String value = prefs.getString("room", "love");
        return value == null || value.trim().isEmpty() ? "love" : value.trim();
    }

    public void setRoom(String value) {
        prefs.edit().putString("room", value == null ? "love" : value.trim()).apply();
    }

    public String peerIp() {
        String value = prefs.getString("peer_ip", "");
        return value == null ? "" : value.trim();
    }

    public void setPeerIp(String value) {
        prefs.edit().putString("peer_ip", value == null ? "" : value.trim()).apply();
    }

    public String nick() {
        String value = prefs.getString("nick", "匿名弹友");
        return value == null || value.trim().isEmpty() ? "匿名弹友" : value.trim();
    }

    public void setNick(String value) {
        prefs.edit().putString("nick", value == null ? "匿名弹友" : value.trim()).apply();
    }

    public int fontSizeSp() {
        return clamp(prefs.getInt("font_size", 15), 10, 30);
    }

    public void setFontSizeSp(int value) {
        prefs.edit().putInt("font_size", clamp(value, 10, 30)).apply();
    }

    public float speed() {
        float value = prefs.getFloat("speed", 1.0f);
        if (Float.isNaN(value) || value <= 0f) value = 1.0f;
        return Math.max(0.4f, Math.min(2.0f, value));
    }

    public void setSpeed(float value) {
        prefs.edit().putFloat("speed", Math.max(0.4f, Math.min(2.0f, value))).apply();
    }

    public float alpha() {
        float value = prefs.getFloat("alpha", 0.92f);
        if (Float.isNaN(value)) value = 0.92f;
        return Math.max(0.35f, Math.min(1f, value));
    }

    public void setAlpha(float value) {
        prefs.edit().putFloat("alpha", Math.max(0.35f, Math.min(1f, value))).apply();
    }

    public int lanes() {
        return clamp(prefs.getInt("lanes", 6), 2, 12);
    }

    public void setLanes(int value) {
        prefs.edit().putInt("lanes", clamp(value, 2, 12)).apply();
    }

    public boolean showNick() {
        return prefs.getBoolean("show_nick", true);
    }

    public void setShowNick(boolean value) {
        prefs.edit().putBoolean("show_nick", value).apply();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
