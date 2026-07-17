package com.shif.shqip.android.utils;

import android.text.TextUtils;

import com.shif.shqip.android.databases.prefs.SharedPref;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PlaylistGuardSession {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final long MIN_REFRESH_GAP_MS = 60_000L;
    private static final long DEFAULT_BLOCK_TIMEOUT_MS = 8_000L;
    private static volatile long lastRefreshAtMs;
    private static final AtomicBoolean refreshInFlight = new AtomicBoolean(false);

    private PlaylistGuardSession() {
    }

    public static void refreshAsync(SharedPref sharedPref) {
        if (sharedPref == null || TextUtils.isEmpty(sharedPref.getBaseUrl())) {
            return;
        }
        if (!refreshInFlight.compareAndSet(false, true)) {
            return;
        }
        EXECUTOR.execute(() -> {
            try {
                DeviceApiHelper.refreshPlaylistGuardRules(sharedPref);
                lastRefreshAtMs = System.currentTimeMillis();
            } finally {
                refreshInFlight.set(false);
            }
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
        refreshBlocking(sharedPref, DEFAULT_BLOCK_TIMEOUT_MS);
    }

    public static void refreshBlocking(SharedPref sharedPref, long timeoutMs) {
        if (sharedPref == null || TextUtils.isEmpty(sharedPref.getBaseUrl())) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastRefreshAtMs < MIN_REFRESH_GAP_MS) {
            return;
        }
        final CountDownLatch latch = new CountDownLatch(1);
        if (!refreshInFlight.compareAndSet(false, true)) {
            // Another refresh is running — wait briefly for it.
            try {
                long waitMs = Math.max(500L, Math.min(timeoutMs, 3_000L));
                Thread.sleep(waitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return;
        }
        EXECUTOR.execute(() -> {
            try {
                DeviceApiHelper.refreshPlaylistGuardRules(sharedPref);
                lastRefreshAtMs = System.currentTimeMillis();
            } finally {
                refreshInFlight.set(false);
                latch.countDown();
            }
        });
        try {
            latch.await(Math.max(500L, timeoutMs), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
