package com.shif.shqip.android.utils;

import android.content.Context;
import android.text.TextUtils;

import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.models.PlaylistGuardRule;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;

import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PlaylistGuardHelper {

    private static final String DEFAULT_USER_AGENT = "ShifShqip/6.7.3 (Android)";

    private PlaylistGuardHelper() {
    }

    public static PlaylistGuardRule findMatchingRule(String urlString, List<PlaylistGuardRule> rules) {
        if (TextUtils.isEmpty(urlString) || rules == null || rules.isEmpty()) {
            return null;
        }
        String normalizedUrl = normalizeMatchKey(urlString);
        for (PlaylistGuardRule rule : rules) {
            if (rule == null || TextUtils.isEmpty(rule.url_pattern)) {
                continue;
            }
            String pattern = normalizeMatchKey(rule.url_pattern);
            if (!TextUtils.isEmpty(pattern) && normalizedUrl.contains(pattern)) {
                return rule;
            }
        }
        return null;
    }

    public static PlaylistGuardRule findRuleByPattern(List<PlaylistGuardRule> rules, String urlPattern) {
        if (rules == null || rules.isEmpty() || TextUtils.isEmpty(urlPattern)) {
            return null;
        }
        String normalizedPattern = normalizeMatchKey(urlPattern);
        for (PlaylistGuardRule rule : rules) {
            if (rule != null && normalizedPattern.equals(normalizeMatchKey(rule.url_pattern))) {
                return rule;
            }
        }
        return findMatchingRule(urlPattern, rules);
    }

    public static PlaylistGuardRule resolveRule(SharedPref sharedPref, String requestUrl) {
        if (sharedPref == null) {
            return null;
        }
        List<PlaylistGuardRule> rules = sharedPref.getPlaylistGuardRules();
        if (rules.isEmpty()) {
            return null;
        }

        PlaylistGuardRule direct = findMatchingRule(requestUrl, rules);
        if (direct != null) {
            return direct;
        }

        PlaylistGuardRule streamRule = findStreamPatternRule(requestUrl, rules);
        if (streamRule != null) {
            return streamRule;
        }

        String playlistUrl = sharedPref.getPlaylistUrl();
        if (!TextUtils.isEmpty(playlistUrl)) {
            PlaylistGuardRule playlistRule = findMatchingRule(playlistUrl, rules);
            if (playlistRule != null) {
                return playlistRule;
            }
        }

        PlaylistGuardRule active = sharedPref.getActiveGuardRule();
        if (active != null) {
            PlaylistGuardRule hydrated = findRuleByPattern(rules, active.url_pattern);
            if (hydrated != null) {
                return hydrated;
            }
        }

        return findMatchingRule(sharedPref.getActiveGuardSourceUrl(), rules);
    }

    public static void reconcileActiveGuardRule(SharedPref sharedPref) {
        if (sharedPref == null) {
            return;
        }
        List<PlaylistGuardRule> rules = sharedPref.getPlaylistGuardRules();
        if (rules.isEmpty()) {
            sharedPref.clearActiveGuardRule();
            return;
        }
        String playlistUrl = sharedPref.getPlaylistUrl();
        PlaylistGuardRule matched = findMatchingRule(playlistUrl, rules);
        if (matched == null) {
            matched = findMatchingRule(sharedPref.getActiveGuardSourceUrl(), rules);
        }
        if (matched == null) {
            PlaylistGuardRule active = sharedPref.getActiveGuardRule();
            if (active != null) {
                matched = findRuleByPattern(rules, active.url_pattern);
            }
        }
        if (matched != null) {
            String source = !TextUtils.isEmpty(playlistUrl) ? playlistUrl : sharedPref.getActiveGuardSourceUrl();
            rememberRuleForPlaylist(sharedPref, source, matched);
        } else {
            sharedPref.clearActiveGuardRule();
        }
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

    private static PlaylistGuardRule findStreamPatternRule(String requestUrl, List<PlaylistGuardRule> rules) {
        if (TextUtils.isEmpty(requestUrl) || rules == null) {
            return null;
        }
        String normalizedUrl = normalizeMatchKey(requestUrl);
        for (PlaylistGuardRule rule : rules) {
            if (rule == null || TextUtils.isEmpty(rule.stream_url_pattern)) {
                continue;
            }
            String pattern = normalizeMatchKey(rule.stream_url_pattern);
            if (!TextUtils.isEmpty(pattern) && normalizedUrl.contains(pattern)) {
                return rule;
            }
        }
        return null;
    }

    static String normalizeMatchKey(String value) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.US);
        if (normalized.startsWith("https://")) {
            normalized = normalized.substring(8);
        } else if (normalized.startsWith("http://")) {
            normalized = normalized.substring(7);
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.startsWith("www.")) {
            normalized = normalized.substring(4);
        }
        return normalized;
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
