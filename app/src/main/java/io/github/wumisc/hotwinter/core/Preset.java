package io.github.wumisc.hotwinter.core;

/** 加热档位：核心占比 + 占空比上限决定「发热强度 / 耗电」的平衡位置。 */
public enum Preset {
    GENTLE("gentle", "温和", 0.30f, 0.55f, 33f, "两三核轻载，暖手不烫手，最省电", 1.6f),
    BALANCED("balanced", "标准", 0.50f, 0.78f, 36f, "默认档，升温快、耗电可控", 3.2f),
    STRONG("strong", "强力", 0.80f, 0.95f, 39f, "多核并发，冷到极限时再用", 5.0f),
    EXTREME("extreme", "极热", 1.00f, 1.00f, 42f, "全核推进，短时暴热，最费电，注意烫手", 7.2f);

    public final String key;
    public final String label;
    public final float coreFraction;
    public final float dutyCap;
    public final float defaultTargetC;
    public final String summary;
    /** 该档位的热预算（W）：真正决定「有多热」，也决定「费多少电」。 */
    public final float powerBudgetW;

    Preset(String key, String label, float coreFraction, float dutyCap, float defaultTargetC,
           String summary, float powerBudgetW) {
        this.key = key;
        this.label = label;
        this.coreFraction = coreFraction;
        this.dutyCap = dutyCap;
        this.defaultTargetC = defaultTargetC;
        this.summary = summary;
        this.powerBudgetW = powerBudgetW;
    }

    public int threadCount() {
        return threadCount(Runtime.getRuntime().availableProcessors());
    }

    public int threadCount(int cores) {
        int raw = Math.round(cores * coreFraction);
        int upper = Math.max(1, cores - 1);
        return Math.max(1, Math.min(raw, upper));
    }

    public static Preset of(String key) {
        if (key != null) {
            for (Preset p : values()) {
                if (p.key.equals(key)) return p;
            }
        }
        return BALANCED;
    }
}
