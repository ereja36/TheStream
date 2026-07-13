package com.shif.shqip.android.utils;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.shif.shqip.android.databases.prefs.AdsPref;
import com.shif.shqip.android.databases.prefs.SharedPref;

import org.json.JSONObject;

/**
 * Server-driven ad recovery rules for users with ad blockers or failed ad loads.
 */
public final class AdsAntiblockHelper {

    private static final String TAG = "AdsAntiblockHelper";
    private static final long DEFAULT_RETRY_DELAY_MS = 4_000;

    private final Activity activity;
    private final SharedPref sharedPref;
    private final AdsPref adsPref;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int retryCount;

    public AdsAntiblockHelper(Activity activity) {
        this.activity = activity;
        this.sharedPref = new SharedPref(activity);
        this.adsPref = new AdsPref(activity);
    }

    public void applyServerScript(String scriptJson) {
        sharedPref.saveAdsAntiblockScript(scriptJson == null ? "" : scriptJson.trim());
    }

    public void onAdLoadFailed(Runnable reloadAction) {
        if (!adsPref.getAdStatus() || reloadAction == null) {
            return;
        }
        JSONObject config = parseConfig();
        if (!config.optBoolean("reload_on_fail", true)) {
            return;
        }
        int maxRetries = Math.max(1, config.optInt("max_retries", 3));
        long delayMs = Math.max(1_000L, config.optLong("retry_delay_ms", DEFAULT_RETRY_DELAY_MS));
        if (retryCount >= maxRetries) {
            return;
        }
        retryCount++;
        handler.postDelayed(() -> {
            if (activity.isFinishing()) {
                return;
            }
            Log.d(TAG, "retry ad load attempt=" + retryCount);
            reloadAction.run();
        }, delayMs);
    }

    public void resetRetryCount() {
        retryCount = 0;
        handler.removeCallbacksAndMessages(null);
    }

    public boolean shouldForceAdReloadOnResume() {
        // Default off — full reloads on every resume were wiping banners/natives.
        return parseConfig().optBoolean("reload_on_resume", false);
    }

    private JSONObject parseConfig() {
        String raw = sharedPref.getAdsAntiblockScript();
        if (TextUtils.isEmpty(raw)) {
            return defaultConfig();
        }
        try {
            String trimmed = raw.trim();
            if (trimmed.startsWith("{")) {
                return new JSONObject(trimmed);
            }
            JSONObject json = new JSONObject();
            for (String line : trimmed.split("\\r?\\n")) {
                String item = line.trim();
                if (item.isEmpty() || item.startsWith("#") || !item.contains("=")) {
                    continue;
                }
                String[] parts = item.split("=", 2);
                String key = parts[0].trim();
                String value = parts[1].trim();
                if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                    json.put(key, Boolean.parseBoolean(value));
                } else if (value.matches("-?\\d+")) {
                    json.put(key, Long.parseLong(value));
                } else {
                    json.put(key, value);
                }
            }
            return json.length() > 0 ? json : defaultConfig();
        } catch (Exception ignored) {
            return defaultConfig();
        }
    }

    private JSONObject defaultConfig() {
        JSONObject json = new JSONObject();
        try {
            json.put("reload_on_fail", true);
            json.put("max_retries", 3);
            json.put("retry_delay_ms", DEFAULT_RETRY_DELAY_MS);
            json.put("reload_on_resume", false);
        } catch (Exception ignored) {
            // no-op
        }
        return json;
    }
}
