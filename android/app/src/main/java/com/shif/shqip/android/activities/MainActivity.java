package com.shif.shqip.android.activities;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowMetrics;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.OnBackPressedDispatcher;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.shif.shqip.android.Config;
import com.shif.shqip.android.R;
import com.shif.shqip.android.databases.prefs.AdsPref;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.fragments.FragmentCategory;
import com.shif.shqip.android.fragments.FragmentFavorite;
import com.shif.shqip.android.fragments.FragmentRecent;
import com.shif.shqip.android.utils.AdsAntiblockHelper;
import com.shif.shqip.android.utils.AdsManager;
import com.shif.shqip.android.utils.AppBarLayoutBehavior;
import com.shif.shqip.android.utils.AppIntegrityHelper;
import com.shif.shqip.android.utils.Constant;
import com.shif.shqip.android.utils.DesignUiHelper;
import com.shif.shqip.android.utils.PlaylistGuardSession;
import com.shif.shqip.android.utils.RtlViewPager;
import com.shif.shqip.android.utils.Tools;
import com.shif.shqip.android.utils.ViewPagerHelper;
import com.google.android.gms.tasks.Task;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.appupdate.AppUpdateOptions;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.UpdateAvailability;
import com.google.android.play.core.review.ReviewInfo;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;
import com.solodroid.push.sdk.provider.OneSignalPush;
import com.solodroidx.ads.appopen.AppOpenAd;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    BottomNavigationView navigation;
    private ViewPager viewPager;
    private RtlViewPager viewPagerRTL;
    Toolbar toolbar;
    CoordinatorLayout parent_view;
    MenuItem prevMenuItem;
    int pager_number = 3;
    private long exitTime = 0;
    SharedPref sharedPref;
    AdsPref adsPref;
    AdsManager adsManager;
    AdsAntiblockHelper adsAntiblockHelper;
    private AppUpdateManager appUpdateManager;
    private ActivityResultLauncher<IntentSenderRequest> appUpdateResultLauncher;
    ViewPagerHelper viewPagerHelper;
    TextView titleToolbar;
    LinearLayout customDialogLayout;
    View dimBackground;
    FloatingActionButton btnRate;
    FloatingActionButton btnShare;
    Button btnExit;
    Button btnCancel;
    LinearLayout nativeAdView;
    private int screenWidth;
    OnBackPressedDispatcher onBackPressedDispatcher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Tools.getTheme(this);
        sharedPref = new SharedPref(this);
        adsPref = new AdsPref(this);
        setContentView(R.layout.activity_main);
        Tools.setNavigation(this);
        viewPagerHelper = new ViewPagerHelper(this);
        adsManager = new AdsManager(this);
        adsAntiblockHelper = new AdsAntiblockHelper(this);

        if (!AppIntegrityHelper.isAppAllowed(this, sharedPref)
                || !AppIntegrityHelper.isAdsConfigTrusted(this, sharedPref)) {
            finishAffinity();
            return;
        }

        Tools.postDelayed(() -> {
            adsManager.initializeAd();
            adsManager.updateConsentStatus();
            adsManager.loadBannerAd(Constant.BANNER_HOME);
            adsManager.loadInterstitialAd(Constant.INTERSTITIAL_AD_CHANNEL_LIST, adsPref.getInterstitialAdInterval());
            adsManager.loadNativeAd(Constant.NATIVE_AD_CHANNEL_LIST, Constant.NATIVE_AD_STYLE_CHANNEL_LIST);
            adsAntiblockHelper.onAdLoadFailed(this::reloadHomeAds);
            MyApplication.getInstance().preloadAppOpenAd(this);
        }, 100);

        initComponent();
        Tools.notificationOpenHandler(this, getIntent());
        Tools.getCategoryPosition(this, getIntent());
        Tools.getChannelPosition(this, getIntent());

        if (!Tools.isDebug()) {
            inAppUpdate();
            inAppReview();
        }

        new OneSignalPush.Builder(this).requestNotificationPermission();
        handleOnBackPressed();
        initExitDialog();
        getScreenDimensions();
        setCustomDialogWidth();
    }

    public void showInterstitialAd() {
        adsManager.showInterstitialAd();
    }

    public void initComponent() {

        AppBarLayout appBarLayout = findViewById(R.id.tab_appbar_layout);
        ((CoordinatorLayout.LayoutParams) appBarLayout.getLayoutParams()).setBehavior(new AppBarLayoutBehavior());

        toolbar = findViewById(R.id.toolbar);
        Tools.setupToolbar(this, toolbar, "", false);
        titleToolbar = findViewById(R.id.title_toolbar);

        parent_view = findViewById(R.id.parent_view);

        navigation = findViewById(R.id.navigation);
        navigation.getMenu().clear();
        if (Config.SET_CATEGORY_AS_MAIN_MENU) {
            navigation.inflateMenu(R.menu.navigation_category);
        } else {
            navigation.inflateMenu(R.menu.navigation_default);
        }
        if (sharedPref.getIsDarkTheme()) {
            navigation.setBackgroundColor(ContextCompat.getColor(this, R.color.color_dark_bottom_navigation));
        } else {
            navigation.setBackgroundColor(ContextCompat.getColor(this, R.color.color_light_bottom_navigation));
        }
        navigation.setLabelVisibilityMode(BottomNavigationView.LABEL_VISIBILITY_LABELED);

        viewPager = findViewById(R.id.viewpager);
        viewPagerRTL = findViewById(R.id.viewpager_rtl);
        if (Config.ENABLE_RTL_MODE) {
            if (Config.SET_CATEGORY_AS_MAIN_MENU) {
                viewPagerHelper.setupViewPagerRtlCategory(viewPagerRTL, navigation, titleToolbar);
            } else {
                viewPagerHelper.setupViewPagerRtlDefault(viewPagerRTL, navigation, titleToolbar);
            }
        } else {
            if (Config.SET_CATEGORY_AS_MAIN_MENU) {
                viewPagerHelper.setupViewPagerCategory(viewPager, navigation, titleToolbar);
            } else {
                viewPagerHelper.setupViewPagerDefault(viewPager, navigation, titleToolbar);
            }
        }

    }

    public void selectCategory() {
        if (Config.ENABLE_RTL_MODE) {
            viewPagerRTL.setCurrentItem(1);
        } else {
            viewPager.setCurrentItem(1);
        }
    }

    public void selectChannel() {
        if (Config.ENABLE_RTL_MODE) {
            viewPagerRTL.setCurrentItem(1);
        } else {
            viewPager.setCurrentItem(1);
        }
    }

    @SuppressWarnings("deprecation")
    public class MyAdapter extends FragmentPagerAdapter {

        public MyAdapter(FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @NonNull
        @Override
        public Fragment getItem(int position) {

            switch (position) {
                case 0:
                    return new FragmentRecent();
                case 1:
                    return new FragmentCategory();
                case 2:
                    return new FragmentFavorite();
            }
            return null;
        }

        @Override
        public int getCount() {
            return pager_number;
        }

    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == R.id.menu_playlist) {
            View anchor = toolbar != null ? toolbar : findViewById(android.R.id.content);
            DesignUiHelper.showAddMenu(this, anchor);
            return true;
        } else if (menuItem.getItemId() == R.id.menu_search) {
            Intent intent = new Intent(getApplicationContext(), ActivitySearch.class);
            startActivity(intent);
            destroyBannerAd();
            return true;
        } else if (menuItem.getItemId() == R.id.menu_settings) {
            Intent intent = new Intent(getApplicationContext(), ActivitySettings.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    public void handleOnBackPressed() {
        onBackPressedDispatcher = getOnBackPressedDispatcher();
        onBackPressedDispatcher.addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (Config.ENABLE_RTL_MODE) {
                    if (viewPagerRTL.getCurrentItem() != 0) {
                        viewPagerRTL.setCurrentItem((0), true);
                    } else {
                        exitApp();
                    }
                } else {
                    if (viewPager.getCurrentItem() != 0) {
                        viewPager.setCurrentItem((0), true);
                    } else {
                        exitApp();
                    }
                }
            }
        });
    }

    public void exitApp() {
        if (Config.ENABLE_EXIT_DIALOG) {
            if (customDialogLayout != null) {
                if (customDialogLayout.getVisibility() == View.VISIBLE) {
                    hideCustomDialog();
                } else {
                    showCustomDialog();
                }
            } else {
                showCustomDialog();
            }
        } else {
            if ((System.currentTimeMillis() - exitTime) > 2000) {
                showSnackBar(getString(R.string.press_again_to_exit));
                exitTime = System.currentTimeMillis();
            } else {
                finish();
                destroyBannerAd();
                destroyAppOpenAd();
            }
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    private void showSnackBar(String msg) {
        Snackbar.make(parent_view, msg, Snackbar.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!AppIntegrityHelper.isAppAllowed(this, sharedPref)
                || !AppIntegrityHelper.isAdsConfigTrusted(this, sharedPref)) {
            finishAffinity();
            return;
        }
        adsManager.resumeBannerAd(Constant.BANNER_HOME);
        reloadHomeAds();
        PlaylistGuardSession.refreshAsync(sharedPref);
        DesignUiHelper.refreshRemoteConfig(this, sharedPref, null);
    }

    private void reloadHomeAds() {
        if (!adsPref.getAdStatus()) {
            return;
        }
        adsManager.initializeAd();
        adsManager.loadBannerAd(Constant.BANNER_HOME);
        adsManager.loadInterstitialAd(Constant.INTERSTITIAL_AD_CHANNEL_LIST, adsPref.getInterstitialAdInterval());
        adsManager.loadNativeAd(Constant.NATIVE_AD_CHANNEL_LIST, Constant.NATIVE_AD_STYLE_CHANNEL_LIST);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroyBannerAd();
    }

    public void destroyAppOpenAd() {
        AppOpenAd.isAppOpenAdLoaded = false;
        adsManager.destroyAppOpenAd();
    }

    public void destroyBannerAd() {
        adsManager.destroyBannerAd();
    }

    @Override
    public AssetManager getAssets() {
        return getResources().getAssets();
    }

    private void inAppReview() {
        if (sharedPref.getInAppReviewToken() <= 3) {
            sharedPref.updateInAppReviewToken(sharedPref.getInAppReviewToken() + 1);
        } else {
            ReviewManager manager = ReviewManagerFactory.create(this);
            Task<ReviewInfo> request = manager.requestReviewFlow();
            request.addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    ReviewInfo reviewInfo = task.getResult();
                    manager.launchReviewFlow(MainActivity.this, reviewInfo).addOnFailureListener(e -> {
                    }).addOnCompleteListener(complete -> {
                                Log.d(TAG, "In-App Review Success");
                            }
                    ).addOnFailureListener(failure -> {
                        Log.d(TAG, "In-App Review Rating Failed");
                    });
                }
            }).addOnFailureListener(failure -> Log.d("In-App Review", "In-App Request Failed " + failure));
        }
        Log.d(TAG, "in app review token : " + sharedPref.getInAppReviewToken());
    }

    private void inAppUpdate() {
        appUpdateManager = AppUpdateManagerFactory.create(getApplicationContext());
        appUpdateResultLauncher = registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK) {
                showSnackBar(getString(R.string.msg_success_update));
            } else if (result.getResultCode() == Activity.RESULT_CANCELED) {
                showSnackBar(getString(R.string.msg_cancel_update));
                Log.d(TAG, "Update flow cancelled by user.");
            } else {
                showSnackBar(getString(R.string.msg_failed_update));
                Log.e(TAG, "Update flow failed with result code: " + result.getResultCode());
            }
        });

        Task<AppUpdateInfo> appUpdateInfoTask = appUpdateManager.getAppUpdateInfo();
        appUpdateInfoTask.addOnSuccessListener(appUpdateInfo -> {
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)) {
                startUpdateFlow(appUpdateInfo);
            } else if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                startUpdateFlow(appUpdateInfo);
            }
        });
    }

    private void startUpdateFlow(AppUpdateInfo appUpdateInfo) {
        AppUpdateOptions appUpdateOptions = AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).setAllowAssetPackDeletion(true).build();
        appUpdateManager.startUpdateFlowForResult(appUpdateInfo, appUpdateResultLauncher, appUpdateOptions);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Constant.IMMEDIATE_APP_UPDATE_REQ_CODE) {
            if (resultCode == RESULT_CANCELED) {
                showSnackBar(getString(R.string.msg_cancel_update));
            } else if (resultCode == RESULT_OK) {
                showSnackBar(getString(R.string.msg_success_update));
            } else {
                showSnackBar(getString(R.string.msg_failed_update));
                inAppUpdate();
            }
        }
    }

    private void initExitDialog() {
        customDialogLayout = findViewById(R.id.custom_dialog_layout);
        if (sharedPref.getIsDarkTheme()) {
            customDialogLayout.setBackgroundResource(R.drawable.bg_dialog_dark);
        } else {
            customDialogLayout.setBackgroundResource(R.drawable.bg_dialog_light);
        }
        customDialogLayout.setOnClickListener(v -> {
            //do nothing
        });

        dimBackground = findViewById(R.id.dim_background);
        btnRate = findViewById(R.id.btn_rate);
        btnShare = findViewById(R.id.btn_share);
        btnExit = findViewById(R.id.btn_exit);
        btnCancel = findViewById(R.id.btn_cancel);
        nativeAdView = findViewById(R.id.native_ad_view);

        Tools.setNativeAdStyle(MainActivity.this, nativeAdView, Constant.NATIVE_AD_STYLE_EXIT_DIALOG);
        adsManager.loadNativeAd(Constant.NATIVE_AD_EXIT_DIALOG, Constant.NATIVE_AD_STYLE_EXIT_DIALOG);

        btnRate.setOnClickListener(v -> {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("http://play.google.com/store/apps/details?id=" + Tools.getApplicationId())));
            hideCustomDialog();
        });

        btnShare.setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_SEND);
            intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name));
            intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_content) + "\n" + "https://play.google.com/store/apps/details?id=" + Tools.getApplicationId());
            intent.setType("text/plain");
            startActivity(intent);
            hideCustomDialog();
        });

        btnExit.setOnClickListener(v -> {
            finish();
            destroyBannerAd();
            destroyAppOpenAd();
            hideCustomDialog();
        });

        dimBackground.setOnClickListener(v -> hideCustomDialog());
        btnCancel.setOnClickListener(v -> hideCustomDialog());
    }

    private void getScreenDimensions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics windowMetrics = getWindowManager().getCurrentWindowMetrics();
            screenWidth = windowMetrics.getBounds().width();
        } else {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            screenWidth = displayMetrics.widthPixels;
        }
    }

    private void setCustomDialogWidth() {
        int desiredWidth = (int) (screenWidth * 0.85);
        ViewGroup.LayoutParams params = customDialogLayout.getLayoutParams();
        params.width = desiredWidth;
        customDialogLayout.setLayoutParams(params);
    }

    private void showCustomDialog() {
        customDialogLayout.setAlpha(0f);
        dimBackground.setAlpha(0f);
        customDialogLayout.setVisibility(View.VISIBLE);
        dimBackground.setVisibility(View.VISIBLE);
        customDialogLayout.animate().alpha(1f).setDuration(300).setListener(null);
        dimBackground.animate().alpha(1f).setDuration(300).setListener(null);
        isWindowLightStatusBarNavigation(false);
        onDialogShowStatusBarChanged(true);
        showBannerAd(false);
    }

    private void hideCustomDialog() {
        customDialogLayout.animate().alpha(0f).setDuration(300).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                customDialogLayout.setVisibility(View.GONE);
            }
        });
        dimBackground.animate().alpha(0f).setDuration(300).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                dimBackground.setVisibility(View.GONE);
            }
        });
        isWindowLightStatusBarNavigation(true);
        onDialogShowStatusBarChanged(false);
        showBannerAd(true);
    }

    private void onDialogShowStatusBarChanged(boolean onShow) {
        Tools.postDelayed(() -> {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                int defaultColor;
                if (sharedPref.getIsDarkTheme()) {
                    defaultColor = ContextCompat.getColor(this, R.color.color_dark_background);
                } else {
                    defaultColor = ContextCompat.getColor(this, R.color.color_light_background);
                }
                int newColor = ColorUtils.blendARGB(defaultColor, Color.BLACK, 0.5f);
                if (onShow) {
                    getWindow().setNavigationBarColor(newColor);
                    WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightNavigationBars(false);
                } else {
                    getWindow().setNavigationBarColor(defaultColor);
                }
            }
        }, 100);
    }

    private void isWindowLightStatusBarNavigation(boolean show) {
        if (sharedPref.getIsDarkTheme()) {
            WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(false);
            WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightNavigationBars(false);
        } else {
            if (show) {
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(false);
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightNavigationBars(true);
            } else {
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightStatusBars(false);
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView()).setAppearanceLightNavigationBars(false);
            }
        }
    }

    private void showBannerAd(boolean show) {
        if (show) {
            findViewById(R.id.bannerAdView).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.bannerAdView).setVisibility(View.GONE);
        }
    }

}
