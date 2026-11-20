package io.github.wumisc.hotwinter;

import android.app.Application;
import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 应用入口：装一个兜底崩溃记录，闪退后至少能知道崩在哪一行。 */
public final class HeatApp extends Application {

    private static final String CRASH_NAME = "crash.log";
    private static final long MAX_BYTES = 256 * 1024;

    @Override
    public void onCreate() {
        super.onCreate();
        installCrashHandler();
    }

    private void installCrashHandler() {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            saveCrash(error);
            if (previous != null) {
                previous.uncaughtException(thread, error);
            } else {
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
        });
    }

    private void saveCrash(Throwable error) {
        if (error == null) return;
        StringBuilder builder = new StringBuilder();
        builder.append("==== ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                .format(new Date())).append(" ====\n");
        builder.append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" · Android ").append(Build.VERSION.RELEASE)
                .append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
        builder.append(error.toString()).append('\n');
        for (StackTraceElement element : error.getStackTrace()) {
            builder.append("    at ").append(element).append('\n');
        }
        Throwable cause = error.getCause();
        while (cause != null) {
            builder.append("Caused by: ").append(cause).append('\n');
            for (StackTraceElement element : cause.getStackTrace()) {
                builder.append("    at ").append(element).append('\n');
            }
            cause = cause.getCause();
        }
        builder.append('\n');

        append(new File("/storage/emulated/0/Android/NBB", CRASH_NAME), builder.toString());
        File external = getExternalFilesDir("logs");
        if (external != null) append(new File(external, CRASH_NAME), builder.toString());
        append(new File(getFilesDir(), CRASH_NAME), builder.toString());
    }

    private void append(File file, String text) {
        try {
            File parent = file.getParentFile();
            if (parent == null) return;
            if (!parent.exists() && !parent.mkdirs()) return;
            if (file.length() > MAX_BYTES) {
                File old = new File(parent, file.getName() + ".old");
                if (old.exists()) //noinspection ResultOfMethodCallIgnored
                    old.delete();
                //noinspection ResultOfMethodCallIgnored
                file.renameTo(old);
            }
            try (FileWriter writer = new FileWriter(file, true)) {
                writer.write(text);
            }
        } catch (IOException | RuntimeException ignored) {
            // 崩溃记录本身不能再抛异常
        }
    }

    /** 最近一次崩溃内容，读不到返回 null。 */
    public static String lastCrash(Context context) {
        File[] candidates = {
                new File("/storage/emulated/0/Android/NBB", CRASH_NAME),
                new File(context.getFilesDir(), CRASH_NAME)
        };
        for (File file : candidates) {
            if (file.isFile() && file.length() > 0) {
                try {
                    return new String(java.nio.file.Files.readAllBytes(file.toPath()),
                            java.nio.charset.StandardCharsets.UTF_8);
                } catch (IOException | RuntimeException ignored) {
                    // 换下一个位置
                }
            }
        }
        return null;
    }
}
