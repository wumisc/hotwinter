package io.github.wumisc.hotwinter.data;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;

import androidx.core.content.ContextCompat;

import io.github.wumisc.hotwinter.core.Preset;
import io.github.wumisc.hotwinter.core.SocProfile;
import io.github.wumisc.hotwinter.core.ThermalZoneReader;

/** 设备自检：只报事实，不给「要装什么权限」的建议——这个应用本来就不需要额外授权。 */
public final class DeviceInspector {

    public static final class Report {
        public final int sdkInt;
        public final int cores;
        public final boolean batteryOptimizationIgnored;
        public final boolean notificationsGranted;
        public final boolean keepAlive;
        public final int readableThermalZones;
        public final float hottestCpuTemp;

        public Report(int sdkInt, int cores, boolean batteryOptimizationIgnored,
                      boolean notificationsGranted, boolean keepAlive,
                      int readableThermalZones, float hottestCpuTemp) {
            this.sdkInt = sdkInt;
            this.cores = cores;
            this.batteryOptimizationIgnored = batteryOptimizationIgnored;
            this.notificationsGranted = notificationsGranted;
            this.keepAlive = keepAlive;
            this.readableThermalZones = readableThermalZones;
            this.hottestCpuTemp = hottestCpuTemp;
        }
    }

    private DeviceInspector() {
    }

    public static Report inspect(Context context) {
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        boolean ignored = pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName());
        boolean notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        return new Report(
                Build.VERSION.SDK_INT,
                Runtime.getRuntime().availableProcessors(),
                ignored,
                notifications,
                SettingsStore.get(context).keepAlive(),
                ThermalZoneReader.countReadableZones(),
                ThermalZoneReader.readHottestCpuTemp()
        );
    }

    public static String describe(Context context, Report report) {
        StringBuilder sb = new StringBuilder();
        sb.append("系统：Android ").append(androidVersionName(report.sdkInt))
                .append("（SDK ").append(report.sdkInt).append("）· ").append(report.cores).append(" 核\n");

        Preset preset = SettingsStore.get(context).preset();
        SocProfile soc = SocDetector.detect(context);
        sb.append("SoC：").append(soc.name)
                .append(" · ").append(preset.label).append("档 ")
                .append(soc.planThreads(preset, report.cores)).append(" 线程")
                .append(" · 占空比上限 ").append(Math.round(soc.dutyCap(preset) * 100)).append("%\n");

        sb.append("界面：").append(RomCompat.detect(context).name).append('\n');

        sb.append("电池优化：").append(report.batteryOptimizationIgnored
                ? "已放行（息屏更稳）"
                : "未放行（可以点下面的「电池优化」去放行，不放行也能用）").append('\n');

        sb.append("通知权限：").append(report.notificationsGranted
                ? "已授予（能看到「爱情加热中」）"
                : "未授予（状态栏看不到提示，不影响发热）").append('\n');

        sb.append("后台保活：").append(report.keepAlive
                ? "通知保后台已开启"
                : "通知保后台已关闭").append('\n');

        sb.append("温区读取：").append(report.readableThermalZones > 0
                ? "可读 " + report.readableThermalZones + " 个"
                + (Float.isNaN(report.hottestCpuTemp) ? "" : "，最热 " + Math.round(report.hottestCpuTemp) + "°C")
                : "不可读（只影响展示，不影响安全判定）");

        sb.append("\n\n结论：").append(conclusion(report));
        return sb.toString();
    }

    private static String conclusion(Report report) {
        if (!report.batteryOptimizationIgnored) {
            return "直接就能用：不需要 Root、不需要 Shizuku、不需要无障碍。想让息屏更稳，点「电池优化」放行一次即可。";
        }
        return "状态良好：不需要 Root、不需要 Shizuku、不需要无障碍，后台不会被轻易打断。";
    }

    private static String androidVersionName(int sdk) {
        switch (sdk) {
            case 26:
                return "8.0";
            case 27:
                return "8.1";
            case 28:
                return "9";
            case 29:
                return "10";
            case 30:
                return "11";
            case 31:
            case 32:
                return "12";
            case 33:
                return "13";
            case 34:
                return "14";
            case 35:
                return "15";
            case 36:
                return "16";
            case 37:
                return "17";
            default:
                return String.valueOf(sdk);
        }
    }
}
