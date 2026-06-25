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
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.fragments.FragmentFavorite;
import com.shif.shqip.android.models.Category;
import com.shif.shqip.android.models.Channel;
import com.shif.shqip.android.rests.ApiInterface;
import com.shif.shqip.android.rests.RestAdapter;
import com.shif.shqip.android.utils.Constant;
import com.shif.shqip.android.utils.DesignRouter;
import com.shif.shqip.android.utils.Tools;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ActivityMainV2 extends AppCompatActivity {

    private SharedPref sharedPref;
    private List<Channel> allChannels = new ArrayList<>();
    private Channel featuredChannel;
    private LinearLayout categorySections;
    private TextView emptyMessage;
    private FrameLayout overlayContainer;
    private NestedScrollView scrollHome;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Tools.getTheme(this);
        sharedPref = new SharedPref(this);
        setContentView(R.layout.activity_main_v2);
        Tools.setNavigation(this);

        categorySections = findViewById(R.id.category_sections);
        emptyMessage = findViewById(R.id.empty_message);
        overlayContainer = findViewById(R.id.overlay_container);
        scrollHome = findViewById(R.id.scroll_home);

        setupTopActions();
        setupBottomNav();
        loadChannels();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshDesignIfNeeded();
    }

    private void refreshDesignIfNeeded() {
        if (!"v2".equals(sharedPref.getDesignId())) {
            startActivity(DesignRouter.getMainActivityIntent(this));
            finish();
        }
    }

    private void setupTopActions() {
        findViewById(R.id.btn_add_playlist).setOnClickListener(v ->
                startActivity(new Intent(this, ActivityPlaylistSetup.class)));
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
        navCategories.setOnClickListener(v -> showAllChannelsSheet());
        navFavorites.setOnClickListener(v -> showFavoritesOverlay());

        findViewById(R.id.banner_all_channels).setOnClickListener(v -> showAllChannelsSheet());
    }

    private void showHome() {
        overlayContainer.setVisibility(View.GONE);
        scrollHome.setVisibility(View.VISIBLE);
    }

    private void showFavoritesOverlay() {
        overlayContainer.setVisibility(View.VISIBLE);
        scrollHome.setVisibility(View.GONE);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.overlay_container, new FragmentFavorite())
                .commitAllowingStateLoss();
    }

    private void showAllChannelsSheet() {
        startActivity(new Intent(this, ActivitySearch.class));
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
        allChannels = channels == null ? new ArrayList<>() : new ArrayList<>(channels);
        if (allChannels.isEmpty()) {
            showEmpty();
            return;
        }
        emptyMessage.setVisibility(View.GONE);
        featuredChannel = allChannels.get(0);
        bindHero(featuredChannel);
        buildCategorySections();
    }

    private void showEmpty() {
        emptyMessage.setVisibility(View.VISIBLE);
        categorySections.removeAllViews();
    }

    private void bindHero(Channel channel) {
        TextView heroTitle = findViewById(R.id.hero_title);
        TextView heroSubtitle = findViewById(R.id.hero_subtitle);
        ImageView heroImage = findViewById(R.id.hero_image);
        Button watchButton = findViewById(R.id.btn_watch);

        heroTitle.setText(channel.channel_name);
        heroSubtitle.setText(TextUtils.isEmpty(channel.category_name) ? "TV" : channel.category_name);

        String imageUrl = resolveImageUrl(channel.channel_image);
        if (!TextUtils.isEmpty(imageUrl)) {
            Glide.with(this)
                    .load(imageUrl)
                    .apply(new RequestOptions().diskCacheStrategy(DiskCacheStrategy.ALL))
                    .into(heroImage);
        }

        watchButton.setOnClickListener(v -> Tools.startPlayer(this, watchButton, channel));
        findViewById(R.id.hero_container).setOnClickListener(v -> Tools.startPlayer(this, watchButton, channel));
    }

    private void buildCategorySections() {
        categorySections.removeAllViews();
        List<Category> categories = sharedPref.hasPlaylistChannels()
                ? sharedPref.getPlaylistCategories()
                : buildCategoriesFromChannels(allChannels);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (Category category : categories) {
            List<Channel> categoryChannels = getChannelsForCategory(category.category_name);
            if (categoryChannels.isEmpty()) {
                continue;
            }

            View section = inflater.inflate(R.layout.include_v2_category_section, categorySections, false);
            TextView title = section.findViewById(R.id.category_title);
            View dot = section.findViewById(R.id.category_dot);
            RecyclerView recyclerView = section.findViewById(R.id.recycler_channels);

            title.setText(category.category_name);
            tintCategoryDot(dot, category.category_name);

            AdapterChannelCardV2 adapter = new AdapterChannelCardV2(this);
            adapter.setOnChannelClickListener(channel -> Tools.startPlayer(this, recyclerView, channel));
            recyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            recyclerView.setNestedScrollingEnabled(false);
            recyclerView.setAdapter(adapter);
            adapter.setItems(categoryChannels);

            section.findViewById(R.id.category_header).setOnClickListener(v -> {
                Intent intent = new Intent(this, ActivityCategoryDetail.class);
                intent.putExtra(Constant.EXTRA_OBJC, category);
                startActivity(intent);
            });

            categorySections.addView(section);
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
}
