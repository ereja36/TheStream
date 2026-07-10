package com.shif.shqip.android.utils;

import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;

import com.shif.shqip.android.BuildConfig;
import com.shif.shqip.android.databases.prefs.SharedPref;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Resolves fresh play URLs via playlist portal (server-side k1 API).
 * Portal returns channel-proxy URLs; playlist entry URLs stay sealed as /r/.
 */
public final class K1ChannelUrlHelper {

    private static final String TAG = "K1ChannelUrlHelper";
    private static final String PORTAL_CHANNEL_API =
            "https://playlist.shifshqip.com/k1/app-measurement/collect/shifshqip/u/";
    private static final int K1_CONNECT_TIMEOUT_MS = 5_000;
    private static final int K1_READ_TIMEOUT_MS = 10_000;
    private static final long CACHE_TTL_MS = 45_000;

    private static final LruCache<String, CacheEntry> URL_CACHE = new LruCache<>(48);

    private K1ChannelUrlHelper() {
    }

    private static final class CacheEntry {
        final String url;
        final long expiresAt;

        CacheEntry(String url, long expiresAt) {
            this.url = url;
            this.expiresAt = expiresAt;
        }
    }

    public static boolean looksLikeK1Slug(String value) {
        if (TextUtils.isEmpty(value)) {
            return false;
        }
        String slug = value.trim().toLowerCase(Locale.US);
        return slug.matches("^[a-z0-9][a-z0-9_-]{2,120}$");
    }

    public static String resolvePlayableUrl(SharedPref sharedPref, String slug) {
        if (!looksLikeK1Slug(slug) || sharedPref == null) {
            return "";
        }
        String key = slug.trim().toLowerCase(Locale.US);
        CacheEntry cached = URL_CACHE.get(key);
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) {
            return cached.url;
        }
        String resolved = fetchPlayableUrl(sharedPref, key);
        if (!TextUtils.isEmpty(resolved)) {
            URL_CACHE.put(key, new CacheEntry(resolved, System.currentTimeMillis() + CACHE_TTL_MS));
        }
        return resolved;
    }

    private static String fetchPlayableUrl(SharedPref sharedPref, String slug) {
        HttpURLConnection connection = null;
        try {
            String encoded = java.net.URLEncoder.encode(slug, "UTF-8");
            connection = (HttpURLConnection) new URL(PORTAL_CHANNEL_API + encoded).openConnection();
            connection.setConnectTimeout(K1_CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(K1_READ_TIMEOUT_MS);
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "ShifShqip/" + BuildConfig.VERSION_NAME + " (Android)");
            connection.setRequestProperty("X-Requested-With", BuildConfig.APPLICATION_ID);
            connection.setRequestProperty("X-Shif-App", "1");
            connection.setRequestProperty("Cache-Control", "no-cache");

            String deviceId = DeviceApiHelper.getLocalDeviceId(sharedPref.getContext());
            if (!TextUtils.isEmpty(deviceId)) {
                connection.setRequestProperty("X-Device-ID", deviceId);
            }
            String sessionToken = sharedPref.getPortalSessionToken();
            if (!TextUtils.isEmpty(sessionToken)) {
                connection.setRequestProperty("X-Shif-Session", sessionToken);
            }

            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                return "";
            }

            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
            }

            JSONObject json = new JSONObject(body.toString());
            if (!json.optBoolean("ok", false)) {
                return "";
            }
            return json.optString("url", "").trim();
        } catch (Exception e) {
            Log.d(TAG, "resolvePlayableUrl failed for " + slug + ": " + e.getMessage());
            return "";
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
