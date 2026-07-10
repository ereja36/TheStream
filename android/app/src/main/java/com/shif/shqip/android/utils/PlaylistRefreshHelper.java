package com.shif.shqip.android.utils;

import android.app.Activity;
import android.os.SystemClock;

import com.shif.shqip.android.databases.prefs.SharedPref;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Debounced playlist refresh when the app returns to foreground.
 */
public final class PlaylistRefreshHelper {

    private static final long MIN_REFRESH_GAP_MS = 5_000L;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static long lastRefreshAt;

    private PlaylistRefreshHelper() {
    }

    public static void refreshOnResume(Activity activity, SharedPref sharedPref, Runnable onUpdated) {
        if (activity == null || sharedPref == null) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - lastRefreshAt < MIN_REFRESH_GAP_MS) {
            return;
        }
        lastRefreshAt = now;
        EXECUTOR.execute(() -> {
            try {
                String deviceId = DeviceApiHelper.getLocalDeviceId(activity);
                DeviceApiHelper.PlaylistSyncResult result =
                        DeviceApiHelper.refreshPlaylistChannelsFromApi(sharedPref, deviceId);
                if (result.blocked || activity.isFinishing()) {
                    return;
                }
                if (onUpdated != null && (result.channelsUpdated || sharedPref.hasPlaylistChannels())) {
                    activity.runOnUiThread(() -> {
                        if (!activity.isFinishing()) {
                            onUpdated.run();
                        }
                    });
                }
            } catch (Exception ignored) {
                // Keep cached playlist on refresh errors.
            }
        });
    }
}
