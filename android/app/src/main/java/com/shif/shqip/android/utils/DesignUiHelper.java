package com.shif.shqip.android.utils;

import android.app.Activity;
import android.content.Intent;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.shif.shqip.android.Config;
import com.shif.shqip.android.R;
import com.shif.shqip.android.activities.ActivityPlaylistSetup;
import com.shif.shqip.android.activities.ActivitySearch;
import com.shif.shqip.android.activities.ActivitySettings;
import com.shif.shqip.android.callbacks.CallbackConfig;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.rests.RestAdapter;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public final class DesignUiHelper {

    private static final long CONFIG_REFRESH_INTERVAL_MS = 5L * 60L * 1000L;
    private static long lastConfigRefreshMs = 0L;

    private DesignUiHelper() {
    }

    public static void showAddMenu(AppCompatActivity activity, View anchor) {
        PopupMenu popupMenu = new PopupMenu(activity, anchor);
        popupMenu.getMenu().add(0, 1, 0, activity.getString(R.string.playlist_play_url));
        popupMenu.getMenu().add(0, 2, 1, activity.getString(R.string.playlist_add));
        popupMenu.getMenu().add(0, 3, 2, activity.getString(R.string.playlist_open_file));
        popupMenu.getMenu().add(0, 4, 3, activity.getString(R.string.playlist_manage_title));
        popupMenu.setOnMenuItemClickListener(item -> {
            Intent intent = new Intent(activity, ActivityPlaylistSetup.class);
            switch (item.getItemId()) {
                case 1:
                    intent.putExtra("open_mode", "play_url");
                    activity.startActivity(intent);
                    return true;
                case 2:
                    intent.putExtra("open_mode", "add_url");
                    activity.startActivity(intent);
                    return true;
                case 3:
                    intent.putExtra("open_mode", "open_file");
                    activity.startActivity(intent);
                    return true;
                case 4:
                    showPlaylistSheet(activity);
                    return true;
                default:
                    return false;
            }
        });
        popupMenu.show();
    }

    public static void showPlaylistSheet(AppCompatActivity activity) {
        SharedPref sharedPref = new SharedPref(activity);
        BottomSheetDialog dialog = new BottomSheetDialog(activity);
        View view = activity.getLayoutInflater().inflate(R.layout.bottom_sheet_playlists, null);
        dialog.setContentView(view);

        String playlistUrl = sharedPref.getPlaylistUrl();
        String channels = sharedPref.hasPlaylistChannels()
                ? String.valueOf(sharedPref.getPlaylistChannels().size())
                : "0";

        view.findViewById(R.id.btn_sheet_add).setOnClickListener(v -> {
            dialog.dismiss();
            activity.startActivity(new Intent(activity, ActivityPlaylistSetup.class));
        });
        view.findViewById(R.id.btn_sheet_edit).setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(activity, ActivityPlaylistSetup.class);
            intent.putExtra("open_mode", "add_url");
            activity.startActivity(intent);
        });
        view.findViewById(R.id.btn_sheet_search).setOnClickListener(v -> {
            dialog.dismiss();
            activity.startActivity(new Intent(activity, ActivitySearch.class));
        });
        view.findViewById(R.id.btn_sheet_settings).setOnClickListener(v -> {
            dialog.dismiss();
            activity.startActivity(new Intent(activity, ActivitySettings.class));
        });

        android.widget.TextView txtInfo = view.findViewById(R.id.txt_playlist_info);
        txtInfo.setText(TextUtils.isEmpty(playlistUrl)
                ? activity.getString(R.string.no_post_found)
                : channels + " channels • " + playlistUrl);

        dialog.show();
    }

    public static void bindAppLogo(Activity activity, ImageView target, SharedPref sharedPref) {
        // Home chrome no longer shows a floating brand logo — splash owns branding.
        if (target != null) {
            target.setVisibility(View.GONE);
        }
    }

    public static void refreshRemoteConfig(AppCompatActivity activity, SharedPref sharedPref, Runnable onSameDesign) {
        long now = System.currentTimeMillis();
        if (now - lastConfigRefreshMs < CONFIG_REFRESH_INTERVAL_MS) {
            if (onSameDesign != null) {
                onSameDesign.run();
            }
            return;
        }
        lastConfigRefreshMs = now;

        if (TextUtils.isEmpty(sharedPref.getBaseUrl())) {
            if (onSameDesign != null) {
                onSameDesign.run();
            }
            return;
        }
        PlaylistGuardSession.refreshAsync(sharedPref);
        String deviceId = DeviceApiHelper.getLocalDeviceId(activity);
        RestAdapter.createAPI(sharedPref.getBaseUrl())
                .getConfig(Tools.getApplicationId(), Config.REST_API_KEY, deviceId)
                .enqueue(new Callback<CallbackConfig>() {
                    @Override
                    public void onResponse(@NonNull Call<CallbackConfig> call, @NonNull Response<CallbackConfig> response) {
                        CallbackConfig resp = response.body();
                        if (resp == null || resp.app == null || !"ok".equals(resp.status)) {
                            if (onSameDesign != null) {
                                onSameDesign.run();
                            }
                            return;
                        }
                        String remoteDesign = normalizeDesignId(resp.app.design_id);
                        String localDesign = sharedPref.getDesignId();
                        if (!TextUtils.isEmpty(resp.app.app_logo_url)) {
                            sharedPref.saveAppLogoUrl(resp.app.app_logo_url);
                        }
                        if (!remoteDesign.equals(localDesign)) {
                            sharedPref.saveDesignId(remoteDesign);
                        }
                        if (onSameDesign != null) {
                            onSameDesign.run();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<CallbackConfig> call, @NonNull Throwable t) {
                        if (onSameDesign != null) {
                            onSameDesign.run();
                        }
                    }
                });
    }

    private static String normalizeDesignId(String designId) {
        String normalized = designId == null ? "v1" : designId.trim().toLowerCase();
        if ("v2".equals(normalized) || "v3".equals(normalized) || "v4".equals(normalized)) {
            return normalized;
        }
        return "v1";
    }
}
