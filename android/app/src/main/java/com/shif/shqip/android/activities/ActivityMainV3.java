package com.shif.shqip.android.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.shif.shqip.android.Config;
import com.shif.shqip.android.R;
import com.shif.shqip.android.adapters.AdapterChannelCardV3;
import com.shif.shqip.android.callbacks.CallbackChannel;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.fragments.FragmentFavorite;
import com.shif.shqip.android.models.Channel;
import com.shif.shqip.android.rests.ApiInterface;
import com.shif.shqip.android.rests.RestAdapter;
import com.shif.shqip.android.utils.DesignInsetsHelper;
import com.shif.shqip.android.utils.DesignRouter;
import com.shif.shqip.android.utils.Tools;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ActivityMainV3 extends AppCompatActivity {

    private SharedPref sharedPref;
    private AdapterChannelCardV3 featuredAdapter;
    private AdapterChannelCardV3 gridAdapter;
    private FrameLayout overlayContainer;
    private View scrollContent;
    private TextView emptyMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Tools.getTheme(this);
        sharedPref = new SharedPref(this);
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
                startActivity(new Intent(this, ActivityPlaylistSetup.class)));
        findViewById(R.id.btn_search).setOnClickListener(v ->
                startActivity(new Intent(this, ActivitySearch.class)));
        findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new Intent(this, ActivitySettings.class)));

        setupBottomNav();
        loadChannels();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!"v3".equals(sharedPref.getDesignId())) {
            startActivity(DesignRouter.getMainActivityIntent(this));
            finish();
        }
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
                startActivity(new Intent(this, ActivitySearch.class));
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
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.overlay_container, new FragmentFavorite())
                .commitAllowingStateLoss();
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
