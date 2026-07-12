/** Live excerpt from /root/all/all2/server.js — A3 playback helpers (keep in sync). */
function encodeA3UpstreamRh(headers = {}, userAgent = '') {
  const payload = {};
  for (const [rawKey, rawValue] of Object.entries(headers && typeof headers === 'object' ? headers : {})) {
    const name = String(rawKey || '').trim();
    const value = String(rawValue || '').trim();
    if (!name || !value) continue;
    if (/^(host|connection|content-length|accept-encoding|transfer-encoding)$/i.test(name)) continue;
    if (/^x-forwarded-for$/i.test(name) && /^(127\.0\.0\.1|::1|localhost)$/i.test(value)) continue;
    payload[name] = value;
  }
  const ua = String(userAgent || '').trim();
  if (ua && !Object.keys(payload).some((k) => String(k).toLowerCase() === 'user-agent')) {
    payload['User-Agent'] = ua;
  }
  if (!Object.keys(payload).length) return '';
  return Buffer.from(JSON.stringify(payload), 'utf8').toString('base64url');
}
function wrapA3CorsProxyUrl(streamUrl = '', options = {}) {
  const url = String(streamUrl || '').trim();
  if (!/^https?:\/\//i.test(url)) return url;
  // Already proxied
  if (/^https?:\/\/a3\.phinemi\.live\/?/i.test(url)) return url;
  if (/phinemi\.live\/a3proxy\/?/i.test(url)) return url;
  const base = String(A3_CORS_PROXY_BASE || 'https://a3.phinemi.live/').replace(/\/?$/, '/');
  const rh = String(options.rh || encodeA3UpstreamRh(options.headers || {}, options.userAgent || '')).trim();
  let out = `${base}?u=${encodeURIComponent(url)}`;
  if (rh) out += `&rh=${encodeURIComponent(rh)}`;
  return out;
}
function isAlbFastlyUrl(url = '') {
  return /app-measurement\.fastlycache\.com/i.test(String(url || ''));
}
function isShifAppRequest(req) {
  const appHeader = String(req?.headers?.['x-shif-app'] || '').trim();
  if (appHeader === '1' || /^true$/i.test(appHeader)) return true;
  const ua = String(req?.headers?.['user-agent'] || '');
  if (/ShifShqip\//i.test(ua)) return true;
  const requestedWith = String(req?.headers?.['x-requested-with'] || '');
  if (/com\.shif\.shqip/i.test(requestedWith)) return true;
  return false;
}
/**
 * Wrap A3 through same-origin /a3sw so the browser Service Worker can fetch the
 * CDN with the viewer's residential IP (Worker/VPS egress is blocked on fastly).
 */
function wrapA3ClientSwUrl(streamUrl = '', options = {}) {
  const url = String(streamUrl || '').trim();
  if (!/^https?:\/\//i.test(url)) return url;
  if (String(url).includes(`${BASE_PATH}/a3sw`)) return url;
  const rh = String(options.rh || encodeA3UpstreamRh(options.headers || {}, options.userAgent || '')).trim();
  let out = `${BASE_PATH}/a3sw?u=${encodeURIComponent(url)}`;
  if (rh) out += `&rh=${encodeURIComponent(rh)}`;
  return out;
}
/**
 * Route A3 playback:
 * - App (ExoPlayer): direct CDN for fastly (phone IP); other A3 via Worker (CORS N/A to Exo).
 * - Web: CF Worker for CORS + injected Referer/Origin/Cookie/UA (and gjirafa v1a1 rewrite).
 */
function buildA3PlaybackUrl(streamUrl = '', options = {}) {
  const url = String(streamUrl || '').trim();
  if (!/^https?:\/\//i.test(url)) return url;
  if (isAlbFastlyUrl(url) && options.preferDirect === true) return url;
  if (options.preferClientSw === true) return wrapA3ClientSwUrl(url, options);
  return wrapA3CorsProxyUrl(url, options);
}
function buildA3PlaybackOptionsFromChannel(channel = {}, req = null) {
  const fastly = isAlbFastlyUrl(channel?.url || '');
  const fromApp = req ? isShifAppRequest(req) : false;
  const headers = channel?.albHeaders && typeof channel.albHeaders === 'object' ? { ...channel.albHeaders } : {};
  // Prefer exact headers from config.php. Only fill Gjirafa Referer/Origin when the
  // stream host is actually gjirafa — do NOT blanket-apply to all gcore /external/ gates
  // (Vizion Plus, News24, etc. ship their own headers or need none).
  if (/gjirafa\.(net|com)/i.test(String(channel?.url || ''))) {
    if (!Object.keys(headers).some((k) => k.toLowerCase() === 'referer')) {
      headers.Referer = 'https://video.gjirafa.com/';
    }
    if (!Object.keys(headers).some((k) => k.toLowerCase() === 'origin')) {
      headers.Origin = 'https://video.gjirafa.com';
    }
  }
  return {
    headers,
    userAgent: String(channel?.albUserAgent || ALB_DEFAULT_UPSTREAM_UA || '').trim() || ALB_DEFAULT_UPSTREAM_UA,
    preferDirect: fastly && fromApp,
    preferClientSw: false,
    webBlocked: fastly && !fromApp
  };
}

async function resolveAlbChannelBySlugRuntime(req, slug, options = {}) {
  if (ALB_API_REMOVED) return null;
  const safeSlug = String(slug || '').trim();
  const isAlbVodSlug = /^albvod_/i.test(safeSlug);
  const isAlbLiveSlug = /^alb_/i.test(safeSlug);
  if (!isAlbVodSlug && !isAlbLiveSlug) return null;
  const sourceKey = isAlbVodSlug ? 'alb_vod' : 'alb';
  if (!isSourceAccessibleNow(sourceKey)) return null;
  const clientIpv4 = getLikelyClientIPv4(req);
  const triesRaw = Number(options.tries || 2);
  const tries = Number.isFinite(triesRaw) ? Math.min(Math.max(Math.floor(triesRaw), 1), 5) : 2;
