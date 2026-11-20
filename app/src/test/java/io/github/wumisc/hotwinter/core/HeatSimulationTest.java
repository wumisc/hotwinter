package io.github.wumisc.hotwinter.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 用一阶热学模型跑完整的 400 次温控心跳，验证「目标温度能稳定收敛、不会过冲失控」。
 * 这不是替代真机测试，而是把控制环的逻辑在 JVM 里跑一遍，防止改参数时引入震荡。
 */
public class HeatSimulationTest {

    private static final float AMBIENT = 25f;
    private static final float DT = 2.5f;

    /** 一次模拟结果。 */
    private static final class Trace {
        float finalTemp;
        float finalSensor;
        float finalDuty;
        float maxTemp;
        float lastDutyMin;
        float lastDutyMax;
        boolean halted;
    }

    private Trace simulate(float gain, float loss, float sensorTau) {
        HeatPolicy.Args args = new HeatPolicy.Args(Preset.BALANCED, 36f, 20, false);
        float temp = AMBIENT;
        float sensor = AMBIENT;
        float duty = Preset.BALANCED.dutyCap * 0.25f;
        float integral = 0f;

        Trace trace = new Trace();
        trace.maxTemp = temp;
        float dutyMin = Float.MAX_VALUE;
        float dutyMax = -Float.MAX_VALUE;

        for (int tick = 0; tick < 400; tick++) {
            HeatSample sample = new HeatSample(sensor, 80, false, HeatSample.THERMAL_NONE);
            HeatPolicy.Output out = HeatPolicy.evaluate(sample, args, duty, integral, DT);
            if (out.shouldHalt()) {
                trace.halted = true;
                break;
            }
            duty = out.duty;
            integral = out.integral;

            temp += (gain * duty - loss * (temp - AMBIENT)) * DT;
            sensor += (temp - sensor) * (DT / sensorTau);
            trace.maxTemp = Math.max(trace.maxTemp, temp);

            if (tick >= 380) {
                dutyMin = Math.min(dutyMin, duty);
                dutyMax = Math.max(dutyMax, duty);
            }
        }

        trace.finalTemp = temp;
        trace.finalSensor = sensor;
        trace.finalDuty = duty;
        trace.lastDutyMin = dutyMin;
        trace.lastDutyMax = dutyMax;
        return trace;
    }

    @Test
    public void convergesToTargetWithoutOvershoot() {
        Trace trace = simulate(0.70f, 0.02f, 20f);
        assertFalse("不应触发保护停机", trace.halted);
        assertEquals("稳态温度应收敛到目标", 36f, trace.finalSensor, 0.6f);
        assertTrue("占空比不应超过档位上限", trace.finalDuty <= Preset.BALANCED.dutyCap + 0.001f);
        assertTrue("过程中不应超过硬保护温度", trace.maxTemp < HeatPolicy.HARD_LIMIT_C);
        assertTrue("稳态占空比应稳定", trace.lastDutyMax - trace.lastDutyMin < 0.02f);
    }

    @Test
    public void heavierLoadSettlesAtLowerDuty() {
        Trace light = simulate(0.55f, 0.02f, 20f);
        Trace heavy = simulate(0.90f, 0.02f, 20f);
        assertTrue("热得更快时应用更低占空比", heavy.finalDuty < light.finalDuty);
        assertEquals(36f, heavy.finalSensor, 0.6f);
    }

    @Test
    public void runawayHeatIsStoppedByHardLimit() {
        HeatPolicy.Args args = new HeatPolicy.Args(Preset.BALANCED, 36f, 20, false);
        float duty = 0.7f;
        float integral = 0f;
        float temp = 30f;
        boolean halted = false;

        for (int tick = 0; tick < 200; tick++) {
            temp += 1.2f * duty; // 夸张的升温速度，模拟散热失败
            HeatSample sample = new HeatSample(temp, 70, false, HeatSample.THERMAL_NONE);
            HeatPolicy.Output out = HeatPolicy.evaluate(sample, args, duty, integral, DT);
            if (out.shouldHalt()) {
                halted = true;
                break;
            }
            duty = out.duty;
        }

        assertTrue("温度冲到 45°C 必须停机", halted);
        assertTrue("停机温度不应明显低于硬限", temp >= HeatPolicy.HARD_LIMIT_C - 2.5f);
    }
}
