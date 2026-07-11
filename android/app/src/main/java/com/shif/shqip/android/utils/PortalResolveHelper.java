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
 * One-shot portal resolve: sealed playlist URLs → direct CDN stream URL.
 */
public final class PortalResolveHelper {

    private static final String TAG = "PortalResolveHelper";
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;
    private static final long CACHE_TTL_MS = 20_000;

    private static final LruCache<String, CacheEntry> URL_CACHE = new LruCache<>(48);

    private PortalResolveHelper() {
    }

    private static final class CacheEntry {
        final String url;
        final long expiresAt;

        CacheEntry(String url, long expiresAt) {
            this.url = url;
            this.expiresAt = expiresAt;
        }
    }

    public static String resolveToDirectUrl(SharedPref sharedPref, String sourceUrl) {
        if (TextUtils.isEmpty(sourceUrl) || sharedPref == null) {
            return "";
        }
        String trimmed = sourceUrl.trim();
        if (isDirectCdnUrl(trimmed)) {
            return trimmed;
        }
        if (PlaylistPortalHelper.isPortalResolveUrl(trimmed)) {
            return resolvePortalEntry(sharedPref, trimmed);
        }
        if (PlaylistPortalHelper.isUpstreamStreamUrl(trimmed)
                || K1PlaybackHelper.isK1ProxyUrl(trimmed)) {
            return followRedirectsToCdn(sharedPref, trimmed);
        }
        return "";
    }

    private static String resolvePortalEntry(SharedPref sharedPref, String portalUrl) {
        String cacheKey = portalUrl.toLowerCase(Locale.US);
        CacheEntry cached = URL_CACHE.get(cacheKey);
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) {
            return cached.url;
        }
        String resolved = fetchDirectUrl(sharedPref, portalUrl);
        if (isPlayableResolvedUrl(resolved)) {
            URL_CACHE.put(cacheKey, new CacheEntry(resolved, System.currentTimeMillis() + CACHE_TTL_MS));
            return resolved;
        }
        return "";
    }

    /**
     * A resolved URL is playable when it is a direct CDN stream, or a sealed
     * portal manifest URL (api_/sport_/custom_tv channels streamed through the
     * portal with segments 302-ing straight to the origin).
     */
    private static boolean isPlayableResolvedUrl(String url) {
        return isDirectCdnUrl(url) || PlaylistPortalHelper.isPortalResolveUrl(url);
    }

    private static String followRedirectsToCdn(SharedPref sharedPref, String startUrl) {
        String cacheKey = "cdn:" + startUrl.toLowerCase(Locale.US);
        CacheEntry cached = URL_CACHE.get(cacheKey);
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) {
            return cached.url;
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(startUrl).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "*/*");
            connection.setRequestProperty("User-Agent", PlaylistPortalHelper.UPSTREAM_STREAM_UA);
            connection.setRequestProperty("Referer", PlaylistPortalHelper.UPSTREAM_STREAM_REFERER);
            connection.setRequestProperty("X-Shif-App", "1");
            connection.setRequestProperty("X-Requested-With", BuildConfig.APPLICATION_ID);

            String deviceId = DeviceApiHelper.getLocalDeviceId(sharedPref.getContext());
            if (!TextUtils.isEmpty(deviceId)) {
                connection.setRequestProperty("X-Device-ID", deviceId);
            }
            String sessionToken = sharedPref.getPortalSessionToken();
            if (!TextUtils.isEmpty(sessionToken)) {
                connection.setRequestProperty("X-Shif-Session", sessionToken);
            }

            int code = connection.getResponseCode();
            String finalUrl = connection.getURL().toString();
            if (code >= 200 && code < 400 && isDirectCdnUrl(finalUrl)) {
                URL_CACHE.put(cacheKey, new CacheEntry(finalUrl, System.currentTimeMillis() + CACHE_TTL_MS));
                return finalUrl;
            }
            if (code >= 200 && code < 300) {
                String body = readBody(connection);
                if (body.contains("#EXTM3U")) {
                    for (String line : body.split("\n")) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        if (line.startsWith("http") && isDirectCdnUrl(line)) {
                            URL_CACHE.put(cacheKey, new CacheEntry(line, System.currentTimeMillis() + CACHE_TTL_MS));
                            return line;
                        }
                    }
                }
            }
            return "";
        } catch (Exception e) {
            Log.d(TAG, "followRedirectsToCdn failed: " + e.getMessage());
            return "";
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readBody(HttpURLConnection connection) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream()))) {
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line).append('\n');
            }
            return body.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String fetchDirectUrl(SharedPref sharedPref, String requestUrl) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(requestUrl).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "ShifShqip/" + BuildConfig.VERSION_NAME + " (Android)");
            connection.setRequestProperty("X-Requested-With", BuildConfig.APPLICATION_ID);
            connection.setRequestProperty("X-Shif-App", "1");
            connection.setRequestProperty("X-Shif-Resolve", "direct");

            String deviceId = DeviceApiHelper.getLocalDeviceId(sharedPref.getContext());
            if (!TextUtils.isEmpty(deviceId)) {
                connection.setRequestProperty("X-Device-ID", deviceId);
            }
            connection.setRequestProperty(
                    "X-Shif-Session",
                    TextUtils.isEmpty(sharedPref.getPortalSessionToken()) ? "" : sharedPref.getPortalSessionToken()
            );
            for (java.util.Map.Entry<String, String> entry
                    : PlaylistPortalHelper.buildPortalHeaders(requestUrl, "GET", deviceId, sharedPref).entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }

            int code = connection.getResponseCode();
            if (code >= 300 && code < 400) {
                String location = connection.getHeaderField("Location");
                if (!TextUtils.isEmpty(location) && isDirectCdnUrl(location)) {
                    return location;
                }
            }
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

            String trimmed = body.toString().trim();
            if (!trimmed.startsWith("{")) {
                return "";
            }
            JSONObject json = new JSONObject(trimmed);
            if (!json.optBoolean("ok", false)) {
                return "";
            }
            String mode = json.optString("mode", "").trim();
            String url = json.optString("url", "").trim();
            if ("manifest".equalsIgnoreCase(mode)) {
                // Portal streams the manifest through this sealed URL; play it directly.
                return TextUtils.isEmpty(url) ? requestUrl : url;
            }
            if (isDirectCdnUrl(url)) {
                return url;
            }
            if (!TextUtils.isEmpty(url) && !url.equalsIgnoreCase(requestUrl)) {
                return resolveToDirectUrl(sharedPref, url);
            }
            return "";
        } catch (Exception e) {
            Log.d(TAG, "fetchDirectUrl failed: " + e.getMessage());
            return "";
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public static boolean isDirectCdnUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        String lower = url.toLowerCase(Locale.US);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")
                && !lower.startsWith("rtsp://") && !lower.startsWith("rtmp://")) {
            return false;
        }
        if (lower.contains("playlist.shifshqip.com")) {
            return false;
        }
        if (lower.contains("shifshqip.com")) {
            if (lower.contains("/k1/channel-proxy/")
                    || lower.contains("/k1/proxy/")
                    || lower.contains("/k1/channel/")
                    || lower.contains("/k1/playlist/")
                    || lower.contains("/app-measurement/collect/")) {
                return false;
            }
        }
        if (lower.contains("/r/") && lower.contains("/app-measurement/collect/")) {
            return false;
        }
        return lower.contains(".m3u8")
                || lower.contains(".mpd")
                || lower.contains(".m3u")
                || lower.startsWith("rtsp://")
                || lower.startsWith("rtmp://")
                || lower.contains("/live/")
                || lower.contains("/hls/");
    }
}
