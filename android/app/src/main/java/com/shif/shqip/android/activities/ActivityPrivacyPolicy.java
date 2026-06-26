package com.shif.shqip.android.activities;

import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.OnBackPressedDispatcher;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.shif.shqip.android.R;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.utils.Tools;

public class ActivityPrivacyPolicy extends AppCompatActivity {

    private static final String FALLBACK_PRIVACY_URL = "https://shifshqip.com/privacy";

    SharedPref sharedPref;
    WebView webView;
    ProgressBar progressBar;
    TextView errorView;
    OnBackPressedDispatcher onBackPressedDispatcher;
    private String activeUrl = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Tools.getTheme(this);
        setContentView(R.layout.activity_privacy_policy);
        Tools.setNavigation(this);
        sharedPref = new SharedPref(this);
        webView = findViewById(R.id.web_view);
        progressBar = findViewById(R.id.progress_privacy);
        errorView = findViewById(R.id.txt_privacy_error);
        loadPrivacyContent();
        setupToolbar();
        handleOnBackPressed();
    }

    private void loadPrivacyContent() {
        String privacyUrl = sharedPref.getPrivacyPublicUrl();
        if (TextUtils.isEmpty(privacyUrl)) {
            privacyUrl = FALLBACK_PRIVACY_URL;
        }
        activeUrl = privacyUrl;

        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }
        if (errorView != null) {
            errorView.setVisibility(View.GONE);
        }

        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        if (sharedPref.getIsDarkTheme()) {
            webView.setBackgroundColor(Color.parseColor("#121212"));
        } else {
            webView.setBackgroundColor(Color.WHITE);
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request == null || !request.isForMainFrame()) {
                    return;
                }
                showPrivacyError();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }
        });

        webView.loadUrl(privacyUrl);
    }

    private void showPrivacyError() {
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
        String inlinePolicy = sharedPref.getPrivacyPolicy();
        if (!TextUtils.isEmpty(inlinePolicy)) {
            if (errorView != null) {
                errorView.setVisibility(View.GONE);
            }
            Tools.displayContent(this, webView, inlinePolicy);
            return;
        }
        if (errorView != null) {
            errorView.setVisibility(View.VISIBLE);
            errorView.setOnClickListener(v -> loadPrivacyContent());
        }
    }

    public void handleOnBackPressed() {
        onBackPressedDispatcher = getOnBackPressedDispatcher();
        onBackPressedDispatcher.addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                } else {
                    finish();
                }
            }
        });
    }

    public void setupToolbar() {
        final Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (sharedPref.getIsDarkTheme()) {
            toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.color_dark_toolbar));
        } else {
            toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.color_light_primary));
        }

        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
            getSupportActionBar().setTitle(getResources().getString(R.string.title_setting_privacy));
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(menuItem);
    }

}
