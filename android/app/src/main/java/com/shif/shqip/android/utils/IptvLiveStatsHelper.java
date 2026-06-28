package com.shif.shqip.android.utils;

import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.analytics.AnalyticsListener;

import java.util.Locale;

/**
 * Live stream stats overlay (quality, bitrate, buffer) like shifshqip.com/k1 web.
 */
@SuppressWarnings("deprecation")
public final class IptvLiveStatsHelper implements AnalyticsListener, Player.Listener {

    private final ExoPlayer player;
    private final TextView statsView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private long estimatedBitrateKbps;

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            updateStats();
            handler.postDelayed(this, 1_000);
        }
    };

    public IptvLiveStatsHelper(ExoPlayer player, TextView statsView) {
        this.player = player;
        this.statsView = statsView;
    }

    public void start() {
        handler.post(refreshRunnable);
    }

    public void stop() {
        handler.removeCallbacks(refreshRunnable);
    }

    @Override
    public void onBandwidthEstimate(
            EventTime eventTime,
            int totalLoadTimeMs,
            long totalBytesLoaded,
            long bitrateEstimate
    ) {
        if (bitrateEstimate > 0) {
            estimatedBitrateKbps = bitrateEstimate / 1000;
        }
    }

    private void updateStats() {
        if (statsView == null || player == null) {
            return;
        }
        String quality = "--";
        Format videoFormat = player.getVideoFormat();
        if (videoFormat != null && videoFormat.height > 0) {
            quality = videoFormat.height + "p";
        }

        long bitrate = estimatedBitrateKbps;
        if (videoFormat != null && videoFormat.bitrate > 0) {
            bitrate = videoFormat.bitrate / 1000;
        }

        long bufferSec = player.getBufferedPosition() > 0
                ? Math.max(0, (player.getBufferedPosition() - player.getCurrentPosition()) / 1000)
                : 0;
        String state;
        switch (player.getPlaybackState()) {
            case Player.STATE_BUFFERING:
                state = "Buffering";
                break;
            case Player.STATE_READY:
                state = player.getPlayWhenReady() ? "Live" : "Paused";
                break;
            default:
                state = "Loading";
                break;
        }

        String text = String.format(
                Locale.US,
                "%s · %d kbps · buf %ds · %s",
                quality,
                bitrate,
                bufferSec,
                state
        );
        statsView.setText(text);
    }
}
