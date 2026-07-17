package com.shif.shqip.android.activities;

import static com.shif.shqip.android.Config.PRESS_BACK_TWICE_TO_CLOSE_PLAYER;
import static com.shif.shqip.android.utils.Constant.PLAYER_MODE_LANDSCAPE;

import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.OnBackPressedDispatcher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.shif.shqip.android.R;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.models.Resp;
import com.shif.shqip.android.rests.ApiInterface;
import com.shif.shqip.android.rests.RestAdapter;
import com.shif.shqip.android.utils.Tools;
import com.shif.shqip.android.webview.AdvancedWebView;
import com.shif.shqip.android.webview.MyWebView;
import com.shif.shqip.android.webview.VideoEnabledWebChromeClient;
import com.google.android.material.snackbar.Snackbar;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ActivityPlayerWebView extends AppCompatActivity implements AdvancedWebView.Listener {

    private static final String TAG = "ActivityPlayerWebView";
    private ProgressBar progressBar;
    MyWebView webView;
    SharedPref sharedPref;
    String channel_id = "";
    String embedUrlSrc = "";
    private long exitTime = 0;
    OnBackPressedDispatcher onBackPressedDispatcher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_player_webview);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.getWindow().setNavigationBarColor(ContextCompat.getColor(getApplicationContext(), R.color.color_black));
            this.getWindow().setStatusBarColor(ContextCompat.getColor(getApplicationContext(), R.color.color_black));
            this.getWindow().getDecorView().setSystemUiVisibility(0);
        }
        Tools.setPlayerNavigation(this);

        sharedPref = new SharedPref(this);

        channel_id = getIntent().getStringExtra("channel_id");
        embedUrlSrc = getIntent().getStringExtra("url");

        initView();
        initComponents();
        loadData();

        if (sharedPref.getPlayerOrientation() == PLAYER_MODE_LANDSCAPE) {
            hideSystemUI();
        }

        handleOnBackPressed();
        updateView(channel_id);
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

    private void initView() {
        progressBar = findViewById(R.id.progress_bar);
        webView = findViewById(R.id.player_web_view);
    }

    @SuppressLint({"ClickableViewAccessibility", "SetJavaScriptEnabled"})
    private void initComponents() {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);

        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.setListener(this, this);
        webView.setScrollBarStyle(WebView.SCROLLBARS_OUTSIDE_OVERLAY);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setBackgroundColor(Color.BLACK);

        //webView chrome client
        View nonVideoLayout = findViewById(R.id.main_non_video_layout);
        ViewGroup videoLayout = findViewById(R.id.main_video_layout);
        @SuppressLint("InflateParams") View progressView = getLayoutInflater().inflate(R.layout.include_progress, null);
        VideoEnabledWebChromeClient webChromeClient = new VideoEnabledWebChromeClient(nonVideoLayout, videoLayout, progressView, webView, progressBar);
        webChromeClient.setOnToggledFullscreen(new WebViewToggledFullscreenCallback());
        webView.setWebChromeClient(webChromeClient);

        webView.setWebViewClient(new MyWebViewClient());
        webView.requestFocus(View.FOCUS_DOWN);

    }

    private void loadData() {
        if (Tools.isConnect(this)) {
            webView.loadUrl(embedUrlSrc);
        } else {
            showSwipeRefresh(false);
            webView.setVisibility(View.GONE);
            //retryData();
        }
    }

    private class WebViewToggledFullscreenCallback implements VideoEnabledWebChromeClient.ToggledFullscreenCallback {
        @Override
        public void toggledFullscreen(boolean fullScreen) {
            if (fullScreen) {
                hideSystemUI();
            } else {
                showSystemUI();
            }
        }
    }

    public void hideSystemUI() {
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
    public void showSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        SharedPref sharedPref = new SharedPref(this);
        if (!sharedPref.getIsDarkTheme()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
            }
        }
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }

    private class MyWebViewClient extends WebViewClient {

        private boolean mSuccess = true;

        @SuppressWarnings("deprecation")
        @Override
        public void onPageFinished(final WebView view, final String url) {
            if (mSuccess) {
                Tools.postDelayed(() -> {
                    showSwipeRefresh(false);
                    webView.setVisibility(View.VISIBLE);
                }, 1000);
                CookieSyncManager.getInstance().sync();
            }
            mSuccess = true;
            super.onPageFinished(view, url);
        }

        @SuppressWarnings("deprecation")
        @Override
        public void onReceivedError(final WebView view, final int errorCode, final String description, final String failingUrl) {
            mSuccess = false;
            Tools.postDelayed(() -> showSwipeRefresh(false), 1000);
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
    public void onPageStarted(String url, Bitmap favicon) {
    }

    @Override
    public void onPageFinished(String url) {
    }

    @Override
    public void onPageError(int errorCode, String description, String failingUrl) {
    }

    @Override
    public void onDownloadRequested(String url, String suggestedFilename, String mimeType, long contentLength, String contentDisposition, String userAgent) {
    }

    @Override
    public void onExternalPageRequest(String url) {
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onPause() {
        super.onPause();
        webView.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        webView.onDestroy();
    }

    private void showSwipeRefresh(boolean show) {
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

    public void closePlayer() {
        if (PRESS_BACK_TWICE_TO_CLOSE_PLAYER) {
            if ((System.currentTimeMillis() - exitTime) > 2000) {
                Snackbar.make(findViewById(R.id.root_view), getString(R.string.press_again_to_close_player), Snackbar.LENGTH_SHORT).show();
                exitTime = System.currentTimeMillis();
            } else {
                finish();
            }
        } else {
            finish();
        }
    }

}