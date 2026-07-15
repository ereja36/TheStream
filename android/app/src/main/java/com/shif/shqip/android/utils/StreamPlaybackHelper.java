package com.shif.shqip.android.utils;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.shif.shqip.android.databases.prefs.SharedPref;
import android.text.TextUtils;

import java.util.Locale;

/**
 * IPTV-first playback: any normal stream URL plays as-is (VLC-style).
 * Only sealed portal /r/ (and a few proxy endpoints) need server resolve.
 */
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

    /**
     * True for normal IPTV stream URLs that ExoPlayer can open without portal resolve.
     * External playlists, /k1/playlist entries, CDN links, etc.
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
        // Sealed portal tokens must be resolved first.
        if (PlaylistPortalHelper.isPortalResolveUrl(trimmed)) {
            return false;
        }
        if (lower.contains("playlist.shifshqip.com")) {
            return false;
        }
        // XUI CDN play/auth/hls — play as-is (no portal proxy after resolve).
        if (PortalResolveHelper.isDirectCdnUrl(trimmed)
                && lower.contains("k1.shifshqip.com")
                && (lower.contains("/play/") || lower.contains("/auth/") || lower.contains("/hls/"))) {
            return true;
        }
        if (lower.contains("shifshqip.com")
                && !lower.contains("playlist.shifshqip.com")) {
            if (lower.contains("/k1/channel-proxy/")
                    || lower.contains("/k1/proxy/")
                    || lower.contains("/app-measurement/collect/")) {
                return false;
            }
        }
        return true;
    }

    public static boolean canPlayDirectly(SharedPref sharedPref, String sourceUrl) {
        // Portal sealed URLs need a background resolve thread.
        if (PlaylistPortalHelper.isPortalResolveUrl(sourceUrl)) {
            return false;
        }
        return isRawPlaylistStreamUrl(sourceUrl) || PortalResolveHelper.isPlayableStreamUrl(sourceUrl);
    }

    public static boolean isPlayableUrl(String url) {
        return isRawPlaylistStreamUrl(url) || PortalResolveHelper.isPlayableStreamUrl(url);
    }

    /**
     * Returns a play URL. External IPTV URLs always pass through.
     * Portal /r/ URLs are resolved once to CDN/all2.
     */
    public static String resolvePlaybackUrl(SharedPref sharedPref, String sourceUrl, String k1Slug) {
        if (TextUtils.isEmpty(sourceUrl)) {
            return "";
        }
        String trimmed = sourceUrl.trim();

        // 1) Normal IPTV / raw playlist streams — play as-is (external + most k1 entries).
        if (isRawPlaylistStreamUrl(trimmed)) {
            return trimmed;
        }

        // 2) Already a playable all2/CDN URL (never sealed portal).
        if (PortalResolveHelper.isPlayableStreamUrl(trimmed)
                && !PlaylistPortalHelper.isPortalResolveUrl(trimmed)) {
            return trimmed;
        }

        // 3) Sealed portal /r/ → resolve to real stream.
        if (PlaylistPortalHelper.isPortalResolveUrl(trimmed)) {
            String direct = PortalResolveHelper.resolveToDirectUrl(sharedPref, trimmed);
            if (!TextUtils.isEmpty(direct)) {
                return direct;
            }
            return "";
        }

        // 4) Optional k1 slug helper via portal /u/ API.
        if (!TextUtils.isEmpty(k1Slug)
                && K1ChannelUrlHelper.looksLikeK1Slug(k1Slug)
                && sharedPref != null) {
            String portalUrl = K1ChannelUrlHelper.resolvePlayableUrl(sharedPref, k1Slug);
            if (!TextUtils.isEmpty(portalUrl)) {
                if (isRawPlaylistStreamUrl(portalUrl) || PortalResolveHelper.isPlayableStreamUrl(portalUrl)) {
                    return portalUrl;
                }
                String direct = PortalResolveHelper.resolveToDirectUrl(sharedPref, portalUrl);
                if (!TextUtils.isEmpty(direct)) {
                    return direct;
                }
            }
        }

        // 5) Proxy endpoints that need unwrap — try resolve, else original.
        if (K1PlaybackHelper.isK1ProxyUrl(trimmed)) {
            String direct = PortalResolveHelper.resolveToDirectUrl(sharedPref, trimmed);
            if (!TextUtils.isEmpty(direct)) {
                return direct;
            }
            return trimmed;
        }

        // IPTV player fallback: never block unknown http(s) stream URLs.
        String lower = trimmed.toLowerCase(Locale.US);
        if (lower.startsWith("http://") || lower.startsWith("https://")
                || lower.startsWith("rtsp://") || lower.startsWith("rtmp://")
                || lower.startsWith("udp://") || lower.startsWith("mms://")) {
            return trimmed;
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
