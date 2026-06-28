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

    /** k1 STALL_GRACE_MS */
    private static final long STALL_GRACE_MS = 18_000;
    private static final long PROGRESS_EPSILON_MS = 200;
    /** k1 MAX_STALL_RECOVERIES */
    private static final int MAX_STALL_RECOVERIES = 4;

    private final ExoPlayer player;
    private final Callback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final boolean liveStream;

    private long checkpointPositionMs = C.TIME_UNSET;
    private int stallRecoveries;
    private boolean stallTimerScheduled;

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
    };

    public IptvStallRecoveryHelper(ExoPlayer player, String playbackUrl, Callback callback) {
        this.player = player;
        this.callback = callback;
        this.liveStream = IptvPlayerHelper.isLiveStream(playbackUrl);
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
        clearStallWatch();
    }

    private void scheduleStallCheck() {
        if (stallTimerScheduled) {
            return;
        }
        stallTimerScheduled = true;
        handler.postDelayed(stallCheckRunnable, STALL_GRACE_MS);
    }

    private void clearStallWatch() {
        handler.removeCallbacks(stallCheckRunnable);
        stallTimerScheduled = false;
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
