package io.github.wumisc.hotwinter.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SocProfileTest {

    @Test
    public void matchesKnownSnapdragon() {
        SocProfile soc = SocProfile.match("qcom sm8650 pineapple", 8);
        assertTrue(soc.name.contains("8 Gen 3"));
        assertTrue(soc.sustainedW > 2f);
    }

    @Test
    public void matchesKnownDimensity() {
        SocProfile soc = SocProfile.match("mt6989", 8);
        assertTrue(soc.name.contains("9300"));
    }

    @Test
    public void unknownSocIsConservative() {
        SocProfile soc = SocProfile.match("totally-unknown-chip", 8);
        assertTrue(soc.name.contains("未知"));
        assertTrue(soc.planThreads(Preset.STRONG, 8) <= 4);
    }

    @Test
    public void threadsAlwaysLeaveOneCoreForSystem() {
        SocProfile soc = SocProfile.match("sm8650", 8);
        for (Preset preset : Preset.values()) {
            for (int cores = 2; cores <= 10; cores++) {
                int threads = soc.planThreads(preset, cores);
                assertTrue(threads >= 1);
                assertTrue(threads <= cores - 1);
            }
        }
    }

    @Test
    public void strongerPresetNeverUsesFewerThreads() {
        SocProfile soc = SocProfile.match("sm8550", 8);
        assertTrue(soc.planThreads(Preset.STRONG, 8) >= soc.planThreads(Preset.BALANCED, 8));
        assertTrue(soc.planThreads(Preset.BALANCED, 8) >= soc.planThreads(Preset.GENTLE, 8));
    }

    @Test
    public void oldSocCapsDutyBelowPresetLimit() {
        SocProfile soc = SocProfile.match("sm4350", 8);
        assertTrue(soc.dutyCap(Preset.STRONG) < Preset.STRONG.dutyCap);
        assertEquals(Preset.STRONG.dutyCap, SocProfile.match("sm8650", 8).dutyCap(Preset.STRONG), 0.0001f);
    }
}
