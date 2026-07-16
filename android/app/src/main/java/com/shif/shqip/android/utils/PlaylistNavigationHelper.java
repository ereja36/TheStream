package com.shif.shqip.android.utils;

import android.content.Context;
import android.content.Intent;

import com.shif.shqip.android.activities.ActivityPlaylistBrowser;
import com.shif.shqip.android.models.Category;

public final class PlaylistNavigationHelper {

    public static final String ALL_CHANNELS_CATEGORY = "__ALL_CHANNELS__";

    private PlaylistNavigationHelper() {
    }

    public static Intent createAllChannelsIntent(Context context) {
        Category category = new Category();
        category.cid = 0;
        category.category_name = ALL_CHANNELS_CATEGORY;
        category.category_image = "";
        Intent intent = new Intent(context, ActivityPlaylistBrowser.class);
        intent.putExtra(Constant.EXTRA_OBJC, category);
        return intent;
    }

    public static Intent createCategoryIntent(Context context, Category category) {
        Intent intent = new Intent(context, ActivityPlaylistBrowser.class);
        intent.putExtra(Constant.EXTRA_OBJC, category);
        return intent;
    }

    public static boolean isAllChannelsCategory(Category category) {
        return category != null && ALL_CHANNELS_CATEGORY.equals(category.category_name);
    }
}
