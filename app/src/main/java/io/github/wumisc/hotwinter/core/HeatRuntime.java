package io.github.wumisc.hotwinter.core;

import java.util.concurrent.CopyOnWriteArrayList;

/** 全局状态单例：服务写入，界面 / 磁贴读取，双方不持有对方引用。 */
public final class HeatRuntime {

    private static volatile HeatStatus current = HeatStatus.idle();
    private static final CopyOnWriteArrayList<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private HeatRuntime() {
    }

    public static HeatStatus get() {
        return current;
    }

    public static boolean isActive() {
        return current.running;
    }

    public static void publish(HeatStatus status) {
        current = status;
        for (Runnable listener : LISTENERS) {
            try {
                listener.run();
            } catch (RuntimeException ignored) {
                // 单个监听者异常不应影响状态广播
            }
        }
    }

    public static void addListener(Runnable listener) {
        if (listener != null && !LISTENERS.contains(listener)) {
            LISTENERS.add(listener);
        }
    }

    public static void removeListener(Runnable listener) {
        LISTENERS.remove(listener);
    }
}
