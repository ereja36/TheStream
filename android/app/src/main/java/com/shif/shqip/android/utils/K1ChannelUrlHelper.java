package com.shif.shqip.android.utils;

import android.text.TextUtils;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Resolves playback URLs the same way as shifshqip.com/k1 web (HLS.js player):
 * GET /k1/api/channel-url/{slug} → channel-proxy URL with fresh token.
 */
public final class K1ChannelUrlHelper {

    private static final String TAG = "K1ChannelUrlHelper";
    private static final String K1_API_BASE = "https://shifshqip.com/k1/api/channel-url/";

    private K1ChannelUrlHelper() {
    }

    public static boolean looksLikeK1Slug(String value) {
        if (TextUtils.isEmpty(value)) {
            return false;
        }
        String slug = value.trim().toLowerCase(Locale.US);
        return slug.matches("^[a-z0-9][a-z0-9_-]{2,120}$");
    }

    /**
     * Fetch a fresh channel-proxy URL from k1 API (same as web getPlayableUrl).
     * Returns empty string on failure so callers can fall back to /r/ resolve.
     */
    public static String resolvePlayableUrl(String slug) {
        if (!looksLikeK1Slug(slug)) {
            return "";
        }
        HttpURLConnection connection = null;
        try {
            String encoded = java.net.URLEncoder.encode(slug.trim(), "UTF-8");
            connection = (HttpURLConnection) new URL(K1_API_BASE + encoded).openConnection();
            connection.setConnectTimeout(StreamPlaybackHelper.CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(StreamPlaybackHelper.READ_TIMEOUT_MS);
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", PlaylistPortalHelper.UPSTREAM_STREAM_UA);
            connection.setRequestProperty("Referer", PlaylistPortalHelper.UPSTREAM_STREAM_REFERER);
            connection.setRequestProperty("Cache-Control", "no-cache");

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

            String url = json.optString("url", "").trim();
            if (TextUtils.isEmpty(url)) {
                url = json.optString("proxy_url", "").trim();
            }
            if (TextUtils.isEmpty(url)) {
                return "";
            }

            if (url.startsWith("/")) {
                url = "https://shifshqip.com" + url;
            }
            return K1PlaybackHelper.optimizePlaybackUrl(url);
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
