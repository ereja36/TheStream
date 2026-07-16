package com.shif.shqip.android.adapters;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.shif.shqip.android.R;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.models.Category;

import java.util.ArrayList;
import java.util.List;

public class AdapterPlaylistCategory extends RecyclerView.Adapter<AdapterPlaylistCategory.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(View view, Category category, int position);
    }

    private final List<Category> items;
    private final Context context;
    private final SharedPref sharedPref;
    private OnItemClickListener onItemClickListener;

    public AdapterPlaylistCategory(Context context, List<Category> items) {
        this.context = context;
        this.items = items == null ? new ArrayList<>() : items;
        this.sharedPref = new SharedPref(context);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_playlist_category_compact, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Category category = items.get(position);
        holder.categoryName.setText(category.category_name);
        String image = category.category_image;
        if (TextUtils.isEmpty(image)) {
            image = sharedPref.getCategoryLogo(category.category_name);
        }
        if (!TextUtils.isEmpty(image)) {
            String url = image.startsWith("http")
                    ? image
                    : sharedPref.getBaseUrl() + "/upload/category/" + image.replace(" ", "%20");
            Glide.with(context)
                    .load(url)
                    .placeholder(R.drawable.ic_category_app_logo)
                    .error(R.drawable.ic_category_app_logo)
                    .into(holder.categoryImage);
        } else {
            holder.categoryImage.setImageResource(R.drawable.ic_category_app_logo);
        }
        holder.itemView.setOnClickListener(v -> {
            if (onItemClickListener != null) {
                onItemClickListener.onItemClick(v, category, position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView categoryName;
        final ImageView categoryImage;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            categoryName = itemView.findViewById(R.id.category_name);
            categoryImage = itemView.findViewById(R.id.category_image);
        }
    }
}
