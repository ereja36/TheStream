package com.shif.shqip.android.activities;

import android.os.Bundle;

import com.shif.shqip.android.R;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.utils.Tools;

/**
 * Design V4 — Triangolo-style liquid glass home.
 * Same ads/playback behavior as V2 (banner strip, native slots, interstitial).
 */
public class ActivityMainV4 extends ActivityMainV2 {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Glass V4 is dark-only; apply before V2 theme/layout inflation.
        SharedPref prefs = new SharedPref(this);
        prefs.setIsDarkTheme(true);
        Tools.applyNightMode(true);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected int getHomeLayoutResId() {
        return R.layout.activity_main_v4;
    }

    @Override
    protected int getCategorySectionLayoutResId() {
        return R.layout.include_v4_category_section;
    }
}
