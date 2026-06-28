package com.shif.shqip.android.utils;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.shif.shqip.android.databases.prefs.SharedPref;
import android.text.TextUtils;

public final class StreamPlaybackHelper {

    public static final int AUTO_RETRY_MAX = 6;
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

    /** Resolve portal /r/ URLs to the final upstream link. */
    public static String resolveFinalPlaybackUrl(SharedPref sharedPref, String sourceUrl) {
        return resolveFinalPlaybackUrl(sharedPref, sourceUrl, "");
    }

    public static String resolveFinalPlaybackUrl(SharedPref sharedPref, String sourceUrl, String k1Slug) {
        if (!TextUtils.isEmpty(k1Slug)) {
            String apiUrl = K1ChannelUrlHelper.resolvePlayableUrl(k1Slug);
            if (!TextUtils.isEmpty(apiUrl)) {
                return apiUrl;
            }
        }
        if (TextUtils.isEmpty(sourceUrl)) {
            return sourceUrl;
        }
        String resolved;
        if (sharedPref != null && PlaylistPortalHelper.isPortalResolveUrl(sourceUrl)) {
            try {
                resolved = PlaylistGuardHelper.resolvePlaybackUrl(sharedPref, sourceUrl);
            } catch (Exception ignored) {
                return "";
            }
        } else {
            resolved = resolveForPlayback(sharedPref, sourceUrl);
        }
        return K1PlaybackHelper.optimizePlaybackUrl(resolved);
    }

    public static DefaultHttpDataSource.Factory configureHttpFactory(DefaultHttpDataSource.Factory factory) {
        return factory
                .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
                .setReadTimeoutMs(READ_TIMEOUT_MS)
                .setAllowCrossProtocolRedirects(true);
    }
}
