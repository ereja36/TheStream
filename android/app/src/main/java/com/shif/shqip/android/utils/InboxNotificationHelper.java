package com.shif.shqip.android.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import androidx.core.app.NotificationCompat;

import com.shif.shqip.android.R;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.utils.DesignRouter;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class InboxNotificationHelper {

    private static final String CHANNEL_ID = "shif_inbox";
    public static final String EXTRA_INBOX_NOTIFICATION_ID = "inbox_notification_id";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final long POLL_INTERVAL_MS = 2L * 60L * 1000L;
    private static long lastPollMs = 0L;

    private InboxNotificationHelper() {
    }

    public static void handleOpenIntent(Context context, SharedPref sharedPref, Intent intent) {
        if (context == null || sharedPref == null || intent == null) {
            return;
        }
        long notificationId = intent.getLongExtra(EXTRA_INBOX_NOTIFICATION_ID, 0L);
        if (notificationId <= 0L) {
            return;
        }
        intent.removeExtra(EXTRA_INBOX_NOTIFICATION_ID);
        String deviceId = DeviceApiHelper.getLocalDeviceId(context);
        EXECUTOR.execute(() -> DeviceApiHelper.ackInboxNotification(sharedPref, deviceId, notificationId, true));
    }

    public static void pollAndShow(Context context, SharedPref sharedPref) {
        if (context == null || sharedPref == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastPollMs < POLL_INTERVAL_MS) {
            return;
        }
        lastPollMs = now;
        String deviceId = DeviceApiHelper.getLocalDeviceId(context);
        EXECUTOR.execute(() -> {
            List<DeviceApiHelper.InboxNotification> notifications =
                    DeviceApiHelper.fetchInboxNotifications(sharedPref, deviceId);
            for (DeviceApiHelper.InboxNotification notification : notifications) {
                showNotification(context.getApplicationContext(), sharedPref, deviceId, notification);
            }
        });
    }

    private static void showNotification(Context context, SharedPref sharedPref, String deviceId,
                                         DeviceApiHelper.InboxNotification notification) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_HIGH
            );
            manager.createNotificationChannel(channel);
        }

        Intent intent;
        if (!TextUtils.isEmpty(notification.targetUrl)) {
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse(notification.targetUrl));
        } else {
            intent = DesignRouter.getMainActivityIntent(context);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(EXTRA_INBOX_NOTIFICATION_ID, notification.id);
        int requestCode = (int) (notification.id % Integer.MAX_VALUE);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_onesignal_default)
                .setContentTitle(TextUtils.isEmpty(notification.title) ? context.getString(R.string.app_name) : notification.title)
                .setContentText(notification.message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(notification.message))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        manager.notify(requestCode, builder.build());
        DeviceApiHelper.ackInboxNotification(sharedPref, deviceId, notification.id, false);
    }
}
