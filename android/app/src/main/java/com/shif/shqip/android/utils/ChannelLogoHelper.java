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
        RELATIVE_ALIASES.put("ss1.png", CDN + "supersport-1-al.png");
        RELATIVE_ALIASES.put("ss2.png", CDN + "supersport-2-al.png");
        RELATIVE_ALIASES.put("ss3.png", CDN + "supersport-3-al.png");
        RELATIVE_ALIASES.put("ss4.png", CDN + "supersport-4-al.png");
        RELATIVE_ALIASES.put("supersport1.png", CDN + "supersport-1-al.png");
        RELATIVE_ALIASES.put("supersport2.png", CDN + "supersport-2-al.png");
        RELATIVE_ALIASES.put("supersport3.png", CDN + "supersport-3-al.png");
        RELATIVE_ALIASES.put("supersport4.png", CDN + "supersport-4-al.png");
        RELATIVE_ALIASES.put("arena1.png", CDN + "arena-1-premium-al.png");
        RELATIVE_ALIASES.put("arena2.png", CDN + "arena-2-premium-al.png");
        RELATIVE_ALIASES.put("arenapremium1.png", CDN + "arena-1-premium-al.png");
        RELATIVE_ALIASES.put("arenapremium2.png", CDN + "arena-2-premium-al.png");
    }

    /** Resolve by channel name when image path is empty or broken. */
    public static String resolveByChannelName(String channelName) {
        if (TextUtils.isEmpty(channelName)) {
            return "";
        }
        String key = channelName.trim().toLowerCase(Locale.US)
                .replace(" ", "")
                .replace("-", "")
                .replace("_", "");
        if (key.contains("supersportkosova1") || key.contains("supersportks1")) {
            return CDN + "supersport-kosova-1-al.png";
        }
        if (key.contains("supersportkosova2") || key.contains("supersportks2")) {
            return CDN + "supersport-kosova-2-al.png";
        }
        if (key.contains("supersportkosova3") || key.contains("supersportks3")) {
            return CDN + "supersport-kosova-3-al.png";
        }
        if (key.contains("supersport1") || key.equals("ss1")) return CDN + "supersport-1-al.png";
        if (key.contains("supersport2") || key.equals("ss2")) return CDN + "supersport-2-al.png";
        if (key.contains("supersport3") || key.equals("ss3")) return CDN + "supersport-3-al.png";
        if (key.contains("supersport4") || key.equals("ss4")) return CDN + "supersport-4-al.png";
        if (key.contains("supersport5") || key.equals("ss5")) return CDN + "supersport-5-al.png";
        if (key.contains("supersport6") || key.equals("ss6")) return CDN + "supersport-6-al.png";
        if (key.contains("supersport7") || key.equals("ss7")) return CDN + "supersport-7-al.png";
        if (key.contains("arenapremium1") || key.contains("arenapremiumhd1") || key.equals("arena1")) {
            return CDN + "arena-1-premium-al.png";
        }
        if (key.contains("arenapremium2") || key.contains("arenapremiumhd2") || key.equals("arena2")) {
            return CDN + "arena-2-premium-al.png";
        }
        if (key.contains("tringsport1") || key.contains("tringsports1")) return CDN + "tring-sport-1-al.png";
        if (key.contains("tringsport2") || key.contains("tringsports2")) return CDN + "tring-sport-2-al.png";
        if (key.contains("tringsport3") || key.contains("tringsports3")) return CDN + "tring-sport-3-al.png";
        if (key.contains("tringsport4") || key.contains("tringsports4")) return CDN + "tring-sport-4-al.png";
        return "";
    }

    private ChannelLogoHelper() {
    }

    public static String resolve(String image, String baseUrl) {
        return resolve(image, baseUrl, "");
    }

    public static String resolve(String image, String baseUrl, String channelName) {
        // Prefer known CDN logos by channel name — M3U tvg-logo is often a
        // broken composite / wrong host that Glide renders as overlapping glyphs.
        String byName = resolveByChannelName(channelName);
        if (!TextUtils.isEmpty(byName)) {
            return byName;
        }
        if (TextUtils.isEmpty(image)) {
            return "";
        }
        String value = image.trim();
        if (value.startsWith("http://") || value.startsWith("https://")) {
            // Fix wrong Austria K-TV hit for Kohavision
            if (value.contains("/austria/k-tv") || value.contains("/austria/k-tv-at")) {
                return CDN + "kohavision-al.png";
            }
            // Already pointing at our CDN — keep it
            if (value.contains("/k1/tv-logos/")) {
                return value;
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
