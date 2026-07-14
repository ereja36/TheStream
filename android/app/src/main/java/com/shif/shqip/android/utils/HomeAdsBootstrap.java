package com.shif.shqip.android.utils;

import android.app.Activity;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.shif.shqip.android.R;
import com.shif.shqip.android.databases.prefs.AdsPref;

/**
 * Home-screen ad bootstrap. Keeps reloading forever so fills recover without clear-data.
 */
public final class HomeAdsBootstrap {

    private static final String TAG = "HomeAdsBootstrap";

    private HomeAdsBootstrap() {
    }

    public static void init(AppCompatActivity activity, AdsManager adsManager, AdsPref adsPref,
                            AdsAntiblockHelper antiblockHelper) {
        initFast(activity, adsManager, adsPref, antiblockHelper);
    }

    public static void initFast(AppCompatActivity activity, AdsManager adsManager, AdsPref adsPref,
                                AdsAntiblockHelper antiblockHelper) {
        if (activity == null || adsManager == null || adsPref == null) {
            return;
        }
        View strip = activity.findViewById(R.id.banner_strip);
        if (strip != null) {
            strip.setVisibility(View.GONE);
        }
        if (!adsPref.getAdStatus() || !AdPlacementHelper.hasValidAdNetwork(adsPref)) {
            return;
        }
        if (antiblockHelper != null) {
            antiblockHelper.resetRetryCount();
        }
        View foundAnchor = activity.findViewById(R.id.bottom_chrome);
        if (foundAnchor == null) {
            foundAnchor = activity.findViewById(R.id.design_root);
        }
        if (foundAnchor == null) {
            foundAnchor = activity.getWindow().getDecorView();
        }
        final View anchor = foundAnchor;
        final View bannerStrip = strip;
        anchor.post(() -> {
            if (activity.isFinishing()) {
                return;
            }
            try {
                adsManager.initializeAd();
                adsManager.updateConsentStatus();
                adsManager.loadBannerAd(Constant.BANNER_HOME);
                adsManager.loadInterstitialAd(Constant.INTERSTITIAL_AD_CHANNEL_LIST,
                        AdPlacementHelper.SOLODROID_SHOW_EVERY_CALL);
                AppOpenAdCache.invalidateIfStale();
                // Delayed retries for init race / temporary no-fill.
                anchor.postDelayed(() -> {
                    if (!activity.isFinishing() && adsPref.getAdStatus()) {
                        adsManager.loadBannerAd(Constant.BANNER_HOME);
                    }
                }, 5_000L);
                anchor.postDelayed(() -> {
                    if (!activity.isFinishing() && adsPref.getAdStatus()) {
                        adsManager.loadBannerAd(Constant.BANNER_HOME);
                        adsManager.loadInterstitialAd(Constant.INTERSTITIAL_AD_CHANNEL_LIST,
                                AdPlacementHelper.SOLODROID_SHOW_EVERY_CALL);
                    }
                }, 30_000L);
            } catch (Exception e) {
                Log.e(TAG, "Home ads bootstrap failed", e);
                if (bannerStrip != null) {
                    bannerStrip.setVisibility(View.GONE);
                }
                if (antiblockHelper != null) {
                    antiblockHelper.onAdLoadFailed(() -> {
                        if (!activity.isFinishing()) {
                            adsManager.loadBannerAd(Constant.BANNER_HOME);
                        }
                    });
                }
            }
        });
    }

    public static void resume(Activity activity, AdsManager adsManager, AdsPref adsPref,
                              AdsAntiblockHelper antiblockHelper) {
        if (activity == null || adsManager == null || adsPref == null) {
            return;
        }
        if (!adsPref.getAdStatus() || !AdPlacementHelper.hasValidAdNetwork(adsPref)) {
            return;
        }
        try {
            if (antiblockHelper != null) {
                antiblockHelper.resetRetryCount();
            }
            adsManager.resumeBannerAd(Constant.BANNER_HOME);
            // Always refresh interstitial inventory on resume (cheap if already loaded).
            adsManager.loadInterstitialAd(Constant.INTERSTITIAL_AD_CHANNEL_LIST,
                    AdPlacementHelper.SOLODROID_SHOW_EVERY_CALL);
            AppOpenAdCache.invalidateIfStale();
            if (antiblockHelper != null && antiblockHelper.shouldForceAdReloadOnResume()) {
                adsManager.loadBannerAd(Constant.BANNER_HOME);
            }
        } catch (Exception e) {
            Log.e(TAG, "Home ads resume failed", e);
        }
    }
}
