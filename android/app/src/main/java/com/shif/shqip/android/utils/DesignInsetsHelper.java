package com.shif.shqip.android.utils;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.shif.shqip.android.databases.prefs.SharedPref;

public final class DesignInsetsHelper {

    private DesignInsetsHelper() {
    }

    public static void apply(Activity activity, int rootId, int topBarId, int bottomBarId) {
        SharedPref sharedPref = new SharedPref(activity);
        boolean lightBars = !sharedPref.getIsDarkTheme();

        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), true);
        Tools.applyDesignSystemBars(activity, lightBars);

        View root = activity.findViewById(rootId);
        if (root == null) {
            return;
        }

        View topBar = topBarId != 0 ? activity.findViewById(topBarId) : null;
        View bottomBar = bottomBarId != 0 ? activity.findViewById(bottomBarId) : null;
        final int extraTopDp = dp(activity, 8);
        final int extraBottomDp = dp(activity, 8);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            if (topBar != null) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) topBar.getLayoutParams();
                lp.topMargin = bars.top + extraTopDp;
                topBar.setLayoutParams(lp);
            }
            if (bottomBar != null) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) bottomBar.getLayoutParams();
                lp.bottomMargin = bars.bottom + extraBottomDp;
                bottomBar.setLayoutParams(lp);
            }
            bringChromeToFront(topBar, bottomBar);
            return insets;
        });

        ViewCompat.requestApplyInsets(root);
        bringChromeToFront(topBar, bottomBar);
    }

    public static void bringChromeToFront(View topBar, View bottomBar) {
        if (topBar != null) {
            topBar.bringToFront();
            topBar.setElevation(dp(topBar, 24));
        }
        if (bottomBar != null) {
            bottomBar.bringToFront();
            bottomBar.setElevation(dp(bottomBar, 24));
        }
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
