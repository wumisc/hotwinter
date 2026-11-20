package io.github.wumisc.hotwinter.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** 稳定性测试：长跑随机输入 + 并发状态广播，确认不会发散、不会崩。 */
public class HeatStabilityTest {

    @Test
    public void tenThousandRandomEvaluationsKeepDutyLegal() {
        Random random = new Random(20261117L);
        float previousDuty = 0.3f;
        float integral = 0f;
        int halted = 0;

        for (int i = 0; i < 10_000; i++) {
            float temp = -10f + random.nextFloat() * 70f; // -10 ~ 60，含异常读数
            int level = random.nextInt(101);
            boolean charging = random.nextBoolean();
            int thermal = random.nextInt(5);
            Preset preset = Preset.values()[random.nextInt(Preset.values().length)];
            float target = 30f + random.nextFloat() * 13f;
            HeatPolicy.Args args = new HeatPolicy.Args(preset, target, 20, random.nextBoolean());

            HeatPolicy.Output out = HeatPolicy.evaluate(
                    new HeatSample(temp, level, charging, thermal), args, previousDuty, integral, 2.5f);

            assertTrue("占空比必须落在 [0, cap] 内: " + out.duty,
                    out.duty >= 0f && out.duty <= args.preset.dutyCap * 1.0001f);
            assertTrue("积分项必须被限幅: " + out.integral,
                    out.integral >= -45.001f && out.integral <= 45.001f);
            if (!out.shouldHalt()) {
                assertTrue("运行中占空比不低于下限: " + out.duty, out.duty >= 0.1199f);
            }

            if (out.shouldHalt()) {
                halted++;
                assertEquals(0f, out.duty, 0.0001f);
            } else {
                previousDuty = out.duty;
                integral = out.integral;
            }
        }

        assertTrue("随机输入下应有一部分被安全停机", halted > 0);
        assertTrue("不应每次都被停机", halted < 10_000);
        assertFalse("积分不应为 NaN", Float.isNaN(integral));
    }

    @Test
    public void runtimeBroadcastsAreThreadSafe() {
        HeatRuntime.publish(HeatStatus.idle());
        final AtomicInteger callbacks = new AtomicInteger();
        final AtomicReference<HeatStatus> latest = new AtomicReference<>(HeatStatus.idle());
        Runnable listener = () -> {
            callbacks.incrementAndGet();
            latest.set(HeatRuntime.get());
        };
        HeatRuntime.addListener(listener);

        int threads = 6;
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 500; i++) {
                        HeatStatus status = new HeatStatus(i % 2 == 0, 0.4f, 3, 35f, 0, 60, false,
                                1_000L, Preset.BALANCED, 36f, "测试");
                        HeatRuntime.publish(status);
                        HeatRuntime.get();
                    }
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }

        start.countDown();
        try {
            done.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        HeatRuntime.removeListener(listener);
        assertTrue("监听回调应被触发", callbacks.get() > 0);
        assertTrue("状态必须始终可读", latest.get() != null);
        assertEquals(HeatRuntime.isActive(), latest.get().running);
    }

    @Test
    public void threadCountAlwaysLeavesOneCoreForSystem() {
        for (int cores = 1; cores <= 16; cores++) {
            for (Preset preset : Preset.values()) {
                int threads = preset.threadCount(cores);
                assertTrue("至少 1 线程", threads >= 1);
                if (cores > 1) {
                    assertTrue("必须给系统留核", threads <= cores - 1);
                }
            }
        }
    }
}
