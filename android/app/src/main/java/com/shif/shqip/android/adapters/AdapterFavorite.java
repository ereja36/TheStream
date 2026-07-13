package com.shif.shqip.android.adapters;

import static com.shif.shqip.android.utils.Constant.CHANNEL_GRID_2_COLUMN;
import static com.shif.shqip.android.utils.Constant.CHANNEL_GRID_3_COLUMN;

import android.content.Context;
import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.shif.shqip.android.Config;
import com.shif.shqip.android.R;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.models.Channel;
import com.shif.shqip.android.utils.Constant;
import com.shif.shqip.android.utils.ChannelLogoHelper;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;

import java.util.List;

public class AdapterFavorite extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private Context context;
    private final int VIEW_ITEM = 1;
    private final int VIEW_PROG = 0;
    private List<Channel> items;
    private boolean loading;
    private OnLoadMoreListener onLoadMoreListener;
    private OnItemClickListener mOnItemClickListener;
    private OnItemOverflowClickListener mOnItemOverflowClickListener;
    SharedPref sharedPref;

    public interface OnItemClickListener {
        void onItemClick(View view, Channel obj, int position);
    }

    public interface OnItemOverflowClickListener {
        void onItemOverflowClick(View view, Channel obj, int position);
    }

    public void setOnItemClickListener(final OnItemClickListener mItemClickListener) {
        this.mOnItemClickListener = mItemClickListener;
    }

    public void setOnItemOverflowClickListener(final OnItemOverflowClickListener mItemOverflowClickListener) {
        this.mOnItemOverflowClickListener = mItemOverflowClickListener;
    }

    public AdapterFavorite(Context context, RecyclerView view, List<Channel> items) {
        this.items = items;
        this.context = context;
        this.sharedPref = new SharedPref(context);
        lastItemViewDetector(view);
    }

    public class OriginalViewHolder extends RecyclerView.ViewHolder {

        public TextView channel_name;
        public TextView channel_category;
        public ImageView channel_image;
        public LinearLayout lyt_parent;
        public ImageView overflow;

        private OriginalViewHolder(View v) {
            super(v);
            channel_name = v.findViewById(R.id.channel_name);
            channel_category = v.findViewById(R.id.channel_category);
            channel_image = v.findViewById(R.id.channel_image);
            lyt_parent = v.findViewById(R.id.lyt_parent);
            overflow = v.findViewById(R.id.overflow);
        }
    }

    public static class ProgressViewHolder extends RecyclerView.ViewHolder {
        public ProgressBar progressBar;
        ProgressViewHolder(View v) {
            super(v);
            progressBar = v.findViewById(R.id.loadMore);
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        switch (viewType) {
            case VIEW_ITEM:
                SharedPref sharedPref = new SharedPref(context);
                if (sharedPref.getChannelViewType() == CHANNEL_GRID_2_COLUMN || sharedPref.getChannelViewType() == CHANNEL_GRID_3_COLUMN) {
                    View menuItemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.lsv_item_post_grid, parent, false);
                    return new OriginalViewHolder(menuItemView);
                } else {
                    View menuItemView = LayoutInflater.from(parent.getContext()).inflate(R.layout.lsv_item_post, parent, false);
                    return new OriginalViewHolder(menuItemView);
                }
            case VIEW_PROG:
                // fall through
            default:
                View loadMoreView = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_loading, parent, false);
                return new ProgressViewHolder(loadMoreView);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, final int position) {
        int viewType = getItemViewType(position);
        switch (viewType) {
            case VIEW_ITEM:
                final Channel p = items.get(position);
                final OriginalViewHolder vItem = (OriginalViewHolder) holder;

                vItem.channel_name.setText(p.channel_name);

                vItem.channel_category.setText(p.category_name);
                if (Config.ENABLE_CHANNEL_LIST_CATEGORY_NAME) {
                    vItem.channel_category.setVisibility(View.VISIBLE);
                } else {
                    vItem.channel_category.setVisibility(View.GONE);
                }

                if (p.channel_type != null && p.channel_type.equals("YOUTUBE")) {
                    if (!p.channel_image.equals("")) {
                        Glide.with(context)
                                .load(resolveAnyChannelImage(p.channel_image))
                                .apply(new RequestOptions()
                                        .override(Constant.list_image_width, Constant.list_image_height)
                                        .fitCenter())
                                .placeholder(R.drawable.ic_thumbnail)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .into(vItem.channel_image);
                    } else {
                        Glide.with(context)
                                .load(Constant.YOUTUBE_IMG_FRONT + p.video_id + Constant.YOUTUBE_IMG_BACK)
                                .apply(new RequestOptions()
                                        .override(Constant.list_image_width, Constant.list_image_height)
                                        .fitCenter())
                                .placeholder(R.drawable.ic_thumbnail)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .into(vItem.channel_image);
                    }
                } else {
                    Glide.with(context)
                            .load(resolveAnyChannelImage(p.channel_image))
                            .apply(new RequestOptions()
                                    .override(Constant.list_image_width, Constant.list_image_height)
                                    .fitCenter())
                            .placeholder(R.drawable.ic_thumbnail)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .into(vItem.channel_image);
                }

                if (sharedPref.getIsDarkTheme()) {
                    vItem.overflow.setColorFilter(ContextCompat.getColor(context, R.color.color_dark_icon), PorterDuff.Mode.SRC_IN);
                } else {
                    vItem.overflow.setColorFilter(ContextCompat.getColor(context, R.color.color_light_icon), PorterDuff.Mode.SRC_IN);
                }

                vItem.lyt_parent.setOnClickListener(view -> {
                    if (mOnItemClickListener != null) {
                        mOnItemClickListener.onItemClick(view, p, position);
                    }
                });

                vItem.overflow.setOnClickListener(view -> {
                    if (mOnItemOverflowClickListener != null) {
                        mOnItemOverflowClickListener.onItemOverflowClick(view, p, position);
                    }
                });

                break;
            case VIEW_PROG:
                //fall through
            default:
                ((ProgressViewHolder) holder).progressBar.setIndeterminate(true);
        }

    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public int getItemViewType(int position) {

        if (items.get(position) != null) {
            return VIEW_ITEM;
        } else {
            return VIEW_PROG;
        }

    }

    public void insertData(List<Channel> items) {
        setLoaded();
        int positionStart = getItemCount();
        int itemCount = items.size();
        this.items.addAll(items);
        notifyItemRangeInserted(positionStart, itemCount);
    }

    public void setLoaded() {
        loading = false;
        for (int i = 0; i < getItemCount(); i++) {
            if (items.get(i) == null) {
                items.remove(i);
                notifyItemRemoved(i);
            }
        }
    }

    public void setLoading() {
        if (getItemCount() != 0) {
            this.items.add(null);
            notifyItemInserted(getItemCount() - 1);
            loading = true;
        }
    }

    public void resetListData() {
        this.items.clear();
        notifyDataSetChanged();
    }

    public void setOnLoadMoreListener(OnLoadMoreListener onLoadMoreListener) {
        this.onLoadMoreListener = onLoadMoreListener;
    }

    private void lastItemViewDetector(RecyclerView recyclerView) {
        if (recyclerView.getLayoutManager() instanceof LinearLayoutManager) {
            final LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
            recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    int lastPos = layoutManager.findLastVisibleItemPosition();
                    if (!loading && lastPos == getItemCount() - 1 && onLoadMoreListener != null) {
                        if (onLoadMoreListener != null) {
                            int current_page = getItemCount() / Config.LOAD_MORE;
                            onLoadMoreListener.onLoadMore(current_page);
                        }
                        loading = true;
                    }
                }
            });
        }
    }

    public interface OnLoadMoreListener {
        void onLoadMore(int current_page);
    }

    private String resolveAnyChannelImage(String image) {
        return ChannelLogoHelper.resolve(image, sharedPref.getBaseUrl());
    }

}