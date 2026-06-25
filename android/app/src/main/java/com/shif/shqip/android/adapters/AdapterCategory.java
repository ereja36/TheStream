package com.shif.shqip.android.adapters;

import static com.shif.shqip.android.utils.Constant.CHANNEL_GRID_2_COLUMN;
import static com.shif.shqip.android.utils.Constant.CHANNEL_GRID_3_COLUMN;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.shif.shqip.android.R;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.models.Category;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.ArrayList;
import java.util.List;

public class AdapterCategory extends RecyclerView.Adapter<AdapterCategory.ViewHolder> {

    private List<Category> items;
    Context context;
    private OnItemClickListener mOnItemClickListener;
    SharedPref sharedPref;

    public interface OnItemClickListener {
        void onItemClick(View view, Category obj, int position);
    }

    public void setOnItemClickListener(final OnItemClickListener mItemClickListener) {
        this.mOnItemClickListener = mItemClickListener;
    }

    // Provide a suitable constructor (depends on the kind of dataset)
    public AdapterCategory(Context context, List<Category> items) {
        this.items = items;
        this.context = context;
        this.sharedPref = new SharedPref(context);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        // each data item is just a string in this case
        public TextView category_name;
        public ImageView category_image;
        public LinearLayout lyt_parent;

        public ViewHolder(View v) {
            super(v);
            category_name = v.findViewById(R.id.category_name);
            category_image = v.findViewById(R.id.category_image);
            lyt_parent = v.findViewById(R.id.lyt_parent);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (sharedPref.getCategoryViewType() == CHANNEL_GRID_2_COLUMN || sharedPref.getCategoryViewType() == CHANNEL_GRID_3_COLUMN) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.lsv_item_category_grid, parent, false);
            return new ViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.lsv_item_category, parent, false);
            return new ViewHolder(view);
        }

    }

    @Override
    public void onBindViewHolder(ViewHolder holder, final int position) {
        final Category c = items.get(position);

        holder.category_name.setText(c.category_name);

        Glide.with(context)
                .load(sharedPref.getBaseUrl() + "/upload/category/" + c.category_image)
                .placeholder(R.drawable.ic_thumbnail)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(holder.category_image);

        holder.lyt_parent.setOnClickListener(view -> {
            if (mOnItemClickListener != null) {
                mOnItemClickListener.onItemClick(view, c, position);
            }
        });
    }

    public void setListData(List<Category> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    public void resetListData() {
        this.items = new ArrayList<>();
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

}