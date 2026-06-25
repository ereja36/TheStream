package com.shif.shqip.android.activities;

import static com.shif.shqip.android.Config.ENABLE_VPN_ACCESS;
import static com.shif.shqip.android.utils.Constant.LOCALHOST_ADDRESS;

import android.app.Application;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.shif.shqip.android.Config;
import com.shif.shqip.android.R;
import com.shif.shqip.android.callbacks.CallbackConfig;
import com.shif.shqip.android.databases.prefs.AdsPref;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.models.Ads;
import com.shif.shqip.android.models.App;
import com.shif.shqip.android.models.License;
import com.shif.shqip.android.models.Settings;
import com.shif.shqip.android.rests.RestAdapter;
import com.shif.shqip.android.utils.AdsManager;
import com.shif.shqip.android.utils.Constant;
import com.shif.shqip.android.utils.DesignRouter;
import com.shif.shqip.android.utils.DeviceApiHelper;
import com.shif.shqip.android.utils.Tools;
import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.solodroidx.ads.appopen.AppOpenAd;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ActivitySplash extends AppCompatActivity {

    public static final String TAG = "ActivitySplash";
    Call<CallbackConfig> callbackCall = null;
    ImageView img_splash;
    SharedPref sharedPref;
    AdsManager adsManager;
    AdsPref adsPref;
    App app;
    Settings settings;
    Ads ads;
    License license;
    boolean isForceOpenAds;
    private final ExecutorService startupExecutor = Executors.newSingleThreadExecutor();
    private String deviceId;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Tools.transparentStatusBarNavigation(ActivitySplash.this);
        setContentView(R.layout.activity_splash);
        isForceOpenAds = Config.FORCE_TO_SHOW_APP_OPEN_AD_ON_START;
        adsManager = new AdsManager(this);
        sharedPref = new SharedPref(this);
        adsPref = new AdsPref(this);
        deviceId = DeviceApiHelper.getLocalDeviceId(this);

        img_splash = findViewById(R.id.img_splash);
        if (sharedPref.getIsDarkTheme()) {
            img_splash.setImageResource(R.drawable.bg_splash_dark);
        } else {
            img_splash.setImageResource(R.drawable.bg_splash_default);
        }

        if (ENABLE_VPN_ACCESS) {
            Tools.postDelayed(this::requestConfig, Config.DELAY_SPLASH);
        } else {
            if (Tools.isVpnConnectionAvailable()) {
                Tools.showWarningDialog(ActivitySplash.this, getString(R.string.vpn_detected), getString(R.string.close_vpn));
            } else {
                Tools.postDelayed(this::requestConfig, Config.DELAY_SPLASH);
            }
        }
    }

    @SuppressWarnings("ConstantValue")
    private void requestConfig() {
        if (Config.SERVER_KEY.contains("XXXXX")) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("App not configured")
                    .setMessage("Please put your Server Key and Rest API Key from settings menu in your admin panel to AppConfig, you can see the documentation for more detailed instructions.")
                    .setPositiveButton(getString(R.string.dialog_ok), (dialogInterface, i) -> startMainActivity())
                    .setCancelable(false)
                    .show();
        } else {
            String data = Tools.decode(Config.SERVER_KEY);
            String[] results = data.split("_applicationId_");
            String baseUrl = results[0].replace("localhost", LOCALHOST_ADDRESS);
            String applicationId = results[1];
            sharedPref.saveConfig(baseUrl, applicationId);

            if (applicationId.equals(Tools.getApplicationId())) {
                if (Tools.isConnect(this)) {
                    requestAPI(baseUrl);
                } else {
                    startMainActivity();
                }
            } else {
                String message = "applicationId does not match, applicationId in your app is : " + Tools.getApplicationId() +
                        "\n\n But your Server Key is registered with applicationId : " + applicationId + "\n\n" +
                        "Please update your Server Key with the appropriate registration applicationId that is used in your Android project.";
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Error")
                        .setMessage(Html.fromHtml(message))
                        .setPositiveButton(getString(R.string.dialog_ok), (dialog, which) -> finish())
                        .setCancelable(false)
                        .show();
            }
        }
    }

    private void requestAPI(String apiUrl) {
        this.callbackCall = RestAdapter.createAPI(apiUrl).getConfig(Tools.getApplicationId(), Config.REST_API_KEY);
        this.callbackCall.enqueue(new Callback<>() {
            public void onResponse(@NonNull Call<CallbackConfig> call, @NonNull Response<CallbackConfig> response) {
                CallbackConfig resp = response.body();
                if (resp != null && resp.status.equals("ok")) {
                    app = resp.app;
                    settings = resp.settings;
                    ads = resp.ads;
                    license = resp.license;
                    Tools.saveAds(adsPref, ads);
                    sharedPref.saveCredentials(settings.youtube_api_key, settings.more_apps_url, settings.privacy_policy, app.redirect_url, settings.providers);
                    sharedPref.saveUser(license.item_id, license.item_name, license.buyer, license.license_type);
                    if (app.design_id != null) {
                        sharedPref.saveDesignId(app.design_id);
                    }
                    if (app.status != null && app.status.equals("0")) {
                        Intent intent = new Intent(getApplicationContext(), ActivityRedirect.class);
                        intent.putExtra("redirect_url", app.redirect_url);
                        startActivity(intent);
                        finish();
                        Log.d(TAG, "App is inactive, call redirect method");
                    } else {
                        handleStartupState();
                        Log.d(TAG, "App is active");
                    }
                } else {
                    startMainActivity();
                    Log.d(TAG, "Resp null");
                }
            }

            public void onFailure(@NonNull Call<CallbackConfig> call, @NonNull Throwable th) {
                Log.e("onFailure", "onFailure: " + th.getMessage());
                startMainActivity();
            }
        });

    }

    private void handleStartupState() {
        startupExecutor.execute(() -> {
            try {
                DeviceApiHelper.DeviceProfile profile = DeviceApiHelper.fetchDeviceProfile(sharedPref, deviceId);
                if (profile.blocked) {
                    sharedPref.clearPlaylistChannels();
                    runOnUiThread(() -> showBlockedDialog(profile.reason));
                    return;
                }

                if (profile.noAds) {
                    adsPref.setAdStatus(false);
                }

                DeviceApiHelper.PlaylistSyncResult playlistSync = DeviceApiHelper.refreshPlaylistChannelsFromApi(sharedPref, deviceId);
                if (playlistSync.blocked) {
                    sharedPref.clearPlaylistChannels();
                    runOnUiThread(() -> showBlockedDialog(playlistSync.reason));
                    return;
                }

                DeviceApiHelper.PopupConfig popupConfig = DeviceApiHelper.fetchStartupPopup(sharedPref, deviceId);
                runOnUiThread(() -> {
                    if (popupConfig.enabled) {
                        showStartupPopup(popupConfig, this::showAppOpenAdIfAvailable);
                    } else {
                        showAppOpenAdIfAvailable();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(this::showAppOpenAdIfAvailable);
            }
        });
    }

    private void showAppOpenAdIfAvailable() {
        if (isForceOpenAds) {
            if (Constant.APP_OPEN_AD_ON_START) {
                adsManager.loadAppOpenAd(Constant.APP_OPEN_AD_ON_START, true, () -> {
                    startMainActivity();
                    AppOpenAd.isAppOpenAdLoaded = false;
                    Log.d(TAG, "showAppOpenAdIfAvailable");
                });
            } else {
                startMainActivity();
                Log.d(TAG, "app open on start disabled");
            }
        } else {
            if (adsPref.getAdStatus() && Constant.APP_OPEN_AD_ON_START) {
                Application application = getApplication();
                ((MyApplication) application).showAdIfAvailable(this, this::startMainActivity);
            } else {
                startMainActivity();
                Log.d(TAG, "startMainActivity");
            }
        }
    }

    private void showBlockedDialog(String reason) {
        String safeReason = TextUtils.isEmpty(reason) ? "Please contact support." : reason;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.device_blocked_title)
                .setMessage(getString(R.string.device_blocked_message, safeReason))
                .setCancelable(false)
                .setPositiveButton(R.string.dialog_ok, (dialog, which) -> {
                    finishAffinity();
                })
                .show();
    }

    private void showStartupPopup(DeviceApiHelper.PopupConfig popupConfig, Runnable onComplete) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) getResources().getDimension(R.dimen.settings_space);
        container.setPadding(padding, padding, padding, padding);

        if (!TextUtils.isEmpty(popupConfig.mediaUrl) && "image".equalsIgnoreCase(popupConfig.popupType)) {
            ImageView imageView = new ImageView(this);
            imageView.setAdjustViewBounds(true);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            imageParams.bottomMargin = (int) getResources().getDimension(R.dimen.spacing_middle);
            imageView.setLayoutParams(imageParams);
            Glide.with(this).load(popupConfig.mediaUrl).into(imageView);
            container.addView(imageView);
        }

        if (!TextUtils.isEmpty(popupConfig.message)) {
            TextView messageView = new TextView(this);
            messageView.setText(popupConfig.message);
            messageView.setGravity(Gravity.START);
            container.addView(messageView);
        }

        String openUrl = !TextUtils.isEmpty(popupConfig.targetUrl) ? popupConfig.targetUrl : popupConfig.mediaUrl;

        final boolean[] completed = {false};
        Runnable completeOnce = () -> {
            if (!completed[0]) {
                completed[0] = true;
                onComplete.run();
            }
        };

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(TextUtils.isEmpty(popupConfig.title) ? getString(R.string.app_name) : popupConfig.title)
                .setView(container)
                .setCancelable(popupConfig.allowManualClose);

        if (!TextUtils.isEmpty(openUrl)) {
            builder.setNeutralButton(R.string.popup_open_link, (dialog, which) -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(openUrl)));
                } catch (ActivityNotFoundException ignored) {
                    // no handler available
                }
            });
        }

        if (popupConfig.allowManualClose) {
            builder.setPositiveButton(R.string.popup_close, (dialog, which) -> dialog.dismiss());
        }

        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.setOnDismissListener(d -> completeOnce.run());
        dialog.show();

        int durationSeconds = popupConfig.durationSeconds;
        if (durationSeconds <= 0 && !popupConfig.allowManualClose) {
            durationSeconds = 8;
        }
        if (durationSeconds > 0) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (dialog.isShowing()) {
                    dialog.dismiss();
                }
            }, durationSeconds * 1000L);
        }
    }

    private void startMainActivity() {
        Intent intent;
        if (sharedPref.hasPlaylistChannels()) {
            intent = DesignRouter.getMainActivityIntent(getApplicationContext());
        } else {
            intent = new Intent(getApplicationContext(), ActivityPlaylistSetup.class);
        }
        startActivity(intent);
        finish();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        AppOpenAd.isAppOpenAdLoaded = false;
        startupExecutor.shutdownNow();
    }

}