package io.github.wumisc.hotwinter.core;

import java.util.Locale;

/**
 * SoC 方案表：不同芯片的「可持续热功率」「单线程功耗」「线程上限」不一样，
 * 统一拉满只会让老机器先撞热控、旗舰机白耗电。这里按芯片给配额。
 *
 * 纯 Java，无 Android 依赖，便于单测与复用。
 */
public final class SocProfile {

    public final String key;
    public final String name;
    /** 可持续散热功率（W），超过它大概率触发系统降频，反而更不热。 */
    public final float sustainedW;
    /** 单线程满占空比的平均功耗估算（W）。 */
    public final float perThreadW;
    /** 线程上限（留出 UI/系统线程余量）。 */
    public final int maxThreads;
    /** 占空比上限的收紧系数，老芯片先压一档。 */
    public final float dutyScale;

    private static final SocProfile UNKNOWN = new SocProfile(
            "unknown", "未知 SoC（保守方案）", 1.8f, 0.75f, 4, 0.85f);

    private static final Entry[] TABLE = {
            // 高通骁龙
            entry("sm8750", "骁龙 8 Elite", 3.4f, 0.52f, 7, 1.00f),
            entry("sun", "骁龙 8 Elite", 3.4f, 0.52f, 7, 1.00f),
            entry("sm8650", "骁龙 8 Gen 3", 3.1f, 0.55f, 7, 1.00f),
            entry("pineapple", "骁龙 8 Gen 3", 3.1f, 0.55f, 7, 1.00f),
            entry("sm8550", "骁龙 8 Gen 2", 3.0f, 0.58f, 7, 1.00f),
            entry("kalama", "骁龙 8 Gen 2", 3.0f, 0.58f, 7, 1.00f),
            entry("sm8475", "骁龙 8+ Gen 1", 2.8f, 0.60f, 7, 0.97f),
            entry("sm8450", "骁龙 8 Gen 1", 2.4f, 0.66f, 6, 0.90f),
            entry("taro", "骁龙 8 Gen 1", 2.4f, 0.66f, 6, 0.90f),
            entry("sm8250", "骁龙 865", 2.4f, 0.70f, 7, 0.95f),
            entry("kona", "骁龙 865", 2.4f, 0.70f, 7, 0.95f),
            entry("sm8150", "骁龙 855", 2.2f, 0.75f, 6, 0.90f),
            entry("sm7325", "骁龙 778G", 2.0f, 0.75f, 6, 0.90f),
            entry("sm7125", "骁龙 750G", 1.9f, 0.80f, 6, 0.88f),
            entry("sm6375", "骁龙 695", 1.8f, 0.80f, 6, 0.85f),
            entry("sm4350", "骁龙 480", 1.6f, 0.90f, 6, 0.80f),
            // 联发科天玑
            entry("mt6991", "天玑 9400", 3.3f, 0.52f, 7, 1.00f),
            entry("mt6989", "天玑 9300", 3.1f, 0.55f, 7, 1.00f),
            entry("mt6985", "天玑 9200", 2.9f, 0.58f, 7, 0.98f),
            entry("mt6983", "天玑 9000", 2.6f, 0.62f, 7, 0.95f),
            entry("mt6896", "天玑 8200", 2.1f, 0.70f, 6, 0.90f),
            entry("mt6893", "天玑 1200", 2.2f, 0.72f, 6, 0.90f),
            entry("mt6877", "天玑 900", 1.8f, 0.80f, 6, 0.86f),
            entry("mt6833", "天玑 700", 1.7f, 0.85f, 6, 0.82f),
            entry("mt6789", "天玑 1080 / Helio G99", 1.7f, 0.85f, 6, 0.82f),
            // 麒麟
            entry("kirin9000s", "麒麟 9000S", 2.6f, 0.66f, 6, 0.92f),
            entry("kirin9000", "麒麟 9000", 2.5f, 0.64f, 6, 0.92f),
            entry("kirin990", "麒麟 990", 2.2f, 0.72f, 6, 0.88f),
            entry("kirin985", "麒麟 985", 2.0f, 0.76f, 6, 0.86f),
            entry("kirin810", "麒麟 810", 1.7f, 0.85f, 6, 0.82f),
            // 三星 / 谷歌
            entry("exynos2400", "Exynos 2400", 2.6f, 0.62f, 7, 0.93f),
            entry("exynos2200", "Exynos 2200", 2.3f, 0.70f, 6, 0.88f),
            entry("exynos1380", "Exynos 1380", 1.9f, 0.80f, 6, 0.85f),
            entry("gs201", "Tensor G2", 2.5f, 0.66f, 6, 0.92f),
            entry("gs101", "Tensor", 2.4f, 0.68f, 6, 0.90f),
    };


    private SocProfile(String key, String name, float sustainedW, float perThreadW,
                       int maxThreads, float dutyScale) {
        this.key = key;
        this.name = name;
        this.sustainedW = sustainedW;
        this.perThreadW = perThreadW;
        this.maxThreads = maxThreads;
        this.dutyScale = dutyScale;
    }

    private static final class Entry {
        final String key;
        final SocProfile profile;

        Entry(String key, SocProfile profile) {
            this.key = key;
            this.profile = profile;
        }
    }

    private static Entry entry(String key, String name, float sustainedW, float perThreadW,
                               int maxThreads, float dutyScale) {
        return new Entry(key.toLowerCase(Locale.ROOT),
                new SocProfile(key, name, sustainedW, perThreadW, maxThreads, dutyScale));
    }

    /** rawId 可以是 ro.soc.model / ro.board.platform / Build.HARDWARE 的拼接串，按包含关系匹配。 */
    public static SocProfile match(String rawId, int cores) {
        if (rawId != null) {
            String lower = rawId.toLowerCase(Locale.ROOT);
            for (Entry e : TABLE) {
                if (lower.contains(e.key)) return e.profile;
            }
        }
        return new SocProfile(UNKNOWN.key, UNKNOWN.name, UNKNOWN.sustainedW,
                UNKNOWN.perThreadW, Math.max(1, Math.min(4, Math.max(1, cores - 1))), UNKNOWN.dutyScale);
    }

    /**
     * 线程配额 = min(档位热预算, 芯片可持续功率) / 单线程功耗，
     * 再被 cores-1 与芯片线程上限夹住。这样老机器不会莫名其妙开 7 线程。
     */
    public int planThreads(Preset preset, int cores) {
        int safeCores = Math.max(1, cores);
        float budgetW = Math.min(preset.powerBudgetW, sustainedW);
        int byPower = Math.round(budgetW / perThreadW);
        int upper = Math.min(maxThreads, safeCores - 1);
        upper = Math.max(1, upper);
        return Math.max(1, Math.min(byPower, upper));
    }

    /** 该芯片下的占空比硬上限（在档位基础上再收紧一档）。 */
    public float dutyCap(Preset preset) {
        return preset.dutyCap * dutyScale;
    }

    public String planText(Preset preset, int cores) {
        return name + " · " + planThreads(preset, cores) + " 线程 · 上限 "
                + Math.round(dutyCap(preset) * 100) + "%";
    }
}
