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
import android.support.v4.media.session.PlaybackStateCompat;
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
import com.shif.shqip.android.utils.Tools;
import com.shif.shqip.android.utils.TrackSelectionDialog;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.rtsp.RtspMediaSource;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
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

        if (!channel_id.trim().isEmpty()) {
            updateView(channel_id);
        }

        progressBar = findViewById(R.id.progress_bar);
        layoutEpgInfo = findViewById(R.id.layout_epg_info);
        txtEpgNow = findViewById(R.id.txt_epg_now);
        txtEpgNext = findViewById(R.id.txt_epg_next);

        if (user_agent.equals("default")) {
            HttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).setUserAgent(getUserAgent());
            dataSourceFactory = new DefaultDataSource.Factory(getApplicationContext(), httpDataSourceFactory);
            Log.d(TAG, "user agent : " + getUserAgent());
        } else {
            HttpDataSource.Factory httpDataSourceFactory = new DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).setUserAgent(user_agent);
            dataSourceFactory = new DefaultDataSource.Factory(getApplicationContext(), httpDataSourceFactory);
            Log.d(TAG, "user agent : " + user_agent);
        }

        LoadControl loadControl = new DefaultLoadControl();

        AdaptiveTrackSelection.Factory trackSelectionFactory = new AdaptiveTrackSelection.Factory();
        DefaultTrackSelector trackSelector = new DefaultTrackSelector(this, trackSelectionFactory);

        exoPlayer = new ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .build();

        styledPlayerView = findViewById(R.id.exoPlayerView);
        styledPlayerView.setPlayer(exoPlayer);
        styledPlayerView.setUseController(true);
        styledPlayerView.requestFocus();

        playerOrientation();

        Uri uri = Uri.parse(video_url);

        MediaSource mediaSource = buildMediaSource(uri);
        exoPlayer.setMediaSource(mediaSource);
        exoPlayer.prepare();
        exoPlayer.setPlayWhenReady(true);

        exoPlayer.addListener(new Player.Listener() {
            @Override
            public void onCues(@NonNull List<Cue> cues) {

            }

            @Override
            public void onTimelineChanged(@NonNull Timeline timeline, int reason) {

            }

            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == PlaybackStateCompat.STATE_PLAYING) {
                    progressBar.setVisibility(View.GONE);
                    contentLoaded = true;
                }

                if (Config.ENABLE_LOOPING_MODE) {
                    switch (state) {
                        case Player.STATE_READY:
                            progressBar.setVisibility(View.GONE);
                            exoPlayer.setPlayWhenReady(true);
                            break;
                        case Player.STATE_ENDED:
                            exoPlayer.seekTo(0);
                            break;
                        case Player.STATE_BUFFERING:
                            progressBar.setVisibility(View.VISIBLE);
                            exoPlayer.seekTo(0);
                            break;
                        case Player.STATE_IDLE:
                            break;
                    }
                }
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                exoPlayer.stop();
                errorDialog();
                Log.d(TAG, "onPlayerError " + error.getMessage());
            }

            @Override
            public void onPlayerErrorChanged(@Nullable PlaybackException error) {
                Log.d(TAG, "onPlayerErrorChanged " + error);
            }

        });

        styledPlayerView.findViewById(R.id.exo_track_selection_view).setOnClickListener(view -> {
            if (contentLoaded) {
                MappingTrackSelector.MappedTrackInfo mappedTrackInfo;
                DefaultTrackSelector.Parameters parameters = trackSelector.getParameters();
                TrackSelectionDialog trackSelectionDialog = TrackSelectionDialog.createForPlayer(
                        exoPlayer, dismissedDialog -> {
                        });
                trackSelectionDialog.show(getSupportFragmentManager(), null);
            } else {
                Toast.makeText(getApplicationContext(), R.string.please_wait, Toast.LENGTH_SHORT).show();
            }
        });

        styledPlayerView.findViewById(R.id.btn_cast).setOnClickListener(view -> {
            if (contentLoaded) {
                Intent shareVideo = new Intent(Intent.ACTION_VIEW);
                shareVideo.setDataAndType(Uri.parse(video_url), "video/*");
                shareVideo.setPackage("com.instantbits.cast.webvideo");
                shareVideo.putExtra("secure_uri", true);
                try {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(getString(R.string.dialog_title_cast))
                            .setMessage(getString(R.string.ready_cast))
                            .setPositiveButton(getString(R.string.dialog_ok), (dialog, which) -> {
                                startActivity(shareVideo);
                            })
                            .setNegativeButton(getString(R.string.dialog_cancel), (dialogInterface, i) -> {
                            })
                            .show();
                } catch (ActivityNotFoundException ex) {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle(getString(R.string.whops))
                            .setMessage(getString(R.string.error_cast))
                            .setPositiveButton(getString(R.string.dialog_ok), (dialog, which) -> {
                                Intent install = new Intent(Intent.ACTION_VIEW);
                                String uriString = "https://play.google.com/store/apps/details?id=com.instantbits.cast.webvideo";
                                install.setData(Uri.parse(uriString));
                                startActivity(install);
                            })
                            .setNegativeButton(getString(R.string.dialog_cancel), (dialogInterface, i) -> {
                            })
                            .show();
                }
            } else {
                Toast.makeText(getApplicationContext(), R.string.please_wait, Toast.LENGTH_SHORT).show();
            }
        });

        handleOnBackPressed();
        loadEpgInfo();

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

    @SuppressLint("SwitchIntDef")
    private MediaSource buildMediaSource(Uri uri) {
        MediaItem mMediaItem = MediaItem.fromUri(Uri.parse(String.valueOf(uri)));
        int type = TextUtils.isEmpty(null) ? Util.inferContentType(uri) : Util.inferContentType("." + null);
        switch (type) {
            case C.TYPE_DASH:
                return new DashMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(mMediaItem);
            case C.TYPE_HLS:
                return new HlsMediaSource.Factory(dataSourceFactory)
                        .setAllowChunklessPreparation(true)
                        .createMediaSource(mMediaItem);
            case C.TYPE_OTHER:
                if (video_url.contains("hls")) {
                    return new HlsMediaSource.Factory(dataSourceFactory)
                            .setAllowChunklessPreparation(true)
                            .createMediaSource(mMediaItem);
                } else {
                    return new ProgressiveMediaSource.Factory(dataSourceFactory, new DefaultExtractorsFactory())
                            .createMediaSource(mMediaItem);
                }
            case C.TYPE_RTSP:
                return new RtspMediaSource.Factory()
                        .createMediaSource(MediaItem.fromUri(uri));
            default: {
                throw new IllegalStateException("Unsupported type: " + type);
            }
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
                closePlayer();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        exoPlayer.setPlayWhenReady(false);
        exoPlayer.getPlaybackState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        exoPlayer.setPlayWhenReady(true);
        exoPlayer.getPlaybackState();
    }

    public void closePlayer() {
        if (PRESS_BACK_TWICE_TO_CLOSE_PLAYER) {
            if ((System.currentTimeMillis() - exitTime) > 2000) {
                Snackbar.make(findViewById(R.id.root_view), getString(R.string.press_again_to_close_player), Snackbar.LENGTH_SHORT).show();
                exitTime = System.currentTimeMillis();
            } else {
                finish();
                exoPlayer.stop();
            }
        } else {
            finish();
            exoPlayer.stop();
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
        Uri uri = Uri.parse(video_url);
        MediaSource mediaSource = buildMediaSource(uri);
        exoPlayer.setMediaSource(mediaSource);
        exoPlayer.prepare();
        exoPlayer.setPlayWhenReady(true);
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
                layoutEpgInfo.setVisibility(View.VISIBLE);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        epgExecutor.shutdownNow();
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
