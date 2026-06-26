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

        View bottomBar = bottomBarId != 0 ? activity.findViewById(bottomBarId) : null;
        final int extraBottomDp = dp(activity, 8);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            if (bottomBar != null) {
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) bottomBar.getLayoutParams();
                lp.bottomMargin = bars.bottom + extraBottomDp;
                bottomBar.setLayoutParams(lp);
                bottomBar.bringToFront();
            }
            return insets;
        });

        ViewCompat.requestApplyInsets(root);
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
