package com.shif.shqip.android.utils;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.shif.shqip.android.databases.prefs.SharedPref;
import android.text.TextUtils;

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
     * True only for real CDN stream URLs — never portal /r/ or VPS proxy URLs.
     */
    public static boolean canPlayDirectly(SharedPref sharedPref, String sourceUrl) {
        return PortalResolveHelper.isDirectCdnUrl(sourceUrl);
    }

    /**
     * Returns a direct CDN play URL. Portal obfuscation is resolved once on the server;
     * video traffic goes straight to the stream source afterward.
     */
    public static String resolvePlaybackUrl(SharedPref sharedPref, String sourceUrl, String k1Slug) {
        if (TextUtils.isEmpty(sourceUrl)) {
            return sourceUrl;
        }

        if (PortalResolveHelper.isDirectCdnUrl(sourceUrl)) {
            return sourceUrl.trim();
        }

        if (sharedPref != null && sharedPref.isPortalSessionActive()) {
            if (PlaylistPortalHelper.isPortalResolveUrl(sourceUrl)
                    || PlaylistPortalHelper.isUpstreamStreamUrl(sourceUrl)
                    || K1PlaybackHelper.isK1ProxyUrl(sourceUrl)) {
                String direct = PortalResolveHelper.resolveToDirectUrl(sharedPref, sourceUrl);
                if (!TextUtils.isEmpty(direct) && PortalResolveHelper.isDirectCdnUrl(direct)) {
                    return direct;
                }
            }
        }

        if (!TextUtils.isEmpty(k1Slug)) {
            String portalUrl = K1ChannelUrlHelper.resolvePlayableUrl(sharedPref, k1Slug);
            if (!TextUtils.isEmpty(portalUrl)) {
                if (PortalResolveHelper.isDirectCdnUrl(portalUrl)) {
                    return portalUrl;
                }
                String direct = PortalResolveHelper.resolveToDirectUrl(sharedPref, portalUrl);
                if (!TextUtils.isEmpty(direct)) {
                    return direct;
                }
                return portalUrl;
            }
        }

        String resolved = resolveForPlayback(sharedPref, sourceUrl);
        if (!TextUtils.isEmpty(resolved) && PortalResolveHelper.isDirectCdnUrl(resolved)) {
            return resolved;
        }
        if (!TextUtils.isEmpty(resolved)) {
            String direct = PortalResolveHelper.resolveToDirectUrl(sharedPref, resolved);
            if (!TextUtils.isEmpty(direct)) {
                return direct;
            }
            return resolved;
        }
        return sourceUrl.trim();
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
