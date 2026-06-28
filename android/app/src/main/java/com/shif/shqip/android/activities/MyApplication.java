package com.shif.shqip.android.activities;

import static com.solodroidx.ads.util.Constant.AD_STATUS_ON;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.multidex.MultiDex;

import com.shif.shqip.android.Config;
import com.shif.shqip.android.R;
import com.shif.shqip.android.databases.prefs.AdsPref;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.utils.Constant;
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
    private static MyApplication mInstance;
    FirebaseAnalytics mFirebaseAnalytics;
    AdsPref adsPref;
    SharedPref sharedPref;
    AppOpenAd appOpenAd;

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
        adsPref = new AdsPref(this);
        appOpenAd = new AppOpenAd();
        String mainAds = adsPref.getMainAds();
        boolean hasValidMainAds = !TextUtils.isEmpty(mainAds) && !"0".equals(mainAds);
        if (adsPref.getAdStatus() && hasValidMainAds && !Config.FORCE_TO_SHOW_APP_OPEN_AD_ON_START) {
            registerActivityLifecycleCallbacks(activityLifecycleCallbacks);
            ProcessLifecycleOwner.get().getLifecycle().addObserver(lifecycleObserver);
            appOpenAd.initAppOpenAdMob(new AppOpenAdMob())
                    .initAppOpenAdManager(new AppOpenAdManager())
                    .initAppOpenAdAppLovin(new AppOpenAdAppLovin())
                    .initAppOpenAdWortise(new AppOpenAdWortise())
                    .initAppOpenAdPangle(new AppOpenAdPangle())
                    .initAppOpenAdYandex(new AppOpenAdYandex())
                    .setAdStatus(AD_STATUS_ON)
                    .setAdNetwork(mainAds)
                    .setBackupAdNetwork(adsPref.getBackupAds())
                    .setPlacementOnStart(Constant.APP_OPEN_AD_ON_START)
                    .setPlacementOnResume(Constant.APP_OPEN_AD_ON_RESUME)
                    .setAdMobAppOpenId(adsPref.getAdMobAppOpenAdId())
                    .setAdManagerAppOpenId(adsPref.getAdManagerAppOpenAdId())
                    .setApplovinAppOpenId(adsPref.getAppLovinAppOpenAdUnitId());
        }
    }

    LifecycleObserver lifecycleObserver = new DefaultLifecycleObserver() {
        @Override
        public void onStart(@NonNull LifecycleOwner owner) {
            DefaultLifecycleObserver.super.onStart(owner);
            if (AppOpenAd.isAppOpenAdLoaded) {
                if (appOpenAd != null) {
                    appOpenAd.setOnStartLifecycleObserver();
                }
            }
        }
    };

    ActivityLifecycleCallbacks activityLifecycleCallbacks = new ActivityLifecycleCallbacks() {
        @Override
        public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        }

        @Override
        public void onActivityStarted(@NonNull Activity activity) {
            if (appOpenAd != null) {
                appOpenAd.setOnStartActivityLifecycleCallbacks(activity);
            }
        }

        @Override
        public void onActivityResumed(@NonNull Activity activity) {
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
        }
    };

    public void showAdIfAvailable(@NonNull Activity activity, @NonNull OnShowAdCompleteListener onShowAdCompleteListener) {
        if (appOpenAd != null) {
            appOpenAd.showAdIfAvailable(activity, onShowAdCompleteListener);
        }
    }


}
