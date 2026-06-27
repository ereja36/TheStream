package com.shif.shqip.android.utils;

import android.text.TextUtils;

import android.util.Base64;

import com.shif.shqip.android.BuildConfig;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.models.PlaylistGuardRule;

import org.json.JSONObject;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class PlaylistPortalHelper {

    private static final String PORTAL_HOST = "playlist.shifshqip.com";

    public static final String PUBLIC_PORTAL_URL = "https://playlist.shifshqip.com/";

    private PlaylistPortalHelper() {
    }

    public static boolean isPortalUrl(String urlString) {
        if (TextUtils.isEmpty(urlString)) {
            return false;
        }
        return hostMatches(urlString, PORTAL_HOST);
    }

    public static boolean isPortalResolveUrl(String urlString) {
        if (!isPortalUrl(urlString)) {
            return false;
        }
        String lower = urlString.toLowerCase(Locale.US);
        return lower.contains("/app-measurement/collect/") && lower.contains("/r/");
    }

    public static boolean isPortalStreamUrl(String urlString, SharedPref sharedPref) {
        return false;
    }

    public static String resolvePortalUrl(String urlString, SharedPref sharedPref) {
        if (!isPortalUrl(urlString)) {
            return urlString == null ? "" : urlString.trim();
        }
        return PUBLIC_PORTAL_URL;
    }

    public static PlaylistGuardRule buildPortalStreamRule(SharedPref sharedPref) {
        return null;
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
        String sessionToken = sharedPref.getPortalSessionToken();
        if (TextUtils.isEmpty(sessionToken) || TextUtils.isEmpty(deviceId)) {
            return;
        }
        connection.setRequestProperty("X-Shif-Session", sessionToken);
        connection.setRequestProperty("X-Device-ID", deviceId);
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
        String sessionToken = sharedPref.getPortalSessionToken();
        if (TextUtils.isEmpty(sessionToken)) {
            return headers;
        }
        headers.put("X-Shif-Session", sessionToken);
        headers.put("X-Device-ID", deviceId);
        return headers;
    }

    public static String derivePlaylistKey(String deviceId, String sessionToken) {
        if (TextUtils.isEmpty(deviceId) || TextUtils.isEmpty(sessionToken)) {
            return "";
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(sessionToken.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(deviceId.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                builder.append(String.format(Locale.US, "%02x", value));
            }
            return builder.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    public static String decryptPayload(String encoded, String derivedAccessKey) {
        if (TextUtils.isEmpty(encoded) || TextUtils.isEmpty(derivedAccessKey)) {
            return "";
        }
        try {
            byte[] raw = Base64.decode(encoded.trim(), Base64.DEFAULT);
            if (raw == null || raw.length < 17) {
                return "";
            }
            byte[] key = MessageDigest.getInstance("SHA-256")
                    .digest(derivedAccessKey.getBytes(StandardCharsets.UTF_8));
            byte[] iv = new byte[16];
            System.arraycopy(raw, 0, iv, 0, 16);
            byte[] encrypted = new byte[raw.length - 16];
            System.arraycopy(raw, 16, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            byte[] plain = cipher.doFinal(encrypted);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    public static void applyEncryptedStreamConfig(SharedPref sharedPref, String cfg, String derivedAccessKey) {
        if (sharedPref != null) {
            sharedPref.clearPortalStreamConfig();
        }
    }

    private static boolean hostMatches(String urlString, String expectedHost) {
        try {
            URL url = new URL(urlString);
            String host = url.getHost();
            if (TextUtils.isEmpty(host) || TextUtils.isEmpty(expectedHost)) {
                return false;
            }
            return host.equalsIgnoreCase(expectedHost);
        } catch (Exception ignored) {
            String normalized = PlaylistGuardHelper.normalizeMatchKey(urlString);
            return !TextUtils.isEmpty(normalized) && normalized.startsWith(expectedHost.toLowerCase(Locale.US));
        }
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
