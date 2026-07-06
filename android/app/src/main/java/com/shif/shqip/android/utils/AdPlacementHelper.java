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

    private AdPlacementHelper() {
    }

    public static void onRemoteAdsConfigLoaded(Activity activity) {
        if (activity == null) {
            return;
        }
        MyApplication application = (MyApplication) activity.getApplicationContext();
        application.refreshOpenAds();
    }

    public static void showInterstitialBeforeAction(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        AdsPref adsPref = new AdsPref(activity);
        if (!adsPref.getAdStatus()) {
            return;
        }
        if (activity instanceof MainActivity) {
            ((MainActivity) activity).showInterstitialAd();
        } else if (activity instanceof ActivityMainV2) {
            ((ActivityMainV2) activity).showInterstitialAd();
        } else if (activity instanceof ActivityMainV3) {
            ((ActivityMainV3) activity).showInterstitialAd();
        } else {
            AdsManager adsManager = new AdsManager(activity);
            adsManager.loadInterstitialAd(true, adsPref.getInterstitialAdInterval());
            adsManager.showInterstitialAd();
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
