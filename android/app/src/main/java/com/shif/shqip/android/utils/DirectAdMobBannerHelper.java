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

/**
 * OneIPTV-style sticky banner:
 * - sits under the 3-button floating nav
 * - stays GONE (no black strip) until an ad actually loads
 * - waits for MobileAds.initialize before requesting
 */
public final class DirectAdMobBannerHelper {

    private static final String TAG = "DirectAdMobBanner";
    private static final long RETRY_DELAY_MS = 4_000L;
    private static final int MAX_RETRIES = 5;

    private final Activity activity;
    private final AdsPref adsPref;
    private final Handler handler = new Handler(Looper.getMainLooper());
    @Nullable
    private AdView adView;
    private int retries;
    private boolean loading;
    private boolean shown;
    private boolean destroyed;

    public DirectAdMobBannerHelper(Activity activity) {
        this.activity = activity;
        this.adsPref = new AdsPref(activity);
    }

    public void load() {
        if (destroyed || activity.isFinishing() || !adsPref.getAdStatus()) {
            hideStrip();
            return;
        }
        String unitId = adsPref.getAdMobBannerId();
        if (!AdPlacementHelper.isValidAdUnit(unitId)) {
            Log.w(TAG, "invalid banner unit id");
            hideStrip();
            return;
        }
        if (shown && adView != null) {
            // Already displaying — avoid destructive reload flicker on resume.
            return;
        }
        if (loading) {
            return;
        }
        loading = true;
        // Never show empty strip while waiting.
        hideStrip();
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
    }

    public void onResume() {
        destroyed = false;
        if (!shown) {
            load();
        }
    }

    private void attachAndLoad() {
        if (destroyed || activity.isFinishing()) {
            loading = false;
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

            // Keep strip GONE until onAdLoaded — prevents black empty bar.
            hideStrip();

            view.setAdListener(new AdListener() {
                @Override
                public void onAdLoaded() {
                    if (destroyed || activity.isFinishing()) {
                        return;
                    }
                    loading = false;
                    retries = 0;
                    shown = true;
                    showStrip();
                    Log.d(TAG, "banner loaded");
                }

                @Override
                public void onAdFailedToLoad(LoadAdError error) {
                    loading = false;
                    shown = false;
                    Log.w(TAG, "banner failed code=" + error.getCode() + " msg=" + error.getMessage());
                    try {
                        if (adView != null) {
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

    private void scheduleRetry() {
        if (destroyed || activity.isFinishing() || retries >= MAX_RETRIES) {
            return;
        }
        retries++;
        long delay = RETRY_DELAY_MS * retries;
        handler.postDelayed(() -> {
            if (!destroyed && !activity.isFinishing() && !shown) {
                load();
            }
        }, delay);
    }

    private void showStrip() {
        View strip = activity.findViewById(R.id.banner_strip);
        FrameLayout host = host();
        if (host != null) {
            host.setVisibility(View.VISIBLE);
        }
        if (strip != null) {
            strip.setVisibility(View.VISIBLE);
            // Transparent until content exists; avoid dark empty plate.
            strip.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        }
        View chrome = activity.findViewById(R.id.bottom_chrome);
        if (chrome != null) {
            chrome.setVisibility(View.VISIBLE);
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

    /** System bottom inset for padding the chrome when banner is hidden. */
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
