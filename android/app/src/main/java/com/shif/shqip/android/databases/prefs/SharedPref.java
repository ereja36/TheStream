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

    Context context;
    SharedPreferences sharedPreferences;
    SharedPreferences.Editor editor;

    public SharedPref(Context context) {
        this.context = context;
        sharedPreferences = context.getSharedPreferences(BuildConfig.APPLICATION_ID + "_" + "shared_preferences", Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
    }

    public void saveDesignId(String designId) {
        String normalized = designId == null ? "v1" : designId.trim().toLowerCase();
        if (!"v2".equals(normalized) && !"v3".equals(normalized)) {
            normalized = "v1";
        }
        editor.putString(KEY_DESIGN_ID, normalized);
        editor.apply();
        if ("v2".equals(normalized) || "v3".equals(normalized)) {
            setIsDarkTheme(true);
        }
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

    public Boolean getIsDarkTheme() {
        String designId = getDesignId();
        if ("v2".equals(designId) || "v3".equals(designId)) {
            return true;
        }
        return sharedPreferences.getBoolean("theme", Config.SET_DARK_MODE_AS_DEFAULT_THEME);
    }

    public void setIsDarkTheme(Boolean isDarkTheme) {
        editor.putBoolean("theme", isDarkTheme);
        editor.apply();
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

    public void saveCredentials(String youtube_api_key, String more_apps_url, String privacy_policy, String redirect_url, String pushNotificationProvider) {
        editor.putString("youtube_api_key", youtube_api_key);
        editor.putString("more_apps_url", more_apps_url);
        editor.putString("privacy_policy", privacy_policy);
        editor.putString("redirect_url", redirect_url);
        editor.putString("providers", pushNotificationProvider);
        editor.apply();
    }

    public String getYoutubeAPIKey() {
        return sharedPreferences.getString("youtube_api_key", "0");
    }

    public String getPrivacyPolicy() {
        return sharedPreferences.getString("privacy_policy", "");
    }

    public String getMoreAppsUrl() {
        return sharedPreferences.getString("more_apps_url", "https://play.google.com/store/apps/developer?id=Solodroid");
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

}
