<?php

require_once __DIR__ . '/config.php';
require_once __DIR__ . '/lib/PortalService.php';

header('X-Content-Type-Options: nosniff');
header('Referrer-Policy: no-referrer');

$headers = PortalService::readHeaders();
$path = PortalService::parsePath($_SERVER['REQUEST_URI']);

if ($path === null) {
    if (PortalService::isBrowserClient($headers)) {
        PortalService::renderLanding($apk_download_url, $play_store_url);
    }
    PortalService::deny('invalid_endpoint');
}

$portal = PortalService::loadPortal($connect, $path['slug']);
if ($portal === null) {
    if (PortalService::isBrowserClient($headers)) {
        PortalService::renderLanding($apk_download_url, $play_store_url);
    }
    PortalService::deny('playlist_not_found');
}

if (!PortalService::isAppClient($portal, $headers)) {
    if (PortalService::isBrowserClient($headers)) {
        PortalService::renderLanding($apk_download_url, $play_store_url);
    }
    PortalService::deny('forbidden');
}

if ((int) $portal['status'] !== 1 || trim($portal['source_url']) === '') {
    PortalService::deny('playlist_unavailable');
}

$sourceUrl = trim($portal['source_url']);
$host = $portal_public_host;

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
    header('Content-Type: application/vnd.apple.mpegurl; charset=utf-8');
    echo PortalService::rewriteM3uIndex($upstream['body'], $host, $path['slug'], $sourceUrl);
    exit;
}

if ($path['channel'] === 'x' && $path['stream'] !== '') {
    $proxyTarget = PortalService::decodeProxyTarget($path['stream']);
    if ($proxyTarget === '' || !preg_match('#^https?://#i', $proxyTarget)) {
        PortalService::deny('invalid_proxy');
    }
    $proxyUpstream = PortalService::fetchUpstream($portal, $proxyTarget);
    if (!$proxyUpstream['ok']) {
        PortalService::deny('proxy_error');
    }
    header('Cache-Control: private, no-store, no-cache, must-revalidate, max-age=0');
    $proxyType = PortalService::detectSourceType($proxyUpstream['body']);
    if ($proxyType === 'json') {
        $proxyJson = json_decode($proxyUpstream['body'], true);
        if (is_array($proxyJson) && isset($proxyJson['stream_url']) && $proxyJson['stream_url'] !== '') {
            $proxyUpstream = PortalService::fetchUpstream($portal, $proxyJson['stream_url']);
            if (!$proxyUpstream['ok']) {
                PortalService::deny('proxy_error');
            }
        }
    }
    if (stripos($proxyUpstream['body'], '#EXTM3U') !== false) {
        header('Content-Type: application/vnd.apple.mpegurl; charset=utf-8');
    } else {
        header('Content-Type: application/octet-stream');
    }
    echo $proxyUpstream['body'];
    exit;
}

$indexUpstream = PortalService::fetchUpstream($portal, $sourceUrl);
if (!$indexUpstream['ok']) {
    PortalService::deny('upstream_error');
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
    if (strpos($upstreamStream, 'http') !== 0) {
        $parts = parse_url($sourceUrl);
        $scheme = isset($parts['scheme']) ? $parts['scheme'] : 'https';
        $hostPart = isset($parts['host']) ? $parts['host'] : '';
        $upstreamStream = $scheme . '://' . $hostPart . $upstreamStream;
    }
    $streamUpstream = PortalService::fetchUpstream($portal, $upstreamStream);
    if (!$streamUpstream['ok']) {
        PortalService::deny('stream_error');
    }
    header('Cache-Control: private, no-store, no-cache, must-revalidate, max-age=0');
    $streamType = PortalService::detectSourceType($streamUpstream['body']);
    if ($streamType === 'json') {
        $streamJson = json_decode($streamUpstream['body'], true);
        if (is_array($streamJson) && isset($streamJson['stream_url'])) {
            $streamUpstream = PortalService::fetchUpstream($portal, $streamJson['stream_url']);
            if (!$streamUpstream['ok']) {
                PortalService::deny('stream_error');
            }
        }
    }
    if (stripos($streamUpstream['body'], '#EXTM3U') !== false) {
        header('Content-Type: application/vnd.apple.mpegurl; charset=utf-8');
    } else {
        header('Content-Type: application/octet-stream');
    }
    echo $streamUpstream['body'];
    exit;
}

unset($channelResponse['_upstream_stream']);
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: private, no-store, no-cache, must-revalidate, max-age=0');
echo json_encode($channelResponse, JSON_UNESCAPED_SLASHES);
exit;
