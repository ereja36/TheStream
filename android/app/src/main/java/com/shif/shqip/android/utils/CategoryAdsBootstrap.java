package com.shif.shqip.android.utils;

import android.app.Activity;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.shif.shqip.android.R;
import com.shif.shqip.android.databases.prefs.AdsPref;

import static com.shif.shqip.android.utils.Constant.BANNER_CATEGORY_DETAIL;
import static com.shif.shqip.android.utils.Constant.INTERSTITIAL_AD_CHANNEL_LIST;

/**
 * Deferred ad initialization for category / channel list screens.
 */
public final class CategoryAdsBootstrap {

    private static final String TAG = "CategoryAdsBootstrap";

    private CategoryAdsBootstrap() {
    }

    public static void init(AppCompatActivity activity, AdsManager adsManager, AdsPref adsPref) {
        initFast(activity, adsManager, adsPref);
    }

    public static void initFast(AppCompatActivity activity, AdsManager adsManager, AdsPref adsPref) {
        if (activity == null || adsManager == null || adsPref == null) {
            return;
        }
        initNow(activity, adsManager, adsPref);
    }

    private static void initNow(Activity activity, AdsManager adsManager, AdsPref adsPref) {
        if (activity.isFinishing()) {
            return;
        }
        if (!adsPref.getAdStatus() || !AdPlacementHelper.hasValidAdNetwork(adsPref)) {
            return;
        }
        Runnable load = () -> {
            if (activity.isFinishing()) {
                return;
            }
            try {
                adsManager.initializeAd();
                adsManager.updateConsentStatus();
                adsManager.loadBannerAd(BANNER_CATEGORY_DETAIL);
                adsManager.loadInterstitialAd(INTERSTITIAL_AD_CHANNEL_LIST, AdPlacementHelper.SOLODROID_SHOW_EVERY_CALL);
                View bannerSlot = activity.findViewById(R.id.bannerAdView);
                if (bannerSlot == null) {
                    bannerSlot = activity.findViewById(R.id.banner_strip);
                }
                if (bannerSlot != null) {
                    bannerSlot.postDelayed(() -> {
                        if (!activity.isFinishing()) {
                            adsManager.loadBannerAd(BANNER_CATEGORY_DETAIL);
                        }
                    }, 2_500L);
                }
            } catch (Exception e) {
                Log.e(TAG, "Category ads init failed", e);
            }
        };
        View bannerSlot = activity.findViewById(R.id.admob_banner_host);
        if (bannerSlot == null) {
            bannerSlot = activity.findViewById(R.id.bannerAdView);
        }
        if (bannerSlot != null) {
            bannerSlot.post(load);
        } else {
            load.run();
        }
    }
}
