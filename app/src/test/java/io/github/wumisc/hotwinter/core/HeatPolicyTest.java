package io.github.wumisc.hotwinter.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 温控策略的纯 JVM 测试：不依赖 Android，直接跑 Gradle test。 */
public class HeatPolicyTest {

    private static final HeatPolicy.Args BALANCED =
            new HeatPolicy.Args(Preset.BALANCED, 36f, 20, false);

    private static HeatSample sample(float tempC, int level, boolean charging, int thermal) {
        return new HeatSample(tempC, level, charging, thermal);
    }

    @Test
    public void lowBatteryWithoutChargingHalts() {
        HeatPolicy.Output out = HeatPolicy.evaluate(sample(33f, 15, false, HeatSample.THERMAL_NONE),
                BALANCED, 0.4f, 0f, 2.5f);
        assertTrue(out.shouldHalt());
        assertEquals(0f, out.duty, 0.0001f);
    }

    @Test
    public void lowBatteryButChargingKeepsRunning() {
        HeatPolicy.Output out = HeatPolicy.evaluate(sample(33f, 15, true, HeatSample.THERMAL_NONE),
                BALANCED, 0.4f, 0f, 2.5f);
        assertFalse(out.shouldHalt());
        assertTrue(out.duty > 0f);
    }

    @Test
    public void hardLimitTemperatureHalts() {
        HeatPolicy.Output out = HeatPolicy.evaluate(sample(45f, 60, true, HeatSample.THERMAL_NONE),
                BALANCED, 0.5f, 0f, 2.5f);
        assertTrue(out.shouldHalt());
    }

    @Test
    public void severeThermalStatusHalts() {
        HeatPolicy.Output out = HeatPolicy.evaluate(sample(38f, 60, false, HeatSample.THERMAL_SEVERE),
                BALANCED, 0.5f, 0f, 2.5f);
        assertTrue(out.shouldHalt());
    }

    @Test
    public void dutyNeverExceedsPresetCap() {
        HeatPolicy.Output out = HeatPolicy.evaluate(sample(30f, 70, false, HeatSample.THERMAL_NONE),
                BALANCED, 0.9f, 0.9f, 2.5f);
        assertFalse(out.shouldHalt());
        assertTrue("duty=" + out.duty, out.duty <= Preset.BALANCED.dutyCap + 0.0001f);
    }

    @Test
    public void moderateThermalDeratesTo035() {
        HeatPolicy.Output out = HeatPolicy.evaluate(sample(38f, 70, false, HeatSample.THERMAL_MODERATE),
                BALANCED, 0.6f, 0f, 2.5f);
        assertFalse(out.shouldHalt());
        assertTrue("duty=" + out.duty, out.duty <= 0.35f + 0.0001f);
        assertFalse(out.note.isEmpty());
    }

    @Test
    public void aboveTargetReducesDuty() {
        HeatPolicy.Output hot = HeatPolicy.evaluate(sample(40f, 70, false, HeatSample.THERMAL_NONE),
                BALANCED, 0.7f, 0f, 2.5f);
        HeatPolicy.Output cold = HeatPolicy.evaluate(sample(31f, 70, false, HeatSample.THERMAL_NONE),
                BALANCED, 0.7f, 0f, 2.5f);
        assertTrue(hot.duty < cold.duty);
    }

    @Test
    public void ecoModeLowersCapAndTarget() {
        HeatPolicy.Args eco = new HeatPolicy.Args(Preset.STRONG, 39f, 20, true);
        HeatPolicy.Output out = HeatPolicy.evaluate(sample(37f, 70, false, HeatSample.THERMAL_NONE),
                eco, 0.9f, 0f, 2.5f);
        assertFalse(out.shouldHalt());
        assertTrue("duty=" + out.duty, out.duty <= Preset.STRONG.dutyCap * 0.7f + 0.0001f);
    }

    @Test
    public void slewLimitsJumpsBetweenTicks() {
        HeatPolicy.Output out = HeatPolicy.evaluate(sample(28f, 70, false, HeatSample.THERMAL_NONE),
                BALANCED, 0.2f, 0f, 2.5f);
        assertTrue("单次跳变过大: " + out.duty, out.duty <= 0.35f + 0.0001f);
    }

    @Test
    public void gentlePresetUsesFewerThreadsThanStrong() {
        int cores = 8;
        assertTrue(Preset.GENTLE.threadCount(cores) < Preset.STRONG.threadCount(cores));
        assertEquals(1, Preset.GENTLE.threadCount(1));
        assertTrue(Preset.STRONG.threadCount(2) <= 1);
    }
}
