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
     * Normalize XUI /play/…/m3u8 for sealing (stay server-side). Do not unwrap to /auth/.
     */
    public static function optimizeK1PlaybackUrl($url)
    {
        $url = trim((string) $url);
        if ($url === '') {
            return '';
        }
        if (self::isXuiPlayUrl($url) || preg_match('#k1\.shifshqip\.com(?::\d+)?/play/#i', $url)) {
            return self::normalizeXuiPlayEntryUrl($url);
        }
        return $url;
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

    /**
     * XUI /play/… — permanent stream key. Keep server-side only (never return to app).
     */
    public static function isXuiPlayUrl($url)
    {
        $url = trim((string) $url);
        if ($url === '' || !preg_match('#^https?://#i', $url)) {
            return false;
        }
        $host = strtolower((string) parse_url($url, PHP_URL_HOST));
        if ($host !== 'k1.shifshqip.com' && !preg_match('#\.k1\.shifshqip\.com$#', $host)) {
            return false;
        }
        $path = strtolower((string) parse_url($url, PHP_URL_PATH));
        return (bool) preg_match('#^/play(/|$)#', $path);
    }

    /**
     * XUI client CDN: /auth/… token + /hls/… segments — no portal proxy after resolve.
     */
    public static function isXuiClientCdnUrl($url)
    {
        $url = trim((string) $url);
        if ($url === '' || !preg_match('#^https?://#i', $url)) {
            return false;
        }
        $host = strtolower((string) parse_url($url, PHP_URL_HOST));
        if ($host !== 'k1.shifshqip.com' && !preg_match('#\.k1\.shifshqip\.com$#', $host)) {
            return false;
        }
        $path = strtolower((string) parse_url($url, PHP_URL_PATH));
        return (bool) preg_match('#^/(auth|hls)(/|$)#', $path);
    }

    /** @deprecated use isXuiPlayUrl / isXuiClientCdnUrl */
    public static function isXuiDirectUrl($url)
    {
        return self::isXuiPlayUrl($url) || self::isXuiClientCdnUrl($url);
    }

    /**
     * Prefer HLS channel URLs for XUI playlists (…/m3u8 not …/ts MPEG-TS).
     */
    public static function preferHlsSourceUrl($url)
    {
        $url = trim((string) $url);
        if ($url === '') {
            return '';
        }
        if (!preg_match('#k1\.shifshqip\.com(?::\d+)?/.*/?playlist/#i', $url)) {
            return $url;
        }
        if (preg_match('#(?:\?|&)output=#i', $url)) {
            return $url;
        }
        return $url . (strpos($url, '?') === false ? '?' : '&') . 'output=hls';
    }

    /**
     * Normalize sealed /play/ entry (server-side only): strip :443, /ts→/m3u8.
     */
    public static function normalizeXuiPlayEntryUrl($url)
    {
        $url = trim((string) $url);
        if ($url === '') {
            return '';
        }
        $url = preg_replace('#^(https?://[^/]+):443(/)#i', '$1$2', $url);
        if (!is_string($url) || $url === '') {
            return '';
        }
        $url = preg_replace('#(/play/[^/\\?\\s]+)/ts(\\?|$)#i', '$1/m3u8$2', $url);
        return is_string($url) ? $url : '';
    }

    /**
     * Client-facing CDN URL (/auth or /hls): strip :443; ?.m3u8 for older APKs.
     * Never returns /play/.
     */
    public static function normalizeDirectPlaybackUrl($url)
    {
        $url = trim((string) $url);
        if ($url === '') {
            return '';
        }
        $url = preg_replace('#^(https?://[^/]+):443(/)#i', '$1$2', $url);
        if (!is_string($url) || $url === '') {
            return '';
        }
        // If a /play/ URL slips through, do not hand it to the client.
        if (self::isXuiPlayUrl($url)) {
            return '';
        }
        if (self::isXuiClientCdnUrl($url) && stripos($url, '.m3u8') === false) {
            $url .= (strpos($url, '?') === false ? '?' : '&') . '.m3u8';
        }
        return $url;
    }

    /**
     * Server-side /play/ → Location /auth/… (client then streams auth+hls on CDN).
     */
    public static function unwrapXuiPlayToAuthUrl($portal, $playUrl)
    {
        $playUrl = self::normalizeXuiPlayEntryUrl($playUrl);
        if ($playUrl === '' || !self::isXuiPlayUrl($playUrl)) {
            return '';
        }
        $fetchUrl = self::rewriteLocalUpstreamUrl($playUrl);
        $redirectLocation = '';
        $ch = curl_init();
        curl_setopt($ch, CURLOPT_URL, $fetchUrl);
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        curl_setopt($ch, CURLOPT_FOLLOWLOCATION, false);
        curl_setopt($ch, CURLOPT_CONNECTTIMEOUT, 10);
        curl_setopt($ch, CURLOPT_TIMEOUT, 20);
        curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, false);
        curl_setopt($ch, CURLOPT_HEADERFUNCTION, function ($handle, $headerLine) use (&$redirectLocation) {
            if (preg_match('/^Location:\s*(.+)$/i', trim($headerLine), $matches)) {
                $redirectLocation = trim($matches[1]);
            }
            return strlen($headerLine);
        });
        $headers = array('Accept: */*');
        if (strpos($fetchUrl, 'http://127.0.0.1:81/') === 0) {
            $headers[] = 'Host: k1.shifshqip.com';
            $headers[] = 'X-Forwarded-Proto: https';
        }
        curl_setopt($ch, CURLOPT_HTTPHEADER, $headers);
        curl_setopt($ch, CURLOPT_USERAGENT, !empty($portal['outbound_user_agent'])
            ? $portal['outbound_user_agent']
            : 'Ooew');
        $body = curl_exec($ch);
        $status = (int) curl_getinfo($ch, CURLINFO_HTTP_CODE);
        curl_close($ch);

        $next = $redirectLocation;
        if ($next === '' && is_string($body) && preg_match('#Redirecting to\s+(/[^\s"\'<>]+)#i', $body, $matches)) {
            $next = $matches[1];
        }
        if ($status >= 300 && $status < 400 && $next !== '') {
            $auth = self::resolveRelativeUrl($next, $playUrl);
            $auth = preg_replace('#^(https?://[^/]+):443(/)#i', '$1$2', $auth);
            if (is_string($auth) && self::isXuiClientCdnUrl($auth)) {
                return $auth;
            }
        }
        return '';
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
        // /play/ is not client-direct — must unwrap to /auth/ server-side first.
        if (self::isXuiPlayUrl($url)) {
            return false;
        }
        if (self::isXuiClientCdnUrl($url)) {
            return true;
        }
        $lower = strtolower($url);
        // Media-Colony A3 + common CDN hosts (including .js gate URLs).
        if (strpos($lower, 'fastlycache.com') !== false
            || strpos($lower, 'anytools.eu.org') !== false
            || strpos($lower, 'unique-user-id=') !== false
            || strpos($lower, 'gcore-cdn.org') !== false
            || strpos($lower, 'gjirafa.net') !== false
            || strpos($lower, 'gjirafa.com') !== false
            || strpos($lower, 'ngolpdkyoctjcddxshli469r.org') !== false) {
            return true;
        }
        // XUI-style path "/m3u8" (no dot) and normal playlist extensions.
        if (strpos($lower, '.m3u8') !== false || strpos($lower, '.mpd') !== false || strpos($lower, '.m3u') !== false
            || preg_match('#/m3u8(\?|$)#', $lower)) {
            return true;
        }
        if (preg_match('#^https?://[^/]+:\d+/.+#i', $url)) {
            return true;
        }
        return strpos($lower, '/live/') !== false || strpos($lower, '/hls/') !== false
            || strpos($lower, '/auth/') !== false;
    }

    /**
     * Resolve once on the portal server; client streams from CDN (/auth+/hls) afterward.
     * /play/ is unwrapped server-side and never returned to the app.
     */
    public static function resolveToDirectCdnUrl($portal, $url, $depth = 0)
    {
        $url = trim((string) $url);
        if ($url === '' || $depth > 10) {
            return '';
        }
        if (self::isXuiPlayUrl($url)) {
            $auth = self::unwrapXuiPlayToAuthUrl($portal, $url);
            return $auth !== '' ? self::normalizeDirectPlaybackUrl($auth) : '';
        }
        if (self::isXuiClientCdnUrl($url) || self::isDirectCdnUrl($url)) {
            return self::normalizeDirectPlaybackUrl($url);
        }
        if (self::isK1ChannelProxyUrl($url)
            || (strpos(strtolower($url), '/k1/channel/') !== false && strpos(strtolower($url), 'shifshqip.com') !== false)) {
            $direct = self::unwrapProxyManifest($portal, $url);
            if ($direct !== '') {
                return self::resolveToDirectCdnUrl($portal, $direct, $depth + 1);
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
        if (self::isDirectCdnUrl($url)) {
            return self::normalizeDirectPlaybackUrl($url);
        }
        // Private all2 streams no longer unwrap to raw CDN; allow proxy URLs for app playback.
        if (self::isPlayableAll2Url($url)) {
            return $url;
        }
        return '';
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
    public static function resolveK1ChannelPortalUrl($portal, $host, $slug, $accessKey, $clientIpv4 = '')
    {
        $slug = trim((string) $slug);
        if ($slug === '') {
            return null;
        }
        $apiUrl = 'https://shifshqip.com/k1/api/channel-url/' . rawurlencode($slug);
        $clientIpv4 = trim((string) $clientIpv4);
        if ($clientIpv4 !== '' && preg_match('/^\d{1,3}(\.\d{1,3}){3}$/', $clientIpv4)) {
            $apiUrl .= (strpos($apiUrl, '?') === false ? '?' : '&') . 'ipv4=' . rawurlencode($clientIpv4);
        }
        $upstream = self::fetchChannelUrlApi($portal, $apiUrl, $clientIpv4);
        if (!$upstream['ok']) {
            return null;
        }
        $json = json_decode($upstream['body'], true);
        if (!is_array($json) || empty($json['ok'])) {
            return null;
        }
        $url = '';
        // Prefer direct CDN for app (fastly/gcore). Avoid Worker URLs that DC-block.
        foreach (array('direct_url', 'upstream_url', 'cdn_url', 'url', 'proxy_url') as $key) {
            if (!empty($json[$key])) {
                $url = trim((string) $json[$key]);
                break;
            }
        }
        if ($url === '') {
            return null;
        }
        if ($url[0] === '/') {
            $url = 'https://shifshqip.com' . $url;
        }
        $direct = self::resolveToDirectCdnUrl($portal, $url);
        if ($direct === '' || (!self::isDirectCdnUrl($direct) && !self::isPlayableAll2Url($direct))) {
            // Still accept known A3 hosts / all2 proxy URLs even if unwrap failed.
            if (self::isDirectCdnUrl($url) || self::isPlayableAll2Url($url)) {
                $direct = $url;
            } else {
                return null;
            }
        }
        $headers = array();
        if (!empty($json['headers']) && is_array($json['headers'])) {
            $headers = $json['headers'];
        }
        $userAgent = isset($json['user_agent']) ? trim((string) $json['user_agent']) : '';
        return array(
            'url' => $direct,
            'headers' => $headers,
            'user_agent' => $userAgent,
            'a3_cdn' => isset($json['a3_cdn']) ? (string) $json['a3_cdn'] : '',
            'direct_url' => !empty($json['direct_url']) ? trim((string) $json['direct_url']) : $direct
        );
    }

    /**
     * Fetch channel-url API as the app would: forward device IP + app markers so
     * A3 preferDirect + cip rewrite use the phone's residential IPv4.
     */
    public static function fetchChannelUrlApi($portal, $apiUrl, $clientIpv4 = '')
    {
        $apiUrl = trim((string) $apiUrl);
        if ($apiUrl === '') {
            return array('ok' => false, 'body' => '', 'http_code' => 0);
        }
        $ch = curl_init($apiUrl);
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        curl_setopt($ch, CURLOPT_FOLLOWLOCATION, true);
        curl_setopt($ch, CURLOPT_CONNECTTIMEOUT, 8);
        curl_setopt($ch, CURLOPT_TIMEOUT, 20);
        curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, false);
        curl_setopt($ch, CURLOPT_SSL_VERIFYHOST, false);
        curl_setopt($ch, CURLOPT_USERAGENT, 'ShifShqip/Portal (Android)');
        $headers = array(
            'Accept: application/json',
            'X-Shif-App: 1',
            'X-Requested-With: com.shif.shqip.android',
            'X-Shif-Portal-Resolve: 1'
        );
        $clientIpv4 = trim((string) $clientIpv4);
        if ($clientIpv4 !== '' && preg_match('/^\d{1,3}(\.\d{1,3}){3}$/', $clientIpv4)) {
            $headers[] = 'X-Forwarded-For: ' . $clientIpv4;
            $headers[] = 'CF-Connecting-IP: ' . $clientIpv4;
            $headers[] = 'X-Real-IP: ' . $clientIpv4;
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
        $code = (int) curl_getinfo($ch, CURLINFO_HTTP_CODE);
        curl_close($ch);
        return array(
            'ok' => $body !== false && $code >= 200 && $code < 400,
            'body' => $body === false ? '' : $body,
            'http_code' => $code
        );
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
     * all2 private delivery serves streams via /k1/playlist|/channel-proxy|/proxy.
     * These are playable by the app even when no raw Fastly/Gcore URL is exposed.
     */
    public static function isPlayableAll2Url($url)
    {
        $url = strtolower(trim((string) $url));
        if ($url === '' || strpos($url, 'shifshqip.com') === false) {
            return false;
        }
        // Match /k1/a3hls?id=... (no trailing slash) as well as /k1/a3hls/...
        return strpos($url, '/k1/playlist/') !== false
            || strpos($url, '/k1/channel-proxy/') !== false
            || strpos($url, '/k1/channel/') !== false
            || strpos($url, '/k1/proxy/') !== false
            || strpos($url, '/k1/a3proxy') !== false
            || strpos($url, '/k1/a3relay') !== false
            || strpos($url, '/k1/a3hls') !== false
            || strpos($url, '/app-measurement/') !== false;
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
        $fetchUrl = self::rewriteLocalUpstreamUrl($entryUrl);
        $referer = !empty($portal['outbound_referer']) ? $portal['outbound_referer'] : '';
        if ($referer === '' && !empty($portal['source_url'])) {
            $referer = $portal['source_url'];
        }

        $redirectLocation = '';
        $ch = curl_init();
        curl_setopt($ch, CURLOPT_URL, $fetchUrl);
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

        $headers = array(
            'Accept: */*',
            // Prefer app/direct A3 paths (fastly) over datacenter a3hls remux.
            'X-Shif-App: 1',
            'X-Requested-With: com.shif.shqip.android'
        );
        if (strpos($fetchUrl, 'http://127.0.0.1:81/') === 0) {
            $headers[] = 'Host: k1.shifshqip.com';
            $headers[] = 'X-Forwarded-Proto: https';
        }
        if (strpos($fetchUrl, 'http://127.0.0.1:3000/') === 0) {
            $headers[] = 'Host: shifshqip.com';
            $headers[] = 'X-Forwarded-Proto: https';
            $headers[] = 'CF-Connecting-IP: 1.1.1.1';
            $headers[] = 'X-Forwarded-For: 1.1.1.1';
        }
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

    /**
     * Same-VPS fetch: avoid Cloudflare + never hit blocked 127.0.0.1 client path via public loop.
     * XUI (k1) → nginx upstream :81; all2 /k1 → node :3000.
     */
    public static function rewriteLocalUpstreamUrl($url)
    {
        $url = trim((string) $url);
        if ($url === '') {
            return $url;
        }
        $xui = preg_replace(
            '#^https?://(?:[^/]+\.)?k1\.shifshqip\.com(?::\d+)?(/.*)$#i',
            'http://127.0.0.1:81$1',
            $url,
            1
        );
        if (is_string($xui) && $xui !== '' && strpos($xui, 'http://127.0.0.1:81/') === 0) {
            return $xui;
        }
        $rewritten = preg_replace(
            '#^https?://(?:www\.)?shifshqip\.com(?::\d+)?(/k1/.*)$#i',
            'http://127.0.0.1:3000$1',
            $url,
            1
        );
        return is_string($rewritten) && $rewritten !== '' ? $rewritten : $url;
    }

    public static function fetchUpstream($portal, $url, $depth = 0)
    {
        if ($depth > 5) {
            return array('ok' => false, 'status' => 0, 'error' => 'redirect_limit');
        }
        $originalUrl = self::preferHlsSourceUrl(trim((string) $url));
        $url = self::rewriteLocalUpstreamUrl($originalUrl);

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
        if (strpos($url, 'http://127.0.0.1:81/') === 0) {
            $headers[] = 'Host: k1.shifshqip.com';
            $headers[] = 'X-Forwarded-Proto: https';
        }
        if (strpos($url, 'http://127.0.0.1:3000/') === 0) {
            $headers[] = 'Host: shifshqip.com';
            $headers[] = 'X-Forwarded-Proto: https';
            $headers[] = 'CF-Connecting-IP: 1.1.1.1';
            $headers[] = 'X-Forwarded-For: 1.1.1.1';
        }
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
                $baseForRelative = $originalUrl !== '' ? $originalUrl : $url;
                $nextUrl = self::resolveRelativeUrl($nextUrl, $baseForRelative);
                return self::fetchUpstream($portal, $nextUrl, $depth + 1);
            }
            return array('ok' => false, 'status' => $status, 'error' => 'redirect_missing');
        }

        if ($status < 200 || $status >= 300) {
            return array('ok' => false, 'status' => $status, 'error' => $error !== '' ? $error : 'upstream_failed');
        }

        if (preg_match('#Redirecting to\s+(/[^\s"\'<>]+)#i', (string) $body, $matches)) {
            $baseForRelative = $originalUrl !== '' ? $originalUrl : $url;
            $nextUrl = self::resolveRelativeUrl($matches[1], $baseForRelative);
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
                // Seal only the play/m3u8 entry — /auth and /hls stay client↔CDN.
                $target = self::optimizeK1PlaybackUrl($target);
                $sealed = self::sealUrl($target, $accessKey);
                $output[] = $sealed !== '' ? $base . '/r/' . $sealed : $target;
            } else {
                $output[] = $target !== '' ? self::optimizeK1PlaybackUrl($target) : $target;
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
