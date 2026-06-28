package com.shif.shqip.android.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.shif.shqip.android.R;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.utils.DeviceApiHelper;
import com.shif.shqip.android.utils.PlaylistGuardSession;
import com.shif.shqip.android.utils.PlaylistPortalHelper;
import com.shif.shqip.android.utils.StreamPlaybackHelper;
import com.shif.shqip.android.utils.Tools;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ActivityPlayerHlsJs extends AppCompatActivity {

    private SharedPref sharedPref;
    private WebView webView;
    private ProgressBar progressBar;
    private String sourceStreamUrl = "";
    private String playbackUrl = "";
    private String userAgent = "default";
    private String channelId = "";
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private int autoRetryCount = 0;
    private boolean destroyed = false;

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (!destroyed && StreamPlaybackHelper.needsPeriodicRefresh(sourceStreamUrl)) {
                refreshPlaybackUrl(false);
            }
            refreshHandler.postDelayed(this, StreamPlaybackHelper.STREAM_REFRESH_MS);
        }
    };

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Tools.getTheme(this);
        setContentView(R.layout.activity_player_hlsjs);
        Tools.setPlayerNavigation(this);
        sharedPref = new SharedPref(this);

        webView = findViewById(R.id.hls_webview);
        progressBar = findViewById(R.id.progress_bar);

        Intent intent = getIntent();
        sourceStreamUrl = intent.getStringExtra("url");
        userAgent = intent.getStringExtra("user_agent");
        channelId = intent.getStringExtra("channel_id");
        if (sourceStreamUrl == null) {
            sourceStreamUrl = "";
        }
        if (userAgent == null || userAgent.trim().isEmpty()) {
            userAgent = "default";
        }
        if (TextUtils.isEmpty(sourceStreamUrl)) {
            Toast.makeText(this, R.string.msg_failed, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (!TextUtils.isEmpty(channelId)) {
            updateView(channelId);
        }

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        webView.addJavascriptInterface(new PlayerBridge(), "AndroidBridge");
        webView.setWebViewClient(new InterceptingWebViewClient());

        executor.execute(() -> {
            PlaylistGuardSession.refreshBlocking(sharedPref);
            String resolved = StreamPlaybackHelper.resolveForPlayback(sharedPref, sourceStreamUrl);
            runOnUiThread(() -> {
                if (TextUtils.isEmpty(resolved)) {
                    showErrorDialog();
                    return;
                }
                playbackUrl = resolved;
                loadPlayerHtml(playbackUrl);
                if (StreamPlaybackHelper.needsPeriodicRefresh(sourceStreamUrl)) {
                    refreshHandler.postDelayed(refreshRunnable, StreamPlaybackHelper.STREAM_REFRESH_MS);
                }
            });
        });
    }

    private void loadPlayerHtml(String streamUrl) {
        try {
            InputStream inputStream = getAssets().open("hls_player.html");
            byte[] buffer = new byte[inputStream.available()];
            int read = inputStream.read(buffer);
            inputStream.close();
            String html = new String(buffer, 0, Math.max(read, 0));
            html = html.replace("__STREAM_URL__", escapeForJs(streamUrl));
            webView.loadDataWithBaseURL("https://playlist.shifshqip.com/", html, "text/html", "UTF-8", null);
            progressBar.setVisibility(View.GONE);
        } catch (Exception e) {
            showErrorDialog();
        }
    }

    private void refreshPlaybackUrl(boolean fromError) {
        executor.execute(() -> {
            PlaylistGuardSession.refreshBlocking(sharedPref);
            String resolved = StreamPlaybackHelper.resolveForPlayback(sharedPref, sourceStreamUrl);
            runOnUiThread(() -> {
                if (TextUtils.isEmpty(resolved)) {
                    if (fromError) {
                        showErrorDialog();
                    }
                    return;
                }
                if (!resolved.equals(playbackUrl)) {
                    playbackUrl = resolved;
                    autoRetryCount = 0;
                    webView.evaluateJavascript("updateStream(" + quoteJs(resolved) + ");", null);
                } else if (fromError && autoRetryCount < StreamPlaybackHelper.AUTO_RETRY_MAX) {
                    autoRetryCount++;
                    webView.evaluateJavascript("updateStream(" + quoteJs(resolved) + ");", null);
                } else if (fromError) {
                    showErrorDialog();
                }
            });
        });
    }

    private String escapeForJs(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private String quoteJs(String value) {
        return "'" + escapeForJs(value) + "'";
    }

    private void showErrorDialog() {
        if (destroyed || isFinishing()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.whops))
                .setCancelable(false)
                .setMessage(getString(R.string.msg_failed))
                .setPositiveButton(getString(R.string.option_retry), (dialog, which) -> refreshPlaybackUrl(true))
                .setNegativeButton(getString(R.string.option_no), (dialogInterface, i) -> finish())
                .show();
    }

    private void updateView(String channelIdValue) {
        // keep parity with native player telemetry hook if added later
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        refreshHandler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }

    private class PlayerBridge {
        @JavascriptInterface
        public void onNetworkError() {
            runOnUiThread(() -> refreshPlaybackUrl(true));
        }

        @JavascriptInterface
        public void onFatalError() {
            runOnUiThread(() -> refreshPlaybackUrl(true));
        }
    }

    private class InterceptingWebViewClient extends WebViewClient {

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            String url = request.getUrl() != null ? request.getUrl().toString() : "";
            if (shouldInterceptUrl(url)) {
                WebResourceResponse response = fetchWithHeaders(url, request.getRequestHeaders());
                if (response != null) {
                    return response;
                }
            }
            return super.shouldInterceptRequest(view, request);
        }

        private boolean shouldInterceptUrl(String url) {
            if (TextUtils.isEmpty(url)) {
                return false;
            }
            String lower = url.toLowerCase(Locale.US);
            return lower.startsWith("https://shifshqip.com/")
                    || lower.startsWith("http://shifshqip.com/")
                    || PlaylistPortalHelper.isPortalUrl(url);
        }

        private WebResourceResponse fetchWithHeaders(String url, java.util.Map<String, String> requestHeaders) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(StreamPlaybackHelper.CONNECT_TIMEOUT_MS);
                connection.setReadTimeout(StreamPlaybackHelper.READ_TIMEOUT_MS);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "*/*");
                connection.setRequestProperty("User-Agent", PlaylistPortalHelper.UPSTREAM_STREAM_UA);
                connection.setRequestProperty("Referer", PlaylistPortalHelper.UPSTREAM_STREAM_REFERER);
                if (PlaylistPortalHelper.isPortalUrl(url)) {
                    String deviceId = DeviceApiHelper.getLocalDeviceId(getApplicationContext());
                    PlaylistPortalHelper.applyPortalHeaders(connection, url, deviceId, sharedPref);
                }
                if (requestHeaders != null) {
                    for (java.util.Map.Entry<String, String> entry : requestHeaders.entrySet()) {
                        if (!TextUtils.isEmpty(entry.getKey()) && !TextUtils.isEmpty(entry.getValue())) {
                            connection.setRequestProperty(entry.getKey(), entry.getValue());
                        }
                    }
                }
                int code = connection.getResponseCode();
                InputStream stream = code >= 200 && code < 300
                        ? connection.getInputStream()
                        : connection.getErrorStream();
                if (stream == null) {
                    return null;
                }
                String mime = connection.getContentType();
                if (TextUtils.isEmpty(mime)) {
                    mime = guessMime(url);
                } else {
                    int semi = mime.indexOf(';');
                    if (semi > 0) {
                        mime = mime.substring(0, semi).trim();
                    }
                }
                String encoding = connection.getContentEncoding();
                return new WebResourceResponse(mime, encoding, code, "OK", null, new BufferedInputStream(stream));
            } catch (Exception ignored) {
                return null;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        private String guessMime(String url) {
            String lower = url.toLowerCase(Locale.US);
            if (lower.contains(".m3u8") || lower.contains("/r/") || lower.contains("/s/")) {
                return "application/vnd.apple.mpegurl";
            }
            if (lower.endsWith(".ts")) {
                return "video/mp2t";
            }
            return "application/octet-stream";
        }
    }
}
