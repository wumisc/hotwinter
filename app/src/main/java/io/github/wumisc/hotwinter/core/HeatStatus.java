package io.github.wumisc.hotwinter.core;

/** 界面、通知、磁贴共用的运行时快照，由前台服务单向写入。 */
public final class HeatStatus {

    public final boolean running;
    public final float duty;
    public final int threadCount;
    public final float batteryTempC;
    public final int thermalStatus;
    public final int batteryLevel;
    public final boolean charging;
    public final long startedAt;
    public final Preset preset;
    public final float targetC;
    public final String note;

    public HeatStatus(boolean running, float duty, int threadCount, float batteryTempC,
                      int thermalStatus, int batteryLevel, boolean charging,
                      long startedAt, Preset preset, float targetC, String note) {
        this.running = running;
        this.duty = duty;
        this.threadCount = threadCount;
        this.batteryTempC = batteryTempC;
        this.thermalStatus = thermalStatus;
        this.batteryLevel = batteryLevel;
        this.charging = charging;
        this.startedAt = startedAt;
        this.preset = preset;
        this.targetC = targetC;
        this.note = note;
    }

    public static HeatStatus idle() {
        return new HeatStatus(false, 0f, 0, 0f, HeatSample.THERMAL_NONE, 0, false,
                0L, Preset.BALANCED, Preset.BALANCED.defaultTargetC, "");
    }

    public long elapsedMs(long now) {
        if (!running || startedAt <= 0L) return 0L;
        return Math.max(0L, now - startedAt);
    }
}
