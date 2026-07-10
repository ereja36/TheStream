package com.shif.shqip.android.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.shif.shqip.android.R;
import com.shif.shqip.android.models.Category;

import java.util.ArrayList;
import java.util.List;

public class AdapterPlaylistCategory extends RecyclerView.Adapter<AdapterPlaylistCategory.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(View view, Category category, int position);
    }

    private final List<Category> items;
    private final Context context;
    private OnItemClickListener onItemClickListener;

    public AdapterPlaylistCategory(Context context, List<Category> items) {
        this.context = context;
        this.items = items == null ? new ArrayList<>() : items;
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
        holder.categoryImage.setImageResource(R.drawable.ic_category_app_logo);
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
