package com.shif.shqip.android.activities;

import static com.shif.shqip.android.Config.PRESS_BACK_TWICE_TO_CLOSE_PLAYER;
import static com.shif.shqip.android.utils.Constant.PLAYER_MODE_LANDSCAPE;

import android.annotation.SuppressLint;
import android.app.UiModeManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.shif.shqip.android.models.Channel;
import com.shif.shqip.android.databases.prefs.AdsPref;
import com.shif.shqip.android.utils.AdPlacementHelper;
import com.shif.shqip.android.utils.AdsManager;

import java.util.ArrayList;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.OnBackPressedDispatcher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.shif.shqip.android.Config;
import com.shif.shqip.android.R;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.models.Resp;
import com.shif.shqip.android.rests.ApiInterface;
import com.shif.shqip.android.rests.RestAdapter;
import com.shif.shqip.android.utils.IptvPlayerHelper;
import com.shif.shqip.android.utils.PlaylistGuardHelper;
import com.shif.shqip.android.utils.StreamPlaybackHelper;
import com.shif.shqip.android.utils.Tools;
import com.bumptech.glide.Glide;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.util.Log;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@SuppressWarnings("deprecation")
public class ActivityPlayerVideo extends AppCompatActivity {

    private static final String TAG = "ActivityStreamPlayer";
    String channel_id = "";
    String video_url;
    String sourceStreamUrl = "";
    String user_agent;
    String epgChannelId = "";
    String epgUrl = "";
    String channelName = "";
    private StyledPlayerView styledPlayerView;
    private ExoPlayer exoPlayer;
    private DefaultDataSource.Factory dataSourceFactory;
    private ProgressBar progressBar;
    boolean fullscreen = false;
    private ImageView fullscreenButton;
    private long exitTime = 0;
    SharedPref sharedPref;
    Boolean contentLoaded = false;
    OnBackPressedDispatcher onBackPressedDispatcher;
    private TextView txtEpgNow;
    private TextView txtEpgNext;
    private LinearLayout layoutEpgInfo;
    private final ExecutorService epgExecutor = Executors.newSingleThreadExecutor();
    private int autoRetryCount = 0;
    private boolean destroyed = false;
    private boolean epgLoadScheduled = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable bufferingSpinnerRunnable;
    private static final long BUFFERING_SPINNER_DELAY_MS = 1_500L;
    private static final long EPG_LOAD_DELAY_MS = 8_000L;
    private GestureDetector gestureDetector;
    private View quickGesturesOverlay;
    private View mxGestureOverlay;
    private TextView mxGestureLabel;
    private TextView mxGestureValue;
    private ProgressBar mxGestureProgress;
    private AudioManager audioManager;
    private float currentBrightness = -1f;
    private Toast channelToast;
    private AdsManager adsManager;
    private AdsPref adsPref;
    private AlertDialog channelListDialog;
    private AlertDialog holdDialog;
    private long reloadGeneration = 0L;
    private int mxGestureMode = 0; // 0 none, 1 brightness, 2 volume
    private float mxGestureStartY;
    private float mxGestureStartValue;
    private final Runnable hideMxGestureRunnable = this::hideMxGestureOverlay;
    private View channelSwitchOverlay;
    private TextView channelSwitchName;
    private ImageView channelSwitchLogo;
    private final Runnable hideChannelSwitchRunnable = this::hideChannelSwitchOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_player_video);
        Tools.setPlayerNavigation(this);
        sharedPref = new SharedPref(this);
        adsPref = new AdsPref(this);
        adsManager = new AdsManager(this);
        if (adsPref.getAdStatus()) {
            adsManager.initializeAd();
            // Interval 1 here: AdsPref counter in maybeShowChannelSwapAd already throttles to every 3.
            adsManager.loadInterstitialAd(true, 1);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.getWindow().setNavigationBarColor(ContextCompat.getColor(getApplicationContext(), R.color.color_black));
            this.getWindow().setStatusBarColor(ContextCompat.getColor(getApplicationContext(), R.color.color_black));
            this.getWindow().getDecorView().setSystemUiVisibility(0);
        }
        applyChannelFromIntent(getIntent(), false);
        if (TextUtils.isEmpty(sourceStreamUrl)) {
            Toast.makeText(this, R.string.msg_failed, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        if (!channel_id.trim().isEmpty()) {
            updateView(channel_id);
        }

        progressBar = findViewById(R.id.progress_bar);
        progressBar.setVisibility(View.VISIBLE);

        if (StreamPlaybackHelper.canPlayDirectly(sharedPref, sourceStreamUrl)) {
            video_url = StreamPlaybackHelper.resolvePlaybackUrl(sharedPref, sourceStreamUrl, "");
            if (TextUtils.isEmpty(video_url)) {
                errorDialog();
                return;
            }
            autoRetryCount = 0;
            initializePlayer();
        } else {
            new Thread(() -> {
                String resolved = StreamPlaybackHelper.resolvePlaybackUrl(sharedPref, sourceStreamUrl, "");
                runOnUiThread(() -> {
                    if (TextUtils.isEmpty(resolved)) {
                        errorDialog();
                        return;
                    }
                    video_url = resolved;
                    autoRetryCount = 0;
                    initializePlayer();
                });
            }, "resolve-playback").start();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyChannelFromIntent(intent, true);
    }

    private void applyChannelFromIntent(Intent intent, boolean switchNow) {
        if (intent == null) {
            return;
        }
        String nextId = intent.getStringExtra("channel_id");
        String nextUrl = intent.getStringExtra("url");
        String nextUa = intent.getStringExtra("user_agent");
        String nextEpgId = intent.getStringExtra("epg_channel_id");
        String nextEpgUrl = intent.getStringExtra("epg_url");
        String nextName = intent.getStringExtra("channel_name");

        channel_id = nextId == null ? "" : nextId;
        user_agent = (nextUa == null || nextUa.trim().isEmpty()) ? "default" : nextUa;
        epgChannelId = nextEpgId == null ? "" : nextEpgId;
        epgUrl = nextEpgUrl == null ? "" : nextEpgUrl;
        channelName = nextName == null ? "" : nextName;

        if (nextUrl == null || nextUrl.trim().isEmpty()) {
            if (!switchNow) {
                sourceStreamUrl = "";
            }
            return;
        }
        sourceStreamUrl = nextUrl.trim();
        video_url = sourceStreamUrl;
        if (switchNow) {
            Channel channel = new Channel();
            channel.channel_id = channel_id;
            channel.channel_url = sourceStreamUrl;
            channel.user_agent = user_agent;
            channel.epg_id = epgChannelId;
            channel.epg_url = epgUrl;
            channel.channel_name = channelName;
            switchToChannel(channel, false);
        }
    }

    private void initializePlayer() {
        if (isFinishing()) {
            return;
        }

        styledPlayerView = findViewById(R.id.exoPlayerView);
        Log.d(TAG, "playback url: " + video_url + " engine=exo");
        playerOrientation();
        initializeExoPlayer();
    }

    private void initializeExoPlayer() {
        releasePlayers();
        dataSourceFactory = IptvPlayerHelper.createDataSourceFactory(
                this, sharedPref, sourceStreamUrl, user_agent);
        exoPlayer = IptvPlayerHelper.createPlayer(this);
        styledPlayerView.setPlayer(exoPlayer);
        styledPlayerView.setUseController(true);
        styledPlayerView.setControllerShowTimeoutMs(3500);
        styledPlayerView.requestFocus();

        startExoPlayback();
        attachPlayerListener();
        setupControls();
        setupPlayerGestures();
        handleOnBackPressed();
    }

    private void scheduleEpgLoad() {
        if (epgLoadScheduled || !sharedPref.isPlayerEpgEnabled()) {
            return;
        }
        epgLoadScheduled = true;
        mainHandler.postDelayed(this::loadEpgInfo, EPG_LOAD_DELAY_MS);
    }

    private void startExoPlayback() {
        MediaSource mediaSource = IptvPlayerHelper.buildMediaSource(video_url, dataSourceFactory);
        exoPlayer.setMediaSource(mediaSource);
        exoPlayer.prepare();
        exoPlayer.setPlayWhenReady(true);
    }

    private void silenceCurrentPlayback() {
        if (exoPlayer == null) {
            return;
        }
        try {
            exoPlayer.setVolume(0f);
            exoPlayer.setPlayWhenReady(false);
            exoPlayer.stop();
            exoPlayer.clearMediaItems();
        } catch (Exception ignored) {
            // Best-effort stop to avoid dual-audio while switching.
        }
    }

    private void reloadLiveStream(boolean showErrorOnFailure) {
        final long generation = ++reloadGeneration;
        silenceCurrentPlayback();
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }
        new Thread(() -> {
            String resolvedUrl = StreamPlaybackHelper.resolvePlaybackUrl(
                    sharedPref, sourceStreamUrl, "");
            final String playbackUrl = TextUtils.isEmpty(resolvedUrl) ? "" : resolvedUrl;
            runOnUiThread(() -> {
                if (destroyed || isFinishing() || generation != reloadGeneration) {
                    return;
                }
                if (TextUtils.isEmpty(playbackUrl) || exoPlayer == null) {
                    if (progressBar != null) {
                        progressBar.setVisibility(View.GONE);
                    }
                    if (showErrorOnFailure) {
                        errorDialog();
                    }
                    return;
                }
                video_url = playbackUrl;
                dataSourceFactory = IptvPlayerHelper.createDataSourceFactory(
                        ActivityPlayerVideo.this, sharedPref, sourceStreamUrl, user_agent);
                MediaSource mediaSource = IptvPlayerHelper.buildMediaSource(video_url, dataSourceFactory);
                try {
                    exoPlayer.setMediaSource(mediaSource);
                    exoPlayer.prepare();
                    exoPlayer.setVolume(1f);
                    exoPlayer.setPlayWhenReady(true);
                } catch (Exception ignored) {
                    if (showErrorOnFailure) {
                        errorDialog();
                    }
                }
            });
        }).start();
    }

    private void toggleEpgPanel() {
        if (layoutEpgInfo == null) {
            return;
        }
        if (layoutEpgInfo.getVisibility() == View.VISIBLE) {
            layoutEpgInfo.setVisibility(View.GONE);
        } else {
            layoutEpgInfo.setVisibility(View.VISIBLE);
        }
    }

    private void releasePlayers() {
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
    }

    private void attachPlayerListener() {
        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onCues(@NonNull List<Cue> cues) {

            }

            @Override
            public void onTimelineChanged(@NonNull Timeline timeline, int reason) {

            }

            @Override
            public void onPlaybackStateChanged(int state) {
                switch (state) {
                    case Player.STATE_READY:
                        hideBufferingSpinner();
                        contentLoaded = true;
                        autoRetryCount = 0;
                        scheduleEpgLoad();
                        break;
                    case Player.STATE_BUFFERING:
                        if (!Config.ENABLE_LOOPING_MODE) {
                            showBufferingSpinnerDelayed();
                        }
                        break;
                    case Player.STATE_ENDED:
                        if (Config.ENABLE_LOOPING_MODE) {
                            exoPlayer.seekTo(0);
                        }
                        break;
                    default:
                        break;
                }
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                Log.d(TAG, "onPlayerError " + error.getMessage() + " code=" + error.errorCode);
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    long recovery = IptvPlayerHelper.liveRecoveryPositionMs(exoPlayer);
                    if (recovery != com.google.android.exoplayer2.C.TIME_UNSET) {
                        exoPlayer.seekTo(recovery);
                    } else {
                        exoPlayer.seekToDefaultPosition();
                    }
                    exoPlayer.prepare();
                    return;
                }
                if (autoRetryCount < StreamPlaybackHelper.AUTO_RETRY_MAX) {
                    autoRetryCount++;
                    retryPlayback(false);
                    return;
                }
                exoPlayer.stop();
                errorDialog();
            }

            @Override
            public void onPlayerErrorChanged(@Nullable PlaybackException error) {
                Log.d(TAG, "onPlayerErrorChanged " + error);
            }

        });
    }

    private void setupControls() {
        layoutEpgInfo = styledPlayerView.findViewById(R.id.layout_epg_info);
        txtEpgNow = styledPlayerView.findViewById(R.id.txt_epg_now);
        txtEpgNext = styledPlayerView.findViewById(R.id.txt_epg_next);

        if (IptvPlayerHelper.isLiveStream(video_url)) {
            View timeline = styledPlayerView.findViewById(R.id.layout_player_timeline);
            if (timeline != null) {
                timeline.setVisibility(View.VISIBLE);
            }
            View pos = styledPlayerView.findViewById(com.google.android.exoplayer2.ui.R.id.exo_position);
            View dur = styledPlayerView.findViewById(com.google.android.exoplayer2.ui.R.id.exo_duration);
            if (pos != null) {
                pos.setVisibility(View.GONE);
            }
            if (dur != null) {
                dur.setVisibility(View.GONE);
            }
        }

        setupPlayerBranding();

        View btnEpg = styledPlayerView.findViewById(R.id.btn_epg);
        if (sharedPref.isPlayerEpgEnabled() && !TextUtils.isEmpty(epgUrl)) {
            if (btnEpg != null) {
                btnEpg.setVisibility(View.VISIBLE);
                btnEpg.setOnClickListener(view -> toggleEpgPanel());
            }
        } else if (btnEpg != null) {
            btnEpg.setVisibility(View.GONE);
        }
    }

    private void setupPlayerBranding() {
        if (!sharedPref.isPlayerBrandEnabled()) {
            return;
        }
        TextView brandText = styledPlayerView.findViewById(R.id.txt_player_brand);
        ImageView brandLogo = styledPlayerView.findViewById(R.id.img_player_brand);
        String logoUrl = sharedPref.getPlayerBrandLogoUrl();
        String text = sharedPref.getPlayerBrandText();
        if (!TextUtils.isEmpty(logoUrl) && brandLogo != null) {
            brandLogo.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(logoUrl)
                    .into(brandLogo);
            if (brandText != null) {
                brandText.setVisibility(View.GONE);
            }
            return;
        }
        if (brandLogo != null) {
            brandLogo.setVisibility(View.GONE);
        }
        if (brandText != null) {
            if (!TextUtils.isEmpty(text)) {
                brandText.setText(text);
                brandText.setVisibility(View.VISIBLE);
            } else {
                brandText.setVisibility(View.GONE);
            }
        }
    }

    private void playerOrientation() {
        fullscreenButton = styledPlayerView.findViewById(R.id.exo_fullscreen_icon);
        if (fullscreenButton != null) {
            fullscreenButton.setOnClickListener(view -> {
                if (fullscreen) {
                    setPortrait();
                } else {
                    setLandscape();
                }
            });
        }

        if (sharedPref.getPlayerOrientation() == PLAYER_MODE_LANDSCAPE) {
            setLandscape();
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private void setPortrait() {
        updateFullscreenIcon(R.drawable.ic_exo_fullscreen_open);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        if (getSupportActionBar() != null) {
            getSupportActionBar().show();
        }
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        resizeVideoSurface(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        fullscreen = false;
    }

    private void setLandscape() {
        updateFullscreenIcon(R.drawable.ic_exo_fullscreen_close);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        resizeVideoSurface(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        fullscreen = true;
    }

    private void updateFullscreenIcon(int drawableRes) {
        if (fullscreenButton != null) {
            fullscreenButton.setImageDrawable(ContextCompat.getDrawable(ActivityPlayerVideo.this, drawableRes));
        }
    }

    private void resizeVideoSurface(int width, int height) {
        if (styledPlayerView != null) {
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) styledPlayerView.getLayoutParams();
            params.width = width;
            params.height = height;
            styledPlayerView.setLayoutParams(params);
        }
    }

    private String getUserAgent() {

        StringBuilder result = new StringBuilder(64);
        result.append("Dalvik/");
        result.append(System.getProperty("java.vm.version"));
        result.append(" (Linux; U; Android ");

        String version = Build.VERSION.RELEASE;
        result.append(version.length() > 0 ? version : "1.0");

        if ("REL".equals(Build.VERSION.CODENAME)) {
            String model = Build.MODEL;
            if (model.length() > 0) {
                result.append("; ");
                result.append(model);
            }
        }

        String id = Build.ID;

        if (id.length() > 0) {
            result.append(" Build/");
            result.append(id);
        }

        result.append(")");
        return result.toString();
    }

    public void handleOnBackPressed() {
        onBackPressedDispatcher = getOnBackPressedDispatcher();
        onBackPressedDispatcher.addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (dismissQuickGesturesTip()) {
                    return;
                }
                closePlayer();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isFinishing()) {
            silenceCurrentPlayback();
        } else if (exoPlayer != null) {
            exoPlayer.setPlayWhenReady(false);
            exoPlayer.getPlaybackState();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (exoPlayer != null) {
            exoPlayer.setVolume(1f);
            exoPlayer.setPlayWhenReady(true);
            exoPlayer.getPlaybackState();
        }
    }

    public void closePlayer() {
        if (PRESS_BACK_TWICE_TO_CLOSE_PLAYER) {
            if ((System.currentTimeMillis() - exitTime) > 2000) {
                Snackbar.make(findViewById(R.id.root_view), getString(R.string.press_again_to_close_player), Snackbar.LENGTH_SHORT).show();
                exitTime = System.currentTimeMillis();
            } else {
                finish();
                stopPlayback();
            }
        } else {
            finish();
            stopPlayback();
        }
    }

    private void stopPlayback() {
        silenceCurrentPlayback();
    }

    public void errorDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.whops))
                .setCancelable(false)
                .setMessage(getString(R.string.msg_failed))
                .setPositiveButton(getString(R.string.option_retry), (dialog, which) -> retryLoad())
                .setNegativeButton(getString(R.string.option_no), (dialogInterface, i) -> finish())
                .show();
    }

    public void retryLoad() {
        retryPlayback(true);
    }

    private void retryPlayback(boolean showErrorOnFailure) {
        progressBar.setVisibility(View.VISIBLE);

        if (exoPlayer == null) {
            initializePlayer();
            return;
        }

        if (autoRetryCount <= 1) {
            if (IptvPlayerHelper.isLiveStream(video_url)) {
                reloadLiveStream(false);
            } else {
                exoPlayer.seekToDefaultPosition();
                exoPlayer.prepare();
                exoPlayer.setPlayWhenReady(true);
            }
            return;
        }

        reloadLiveStream(showErrorOnFailure);
    }

    private void showBufferingSpinnerDelayed() {
        if (bufferingSpinnerRunnable != null) {
            mainHandler.removeCallbacks(bufferingSpinnerRunnable);
        }
        bufferingSpinnerRunnable = () -> {
            if (progressBar != null && exoPlayer != null
                    && exoPlayer.getPlaybackState() == Player.STATE_BUFFERING) {
                progressBar.setVisibility(View.VISIBLE);
            }
        };
        mainHandler.postDelayed(bufferingSpinnerRunnable, BUFFERING_SPINNER_DELAY_MS);
    }

    private void hideBufferingSpinner() {
        if (bufferingSpinnerRunnable != null) {
            mainHandler.removeCallbacks(bufferingSpinnerRunnable);
            bufferingSpinnerRunnable = null;
        }
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        reloadGeneration++;
        if (channelListDialog != null && channelListDialog.isShowing()) {
            channelListDialog.dismiss();
        }
        if (holdDialog != null && holdDialog.isShowing()) {
            holdDialog.dismiss();
        }
        silenceCurrentPlayback();
        hideBufferingSpinner();
        mainHandler.removeCallbacksAndMessages(null);
        epgExecutor.shutdownNow();
        releasePlayers();
        super.onDestroy();
    }

    private void updateView(String id) {
        if (Tools.isConnect(this)) {
            ApiInterface apiInterface = RestAdapter.createAPI(sharedPref.getBaseUrl());
            Call<Resp> call = apiInterface.updateView(id);
            call.enqueue(new Callback<Resp>() {
                @Override
                public void onResponse(@NonNull Call<Resp> call, @NonNull Response<Resp> response) {
                    Resp data = response.body();
                    if (data != null) {
                        android.util.Log.d(TAG, "View counter updated +" + data.value);
                    }
                }

                @Override
                public void onFailure(@NonNull Call<Resp> call, @NonNull Throwable t) {
                    android.util.Log.d(TAG, "error " + t.getMessage());
                }
            });
        }
    }

    private void loadEpgInfo() {
        if (layoutEpgInfo == null || txtEpgNow == null || txtEpgNext == null) {
            return;
        }
        if (TextUtils.isEmpty(epgUrl) || (TextUtils.isEmpty(epgChannelId) && TextUtils.isEmpty(channelName))) {
            layoutEpgInfo.setVisibility(View.GONE);
            return;
        }
        epgExecutor.execute(() -> {
            EpgPrograms epgPrograms = fetchEpgPrograms(epgUrl, epgChannelId, channelName);
            new Handler(Looper.getMainLooper()).post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (epgPrograms == null || (epgPrograms.now == null && epgPrograms.next == null)) {
                    layoutEpgInfo.setVisibility(View.GONE);
                    return;
                }
                txtEpgNow.setText(epgPrograms.now == null
                        ? getString(R.string.epg_now_unavailable)
                        : getString(R.string.epg_now_format, formatProgram(epgPrograms.now)));
                txtEpgNext.setText(epgPrograms.next == null
                        ? getString(R.string.epg_next_unavailable)
                        : getString(R.string.epg_next_format, formatProgram(epgPrograms.next)));
            });
        });
    }

    private EpgPrograms fetchEpgPrograms(String epgXmlUrl, String epgId, String nameFallback) {
        HttpURLConnection connection = null;
        InputStream inputStream = null;
        try {
            URL url = new URL(epgXmlUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(25000);
            connection.setRequestProperty("User-Agent", user_agent == null || user_agent.trim().isEmpty() || "default".equals(user_agent) ? getUserAgent() : user_agent);
            connection.setRequestProperty("Accept", "*/*");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                return null;
            }
            inputStream = connection.getInputStream();
            return parseEpgXml(inputStream, epgId, nameFallback);
        } catch (Exception e) {
            Log.d(TAG, "EPG load failed: " + e.getMessage());
            return null;
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (Exception ignored) {
                // no-op
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private EpgPrograms parseEpgXml(InputStream inputStream, String epgId, String nameFallback) throws XmlPullParserException, java.io.IOException {
        XmlPullParser parser = android.util.Xml.newPullParser();
        parser.setInput(inputStream, "UTF-8");
        long now = System.currentTimeMillis();
        EpgProgram currentProgram = null;
        EpgProgram nextProgram = null;
        EpgProgram pendingProgram = null;
        boolean matchChannel = false;
        int eventType = parser.getEventType();

        while (eventType != XmlPullParser.END_DOCUMENT) {
            String tag = parser.getName();
            if (eventType == XmlPullParser.START_TAG) {
                if ("programme".equals(tag)) {
                    String channelAttr = parser.getAttributeValue(null, "channel");
                    matchChannel = isEpgChannelMatch(channelAttr, epgId, nameFallback);
                    if (matchChannel) {
                        pendingProgram = new EpgProgram();
                        pendingProgram.title = "";
                        pendingProgram.startTimeMs = parseEpgTime(parser.getAttributeValue(null, "start"));
                        pendingProgram.stopTimeMs = parseEpgTime(parser.getAttributeValue(null, "stop"));
                    }
                } else if (matchChannel && pendingProgram != null && "title".equals(tag)) {
                    pendingProgram.title = safeText(parser.nextText());
                }
            } else if (eventType == XmlPullParser.END_TAG && "programme".equals(tag)) {
                if (matchChannel && pendingProgram != null) {
                    if (pendingProgram.startTimeMs <= now && now < pendingProgram.stopTimeMs) {
                        if (currentProgram == null || pendingProgram.startTimeMs > currentProgram.startTimeMs) {
                            currentProgram = pendingProgram;
                        }
                    } else if (pendingProgram.startTimeMs > now) {
                        if (nextProgram == null || pendingProgram.startTimeMs < nextProgram.startTimeMs) {
                            nextProgram = pendingProgram;
                        }
                    }
                }
                pendingProgram = null;
                matchChannel = false;
            }
            eventType = parser.next();
        }

        EpgPrograms programs = new EpgPrograms();
        programs.now = currentProgram;
        programs.next = nextProgram;
        return programs;
    }

    private boolean isEpgChannelMatch(String xmlChannelValue, String epgId, String nameFallback) {
        if (TextUtils.isEmpty(xmlChannelValue)) {
            return false;
        }
        String channelNorm = xmlChannelValue.trim().toLowerCase(Locale.US);
        if (!TextUtils.isEmpty(epgId) && channelNorm.equals(epgId.trim().toLowerCase(Locale.US))) {
            return true;
        }
        if (!TextUtils.isEmpty(nameFallback)) {
            String fallback = nameFallback.trim().toLowerCase(Locale.US);
            if (channelNorm.equals(fallback)) {
                return true;
            }
        }
        return false;
    }

    private long parseEpgTime(String value) {
        if (TextUtils.isEmpty(value)) {
            return 0L;
        }
        String normalized = value.trim();
        String[] parts = normalized.split("\\s+");
        if (parts.length >= 2) {
            normalized = parts[0] + " " + parts[1];
        } else if (parts.length == 1) {
            normalized = parts[0] + " +0000";
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US);
        format.setTimeZone(TimeZone.getDefault());
        try {
            Date date = format.parse(normalized);
            return date == null ? 0L : date.getTime();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private String formatProgram(EpgProgram program) {
        String title = TextUtils.isEmpty(program.title) ? getString(R.string.epg_unknown_title) : program.title;
        if (program.startTimeMs <= 0 || program.stopTimeMs <= 0) {
            return title;
        }
        SimpleDateFormat hourFormat = new SimpleDateFormat("HH:mm", Locale.US);
        String range = hourFormat.format(new Date(program.startTimeMs)) + " - " + hourFormat.format(new Date(program.stopTimeMs));
        return title + " (" + range + ")";
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isTelevisionDevice() {
        UiModeManager uiModeManager = (UiModeManager) getSystemService(UI_MODE_SERVICE);
        return uiModeManager != null
                && uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    private boolean dismissQuickGesturesTip() {
        if (quickGesturesOverlay == null || quickGesturesOverlay.getVisibility() != View.VISIBLE) {
            return false;
        }
        sharedPref.setQuickGesturesTipShown(true);
        quickGesturesOverlay.setVisibility(View.GONE);
        return true;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupPlayerGestures() {
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        quickGesturesOverlay = findViewById(R.id.quick_gestures_overlay);
        mxGestureOverlay = findViewById(R.id.mx_gesture_overlay);
        mxGestureLabel = findViewById(R.id.mx_gesture_label);
        mxGestureValue = findViewById(R.id.mx_gesture_value);
        mxGestureProgress = findViewById(R.id.mx_gesture_progress);
        channelSwitchOverlay = findViewById(R.id.channel_switch_overlay);
        channelSwitchName = findViewById(R.id.channel_switch_name);
        channelSwitchLogo = findViewById(R.id.channel_switch_logo);
        View gotIt = findViewById(R.id.btn_quick_gestures_got_it);
        View tvHint = findViewById(R.id.txt_quick_gestures_tv_hint);
        if (tvHint != null && isTelevisionDevice()) {
            tvHint.setVisibility(View.VISIBLE);
        }
        if (gotIt != null) {
            gotIt.setOnClickListener(v -> dismissQuickGesturesTip());
        }
        if (!sharedPref.isQuickGesturesTipShown() && quickGesturesOverlay != null) {
            quickGesturesOverlay.setVisibility(View.VISIBLE);
            if (gotIt != null) {
                gotIt.requestFocus();
            }
        }

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_MIN = 100;
            private static final int SWIPE_VEL = 180;

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (styledPlayerView != null) {
                    styledPlayerView.showController();
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                showChannelListDialog();
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                showBrightnessVolumeDialog();
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (e1 == null || e2 == null || Math.abs(distanceY) < Math.abs(distanceX)) {
                    return false;
                }
                int width = styledPlayerView != null ? styledPlayerView.getWidth() : 0;
                if (width <= 0) {
                    View bg = findViewById(R.id.background_view);
                    width = bg != null ? bg.getWidth() : 0;
                }
                if (width <= 0) {
                    return false;
                }
                float startX = e1.getX();
                if (mxGestureMode == 0) {
                    if (startX < width * 0.35f) {
                        mxGestureMode = 1;
                        mxGestureStartY = e1.getY();
                        float brightness = currentBrightness;
                        if (brightness < 0f) {
                            brightness = getWindow().getAttributes().screenBrightness;
                            if (brightness < 0f) {
                                brightness = 0.5f;
                            }
                        }
                        mxGestureStartValue = brightness;
                    } else if (startX > width * 0.65f) {
                        mxGestureMode = 2;
                        mxGestureStartY = e1.getY();
                        int max = audioManager != null ? audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) : 15;
                        int cur = audioManager != null ? audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) : 7;
                        mxGestureStartValue = max <= 0 ? 0f : (cur / (float) max);
                    } else {
                        return false;
                    }
                }
                float delta = (mxGestureStartY - e2.getY()) / Math.max(1f, (float) width);
                float next = Math.max(0f, Math.min(1f, mxGestureStartValue + delta));
                if (mxGestureMode == 1) {
                    applyBrightness(next);
                    showMxGestureOverlay(getString(R.string.quick_gestures_brightness), Math.round(next * 100f));
                    return true;
                }
                if (mxGestureMode == 2) {
                    applyVolumeFraction(next);
                    showMxGestureOverlay(getString(R.string.quick_gestures_volume), Math.round(next * 100f));
                    return true;
                }
                return false;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (mxGestureMode != 0 || e1 == null || e2 == null) {
                    return false;
                }
                float dy = e2.getY() - e1.getY();
                float dx = e2.getX() - e1.getX();
                if (Math.abs(dy) < SWIPE_MIN || Math.abs(dy) < Math.abs(dx) || Math.abs(velocityY) < SWIPE_VEL) {
                    return false;
                }
                int width = styledPlayerView != null ? styledPlayerView.getWidth() : 0;
                if (width > 0) {
                    float startX = e1.getX();
                    if (startX < width * 0.35f || startX > width * 0.65f) {
                        return false;
                    }
                }
                if (dy < 0) {
                    switchChannelByOffset(1);
                } else {
                    switchChannelByOffset(-1);
                }
                return true;
            }
        });

        View.OnTouchListener gestureTouchListener = (v, event) -> {
            if (quickGesturesOverlay != null && quickGesturesOverlay.getVisibility() == View.VISIBLE) {
                return true;
            }
            boolean handled = gestureDetector.onTouchEvent(event);
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                mxGestureMode = 0;
                scheduleHideMxGestureOverlay();
            }
            return true;
        };
        if (styledPlayerView != null) {
            styledPlayerView.setOnTouchListener(gestureTouchListener);
        }
        View background = findViewById(R.id.background_view);
        if (background != null) {
            background.setOnTouchListener(gestureTouchListener);
        }
    }

    private void applyBrightness(float value) {
        currentBrightness = Math.max(0.05f, Math.min(1f, value));
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = currentBrightness;
        getWindow().setAttributes(lp);
    }

    private void applyVolumeFraction(float fraction) {
        if (audioManager == null) {
            return;
        }
        int max = Math.max(1, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        int next = Math.round(Math.max(0f, Math.min(1f, fraction)) * max);
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0);
    }

    private void showMxGestureOverlay(String label, int percent) {
        if (mxGestureOverlay == null) {
            return;
        }
        mxGestureOverlay.setVisibility(View.VISIBLE);
        if (mxGestureLabel != null) {
            mxGestureLabel.setText(label);
        }
        if (mxGestureValue != null) {
            mxGestureValue.setText(percent + "%");
        }
        if (mxGestureProgress != null) {
            mxGestureProgress.setProgress(Math.max(0, Math.min(100, percent)));
        }
        mainHandler.removeCallbacks(hideMxGestureRunnable);
    }

    private void scheduleHideMxGestureOverlay() {
        mainHandler.removeCallbacks(hideMxGestureRunnable);
        mainHandler.postDelayed(hideMxGestureRunnable, 700L);
    }

    private void hideMxGestureOverlay() {
        if (mxGestureOverlay != null) {
            mxGestureOverlay.setVisibility(View.GONE);
        }
    }

    private List<Channel> playableChannels() {
        List<Channel> all = sharedPref.getPlaylistChannels();
        List<Channel> result = new ArrayList<>();
        if (all == null) {
            return result;
        }
        for (Channel channel : all) {
            if (channel == null || TextUtils.isEmpty(channel.channel_url)) {
                continue;
            }
            if ("YOUTUBE".equals(channel.channel_type) || "EMBED".equals(channel.channel_type)) {
                continue;
            }
            result.add(channel);
        }
        return result;
    }

    private int currentChannelIndex(List<Channel> channels) {
        for (int i = 0; i < channels.size(); i++) {
            Channel channel = channels.get(i);
            if (!TextUtils.isEmpty(channel_id) && channel_id.equals(channel.channel_id)) {
                return i;
            }
            if (!TextUtils.isEmpty(sourceStreamUrl)
                    && sourceStreamUrl.equalsIgnoreCase(safeText(channel.channel_url))) {
                return i;
            }
        }
        return -1;
    }

    private void switchChannelByOffset(int offset) {
        List<Channel> channels = playableChannels();
        if (channels.size() < 2) {
            return;
        }
        int index = currentChannelIndex(channels);
        if (index < 0) {
            index = 0;
        }
        int next = (index + offset) % channels.size();
        if (next < 0) {
            next += channels.size();
        }
        switchToChannel(channels.get(next), true);
    }

    private void switchToChannel(Channel channel) {
        switchToChannel(channel, true);
    }

    private void switchToChannel(Channel channel, boolean countSwapAd) {
        if (channel == null || TextUtils.isEmpty(channel.channel_url)) {
            return;
        }
        silenceCurrentPlayback();
        if (channelListDialog != null && channelListDialog.isShowing()) {
            channelListDialog.dismiss();
        }
        if (holdDialog != null && holdDialog.isShowing()) {
            holdDialog.dismiss();
        }
        channel_id = channel.channel_id == null ? "" : channel.channel_id;
        sourceStreamUrl = channel.channel_url.trim();
        user_agent = TextUtils.isEmpty(channel.user_agent) ? "default" : channel.user_agent;
        epgChannelId = channel.epg_id == null ? "" : channel.epg_id;
        epgUrl = channel.epg_url == null ? "" : channel.epg_url;
        channelName = channel.channel_name == null ? "" : channel.channel_name;
        epgLoadScheduled = false;
        autoRetryCount = 0;
        if (channelToast != null) {
            channelToast.cancel();
        }
        channelToast = Toast.makeText(this, channelName, Toast.LENGTH_SHORT);
        channelToast.show();
        showChannelSwitchOverlay(channel);
        if (countSwapAd) {
            maybeShowChannelSwapAd();
        }
        reloadLiveStream(true);
    }

    private void showChannelSwitchOverlay(Channel channel) {
        if (channelSwitchOverlay == null) {
            return;
        }
        String name = channel == null || TextUtils.isEmpty(channel.channel_name)
                ? channelName
                : channel.channel_name;
        if (channelSwitchName != null) {
            channelSwitchName.setText(name);
        }
        if (channelSwitchLogo != null) {
            String image = channel == null ? "" : channel.channel_image;
            if (!TextUtils.isEmpty(image)) {
                String url = image.startsWith("http") ? image
                        : sharedPref.getBaseUrl() + "/upload/" + image.replace(" ", "%20");
                Glide.with(this)
                        .load(url)
                        .placeholder(R.drawable.ic_category_app_logo)
                        .error(R.drawable.ic_category_app_logo)
                        .into(channelSwitchLogo);
            } else {
                channelSwitchLogo.setImageResource(R.drawable.ic_category_app_logo);
            }
        }
        channelSwitchOverlay.setVisibility(View.VISIBLE);
        mainHandler.removeCallbacks(hideChannelSwitchRunnable);
        mainHandler.postDelayed(hideChannelSwitchRunnable, 1_800L);
    }

    private void hideChannelSwitchOverlay() {
        if (channelSwitchOverlay != null) {
            channelSwitchOverlay.setVisibility(View.GONE);
        }
    }

    private void maybeShowChannelSwapAd() {
        if (adsPref == null || !adsPref.getAdStatus() || adsManager == null) {
            return;
        }
        int interval = AdPlacementHelper.resolveInterstitialInterval(adsPref);
        int counter = adsPref.getInterstitialAdCounter();
        if (counter < interval) {
            adsPref.updateInterstitialAdCounter(counter + 1);
            return;
        }
        adsPref.updateInterstitialAdCounter(1);
        try {
            adsManager.showInterstitialAd();
            adsManager.loadInterstitialAd(true, 1);
        } catch (Exception ignored) {
            // Ads must never block channel switching.
        }
    }

    private void showChannelListDialog() {
        List<Channel> channels = playableChannels();
        if (channels.isEmpty()) {
            return;
        }
        if (channelListDialog != null && channelListDialog.isShowing()) {
            channelListDialog.dismiss();
        }

        View content = LayoutInflater.from(this).inflate(R.layout.dialog_player_channels, null, false);
        EditText searchInput = content.findViewById(R.id.input_channel_search);
        ListView listView = content.findViewById(R.id.list_channels);

        final List<Channel> visible = new ArrayList<>(channels);
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_single_choice,
                new ArrayList<>());
        listView.setAdapter(adapter);
        listView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        Runnable bindList = () -> {
            adapter.clear();
            for (Channel channel : visible) {
                adapter.add(TextUtils.isEmpty(channel.channel_name)
                        ? ("Channel " + channel.channel_id)
                        : channel.channel_name);
            }
            adapter.notifyDataSetChanged();
            int selected = 0;
            for (int i = 0; i < visible.size(); i++) {
                Channel channel = visible.get(i);
                if (!TextUtils.isEmpty(channel_id) && channel_id.equals(channel.channel_id)) {
                    selected = i;
                    break;
                }
            }
            if (!visible.isEmpty()) {
                listView.setItemChecked(selected, true);
                listView.setSelection(selected);
            }
        };
        bindList.run();

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                String q = s == null ? "" : s.toString().trim().toLowerCase(Locale.US);
                visible.clear();
                for (Channel channel : channels) {
                    String name = channel.channel_name == null ? "" : channel.channel_name.toLowerCase(Locale.US);
                    if (q.isEmpty() || name.contains(q)) {
                        visible.add(channel);
                    }
                }
                bindList.run();
            }
        });

        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= visible.size()) {
                return;
            }
            Channel selected = visible.get(position);
            if (channelListDialog != null) {
                channelListDialog.dismiss();
            }
            switchToChannel(selected);
        });

        channelListDialog = new MaterialAlertDialogBuilder(this)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        if (channelListDialog.getWindow() != null) {
            channelListDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        channelListDialog.show();
        if (channelListDialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
            channelListDialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                    .setTextColor(ContextCompat.getColor(this, R.color.color_dark_accent));
        }
    }

    private void showBrightnessVolumeDialog() {
        if (holdDialog != null && holdDialog.isShowing()) {
            holdDialog.dismiss();
        }
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_player_hold, null, false);
        SeekBar brightSeek = content.findViewById(R.id.seek_brightness);
        SeekBar volumeSeek = content.findViewById(R.id.seek_volume);

        float brightness = currentBrightness;
        if (brightness < 0f) {
            brightness = getWindow().getAttributes().screenBrightness;
            if (brightness < 0f) {
                brightness = 0.5f;
            }
        }
        brightSeek.setProgress(Math.round(brightness * 100f));
        brightSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float value = Math.max(0.05f, progress / 100f);
                currentBrightness = value;
                WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.screenBrightness = value;
                getWindow().setAttributes(lp);
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        int max = audioManager != null ? audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) : 15;
        int cur = audioManager != null ? audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) : 7;
        volumeSeek.setMax(Math.max(1, max));
        volumeSeek.setProgress(cur);
        volumeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (audioManager != null) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0);
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        holdDialog = new MaterialAlertDialogBuilder(this)
                .setView(content)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.quick_gestures_channel_list, (d, w) -> showChannelListDialog())
                .create();
        if (holdDialog.getWindow() != null) {
            holdDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        holdDialog.show();
        if (holdDialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
            holdDialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setTextColor(ContextCompat.getColor(this, R.color.color_dark_accent));
        }
        if (holdDialog.getButton(AlertDialog.BUTTON_NEUTRAL) != null) {
            holdDialog.getButton(AlertDialog.BUTTON_NEUTRAL)
                    .setTextColor(ContextCompat.getColor(this, R.color.color_dark_accent));
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (quickGesturesOverlay != null && quickGesturesOverlay.getVisibility() == View.VISIBLE) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_BACK) {
                dismissQuickGesturesTip();
                return true;
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_CHANNEL_UP) {
            switchChannelByOffset(1);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) {
            switchChannelByOffset(-1);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            showChannelListDialog();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private static class EpgProgram {
        String title;
        long startTimeMs;
        long stopTimeMs;
    }

    private static class EpgPrograms {
        EpgProgram now;
        EpgProgram next;
    }

}
