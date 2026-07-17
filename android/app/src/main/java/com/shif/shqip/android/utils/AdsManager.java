package com.shif.shqip.android.utils;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.shif.shqip.android.Config;
import com.shif.shqip.android.R;
import com.shif.shqip.android.databases.prefs.AdsPref;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.solodroidx.ads.appopen.AppOpenAd;
import com.solodroidx.ads.banner.BannerAd;
import com.solodroidx.ads.gdpr.GDPR;
import com.solodroidx.ads.initialization.InitializeAd;
import com.solodroidx.ads.interstitial.InterstitialAd;
import com.solodroidx.ads.listener.OnShowAdCompleteListener;
import com.solodroidx.ads.nativead.NativeAd;
import com.solodroidx.ads.nativead.NativeAdView;
import com.solodroidx.ads.nativead.NativeAdViewHolder;

public class AdsManager {

    public static final String TAG = "AdsManager";
    Activity activity;
    InitializeAd initializeAd;
    AppOpenAd appOpenAd;
    BannerAd bannerAd;
    InterstitialAd interstitialAd;
    NativeAd nativeAd;
    NativeAdView nativeAdView;
    SharedPref sharedPref;
    AdsPref adsPref;
    GDPR gdpr;

    DirectAdMobBannerHelper directBanner;

    public AdsManager(Activity activity) {
        this.activity = activity;
        this.sharedPref = new SharedPref(activity);
        this.adsPref = new AdsPref(activity);
        this.gdpr = new GDPR(activity);
        initializeAd = new InitializeAd(activity);
        appOpenAd = new AppOpenAd(activity);
        bannerAd = new BannerAd(activity);
        interstitialAd = new InterstitialAd(activity);
        nativeAd = new NativeAd(activity);
        nativeAdView = new NativeAdView(activity);
        try {
            directBanner = DirectAdMobBannerHelper.forActivity(activity);
        } catch (Exception ignored) {
            directBanner = null;
        }
    }

    public void initializeAd() {
        if (adsPref.getAdStatus()) {
            String adNetwork = AdPlacementHelper.resolveAdNetwork(adsPref);
            initializeAd.setAdStatus("1")
                    .setAdNetwork(adNetwork)
                    .setBackupAdNetwork(adsPref.getBackupAds())
                    .setAdMobAppId(adsPref.getAdMobAppId())
                    .setAppLovinSdkKey(activity.getResources().getString(R.string.applovin_sdk_key))
                    .setStartappAppId(adsPref.getStartappAppId())
                    .setUnityGameId(adsPref.getUnityGameId())
                    .setIronSourceAppKey(adsPref.getIronSourceAppKey())
                    .setApplicationId(Tools.getApplicationId())
                    .setDebug(Tools.isDebug())
                    .build();
        }
    }

    private String activeAdNetwork() {
        return AdPlacementHelper.resolveAdNetwork(adsPref);
    }

    public void loadAppOpenAd(boolean placement, boolean withListener, OnShowAdCompleteListener onShowAdCompleteListener) {
        if (placement) {
            if (adsPref.getAdStatus()) {
                appOpenAd.setAdStatus("1")
                        .setAdNetwork(activeAdNetwork())
                        .setBackupAdNetwork(adsPref.getBackupAds())
                        .setAdMobAppOpenId(adsPref.getAdMobAppOpenAdId())
                        .setAdManagerAppOpenId(adsPref.getAdManagerAppOpenAdId())
                        .setApplovinAppOpenId(adsPref.getAppLovinAppOpenAdUnitId())
                        .setWithListener(withListener)
                        .build(onShowAdCompleteListener);
            } else {
                onShowAdCompleteListener.onShowAdComplete();
            }
        }
    }

    public void showAppOpenAd(OnShowAdCompleteListener onShowAdCompleteListener) {
        appOpenAd.show(onShowAdCompleteListener);
    }

    public void destroyAppOpenAd() {
        appOpenAd.destroyOpenAd();
    }

    public void loadBannerAd(boolean placement) {
        if (!placement || !adsPref.getAdStatus()) {
            return;
        }
        try {
            // Home/V2 layouts have banner_strip + admob_banner_host → Direct AdMob.
            // Category/Search often only have Solodroid BannerAdView → use that path.
            if (canUseDirectBanner()) {
                directBanner.ensureLoaded();
                return;
            }
            bannerAd.setAdStatus("1")
                    .setAdNetwork(activeAdNetwork())
                    .setBackupAdNetwork(adsPref.getBackupAds())
                    .setAdMobBannerId(adsPref.getAdMobBannerId())
                    .setGoogleAdManagerBannerId(adsPref.getAdManagerBannerId())
                    .setFanBannerId(adsPref.getFanBannerId())
                    .setUnityBannerId(adsPref.getUnityBannerPlacementId())
                    .setAppLovinBannerId(adsPref.getAppLovinBannerAdUnitId())
                    .setAppLovinBannerZoneId(adsPref.getAppLovinBannerZoneId())
                    .setIronSourceBannerId(adsPref.getIronSourceBannerId())
                    .setIsCollapsibleBanner(false)
                    .setDarkTheme(sharedPref.getIsDarkTheme())
                    .build();
        } catch (Exception ignored) {
        }
    }

    public void destroyBannerAd() {
        try {
            if (directBanner != null) {
                directBanner.destroy();
            }
        } catch (Exception ignored) {
        }
        if (adsPref.getAdStatus()) {
            try {
                bannerAd.destroyAndDetachBanner();
            } catch (Exception ignored) {
            }
        }
    }

    public void resumeBannerAd(boolean placement) {
        if (!placement || !adsPref.getAdStatus()) {
            return;
        }
        try {
            if (canUseDirectBanner()) {
                directBanner.onResume();
                return;
            }
            loadBannerAd(placement);
        } catch (Exception ignored) {
            loadBannerAd(placement);
        }
    }

    public void ensureBannerAd() {
        if (!adsPref.getAdStatus()) {
            return;
        }
        try {
            if (canUseDirectBanner()) {
                directBanner.ensureLoaded();
                return;
            }
            loadBannerAd(true);
        } catch (Exception ignored) {
        }
    }

    public void forceReloadBannerAd() {
        if (!adsPref.getAdStatus()) {
            return;
        }
        try {
            if (canUseDirectBanner()) {
                directBanner.forceReload();
                return;
            }
            destroyBannerAd();
            loadBannerAd(true);
        } catch (Exception ignored) {
        }
    }

    private boolean canUseDirectBanner() {
        if (directBanner == null) {
            return false;
        }
        if (!"admob".equalsIgnoreCase(activeAdNetwork())) {
            return false;
        }
        if (!AdPlacementHelper.isValidAdUnit(adsPref.getAdMobBannerId())) {
            return false;
        }
        // Only when layout has DirectAdMob host/strip (home). Otherwise Solodroid BannerAdView.
        return activity.findViewById(R.id.admob_banner_host) != null
                || activity.findViewById(R.id.banner_strip) != null;
    }

    public void loadInterstitialAd(boolean placement, int interval) {
        if (placement) {
            if (adsPref.getAdStatus()) {
                interstitialAd.setAdStatus("1")
                        .setAdNetwork(activeAdNetwork())
                        .setBackupAdNetwork(adsPref.getBackupAds())
                        .setAdMobInterstitialId(adsPref.getAdMobInterstitialId())
                        .setGoogleAdManagerInterstitialId(adsPref.getAdManagerInterstitialId())
                        .setFanInterstitialId(adsPref.getFanInterstitialId())
                        .setUnityInterstitialId(adsPref.getUnityInterstitialPlacementId())
                        .setAppLovinInterstitialId(adsPref.getAppLovinInterstitialAdUnitId())
                        .setAppLovinInterstitialZoneId(adsPref.getAppLovinInterstitialZoneId())
                        .setIronSourceInterstitialId(adsPref.getIronSourceInterstitialId())
                        .setInterval(interval)
                        .build();
            }
        }
    }

    public void showInterstitialAd() {
        if (adsPref.getAdStatus()) {
            interstitialAd.show();
        }
    }

    public void loadNativeAd(boolean placement, String style) {
        if (placement) {
            if (adsPref.getAdStatus()) {
                nativeAd.setAdStatus("1")
                        .setAdNetwork(activeAdNetwork())
                        .setBackupAdNetwork(adsPref.getBackupAds())
                        .setAdMobNativeId(adsPref.getAdMobNativeId())
                        .setAdManagerNativeId(adsPref.getAdManagerNativeId())
                        .setFanNativeId(adsPref.getFanNativeId())
                        .setAppLovinNativeId(adsPref.getAppLovinNativeAdManualUnitId())
                        .setAppLovinDiscoveryMrecZoneId(adsPref.getAppLovinBannerMrecZoneId())
                        .setNativeAdStyle(style)
                        .setRadius(R.dimen.corner_radius)
                        .setStrokeWidth(R.dimen.native_ad_stroke_width)
                        .setStrokeColor(R.color.color_stroke_native_ad)
                        .setBackgroundColor(R.color.color_light_native_ad_background, R.color.color_dark_native_ad_background)
                        .setMargin(R.dimen.no_margin, R.dimen.no_margin, R.dimen.no_margin, R.dimen.no_margin)
                        .setDarkTheme(sharedPref.getIsDarkTheme())
                        .build();
            }
        }
    }

    public void loadNativeAdView(View view, boolean placement, String nativeAdStyle) {
        if (placement) {
            if (adsPref.getAdStatus()) {
                nativeAdView.setView(view)
                        .setAdStatus("1")
                        .setAdNetwork(activeAdNetwork())
                        .setBackupAdNetwork(adsPref.getBackupAds())
                        .setAdMobNativeId(adsPref.getAdMobNativeId())
                        .setAdManagerNativeId(adsPref.getAdManagerNativeId())
                        .setFanNativeId(adsPref.getFanNativeId())
                        .setAppLovinNativeId(adsPref.getAppLovinNativeAdManualUnitId())
                        .setAppLovinDiscoveryMrecZoneId(adsPref.getAppLovinBannerMrecZoneId())
                        .setNativeAdStyle(nativeAdStyle)
                        .setRadius(R.dimen.corner_radius)
                        .setStrokeWidth(R.dimen.native_ad_stroke_width)
                        .setStrokeColor(R.color.color_stroke_native_ad)
                        .setBackgroundColor(R.color.color_light_native_ad_background, R.color.color_dark_native_ad_background)
                        .setMargin(R.dimen.no_margin, R.dimen.no_margin, R.dimen.no_margin, R.dimen.no_margin)
                        .setDarkTheme(sharedPref.getIsDarkTheme())
                        .build();
            }
        }
    }

    public RecyclerView.ViewHolder createNativeAdViewHolder(Context context, @NonNull ViewGroup parent) {
        String adStatus;
        if (adsPref.getAdStatus()) {
            adStatus = "1";
        } else {
            adStatus = "0";
        }

        int marginLeftRight;
        if (sharedPref.getChannelViewType() == Constant.CHANNEL_LIST_DEFAULT) {
            marginLeftRight = R.dimen.spacing_middle;
        } else {
            marginLeftRight = R.dimen.grid_space_channel;
        }

        int marginTopBottom = R.dimen.grid_space_channel;

        return new NativeAdViewHolder(NativeAdViewHolder.setLayoutInflater(parent, Tools.nativeAdStyleFormatter(Constant.NATIVE_AD_STYLE_CHANNEL_LIST)))
                .setAdStatus(adStatus)
                .setAdNetwork(activeAdNetwork())
                .setBackupAdNetwork(adsPref.getBackupAds())
                .setAdMobNativeId(adsPref.getAdMobNativeId())
                .setAdManagerNativeId(adsPref.getAdManagerNativeId())
                .setFanNativeId(adsPref.getFanNativeId())
                .setAppLovinNativeId(adsPref.getAppLovinNativeAdManualUnitId())
                .setAppLovinDiscoveryMrecZoneId(adsPref.getAppLovinBannerMrecZoneId())
                .setNativeAdStyle(Tools.nativeAdStyleFormatter(Constant.NATIVE_AD_STYLE_CHANNEL_LIST))
                .setBackgroundColor(R.color.color_light_native_ad_background, R.color.color_dark_native_ad_background)
                .setRadius(context, R.dimen.corner_radius)
                .setStrokeWidth(context, R.dimen.native_ad_stroke_width)
                .setStrokeColor(context, R.color.color_stroke_native_ad)
                .setMargin(context, marginLeftRight, marginTopBottom, marginLeftRight, marginTopBottom)
                //.setPadding(context, noPadding, padding, noPadding, padding)
                .setDarkTheme(sharedPref.getIsDarkTheme());
    }

    public void bindNativeAdViewHolder(Context context, NativeAdViewHolder holder) {
        holder.buildNativeAd(context);
    }

    public void updateConsentStatus() {
        if (Config.ENABLE_GDPR_EU_CONSENT) {
            gdpr.updateGDPRConsentStatus(activeAdNetwork(), false, false);
        }
    }

}
