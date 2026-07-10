package com.shif.shqip.android.utils;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.shif.shqip.android.databases.prefs.SharedPref;
import android.text.TextUtils;

import java.util.Locale;

public final class StreamPlaybackHelper {

    public static final int AUTO_RETRY_MAX = 6;
    public static final int CONNECT_TIMEOUT_MS = 8_000;
    public static final int READ_TIMEOUT_MS = 20_000;

    private StreamPlaybackHelper() {
    }

    public static LoadControl createLoadControl() {
        return IptvPlayerHelper.createLoadControl();
    }

    public static String resolveForPlayback(SharedPref sharedPref, String sourceUrl) {
        return PlaylistGuardHelper.resolvePlaybackUrlSafe(sharedPref, sourceUrl);
    }

    /**
     * True when ExoPlayer can start immediately without blocking HTTP resolution.
     */
    public static boolean canPlayDirectly(SharedPref sharedPref, String sourceUrl) {
        if (TextUtils.isEmpty(sourceUrl)) {
            return false;
        }
        if (sharedPref != null && sharedPref.isPortalSessionActive()) {
            if (PlaylistPortalHelper.isPortalResolveUrl(sourceUrl)) {
                return true;
            }
            if (PlaylistPortalHelper.isUpstreamStreamUrl(sourceUrl)
                    || PlaylistPortalHelper.isPortalStreamEndpoint(sourceUrl)) {
                return true;
            }
        }
        return isDirectStreamUrl(sourceUrl);
    }

    /**
     * Returns a play URL. Playlist/channel entry URLs stay sealed on the portal;
     * resolved playback goes directly to shifshqip.com channel-proxy.
     */
    public static String resolvePlaybackUrl(SharedPref sharedPref, String sourceUrl, String k1Slug) {
        if (TextUtils.isEmpty(sourceUrl)) {
            return sourceUrl;
        }

        if (sharedPref != null && sharedPref.isPortalSessionActive()) {
            if (PlaylistPortalHelper.isPortalResolveUrl(sourceUrl)) {
                return sourceUrl;
            }
            if (PlaylistPortalHelper.isUpstreamStreamUrl(sourceUrl)
                    || PlaylistPortalHelper.isPortalStreamEndpoint(sourceUrl)) {
                return sourceUrl;
            }
        }

        if (isDirectStreamUrl(sourceUrl)) {
            return K1PlaybackHelper.optimizePlaybackUrl(sourceUrl);
        }

        if (!TextUtils.isEmpty(k1Slug)) {
            String portalUrl = K1ChannelUrlHelper.resolvePlayableUrl(sharedPref, k1Slug);
            if (!TextUtils.isEmpty(portalUrl)) {
                return portalUrl;
            }
        }

        return K1PlaybackHelper.optimizePlaybackUrl(resolveForPlayback(sharedPref, sourceUrl));
    }

    public static String resolveFinalPlaybackUrl(SharedPref sharedPref, String sourceUrl) {
        return resolvePlaybackUrl(sharedPref, sourceUrl, "");
    }

    public static String resolveFinalPlaybackUrl(SharedPref sharedPref, String sourceUrl, String k1Slug) {
        return resolvePlaybackUrl(sharedPref, sourceUrl, k1Slug);
    }

    private static boolean isDirectStreamUrl(String url) {
        String lower = url.toLowerCase(Locale.US);
        if (lower.contains("/channel-proxy/")
                || lower.contains("/channel/")
                || lower.contains("/proxy/")
                || lower.contains("/r/")) {
            return true;
        }
        return lower.contains(".m3u8")
                || lower.contains(".mpd")
                || lower.startsWith("rtsp://")
                || lower.startsWith("rtmp://");
    }

    public static DefaultHttpDataSource.Factory configureHttpFactory(DefaultHttpDataSource.Factory factory) {
        return factory
                .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
                .setReadTimeoutMs(READ_TIMEOUT_MS)
                .setAllowCrossProtocolRedirects(true);
    }
}
