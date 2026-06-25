package com.shif.shqip.android.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PorterDuff;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.shif.shqip.android.R;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.utils.Constant;

import java.util.List;

public class AdapterViewType extends RecyclerView.Adapter<AdapterViewType.ViewHolder> {

    private List<String> items;
    Context context;
    private OnItemClickListener mOnItemClickListener;
    private int clickedItemPosition = -1;
    SharedPref sharedPref;

    public interface OnItemClickListener {
        void onItemClick(View view, String obj, int position);
    }

    public void setOnItemClickListener(final OnItemClickListener mItemClickListener) {
        this.mOnItemClickListener = mItemClickListener;
    }

    public AdapterViewType(Context context, List<String> items) {
        this.items = items;
        this.context = context;
        this.sharedPref = new SharedPref(context);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        public TextView menuTitle;
        public ImageView menuIcon;
        public LinearLayout lytItem;
        public LinearLayout lytParent;

        public ViewHolder(View v) {
            super(v);
            menuTitle = v.findViewById(R.id.menu_title);
            menuIcon = v.findViewById(R.id.menu_icon);
            lytItem = v.findViewById(R.id.lyt_item);
            lytParent = v.findViewById(R.id.lyt_parent);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_view_type, parent, false);
        return new ViewHolder(view);
    }

    @SuppressLint({"RecyclerView", "NotifyDataSetChanged"})
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, final int position) {
        final String obj = items.get(position);

        holder.menuTitle.setText(obj);

        holder.lytParent.setOnClickListener(view -> {
            new Handler().postDelayed(() -> {
                clickedItemPosition = position;
                Constant.selectedChannelPosition = position;
                Constant.selectedCategoryPosition = position;
                Constant.selectedPlayerModePosition = position;
                notifyDataSetChanged();
            }, 200);
        });

        if (clickedItemPosition == position) {
            holder.menuTitle.setTextColor(ContextCompat.getColor(context, R.color.color_light_primary));
            holder.menuIcon.setImageResource(R.drawable.ic_radio_button_on);
            holder.menuIcon.setColorFilter(ContextCompat.getColor(context, R.color.color_light_primary), PorterDuff.Mode.SRC_IN);
        } else {
            holder.menuIcon.setImageResource(R.drawable.ic_radio_button_off);
            if (sharedPref.getIsDarkTheme()) {
                holder.menuTitle.setTextColor(ContextCompat.getColor(context, R.color.color_dark_text_default));
                holder.menuIcon.setColorFilter(ContextCompat.getColor(context, R.color.color_dark_text_default), PorterDuff.Mode.SRC_IN);
            } else {
                holder.menuTitle.setTextColor(ContextCompat.getColor(context, R.color.color_light_text_default));
                holder.menuIcon.setColorFilter(ContextCompat.getColor(context, R.color.color_light_text_default), PorterDuff.Mode.SRC_IN);
            }
        }

    }

    @SuppressLint("NotifyDataSetChanged")
    public void setListData(List<String> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void resetListData() {
        this.items.clear();
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

}