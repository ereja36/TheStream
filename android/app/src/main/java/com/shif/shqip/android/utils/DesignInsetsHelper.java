package com.shif.shqip.android.utils;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.shif.shqip.android.R;

public final class DesignInsetsHelper {

    private DesignInsetsHelper() {
    }

    public static void apply(Activity activity, int rootId, int topBarId, int bottomBarId) {
        View root = activity.findViewById(rootId);
        if (root == null) {
            return;
        }

        View topBar = topBarId != 0 ? activity.findViewById(topBarId) : null;
        View bottomBar = bottomBarId != 0 ? activity.findViewById(bottomBarId) : null;

        final int extraTopDp = dp(activity, 10);
        final int extraBottomDp = dp(activity, 12);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());

            if (topBar != null) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) topBar.getLayoutParams();
                lp.topMargin = bars.top + extraTopDp;
                topBar.setLayoutParams(lp);
                topBar.bringToFront();
            }

            if (bottomBar != null) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) bottomBar.getLayoutParams();
                lp.bottomMargin = bars.bottom + extraBottomDp;
                bottomBar.setLayoutParams(lp);
                bottomBar.bringToFront();
            }

            return insets;
        });

        ViewCompat.requestApplyInsets(root);
        Tools.darkNavigation(activity);
        WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);
        WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView())
                .setAppearanceLightNavigationBars(false);
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
