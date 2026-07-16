package com.shif.shqip.android.activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.shif.shqip.android.R;
import com.shif.shqip.android.databases.prefs.AdsPref;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.models.Category;
import com.shif.shqip.android.models.Channel;
import com.shif.shqip.android.utils.AdsManager;
import com.shif.shqip.android.utils.CategoryDisplayHelper;
import com.shif.shqip.android.utils.Constant;
import com.shif.shqip.android.utils.PlaylistNavigationHelper;
import com.shif.shqip.android.utils.Tools;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * IPTV-style browser: left categories, right full channel list (no 20/40 cap).
 */
public class ActivityPlaylistBrowser extends AppCompatActivity {

    private SharedPref sharedPref;
    private AdsManager adsManager;
    private RecyclerView sidebar;
    private RecyclerView channelList;
    private ProgressBar progressBar;
    private SidebarAdapter sidebarAdapter;
    private ChannelRowAdapter channelAdapter;
    private final List<Category> categories = new ArrayList<>();
    private final List<Channel> visibleChannels = new ArrayList<>();
    private String selectedKey = PlaylistNavigationHelper.ALL_CHANNELS_CATEGORY;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Tools.getTheme(this);
        setContentView(R.layout.activity_playlist_browser);
        Tools.setNavigation(this);

        sharedPref = new SharedPref(this);
        AdsPref adsPref = new AdsPref(this);
        adsManager = new AdsManager(this);
        if (adsPref.getAdStatus()) {
            adsManager.initializeAd();
            adsManager.loadBannerAd(true);
        }

        Category initial = null;
        try {
            initial = (Category) getIntent().getSerializableExtra(Constant.EXTRA_OBJC);
        } catch (Exception ignored) {
        }
        if (initial != null && !TextUtils.isEmpty(initial.category_name)) {
            selectedKey = initial.category_name;
        }

        Toolbar toolbar = findViewById(R.id.toolbar);
        Tools.setupToolbar(this, toolbar, getString(R.string.title_all_channels), true);
        progressBar = findViewById(R.id.progress_browser);
        sidebar = findViewById(R.id.recycler_sidebar);
        channelList = findViewById(R.id.recyclerView);

        sidebar.setLayoutManager(new LinearLayoutManager(this));
        channelList.setLayoutManager(new LinearLayoutManager(this));
        channelList.setItemViewCacheSize(40);
        channelList.setHasFixedSize(true);

        sidebarAdapter = new SidebarAdapter();
        channelAdapter = new ChannelRowAdapter();
        sidebar.setAdapter(sidebarAdapter);
        channelList.setAdapter(channelAdapter);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
                if (adsManager != null) {
                    adsManager.destroyBannerAd();
                }
            }
        });

        loadData();
    }

    private void loadData() {
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }
        executor.execute(() -> {
            List<Channel> all = sharedPref.getPlaylistChannels();
            List<Category> cats = new ArrayList<>();
            Category allCat = new Category();
            allCat.category_name = PlaylistNavigationHelper.ALL_CHANNELS_CATEGORY;
            cats.add(allCat);
            cats.addAll(sharedPref.getPlaylistCategories());
            runOnUiThread(() -> {
                if (isFinishing()) {
                    return;
                }
                categories.clear();
                categories.addAll(cats);
                sidebarAdapter.notifyDataSetChanged();
                selectCategory(selectedKey, all);
                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }
            });
        });
    }

    private void selectCategory(String key, List<Channel> allChannels) {
        selectedKey = key == null ? PlaylistNavigationHelper.ALL_CHANNELS_CATEGORY : key;
        visibleChannels.clear();
        if (PlaylistNavigationHelper.ALL_CHANNELS_CATEGORY.equals(selectedKey)) {
            if (allChannels != null) {
                visibleChannels.addAll(allChannels);
            }
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(R.string.title_all_channels);
            }
        } else {
            List<Channel> filtered = sharedPref.getPlaylistChannelsByCategory(selectedKey);
            visibleChannels.addAll(filtered);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setTitle(CategoryDisplayHelper.displayName(selectedKey));
            }
        }
        channelAdapter.notifyDataSetChanged();
        sidebarAdapter.notifyDataSetChanged();
        channelList.scrollToPosition(0);
    }

    private final class SidebarAdapter extends RecyclerView.Adapter<SidebarAdapter.Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.item_sidebar_category, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Category category = categories.get(position);
            boolean all = PlaylistNavigationHelper.ALL_CHANNELS_CATEGORY.equals(category.category_name);
            String label = all ? getString(R.string.title_all_channels)
                    : CategoryDisplayHelper.displayName(category.category_name);
            boolean selected = selectedKey.equals(category.category_name);
            holder.name.setText(label);
            holder.name.setBackgroundColor(selected ? Color.parseColor("#2563EB") : Color.TRANSPARENT);
            holder.name.setTextColor(Color.WHITE);
            holder.itemView.setOnClickListener(v -> {
                if (progressBar != null) {
                    progressBar.setVisibility(View.VISIBLE);
                }
                executor.execute(() -> {
                    List<Channel> channels = sharedPref.getPlaylistChannels();
                    runOnUiThread(() -> {
                        selectCategory(category.category_name, channels);
                        if (progressBar != null) {
                            progressBar.setVisibility(View.GONE);
                        }
                    });
                });
            });
        }

        @Override
        public int getItemCount() {
            return categories.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView name;

            Holder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.sidebar_category_name);
            }
        }
    }

    private final class ChannelRowAdapter extends RecyclerView.Adapter<ChannelRowAdapter.Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.item_playlist_channel_row, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Channel channel = visibleChannels.get(position);
            holder.name.setText(TextUtils.isEmpty(channel.channel_name)
                    ? ("Channel " + (position + 1))
                    : channel.channel_name);
            String image = channel.channel_image;
            if (!TextUtils.isEmpty(image)) {
                String url = image.startsWith("http") ? image
                        : sharedPref.getBaseUrl() + "/upload/" + image.replace(" ", "%20");
                Glide.with(ActivityPlaylistBrowser.this)
                        .load(url)
                        .placeholder(R.drawable.ic_category_app_logo)
                        .error(R.drawable.ic_category_app_logo)
                        .into(holder.logo);
            } else {
                holder.logo.setImageResource(R.drawable.ic_category_app_logo);
            }
            boolean selected = position == 0;
            holder.row.setBackgroundColor(selected ? Color.parseColor("#333333") : Color.TRANSPARENT);
            holder.itemView.setOnClickListener(v ->
                    Tools.startPlayer(ActivityPlaylistBrowser.this, v, channel));
        }

        @Override
        public int getItemCount() {
            return visibleChannels.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final View row;
            final ImageView logo;
            final TextView name;

            Holder(@NonNull View itemView) {
                super(itemView);
                row = itemView.findViewById(R.id.channel_row);
                logo = itemView.findViewById(R.id.channel_logo);
                name = itemView.findViewById(R.id.channel_name);
            }
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        if (adsManager != null) {
            adsManager.destroyBannerAd();
        }
        super.onDestroy();
    }
}
