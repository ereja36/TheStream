package com.shif.shqip.android.activities;

import static com.shif.shqip.android.Config.PRESS_BACK_TWICE_TO_CLOSE_PLAYER;
import static com.shif.shqip.android.utils.Constant.PLAYER_MODE_LANDSCAPE;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

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
import com.shif.shqip.android.utils.StreamPlaybackHelper;
import com.shif.shqip.android.utils.Tools;
import com.shif.shqip.android.utils.TrackSelectionDialog;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_player_video);
        Tools.setPlayerNavigation(this);
        sharedPref = new SharedPref(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            this.getWindow().setNavigationBarColor(ContextCompat.getColor(getApplicationContext(), R.color.color_black));
            this.getWindow().setStatusBarColor(ContextCompat.getColor(getApplicationContext(), R.color.color_black));
            this.getWindow().getDecorView().setSystemUiVisibility(0);
        }
        Intent intent = getIntent();
        channel_id = intent.getStringExtra("channel_id");
        video_url = intent.getStringExtra("url");
        user_agent = intent.getStringExtra("user_agent");
        epgChannelId = intent.getStringExtra("epg_channel_id");
        epgUrl = intent.getStringExtra("epg_url");
        channelName = intent.getStringExtra("channel_name");

        if (channel_id == null) {
            channel_id = "";
        }
        if (user_agent == null || user_agent.trim().isEmpty()) {
            user_agent = "default";
        }
        if (video_url == null || video_url.trim().isEmpty()) {
            Toast.makeText(this, R.string.msg_failed, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        sourceStreamUrl = video_url.trim();

        if (!channel_id.trim().isEmpty()) {
            updateView(channel_id);
        }

        progressBar = findViewById(R.id.progress_bar);
        layoutEpgInfo = findViewById(R.id.layout_epg_info);
        txtEpgNow = findViewById(R.id.txt_epg_now);
        txtEpgNext = findViewById(R.id.txt_epg_next);
        progressBar.setVisibility(View.VISIBLE);
        video_url = StreamPlaybackHelper.resolveForPlayback(sharedPref, sourceStreamUrl);
        if (TextUtils.isEmpty(video_url)) {
            errorDialog();
            return;
        }
        autoRetryCount = 0;
        initializePlayer();
    }

    private void initializePlayer() {
        if (isFinishing()) {
            return;
        }

        dataSourceFactory = IptvPlayerHelper.createDataSourceFactory(
                this, sharedPref, sourceStreamUrl, user_agent);
        Log.d(TAG, "playback url: " + video_url);

        if (exoPlayer != null) {
            exoPlayer.release();
        }
        exoPlayer = IptvPlayerHelper.createPlayer(this);

        styledPlayerView = findViewById(R.id.exoPlayerView);
        styledPlayerView.setPlayer(exoPlayer);
        styledPlayerView.setUseController(true);
        styledPlayerView.requestFocus();

        playerOrientation();
        startPlayback();
        attachPlayerListener();
        setupControls();
    }

    private void startPlayback() {
        MediaSource mediaSource = IptvPlayerHelper.buildMediaSource(video_url, dataSourceFactory);
        exoPlayer.setMediaSource(mediaSource);
        exoPlayer.prepare();
        exoPlayer.setPlayWhenReady(true);
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
                        progressBar.setVisibility(View.GONE);
                        contentLoaded = true;
                        break;
                    case Player.STATE_BUFFERING:
                        if (!Config.ENABLE_LOOPING_MODE) {
                            progressBar.setVisibility(View.VISIBLE);
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
                    exoPlayer.seekToDefaultPosition();
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
        styledPlayerView.findViewById(R.id.exo_track_selection_view).setOnClickListener(view -> {
            if (contentLoaded) {
                TrackSelectionDialog trackSelectionDialog = TrackSelectionDialog.createForPlayer(
                        exoPlayer, dismissedDialog -> {
                        });
                trackSelectionDialog.show(getSupportFragmentManager(), null);
            } else {
                Toast.makeText(getApplicationContext(), R.string.please_wait, Toast.LENGTH_SHORT).show();
            }
        });

        View btnEpg = styledPlayerView.findViewById(R.id.btn_epg);
        if (sharedPref.isPlayerEpgEnabled() && !TextUtils.isEmpty(epgUrl)) {
            btnEpg.setVisibility(View.VISIBLE);
            btnEpg.setOnClickListener(view -> {
                if (layoutEpgInfo.getVisibility() == View.VISIBLE) {
                    layoutEpgInfo.setVisibility(View.GONE);
                } else {
                    layoutEpgInfo.setVisibility(View.VISIBLE);
                }
            });
        } else if (btnEpg != null) {
            btnEpg.setVisibility(View.GONE);
        }

        handleOnBackPressed();
        if (sharedPref.isPlayerEpgEnabled()) {
            loadEpgInfo();
        }
    }

    private void playerOrientation() {
        fullscreenButton = styledPlayerView.findViewById(R.id.exo_fullscreen_icon);
        fullscreenButton.setOnClickListener(view -> {
            if (fullscreen) {
                setPortrait();
            } else {
                setLandscape();
            }
        });

        if (sharedPref.getPlayerOrientation() == PLAYER_MODE_LANDSCAPE) {
            setLandscape();
        }

    }

    @SuppressLint("SourceLockedOrientationActivity")
    private void setPortrait() {
        fullscreenButton.setImageDrawable(ContextCompat.getDrawable(ActivityPlayerVideo.this, R.drawable.ic_exo_fullscreen_open));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        if (getSupportActionBar() != null) {
            getSupportActionBar().show();
        }
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) styledPlayerView.getLayoutParams();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        styledPlayerView.setLayoutParams(params);
        fullscreen = false;
    }

    private void setLandscape() {
        fullscreenButton.setImageDrawable(ContextCompat.getDrawable(ActivityPlayerVideo.this, R.drawable.ic_exo_fullscreen_close));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) styledPlayerView.getLayoutParams();
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        styledPlayerView.setLayoutParams(params);
        fullscreen = true;
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
                closePlayer();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (exoPlayer != null) {
            exoPlayer.setPlayWhenReady(false);
            exoPlayer.getPlaybackState();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (exoPlayer != null) {
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
                if (exoPlayer != null) {
                    exoPlayer.stop();
                }
            }
        } else {
            finish();
            if (exoPlayer != null) {
                exoPlayer.stop();
            }
        }
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
        if (exoPlayer == null) {
            return;
        }
        progressBar.setVisibility(View.VISIBLE);

        if (autoRetryCount <= 1) {
            exoPlayer.seekToDefaultPosition();
            exoPlayer.prepare();
            return;
        }

        String resolvedUrl = StreamPlaybackHelper.resolveForPlayback(sharedPref, sourceStreamUrl);
        if (TextUtils.isEmpty(resolvedUrl)) {
            resolvedUrl = sourceStreamUrl;
        }
        if (TextUtils.isEmpty(resolvedUrl)) {
            progressBar.setVisibility(View.GONE);
            if (showErrorOnFailure) {
                errorDialog();
            }
            return;
        }
        video_url = resolvedUrl;
        dataSourceFactory = IptvPlayerHelper.createDataSourceFactory(
                this, sharedPref, sourceStreamUrl, user_agent);
        MediaSource mediaSource = IptvPlayerHelper.buildMediaSource(video_url, dataSourceFactory);
        exoPlayer.setMediaSource(mediaSource);
        exoPlayer.prepare();
        exoPlayer.setPlayWhenReady(true);
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        epgExecutor.shutdownNow();
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
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
