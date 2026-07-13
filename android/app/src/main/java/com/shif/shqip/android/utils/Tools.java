package com.shif.shqip.android.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.shif.shqip.android.BuildConfig;
import com.shif.shqip.android.Config;
import com.shif.shqip.android.R;
import com.shif.shqip.android.activities.ActivityChannelDetail;
import com.shif.shqip.android.activities.ActivityNotificationDetail;
import com.shif.shqip.android.activities.ActivityPlayerWebView;
import com.shif.shqip.android.activities.ActivityPlayerVideo;
import com.shif.shqip.android.activities.ActivityWebView;
import com.shif.shqip.android.activities.ActivityPlayerYoutube;
import com.shif.shqip.android.activities.MainActivity;
import com.shif.shqip.android.databases.dao.AppDatabase;
import com.shif.shqip.android.databases.dao.ChannelEntity;
import com.shif.shqip.android.databases.dao.DAO;
import com.shif.shqip.android.databases.prefs.AdsPref;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.listener.OnCompleteListener;
import com.shif.shqip.android.listener.OnPositiveButtonListener;
import com.shif.shqip.android.models.Ads;
import com.shif.shqip.android.models.Channel;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.solodroid.push.sdk.provider.OneSignalPush;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Objects;

public class Tools {

    public static final String TAG = "tools";
    Activity activity;
    SharedPref sharedPref;
    private BottomSheetDialog mBottomSheetDialog;
    boolean flag_read_later;

    public Tools() {
    }

    public Tools(Activity activity) {
        this.activity = activity;
        this.sharedPref = new SharedPref(activity);
    }

    public static void getTheme(Context context) {
        SharedPref sharedPref = new SharedPref(context);
        applyNightMode(sharedPref.getIsDarkTheme());
        if (sharedPref.getIsDarkTheme()) {
            context.setTheme(R.style.AppDarkTheme);
        } else {
            context.setTheme(R.style.AppTheme);
        }
        if (context instanceof ComponentActivity) {
            ComponentActivity activity = (ComponentActivity) context;
            WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), true);
        }
    }

    public static void getDesignTheme(Context context) {
        SharedPref sharedPref = new SharedPref(context);
        applyNightMode(sharedPref.getIsDarkTheme());
        if (sharedPref.getIsDarkTheme()) {
            context.setTheme(R.style.AppDarkTheme);
        } else {
            context.setTheme(R.style.AppTheme);
        }
        if (context instanceof ComponentActivity) {
            ComponentActivity activity = (ComponentActivity) context;
            WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), true);
            applyDesignSystemBars(activity, !sharedPref.getIsDarkTheme());
        }
    }

    public static void applyNightMode(boolean darkTheme) {
        AppCompatDelegate.setDefaultNightMode(darkTheme
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
    }

    public static void applyDesignSystemBars(Activity activity, boolean lightBars) {
        SharedPref sharedPref = new SharedPref(activity);
        int bgColor;
        String designId = sharedPref.getDesignId();
        if ("v3".equals(designId)) {
            bgColor = ContextCompat.getColor(activity, R.color.v3_surface);
        } else {
            bgColor = ContextCompat.getColor(activity, R.color.v2_bg);
        }
        Window window = activity.getWindow();
        window.setStatusBarColor(bgColor);
        window.setNavigationBarColor(bgColor);
        // Android 10+ / targetSdk 35 can paint a black contrast scrim over the nav bar —
        // disable it so the gesture area matches the app background (OneIPTV-style).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
            window.setStatusBarContrastEnforced(false);
        }
        WindowCompat.getInsetsController(window, window.getDecorView())
                .setAppearanceLightStatusBars(lightBars);
        WindowCompat.getInsetsController(window, window.getDecorView())
                .setAppearanceLightNavigationBars(lightBars);
    }

    public static void getPlayerTheme(Context context) {
        context.setTheme(R.style.AppDarkTheme);
        EdgeToEdge.enable((ComponentActivity) context);
    }

    public static void setNavigation(Activity activity) {
        RelativeLayout rootView = activity.findViewById(R.id.root_view);
        RelativeLayout backgroundView = activity.findViewById(R.id.background_view);
        RelativeLayout navigationBarView = activity.findViewById(R.id.navigation_bar_view);

        // Some screens (e.g. onboarding) intentionally use simpler layouts
        // that do not include this navigation container structure.
        if (rootView == null || backgroundView == null || navigationBarView == null) {
            return;
        }

        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            navigationBarView.setPadding(0, 0, 0, systemBars.bottom);
            return insets;
        });
        WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView()).setAppearanceLightStatusBars(false);
        SharedPref sharedPref = new SharedPref(activity);
        if (sharedPref.getIsDarkTheme()) {
            Tools.darkNavigation(activity);
            rootView.setBackgroundColor(ContextCompat.getColor(activity, R.color.color_dark_status_bar));
            backgroundView.setBackgroundColor(ContextCompat.getColor(activity, R.color.color_dark_background));
            navigationBarView.setBackgroundColor(ContextCompat.getColor(activity, R.color.color_dark_bottom_navigation));
            WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView()).setAppearanceLightNavigationBars(false);
        } else {
            Tools.lightNavigation(activity);
            rootView.setBackgroundColor(ContextCompat.getColor(activity, R.color.color_light_status_bar));
            backgroundView.setBackgroundColor(ContextCompat.getColor(activity, R.color.color_light_background));
            navigationBarView.setBackgroundColor(ContextCompat.getColor(activity, R.color.color_white));
            WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView()).setAppearanceLightNavigationBars(true);
        }
        setLayoutDirection(activity);
    }

    public static void setPlayerNavigation(Activity activity) {
        RelativeLayout rootView = activity.findViewById(R.id.root_view);
        RelativeLayout backgroundView = activity.findViewById(R.id.background_view);
        RelativeLayout navigationBarView = activity.findViewById(R.id.navigation_bar_view);
        if (rootView == null || backgroundView == null || navigationBarView == null) {
            return;
        }
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, 0, 0, 0);
            navigationBarView.setPadding(0, 0, 0, systemBars.bottom);
            return insets;
        });
        WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView()).setAppearanceLightStatusBars(false);
        rootView.setBackgroundColor(ContextCompat.getColor(activity, R.color.color_black));
        backgroundView.setBackgroundColor(ContextCompat.getColor(activity, R.color.color_black));
        navigationBarView.setBackgroundColor(ContextCompat.getColor(activity, R.color.color_black));
        WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView()).setAppearanceLightNavigationBars(false);
        darkNavigation(activity);
    }

    public static void setWindowInsetsLayout(Activity activity) {
        ViewCompat.setOnApplyWindowInsetsListener(activity.findViewById(R.id.background_view), (v, insets) -> {
            Insets imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime());
            Insets systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), imeInsets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    public static void setLayoutDirection(Activity activity) {
        if (Config.ENABLE_RTL_MODE) {
            activity.getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        }
    }

    public static void darkNavigation(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            activity.getWindow().setNavigationBarColor(ContextCompat.getColor(activity, R.color.color_dark_bottom_navigation));
            activity.getWindow().setStatusBarColor(ContextCompat.getColor(activity, R.color.color_dark_status_bar));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.getWindow().getDecorView().setSystemUiVisibility(0);
        }
    }

    public static void lightNavigation(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            activity.getWindow().setNavigationBarColor(ContextCompat.getColor(activity, R.color.color_light_bottom_navigation));
            activity.getWindow().setStatusBarColor(ContextCompat.getColor(activity, R.color.color_light_status_bar));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    public static void transparentStatusBarNavigation(Activity activity) {
        Window window = activity.getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        WindowCompat.getInsetsController(window, window.getDecorView()).setAppearanceLightStatusBars(false);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            setWindowFlag(activity,
                    WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                            | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
                    false);
        }
    }

    public static void setWindowFlag(Activity activity, final int bits, boolean on) {
        Window win = activity.getWindow();
        WindowManager.LayoutParams winParams = win.getAttributes();
        if (on) {
            winParams.flags |= bits;
        } else {
            winParams.flags &= ~bits;
        }
        win.setAttributes(winParams);
    }

    public static void setupToolbar(AppCompatActivity activity, Toolbar toolbar, String title, boolean backButton) {
        SharedPref sharedPref = new SharedPref(activity);
        activity.setSupportActionBar(toolbar);
        if (sharedPref.getIsDarkTheme()) {
            toolbar.setBackgroundColor(ContextCompat.getColor(activity, R.color.color_dark_toolbar));
        } else {
            toolbar.setBackgroundColor(ContextCompat.getColor(activity, R.color.color_light_primary));
        }
        final ActionBar actionBar = activity.getSupportActionBar();
        if (actionBar != null) {
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(backButton);
            activity.getSupportActionBar().setHomeButtonEnabled(backButton);
            activity.getSupportActionBar().setTitle(title);
        }
    }

    public static void notificationOpenHandler(Context context, Intent getIntent) {
        String id = getIntent.getStringExtra(OneSignalPush.EXTRA_ID);
        String title = getIntent.getStringExtra(OneSignalPush.EXTRA_TITLE);
        String message = getIntent.getStringExtra(OneSignalPush.EXTRA_MESSAGE);
        String bigImage = getIntent.getStringExtra(OneSignalPush.EXTRA_IMAGE);
        String launchUrl = getIntent.getStringExtra(OneSignalPush.EXTRA_LAUNCH_URL);
        String link = getIntent.getStringExtra(OneSignalPush.EXTRA_LINK);
        String uniqueId = getIntent.getStringExtra(OneSignalPush.EXTRA_UNIQUE_ID);

        SharedPref sharedPref = new SharedPref(context);
        String postId;
        if (sharedPref.getPushNotificationProvider().equals("onesignal")) {
            postId = OneSignalPush.AdditionalData.postId;
        } else {
            postId = getIntent.getStringExtra(OneSignalPush.EXTRA_POST_ID);
        }

        long _postId;
        if (postId != null && !postId.equals("")) {
            _postId = Long.parseLong(postId);
        } else {
            _postId = 0;
        }

        if (getIntent.hasExtra(OneSignalPush.EXTRA_UNIQUE_ID)) {
            if (_postId > 0) {
                Intent intent = new Intent(context, ActivityNotificationDetail.class);
                intent.putExtra("id", postId);
                context.startActivity(intent);
                OneSignalPush.AdditionalData.postId = "0";
            }

            if (launchUrl != null && !launchUrl.equals("")) {
                Tools.openActivityLaunchUrl(context, title, launchUrl);
            } else {
                if (link != null && !link.equals("")) {
                    if (!link.equals("0")) {
                        Tools.openActivityLaunchUrl(context, title, link);
                    }
                }
            }

        }
    }

    public static void openActivityLaunchUrl(Context context, String title, String url) {
        if (url.contains("play.google.com") || url.contains("?target=external")) {
            context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } else {
            Intent intent = new Intent(context, ActivityWebView.class);
            intent.putExtra("title", title);
            intent.putExtra("url", url);
            context.startActivity(intent);
        }
    }

    /**
     * @noinspection deprecation
     */
    public static boolean isNetworkAvailable(Activity activity) {
        ConnectivityManager connectivity = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
        Network[] networks = connectivity.getAllNetworks();
        NetworkInfo networkInfo;
        for (Network mNetwork : networks) {
            networkInfo = connectivity.getNetworkInfo(mNetwork);
            assert networkInfo != null;
            if (networkInfo.getState().equals(NetworkInfo.State.CONNECTED)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @noinspection deprecation
     */
    public static boolean isConnect(Context context) {
        try {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
            if (activeNetworkInfo != null) {
                return activeNetworkInfo.isConnected() || activeNetworkInfo.isConnectedOrConnecting();
            } else {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    public static String withSuffix(long count) {
        if (count < 1000) return "" + count;
        int exp = (int) (Math.log(count) / Math.log(1000));
        return String.format("%.1f%c", count / Math.pow(1000, exp), "KMGTPE".charAt(exp - 1));
    }

    public static void getCategoryPosition(Activity activity, Intent intent) {
        if (intent.hasExtra("category_position")) {
            String select = intent.getStringExtra("category_position");
            if (select != null) {
                if (select.equals("category_position")) {
                    if (activity instanceof MainActivity) {
                        ((MainActivity) activity).selectCategory();
                    }
                }
            }
        }
    }

    public static void getChannelPosition(Activity activity, Intent intent) {
        if (intent.hasExtra("channel_position")) {
            String select = intent.getStringExtra("channel_position");
            if (select != null) {
                if (select.equals("channel_position")) {
                    if (activity instanceof MainActivity) {
                        ((MainActivity) activity).selectChannel();
                    }
                }
            }
        }
    }

    public static void shareApp(Activity activity) {
        String share_content = Html.fromHtml(activity.getString(R.string.share_content)).toString();
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, share_content + "\n\n" + "https://play.google.com/store/apps/details?id=" + Tools.getApplicationId());
        sendIntent.setType("text/plain");
        activity.startActivity(sendIntent);
    }

    public static void shareContent(Activity activity, String title) {
        String share_title = Html.fromHtml(title).toString();
        String share_content = Html.fromHtml(activity.getString(R.string.share_content)).toString();
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, share_title + "\n\n" + share_content + "\n\n" + "https://play.google.com/store/apps/details?id=" + Tools.getApplicationId());
        sendIntent.setType("text/plain");
        activity.startActivity(sendIntent);
    }

    public static void rateApp(Activity activity) {
        final String applicationId = Tools.getApplicationId();
        activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=" + applicationId)));
    }

    public static void sendReport(Context activity, String email, String title, String reason) {
        String str;
        try {
            str = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:"));
            intent.putExtra(Intent.EXTRA_EMAIL, new String[]{email});
            intent.putExtra(Intent.EXTRA_SUBJECT, "Report " + title + " in the " + activity.getResources().getString(R.string.app_name));
            intent.putExtra(Intent.EXTRA_TEXT, "Channel name: " + title + "\nDevice OS : Android \n Device OS version : " +
                    Build.VERSION.RELEASE + "\n App Version : " + str + "\n Device Brand : " + Build.BRAND +
                    "\n Device Model : " + Build.MODEL + "\n Device Manufacturer : " + Build.MANUFACTURER + "\n" + "Reason : " + reason);
            try {
                activity.startActivity(Intent.createChooser(intent, activity.getResources().getString(R.string.send_report)));
            } catch (android.content.ActivityNotFoundException ex) {
                Toast.makeText(activity.getApplicationContext(), "There are no email clients installed.", Toast.LENGTH_SHORT).show();
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void sendFeedback(Context activity, String email, String title) {
        String str;
        try {
            str = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName;
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(Uri.parse("mailto:"));
            intent.putExtra(Intent.EXTRA_EMAIL, new String[]{email});
            intent.putExtra(Intent.EXTRA_SUBJECT, "Feedback for " + activity.getResources().getString(R.string.app_name));
            intent.putExtra(Intent.EXTRA_TEXT, "Channel name: " + title + "\nDevice OS : Android \n Device OS version : " +
                    Build.VERSION.RELEASE + "\n App Version : " + str + "\n Device Brand : " + Build.BRAND +
                    "\n Device Model : " + Build.MODEL + "\n Device Manufacturer : " + Build.MANUFACTURER + "\n" + "Message : ");
            try {
                activity.startActivity(Intent.createChooser(intent, activity.getResources().getString(R.string.send_feedback)));
            } catch (android.content.ActivityNotFoundException ex) {
                Toast.makeText(activity.getApplicationContext(), "There are no email clients installed.", Toast.LENGTH_SHORT).show();
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void displayContent(Activity activity, WebView webView, String htmlData) {
        SharedPref sharedPref = new SharedPref(activity);
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setFocusableInTouchMode(false);
        webView.setFocusable(false);
        webView.getSettings().setDefaultTextEncodingName("UTF-8");

        WebSettings webSettings = webView.getSettings();
        Resources res = activity.getResources();
        int fontSize = res.getInteger(R.integer.font_size);
        webSettings.setDefaultFontSize(fontSize);

        String mimeType = "text/html; charset=UTF-8";
        String encoding = "utf-8";

        String bg_paragraph;
        if (sharedPref.getIsDarkTheme()) {
            bg_paragraph = "<style type=\"text/css\">body{color: #eeeeee;} a{color:#ffffff; font-weight:bold;}";
        } else {
            bg_paragraph = "<style type=\"text/css\">body{color: #000000;} a{color:#1e88e5; font-weight:bold;}";
        }

        String font_style_default = "<style type=\"text/css\">@font-face {font-family: MyFont;src: url(\"file:///android_asset/font/custom_font.ttf\")}body {font-family: MyFont; font-size: medium; overflow-wrap: break-word; word-wrap: break-word; -ms-word-break: break-all; word-break: break-all; word-break: break-word; -ms-hyphens: auto; -moz-hyphens: auto; -webkit-hyphens: auto; hyphens: auto;}</style>";

        String text = "<html><head>"
                + font_style_default
                + "<style>img{max-width:100%;height:auto;} figure{max-width:100%;height:auto;} iframe{width:100%;}</style> "
                + bg_paragraph
                + "</style></head>"
                + "<body>"
                + htmlData
                + "</body></html>";

        String text_rtl = "<html dir='rtl'><head>"
                + font_style_default
                + "<style>img{max-width:100%;height:auto;} figure{max-width:100%;height:auto;} iframe{width:100%;}</style> "
                + bg_paragraph
                + "</style></head>"
                + "<body>"
                + htmlData
                + "</body></html>";

        if (Config.ENABLE_RTL_MODE) {
            webView.loadDataWithBaseURL(null, text_rtl, mimeType, encoding, null);
        } else {
            webView.loadDataWithBaseURL(null, text, mimeType, encoding, null);
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Intent intent;
                if (url.contains("play.google.com") || url.contains("target=external") || url.contains("target=outside")) {
                    intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                } else {
                    intent = new Intent(activity, ActivityWebView.class);
                    intent.putExtra("title", "");
                    intent.putExtra("url", url);
                }
                activity.startActivity(intent);
                return true;
            }

            @SuppressLint("WebViewClientOnReceivedSslError")
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {

            }
        });

    }

    public static void startPlayer(Activity activity, View view, Channel channel) {
        if (isNetworkAvailable(activity)) {
            SharedPref sharedPref = new SharedPref(activity);
            boolean needsGuardRefresh = channel.channel_type == null
                    || (!"YOUTUBE".equals(channel.channel_type) && !"EMBED".equals(channel.channel_type));
            if (needsGuardRefresh) {
                PlaylistGuardSession.refreshIfStale(sharedPref);
            }
            Intent intent;
            if (channel.channel_type != null && channel.channel_type.equals("YOUTUBE")) {
                intent = new Intent(activity, ActivityPlayerYoutube.class);
                intent.putExtra("id", channel.video_id);
            } else if (channel.channel_type != null && channel.channel_type.equals("EMBED")) {
                intent = new Intent(activity, ActivityPlayerWebView.class);
                intent.putExtra("url", channel.channel_url);
            } else {
                intent = new Intent(activity, ActivityPlayerVideo.class);
                intent.putExtra("url", channel.channel_url);
                intent.putExtra("user_agent", channel.user_agent);
                intent.putExtra("epg_channel_id", channel.epg_id);
                intent.putExtra("epg_url", channel.epg_url);
                intent.putExtra("channel_name", channel.channel_name);
            }
            intent.putExtra("channel_id", channel.channel_id);
            activity.startActivity(intent);
            View anchor = activity.getWindow() != null ? activity.getWindow().getDecorView() : view;
            if (anchor != null) {
                anchor.postDelayed(() -> AdPlacementHelper.showInterstitialBeforeAction(activity), 1_500L);
            } else {
                AdPlacementHelper.showInterstitialBeforeAction(activity);
            }
        } else {
            Snackbar.make(view, activity.getResources().getString(R.string.network_required), Snackbar.LENGTH_SHORT).show();
        }
    }

    public static void startIntentChooserActivity(Context context, Channel channel) {
        try {
            if (channel.channel_type != null && channel.channel_type.equals("YOUTUBE")) {
                Tools.startExternalApplication(context, channel.channel_url, "com.google.android.youtube");
            } else if (channel.channel_type != null && channel.channel_type.equals("EMBED")) {
                context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(channel.channel_url)));
            } else {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(channel.channel_url));
                intent.setDataAndType(Uri.parse(channel.channel_url), "video/*");
                context.startActivity(Intent.createChooser(intent, context.getString(R.string.complete_action)));
            }
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void startExternalApplication(Context context, String url, String packageName) {
        try {
            boolean isAppInstalled = appInstalledOrNot(context, packageName);
            if (isAppInstalled) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.setPackage(packageName);
                intent.setData(Uri.parse(url));
                context.startActivity(intent);
                Log.i(TAG, "Application is already installed.");
            } else {
                context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + packageName)));
                Log.i(TAG, "Application is not currently installed.");
            }
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
            Log.i(TAG, "error : " + e.getMessage());
            Toast.makeText(context, context.getString(R.string.msg_error_handler), Toast.LENGTH_SHORT).show();
        }
    }

    public static void startExternalApplication(Context context, String url) {
        try {
            String[] results = url.split("package=");
            String packageName = results[1];
            boolean isAppInstalled = appInstalledOrNot(context, packageName);
            if (isAppInstalled) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.setPackage(packageName);
                intent.setData(Uri.parse(url));
                context.startActivity(intent);
            } else {
                context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + packageName)));
            }
        } catch (ActivityNotFoundException e) {
            e.printStackTrace();
            Toast.makeText(context, "Whoops! cannot handle this url.", Toast.LENGTH_SHORT).show();
        }
    }

    private static boolean appInstalledOrNot(Context context, String uri) {
        PackageManager pm = context.getPackageManager();
        try {
            pm.getPackageInfo(uri, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            Log.d(TAG, "NameNotFoundException");
        }
        return false;
    }


    public static void saveAds(AdsPref adsPref, Ads ads) {
        if (ads.test_mode == 1) {
            adsPref.saveAds(
                    ads.ad_status.equals("on"),
                    ads.ad_type,
                    ads.backup_ads,
                    "pub-3940256099942544",
                    "ca-app-pub-3940256099942544~3347511713",
                    "ca-app-pub-3940256099942544/9214589741",
                    "ca-app-pub-3940256099942544/1033173712",
                    "ca-app-pub-3940256099942544/2247696110",
                    "ca-app-pub-3940256099942544/9257395921",
                    "/6499/example/banner",
                    "/6499/example/interstitial",
                    "/6499/example/native",
                    "/6499/example/app-open",
                    "IMG_16_9_APP_INSTALL#1102290040176998_1102321626840506",
                    "IMG_16_9_APP_INSTALL#1102290040176998_1103218190084183",
                    "IMG_16_9_APP_INSTALL#1102290040176998_1142394442833224",
                    "0",
                    "4089993",
                    "banner",
                    "video",
                    "0",
                    "0",
                    "0",
                    "0",
                    "0",
                    "0",
                    "0",
                    "85460dcd",
                    "DefaultBanner",
                    "DefaultInterstitial",
                    "test-app-id",
                    "test-banner",
                    "test-interstitial",
                    "test-native",
                    "test-app-open",
                    ads.interstitial_ad_interval,
                    ads.native_ad_interval,
                    ads.native_ad_index,
                    ads.date_time
            );

            adsPref.setTestMode(true);
            adsPref.saveAdPlacementFlags(
                    ads.app_open_on_start == 0 ? false : true,
                    ads.app_open_on_resume == 0 ? false : true
            );
            Log.d("AdNetwork", "Ad Test Mode ON");
        } else {
            adsPref.saveAds(
                    ads.ad_status.equals("on"),
                    ads.ad_type,
                    ads.backup_ads,
                    ads.admob_publisher_id,
                    ads.admob_app_id,
                    ads.admob_banner_unit_id,
                    ads.admob_interstitial_unit_id,
                    ads.admob_native_unit_id,
                    ads.admob_app_open_ad_unit_id,
                    ads.ad_manager_banner_unit_id,
                    ads.ad_manager_interstitial_unit_id,
                    ads.ad_manager_native_unit_id,
                    ads.ad_manager_app_open_ad_unit_id,
                    ads.fan_banner_unit_id,
                    ads.fan_interstitial_unit_id,
                    ads.fan_native_unit_id,
                    ads.startapp_app_id,
                    ads.unity_game_id,
                    ads.unity_banner_placement_id,
                    ads.unity_interstitial_placement_id,
                    ads.applovin_banner_ad_unit_id,
                    ads.applovin_interstitial_ad_unit_id,
                    ads.applovin_native_ad_manual_unit_id,
                    ads.applovin_app_open_ad_unit_id,
                    ads.applovin_banner_zone_id,
                    ads.applovin_banner_mrec_zone_id,
                    ads.applovin_interstitial_zone_id,
                    ads.ironsource_app_key,
                    ads.ironsource_banner_placement_name,
                    ads.ironsource_interstitial_placement_name,
                    ads.wortise_app_id,
                    ads.wortise_banner_unit_id,
                    ads.wortise_interstitial_unit_id,
                    ads.wortise_native_unit_id,
                    ads.wortise_app_open_unit_id,
                    ads.interstitial_ad_interval,
                    ads.native_ad_interval,
                    ads.native_ad_index,
                    ads.date_time
            );

            adsPref.setTestMode(false);
            adsPref.saveAdPlacementFlags(
                    ads.app_open_on_start == 0 ? false : true,
                    ads.app_open_on_resume == 0 ? false : true
            );
            Log.d("AdNetwork", "Ad Test Mode OFF");
        }
    }

    public static String decode(String code) {
        return decodeBase64(decodeBase64(decodeBase64(code)));
    }

    public static String decodeBase64(String code) {
        byte[] valueDecoded = Base64.decode(code.getBytes(StandardCharsets.UTF_8), Base64.DEFAULT);
        return new String(valueDecoded);
    }

    public static boolean isAppInstalled(Context context, String packageName) {
        PackageManager packageManager = context.getPackageManager();
        try {
            packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES);
            return packageManager.getApplicationInfo(packageName, 0).enabled;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean isVpnConnectionAvailable() {
        String iface = "";
        try {
            for (NetworkInterface networkInst : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (networkInst.isUp())
                    iface = networkInst.getName();
                if (iface.contains("tun") || iface.contains("ppp") || iface.contains("pptp")) {
                    return true;
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static void showWarningDialog(Activity activity, String title, String message) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(activity.getString(R.string.dialog_ok), (dialog, which) -> activity.finish())
                .setCancelable(false)
                .show();
    }

    public static void postDelayed(OnCompleteListener onCompleteListener, int millisecond) {
        new Handler(Looper.getMainLooper()).postDelayed(onCompleteListener::onComplete, millisecond);
    }

    public void showBottomSheetDialog(Activity activity, View parentView, Channel channel, boolean isFragment, boolean isDetailView, OnCompleteListener onCompleteListener) {
        @SuppressLint("InflateParams") View view = activity.getLayoutInflater().inflate(R.layout.include_bottom_sheet, null);
        DAO db = AppDatabase.getDatabase(activity).get();

        FrameLayout lytBottomSheet = view.findViewById(R.id.bottom_sheet);
        ImageView sheetImage = view.findViewById(R.id.sheet_image);
        TextView sheetTitle = view.findViewById(R.id.sheet_title);
        TextView sheetCategory = view.findViewById(R.id.sheet_category);

        if (channel.channel_type != null && channel.channel_type.equals("YOUTUBE")) {
            if (!channel.channel_image.equals("")) {
                Glide.with((Context) activity)
                        .load(resolveChannelImage(channel))
                        .apply(new RequestOptions().override(Constant.list_image_width, Constant.list_image_height))
                        .placeholder(R.drawable.ic_thumbnail)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(sheetImage);
            } else {
                Glide.with((Context) activity)
                        .load(Constant.YOUTUBE_IMG_FRONT + channel.video_id + Constant.YOUTUBE_IMG_BACK)
                        .apply(new RequestOptions().override(Constant.list_image_width, Constant.list_image_height))
                        .placeholder(R.drawable.ic_thumbnail)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(sheetImage);
            }
        } else {
            Glide.with((Context) activity)
                    .load(resolveChannelImage(channel))
                    .apply(new RequestOptions().override(Constant.list_image_width, Constant.list_image_height))
                    .placeholder(R.drawable.ic_thumbnail)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(sheetImage);
        }

        sheetTitle.setText(channel.channel_name);
        sheetCategory.setText(channel.category_name);

        ImageView imgPlay = view.findViewById(R.id.img_play);
        ImageView imgPlayExternal = view.findViewById(R.id.img_play_external);
        ImageView imgFavorite = view.findViewById(R.id.img_favorite);
        ImageView imgDetails = view.findViewById(R.id.img_detail);
        ImageView imgShare = view.findViewById(R.id.img_share);
        ImageView imgReport = view.findViewById(R.id.img_report);
        ImageView imgFeedback = view.findViewById(R.id.img_feedback);
        ImageView btnClose = view.findViewById(R.id.btn_close);

        if (sharedPref.getIsDarkTheme()) {
            lytBottomSheet.setBackground(ContextCompat.getDrawable(activity, R.drawable.bg_sheet_dark));
            imgPlay.setColorFilter(ContextCompat.getColor(activity, R.color.color_dark_icon));
            imgPlayExternal.setColorFilter(ContextCompat.getColor(activity, R.color.color_dark_icon));
            imgFavorite.setColorFilter(ContextCompat.getColor(activity, R.color.color_dark_icon));
            imgDetails.setColorFilter(ContextCompat.getColor(activity, R.color.color_dark_icon));
            imgShare.setColorFilter(ContextCompat.getColor(activity, R.color.color_dark_icon));
            imgReport.setColorFilter(ContextCompat.getColor(activity, R.color.color_dark_icon));
            imgFeedback.setColorFilter(ContextCompat.getColor(activity, R.color.color_dark_icon));
            btnClose.setColorFilter(ContextCompat.getColor(activity, R.color.color_dark_icon));
        } else {
            lytBottomSheet.setBackground(ContextCompat.getDrawable(activity, R.drawable.bg_sheet_default));
            imgPlay.setColorFilter(ContextCompat.getColor(activity, R.color.color_light_icon));
            imgPlayExternal.setColorFilter(ContextCompat.getColor(activity, R.color.color_light_icon));
            imgFavorite.setColorFilter(ContextCompat.getColor(activity, R.color.color_light_icon));
            imgDetails.setColorFilter(ContextCompat.getColor(activity, R.color.color_light_icon));
            imgShare.setColorFilter(ContextCompat.getColor(activity, R.color.color_light_icon));
            imgReport.setColorFilter(ContextCompat.getColor(activity, R.color.color_light_icon));
            imgFeedback.setColorFilter(ContextCompat.getColor(activity, R.color.color_light_icon));
            btnClose.setColorFilter(ContextCompat.getColor(activity, R.color.color_light_icon));
        }

        LinearLayout btnPlay = view.findViewById(R.id.btn_play);
        LinearLayout btnPlayExternal = view.findViewById(R.id.btn_play_external);
        LinearLayout btnFavorite = view.findViewById(R.id.btn_favorite);
        LinearLayout btnDetails = view.findViewById(R.id.btn_detail);
        LinearLayout btnShare = view.findViewById(R.id.btn_share);
        LinearLayout btnReport = view.findViewById(R.id.btn_report);
        LinearLayout btnFeedback = view.findViewById(R.id.btn_feedback);

        if (isDetailView) {
            btnDetails.setVisibility(View.GONE);
        }

        btnPlay.setOnClickListener(v -> {
            Tools.startPlayer(activity, parentView, channel);
            if (isFragment) {
                ((MainActivity) activity).destroyBannerAd();
            }
            mBottomSheetDialog.dismiss();
        });

        if (!Config.ENABLE_PLAY_STREAMING_USING_EXTERNAL_PLAYER) {
            btnPlayExternal.setVisibility(View.GONE);
        }
        btnPlayExternal.setOnClickListener(v -> {
            Tools.startIntentChooserActivity(activity, channel);
            mBottomSheetDialog.dismiss();
        });

        flag_read_later = db.getChannel(channel.channel_id) != null;
        if (flag_read_later) {
            imgFavorite.setImageResource(R.drawable.ic_menu_favorite);
            ((TextView) view.findViewById(R.id.txt_favorite)).setText(activity.getString(R.string.msg_favorite_remove));
        } else {
            imgFavorite.setImageResource(R.drawable.ic_menu_favorite_outline);
            ((TextView) view.findViewById(R.id.txt_favorite)).setText(activity.getString(R.string.msg_favorite_add));
        }

        btnFavorite.setOnClickListener(v -> {
            if (isDetailView) {
                ((ActivityChannelDetail) activity).onFavoriteClicked();
            } else {
                String str;
                if (flag_read_later) {
                    db.deleteChannel(channel.channel_id);
                    str = activity.getString(R.string.favorite_removed);
                    onCompleteListener.onComplete();
                } else {
                    db.insertChannel(ChannelEntity.entity(channel));
                    str = activity.getString(R.string.favorite_added);
                }
                Snackbar.make(parentView, str, Snackbar.LENGTH_SHORT).show();
            }
            mBottomSheetDialog.dismiss();
        });

        btnDetails.setOnClickListener(v -> {
            if (!isDetailView) {
                Intent intent = new Intent(activity, ActivityChannelDetail.class);
                intent.putExtra(Constant.EXTRA_OBJC, channel);
                activity.startActivity(intent);
                if (isFragment) {
                    ((MainActivity) activity).showInterstitialAd();
                    ((MainActivity) activity).destroyBannerAd();
                }
            }
            mBottomSheetDialog.dismiss();
        });

        btnShare.setOnClickListener(v -> {
            Tools.shareContent(activity, channel.category_name);
            mBottomSheetDialog.dismiss();
        });

        btnReport.setOnClickListener(v -> {
            Tools.sendReport(activity, activity.getString(R.string.email_feedback_report), channel.channel_name, "");
            mBottomSheetDialog.dismiss();
        });

        btnFeedback.setOnClickListener(v -> {
            Tools.sendFeedback(activity, activity.getString(R.string.email_feedback_report), channel.channel_name);
            mBottomSheetDialog.dismiss();
        });

        btnClose.setOnClickListener(v -> mBottomSheetDialog.dismiss());

        if (Config.ENABLE_RTL_MODE) {
            if (sharedPref.getIsDarkTheme()) {
                mBottomSheetDialog = new BottomSheetDialog(activity, R.style.SheetDialogDarkRtl);
            } else {
                mBottomSheetDialog = new BottomSheetDialog(activity, R.style.SheetDialogLightRtl);
            }
        } else {
            if (sharedPref.getIsDarkTheme()) {
                mBottomSheetDialog = new BottomSheetDialog(activity, R.style.SheetDialogDark);
            } else {
                mBottomSheetDialog = new BottomSheetDialog(activity, R.style.SheetDialogLight);
            }
        }
        this.mBottomSheetDialog.setContentView(view);

        mBottomSheetDialog.show();
        mBottomSheetDialog.setOnDismissListener(dialog -> mBottomSheetDialog = null);

    }

    public static void setNativeAdStyle(Activity activity, LinearLayout nativeAdView, String style) {
        switch (style) {
            case "small":
            case "radio":
                nativeAdView.addView(View.inflate(activity, com.solodroidx.ads.R.layout.view_native_ad_radio, null));
                break;
            case "news":
            case "medium":
                nativeAdView.addView(View.inflate(activity, com.solodroidx.ads.R.layout.view_native_ad_news, null));
                break;
            default:
                nativeAdView.addView(View.inflate(activity, com.solodroidx.ads.R.layout.view_native_ad_medium, null));
                break;
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    public static void fullScreenMode(AppCompatActivity activity, boolean show) {
        SharedPref sharedPref = new SharedPref(activity);
        if (show) {
            activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().hide();
            }
        } else {
            activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            if (!sharedPref.getIsDarkTheme()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    activity.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                }
            }
            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().show();
            }
        }
    }

    public static String nativeAdStyleFormatter(String style) {
        return switch (style) {
            case "small" -> "radio";
            case "medium" -> "news";
            case "large" -> "medium";
            default -> "default";
        };
    }

    @SuppressLint("RtlHardcoded")
    public static void dialogExitButtonSelected(Activity activity, View rootView, AlertDialog alertDialog, OnPositiveButtonListener onPositiveButtonListener) {

        LinearLayout lytButton = rootView.findViewById(R.id.lyt_button);
        Button btnPositive = rootView.findViewById(R.id.btn_positive);
        Button btnNegative = rootView.findViewById(R.id.btn_negative);

        btnPositive.setOnClickListener(view -> Tools.postDelayed(() -> {
            onPositiveButtonListener.onPositive();
            alertDialog.dismiss();
        }, 250));

        btnNegative.setOnClickListener(view -> Tools.postDelayed(alertDialog::dismiss, 250));

        //btnPositive.setTextColor(ContextCompat.getColor(activity, R.color.color_light_primary));
        //btnNegative.setTextColor(ContextCompat.getColor(activity, R.color.color_light_primary));

        if (Config.ENABLE_RTL_MODE) {
            lytButton.setGravity(Gravity.LEFT);
            Objects.requireNonNull(alertDialog.getWindow()).getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        } else {
            lytButton.setGravity(Gravity.RIGHT);
            Objects.requireNonNull(alertDialog.getWindow()).getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        }

    }

    private String resolveChannelImage(Channel channel) {
        if (channel == null || TextUtils.isEmpty(channel.channel_image)) {
            return "";
        }
        String image = channel.channel_image.trim();
        if (image.startsWith("http://") || image.startsWith("https://")) {
            return image;
        }
        return sharedPref.getBaseUrl() + "/upload/" + image.replace(" ", "%20");
    }

    public static String getApplicationId() {
        return BuildConfig.APPLICATION_ID;
    }

    public static int getVersionCode() {
        return BuildConfig.VERSION_CODE;
    }

    public static String getVersionName() {
        return BuildConfig.VERSION_NAME;
    }

    public static boolean isDebug() {
        return BuildConfig.DEBUG;
    }

}