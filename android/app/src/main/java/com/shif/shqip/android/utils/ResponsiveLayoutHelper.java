package com.shif.shqip.android.utils;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * View-system equivalents of Compose responsive APIs:
 * WindowInsets.safeDrawing, fillMaxWidth, weight, BoxWithConstraints / window size classes.
 */
public final class ResponsiveLayoutHelper {

    public enum WidthSizeClass {
        COMPACT,   // phones portrait (under 600dp)
        MEDIUM,    // large phones / small tablets (600–839dp)
        EXPANDED   // tablets / landscape (&gt;= 840dp)
    }

    private ResponsiveLayoutHelper() {
    }

    /** Approx. Compose WindowInsets.safeDrawing */
    public static int safeDrawingTypeMask() {
        return WindowInsetsCompat.Type.systemBars()
                | WindowInsetsCompat.Type.displayCutout();
    }

    public static float widthDp(@NonNull Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return metrics.widthPixels / metrics.density;
    }

    public static float heightDp(@NonNull Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        return metrics.heightPixels / metrics.density;
    }

    public static boolean isLandscape(@NonNull Context context) {
        return context.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
    }

    public static WidthSizeClass widthSizeClass(@NonNull Context context) {
        float w = widthDp(context);
        if (w >= 840f) {
            return WidthSizeClass.EXPANDED;
        }
        if (w >= 600f) {
            return WidthSizeClass.MEDIUM;
        }
        return WidthSizeClass.COMPACT;
    }

    /** Adaptive grid span for channel / category lists. */
    public static int gridSpanCount(@NonNull Context context, int compactSpan) {
        WidthSizeClass size = widthSizeClass(context);
        boolean land = isLandscape(context);
        switch (size) {
            case EXPANDED:
                return land ? Math.max(compactSpan + 3, 5) : Math.max(compactSpan + 2, 4);
            case MEDIUM:
                return land ? Math.max(compactSpan + 2, 4) : Math.max(compactSpan + 1, 3);
            case COMPACT:
            default:
                return land ? Math.max(compactSpan + 1, 2) : Math.max(compactSpan, 1);
        }
    }

    /**
     * Prefer user view-type for phones; scale up on tablets/landscape.
     * List mode (preferredSpan == 1) stays single column.
     */
    public static int spanForPreferredColumns(@NonNull Context context, int preferredSpan) {
        if (preferredSpan <= 1) {
            return 1;
        }
        return gridSpanCount(context, preferredSpan);
    }

    /** Enable edge-to-edge and pad a root like Compose Scaffold + safeDrawing. */
    public static void applySafeDrawing(@NonNull Activity activity, @NonNull View root) {
        applySafeDrawing(activity, root, true, true, true, true);
    }

    public static void applySafeDrawing(@NonNull Activity activity,
                                        @NonNull View root,
                                        boolean padLeft,
                                        boolean padTop,
                                        boolean padRight,
                                        boolean padBottom) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        final int baseL = root.getPaddingLeft();
        final int baseT = root.getPaddingTop();
        final int baseR = root.getPaddingRight();
        final int baseB = root.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(safeDrawingTypeMask());
            v.setPadding(
                    padLeft ? baseL + bars.left : baseL,
                    padTop ? baseT + bars.top : baseT,
                    padRight ? baseR + bars.right : baseR,
                    padBottom ? baseB + bars.bottom : baseB);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    /**
     * Pad chrome bars with status / nav insets (statusBarsPadding / navigationBarsPadding).
     * Content itself can stay edge-to-edge under translucent system bars.
     */
    public static void applyChromeInsets(@NonNull Activity activity,
                                         @NonNull View root,
                                         View topBar,
                                         View bottomBar) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(safeDrawingTypeMask());
            if (topBar != null) {
                setMarginTop(topBar, bars.top);
            }
            if (bottomBar != null) {
                setMarginBottom(bottomBar, bars.bottom);
            }
            v.setPadding(bars.left, 0, bars.right, 0);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private static void setMarginTop(View view, int top) {
        ViewGroup.LayoutParams raw = view.getLayoutParams();
        if (raw instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) raw;
            if (lp.topMargin != top) {
                lp.topMargin = top;
                view.setLayoutParams(lp);
            }
        }
    }

    private static void setMarginBottom(View view, int bottom) {
        ViewGroup.LayoutParams raw = view.getLayoutParams();
        if (raw instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) raw;
            if (lp.bottomMargin != bottom) {
                lp.bottomMargin = bottom;
                view.setLayoutParams(lp);
            }
        }
    }
}
