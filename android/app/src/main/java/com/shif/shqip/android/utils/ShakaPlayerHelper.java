package com.shif.shqip.android.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

/**
 * WebView + Shaka Player — same engine and buffer tuning as shifshqip.com/k1 on Android.
 */
public final class ShakaPlayerHelper {

    public interface PlaybackCallback {
        void onPlaying();

        void onError();
    }

    private static final String ASSET_URL = "file:///android_asset/shaka_live_player.html";
    private static final String UPSTREAM_UA = PlaylistPortalHelper.UPSTREAM_STREAM_UA;

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private WebView webView;
    private PlaybackCallback callback;
    private String pendingUrl = "";
    private boolean pageReady;

    public ShakaPlayerHelper(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void setCallback(PlaybackCallback callback) {
        this.callback = callback;
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    public void attach(WebView view) {
        this.webView = view;
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setUserAgentString(UPSTREAM_UA);
        view.setBackgroundColor(0xFF000000);
        view.setWebChromeClient(new WebChromeClient());
        view.addJavascriptInterface(new ShakaBridge(), "AndroidBridge");
        view.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView webView, String url) {
                pageReady = true;
                if (!pendingUrl.isEmpty()) {
                    startPlaybackInternal(pendingUrl);
                }
            }
        });
        pageReady = false;
        view.loadUrl(ASSET_URL);
    }

    public void play(String playbackUrl) {
        pendingUrl = K1PlaybackHelper.optimizePlaybackUrl(playbackUrl);
        if (webView == null) {
            return;
        }
        if (pageReady) {
            startPlaybackInternal(pendingUrl);
        }
    }

    private void startPlaybackInternal(String url) {
        if (webView == null || url == null || url.isEmpty()) {
            return;
        }
        String js = "play(" + JSONObject.quote(url) + ");";
        mainHandler.post(() -> webView.evaluateJavascript(js, null));
    }

    public void pause() {
        if (webView != null) {
            mainHandler.post(() -> webView.evaluateJavascript("pausePlayback();", null));
        }
    }

    public void resume() {
        if (webView != null) {
            mainHandler.post(() -> webView.evaluateJavascript("resumePlayback();", null));
        }
    }

    public void stop() {
        if (webView != null) {
            mainHandler.post(() -> webView.evaluateJavascript("destroyPlayer();", null));
        }
        pendingUrl = "";
    }

    public void release() {
        stop();
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidBridge");
            webView.loadUrl("about:blank");
            webView.destroy();
            webView = null;
        }
        pageReady = false;
    }

    public static boolean shouldUseShaka(String url) {
        return K1PlaybackHelper.isK1LiveStream(url);
    }

    public static void setPlayerVisibility(
            boolean useShaka,
            View shakaView,
            View vlcView,
            View exoView
    ) {
        if (shakaView != null) {
            shakaView.setVisibility(useShaka ? View.VISIBLE : View.GONE);
        }
        if (vlcView != null) {
            vlcView.setVisibility(View.GONE);
        }
        if (exoView != null) {
            exoView.setVisibility(useShaka ? View.GONE : View.VISIBLE);
        }
    }

    private final class ShakaBridge {
        @JavascriptInterface
        public void onPlaying() {
            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onPlaying();
                }
            });
        }

        @JavascriptInterface
        public void onError(String reason) {
            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onError();
                }
            });
        }
    }
}
