package com.shif.shqip.android.activities;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.shif.shqip.android.Config;
import com.shif.shqip.android.R;
import com.shif.shqip.android.adapters.AdapterChannelCardV2;
import com.shif.shqip.android.callbacks.CallbackChannel;
import com.shif.shqip.android.databases.prefs.AdsPref;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.fragments.FragmentFavorite;
import com.shif.shqip.android.models.Category;
import com.shif.shqip.android.models.Channel;
import com.shif.shqip.android.rests.ApiInterface;
import com.shif.shqip.android.rests.RestAdapter;
import com.shif.shqip.android.utils.AdPlacementHelper;
import com.shif.shqip.android.utils.AdsAntiblockHelper;
import com.shif.shqip.android.utils.AdsManager;
import com.shif.shqip.android.utils.CategoryDisplayHelper;
import com.shif.shqip.android.utils.Constant;
import com.shif.shqip.android.utils.DesignInsetsHelper;
import com.shif.shqip.android.utils.DesignUiHelper;
import com.shif.shqip.android.utils.EpgHelper;
import com.shif.shqip.android.utils.NativeAdSlotHelper;
import com.shif.shqip.android.utils.HomeAdsBootstrap;
import com.shif.shqip.android.utils.InboxNotificationHelper;
import com.shif.shqip.android.utils.IptvPlayerHelper;
import com.shif.shqip.android.utils.PlaylistNavigationHelper;
import com.shif.shqip.android.utils.PlaylistRefreshHelper;
import com.shif.shqip.android.utils.StreamPlaybackHelper;
import com.shif.shqip.android.utils.Tools;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.upstream.DefaultDataSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ActivityMainV2 extends AppCompatActivity {

    private SharedPref sharedPref;
    private AdsPref adsPref;
    private List<Channel> allChannels = new ArrayList<>();
    private Channel featuredChannel;
    private LinearLayout categorySections;
    private TextView emptyMessage;
    private FrameLayout overlayContainer;
    private NestedScrollView scrollHome;
    private final ExecutorService epgExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService heroExecutor = Executors.newSingleThreadExecutor();
    private ExoPlayer heroPlayer;
    private StyledPlayerView heroPreview;
    private Channel heroPreviewChannel;
    private long heroPreviewToken;
    private AdsManager adsManager;
    private AdsAntiblockHelper adsAntiblockHelper;
    private boolean allChannelsNativeBound;
    private boolean heroMuted = true;
    private final List<AdsManager> nativeSlotManagers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Tools.getDesignTheme(this);
        sharedPref = new SharedPref(this);
        adsPref = new AdsPref(this);
        adsManager = new AdsManager(this);
        adsAntiblockHelper = new AdsAntiblockHelper(this);
        setContentView(R.layout.activity_main_v2);
        DesignInsetsHelper.apply(this, R.id.design_root, R.id.top_actions, R.id.bottom_container,
                R.id.scroll_home, 0);

        categorySections = findViewById(R.id.category_sections);
        emptyMessage = findViewById(R.id.empty_message);
        overlayContainer = findViewById(R.id.overlay_container);
        overlayContainer.setClickable(false);
        overlayContainer.setFocusable(false);
        scrollHome = findViewById(R.id.scroll_home);

        setupTopActions();
        setupBottomNav();
        bringChromeToFront();
        DesignUiHelper.bindAppLogo(this, findViewById(R.id.img_app_logo), sharedPref);
        HomeAdsBootstrap.initFast(this, adsManager, adsPref, adsAntiblockHelper);
        bindAllChannelsNativeAd();
        loadChannels();
    }

    public void showInterstitialAd() {
        if (adsManager != null) {
            adsManager.showInterstitialAd();
            adsManager.loadInterstitialAd(
                    Constant.INTERSTITIAL_AD_CHANNEL_LIST,
                    AdPlacementHelper.resolveInterstitialInterval(adsPref));
        }
    }

    public void destroyBannerAd() {
        if (adsManager != null) {
            adsManager.destroyBannerAd();
        }
    }

    @Override
    protected void onDestroy() {
        destroyBannerAd();
        releaseHeroPreview();
        nativeSlotManagers.clear();
        super.onDestroy();
        epgExecutor.shutdownNow();
        heroExecutor.shutdownNow();
    }

    @Override
    protected void onPause() {
        releaseHeroPreview();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        HomeAdsBootstrap.resume(this, adsManager, adsPref, adsAntiblockHelper);
        InboxNotificationHelper.pollAndShow(this, sharedPref);
        handleInboxNotificationIntent(getIntent());
        if (featuredChannel != null && Config.ENABLE_HERO_LIVE_PREVIEW) {
            scheduleHeroPreview(featuredChannel);
        }
        PlaylistRefreshHelper.refreshOnResume(this, sharedPref, this::loadChannels);
        DesignUiHelper.refreshRemoteConfig(this, sharedPref, () ->
                DesignUiHelper.bindAppLogo(this, findViewById(R.id.img_app_logo), sharedPref));
    }

    private void reloadHomeAds() {
        if (!adsPref.getAdStatus()) {
            return;
        }
        AdPlacementHelper.loadHomePlacementAds(this, adsManager, adsPref);
    }

    private void setupTopActions() {
        findViewById(R.id.btn_add_playlist).setOnClickListener(v ->
                DesignUiHelper.showAddMenu(this, v));
        findViewById(R.id.btn_search).setOnClickListener(v ->
                startActivity(new Intent(this, ActivitySearch.class)));
        findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new Intent(this, ActivitySettings.class)));
    }

    private void setupBottomNav() {
        ImageButton navHome = findViewById(R.id.nav_home);
        ImageButton navCategories = findViewById(R.id.nav_categories);
        ImageButton navFavorites = findViewById(R.id.nav_favorites);

        navHome.setOnClickListener(v -> showHome());
        navCategories.setOnClickListener(v -> {
            AdPlacementHelper.showInterstitialBeforeAction(this);
            showCategories();
        });
        navFavorites.setOnClickListener(v -> {
            AdPlacementHelper.showInterstitialBeforeAction(this);
            showFavoritesOverlay();
        });

        findViewById(R.id.banner_all_channels).setOnClickListener(v -> {
            AdPlacementHelper.showInterstitialBeforeAction(this);
            showAllChannels();
        });
    }

    private void showCategories() {
        startActivity(new Intent(this, ActivityPlaylistCategories.class));
    }

    private void showAllChannels() {
        startActivity(PlaylistNavigationHelper.createAllChannelsIntent(this));
    }

    private void showHome() {
        overlayContainer.setVisibility(View.GONE);
        overlayContainer.setClickable(false);
        scrollHome.setVisibility(View.VISIBLE);
        bringChromeToFront();
    }

    private void showFavoritesOverlay() {
        overlayContainer.setVisibility(View.VISIBLE);
        overlayContainer.setClickable(true);
        scrollHome.setVisibility(View.GONE);
        overlayContainer.bringToFront();
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.overlay_container, new FragmentFavorite())
                .commitAllowingStateLoss();
    }

    private void bringChromeToFront() {
        View topActions = findViewById(R.id.top_actions);
        View bottomNav = findViewById(R.id.bottom_nav_pill);
        View bottomContainer = findViewById(R.id.bottom_container);
        DesignInsetsHelper.bringChromeToFront(topActions, bottomContainer, bottomNav);
    }

    private void loadChannels() {
        if (sharedPref.hasPlaylistChannels()) {
            applyChannels(sharedPref.getPlaylistChannels());
            return;
        }
        ApiInterface apiInterface = RestAdapter.createAPI(sharedPref.getBaseUrl());
        apiInterface.getRecentChannel(1, 500, Config.REST_API_KEY).enqueue(new Callback<CallbackChannel>() {
            @Override
            public void onResponse(@NonNull Call<CallbackChannel> call, @NonNull Response<CallbackChannel> response) {
                CallbackChannel body = response.body();
                if (body != null && "ok".equals(body.status) && body.posts != null) {
                    applyChannels(body.posts);
                } else {
                    showEmpty();
                }
            }

            @Override
            public void onFailure(@NonNull Call<CallbackChannel> call, @NonNull Throwable t) {
                showEmpty();
            }
        });
    }

    private void applyChannels(List<Channel> channels) {
        try {
            allChannels = channels == null ? new ArrayList<>() : new ArrayList<>(channels);
            if (allChannels.isEmpty()) {
                showEmpty();
                return;
            }
            emptyMessage.setVisibility(View.GONE);
            featuredChannel = allChannels.get(0);
            bindHero(featuredChannel);
            categorySections.post(this::buildCategorySections);
        } catch (Exception e) {
            showEmpty();
        }
    }

    private void showEmpty() {
        emptyMessage.setVisibility(View.VISIBLE);
        categorySections.removeAllViews();
    }

    private void bindHero(Channel channel) {
        TextView heroTitle = findViewById(R.id.hero_title);
        TextView heroSubtitle = findViewById(R.id.hero_subtitle);
        TextView heroEpg = findViewById(R.id.hero_epg);
        ImageView heroImage = findViewById(R.id.hero_image);
        Button watchButton = findViewById(R.id.btn_watch);

        heroTitle.setText(channel.channel_name);
        heroSubtitle.setText(CategoryDisplayHelper.displayName(channel.category_name));
        heroEpg.setVisibility(View.GONE);

        String imageUrl = resolveImageUrl(channel.channel_image);
        if (!TextUtils.isEmpty(imageUrl)) {
            Glide.with(this)
                    .load(imageUrl)
                    .apply(new RequestOptions().diskCacheStrategy(DiskCacheStrategy.ALL))
                    .into(heroImage);
        }

        watchButton.setOnClickListener(v -> openChannel(channel));
        findViewById(R.id.hero_container).setOnClickListener(v -> openChannel(channel));
        loadHeroEpg(channel, heroEpg);
        setupHeroMuteButton();
        scheduleHeroPreview(channel);
    }

    private void openChannel(Channel channel) {
        stopHeroForPlayback();
        View anchor = findViewById(R.id.btn_watch);
        Tools.startPlayer(this, anchor, channel);
    }

    private void stopHeroForPlayback() {
        releaseHeroPreview();
    }

    private void setupHeroMuteButton() {
        ImageButton muteButton = findViewById(R.id.btn_hero_mute);
        if (muteButton == null) {
            return;
        }
        muteButton.setOnClickListener(v -> {
            heroMuted = !heroMuted;
            if (heroPlayer != null) {
                heroPlayer.setVolume(heroMuted ? 0f : 1f);
            }
            muteButton.setImageResource(heroMuted
                    ? android.R.drawable.ic_lock_silent_mode
                    : android.R.drawable.ic_lock_silent_mode_off);
        });
    }

    private void scheduleHeroPreview(Channel channel) {
        if (!Config.ENABLE_HERO_LIVE_PREVIEW) {
            return;
        }
        final long token = ++heroPreviewToken;
        heroExecutor.execute(() -> {
            try {
                Thread.sleep(2500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (isFinishing()) {
                return;
            }
            runOnUiThread(() -> {
                if (token != heroPreviewToken || isFinishing() || isDestroyed()) {
                    return;
                }
                startHeroPreview(channel);
            });
        });
    }

    private void ensureHeroPreviewView() {
        if (heroPreview != null) {
            return;
        }
        FrameLayout container = findViewById(R.id.hero_preview_container);
        if (container == null) {
            return;
        }
        try {
            heroPreview = new StyledPlayerView(this);
            heroPreview.setLayoutParams(new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
            heroPreview.setClickable(false);
            heroPreview.setFocusable(false);
            heroPreview.setUseController(false);
            container.addView(heroPreview);
        } catch (Exception ignored) {
            heroPreview = null;
        }
    }

    private void startHeroPreview(Channel channel) {
        if (channel == null || TextUtils.isEmpty(channel.channel_url)) {
            return;
        }
        if (heroPlayer != null && heroPreviewChannel != null
                && TextUtils.equals(heroPreviewChannel.channel_id, channel.channel_id)) {
            resumeHeroPreview();
            return;
        }
        String streamUrl = channel.channel_url.trim().toLowerCase();
        if (!streamUrl.startsWith("http") && !streamUrl.startsWith("rtsp") && !streamUrl.startsWith("rtmp")) {
            return;
        }
        releaseHeroPreview();
        heroPreviewChannel = channel;
        final Channel previewChannel = channel;
        heroExecutor.execute(() -> {
            String playbackUrl = StreamPlaybackHelper.resolvePlaybackUrl(
                    sharedPref, previewChannel.channel_url, previewChannel.epg_id);
            if (TextUtils.isEmpty(playbackUrl)) {
                return;
            }
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || heroPreviewChannel == null) {
                    return;
                }
                startHeroPreviewPlayer(previewChannel, playbackUrl);
            });
        });
    }

    private void startHeroPreviewPlayer(Channel channel, String playbackUrl) {
        if (channel == null || heroPreviewChannel == null) {
            return;
        }
        ensureHeroPreviewView();
        if (heroPreview == null) {
            return;
        }
        try {
            FrameLayout container = findViewById(R.id.hero_preview_container);
            if (container != null) {
                container.setVisibility(View.VISIBLE);
            }
            heroPlayer = IptvPlayerHelper.createPlayer(this);
            heroPlayer.setVolume(heroMuted ? 0f : 1f);
            heroPreview.setPlayer(heroPlayer);
            heroPreview.setUseController(false);

            DefaultDataSource.Factory factory = IptvPlayerHelper.createDataSourceFactory(
                    this, sharedPref, channel.channel_url, channel.user_agent);
            MediaSource mediaSource = IptvPlayerHelper.buildMediaSource(playbackUrl, factory);
            heroPlayer.setMediaSource(mediaSource);
            heroPlayer.prepare();
            heroPlayer.setPlayWhenReady(true);
            heroPreview.setVisibility(View.VISIBLE);
            findViewById(R.id.hero_image).setVisibility(View.GONE);
            ImageButton muteButton = findViewById(R.id.btn_hero_mute);
            if (muteButton != null) {
                muteButton.setVisibility(View.VISIBLE);
            }
            bringChromeToFront();
        } catch (Exception ignored) {
            releaseHeroPreview();
        }
    }

    private void pauseHeroPreview() {
        if (heroPlayer != null) {
            heroPlayer.setPlayWhenReady(false);
        }
    }

    private void resumeHeroPreview() {
        if (heroPlayer != null) {
            heroPlayer.setPlayWhenReady(true);
        }
    }

    private void releaseHeroPreview() {
        heroPreviewToken++;
        if (heroPreview != null) {
            heroPreview.setPlayer(null);
        }
        if (heroPlayer != null) {
            heroPlayer.release();
            heroPlayer = null;
        }
        heroPreviewChannel = null;
        ImageView heroImage = findViewById(R.id.hero_image);
        if (heroImage != null) {
            heroImage.setVisibility(View.VISIBLE);
        }
        if (heroPreview != null) {
            heroPreview.setVisibility(View.GONE);
        }
        FrameLayout container = findViewById(R.id.hero_preview_container);
        if (container != null) {
            container.setVisibility(View.GONE);
        }
        ImageButton muteButton = findViewById(R.id.btn_hero_mute);
        if (muteButton != null) {
            muteButton.setVisibility(View.GONE);
        }
    }

    private void loadHeroEpg(Channel channel, TextView heroEpg) {
        if (heroEpg == null || TextUtils.isEmpty(channel.epg_url)) {
            return;
        }
        epgExecutor.execute(() -> {
            EpgHelper.EpgPrograms programs = EpgHelper.fetchPrograms(
                    channel.epg_url,
                    channel.epg_id,
                    channel.channel_name
            );
            runOnUiThread(() -> {
                if (isFinishing() || programs == null || programs.now == null) {
                    return;
                }
                String unknown = getString(R.string.epg_unknown_title);
                String nowText = getString(R.string.epg_now_format, EpgHelper.formatProgram(programs.now, unknown));
                heroEpg.setText(nowText);
                heroEpg.setVisibility(View.VISIBLE);
            });
        });
    }

    private void buildCategorySections() {
        categorySections.removeAllViews();
        List<Category> categories = sharedPref.hasPlaylistChannels()
                ? sharedPref.getPlaylistCategories()
                : buildCategoriesFromChannels(allChannels);

        LayoutInflater inflater = LayoutInflater.from(this);
        int sectionIndex = 0;
        for (Category category : categories) {
            List<Channel> categoryChannels = getChannelsForCategory(category.category_name);
            if (categoryChannels.isEmpty()) {
                continue;
            }
            if (categoryChannels.size() > 20) {
                categoryChannels = new ArrayList<>(categoryChannels.subList(0, 20));
            }

            View section = inflater.inflate(R.layout.include_v2_category_section, categorySections, false);
            TextView title = section.findViewById(R.id.category_title);
            View dot = section.findViewById(R.id.category_dot);
            RecyclerView recyclerView = section.findViewById(R.id.recycler_channels);

            title.setText(category.category_name);
            tintCategoryDot(dot, category.category_name);

            AdapterChannelCardV2 adapter = new AdapterChannelCardV2(this);
            adapter.setOnChannelClickListener(channel -> {
                stopHeroForPlayback();
                Tools.startPlayer(this, recyclerView, channel);
            });
            recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            recyclerView.setNestedScrollingEnabled(false);
            recyclerView.setNestedScrollingEnabled(false);
            recyclerView.setAdapter(adapter);
            adapter.setItems(categoryChannels);

            section.findViewById(R.id.category_header).setOnClickListener(v -> {
                AdPlacementHelper.showInterstitialBeforeAction(this);
                Intent intent = new Intent(this, ActivityCategoryDetail.class);
                intent.putExtra(Constant.EXTRA_OBJC, category);
                startActivity(intent);
            });

            categorySections.addView(section);
            sectionIndex++;
            addNativeAdSlot();
        }
    }

    private void bindAllChannelsNativeAd() {
        if (allChannelsNativeBound || !adsPref.getAdStatus() || !Constant.NATIVE_AD_CHANNEL_LIST) {
            return;
        }
        View banner = findViewById(R.id.banner_all_channels);
        if (!(banner.getParent() instanceof LinearLayout)) {
            return;
        }
        LinearLayout parent = (LinearLayout) banner.getParent();
        int bannerIndex = parent.indexOfChild(banner);
        LinearLayout nativeHost = new LinearLayout(this);
        nativeHost.setOrientation(LinearLayout.VERTICAL);
        int horizontal = (int) getResources().getDimension(R.dimen.spacing_middle);
        nativeHost.setPadding(horizontal, 0, horizontal, 0);
        parent.addView(nativeHost, bannerIndex + 1);
        AdsManager slot = NativeAdSlotHelper.mount(this, nativeHost);
        if (slot != null) {
            nativeSlotManagers.add(slot);
        }
        allChannelsNativeBound = true;
    }

    private void addNativeAdSlot() {
        if (!adsPref.getAdStatus() || !Constant.NATIVE_AD_CHANNEL_LIST) {
            return;
        }
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        int horizontal = (int) getResources().getDimension(R.dimen.spacing_middle);
        int vertical = (int) getResources().getDimension(R.dimen.spacing_middle);
        wrapper.setPadding(horizontal, vertical, horizontal, vertical);
        LinearLayout nativeHost = new LinearLayout(this);
        nativeHost.setOrientation(LinearLayout.VERTICAL);
        wrapper.addView(nativeHost, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        categorySections.addView(wrapper);
        AdsManager slot = NativeAdSlotHelper.mount(this, nativeHost);
        if (slot != null) {
            nativeSlotManagers.add(slot);
        }
    }

    private List<Category> buildCategoriesFromChannels(List<Channel> channels) {
        List<Category> categories = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        int id = 1;
        for (Channel channel : channels) {
            String name = TextUtils.isEmpty(channel.category_name) ? "TV" : channel.category_name;
            if (!seen.contains(name)) {
                seen.add(name);
                Category category = new Category();
                category.cid = id++;
                category.category_name = name;
                categories.add(category);
            }
        }
        return categories;
    }

    private List<Channel> getChannelsForCategory(String categoryName) {
        if (sharedPref.hasPlaylistChannels()) {
            return sharedPref.getPlaylistChannelsByCategory(categoryName);
        }
        List<Channel> result = new ArrayList<>();
        for (Channel channel : allChannels) {
            String name = TextUtils.isEmpty(channel.category_name) ? "TV" : channel.category_name;
            if (name.equalsIgnoreCase(categoryName)) {
                result.add(channel);
            }
        }
        return result;
    }

    private void tintCategoryDot(View dot, String categoryName) {
        int color = R.color.v2_category_default;
        String lower = categoryName == null ? "" : categoryName.toLowerCase();
        if (lower.contains("sport")) {
            color = R.color.v2_category_sport;
        } else if (lower.contains("tv") || lower.contains("film") || lower.contains("movie")) {
            color = R.color.v2_category_tv;
        }
        dot.setBackgroundTintList(ColorStateList.valueOf(getColor(color)));
    }

    private String resolveImageUrl(String image) {
        if (TextUtils.isEmpty(image)) {
            return "";
        }
        if (image.startsWith("http")) {
            return image;
        }
        return sharedPref.getBaseUrl() + "/upload/" + image.replace(" ", "%20");
    }

    private void handleInboxNotificationIntent(Intent intent) {
        InboxNotificationHelper.handleOpenIntent(this, sharedPref, intent);
    }
}
