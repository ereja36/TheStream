package com.shif.shqip.android.adapters;

import android.app.Activity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.shif.shqip.android.R;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.models.Channel;
import com.shif.shqip.android.utils.Tools;
import com.shif.shqip.android.utils.ChannelLogoHelper;

import java.util.ArrayList;
import java.util.List;

public class AdapterChannelCardV3 extends RecyclerView.Adapter<AdapterChannelCardV3.ViewHolder> {

    public interface OnChannelClickListener {
        void onChannelClick(Channel channel);
    }

    private final Activity activity;
    private final SharedPref sharedPref;
    private final List<Channel> items = new ArrayList<>();
    private OnChannelClickListener listener;

    public AdapterChannelCardV3(Activity activity) {
        this.activity = activity;
        this.sharedPref = new SharedPref(activity);
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
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_channel_card_v3, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Channel channel = items.get(position);
        holder.channelName.setText(channel.channel_name);

        String imageUrl = resolveImageUrl(channel.channel_image);
        if (!TextUtils.isEmpty(imageUrl)) {
            Glide.with(activity)
                    .load(imageUrl)
                    .apply(new RequestOptions().diskCacheStrategy(DiskCacheStrategy.ALL))
                    .into(holder.channelLogo);
        } else {
            holder.channelLogo.setImageResource(R.mipmap.ic_launcher);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onChannelClick(channel);
            }
        });
    }

    private String resolveImageUrl(String image) {
        return ChannelLogoHelper.resolve(image, sharedPref.getBaseUrl());
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView channelLogo;
        TextView channelName;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            channelLogo = itemView.findViewById(R.id.channel_logo);
            channelName = itemView.findViewById(R.id.channel_name);
        }
    }
}
