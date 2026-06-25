package com.shif.shqip.android.activities;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.shif.shqip.android.R;
import com.shif.shqip.android.adapters.AdapterPlaylistCategory;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.models.Category;
import com.shif.shqip.android.utils.PlaylistNavigationHelper;
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
        recyclerView.setLayoutManager(new GridLayoutManager(this, 2));
        recyclerView.setHasFixedSize(true);

        List<Category> categories = sharedPref.getPlaylistCategories();
        if (categories == null || categories.isEmpty()) {
            emptyMessage.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            AdapterPlaylistCategory adapter = new AdapterPlaylistCategory(this, new ArrayList<>(categories));
            adapter.setOnItemClickListener((view, category, position) ->
                    startActivity(PlaylistNavigationHelper.createCategoryIntent(this, category)));
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
