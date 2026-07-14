package com.shif.shqip.android.activities;

import static com.shif.shqip.android.Config.ENABLE_VPN_ACCESS;
import static com.shif.shqip.android.Config.REST_API_KEY;
import static com.shif.shqip.android.utils.Constant.BANNER_CATEGORY_DETAIL;
import static com.shif.shqip.android.utils.Constant.CHANNEL_GRID_2_COLUMN;
import static com.shif.shqip.android.utils.Constant.CHANNEL_GRID_3_COLUMN;
import static com.shif.shqip.android.utils.Constant.CHANNEL_LIST_DEFAULT;
import static com.shif.shqip.android.utils.Constant.INTERSTITIAL_AD_CHANNEL_LIST;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.OnBackPressedDispatcher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.shif.shqip.android.Config;
import com.shif.shqip.android.R;
import com.shif.shqip.android.adapters.AdapterChannel;
import com.shif.shqip.android.callbacks.CallbackDetailCategory;
import com.shif.shqip.android.databases.prefs.AdsPref;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.models.Category;
import com.shif.shqip.android.models.Channel;
import com.shif.shqip.android.rests.ApiInterface;
import com.shif.shqip.android.rests.RestAdapter;
import com.shif.shqip.android.utils.AdsManager;
import com.shif.shqip.android.utils.CategoryAdsBootstrap;
import com.shif.shqip.android.utils.CategoryDisplayHelper;
import com.shif.shqip.android.utils.Constant;
import com.shif.shqip.android.utils.PlaylistNavigationHelper;
import com.shif.shqip.android.utils.Tools;
import com.facebook.shimmer.ShimmerFrameLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ActivityCategoryDetail extends AppCompatActivity {

    private RecyclerView recyclerView;
    private AdapterChannel adapterChannel;
    private SwipeRefreshLayout swipeRefreshLayout;
    private Call<CallbackDetailCategory> callbackCall = null;
    private int post_total = 0;
    private int failed_page = 0;
    private Category category;
    private ShimmerFrameLayout lyt_shimmer;
    SharedPref sharedPref;
    AdsPref adsPref;
    AdsManager adsManager;
    CoordinatorLayout parent_view;
    Tools tools;
    OnBackPressedDispatcher onBackPressedDispatcher;
    private List<Channel> playlistSource = new ArrayList<>();
    private final ExecutorService playlistExecutor = Executors.newSingleThreadExecutor();
    private int shimmerGeneration = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Tools.getTheme(this);
        setContentView(R.layout.activity_category_details);
        Tools.setNavigation(this);

        tools = new Tools(this);
        sharedPref = new SharedPref(this);
        adsPref = new AdsPref(this);

        adsManager = new AdsManager(this);
        CategoryAdsBootstrap.init(this, adsManager, adsPref);

        category = (Category) getIntent().getSerializableExtra(Constant.EXTRA_OBJC);
        if (category == null) {
            finish();
            return;
        }

        parent_view = findViewById(R.id.parent_view);
        lyt_shimmer = findViewById(R.id.shimmer_view_container);
        initShimmerLayout();

        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        swipeRefreshLayout.setColorSchemeResources(R.color.color_light_primary);

        recyclerView = findViewById(R.id.recyclerView);
        int padding = getResources().getDimensionPixelOffset(R.dimen.grid_space_channel);

        if (sharedPref.getChannelViewType() == CHANNEL_LIST_DEFAULT) {
            recyclerView.setLayoutManager(new StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL));
        } else if (sharedPref.getChannelViewType() == CHANNEL_GRID_2_COLUMN) {
            recyclerView.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
        } else if (sharedPref.getChannelViewType() == CHANNEL_GRID_3_COLUMN) {
            recyclerView.setLayoutManager(new StaggeredGridLayoutManager(3, StaggeredGridLayoutManager.VERTICAL));
        }
        if (sharedPref.hasPlaylistChannels()) {
            recyclerView.setLayoutManager(new StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL));
            recyclerView.setPadding(0, padding, 0, padding);
            recyclerView.setItemViewCacheSize(24);
        } else if (sharedPref.getChannelViewType() == CHANNEL_LIST_DEFAULT) {
            recyclerView.setPadding(0, padding, 0, padding);
        } else {
            recyclerView.setPadding(padding, padding, padding, padding);
        }

        adapterChannel = new AdapterChannel(this, recyclerView, new ArrayList<>());
        recyclerView.setAdapter(adapterChannel);

        adapterChannel.setOnItemClickListener((v, obj, position) -> {
            Tools.startPlayer(ActivityCategoryDetail.this, v, obj);
        });

        adapterChannel.setOnItemOverflowClickListener((view, obj, position) -> {
            tools.showBottomSheetDialog(ActivityCategoryDetail.this, parent_view, obj, false, false, () -> {
            });
        });

        adapterChannel.setOnLoadMoreListener(this::setLoadMore);

        swipeRefreshLayout.setOnRefreshListener(() -> {
            if (callbackCall != null && callbackCall.isExecuted()) callbackCall.cancel();
            adapterChannel.resetListData();
            playlistSource.clear();
            requestAction(1);
        });

        if (sharedPref.hasPlaylistChannels()) {
            requestAction(1);
        } else if (ENABLE_VPN_ACCESS) {
            requestAction(1);
        } else {
            if (Tools.isVpnConnectionAvailable()) {
                Tools.showWarningDialog(this, getString(R.string.vpn_detected), getString(R.string.close_vpn));
            } else {
                requestAction(1);
            }
        }

        setupToolbar();
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

    public void setLoadMore(int current_page) {
        Log.d("page", "currentPage: " + current_page);
        int loadedChannels = adapterChannel.getRealChannelCount();
        if (post_total > loadedChannels && current_page != 0) {
            int next_page = current_page + 1;
            requestAction(next_page);
        } else {
            adapterChannel.setLoaded();
        }
    }

    public void showInterstitialAd() {
        if (adsManager != null) {
            adsManager.showInterstitialAd();
            adsManager.loadInterstitialAd(
                    INTERSTITIAL_AD_CHANNEL_LIST,
                    com.shif.shqip.android.utils.AdPlacementHelper.SOLODROID_SHOW_EVERY_CALL);
        }
    }

    public void setupToolbar() {
        final Toolbar toolbar = findViewById(R.id.toolbar);
        String title = PlaylistNavigationHelper.isAllChannelsCategory(category)
                ? getString(R.string.title_all_channels)
                : CategoryDisplayHelper.displayName(category.category_name);
        Tools.setupToolbar(this, toolbar, title, true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_search, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        int itemId = menuItem.getItemId();
        if (itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed();
            return true;
        } else if (itemId == R.id.search) {
            Intent intent = new Intent(getApplicationContext(), ActivitySearch.class);
            startActivity(intent);
            adsManager.destroyBannerAd();
            return true;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    private void displayApiResult(final List<Channel> channels) {
        adapterChannel.insertDataWithNativeAd(channels);
        swipeProgress(false);
        if (channels.isEmpty() && adapterChannel.getItemCount() == 0) {
            showNoItemView(true);
        }
    }

    private void requestPostApi(final int page_no) {
        ApiInterface apiInterface = RestAdapter.createAPI(sharedPref.getBaseUrl());
        // Request a large page so category screens load all channels in one shot.
        int pageSize = Math.max(Config.LOAD_MORE, 500);
        callbackCall = apiInterface.getChannelByCategory(category.cid, page_no, pageSize, REST_API_KEY);
        callbackCall.enqueue(new Callback<CallbackDetailCategory>() {
            @Override
            public void onResponse(@NonNull Call<CallbackDetailCategory> call, @NonNull Response<CallbackDetailCategory> response) {
                CallbackDetailCategory resp = response.body();
                if (resp != null && resp.status.equals("ok")) {
                    post_total = resp.count_total;
                    displayApiResult(resp.posts);
                    // If server still paginates, keep fetching until everything is loaded.
                    int loaded = adapterChannel.getRealChannelCount();
                    if (post_total > loaded && resp.posts != null && !resp.posts.isEmpty()) {
                        requestAction(page_no + 1);
                    }
                } else {
                    onFailRequest(page_no);
                }
            }

            @Override
            public void onFailure(@NonNull Call<CallbackDetailCategory> call, @NonNull Throwable t) {
                if (!call.isCanceled()) onFailRequest(page_no);
            }
        });
    }

    private void onFailRequest(int page_no) {
        failed_page = page_no;
        adapterChannel.setLoaded();
        swipeProgress(false);
        if (Tools.isConnect(getApplicationContext())) {
            showFailedView(true, getString(R.string.failed_text));
        } else {
            showFailedView(true, getString(R.string.no_internet_text));
        }
    }

    private void requestAction(final int page_no) {
        if (sharedPref.hasPlaylistChannels()) {
            requestPlaylistChannels(page_no);
            return;
        }
        showFailedView(false, "");
        showNoItemView(false);
        if (page_no == 1) {
            swipeProgress(true);
        } else {
            adapterChannel.setLoading();
        }
        new Handler().postDelayed(() -> requestPostApi(page_no), Constant.DELAY_TIME);
    }

    private void requestPlaylistChannels(final int page_no) {
        showFailedView(false, "");
        showNoItemView(false);
        if (page_no != 1) {
            adapterChannel.setLoaded();
            return;
        }
        swipeProgress(true);
        playlistExecutor.execute(() -> {
            List<Channel> loaded;
            if (PlaylistNavigationHelper.isAllChannelsCategory(category)) {
                loaded = sharedPref.getPlaylistChannels();
            } else {
                loaded = sharedPref.getPlaylistChannelsByCategory(category.category_name);
            }
            List<Channel> snapshot = loaded == null ? new ArrayList<>() : new ArrayList<>(loaded);
            runOnUiThread(() -> {
                if (isFinishing()) {
                    return;
                }
                playlistSource = snapshot;
                post_total = playlistSource.size();
                adapterChannel.resetListData();
                if (playlistSource.isEmpty()) {
                    adapterChannel.setLoaded();
                    swipeProgress(false);
                    showNoItemView(true);
                    return;
                }
                // Load every channel in the category at once (no pagination bug / missing logos).
                displayApiResult(playlistSource);
                adapterChannel.setLoaded();
            });
        });
    }

    private void showFailedView(boolean show, String message) {
        View view = findViewById(R.id.lyt_failed);
        ((TextView) findViewById(R.id.failed_message)).setText(message);
        if (show) {
            recyclerView.setVisibility(View.GONE);
            view.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            view.setVisibility(View.GONE);
        }
        findViewById(R.id.failed_retry).setOnClickListener(view1 -> requestAction(failed_page));
    }

    private void showNoItemView(boolean show) {
        View view = findViewById(R.id.lyt_no_item);
        ((TextView) findViewById(R.id.no_item_message)).setText(R.string.no_post_found);
        if (show) {
            recyclerView.setVisibility(View.GONE);
            view.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            view.setVisibility(View.GONE);
        }
    }

    private void swipeProgress(final boolean show) {
        if (!show) {
            shimmerGeneration++;
            swipeRefreshLayout.setRefreshing(false);
            lyt_shimmer.stopShimmer();
            lyt_shimmer.setVisibility(View.GONE);
            if (recyclerView != null) {
                recyclerView.setVisibility(View.VISIBLE);
            }
            return;
        }
        final int gen = ++shimmerGeneration;
        if (recyclerView != null) {
            recyclerView.setVisibility(View.INVISIBLE);
        }
        swipeRefreshLayout.post(() -> {
            // Guard against race: hide can run before this post executes and re-show shimmer over loaded rows.
            if (gen != shimmerGeneration || isFinishing()) {
                return;
            }
            swipeRefreshLayout.setRefreshing(true);
            lyt_shimmer.setVisibility(View.VISIBLE);
            lyt_shimmer.startShimmer();
        });
    }

    private void initShimmerLayout() {
        View lyt_shimmer_channel_list = findViewById(R.id.lyt_shimmer_channel_list);
        View lyt_shimmer_channel_grid2 = findViewById(R.id.lyt_shimmer_channel_grid2);
        View lyt_shimmer_channel_grid3 = findViewById(R.id.lyt_shimmer_channel_grid3);
        if (sharedPref.getChannelViewType() == CHANNEL_LIST_DEFAULT) {
            lyt_shimmer_channel_list.setVisibility(View.VISIBLE);
            lyt_shimmer_channel_grid2.setVisibility(View.GONE);
            lyt_shimmer_channel_grid3.setVisibility(View.GONE);
        } else if (sharedPref.getChannelViewType() == CHANNEL_GRID_2_COLUMN) {
            lyt_shimmer_channel_list.setVisibility(View.GONE);
            lyt_shimmer_channel_grid2.setVisibility(View.VISIBLE);
            lyt_shimmer_channel_grid3.setVisibility(View.GONE);
        } else if (sharedPref.getChannelViewType() == CHANNEL_GRID_3_COLUMN) {
            lyt_shimmer_channel_list.setVisibility(View.GONE);
            lyt_shimmer_channel_grid2.setVisibility(View.GONE);
            lyt_shimmer_channel_grid3.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        adsManager.resumeBannerAd(BANNER_CATEGORY_DETAIL);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        swipeProgress(false);
        if (callbackCall != null && callbackCall.isExecuted()) {
            callbackCall.cancel();
        }
        lyt_shimmer.stopShimmer();
        adsManager.destroyBannerAd();
        playlistExecutor.shutdownNow();
    }

}
