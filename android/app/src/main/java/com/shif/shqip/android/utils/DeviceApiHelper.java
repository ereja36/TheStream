package com.shif.shqip.android.utils;

import android.content.Context;
import android.net.Uri;
import android.provider.Settings;
import android.text.TextUtils;

import com.shif.shqip.android.BuildConfig;
import com.shif.shqip.android.Config;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.models.Channel;
import com.shif.shqip.android.models.PlaylistGuardRule;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DeviceApiHelper {

    private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile("([a-zA-Z0-9\\-]+)=\"([^\"]*)\"");
    private static final Pattern PORTAL_PLAYLIST_URL_PATTERN = Pattern.compile("class\\s*=\\s*\"pl-url\"[^>]*>(.*?)<", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern GENERIC_HTTP_URL_PATTERN = Pattern.compile("https?://[^\\s\"'<>]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern TVG_URL_PATTERN = Pattern.compile("(?:url-tvg|x-tvg-url)=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    private DeviceApiHelper() {
        // no-op
    }

    public static class DeviceProfile {
        public boolean blocked = false;
        public String reason = "";
        public boolean noAds = false;
    }

    public static class RegisterResult {
        public boolean success = false;
        public boolean blocked = false;
        public String reason = "";
        public String message = "";
    }

    public static class PopupConfig {
        public boolean enabled = false;
        public String title = "";
        public String message = "";
        public String popupType = "text";
        public String mediaUrl = "";
        public String targetUrl = "";
        public int durationSeconds = 0;
        public boolean allowManualClose = true;
    }

    public static class PlaylistSyncResult {
        public boolean blocked = false;
        public String reason = "";
        public List<Channel> channels = new ArrayList<>();
        public int playlistCount = 0;
    }

    public static class DevicePlaylist {
        public String playlistName = "";
        public String playlistType = "";
        public String playlistSource = "";
        public int status = 1;
    }

    public static String getDisplayDeviceId(Context context) {
        String deviceId = getLocalDeviceId(context);
        if (TextUtils.isEmpty(deviceId)) {
            return "UNKNOWN";
        }
        String normalized = deviceId.replace("-", "").toUpperCase(Locale.US);
        if (normalized.length() >= 8) {
            return normalized.substring(0, 8);
        }
        return normalized;
    }

    public static String getLocalDeviceId(Context context) {
        String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (TextUtils.isEmpty(androidId)) {
            return "android-device";
        }
        return androidId;
    }

    public static DeviceProfile fetchDeviceProfile(SharedPref sharedPref, String deviceId) {
        DeviceProfile profile = new DeviceProfile();
        if (TextUtils.isEmpty(deviceId)) {
            return profile;
        }
        try {
            String endpoint = buildApiUrl(
                    sharedPref.getBaseUrl(),
                    "/api/api.php?get_device_profile&api_key=" + urlEncode(Config.REST_API_KEY) +
                            "&device_id=" + urlEncode(deviceId)
            );
            String response = fetchUrlContent(endpoint, "GET", null, deviceId, BuildConfig.APPLICATION_ID);
            JSONObject jsonObject = new JSONObject(response);
            profile.blocked = jsonObject.optInt("blocked", 0) == 1;
            profile.reason = jsonObject.optString("reason", "");
            profile.noAds = false;
        } catch (Exception ignored) {
            // fallback to legacy endpoint
            try {
                String endpoint = buildApiUrl(
                        sharedPref.getBaseUrl(),
                        "/api/api.php?check_device_status&api_key=" + urlEncode(Config.REST_API_KEY) +
                                "&device_id=" + urlEncode(deviceId)
                );
                String response = fetchUrlContent(endpoint, "GET", null, deviceId, BuildConfig.APPLICATION_ID);
                JSONObject jsonObject = new JSONObject(response);
                profile.blocked = jsonObject.optInt("blocked", 0) == 1;
                profile.reason = jsonObject.optString("reason", "");
                profile.noAds = false;
            } catch (Exception ignoredToo) {
                profile.blocked = false;
                profile.reason = "";
                profile.noAds = false;
            }
        }
        return profile;
    }

    public static void refreshPlaylistGuardRules(SharedPref sharedPref) {
        refreshPlaylistPortalConfig(sharedPref);
        if (sharedPref != null && !TextUtils.isEmpty(sharedPref.getPortalAccessKey())) {
            sharedPref.savePlaylistGuardRules(new ArrayList<>());
            sharedPref.clearActiveGuardRule();
            return;
        }
        if (sharedPref == null || TextUtils.isEmpty(sharedPref.getBaseUrl())) {
            return;
        }
        try {
            String endpoint = buildApiUrl(
                    sharedPref.getBaseUrl(),
                    "/api/api.php?get_playlist_guard&api_key=" + urlEncode(Config.REST_API_KEY)
                            + "&_=" + System.currentTimeMillis()
            );
            String response = fetchUrlContent(endpoint, "GET", null, "", BuildConfig.APPLICATION_ID, null);
            JSONObject json = new JSONObject(response);
            if (!"ok".equals(json.optString("status"))) {
                return;
            }
            JSONArray array = json.optJSONArray("rules");
            List<PlaylistGuardRule> rules = new ArrayList<>();
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    PlaylistGuardRule rule = new PlaylistGuardRule();
                    rule.url_pattern = readJsonString(item, "url_pattern");
                    rule.stream_url_pattern = readJsonString(item, "stream_url_pattern");
                    rule.referer = readJsonString(item, "referer");
                    rule.origin = readJsonString(item, "origin");
                    rule.user_agent = readJsonString(item, "user_agent");
                    rule.cookie = readJsonString(item, "cookie");
                    rule.extra_headers = readJsonString(item, "extra_headers");
                    if (!TextUtils.isEmpty(rule.url_pattern)) {
                        rules.add(rule);
                    }
                }
            }
            sharedPref.savePlaylistGuardRules(rules);
            PlaylistGuardHelper.reconcileActiveGuardRule(sharedPref);
        } catch (Exception ignored) {
            PlaylistGuardHelper.reconcileActiveGuardRule(sharedPref);
        }
    }

    public static void refreshPlaylistPortalConfig(SharedPref sharedPref) {
        if (sharedPref == null || TextUtils.isEmpty(sharedPref.getBaseUrl())) {
            return;
        }
        try {
            String endpoint = buildApiUrl(
                    sharedPref.getBaseUrl(),
                    "/api/api.php?get_playlist_portal&api_key=" + urlEncode(Config.REST_API_KEY)
                            + "&_=" + System.currentTimeMillis()
            );
            String response = fetchUrlContent(endpoint, "GET", null, "", BuildConfig.APPLICATION_ID, sharedPref);
            JSONObject json = new JSONObject(response);
            if (!"ok".equals(json.optString("status"))) {
                return;
            }
            JSONObject portal = json.optJSONObject("portal");
            if (portal == null) {
                return;
            }
            sharedPref.savePortalPublicUrl(PlaylistPortalHelper.PUBLIC_PORTAL_URL);
            if (portal.optInt("enabled", 0) == 1) {
                sharedPref.savePortalAccessKey(portal.optString("access_key", ""));
                sharedPref.savePortalStreamConfig(
                        portal.optString("stream_host", ""),
                        portal.optString("stream_user_agent", ""),
                        portal.optString("stream_referer", ""),
                        portal.optString("stream_origin", ""),
                        portal.optString("stream_cookie", "")
                );
            } else {
                sharedPref.savePortalAccessKey("");
                sharedPref.clearPortalStreamConfig();
            }
        } catch (Exception ignored) {
            // keep cached portal key
        }
    }

    public static void refreshPlaylistGuardRulesAsync(SharedPref sharedPref) {
        PlaylistGuardSession.refreshAsync(sharedPref);
    }

    private static String readJsonString(JSONObject item, String key) {
        if (item == null || TextUtils.isEmpty(key) || !item.has(key) || item.isNull(key)) {
            return "";
        }
        Object value = item.opt(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    public static RegisterResult registerDevicePlaylist(SharedPref sharedPref, String deviceId, String playlistSource, String playlistName, int channelsCount, String playlistType) {
        RegisterResult result = new RegisterResult();
        HttpURLConnection connection = null;
        try {
            String endpoint = buildApiUrl(sharedPref.getBaseUrl(), "/api/api.php?register_device_playlist");
            URL url = new URL(endpoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            String payload = "api_key=" + urlEncode(Config.REST_API_KEY)
                    + "&device_id=" + urlEncode(deviceId)
                    + "&playlist_name=" + urlEncode(TextUtils.isEmpty(playlistName) ? "Default Playlist" : playlistName)
                    + "&playlist_type=" + urlEncode(TextUtils.isEmpty(playlistType) ? "m3u" : playlistType)
                    + "&playlist_source=" + urlEncode(playlistSource)
                    + "&channels_count=" + channelsCount
                    + "&app_version=" + urlEncode(BuildConfig.VERSION_NAME)
                    + "&status=1";
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(payload.getBytes(StandardCharsets.UTF_8));
            }
            int responseCode = connection.getResponseCode();
            InputStream inputStream = responseCode >= 200 && responseCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String body = inputStream == null ? "" : readAll(inputStream);
            JSONObject json = new JSONObject(body);
            result.success = "ok".equalsIgnoreCase(json.optString("status", ""));
            result.blocked = json.optInt("blocked", 0) == 1;
            result.reason = json.optString("reason", "");
            result.message = json.optString("msg", "");
        } catch (Exception e) {
            result.success = false;
            result.message = e.getMessage() == null ? "Sync failed" : e.getMessage();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return result;
    }

    public static PopupConfig fetchStartupPopup(SharedPref sharedPref, String deviceId) {
        PopupConfig config = new PopupConfig();
        try {
            String endpoint = buildApiUrl(
                    sharedPref.getBaseUrl(),
                    "/api/api.php?get_startup_popup&api_key=" + urlEncode(Config.REST_API_KEY) +
                            "&device_id=" + urlEncode(deviceId)
            );
            String response = fetchUrlContent(endpoint, "GET", null, deviceId, BuildConfig.APPLICATION_ID);
            JSONObject json = new JSONObject(response);
            if (!"ok".equalsIgnoreCase(json.optString("status", ""))) {
                return config;
            }
            JSONObject popup = json.optJSONObject("popup");
            if (popup == null) {
                return config;
            }
            config.enabled = popup.optInt("status", 0) == 1;
            config.title = popup.optString("title", "");
            config.message = popup.optString("message", "");
            config.popupType = popup.optString("popup_type", "text");
            config.mediaUrl = popup.optString("media_url", "");
            config.targetUrl = popup.optString("target_url", "");
            config.durationSeconds = popup.optInt("duration_seconds", 0);
            config.allowManualClose = popup.optInt("allow_manual_close", 1) == 1;
        } catch (Exception ignored) {
            // Keep disabled popup.
        }
        return config;
    }

    public static PlaylistSyncResult refreshPlaylistChannelsFromApi(SharedPref sharedPref, String deviceId) {
        PlaylistSyncResult syncResult = new PlaylistSyncResult();
        try {
            PlaylistGuardSession.refreshBlocking(sharedPref);
            DeviceProfile profile = fetchDeviceProfile(sharedPref, deviceId);
            if (profile.blocked) {
                syncResult.blocked = true;
                syncResult.reason = profile.reason;
                sharedPref.clearPlaylistChannels();
                return syncResult;
            }

            PlaylistFetchResult fetchResult = fetchDevicePlaylistsWithMeta(sharedPref, deviceId);
            List<DevicePlaylist> playlists = fetchResult.playlists;
            syncResult.playlistCount = playlists.size();

            if (fetchResult.globalOverride) {
                if (!sharedPref.isGlobalPlaylistActive()) {
                    sharedPref.backupUserPlaylist();
                    sharedPref.setGlobalPlaylistActive(true);
                }
                sharedPref.setHidePlaylistSource(fetchResult.hideSource);
            } else if (sharedPref.isGlobalPlaylistActive()) {
                sharedPref.restoreUserPlaylistBackup();
                sharedPref.setGlobalPlaylistActive(false);
                sharedPref.setHidePlaylistSource(false);
                playlists = fetchResult.playlists;
                syncResult.playlistCount = playlists.size();
            }

            if (playlists.isEmpty()) {
                if (!sharedPref.hasPlaylistChannels()) {
                    sharedPref.clearPlaylistChannels();
                }
                return syncResult;
            }

            List<Channel> merged = new ArrayList<>();
            Set<String> urls = new LinkedHashSet<>();
            String firstSource = "";
            String firstName = "";

            for (DevicePlaylist playlist : playlists) {
                if (playlist.status != 1) {
                    continue;
                }
                String source = playlist.playlistSource == null ? "" : playlist.playlistSource.trim();
                if (TextUtils.isEmpty(source) || source.startsWith("content://")) {
                    continue;
                }
                source = PlaylistPortalHelper.resolvePortalUrl(source, sharedPref);
                if (TextUtils.isEmpty(firstSource)) {
                    firstSource = source;
                    firstName = playlist.playlistName;
                }

                List<Channel> parsedChannels = new ArrayList<>();
                try {
                    String content = fetchUrlContent(source, "GET", null, deviceId, BuildConfig.APPLICATION_ID, sharedPref);
                    parsedChannels = parsePlaylistContent(content, playlist.playlistName, source);
                    if (parsedChannels.isEmpty() && isPortalPage(content)) {
                        parsedChannels = fetchChannelsFromPortal(sharedPref, source, content, deviceId, playlist.playlistName);
                    }
                    applyJsonPlaylistGuardRule(sharedPref, content, source);
                } catch (Exception ignored) {
                    // Keep trying other playlist sources.
                }

                for (Channel channel : parsedChannels) {
                    String key = normalizeUrl(channel.channel_url);
                    if (!TextUtils.isEmpty(key) && urls.add(key)) {
                        merged.add(channel);
                    }
                }
            }

            resequenceChannelIds(merged);
            syncResult.channels = merged;
            if (!merged.isEmpty()) {
                sharedPref.savePlaylistChannels(merged);
                if (!fetchResult.hideSource && !TextUtils.isEmpty(firstSource)) {
                    sharedPref.savePlaylistUrl(firstSource);
                }
                if (!TextUtils.isEmpty(firstName)) {
                    sharedPref.savePlaylistName(firstName);
                }
            }
        } catch (Exception ignored) {
            // Keep previous local cache on unexpected errors.
        }
        return syncResult;
    }

    public static class PlaylistFetchResult {
        public List<DevicePlaylist> playlists = new ArrayList<>();
        public boolean globalOverride = false;
        public boolean hideSource = false;
    }

    public static PlaylistFetchResult fetchDevicePlaylistsWithMeta(SharedPref sharedPref, String deviceId) throws Exception {
        PlaylistFetchResult result = new PlaylistFetchResult();
        String endpoint = buildApiUrl(
                sharedPref.getBaseUrl(),
                "/api/api.php?get_device_playlists&api_key=" + urlEncode(Config.REST_API_KEY) +
                        "&device_id=" + urlEncode(deviceId)
        );
        String response = fetchUrlContent(endpoint, "GET", null, deviceId, BuildConfig.APPLICATION_ID);
        JSONObject json = new JSONObject(response);

        if (json.optInt("blocked", 0) == 1) {
            return result;
        }
        if (!"ok".equalsIgnoreCase(json.optString("status", ""))) {
            return result;
        }

        result.globalOverride = json.optInt("global_override", 0) == 1;
        result.hideSource = json.optInt("hide_source", 0) == 1;

        JSONArray array = json.optJSONArray("playlists");
        if (array == null) {
            return result;
        }
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                continue;
            }
            DevicePlaylist playlist = new DevicePlaylist();
            playlist.playlistName = item.optString("playlist_name", "");
            playlist.playlistType = item.optString("playlist_type", "");
            playlist.playlistSource = item.optString("playlist_source", "");
            playlist.status = item.optInt("status", 1);
            result.playlists.add(playlist);
        }
        return result;
    }

    public static List<DevicePlaylist> fetchDevicePlaylists(SharedPref sharedPref, String deviceId) throws Exception {
        return fetchDevicePlaylistsWithMeta(sharedPref, deviceId).playlists;
    }

    public static List<Channel> parsePlaylistContent(String content, String playlistName, String sourceUrl) {
        if (isJsonPlaylistIndex(content)) {
            return parseJsonPlaylistIndex(content, playlistName);
        }
        return parsePlaylistChannels(content, playlistName);
    }

    private static boolean isJsonPlaylistIndex(String content) {
        if (TextUtils.isEmpty(content)) {
            return false;
        }
        String trimmed = content.trim();
        if (!trimmed.startsWith("{")) {
            return false;
        }
        try {
            JSONObject json = new JSONObject(trimmed);
            return json.optBoolean("ok", false) && json.has("channels");
        } catch (Exception ignored) {
            return false;
        }
    }

    public static List<Channel> parseJsonPlaylistIndex(String content, String playlistName) {
        List<Channel> channels = new ArrayList<>();
        if (TextUtils.isEmpty(content)) {
            return channels;
        }
        try {
            JSONObject json = new JSONObject(content.trim());
            JSONArray array = json.optJSONArray("channels");
            if (array == null) {
                return channels;
            }
            String categoryLabel = TextUtils.isEmpty(playlistName) ? "TV" : playlistName + " • TV";
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String name = item.optString("name", "");
                String url = item.optString("url", "");
                if (TextUtils.isEmpty(name) || TextUtils.isEmpty(url)) {
                    continue;
                }
                Channel channel = new Channel();
                channel.channel_id = "json_" + (i + 1);
                channel.channel_name = name.trim();
                channel.channel_image = "";
                channel.channel_url = url.trim();
                channel.channel_description = "";
                channel.channel_type = "JSON";
                channel.video_id = "";
                channel.category_name = categoryLabel;
                channel.user_agent = "default";
                channel.total_views = "0";
                channel.epg_id = "";
                channel.epg_url = "";
                channels.add(channel);
            }
        } catch (Exception ignored) {
            return channels;
        }
        return channels;
    }

    public static void applyJsonPlaylistGuardRule(SharedPref sharedPref, String content, String sourceUrl) {
        if (sharedPref == null || TextUtils.isEmpty(content) || !content.trim().startsWith("{")) {
            return;
        }
        try {
            JSONObject json = new JSONObject(content.trim());
            JSONObject headers = json.optJSONObject("required_headers");
            if (headers == null) {
                return;
            }
            PlaylistGuardRule rule = new PlaylistGuardRule();
            rule.url_pattern = sourceUrl;
            rule.referer = readJsonString(headers, "referer");
            rule.origin = readJsonString(headers, "origin");
            PlaylistGuardHelper.rememberRuleForPlaylist(sharedPref, sourceUrl, rule);
        } catch (Exception ignored) {
            // ignore malformed json
        }
    }

    public static List<Channel> parsePlaylistChannels(String content, String playlistName) {
        List<Channel> channels = new ArrayList<>();
        if (TextUtils.isEmpty(content)) {
            return channels;
        }

        String[] lines = content.split("\\r?\\n");
        String extInfoLine = "";
        String userAgent = "default";
        String epgUrl = extractGlobalEpgUrl(content);
        int index = 0;

        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }

            if (line.startsWith("#EXTM3U") && TextUtils.isEmpty(epgUrl)) {
                epgUrl = extractTvgUrl(line);
                continue;
            }

            if (line.startsWith("#EXTINF")) {
                extInfoLine = line;
                continue;
            }

            if (line.startsWith("#EXTVLCOPT:http-user-agent=")) {
                String value = line.substring("#EXTVLCOPT:http-user-agent=".length()).trim();
                if (!TextUtils.isEmpty(value)) {
                    userAgent = value;
                }
                continue;
            }

            if (line.startsWith("#")) {
                continue;
            }

            if (!isPlayableStreamUrl(line)) {
                continue;
            }

            index++;
            Channel channel = buildChannel(extInfoLine, line, userAgent, index, playlistName, epgUrl);
            channels.add(channel);
            extInfoLine = "";
            userAgent = "default";
        }

        return channels;
    }

    private static Channel buildChannel(String extInfoLine, String streamUrl, String userAgent, int index, String playlistName, String epgUrl) {
        Channel channel = new Channel();
        String channelName = extractChannelName(extInfoLine);
        if (TextUtils.isEmpty(channelName)) {
            channelName = "Channel " + index;
        }
        String categoryName = extractAttribute(extInfoLine, "group-title");
        if (TextUtils.isEmpty(categoryName)) {
            categoryName = "TV";
        }
        String tvgId = extractAttribute(extInfoLine, "tvg-id");
        if (TextUtils.isEmpty(tvgId)) {
            tvgId = extractAttribute(extInfoLine, "tvg-name");
        }

        channel.channel_id = "m3u_" + index;
        channel.channel_name = channelName;
        channel.channel_image = extractAttribute(extInfoLine, "tvg-logo");
        channel.channel_url = streamUrl;
        channel.channel_description = "";
        channel.channel_type = "M3U";
        channel.video_id = "";
        channel.category_name = buildCategoryName(categoryName.trim(), playlistName);
        channel.user_agent = TextUtils.isEmpty(userAgent) ? "default" : userAgent;
        channel.total_views = "0";
        channel.epg_id = TextUtils.isEmpty(tvgId) ? "" : tvgId.trim();
        channel.epg_url = TextUtils.isEmpty(epgUrl) ? "" : epgUrl.trim();
        return channel;
    }

    private static List<Channel> fetchChannelsFromPortal(SharedPref sharedPref, String portalUrl, String portalHtml, String deviceId, String playlistName) throws IOException {
        List<Channel> channels = new ArrayList<>();
        List<String> playlistUrls = extractPlaylistUrlsFromPortal(portalHtml);
        if (playlistUrls.isEmpty()) {
            String body = "action=lookup&device_id=" + urlEncode(deviceId);
            String lookupHtml = fetchUrlContent(portalUrl, "POST", body, deviceId, BuildConfig.APPLICATION_ID, sharedPref);
            playlistUrls = extractPlaylistUrlsFromPortal(lookupHtml);
        }

        int index = 0;
        for (String playlistUrl : playlistUrls) {
            try {
                String m3uContent = fetchUrlContent(playlistUrl, "GET", null, deviceId, BuildConfig.APPLICATION_ID, sharedPref);
                String sourceLabel = TextUtils.isEmpty(playlistName) ? "Portal" : playlistName;
                List<Channel> parsed = parsePlaylistChannels(m3uContent, sourceLabel);
                for (Channel channel : parsed) {
                    index++;
                    channel.channel_id = "m3u_" + index;
                    channels.add(channel);
                }
            } catch (Exception ignored) {
                // keep trying next portal source
            }
        }
        return channels;
    }

    private static List<String> extractPlaylistUrlsFromPortal(String html) {
        Set<String> urls = new LinkedHashSet<>();
        Matcher playlistMatcher = PORTAL_PLAYLIST_URL_PATTERN.matcher(html);
        while (playlistMatcher.find()) {
            String raw = playlistMatcher.group(1);
            if (raw == null) {
                continue;
            }
            String cleaned = raw.replace("&amp;", "&").trim();
            if (isPlayableStreamUrl(cleaned)) {
                urls.add(cleaned);
            }
        }

        if (!urls.isEmpty()) {
            return new ArrayList<>(urls);
        }

        Matcher genericMatcher = GENERIC_HTTP_URL_PATTERN.matcher(html);
        while (genericMatcher.find()) {
            String url = genericMatcher.group().trim();
            if (isLikelyPlaylistUrl(url)) {
                urls.add(url);
            }
        }
        return new ArrayList<>(urls);
    }

    private static boolean isLikelyPlaylistUrl(String url) {
        String lower = url.toLowerCase(Locale.US);
        return lower.contains(".m3u")
                || lower.contains("m3u8")
                || lower.contains("get.php")
                || lower.contains("player_api")
                || lower.contains("type=m3u")
                || lower.contains("output=m3u")
                || lower.contains("playlist");
    }

    private static boolean isPortalPage(String content) {
        if (TextUtils.isEmpty(content)) {
            return false;
        }
        String lower = content.toLowerCase(Locale.US);
        return lower.contains("<html") && lower.contains("device id") && lower.contains("m3u");
    }

    private static boolean isPlayableStreamUrl(String url) {
        String lower = url.toLowerCase(Locale.US);
        return lower.startsWith("http://")
                || lower.startsWith("https://")
                || lower.startsWith("rtsp://")
                || lower.startsWith("rtmp://")
                || lower.startsWith("udp://")
                || lower.startsWith("mms://");
    }

    private static String extractGlobalEpgUrl(String content) {
        String[] lines = content.split("\\r?\\n");
        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.startsWith("#EXTM3U")) {
                String epgUrl = extractTvgUrl(line);
                if (!TextUtils.isEmpty(epgUrl)) {
                    return epgUrl;
                }
            }
        }
        return "";
    }

    private static String extractTvgUrl(String line) {
        if (TextUtils.isEmpty(line)) {
            return "";
        }
        Matcher matcher = TVG_URL_PATTERN.matcher(line);
        if (matcher.find()) {
            return matcher.group(1) == null ? "" : matcher.group(1).trim();
        }
        return "";
    }

    private static String extractChannelName(String extInfoLine) {
        if (TextUtils.isEmpty(extInfoLine)) {
            return "";
        }
        int commaIndex = extInfoLine.indexOf(',');
        if (commaIndex == -1 || commaIndex >= extInfoLine.length() - 1) {
            String tvgName = extractAttribute(extInfoLine, "tvg-name");
            return TextUtils.isEmpty(tvgName) ? "" : tvgName.trim();
        }
        return extInfoLine.substring(commaIndex + 1).trim();
    }

    private static String extractAttribute(String line, String key) {
        if (TextUtils.isEmpty(line)) {
            return "";
        }
        Matcher matcher = ATTRIBUTE_PATTERN.matcher(line);
        while (matcher.find()) {
            String attrName = matcher.group(1);
            if (key.equalsIgnoreCase(attrName)) {
                String value = matcher.group(2);
                return value == null ? "" : value.trim();
            }
        }
        return "";
    }

    private static String buildCategoryName(String categoryName, String playlistName) {
        if (TextUtils.isEmpty(playlistName)) {
            return categoryName;
        }
        return playlistName + " • " + categoryName;
    }

    private static void resequenceChannelIds(List<Channel> channels) {
        for (int i = 0; i < channels.size(); i++) {
            channels.get(i).channel_id = "m3u_" + (i + 1);
        }
    }

    private static String normalizeUrl(String url) {
        return url == null ? "" : url.trim().toLowerCase(Locale.US);
    }

    public static String buildApiUrl(String baseUrl, String path) {
        if (TextUtils.isEmpty(baseUrl)) {
            return path;
        }
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalizedBaseUrl + path;
    }

    public static String fetchUrlContent(String urlString, String method, String requestBody, String deviceId, String packageName) throws IOException {
        return fetchUrlContent(urlString, method, requestBody, deviceId, packageName, null);
    }

    public static String fetchUrlContent(String urlString, String method, String requestBody, String deviceId, String packageName, SharedPref sharedPref) throws IOException {
        HttpURLConnection connection = null;
        try {
            String resolvedUrl = PlaylistPortalHelper.resolvePortalUrl(urlString, sharedPref);
            URL url = new URL(resolvedUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            connection.setRequestMethod(method);
            connection.setRequestProperty("User-Agent", buildAppUserAgent());
            connection.setRequestProperty("Accept", "*/*");
            connection.setRequestProperty("X-Requested-With", packageName);
            connection.setRequestProperty("X-Shif-App", "1");
            if (!TextUtils.isEmpty(deviceId)) {
                connection.setRequestProperty("X-Device-ID", deviceId);
            }
            PlaylistPortalHelper.applyPortalHeaders(connection, resolvedUrl, deviceId, sharedPref);

            if (!PlaylistPortalHelper.isPortalUrl(resolvedUrl)) {
                PlaylistGuardRule guardRule = PlaylistGuardHelper.resolveRule(sharedPref, resolvedUrl);
                if (guardRule == null && sharedPref != null) {
                    guardRule = PlaylistGuardHelper.findMatchingRule(resolvedUrl, sharedPref.getPlaylistGuardRules());
                }
                if (guardRule != null) {
                    PlaylistGuardHelper.applyRuleHeaders(connection, guardRule);
                    PlaylistGuardHelper.rememberRuleIfMatched(sharedPref, resolvedUrl);
                }
            }

            if ("POST".equalsIgnoreCase(method) && requestBody != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                try (OutputStream outputStream = connection.getOutputStream()) {
                    outputStream.write(requestBody.getBytes(StandardCharsets.UTF_8));
                }
            }

            int responseCode = connection.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("HTTP " + responseCode);
            }
            return readAll(connection.getInputStream());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readAll(InputStream inputStream) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }

    private static String urlEncode(String value) {
        return Uri.encode(value == null ? "" : value);
    }

    public static String buildAppUserAgent() {
        return "ShifShqip/" + BuildConfig.VERSION_NAME + " (Android)";
    }

    public static void reportDeviceOpen(SharedPref sharedPref, String deviceId) {
        if (sharedPref == null || TextUtils.isEmpty(deviceId)) {
            return;
        }
        try {
            String endpoint = buildApiUrl(sharedPref.getBaseUrl(), "/api/api.php?report_device_open");
            String payload = "api_key=" + urlEncode(Config.REST_API_KEY)
                    + "&device_id=" + urlEncode(deviceId)
                    + "&app_version=" + urlEncode(BuildConfig.VERSION_NAME);
            fetchUrlContent(endpoint, "POST", payload, deviceId, BuildConfig.APPLICATION_ID, sharedPref);
        } catch (Exception ignored) {
            // non-blocking telemetry
        }
    }

    public static class InboxNotification {
        public long id;
        public String title = "";
        public String message = "";
        public String targetUrl = "";
    }

    public static List<InboxNotification> fetchInboxNotifications(SharedPref sharedPref, String deviceId) {
        List<InboxNotification> notifications = new ArrayList<>();
        if (sharedPref == null || TextUtils.isEmpty(deviceId)) {
            return notifications;
        }
        try {
            String endpoint = buildApiUrl(
                    sharedPref.getBaseUrl(),
                    "/api/api.php?get_inbox_notifications&api_key=" + urlEncode(Config.REST_API_KEY)
                            + "&device_id=" + urlEncode(deviceId)
            );
            String response = fetchUrlContent(endpoint, "GET", null, deviceId, BuildConfig.APPLICATION_ID, sharedPref);
            JSONObject json = new JSONObject(response);
            if (!"ok".equalsIgnoreCase(json.optString("status", ""))) {
                return notifications;
            }
            JSONArray array = json.optJSONArray("notifications");
            if (array == null) {
                return notifications;
            }
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                InboxNotification notification = new InboxNotification();
                notification.id = item.optLong("id", 0);
                notification.title = item.optString("title", "");
                notification.message = item.optString("message", "");
                notification.targetUrl = item.optString("target_url", "");
                if (notification.id > 0) {
                    notifications.add(notification);
                }
            }
        } catch (Exception ignored) {
            // ignore polling errors
        }
        return notifications;
    }

    public static void ackInboxNotification(SharedPref sharedPref, String deviceId, long notificationId, boolean opened) {
        if (sharedPref == null || TextUtils.isEmpty(deviceId) || notificationId <= 0) {
            return;
        }
        try {
            String endpoint = buildApiUrl(sharedPref.getBaseUrl(), "/api/api.php?ack_inbox_notification");
            String payload = "api_key=" + urlEncode(Config.REST_API_KEY)
                    + "&device_id=" + urlEncode(deviceId)
                    + "&notification_id=" + notificationId
                    + "&opened=" + (opened ? 1 : 0);
            fetchUrlContent(endpoint, "POST", payload, deviceId, BuildConfig.APPLICATION_ID, sharedPref);
        } catch (Exception ignored) {
            // ignore ack errors
        }
    }
}
