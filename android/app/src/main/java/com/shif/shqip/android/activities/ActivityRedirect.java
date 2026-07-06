package com.shif.shqip.android.activities;

import android.content.Intent;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageButton;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.OnBackPressedDispatcher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.shif.shqip.android.R;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.utils.Tools;
import com.google.android.material.snackbar.Snackbar;
import com.solodroidx.ads.appopen.AppOpenAd;

public class ActivityRedirect extends AppCompatActivity {

    String redirectUrl = "";
    ImageButton btnClose;
    Button btnRedirect;
    SharedPref sharedPref;
    OnBackPressedDispatcher onBackPressedDispatcher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Tools.getTheme(this);
        setContentView(R.layout.activity_redirect);
        Tools.setNavigation(this);
        sharedPref = new SharedPref(this);
        redirectUrl = getIntent().getStringExtra("redirect_url");
        initView();
        handleOnBackPressed();
    }

    public void handleOnBackPressed() {
        onBackPressedDispatcher = getOnBackPressedDispatcher();
        onBackPressedDispatcher.addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
                AppOpenAd.isAppOpenAdLoaded = false;
            }
        });
    }

    private void initView() {
        btnClose = findViewById(R.id.btn_close);
        if (sharedPref.getIsDarkTheme()) {
            btnClose.setColorFilter(ContextCompat.getColor(this, R.color.color_dark_icon), PorterDuff.Mode.SRC_IN);
        } else {
            btnClose.setColorFilter(ContextCompat.getColor(this, R.color.color_light_icon), PorterDuff.Mode.SRC_IN);
        }

        btnRedirect = findViewById(R.id.btn_redirect);

        btnClose.setOnClickListener(view -> {
            finish();
            AppOpenAd.isAppOpenAdLoaded = false;
        });

        btnRedirect.setOnClickListener(view -> {
            if (redirectUrl.isEmpty()) {
                Snackbar.make(findViewById(android.R.id.content), getString(R.string.redirect_error), Snackbar.LENGTH_SHORT).show();
            } else {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(redirectUrl)));
                finish();
                AppOpenAd.isAppOpenAdLoaded = false;
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        AppOpenAd.isAppOpenAdLoaded = false;
    }

}
