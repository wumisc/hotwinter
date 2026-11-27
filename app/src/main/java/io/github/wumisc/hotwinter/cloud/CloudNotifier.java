package io.github.wumisc.hotwinter.cloud;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import io.github.wumisc.hotwinter.R;
import io.github.wumisc.hotwinter.ui.MainActivity;

/** 云端轻提醒的本地通知；不带任何远程控制能力。 */
public final class CloudNotifier {

    private static final String CHANNEL = "cloud_nudge";

    private CloudNotifier() {
    }

    public static void remind(Context context, String title, String body, int id) {
        Context app = context.getApplicationContext();
        NotificationManager manager = (NotificationManager)
                app.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL, "云端提醒", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("伴侣从云端发来的轻提醒");
            channel.enableVibration(true);
            manager.createNotificationChannel(channel);
        }
        PendingIntent open = PendingIntent.getActivity(app, 50 + id,
                new Intent(app, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new NotificationCompat.Builder(app, CHANNEL)
                .setSmallIcon(R.drawable.ic_tile_heat)
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build();
        try {
            manager.notify(id, notification);
        } catch (SecurityException ignored) {
            // 通知权限被关闭时不影响连接
        }
    }
}
