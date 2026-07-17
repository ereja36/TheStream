package com.shif.shqip.android.activities;

import com.shif.shqip.android.R;

/**
 * Design V4 — Triangolo-style liquid glass home (same behavior as V2).
 */
public class ActivityMainV4 extends ActivityMainV2 {

    @Override
    protected int getHomeLayoutResId() {
        return R.layout.activity_main_v4;
    }

    @Override
    protected int getCategorySectionLayoutResId() {
        return R.layout.include_v4_category_section;
    }
}
