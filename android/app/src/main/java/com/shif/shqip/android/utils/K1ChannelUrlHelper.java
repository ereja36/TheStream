package com.shif.shqip.android.utils;

import android.text.TextUtils;
import android.util.Log;

import com.shif.shqip.android.BuildConfig;
import com.shif.shqip.android.databases.prefs.SharedPref;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Resolves fresh play URLs via playlist portal (server-side k1 API proxy).
 * Client never calls shifshqip.com directly — no upstream Referer/UA leak.
 */
public final class K1ChannelUrlHelper {

    private static final String TAG = "K1ChannelUrlHelper";
    private static final String PORTAL_CHANNEL_API =
            "https://playlist.shifshqip.com/k1/app-measurement/collect/shifshqip/u/";

    private K1ChannelUrlHelper() {
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
        HttpURLConnection connection = null;
        try {
            String encoded = java.net.URLEncoder.encode(slug.trim(), "UTF-8");
            connection = (HttpURLConnection) new URL(PORTAL_CHANNEL_API + encoded).openConnection();
            connection.setConnectTimeout(StreamPlaybackHelper.CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(StreamPlaybackHelper.READ_TIMEOUT_MS);
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
