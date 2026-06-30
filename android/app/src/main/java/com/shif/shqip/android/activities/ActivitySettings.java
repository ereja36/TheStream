package com.shif.shqip.android.activities;

import static com.shif.shqip.android.utils.Constant.CATEGORY_GRID_2_COLUMN;
import static com.shif.shqip.android.utils.Constant.CATEGORY_GRID_3_COLUMN;
import static com.shif.shqip.android.utils.Constant.CATEGORY_LIST_DEFAULT;
import static com.shif.shqip.android.utils.Constant.CHANNEL_GRID_2_COLUMN;
import static com.shif.shqip.android.utils.Constant.CHANNEL_GRID_3_COLUMN;
import static com.shif.shqip.android.utils.Constant.CHANNEL_LIST_DEFAULT;
import static com.shif.shqip.android.utils.Constant.PLAYER_MODE_LANDSCAPE;
import static com.shif.shqip.android.utils.Constant.PLAYER_MODE_PORTRAIT;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewStub;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.OnBackPressedDispatcher;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.shif.shqip.android.BuildConfig;
import com.shif.shqip.android.Config;
import com.shif.shqip.android.R;
import com.shif.shqip.android.adapters.AdapterViewType;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.utils.Constant;
import com.shif.shqip.android.utils.Tools;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

public class ActivitySettings extends AppCompatActivity {

    SharedPref sharedPref;
    MaterialSwitch switchTheme;
    MaterialSwitch switchDialogPlayer;
    TextView txt_current_video_list;
    TextView txt_current_category_list;
    TextView txt_current_player_mode;
    RelativeLayout parentView;
    OnBackPressedDispatcher onBackPressedDispatcher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Tools.getTheme(this);
        setContentView(R.layout.activity_settings);
        Tools.setNavigation(this);
        sharedPref = new SharedPref(this);
        initComponent();
        setupToolbar();
        handleOnBackPressed();
    }

    public void handleOnBackPressed() {
        onBackPressedDispatcher = getOnBackPressedDispatcher();
        onBackPressedDispatcher.addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finish();
            }
        });
    }

    public void setupToolbar() {
        final Toolbar toolbar = findViewById(R.id.toolbar);
        Tools.setupToolbar(this, toolbar, getString(R.string.title_nav_settings), true);
    }

    private void initComponent() {

        parentView = findViewById(R.id.root_view);

        ViewStub viewStub = findViewById(R.id.settings_view);
        if (sharedPref.getIsDarkTheme()) {
            viewStub.setLayoutResource(R.layout.include_settings_dark);
        } else {
            viewStub.setLayoutResource(R.layout.include_settings_light);
        }
        viewStub.inflate();

        txt_current_video_list = findViewById(R.id.txt_current_video_list);
        if (sharedPref.getChannelViewType() == CHANNEL_LIST_DEFAULT) {
            txt_current_video_list.setText(getResources().getString(R.string.single_choice_default));
        } else if (sharedPref.getChannelViewType() == CHANNEL_GRID_2_COLUMN) {
            txt_current_video_list.setText(getResources().getString(R.string.single_choice_grid2));
        } else if (sharedPref.getChannelViewType() == CHANNEL_GRID_3_COLUMN) {
            txt_current_video_list.setText(getResources().getString(R.string.single_choice_grid3));
        }

        txt_current_category_list = findViewById(R.id.txt_current_category_list);
        if (sharedPref.getCategoryViewType() == CATEGORY_LIST_DEFAULT) {
            txt_current_category_list.setText(getResources().getString(R.string.single_choice_list));
        } else if (sharedPref.getCategoryViewType() == CATEGORY_GRID_2_COLUMN) {
            txt_current_category_list.setText(getResources().getString(R.string.single_choice_grid_2));
        } else if (sharedPref.getCategoryViewType() == CATEGORY_GRID_3_COLUMN) {
            txt_current_category_list.setText(getResources().getString(R.string.single_choice_grid_3));
        }

        txt_current_player_mode = findViewById(R.id.txt_current_player_mode);
        if (sharedPref.getPlayerOrientation() == PLAYER_MODE_PORTRAIT) {
            txt_current_player_mode.setText(getResources().getString(R.string.player_portrait));
        } else if (sharedPref.getPlayerOrientation() == PLAYER_MODE_LANDSCAPE) {
            txt_current_player_mode.setText(getResources().getString(R.string.player_landscape));
        }

        onThemeChanged();
        onDialogPlayerChanged();
        changeChannelViewType();
        changeCategoryListViewType();
        changePlayerMode();

        findViewById(R.id.btn_manage_playlist).setOnClickListener(view -> startActivity(new Intent(getApplicationContext(), ActivityPlaylistSetup.class)));
        findViewById(R.id.btn_privacy_policy).setOnClickListener(view -> startActivity(new Intent(getApplicationContext(), ActivityPrivacyPolicy.class)));
        findViewById(R.id.btn_share).setOnClickListener(view -> Tools.shareApp(this));
        findViewById(R.id.btn_rate).setOnClickListener(view -> Tools.rateApp(this));
        findViewById(R.id.btn_more).setOnClickListener(view -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(sharedPref.getMoreAppsUrl()))));
        findViewById(R.id.btn_about).setOnClickListener(view -> aboutDialog());
    }

    private void onThemeChanged() {
        switchTheme = findViewById(R.id.switch_theme);
        switchTheme.setChecked(sharedPref.getIsDarkTheme());
        switchTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPref.setIsDarkTheme(isChecked);
            Tools.postDelayed(() -> {
                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            }, 200);
        });

        findViewById(R.id.btn_switch_theme).setOnClickListener(v -> {
            if (switchTheme.isChecked()) {
                sharedPref.setIsDarkTheme(false);
                switchTheme.setChecked(false);
            } else {
                sharedPref.setIsDarkTheme(true);
                switchTheme.setChecked(true);
            }
            Tools.postDelayed(() -> {
                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
            }, 200);
        });
    }

    private void onDialogPlayerChanged() {
        switchDialogPlayer = findViewById(R.id.switch_dialog_player);
        switchDialogPlayer.setChecked(sharedPref.getIsDialogPlayerOption());
        switchDialogPlayer.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPref.setDialogPlayerOption(isChecked);
        });

        findViewById(R.id.btn_switch_dialog_player).setOnClickListener(v -> {
            if (switchDialogPlayer.isChecked()) {
                sharedPref.setDialogPlayerOption(false);
                switchDialogPlayer.setChecked(false);
            } else {
                sharedPref.setDialogPlayerOption(true);
                switchDialogPlayer.setChecked(true);
            }
        });

        if (!Config.ENABLE_PLAY_STREAMING_USING_EXTERNAL_PLAYER) {
            findViewById(R.id.lyt_switch_dialog_player).setVisibility(View.GONE);
        }
    }

    private void changeChannelViewType() {
        findViewById(R.id.btn_channel_view_type).setOnClickListener(view -> {
            AlertDialog alertDialog = new MaterialAlertDialogBuilder(this)
                    .setView(R.layout.dialog_view_type)
                    .setCancelable(false)
                    .setPositiveButton(R.string.dialog_ok, (dialog, which) -> {
                        if (sharedPref.getChannelViewType() != Constant.selectedChannelPosition) {
                            sharedPref.updateChannelViewType(Constant.selectedChannelPosition);
                            Tools.postDelayed(() -> {
                                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                if (Config.SET_CATEGORY_AS_MAIN_MENU) {
                                    intent.putExtra("channel_position", "channel_position");
                                }
                                startActivity(intent);
                            }, 10);
                        }
                        dialog.dismiss();
                    })
                    .setNegativeButton(R.string.dialog_cancel, (dialog, which) -> dialog.dismiss())
                    .show();

            TextView title = alertDialog.findViewById(R.id.dialog_title);
            assert title != null;
            title.setText(getString(R.string.title_settings_channel_view_type));

            String[] list = new String[]{getString(R.string.channel_view_type_list), getString(R.string.channel_view_type_grid2), getString(R.string.channel_view_type_grid3)};
            RecyclerView recyclerView = alertDialog.findViewById(R.id.recycler_view_type);
            assert recyclerView != null;
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            AdapterViewType adapterMenu = new AdapterViewType(this, new ArrayList<>());
            adapterMenu.setListData(Arrays.asList(list));
            recyclerView.setAdapter(adapterMenu);
            recyclerView.postDelayed(() -> Objects.requireNonNull(recyclerView.findViewHolderForAdapterPosition(sharedPref.getChannelViewType())).itemView.performClick(), 0);
            Tools.postDelayed(() -> recyclerView.setVisibility(View.VISIBLE), 250);
        });
    }

    private void changeCategoryListViewType() {
        findViewById(R.id.btn_category_view_type).setOnClickListener(view -> {
            AlertDialog alertDialog = new MaterialAlertDialogBuilder(this)
                    .setView(R.layout.dialog_view_type)
                    .setCancelable(false)
                    .setPositiveButton(R.string.dialog_ok, (dialog, which) -> {
                        if (sharedPref.getCategoryViewType() != Constant.selectedCategoryPosition) {
                            sharedPref.updateCategoryViewType(Constant.selectedCategoryPosition);
                            Tools.postDelayed(() -> {
                                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                if (!Config.SET_CATEGORY_AS_MAIN_MENU) {
                                    intent.putExtra("category_position", "category_position");
                                }
                                startActivity(intent);
                            }, 10);
                        }
                        dialog.dismiss();
                    })
                    .setNegativeButton(R.string.dialog_cancel, (dialog, which) -> dialog.dismiss())
                    .show();

            TextView title = alertDialog.findViewById(R.id.dialog_title);
            assert title != null;
            title.setText(getString(R.string.title_settings_category_view_type));

            String[] list = new String[]{getString(R.string.category_view_type_list), getString(R.string.category_view_type_grid2), getString(R.string.category_view_type_grid3)};
            RecyclerView recyclerView = alertDialog.findViewById(R.id.recycler_view_type);
            assert recyclerView != null;
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            AdapterViewType adapterMenu = new AdapterViewType(this, new ArrayList<>());
            adapterMenu.setListData(Arrays.asList(list));
            recyclerView.setAdapter(adapterMenu);
            recyclerView.postDelayed(() -> Objects.requireNonNull(recyclerView.findViewHolderForAdapterPosition(sharedPref.getCategoryViewType())).itemView.performClick(), 0);
            Tools.postDelayed(() -> recyclerView.setVisibility(View.VISIBLE), 250);

        });
    }

    private void changePlayerMode() {

        findViewById(R.id.btn_switch_player_mode).setOnClickListener(view -> {
            AlertDialog alertDialog = new MaterialAlertDialogBuilder(this)
                    .setView(R.layout.dialog_view_type)
                    .setCancelable(false)
                    .setPositiveButton(R.string.dialog_ok, (dialog, which) -> {
                        if (sharedPref.getPlayerOrientation() != Constant.selectedPlayerModePosition) {
                            sharedPref.updatePlayerOrientation(Constant.selectedPlayerModePosition);
                            if (Constant.selectedPlayerModePosition == 0) {
                                txt_current_player_mode.setText(getResources().getString(R.string.player_portrait));
                                Snackbar.make(parentView, "player mode changed to portrait", Snackbar.LENGTH_SHORT).show();
                            } else if (Constant.selectedPlayerModePosition == 1) {
                                txt_current_player_mode.setText(getResources().getString(R.string.player_landscape));
                                Snackbar.make(parentView, "player mode changed to landscape", Snackbar.LENGTH_SHORT).show();
                            }
                        }
                        dialog.dismiss();
                    })
                    .setNegativeButton(R.string.dialog_cancel, (dialog, which) -> dialog.dismiss())
                    .show();

            TextView title = alertDialog.findViewById(R.id.dialog_title);
            assert title != null;
            title.setText(getString(R.string.title_setting_player));

            String[] list = new String[]{getString(R.string.player_portrait), getString(R.string.player_landscape)};
            RecyclerView recyclerView = alertDialog.findViewById(R.id.recycler_view_type);
            assert recyclerView != null;
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            AdapterViewType adapterMenu = new AdapterViewType(this, new ArrayList<>());
            adapterMenu.setListData(Arrays.asList(list));
            recyclerView.setAdapter(adapterMenu);
            recyclerView.postDelayed(() -> Objects.requireNonNull(recyclerView.findViewHolderForAdapterPosition(sharedPref.getPlayerOrientation())).itemView.performClick(), 0);
            Tools.postDelayed(() -> recyclerView.setVisibility(View.VISIBLE), 250);

        });
    }

    public void aboutDialog() {
        LayoutInflater layoutInflater = LayoutInflater.from(this);
        View view = layoutInflater.inflate(R.layout.custom_dialog_about, null);
        final MaterialAlertDialogBuilder alert = new MaterialAlertDialogBuilder(this);

        TextView txtAppVersion = view.findViewById(R.id.txt_app_version);
        txtAppVersion.setText(getString(R.string.sub_about_app_version) + " " + BuildConfig.VERSION_NAME);

        alert.setView(view);
        alert.setCancelable(false);
        alert.setPositiveButton(R.string.option_ok, (dialog, which) -> dialog.dismiss());
        alert.show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(menuItem);
    }

}