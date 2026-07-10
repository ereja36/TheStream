package com.shif.shqip.android.utils;

import android.text.TextUtils;

import com.shif.shqip.android.databases.prefs.SharedPref;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PlaylistGuardSession {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final long MIN_REFRESH_GAP_MS = 60_000L;
    private static volatile long lastRefreshAtMs;

    private PlaylistGuardSession() {
    }

    public static void refreshAsync(SharedPref sharedPref) {
        if (sharedPref == null || TextUtils.isEmpty(sharedPref.getBaseUrl())) {
            return;
        }
        EXECUTOR.execute(() -> {
            DeviceApiHelper.refreshPlaylistGuardRules(sharedPref);
            lastRefreshAtMs = System.currentTimeMillis();
        });
    }

    public static void refreshIfStale(SharedPref sharedPref) {
        if (sharedPref == null || TextUtils.isEmpty(sharedPref.getBaseUrl())) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastRefreshAtMs < MIN_REFRESH_GAP_MS) {
            return;
        }
        refreshAsync(sharedPref);
    }

    public static void refreshBlocking(SharedPref sharedPref) {
        refreshIfStale(sharedPref);
    }

    public static void refreshBlocking(SharedPref sharedPref, long timeoutMs) {
        refreshIfStale(sharedPref);
    }
}
