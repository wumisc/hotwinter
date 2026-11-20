package io.github.wumisc.hotwinter.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** 设置项夹紧逻辑：任何异常输入都不能把温控带到非法区间。 */
public class SettingsClampTest {

    @Test
    public void targetIsClampedTo30Through43() {
        assertEquals(30f, SettingsStore.clampTarget(12f), 0.001f);
        assertEquals(43f, SettingsStore.clampTarget(60f), 0.001f);
        assertEquals(36f, SettingsStore.clampTarget(36f), 0.001f);
        assertEquals(30f, SettingsStore.clampTarget(Float.NaN), 0.001f);
    }

    @Test
    public void autoOffIsClampedTo10Through45() {
        assertEquals(10, SettingsStore.clampAutoOff(0));
        assertEquals(45, SettingsStore.clampAutoOff(120));
        assertEquals(25, SettingsStore.clampAutoOff(25));
        assertEquals(10, SettingsStore.clampAutoOff(-7));
    }

    @Test
    public void batteryFloorIsClampedTo10Through40() {
        assertEquals(10, SettingsStore.clampFloor(1));
        assertEquals(40, SettingsStore.clampFloor(99));
        assertEquals(20, SettingsStore.clampFloor(20));
    }
}
