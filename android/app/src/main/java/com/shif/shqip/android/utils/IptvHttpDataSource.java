package com.shif.shqip.android.utils;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.shif.shqip.android.BuildConfig;
import com.shif.shqip.android.databases.prefs.SharedPref;
import com.shif.shqip.android.models.PlaylistGuardRule;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP data source for portal-proxied IPTV. All stream traffic stays on
 * playlist.shifshqip.com — upstream Referer/UA are applied server-side only.
 */
@SuppressWarnings("deprecation")
public final class IptvHttpDataSource implements HttpDataSource {

    private final HttpDataSource delegate;
    private final Context context;
    private final SharedPref sharedPref;
    private final String channelUserAgent;

    private IptvHttpDataSource(
            HttpDataSource delegate,
            Context context,
            SharedPref sharedPref,
            String channelUserAgent
    ) {
        this.delegate = delegate;
        this.context = context.getApplicationContext();
        this.sharedPref = sharedPref;
        this.channelUserAgent = channelUserAgent;
    }

    @Override
    public long open(DataSpec dataSpec) throws HttpDataSourceException {
        String requestUrl = dataSpec.uri != null ? dataSpec.uri.toString() : "";
        Map<String, String> headers = new HashMap<>();
        if (dataSpec.httpRequestHeaders != null) {
            headers.putAll(dataSpec.httpRequestHeaders);
        }
        headers.put("Accept", "*/*");
        headers.put("X-Shif-App", "1");
        headers.put("X-Requested-With", BuildConfig.APPLICATION_ID);
        headers.put("User-Agent", "ShifShqip/" + BuildConfig.VERSION_NAME + " (Android)");

        if (sharedPref != null && sharedPref.isPortalSessionActive()) {
            String deviceId = DeviceApiHelper.getLocalDeviceId(context);
            if (!TextUtils.isEmpty(deviceId)) {
                headers.put("X-Device-ID", deviceId);
            }
            if (PlaylistPortalHelper.isPortalUrl(requestUrl)) {
                headers.putAll(PlaylistPortalHelper.buildPortalHeaders(
                        requestUrl,
                        "GET",
                        deviceId,
                        sharedPref
                ));
                if (PlaylistPortalHelper.isPortalResolveUrl(requestUrl)
                        || isPortalProxyPath(requestUrl)) {
                    headers.put("X-Shif-Stream-Proxy", "1");
                }
            }
            return delegate.open(dataSpec.withRequestHeaders(headers));
        }

        if (PlaylistPortalHelper.isPortalUrl(requestUrl)) {
            String deviceId = DeviceApiHelper.getLocalDeviceId(context);
            if (!TextUtils.isEmpty(deviceId)) {
                headers.put("X-Device-ID", deviceId);
            }
            headers.putAll(PlaylistPortalHelper.buildPortalHeaders(
                    requestUrl,
                    "GET",
                    deviceId,
                    sharedPref
            ));
        } else {
            PlaylistGuardRule rule = PlaylistGuardHelper.resolveRule(sharedPref, requestUrl);
            String userAgent = PlaylistGuardHelper.resolveUserAgent(rule, channelUserAgent);
            headers.put("User-Agent", userAgent);
            Map<String, String> ruleHeaders = PlaylistGuardHelper.buildRequestHeaders(rule);
            if (ruleHeaders != null) {
                headers.putAll(ruleHeaders);
            }
        }

        return delegate.open(dataSpec.withRequestHeaders(headers));
    }

    private static boolean isPortalProxyPath(String requestUrl) {
        if (TextUtils.isEmpty(requestUrl)) {
            return false;
        }
        String lower = requestUrl.toLowerCase();
        return lower.contains("/collect/") && (lower.contains("/s/") || lower.contains("/x/"));
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws HttpDataSourceException {
        return delegate.read(buffer, offset, length);
    }

    @Override
    public Uri getUri() {
        return delegate.getUri();
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return delegate.getResponseHeaders();
    }

    @Override
    public void close() throws HttpDataSourceException {
        delegate.close();
    }

    @Override
    public void addTransferListener(TransferListener transferListener) {
        delegate.addTransferListener(transferListener);
    }

    @Override
    public void setRequestProperty(String name, String value) {
        delegate.setRequestProperty(name, value);
    }

    @Override
    public void clearRequestProperty(String name) {
        delegate.clearRequestProperty(name);
    }

    @Override
    public void clearAllRequestProperties() {
        delegate.clearAllRequestProperties();
    }

    @Override
    public int getResponseCode() {
        return delegate.getResponseCode();
    }

    public static final class Factory implements HttpDataSource.Factory {

        private final Context context;
        private final SharedPref sharedPref;
        private final String channelUserAgent;

        public Factory(Context context, SharedPref sharedPref, String channelUserAgent) {
            this.context = context.getApplicationContext();
            this.sharedPref = sharedPref;
            this.channelUserAgent = channelUserAgent;
        }

        @Override
        public HttpDataSource createDataSource() {
            DefaultHttpDataSource.Factory delegateFactory = StreamPlaybackHelper.configureHttpFactory(
                    new DefaultHttpDataSource.Factory());
            return new IptvHttpDataSource(
                    delegateFactory.createDataSource(),
                    context,
                    sharedPref,
                    channelUserAgent
            );
        }

        @Override
        public HttpDataSource.Factory setDefaultRequestProperties(Map<String, String> defaultRequestProperties) {
            return this;
        }
    }
}
