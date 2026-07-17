package com.shif.shqip.android.databases.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import com.shif.shqip.android.BuildConfig;

public class AdsPref {

    Context context;
    SharedPreferences sharedPreferences;
    SharedPreferences.Editor editor;

    public AdsPref(Context context) {
        this.context = context;
        sharedPreferences = context.getSharedPreferences(BuildConfig.APPLICATION_ID + "_" + "ads_preferences", Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
    }

    public void saveAds(boolean ad_status, String ad_type, String backup_ads, String admob_publisher_id, String admob_app_id, String admob_banner_unit_id, String admob_interstitial_unit_id, String admob_native_unit_id, String admob_app_open_ad_unit_id, String adManagerBannerUnitId, String adManagerInterstitialUnitId, String adManagerNativeUnitId, String adManagerAppOpenAdUnitId, String fan_banner_unit_id, String fan_interstitial_unit_id, String fan_native_unit_id, String startapp_app_id, String unity_game_id, String unity_banner_placement_id, String unity_interstitial_placement_id, String applovin_banner_ad_unit_id, String applovin_interstitial_ad_unit_id, String applovin_native_ad_manual_unit_id, String applovinAppOpenAdUnitId, String applovin_banner_zone_id, String applovinBannerMrecZoneId, String applovin_interstitial_zone_id, String ironSourceAppKey, String ironSourceBannerId, String ironSourceInterstitialId, String wortiseAppId, String wortiseBannerId, String wortiseInterstitialId, String wortiseNativeId, String wortiseAppOpenId, int interstitial_ad_interval, int native_ad_interval, int native_ad_index, String date_time) {
        editor.putBoolean("server_ads_enabled", ad_status);
        editor.putBoolean("ads_status", ad_status && !getDeviceNoAds());
        editor.putString("ad_type", ad_type);
        editor.putString("backup_ads", backup_ads);
        editor.putString("admob_publisher_id", admob_publisher_id);
        editor.putString("admob_app_id", admob_app_id);
        editor.putString("admob_banner_unit_id", admob_banner_unit_id);
        editor.putString("admob_interstitial_unit_id", admob_interstitial_unit_id);
        editor.putString("admob_native_unit_id", admob_native_unit_id);
        editor.putString("admob_app_open_ad_unit_id", admob_app_open_ad_unit_id);
        editor.putString("ad_manager_banner_unit_id", adManagerBannerUnitId);
        editor.putString("ad_manager_interstitial_unit_id", adManagerInterstitialUnitId);
        editor.putString("ad_manager_native_unit_id", adManagerNativeUnitId);
        editor.putString("ad_manager_app_open_ad_unit_id", adManagerAppOpenAdUnitId);
        editor.putString("fan_banner_unit_id", fan_banner_unit_id);
        editor.putString("fan_interstitial_unit_id", fan_interstitial_unit_id);
        editor.putString("fan_native_unit_id", fan_native_unit_id);
        editor.putString("startapp_app_id", startapp_app_id);
        editor.putString("unity_game_id", unity_game_id);
        editor.putString("unity_banner_placement_id", unity_banner_placement_id);
        editor.putString("unity_interstitial_placement_id", unity_interstitial_placement_id);
        editor.putString("applovin_banner_ad_unit_id", applovin_banner_ad_unit_id);
        editor.putString("applovin_interstitial_ad_unit_id", applovin_interstitial_ad_unit_id);
        editor.putString("applovin_native_ad_manual_unit_id", applovin_native_ad_manual_unit_id);
        editor.putString("applovin_app_open_ad_unit_id", applovinAppOpenAdUnitId);
        editor.putString("applovin_banner_zone_id", applovin_banner_zone_id);
        editor.putString("applovin_banner_mrec_zone_id", applovinBannerMrecZoneId);
        editor.putString("applovin_interstitial_zone_id", applovin_interstitial_zone_id);
        editor.putString("ironsource_app_key", ironSourceAppKey);
        editor.putString("ironsource_banner_id", ironSourceBannerId);
        editor.putString("ironsource_interstitial_id", ironSourceInterstitialId);
        editor.putString("wortise_app_id", wortiseAppId);
        editor.putString("wortise_banner_id", wortiseBannerId);
        editor.putString("wortise_interstitial_id", wortiseInterstitialId);
        editor.putString("wortise_native_id", wortiseNativeId);
        editor.putString("wortise_app_open_id", wortiseAppOpenId);
        editor.putInt("interstitial_ad_interval", interstitial_ad_interval);
        editor.putInt("native_ad_interval", native_ad_interval);
        editor.putInt("native_ad_index", native_ad_index);
        editor.putString("date_time", date_time);
        editor.apply();
    }

    public void setTestMode(boolean test_mode) {
        editor.putBoolean("test_mode", test_mode);
        editor.apply();
    }

    public boolean getTestMode() {
        return sharedPreferences.getBoolean("test_mode", false);
    }

    public boolean getServerAdsEnabled() {
        return sharedPreferences.getBoolean("server_ads_enabled", true);
    }

    public boolean getDeviceNoAds() {
        return sharedPreferences.getBoolean("device_no_ads", false);
    }

    public void setDeviceNoAds(boolean deviceNoAds) {
        editor.putBoolean("device_no_ads", deviceNoAds);
        editor.apply();
        refreshEffectiveAdStatus();
    }

    public void refreshEffectiveAdStatus() {
        boolean enabled = getServerAdsEnabled() && !getDeviceNoAds();
        editor.putBoolean("ads_status", enabled);
        editor.apply();
    }

    public boolean getAdStatus() {
        return sharedPreferences.getBoolean("ads_status", true);
    }

    public void setAdStatus(boolean enabled) {
        editor.putBoolean("ads_status", enabled);
        editor.apply();
    }

    public String getMainAds() {
        return sharedPreferences.getString("ad_type", "0");
    }

    public String getBackupAds() {
        return sharedPreferences.getString("backup_ads", "none");
    }

    public String getAdMobPublisherId() {
        return sharedPreferences.getString("admob_publisher_id", "0");
    }

    public String getAdMobAppId() {
        return sharedPreferences.getString("admob_app_id", "0");
    }

    public String getAdMobBannerId() {
        return sharedPreferences.getString("admob_banner_unit_id", "0");
    }

    public String getAdMobInterstitialId() {
        return sharedPreferences.getString("admob_interstitial_unit_id", "0");
    }

    public String getAdMobNativeId() {
        return sharedPreferences.getString("admob_native_unit_id", "0");
    }

    public String getAdMobAppOpenAdId() {
        return sharedPreferences.getString("admob_app_open_ad_unit_id", "0");
    }

    public String getAdManagerBannerId() {
        return sharedPreferences.getString("ad_manager_banner_unit_id", "0");
    }

    public String getAdManagerInterstitialId() {
        return sharedPreferences.getString("ad_manager_interstitial_unit_id", "0");
    }

    public String getAdManagerNativeId() {
        return sharedPreferences.getString("ad_manager_native_unit_id", "0");
    }

    public String getAdManagerAppOpenAdId() {
        return sharedPreferences.getString("ad_manager_app_open_ad_unit_id", "0");
    }

    public String getFanBannerId() {
        return sharedPreferences.getString("fan_banner_unit_id", "0");
    }

    public String getFanInterstitialId() {
        return sharedPreferences.getString("fan_interstitial_unit_id", "0");
    }

    public String getFanNativeId() {
        return sharedPreferences.getString("fan_native_unit_id", "0");
    }

    public String getStartappAppId() {
        return sharedPreferences.getString("startapp_app_id", "0");
    }

    public String getUnityGameId() {
        return sharedPreferences.getString("unity_game_id", "0");
    }

    public String getUnityBannerPlacementId() {
        return sharedPreferences.getString("unity_banner_placement_id", "banner");
    }

    public String getUnityInterstitialPlacementId() {
        return sharedPreferences.getString("unity_interstitial_placement_id", "video");
    }

    public String getAppLovinBannerAdUnitId() {
        return sharedPreferences.getString("applovin_banner_ad_unit_id", "0");
    }

    public String getAppLovinInterstitialAdUnitId() {
        return sharedPreferences.getString("applovin_interstitial_ad_unit_id", "0");
    }

    public String getAppLovinNativeAdManualUnitId() {
        return sharedPreferences.getString("applovin_native_ad_manual_unit_id", "0");
    }

    public String getAppLovinAppOpenAdUnitId() {
        return sharedPreferences.getString("applovin_app_open_ad_unit_id", "0");
    }

    public String getAppLovinBannerZoneId() {
        return sharedPreferences.getString("applovin_banner_zone_id", "0");
    }

    public String getAppLovinBannerMrecZoneId() {
        return sharedPreferences.getString("applovin_banner_mrec_zone_id", "0");
    }

    public String getAppLovinInterstitialZoneId() {
        return sharedPreferences.getString("applovin_interstitial_zone_id", "0");
    }

    public String getIronSourceAppKey() {
        return sharedPreferences.getString("ironsource_app_key", "0");
    }

    public String getIronSourceBannerId() {
        return sharedPreferences.getString("ironsource_banner_id", "0");
    }

    public String getIronSourceInterstitialId() {
        return sharedPreferences.getString("ironsource_interstitial_id", "0");
    }

    public String getWortiseAppId() {
        return sharedPreferences.getString("wortise_app_id", "0");
    }

    public String getWortiseBannerId() {
        return sharedPreferences.getString("wortise_banner_id", "0");
    }

    public String getWortiseInterstitialId() {
        return sharedPreferences.getString("wortise_interstitial_id", "0");
    }

    public String getWortiseNativeId() {
        return sharedPreferences.getString("wortise_native_id", "0");
    }

    public String getWortiseAppOpenId() {
        return sharedPreferences.getString("wortise_app_open_id", "0");
    }

    public int getInterstitialAdInterval() {
        // Product rule: interstitial every 3 channel clicks (ignore remote overrides).
        return 3;
    }

    public boolean getAppOpenOnStart() {
        return sharedPreferences.getBoolean("app_open_on_start", true);
    }

    public boolean getAppOpenOnResume() {
        return sharedPreferences.getBoolean("app_open_on_resume", true);
    }

    public void saveAdPlacementFlags(boolean appOpenOnStart, boolean appOpenOnResume) {
        editor.putBoolean("app_open_on_start", appOpenOnStart);
        editor.putBoolean("app_open_on_resume", appOpenOnResume);
        editor.apply();
    }

    public int getNativeAdInterval() {
        return sharedPreferences.getInt("native_ad_interval", 0);
    }

    public int getNativeAdIndex() {
        return sharedPreferences.getInt("native_ad_index", 0);
    }

    public String getDateTime() {
        return sharedPreferences.getString("date_time", "0");
    }

    public Integer getInterstitialAdCounter() {
        return sharedPreferences.getInt("interstitial_ad_counter", 1);
    }

    public void updateInterstitialAdCounter(int counter) {
        editor.putInt("interstitial_ad_counter", counter);
        editor.apply();
    }

}