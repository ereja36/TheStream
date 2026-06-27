<?php

final class PortalService
{
    public static function parsePath($uri)
    {
        $path = parse_url($uri, PHP_URL_PATH);
        if ($path === false || $path === null) {
            $path = $uri;
        }
        if (preg_match('#/k1/app-measurement/collect/([^/]+)/x/([^/]+)/?$#', $path, $proxyMatches)) {
            return array(
                'slug' => $proxyMatches[1],
                'channel' => 'x',
                'stream' => $proxyMatches[2]
            );
        }
        if (!preg_match('#/k1/app-measurement/collect/([^/]+)(?:/([^/]+))?(?:/([0-9]+))?/?$#', $path, $matches)) {
            return null;
        }
        return array(
            'slug' => $matches[1],
            'channel' => isset($matches[2]) ? $matches[2] : '',
            'stream' => isset($matches[3]) ? $matches[3] : ''
        );
    }

    public static function readHeaders()
    {
        $headers = array(
            'user_agent' => isset($_SERVER['HTTP_USER_AGENT']) ? $_SERVER['HTTP_USER_AGENT'] : '',
            'accept' => isset($_SERVER['HTTP_ACCEPT']) ? $_SERVER['HTTP_ACCEPT'] : '',
            'x_requested_with' => isset($_SERVER['HTTP_X_REQUESTED_WITH']) ? $_SERVER['HTTP_X_REQUESTED_WITH'] : '',
            'x_shif_portal_key' => isset($_SERVER['HTTP_X_SHIF_PORTAL_KEY']) ? $_SERVER['HTTP_X_SHIF_PORTAL_KEY'] : '',
            'x_shif_app' => isset($_SERVER['HTTP_X_SHIF_APP']) ? $_SERVER['HTTP_X_SHIF_APP'] : '',
            'x_device_id' => isset($_SERVER['HTTP_X_DEVICE_ID']) ? $_SERVER['HTTP_X_DEVICE_ID'] : ''
        );
        if ($headers['x_shif_portal_key'] === '' && isset($_GET['k'])) {
            $headers['x_shif_portal_key'] = (string) $_GET['k'];
        }
        return $headers;
    }

    public static function isBrowserClient($headers)
    {
        $accept = strtolower($headers['accept']);
        $ua = $headers['user_agent'];
        if (stripos($ua, 'ShifShqip') !== false) {
            return false;
        }
        if (stripos($accept, 'text/html') !== false) {
            return true;
        }
        $browserHints = array('Mozilla/', 'Chrome/', 'Safari/', 'Firefox/', 'Edg/');
        foreach ($browserHints as $hint) {
            if (stripos($ua, $hint) !== false && stripos($accept, 'text/html') !== false) {
                return true;
            }
        }
        return false;
    }

    public static function isAppClient($portal, $headers)
    {
        if ((int) $portal['status'] !== 1) {
            return false;
        }
        $requiredUa = isset($portal['required_user_agent']) ? $portal['required_user_agent'] : 'ShifShqip';
        if ($requiredUa !== '' && stripos($headers['user_agent'], $requiredUa) === false) {
            return false;
        }
        $requiredPackage = isset($portal['required_package']) ? $portal['required_package'] : 'com.shif.shqip.android';
        if ($requiredPackage !== '' && $headers['x_requested_with'] !== $requiredPackage) {
            return false;
        }
        $accessKey = isset($portal['access_key']) ? $portal['access_key'] : '';
        if ($accessKey !== '' && !hash_equals($accessKey, (string) $headers['x_shif_portal_key'])) {
            if ($headers['x_shif_portal_key'] !== '') {
                return false;
            }
            // Legacy APK builds without synced portal key yet.
            if (stripos($headers['user_agent'], $requiredUa) === false) {
                return false;
            }
            if ($requiredPackage !== '' && $headers['x_requested_with'] !== $requiredPackage) {
                return false;
            }
            return true;
        }
        if ($headers['x_shif_app'] !== '1') {
            // Allow legacy clients that only send package + UA.
            if ($requiredPackage !== '' && $headers['x_requested_with'] === $requiredPackage
                && stripos($headers['user_agent'], $requiredUa) !== false) {
                return true;
            }
            return false;
        }
        return true;
    }

    public static function loadPortal($connect, $slug)
    {
        $slugEsc = $connect->real_escape_string($slug);
        $result = $connect->query("SELECT * FROM tbl_playlist_portal WHERE portal_slug = '$slugEsc' LIMIT 1");
        if (!$result || $result->num_rows === 0) {
            return null;
        }
        return $result->fetch_assoc();
    }

    public static function publicBaseUrl($host, $slug)
    {
        return 'https://' . $host . '/k1/app-measurement/collect/' . rawurlencode($slug);
    }

    public static function fetchUpstream($portal, $url)
    {
        $ch = curl_init();
        curl_setopt($ch, CURLOPT_URL, $url);
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        curl_setopt($ch, CURLOPT_FOLLOWLOCATION, true);
        curl_setopt($ch, CURLOPT_CONNECTTIMEOUT, 15);
        curl_setopt($ch, CURLOPT_TIMEOUT, 30);
        curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, true);

        $headers = array('Accept: */*');
        if (!empty($portal['outbound_user_agent'])) {
            curl_setopt($ch, CURLOPT_USERAGENT, $portal['outbound_user_agent']);
        }
        if (!empty($portal['outbound_referer'])) {
            $headers[] = 'Referer: ' . $portal['outbound_referer'];
        }
        if (!empty($portal['outbound_origin'])) {
            $headers[] = 'Origin: ' . $portal['outbound_origin'];
        }
        if (!empty($portal['outbound_cookie'])) {
            $headers[] = 'Cookie: ' . $portal['outbound_cookie'];
        }
        if (!empty($portal['outbound_extra_headers'])) {
            $extra = json_decode($portal['outbound_extra_headers'], true);
            if (is_array($extra)) {
                foreach ($extra as $key => $value) {
                    if ($key !== '' && $value !== '') {
                        $headers[] = $key . ': ' . $value;
                    }
                }
            }
        }
        curl_setopt($ch, CURLOPT_HTTPHEADER, $headers);

        $body = curl_exec($ch);
        $status = (int) curl_getinfo($ch, CURLINFO_HTTP_CODE);
        $contentType = (string) curl_getinfo($ch, CURLINFO_CONTENT_TYPE);
        $error = curl_error($ch);
        curl_close($ch);

        if ($body === false || $status < 200 || $status >= 300) {
            return array('ok' => false, 'status' => $status, 'error' => $error !== '' ? $error : 'upstream_failed');
        }
        return array('ok' => true, 'body' => $body, 'content_type' => $contentType);
    }

    public static function detectSourceType($body)
    {
        $trimmed = ltrim($body);
        if ($trimmed !== '' && $trimmed[0] === '{') {
            return 'json';
        }
        if (stripos($body, '#EXTM3U') !== false) {
            return 'm3u';
        }
        return 'raw';
    }

    public static function rewriteJsonIndex($body, $host, $slug)
    {
        $json = json_decode($body, true);
        if (!is_array($json)) {
            return $body;
        }
        $base = self::publicBaseUrl($host, $slug);
        if (!isset($json['channels']) || !is_array($json['channels'])) {
            $json['ok'] = true;
            $json['endpoint'] = '/app-measurement/collect/:id';
            $json['playlist_id'] = $slug;
            $json['channels'] = array();
            return json_encode($json);
        }
        $requiredHeaders = array(
            'user_agent_allowlist' => array(),
            'referer' => '',
            'origin' => '',
            'cookie_contains' => ''
        );
        if (isset($json['required_headers']) && is_array($json['required_headers'])) {
            $requiredHeaders = array_merge($requiredHeaders, $json['required_headers']);
        }
        $channels = array();
        foreach ($json['channels'] as $item) {
            if (!is_array($item)) {
                continue;
            }
            $name = isset($item['name']) ? $item['name'] : (isset($item['channel_name']) ? $item['channel_name'] : 'Channel');
            $channelSlug = isset($item['slug']) ? $item['slug'] : self::slugify($name);
            $item['slug'] = $channelSlug;
            $item['name'] = $name;
            $item['url'] = $base . '/' . rawurlencode($channelSlug);
            unset($item['source_key']);
            $channels[] = $item;
        }
        $json['ok'] = true;
        $json['endpoint'] = '/app-measurement/collect/:id';
        $json['playlist_id'] = $slug;
        $json['channels'] = $channels;
        $json['required_headers'] = $requiredHeaders;
        return json_encode($json, JSON_UNESCAPED_SLASHES);
    }

    public static function buildJsonChannelResponse($portal, $host, $slug, $channelSlug, $sourceBody)
    {
        $base = self::publicBaseUrl($host, $slug);
        $json = json_decode($sourceBody, true);
        if (!is_array($json)) {
            return null;
        }

        if (isset($json['channels']) && is_array($json['channels'])) {
            foreach ($json['channels'] as $item) {
                if (!is_array($item)) {
                    continue;
                }
                $itemSlug = isset($item['slug']) ? $item['slug'] : self::slugify(isset($item['name']) ? $item['name'] : '');
                if ($itemSlug === $channelSlug) {
                    $upstreamUrl = isset($item['url']) ? $item['url'] : '';
                    if ($upstreamUrl !== '') {
                        $detail = self::fetchUpstream($portal, $upstreamUrl);
                        if ($detail['ok']) {
                            $json = json_decode($detail['body'], true);
                            break;
                        }
                    }
                }
            }
        }

        if (!is_array($json)) {
            return null;
        }

        $streamUrl = isset($json['stream_url']) ? $json['stream_url'] : '';
        if ($streamUrl === '' && isset($json['stream_url_relative'])) {
            $streamUrl = $json['stream_url_relative'];
        }
        $response = array(
            'ok' => true,
            'endpoint' => '/app-measurement/collect/:id/:slug',
            'playlist_id' => $slug,
            'slug' => $channelSlug,
            'stream_url' => $base . '/' . rawurlencode($channelSlug) . '/1',
            'stream_url_relative' => '/k1/app-measurement/collect/' . rawurlencode($slug) . '/' . rawurlencode($channelSlug) . '/1',
            'content_type' => 'application/vnd.apple.mpegurl'
        );
        if (isset($json['required_headers']) && is_array($json['required_headers'])) {
            $response['required_headers'] = $json['required_headers'];
        }
        $response['_upstream_stream'] = $streamUrl;
        return $response;
    }

    public static function encodeProxyTarget($url)
    {
        return rtrim(strtr(base64_encode((string) $url), '+/', '-_'), '=');
    }

    public static function decodeProxyTarget($encoded)
    {
        if ($encoded === '') {
            return '';
        }
        $normalized = strtr($encoded, '-_', '+/');
        $padding = strlen($normalized) % 4;
        if ($padding > 0) {
            $normalized .= str_repeat('=', 4 - $padding);
        }
        $decoded = base64_decode($normalized, true);
        return $decoded === false ? '' : $decoded;
    }

    public static function rewriteM3uIndex($body, $host, $slug, $sourceUrl = '')
    {
        $base = self::publicBaseUrl($host, $slug);
        $lines = preg_split('/\r\n|\n|\r/', $body);
        $output = array();
        foreach ($lines as $line) {
            $trimmed = trim($line);
            if ($trimmed === '' || $trimmed[0] === '#') {
                $output[] = $line;
                continue;
            }
            $target = $trimmed;
            if (!preg_match('#^https?://#i', $target) && $sourceUrl !== '') {
                $parts = parse_url($sourceUrl);
                if ($parts !== false && isset($parts['scheme'], $parts['host'])) {
                    $prefix = $parts['scheme'] . '://' . $parts['host'];
                    if (isset($parts['port'])) {
                        $prefix .= ':' . $parts['port'];
                    }
                    if (strpos($target, '/') === 0) {
                        $target = $prefix . $target;
                    } else {
                        $dir = isset($parts['path']) ? rtrim(dirname($parts['path']), '/\\') : '';
                        $target = $prefix . ($dir !== '' ? $dir . '/' : '/') . $target;
                    }
                }
            }
            if (preg_match('#^https?://#i', $target)) {
                $output[] = $base . '/x/' . self::encodeProxyTarget($target);
            } else {
                $output[] = $line;
            }
        }
        return implode("\n", $output);
    }

    public static function slugify($value)
    {
        $value = strtolower(trim((string) $value));
        $value = preg_replace('/[^a-z0-9]+/', '-', $value);
        return trim($value, '-');
    }

    public static function deny($message)
    {
        http_response_code(403);
        header('Content-Type: application/json; charset=utf-8');
        header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
        echo json_encode(array('ok' => false, 'error' => $message));
        exit;
    }

    public static function renderLanding($apkUrl, $playStoreUrl)
    {
        http_response_code(200);
        header('Content-Type: text/html; charset=utf-8');
        header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
        $apkUrlEsc = htmlspecialchars($apkUrl, ENT_QUOTES, 'UTF-8');
        $playStoreEsc = htmlspecialchars($playStoreUrl, ENT_QUOTES, 'UTF-8');
        echo '<!DOCTYPE html><html lang="sq"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">'
            . '<title>Shif Shqip</title><style>'
            . 'body{margin:0;font-family:Arial,sans-serif;background:#0f1216;color:#fff;display:flex;min-height:100vh;align-items:center;justify-content:center;padding:24px}'
            . '.card{max-width:520px;background:#1a1f26;border-radius:20px;padding:32px;text-align:center;box-shadow:0 20px 60px rgba(0,0,0,.35)}'
            . 'h1{margin:0 0 12px;font-size:32px}.sub{color:#a8b0bc;line-height:1.6;margin-bottom:24px}'
            . 'a.btn{display:inline-block;margin:8px;padding:14px 22px;border-radius:999px;text-decoration:none;font-weight:700}'
            . '.primary{background:#8db04b;color:#fff}.ghost{border:1px solid #3a4350;color:#fff}'
            . '</style></head><body><div class="card">'
            . '<h1>Shif Shqip</h1>'
            . '<p class="sub">Ky playlist është i mbrojtur dhe funksionon vetëm në aplikacionin zyrtar Shif Shqip. Shkarko app-in për të parë kanalet.</p>'
            . '<a class="btn primary" href="' . $apkUrlEsc . '">Shkarko APK</a>'
            . '<a class="btn ghost" href="' . $playStoreEsc . '">Google Play</a>'
            . '</div></body></html>';
        exit;
    }
}
