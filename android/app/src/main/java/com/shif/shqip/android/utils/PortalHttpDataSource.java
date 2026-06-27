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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("deprecation")
public final class PortalHttpDataSource implements HttpDataSource {

    private final HttpDataSource delegate;
    private final Context context;
    private final SharedPref sharedPref;

    private PortalHttpDataSource(HttpDataSource delegate, Context context, SharedPref sharedPref) {
        this.delegate = delegate;
        this.context = context.getApplicationContext();
        this.sharedPref = sharedPref;
    }

    @Override
    public long open(DataSpec dataSpec) throws HttpDataSourceException {
        DataSpec requestSpec = dataSpec;
        String requestUrl = dataSpec.uri != null ? dataSpec.uri.toString() : "";
        if (PlaylistPortalHelper.isPortalUrl(requestUrl) && sharedPref != null) {
            String deviceId = DeviceApiHelper.getLocalDeviceId(context);
            Map<String, String> headers = new HashMap<>();
            if (dataSpec.httpRequestHeaders != null) {
                headers.putAll(dataSpec.httpRequestHeaders);
            }
            headers.put("Accept", "*/*");
            headers.put("User-Agent", "ShifShqip/" + BuildConfig.VERSION_NAME + " (Android)");
            headers.put("X-Shif-App", "1");
            headers.put("X-Requested-With", BuildConfig.APPLICATION_ID);
            if (!TextUtils.isEmpty(deviceId)) {
                headers.put("X-Device-ID", deviceId);
            }
            headers.putAll(PlaylistPortalHelper.buildPortalHeaders(
                    requestUrl,
                    "GET",
                    deviceId,
                    sharedPref
            ));
            requestSpec = dataSpec.withRequestHeaders(headers);
        }
        return delegate.open(requestSpec);
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
        private final String userAgent;

        public Factory(Context context, SharedPref sharedPref, String userAgent) {
            this.context = context.getApplicationContext();
            this.sharedPref = sharedPref;
            this.userAgent = userAgent;
        }

        @Override
        public HttpDataSource createDataSource() {
            DefaultHttpDataSource.Factory delegateFactory = new DefaultHttpDataSource.Factory()
                    .setUserAgent(userAgent)
                    .setAllowCrossProtocolRedirects(true);
            return new PortalHttpDataSource(delegateFactory.createDataSource(), context, sharedPref);
        }

        @Override
        public HttpDataSource.Factory setDefaultRequestProperties(Map<String, String> defaultRequestProperties) {
            return this;
        }
    }
}
