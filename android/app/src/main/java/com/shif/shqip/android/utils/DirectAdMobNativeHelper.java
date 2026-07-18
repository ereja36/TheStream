package com.shif.shqip.android.utils;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdLoader;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.nativead.NativeAd;
import com.shif.shqip.android.databases.prefs.AdsPref;
import com.solodroidx.ads.util.TemplateView;

/**
 * Direct AdMob native loader for home slots. Bypasses Solodroid dual-load races.
 * Retries freely forever; keeps a filled creative until Google serves a new one.
 */
public final class DirectAdMobNativeHelper {

    private static final String TAG = "DirectAdMobNative";
    private static final long RETRY_MS = 8_000L;
    private static final long REFRESH_MS = 55_000L;

    private final Activity activity;
    private final AdsPref adsPref;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LinearLayout host;
    @Nullable
    private NativeAd loadedAd;
    private boolean loading;
    private boolean destroyed;
    private boolean shown;
    private final Runnable retryRunnable = this::load;
    private final Runnable refreshRunnable = this::refreshInPlace;

    public DirectAdMobNativeHelper(Activity activity, LinearLayout host) {
        this.activity = activity;
        this.host = host;
        this.adsPref = new AdsPref(activity);
    }

    public void load() {
        if (destroyed || activity.isFinishing() || host == null) {
            return;
        }
        if (!adsPref.getAdStatus() || !AdPlacementHelper.isValidAdUnit(adsPref.getAdMobNativeId())) {
            hideHost();
            return;
        }
        if (loading) {
            return;
        }
        if (shown && loadedAd != null) {
            // Already filled — schedule periodic in-place refresh only.
            scheduleRefresh();
            return;
        }
        loading = true;
        try {
            MobileAds.initialize(activity, status -> {
                if (destroyed || activity.isFinishing()) {
                    loading = false;
                    return;
                }
                handler.post(this::requestNative);
            });
        } catch (Exception e) {
            loading = false;
            Log.e(TAG, "MobileAds.initialize failed", e);
            scheduleRetry();
        }
    }

    /** Ask Google for a new creative without collapsing the slot. */
    public void refreshInPlace() {
        if (destroyed || activity.isFinishing() || !adsPref.getAdStatus()) {
            return;
        }
        if (!AdPlacementHelper.isValidAdUnit(adsPref.getAdMobNativeId())) {
            return;
        }
        if (loading) {
            return;
        }
        loading = true;
        requestNative();
    }

    public void destroy() {
        destroyed = true;
        handler.removeCallbacksAndMessages(null);
        loading = false;
        shown = false;
        destroyLoadedAd();
        hideHost();
    }

    public boolean isShowing() {
        return shown && loadedAd != null && !destroyed;
    }

    private void requestNative() {
        if (destroyed || activity.isFinishing()) {
            loading = false;
            return;
        }
        final String unitId = adsPref.getAdMobNativeId();
        try {
            AdLoader loader = new AdLoader.Builder(activity, unitId)
                    .forNativeAd(nativeAd -> {
                        if (destroyed || activity.isFinishing()) {
                            nativeAd.destroy();
                            loading = false;
                            return;
                        }
                        bindNative(nativeAd);
                    })
                    .withAdListener(new AdListener() {
                        @Override
                        public void onAdFailedToLoad(LoadAdError error) {
                            loading = false;
                            Log.w(TAG, "native failed code=" + error.getCode() + " msg=" + error.getMessage());
                            if (!shown) {
                                hideHost();
                            }
                            scheduleRetry();
                        }
                    })
                    .build();
            loader.loadAd(new AdRequest.Builder().build());
        } catch (Exception e) {
            loading = false;
            Log.e(TAG, "requestNative failed", e);
            if (!shown) {
                hideHost();
            }
            scheduleRetry();
        }
    }

    private void bindNative(NativeAd nativeAd) {
        try {
            destroyLoadedAd();
            loadedAd = nativeAd;
            host.setVisibility(View.VISIBLE);
            host.removeAllViews();

            View root = LayoutInflater.from(activity).inflate(
                    com.solodroidx.ads.R.layout.view_native_ad_medium, host, false);
            host.addView(root, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            View container = root.findViewById(com.solodroidx.ads.R.id.native_ad_view_container);
            TemplateView template = root.findViewById(com.solodroidx.ads.R.id.admob_native_ad_container);
            if (template == null) {
                Log.e(TAG, "TemplateView missing in layout");
                nativeAd.destroy();
                loadedAd = null;
                loading = false;
                shown = false;
                hideHost();
                scheduleRetry();
                return;
            }

            template.setNativeAd(nativeAd);
            template.setVisibility(View.VISIBLE);
            if (container != null) {
                container.setVisibility(View.VISIBLE);
            }

            shown = true;
            loading = false;
            Log.d(TAG, "native loaded");
            scheduleRefresh();
        } catch (Exception e) {
            loading = false;
            Log.e(TAG, "bindNative failed", e);
            try {
                nativeAd.destroy();
            } catch (Exception ignored) {
            }
            loadedAd = null;
            if (!shown) {
                hideHost();
            }
            scheduleRetry();
        }
    }

    private void destroyLoadedAd() {
        try {
            if (loadedAd != null) {
                loadedAd.destroy();
            }
        } catch (Exception ignored) {
        }
        loadedAd = null;
    }

    private void hideHost() {
        if (host != null) {
            host.removeAllViews();
            host.setVisibility(View.GONE);
        }
    }

    private void scheduleRetry() {
        if (destroyed || activity.isFinishing()) {
            return;
        }
        if (shown) {
            scheduleRefresh();
            return;
        }
        handler.removeCallbacks(retryRunnable);
        handler.postDelayed(retryRunnable, RETRY_MS);
    }

    private void scheduleRefresh() {
        if (destroyed || activity.isFinishing()) {
            return;
        }
        handler.removeCallbacks(refreshRunnable);
        handler.postDelayed(refreshRunnable, REFRESH_MS);
    }
}
