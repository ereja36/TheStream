<?php

final class PortalService
{
    private const SIGNATURE_MAX_AGE_SECONDS = 300;

    public static function parsePath($uri)
    {
        $path = parse_url($uri, PHP_URL_PATH);
        if ($path === false || $path === null) {
            $path = $uri;
        }
        if (preg_match('#/k1/app-measurement/collect/([^/]+)/u/([^/]+)/?$#', $path, $uMatches)) {
            return array(
                'slug' => $uMatches[1],
                'channel' => 'u',
                'stream' => $uMatches[2]
            );
        }
        if (preg_match('#/k1/app-measurement/collect/([^/]+)/r/([^/]+)/?$#', $path, $resolveMatches)) {
            return array(
                'slug' => $resolveMatches[1],
                'channel' => 'r',
                'stream' => $resolveMatches[2]
            );
        }
        if (preg_match('#/k1/app-measurement/collect/([^/]+)/s/([^/]+)/?$#', $path, $sealedMatches)) {
            return array(
                'slug' => $sealedMatches[1],
                'channel' => 's',
                'stream' => $sealedMatches[2]
            );
        }
        if (preg_match('#/k1/app-measurement/collect/([^/]+)/x/([^/]+)/?$#', $path, $proxyMatches)) {
            return array(
                'slug' => $proxyMatches[1],
                'channel' => 'x',
                'stream' => $proxyMatches[2]
            );
        }
        if (preg_match('#/k1/app-measurement/collect/([^/]+)(?:/([^/]+))?(?:/([0-9]+))?/?$#', $path, $matches)) {
            return array(
                'slug' => $matches[1],
                'channel' => isset($matches[2]) ? $matches[2] : '',
                'stream' => isset($matches[3]) ? $matches[3] : ''
            );
        }
        $trimmed = rtrim((string) $path, '/');
        if ($trimmed === '') {
            return array('slug' => 'shifshqip', 'channel' => '', 'stream' => '');
        }
        if (preg_match('#^/([^/]+)$#', $trimmed, $simple) && $simple[1] !== 'k1') {
            return array('slug' => $simple[1], 'channel' => '', 'stream' => '');
        }
        return null;
    }

    public static function readHeaders()
    {
        $headers = array(
            'user_agent' => isset($_SERVER['HTTP_USER_AGENT']) ? $_SERVER['HTTP_USER_AGENT'] : '',
            'accept' => isset($_SERVER['HTTP_ACCEPT']) ? $_SERVER['HTTP_ACCEPT'] : '',
            'x_requested_with' => isset($_SERVER['HTTP_X_REQUESTED_WITH']) ? $_SERVER['HTTP_X_REQUESTED_WITH'] : '',
            'x_shif_portal_key' => isset($_SERVER['HTTP_X_SHIF_PORTAL_KEY']) ? $_SERVER['HTTP_X_SHIF_PORTAL_KEY'] : '',
            'x_shif_session' => isset($_SERVER['HTTP_X_SHIF_SESSION']) ? $_SERVER['HTTP_X_SHIF_SESSION'] : '',
            'x_shif_app' => isset($_SERVER['HTTP_X_SHIF_APP']) ? $_SERVER['HTTP_X_SHIF_APP'] : '',
            'x_device_id' => isset($_SERVER['HTTP_X_DEVICE_ID']) ? $_SERVER['HTTP_X_DEVICE_ID'] : '',
            'x_shif_stream_proxy' => isset($_SERVER['HTTP_X_SHIF_STREAM_PROXY']) ? $_SERVER['HTTP_X_SHIF_STREAM_PROXY'] : '',
            'x_shif_timestamp' => isset($_SERVER['HTTP_X_SHIF_TIMESTAMP']) ? $_SERVER['HTTP_X_SHIF_TIMESTAMP'] : '',
            'x_shif_signature' => isset($_SERVER['HTTP_X_SHIF_SIGNATURE']) ? $_SERVER['HTTP_X_SHIF_SIGNATURE'] : '',
            'x_shif_resolve' => isset($_SERVER['HTTP_X_SHIF_RESOLVE']) ? $_SERVER['HTTP_X_SHIF_RESOLVE'] : ''
        );
        return $headers;
    }

    public static function wantsDirectResolve($headers)
    {
        if (!is_array($headers)) {
            return false;
        }
        $mode = isset($headers['x_shif_resolve']) ? strtolower(trim((string) $headers['x_shif_resolve'])) : '';
        return $mode === 'direct';
    }

    public static function isBrowserClient($headers)
    {
        if (stripos($headers['user_agent'], 'ShifShqip') !== false) {
            return false;
        }
        if (stripos($headers['accept'], 'text/html') !== false) {
            return true;
        }
        $browserHints = array('Mozilla/', 'Chrome/', 'Safari/', 'Firefox/', 'Edg/');
        foreach ($browserHints as $hint) {
            if (stripos($headers['user_agent'], $hint) !== false) {
                return true;
            }
        }
        return false;
    }

    public static function validateSession($connect, $deviceId, $sessionToken)
    {
        $deviceId = trim((string) $deviceId);
        $sessionToken = trim((string) $sessionToken);
        if ($connect === null || strlen($deviceId) < 8 || strlen($sessionToken) < 32) {
            return false;
        }
        self::ensureSessionTable($connect);
        $deviceEsc = $connect->real_escape_string($deviceId);
        $tokenEsc = $connect->real_escape_string($sessionToken);
        $result = $connect->query(
            "SELECT id FROM tbl_portal_sessions WHERE device_id = '$deviceEsc' AND session_token = '$tokenEsc' AND expires_at > NOW() LIMIT 1"
        );
        return $result && $result->num_rows > 0;
    }

    public static function ensureSessionTable($connect)
    {
        if ($connect === null) {
            return;
        }
        $connect->query(
            "CREATE TABLE IF NOT EXISTS `tbl_portal_sessions` (
              `id` int(11) NOT NULL AUTO_INCREMENT,
              `device_id` varchar(128) NOT NULL,
              `session_token` varchar(128) NOT NULL,
              `expires_at` datetime NOT NULL,
              `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
              `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
              PRIMARY KEY (`id`),
              UNIQUE KEY `device_id` (`device_id`),
              KEY `session_token` (`session_token`),
              KEY `expires_at` (`expires_at`)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
        );
    }

    public static function isAppClient($portal, $headers, $requestPath = '', $connect = null)
    {
        if ((int) $portal['status'] !== 1) {
            return false;
        }

        $requiredUa = isset($portal['required_user_agent']) && $portal['required_user_agent'] !== ''
            ? $portal['required_user_agent']
            : 'ShifShqip';
        if (stripos($headers['user_agent'], $requiredUa) === false) {
            return false;
        }

        $requiredPackage = isset($portal['required_package']) && $portal['required_package'] !== ''
            ? $portal['required_package']
            : 'com.shif.shqip.android';
        if ($headers['x_requested_with'] !== $requiredPackage) {
            return false;
        }

        if ($headers['x_shif_app'] !== '1') {
            return false;
        }

        $deviceId = trim((string) $headers['x_device_id']);
        if (strlen($deviceId) < 8) {
            return false;
        }

        $sessionToken = trim((string) $headers['x_shif_session']);
        if ($sessionToken !== '' && $connect !== null) {
            return self::validateSession($connect, $deviceId, $sessionToken);
        }

        return false;
    }

    private static function currentRequestPath()
    {
        $path = isset($_SERVER['REQUEST_URI']) ? parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH) : '/';
        return $path === false || $path === null || $path === '' ? '/' : $path;
    }

    public static function loadActivePortal($connect)
    {
        $result = $connect->query("SELECT * FROM tbl_playlist_portal WHERE status = 1 ORDER BY id ASC LIMIT 1");
        if (!$result || $result->num_rows === 0) {
            return null;
        }
        return $result->fetch_assoc();
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

    /**
     * Keep upstream URLs unchanged — video must not be forced through channel-proxy.
     */
    public static function optimizeK1PlaybackUrl($url)
    {
        return trim((string) $url);
    }

    public static function isOurInfrastructureUrl($url)
    {
        $lower = strtolower(trim((string) $url));
        if ($lower === '') {
            return false;
        }
        if (strpos($lower, 'playlist.shifshqip.com') !== false) {
            return true;
        }
        if (strpos($lower, 'shifshqip.com') === false) {
            return false;
        }
        return strpos($lower, '/k1/channel-proxy/') !== false
            || strpos($lower, '/k1/proxy/') !== false
            || strpos($lower, '/k1/channel/') !== false
            || strpos($lower, '/k1/playlist/') !== false
            || strpos($lower, '/app-measurement/collect/') !== false;
    }

    public static function isDirectCdnUrl($url)
    {
        $url = trim((string) $url);
        if ($url === '' || !preg_match('#^https?://#i', $url)) {
            return false;
        }
        if (self::isOurInfrastructureUrl($url)) {
            return false;
        }
        $lower = strtolower($url);
        if (strpos($lower, '.m3u8') !== false || strpos($lower, '.mpd') !== false || strpos($lower, '.m3u') !== false) {
            return true;
        }
        if (preg_match('#^https?://[^/]+:\d+/.+#i', $url)) {
            return true;
        }
        return strpos($lower, '/live/') !== false || strpos($lower, '/hls/') !== false;
    }

    /**
     * Resolve once on the portal server; client streams directly from the CDN afterward.
     */
    public static function resolveToDirectCdnUrl($portal, $url, $depth = 0)
    {
        $url = trim((string) $url);
        if ($url === '' || $depth > 10) {
            return '';
        }
        if (self::isDirectCdnUrl($url)) {
            return $url;
        }
        if (self::isK1ChannelProxyUrl($url)
            || (strpos(strtolower($url), '/k1/channel/') !== false && strpos(strtolower($url), 'shifshqip.com') !== false)) {
            $direct = self::unwrapProxyManifest($portal, $url);
            if ($direct !== '') {
                return $direct;
            }
        }
        if (self::isK1PlaylistEntryUrl($url) || strpos(strtolower($url), '/k1/channel/') !== false) {
            $resolved = self::resolveStreamEntryUrl($portal, $url);
            if ($resolved !== '' && strcasecmp($resolved, $url) !== 0) {
                return self::resolveToDirectCdnUrl($portal, $resolved, $depth + 1);
            }
        }
        if (!preg_match('#^https?://#i', $url)) {
            return '';
        }
        $resolved = self::resolveStreamEntryUrl($portal, $url);
        if ($resolved !== '' && strcasecmp($resolved, $url) !== 0) {
            return self::resolveToDirectCdnUrl($portal, $resolved, $depth + 1);
        }
        return self::isDirectCdnUrl($url) ? $url : '';
    }

    public static function unwrapProxyManifest($portal, $proxyUrl)
    {
        $proxyUrl = trim((string) $proxyUrl);
        if ($proxyUrl === '') {
            return '';
        }
        $upstream = self::fetchUpstream($portal, $proxyUrl);
        if (!$upstream['ok'] || empty($upstream['body'])) {
            return '';
        }
        $body = (string) $upstream['body'];
        if (stripos($body, '#EXTM3U') === false) {
            $json = json_decode($body, true);
            if (is_array($json)) {
                foreach (array('direct_url', 'upstream_url', 'cdn_url', 'stream_url', 'url') as $key) {
                    if (!empty($json[$key]) && self::isDirectCdnUrl($json[$key])) {
                        return trim((string) $json[$key]);
                    }
                }
            }
            return '';
        }
        foreach (preg_split('/\r?\n/', $body) as $line) {
            $line = trim($line);
            if ($line === '' || $line[0] === '#') {
                continue;
            }
            $candidate = self::resolveRelativeUrl($line, $proxyUrl);
            if (self::isDirectCdnUrl($candidate)) {
                return $candidate;
            }
            if (strpos(strtolower($candidate), '.m3u8') !== false && !self::isOurInfrastructureUrl($candidate)) {
                $nested = self::resolveToDirectCdnUrl($portal, $candidate, 1);
                if ($nested !== '') {
                    return $nested;
                }
            }
        }
        return '';
    }

    /**
     * Server-side k1 channel-url lookup — resolves to channel-proxy on the
     * portal only; client streams directly from shifshqip.com afterward.
     */
    public static function resolveK1ChannelPortalUrl($portal, $host, $slug, $accessKey)
    {
        $slug = trim((string) $slug);
        if ($slug === '') {
            return '';
        }
        $apiUrl = 'https://shifshqip.com/k1/api/channel-url/' . rawurlencode($slug);
        $upstream = self::fetchUpstream($portal, $apiUrl);
        if (!$upstream['ok']) {
            return '';
        }
        $json = json_decode($upstream['body'], true);
        if (!is_array($json) || empty($json['ok'])) {
            return '';
        }
        $url = '';
        foreach (array('direct_url', 'upstream_url', 'cdn_url', 'url', 'proxy_url') as $key) {
            if (!empty($json[$key])) {
                $url = trim((string) $json[$key]);
                break;
            }
        }
        if ($url === '') {
            return '';
        }
        if ($url[0] === '/') {
            $url = 'https://shifshqip.com' . $url;
        }
        $direct = self::resolveToDirectCdnUrl($portal, $url);
        return ($direct !== '' && self::isDirectCdnUrl($direct)) ? $direct : '';
    }

    public static function isK1ChannelProxyUrl($url)
    {
        $url = strtolower((string) $url);
        return strpos($url, '/k1/channel-proxy/') !== false || strpos($url, '/k1/proxy/') !== false;
    }

    public static function isK1PlaylistEntryUrl($url)
    {
        $url = strtolower((string) $url);
        return strpos($url, '/k1/playlist/') !== false;
    }

    /**
     * Resolve playlist entry URLs to a direct CDN stream URL when possible.
     */
    public static function resolvePlaybackRedirectUrl($portal, $url)
    {
        $url = trim((string) $url);
        if ($url === '' || !preg_match('#^https?://#i', $url)) {
            return '';
        }
        $direct = self::resolveToDirectCdnUrl($portal, $url);
        if ($direct !== '') {
            return $direct;
        }
        return $url;
    }

    public static function redirectToStreamUrl($finalUrl)
    {
        $finalUrl = trim((string) $finalUrl);
        if ($finalUrl === '' || !preg_match('#^https?://#i', $finalUrl)) {
            return false;
        }
        header('Cache-Control: private, no-store, no-cache, must-revalidate, max-age=0');
        header('Location: ' . $finalUrl, true, 302);
        return true;
    }

    public static function resolveStreamEntryUrl($portal, $entryUrl)
    {
        $entryUrl = trim((string) $entryUrl);
        if ($entryUrl === '' || !preg_match('#^https?://#i', $entryUrl)) {
            return '';
        }
        $referer = !empty($portal['outbound_referer']) ? $portal['outbound_referer'] : '';
        if ($referer === '' && !empty($portal['source_url'])) {
            $referer = $portal['source_url'];
        }

        $redirectLocation = '';
        $ch = curl_init();
        curl_setopt($ch, CURLOPT_URL, $entryUrl);
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        curl_setopt($ch, CURLOPT_FOLLOWLOCATION, false);
        curl_setopt($ch, CURLOPT_CONNECTTIMEOUT, 15);
        curl_setopt($ch, CURLOPT_TIMEOUT, 30);
        curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, true);
        curl_setopt($ch, CURLOPT_HEADERFUNCTION, function ($handle, $headerLine) use (&$redirectLocation) {
            if (preg_match('/^Location:\s*(.+)$/i', trim($headerLine), $matches)) {
                $redirectLocation = trim($matches[1]);
            }
            return strlen($headerLine);
        });

        $headers = array('Accept: */*');
        if (!empty($portal['outbound_user_agent'])) {
            curl_setopt($ch, CURLOPT_USERAGENT, $portal['outbound_user_agent']);
        } else {
            curl_setopt($ch, CURLOPT_USERAGENT, 'Ooew');
        }
        if ($referer !== '') {
            $headers[] = 'Referer: ' . $referer;
        } elseif (!empty($portal['source_url']) && strpos($portal['source_url'], 'shifshqip.com') !== false) {
            $headers[] = 'Referer: https://shifshqip.com/k1/playlist/shifshqip.m3u';
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
        curl_close($ch);

        if ($status >= 300 && $status < 400 && $redirectLocation !== '') {
            return self::resolveRelativeUrl($redirectLocation, $entryUrl);
        }

        if (preg_match('#Redirecting to\s+(/[^\s"\'<>]+)#i', (string) $body, $matches)) {
            return self::resolveRelativeUrl($matches[1], $entryUrl);
        }

        return '';
    }

    public static function fetchUpstream($portal, $url, $depth = 0)
    {
        if ($depth > 5) {
            return array('ok' => false, 'status' => 0, 'error' => 'redirect_limit');
        }

        $referer = !empty($portal['outbound_referer']) ? $portal['outbound_referer'] : '';
        if ($referer === '' && !empty($portal['source_url'])) {
            $referer = $portal['source_url'];
        }

        $redirectLocation = '';
        $ch = curl_init();
        curl_setopt($ch, CURLOPT_URL, $url);
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        curl_setopt($ch, CURLOPT_FOLLOWLOCATION, false);
        curl_setopt($ch, CURLOPT_CONNECTTIMEOUT, 15);
        curl_setopt($ch, CURLOPT_TIMEOUT, 30);
        curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, true);
        curl_setopt($ch, CURLOPT_HEADERFUNCTION, function ($handle, $headerLine) use (&$redirectLocation) {
            if (preg_match('/^Location:\s*(.+)$/i', trim($headerLine), $matches)) {
                $redirectLocation = trim($matches[1]);
            }
            return strlen($headerLine);
        });

        $headers = array('Accept: */*');
        if (!empty($portal['outbound_user_agent'])) {
            curl_setopt($ch, CURLOPT_USERAGENT, $portal['outbound_user_agent']);
        } else {
            curl_setopt($ch, CURLOPT_USERAGENT, 'Ooew');
        }
        if ($referer !== '') {
            $headers[] = 'Referer: ' . $referer;
        } elseif (!empty($portal['source_url']) && strpos($portal['source_url'], 'shifshqip.com') !== false) {
            $headers[] = 'Referer: https://shifshqip.com/k1/playlist/shifshqip.m3u';
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

        if ($body === false) {
            return array('ok' => false, 'status' => $status, 'error' => $error !== '' ? $error : 'upstream_failed');
        }

        if ($status >= 300 && $status < 400) {
            $nextUrl = $redirectLocation;
            if ($nextUrl === '' && preg_match('#Redirecting to\s+(/[^\s"\'<>]+)#i', (string) $body, $matches)) {
                $nextUrl = $matches[1];
            }
            if ($nextUrl !== '') {
                $nextUrl = self::resolveRelativeUrl($nextUrl, $url);
                return self::fetchUpstream($portal, $nextUrl, $depth + 1);
            }
            return array('ok' => false, 'status' => $status, 'error' => 'redirect_missing');
        }

        if ($status < 200 || $status >= 300) {
            return array('ok' => false, 'status' => $status, 'error' => $error !== '' ? $error : 'upstream_failed');
        }

        if (preg_match('#Redirecting to\s+(/[^\s"\'<>]+)#i', (string) $body, $matches)) {
            $nextUrl = self::resolveRelativeUrl($matches[1], $url);
            if ($nextUrl !== '') {
                return self::fetchUpstream($portal, $nextUrl, $depth + 1);
            }
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

    public static function sealUrl($url, $key)
    {
        if ($url === '' || $key === '') {
            return '';
        }
        $material = hash('sha256', (string) $key, true);
        $iv = random_bytes(16);
        $encrypted = openssl_encrypt((string) $url, 'AES-256-CBC', $material, OPENSSL_RAW_DATA, $iv);
        if ($encrypted === false) {
            return '';
        }
        return rtrim(strtr(base64_encode($iv . $encrypted), '+/', '-_'), '=');
    }

    public static function unsealUrl($token, $key)
    {
        if ($token === '' || $key === '') {
            return '';
        }
        $normalized = strtr($token, '-_', '+/');
        $padding = strlen($normalized) % 4;
        if ($padding > 0) {
            $normalized .= str_repeat('=', 4 - $padding);
        }
        $raw = base64_decode($normalized, true);
        if ($raw === false || strlen($raw) < 17) {
            return '';
        }
        $iv = substr($raw, 0, 16);
        $encrypted = substr($raw, 16);
        $material = hash('sha256', (string) $key, true);
        $url = openssl_decrypt($encrypted, 'AES-256-CBC', $material, OPENSSL_RAW_DATA, $iv);
        return $url === false ? '' : $url;
    }

    public static function resolveRelativeUrl($target, $baseUrl)
    {
        $target = trim((string) $target);
        if ($target === '') {
            return '';
        }
        if (preg_match('#^https?://#i', $target)) {
            return $target;
        }
        if ($baseUrl === '') {
            return $target;
        }
        $parts = parse_url($baseUrl);
        if ($parts === false || !isset($parts['scheme'], $parts['host'])) {
            return $target;
        }
        $prefix = $parts['scheme'] . '://' . $parts['host'];
        if (isset($parts['port'])) {
            $prefix .= ':' . $parts['port'];
        }
        if (strpos($target, '/') === 0) {
            return $prefix . $target;
        }
        $dir = isset($parts['path']) ? rtrim(dirname($parts['path']), '/\\') : '';
        return $prefix . ($dir !== '' ? $dir . '/' : '/') . $target;
    }

    public static function parseM3uEntries($body, $sourceUrl = '')
    {
        $entries = array();
        $lines = preg_split('/\r\n|\n|\r/', (string) $body);
        $pendingName = '';
        $index = 0;
        foreach ($lines as $line) {
            $trimmed = trim($line);
            if ($trimmed === '') {
                continue;
            }
            if ($trimmed[0] === '#') {
                if (stripos($trimmed, '#EXTINF') === 0 && preg_match('/,(.*)$/', $trimmed, $matches)) {
                    $pendingName = trim($matches[1]);
                }
                continue;
            }
            $index++;
            $streamSlug = '';
            if (preg_match('#/([^/]+)\.m3u8?$#i', $trimmed, $slugMatch)) {
                $streamSlug = self::slugify(str_replace('_', '-', $slugMatch[1]));
            }
            $channelSlug = $streamSlug !== '' ? $streamSlug : (string) $index;
            $entries[] = array(
                'index' => $index,
                'slug' => $channelSlug,
                'name_slug' => self::slugify($pendingName),
                'name' => $pendingName,
                'url' => self::resolveRelativeUrl($trimmed, $sourceUrl)
            );
            $pendingName = '';
        }
        return $entries;
    }

    public static function findM3uEntry($body, $sourceUrl, $channelSlug)
    {
        $channelSlug = trim((string) $channelSlug);
        if ($channelSlug === '') {
            return null;
        }
        foreach (self::parseM3uEntries($body, $sourceUrl) as $entry) {
            $normalizedSlug = self::slugify(str_replace('_', '-', $channelSlug));
            if ((string) $entry['index'] === $channelSlug
                || (string) $entry['slug'] === $channelSlug
                || (string) $entry['slug'] === $normalizedSlug
                || ($entry['name_slug'] !== '' && $entry['name_slug'] === $channelSlug)
                || ($entry['name_slug'] !== '' && $entry['name_slug'] === $normalizedSlug)) {
                return $entry;
            }
        }
        return null;
    }

    public static function resolveStreamUrl($portal, $streamUrl, $sourceUrl = '')
    {
        $resolved = self::resolveRelativeUrl($streamUrl, $sourceUrl);
        if ($resolved === '' || preg_match('#^https?://#i', $resolved)) {
            return self::optimizeK1PlaybackUrl($resolved);
        }
        return self::optimizeK1PlaybackUrl(self::resolveRelativeUrl($resolved, isset($portal['source_url']) ? $portal['source_url'] : $sourceUrl));
    }

    public static function fetchStreamPayload($portal, $url, $sourceUrl = '', $depth = 0)
    {
        if ($depth > 4) {
            return array('ok' => false, 'status' => 0, 'error' => 'stream_depth');
        }
        $upstream = self::fetchUpstream($portal, $url);
        if (!$upstream['ok']) {
            return $upstream;
        }
        if (self::detectSourceType($upstream['body']) !== 'json') {
            return $upstream;
        }
        $json = json_decode($upstream['body'], true);
        if (!is_array($json)) {
            return $upstream;
        }
        $nextUrl = '';
        if (!empty($json['stream_url'])) {
            $nextUrl = $json['stream_url'];
        } elseif (!empty($json['stream_url_relative'])) {
            $nextUrl = $json['stream_url_relative'];
        }
        if ($nextUrl === '') {
            return $upstream;
        }
        $resolved = self::resolveStreamUrl($portal, $nextUrl, $sourceUrl !== '' ? $sourceUrl : $url);
        return self::fetchStreamPayload($portal, $resolved, $sourceUrl, $depth + 1);
    }

    public static function outputStreamBody($portal, $host, $slug, $sourceUrl, $upstream)
    {
        if (!$upstream['ok']) {
            return false;
        }
        header('Cache-Control: private, no-store, no-cache, must-revalidate, max-age=0');
        $body = $upstream['body'];
        if (stripos($body, '#EXTM3U') !== false) {
            header('Content-Type: application/vnd.apple.mpegurl; charset=utf-8');
            echo self::rewriteM3uStream($body, $host, $slug, $sourceUrl, $portal);
            return true;
        }
        $contentType = isset($upstream['content_type']) ? trim((string) $upstream['content_type']) : '';
        if ($contentType !== '') {
            header('Content-Type: ' . $contentType);
        } else {
            header('Content-Type: application/octet-stream');
        }
        echo $body;
        return true;
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

    public static function rewriteM3uStream($body, $host, $slug, $baseUrl, $portal)
    {
        $base = self::publicBaseUrl($host, $slug);
        $accessKey = isset($portal['access_key']) ? (string) $portal['access_key'] : '';
        $lines = preg_split('/\r\n|\n|\r/', (string) $body);
        $output = array();
        foreach ($lines as $line) {
            $trimmed = trim($line);
            if ($trimmed === '' || $trimmed[0] === '#') {
                $output[] = $line;
                continue;
            }
            $target = self::resolveRelativeUrl($trimmed, $baseUrl);
            if ($target !== '' && preg_match('#^https?://#i', $target)) {
                $sealed = self::sealUrl($target, $accessKey);
                $output[] = $sealed !== '' ? $base . '/s/' . $sealed : $line;
            } else {
                $output[] = $line;
            }
        }
        return implode("\n", $output);
    }

    public static function deriveDeviceKey($deviceId, $masterKey)
    {
        return hash_hmac('sha256', (string) $deviceId, (string) $masterKey);
    }

    public static function encryptForDevice($plaintext, $deviceId, $masterKey)
    {
        if ($plaintext === '' || $deviceId === '' || $masterKey === '') {
            return '';
        }
        $derived = self::deriveDeviceKey($deviceId, $masterKey);
        $key = hash('sha256', $derived, true);
        $iv = random_bytes(16);
        $encrypted = openssl_encrypt((string) $plaintext, 'AES-256-CBC', $key, OPENSSL_RAW_DATA, $iv);
        if ($encrypted === false) {
            return '';
        }
        return base64_encode($iv . $encrypted);
    }

    public static function decryptForDevice($encoded, $deviceId, $masterKey)
    {
        if ($encoded === '' || $deviceId === '' || $masterKey === '') {
            return '';
        }
        $raw = base64_decode((string) $encoded, true);
        if ($raw === false || strlen($raw) < 17) {
            return '';
        }
        $derived = self::deriveDeviceKey($deviceId, $masterKey);
        $key = hash('sha256', $derived, true);
        $iv = substr($raw, 0, 16);
        $encrypted = substr($raw, 16);
        $plain = openssl_decrypt($encrypted, 'AES-256-CBC', $key, OPENSSL_RAW_DATA, $iv);
        return $plain === false ? '' : $plain;
    }

    public static function rewriteM3uIndex($body, $host, $slug, $sourceUrl = '', $portal = null)
    {
        $base = self::publicBaseUrl($host, $slug);
        $accessKey = is_array($portal) && isset($portal['access_key']) ? (string) $portal['access_key'] : '';
        $lines = preg_split('/\r\n|\n|\r/', (string) $body);
        $output = array();
        foreach ($lines as $line) {
            $trimmed = trim($line);
            if ($trimmed === '' || $trimmed[0] === '#') {
                $output[] = $line;
                continue;
            }
            $target = self::resolveRelativeUrl($trimmed, $sourceUrl);
            if ($target !== '' && preg_match('#^https?://#i', $target) && $accessKey !== '') {
                $target = self::optimizeK1PlaybackUrl($target);
                $sealed = self::sealUrl($target, $accessKey);
                $output[] = $sealed !== '' ? $base . '/r/' . $sealed : $target;
            } else {
                $output[] = $target;
            }
        }
        return implode("\n", $output);
    }

    /**
     * True when the URL points to our own origin stream infrastructure
     * (shifshqip.com/k1/playlist or /k1/proxy). These are HLS manifests that
     * the origin proxies itself (api_/sport_/custom_tv channels) — there is no
     * single "direct CDN" URL to redirect to, so we proxy the manifest and let
     * segments flow straight from the origin.
     */
    public static function isOriginStreamUrl($url)
    {
        $lower = strtolower(trim((string) $url));
        if ($lower === '' || strpos($lower, 'shifshqip.com') === false) {
            return false;
        }
        return strpos($lower, '/k1/playlist/') !== false
            || strpos($lower, '/k1/proxy/') !== false
            || strpos($lower, '/k1/channel/') !== false
            || strpos($lower, '/k1/channel-proxy/') !== false;
    }

    /**
     * Single non-following fetch (status + Location + body) using the portal's
     * outbound identity. Used to decide between "redirect to CDN" and
     * "proxy this manifest".
     */
    public static function fetchRaw($portal, $url)
    {
        $referer = !empty($portal['outbound_referer']) ? $portal['outbound_referer'] : '';
        if ($referer === '' && !empty($portal['source_url'])) {
            $referer = $portal['source_url'];
        }
        $location = '';
        $ch = curl_init();
        curl_setopt($ch, CURLOPT_URL, $url);
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        curl_setopt($ch, CURLOPT_FOLLOWLOCATION, false);
        curl_setopt($ch, CURLOPT_CONNECTTIMEOUT, 15);
        curl_setopt($ch, CURLOPT_TIMEOUT, 30);
        curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, true);
        curl_setopt($ch, CURLOPT_HEADERFUNCTION, function ($handle, $headerLine) use (&$location) {
            if (preg_match('/^Location:\s*(.+)$/i', trim($headerLine), $matches)) {
                $location = trim($matches[1]);
            }
            return strlen($headerLine);
        });
        $headers = array('Accept: */*');
        curl_setopt($ch, CURLOPT_USERAGENT, !empty($portal['outbound_user_agent']) ? $portal['outbound_user_agent'] : 'Ooew');
        if ($referer !== '') {
            $headers[] = 'Referer: ' . $referer;
        } elseif (!empty($portal['source_url']) && strpos($portal['source_url'], 'shifshqip.com') !== false) {
            $headers[] = 'Referer: https://shifshqip.com/k1/playlist/shifshqip.m3u';
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
        curl_close($ch);
        return array(
            'status' => $status,
            'location' => $location,
            'body' => $body === false ? '' : (string) $body,
            'content_type' => $contentType
        );
    }

    /**
     * Proxy an origin HLS manifest to the app:
     *  - variant/sub-playlist lines are re-sealed as /r/ (proxied recursively)
     *  - segment lines are sealed as /s/ (a lightweight 302 to the origin)
     * The heavy segment bytes stream directly from the origin/CDN, so the
     * portal only serves tiny manifests + redirects and the real playlist URL
     * is never exposed to the client.
     */
    public static function serveProxiedManifest($portal, $host, $slug, $entryUrl, $depth = 0)
    {
        $entryUrl = trim((string) $entryUrl);
        if ($entryUrl === '' || $depth > 4 || !preg_match('#^https?://#i', $entryUrl)) {
            return false;
        }
        $raw = self::fetchRaw($portal, $entryUrl);
        if ($raw['status'] >= 300 && $raw['status'] < 400 && $raw['location'] !== '') {
            $loc = self::resolveRelativeUrl($raw['location'], $entryUrl);
            if (self::isDirectCdnUrl($loc)) {
                return self::redirectToStreamUrl($loc);
            }
            $direct = self::resolveToDirectCdnUrl($portal, $loc);
            if ($direct !== '' && self::isDirectCdnUrl($direct)) {
                return self::redirectToStreamUrl($direct);
            }
            return self::serveProxiedManifest($portal, $host, $slug, $loc, $depth + 1);
        }
        if ($raw['status'] < 200 || $raw['status'] >= 300 || $raw['body'] === '') {
            return false;
        }
        if (stripos($raw['body'], '#EXTM3U') === false) {
            return self::redirectToStreamUrl($entryUrl);
        }
        header('Content-Type: application/vnd.apple.mpegurl; charset=utf-8');
        header('Cache-Control: private, no-store, no-cache, must-revalidate, max-age=0');
        echo self::rewriteProxyManifest($portal, $host, $slug, $entryUrl, $raw['body']);
        return true;
    }

    public static function rewriteProxyManifest($portal, $host, $slug, $baseUrl, $body)
    {
        $base = self::publicBaseUrl($host, $slug);
        $accessKey = is_array($portal) && isset($portal['access_key']) ? (string) $portal['access_key'] : '';
        $lines = preg_split('/\r\n|\n|\r/', (string) $body);
        $output = array();
        $expectVariant = false;
        foreach ($lines as $line) {
            $trimmed = trim($line);
            if ($trimmed === '') {
                $output[] = $line;
                continue;
            }
            if ($trimmed[0] === '#') {
                if (stripos($trimmed, '#EXT-X-STREAM-INF') === 0) {
                    $expectVariant = true;
                }
                $output[] = self::rewriteManifestTag($trimmed, $base, $baseUrl, $accessKey);
                continue;
            }
            $abs = self::resolveRelativeUrl($trimmed, $baseUrl);
            if ($abs === '' || !preg_match('#^https?://#i', $abs) || $accessKey === '') {
                $output[] = $abs !== '' ? $abs : $line;
                $expectVariant = false;
                continue;
            }
            $lower = strtolower($abs);
            $isVariant = $expectVariant
                || strpos($lower, '/proxy/sub') !== false
                || preg_match('#\.m3u8?(\?|$)#', $lower) === 1;
            $sealed = self::sealUrl($abs, $accessKey);
            if ($sealed === '') {
                $output[] = $abs;
            } else {
                $output[] = $base . ($isVariant ? '/r/' : '/s/') . $sealed;
            }
            $expectVariant = false;
        }
        return implode("\n", $output);
    }

    /**
     * Rewrite URI="..." attributes embedded in HLS tags (EXT-X-KEY, EXT-X-MEDIA,
     * EXT-X-I-FRAME-STREAM-INF) so keys/renditions also route through the portal.
     */
    private static function rewriteManifestTag($tagLine, $base, $baseUrl, $accessKey)
    {
        if ($accessKey === '' || stripos($tagLine, 'URI="') === false) {
            return $tagLine;
        }
        $isKey = stripos($tagLine, '#EXT-X-KEY') === 0 || stripos($tagLine, '#EXT-X-SESSION-KEY') === 0;
        $seg = $isKey ? '/s/' : '/r/';
        return preg_replace_callback('/URI="([^"]*)"/', function ($m) use ($base, $baseUrl, $accessKey, $seg) {
            $abs = self::resolveRelativeUrl($m[1], $baseUrl);
            if ($abs === '' || !preg_match('#^https?://#i', $abs)) {
                return $m[0];
            }
            $sealed = self::sealUrl($abs, $accessKey);
            return $sealed === '' ? $m[0] : 'URI="' . $base . $seg . $sealed . '"';
        }, $tagLine);
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

    public static function renderLanding($apkUrl, $playStoreUrl, $publicUrl = '')
    {
        http_response_code(200);
        header('Content-Type: text/html; charset=utf-8');
        header('Cache-Control: no-store, no-cache, must-revalidate, max-age=0');
        $apkUrlEsc = htmlspecialchars($apkUrl, ENT_QUOTES, 'UTF-8');
        $playStoreEsc = htmlspecialchars($playStoreUrl, ENT_QUOTES, 'UTF-8');
        $publicUrlEsc = htmlspecialchars($publicUrl, ENT_QUOTES, 'UTF-8');
        echo '<!DOCTYPE html><html lang="sq"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">'
            . '<title>Shif Shqip — Playlist i mbrojtur</title><style>'
            . 'body{margin:0;font-family:Arial,sans-serif;background:#0f1216;color:#fff;display:flex;min-height:100vh;align-items:center;justify-content:center;padding:24px}'
            . '.card{max-width:560px;background:#1a1f26;border-radius:20px;padding:32px;box-shadow:0 20px 60px rgba(0,0,0,.35)}'
            . 'h1{margin:0 0 12px;font-size:30px;text-align:center}.sub{color:#a8b0bc;line-height:1.7;margin-bottom:20px;text-align:center}'
            . 'ol{color:#d5dbe3;line-height:1.8;padding-left:20px;margin:0 0 24px}'
            . '.actions{text-align:center}a.btn{display:inline-block;margin:8px;padding:14px 22px;border-radius:999px;text-decoration:none;font-weight:700}'
            . '.primary{background:#8db04b;color:#fff}.ghost{border:1px solid #3a4350;color:#fff}'
            . '.note{margin-top:18px;padding:12px 14px;border-radius:12px;background:#12161c;color:#97a0ad;font-size:14px;line-height:1.6}'
            . '</style></head><body><div class="card">'
            . '<h1>Shif Shqip</h1>'
            . '<p class="sub">Ky playlist është i mbrojtur. Nuk hapet në shfletues apo playerë të tjerë IPTV — vetëm në aplikacionin zyrtar Shif Shqip.</p>'
            . '<ol><li>Shkarko dhe instalo aplikacionin Shif Shqip.</li><li>Hap app-in në telefonin ose TV Android.</li><li>Playlist ngarkohet automatikisht nga serveri — nuk ke nevojë ta shtosh manualisht.</li></ol>'
            . '<div class="actions"><a class="btn primary" href="' . $apkUrlEsc . '">Shkarko APK</a>'
            . '<a class="btn ghost" href="' . $playStoreEsc . '">Google Play</a></div>'
            . ($publicUrlEsc !== '' ? '<div class="note">Përdor aplikacionin Shif Shqip për të parë kanalet.</div>' : '')
            . '</div></body></html>';
        exit;
    }
}
