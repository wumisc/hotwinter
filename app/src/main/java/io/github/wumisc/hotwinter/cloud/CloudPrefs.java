package io.github.wumisc.hotwinter.cloud;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import io.github.wumisc.hotwinter.BuildConfig;

/**
 * 云端账号的本地存储。会话 token 用 Android Keystore 里的 AES-GCM 密钥加密后落盘，
 * 不用明文 SharedPreferences。Keystore 在某些被裁剪的 ROM 上可能不可用，
 * 此时退回应用私有目录的明文存储，功能不中断，但会在状态栏里明确提示。
 */
public final class CloudPrefs {

    private static final String FILE = "hotwinter_cloud";
    private static final String KEY_BASE = "base_url";
    private static final String KEY_TOKEN = "token_enc";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_LAST_EMAIL = "last_email";
    private static final String KEY_NAME = "display_name";
    private static final String KEY_USER = "user_id";
    private static final String KEY_COUPLE = "couple_id";
    private static final String KEY_SHARING = "sharing";
    private static final String KEY_CARE_BATTERY = "care_battery";
    private static final String KEY_CARE_HEAT = "care_heat";
    private static final String KEY_ALIAS = "hotwinter_cloud_token";

    private static volatile CloudPrefs instance;

    private final SharedPreferences prefs;
    private final boolean encrypted;

    private CloudPrefs(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(FILE, Context.MODE_PRIVATE);
        this.encrypted = canUseKeystore();
    }

    public static CloudPrefs get(Context context) {
        CloudPrefs local = instance;
        if (local == null) {
            synchronized (CloudPrefs.class) {
                local = instance;
                if (local == null) {
                    local = new CloudPrefs(context);
                    instance = local;
                }
            }
        }
        return local;
    }

    public String baseUrl() {
        String saved = prefs.getString(KEY_BASE, "");
        if (saved != null && !saved.trim().isEmpty()) return normalize(saved.trim());
        return normalize(BuildConfig.CLOUD_BASE_URL);
    }

    public void setBaseUrl(String value) {
        prefs.edit().putString(KEY_BASE, normalize(value)).apply();
    }

    public static String normalize(String raw) {
        String value = raw == null ? "" : raw.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (value.isEmpty()) return "";
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "https://" + value;
        }
        return value;
    }

    public String token() {
        String stored = prefs.getString(KEY_TOKEN, "");
        if (stored == null || stored.isEmpty()) return "";
        if (stored.startsWith("plain:")) return stored.substring(6);
        String plain = decrypt(stored);
        return plain == null ? "" : plain;
    }

    public void setToken(String token) {
        if (token == null || token.isEmpty()) {
            prefs.edit().remove(KEY_TOKEN).apply();
            return;
        }
        String stored = encrypted ? encrypt(token) : null;
        prefs.edit().putString(KEY_TOKEN, stored == null ? "plain:" + token : stored).apply();
    }

    public String email() {
        return prefs.getString(KEY_EMAIL, "");
    }

    public String displayName() {
        return prefs.getString(KEY_NAME, "");
    }

    public String userId() {
        return prefs.getString(KEY_USER, "");
    }

    public String coupleId() {
        return prefs.getString(KEY_COUPLE, "");
    }

    public boolean sharing() {
        return prefs.getBoolean(KEY_SHARING, false);
    }

    public boolean careBattery() {
        return prefs.getBoolean(KEY_CARE_BATTERY, false);
    }

    public void setCareBattery(boolean value) {
        prefs.edit().putBoolean(KEY_CARE_BATTERY, value).apply();
    }

    public boolean careHeat() {
        return prefs.getBoolean(KEY_CARE_HEAT, false);
    }

    public void setCareHeat(boolean value) {
        prefs.edit().putBoolean(KEY_CARE_HEAT, value).apply();
    }

    public void setSharing(boolean value) {
        prefs.edit().putBoolean(KEY_SHARING, value).apply();
    }

    public void saveSession(String token, String email, String displayName, String userId) {
        setToken(token);
        String value = email == null ? "" : email.trim();
        SharedPreferences.Editor editor = prefs.edit()
                .putString(KEY_EMAIL, value)
                .putString(KEY_NAME, displayName == null ? "" : displayName)
                .putString(KEY_USER, userId == null ? "" : userId);
        if (!value.isEmpty()) editor.putString(KEY_LAST_EMAIL, value);
        editor.apply();
    }

    /** 上次成功登录/注册的邮箱，退出登录后仍保留，用来防止下次输错。 */
    public String lastEmail() {
        String value = prefs.getString(KEY_LAST_EMAIL, "");
        if (value == null || value.trim().isEmpty()) {
            // 兼容旧版本：旧会话里保存过 email，升级后先拿来预填。
            value = prefs.getString(KEY_EMAIL, "");
        }
        return value == null ? "" : value.trim();
    }

    public void saveCouple(String coupleId) {
        prefs.edit().putString(KEY_COUPLE, coupleId == null ? "" : coupleId).apply();
    }

    public void clearSession() {
        prefs.edit()
                .remove(KEY_TOKEN)
                .remove(KEY_EMAIL)
                .remove(KEY_NAME)
                .remove(KEY_USER)
                .remove(KEY_COUPLE)
                .putBoolean(KEY_SHARING, false)
                .apply();
    }

    public boolean hasToken() {
        return !token().isEmpty();
    }

    public boolean encrypted() {
        return encrypted;
    }

    // --- Keystore ---

    private static boolean canUseKeystore() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false;
        try {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            if (!store.containsAlias(KEY_ALIAS)) {
                KeyGenerator generator = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
                generator.init(new KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build());
                generator.generateKey();
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        Key key = store.getKey(KEY_ALIAS, null);
        return key instanceof SecretKey ? (SecretKey) key : null;
    }

    private String encrypt(String plain) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = cipher.getIV();
            byte[] body = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(iv, Base64.NO_WRAP) + ":"
                    + Base64.encodeToString(body, Base64.NO_WRAP);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String decrypt(String stored) {
        try {
            String[] parts = stored.split(":", 2);
            if (parts.length != 2) return null;
            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] body = Base64.decode(parts[1], Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(body), StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
