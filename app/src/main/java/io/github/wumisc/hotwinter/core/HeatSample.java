package io.github.wumisc.hotwinter.core;

/** 一次温控采样。温度取电池温度（0.1°C 精度），是普通应用最可靠的机身温度代理。 */
public final class HeatSample {

    public static final int THERMAL_NONE = 0;
    public static final int THERMAL_LIGHT = 1;
    public static final int THERMAL_MODERATE = 2;
    public static final int THERMAL_SEVERE = 3;
    public static final int THERMAL_CRITICAL = 4;

    public final float batteryTempC;
    public final int batteryLevel;
    public final boolean charging;
    public final int thermalStatus;

    public HeatSample(float batteryTempC, int batteryLevel, boolean charging, int thermalStatus) {
        this.batteryTempC = batteryTempC;
        this.batteryLevel = batteryLevel;
        this.charging = charging;
        this.thermalStatus = thermalStatus;
    }

    public static HeatSample empty() {
        return new HeatSample(0f, 0, false, THERMAL_NONE);
    }
}
