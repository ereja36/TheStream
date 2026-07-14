package com.shif.shqip.android.utils;

import android.text.TextUtils;

public final class CategoryDisplayHelper {

    private static final String CATEGORY_SEPARATOR = " • ";

    private CategoryDisplayHelper() {
    }

    public static String displayName(String categoryName) {
        if (TextUtils.isEmpty(categoryName)) {
            return "TV";
        }
        String trimmed = categoryName.trim();
        int separator = trimmed.lastIndexOf(CATEGORY_SEPARATOR);
        if (separator > 0 && separator < trimmed.length() - CATEGORY_SEPARATOR.length()) {
            return trimmed.substring(separator + CATEGORY_SEPARATOR.length()).trim();
        }
        return trimmed;
    }
}
