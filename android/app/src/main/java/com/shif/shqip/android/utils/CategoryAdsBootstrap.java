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
        if (activity == null || adsManager == null || adsPref == null) {
            return;
        }
        View anchor = activity.findViewById(R.id.parent_view);
        if (anchor == null) {
            anchor = activity.getWindow().getDecorView();
        }
        anchor.post(() -> initNow(activity, adsManager, adsPref));
    }

    private static void initNow(Activity activity, AdsManager adsManager, AdsPref adsPref) {
        if (activity.isFinishing()) {
            return;
        }
        try {
            adsManager.initializeAd();
            adsManager.updateConsentStatus();
            adsManager.loadBannerAd(BANNER_CATEGORY_DETAIL);
            adsManager.loadInterstitialAd(INTERSTITIAL_AD_CHANNEL_LIST, adsPref.getInterstitialAdInterval());
            adsManager.loadNativeAd(Constant.NATIVE_AD_CHANNEL_LIST, Constant.NATIVE_AD_STYLE_CHANNEL_LIST);
        } catch (Exception e) {
            Log.e(TAG, "Category ads init failed", e);
        }
    }
}
