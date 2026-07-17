package com.shif.shqip.android.adapters;

import static com.shif.shqip.android.Config.LOAD_MORE;
import static com.solodroidx.ads.util.Constant.ADMOB;
import static com.solodroidx.ads.util.Constant.APPLOVIN;
import static com.solodroidx.ads.util.Constant.APPLOVIN_DISCOVERY;
import static com.solodroidx.ads.util.Constant.APPLOVIN_MAX;
import static com.solodroidx.ads.util.Constant.FACEBOOK;
import static com.solodroidx.ads.util.Constant.FAN;
import static com.solodroidx.ads.util.Constant.GOOGLE_AD_MANAGER;
import static com.solodroidx.ads.util.Constant.STARTAPP;
import static com.solodroidx.ads.util.Constant.WORTISE;

import android.app.Activity;
import android.content.Context;
import android.graphics.PorterDuff;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.shif.shqip.android.Config;
import com.shif.shqip.android.R;
import com.shif.shqip.android.databases.prefs.AdsPref;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.models.Channel;
import com.shif.shqip.android.utils.AdPlacementHelper;
import com.shif.shqip.android.utils.AdsManager;
import com.shif.shqip.android.utils.CategoryDisplayHelper;
import com.shif.shqip.android.utils.ChannelLogoHelper;
import com.shif.shqip.android.utils.Constant;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.solodroidx.ads.nativead.NativeAdViewHolder;

import java.util.ArrayList;
import java.util.List;

public class AdapterChannel extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final int VIEW_PROG = 0;
    private final int VIEW_ITEM = 1;
    private final int VIEW_AD = 2;
    private final List<Channel> items;
    private boolean loading;
    private OnLoadMoreListener onLoadMoreListener;
    Context context;
    private OnItemClickListener mOnItemClickListener;
    private OnItemOverflowClickListener mOnItemOverflowClickListener;
    boolean scrolling = false;
    private static final Object NATIVE_AD_BOUND_TAG = new Object();
    SharedPref sharedPref;
    AdsPref adsPref;
    AdsManager adsManager;

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

    public AdapterChannel(Context context, RecyclerView view, List<Channel> items) {
        this.items = items;
        this.context = context;
        this.sharedPref = new SharedPref(context);
        this.adsPref = new AdsPref(context);
        this.adsManager = new AdsManager((Activity) context);
        lastItemViewDetector(view);
        view.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING
                        || newState == RecyclerView.SCROLL_STATE_SETTLING) {
                    if (!scrolling) {
                        scrolling = true;
                        Glide.with(context).pauseRequests();
                    }
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    if (scrolling) {
                        scrolling = false;
                        Glide.with(context).resumeRequests();
                    }
                }
                super.onScrollStateChanged(recyclerView, newState);
            }
        });
    }

    public static class OriginalViewHolder extends RecyclerView.ViewHolder {

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

    @SuppressWarnings("ConstantConditions")
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        RecyclerView.ViewHolder vh;
        if (viewType == VIEW_ITEM) {
            if (sharedPref.getChannelViewType() == Constant.CHANNEL_GRID_2_COLUMN || sharedPref.getChannelViewType() == Constant.CHANNEL_GRID_3_COLUMN) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.lsv_item_post_grid, parent, false);
                vh = new OriginalViewHolder(v);
            } else {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.lsv_item_post, parent, false);
                vh = new OriginalViewHolder(v);
            }
        } else if (viewType == VIEW_AD) {
            vh = adsManager.createNativeAdViewHolder(context, parent);
            applyStaggeredSpan(vh, true);
        } else {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.row_loading, parent, false);
            vh = new ProgressViewHolder(v);
            applyStaggeredSpan(vh, true);
        }
        return vh;
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, final int position) {
        if (holder instanceof OriginalViewHolder) {
            final Channel p = (Channel) items.get(position);
            final OriginalViewHolder vItem = (OriginalViewHolder) holder;

            vItem.channel_name.setText(p.channel_name);

            String categoryLabel = CategoryDisplayHelper.displayName(p.category_name);
            vItem.channel_category.setText(categoryLabel);
            if (Config.ENABLE_CHANNEL_LIST_CATEGORY_NAME && !TextUtils.isEmpty(categoryLabel)) {
                vItem.channel_category.setVisibility(View.VISIBLE);
            } else {
                vItem.channel_category.setVisibility(View.GONE);
            }

            if (p.channel_type != null && p.channel_type.equals("YOUTUBE")) {
                if (!p.channel_image.equals("")) {
                    loadChannelImage(vItem.channel_image, resolveChannelImage(p));
                } else {
                    loadChannelImage(vItem.channel_image,
                            Constant.YOUTUBE_IMG_FRONT + p.video_id + Constant.YOUTUBE_IMG_BACK);
                }
            } else {
                loadChannelImage(vItem.channel_image, resolveChannelImage(p));
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

        } else if (holder instanceof NativeAdViewHolder) {
            try {
                adsManager.bindNativeAdViewHolder(context, (NativeAdViewHolder) holder);
            } catch (Exception e) {
                Log.e("AdapterChannel", "native bind failed", e);
            }
        }

        else {
            ((ProgressViewHolder) holder).progressBar.setIndeterminate(true);
        }

        applyStaggeredSpan(holder, getItemViewType(position) == VIEW_PROG || getItemViewType(position) == VIEW_AD);
    }

    private void applyStaggeredSpan(RecyclerView.ViewHolder holder, boolean fullSpan) {
        if (!(holder.itemView.getLayoutParams() instanceof StaggeredGridLayoutManager.LayoutParams)) {
            StaggeredGridLayoutManager.LayoutParams params = new StaggeredGridLayoutManager.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            holder.itemView.setLayoutParams(params);
        }
        ViewGroup.LayoutParams raw = holder.itemView.getLayoutParams();
        if (raw instanceof StaggeredGridLayoutManager.LayoutParams) {
            ((StaggeredGridLayoutManager.LayoutParams) raw).setFullSpan(fullSpan);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public int getItemViewType(int position) {
        Channel channel = items.get(position);
        if (channel != null) {
            if (channel.channel_name == null || channel.channel_name.equals("")) {
                return VIEW_AD;
            }
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

    public void insertDataWithNativeAd(List<Channel> items) {
        setLoaded();
        int positionStart = getItemCount();
        if (!adsPref.getAdStatus() || !AdPlacementHelper.shouldInsertNativeAds()) {
            this.items.addAll(items);
            notifyItemRangeInserted(positionStart, items.size());
            return;
        }

        List<Channel> enriched = new ArrayList<>();
        int startIndex = AdPlacementHelper.resolveNativeStartIndex(adsPref);
        int interval = AdPlacementHelper.resolveNativeInterval(adsPref);
        int channelCount = 0;
        for (Channel post : items) {
            enriched.add(post);
            channelCount++;
            if (channelCount == startIndex || (channelCount > startIndex && (channelCount - startIndex) % interval == 0)) {
                enriched.add(new Channel());
            }
        }

        int itemCount = enriched.size();
        this.items.addAll(enriched);
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
        if (recyclerView.getLayoutManager() instanceof StaggeredGridLayoutManager) {
            final StaggeredGridLayoutManager layoutManager = (StaggeredGridLayoutManager) recyclerView.getLayoutManager();
            recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    int lastPos = getLastVisibleItem(layoutManager.findLastVisibleItemPositions(null));
                    if (!loading && lastPos == getItemCount() - 1 && onLoadMoreListener != null) {
                        if (Constant.NATIVE_AD_CHANNEL_LIST) {
                            switch (adsPref.getMainAds()) {
                                case ADMOB:
                                case GOOGLE_AD_MANAGER:
                                case FAN:
                                case FACEBOOK:
                                case STARTAPP:
                                case APPLOVIN:
                                case APPLOVIN_MAX:
                                case APPLOVIN_DISCOVERY:
                                case WORTISE: {
                                    int current_page = getItemCount() / (LOAD_MORE + 1); //posts per page plus 1 Ad
                                    onLoadMoreListener.onLoadMore(current_page);
                                    break;
                                }
                                default: {
                                    int current_page = getItemCount() / (LOAD_MORE);
                                    onLoadMoreListener.onLoadMore(current_page);
                                    break;
                                }
                            }
                        } else {
                            int current_page = getItemCount() / (LOAD_MORE);
                            onLoadMoreListener.onLoadMore(current_page);
                        }
                    }
                }
            });
        }
    }

    public interface OnLoadMoreListener {
        void onLoadMore(int current_page);
    }

    private int getLastVisibleItem(int[] into) {
        int last_idx = into[0];
        for (int i : into) {
            if (last_idx < i) last_idx = i;
        }
        return last_idx;
    }

    private void loadChannelImage(ImageView imageView, String imageUrl) {
        if (imageView == null) {
            return;
        }
        if (TextUtils.isEmpty(imageUrl)) {
            Glide.with(context).clear(imageView);
            imageView.setImageResource(R.drawable.ic_thumbnail);
            return;
        }
        Glide.with(context)
                .load(imageUrl)
                .apply(new RequestOptions()
                        .override(Constant.list_image_width, Constant.list_image_height)
                        .fitCenter())
                .placeholder(R.drawable.ic_thumbnail)
                .error(R.drawable.ic_thumbnail)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .dontAnimate()
                .into(imageView);
    }


    private String resolveChannelImage(Channel channel) {
        if (channel == null) {
            return "";
        }
        return ChannelLogoHelper.resolve(channel.channel_image, sharedPref.getBaseUrl(), channel.channel_name);
    }


}