package com.shif.shqip.android.utils;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.shif.shqip.android.databases.prefs.SharedPref;
import android.text.TextUtils;

public final class StreamPlaybackHelper {

    public static final int AUTO_RETRY_MAX = 4;
    public static final int CONNECT_TIMEOUT_MS = 8_000;
    public static final int READ_TIMEOUT_MS = 15_000;

    private StreamPlaybackHelper() {
    }

    public static LoadControl createLoadControl() {
        return IptvPlayerHelper.createLoadControl();
    }

    public static String resolveForPlayback(SharedPref sharedPref, String sourceUrl) {
        return PlaylistGuardHelper.resolvePlaybackUrlSafe(sharedPref, sourceUrl);
    }

    public static boolean canPlayDirectly(SharedPref sharedPref, String sourceUrl) {
        return PortalResolveHelper.isDirectCdnUrl(sourceUrl);
    }

    public static boolean isPlayableUrl(String url) {
        return PortalResolveHelper.isDirectCdnUrl(url);
    }

    /**
     * Returns a direct CDN play URL or empty string if resolution fails.
     */
    public static String resolvePlaybackUrl(SharedPref sharedPref, String sourceUrl, String k1Slug) {
        if (TextUtils.isEmpty(sourceUrl)) {
            return "";
        }
        String trimmed = sourceUrl.trim();

        if (PortalResolveHelper.isDirectCdnUrl(trimmed)) {
            return trimmed;
        }

        if (sharedPref != null && sharedPref.isPortalSessionActive()) {
            if (PlaylistPortalHelper.isPortalResolveUrl(trimmed)
                    || PlaylistPortalHelper.isUpstreamStreamUrl(trimmed)
                    || K1PlaybackHelper.isK1ProxyUrl(trimmed)) {
                String direct = PortalResolveHelper.resolveToDirectUrl(sharedPref, trimmed);
                if (PortalResolveHelper.isDirectCdnUrl(direct)) {
                    return direct;
                }
            }
        }

        if (!TextUtils.isEmpty(k1Slug)
                && K1ChannelUrlHelper.looksLikeK1Slug(k1Slug)
                && !PlaylistPortalHelper.isPortalResolveUrl(trimmed)) {
            String portalUrl = K1ChannelUrlHelper.resolvePlayableUrl(sharedPref, k1Slug);
            if (!TextUtils.isEmpty(portalUrl)) {
                if (PortalResolveHelper.isDirectCdnUrl(portalUrl)) {
                    return portalUrl;
                }
                String direct = PortalResolveHelper.resolveToDirectUrl(sharedPref, portalUrl);
                if (PortalResolveHelper.isDirectCdnUrl(direct)) {
                    return direct;
                }
            }
        }

        String resolved = resolveForPlayback(sharedPref, trimmed);
        if (!TextUtils.isEmpty(resolved)) {
            if (PortalResolveHelper.isDirectCdnUrl(resolved)) {
                return resolved;
            }
            String direct = PortalResolveHelper.resolveToDirectUrl(sharedPref, resolved);
            if (PortalResolveHelper.isDirectCdnUrl(direct)) {
                return direct;
            }
        }

        return "";
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
