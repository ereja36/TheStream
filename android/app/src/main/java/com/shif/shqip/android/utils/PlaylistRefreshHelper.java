package com.shif.shqip.android.utils;

import android.app.Activity;
import android.os.SystemClock;
import android.text.TextUtils;

import com.shif.shqip.android.databases.prefs.SharedPref;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Debounced playlist refresh when the app returns to foreground.
 * Forces an immediate reload when playlist-portal source/content_version changes.
 */
public final class PlaylistRefreshHelper {

    private static final long MIN_REFRESH_GAP_MS = 5_000L;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static long lastRefreshAt;
    private static final AtomicBoolean FORCE_NEXT = new AtomicBoolean(false);

    private PlaylistRefreshHelper() {
    }

    public static void forceNextRefresh() {
        FORCE_NEXT.set(true);
    }

    public static void refreshOnResume(Activity activity, SharedPref sharedPref, Runnable onUpdated) {
        if (activity == null || sharedPref == null) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        boolean force = FORCE_NEXT.getAndSet(false);
        if (!force && now - lastRefreshAt < MIN_REFRESH_GAP_MS) {
            return;
        }
        lastRefreshAt = now;
        final String versionBefore = sharedPref.getPortalContentVersion();
        EXECUTOR.execute(() -> {
            try {
                String deviceId = DeviceApiHelper.getLocalDeviceId(activity);
                DeviceApiHelper.refreshPlaylistPortalConfig(sharedPref);
                String versionAfter = sharedPref.getPortalContentVersion();
                boolean portalChanged = !TextUtils.isEmpty(versionAfter)
                        && !versionAfter.equals(versionBefore);
                if (portalChanged) {
                    sharedPref.clearPlaylistChannels();
                }
                DeviceApiHelper.PlaylistSyncResult result =
                        DeviceApiHelper.refreshPlaylistChannelsFromApi(sharedPref, deviceId);
                if (result.blocked || activity.isFinishing()) {
                    return;
                }
                boolean shouldReload = portalChanged || result.channelsUpdated || force;
                if (onUpdated != null && shouldReload) {
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
