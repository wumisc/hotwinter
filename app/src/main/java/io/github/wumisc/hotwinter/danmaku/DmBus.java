package io.github.wumisc.hotwinter.danmaku;

import android.os.Handler;
import android.os.Looper;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.concurrent.CopyOnWriteArrayList;

/** 弹幕事件总线：UDP、蓝牙、云端和本机预览都从这里分发，界面和悬浮窗只订阅。 */
public final class DmBus {

    public interface Listener {
        void onDanmaku(DmMessage message);

        default void onStatus(String status) {
        }
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Object SEEN_LOCK = new Object();
    private static final int MAX_SEEN_IDS = 200;
    private static final LinkedHashSet<String> SEEN_IDS = new LinkedHashSet<>();

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

    /**
     * 发布一条弹幕。
     *
     * @return true 表示第一次出现；false 表示同一 id 已从其他通道到达。
     */
    public static boolean publish(DmMessage message) {
        if (message == null) return false;
        if (!markSeen(message.id)) return false;
        MAIN.post(() -> {
            for (Listener listener : LISTENERS) {
                try {
                    listener.onDanmaku(message);
                } catch (Throwable error) {
                    android.util.Log.e("DmBus", "弹幕监听异常", error);
                }
            }
        });
        return true;
    }

    /** 同一句弹幕从云端、蓝牙、局域网合并到达时只展示一次。 */
    private static boolean markSeen(String id) {
        if (id == null || id.isEmpty()) return true;
        synchronized (SEEN_LOCK) {
            if (!SEEN_IDS.add(id)) return false;
            if (SEEN_IDS.size() > MAX_SEEN_IDS) {
                Iterator<String> oldest = SEEN_IDS.iterator();
                if (oldest.hasNext()) oldest.next();
            }
            return true;
        }
    }

    /** 测试用：清空去重窗口，避免用例之间互相污染。 */
    static void clearForTest() {
        synchronized (SEEN_LOCK) {
            SEEN_IDS.clear();
        }
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
