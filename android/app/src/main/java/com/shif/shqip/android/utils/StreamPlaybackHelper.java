package com.shif.shqip.android.utils;

import android.content.Context;
import android.text.TextUtils;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.shif.shqip.android.databases.prefs.SharedPref;

public final class StreamPlaybackHelper {

    public static final long STREAM_REFRESH_MS = 20_000L;
    public static final int AUTO_RETRY_MAX = 8;
    public static final int CONNECT_TIMEOUT_MS = 30_000;
    public static final int READ_TIMEOUT_MS = 90_000;

    private StreamPlaybackHelper() {
    }

    public static LoadControl createLoadControl() {
        return new DefaultLoadControl.Builder()
                .setBufferDurationsMs(30_000, 120_000, 2_500, 5_000)
                .build();
    }

    public static boolean needsPeriodicRefresh(String sourceUrl) {
        if (TextUtils.isEmpty(sourceUrl)) {
            return false;
        }
        return PlaylistPortalHelper.isPortalResolveUrl(sourceUrl)
                || PlaylistPortalHelper.isUpstreamStreamUrl(sourceUrl);
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

    public static HttpDataSource.Factory createHttpDataSourceFactory(
            Context context,
            SharedPref sharedPref,
            String requestUrl,
            String channelUserAgent
    ) {
        boolean portalRequest = PlaylistPortalHelper.isPortalUrl(requestUrl);
        String userAgent = portalRequest
                ? DeviceApiHelper.buildAppUserAgent()
                : PlaylistGuardHelper.resolveUserAgent(
                PlaylistGuardHelper.resolveRule(sharedPref, requestUrl),
                channelUserAgent
        );
        if (portalRequest && context != null) {
            return new PortalHttpDataSource.Factory(context, sharedPref, userAgent);
        }
        DefaultHttpDataSource.Factory factory = configureHttpFactory(new DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent));
        java.util.Map<String, String> headers = PlaylistGuardHelper.buildRequestHeaders(
                PlaylistGuardHelper.resolveRule(sharedPref, requestUrl)
        );
        if (!headers.isEmpty()) {
            factory.setDefaultRequestProperties(headers);
        }
        return factory;
    }
}
