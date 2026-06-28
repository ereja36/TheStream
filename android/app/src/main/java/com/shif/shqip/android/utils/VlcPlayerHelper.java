package com.shif.shqip.android.utils;

import android.content.Context;
import android.net.Uri;
import android.view.View;

import com.shif.shqip.android.models.PlaylistGuardRule;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.util.ArrayList;

/**
 * VLC/libVLC playback — same engine as VLC desktop, handles short HLS windows
 * (4 x 6s segments) without the live-edge rebuffer loop ExoPlayer exhibits.
 */
public final class VlcPlayerHelper {

    public interface PlaybackCallback {
        void onPlaying();

        void onBuffering(float percent);

        void onStopped();

        void onError();
    }

    private final Context appContext;
    private LibVLC libVlc;
    private MediaPlayer mediaPlayer;
    private PlaybackCallback callback;

    public VlcPlayerHelper(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void setCallback(PlaybackCallback callback) {
        this.callback = callback;
    }

    public void initialize() {
        if (libVlc != null) {
            return;
        }
        ArrayList<String> options = new ArrayList<>();
        options.add("--network-caching=1500");
        options.add("--live-caching=1500");
        options.add("--clock-jitter=0");
        options.add("--clock-synchro=0");
        libVlc = new LibVLC(appContext, options);
        mediaPlayer = new MediaPlayer(libVlc);
        mediaPlayer.setEventListener(event -> {
            if (callback == null) {
                return;
            }
            switch (event.type) {
                case MediaPlayer.Event.Playing:
                    callback.onPlaying();
                    break;
                case MediaPlayer.Event.Buffering:
                    callback.onBuffering(event.getBuffering());
                    break;
                case MediaPlayer.Event.Stopped:
                case MediaPlayer.Event.EndReached:
                    callback.onStopped();
                    break;
                case MediaPlayer.Event.EncounteredError:
                    callback.onError();
                    break;
                default:
                    break;
            }
        });
    }

    public void play(VLCVideoLayout videoLayout, String playbackUrl, PlaylistGuardRule rule, String channelUserAgent) {
        if (libVlc == null || mediaPlayer == null) {
            initialize();
        }
        detachViews();
        mediaPlayer.attachViews(videoLayout, null, false, false);

        String ua = PlaylistGuardHelper.resolveUserAgent(rule, channelUserAgent);
        if (PlaylistPortalHelper.isUpstreamStreamUrl(playbackUrl)
                || playbackUrl.contains("shifshqip.com/k1/")) {
            ua = PlaylistPortalHelper.UPSTREAM_STREAM_UA;
        }

        Media media = new Media(libVlc, Uri.parse(playbackUrl));
        media.addOption(":network-caching=1500");
        media.addOption(":live-caching=1500");
        media.addOption(":http-user-agent=" + ua);
        media.addOption(":http-referrer=" + PlaylistPortalHelper.UPSTREAM_STREAM_REFERER);
        media.addOption(":no-http-reconnect");
        media.setHWDecoderEnabled(true, false);
        mediaPlayer.setMedia(media);
        media.release();
        mediaPlayer.play();
    }

    public void pause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    public void resume() {
        if (mediaPlayer != null) {
            mediaPlayer.play();
        }
    }

    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    public void stop() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
        }
    }

    public void detachViews() {
        if (mediaPlayer != null) {
            mediaPlayer.detachViews();
        }
    }

    public void release() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            detachViews();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (libVlc != null) {
            libVlc.release();
            libVlc = null;
        }
    }

    public static boolean shouldUseVlc(String url) {
        return IptvPlayerHelper.isLiveStream(url);
    }

    public static void setPlayerVisibility(boolean useVlc, View vlcLayout, View exoView) {
        if (vlcLayout != null) {
            vlcLayout.setVisibility(useVlc ? View.VISIBLE : View.GONE);
        }
        if (exoView != null) {
            exoView.setVisibility(useVlc ? View.GONE : View.VISIBLE);
        }
    }
}
