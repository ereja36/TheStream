package com.shif.shqip.android.utils;

import android.app.Activity;
import android.text.TextUtils;

import com.shif.shqip.android.activities.ActivityMainV2;
import com.shif.shqip.android.activities.ActivityMainV3;
import com.shif.shqip.android.activities.MainActivity;
import com.shif.shqip.android.activities.MyApplication;
import com.shif.shqip.android.databases.prefs.AdsPref;

/**
 * Central place to show interstitials and refresh app-open ads after remote config.
 */
public final class AdPlacementHelper {

    public static final int INTERSTITIAL_EVERY_CLICKS = 3;
    private static final int NATIVE_PRELOAD_COUNT = 4;

    private AdPlacementHelper() {
    }

    public static void onRemoteAdsConfigLoaded(Activity activity) {
        if (activity == null) {
            return;
        }
        MyApplication application = (MyApplication) activity.getApplicationContext();
        application.markResumeAppOpenReady();
        AdsPref adsPref = new AdsPref(activity);
        if (adsPref.getAdStatus() && hasValidAdNetwork(adsPref)) {
            AdsManager adsManager = new AdsManager(activity);
            adsManager.initializeAd();
            adsManager.updateConsentStatus();
            loadHomePlacementAds(activity, adsManager, adsPref);
        }
    }

    public static void loadHomePlacementAds(Activity activity, AdsManager adsManager, AdsPref adsPref) {
        if (activity == null || adsManager == null || adsPref == null || !adsPref.getAdStatus()) {
            return;
        }
        int interval = resolveInterstitialInterval(adsPref);
        adsManager.loadBannerAd(Constant.BANNER_HOME);
        adsManager.loadInterstitialAd(Constant.INTERSTITIAL_AD_CHANNEL_LIST, interval);
        preloadNativeAds(activity, adsManager, NATIVE_PRELOAD_COUNT);
    }

    public static void preloadNativeAds(Activity activity, AdsManager primary, int count) {
        if (activity == null || primary == null || count <= 0) {
            return;
        }
        AdsPref adsPref = new AdsPref(activity);
        if (!adsPref.getAdStatus()) {
            return;
        }
        primary.loadNativeAd(Constant.NATIVE_AD_CHANNEL_LIST, Constant.NATIVE_AD_STYLE_CHANNEL_LIST);
        for (int i = 1; i < count; i++) {
            AdsManager extra = new AdsManager(activity);
            extra.loadNativeAd(Constant.NATIVE_AD_CHANNEL_LIST, Constant.NATIVE_AD_STYLE_CHANNEL_LIST);
        }
    }

    public static int resolveInterstitialInterval(AdsPref adsPref) {
        return INTERSTITIAL_EVERY_CLICKS;
    }

    public static void pauseHomeBanner(Activity activity) {
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).destroyBannerAd();
        } else if (activity instanceof ActivityMainV2) {
            ((ActivityMainV2) activity).destroyBannerAd();
        } else if (activity instanceof ActivityMainV3) {
            ((ActivityMainV3) activity).destroyBannerAd();
        }
    }

    public static void showInterstitialBeforeAction(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        AdsPref adsPref = new AdsPref(activity);
        if (!adsPref.getAdStatus()) {
            return;
        }
        int interval = resolveInterstitialInterval(adsPref);
        int counter = adsPref.getInterstitialAdCounter();
        if (counter < interval) {
            adsPref.updateInterstitialAdCounter(counter + 1);
            return;
        }
        adsPref.updateInterstitialAdCounter(1);
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).showInterstitialAd();
        } else if (activity instanceof ActivityMainV2) {
            ((ActivityMainV2) activity).showInterstitialAd();
        } else if (activity instanceof ActivityMainV3) {
            ((ActivityMainV3) activity).showInterstitialAd();
        } else {
            AdsManager adsManager = new AdsManager(activity);
            adsManager.loadInterstitialAd(true, interval);
            adsManager.showInterstitialAd();
        }
    }

    public static boolean shouldInsertNativeAds() {
        return Constant.NATIVE_AD_CHANNEL_LIST;
    }

    public static int resolveNativeInterval(AdsPref adsPref) {
        return 3;
    }

    public static int resolveNativeStartIndex(AdsPref adsPref) {
        return 3;
    }

    public static boolean hasValidAdNetwork(AdsPref adsPref) {
        if (adsPref == null || !adsPref.getAdStatus()) {
            return false;
        }
        String mainAds = adsPref.getMainAds();
        return !TextUtils.isEmpty(mainAds) && !"0".equals(mainAds);
    }
}
