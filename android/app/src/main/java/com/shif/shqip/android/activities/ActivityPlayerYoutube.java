package com.shif.shqip.android.activities;

import static com.shif.shqip.android.utils.Constant.PLAYER_MODE_LANDSCAPE;

import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.OnBackPressedDispatcher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.shif.shqip.android.Config;
import com.shif.shqip.android.R;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.listener.OnErrorListener;
import com.shif.shqip.android.models.Resp;
import com.shif.shqip.android.rests.ApiInterface;
import com.shif.shqip.android.rests.RestAdapter;
import com.shif.shqip.android.utils.Tools;
import com.shif.shqip.android.utils.YouTubeVideoExistenceChecker;
import com.shif.shqip.android.webview.MyWebView;
import com.shif.shqip.android.webview.VideoEnabledWebChromeClient;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.FullscreenListener;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.YouTubePlayerListener;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ActivityPlayerYoutube extends AppCompatActivity {

    public static final String TAG = "ActivityYoutubePlayer";
    public static final int DELAY_PROGRESS = 1500;
    private YouTubePlayerView youTubePlayerView;
    private YouTubePlayer youTubePlayer;
    FrameLayout fullScreenViewContainer;
    private MyWebView webView;
    String channelId = "";
    private String videoId;
    ProgressBar progressBar;
    RelativeLayout parentView;
    SharedPref sharedPref;
    boolean isVideoError = false;
    boolean isPaused = false;
    boolean isCueVideo = true;
    private long exitTime = 0;
    OnBackPressedDispatcher onBackPressedDispatcher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_player_youtube);
        Tools.setPlayerNavigation(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.getWindow().setNavigationBarColor(ContextCompat.getColor(getApplicationContext(), R.color.color_black));
            this.getWindow().setStatusBarColor(ContextCompat.getColor(getApplicationContext(), R.color.color_black));
            this.getWindow().getDecorView().setSystemUiVisibility(0);
        }
        sharedPref = new SharedPref(this);

        channelId = getIntent().getStringExtra("channel_id");
        videoId = getIntent().getStringExtra("id");
        Log.d(TAG, "video id: " + videoId);

        parentView = findViewById(R.id.root_view);
        youTubePlayerView = findViewById(R.id.youtube_player);
        webView = findViewById(R.id.web_view_player);
        fullScreenViewContainer = findViewById(R.id.full_screen_view_container);
        progressBar = findViewById(R.id.progress_bar);

        loadYouTubePlayerView();
        updateView(channelId);

        if (sharedPref.getPlayerOrientation() == PLAYER_MODE_LANDSCAPE) {
            setLandscape();
        }

        handleOnBackPressed();

    }

    private void loadYouTubePlayerView() {
        youTubePlayerView.setEnableAutomaticInitialization(false);
        YouTubePlayerListener listener = new AbstractYouTubePlayerListener() {
            @Override
            public void onReady(@NonNull YouTubePlayer player) {
                youTubePlayer = player;
                if (isPaused) {
                    youTubePlayer.cueVideo(videoId, 0);
                } else {
                    youTubePlayer.loadVideo(videoId, 0);
                }
                Log.d(TAG, "onReady");
                super.onReady(player);
            }

            @Override
            public void onStateChange(@NonNull YouTubePlayer player, @NonNull PlayerConstants.PlayerState state) {
                if (state == PlayerConstants.PlayerState.PLAYING) {
                    Tools.postDelayed(() -> {
                        youTubePlayerView.setVisibility(View.VISIBLE);
                        showProgress(false);
                    }, DELAY_PROGRESS);
                    Log.d(TAG, "is playing");
                } else if (state == PlayerConstants.PlayerState.PAUSED) {
                    Log.d(TAG, "is paused");
                } else if (state == PlayerConstants.PlayerState.BUFFERING) {
                    Log.d(TAG, "is buffering");
                } else if (state == PlayerConstants.PlayerState.ENDED) {
                    Log.d(TAG, "is ended");
                } else {
                    Log.d(TAG, "unknown");
                }
                super.onStateChange(player, state);
            }

            @Override
            public void onError(@NonNull YouTubePlayer player, @NonNull PlayerConstants.PlayerError error) {
                Log.d(TAG, "error load youtube player view, try to load youtube iframe view: " + error);
                youTubePlayerView.setVisibility(View.GONE);
                loadYouTubeIframePlayerView();
                super.onError(player, error);
            }
        };
        IFramePlayerOptions options = new IFramePlayerOptions.Builder(this)
                .controls(1)
                .fullscreen(1)
                .rel(0)
                .ivLoadPolicy(3)
                .ccLoadPolicy(0)
                .build();

        youTubePlayerView.addFullscreenListener(new FullscreenListener() {
            @Override
            public void onEnterFullscreen(@NonNull View view, @NonNull Function0<Unit> function0) {
                youTubePlayerView.setVisibility(View.GONE);
                fullScreenViewContainer.setVisibility(View.VISIBLE);
                fullScreenViewContainer.addView(view);
                setLandscape();
            }

            @Override
            public void onExitFullscreen() {
                youTubePlayerView.setVisibility(View.VISIBLE);
                fullScreenViewContainer.setVisibility(View.GONE);
                fullScreenViewContainer.removeAllViews();
                setPortrait();
            }
        });

        youTubePlayerView.initialize(listener, options);

        getLifecycle().addObserver(youTubePlayerView);
    }

    private void loadYouTubeIframePlayerView() {
        showProgress(true);
        new YouTubeVideoExistenceChecker().checkVideoExistence(videoId, new YouTubeVideoExistenceChecker.VideoExistenceCallback() {
            @Override
            public void onVideoExists(String videoId) {
                prepareYouTubeIframePlayer();
                Log.d(TAG, "video exists");
            }

            @Override
            public void onVideoNotFound(String videoId) {
                errorDialog();
                showProgress(false);
                Log.d(TAG, "video not found");
            }

            @Override
            public void onError(String videoId, String errorMessage) {
                errorDialog();
                showProgress(false);
                Log.d(TAG, "video error");
            }
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void prepareYouTubeIframePlayer() {
        webView.setVisibility(View.GONE);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        // 2. Add the JavaScript Interface (The Bridge)
        webView.addJavascriptInterface(new YouTubePlayerInterface(webView, () -> {
            Log.d(TAG, "Error load youtube iframe view, try to load in embed mode");
            loadYouTubeVideoInEmbedMode();
            isVideoError = true;
            webView.setVisibility(View.GONE);
        }), "Android");
        webView.setBackgroundColor(ContextCompat.getColor(this, R.color.color_player_background));
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onLoadResource(WebView view, String url) {
                super.onLoadResource(view, url);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                webView.loadUrl("javascript:playVideo();");
                Tools.postDelayed(() -> {
                    if (!isVideoError) {
                        webView.setVisibility(View.VISIBLE);
                        showProgress(false);
                    }
                }, DELAY_PROGRESS);
                Log.d(TAG, "onPageFinished: " + url);
            }

            @SuppressWarnings("deprecation")
            @Override
            public void onReceivedError(final WebView view, final int errorCode, final String description, final String failingUrl) {
                Log.d(TAG, "onReceivedError: " + failingUrl);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                Log.d(TAG, "onReceivedError: " + error.getDescription());
            }
        });

        ImageView btnPlay = findViewById(R.id.btn_play);
        btnPlay.setOnClickListener(view -> {
            String html = getHTMLData(videoId);
            webView.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null);
        });
        btnPlay.performClick();

        View nonVideoLayout = findViewById(R.id.main_non_video_layout);
        ViewGroup videoLayout = findViewById(R.id.main_video_layout);
        @SuppressLint("InflateParams") View progressView = getLayoutInflater().inflate(R.layout.include_progress, null);
        VideoEnabledWebChromeClient webChromeClient = getVideoEnabledWebChromeClient(nonVideoLayout, videoLayout, progressView);
        webView.setWebChromeClient(webChromeClient);
    }

    @NonNull
    private VideoEnabledWebChromeClient getVideoEnabledWebChromeClient(View nonVideoLayout, ViewGroup videoLayout, View progressView) {
        VideoEnabledWebChromeClient webChromeClient = new VideoEnabledWebChromeClient(nonVideoLayout, videoLayout, progressView, webView, progressBar) {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                super.onProgressChanged(view, progress);
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }
        };

        webChromeClient.setOnToggledFullscreen(fullscreen -> {
            if (fullscreen) {
                setLandscape();
            } else {
                setPortrait();
            }
        });
        return webChromeClient;
    }

    public String getHTMLData(String videoId) {
        return "<html>\n" +
                "\n" +
                "    <body style=\"margin:0px;padding:0px;\">\n" +
                "       <div id=\"player\"></div>\n" +
                "        <script>\n" +
                "            var player;\n" +
                "            function onYouTubeIframeAPIReady() {\n" +
                "                player = new YT.Player('player', {\n" +
                "                    height: '100%',\n" +
                "                    width: '100%',\n" +
                "                    videoId: '" + videoId + "',\n" +
                "                    playerVars: {\n" +
                "                        'playsinline': 1\n" +
                "                    },\n" +
                "                    events: {\n" +
                "                        'onReady': onPlayerReady,\n" +
                "                        'onError': onPlayerError // <-- 1. ADDED: YouTube error handler\n" +
                "                    }\n" +
                "                });\n" +
                "            }\n" +
                "            function onPlayerReady(event) {\n" +
                "                player.playVideo();\n" +
                "                // Player is ready\n" +
                "            }\n" +
                "\n" +
                "            // 2. ADDED: Function to handle YouTube player errors\n" +
                "            function onPlayerError(event) {\n" +
                "                var errorCode = event.data;\n" +
                "                // Use the Android bridge to pass the error code to your Java/Kotlin code\n" +
                "                if (window.Android) {\n" +
                "                    Android.onVideoError(errorCode);\n" +
                "                }\n" +
                "            }\n" +
                "\n" +
                "            function seekTo(time) {\n" +
                "                player.seekTo(time, true);\n" +
                "            }\n" +
                "              function playVideo() {\n" +
                "                player.playVideo();\n" +
                "            }\n" +
                "            function pauseVideo() {\n" +
                "                player.pauseVideo();\n" +
                "            }\n" +
                "        </script>\n" +
                "        <script src=\"https://www.youtube.com/iframe_api\"></script>\n" +
                "    </body>\n" +
                "</html>\n";
    }

    public void setLandscape() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
    }

    @SuppressLint("SourceLockedOrientationActivity")
    public void setPortrait() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        if (!sharedPref.getIsDarkTheme()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
            }
        }
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    public void errorDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.whops))
                .setCancelable(false)
                .setMessage(getString(R.string.msg_failed))
                .setPositiveButton(getString(R.string.option_retry), (dialog, which) -> {
                    loadYouTubeIframePlayerView();
                    showProgress(true);
                })
                .setNegativeButton(getString(R.string.dialog_cancel), (dialogInterface, i) -> finish())
                .show();
    }

    private void showProgress(boolean show) {
        if (show) {
            progressBar.setVisibility(View.VISIBLE);
        } else {
            progressBar.setVisibility(View.GONE);
        }
    }

    private void updateView(String id) {
        if (Tools.isConnect(this)) {
            ApiInterface apiInterface = RestAdapter.createAPI(sharedPref.getBaseUrl());
            Call<Resp> call = apiInterface.updateView(id);
            call.enqueue(new Callback<>() {
                @Override
                public void onResponse(@NonNull Call<Resp> call, @NonNull Response<Resp> response) {
                    Resp data = response.body();
                    if (data != null) {
                        android.util.Log.d(TAG, "View counter updated +" + data.value);
                    }
                }

                @Override
                public void onFailure(@NonNull Call<Resp> call, @NonNull Throwable t) {
                    android.util.Log.d(TAG, "error " + t.getMessage());
                }
            });
        }
    }

    @SuppressWarnings("unused")
    public static class YouTubePlayerInterface {

        private final WebView webView;
        OnErrorListener onErrorListener;

        public YouTubePlayerInterface(WebView webView, OnErrorListener onErrorListener) {
            this.webView = webView;
            this.onErrorListener = onErrorListener;
        }

        @JavascriptInterface
        public void onVideoLoaded() {
            webView.post(() -> {
                Log.d(TAG, "Video loaded successfully!");
            });
        }

        @JavascriptInterface
        public void onVideoError(final int errorCode) {
            // Run on the main UI thread to update UI components
            webView.post(() -> {
                Log.d(TAG, "YouTube Player Error Detected: Code " + errorCode);
                onErrorListener.onError();
            });
        }
    }

    @SuppressLint({"ClickableViewAccessibility", "SetJavaScriptEnabled"})
    private void loadYouTubeVideoInEmbedMode() {
        Log.d(TAG, "loadYouTubeVideoInEmbedMode");
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        webView.addJavascriptInterface(new YouTubePlayerInterface(webView, () -> {
            Log.d(TAG, "Error loadYouTubeVideoInEmbedMode");
        }), "Android");
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        //customWebView.setListener(this, this);
        webView.setScrollBarStyle(WebView.SCROLLBARS_OUTSIDE_OVERLAY);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setBackgroundColor(ContextCompat.getColor(this, R.color.color_player_background));

        //webView chrome client
        View nonVideoLayout = findViewById(R.id.main_non_video_layout);
        ViewGroup videoLayout = findViewById(R.id.main_video_layout);
        @SuppressLint("InflateParams") View progressView = getLayoutInflater().inflate(R.layout.include_progress, null);
        VideoEnabledWebChromeClient webChromeClient = new VideoEnabledWebChromeClient(nonVideoLayout, videoLayout, progressView, webView, progressBar);
        webChromeClient.setOnToggledFullscreen(new WebViewToggledFullscreenCallback());
        webView.setWebChromeClient(webChromeClient);

        webView.setWebViewClient(new MyWebViewClient());
        webView.requestFocus(View.FOCUS_DOWN);

        webView.loadUrl("https://www.youtube.com/embed/" + videoId + "?autoplay=1");

    }

    private class WebViewToggledFullscreenCallback implements VideoEnabledWebChromeClient.ToggledFullscreenCallback {
        @Override
        public void toggledFullscreen(boolean fullScreen) {
            if (fullScreen) {
                setLandscape();
            } else {
                setPortrait();
            }
        }
    }

    private class MyWebViewClient extends WebViewClient {

        private boolean mSuccess = true;

        @SuppressWarnings("deprecation")
        @Override
        public void onPageFinished(final WebView view, final String url) {
            if (mSuccess) {
                Tools.postDelayed(() -> {
                    showProgress(false);
                    webView.setVisibility(View.VISIBLE);
                }, DELAY_PROGRESS);
                CookieSyncManager.getInstance().sync();
            }
            mSuccess = true;
            super.onPageFinished(view, url);
        }

        @SuppressWarnings("deprecation")
        @Override
        public void onReceivedError(final WebView view, final int errorCode, final String description, final String failingUrl) {
            mSuccess = false;
            Tools.postDelayed(() -> showProgress(false), DELAY_PROGRESS);
            webView.setVisibility(View.GONE);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            onReceivedError(view, error.getErrorCode(), error.getDescription().toString(), request.getUrl().toString());
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return true;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (webView != null) {
            webView.onPause();
        }

        isPaused = true;
        if (youTubePlayer != null) {
            youTubePlayer.pause();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }

        if (youTubePlayer != null) {
            youTubePlayer.play();
            if (isCueVideo) {
                Tools.postDelayed(() -> {
                    progressBar.setVisibility(View.GONE);
                    youTubePlayerView.setVisibility(View.VISIBLE);
                    isCueVideo = false;
                }, 1000);
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            webView.onDestroy();
        }
    }

    public void handleOnBackPressed() {
        onBackPressedDispatcher = getOnBackPressedDispatcher();
        onBackPressedDispatcher.addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                closePlayer();
            }
        });
    }

    public void closePlayer() {
        if (Config.PRESS_BACK_TWICE_TO_CLOSE_PLAYER) {
            if ((System.currentTimeMillis() - exitTime) > 2000) {
                Snackbar.make(parentView, getString(R.string.press_again_to_close_player), Snackbar.LENGTH_SHORT).show();
                exitTime = System.currentTimeMillis();
            } else {
                finish();
            }
        } else {
            finish();
        }
    }

}