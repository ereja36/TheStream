package com.shif.shqip.android.utils;

import android.text.TextUtils;

import org.json.JSONObject;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds EXTVLCOPT / portal headers for the active channel source host only.
 * After open, requests to that stream host use these headers; unrelated hosts do not.
 */
public final class StreamHeaderSession {

    private static final Object LOCK = new Object();
    private static volatile String activeHost = "";
    private static volatile String activeUserAgent = "";
    private static volatile Map<String, String> activeHeaders = Collections.emptyMap();

    private static final ConcurrentHashMap<String, StickyHeaders> BY_SOURCE = new ConcurrentHashMap<>();

    private StreamHeaderSession() {
    }

    private static final class StickyHeaders {
        final String userAgent;
        final Map<String, String> headers;

        StickyHeaders(String userAgent, Map<String, String> headers) {
            this.userAgent = userAgent == null ? "" : userAgent;
            this.headers = headers == null ? Collections.emptyMap() : headers;
        }
    }

    public static void clearActive() {
        synchronized (LOCK) {
            activeHost = "";
            activeUserAgent = "";
            activeHeaders = Collections.emptyMap();
        }
    }

    public static void rememberResolved(String playUrl, String userAgent, JSONObject headersJson) {
        rememberResolved(playUrl, userAgent, sanitizeHeaders(headersJson));
    }

    public static void rememberResolved(String playUrl, String userAgent, Map<String, String> headers) {
        String host = hostOf(playUrl);
        Map<String, String> safe = sanitizeHeaders(headers);
        synchronized (LOCK) {
            activeHost = host;
            activeUserAgent = userAgent == null ? "" : userAgent.trim();
            activeHeaders = safe;
        }
    }

    public static void rememberForSource(String sourceUrl, String userAgent, String referer, String origin) {
        if (TextUtils.isEmpty(sourceUrl)) {
            return;
        }
        Map<String, String> headers = new HashMap<>();
        if (!TextUtils.isEmpty(referer)) {
            headers.put("Referer", referer.trim());
        }
        if (!TextUtils.isEmpty(origin)) {
            headers.put("Origin", origin.trim());
        }
        BY_SOURCE.put(sourceUrl.trim(), new StickyHeaders(userAgent, sanitizeHeaders(headers)));
    }

    public static void applySourceIfPresent(String sourceUrl) {
        if (TextUtils.isEmpty(sourceUrl)) {
            return;
        }
        StickyHeaders sticky = BY_SOURCE.get(sourceUrl.trim());
        if (sticky == null) {
            return;
        }
        synchronized (LOCK) {
            activeHost = hostOf(sourceUrl);
            if (!TextUtils.isEmpty(sticky.userAgent) && !"default".equalsIgnoreCase(sticky.userAgent)) {
                activeUserAgent = sticky.userAgent;
            }
            if (!sticky.headers.isEmpty()) {
                activeHeaders = sticky.headers;
            }
        }
    }

    public static String resolveUserAgent(String requestUrl, String fallback) {
        synchronized (LOCK) {
            if (!TextUtils.isEmpty(activeUserAgent)
                    && !"default".equalsIgnoreCase(activeUserAgent)
                    && hostMatches(requestUrl, activeHost)) {
                return activeUserAgent;
            }
        }
        if (!TextUtils.isEmpty(fallback) && !"default".equalsIgnoreCase(fallback.trim())) {
            return fallback.trim();
        }
        return "";
    }

    public static Map<String, String> headersFor(String requestUrl) {
        synchronized (LOCK) {
            if (activeHeaders.isEmpty() || !hostMatches(requestUrl, activeHost)) {
                return Collections.emptyMap();
            }
            return new HashMap<>(activeHeaders);
        }
    }

    public static boolean hasActiveFor(String requestUrl) {
        synchronized (LOCK) {
            return !TextUtils.isEmpty(activeHost) && hostMatches(requestUrl, activeHost)
                    && (!activeHeaders.isEmpty() || !TextUtils.isEmpty(activeUserAgent));
        }
    }

    private static boolean hostMatches(String requestUrl, String expectedHost) {
        if (TextUtils.isEmpty(expectedHost)) {
            return false;
        }
        String host = hostOf(requestUrl);
        if (TextUtils.isEmpty(host)) {
            return false;
        }
        return host.equalsIgnoreCase(expectedHost)
                || host.endsWith("." + expectedHost)
                || expectedHost.endsWith("." + host);
    }

    private static String hostOf(String url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }
        try {
            URI uri = URI.create(url.trim());
            String host = uri.getHost();
            return host == null ? "" : host.toLowerCase(Locale.US);
        } catch (Exception e) {
            return "";
        }
    }

    private static Map<String, String> sanitizeHeaders(JSONObject json) {
        Map<String, String> out = new HashMap<>();
        if (json == null) {
            return out;
        }
        Iterator<String> keys = json.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            putSafe(out, key, json.optString(key, ""));
        }
        return out;
    }

    private static Map<String, String> sanitizeHeaders(Map<String, String> input) {
        Map<String, String> out = new HashMap<>();
        if (input == null || input.isEmpty()) {
            return out;
        }
        for (Map.Entry<String, String> entry : input.entrySet()) {
            putSafe(out, entry.getKey(), entry.getValue());
        }
        return out;
    }

    private static void putSafe(Map<String, String> out, String key, String value) {
        if (TextUtils.isEmpty(key) || TextUtils.isEmpty(value)) {
            return;
        }
        String lower = key.trim().toLowerCase(Locale.US);
        if ("host".equals(lower)
                || "connection".equals(lower)
                || "content-length".equals(lower)
                || "transfer-encoding".equals(lower)
                || "accept-encoding".equals(lower)
                || "x-forwarded-for".equals(lower)
                || "x-real-ip".equals(lower)
                || "cf-connecting-ip".equals(lower)
                || "forwarded".equals(lower)
                || "user-agent".equals(lower)) {
            return;
        }
        out.put(key.trim(), value.trim());
    }
}
