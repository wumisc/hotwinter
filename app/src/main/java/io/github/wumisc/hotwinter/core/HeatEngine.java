package io.github.wumisc.hotwinter.core;

import android.os.Process;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/**
 * 发热引擎：N 个线程按可变占空比跑浮点负载。
 *
 * 关键改动（1.2.1）：线程不再降到后台优先级（那会被系统压到小核、限频，
 * 表现为「占空比显示 70% 但机身完全不热」），改用普通优先级；
 * 同时把时间检查从每 32 次浮点运算一次，降到每 512 次一次，减少挂钟调用开销，
 * 让同样的占空比真正压满 CPU。
 */
public final class HeatEngine {

    /** 24ms 切片，够短才能让占空比平滑，又不至于让 nanosleep 精度成为主角。 */
    private static final long SLICE_NS = 24_000_000L;

    /** 一批浮点运算，跑完再查一次时间，避免 System.nanoTime 抢占周期。 */
    private static final int BATCH = 512;
    private static final int BATCHES_PER_CHECK = 4;

    private static final Object LOCK = new Object();
    private static final AtomicBoolean ACTIVE = new AtomicBoolean(false);
    private static final List<Thread> WORKERS = new ArrayList<>();

    private static volatile float duty = 0f;
    private static volatile double sink = 0.0;
    private static volatile int threadCount = 0;

    private HeatEngine() {
    }

    public static boolean isRunning() {
        return ACTIVE.get();
    }

    public static int threadCount() {
        return threadCount;
    }

    public static void start(int threads) {
        int want = Math.max(1, threads);
        synchronized (LOCK) {
            if (ACTIVE.get() && threadCount == want) return;
            haltLocked();
            ACTIVE.set(true);
            threadCount = want;
            for (int i = 0; i < want; i++) {
                Thread worker = new Thread(() -> workLoop(), "hotwinter-worker-" + i);
                worker.setDaemon(true);
                worker.setPriority(Thread.NORM_PRIORITY);
                WORKERS.add(worker);
                worker.start();
            }
        }
    }

    public static void stop() {
        synchronized (LOCK) {
            haltLocked();
        }
    }

    /** 由温控循环每次心跳写入，工作线程只读取。 */
    public static void setDuty(float value) {
        duty = Math.max(0f, Math.min(1f, value));
    }

    private static void haltLocked() {
        ACTIVE.set(false);
        for (Thread worker : WORKERS) {
            worker.interrupt();
        }
        WORKERS.clear();
        threadCount = 0;
        duty = 0f;
    }

    private static void workLoop() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);
        } catch (Throwable ignored) {
            // 极少数 ROM 会拒绝，退回 Java 普通优先级
        }

        double[] buffer = new double[BATCH];
        double[] mirror = new double[BATCH];
        for (int i = 0; i < BATCH; i++) {
            buffer[i] = 1.0 + i * 0.001;
            mirror[i] = 0.5 + i * 0.002;
        }
        double x = 1.2345;

        while (ACTIVE.get() && !Thread.currentThread().isInterrupted()) {
            float currentDuty = duty;
            long busyNs = (long) (SLICE_NS * currentDuty);

            if (busyNs > 0) {
                long deadline = System.nanoTime() + busyNs;
                do {
                    for (int round = 0; round < BATCHES_PER_CHECK; round++) {
                        for (int i = 0; i < BATCH; i++) {
                            double v = buffer[i];
                            double w = mirror[i];
                            v = v * 1.0000003 + 0.17 + w;
                            w = w * 0.9999997 + 0.11 + v;
                            if (v > 1.0e6) v -= 1.0e6;
                            if (w > 1.0e6) w -= 1.0e6;
                            buffer[i] = v;
                            mirror[i] = w;
                        }
                        x = buffer[(int) (x * 7.0) & (BATCH - 1)];
                    }
                } while (System.nanoTime() < deadline);
                sink = x + buffer[BATCH - 1];
            }

            long restNs = SLICE_NS - busyNs;
            if (restNs > 0) {
                if (restNs >= 1_000_000L) {
                    try {
                        Thread.sleep(restNs / 1_000_000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    LockSupport.parkNanos(restNs);
                }
            }
        }
    }
}
