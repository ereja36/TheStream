package com.shif.shqip.android.utils;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.shif.shqip.android.R;
import com.shif.shqip.android.databases.prefs.SharedPref;

public final class DesignInsetsHelper {

    private DesignInsetsHelper() {
    }

    public static void apply(Activity activity, int rootId, int topBarId, int bottomBarId) {
        apply(activity, rootId, topBarId, bottomBarId, 0, 0);
    }

    public static void apply(Activity activity, int rootId, int topBarId, int bottomBarId,
                             int contentId, int floatingNavId) {
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
        View content = contentId != 0 ? activity.findViewById(contentId) : null;
        View floatingNav = floatingNavId != 0 ? activity.findViewById(floatingNavId) : null;
        final int extraTopDp = dp(activity, 8);
        final int extraBottomDp = dp(activity, 12);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            applyTopMargin(topBar, bars.top + extraTopDp);
            applyBottomMargin(bottomBar, bars.bottom + extraBottomDp);
            applyBottomMargin(floatingNav, bars.bottom + extraBottomDp + dp(activity, 56));
            if (content != null) {
                content.setPadding(
                        content.getPaddingLeft(),
                        bars.top,
                        content.getPaddingRight(),
                        content.getPaddingBottom());
            }
            bringChromeToFront(topBar, bottomBar, floatingNav);
            return insets;
        });

        ViewCompat.requestApplyInsets(root);
        bringChromeToFront(topBar, bottomBar, floatingNav);
    }

    public static void applyLiquidGlass(View view) {
        if (view == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.setBackgroundResource(R.drawable.bg_v2_glass_nav_pill);
        }
    }

    private static void applyTopMargin(View view, int margin) {
        if (view == null) {
            return;
        }
        ViewGroup.LayoutParams raw = view.getLayoutParams();
        if (raw instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) raw;
            lp.topMargin = margin;
            view.setLayoutParams(lp);
        }
    }

    private static void applyBottomMargin(View view, int margin) {
        if (view == null) {
            return;
        }
        ViewGroup.LayoutParams raw = view.getLayoutParams();
        if (raw instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) raw;
            lp.bottomMargin = margin;
            view.setLayoutParams(lp);
        }
    }

    public static void bringChromeToFront(View topBar, View bottomBar) {
        bringChromeToFront(topBar, bottomBar, null);
    }

    public static void bringChromeToFront(View topBar, View bottomBar, View floatingNav) {
        if (topBar != null) {
            topBar.bringToFront();
            topBar.setElevation(dp(topBar, 24));
        }
        if (bottomBar != null) {
            bottomBar.bringToFront();
            bottomBar.setElevation(dp(bottomBar, 16));
        }
        if (floatingNav != null) {
            floatingNav.bringToFront();
            floatingNav.setElevation(dp(floatingNav, 28));
        }
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
