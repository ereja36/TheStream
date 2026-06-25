package com.shif.shqip.android.utils;

import android.text.TextUtils;

import com.shif.shqip.android.databases.prefs.SharedPref;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class PlaylistGuardSession {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final long DEFAULT_TIMEOUT_MS = 10_000L;

    private PlaylistGuardSession() {
    }

    public static void refreshAsync(SharedPref sharedPref) {
        if (sharedPref == null || TextUtils.isEmpty(sharedPref.getBaseUrl())) {
            return;
        }
        EXECUTOR.execute(() -> DeviceApiHelper.refreshPlaylistGuardRules(sharedPref));
    }

    public static void refreshBlocking(SharedPref sharedPref) {
        refreshBlocking(sharedPref, DEFAULT_TIMEOUT_MS);
    }

    public static void refreshBlocking(SharedPref sharedPref, long timeoutMs) {
        if (sharedPref == null || TextUtils.isEmpty(sharedPref.getBaseUrl())) {
            return;
        }
        Future<?> future = EXECUTOR.submit(() -> DeviceApiHelper.refreshPlaylistGuardRules(sharedPref));
        try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
            PlaylistGuardHelper.reconcileActiveGuardRule(sharedPref);
        }
    }
}
