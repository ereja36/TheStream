package com.shif.shqip.android.utils;

import android.content.Context;
import android.content.Intent;

import com.shif.shqip.android.activities.ActivityMainV2;
import com.shif.shqip.android.activities.ActivityMainV3;
import com.shif.shqip.android.activities.ActivityMainV4;
import com.shif.shqip.android.activities.MainActivity;
import com.shif.shqip.android.databases.prefs.SharedPref;

public final class DesignRouter {

    private DesignRouter() {
    }

    public static String normalizeDesignId(String designId) {
        if (designId == null) {
            return "v1";
        }
        String normalized = designId.trim().toLowerCase();
        if ("v2".equals(normalized) || "v3".equals(normalized) || "v4".equals(normalized)) {
            return normalized;
        }
        return "v1";
    }

    public static Class<?> getMainActivityClass(String designId) {
        String id = normalizeDesignId(designId);
        if ("v2".equals(id)) {
            return ActivityMainV2.class;
        }
        if ("v3".equals(id)) {
            return ActivityMainV3.class;
        }
        if ("v4".equals(id)) {
            return ActivityMainV4.class;
        }
        return MainActivity.class;
    }

    public static Intent getMainActivityIntent(Context context) {
        SharedPref sharedPref = new SharedPref(context);
        Intent intent = new Intent(context, getMainActivityClass(sharedPref.getDesignId()));
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return intent;
    }
}
