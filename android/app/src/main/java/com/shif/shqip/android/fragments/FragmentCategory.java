package com.shif.shqip.android.fragments;

import static com.shif.shqip.android.Config.ENABLE_VPN_ACCESS;
import static com.shif.shqip.android.utils.Constant.CATEGORY_GRID_2_COLUMN;
import static com.shif.shqip.android.utils.Constant.CATEGORY_GRID_3_COLUMN;
import static com.shif.shqip.android.utils.Constant.CATEGORY_LIST_DEFAULT;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.shif.shqip.android.Config;
import com.shif.shqip.android.R;
import com.shif.shqip.android.activities.ActivityCategoryDetail;
import com.shif.shqip.android.activities.MainActivity;
import com.shif.shqip.android.adapters.AdapterCategory;
import com.shif.shqip.android.callbacks.CallbackCategories;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.models.Category;
import com.shif.shqip.android.rests.ApiInterface;
import com.shif.shqip.android.rests.RestAdapter;
import com.shif.shqip.android.utils.AdPlacementHelper;
import com.shif.shqip.android.utils.Constant;
import com.shif.shqip.android.utils.Tools;
import com.facebook.shimmer.ShimmerFrameLayout;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FragmentCategory extends Fragment {

    private View root_view;
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private AdapterCategory adapterCategory;
    public static final String EXTRA_OBJC = "key.EXTRA_OBJC";
    private Call<CallbackCategories> callbackCall = null;
    private ShimmerFrameLayout lyt_shimmer;
    SharedPref sharedPref;
    private Activity activity;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        activity = (Activity) context;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        root_view = inflater.inflate(R.layout.fragment_category, container, false);
        sharedPref = new SharedPref(activity);

        lyt_shimmer = root_view.findViewById(R.id.shimmer_view_container);
        initShimmerLayout();

        swipeRefreshLayout = root_view.findViewById(R.id.swipe_refresh_layout_category);
        swipeRefreshLayout.setColorSchemeResources(R.color.color_light_primary);

        recyclerView = root_view.findViewById(R.id.recyclerViewCategory);

        //ItemOffsetDecoration itemDecoration = new ItemOffsetDecoration(activity, R.dimen.grid_space_category);
        if (sharedPref.getCategoryViewType() == CATEGORY_LIST_DEFAULT) {
            recyclerView.setLayoutManager(new StaggeredGridLayoutManager(1, LinearLayoutManager.VERTICAL));
        } else if (sharedPref.getCategoryViewType() == CATEGORY_GRID_2_COLUMN) {
            recyclerView.setLayoutManager(new StaggeredGridLayoutManager(2, LinearLayoutManager.VERTICAL));
            //recyclerView.addItemDecoration(itemDecoration);
        } else if (sharedPref.getCategoryViewType() == CATEGORY_GRID_3_COLUMN) {
            recyclerView.setLayoutManager(new StaggeredGridLayoutManager(3, LinearLayoutManager.VERTICAL));
            //recyclerView.addItemDecoration(itemDecoration);
        }

        int padding = getResources().getDimensionPixelOffset(R.dimen.grid_space_category);
        if (sharedPref.getCategoryViewType() == CATEGORY_LIST_DEFAULT) {
            recyclerView.setPadding(0, padding, 0, padding);
        } else {
            recyclerView.setPadding(padding, padding, padding, padding);
        }

        adapterCategory = new AdapterCategory(activity, new ArrayList<>());
        recyclerView.setAdapter(adapterCategory);

        adapterCategory.setOnItemClickListener((v, obj, position) -> {
            Intent intent = new Intent(activity, ActivityCategoryDetail.class);
            intent.putExtra(EXTRA_OBJC, obj);
            startActivity(intent);
            AdPlacementHelper.showInterstitialBeforeAction(activity);
            AdPlacementHelper.pauseHomeBanner(activity);
        });

        // on swipe list
        swipeRefreshLayout.setOnRefreshListener(() -> {
            adapterCategory.resetListData();
            requestAction();
        });

        if (sharedPref.hasPlaylistChannels()) {
            requestAction();
        } else if (ENABLE_VPN_ACCESS) {
            requestAction();
        } else {
            if (Tools.isVpnConnectionAvailable()) {
                Tools.showWarningDialog(activity, getString(R.string.vpn_detected), getString(R.string.close_vpn));
            } else {
                requestAction();
            }
        }

        return root_view;
    }

    private void displayApiResult(final List<Category> categories) {
        adapterCategory.setListData(categories);
        swipeProgress(false);
        if (categories.size() == 0) {
            showNoItemView(true);
        }
    }

    private void requestCategoriesApi() {
        ApiInterface apiInterface = RestAdapter.createAPI(sharedPref.getBaseUrl());
        callbackCall = apiInterface.getAllCategories(Config.REST_API_KEY);
        callbackCall.enqueue(new Callback<CallbackCategories>() {
            @Override
            public void onResponse(@NonNull Call<CallbackCategories> call, @NonNull Response<CallbackCategories> response) {
                CallbackCategories resp = response.body();
                if (resp != null && resp.status.equals("ok")) {
                    displayApiResult(resp.categories);
                } else {
                    onFailRequest();
                }
            }

            @Override
            public void onFailure(@NonNull Call<CallbackCategories> call, @NonNull Throwable t) {
                if (!call.isCanceled()) onFailRequest();
            }
        });

    }

    private void onFailRequest() {
        swipeProgress(false);
        if (Tools.isConnect(activity)) {
            showFailedView(true, getString(R.string.failed_text));
        } else {
            showFailedView(true, getString(R.string.no_internet_text));
        }
    }

    private void requestAction() {
        if (sharedPref.hasPlaylistChannels()) {
            requestPlaylistCategories();
            return;
        }
        showFailedView(false, "");
        swipeProgress(true);
        showNoItemView(false);
        new Handler().postDelayed(this::requestCategoriesApi, Constant.DELAY_TIME);
    }

    private void requestPlaylistCategories() {
        showFailedView(false, "");
        swipeProgress(true);
        showNoItemView(false);
        new Handler().postDelayed(() -> displayApiResult(sharedPref.getPlaylistCategories()), Constant.DELAY_TIME);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        swipeProgress(false);
        if (callbackCall != null && callbackCall.isExecuted()) {
            callbackCall.cancel();
        }
        lyt_shimmer.stopShimmer();
    }

    private void showFailedView(boolean flag, String message) {
        View lyt_failed = root_view.findViewById(R.id.lyt_failed_category);
        ((TextView) root_view.findViewById(R.id.failed_message)).setText(message);
        if (flag) {
            recyclerView.setVisibility(View.GONE);
            lyt_failed.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            lyt_failed.setVisibility(View.GONE);
        }
        root_view.findViewById(R.id.failed_retry).setOnClickListener(view -> requestAction());
    }

    private void showNoItemView(boolean show) {
        View lyt_no_item = root_view.findViewById(R.id.lyt_no_item_category);
        ((TextView) root_view.findViewById(R.id.no_item_message)).setText(R.string.no_category_found);
        if (show) {
            recyclerView.setVisibility(View.GONE);
            lyt_no_item.setVisibility(View.VISIBLE);
        } else {
            recyclerView.setVisibility(View.VISIBLE);
            lyt_no_item.setVisibility(View.GONE);
        }
    }

    private void swipeProgress(final boolean show) {
        if (!show) {
            swipeRefreshLayout.setRefreshing(show);
            lyt_shimmer.setVisibility(View.GONE);
            lyt_shimmer.stopShimmer();
            return;
        }
        swipeRefreshLayout.post(() -> {
            swipeRefreshLayout.setRefreshing(show);
            lyt_shimmer.setVisibility(View.VISIBLE);
            lyt_shimmer.startShimmer();
        });
    }

    private void initShimmerLayout() {
        View lyt_shimmer_category_list = root_view.findViewById(R.id.lyt_shimmer_category_list);
        View lyt_shimmer_category_grid2 = root_view.findViewById(R.id.lyt_shimmer_category_grid2);
        View lyt_shimmer_category_grid3 = root_view.findViewById(R.id.lyt_shimmer_category_grid3);
        if (sharedPref.getCategoryViewType() == CATEGORY_LIST_DEFAULT) {
            lyt_shimmer_category_list.setVisibility(View.VISIBLE);
            lyt_shimmer_category_grid2.setVisibility(View.GONE);
            lyt_shimmer_category_grid3.setVisibility(View.GONE);
        } else if (sharedPref.getCategoryViewType() == CATEGORY_GRID_2_COLUMN) {
            lyt_shimmer_category_list.setVisibility(View.GONE);
            lyt_shimmer_category_grid2.setVisibility(View.VISIBLE);
            lyt_shimmer_category_grid3.setVisibility(View.GONE);
        } else if (sharedPref.getCategoryViewType() == CATEGORY_GRID_3_COLUMN) {
            lyt_shimmer_category_list.setVisibility(View.GONE);
            lyt_shimmer_category_grid2.setVisibility(View.GONE);
            lyt_shimmer_category_grid3.setVisibility(View.VISIBLE);
        }
    }

}
