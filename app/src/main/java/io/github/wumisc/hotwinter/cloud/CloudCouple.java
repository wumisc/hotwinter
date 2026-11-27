package io.github.wumisc.hotwinter.cloud;

/**
 * 情侣互动的纯规则：天数、冷却和提醒跃迁。
 *
 * <p>这里只放可单测的判断，不碰界面和网络。CloudPanel 负责表现，CloudSync 负责投递通知。</p>
 */
public final class CloudCouple {

    public static final long AFFECTION_COOLDOWN_MS = 30_000L;
    public static final long BATTERY_CARE_COOLDOWN_MS = 6L * 60L * 60L * 1000L;
    public static final long DAY_MS = 86_400_000L;
    public static final int MAX_TOGETHER_DAYS = 100_000;

    private CloudCouple() {
    }

    /** 配对当天算第 1 天；未来时间或缺少 joinedAt 时返回 0/1，不抛异常。 */
    public static int togetherDays(long joinedAt, long now) {
        if (joinedAt <= 0L) return 0;
        long elapsed = now - joinedAt;
        if (elapsed < 0L) elapsed = 0L;
        long days = elapsed / DAY_MS + 1L;
        return (int) Math.min(days, MAX_TOGETHER_DAYS);
    }

    /** 第一次发送不冷却；时钟回拨时按冷却处理，避免连点刷屏。 */
    public static boolean canSendAffection(long lastSentAt, long now) {
        if (lastSentAt <= 0L) return true;
        if (now <= lastSentAt) return false;
        return now - lastSentAt >= AFFECTION_COOLDOWN_MS;
    }

    /** 首次状态不提醒；只有从未加热变成加热时才提醒。 */
    public static boolean shouldAlertHeat(boolean hadStatus, boolean wasHeating, boolean heating) {
        return hadStatus && heating && !wasHeating;
    }

    /**
     * 首次状态不提醒；只在“从高于 20% 或充电中”进入低电量且未充电时提醒。
     * 冷却结束后，即使一直低电量，也需要再次发生跃迁才会提醒。
     */
    public static boolean shouldAlertBattery(boolean hadStatus, int lastBattery, boolean wasCharging,
                                             int battery, boolean charging, long now, long lastAlertAt) {
        if (!hadStatus || battery < 0 || battery > 20 || charging) return false;
        if (!(lastBattery > 20 || wasCharging)) return false;
        if (lastAlertAt > 0L && now <= lastAlertAt) return false;
        return now - lastAlertAt >= BATTERY_CARE_COOLDOWN_MS;
    }
}
