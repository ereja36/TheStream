package com.shif.shqip.android.utils;

import android.content.Context;
import android.text.TextUtils;

import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.models.PlaylistGuardRule;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;

import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PlaylistGuardHelper {

    private static final String DEFAULT_USER_AGENT = "ShifShqip/6.7 (Android)";

    private PlaylistGuardHelper() {
    }

    public static PlaylistGuardRule findMatchingRule(String urlString, List<PlaylistGuardRule> rules) {
        if (TextUtils.isEmpty(urlString) || rules == null || rules.isEmpty()) {
            return null;
        }
        String normalizedUrl = urlString.toLowerCase(Locale.US);
        for (PlaylistGuardRule rule : rules) {
            if (rule == null || TextUtils.isEmpty(rule.url_pattern)) {
                continue;
            }
            if (normalizedUrl.contains(rule.url_pattern.trim().toLowerCase(Locale.US))) {
                return rule;
            }
        }
        return null;
    }

    public static PlaylistGuardRule resolveRule(SharedPref sharedPref, String requestUrl) {
        if (sharedPref == null) {
            return null;
        }
        PlaylistGuardRule direct = findMatchingRule(requestUrl, sharedPref.getPlaylistGuardRules());
        if (direct != null) {
            return direct;
        }
        if (sharedPref.hasPlaylistChannels()) {
            return sharedPref.getActiveGuardRule();
        }
        return null;
    }

    public static void rememberRuleForPlaylist(SharedPref sharedPref, String playlistUrl, PlaylistGuardRule rule) {
        if (sharedPref == null || rule == null || TextUtils.isEmpty(playlistUrl)) {
            return;
        }
        sharedPref.saveActiveGuardRule(rule, playlistUrl);
    }

    public static void rememberRuleIfMatched(SharedPref sharedPref, String url) {
        if (sharedPref == null || TextUtils.isEmpty(url)) {
            return;
        }
        PlaylistGuardRule rule = findMatchingRule(url, sharedPref.getPlaylistGuardRules());
        if (rule != null && isLikelyPlaylistUrl(url)) {
            rememberRuleForPlaylist(sharedPref, url, rule);
        }
    }

    public static void applyRuleHeaders(HttpURLConnection connection, PlaylistGuardRule rule) {
        if (connection == null || rule == null) {
            return;
        }
        if (!TextUtils.isEmpty(rule.user_agent)) {
            connection.setRequestProperty("User-Agent", rule.user_agent);
        }
        if (!TextUtils.isEmpty(rule.referer)) {
            connection.setRequestProperty("Referer", rule.referer);
        }
        if (!TextUtils.isEmpty(rule.origin)) {
            connection.setRequestProperty("Origin", rule.origin);
        }
        if (!TextUtils.isEmpty(rule.cookie)) {
            connection.setRequestProperty("Cookie", rule.cookie);
        }
        applyExtraHeaders(connection, rule.extra_headers);
    }

    public static Map<String, String> buildRequestHeaders(PlaylistGuardRule rule) {
        Map<String, String> headers = new HashMap<>();
        if (rule == null) {
            return headers;
        }
        if (!TextUtils.isEmpty(rule.referer)) {
            headers.put("Referer", rule.referer);
        }
        if (!TextUtils.isEmpty(rule.origin)) {
            headers.put("Origin", rule.origin);
        }
        if (!TextUtils.isEmpty(rule.cookie)) {
            headers.put("Cookie", rule.cookie);
        }
        if (!TextUtils.isEmpty(rule.extra_headers)) {
            try {
                JSONObject json = new JSONObject(rule.extra_headers);
                java.util.Iterator<String> keys = json.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    String value = json.optString(key, "");
                    if (!TextUtils.isEmpty(key) && !TextUtils.isEmpty(value)) {
                        headers.put(key, value);
                    }
                }
            } catch (Exception ignored) {
                // ignore malformed json
            }
        }
        return headers;
    }

    public static String resolveUserAgent(PlaylistGuardRule rule, String channelUserAgent) {
        if (rule != null && !TextUtils.isEmpty(rule.user_agent)) {
            return rule.user_agent;
        }
        if (!TextUtils.isEmpty(channelUserAgent) && !"default".equalsIgnoreCase(channelUserAgent.trim())) {
            return channelUserAgent.trim();
        }
        return DEFAULT_USER_AGENT;
    }

    public static DefaultDataSource.Factory createDataSourceFactory(Context context, SharedPref sharedPref, String requestUrl, String channelUserAgent) {
        PlaylistGuardRule rule = resolveRule(sharedPref, requestUrl);
        String userAgent = resolveUserAgent(rule, channelUserAgent);
        Map<String, String> headers = buildRequestHeaders(rule);

        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setUserAgent(userAgent);
        if (!headers.isEmpty()) {
            httpFactory.setDefaultRequestProperties(headers);
        }
        return new DefaultDataSource.Factory(context.getApplicationContext(), httpFactory);
    }

    private static void applyExtraHeaders(HttpURLConnection connection, String extraHeadersJson) {
        if (TextUtils.isEmpty(extraHeadersJson)) {
            return;
        }
        try {
            JSONObject headers = new JSONObject(extraHeadersJson);
            java.util.Iterator<String> keys = headers.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String value = headers.optString(key, "");
                if (!TextUtils.isEmpty(key) && !TextUtils.isEmpty(value)) {
                    connection.setRequestProperty(key, value);
                }
            }
        } catch (Exception ignored) {
            // ignore malformed json
        }
    }

    private static boolean isLikelyPlaylistUrl(String url) {
        String lower = url.toLowerCase(Locale.US);
        return lower.contains(".m3u")
                || lower.contains("playlist")
                || lower.contains("get.php")
                || lower.contains("player_api");
    }
}
