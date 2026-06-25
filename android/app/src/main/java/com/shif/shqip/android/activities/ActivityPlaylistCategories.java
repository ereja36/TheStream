package com.shif.shqip.android.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.shif.shqip.android.R;
import com.shif.shqip.android.adapters.AdapterCategory;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.models.Category;
import com.shif.shqip.android.utils.Tools;

import java.util.ArrayList;
import java.util.List;

public class ActivityPlaylistCategories extends AppCompatActivity {

    private SharedPref sharedPref;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Tools.getTheme(this);
        setContentView(R.layout.activity_playlist_categories);
        Tools.setNavigation(this);
        sharedPref = new SharedPref(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        Tools.setupToolbar(this, toolbar, getString(R.string.title_nav_category), true);

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        TextView emptyMessage = findViewById(R.id.empty_message);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        List<Category> categories = sharedPref.getPlaylistCategories();
        if (categories == null || categories.isEmpty()) {
            emptyMessage.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            AdapterCategory adapter = new AdapterCategory(this, new ArrayList<>(categories));
            adapter.setOnItemClickListener((view, category, position) -> {
                Intent intent = new Intent(getApplicationContext(), ActivityCategoryDetail.class);
                intent.putExtra("category", category);
                startActivity(intent);
            });
            recyclerView.setAdapter(adapter);
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
