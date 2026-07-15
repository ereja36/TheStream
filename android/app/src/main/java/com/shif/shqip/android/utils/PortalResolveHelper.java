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
 * One-shot portal resolve: sealed playlist URLs → playable stream URL (CDN or all2).
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
            return cached.url;
        }
        String resolved = fetchDirectUrl(sharedPref, portalUrl);
        if (isPlayableStreamUrl(resolved)) {
            URL_CACHE.put(cacheKey, new CacheEntry(resolved, System.currentTimeMillis() + CACHE_TTL_MS));
            return resolved;
        }
        return "";
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
            if (code >= 200 && code < 400 && isPlayableStreamUrl(finalUrl)) {
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
                        if (line.startsWith("http") && isPlayableStreamUrl(line)) {
                            URL_CACHE.put(cacheKey, new CacheEntry(line, System.currentTimeMillis() + CACHE_TTL_MS));
                            return line;
                        }
                    }
                }
            }
            // all2 playlist entries are themselves playable (manifest + /proxy/seg).
            if (code >= 200 && code < 300 && isPlayableAll2Url(startUrl)) {
                URL_CACHE.put(cacheKey, new CacheEntry(startUrl, System.currentTimeMillis() + CACHE_TTL_MS));
                return startUrl;
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
                if (!TextUtils.isEmpty(location) && isPlayableStreamUrl(location)) {
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
            String url = json.optString("url", "").trim();
            if (isPlayableStreamUrl(url)) {
                return url;
            }
            if (!TextUtils.isEmpty(url)) {
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

    /** CDN direct OR same-origin all2 delivery URL the player can open. */
    public static boolean isPlayableStreamUrl(String url) {
        return isDirectCdnUrl(url) || isPlayableAll2Url(url);
    }

    /**
     * all2 private delivery: /k1/playlist, /channel, a3hls, proxy — playable by ExoPlayer with Ooew UA.
     */
    public static boolean isPlayableAll2Url(String url) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        String lower = url.toLowerCase(Locale.US);
        if (!lower.contains("shifshqip.com")) {
            return false;
        }
        return lower.contains("/k1/playlist/")
                || lower.contains("/k1/channel-proxy/")
                || lower.contains("/k1/channel/")
                || lower.contains("/k1/proxy/")
                || lower.contains("/k1/a3proxy")
                || lower.contains("/k1/a3relay")
                || lower.contains("/k1/a3hls")
                || lower.contains("/app-measurement/");
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
                    || lower.contains("/k1/a3")
                    || lower.contains("/app-measurement/collect/")) {
                return false;
            }
        }
        if (lower.contains("/r/") && lower.contains("/app-measurement/collect/")) {
            return false;
        }
        if (lower.contains("fastlycache.com")
                || lower.contains("anytools.eu.org")
                || lower.contains("unique-user-id=")
                || lower.contains("gcore-cdn.org")
                || lower.contains("gjirafa.net")
                || lower.contains("gjirafa.com")
                || lower.contains("ngolpdkyoctjcddxshli469r.org")) {
            return true;
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
