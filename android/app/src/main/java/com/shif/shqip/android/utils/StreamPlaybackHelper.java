package com.shif.shqip.android.utils;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.shif.shqip.android.databases.prefs.SharedPref;
import android.text.TextUtils;

import java.util.Locale;

public final class StreamPlaybackHelper {

    public static final int AUTO_RETRY_MAX = 4;
    public static final int CONNECT_TIMEOUT_MS = 10_000;
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
     * VLC-style: any http/https/rtsp/rtmp URL from the playlist plays directly in ExoPlayer.
     * Only sealed portal /r/ URLs and our channel-proxy endpoints need server resolution.
     */
    public static boolean isRawPlaylistStreamUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        String trimmed = url.trim();
        String lower = trimmed.toLowerCase(Locale.US);
        if (!lower.startsWith("http://")
                && !lower.startsWith("https://")
                && !lower.startsWith("rtsp://")
                && !lower.startsWith("rtmp://")
                && !lower.startsWith("udp://")
                && !lower.startsWith("mms://")) {
            return false;
        }
        if (PlaylistPortalHelper.isPortalResolveUrl(trimmed)) {
            return false;
        }
        if (lower.contains("playlist.shifshqip.com")) {
            return false;
        }
        if (lower.contains("shifshqip.com")) {
            if (lower.contains("/k1/channel-proxy/")
                    || lower.contains("/k1/proxy/")
                    || lower.contains("/app-measurement/collect/")) {
                return false;
            }
        }
        return true;
    }

    public static boolean canPlayDirectly(SharedPref sharedPref, String sourceUrl) {
        return isRawPlaylistStreamUrl(sourceUrl) || PortalResolveHelper.isDirectCdnUrl(sourceUrl);
    }

    public static boolean isPlayableUrl(String url) {
        return isRawPlaylistStreamUrl(url) || PortalResolveHelper.isDirectCdnUrl(url);
    }

    /**
     * Returns a play URL: raw playlist URLs as-is (like VLC/MX Player), portal URLs resolved once to CDN.
     */
    public static String resolvePlaybackUrl(SharedPref sharedPref, String sourceUrl, String k1Slug) {
        if (TextUtils.isEmpty(sourceUrl)) {
            return "";
        }
        String trimmed = sourceUrl.trim();

        if (isRawPlaylistStreamUrl(trimmed)) {
            return trimmed;
        }

        if (PortalResolveHelper.isDirectCdnUrl(trimmed)) {
            return trimmed;
        }

        if (sharedPref != null && sharedPref.isPortalSessionActive()) {
            if (PlaylistPortalHelper.isPortalResolveUrl(trimmed)
                    || PlaylistPortalHelper.isUpstreamStreamUrl(trimmed)
                    || K1PlaybackHelper.isK1ProxyUrl(trimmed)) {
                String direct = PortalResolveHelper.resolveToDirectUrl(sharedPref, trimmed);
                if (!TextUtils.isEmpty(direct)) {
                    return direct;
                }
            }
        }

        if (!TextUtils.isEmpty(k1Slug)
                && K1ChannelUrlHelper.looksLikeK1Slug(k1Slug)
                && !PlaylistPortalHelper.isPortalResolveUrl(trimmed)) {
            String portalUrl = K1ChannelUrlHelper.resolvePlayableUrl(sharedPref, k1Slug);
            if (!TextUtils.isEmpty(portalUrl)) {
                if (isRawPlaylistStreamUrl(portalUrl) || PortalResolveHelper.isDirectCdnUrl(portalUrl)) {
                    return portalUrl;
                }
                String direct = PortalResolveHelper.resolveToDirectUrl(sharedPref, portalUrl);
                if (!TextUtils.isEmpty(direct)) {
                    return direct;
                }
            }
        }

        if (PlaylistPortalHelper.isPortalResolveUrl(trimmed)
                || K1PlaybackHelper.isK1ProxyUrl(trimmed)) {
            String direct = PortalResolveHelper.resolveToDirectUrl(sharedPref, trimmed);
            if (!TextUtils.isEmpty(direct)) {
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
