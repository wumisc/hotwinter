package io.github.wumisc.hotwinter.data;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import java.lang.reflect.Method;

/**
 * 国产 UI / 三星 / Pixel / 类原生的差异都收在这里：
 * 先认系统，再给「自启动管理」「电池优化」两条真实可跳的路径，
 * 跳不过去就退回系统设置，不让用户卡死。
 */
public final class RomCompat {

    public final String key;
    public final String name;
    private final Context context;

    private RomCompat(Context context, String key, String name) {
        this.context = context.getApplicationContext();
        this.key = key;
        this.name = name;
    }

    public static RomCompat detect(Context context) {
        String brand = safe(Build.BRAND);
        String manufacturer = safe(Build.MANUFACTURER);
        String fingerprint = safe(Build.FINGERPRINT);
        String miui = prop("ro.miui.ui.version.name");
        String miOs = prop("ro.mi.os.version.name");
        String colorOs = prop("ro.build.version.opporom");
        String vivo = prop("ro.vivo.os.version");
        String emui = prop("ro.build.version.emui");
        String harmony = prop("ro.build.version.harmony");

        if (!miOs.isEmpty()) return new RomCompat(context, "hyperos", "小米 HyperOS");
        if (!miui.isEmpty()) return new RomCompat(context, "miui", "小米 MIUI");
        if (manufacturer.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco")) {
            return new RomCompat(context, "miui", "小米 MIUI / HyperOS");
        }
        if (!colorOs.isEmpty() || manufacturer.contains("oppo") || brand.contains("realme")
                || brand.contains("oneplus")) {
            return new RomCompat(context, "coloros", "OPPO ColorOS / 一加");
        }
        if (!vivo.isEmpty() || manufacturer.contains("vivo") || brand.contains("iqoo")) {
            return new RomCompat(context, "originos", "vivo OriginOS");
        }
        if (!harmony.isEmpty()) return new RomCompat(context, "harmony", "华为 HarmonyOS");
        if (!emui.isEmpty() || manufacturer.contains("huawei") || brand.contains("honor")) {
            return new RomCompat(context, "emui", "华为 EMUI / 荣耀 MagicOS");
        }
        if (manufacturer.contains("samsung") || brand.contains("samsung")) {
            return new RomCompat(context, "oneui", "三星 One UI");
        }
        if (manufacturer.contains("google") || fingerprint.contains("google")) {
            return new RomCompat(context, "pixel", "Google Pixel");
        }
        if (fingerprint.contains("lineage") || fingerprint.contains("aosp")
                || fingerprint.contains("evolution") || fingerprint.contains("crdroid")) {
            return new RomCompat(context, "aosp", "类原生 / Lineage");
        }
        return new RomCompat(context, "aosp", "原生 Android");
    }

    /** 自启动管理页，找不到就返回 null。 */
    public Intent autostart() {
        switch (key) {
            case "hyperos":
            case "miui":
                return resolve(component("com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"));
            case "coloros":
                return firstResolved(
                        component("com.coloros.safecenter",
                                "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                        component("com.oppo.safe",
                                "com.oppo.safe.permission.startup.StartupAppListActivity"),
                        component("com.oneplus.security",
                                "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"));
            case "originos":
                return firstResolved(
                        component("com.vivo.permissionmanager",
                                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                        component("com.iqoo.secure",
                                "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"));
            case "emui":
            case "harmony":
                return firstResolved(
                        component("com.hihonor.systemmanager",
                                "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                        component("com.huawei.systemmanager",
                                "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"));
            case "oneui":
                return firstResolved(
                        component("com.samsung.android.lool",
                                "com.samsung.android.sm.ui.battery.BatteryActivity"));
            default:
                return null;
        }
    }

    /** 电池优化/省电策略页，任何 UI 都能退到系统设置。 */
    public Intent battery() {
        Intent specific = resolve(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        if (specific != null) return specific;
        return appDetails();
    }

    public Intent appDetails() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", this.context.getPackageName(), null));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    /** 该 UI 特别要注意的点，展示在功能页。 */
    public String note() {
        switch (key) {
            case "hyperos":
            case "miui":
                return "HyperOS/MIUI 会在息屏后限制后台 CPU：建议开 Shizuku 增强，或把本应用加入自启动白名单。";
            case "coloros":
                return "ColorOS/一加 需要在「自启动管理」里允许本应用，否则息屏后可能被冻结。";
            case "originos":
                return "OriginOS 需要在「后台启动」里允许本应用，并关闭「智能省电」对该应用的限制。";
            case "emui":
            case "harmony":
                return "EMUI/MagicOS 需要在「应用启动管理」里改为手动管理，并允许后台活动。";
            case "oneui":
                return "One UI 的「休眠应用」会冻结后台：请在电池设置里把本应用设为「不受限制」。";
            default:
                return "原生/Pixel 默认策略较宽松，若被 Doze 限制可用电池优化白名单或 Shizuku 增强。";
        }
    }

    private Intent component(String pkg, String cls) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(pkg, cls));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    private Intent firstResolved(Intent... candidates) {
        for (Intent candidate : candidates) {
            Intent resolved = resolve(candidate);
            if (resolved != null) return resolved;
        }
        return null;
    }

    private Intent resolve(Intent intent) {
        if (intent == null) return null;
        PackageManager pm = context.getPackageManager();
        return pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null ? intent : null;
    }

    private static String safe(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
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
}
