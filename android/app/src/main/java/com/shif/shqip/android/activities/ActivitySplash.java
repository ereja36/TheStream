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
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;

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
import com.shif.shqip.android.utils.AdsAntiblockHelper;
import com.shif.shqip.android.utils.DesignRouter;
import com.shif.shqip.android.utils.DeviceApiHelper;
import com.shif.shqip.android.utils.InboxNotificationHelper;
import com.shif.shqip.android.utils.PlaylistGuardSession;
import com.shif.shqip.android.utils.Tools;
import com.bumptech.glide.Glide;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.solodroidx.ads.appopen.AppOpenAd;

import java.util.Locale;
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
    AdsAntiblockHelper adsAntiblockHelper;
    AdsPref adsPref;
    App app;
    Settings settings;
    Ads ads;
    License license;
    private final ExecutorService startupExecutor = Executors.newSingleThreadExecutor();
    private String deviceId;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Tools.getDesignTheme(this);
        setContentView(R.layout.activity_splash);
        adsManager = new AdsManager(this);
        adsAntiblockHelper = new AdsAntiblockHelper(this);
        sharedPref = new SharedPref(this);
        adsPref = new AdsPref(this);
        deviceId = DeviceApiHelper.getLocalDeviceId(this);

        img_splash = findViewById(R.id.img_splash);
        img_splash.setImageResource(R.drawable.bg_splash_default);

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
        if ("XXXXX".equals(Config.SERVER_KEY) || "XXXXX".equals(Config.REST_API_KEY)) {
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
        this.callbackCall = RestAdapter.createAPI(apiUrl).getConfig(Tools.getApplicationId(), Config.REST_API_KEY, deviceId);
        this.callbackCall.enqueue(new Callback<>() {
            public void onResponse(@NonNull Call<CallbackConfig> call, @NonNull Response<CallbackConfig> response) {
                CallbackConfig resp = response.body();
                if (resp != null && resp.status.equals("ok")) {
                    app = resp.app;
                    settings = resp.settings;
                    ads = resp.ads;
                    license = resp.license;
                    Tools.saveAds(adsPref, ads);
                    boolean serverAdsOn = ads != null && "on".equalsIgnoreCase(ads.ad_status);
                    sharedPref.saveCredentials(
                            settings.youtube_api_key,
                            settings.more_apps_url,
                            settings.privacy_policy,
                            app.redirect_url,
                            settings.providers,
                            settings.enable_player_epg != 0,
                            TextUtils.isEmpty(settings.privacy_public_url) ? "https://shifshqip.com/privacy" : settings.privacy_public_url);
                    sharedPref.savePlayerBranding(
                            settings.player_brand_enabled != 0,
                            settings.player_brand_text,
                            settings.player_brand_logo_url
                    );
                    sharedPref.saveSecuritySettings(
                            settings.app_integrity_enabled != 0,
                            settings.app_release_cert_sha256,
                            settings.ads_antiblock_script,
                            serverAdsOn
                    );
                    adsAntiblockHelper.applyServerScript(settings.ads_antiblock_script);
                    DeviceApiHelper.reportDeviceOpen(sharedPref, deviceId);
                    sharedPref.saveUser(license.item_id, license.item_name, license.buyer, license.license_type);
                    if (app.design_id != null) {
                        sharedPref.saveDesignId(app.design_id);
                    }
                    sharedPref.ensureDefaultTheme();
                    if (app.app_logo_url != null) {
                        sharedPref.saveAppLogoUrl(app.app_logo_url);
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
            String versionBefore = sharedPref.getPortalContentVersion();
            try {
                DeviceApiHelper.refreshPlaylistPortalConfig(sharedPref);
            } catch (Exception ignored) {
            }
            String versionAfter = sharedPref.getPortalContentVersion();
            boolean portalChanged = !android.text.TextUtils.isEmpty(versionAfter)
                    && !versionAfter.equals(versionBefore);
            boolean needSyncFirst = portalChanged || !sharedPref.hasPlaylistChannels();

            if (needSyncFirst) {
                try {
                    if (portalChanged) {
                        sharedPref.clearPlaylistChannels();
                    }
                    DeviceApiHelper.PlaylistSyncResult syncResult =
                            DeviceApiHelper.refreshPlaylistChannelsFromApi(sharedPref, deviceId);
                    if (syncResult.blocked) {
                        sharedPref.clearPlaylistChannels();
                    }
                } catch (Exception ignored) {
                }
                runOnUiThread(() -> {
                    if (!isFinishing() && !mainStarted) {
                        startMainActivity();
                    }
                });
                syncStartupPopupInBackground();
                return;
            }

            runOnUiThread(() -> {
                if (!isFinishing() && !mainStarted) {
                    startMainActivity();
                }
            });
            syncPlaylistAndStartupPopupInBackground();
        });
    }

    private void syncPlaylistAndStartupPopupInBackground() {
        try {
            DeviceApiHelper.DeviceProfile profile = DeviceApiHelper.fetchDeviceProfile(sharedPref, deviceId);
            if (profile.blocked) {
                sharedPref.clearPlaylistChannels();
                return;
            }
            adsPref.setDeviceNoAds(profile.noAds);
            adsPref.refreshEffectiveAdStatus();
            PlaylistGuardSession.refreshAsync(sharedPref);
            DeviceApiHelper.PlaylistSyncResult playlistSync =
                    DeviceApiHelper.refreshPlaylistChannelsFromApi(sharedPref, deviceId);
            if (playlistSync.blocked) {
                sharedPref.clearPlaylistChannels();
                return;
            }
        } catch (Exception ignored) {
            // Keep cached playlist on sync errors.
        }
        syncStartupPopupInBackground();
    }

    private void syncStartupPopupInBackground() {
        try {
            DeviceApiHelper.DeviceProfile profile = DeviceApiHelper.fetchDeviceProfile(sharedPref, deviceId);
            if (profile.blocked) {
                sharedPref.clearPlaylistChannels();
                return;
            }
            adsPref.setDeviceNoAds(profile.noAds);
            adsPref.refreshEffectiveAdStatus();
            PlaylistGuardSession.refreshAsync(sharedPref);

            DeviceApiHelper.PopupConfig popupConfig = DeviceApiHelper.fetchStartupPopup(sharedPref, deviceId);
            if (!popupConfig.enabled) {
                return;
            }
            runOnUiThread(() -> {
                if (isFinishing()) {
                    return;
                }
                if (isSupportPopup(popupConfig)) {
                    showSupportPopup(popupConfig, () -> {
                    });
                } else {
                    showStartupPopup(popupConfig, () -> {
                    });
                }
            });
        } catch (Exception ignored) {
            // Popup is optional.
        }
    }

    private void showIntegrityBlockedDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.integrity_blocked_title)
                .setMessage(R.string.integrity_blocked_message)
                .setCancelable(false)
                .setPositiveButton(R.string.dialog_ok, (dialog, which) -> startMainActivity())
                .show();
    }

    private void showBlockedDialog(String reason) {
        String safeReason = TextUtils.isEmpty(reason) ? "Please contact support." : reason;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.device_blocked_title)
                .setMessage(getString(R.string.device_blocked_message, safeReason))
                .setCancelable(false)
                .setPositiveButton(R.string.dialog_ok, (dialog, which) -> {
                    startMainActivity();
                })
                .show();
    }

    private boolean isSupportPopup(DeviceApiHelper.PopupConfig popupConfig) {
        if (popupConfig == null || TextUtils.isEmpty(popupConfig.popupType)) {
            return false;
        }
        String type = popupConfig.popupType.toLowerCase(Locale.US);
        return "support".equals(type) || "donation".equals(type) || "donate".equals(type);
    }

    private void showSupportPopup(DeviceApiHelper.PopupConfig popupConfig, Runnable onComplete) {
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_support_popup, null, false);
        TextView titleView = content.findViewById(R.id.txt_support_title);
        TextView messageView = content.findViewById(R.id.txt_support_message);
        TextView deviceIdView = content.findViewById(R.id.txt_support_device_id);
        MaterialButton copyButton = content.findViewById(R.id.btn_copy_device_id);
        MaterialButton donateButton = content.findViewById(R.id.btn_support_donate);
        TextView laterButton = content.findViewById(R.id.btn_support_later);

        String displayDeviceId = DeviceApiHelper.getDisplayDeviceId(this);
        final String fullDeviceId = DeviceApiHelper.getLocalDeviceId(this);
        titleView.setText(TextUtils.isEmpty(popupConfig.title)
                ? getString(R.string.support_default_title)
                : popupConfig.title);
        messageView.setText(TextUtils.isEmpty(popupConfig.message)
                ? getString(R.string.support_default_message)
                : popupConfig.message);
        deviceIdView.setText(displayDeviceId);

        final boolean[] completed = {false};
        Runnable completeOnce = () -> {
            if (!completed[0]) {
                completed[0] = true;
                onComplete.run();
            }
        };

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(content)
                .setCancelable(popupConfig.allowManualClose)
                .create();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        copyButton.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("device_id", fullDeviceId));
                Toast.makeText(this, R.string.support_copied, Toast.LENGTH_SHORT).show();
            }
        });

        String donateUrl = !TextUtils.isEmpty(popupConfig.targetUrl) ? popupConfig.targetUrl : popupConfig.mediaUrl;
        if (TextUtils.isEmpty(donateUrl)) {
            donateButton.setVisibility(View.GONE);
        } else {
            donateButton.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(donateUrl)));
                } catch (ActivityNotFoundException ignored) {
                    // no handler available
                }
            });
        }

        laterButton.setOnClickListener(v -> dialog.dismiss());
        dialog.setOnDismissListener(d -> completeOnce.run());
        dialog.show();
    }

    private void showStartupPopup(DeviceApiHelper.PopupConfig popupConfig, Runnable onComplete) {
        android.widget.LinearLayout container = new android.widget.LinearLayout(this);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        int padding = (int) getResources().getDimension(R.dimen.settings_space);
        container.setPadding(padding, padding, padding, padding);

        if (!TextUtils.isEmpty(popupConfig.mediaUrl) && "image".equalsIgnoreCase(popupConfig.popupType)) {
            ImageView imageView = new ImageView(this);
            imageView.setAdjustViewBounds(true);
            imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            android.widget.LinearLayout.LayoutParams imageParams = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            );
            imageParams.bottomMargin = (int) getResources().getDimension(R.dimen.spacing_middle);
            imageView.setLayoutParams(imageParams);
            Glide.with(this).load(popupConfig.mediaUrl).into(imageView);
            container.addView(imageView);
        }

        if (!TextUtils.isEmpty(popupConfig.message)) {
            TextView messageView = new TextView(this);
            messageView.setText(popupConfig.message);
            messageView.setGravity(android.view.Gravity.START);
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

    private boolean mainStarted;

    private void startMainActivity() {
        if (mainStarted || isFinishing()) {
            return;
        }
        Runnable launch = () -> {
            if (mainStarted || isFinishing()) {
                return;
            }
            mainStarted = true;
            MyApplication.getInstance().markResumeAppOpenReady();
            InboxNotificationHelper.pollAndShow(this, sharedPref);
            Intent intent;
            if (sharedPref.hasPlaylistChannels()) {
                intent = DesignRouter.getMainActivityIntent(getApplicationContext());
            } else {
                intent = new Intent(getApplicationContext(), ActivityPlaylistSetup.class);
            }
            startActivity(intent);
            finish();
        };
        if (adsPref.getAdStatus() && adsPref.getAppOpenOnStart()) {
            adsManager.initializeAd();
            adsManager.updateConsentStatus();
            adsManager.loadAppOpenAd(true, true, () -> {
                AppOpenAd.isAppOpenAdLoaded = false;
                preloadNextAppOpenAd();
                launch.run();
            });
        } else {
            launch.run();
        }
    }

    private void preloadNextAppOpenAd() {
        if (!adsPref.getAdStatus() || !adsPref.getAppOpenOnResume()) {
            return;
        }
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isFinishing()) {
                return;
            }
            try {
                adsManager.loadAppOpenAd(true, false, () -> {
                });
            } catch (Exception ignored) {
                // Preload only; ignore failures.
            }
        }, 500L);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        AppOpenAd.isAppOpenAdLoaded = false;
        startupExecutor.shutdownNow();
    }

}