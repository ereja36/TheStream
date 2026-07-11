<?php

require_once __DIR__ . '/config.php';
require_once __DIR__ . '/lib/PortalService.php';

header('X-Content-Type-Options: nosniff');
header('Referrer-Policy: no-referrer');

$headers = PortalService::readHeaders();
$path = PortalService::parsePath($_SERVER['REQUEST_URI']);

if ($path === null) {
    PortalService::renderLanding($apk_download_url, $play_store_url);
}

$requestPath = isset($_SERVER['REQUEST_URI']) ? parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH) : '';
if ($requestPath === false || $requestPath === null) {
    $requestPath = '';
}
$isPublicEntry = ($requestPath === '/' || $requestPath === '');

if ($isPublicEntry) {
    $portal = PortalService::loadActivePortal($connect);
    if ($portal === null) {
        PortalService::renderLanding($apk_download_url, $play_store_url);
    }
    $path = array(
        'slug' => $portal['portal_slug'],
        'channel' => '',
        'stream' => ''
    );
} else {
    if ($path === null) {
        PortalService::renderLanding($apk_download_url, $play_store_url);
    }
    $portal = PortalService::loadPortal($connect, $path['slug']);
    if ($portal === null) {
        PortalService::renderLanding($apk_download_url, $play_store_url);
    }
}

if (!PortalService::isAppClient($portal, $headers, $requestPath, $connect)) {
    PortalService::renderLanding($apk_download_url, $play_store_url);
}

if ((int) $portal['status'] !== 1 || trim($portal['source_url']) === '') {
    PortalService::deny('playlist_unavailable');
}

$sourceUrl = trim($portal['source_url']);
$host = $portal_public_host;
$accessKey = isset($portal['access_key']) ? (string) $portal['access_key'] : '';

if ($path['channel'] === '') {
    $upstream = PortalService::fetchUpstream($portal, $sourceUrl);
    if (!$upstream['ok']) {
        PortalService::deny('upstream_error');
    }
    $type = PortalService::detectSourceType($upstream['body']);
    header('Cache-Control: private, no-store, no-cache, must-revalidate, max-age=0');
    if ($type === 'json') {
        header('Content-Type: application/json; charset=utf-8');
        echo PortalService::rewriteJsonIndex($upstream['body'], $host, $path['slug']);
        exit;
    }
    $playlistBody = PortalService::rewriteM3uIndex($upstream['body'], $host, $path['slug'], $sourceUrl, $portal);
    $deviceId = trim((string) $headers['x_device_id']);
    $sessionToken = trim((string) $headers['x_shif_session']);
    if ($deviceId !== '' && $sessionToken !== '') {
        $encrypted = PortalService::encryptForDevice($playlistBody, $deviceId, $sessionToken);
        if ($encrypted !== '') {
            header('Content-Type: application/octet-stream');
            header('X-Shif-Payload: enc');
            echo $encrypted;
            exit;
        }
    }
    header('Content-Type: application/vnd.apple.mpegurl; charset=utf-8');
    echo $playlistBody;
    exit;
}

if ($path['channel'] === 'u' && $path['stream'] !== '') {
    $playUrl = PortalService::resolveK1ChannelPortalUrl($portal, $host, $path['stream'], $accessKey);
    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: private, no-store, no-cache, must-revalidate, max-age=0');
    if ($playUrl === '') {
        echo json_encode(array('ok' => false, 'error' => 'channel_not_found'));
        exit;
    }
    if (!PortalService::isDirectCdnUrl($playUrl)) {
        echo json_encode(array('ok' => false, 'error' => 'resolve_not_direct'));
        exit;
    }
    echo json_encode(array(
        'ok' => true,
        'mode' => 'direct',
        'url' => $playUrl
    ), JSON_UNESCAPED_SLASHES);
    exit;
}

if ($path['channel'] === 'r' && $path['stream'] !== '') {
    $entryUrl = PortalService::unsealUrl($path['stream'], $accessKey);
    if ($entryUrl === '' || !preg_match('#^https?://#i', $entryUrl)) {
        PortalService::deny('invalid_entry');
    }

    // Control response: the app asks (X-Shif-Resolve: direct) for a playable URL.
    if (PortalService::wantsDirectResolve($headers)) {
        $finalUrl = PortalService::resolveToDirectCdnUrl($portal, $entryUrl);
        header('Content-Type: application/json; charset=utf-8');
        header('Cache-Control: private, no-store, no-cache, must-revalidate, max-age=0');
        if ($finalUrl !== '' && PortalService::isDirectCdnUrl($finalUrl)) {
            // Direct CDN channel (e.g. redirect-CDN) — client streams straight from the CDN.
            echo json_encode(array(
                'ok' => true,
                'mode' => 'direct',
                'url' => $finalUrl
            ), JSON_UNESCAPED_SLASHES);
            exit;
        }
        if (PortalService::isOriginStreamUrl($entryUrl)) {
            // Proxied-manifest channel (api_/sport_/custom_tv) — client streams the
            // manifest through this same sealed URL; segments 302 straight to origin.
            echo json_encode(array(
                'ok' => true,
                'mode' => 'manifest',
                'url' => PortalService::publicBaseUrl($host, $path['slug']) . '/r/' . $path['stream']
            ), JSON_UNESCAPED_SLASHES);
            exit;
        }
        echo json_encode(array('ok' => false, 'error' => 'resolve_failed'));
        exit;
    }

    // Playback request (ExoPlayer GET). serveProxiedManifest does one non-following
    // fetch: a 302 (redirect-CDN channel) is forwarded to the CDN, a 200 manifest is
    // proxied with segments sealed as lightweight /s/ redirects to the origin.
    if (!PortalService::serveProxiedManifest($portal, $host, $path['slug'], $entryUrl)) {
        PortalService::deny('stream_error');
    }
    exit;
}

if ($path['channel'] === 's' && $path['stream'] !== '') {
    // Sealed segment/stream URL — a lightweight 302 straight to the origin/CDN so
    // the heavy bytes never pass through the portal.
    $proxyTarget = PortalService::unsealUrl($path['stream'], $accessKey);
    if ($proxyTarget === '' || !preg_match('#^https?://#i', $proxyTarget)) {
        PortalService::deny('invalid_proxy');
    }
    if (!PortalService::redirectToStreamUrl($proxyTarget)) {
        PortalService::deny('resolve_failed');
    }
    exit;
}

if ($path['channel'] === 'x' && $path['stream'] !== '') {
    $proxyTarget = PortalService::decodeProxyTarget($path['stream']);
    if ($proxyTarget === '' || !preg_match('#^https?://#i', $proxyTarget)) {
        PortalService::deny('invalid_proxy');
    }
    $finalUrl = PortalService::resolvePlaybackRedirectUrl($portal, $proxyTarget);
    if ($finalUrl === '' || !PortalService::redirectToStreamUrl($finalUrl)) {
        PortalService::deny('resolve_failed');
    }
    exit;
}

$indexUpstream = PortalService::fetchUpstream($portal, $sourceUrl);
if (!$indexUpstream['ok']) {
    PortalService::deny('upstream_error');
}

$m3uEntry = PortalService::findM3uEntry($indexUpstream['body'], $sourceUrl, $path['channel']);
if ($m3uEntry !== null) {
    if ($path['stream'] === '') {
        $base = PortalService::publicBaseUrl($host, $path['slug']);
        header('Content-Type: application/json; charset=utf-8');
        header('Cache-Control: private, no-store, no-cache, must-revalidate, max-age=0');
        echo json_encode(array(
            'ok' => true,
            'endpoint' => '/app-measurement/collect/:id/:slug',
            'playlist_id' => $path['slug'],
            'slug' => (string) $m3uEntry['index'],
            'stream_url' => $base . '/' . $m3uEntry['index'] . '/1',
            'stream_url_relative' => '/k1/app-measurement/collect/' . rawurlencode($path['slug']) . '/' . $m3uEntry['index'] . '/1',
            'content_type' => 'application/vnd.apple.mpegurl'
        ), JSON_UNESCAPED_SLASHES);
        exit;
    }

    $upstreamStream = PortalService::resolvePlaybackRedirectUrl(
        $portal,
        PortalService::resolveStreamUrl($portal, $m3uEntry['url'], $sourceUrl)
    );
    if ($upstreamStream === '') {
        PortalService::deny('stream_not_found');
    }
    if (!PortalService::redirectToStreamUrl($upstreamStream)) {
        PortalService::deny('stream_error');
    }
    exit;
}

$channelResponse = PortalService::buildJsonChannelResponse(
    $portal,
    $host,
    $path['slug'],
    $path['channel'],
    $indexUpstream['body']
);

if ($channelResponse === null) {
    PortalService::deny('channel_not_found');
}

if ($path['stream'] !== '') {
    $upstreamStream = isset($channelResponse['_upstream_stream']) ? $channelResponse['_upstream_stream'] : '';
    unset($channelResponse['_upstream_stream']);
    if ($upstreamStream === '') {
        PortalService::deny('stream_not_found');
    }
    $upstreamStream = PortalService::resolvePlaybackRedirectUrl(
        $portal,
        PortalService::resolveStreamUrl($portal, $upstreamStream, $sourceUrl)
    );
    if ($upstreamStream === '') {
        PortalService::deny('stream_not_found');
    }
    if (!PortalService::redirectToStreamUrl($upstreamStream)) {
        PortalService::deny('stream_error');
    }
    exit;
}

unset($channelResponse['_upstream_stream']);
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: private, no-store, no-cache, must-revalidate, max-age=0');
echo json_encode($channelResponse, JSON_UNESCAPED_SLASHES);
exit;
