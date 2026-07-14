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
import android.widget.LinearLayout;

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
 * Sticky banner under floating nav / above reserved bottom slot.
 * Stays GONE until fill so empty black bars never appear.
 * One helper per Activity — multiple AdsManager instances must not fight over the same strip.
 * Never tear down a filled banner unless {@link #forceReload()} is explicitly requested.
 *
 * Layout contract (ONE IPTV-style):
 * - Prefer {@code #banner_strip} containing {@code #admob_banner_host} + optional Solodroid {@code #bannerAdView}.
 * - List screens that use RelativeLayout should set content {@code layout_above="@id/banner_strip"}
 *   so the list never scrolls under the sticky banner.
 * - Overlay chrome screens (V2/V3) keep bottom padding via {@link #applyContentClearance()}.
 */
public final class DirectAdMobBannerHelper {

    private static final String TAG = "DirectAdMobBanner";
    private static final long RETRY_DELAY_MS = 4_000L;
    private static final long LONG_RETRY_MS = 60_000L;
    private static final long IN_PLACE_REFRESH_MS = 45_000L;
    private static final int BURST_RETRIES = 5;
    private static final int OVERLAY_BASE_PAD_DP = 96;
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
            Log.w(TAG, "invalid banner unit id");
            hideStrip();
            return;
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
        }
        // Search keeps an outer gate container that must open with the strip.
        View searchGate = activity.findViewById(R.id.lyt_banner_ad);
        if (searchGate != null) {
            searchGate.setVisibility(View.VISIBLE);
        }
        View chrome = activity.findViewById(R.id.bottom_chrome);
        if (chrome != null) {
            chrome.setVisibility(View.VISIBLE);
        }
        View bottomContainer = activity.findViewById(R.id.bottom_container);
        if (bottomContainer != null) {
            bottomContainer.setVisibility(View.VISIBLE);
        }
        applyContentClearance(true);
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
        applyContentClearance(false);
    }

    /**
     * For overlay chrome (V2/V3 NestedScrollView), keep content clear of nav + banner.
     * RelativeLayout screens with layout_above=@id/banner_strip do not need this.
     */
    private void applyContentClearance(boolean bannerVisible) {
        View scroll = activity.findViewById(R.id.scroll_home);
        if (scroll == null) {
            return;
        }
        float density = activity.getResources().getDisplayMetrics().density;
        int basePad = Math.round(OVERLAY_BASE_PAD_DP * density);
        int bannerPad = 0;
        if (bannerVisible) {
            View strip = activity.findViewById(R.id.banner_strip);
            int measured = strip != null ? strip.getHeight() : 0;
            if (measured <= 0) {
                // Adaptive banner is typically ~50–90dp before first measure.
                measured = Math.round(56 * density);
            }
            bannerPad = measured + Math.round(8 * density);
        }
        int bottomInset = systemBottomInset(activity);
        int target = basePad + bannerPad + bottomInset;
        if (scroll.getPaddingBottom() != target) {
            scroll.setPadding(
                    scroll.getPaddingLeft(),
                    scroll.getPaddingTop(),
                    scroll.getPaddingRight(),
                    target);
        }
        if (bannerVisible) {
            View strip = activity.findViewById(R.id.banner_strip);
            if (strip != null) {
                strip.post(() -> {
                    if (destroyed || activity.isFinishing() || !shown) {
                        return;
                    }
                    int h = strip.getHeight();
                    if (h > 0) {
                        int refined = basePad + h + Math.round(8 * density) + systemBottomInset(activity);
                        if (scroll.getPaddingBottom() != refined) {
                            scroll.setPadding(
                                    scroll.getPaddingLeft(),
                                    scroll.getPaddingTop(),
                                    scroll.getPaddingRight(),
                                    refined);
                        }
                    }
                });
            }
        }
    }

    @Nullable
    private FrameLayout ensureHost() {
        FrameLayout host = host();
        if (host != null) {
            return host;
        }

        // Preferred: create host inside banner_strip without breaking layout_above rules.
        View strip = activity.findViewById(R.id.banner_strip);
        if (strip instanceof ViewGroup) {
            FrameLayout created = new FrameLayout(activity);
            created.setId(R.id.admob_banner_host);
            ((ViewGroup) strip).addView(created, 0, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return created;
        }

        // Search outer gate.
        View searchGate = activity.findViewById(R.id.lyt_banner_ad);
        if (searchGate instanceof ViewGroup) {
            FrameLayout created = new FrameLayout(activity);
            created.setId(R.id.admob_banner_host);
            ((ViewGroup) searchGate).addView(created, 0, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return created;
        }

        // Legacy fallback: wrap/replace bannerAdView IN PLACE so RelativeLayout
        // layout_above=@id/bannerAdView still reserves space (ONE IPTV behavior).
        View legacy = activity.findViewById(R.id.bannerAdView);
        if (legacy != null && legacy.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) legacy.getParent();
            int index = parent.indexOfChild(legacy);
            ViewGroup.LayoutParams lp = legacy.getLayoutParams();
            parent.removeView(legacy);

            FrameLayout created = new FrameLayout(activity);
            created.setId(R.id.admob_banner_host);
            // Keep Solodroid view available but hidden for non-AdMob fallbacks.
            legacy.setVisibility(View.GONE);
            created.addView(legacy, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            parent.addView(created, index, lp);
            return created;
        }

        return null;
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
