package com.shif.shqip.android.utils;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.rtsp.RtspMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultLoadErrorHandlingPolicy;
import com.google.android.exoplayer2.util.Util;
import com.shif.shqip.android.databases.prefs.SharedPref;

import java.util.Locale;

/**
 * ExoPlayer tuned to match shifshqip.com/k1 HLS.js live settings:
 * liveSyncDurationCount: 6, maxLiveSyncPlaybackRate: 1,
 * maxBufferLength: 60, backBufferLength: 90.
 *
 * IPTV playlists expose only ~4 x 6s segments (~24s window); playback must stay
 * near the back of that window instead of chasing the live edge.
 */
@SuppressWarnings("deprecation")
public final class IptvPlayerHelper {

  /** HLS.js maxBufferLength: 60 */
  private static final int LIVE_MIN_BUFFER_MS = 15_000;
  private static final int LIVE_MAX_BUFFER_MS = 60_000;
  private static final int BUFFER_FOR_PLAYBACK_MS = 2_000;
  private static final int BUFFER_AFTER_REBUFFER_MS = 4_000;

  /**
   * HLS.js liveSyncDurationCount: 6 (~36s behind edge). With a ~24s window this
   * maps to staying near the oldest available segment (back of the playlist).
   */
  private static final long LIVE_TARGET_OFFSET_MS = 22_000;
  private static final long LIVE_MIN_OFFSET_MS = 18_000;
  private static final long LIVE_MAX_OFFSET_MS = 23_500;
  private static final long LIVE_SAFE_EDGE_MS = 6_000;
  private static final long LIVE_SEEK_BACK_MS = 20_000;

  private static final int HLS_MIN_LOADABLE_RETRY_COUNT = 5;
  private static final long OFFSET_GUARD_INTERVAL_MS = 6_000;

  private IptvPlayerHelper() {
  }

  public static LoadControl createLoadControl() {
    return new DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                    LIVE_MIN_BUFFER_MS,
                    LIVE_MAX_BUFFER_MS,
                    BUFFER_FOR_PLAYBACK_MS,
                    BUFFER_AFTER_REBUFFER_MS
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build();
  }

  public static ExoPlayer createPlayer(Context context) {
    DefaultTrackSelector trackSelector = new DefaultTrackSelector(
            context,
            new AdaptiveTrackSelection.Factory()
    );
    return new ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setLoadControl(createLoadControl())
            .build();
  }

  public static DefaultDataSource.Factory createDataSourceFactory(
          Context context,
          SharedPref sharedPref,
          String sourceUrl,
          String channelUserAgent
  ) {
    if (sharedPref != null && sharedPref.isPortalSessionActive()) {
      return new DefaultDataSource.Factory(
              context.getApplicationContext(),
              new IptvHttpDataSource.Factory(context, sharedPref, channelUserAgent)
      );
    }
    return PlaylistGuardHelper.createDataSourceFactory(context, sharedPref, sourceUrl, channelUserAgent);
  }

  public static MediaSource buildMediaSource(
          String playbackUrl,
          DefaultDataSource.Factory dataSourceFactory
  ) {
    Uri uri = Uri.parse(playbackUrl);
    MediaItem mediaItem = buildMediaItem(playbackUrl, uri);
    int type = inferStreamType(playbackUrl, uri);

    switch (type) {
      case C.TYPE_DASH:
        return new DashMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem);
      case C.TYPE_HLS:
        return buildHlsSource(dataSourceFactory, mediaItem);
      case C.TYPE_RTSP:
        return new RtspMediaSource.Factory().createMediaSource(mediaItem);
      case C.TYPE_OTHER:
      default:
        if (playbackUrl.toLowerCase(Locale.US).contains("hls")) {
          return buildHlsSource(dataSourceFactory, mediaItem);
        }
        return new ProgressiveMediaSource.Factory(dataSourceFactory, new DefaultExtractorsFactory())
                .createMediaSource(mediaItem);
    }
  }

  private static HlsMediaSource buildHlsSource(
          DefaultDataSource.Factory dataSourceFactory,
          MediaItem mediaItem
  ) {
    return new HlsMediaSource.Factory(dataSourceFactory)
            .setAllowChunklessPreparation(false)
            .setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy(HLS_MIN_LOADABLE_RETRY_COUNT))
            .createMediaSource(mediaItem);
  }

  private static MediaItem buildMediaItem(String playbackUrl, Uri uri) {
    if (isLiveStream(playbackUrl)) {
      return new MediaItem.Builder()
              .setUri(uri)
              .setLiveConfiguration(
                      new MediaItem.LiveConfiguration.Builder()
                              .setTargetOffsetMs(LIVE_TARGET_OFFSET_MS)
                              .setMinOffsetMs(LIVE_MIN_OFFSET_MS)
                              .setMaxOffsetMs(LIVE_MAX_OFFSET_MS)
                              .setMinPlaybackSpeed(1.0f)
                              .setMaxPlaybackSpeed(1.0f)
                              .build()
              )
              .build();
    }
    return MediaItem.fromUri(uri);
  }

  /** Keeps live playback at the back of short HLS windows (HLS.js live-sync logic). */
  public static Player.Listener createLiveOffsetListener(ExoPlayer player, String playbackUrl) {
    if (!isLiveStream(playbackUrl) || player == null) {
      return new Player.Listener() {};
    }
    return new LiveOffsetGuard(player);
  }

  /** Seek target when the player falls behind the live window. */
  public static long liveRecoveryPositionMs(ExoPlayer player) {
    if (player == null) {
      return C.TIME_UNSET;
    }
    long duration = player.getDuration();
    if (duration <= 0 || duration == C.TIME_UNSET) {
      return C.TIME_UNSET;
    }
    return Math.max(0L, duration - LIVE_SEEK_BACK_MS);
  }

  public static boolean isLiveStream(String url) {
    if (url == null) {
      return false;
    }
    String lower = url.toLowerCase(Locale.US);
    return lower.contains(".m3u8")
            || lower.contains("/hls/")
            || lower.contains("/channel/")
            || lower.contains("/channel-proxy/")
            || lower.contains("/r/")
            || lower.contains("/proxy/");
  }

  private static int inferStreamType(String playbackUrl, Uri uri) {
    int type = Util.inferContentType(uri);
    if (type == C.TYPE_OTHER && PlaylistPortalHelper.isPortalStreamEndpoint(playbackUrl)) {
      return C.TYPE_HLS;
    }
    String lowerUrl = playbackUrl.toLowerCase(Locale.US);
    if (type == C.TYPE_OTHER
            && (lowerUrl.contains(".m3u8")
            || lowerUrl.contains(".m3u")
            || lowerUrl.contains("/hls/")
            || lowerUrl.contains("/r/"))) {
      return C.TYPE_HLS;
    }
    return type;
  }

  private static final class LiveOffsetGuard implements Player.Listener {

    private final ExoPlayer player;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean startAdjusted;

    private final Runnable guardRunnable = new Runnable() {
      @Override
      public void run() {
        maintainOffset();
        handler.postDelayed(this, OFFSET_GUARD_INTERVAL_MS);
      }
    };

    LiveOffsetGuard(ExoPlayer player) {
      this.player = player;
    }

    @Override
    public void onPlaybackStateChanged(int state) {
      if (state == Player.STATE_READY) {
        if (!startAdjusted) {
          adjustStartPosition();
          startAdjusted = true;
        }
        handler.removeCallbacks(guardRunnable);
        handler.post(guardRunnable);
      } else if (state == Player.STATE_IDLE || state == Player.STATE_ENDED) {
        handler.removeCallbacks(guardRunnable);
      }
    }

    private void adjustStartPosition() {
      long duration = player.getDuration();
      if (duration <= 0 || duration == C.TIME_UNSET) {
        return;
      }
      long position = player.getCurrentPosition();
      long distanceFromEdge = duration - position;
      if (distanceFromEdge < LIVE_SAFE_EDGE_MS) {
        player.seekTo(Math.max(0, duration - LIVE_SEEK_BACK_MS));
      }
    }

    private void maintainOffset() {
      if (!player.isPlaying()) {
        return;
      }
      long duration = player.getDuration();
      if (duration <= 0 || duration == C.TIME_UNSET) {
        return;
      }
      long position = player.getCurrentPosition();
      long distanceFromEdge = duration - position;
      if (distanceFromEdge < LIVE_SAFE_EDGE_MS) {
        long target = Math.max(0, duration - LIVE_SEEK_BACK_MS);
        if (Math.abs(target - position) > 1_500) {
          player.seekTo(target);
        }
      }
    }
  }
}
