package io.github.wumisc.hotwinter.data;

import android.content.Context;
import android.os.Build;

import java.io.BufferedReader;
import java.io.FileReader;
import java.lang.reflect.Method;
import java.util.Locale;

import io.github.wumisc.hotwinter.core.SocProfile;

/**
 * SoC 识别：Build 字段优先，读不到再退系统属性与 cpuinfo，最后退回保守方案。
 * 只读，不涉及任何权限。
 */
public final class SocDetector {

    private SocDetector() {
    }

    public static SocProfile detect(Context context) {
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        StringBuilder raw = new StringBuilder();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            safeAppend(raw, Build.SOC_MODEL);
            safeAppend(raw, Build.SOC_MANUFACTURER);
        }
        safeAppend(raw, Build.HARDWARE);
        safeAppend(raw, Build.BOARD);
        safeAppend(raw, prop("ro.soc.model"));
        safeAppend(raw, prop("ro.board.platform"));
        safeAppend(raw, prop("ro.hardware"));
        safeAppend(raw, prop("ro.product.board"));
        safeAppend(raw, prop("ro.chipname"));
        safeAppend(raw, cpuInfoHardware());
        return SocProfile.match(raw.toString(), cores);
    }

    private static void safeAppend(StringBuilder sb, String value) {
        if (value == null || value.isEmpty() || "unknown".equals(value)) return;
        sb.append(' ').append(value);
    }

    private static String prop(String key) {
        try {
            Class<?> clazz = Class.forName("android.os.SystemProperties");
            Method get = clazz.getMethod("get", String.class, String.class);
            Object value = get.invoke(null, key, "");
            return value instanceof String ? (String) value : "";
        } catch (Throwable t) {
            return "";
        }
    }

    private static String cpuInfoHardware() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/cpuinfo"))) {
            String line;
            int guard = 0;
            while ((line = reader.readLine()) != null && guard++ < 40) {
                String lower = line.toLowerCase(Locale.ROOT);
                if (lower.startsWith("hardware") || lower.startsWith("model name")) {
                    String[] parts = line.split(":", 2);
                    if (parts.length == 2) return parts[1].trim();
                }
            }
        } catch (Throwable ignored) {
            // 读不到就算了
        }
        return "";
    }
}
