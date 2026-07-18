package com.shif.shqip.android.utils;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;

import com.shif.shqip.android.R;
import com.shif.shqip.android.databases.prefs.AdsPref;

import java.util.ArrayList;
import java.util.WeakHashMap;

/**
 * Mounts native ad slots. For AdMob uses {@link DirectAdMobNativeHelper} so fills
 * refresh freely without Solodroid dual-load races.
 */
public final class NativeAdSlotHelper {

    private static final String TAG = "NativeAdSlotHelper";
    private static final int[] HOME_NATIVE_HOST_IDS = {
            R.id.native_ad_home
    };
    private static final WeakHashMap<LinearLayout, DirectAdMobNativeHelper> DIRECT_BY_HOST = new WeakHashMap<>();

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

        // Prefer direct AdMob path — free refresh, no Solodroid fight.
        if ("admob".equals(AdPlacementHelper.resolveAdNetwork(adsPref))
                && AdPlacementHelper.isValidAdUnit(adsPref.getAdMobNativeId())) {
            DirectAdMobNativeHelper existing = DIRECT_BY_HOST.get(host);
            if (existing != null && existing.isShowing()) {
                return null;
            }
            if (existing != null) {
                existing.destroy();
            }
            DirectAdMobNativeHelper helper = new DirectAdMobNativeHelper(activity, host);
            DIRECT_BY_HOST.put(host, helper);
            host.setTag("native_ad_slot");
            helper.load();
            return null;
        }

        // Non-AdMob fallback via Solodroid NativeAdView only (not dual loadNativeAd).
        if (isSolodroidFilled(host)) {
            return null;
        }
        try {
            host.setVisibility(View.VISIBLE);
            host.removeAllViews();
            String style = Tools.nativeAdStyleFormatter(Constant.NATIVE_AD_STYLE_CHANNEL_LIST);
            Tools.setNativeAdStyle(activity, host, style);
            AdsManager slotAds = new AdsManager(activity);
            slotAds.initializeAd();
            slotAds.loadNativeAdView(host, Constant.NATIVE_AD_CHANNEL_LIST, style);
            host.setTag("native_ad_slot");
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
                ensureHost(appCompatActivity, (LinearLayout) host);
            }
        }
        View root = activity.findViewById(android.R.id.content);
        remountTaggedSlots(appCompatActivity, root);
    }

    /** Call before tearing down section trees so helpers stop requesting. */
    public static void destroyHostsIn(ViewGroup parent) {
        if (parent == null) {
            return;
        }
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof LinearLayout && "native_ad_slot".equals(child.getTag())) {
                DirectAdMobNativeHelper helper = DIRECT_BY_HOST.remove(child);
                if (helper != null) {
                    helper.destroy();
                }
            }
            if (child instanceof ViewGroup) {
                destroyHostsIn((ViewGroup) child);
            }
        }
    }

    public static void destroyAll() {
        for (DirectAdMobNativeHelper helper : new ArrayList<>(DIRECT_BY_HOST.values())) {
            try {
                helper.destroy();
            } catch (Exception ignored) {
            }
        }
        DIRECT_BY_HOST.clear();
    }

    private static void ensureHost(AppCompatActivity activity, LinearLayout host) {
        DirectAdMobNativeHelper helper = DIRECT_BY_HOST.get(host);
        if (helper != null && helper.isShowing()) {
            return;
        }
        if (isSolodroidFilled(host)) {
            return;
        }
        mount(activity, host);
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
                ensureHost(activity, (LinearLayout) child);
            }
            remountTaggedSlots(activity, child);
        }
    }

    private static boolean isSolodroidFilled(LinearLayout host) {
        if (host.getChildCount() == 0 || host.getVisibility() != View.VISIBLE) {
            return false;
        }
        View container = host.findViewById(com.solodroidx.ads.R.id.admob_native_ad_container);
        if (container == null) {
            container = host.findViewById(com.solodroidx.ads.R.id.native_ad_view_container);
        }
        return container != null && container.getVisibility() == View.VISIBLE;
    }
}
