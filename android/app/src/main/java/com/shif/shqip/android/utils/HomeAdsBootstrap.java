package com.shif.shqip.android.utils;

import android.app.Activity;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.shif.shqip.android.R;
import com.shif.shqip.android.databases.prefs.AdsPref;

/**
 * Safe, deferred home-screen ad initialization shared by all design variants.
 */
public final class HomeAdsBootstrap {

    private static final String TAG = "HomeAdsBootstrap";

    private HomeAdsBootstrap() {
    }

    public static void init(AppCompatActivity activity, AdsManager adsManager, AdsPref adsPref,
                            AdsAntiblockHelper antiblockHelper) {
        initFast(activity, adsManager, adsPref, antiblockHelper);
    }

    /** Immediate ad SDK + banner/interstitial preload (Design 2 smooth start). */
    public static void initFast(AppCompatActivity activity, AdsManager adsManager, AdsPref adsPref,
                                AdsAntiblockHelper antiblockHelper) {
        if (activity == null || adsManager == null || adsPref == null) {
            return;
        }
        initNow(activity, adsManager, adsPref, antiblockHelper);
    }

    private static void initNow(Activity activity, AdsManager adsManager, AdsPref adsPref,
                                AdsAntiblockHelper antiblockHelper) {
        if (activity.isFinishing()) {
            return;
        }
        if (!adsPref.getAdStatus() || !AdPlacementHelper.hasValidAdNetwork(adsPref)) {
            return;
        }
        Runnable loadAds = () -> {
            if (activity.isFinishing()) {
                return;
            }
            try {
                adsManager.initializeAd();
                adsManager.updateConsentStatus();
                // Banner waits for MobileAds.initialize internally (DirectAdMobBannerHelper).
                adsManager.loadBannerAd(Constant.BANNER_HOME);
                adsManager.loadInterstitialAd(Constant.INTERSTITIAL_AD_CHANNEL_LIST,
                        AdPlacementHelper.SOLODROID_SHOW_EVERY_CALL);
                // Soft retries — DirectAdMobBannerHelper also retries on failure.
                View root = activity.findViewById(R.id.design_root);
                if (root == null) {
                    root = activity.getWindow().getDecorView();
                }
                root.postDelayed(() -> {
                    if (!activity.isFinishing()) {
                        adsManager.loadBannerAd(Constant.BANNER_HOME);
                    }
                }, 2_500L);
                root.postDelayed(() -> {
                    if (!activity.isFinishing()) {
                        adsManager.loadBannerAd(Constant.BANNER_HOME);
                    }
                }, 6_000L);
                if (antiblockHelper != null) {
                    antiblockHelper.onAdLoadFailed(() -> {
                        if (!activity.isFinishing()) {
                            adsManager.loadBannerAd(Constant.BANNER_HOME);
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Home ads bootstrap failed", e);
            }
        };
        View anchor = activity.findViewById(R.id.banner_strip);
        if (anchor == null) {
            anchor = activity.findViewById(R.id.bannerAdView);
        }
        if (anchor == null) {
            anchor = activity.findViewById(R.id.design_root);
        }
        if (anchor != null) {
            anchor.post(loadAds);
        } else {
            loadAds.run();
        }
    }

    public static void resume(Activity activity, AdsManager adsManager, AdsPref adsPref,
                              AdsAntiblockHelper antiblockHelper) {
        if (activity == null || adsManager == null || adsPref == null || !adsPref.getAdStatus()) {
            return;
        }
        try {
            adsManager.resumeBannerAd(Constant.BANNER_HOME);
            if (antiblockHelper != null && antiblockHelper.shouldForceAdReloadOnResume()) {
                adsManager.loadBannerAd(Constant.BANNER_HOME);
                adsManager.loadInterstitialAd(Constant.INTERSTITIAL_AD_CHANNEL_LIST,
                        AdPlacementHelper.SOLODROID_SHOW_EVERY_CALL);
            }
        } catch (Exception e) {
            Log.e(TAG, "Home ads resume failed", e);
        }
    }
}
