package com.shif.shqip.android.utils;

import android.text.TextUtils;

/**
 * Playlist logos as-is: absolute tvg-logo URLs pass through;
 * relative filenames use admin /upload/ path. No CDN name overrides.
 */
public final class ChannelLogoHelper {

    private ChannelLogoHelper() {
    }

    public static String resolve(String image, String baseUrl) {
        return resolve(image, baseUrl, "");
    }

    public static String resolve(String image, String baseUrl, String channelName) {
        if (TextUtils.isEmpty(image)) {
            return "";
        }
        String value = image.trim();
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        if (value.startsWith("//")) {
            return "https:" + value;
        }
        if (TextUtils.isEmpty(baseUrl)) {
            return value;
        }
        if (value.startsWith("/")) {
            // Absolute path on admin host
            String root = baseUrl;
            int scheme = root.indexOf("://");
            if (scheme > 0) {
                int slash = root.indexOf('/', scheme + 3);
                root = slash > 0 ? root.substring(0, slash) : root;
            }
            return root + value;
        }
        return baseUrl + "/upload/" + value.replace(" ", "%20");
    }
}
