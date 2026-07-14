package com.shif.shqip.android.activities;

import static com.shif.shqip.android.Config.ENABLE_VPN_ACCESS;
import static com.shif.shqip.android.utils.Constant.BANNER_CHANNEL_DETAIL;
import static com.shif.shqip.android.utils.Constant.INTERSTITIAL_AD_CHANNEL_DETAIL;

import android.content.Intent;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.OnBackPressedDispatcher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.shif.shqip.android.Config;
import com.shif.shqip.android.R;
import com.shif.shqip.android.adapters.AdapterSuggested;
import com.shif.shqip.android.callbacks.CallbackChannelDetail;
import com.shif.shqip.android.databases.dao.AppDatabase;
import com.shif.shqip.android.databases.dao.ChannelEntity;
import com.shif.shqip.android.databases.dao.DAO;
import com.shif.shqip.android.databases.prefs.AdsPref;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.models.Channel;
import com.shif.shqip.android.rests.RestAdapter;
import com.shif.shqip.android.utils.AdsManager;
import com.shif.shqip.android.utils.ChannelLogoHelper;
import com.shif.shqip.android.utils.AppBarLayoutBehavior;
import com.shif.shqip.android.utils.Constant;
import com.shif.shqip.android.utils.Tools;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ActivityChannelDetail extends AppCompatActivity {

    private LinearLayout lyt_main_content;
    private Channel channel;
    ImageView channel_image;
    private Call<CallbackChannelDetail> callbackCall = null;
    TextView channel_name, channel_category, title_toolbar;
    TextView total_views;
    WebView channel_description;
    private DAO dao;
    boolean flag_read_later;
    CoordinatorLayout parent_view;
    private ShimmerFrameLayout lyt_shimmer;
    RelativeLayout lyt_suggested;
    private SwipeRefreshLayout swipe_refresh;
    SharedPref sharedPref;
    AdsPref adsPref;
    ImageButton btn_favorite, btn_overflow;
    AdsManager adsManager;
    Tools tools;
    OnBackPressedDispatcher onBackPressedDispatcher;
    private boolean isLocalPlaylistChannel = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Tools.getTheme(this);
        setContentView(R.layout.activity_detail);
        Tools.setNavigation(this);

        channel = (Channel) getIntent().getSerializableExtra(Constant.EXTRA_OBJC);

        tools = new Tools(this);
        sharedPref = new SharedPref(this);
        adsPref = new AdsPref(this);
        isLocalPlaylistChannel = sharedPref.hasPlaylistChannels() && "M3U".equalsIgnoreCase(channel.channel_type);

        adsManager = new AdsManager(this);
        adsManager.loadBannerAd(BANNER_CHANNEL_DETAIL);
        adsManager.loadInterstitialAd(INTERSTITIAL_AD_CHANNEL_DETAIL, 1);

        LinearLayout nativeAdView = findViewById(R.id.native_ad_view);
        Tools.setNativeAdStyle(this, nativeAdView, Constant.NATIVE_AD_STYLE_CHANNEL_DETAILS);
        adsManager.loadNativeAd(Constant.NATIVE_AD_CHANNEL_DETAILS, Constant.NATIVE_AD_STYLE_CHANNEL_DETAILS);

        dao = AppDatabase.getDatabase(this).get();

        AppBarLayout appBarLayout = findViewById(R.id.appbar);
        ((CoordinatorLayout.LayoutParams) appBarLayout.getLayoutParams()).setBehavior(new AppBarLayoutBehavior());

        swipe_refresh = findViewById(R.id.swipe_refresh_layout);
        swipe_refresh.setColorSchemeResources(R.color.color_light_primary);
        swipe_refresh.setRefreshing(false);

        lyt_main_content = findViewById(R.id.lyt_main_content);
        lyt_shimmer = findViewById(R.id.shimmer_view_container);
        parent_view = findViewById(R.id.parent_view);

        title_toolbar = findViewById(R.id.title_toolbar);
        btn_favorite = findViewById(R.id.btn_favorite);
        btn_overflow = findViewById(R.id.btn_overflow);
        channel_image = findViewById(R.id.channel_image);
        channel_name = findViewById(R.id.channel_name);
        channel_category = findViewById(R.id.channel_category);
        channel_description = findViewById(R.id.channel_description);
        total_views = findViewById(R.id.total_views);

        lyt_suggested = findViewById(R.id.lyt_suggested);

        if (isLocalPlaylistChannel) {
            displayData(channel);
            displaySuggested(getLocalSuggestedChannels());
            swipeProgress(false);
            swipe_refresh.setEnabled(false);
        } else if (Tools.isConnect(ActivityChannelDetail.this)) {
            if (ENABLE_VPN_ACCESS) {
                requestAction();
            } else {
                if (Tools.isVpnConnectionAvailable()) {
                    Tools.showWarningDialog(this, getString(R.string.vpn_detected), getString(R.string.close_vpn));
                } else {
                    requestAction();
                }
            }
            swipe_refresh.setOnRefreshListener(() -> {
                if (callbackCall != null && callbackCall.isExecuted()) callbackCall.cancel();
                lyt_shimmer.setVisibility(View.VISIBLE);
                lyt_shimmer.startShimmer();
                lyt_main_content.setVisibility(View.GONE);
                requestAction();
            });
        } else {
            displayDataOffline();
            swipeProgress(false);
            swipe_refresh.setEnabled(false);
        }

        initToolbar();
        refreshReadLaterMenu();
        handleOnBackPressed();

    }

    public void handleOnBackPressed() {
        onBackPressedDispatcher = getOnBackPressedDispatcher();
        onBackPressedDispatcher.addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
                adsManager.destroyBannerAd();
            }
        });
    }

    private void requestAction() {
        showFailedView(false, "");
        swipeProgress(true);
        new Handler().postDelayed(this::requestPostData, 200);
    }

    private void requestPostData() {
        this.callbackCall = RestAdapter.createAPI(sharedPref.getBaseUrl()).getChannelDetail(channel.channel_id);
        this.callbackCall.enqueue(new Callback<CallbackChannelDetail>() {
            public void onResponse(@NonNull Call<CallbackChannelDetail> call, @NonNull Response<CallbackChannelDetail> response) {
                CallbackChannelDetail resp = response.body();
                if (resp != null && resp.status.equals("ok")) {
                    displayAllData(resp);
                    swipeProgress(false);
                    lyt_main_content.setVisibility(View.VISIBLE);
                } else {
                    onFailRequest();
                }
            }

            public void onFailure(@NonNull Call<CallbackChannelDetail> call, @NonNull Throwable th) {
                Log.e("onFailure", "" + th.getMessage());
                if (!call.isCanceled()) {
                    onFailRequest();
                }
            }
        });
    }

    private void onFailRequest() {
        swipeProgress(false);
        lyt_main_content.setVisibility(View.GONE);
        if (Tools.isConnect(ActivityChannelDetail.this)) {
            showFailedView(true, getString(R.string.failed_text));
        } else {
            showFailedView(true, getString(R.string.failed_text));
        }
    }

    private void showFailedView(boolean show, String message) {
        View lyt_failed = findViewById(R.id.lyt_failed_home);
        ((TextView) findViewById(R.id.failed_message)).setText(message);
        if (show) {
            lyt_failed.setVisibility(View.VISIBLE);
        } else {
            lyt_failed.setVisibility(View.GONE);
        }
        findViewById(R.id.failed_retry).setOnClickListener(view -> requestAction());
    }

    private void swipeProgress(final boolean show) {
        if (!show) {
            swipe_refresh.setRefreshing(show);
            lyt_shimmer.setVisibility(View.GONE);
            lyt_shimmer.stopShimmer();
            lyt_main_content.setVisibility(View.VISIBLE);
            return;
        }
        lyt_main_content.setVisibility(View.GONE);
    }

    private void displayAllData(CallbackChannelDetail resp) {
        displayData(resp.post);
        displaySuggested(resp.suggested);
    }

    public void displayData(final Channel channel) {

        channel_name.setText(channel.channel_name);

        channel_category.setText(channel.category_name);
        if (Config.ENABLE_CHANNEL_LIST_CATEGORY_NAME) {
            channel_category.setVisibility(View.VISIBLE);
        } else {
            channel_category.setVisibility(View.GONE);
        }

        long views = 0L;
        try {
            views = Long.parseLong(channel.total_views);
        } catch (Exception ignored) {
        }
        total_views.setText(Tools.withSuffix(views) + " " + getString(R.string.views_count));

        if (channel.channel_type != null && channel.channel_type.equals("YOUTUBE")) {
            if (!channel.channel_image.equals("")) {
                Glide.with(this)
                        .load(resolveChannelImage(channel))
                        .placeholder(R.drawable.ic_thumbnail)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(channel_image);
            } else {
                Glide.with(this)
                        .load(Constant.YOUTUBE_IMG_FRONT + channel.video_id + Constant.YOUTUBE_IMG_BACK)
                        .placeholder(R.drawable.ic_thumbnail)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(channel_image);
            }
        } else {
            Glide.with(this)
                    .load(resolveChannelImage(channel))
                    .placeholder(R.drawable.ic_thumbnail)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(channel_image);
        }

        channel_image.setOnClickListener(view -> {
            if (Config.ENABLE_PLAY_STREAMING_USING_EXTERNAL_PLAYER) {
                if (sharedPref.getIsDialogPlayerOption()) {
                    showPlayerDialog();
                } else {
                    if (sharedPref.getPlayerPosition() == 0) {
                        Tools.startPlayer(this, parent_view, channel);
                    } else if (sharedPref.getPlayerPosition() == 1) {
                        Tools.startIntentChooserActivity(this, channel);
                    } else {
                        Tools.startPlayer(this, parent_view, channel);
                    }
                }
            } else {
                Tools.startPlayer(this, parent_view, channel);
            }
        });

        btn_overflow.setOnClickListener(view -> {
            tools.showBottomSheetDialog(this, parent_view, channel, false, true, () -> {
            });
        });

        Tools.displayContent(this, channel_description, channel.channel_description);

        addToFavorite();

        new Handler().postDelayed(() -> lyt_suggested.setVisibility(View.VISIBLE), 1000);

    }

    public void showPlayerDialog() {
        final MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        LayoutInflater inflater = LayoutInflater.from(this);
        View view = inflater.inflate(R.layout.dialog_player_selection, null);

        dialog.setView(view);
        dialog.setCancelable(true);

        AlertDialog alertDialog = dialog.create();

        ImageView imgActionPlay = view.findViewById(R.id.img_action_play);
        ImageView imgActionPlayExternal = view.findViewById(R.id.img_action_play_external);
        if (sharedPref.getIsDarkTheme()) {
            imgActionPlay.setColorFilter(ContextCompat.getColor(this, R.color.color_dark_icon), PorterDuff.Mode.SRC_IN);
            imgActionPlayExternal.setColorFilter(ContextCompat.getColor(this, R.color.color_dark_icon), PorterDuff.Mode.SRC_IN);
        } else {
            imgActionPlay.setColorFilter(ContextCompat.getColor(this, R.color.color_light_icon), PorterDuff.Mode.SRC_IN);
            imgActionPlayExternal.setColorFilter(ContextCompat.getColor(this, R.color.color_light_icon), PorterDuff.Mode.SRC_IN);
        }

        LinearLayout btnActionPlay = view.findViewById(R.id.btn_action_play);
        LinearLayout btnActionPlayExternal = view.findViewById(R.id.btn_action_play_external);
        MaterialCheckBox checkbox = view.findViewById(R.id.checkbox);

        btnActionPlay.setOnClickListener(v -> {
            Tools.startPlayer(this, parent_view, channel);
            sharedPref.setPlayerPosition(0);
            alertDialog.dismiss();
        });

        btnActionPlayExternal.setOnClickListener(v -> {
            Tools.startIntentChooserActivity(this, channel);
            sharedPref.setPlayerPosition(1);
            alertDialog.dismiss();
        });

        if (!Config.ENABLE_PLAY_STREAMING_USING_EXTERNAL_PLAYER) {
            btnActionPlayExternal.setVisibility(View.GONE);
        }

        checkbox.setOnCheckedChangeListener((compoundButton, checked) -> {
            sharedPref.setDialogPlayerOption(!checked);
        });

        alertDialog.show();
    }

    public void displayDataOffline() {

        channel_name.setText(channel.channel_name);

        channel_category.setText(channel.category_name);
        if (Config.ENABLE_CHANNEL_LIST_CATEGORY_NAME) {
            channel_category.setVisibility(View.VISIBLE);
        } else {
            channel_category.setVisibility(View.GONE);
        }

        if (channel.channel_type != null && channel.channel_type.equals("YOUTUBE")) {
            if (!channel.channel_image.equals("")) {
                Glide.with(this)
                        .load(resolveChannelImage(channel))
                        .placeholder(R.drawable.ic_thumbnail)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(channel_image);
            } else {
                Glide.with(this)
                        .load(Constant.YOUTUBE_IMG_FRONT + channel.video_id + Constant.YOUTUBE_IMG_BACK)
                        .placeholder(R.drawable.ic_thumbnail)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .into(channel_image);
            }
        } else {
            Glide.with(this)
                    .load(resolveChannelImage(channel))
                    .placeholder(R.drawable.ic_thumbnail)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(channel_image);
        }

        channel_image.setOnClickListener(view -> {
            Tools.startPlayer(this, parent_view, channel);
        });

        btn_overflow.setOnClickListener(view -> {
            tools.showBottomSheetDialog(this, parent_view, channel, false, true, () -> {
            });
        });

        Tools.displayContent(this, channel_description, channel.channel_description);

        addToFavorite();

        lyt_suggested.setVisibility(View.GONE);

    }

    private void showInterstitialAd() {
        if (adsPref.getInterstitialAdCounter() < adsPref.getInterstitialAdInterval()) {
            adsPref.updateInterstitialAdCounter(adsPref.getInterstitialAdCounter() + 1);
        } else {
            adsPref.updateInterstitialAdCounter(1);
            adsManager.showInterstitialAd();
        }
    }

    private void displaySuggested(List<Channel> list) {

        RecyclerView recyclerView = findViewById(R.id.recycler_view_suggested);
        recyclerView.setLayoutManager(new LinearLayoutManager(ActivityChannelDetail.this));
        AdapterSuggested adapterSuggested = new AdapterSuggested(ActivityChannelDetail.this, recyclerView, list);
        recyclerView.setAdapter(adapterSuggested);
        recyclerView.setNestedScrollingEnabled(false);
        adapterSuggested.setOnItemClickListener((view, obj, position) -> {
            Intent intent = new Intent(getApplicationContext(), ActivityChannelDetail.class);
            intent.putExtra(Constant.EXTRA_OBJC, obj);
            startActivity(intent);
            showInterstitialAd();
            adsManager.destroyBannerAd();
        });

        adapterSuggested.setOnItemOverflowClickListener((v, obj, position) -> {
            tools.showBottomSheetDialog(ActivityChannelDetail.this, parent_view, obj, false, false, () -> {
            });
        });

        TextView txt_suggested = findViewById(R.id.txt_suggested);
        if (list.size() > 0) {
            txt_suggested.setText(getResources().getString(R.string.txt_suggested));
        } else {
            txt_suggested.setText("");
        }

    }

    private List<Channel> getLocalSuggestedChannels() {
        List<Channel> suggested = new java.util.ArrayList<>();
        for (Channel item : sharedPref.getPlaylistChannelsByCategory(channel.category_name)) {
            if (!TextUtils.equals(item.channel_id, channel.channel_id)) {
                suggested.add(item);
            }
            if (suggested.size() >= 20) {
                break;
            }
        }
        return suggested;
    }

    private String resolveChannelImage(Channel item) {
        if (item == null) {
            return "";
        }
        return ChannelLogoHelper.resolve(item.channel_image, sharedPref.getBaseUrl());
    }

    private void initToolbar() {
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
            getSupportActionBar().setTitle("");
        }

        title_toolbar.setText(channel.category_name);

    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        //getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    public void addToFavorite() {
        btn_favorite.setOnClickListener(view -> {
            onFavoriteClicked();
        });
    }

    public void onFavoriteClicked() {
        String str;
        if (flag_read_later) {
            dao.deleteChannel(channel.channel_id);
            str = getString(R.string.favorite_removed);
        } else {
            dao.insertChannel(ChannelEntity.entity(channel));
            str = getString(R.string.favorite_added);
        }
        Snackbar.make(parent_view, str, Snackbar.LENGTH_SHORT).show();
        refreshReadLaterMenu();
    }

    private void refreshReadLaterMenu() {
        flag_read_later = dao.getChannel(channel.channel_id) != null;
        if (flag_read_later) {
            btn_favorite.setImageResource(R.drawable.ic_menu_favorite);
        } else {
            btn_favorite.setImageResource(R.drawable.ic_menu_favorite_outline);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed();
        } else {
            return super.onOptionsItemSelected(menuItem);
        }
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        adsManager.resumeBannerAd(BANNER_CHANNEL_DETAIL);
    }

    public void onDestroy() {
        super.onDestroy();
        lyt_shimmer.stopShimmer();
        if (!(callbackCall == null || callbackCall.isCanceled())) {
            this.callbackCall.cancel();
        }
        adsManager.destroyBannerAd();
    }

}
