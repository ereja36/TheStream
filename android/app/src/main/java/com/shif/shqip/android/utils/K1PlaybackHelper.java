package com.shif.shqip.android.utils;

import android.text.TextUtils;

import java.util.Locale;

/**
 * Aligns resolved upstream URLs with the k1 web player (channel-proxy path).
 */
public final class K1PlaybackHelper {

    private static final String K1_CHANNEL = "/k1/channel/";
    private static final String K1_CHANNEL_PROXY = "/k1/channel-proxy/";

    private K1PlaybackHelper() {
    }

    public static String optimizePlaybackUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return url;
        }
        if (url.contains(K1_CHANNEL) && !url.contains(K1_CHANNEL_PROXY)) {
            return url.replace(K1_CHANNEL, K1_CHANNEL_PROXY);
        }
        return url;
    }

    public static boolean isK1LiveStream(String url) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        String lower = url.toLowerCase(Locale.US);
        return lower.contains("shifshqip.com/k1/")
                && (lower.contains(K1_CHANNEL_PROXY)
                || lower.contains(K1_CHANNEL)
                || lower.contains("/k1/proxy/"));
    }
}
