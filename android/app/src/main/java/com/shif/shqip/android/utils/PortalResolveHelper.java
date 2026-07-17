package com.shif.shqip.android.utils;

import android.text.TextUtils;

import com.shif.shqip.android.BuildConfig;
import com.shif.shqip.android.databases.prefs.SharedPref;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * One-shot portal resolve: sealed playlist URLs → direct CDN stream URL (+ headers).
 */
public final class PortalResolveHelper {

    private static final String TAG = "PortalResolveHelper";
    private static final int CONNECT_TIMEOUT_MS = 8_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final long CACHE_TTL_MS = 45_000;

    private static final android.util.LruCache<String, CacheEntry> URL_CACHE = new android.util.LruCache<>(64);

    private PortalResolveHelper() {
    }

    private static final class CacheEntry {
        final String url;
        final String userAgent;
        final JSONObject headers;
        final long expiresAt;

        CacheEntry(String url, String userAgent, JSONObject headers, long expiresAt) {
            this.url = url;
            this.userAgent = userAgent == null ? "" : userAgent;
            this.headers = headers;
            this.expiresAt = expiresAt;
        }
    }

    public static void invalidateCache() {
        URL_CACHE.evictAll();
    }

    public static String resolveToDirectUrl(SharedPref sharedPref, String sourceUrl) {
        if (TextUtils.isEmpty(sourceUrl) || sharedPref == null) {
            return "";
        }
        String trimmed = sourceUrl.trim();
        StreamHeaderSession.applySourceIfPresent(trimmed);
        if (isPlayableStreamUrl(trimmed)) {
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
            StreamHeaderSession.rememberResolved(cached.url, cached.userAgent, cached.headers);
            return cached.url;
        }
        Resolved resolved = fetchResolved(sharedPref, portalUrl);
        if (resolved == null || TextUtils.isEmpty(resolved.url)) {
            return "";
        }
        if (isPlayableStreamUrl(resolved.url)) {
            StreamHeaderSession.rememberResolved(resolved.url, resolved.userAgent, resolved.headers);
            URL_CACHE.put(cacheKey, new CacheEntry(
                    resolved.url,
                    resolved.userAgent,
                    resolved.headers,
                    System.currentTimeMillis() + CACHE_TTL_MS
            ));
            return resolved.url;
        }
        String nested = resolveToDirectUrl(sharedPref, resolved.url);
        if (!TextUtils.isEmpty(nested)) {
            return nested;
        }
        return "";
    }

    private static String followRedirectsToCdn(SharedPref sharedPref, String startUrl) {
        String cacheKey = "cdn:" + startUrl.toLowerCase(Locale.US);
        CacheEntry cached = URL_CACHE.get(cacheKey);
        if (cached != null && cached.expiresAt > System.currentTimeMillis()) {
            StreamHeaderSession.rememberResolved(cached.url, cached.userAgent, cached.headers);
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
            if (code >= 200 && code < 400 && isPlayableStreamUrl(finalUrl)) {
                Map<String, String> hopHeaders = new HashMap<>();
                hopHeaders.put("Referer", PlaylistPortalHelper.UPSTREAM_STREAM_REFERER);
                StreamHeaderSession.rememberResolved(
                        finalUrl, PlaylistPortalHelper.UPSTREAM_STREAM_UA, hopHeaders);
                URL_CACHE.put(cacheKey, new CacheEntry(
                        finalUrl,
                        PlaylistPortalHelper.UPSTREAM_STREAM_UA,
                        null,
                        System.currentTimeMillis() + CACHE_TTL_MS));
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
                        if (line.startsWith("http") && isPlayableStreamUrl(line)) {
                            URL_CACHE.put(cacheKey, new CacheEntry(line, "", null, System.currentTimeMillis() + CACHE_TTL_MS));
                            return line;
                        }
                    }
                }
            }
            return "";
        } catch (Exception e) {
            android.util.Log.d(TAG, "followRedirectsToCdn failed: " + e.getMessage());
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

    private static final class Resolved {
        final String url;
        final String userAgent;
        final JSONObject headers;

        Resolved(String url, String userAgent, JSONObject headers) {
            this.url = url;
            this.userAgent = userAgent;
            this.headers = headers;
        }
    }

    private static Resolved fetchResolved(SharedPref sharedPref, String requestUrl) {
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
                if (!TextUtils.isEmpty(location) && isPlayableStreamUrl(location)) {
                    return new Resolved(location, "", null);
                }
            }
            if (code < 200 || code >= 300) {
                return null;
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
                return null;
            }
            JSONObject json = new JSONObject(trimmed);
            if (!json.optBoolean("ok", false)) {
                return null;
            }
            String url = json.optString("url", "").trim();
            if (TextUtils.isEmpty(url)) {
                url = json.optString("direct_url", "").trim();
            }
            String userAgent = json.optString("user_agent", "").trim();
            JSONObject headers = json.optJSONObject("headers");
            if (TextUtils.isEmpty(url)) {
                return null;
            }
            return new Resolved(url, userAgent, headers);
        } catch (Exception e) {
            android.util.Log.d(TAG, "fetchResolved failed: " + e.getMessage());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** @deprecated use {@link #isPlayableStreamUrl(String)} */
    @Deprecated
    public static boolean isDirectCdnUrl(String url) {
        return isPlayableStreamUrl(url);
    }

    public static boolean isPlayableStreamUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        String lower = url.toLowerCase(Locale.US);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")
                && !lower.startsWith("rtsp://") && !lower.startsWith("rtmp://")) {
            return false;
        }
        if (lower.contains("playlist.shifshqip.com")) {
            // Allow only resolved playable proxy endpoints under /u or already-unsealed CDN.
            if (lower.contains("/k1/app-measurement/collect/") && (lower.contains("/r/") || lower.contains("/u/"))) {
                return false;
            }
            if (lower.contains("/k1/app-measurement/collect/")) {
                return false;
            }
        }
        // Origin / CF Worker proxies that serve rewritten HLS — playable in ExoPlayer.
        if (lower.contains("phinemi.live") && (lower.contains("/a3proxy")
                || lower.contains("/a3relay")
                || lower.contains("/a3hls")
                || lower.contains("a3.phinemi.live"))) {
            return true;
        }
        if (lower.contains("a3.phinemi.live")) {
            return true;
        }
        if (lower.contains("shifshqip.com")) {
            if (lower.contains("/k1/channel-proxy/")
                    || lower.contains("/k1/proxy/")
                    || lower.contains("/k1/channel/")
                    || lower.contains("/k1/playlist/")
                    || lower.contains("/k1/a3proxy")
                    || lower.contains("/k1/a3relay")
                    || lower.contains("/k1/a3hls")
                    || lower.contains("/app-measurement/collect/")) {
                // a3hls/a3proxy on shifshqip are playable same-origin proxies
                if (lower.contains("/k1/a3proxy")
                        || lower.contains("/k1/a3relay")
                        || lower.contains("/k1/a3hls")) {
                    return true;
                }
                return false;
            }
        }
        if (lower.contains("/r/") && lower.contains("/app-measurement/collect/")) {
            return false;
        }
        if (lower.contains("fastlycache.com")
                || lower.contains("gcore-cdn.org")
                || lower.contains("anytools.eu.org")
                || lower.contains("gjirafa.net")
                || lower.contains("gjirafa.com")
                || lower.contains("unique-user-id=")) {
            return true;
        }
        return lower.contains(".m3u8")
                || lower.contains(".mpd")
                || lower.contains(".m3u")
                || lower.contains(".mp4")
                || lower.startsWith("rtsp://")
                || lower.startsWith("rtmp://")
                || lower.contains("/live/")
                || lower.contains("/hls/")
                || lower.matches("(?i)https?://[^/]+:\\d+/.+");
    }
}
