package io.github.wumisc.hotwinter.data;

import android.content.Context;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 运行日志：开关打开时，把关键事件追加到 /storage/emulated/0/Android/NBB/hotwinter.log。
 * 只写本地、不联网；文件超过 512KB 自动滚动成 .old，避免越写越大。
 */
public final class Logbook {

    private static final long MAX_BYTES = 512L * 1024L;
    private static final SimpleDateFormat TIME =
            new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.CHINA);

    private static volatile boolean enabled;
    private static File file;

    private Logbook() {
    }

    public static synchronized void init(Context context) {
        file = new File(LocalArtwork.dir(), "hotwinter.log");
        enabled = SettingsStore.get(context).logEnabled();
    }

    public static synchronized void setEnabled(boolean value) {
        enabled = value;
        if (value) {
            log("log", "日志已开启");
        }
    }

    public static boolean enabled() {
        return enabled;
    }

    public static String path() {
        return file == null ? LocalArtwork.dir() + "hotwinter.log" : file.getAbsolutePath();
    }

    public static synchronized void log(String tag, String message) {
        if (!enabled || file == null || message == null) return;
        try {
            File dir = file.getParentFile();
            if (dir != null && !dir.exists()) {
                // 没有目录说明还没拿到存储权限，直接放弃这一行
                return;
            }
            rotateIfNeeded();
            try (FileWriter writer = new FileWriter(file, true)) {
                writer.write(TIME.format(new Date()));
                writer.write(" [");
                writer.write(tag);
                writer.write("] ");
                writer.write(message);
                writer.write('\n');
            }
        } catch (Throwable ignored) {
            // 日志失败绝不能影响发热主流程
        }
    }

    private static void rotateIfNeeded() {
        if (file.length() <= MAX_BYTES) return;
        File old = new File(file.getParentFile(), "hotwinter.old.log");
        if (old.exists() && !old.delete()) return;
        if (!file.renameTo(old)) {
            // 重命名失败就清空重来，至少不会无限增长
            try {
                new FileWriter(file, false).close();
            } catch (Throwable ignored) {
                // 清空失败不影响后续写入
            }
        }
    }
}
