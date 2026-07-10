package com.shif.shqip.android.activities;

import static com.solodroidx.ads.util.Constant.AD_STATUS_ON;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.multidex.MultiDex;

import com.shif.shqip.android.R;
import com.shif.shqip.android.databases.prefs.AdsPref;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.utils.AdsManager;
import com.shif.shqip.android.utils.Tools;
import com.google.firebase.FirebaseApp;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.messaging.FirebaseMessaging;
import com.solodroid.push.sdk.provider.OneSignalPush;
import com.solodroidx.ads.appopen.AppOpenAd;
import com.solodroidx.ads.appopen.AppOpenAdAppLovin;
import com.solodroidx.ads.appopen.AppOpenAdManager;
import com.solodroidx.ads.appopen.AppOpenAdMob;
import com.solodroidx.ads.appopen.AppOpenAdPangle;
import com.solodroidx.ads.appopen.AppOpenAdWortise;
import com.solodroidx.ads.appopen.AppOpenAdYandex;
import com.solodroidx.ads.listener.OnShowAdCompleteListener;

public class MyApplication extends Application {

    public static final String TAG = "MyApplication";
    private static final long MIN_APP_OPEN_GAP_MS = 1_500L;

    private static MyApplication mInstance;
    FirebaseAnalytics mFirebaseAnalytics;
    AdsPref adsPref;
    SharedPref sharedPref;
    AppOpenAd appOpenAd;
    AppOpenAdMob appOpenAdMob;
    AppOpenAdManager appOpenAdManager;
    private boolean openAdsLifecycleRegistered;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Activity currentActivity;
    private boolean resumeAppOpenReady;
    private boolean wasInBackground;
    private boolean pendingResumeAppOpen;
    private long lastAppOpenShownAt;

    @Override
    public void onCreate() {
        super.onCreate();
        mInstance = this;
        sharedPref = new SharedPref(this);
        Tools.applyNightMode(sharedPref.getIsDarkTheme());
        adsPref = new AdsPref(this);
        try {
            initAnalytics();
        } catch (Exception e) {
            Log.e(TAG, "initAnalytics failed", e);
        }
        try {
            initOpenAds();
        } catch (Exception e) {
            Log.e(TAG, "initOpenAds failed", e);
        }
        try {
            initNotification();
        } catch (Exception e) {
            Log.e(TAG, "initNotification failed", e);
        }
    }

    private void initAnalytics() {
        FirebaseApp.initializeApp(this);
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
    }

    public void initNotification() {
        String oneSignalAppId = getResources().getString(R.string.onesignal_app_id);
        boolean hasValidOneSignalAppId = !TextUtils.isEmpty(oneSignalAppId)
                && !"0".equals(oneSignalAppId)
                && !"00000000-0000-0000-0000-000000000000".equals(oneSignalAppId);

        if (hasValidOneSignalAppId) {
            new OneSignalPush.Builder(this)
                    .setOneSignalAppId(oneSignalAppId)
                    .build(() -> {
                        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        intent.putExtra(OneSignalPush.EXTRA_ID, OneSignalPush.Data.id);
                        intent.putExtra(OneSignalPush.EXTRA_TITLE, OneSignalPush.Data.title);
                        intent.putExtra(OneSignalPush.EXTRA_MESSAGE, OneSignalPush.Data.message);
                        intent.putExtra(OneSignalPush.EXTRA_IMAGE, OneSignalPush.Data.bigImage);
                        intent.putExtra(OneSignalPush.EXTRA_LAUNCH_URL, OneSignalPush.Data.launchUrl);
                        intent.putExtra(OneSignalPush.EXTRA_UNIQUE_ID, OneSignalPush.AdditionalData.uniqueId);
                        intent.putExtra(OneSignalPush.EXTRA_POST_ID, OneSignalPush.AdditionalData.postId);
                        intent.putExtra(OneSignalPush.EXTRA_LINK, OneSignalPush.AdditionalData.link);
                        startActivity(intent);
                    });
        } else {
            Log.w(TAG, "OneSignal disabled: invalid app id");
        }

        String topic = getResources().getString(R.string.fcm_notification_topic);
        if (!TextUtils.isEmpty(topic)) {
            FirebaseMessaging.getInstance().subscribeToTopic(topic);
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    public static synchronized MyApplication getInstance() {
        return mInstance;
    }

    private void initOpenAds() {
        refreshOpenAds();
    }

    public void refreshOpenAds() {
        adsPref = new AdsPref(this);
        String mainAds = adsPref.getMainAds();
        if (!adsPref.getAdStatus() || TextUtils.isEmpty(mainAds) || "0".equals(mainAds)) {
            return;
        }
        if (appOpenAdMob == null) {
            appOpenAdMob = new AppOpenAdMob();
        }
        if (appOpenAdManager == null) {
            appOpenAdManager = new AppOpenAdManager();
        }
        if (appOpenAd == null) {
            appOpenAd = new AppOpenAd();
        }
        appOpenAd.initAppOpenAdMob(appOpenAdMob)
                .initAppOpenAdManager(appOpenAdManager)
                .initAppOpenAdAppLovin(new AppOpenAdAppLovin())
                .initAppOpenAdWortise(new AppOpenAdWortise())
                .initAppOpenAdPangle(new AppOpenAdPangle())
                .initAppOpenAdYandex(new AppOpenAdYandex())
                .setAdStatus(AD_STATUS_ON)
                .setAdNetwork(mainAds)
                .setBackupAdNetwork(adsPref.getBackupAds())
                .setPlacementOnStart(false)
                .setPlacementOnResume(adsPref.getAppOpenOnResume())
                .setAdMobAppOpenId(adsPref.getAdMobAppOpenAdId())
                .setAdManagerAppOpenId(adsPref.getAdManagerAppOpenAdId())
                .setApplovinAppOpenId(adsPref.getAppLovinAppOpenAdUnitId());
        if (!openAdsLifecycleRegistered) {
            registerActivityLifecycleCallbacks(activityLifecycleCallbacks);
            ProcessLifecycleOwner.get().getLifecycle().addObserver(processLifecycleObserver);
            openAdsLifecycleRegistered = true;
        }
    }

    public void markResumeAppOpenReady() {
        resumeAppOpenReady = true;
    }

    public void recordAppOpenShown() {
        lastAppOpenShownAt = SystemClock.elapsedRealtime();
    }

    public void preloadAppOpenAd(@Nullable Activity activity) {
        Activity host = activity != null ? activity : currentActivity;
        Context context = host != null ? host : getApplicationContext();
        adsPref = new AdsPref(this);
        if (!adsPref.getAdStatus()) {
            return;
        }
        String mainAds = adsPref.getMainAds();
        if ("admob".equals(mainAds) || "fan_bidding_admob".equals(mainAds)) {
            String unitId = adsPref.getAdMobAppOpenAdId();
            if (!TextUtils.isEmpty(unitId) && !"0".equals(unitId) && appOpenAdMob != null) {
                appOpenAdMob.loadAd(context, unitId);
            }
            return;
        }
        if ("google_ad_manager".equals(mainAds) || "fan_bidding_ad_manager".equals(mainAds)) {
            String unitId = adsPref.getAdManagerAppOpenAdId();
            if (!TextUtils.isEmpty(unitId) && !"0".equals(unitId) && appOpenAdManager != null) {
                appOpenAdManager.loadAd(context, unitId);
            }
            return;
        }
        if (host == null || host.isFinishing()) {
            return;
        }
        AdsManager adsManager = new AdsManager(host);
        adsManager.loadAppOpenAd(true, false, () -> {
        });
    }

    private void scheduleResumeAppOpenIfNeeded(@NonNull Activity activity) {
        if (!pendingResumeAppOpen) {
            return;
        }
        pendingResumeAppOpen = false;
        mainHandler.post(() -> showResumeAppOpenIfNeeded(activity));
    }

    private void showResumeAppOpenIfNeeded(@NonNull Activity activity) {
        try {
            adsPref = new AdsPref(this);
            if (!resumeAppOpenReady || !adsPref.getAdStatus() || !adsPref.getAppOpenOnResume()) {
                return;
            }
            if (activity.isFinishing() || activity.isDestroyed()
                    || activity instanceof ActivitySplash) {
                return;
            }
            long now = SystemClock.elapsedRealtime();
            if (now - lastAppOpenShownAt < MIN_APP_OPEN_GAP_MS) {
                return;
            }
            OnShowAdCompleteListener onComplete = () -> {
                recordAppOpenShown();
                AppOpenAd.isAppOpenAdLoaded = false;
                preloadAppOpenAd(activity);
            };
            String mainAds = adsPref.getMainAds();
            if (("admob".equals(mainAds) || "fan_bidding_admob".equals(mainAds))
                    && appOpenAdMob != null) {
                String unitId = adsPref.getAdMobAppOpenAdId();
                if (!TextUtils.isEmpty(unitId) && !"0".equals(unitId)) {
                    appOpenAdMob.showAdIfAvailable(activity, unitId, onComplete);
                    return;
                }
            }
            if (("google_ad_manager".equals(mainAds) || "fan_bidding_ad_manager".equals(mainAds))
                    && appOpenAdManager != null) {
                String unitId = adsPref.getAdManagerAppOpenAdId();
                if (!TextUtils.isEmpty(unitId) && !"0".equals(unitId)) {
                    appOpenAdManager.showAdIfAvailable(activity, unitId, onComplete);
                    return;
                }
            }
            AdsManager adsManager = new AdsManager(activity);
            adsManager.loadAppOpenAd(true, true, onComplete);
        } catch (Exception e) {
            Log.e(TAG, "showResumeAppOpenIfNeeded failed", e);
        }
    }

    private final DefaultLifecycleObserver processLifecycleObserver = new DefaultLifecycleObserver() {
        @Override
        public void onStart(@NonNull LifecycleOwner owner) {
            if (wasInBackground) {
                pendingResumeAppOpen = true;
            }
            wasInBackground = false;
        }

        @Override
        public void onStop(@NonNull LifecycleOwner owner) {
            wasInBackground = true;
            preloadAppOpenAd(currentActivity);
        }
    };

    private final ActivityLifecycleCallbacks activityLifecycleCallbacks = new ActivityLifecycleCallbacks() {
        @Override
        public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        }

        @Override
        public void onActivityStarted(@NonNull Activity activity) {
            currentActivity = activity;
        }

        @Override
        public void onActivityResumed(@NonNull Activity activity) {
            currentActivity = activity;
            scheduleResumeAppOpenIfNeeded(activity);
        }

        @Override
        public void onActivityPaused(@NonNull Activity activity) {
        }

        @Override
        public void onActivityStopped(@NonNull Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(@NonNull Activity activity) {
            if (currentActivity == activity) {
                currentActivity = null;
            }
        }
    };

    public void showAdIfAvailable(@NonNull Activity activity, @NonNull OnShowAdCompleteListener onShowAdCompleteListener) {
        if (!adsPref.getAppOpenOnStart()) {
            onShowAdCompleteListener.onShowAdComplete();
            return;
        }
        AdsManager adsManager = new AdsManager(activity);
        adsManager.loadAppOpenAd(true, true, () -> {
            recordAppOpenShown();
            AppOpenAd.isAppOpenAdLoaded = false;
            onShowAdCompleteListener.onShowAdComplete();
        });
    }
}
