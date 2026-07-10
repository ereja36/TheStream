package com.shif.shqip.android.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.shif.shqip.android.Config;
import com.shif.shqip.android.R;
import com.shif.shqip.android.adapters.AdapterChannelCardV3;
import com.shif.shqip.android.callbacks.CallbackChannel;
import com.shif.shqip.android.databases.prefs.AdsPref;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.fragments.FragmentFavorite;
import com.shif.shqip.android.models.Channel;
import com.shif.shqip.android.rests.ApiInterface;
import com.shif.shqip.android.rests.RestAdapter;
import com.shif.shqip.android.utils.AdPlacementHelper;
import com.shif.shqip.android.utils.AdsAntiblockHelper;
import com.shif.shqip.android.utils.AdsManager;
import com.shif.shqip.android.utils.Constant;
import com.shif.shqip.android.utils.DesignInsetsHelper;
import com.shif.shqip.android.utils.DesignUiHelper;
import com.shif.shqip.android.utils.InboxNotificationHelper;
import com.shif.shqip.android.utils.PlaylistRefreshHelper;
import com.shif.shqip.android.utils.Tools;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ActivityMainV3 extends AppCompatActivity {

    private static final String TAG_FAVORITES = "v3_favorites";

    private SharedPref sharedPref;
    private AdsPref adsPref;
    private AdapterChannelCardV3 featuredAdapter;
    private AdapterChannelCardV3 gridAdapter;
    private FrameLayout overlayContainer;
    private View scrollContent;
    private TextView emptyMessage;
    private AdsManager adsManager;
    private AdsAntiblockHelper adsAntiblockHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Tools.getDesignTheme(this);
        sharedPref = new SharedPref(this);
        adsPref = new AdsPref(this);
        adsManager = new AdsManager(this);
        adsAntiblockHelper = new AdsAntiblockHelper(this);
        setContentView(R.layout.activity_main_v3);
        DesignInsetsHelper.apply(this, R.id.design_root, R.id.top_actions, R.id.bottom_nav);

        overlayContainer = findViewById(R.id.overlay_container);
        scrollContent = findViewById(R.id.scroll_home);
        emptyMessage = findViewById(R.id.empty_message);

        RecyclerView featuredRecycler = findViewById(R.id.recycler_featured);
        RecyclerView gridRecycler = findViewById(R.id.recycler_channels);

        featuredAdapter = new AdapterChannelCardV3(this);
        gridAdapter = new AdapterChannelCardV3(this);
        featuredAdapter.setOnChannelClickListener(channel -> Tools.startPlayer(this, featuredRecycler, channel));
        gridAdapter.setOnChannelClickListener(channel -> Tools.startPlayer(this, gridRecycler, channel));

        featuredRecycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        featuredRecycler.setAdapter(featuredAdapter);
        gridRecycler.setLayoutManager(new GridLayoutManager(this, 3));
        gridRecycler.setAdapter(gridAdapter);

        findViewById(R.id.btn_add_playlist).setOnClickListener(v ->
                DesignUiHelper.showAddMenu(this, v));
        findViewById(R.id.btn_search).setOnClickListener(v ->
                startActivity(new Intent(this, ActivitySearch.class)));
        findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new Intent(this, ActivitySettings.class)));

        DesignUiHelper.bindAppLogo(this, findViewById(R.id.img_app_logo), sharedPref);
        Tools.postDelayed(() -> {
            adsManager.initializeAd();
            adsManager.updateConsentStatus();
            AdPlacementHelper.loadHomePlacementAds(this, adsManager, adsPref);
            adsAntiblockHelper.onAdLoadFailed(this::reloadHomeAds);
        }, 100);
        setupBottomNav();
        loadChannels();
    }

    public void showInterstitialAd() {
        if (adsManager != null) {
            adsManager.showInterstitialAd();
        }
    }

    public void destroyBannerAd() {
        if (adsManager != null) {
            adsManager.destroyBannerAd();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adsPref.getAdStatus()) {
            adsManager.resumeBannerAd(Constant.BANNER_HOME);
            if (adsAntiblockHelper.shouldForceAdReloadOnResume()) {
                reloadHomeAds();
            }
        }
        PlaylistRefreshHelper.refreshOnResume(this, sharedPref, this::loadChannels);
        InboxNotificationHelper.pollAndShow(this, sharedPref);
        InboxNotificationHelper.handleOpenIntent(this, sharedPref, getIntent());
        DesignUiHelper.refreshRemoteConfig(this, sharedPref, () ->
                DesignUiHelper.bindAppLogo(this, findViewById(R.id.img_app_logo), sharedPref));
    }

    @Override
    protected void onDestroy() {
        destroyBannerAd();
        super.onDestroy();
    }

    private void reloadHomeAds() {
        if (!adsPref.getAdStatus()) {
            return;
        }
        AdPlacementHelper.loadHomePlacementAds(this, adsManager, adsPref);
    }

    private void setupBottomNav() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.navigation_home) {
                showHome();
                return true;
            }
            if (id == R.id.navigation_category) {
                startActivity(new Intent(this, ActivityPlaylistCategories.class));
                return true;
            }
            if (id == R.id.navigation_favorite) {
                showFavorites();
                return true;
            }
            return false;
        });
    }

    private void showHome() {
        overlayContainer.setVisibility(View.GONE);
        scrollContent.setVisibility(View.VISIBLE);
    }

    private void showFavorites() {
        overlayContainer.setVisibility(View.VISIBLE);
        scrollContent.setVisibility(View.GONE);
        Fragment existing = getSupportFragmentManager().findFragmentByTag(TAG_FAVORITES);
        if (existing == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.overlay_container, new FragmentFavorite(), TAG_FAVORITES)
                    .commit();
        }
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
        List<Channel> list = channels == null ? new ArrayList<>() : channels;
        if (list.isEmpty()) {
            showEmpty();
            return;
        }
        emptyMessage.setVisibility(View.GONE);
        int featuredCount = Math.min(8, list.size());
        featuredAdapter.setItems(list.subList(0, featuredCount));
        gridAdapter.setItems(list);
        TextView subtitle = findViewById(R.id.v3_subtitle);
        subtitle.setText(list.size() + " channels ready to stream");
    }

    private void showEmpty() {
        emptyMessage.setVisibility(View.VISIBLE);
        featuredAdapter.setItems(new ArrayList<>());
        gridAdapter.setItems(new ArrayList<>());
    }
}
