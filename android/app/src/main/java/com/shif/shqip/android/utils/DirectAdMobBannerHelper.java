package com.shif.shqip.android.utils;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.shif.shqip.android.R;
import com.shif.shqip.android.databases.prefs.AdsPref;

import java.util.WeakHashMap;

/**
 * Sticky banner under floating nav. Stays GONE until fill.
 * One helper per Activity — multiple AdsManager instances must not fight over the same strip.
 * Never tear down a filled banner unless {@link #forceReload()} is explicitly requested.
 */
public final class DirectAdMobBannerHelper {

    private static final String TAG = "DirectAdMobBanner";
    private static final long RETRY_DELAY_MS = 4_000L;
    private static final long LONG_RETRY_MS = 60_000L;
    private static final long IN_PLACE_REFRESH_MS = 45_000L;
    private static final int BURST_RETRIES = 5;
    private static final WeakHashMap<Activity, DirectAdMobBannerHelper> BY_ACTIVITY = new WeakHashMap<>();

    private final Activity activity;
    private final AdsPref adsPref;
    private final Handler handler = new Handler(Looper.getMainLooper());
    @Nullable
    private AdView adView;
    private int retries;
    private boolean loading;
    private boolean shown;
    private boolean destroyed;
    private boolean refreshingInPlace;
    private final Runnable longRetryRunnable;
    private final Runnable inPlaceRefreshRunnable;

    public static DirectAdMobBannerHelper forActivity(Activity activity) {
        if (activity == null) {
            throw new IllegalArgumentException("activity");
        }
        synchronized (BY_ACTIVITY) {
            DirectAdMobBannerHelper existing = BY_ACTIVITY.get(activity);
            if (existing != null && !existing.destroyed) {
                return existing;
            }
            DirectAdMobBannerHelper created = new DirectAdMobBannerHelper(activity);
            BY_ACTIVITY.put(activity, created);
            return created;
        }
    }

    private DirectAdMobBannerHelper(Activity activity) {
        this.activity = activity;
        this.adsPref = new AdsPref(activity);
        this.longRetryRunnable = () -> {
            if (!destroyed && !this.activity.isFinishing() && !shown && this.adsPref.getAdStatus()) {
                retries = 0;
                load();
            }
        };
        this.inPlaceRefreshRunnable = this::refreshInPlace;
    }

    /** Soft ensure: keep a filled banner; only request if empty. */
    public void ensureLoaded() {
        destroyed = false;
        if (shown && adView != null) {
            showStrip();
            scheduleInPlaceRefresh();
            return;
        }
        load();
    }

    /**
     * Ask Google for a new creative on the existing AdView.
     * Does not hide the strip — failed refresh keeps the previous ad.
     */
    public void refreshInPlace() {
        if (destroyed || activity.isFinishing() || !adsPref.getAdStatus()) {
            return;
        }
        if (adView == null || !shown) {
            load();
            return;
        }
        if (loading) {
            scheduleInPlaceRefresh();
            return;
        }
        try {
            refreshingInPlace = true;
            loading = true;
            adView.loadAd(new AdRequest.Builder().build());
            Log.d(TAG, "in-place refresh requested");
        } catch (Exception e) {
            refreshingInPlace = false;
            loading = false;
            Log.e(TAG, "refreshInPlace failed", e);
            scheduleInPlaceRefresh();
        }
    }

    public void load() {
        if (destroyed || activity.isFinishing() || !adsPref.getAdStatus()) {
            if (!shown) {
                hideStrip();
            }
            return;
        }
        String unitId = adsPref.getAdMobBannerId();
        if (!AdPlacementHelper.isValidAdUnit(unitId)) {
            Log.w(TAG, "invalid banner unit id adStatus=" + adsPref.getAdStatus()
                    + " deviceNoAds=" + adsPref.getDeviceNoAds()
                    + " testMode=" + adsPref.getTestMode());
            hideStrip();
            return;
        }
        if (unitId.contains("3940256099942544")) {
            Log.w(TAG, "loading Google SAMPLE banner unit — fills on all devices as Test Ad");
        } else {
            Log.d(TAG, "loading production banner unit …" + unitId.substring(Math.max(0, unitId.lastIndexOf('/')))
                    + " adStatus=" + adsPref.getAdStatus()
                    + " deviceNoAds=" + adsPref.getDeviceNoAds());
        }
        if (shown && adView != null) {
            showStrip();
            return;
        }
        if (loading) {
            return;
        }
        loading = true;
        // Only collapse the strip while waiting for the first fill — never blink a live ad.
        if (!shown) {
            hideStrip();
        }
        try {
            MobileAds.initialize(activity, status -> {
                if (destroyed || activity.isFinishing()) {
                    loading = false;
                    return;
                }
                handler.post(this::attachAndLoad);
            });
        } catch (Exception e) {
            loading = false;
            Log.e(TAG, "MobileAds.initialize failed", e);
            scheduleRetry();
        }
    }

    public void destroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        loading = false;
        shown = false;
        try {
            if (adView != null) {
                adView.destroy();
                adView = null;
            }
        } catch (Exception ignored) {
        }
        hideStrip();
        synchronized (BY_ACTIVITY) {
            DirectAdMobBannerHelper mapped = BY_ACTIVITY.get(activity);
            if (mapped == this) {
                BY_ACTIVITY.remove(activity);
            }
        }
    }

    public void onResume() {
        destroyed = false;
        try {
            if (adView != null) {
                adView.resume();
            }
        } catch (Exception ignored) {
        }
        ensureLoaded();
    }

    public void onPause() {
        try {
            if (adView != null) {
                adView.pause();
            }
        } catch (Exception ignored) {
        }
    }

    /** Force a fresh request even if something was previously showing. */
    public void forceReload() {
        destroyed = false;
        shown = false;
        retries = 0;
        loading = false;
        handler.removeCallbacks(longRetryRunnable);
        try {
            if (adView != null) {
                adView.destroy();
                adView = null;
            }
        } catch (Exception ignored) {
        }
        load();
    }

    public boolean isShowing() {
        return shown && adView != null && !destroyed;
    }

    private void attachAndLoad() {
        if (destroyed || activity.isFinishing()) {
            loading = false;
            return;
        }
        if (shown && adView != null) {
            loading = false;
            showStrip();
            return;
        }
        FrameLayout host = ensureHost();
        if (host == null) {
            loading = false;
            Log.e(TAG, "banner host missing");
            scheduleRetry();
            return;
        }

        final String unitId = adsPref.getAdMobBannerId();
        try {
            if (adView != null) {
                adView.destroy();
                adView = null;
            }
            host.removeAllViews();

            final AdView view = new AdView(activity);
            adView = view;
            view.setAdUnitId(unitId);
            view.setAdSize(adaptiveSize());
            host.addView(view, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            if (!shown) {
                hideStrip();
            }

            view.setAdListener(new AdListener() {
                @Override
                public void onAdLoaded() {
                    if (destroyed || activity.isFinishing()) {
                        return;
                    }
                    loading = false;
                    refreshingInPlace = false;
                    retries = 0;
                    shown = true;
                    handler.removeCallbacks(longRetryRunnable);
                    showStrip();
                    scheduleInPlaceRefresh();
                    Log.d(TAG, "banner loaded");
                }

                @Override
                public void onAdFailedToLoad(LoadAdError error) {
                    loading = false;
                    Log.w(TAG, "banner failed code=" + error.getCode()
                            + " msg=" + error.getMessage()
                            + " inPlace=" + refreshingInPlace);
                    // In-place refresh failed — keep the previous creative visible.
                    if (refreshingInPlace && shown && adView != null) {
                        refreshingInPlace = false;
                        showStrip();
                        scheduleInPlaceRefresh();
                        return;
                    }
                    refreshingInPlace = false;
                    if (shown && adView != null && adView != view) {
                        showStrip();
                        scheduleInPlaceRefresh();
                        return;
                    }
                    shown = false;
                    try {
                        if (adView == view) {
                            adView.destroy();
                            adView = null;
                        }
                        host.removeAllViews();
                    } catch (Exception ignored) {
                    }
                    hideStrip();
                    scheduleRetry();
                }
            });

            view.loadAd(new AdRequest.Builder().build());
        } catch (Exception e) {
            loading = false;
            shown = false;
            Log.e(TAG, "attachAndLoad failed", e);
            hideStrip();
            scheduleRetry();
        }
    }

    private void scheduleInPlaceRefresh() {
        if (destroyed || activity.isFinishing()) {
            return;
        }
        handler.removeCallbacks(inPlaceRefreshRunnable);
        handler.postDelayed(inPlaceRefreshRunnable, IN_PLACE_REFRESH_MS);
    }

    private void scheduleRetry() {
        if (destroyed || activity.isFinishing() || shown) {
            return;
        }
        if (retries < BURST_RETRIES) {
            retries++;
            long delay = RETRY_DELAY_MS * retries;
            handler.postDelayed(() -> {
                if (!destroyed && !activity.isFinishing() && !shown) {
                    load();
                }
            }, delay);
            return;
        }
        handler.removeCallbacks(longRetryRunnable);
        handler.postDelayed(longRetryRunnable, LONG_RETRY_MS);
    }

    private void showStrip() {
        View strip = activity.findViewById(R.id.banner_strip);
        FrameLayout host = host();
        if (host != null) {
            host.setVisibility(View.VISIBLE);
        }
        if (strip != null) {
            strip.setVisibility(View.VISIBLE);
            strip.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            strip.bringToFront();
        }
        View chrome = activity.findViewById(R.id.bottom_chrome);
        if (chrome != null) {
            chrome.setVisibility(View.VISIBLE);
            chrome.bringToFront();
            chrome.setElevation(24f * activity.getResources().getDisplayMetrics().density);
        }
        View nav = activity.findViewById(R.id.bottom_nav_pill);
        if (nav != null) {
            nav.bringToFront();
            nav.setElevation(28f * activity.getResources().getDisplayMetrics().density);
        }
    }

    private void hideStrip() {
        FrameLayout host = host();
        if (host != null) {
            host.setVisibility(View.GONE);
        }
        View strip = activity.findViewById(R.id.banner_strip);
        if (strip != null) {
            strip.setVisibility(View.GONE);
            ViewGroup.LayoutParams lp = strip.getLayoutParams();
            if (lp != null) {
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                strip.setLayoutParams(lp);
            }
        }
    }

    @Nullable
    private FrameLayout ensureHost() {
        FrameLayout host = host();
        if (host != null) {
            return host;
        }
        View strip = activity.findViewById(R.id.banner_strip);
        ViewGroup parent = null;
        if (strip instanceof ViewGroup) {
            parent = (ViewGroup) strip;
        } else {
            View legacy = activity.findViewById(R.id.bannerAdView);
            if (legacy != null) {
                legacy.setVisibility(View.GONE);
                if (legacy.getParent() instanceof ViewGroup) {
                    parent = (ViewGroup) legacy.getParent();
                }
            }
        }
        if (parent == null) {
            return null;
        }
        FrameLayout created = new FrameLayout(activity);
        created.setId(R.id.admob_banner_host);
        if (parent instanceof android.widget.RelativeLayout) {
            android.widget.RelativeLayout.LayoutParams lp = new android.widget.RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.addRule(android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM);
            parent.addView(created, lp);
        } else {
            parent.addView(created, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return created;
    }

    @Nullable
    private FrameLayout host() {
        View v = activity.findViewById(R.id.admob_banner_host);
        return v instanceof FrameLayout ? (FrameLayout) v : null;
    }

    private AdSize adaptiveSize() {
        Display display = activity.getWindowManager().getDefaultDisplay();
        DisplayMetrics metrics = new DisplayMetrics();
        display.getMetrics(metrics);
        int adWidth = Math.max(320, (int) (metrics.widthPixels / metrics.density));
        try {
            return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth);
        } catch (Exception e) {
            return AdSize.BANNER;
        }
    }

    public static int systemBottomInset(Activity activity) {
        View root = activity.findViewById(R.id.design_root);
        if (root == null) {
            root = activity.getWindow().getDecorView();
        }
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(root);
        if (insets == null) {
            return 0;
        }
        Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
        return bars.bottom;
    }
}
