package com.shif.shqip.android.utils;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.shif.shqip.android.activities.ActivitySplash;
import com.shif.shqip.android.activities.MyApplication;
import com.shif.shqip.android.databases.prefs.AdsPref;
import com.solodroidx.ads.appopen.AppOpenAd;

/**
 * Shows app-open ads when returning from background, similar to OneIPTV.
 */
public final class AppOpenAdLifecycle implements DefaultLifecycleObserver, Application.ActivityLifecycleCallbacks {

    private static final String TAG = "AppOpenAdLifecycle";
    private static final long MIN_GAP_MS = 300L;

    private final MyApplication application;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Activity currentActivity;
    private boolean registered;
    private boolean wasInBackground;
    private boolean pendingResumeAd;
    private long lastShownAt;

    public AppOpenAdLifecycle(MyApplication application) {
        this.application = application;
    }

    public void register() {
        if (registered) {
            return;
        }
        registered = true;
        application.registerActivityLifecycleCallbacks(this);
        ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        if (wasInBackground) {
            pendingResumeAd = true;
        }
        wasInBackground = false;
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        wasInBackground = true;
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        currentActivity = activity;
        if (!pendingResumeAd) {
            return;
        }
        pendingResumeAd = false;
        mainHandler.post(() -> showResumeAdIfNeeded(activity));
    }

    private void showResumeAdIfNeeded(@NonNull Activity activity) {
        try {
            if (!application.isResumeAppOpenReady()) {
                return;
            }
            AdsPref adsPref = new AdsPref(application);
            if (!adsPref.getAdStatus() || !adsPref.getAppOpenOnResume()) {
                return;
            }
            if (activity.isFinishing() || activity.isDestroyed()
                    || activity instanceof ActivitySplash) {
                return;
            }
            long now = SystemClock.elapsedRealtime();
            if (now - lastShownAt < MIN_GAP_MS) {
                return;
            }
            AdsManager adsManager = new AdsManager(activity);
            adsManager.initializeAd();
            adsManager.updateConsentStatus();
            adsManager.loadAppOpenAd(true, true, () -> {
                lastShownAt = SystemClock.elapsedRealtime();
                AppOpenAd.isAppOpenAdLoaded = false;
            });
        } catch (Exception e) {
            Log.e(TAG, "showResumeAdIfNeeded failed", e);
        }
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        currentActivity = activity;
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        if (currentActivity == activity) {
            currentActivity = null;
        }
    }
}
