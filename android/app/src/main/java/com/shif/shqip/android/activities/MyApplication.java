package com.shif.shqip.android.activities;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;

import androidx.multidex.MultiDex;

import com.shif.shqip.android.R;
import com.shif.shqip.android.databases.prefs.AdsPref;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.utils.Tools;
import com.shif.shqip.android.utils.AppOpenAdLifecycle;
import com.google.firebase.FirebaseApp;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.messaging.FirebaseMessaging;
import com.solodroid.push.sdk.provider.OneSignalPush;

public class MyApplication extends Application {

    public static final String TAG = "MyApplication";

    private static MyApplication mInstance;
    FirebaseAnalytics mFirebaseAnalytics;
    AdsPref adsPref;
    SharedPref sharedPref;
    private boolean resumeAppOpenReady;
    private AppOpenAdLifecycle appOpenAdLifecycle;

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
            initNotification();
        } catch (Exception e) {
            Log.e(TAG, "initNotification failed", e);
        }
        try {
            appOpenAdLifecycle = new AppOpenAdLifecycle(this);
            appOpenAdLifecycle.register();
        } catch (Exception e) {
            Log.e(TAG, "appOpenAdLifecycle failed", e);
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

    public void markResumeAppOpenReady() {
        resumeAppOpenReady = true;
    }

    public boolean isResumeAppOpenReady() {
        return resumeAppOpenReady;
    }
}
