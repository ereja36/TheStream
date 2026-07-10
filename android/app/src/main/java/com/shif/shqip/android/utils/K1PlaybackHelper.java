package com.shif.shqip.android.utils;

import android.text.TextUtils;

import java.util.Locale;

/**
 * K1 URL helpers — playback goes directly to CDN, never through channel-proxy.
 */
public final class K1PlaybackHelper {

    private static final String K1_CHANNEL = "/k1/channel/";
    private static final String K1_CHANNEL_PROXY = "/k1/channel-proxy/";
    private static final String K1_PROXY = "/k1/proxy/";

    private K1PlaybackHelper() {
    }

    /** Pass-through — do not rewrite to channel-proxy. */
    public static String optimizePlaybackUrl(String url) {
        return url;
    }

    public static boolean isK1ProxyUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        String lower = url.toLowerCase(Locale.US);
        return lower.contains("shifshqip.com/k1/")
                && (lower.contains(K1_CHANNEL_PROXY)
                || lower.contains(K1_CHANNEL)
                || lower.contains(K1_PROXY));
    }

    public static boolean isK1LiveStream(String url) {
        return isK1ProxyUrl(url) || PortalResolveHelper.isDirectCdnUrl(url);
    }
}
