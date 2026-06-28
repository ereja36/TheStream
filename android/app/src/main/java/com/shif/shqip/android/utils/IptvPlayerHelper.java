package com.shif.shqip.android.utils;

import android.content.Context;
import android.net.Uri;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.MediaItem;
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
 * ExoPlayer setup tuned for live IPTV playback, modeled after One IPTV Player:
 * direct URL playback, drip-feed buffering, and stable HLS handling.
 */
@SuppressWarnings("deprecation")
public final class IptvPlayerHelper {

  private static final int MIN_BUFFER_MS = 30_000;
  private static final int MAX_BUFFER_MS = 30_000;
  private static final int BUFFER_FOR_PLAYBACK_MS = 1_000;
  private static final int BUFFER_AFTER_REBUFFER_MS = 2_500;
  private static final int HLS_MIN_LOADABLE_RETRY_COUNT = 12;

  private IptvPlayerHelper() {
  }

  public static LoadControl createLoadControl() {
    return new DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                    MIN_BUFFER_MS,
                    MAX_BUFFER_MS,
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
        return new HlsMediaSource.Factory(dataSourceFactory)
                .setAllowChunklessPreparation(false)
                .setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy(HLS_MIN_LOADABLE_RETRY_COUNT))
                .createMediaSource(mediaItem);
      case C.TYPE_RTSP:
        return new RtspMediaSource.Factory().createMediaSource(mediaItem);
      case C.TYPE_OTHER:
      default:
        if (playbackUrl.toLowerCase(Locale.US).contains("hls")) {
          return new HlsMediaSource.Factory(dataSourceFactory)
                  .setAllowChunklessPreparation(false)
                  .setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy(HLS_MIN_LOADABLE_RETRY_COUNT))
                  .createMediaSource(mediaItem);
        }
        return new ProgressiveMediaSource.Factory(dataSourceFactory, new DefaultExtractorsFactory())
                .createMediaSource(mediaItem);
    }
  }

  private static MediaItem buildMediaItem(String playbackUrl, Uri uri) {
    if (isLiveStream(playbackUrl)) {
      return new MediaItem.Builder()
              .setUri(uri)
              .setLiveConfiguration(
                      new MediaItem.LiveConfiguration.Builder()
                              .setTargetOffsetMs(3_000)
                              .setMinPlaybackSpeed(0.97f)
                              .setMaxPlaybackSpeed(1.03f)
                              .build()
              )
              .build();
    }
    return MediaItem.fromUri(uri);
  }

  private static boolean isLiveStream(String url) {
    if (url == null) {
      return false;
    }
    String lower = url.toLowerCase(Locale.US);
    return lower.contains(".m3u8")
            || lower.contains("/hls/")
            || lower.contains("/channel/")
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
}
