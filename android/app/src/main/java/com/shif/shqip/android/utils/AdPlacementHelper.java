package com.shif.shqip.android.utils;

import android.app.Activity;
import android.text.TextUtils;

import com.shif.shqip.android.activities.MainActivity;
import com.shif.shqip.android.activities.MyApplication;
import com.shif.shqip.android.databases.prefs.AdsPref;

/**
 * Central place to initialize and refresh ads after remote config is loaded.
 */
public final class AdPlacementHelper {

    private AdPlacementHelper() {
    }

    public static void onRemoteAdsConfigLoaded(Activity activity) {
        if (activity == null) {
            return;
        }
        MyApplication application = (MyApplication) activity.getApplicationContext();
        application.refreshOpenAds();
        AdsPref adsPref = new AdsPref(activity);
        if (adsPref.getAdStatus() && hasValidAdNetwork(adsPref)) {
            AdsManager adsManager = new AdsManager(activity);
            adsManager.initializeAd();
            adsManager.updateConsentStatus();
        }
    }

    public static void bootstrapMainAds(MainActivity activity, AdsManager adsManager) {
        if (activity == null || adsManager == null) {
            return;
        }
        AdsPref adsPref = new AdsPref(activity);
        if (!adsPref.getAdStatus() || !hasValidAdNetwork(adsPref)) {
            return;
        }
        adsManager.initializeAd();
        adsManager.updateConsentStatus();
        reloadHomeAds(activity, adsManager);
    }

    public static void reloadHomeAds(MainActivity activity, AdsManager adsManager) {
        if (activity == null || adsManager == null || activity.isFinishing()) {
            return;
        }
        AdsPref adsPref = new AdsPref(activity);
        if (!adsPref.getAdStatus() || !hasValidAdNetwork(adsPref)) {
            return;
        }
        adsManager.loadAppOpenAd(Constant.APP_OPEN_AD_ON_RESUME, false, () -> {
        });
        adsManager.loadBannerAd(Constant.BANNER_HOME);
        adsManager.loadInterstitialAd(Constant.INTERSTITIAL_AD_CHANNEL_LIST, adsPref.getInterstitialAdInterval());
        if (Constant.NATIVE_AD_EXIT_DIALOG) {
            adsManager.loadNativeAd(Constant.NATIVE_AD_EXIT_DIALOG, Constant.NATIVE_AD_STYLE_EXIT_DIALOG);
        }
    }

    public static boolean shouldInsertNativeAds() {
        return Constant.NATIVE_AD_CHANNEL_LIST;
    }

    public static int resolveNativeInterval(AdsPref adsPref) {
        int interval = adsPref.getNativeAdInterval();
        if (interval <= 0) {
            interval = 8;
        }
        return Math.max(6, interval);
    }

    public static int resolveNativeStartIndex(AdsPref adsPref) {
        int index = adsPref.getNativeAdIndex();
        if (index <= 0) {
            index = 4;
        }
        return Math.max(2, index);
    }

    public static boolean hasValidAdNetwork(AdsPref adsPref) {
        if (adsPref == null || !adsPref.getAdStatus()) {
            return false;
        }
        String mainAds = adsPref.getMainAds();
        return !TextUtils.isEmpty(mainAds) && !"0".equals(mainAds);
    }
}
