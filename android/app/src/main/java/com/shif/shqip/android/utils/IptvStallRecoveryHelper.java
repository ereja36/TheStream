package com.shif.shqip.android.utils;

import android.os.Handler;
import android.os.Looper;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Player;

/**
 * Mirrors k1 web scheduleStallRecovery: if live playback stops progressing
 * while buffering, soft-recover (seek + prepare) before a full reload.
 */
@SuppressWarnings("deprecation")
public final class IptvStallRecoveryHelper implements Player.Listener {

    public interface Callback {
        void onStallSoftRecover(int attempt);

        void onStallHardRecover();
    }

    private static final long STALL_GRACE_MS = 8_000;
    private static final long LOW_BUFFER_GRACE_MS = 3_000;
    private static final long LOW_BUFFER_THRESHOLD_MS = 3_000;
    private static final long BUFFER_POLL_MS = 2_000;
    private static final long PROGRESS_EPSILON_MS = 200;
    private static final int MAX_STALL_RECOVERIES = 4;

    private final ExoPlayer player;
    private final Callback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final boolean liveStream;

    private long checkpointPositionMs = C.TIME_UNSET;
    private int stallRecoveries;
    private boolean stallTimerScheduled;
    private boolean bufferWatchScheduled;

    private final Runnable stallCheckRunnable = new Runnable() {
        @Override
        public void run() {
            stallTimerScheduled = false;
            if (player.getPlaybackState() != Player.STATE_BUFFERING) {
                return;
            }
            if (!player.getPlayWhenReady()) {
                return;
            }
            long currentPosition = player.getCurrentPosition();
            boolean progressed = checkpointPositionMs != C.TIME_UNSET
                    && Math.abs(currentPosition - checkpointPositionMs) > PROGRESS_EPSILON_MS;
            if (progressed) {
                checkpointPositionMs = currentPosition;
                return;
            }

            triggerSoftRecover();
        }
    };

    private final Runnable lowBufferRunnable = new Runnable() {
        @Override
        public void run() {
            bufferWatchScheduled = false;
            if (!player.getPlayWhenReady()) {
                return;
            }
            int state = player.getPlaybackState();
            if (state != Player.STATE_READY && state != Player.STATE_BUFFERING) {
                return;
            }
            long ahead = IptvPlayerHelper.bufferedAheadMs(player);
            if (ahead >= LOW_BUFFER_THRESHOLD_MS) {
                return;
            }
            triggerSoftRecover();
        }
    };

    private final Runnable bufferPollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!liveStream || !player.getPlayWhenReady()) {
                return;
            }
            int state = player.getPlaybackState();
            if (state == Player.STATE_READY || state == Player.STATE_BUFFERING) {
                long ahead = IptvPlayerHelper.bufferedAheadMs(player);
                if (ahead < LOW_BUFFER_THRESHOLD_MS) {
                    scheduleLowBufferCheck();
                }
            }
            handler.postDelayed(this, BUFFER_POLL_MS);
        }
    };

    public IptvStallRecoveryHelper(ExoPlayer player, String playbackUrl, Callback callback) {
        this.player = player;
        this.callback = callback;
        this.liveStream = IptvPlayerHelper.isLiveStream(playbackUrl);
        if (liveStream) {
            handler.postDelayed(bufferPollRunnable, BUFFER_POLL_MS);
        }
    }

    @Override
    public void onPlaybackStateChanged(int state) {
        if (!liveStream) {
            return;
        }
        if (state == Player.STATE_BUFFERING) {
            if (checkpointPositionMs == C.TIME_UNSET) {
                checkpointPositionMs = player.getCurrentPosition();
            }
            scheduleStallCheck();
        } else if (state == Player.STATE_READY) {
            clearStallWatch();
            stallRecoveries = 0;
            checkpointPositionMs = player.getCurrentPosition();
        } else if (state == Player.STATE_IDLE || state == Player.STATE_ENDED) {
            clearStallWatch();
        }
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
        if (!liveStream) {
            return;
        }
        if (isPlaying) {
            checkpointPositionMs = player.getCurrentPosition();
            stallRecoveries = 0;
            clearStallWatch();
        }
    }

    public void release() {
        handler.removeCallbacks(bufferPollRunnable);
        clearStallWatch();
        clearLowBufferWatch();
    }

    private void triggerSoftRecover() {
        stallRecoveries++;
        if (stallRecoveries <= MAX_STALL_RECOVERIES) {
            softRecover();
            if (callback != null) {
                callback.onStallSoftRecover(stallRecoveries);
            }
            scheduleStallCheck();
            return;
        }

        stallRecoveries = 0;
        if (callback != null) {
            callback.onStallHardRecover();
        }
    }

    private void scheduleStallCheck() {
        if (stallTimerScheduled) {
            return;
        }
        stallTimerScheduled = true;
        handler.postDelayed(stallCheckRunnable, STALL_GRACE_MS);
    }

    private void scheduleLowBufferCheck() {
        if (bufferWatchScheduled) {
            return;
        }
        bufferWatchScheduled = true;
        handler.postDelayed(lowBufferRunnable, LOW_BUFFER_GRACE_MS);
    }

    private void clearStallWatch() {
        handler.removeCallbacks(stallCheckRunnable);
        stallTimerScheduled = false;
    }

    private void clearLowBufferWatch() {
        handler.removeCallbacks(lowBufferRunnable);
        bufferWatchScheduled = false;
    }

    /** Equivalent to HLS.js startLoad(-1) for ExoPlayer. */
    private void softRecover() {
        long recovery = IptvPlayerHelper.liveRecoveryPositionMs(player);
        if (recovery != C.TIME_UNSET) {
            player.seekTo(recovery);
        } else {
            player.seekToDefaultPosition();
        }
        player.prepare();
        player.setPlayWhenReady(true);
    }
}
