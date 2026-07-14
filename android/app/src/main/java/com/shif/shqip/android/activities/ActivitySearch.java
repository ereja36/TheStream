package com.shif.shqip.android.activities;

import static com.shif.shqip.android.Config.ENABLE_RTL_MODE;
import static com.shif.shqip.android.Config.ENABLE_VPN_ACCESS;
import static com.shif.shqip.android.Config.REST_API_KEY;
import static com.shif.shqip.android.utils.Constant.BANNER_CHANNEL_DETAIL;
import static com.shif.shqip.android.utils.Constant.BANNER_SEARCH;
import static com.shif.shqip.android.utils.Constant.CHANNEL_GRID_2_COLUMN;
import static com.shif.shqip.android.utils.Constant.CHANNEL_GRID_3_COLUMN;
import static com.shif.shqip.android.utils.Constant.CHANNEL_LIST_DEFAULT;
import static com.shif.shqip.android.utils.Constant.INTERSTITIAL_AD_CHANNEL_LIST;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.OnBackPressedDispatcher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.shif.shqip.android.R;
import com.shif.shqip.android.adapters.AdapterChannel;
import com.shif.shqip.android.callbacks.CallbackChannel;
import com.shif.shqip.android.databases.prefs.AdsPref;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.rests.ApiInterface;
import com.shif.shqip.android.rests.RestAdapter;
import com.shif.shqip.android.utils.AdsManager;
import com.shif.shqip.android.utils.Constant;
import com.shif.shqip.android.utils.Tools;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ActivitySearch extends AppCompatActivity {

    private EditText edtSearch;
    private RecyclerView recyclerView;
    private AdapterChannel adapterChannel;
    private ImageButton bt_clear;
    RelativeLayout parent_view;
    Snackbar snackbar;
    Call<CallbackChannel> callbackCall = null;
    private ShimmerFrameLayout lyt_shimmer;
    SharedPref sharedPref;
    AdsPref adsPref;
    AdsManager adsManager;
    Tools tools;
    LinearLayout lytBannerAd;
    OnBackPressedDispatcher onBackPressedDispatcher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Tools.getTheme(this);
        setContentView(R.layout.activity_search);
        Tools.setNavigation(this);
        Tools.setWindowInsetsLayout(this);

        tools = new Tools(this);
        sharedPref = new SharedPref(this);
        adsPref = new AdsPref(this);

        adsManager = new AdsManager(this);
        adsManager.initializeAd();
        adsManager.updateConsentStatus();
        adsManager.loadBannerAd(BANNER_SEARCH);
        adsManager.loadInterstitialAd(INTERSTITIAL_AD_CHANNEL_LIST, com.shif.shqip.android.utils.AdPlacementHelper.SOLODROID_SHOW_EVERY_CALL);

        parent_view = findViewById(R.id.parent_view);
        edtSearch = findViewById(R.id.et_search);
        bt_clear = findViewById(R.id.bt_clear);
        bt_clear.setVisibility(View.GONE);
        lyt_shimmer = findViewById(R.id.shimmer_view_container);
        lytBannerAd = findViewById(R.id.lyt_banner_ad);
        initShimmerLayout();
        swipeProgress(false);
        recyclerView = findViewById(R.id.recyclerView);

        if (sharedPref.getChannelViewType() == CHANNEL_LIST_DEFAULT) {
            recyclerView.setLayoutManager(new StaggeredGridLayoutManager(1, StaggeredGridLayoutManager.VERTICAL));
        } else if (sharedPref.getChannelViewType() == CHANNEL_GRID_2_COLUMN) {
            recyclerView.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
        } else if (sharedPref.getChannelViewType() == CHANNEL_GRID_3_COLUMN) {
            recyclerView.setLayoutManager(new StaggeredGridLayoutManager(3, StaggeredGridLayoutManager.VERTICAL));
        }

        int padding = getResources().getDimensionPixelOffset(R.dimen.grid_space_channel);
        if (sharedPref.getChannelViewType() == CHANNEL_LIST_DEFAULT) {
            recyclerView.setPadding(0, padding, 0, padding);
        } else {
            recyclerView.setPadding(padding, padding, padding, padding);
        }

        edtSearch.addTextChangedListener(textWatcher);

        //set data and list adapter
        adapterChannel = new AdapterChannel(this, recyclerView, new ArrayList<>());
        recyclerView.setAdapter(adapterChannel);

        bt_clear.setOnClickListener(view -> edtSearch.setText(""));

        edtSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard();
                if (sharedPref.hasPlaylistChannels()) {
                    searchAction();
                } else if (ENABLE_VPN_ACCESS) {
                    searchAction();
                } else {
                    if (Tools.isVpnConnectionAvailable()) {
                        Tools.showWarningDialog(this, getString(R.string.vpn_detected), getString(R.string.close_vpn));
                    } else {
                        searchAction();
                    }
                }
                return true;
            }
            return false;
        });

        adapterChannel.setOnItemClickListener((v, obj, position) -> {
            Intent intent = new Intent(getApplicationContext(), ActivityChannelDetail.class);
            intent.putExtra(Constant.EXTRA_OBJC, obj);
            startActivity(intent);
            showInterstitialAd();
            adsManager.destroyBannerAd();
        });

        adapterChannel.setOnItemOverflowClickListener((view, obj, position) -> {
            tools.showBottomSheetDialog(ActivitySearch.this, parent_view, obj, false, false, () -> {
            });
        });

        setupToolbar();
        showEmptySearch();
        handleOnBackPressed();

    }

    public void showInterstitialAd() {
        if (adsManager != null) {
            adsManager.showInterstitialAd();
            adsManager.loadInterstitialAd(
                    INTERSTITIAL_AD_CHANNEL_LIST,
                    com.shif.shqip.android.utils.AdPlacementHelper.SOLODROID_SHOW_EVERY_CALL);
        }
    }

    public void handleOnBackPressed() {
        onBackPressedDispatcher = getOnBackPressedDispatcher();
        onBackPressedDispatcher.addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (edtSearch.length() > 0) {
                    edtSearch.setText("");
                } else {
                    finish();
                    adsManager.destroyBannerAd();
                }
            }
        });
    }

    public void setupToolbar() {
        final Toolbar toolbar = findViewById(R.id.toolbar);
        Tools.setupToolbar(this, toolbar, "", true);
    }

    TextWatcher textWatcher = new TextWatcher() {
        @Override
        public void onTextChanged(CharSequence c, int i, int i1, int i2) {
            if (c.toString().trim().length() == 0) {
                bt_clear.setVisibility(View.GONE);
            } else {
                bt_clear.setVisibility(View.VISIBLE);
            }
        }

        @Override
        public void beforeTextChanged(CharSequence c, int i, int i1, int i2) {
        }

        @Override
        public void afterTextChanged(Editable editable) {
        }
    };

    private void requestSearchApi(final String query) {
        ApiInterface apiInterface = RestAdapter.createAPI(sharedPref.getBaseUrl());
        if (ENABLE_RTL_MODE) {
            callbackCall = apiInterface.getSearchChannelRTL(query, Constant.MAX_SEARCH_RESULT, REST_API_KEY);
        } else {
            callbackCall = apiInterface.getSearchChannel(query, Constant.MAX_SEARCH_RESULT, REST_API_KEY);
        }
        callbackCall.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<CallbackChannel> call, @NonNull Response<CallbackChannel> response) {
                CallbackChannel resp = response.body();
                if (resp != null && resp.status.equals("ok")) {
                    adapterChannel.insertDataWithNativeAd(resp.posts);
                    lytBannerAd.setVisibility(View.VISIBLE);
                    adsManager.ensureBannerAd();
                    if (resp.posts.isEmpty()) {
                        showNotFoundView(true);
                    }
                } else {
                    onFailRequest();
                }
                swipeProgress(false);
            }

            @Override
            public void onFailure(@NonNull Call<CallbackChannel> call, @NonNull Throwable t) {
                onFailRequest();
                swipeProgress(false);
            }

        });
    }

    private void onFailRequest() {
        if (Tools.isConnect(this)) {
            showFailedView(true, getString(R.string.failed_text));
        } else {
            showFailedView(true, getString(R.string.no_internet_text));
        }
    }

    private void searchAction() {
        showFailedView(false, "");
        showNotFoundView(false);
        final String query = edtSearch.getText().toString().trim();
        if (!query.equals("")) {
            adapterChannel.resetListData();
            swipeProgress(true);
            if (sharedPref.hasPlaylistChannels()) {
                new Handler().postDelayed(() -> requestSearchLocal(query), Constant.DELAY_TIME);
            } else {
                new Handler().postDelayed(() -> requestSearchApi(query), Constant.DELAY_TIME);
            }
        } else {
            snackbar = Snackbar.make(parent_view, getResources().getString(R.string.msg_search_input), Snackbar.LENGTH_SHORT);
            snackbar.show();
        }
    }

    private void requestSearchLocal(String query) {
        adapterChannel.insertDataWithNativeAd(sharedPref.searchPlaylistChannels(query));
        lytBannerAd.setVisibility(View.VISIBLE);
        adsManager.ensureBannerAd();
        if (adapterChannel.getItemCount() == 0) {
            showNotFoundView(true);
        }
        swipeProgress(false);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void showFailedView(boolean show, String message) {
        View lyt_failed = findViewById(R.id.lyt_failed);
        ((TextView) findViewById(R.id.failed_message)).setText(message);
        if (show) {
            recyclerView.setVisibility(View.GONE);
            lyt_failed.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            lyt_failed.setVisibility(View.GONE);
        }
        findViewById(R.id.failed_retry).setOnClickListener(view -> searchAction());
    }

    private void showNotFoundView(boolean show) {
        View lyt_no_item = findViewById(R.id.lyt_no_item);
        ((TextView) findViewById(R.id.no_item_message)).setText(R.string.no_search_found);
        if (show) {
            recyclerView.setVisibility(View.GONE);
            lyt_no_item.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            lyt_no_item.setVisibility(View.GONE);
        }
    }

    private void showEmptySearch() {
        View lytNotFound = findViewById(R.id.lyt_no_item);
        ((TextView) findViewById(R.id.no_item_title)).setText(getString(R.string.search));
        ((TextView) findViewById(R.id.no_item_message)).setText(getString(R.string.msg_search_input));
        lytNotFound.setVisibility(View.VISIBLE);
    }

    private void swipeProgress(final boolean show) {
        if (!show) {
            lyt_shimmer.setVisibility(View.GONE);
            lyt_shimmer.stopShimmer();
        } else {
            lyt_shimmer.setVisibility(View.VISIBLE);
            lyt_shimmer.startShimmer();
        }
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
        adsManager.resumeBannerAd(BANNER_CHANNEL_DETAIL);
    }

    public void onDestroy() {
        super.onDestroy();
        adsManager.destroyBannerAd();
    }

}
