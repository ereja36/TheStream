package com.shif.shqip.android.adapters;

import android.app.Activity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.shif.shqip.android.R;
import com.shif.shqip.android.databases.dao.AppDatabase;
import com.shif.shqip.android.databases.dao.ChannelEntity;
import com.shif.shqip.android.databases.dao.DAO;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.models.Channel;
import com.shif.shqip.android.utils.ChannelLogoHelper;
import com.shif.shqip.android.utils.Tools;

import java.util.ArrayList;
import java.util.List;

public class AdapterChannelCardV2 extends RecyclerView.Adapter<AdapterChannelCardV2.ViewHolder> {

    public interface OnChannelClickListener {
        void onChannelClick(Channel channel);
    }

    private final Activity activity;
    private final SharedPref sharedPref;
    private final DAO dao;
    private final List<Channel> items = new ArrayList<>();
    private OnChannelClickListener listener;

    public AdapterChannelCardV2(Activity activity) {
        this.activity = activity;
        this.sharedPref = new SharedPref(activity);
        this.dao = AppDatabase.getDatabase(activity).get();
    }

    public void setOnChannelClickListener(OnChannelClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<Channel> channels) {
        items.clear();
        if (channels != null) {
            items.addAll(channels);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = "v4".equals(sharedPref.getDesignId())
                ? R.layout.item_channel_card_v4
                : R.layout.item_channel_card_v2;
        View view = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Channel channel = items.get(position);
        holder.channelLabel.setText(channel.channel_name);
        if (holder.channelNameInline != null) {
            holder.channelNameInline.setVisibility(View.GONE);
            holder.channelNameInline.setText("");
        }

        Glide.with(activity).clear(holder.channelLogo);
        String imageUrl = ChannelLogoHelper.resolve(
                channel.channel_image, sharedPref.getBaseUrl(), channel.channel_name);
        if (!TextUtils.isEmpty(imageUrl)) {
            Glide.with(activity)
                    .load(imageUrl)
                    .apply(new RequestOptions().fitCenter().diskCacheStrategy(DiskCacheStrategy.ALL))
                    .placeholder(R.drawable.ic_thumbnail)
                    .error(R.drawable.ic_thumbnail)
                    .dontAnimate()
                    .into(holder.channelLogo);
        } else {
            holder.channelLogo.setImageResource(R.drawable.ic_thumbnail);
        }

        boolean favorite = dao.getChannel(channel.channel_id) != null;
        holder.btnFavorite.setImageResource(favorite ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onChannelClick(channel);
            }
        });

        holder.btnFavorite.setOnClickListener(v -> {
            if (favorite) {
                dao.deleteChannel(channel.channel_id);
            } else {
                dao.insertChannel(ChannelEntity.entity(channel));
            }
            notifyItemChanged(position);
        });
    }

    private String resolveImageUrl(String image) {
        return ChannelLogoHelper.resolve(image, sharedPref.getBaseUrl(), "");
    }


    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView channelLogo;
        TextView channelNameInline;
        TextView channelLabel;
        ImageButton btnFavorite;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            channelLogo = itemView.findViewById(R.id.channel_logo);
            channelNameInline = itemView.findViewById(R.id.channel_name_inline);
            channelLabel = itemView.findViewById(R.id.channel_label);
            btnFavorite = itemView.findViewById(R.id.btn_favorite);
        }
    }
}
