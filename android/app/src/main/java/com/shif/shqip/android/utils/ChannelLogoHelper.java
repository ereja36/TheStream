package com.shif.shqip.android.utils;

import android.text.TextUtils;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves channel logo URLs for Glide. Absolute CDN URLs and known relative
 * Prod-API filenames map to https://shifshqip.com/k1/tv-logos/...
 */
public final class ChannelLogoHelper {

    private static final String CDN = "https://shifshqip.com/k1/tv-logos/countries/albania/";
    private static final Map<String, String> RELATIVE_ALIASES = new HashMap<>();

    static {
        RELATIVE_ALIASES.put("rtk1.png", CDN + "rtk1-al.png");
        RELATIVE_ALIASES.put("rtk2.png", CDN + "rtk2-al.png");
        RELATIVE_ALIASES.put("rtk3.png", CDN + "rtk3-al.png");
        RELATIVE_ALIASES.put("rtk4.png", CDN + "rtk4-al.png");
        RELATIVE_ALIASES.put("21rtv.png", CDN + "rtv-21-al.png");
        RELATIVE_ALIASES.put("21mk.png", CDN + "rtv-21-mk-al.png");
        RELATIVE_ALIASES.put("tsport1.png", CDN + "tring-sport-1-al.png");
        RELATIVE_ALIASES.put("tsport2.png", CDN + "tring-sport-2-al.png");
        RELATIVE_ALIASES.put("tsport3.png", CDN + "tring-sport-3-al.png");
        RELATIVE_ALIASES.put("tsport4.png", CDN + "tring-sport-4-al.png");
    }

    private ChannelLogoHelper() {
    }

    public static String resolve(String image, String baseUrl) {
        if (TextUtils.isEmpty(image)) {
            return "";
        }
        String value = image.trim();
        if (value.startsWith("http://") || value.startsWith("https://")) {
            // Fix wrong Austria K-TV hit for Kohavision
            if (value.contains("/austria/k-tv") || value.contains("/austria/k-tv-at")) {
                return CDN + "kohavision-al.png";
            }
            return value;
        }
        if (value.startsWith("/")) {
            return "https://shifshqip.com" + value;
        }
        String alias = RELATIVE_ALIASES.get(value.toLowerCase(Locale.US));
        if (alias != null) {
            return alias;
        }
        if (TextUtils.isEmpty(baseUrl)) {
            return value;
        }
        return baseUrl + "/upload/" + value.replace(" ", "%20");
    }
}
