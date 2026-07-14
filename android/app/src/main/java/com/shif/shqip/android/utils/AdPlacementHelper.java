package com.shif.shqip.android.utils;

import android.app.Activity;
import android.text.TextUtils;

import com.shif.shqip.android.activities.ActivityCategoryDetail;
import com.shif.shqip.android.activities.ActivityMainV2;
import com.shif.shqip.android.activities.ActivityMainV3;
import com.shif.shqip.android.activities.ActivitySearch;
import com.shif.shqip.android.activities.MainActivity;
import com.shif.shqip.android.activities.MyApplication;
import com.shif.shqip.android.databases.prefs.AdsPref;

import java.util.Locale;

/**
 * Central place to show interstitials and refresh app-open ads after remote config.
 * Click interval is owned here; Solodroid interstitial is loaded with interval=1 so
 * every show() call actually displays when our counter says so.
 */
public final class AdPlacementHelper {

    public static final int INTERSTITIAL_EVERY_CLICKS = 3;
    /** Solodroid only shows when its internal counter == interval; use 1 so our counter drives display. */
    public static final int SOLODROID_SHOW_EVERY_CALL = 1;
    private static final int NATIVE_PRELOAD_COUNT = 2;

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
            AppOpenAdCache.invalidateIfStale();
            AdsManager adsManager = new AdsManager(activity);
            adsManager.initializeAd();
            adsManager.updateConsentStatus();
            // Soft ensure — do NOT forceReload on every get_config (that blinks/kills banners).
            loadHomePlacementAds(activity, adsManager, adsPref);
            NativeAdSlotHelper.remountHomeSlots(activity);
            // Keep a fresh App Open preload available for resume.
            if (adsPref.getAppOpenOnResume()) {
                try {
                    adsManager.loadAppOpenAd(true, false, () -> {
                    });
                } catch (Exception ignored) {
                }
            }
        } else {
            AppOpenAdCache.invalidate();
        }
    }

    public static void loadHomePlacementAds(Activity activity, AdsManager adsManager, AdsPref adsPref) {
        if (activity == null || adsManager == null || adsPref == null || !adsPref.getAdStatus()) {
            return;
        }
        if (!hasValidAdNetwork(adsPref)) {
            return;
        }
        adsManager.ensureBannerAd();
        adsManager.loadInterstitialAd(Constant.INTERSTITIAL_AD_CHANNEL_LIST, SOLODROID_SHOW_EVERY_CALL);
    }

    /** Hard refresh — only when ads were remotely re-enabled after being off. */
    public static void forceReloadHomePlacementAds(Activity activity, AdsManager adsManager, AdsPref adsPref) {
        if (activity == null || adsManager == null || adsPref == null || !adsPref.getAdStatus()) {
            return;
        }
        if (!hasValidAdNetwork(adsPref)) {
            return;
        }
        adsManager.forceReloadBannerAd();
        adsManager.loadInterstitialAd(Constant.INTERSTITIAL_AD_CHANNEL_LIST, SOLODROID_SHOW_EVERY_CALL);
        NativeAdSlotHelper.remountHomeSlots(activity);
    }

    public static void preloadNativeAds(Activity activity, AdsManager primary, int count) {
        if (activity == null || primary == null || count <= 0) {
            return;
        }
        AdsPref adsPref = new AdsPref(activity);
        if (!adsPref.getAdStatus() || !hasValidAdNetwork(adsPref)) {
            return;
        }
        primary.loadNativeAd(Constant.NATIVE_AD_CHANNEL_LIST, Constant.NATIVE_AD_STYLE_CHANNEL_LIST);
        for (int i = 1; i < count; i++) {
            AdsManager extra = new AdsManager(activity);
            extra.loadNativeAd(Constant.NATIVE_AD_CHANNEL_LIST, Constant.NATIVE_AD_STYLE_CHANNEL_LIST);
        }
    }

    public static int resolveInterstitialInterval(AdsPref adsPref) {
        if (adsPref == null) {
            return INTERSTITIAL_EVERY_CLICKS;
        }
        return adsPref.getInterstitialAdInterval();
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
        if (!adsPref.getAdStatus() || !hasValidAdNetwork(adsPref)) {
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
        } else if (activity instanceof ActivityCategoryDetail) {
            ((ActivityCategoryDetail) activity).showInterstitialAd();
        } else if (activity instanceof ActivitySearch) {
            ((ActivitySearch) activity).showInterstitialAd();
        } else {
            AdsManager adsManager = new AdsManager(activity);
            adsManager.initializeAd();
            adsManager.loadInterstitialAd(true, SOLODROID_SHOW_EVERY_CALL);
            activity.getWindow().getDecorView().postDelayed(adsManager::showInterstitialAd, 400L);
        }
    }

    public static boolean shouldInsertNativeAds(AdsPref adsPref) {
        return Constant.NATIVE_AD_CHANNEL_LIST
                && adsPref != null
                && adsPref.getAdStatus()
                && hasValidAdNetwork(adsPref)
                && isValidAdUnit(adsPref.getAdMobNativeId());
    }

    /** @deprecated Use {@link #shouldInsertNativeAds(AdsPref)} */
    @Deprecated
    public static boolean shouldInsertNativeAds() {
        return Constant.NATIVE_AD_CHANNEL_LIST;
    }

    public static int resolveNativeInterval(AdsPref adsPref) {
        // Native every 3 channels (normal Solodroid list pattern).
        return 3;
    }

    public static int resolveNativeStartIndex(AdsPref adsPref) {
        // First native after the 3rd channel, then every 3 thereafter.
        return 3;
    }

    public static boolean hasValidAdNetwork(AdsPref adsPref) {
        if (adsPref == null || !adsPref.getAdStatus()) {
            return false;
        }
        return !TextUtils.isEmpty(resolveAdNetwork(adsPref));
    }

    /** Picks main ad network from server config, falling back to AdMob when unit IDs exist. */
    public static String resolveAdNetwork(AdsPref adsPref) {
        if (adsPref == null) {
            return "admob";
        }
        String mainAds = adsPref.getMainAds();
        if (!TextUtils.isEmpty(mainAds) && !"0".equals(mainAds)) {
            return mainAds.trim().toLowerCase(Locale.US);
        }
        if (hasConfiguredAdMobUnits(adsPref)) {
            return "admob";
        }
        String backup = adsPref.getBackupAds();
        if (!TextUtils.isEmpty(backup) && !"0".equals(backup) && !"none".equalsIgnoreCase(backup)) {
            return backup.trim().toLowerCase(Locale.US);
        }
        return "";
    }

    private static boolean hasConfiguredAdMobUnits(AdsPref adsPref) {
        return isValidAdUnit(adsPref.getAdMobBannerId())
                || isValidAdUnit(adsPref.getAdMobInterstitialId())
                || isValidAdUnit(adsPref.getAdMobNativeId())
                || isValidAdUnit(adsPref.getAdMobAppOpenAdId());
    }

    public static boolean isValidAdUnit(String unitId) {
        if (TextUtils.isEmpty(unitId) || "0".equals(unitId)) {
            return false;
        }
        // Reject placeholder-only values like "0" / "000" but allow real ca-app-pub-… IDs.
        return unitId.startsWith("ca-app-pub-")
                || unitId.startsWith("/")
                || (!unitId.startsWith("0") && unitId.length() > 3);
    }
}
