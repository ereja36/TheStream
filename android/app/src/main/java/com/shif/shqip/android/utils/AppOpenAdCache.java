package com.shif.shqip.android.utils;

import android.os.SystemClock;
import android.util.Log;

import com.solodroidx.ads.appopen.AppOpenAd;

/**
 * AdMob App Open ads expire after ~4 hours. Keep a client-side TTL so we never
 * treat a stale Solodroid "loaded" flag as valid — users must not clear data.
 */
public final class AppOpenAdCache {

    private static final String TAG = "AppOpenAdCache";
    /** Refresh before Google's ~4h expiry window. */
    private static final long MAX_AGE_MS = 3L * 60L * 60L * 1000L;

    private static long loadedAtElapsed;

    private AppOpenAdCache() {
    }

    public static void markLoaded() {
        loadedAtElapsed = SystemClock.elapsedRealtime();
    }

    public static boolean isFresh() {
        if (loadedAtElapsed <= 0L) {
            return false;
        }
        return SystemClock.elapsedRealtime() - loadedAtElapsed < MAX_AGE_MS;
    }

    public static long ageMs() {
        if (loadedAtElapsed <= 0L) {
            return Long.MAX_VALUE;
        }
        return SystemClock.elapsedRealtime() - loadedAtElapsed;
    }

    /** Drop stale cache so the next loadAppOpenAd() requests a fresh fill. */
    public static void invalidateIfStale() {
        if (!isFresh()) {
            invalidate();
        }
    }

    public static void invalidate() {
        loadedAtElapsed = 0L;
        try {
            AppOpenAd.isAppOpenAdLoaded = false;
        } catch (Throwable t) {
            Log.w(TAG, "clear isAppOpenAdLoaded failed", t);
        }
    }
}
