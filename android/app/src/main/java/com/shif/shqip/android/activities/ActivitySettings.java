package com.shif.shqip.android.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewStub;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.OnBackPressedDispatcher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.shif.shqip.android.BuildConfig;
import com.shif.shqip.android.R;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.utils.DesignRouter;
import com.shif.shqip.android.utils.Tools;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

public class ActivitySettings extends AppCompatActivity {

    SharedPref sharedPref;
    MaterialSwitch switchTheme;
    OnBackPressedDispatcher onBackPressedDispatcher;
    private boolean suppressThemeCallback;

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
        ViewStub viewStub = findViewById(R.id.settings_view);
        viewStub.setLayoutResource(R.layout.include_settings_grid);
        viewStub.inflate();

        bindThemeTile();
        bindActionTile(R.id.tile_manage_playlist, R.drawable.ic_setting_list,
                R.string.playlist_manage_title, R.string.playlist_manage_subtitle,
                v -> startActivity(new Intent(getApplicationContext(), ActivityPlaylistSetup.class)));
        bindActionTile(R.id.tile_privacy, R.drawable.ic_setting_privacy,
                R.string.title_setting_privacy, R.string.sub_title_setting_privacy,
                v -> startActivity(new Intent(getApplicationContext(), ActivityPrivacyPolicy.class)));
        bindActionTile(R.id.tile_share, R.drawable.ic_setting_share,
                R.string.title_setting_share, R.string.sub_title_setting_share,
                v -> Tools.shareApp(this));
        bindActionTile(R.id.tile_rate, R.drawable.ic_setting_rate,
                R.string.title_setting_rate, R.string.sub_title_setting_rate,
                v -> Tools.rateApp(this));
        bindActionTile(R.id.tile_about, R.drawable.ic_setting_info,
                R.string.title_setting_about, R.string.sub_title_setting_about,
                v -> aboutDialog());
    }

    private void bindThemeTile() {
        View tile = findViewById(R.id.tile_theme);
        ImageView icon = tile.findViewById(R.id.img_tile_icon);
        TextView title = tile.findViewById(R.id.txt_tile_title);
        TextView subtitle = tile.findViewById(R.id.txt_tile_subtitle);
        switchTheme = tile.findViewById(R.id.switch_tile);

        icon.setImageResource(R.drawable.ic_setting_theme);
        title.setText(R.string.title_setting_theme);
        subtitle.setText(R.string.sub_title_setting_theme);
        switchTheme.setVisibility(View.VISIBLE);
        updateThemeSwitch(sharedPref.getIsDarkTheme());
        switchTheme.setOnCheckedChangeListener(this::onThemeSwitchChanged);
        tile.setOnClickListener(v -> {
            if (switchTheme != null) {
                switchTheme.toggle();
            }
        });
    }

    private void onThemeSwitchChanged(CompoundButton buttonView, boolean isChecked) {
        if (suppressThemeCallback) {
            return;
        }
        applyThemeChange(isChecked);
    }

    private void updateThemeSwitch(boolean isDarkTheme) {
        if (switchTheme == null) {
            return;
        }
        suppressThemeCallback = true;
        switchTheme.setChecked(isDarkTheme);
        suppressThemeCallback = false;
    }

    private void bindActionTile(int tileId, int iconRes, int titleRes, int subtitleRes, View.OnClickListener listener) {
        View tile = findViewById(tileId);
        ImageView icon = tile.findViewById(R.id.img_tile_icon);
        TextView title = tile.findViewById(R.id.txt_tile_title);
        TextView subtitle = tile.findViewById(R.id.txt_tile_subtitle);
        icon.setImageResource(iconRes);
        title.setText(titleRes);
        subtitle.setText(subtitleRes);
        tile.setOnClickListener(listener);
    }

    private void applyThemeChange(boolean isDarkTheme) {
        if (sharedPref.getIsDarkTheme() == isDarkTheme) {
            updateThemeSwitch(isDarkTheme);
            return;
        }
        updateThemeSwitch(isDarkTheme);
        sharedPref.setIsDarkTheme(isDarkTheme);
        Tools.applyNightMode(isDarkTheme);

        Intent intent = DesignRouter.getMainActivityIntent(this);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    public void aboutDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.custom_dialog_about, null);
        TextView txtAppVersion = view.findViewById(R.id.txt_app_version);
        txtAppVersion.setText(getString(R.string.sub_about_app_version) + " " + BuildConfig.VERSION_NAME);

        new MaterialAlertDialogBuilder(this)
                .setView(view)
                .setCancelable(false)
                .setPositiveButton(R.string.option_ok, (dialog, which) -> dialog.dismiss())
                .show();
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
