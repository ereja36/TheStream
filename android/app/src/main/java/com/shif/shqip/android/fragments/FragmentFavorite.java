package com.shif.shqip.android.fragments;

import static com.shif.shqip.android.utils.Constant.CHANNEL_GRID_2_COLUMN;
import static com.shif.shqip.android.utils.Constant.CHANNEL_GRID_3_COLUMN;
import static com.shif.shqip.android.utils.Constant.CHANNEL_LIST_DEFAULT;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.shif.shqip.android.R;
import com.shif.shqip.android.activities.ActivityChannelDetail;
import com.shif.shqip.android.adapters.AdapterFavorite;
import com.shif.shqip.android.databases.dao.AppDatabase;
import com.shif.shqip.android.databases.dao.ChannelEntity;
import com.shif.shqip.android.databases.dao.DAO;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.models.Channel;
import com.shif.shqip.android.utils.AdPlacementHelper;
import com.shif.shqip.android.utils.Constant;
import com.shif.shqip.android.utils.Tools;

import java.util.ArrayList;
import java.util.List;

public class FragmentFavorite extends Fragment {

    List<Channel> channels = new ArrayList<>();
    View root_view;
    LinearLayout parent_view;
    AdapterFavorite adapterFavorite;
    boolean flag_read_later;
    private DAO dao;
    RecyclerView recyclerView;
    View lyt_no_favorite;
    private CharSequence charSequence = null;
    SharedPref sharedPref;
    private Activity activity;
    Tools tools;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        activity = (Activity) context;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        root_view = inflater.inflate(R.layout.fragment_favorite, container, false);
        tools = new Tools(activity);
        sharedPref = new SharedPref(activity);
        dao = AppDatabase.getDatabase(getContext()).get();

        parent_view = root_view.findViewById(R.id.parent_view);
        lyt_no_favorite = root_view.findViewById(R.id.lyt_no_favorite);
        recyclerView = root_view.findViewById(R.id.recyclerView);

        if (sharedPref.getChannelViewType() == CHANNEL_LIST_DEFAULT) {
            recyclerView.setLayoutManager(new GridLayoutManager(activity, 1));
        } else if (sharedPref.getChannelViewType() == CHANNEL_GRID_2_COLUMN) {
            recyclerView.setLayoutManager(new GridLayoutManager(activity, 2));
        } else if (sharedPref.getChannelViewType() == CHANNEL_GRID_3_COLUMN) {
            recyclerView.setLayoutManager(new GridLayoutManager(activity, 3));
        }

        int padding = getResources().getDimensionPixelOffset(R.dimen.grid_space_channel);
        if (sharedPref.getChannelViewType() == CHANNEL_LIST_DEFAULT) {
            recyclerView.setPadding(0, padding, 0, padding);
        } else {
            recyclerView.setPadding(padding, padding, padding, padding);
        }

        adapterFavorite = new AdapterFavorite(activity, recyclerView, channels);
        recyclerView.setAdapter(adapterFavorite);
        onChannelClickListener();

        if (channels.size() == 0) {
            lyt_no_favorite.setVisibility(View.VISIBLE);
        } else {
            lyt_no_favorite.setVisibility(View.INVISIBLE);
        }

        return root_view;
    }

    @Override
    public void onResume() {
        super.onResume();
        displayData(dao.getAllChannel());
    }

    private void displayData(final List<ChannelEntity> radios) {
        ArrayList<Channel> items = new ArrayList<>();
        for (ChannelEntity radio : radios) items.add(radio.original());
        showNoItemView(false);
        adapterFavorite.resetListData();
        adapterFavorite.insertData(items);
        if (radios.size() == 0) {
            showNoItemView(true);
        }
    }

    private void showNoItemView(boolean show) {
        View lyt_no_item = root_view.findViewById(R.id.lyt_no_favorite);
        ((TextView) root_view.findViewById(R.id.no_item_message)).setText(R.string.no_favorite_found);
        if (show) {
            lyt_no_item.setVisibility(View.VISIBLE);
        } else {
            lyt_no_item.setVisibility(View.GONE);
        }
    }

    public void onChannelClickListener() {
        adapterFavorite.setOnItemClickListener((v, obj, position) -> {
            Intent intent = new Intent(activity, ActivityChannelDetail.class);
            intent.putExtra(Constant.EXTRA_OBJC, obj);
            startActivity(intent);
            AdPlacementHelper.showInterstitialBeforeAction(activity);
            AdPlacementHelper.pauseHomeBanner(activity);
        });

        adapterFavorite.setOnItemOverflowClickListener((view, obj, position) -> {
            tools.showBottomSheetDialog(activity, parent_view, obj, true, false, () -> {
                displayData(dao.getAllChannel());
            });
        });

    }

}
