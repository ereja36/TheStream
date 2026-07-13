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

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.shif.shqip.android.R;
import com.shif.shqip.android.databases.prefs.AdsPref;

/**
 * Loads AdMob banners the reliable way OneIPTV does in practice:
 * wait for MobileAds.initialize, then attach an AdView into a known host.
 * Solodroid's findViewById path often races init and leaves an empty black strip.
 */
public final class DirectAdMobBannerHelper {

    private static final String TAG = "DirectAdMobBanner";
    private static final long RETRY_DELAY_MS = 3_500L;
    private static final int MAX_RETRIES = 4;

    private final Activity activity;
    private final AdsPref adsPref;
    private final Handler handler = new Handler(Looper.getMainLooper());
    @Nullable
    private AdView adView;
    private int retries;
    private boolean loading;

    public DirectAdMobBannerHelper(Activity activity) {
        this.activity = activity;
        this.adsPref = new AdsPref(activity);
    }

    public void load() {
        if (activity.isFinishing() || !adsPref.getAdStatus()) {
            return;
        }
        String unitId = adsPref.getAdMobBannerId();
        if (!AdPlacementHelper.isValidAdUnit(unitId)) {
            Log.w(TAG, "invalid banner unit id");
            return;
        }
        if (loading) {
            return;
        }
        loading = true;
        // Critical: do not request until MobileAds init completes (interstitial often
        // loads later on click; banner used to fire too early and fail forever).
        MobileAds.initialize(activity, status -> handler.post(this::attachAndLoad));
    }

    public void destroy() {
        handler.removeCallbacksAndMessages(null);
        loading = false;
        if (adView != null) {
            adView.destroy();
            adView = null;
        }
        FrameLayout host = host();
        if (host != null) {
            host.removeAllViews();
            host.setVisibility(View.GONE);
        }
        View strip = activity.findViewById(R.id.banner_strip);
        if (strip != null) {
            strip.setVisibility(View.GONE);
        }
        updateNavMargin(0);
    }

    private void attachAndLoad() {
        if (activity.isFinishing()) {
            loading = false;
            return;
        }
        FrameLayout host = ensureHost();
        if (host == null) {
            loading = false;
            Log.e(TAG, "banner host missing");
            return;
        }

        String unitId = adsPref.getAdMobBannerId();
        if (adView != null) {
            adView.destroy();
            adView = null;
        }
        host.removeAllViews();

        AdView view = new AdView(activity);
        adView = view;
        view.setAdUnitId(unitId);
        view.setAdSize(adaptiveSize());
        host.addView(view, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        View strip = activity.findViewById(R.id.banner_strip);
        if (strip != null) {
            strip.setVisibility(View.VISIBLE);
        }
        host.setVisibility(View.VISIBLE);

        view.setAdListener(new AdListener() {
            @Override
            public void onAdLoaded() {
                loading = false;
                retries = 0;
                host.setVisibility(View.VISIBLE);
                if (strip != null) {
                    strip.setVisibility(View.VISIBLE);
                }
                host.post(() -> updateNavMargin(Math.max(host.getHeight(), dp(50))));
                Log.d(TAG, "banner loaded");
            }

            @Override
            public void onAdFailedToLoad(LoadAdError error) {
                loading = false;
                Log.w(TAG, "banner failed code=" + error.getCode() + " " + error.getMessage());
                host.setVisibility(View.GONE);
                if (strip != null) {
                    strip.setVisibility(View.GONE);
                }
                updateNavMargin(0);
                if (retries < MAX_RETRIES && !activity.isFinishing()) {
                    retries++;
                    handler.postDelayed(() -> {
                        if (!activity.isFinishing()) {
                            load();
                        }
                    }, RETRY_DELAY_MS * retries);
                }
            }
        });

        view.loadAd(new AdRequest.Builder().build());
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
            if (legacy != null && legacy.getParent() instanceof ViewGroup) {
                parent = (ViewGroup) legacy.getParent();
            }
        }
        if (parent == null) {
            return null;
        }
        FrameLayout created = new FrameLayout(activity);
        created.setId(R.id.admob_banner_host);
        parent.addView(created, 0, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
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
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth);
    }

    private void updateNavMargin(int bannerHeightPx) {
        View pill = activity.findViewById(R.id.bottom_nav_pill);
        if (pill == null) {
            return;
        }
        ViewGroup.LayoutParams raw = pill.getLayoutParams();
        if (!(raw instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) raw;
        int systemBottom = 0;
        View root = activity.findViewById(R.id.design_root);
        if (root != null) {
            androidx.core.view.WindowInsetsCompat insets =
                    androidx.core.view.ViewCompat.getRootWindowInsets(root);
            if (insets != null) {
                systemBottom = insets.getInsets(
                        androidx.core.view.WindowInsetsCompat.Type.systemBars()).bottom;
            }
        }
        // Pill floats above banner (OneIPTV style). If no banner, sit just above gesture line.
        int gap = dp(8);
        if (bannerHeightPx > 0) {
            lp.bottomMargin = bannerHeightPx + gap;
        } else {
            lp.bottomMargin = systemBottom + gap;
        }
        pill.setLayoutParams(lp);
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
