package io.github.wumisc.hotwinter.data;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Environment;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * 从固定目录 /storage/emulated/0/Android/NBB/ 读取用户自备的图片（logo、捐赠码）。
 * 只读、不写、不上传；读不到就安静地返回 null。
 */
public final class LocalArtwork {

    public static final String[] LOGO_NAMES = {"logo.png", "logo.jpg", "logo.jpeg", "logo.webp", "logo.PNG"};
    public static final String[] DONATE_NAMES = {"donate.png", "donate.jpg", "donate.jpeg", "donate.webp", "donate.PNG"};

    private static final Map<String, Bitmap> CACHE = new HashMap<>();

    private LocalArtwork() {
    }

    public static String dir() {
        return Environment.getExternalStorageDirectory().getAbsolutePath() + "/Android/NBB/";
    }

    public static File file(String name) {
        return new File(dir(), name);
    }

    public static boolean hasAccess(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static Bitmap loadFirst(int maxPx, String... names) {
        for (String name : names) {
            Bitmap bitmap = load(name, maxPx);
            if (bitmap != null) return bitmap;
        }
        return null;
    }

    public static synchronized Bitmap load(String name, int maxPx) {
        String key = name + "@" + maxPx;
        if (CACHE.containsKey(key)) {
            Bitmap cached = CACHE.get(key);
            return cached != null && !cached.isRecycled() ? cached : null;
        }
        Bitmap bitmap = decode(file(name), maxPx);
        CACHE.put(key, bitmap);
        return bitmap;
    }

    public static synchronized void clearCache() {
        for (Bitmap bitmap : CACHE.values()) {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
        CACHE.clear();
    }

    private static Bitmap decode(File file, int maxPx) {
        if (file == null || !file.isFile() || !file.canRead()) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

            int sample = 1;
            int longest = Math.max(bounds.outWidth, bounds.outHeight);
            while (longest / sample > maxPx * 2) {
                sample *= 2;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (Throwable t) {
            return null;
        }
    }
}
