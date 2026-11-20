package io.github.wumisc.hotwinter.danmaku;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.CopyOnWriteArrayList;

/** 弹幕事件总线：UDP、蓝牙、本机预览都从这里分发，界面和悬浮窗只订阅。 */
public final class DmBus {

    public interface Listener {
        void onDanmaku(DmMessage message);

        default void onStatus(String status) {
        }
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    private DmBus() {
    }

    public static void addListener(Listener listener) {
        if (listener != null && !LISTENERS.contains(listener)) {
            LISTENERS.add(listener);
        }
    }

    public static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    public static void publish(DmMessage message) {
        if (message == null) return;
        MAIN.post(() -> {
            for (Listener listener : LISTENERS) {
                try {
                    listener.onDanmaku(message);
                } catch (Throwable error) {
                    android.util.Log.e("DmBus", "弹幕监听异常", error);
                }
            }
        });
    }

    public static void publishStatus(String status) {
        if (status == null) return;
        MAIN.post(() -> {
            for (Listener listener : LISTENERS) {
                try {
                    listener.onStatus(status);
                } catch (Throwable error) {
                    android.util.Log.e("DmBus", "弹幕状态监听异常", error);
                }
            }
        });
    }
}
