package io.github.wumisc.hotwinter.core;

/**
 * 温控策略：纯 Java、无 Android 依赖，可直接跑 JVM 单测。
 *
 * 先做硬保护（电量、过热），再做降档，最后才做比例积分调节，
 * 让占空比稳定收敛到「刚好维持目标温度」的水平——这就是省电的来源。
 */
public final class HeatPolicy {

    public static final float HARD_LIMIT_C = 45f;
    public static final float DERATE_C = 42f;

    private static final float KP = 0.09f;
    private static final float KI = 0.006f;
    private static final float MIN_DUTY = 0.12f;
    private static final float SLEW_PER_TICK = 0.15f;

    private HeatPolicy() {
    }

    public static final class Args {
        public final Preset preset;
        public final float targetC;
        public final int batteryFloor;
        public final boolean ecoMode;

        public Args(Preset preset, float targetC, int batteryFloor, boolean ecoMode) {
            this.preset = preset;
            this.targetC = targetC;
            this.batteryFloor = batteryFloor;
            this.ecoMode = ecoMode;
        }
    }

    public static final class Output {
        public final float duty;
        public final float integral;
        public final String haltReason;
        public final String note;

        public Output(float duty, float integral, String haltReason, String note) {
            this.duty = duty;
            this.integral = integral;
            this.haltReason = haltReason;
            this.note = note;
        }

        public boolean shouldHalt() {
            return haltReason != null;
        }
    }

    public static Output evaluate(HeatSample sample, Args args, float previousDuty, float integral, float dtSec) {
        if (sample.batteryLevel > 0
                && sample.batteryLevel <= args.batteryFloor
                && !sample.charging) {
            return new Output(0f, 0f, "电量已低于 " + args.batteryFloor + "% 保护线", "");
        }
        if (sample.thermalStatus >= HeatSample.THERMAL_SEVERE
                || sample.batteryTempC >= HARD_LIMIT_C) {
            return new Output(0f, 0f, "机身温度已达安全上限 45°C", "");
        }

        float cap = args.preset.dutyCap * (args.ecoMode ? 0.7f : 1f);
        if (sample.thermalStatus >= HeatSample.THERMAL_MODERATE) {
            cap = Math.min(cap, 0.35f);
        }
        if (sample.batteryTempC >= DERATE_C) {
            cap = Math.min(cap, 0.25f);
        }

        float target = args.ecoMode ? args.targetC - 1.5f : args.targetC;
        float error = target - sample.batteryTempC;
        float nextIntegral = clamp(integral + error * dtSec, -45f, 45f);

        float raw = cap * 0.45f + KP * error + KI * nextIntegral;
        float saturated = clamp(raw, MIN_DUTY, cap);
        float slewed = clamp(saturated, previousDuty - SLEW_PER_TICK, previousDuty + SLEW_PER_TICK);
        slewed = clamp(slewed, MIN_DUTY, cap);

        String note = "";
        if (sample.thermalStatus >= HeatSample.THERMAL_MODERATE) {
            note = "系统热控介入，已自动降档";
        } else if (sample.batteryTempC >= DERATE_C) {
            note = "接近温度上限，已自动降档";
        }
        return new Output(slewed, nextIntegral, null, note);
    }

    private static float clamp(float value, float min, float max) {
        if (max < min) return max;
        return Math.max(min, Math.min(max, value));
    }
}
