package io.github.wumisc.hotwinter.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

/**
 * 直读 /sys/class/thermal 的温区温度，仅用于展示更细的机身温度。
 *
 * 注意：不同机型温区命名差异极大，这份读数只做参考，绝不参与安全停机判定——
 * 安全判定始终以电池温度 + 系统热状态为准。
 */
public final class ThermalZoneReader {

    private static final int MAX_ZONES = 64;

    private ThermalZoneReader() {
    }

    /** @return 最热的 CPU/SOC 温区温度（°C），读不到返回 Float.NaN。 */
    public static float readHottestCpuTemp() {
        float best = Float.NaN;
        for (int i = 0; i < MAX_ZONES; i++) {
            File typeFile = new File("/sys/class/thermal/thermal_zone" + i + "/type");
            if (!typeFile.canRead()) {
                if (i > 20) break;
                continue;
            }
            String type = readLine(typeFile);
            if (type == null || !isCpuZone(type)) continue;
            float celsius = readCelsius(i);
            if (Float.isNaN(celsius)) continue;
            if (Float.isNaN(best) || celsius > best) best = celsius;
        }
        return best;
    }

    /** 统计能读到的温区数量，用于界面「检测」回显。 */
    public static int countReadableZones() {
        int count = 0;
        for (int i = 0; i < MAX_ZONES; i++) {
            File temp = new File("/sys/class/thermal/thermal_zone" + i + "/temp");
            if (temp.canRead() && !Float.isNaN(readCelsius(i))) count++;
        }
        return count;
    }

    private static boolean isCpuZone(String type) {
        String lower = type.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("cpu") || lower.contains("soc") || lower.contains("ap")
                || lower.contains("cluster");
    }

    private static float readCelsius(int zone) {
        String raw = readLine(new File("/sys/class/thermal/thermal_zone" + zone + "/temp"));
        if (raw == null) return Float.NaN;
        try {
            float value = Float.parseFloat(raw.trim());
            // 内核多数以 0.001°C 为单位，偶有直接给 °C 的
            if (value > 1000f) value /= 1000f;
            if (value < -40f || value > 150f) return Float.NaN;
            return value;
        } catch (NumberFormatException e) {
            return Float.NaN;
        }
    }

    private static String readLine(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return reader.readLine();
        } catch (Exception e) {
            return null;
        }
    }
}
