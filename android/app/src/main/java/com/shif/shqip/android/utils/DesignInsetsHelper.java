package com.shif.shqip.android.utils;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.shif.shqip.android.R;
import com.shif.shqip.android.databases.prefs.SharedPref;

/**
 * Browse UI stays non-fullscreen: status/nav bars remain visible and usable.
 * targetSdk 35+ forces edge-to-edge, so we pad content with system bar insets
 * instead of drawing under the status bar.
 */
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

        Window window = activity.getWindow();
        clearImmersiveFlags(window);
        // targetSdk 35+ ignores decorFits opt-out; pad insets ourselves so browse
        // UI never draws under the status bar (not "full screen").
        WindowCompat.setDecorFitsSystemWindows(window, false);
        Tools.applyDesignSystemBars(activity, lightBars);

        View root = activity.findViewById(rootId);
        if (root == null) {
            return;
        }

        View topBar = topBarId != 0 ? activity.findViewById(topBarId) : null;
        View bottomChromeFound = activity.findViewById(R.id.bottom_chrome);
        if (bottomChromeFound == null && bottomBarId != 0) {
            bottomChromeFound = activity.findViewById(bottomBarId);
        }
        final View bottomChrome = bottomChromeFound;
        final View floatingNav = floatingNavId != 0 ? activity.findViewById(floatingNavId) : null;
        View bannerStrip = activity.findViewById(R.id.banner_strip);
        final View content = contentId != 0 ? activity.findViewById(contentId) : null;
        final int extraTopDp = dp(activity, 8);
        final int extraBottomDp = dp(activity, 8);

        if (bannerStrip != null) {
            // Do not collapse a filled AdMob banner — insets apply can re-run after ads load.
            View host = activity.findViewById(R.id.admob_banner_host);
            boolean bannerFilled = host instanceof ViewGroup
                    && ((ViewGroup) host).getChildCount() > 0
                    && host.getVisibility() == View.VISIBLE;
            if (!bannerFilled) {
                bannerStrip.setVisibility(View.GONE);
            }
            bannerStrip.setBackgroundColor(Color.TRANSPARENT);
        }

        final int baseTopPad = root.getPaddingTop();
        final int baseBottomPad = root.getPaddingBottom();
        final int baseLeftPad = root.getPaddingLeft();
        final int baseRightPad = root.getPaddingRight();
        final View topBarFinal = topBar;
        final Integer topBarBaseTop = marginTopOrNull(topBarFinal);
        final Integer bottomBaseBottom = marginBottomOrNull(bottomChrome != null ? bottomChrome : floatingNav);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // Belt-and-suspenders for API 35+: keep content below status / above nav.
            v.setPadding(baseLeftPad + bars.left, baseTopPad + bars.top,
                    baseRightPad + bars.right, baseBottomPad);
            if (topBarFinal != null && topBarBaseTop != null) {
                applyTopMargin(topBarFinal, topBarBaseTop + extraTopDp);
            }
            View bottomTarget = bottomChrome != null ? bottomChrome : floatingNav;
            if (bottomTarget != null && bottomBaseBottom != null) {
                if (bottomChrome != null) {
                    bottomChrome.setBackgroundColor(Color.TRANSPARENT);
                }
                applyBottomMargin(bottomTarget, bottomBaseBottom + bars.bottom + extraBottomDp);
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(root);

        bringChromeToFront(topBarFinal, bottomChrome, floatingNav);
        if (floatingNav != null) {
            floatingNav.bringToFront();
        }
    }

    /** Clear any leftover immersive / layout-fullscreen flags from earlier sessions. */
    public static void clearImmersiveFlags(Window window) {
        if (window == null) {
            return;
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        View decor = window.getDecorView();
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, decor);
        if (controller != null) {
            controller.show(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_DEFAULT);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
            window.setAttributes(lp);
        }
    }

    public static void applyLiquidGlass(View view) {
        if (view == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            view.setBackgroundResource(R.drawable.bg_v2_glass_nav_pill);
        }
    }

    private static Integer marginTopOrNull(View view) {
        if (view == null) {
            return null;
        }
        ViewGroup.LayoutParams raw = view.getLayoutParams();
        if (raw instanceof ViewGroup.MarginLayoutParams) {
            return ((ViewGroup.MarginLayoutParams) raw).topMargin;
        }
        return 0;
    }

    private static Integer marginBottomOrNull(View view) {
        if (view == null) {
            return null;
        }
        ViewGroup.LayoutParams raw = view.getLayoutParams();
        if (raw instanceof ViewGroup.MarginLayoutParams) {
            return ((ViewGroup.MarginLayoutParams) raw).bottomMargin;
        }
        return 0;
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
            bottomBar.setElevation(dp(bottomBar, 20));
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
