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

    /**
     * Returns a portal-proxied play URL. Upstream shifshqip.com is never exposed to the client.
     */
    public static String resolvePlaybackUrl(SharedPref sharedPref, String sourceUrl, String k1Slug) {
        if (!TextUtils.isEmpty(k1Slug)) {
            String portalUrl = K1ChannelUrlHelper.resolvePlayableUrl(sharedPref, k1Slug);
            if (!TextUtils.isEmpty(portalUrl)) {
                return portalUrl;
            }
        }
        if (TextUtils.isEmpty(sourceUrl)) {
            return sourceUrl;
        }
        if (sharedPref != null && sharedPref.isPortalSessionActive()
                && PlaylistPortalHelper.isPortalResolveUrl(sourceUrl)) {
            return sourceUrl;
        }
        if (sharedPref != null && sharedPref.isPortalSessionActive()
                && PlaylistPortalHelper.isPortalUrl(sourceUrl)) {
            return sourceUrl;
        }
        return K1PlaybackHelper.optimizePlaybackUrl(resolveForPlayback(sharedPref, sourceUrl));
    }

    public static String resolveFinalPlaybackUrl(SharedPref sharedPref, String sourceUrl) {
        return resolvePlaybackUrl(sharedPref, sourceUrl, "");
    }

    public static String resolveFinalPlaybackUrl(SharedPref sharedPref, String sourceUrl, String k1Slug) {
        return resolvePlaybackUrl(sharedPref, sourceUrl, k1Slug);
    }

    public static DefaultHttpDataSource.Factory configureHttpFactory(DefaultHttpDataSource.Factory factory) {
        return factory
                .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
                .setReadTimeoutMs(READ_TIMEOUT_MS)
                .setAllowCrossProtocolRedirects(true);
    }
}
