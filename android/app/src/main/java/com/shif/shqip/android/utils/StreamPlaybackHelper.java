package com.shif.shqip.android.utils;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.shif.shqip.android.databases.prefs.SharedPref;

public final class StreamPlaybackHelper {

    public static final int AUTO_RETRY_MAX = 3;
    public static final int CONNECT_TIMEOUT_MS = 20_000;
    public static final int READ_TIMEOUT_MS = 60_000;

    private StreamPlaybackHelper() {
    }

    public static LoadControl createLoadControl() {
        return IptvPlayerHelper.createLoadControl();
    }

    public static String resolveForPlayback(SharedPref sharedPref, String sourceUrl) {
        return PlaylistGuardHelper.resolvePlaybackUrlSafe(sharedPref, sourceUrl);
    }

    public static DefaultHttpDataSource.Factory configureHttpFactory(DefaultHttpDataSource.Factory factory) {
        return factory
                .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
                .setReadTimeoutMs(READ_TIMEOUT_MS)
                .setAllowCrossProtocolRedirects(true);
    }
}
