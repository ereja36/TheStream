package com.shif.shqip.android.utils;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.shif.shqip.android.R;
import com.shif.shqip.android.databases.prefs.AdsPref;

/**
 * Mounts a native ad slot with its own AdsManager so multiple slots load in parallel.
 */
public final class NativeAdSlotHelper {

    private static final String TAG = "NativeAdSlotHelper";

    private NativeAdSlotHelper() {
    }

    public static AdsManager mount(AppCompatActivity activity, LinearLayout host) {
        if (activity == null || host == null) {
            return null;
        }
        AdsPref adsPref = new AdsPref(activity);
        if (!adsPref.getAdStatus() || !Constant.NATIVE_AD_CHANNEL_LIST) {
            return null;
        }
        try {
            Tools.setNativeAdStyle(activity, host, Constant.NATIVE_AD_STYLE_CHANNEL_LIST);
            AdsManager slotAds = new AdsManager(activity);
            slotAds.loadNativeAd(Constant.NATIVE_AD_CHANNEL_LIST, Constant.NATIVE_AD_STYLE_CHANNEL_LIST);
            slotAds.loadNativeAdView(host, Constant.NATIVE_AD_CHANNEL_LIST, Constant.NATIVE_AD_STYLE_CHANNEL_LIST);
            return slotAds;
        } catch (Exception e) {
            Log.e(TAG, "mount failed", e);
            return null;
        }
    }

    public static void mountEarly(AppCompatActivity activity, int hostId) {
        if (activity == null) {
            return;
        }
        View host = activity.findViewById(hostId);
        if (host instanceof LinearLayout) {
            mount(activity, (LinearLayout) host);
        }
    }
}
