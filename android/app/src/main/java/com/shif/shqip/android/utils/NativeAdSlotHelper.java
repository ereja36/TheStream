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
 * Remounts empty/hidden slots on resume/remote config without tearing down filled ones.
 */
public final class NativeAdSlotHelper {

    private static final String TAG = "NativeAdSlotHelper";
    private static final int[] HOME_NATIVE_HOST_IDS = {
            R.id.native_ad_home
    };

    private NativeAdSlotHelper() {
    }

    public static AdsManager mount(AppCompatActivity activity, LinearLayout host) {
        if (activity == null || host == null) {
            return null;
        }
        AdsPref adsPref = new AdsPref(activity);
        if (!adsPref.getAdStatus() || !Constant.NATIVE_AD_CHANNEL_LIST
                || !AdPlacementHelper.hasValidAdNetwork(adsPref)
                || !AdPlacementHelper.shouldInsertNativeAds(adsPref)) {
            host.setVisibility(View.GONE);
            return null;
        }
        // Keep an already-filled native; remounting was wiping successful fills.
        if (host.getChildCount() > 0 && host.getVisibility() == View.VISIBLE) {
            return null;
        }
        try {
            host.setVisibility(View.VISIBLE);
            Tools.setNativeAdStyle(activity, host, Constant.NATIVE_AD_STYLE_CHANNEL_LIST);
            AdsManager slotAds = new AdsManager(activity);
            slotAds.initializeAd();
            slotAds.loadNativeAd(Constant.NATIVE_AD_CHANNEL_LIST, Constant.NATIVE_AD_STYLE_CHANNEL_LIST);
            slotAds.loadNativeAdView(host, Constant.NATIVE_AD_CHANNEL_LIST, Constant.NATIVE_AD_STYLE_CHANNEL_LIST);
            return slotAds;
        } catch (Exception e) {
            Log.e(TAG, "mount failed", e);
            // Leave host VISIBLE so remountIfNeeded can retry; only hide if unit invalid.
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

    public static void remountHomeSlots(Activity activity) {
        if (!(activity instanceof AppCompatActivity) || activity.isFinishing()) {
            return;
        }
        AppCompatActivity appCompatActivity = (AppCompatActivity) activity;
        AdsPref adsPref = new AdsPref(activity);
        if (!AdPlacementHelper.shouldInsertNativeAds(adsPref)) {
            return;
        }
        for (int hostId : HOME_NATIVE_HOST_IDS) {
            View host = activity.findViewById(hostId);
            if (host instanceof LinearLayout) {
                LinearLayout linearHost = (LinearLayout) host;
                if (linearHost.getChildCount() == 0 || linearHost.getVisibility() != View.VISIBLE) {
                    mount(appCompatActivity, linearHost);
                }
            }
        }
        // V2 category section native hosts use dynamic layouts tagged as native_ad_slot.
        View root = activity.findViewById(android.R.id.content);
        remountTaggedSlots(appCompatActivity, root);
    }

    private static void remountTaggedSlots(AppCompatActivity activity, View root) {
        if (!(root instanceof android.view.ViewGroup)) {
            return;
        }
        android.view.ViewGroup group = (android.view.ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            Object tag = child.getTag();
            if (child instanceof LinearLayout && "native_ad_slot".equals(tag)) {
                LinearLayout host = (LinearLayout) child;
                if (host.getChildCount() == 0 || host.getVisibility() != View.VISIBLE) {
                    mount(activity, host);
                }
            }
            remountTaggedSlots(activity, child);
        }
    }
}
