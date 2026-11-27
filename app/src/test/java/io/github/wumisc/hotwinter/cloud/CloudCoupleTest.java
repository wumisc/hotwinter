package io.github.wumisc.hotwinter.cloud;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 情侣互动的纯规则测试：天数、动作冷却、关心提醒跃迁。 */
public class CloudCoupleTest {

    @Test
    public void togetherDaysStartsAtOneAndRollsAtMidnight() {
        long joined = 1_700_000_000_000L;
        assertEquals(0, CloudCouple.togetherDays(0L, joined));
        assertEquals(1, CloudCouple.togetherDays(joined, joined));
        assertEquals(1, CloudCouple.togetherDays(joined, joined + CloudCouple.DAY_MS - 1L));
        assertEquals(2, CloudCouple.togetherDays(joined, joined + CloudCouple.DAY_MS));
        assertEquals(1, CloudCouple.togetherDays(joined + 10_000L, joined));
    }

    @Test
    public void togetherDaysIsCapped() {
        assertEquals(CloudCouple.MAX_TOGETHER_DAYS,
                CloudCouple.togetherDays(1L, Long.MAX_VALUE));
    }

    @Test
    public void affectionHasThirtySecondCooldown() {
        long last = 100_000L;
        assertTrue(CloudCouple.canSendAffection(0L, last));
        assertFalse(CloudCouple.canSendAffection(last, last));
        assertFalse(CloudCouple.canSendAffection(last, last + 29_999L));
        assertTrue(CloudCouple.canSendAffection(last, last + 30_000L));
        assertFalse(CloudCouple.canSendAffection(last, last - 1L));
    }

    @Test
    public void heatAlertOnlyOnFalseToTrue() {
        assertFalse(CloudCouple.shouldAlertHeat(false, false, true));
        assertFalse(CloudCouple.shouldAlertHeat(true, true, true));
        assertFalse(CloudCouple.shouldAlertHeat(true, false, false));
        assertTrue(CloudCouple.shouldAlertHeat(true, false, true));
    }

    @Test
    public void batteryAlertNeedsTransitionAndCooldown() {
        long now = 1_700_000_000_000L;
        long sixHours = CloudCouple.BATTERY_CARE_COOLDOWN_MS;

        assertFalse("首次状态不提醒",
                CloudCouple.shouldAlertBattery(false, -1, false, 15, false, now, 0L));
        assertTrue("30% 掉到 15%，未充电，应提醒",
                CloudCouple.shouldAlertBattery(true, 30, false, 15, false, now, 0L));
        assertFalse("充电中不提醒",
                CloudCouple.shouldAlertBattery(true, 15, false, 15, true, now, 0L));
        assertTrue("低电量刚拔掉充电，提醒一次",
                CloudCouple.shouldAlertBattery(true, 15, true, 15, false, now, 0L));
        assertFalse("一直低电量，不再跃迁不提醒",
                CloudCouple.shouldAlertBattery(true, 15, false, 14, false, now, now - sixHours + 1L));
        assertTrue("6 小时冷却结束后，新的 30%→15% 可再次提醒",
                CloudCouple.shouldAlertBattery(true, 30, false, 15, false, now, now - sixHours));
        assertFalse("未知电量不提醒",
                CloudCouple.shouldAlertBattery(true, 30, false, -1, true, now, 0L));
        assertFalse("高于 20% 不提醒",
                CloudCouple.shouldAlertBattery(true, 30, false, 21, false, now, 0L));
    }
}
