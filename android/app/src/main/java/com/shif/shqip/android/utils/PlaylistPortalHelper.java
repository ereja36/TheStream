package com.shif.shqip.android.utils;

import android.text.TextUtils;

import com.shif.shqip.android.databases.prefs.SharedPref;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class PlaylistPortalHelper {

    private static final String PORTAL_HOST = "playlist.shifshqip.com";

    private PlaylistPortalHelper() {
    }

    public static boolean isPortalUrl(String urlString) {
        if (TextUtils.isEmpty(urlString)) {
            return false;
        }
        return PlaylistGuardHelper.normalizeMatchKey(urlString).contains(PORTAL_HOST);
    }

    public static String resolvePortalUrl(String urlString, SharedPref sharedPref) {
        if (!isPortalUrl(urlString)) {
            return urlString == null ? "" : urlString.trim();
        }
        String trimmed = urlString.trim();
        String key = PlaylistGuardHelper.normalizeMatchKey(trimmed);
        if (key.contains("app-measurement/collect")) {
            return trimmed;
        }
        String publicUrl = sharedPref != null ? sharedPref.getPortalPublicUrl() : "";
        if (!TextUtils.isEmpty(publicUrl)) {
            return publicUrl;
        }
        return "https://" + PORTAL_HOST + "/k1/app-measurement/collect/shifshqip";
    }

    public static void applyPortalHeaders(
            java.net.HttpURLConnection connection,
            String urlString,
            String deviceId,
            SharedPref sharedPref
    ) {
        if (connection == null || sharedPref == null || !isPortalUrl(urlString)) {
            return;
        }
        String portalKey = sharedPref.getPortalAccessKey();
        if (TextUtils.isEmpty(portalKey) || TextUtils.isEmpty(deviceId)) {
            return;
        }
        long timestamp = System.currentTimeMillis() / 1000L;
        String path = extractPath(urlString);
        String signature = signRequest(portalKey, deviceId, timestamp, connection.getRequestMethod(), path);

        connection.setRequestProperty("X-Shif-Portal-Key", portalKey);
        connection.setRequestProperty("X-Shif-Timestamp", String.valueOf(timestamp));
        connection.setRequestProperty("X-Shif-Signature", signature);
    }

    public static java.util.Map<String, String> buildPortalHeaders(
            String requestUrl,
            String method,
            String deviceId,
            SharedPref sharedPref
    ) {
        java.util.Map<String, String> headers = new java.util.HashMap<>();
        if (!isPortalUrl(requestUrl) || sharedPref == null || TextUtils.isEmpty(deviceId)) {
            return headers;
        }
        String portalKey = sharedPref.getPortalAccessKey();
        if (TextUtils.isEmpty(portalKey)) {
            return headers;
        }
        long timestamp = System.currentTimeMillis() / 1000L;
        String path = extractPath(requestUrl);
        String requestMethod = TextUtils.isEmpty(method) ? "GET" : method.toUpperCase(Locale.US);
        headers.put("X-Shif-Portal-Key", portalKey);
        headers.put("X-Shif-Timestamp", String.valueOf(timestamp));
        headers.put("X-Shif-Signature", signRequest(portalKey, deviceId, timestamp, requestMethod, path));
        return headers;
    }

    private static String extractPath(String urlString) {
        try {
            URL url = new URL(urlString);
            String path = url.getPath();
            return TextUtils.isEmpty(path) ? "/" : path;
        } catch (Exception ignored) {
            return "/";
        }
    }

    static String signRequest(String portalKey, String deviceId, long timestamp, String method, String path) {
        String payload = deviceId + "|" + timestamp + "|" + method + "|" + path;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(portalKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                builder.append(String.format(Locale.US, "%02x", value));
            }
            return builder.toString();
        } catch (Exception ignored) {
            return "";
        }
    }
}
