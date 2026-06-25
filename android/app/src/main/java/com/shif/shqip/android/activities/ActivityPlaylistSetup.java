package com.shif.shqip.android.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.OnBackPressedDispatcher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.text.HtmlCompat;

import com.shif.shqip.android.BuildConfig;
import com.shif.shqip.android.Config;
import com.shif.shqip.android.R;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.models.Channel;
import com.shif.shqip.android.utils.DesignRouter;
import com.shif.shqip.android.utils.DeviceApiHelper;
import com.shif.shqip.android.utils.Tools;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;

public class ActivityPlaylistSetup extends AppCompatActivity {

    private static final int REQUEST_CODE_PICK_FILE = 2001;
    private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile("([a-zA-Z0-9\\-]+)=\"([^\"]*)\"");
    private static final Pattern PORTAL_PLAYLIST_URL_PATTERN = Pattern.compile("class\\s*=\\s*\"pl-url\"[^>]*>(.*?)<", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern GENERIC_HTTP_URL_PATTERN = Pattern.compile("https?://[^\\s\"'<>]+", Pattern.CASE_INSENSITIVE);
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private SharedPref sharedPref;
    private OnBackPressedDispatcher onBackPressedDispatcher;
    private String deviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Tools.getTheme(this);
        setContentView(R.layout.activity_playlist_setup);
        Tools.setNavigation(this);
        sharedPref = new SharedPref(this);
        deviceId = DeviceApiHelper.getLocalDeviceId(this);

        setupToolbar();
        setupDeviceInfo();
        setupButtons();
        checkDeviceBlockedOnLaunch();
        handleOnBackPressed();
        handleOpenModeIntent();
        executorService.execute(() -> DeviceApiHelper.refreshPlaylistGuardRules(sharedPref));
    }

    private void handleOpenModeIntent() {
        String openMode = getIntent().getStringExtra("open_mode");
        if (TextUtils.isEmpty(openMode)) {
            return;
        }
        new Handler(Looper.getMainLooper()).post(() -> {
            switch (openMode) {
                case "play_url":
                    showStreamUrlDialog();
                    break;
                case "add_url":
                    showPlaylistUrlDialog();
                    break;
                case "open_file":
                    openPlaylistFile();
                    break;
                default:
                    break;
            }
        });
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        Tools.setupToolbar(this, toolbar, getString(R.string.playlist_setup_title), true);
    }

    private void setupDeviceInfo() {
        TextView txtDeviceId = findViewById(R.id.txt_device_id);
        txtDeviceId.setText(deviceId);
        findViewById(R.id.btn_copy_device_id).setOnClickListener(v -> {
            ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboardManager != null) {
                clipboardManager.setPrimaryClip(ClipData.newPlainText("device_id", deviceId));
                showToast(getString(R.string.playlist_device_id_copied));
            }
        });
    }

    private void setupButtons() {
        findViewById(R.id.btn_add_playlist).setOnClickListener(v -> showPlaylistUrlDialog());
        findViewById(R.id.btn_open_file).setOnClickListener(v -> openPlaylistFile());
        findViewById(R.id.btn_add_xtream).setOnClickListener(v -> showXtreamDialog());
        findViewById(R.id.btn_add_website).setOnClickListener(v -> showWebsiteDialog());
        findViewById(R.id.btn_play_url).setOnClickListener(v -> showStreamUrlDialog());
    }

    private void showPlaylistUrlDialog() {
        LinearLayout container = createDialogContainer();
        final EditText nameInput = createDialogInput(getString(R.string.playlist_name_hint), InputType.TYPE_CLASS_TEXT);
        final EditText urlInput = createDialogInput(getString(R.string.playlist_url_hint), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setText(sharedPref.getPlaylistUrl());
        container.addView(nameInput);
        container.addView(urlInput);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.playlist_add)
                .setMessage(R.string.playlist_add_dialog_message)
                .setView(container)
                .setPositiveButton(R.string.dialog_ok, (dialog, which) -> {
                    String playlistName = nameInput.getText().toString().trim();
                    String playlistUrl = urlInput.getText().toString().trim();
                    if (TextUtils.isEmpty(playlistUrl)) {
                        showToast(getString(R.string.playlist_error_empty_url));
                        return;
                    }
                    fetchAndSavePlaylist(playlistUrl, playlistName);
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void showStreamUrlDialog() {
        LinearLayout container = createDialogContainer();
        final EditText nameInput = createDialogInput(getString(R.string.playlist_name_hint), InputType.TYPE_CLASS_TEXT);
        final EditText urlInput = createDialogInput(getString(R.string.playlist_stream_url_hint), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        container.addView(nameInput);
        container.addView(urlInput);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.playlist_play_url)
                .setMessage(R.string.playlist_play_url_dialog_message)
                .setView(container)
                .setPositiveButton(R.string.dialog_ok, (dialog, which) -> {
                    String playlistName = nameInput.getText().toString().trim();
                    String streamUrl = urlInput.getText().toString().trim();
                    if (TextUtils.isEmpty(streamUrl)) {
                        showToast(getString(R.string.playlist_error_empty_url));
                        return;
                    }
                    showToast(getString(R.string.playlist_loading));
                    executorService.execute(() -> {
                        DeviceBlockStatus deviceStatus = fetchDeviceBlockStatus();
                        if (deviceStatus.blocked) {
                            runOnUiThread(() -> showToast(getString(R.string.playlist_error_device_banned, safeReason(deviceStatus.reason))));
                            return;
                        }
                        List<Channel> channels = new ArrayList<>();
                        channels.add(createDirectChannel(streamUrl, playlistName));
                        persistPlaylistServerFirst(channels, streamUrl, playlistName);
                    });
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void showXtreamDialog() {
        LinearLayout container = createDialogContainer();
        final EditText nameInput = createDialogInput(getString(R.string.playlist_name_hint), InputType.TYPE_CLASS_TEXT);
        final EditText serverInput = createDialogInput(getString(R.string.playlist_xtream_server_hint), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        final EditText usernameInput = createDialogInput(getString(R.string.playlist_xtream_username_hint), InputType.TYPE_CLASS_TEXT);
        final EditText passwordInput = createDialogInput(getString(R.string.playlist_xtream_password_hint), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        container.addView(nameInput);
        container.addView(serverInput);
        container.addView(usernameInput);
        container.addView(passwordInput);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.playlist_add_xtream)
                .setMessage(R.string.playlist_xtream_dialog_message)
                .setView(container)
                .setPositiveButton(R.string.dialog_ok, (dialog, which) -> {
                    String playlistName = nameInput.getText().toString().trim();
                    String server = serverInput.getText().toString().trim();
                    String username = usernameInput.getText().toString().trim();
                    String password = passwordInput.getText().toString().trim();
                    if (TextUtils.isEmpty(server) || TextUtils.isEmpty(username) || TextUtils.isEmpty(password)) {
                        showToast(getString(R.string.playlist_error_xtream_fields));
                        return;
                    }
                    fetchAndSavePlaylist(buildXtreamM3uUrl(server, username, password), playlistName);
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void showWebsiteDialog() {
        LinearLayout container = createDialogContainer();
        final EditText nameInput = createDialogInput(getString(R.string.playlist_name_hint), InputType.TYPE_CLASS_TEXT);
        final EditText urlInput = createDialogInput(getString(R.string.playlist_website_hint), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        container.addView(nameInput);
        container.addView(urlInput);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.playlist_add_website)
                .setMessage(R.string.playlist_website_dialog_message)
                .setView(container)
                .setPositiveButton(R.string.dialog_ok, (dialog, which) -> {
                    String playlistName = nameInput.getText().toString().trim();
                    String websiteUrl = urlInput.getText().toString().trim();
                    if (TextUtils.isEmpty(websiteUrl)) {
                        showToast(getString(R.string.playlist_error_empty_url));
                        return;
                    }
                    fetchAndSavePlaylist(websiteUrl, playlistName);
                })
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void fetchAndSavePlaylist(String playlistUrl, String playlistName) {
        showToast(getString(R.string.playlist_loading));
        executorService.execute(() -> {
            try {
                DeviceBlockStatus deviceStatus = fetchDeviceBlockStatus();
                if (deviceStatus.blocked) {
                    runOnUiThread(() -> showToast(getString(R.string.playlist_error_device_banned, safeReason(deviceStatus.reason))));
                    return;
                }
                String content = DeviceApiHelper.fetchUrlContent(playlistUrl, "GET", null, deviceId, BuildConfig.APPLICATION_ID, sharedPref);
                List<Channel> channels = parsePlaylistChannels(content, playlistName);
                if (channels.isEmpty() && isPortalPage(content)) {
                    channels = fetchChannelsFromPortal(playlistUrl, content, deviceId, playlistName);
                }
                if (channels.isEmpty()) {
                    if (isPortalPage(content)) {
                        String finalDeviceId = deviceId;
                        runOnUiThread(() -> showToast(getString(R.string.playlist_error_portal_empty, finalDeviceId)));
                    } else {
                        runOnUiThread(() -> showToast(getString(R.string.playlist_error_no_new_channels)));
                    }
                    return;
                }

                persistPlaylistServerFirst(channels, playlistUrl, playlistName);
            } catch (Exception e) {
                runOnUiThread(() -> showToast(getString(R.string.playlist_error_download)));
            }
        });
    }

    private void openPlaylistFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_CODE_PICK_FILE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_FILE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) {
                showToast(getString(R.string.playlist_error_invalid_file));
                return;
            }
            readPlaylistFromFile(uri, derivePlaylistNameFromUri(uri));
        }
    }

    private void readPlaylistFromFile(Uri uri, String playlistName) {
        executorService.execute(() -> {
            try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
                DeviceBlockStatus deviceStatus = fetchDeviceBlockStatus();
                if (deviceStatus.blocked) {
                    runOnUiThread(() -> showToast(getString(R.string.playlist_error_device_banned, safeReason(deviceStatus.reason))));
                    return;
                }
                if (inputStream == null) {
                    runOnUiThread(() -> showToast(getString(R.string.playlist_error_invalid_file)));
                    return;
                }
                String content = readAll(inputStream);
                List<Channel> channels = parsePlaylistChannels(content, playlistName);
                if (channels.isEmpty()) {
                    runOnUiThread(() -> showToast(getString(R.string.playlist_error_invalid_file)));
                    return;
                }

                persistPlaylistServerFirst(channels, uri.toString(), playlistName);
            } catch (Exception e) {
                runOnUiThread(() -> showToast(getString(R.string.playlist_error_invalid_file)));
            }
        });
    }

    private String readAll(InputStream inputStream) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }

    private List<Channel> parsePlaylistChannels(String content, String playlistName) {
        return DeviceApiHelper.parsePlaylistChannels(content, playlistName);
    }

    private List<Channel> fetchChannelsFromPortal(String portalUrl, String portalHtml, String deviceId, String playlistName) throws IOException {
        List<Channel> channels = new ArrayList<>();
        List<String> playlistUrls = extractPlaylistUrlsFromPortal(portalHtml);
        if (playlistUrls.isEmpty()) {
            String body = "action=lookup&device_id=" + URLEncoder.encode(deviceId, StandardCharsets.UTF_8.name());
            String lookupHtml = DeviceApiHelper.fetchUrlContent(portalUrl, "POST", body, deviceId, BuildConfig.APPLICATION_ID, sharedPref);
            playlistUrls = extractPlaylistUrlsFromPortal(lookupHtml);
        }

        int index = 0;
        for (String playlistUrl : playlistUrls) {
            try {
                String m3uContent = DeviceApiHelper.fetchUrlContent(playlistUrl, "GET", null, deviceId, BuildConfig.APPLICATION_ID, sharedPref);
                String sourceLabel = TextUtils.isEmpty(playlistName) ? "Portal" : playlistName;
                List<Channel> parsed = parsePlaylistChannels(m3uContent, sourceLabel);
                for (Channel channel : parsed) {
                    index++;
                    channel.channel_id = "m3u_" + index;
                    channels.add(channel);
                }
            } catch (Exception ignored) {
            }
        }
        return channels;
    }

    private List<String> extractPlaylistUrlsFromPortal(String html) {
        Set<String> urls = new LinkedHashSet<>();
        Matcher playlistMatcher = PORTAL_PLAYLIST_URL_PATTERN.matcher(html);
        while (playlistMatcher.find()) {
            String raw = playlistMatcher.group(1);
            String decoded = HtmlCompat.fromHtml(raw, HtmlCompat.FROM_HTML_MODE_LEGACY).toString().trim();
            if (isPlayableStreamUrl(decoded)) {
                urls.add(decoded);
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

    private boolean isLikelyPlaylistUrl(String url) {
        String lower = url.toLowerCase(Locale.US);
        return lower.contains(".m3u")
                || lower.contains("m3u8")
                || lower.contains("get.php")
                || lower.contains("player_api")
                || lower.contains("type=m3u")
                || lower.contains("output=m3u")
                || lower.contains("playlist");
    }

    private boolean isPortalPage(String content) {
        if (TextUtils.isEmpty(content)) {
            return false;
        }
        String lower = content.toLowerCase(Locale.US);
        return lower.contains("<html")
                && lower.contains("device id")
                && lower.contains("m3u");
    }

    private void syncDevicePlaylistAsync(String playlistSource, String playlistName, int importedChannelsCount, String playlistType) {
        executorService.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String endpoint = buildApiUrl(sharedPref.getBaseUrl(), "/api/api.php?register_device_playlist");
                URL url = new URL(endpoint);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setRequestMethod("POST");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                String payload = "api_key=" + URLEncoder.encode(Config.REST_API_KEY, StandardCharsets.UTF_8.name())
                        + "&device_id=" + URLEncoder.encode(deviceId, StandardCharsets.UTF_8.name())
                        + "&playlist_name=" + URLEncoder.encode(TextUtils.isEmpty(playlistName) ? "Default Playlist" : playlistName, StandardCharsets.UTF_8.name())
                        + "&playlist_type=" + URLEncoder.encode(playlistType, StandardCharsets.UTF_8.name())
                        + "&playlist_source=" + URLEncoder.encode(playlistSource, StandardCharsets.UTF_8.name())
                        + "&channels_count=" + importedChannelsCount
                        + "&app_version=" + URLEncoder.encode(BuildConfig.VERSION_NAME, StandardCharsets.UTF_8.name())
                        + "&status=1";

                try (OutputStream outputStream = connection.getOutputStream()) {
                    outputStream.write(payload.getBytes(StandardCharsets.UTF_8));
                }
                connection.getResponseCode();
            } catch (Exception ignored) {
                // Sync is best-effort, local playlist should still work offline.
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private String buildApiUrl(String baseUrl, String path) {
        if (TextUtils.isEmpty(baseUrl)) {
            return path;
        }
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalizedBaseUrl + path;
    }

    private void checkDeviceBlockedOnLaunch() {
        executorService.execute(() -> {
            DeviceBlockStatus deviceStatus = fetchDeviceBlockStatus();
            if (deviceStatus.blocked) {
                runOnUiThread(() -> {
                    setPlaylistButtonsEnabled(false);
                    showToast(getString(R.string.playlist_error_device_banned, safeReason(deviceStatus.reason)));
                });
            } else {
                runOnUiThread(() -> setPlaylistButtonsEnabled(true));
            }
        });
    }

    private void setPlaylistButtonsEnabled(boolean enabled) {
        findViewById(R.id.btn_add_playlist).setEnabled(enabled);
        findViewById(R.id.btn_open_file).setEnabled(enabled);
        findViewById(R.id.btn_add_xtream).setEnabled(enabled);
        findViewById(R.id.btn_add_website).setEnabled(enabled);
        findViewById(R.id.btn_play_url).setEnabled(enabled);
    }

    private DeviceBlockStatus fetchDeviceBlockStatus() {
        DeviceBlockStatus status = new DeviceBlockStatus();
        DeviceApiHelper.DeviceProfile profile = DeviceApiHelper.fetchDeviceProfile(sharedPref, deviceId);
        status.blocked = profile.blocked;
        status.reason = profile.reason;
        return status;
    }

    private String safeReason(String reason) {
        if (TextUtils.isEmpty(reason)) {
            return "Please contact support.";
        }
        return reason;
    }

    private Channel buildChannel(String extInfoLine, String streamUrl, String userAgent, int index, String playlistName) {
        Channel channel = new Channel();
        String channelName = extractChannelName(extInfoLine);
        if (TextUtils.isEmpty(channelName)) {
            channelName = "Channel " + index;
        }
        String categoryName = extractAttribute(extInfoLine, "group-title");
        if (TextUtils.isEmpty(categoryName)) {
            categoryName = "TV";
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
        return channel;
    }

    private Channel createDirectChannel(String streamUrl, String playlistName) {
        Channel channel = new Channel();
        channel.channel_id = "direct_1";
        channel.channel_name = "Direct Stream";
        channel.channel_image = "";
        channel.channel_url = streamUrl;
        channel.channel_description = "";
        channel.channel_type = "M3U";
        channel.video_id = "";
        channel.category_name = buildCategoryName("TV", playlistName);
        channel.user_agent = "default";
        channel.total_views = "0";
        channel.epg_id = "";
        channel.epg_url = "";
        return channel;
    }

    private boolean isPlayableStreamUrl(String url) {
        String lower = url.toLowerCase(Locale.US);
        return lower.startsWith("http://")
                || lower.startsWith("https://")
                || lower.startsWith("rtsp://")
                || lower.startsWith("rtmp://")
                || lower.startsWith("udp://")
                || lower.startsWith("mms://");
    }

    private String extractChannelName(String extInfoLine) {
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

    private String extractAttribute(String line, String key) {
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

    private void persistPlaylistServerFirst(List<Channel> channels, String playlistSource, String playlistName) {
        DeviceApiHelper.RegisterResult registerResult = DeviceApiHelper.registerDevicePlaylist(
                sharedPref,
                deviceId,
                playlistSource,
                playlistName,
                channels == null ? 0 : channels.size(),
                inferPlaylistType(playlistSource)
        );
        if (!registerResult.success) {
            if (registerResult.blocked) {
                runOnUiThread(() -> {
                    setPlaylistButtonsEnabled(false);
                    showToast(getString(R.string.playlist_error_device_banned, safeReason(registerResult.reason)));
                });
            } else {
                runOnUiThread(() -> showToast(getString(R.string.playlist_sync_required)));
            }
            return;
        }

        DeviceApiHelper.PlaylistSyncResult syncResult = DeviceApiHelper.refreshPlaylistChannelsFromApi(sharedPref, deviceId);
        if (syncResult.blocked) {
            runOnUiThread(() -> {
                setPlaylistButtonsEnabled(false);
                showToast(getString(R.string.playlist_error_device_banned, safeReason(syncResult.reason)));
            });
            return;
        }

        List<Channel> channelsToSave = syncResult.channels;
        if (channelsToSave.isEmpty() && channels != null && !channels.isEmpty()) {
            channelsToSave = new ArrayList<>(channels);
            resequenceChannelIds(channelsToSave);
            sharedPref.savePlaylistChannels(channelsToSave);
        }

        if (channelsToSave.isEmpty()) {
            runOnUiThread(() -> showToast(getString(R.string.playlist_error_no_new_channels)));
            return;
        }

        sharedPref.savePlaylistUrl(playlistSource);
        runOnUiThread(() -> {
            showToast(getString(R.string.playlist_saved));
            Intent intent = DesignRouter.getMainActivityIntent(getApplicationContext());
            startActivity(intent);
            finish();
        });
    }

    private List<Channel> mergeChannels(List<Channel> existing, List<Channel> incoming) {
        List<Channel> merged = new ArrayList<>();
        Set<String> existingUrls = new LinkedHashSet<>();
        for (Channel channel : existing) {
            String key = normalizeUrl(channel.channel_url);
            if (!TextUtils.isEmpty(key) && existingUrls.add(key)) {
                merged.add(channel);
            }
        }
        for (Channel channel : incoming) {
            String key = normalizeUrl(channel.channel_url);
            if (!TextUtils.isEmpty(key) && existingUrls.add(key)) {
                merged.add(channel);
            }
        }
        return merged;
    }

    private void resequenceChannelIds(List<Channel> channels) {
        for (int i = 0; i < channels.size(); i++) {
            channels.get(i).channel_id = "m3u_" + (i + 1);
        }
    }

    private String normalizeUrl(String url) {
        return url == null ? "" : url.trim().toLowerCase(Locale.US);
    }

    private String buildCategoryName(String categoryName, String playlistName) {
        if (TextUtils.isEmpty(playlistName)) {
            return categoryName;
        }
        return playlistName + " • " + categoryName;
    }

    private String derivePlaylistNameFromUri(Uri uri) {
        String last = uri.getLastPathSegment();
        if (TextUtils.isEmpty(last)) {
            return "";
        }
        int slash = last.lastIndexOf('/');
        String fileName = slash >= 0 ? last.substring(slash + 1) : last;
        int dot = fileName.lastIndexOf('.');
        if (dot > 0) {
            fileName = fileName.substring(0, dot);
        }
        return fileName.trim();
    }

    private String buildXtreamM3uUrl(String server, String username, String password) {
        String normalizedServer = server.trim();
        if (!normalizedServer.startsWith("http://") && !normalizedServer.startsWith("https://")) {
            normalizedServer = "http://" + normalizedServer;
        }
        while (normalizedServer.endsWith("/")) {
            normalizedServer = normalizedServer.substring(0, normalizedServer.length() - 1);
        }
        String encodedUser = Uri.encode(username.trim());
        String encodedPass = Uri.encode(password.trim());
        return normalizedServer + "/get.php?username=" + encodedUser + "&password=" + encodedPass + "&type=m3u_plus&output=ts";
    }

    private String inferPlaylistType(String source) {
        String lower = source == null ? "" : source.toLowerCase(Locale.US);
        if (lower.contains("get.php") && lower.contains("username=") && lower.contains("password=")) {
            return "xtream";
        }
        if (lower.startsWith("content://")) {
            return "file";
        }
        if (lower.endsWith(".m3u") || lower.endsWith(".m3u8") || lower.contains("type=m3u")) {
            return "m3u";
        }
        if (lower.contains("playlist")) {
            return "website";
        }
        return "direct";
    }

    private LinearLayout createDialogContainer() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) getResources().getDimension(R.dimen.settings_space);
        container.setPadding(padding, padding, padding, padding);
        return container;
    }

    private EditText createDialogInput(String hint, int inputType) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setInputType(inputType);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = (int) getResources().getDimension(R.dimen.spacing_middle);
        editText.setLayoutParams(params);
        return editText;
    }

    private void handleOnBackPressed() {
        onBackPressedDispatcher = getOnBackPressedDispatcher();
        onBackPressedDispatcher.addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private static class DeviceBlockStatus {
        boolean blocked = false;
        String reason = "";
    }

    private String getLocalDeviceId() {
        String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        if (TextUtils.isEmpty(androidId)) {
            return "android-device";
        }
        return androidId;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}
