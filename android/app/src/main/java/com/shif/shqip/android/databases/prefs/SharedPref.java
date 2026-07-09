package com.shif.shqip.android.databases.prefs;

import static com.shif.shqip.android.Config.CATEGORY_VIEW_TYPE;
import static com.shif.shqip.android.Config.CHANNEL_VIEW_TYPE;
import static com.shif.shqip.android.Config.DEFAULT_PLAYER_SCREEN_ORIENTATION;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.shif.shqip.android.BuildConfig;
import com.shif.shqip.android.Config;
import com.shif.shqip.android.models.Category;
import com.shif.shqip.android.models.Channel;
import com.shif.shqip.android.models.PlaylistGuardRule;
import com.shif.shqip.android.models.User;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

public class SharedPref {

    private static final String KEY_PLAYLIST_URL = "playlist_url";
    private static final String KEY_PLAYLIST_CHANNELS = "playlist_channels";
    private static final String KEY_DESIGN_ID = "design_id";
    private static final String KEY_PLAYLIST_GUARD = "playlist_guard_rules";
    private static final String KEY_APP_LOGO_URL = "app_logo_url";
    private static final String KEY_ACTIVE_GUARD_RULE = "active_guard_rule";
    private static final String KEY_ACTIVE_GUARD_SOURCE = "active_guard_source";
    private static final String KEY_USER_PLAYLIST_BACKUP_CHANNELS = "user_playlist_backup_channels";
    private static final String KEY_USER_PLAYLIST_BACKUP_URL = "user_playlist_backup_url";
    private static final String KEY_USER_PLAYLIST_BACKUP_NAME = "user_playlist_backup_name";
    private static final String KEY_GLOBAL_PLAYLIST_ACTIVE = "global_playlist_active";
    private static final String KEY_HIDE_PLAYLIST_SOURCE = "hide_playlist_source";
    private static final String KEY_PORTAL_ACCESS_KEY = "portal_access_key";
    private static final String KEY_PORTAL_SESSION_TOKEN = "portal_session_token";
    private static final String KEY_PORTAL_PUBLIC_URL = "portal_public_url";
    private static final String KEY_PORTAL_STREAM_HOST = "portal_stream_host";
    private static final String KEY_PORTAL_STREAM_USER_AGENT = "portal_stream_user_agent";
    private static final String KEY_PORTAL_STREAM_REFERER = "portal_stream_referer";
    private static final String KEY_PORTAL_STREAM_ORIGIN = "portal_stream_origin";
    private static final String KEY_PORTAL_STREAM_COOKIE = "portal_stream_cookie";

    Context context;
    SharedPreferences sharedPreferences;
    SharedPreferences.Editor editor;

    public SharedPref(Context context) {
        this.context = context;
        sharedPreferences = context.getSharedPreferences(BuildConfig.APPLICATION_ID + "_" + "shared_preferences", Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
        ensureDefaultTheme();
    }

    public Context getContext() {
        return context;
    }

    public void ensureDefaultTheme() {
        if (!sharedPreferences.contains("theme")) {
            String designId = getDesignId();
            boolean defaultDark = "v2".equals(designId) || "v3".equals(designId) || Config.SET_DARK_MODE_AS_DEFAULT_THEME;
            editor.putBoolean("theme", defaultDark);
            editor.apply();
        }
    }

    public void saveDesignId(String designId) {
        String normalized = designId == null ? "v1" : designId.trim().toLowerCase();
        if (!"v2".equals(normalized) && !"v3".equals(normalized)) {
            normalized = "v1";
        }
        editor.putString(KEY_DESIGN_ID, normalized);
        editor.apply();
    }

    public String getDesignId() {
        String designId = sharedPreferences.getString(KEY_DESIGN_ID, "v1");
        if ("v2".equals(designId) || "v3".equals(designId)) {
            return designId;
        }
        return "v1";
    }

    public void saveAppLogoUrl(String logoUrl) {
        editor.putString(KEY_APP_LOGO_URL, logoUrl == null ? "" : logoUrl.trim());
        editor.apply();
    }

    public String getAppLogoUrl() {
        return sharedPreferences.getString(KEY_APP_LOGO_URL, "");
    }

    public void savePlaylistGuardRules(List<PlaylistGuardRule> rules) {
        Gson gson = new Gson();
        editor.putString(KEY_PLAYLIST_GUARD, gson.toJson(rules == null ? new ArrayList<>() : rules));
        editor.apply();
    }

    public List<PlaylistGuardRule> getPlaylistGuardRules() {
        Gson gson = new Gson();
        String json = sharedPreferences.getString(KEY_PLAYLIST_GUARD, null);
        Type type = new TypeToken<ArrayList<PlaylistGuardRule>>() {
        }.getType();
        List<PlaylistGuardRule> rules = gson.fromJson(json, type);
        return rules == null ? new ArrayList<>() : rules;
    }

    public void saveActiveGuardRule(PlaylistGuardRule rule, String sourceUrl) {
        Gson gson = new Gson();
        editor.putString(KEY_ACTIVE_GUARD_RULE, gson.toJson(rule));
        editor.putString(KEY_ACTIVE_GUARD_SOURCE, sourceUrl == null ? "" : sourceUrl.trim());
        editor.apply();
    }

    public PlaylistGuardRule getActiveGuardRule() {
        Gson gson = new Gson();
        String json = sharedPreferences.getString(KEY_ACTIVE_GUARD_RULE, null);
        if (TextUtils.isEmpty(json)) {
            return null;
        }
        return gson.fromJson(json, PlaylistGuardRule.class);
    }

    public String getActiveGuardSourceUrl() {
        return sharedPreferences.getString(KEY_ACTIVE_GUARD_SOURCE, "");
    }

    public void clearActiveGuardRule() {
        editor.remove(KEY_ACTIVE_GUARD_RULE);
        editor.remove(KEY_ACTIVE_GUARD_SOURCE);
        editor.apply();
    }

    public Boolean getIsDarkTheme() {
        if (!sharedPreferences.contains("theme")) {
            if ("v2".equals(getDesignId()) || "v3".equals(getDesignId())) {
                return true;
            }
            return Config.SET_DARK_MODE_AS_DEFAULT_THEME;
        }
        return sharedPreferences.getBoolean("theme", Config.SET_DARK_MODE_AS_DEFAULT_THEME);
    }

    public void setIsDarkTheme(Boolean isDarkTheme) {
        sharedPreferences.edit().putBoolean("theme", isDarkTheme).commit();
    }

    public void setDialogPlayerOption(boolean dialogPlayerOption) {
        editor.putBoolean("dialog_player_option", dialogPlayerOption);
        editor.apply();
    }

    public boolean getIsDialogPlayerOption() {
        return sharedPreferences.getBoolean("dialog_player_option", true);
    }

    public void setPlayerPosition(int position) {
        editor.putInt("player_position", position);
        editor.apply();
    }

    public Integer getPlayerPosition() {
        return sharedPreferences.getInt("player_position", 0);
    }

    public Integer getChannelViewType() {
        return sharedPreferences.getInt("video_list", CHANNEL_VIEW_TYPE);
    }

    public void updateChannelViewType(int position) {
        editor.putInt("video_list", position);
        editor.apply();
    }

    public Integer getCategoryViewType() {
        return sharedPreferences.getInt("category_list", CATEGORY_VIEW_TYPE);
    }

    public void updateCategoryViewType(int position) {
        editor.putInt("category_list", position);
        editor.apply();
    }

    public Integer getPlayerOrientation() {
        return sharedPreferences.getInt("player_mode", DEFAULT_PLAYER_SCREEN_ORIENTATION);
    }

    public void updatePlayerOrientation(int position) {
        editor.putInt("player_mode", position);
        editor.apply();
    }

    public void saveConfig(String api_url, String application_id) {
        editor.putString("api_url", api_url);
        editor.putString("application_id", application_id);
        editor.apply();
    }

    public String getBaseUrl() {
        return sharedPreferences.getString("api_url", "http://10.0.2.2/the_stream");
    }

    public String getApplicationId() {
        return sharedPreferences.getString("application_id", "com.shif.shqip.android");
    }

    public void saveCredentials(String youtube_api_key, String more_apps_url, String privacy_policy, String redirect_url, String pushNotificationProvider, boolean enablePlayerEpg, String privacyPublicUrl) {
        editor.putString("youtube_api_key", youtube_api_key);
        editor.putString("more_apps_url", more_apps_url);
        editor.putString("privacy_policy", privacy_policy);
        editor.putString("redirect_url", redirect_url);
        editor.putString("providers", pushNotificationProvider);
        editor.putBoolean("enable_player_epg", enablePlayerEpg);
        editor.putString("privacy_public_url", privacyPublicUrl);
        editor.apply();
    }

    public boolean isPlayerEpgEnabled() {
        return sharedPreferences.getBoolean("enable_player_epg", true);
    }

    public String getPrivacyPublicUrl() {
        return sharedPreferences.getString("privacy_public_url", "https://shifshqip.com/privacy");
    }

    public String getYoutubeAPIKey() {
        return sharedPreferences.getString("youtube_api_key", "0");
    }

    public String getPrivacyPolicy() {
        return sharedPreferences.getString("privacy_policy", "");
    }

    public String getMoreAppsUrl() {
        return sharedPreferences.getString("more_apps_url", "https://shifshqip.com");
    }

    public Integer getInAppReviewToken() {
        return sharedPreferences.getInt("in_app_review_token", 0);
    }

    public void updateInAppReviewToken(int value) {
        editor.putInt("in_app_review_token", value);
        editor.apply();
    }

    public String getRedirectUrl() {
        return sharedPreferences.getString("redirect_url", "");
    }

    public String getPushNotificationProvider() {
        return sharedPreferences.getString("providers", "");
    }

    public void saveUser(String itemId, String itemName, String buyer, String licenseType) {
        editor.putString("item_id", itemId);
        editor.putString("item_name", itemName);
        editor.putString("buyer", buyer);
        editor.putString("license_type", licenseType);
        editor.apply();
    }

    public String getItemId() {
        return sharedPreferences.getString("item_id", "");
    }

    public String getItemName() {
        return sharedPreferences.getString("item_name", "");
    }

    public String getBuyer() {
        return sharedPreferences.getString("buyer", "");
    }

    public String getLicenseType() {
        return sharedPreferences.getString("license_type", "");
    }

    public void saveUserList(List<User> users) {
        Gson gson = new Gson();
        String json = gson.toJson(users);
        editor.putString("key_user", json);
        editor.apply();
    }

    public List<User> getUserList() {
        Gson gson = new Gson();
        String json = sharedPreferences.getString("key_user", null);
        Type type = new TypeToken<ArrayList<User>>() {
        }.getType();
        return gson.fromJson(json, type);
    }

    public void savePlaylistUrl(String playlistUrl) {
        editor.putString(KEY_PLAYLIST_URL, playlistUrl);
        editor.apply();
    }

    public String getPlaylistUrl() {
        return sharedPreferences.getString(KEY_PLAYLIST_URL, "");
    }

    public boolean hasPlaylistUrl() {
        String playlistUrl = getPlaylistUrl();
        return playlistUrl != null && !playlistUrl.trim().isEmpty();
    }

    public void savePlaylistChannels(List<Channel> channels) {
        Gson gson = new Gson();
        editor.putString(KEY_PLAYLIST_CHANNELS, gson.toJson(channels));
        editor.apply();
    }

    public List<Channel> getPlaylistChannels() {
        Gson gson = new Gson();
        String json = sharedPreferences.getString(KEY_PLAYLIST_CHANNELS, null);
        Type type = new TypeToken<ArrayList<Channel>>() {
        }.getType();
        List<Channel> channels = gson.fromJson(json, type);
        return channels == null ? new ArrayList<>() : channels;
    }

    public boolean hasPlaylistChannels() {
        return !getPlaylistChannels().isEmpty();
    }

    public void clearPlaylistChannels() {
        editor.remove(KEY_PLAYLIST_CHANNELS);
        editor.apply();
    }

    public List<Category> getPlaylistCategories() {
        List<Channel> channels = getPlaylistChannels();
        LinkedHashMap<String, Category> categoryMap = new LinkedHashMap<>();
        int index = 1;
        for (Channel channel : channels) {
            String categoryName = sanitizeCategoryName(channel.category_name);
            if (!categoryMap.containsKey(categoryName)) {
                Category category = new Category();
                category.cid = index++;
                category.category_name = categoryName;
                category.category_image = "";
                categoryMap.put(categoryName, category);
            }
        }
        return new ArrayList<>(categoryMap.values());
    }

    public List<Channel> getPlaylistChannelsByCategory(String categoryName) {
        List<Channel> result = new ArrayList<>();
        String expectedCategory = sanitizeCategoryName(categoryName);
        for (Channel channel : getPlaylistChannels()) {
            if (sanitizeCategoryName(channel.category_name).equalsIgnoreCase(expectedCategory)) {
                result.add(channel);
            }
        }
        return result;
    }

    public List<Channel> searchPlaylistChannels(String query) {
        List<Channel> result = new ArrayList<>();
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(Locale.US);
        if (normalizedQuery.isEmpty()) {
            return result;
        }
        for (Channel channel : getPlaylistChannels()) {
            String name = channel.channel_name == null ? "" : channel.channel_name.toLowerCase(Locale.US);
            String category = channel.category_name == null ? "" : channel.category_name.toLowerCase(Locale.US);
            if (name.contains(normalizedQuery) || category.contains(normalizedQuery)) {
                result.add(channel);
            }
        }
        return result;
    }

    private String sanitizeCategoryName(String categoryName) {
        if (TextUtils.isEmpty(categoryName)) {
            return "TV";
        }
        return categoryName.trim();
    }

    public void setGlobalPlaylistActive(boolean active) {
        editor.putBoolean(KEY_GLOBAL_PLAYLIST_ACTIVE, active);
        editor.apply();
    }

    public boolean isGlobalPlaylistActive() {
        return sharedPreferences.getBoolean(KEY_GLOBAL_PLAYLIST_ACTIVE, false);
    }

    public void setHidePlaylistSource(boolean hide) {
        editor.putBoolean(KEY_HIDE_PLAYLIST_SOURCE, hide);
        editor.apply();
    }

    public boolean shouldHidePlaylistSource() {
        return sharedPreferences.getBoolean(KEY_HIDE_PLAYLIST_SOURCE, false);
    }

    public void savePortalAccessKey(String accessKey) {
        editor.putString(KEY_PORTAL_ACCESS_KEY, accessKey == null ? "" : accessKey.trim());
        editor.apply();
    }

    public String getPortalAccessKey() {
        return sharedPreferences.getString(KEY_PORTAL_ACCESS_KEY, "");
    }

    public void savePortalSessionToken(String sessionToken) {
        editor.putString(KEY_PORTAL_SESSION_TOKEN, sessionToken == null ? "" : sessionToken.trim());
        editor.remove(KEY_PORTAL_ACCESS_KEY);
        editor.commit();
    }

    public String getPortalSessionToken() {
        return sharedPreferences.getString(KEY_PORTAL_SESSION_TOKEN, "");
    }

    public boolean isPortalSessionActive() {
        return !TextUtils.isEmpty(getPortalSessionToken());
    }

    public void savePortalPublicUrl(String publicUrl) {
        editor.putString(KEY_PORTAL_PUBLIC_URL, publicUrl == null ? "" : publicUrl.trim());
        editor.apply();
    }

    public String getPortalPublicUrl() {
        return sharedPreferences.getString(KEY_PORTAL_PUBLIC_URL, "");
    }

    public void savePortalStreamConfig(String host, String userAgent, String referer, String origin, String cookie) {
        editor.putString(KEY_PORTAL_STREAM_HOST, host == null ? "" : host.trim());
        editor.putString(KEY_PORTAL_STREAM_USER_AGENT, userAgent == null ? "" : userAgent.trim());
        editor.putString(KEY_PORTAL_STREAM_REFERER, referer == null ? "" : referer.trim());
        editor.putString(KEY_PORTAL_STREAM_ORIGIN, origin == null ? "" : origin.trim());
        editor.putString(KEY_PORTAL_STREAM_COOKIE, cookie == null ? "" : cookie.trim());
        editor.apply();
    }

    public void clearPortalStreamConfig() {
        editor.putString(KEY_PORTAL_STREAM_HOST, "");
        editor.putString(KEY_PORTAL_STREAM_USER_AGENT, "");
        editor.putString(KEY_PORTAL_STREAM_REFERER, "");
        editor.putString(KEY_PORTAL_STREAM_ORIGIN, "");
        editor.putString(KEY_PORTAL_STREAM_COOKIE, "");
        editor.apply();
    }

    public boolean isPortalStreamActive() {
        return false;
    }

    public String getPortalStreamHost() {
        return sharedPreferences.getString(KEY_PORTAL_STREAM_HOST, "");
    }

    public String getPortalStreamUserAgent() {
        return sharedPreferences.getString(KEY_PORTAL_STREAM_USER_AGENT, "");
    }

    public String getPortalStreamReferer() {
        return sharedPreferences.getString(KEY_PORTAL_STREAM_REFERER, "");
    }

    public String getPortalStreamOrigin() {
        return sharedPreferences.getString(KEY_PORTAL_STREAM_ORIGIN, "");
    }

    public String getPortalStreamCookie() {
        return sharedPreferences.getString(KEY_PORTAL_STREAM_COOKIE, "");
    }

    public boolean hasUserPlaylistBackup() {
        return !TextUtils.isEmpty(sharedPreferences.getString(KEY_USER_PLAYLIST_BACKUP_CHANNELS, ""));
    }

    public void backupUserPlaylist() {
        if (hasUserPlaylistBackup() || isGlobalPlaylistActive()) {
            return;
        }
        Gson gson = new Gson();
        editor.putString(KEY_USER_PLAYLIST_BACKUP_CHANNELS, gson.toJson(getPlaylistChannels()));
        editor.putString(KEY_USER_PLAYLIST_BACKUP_URL, getPlaylistUrl());
        editor.putString(KEY_USER_PLAYLIST_BACKUP_NAME, sharedPreferences.getString("playlist_name", ""));
        editor.apply();
    }

    public void restoreUserPlaylistBackup() {
        if (!hasUserPlaylistBackup()) {
            return;
        }
        Gson gson = new Gson();
        String json = sharedPreferences.getString(KEY_USER_PLAYLIST_BACKUP_CHANNELS, null);
        Type type = new TypeToken<ArrayList<Channel>>() {
        }.getType();
        List<Channel> channels = gson.fromJson(json, type);
        if (channels != null && !channels.isEmpty()) {
            savePlaylistChannels(channels);
        }
        String backupUrl = sharedPreferences.getString(KEY_USER_PLAYLIST_BACKUP_URL, "");
        if (!TextUtils.isEmpty(backupUrl)) {
            savePlaylistUrl(backupUrl);
        }
        clearUserPlaylistBackup();
    }

    public void clearUserPlaylistBackup() {
        editor.remove(KEY_USER_PLAYLIST_BACKUP_CHANNELS);
        editor.remove(KEY_USER_PLAYLIST_BACKUP_URL);
        editor.remove(KEY_USER_PLAYLIST_BACKUP_NAME);
        editor.apply();
    }

    public void savePlaylistName(String playlistName) {
        editor.putString("playlist_name", playlistName == null ? "" : playlistName.trim());
        editor.apply();
    }

    public String getPlaylistName() {
        return sharedPreferences.getString("playlist_name", "");
    }

    public void savePlayerBranding(boolean enabled, String brandText, String brandLogoUrl) {
        editor.putBoolean("player_brand_enabled", enabled);
        editor.putString("player_brand_text", brandText == null ? "" : brandText.trim());
        editor.putString("player_brand_logo_url", brandLogoUrl == null ? "" : brandLogoUrl.trim());
        editor.apply();
    }

    public boolean isPlayerBrandEnabled() {
        return sharedPreferences.getBoolean("player_brand_enabled", true);
    }

    public String getPlayerBrandText() {
        return sharedPreferences.getString("player_brand_text", "");
    }

    public String getPlayerBrandLogoUrl() {
        return sharedPreferences.getString("player_brand_logo_url", "");
    }

    public void saveSecuritySettings(
            boolean integrityEnabled,
            String allowedCertSha256,
            String adsAntiblockScript,
            boolean serverAdsEnabled
    ) {
        editor.putBoolean("app_integrity_enabled", integrityEnabled);
        editor.putString("app_release_cert_sha256", allowedCertSha256 == null ? "" : allowedCertSha256.trim());
        editor.putString("ads_antiblock_script", adsAntiblockScript == null ? "" : adsAntiblockScript.trim());
        editor.putBoolean("server_ads_enabled", serverAdsEnabled);
        editor.apply();
    }

    public boolean isAppIntegrityEnabled() {
        return sharedPreferences.getBoolean("app_integrity_enabled", true);
    }

    public String getAllowedReleaseCertSha256() {
        return sharedPreferences.getString("app_release_cert_sha256", "");
    }

    public String getAdsAntiblockScript() {
        return sharedPreferences.getString("ads_antiblock_script", "");
    }

    public void saveAdsAntiblockScript(String script) {
        editor.putString("ads_antiblock_script", script == null ? "" : script.trim());
        editor.apply();
    }

    public boolean isServerAdsEnabled() {
        return sharedPreferences.getBoolean("server_ads_enabled", true);
    }

}
