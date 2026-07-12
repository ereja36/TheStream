/**
 * Snippet applied on VPS /root/all/all2/server.js (A3 per-channel headers → Worker `rh`).
 * Replaces wrapA3CorsProxyUrl / buildA3PlaybackUrl and adds helpers used by channel redirects
 * and /api/channel-url for alb / alb_vod.
 */
/** Encode per-channel CDN headers for the CF Worker (`rh` query param). */
function encodeA3UpstreamRh(headers = {}, userAgent = '') {
  const payload = {};
  for (const [rawKey, rawValue] of Object.entries(headers && typeof headers === 'object' ? headers : {})) {
    const name = String(rawKey || '').trim();
    const value = String(rawValue || '').trim();
    if (!name || !value) continue;
    if (/^(host|connection|content-length|accept-encoding|transfer-encoding)$/i.test(name)) continue;
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
/**
 * Always route A3 through the CF Worker so hls.js gets CORS + per-channel
 * upstream headers (Referer/Origin/Cookie/UA) on master, variants, and segments.
 * Browsers cannot set those headers themselves.
 */
function buildA3PlaybackUrl(streamUrl = '', options = {}) {
  const url = String(streamUrl || '').trim();
  if (!/^https?:\/\//i.test(url)) return url;
  return wrapA3CorsProxyUrl(url, options);
}
function buildA3PlaybackOptionsFromChannel(channel = {}) {
  return {
    headers: channel?.albHeaders && typeof channel.albHeaders === 'object' ? channel.albHeaders : {},
    userAgent: String(channel?.albUserAgent || ALB_DEFAULT_UPSTREAM_UA || '').trim() || ALB_DEFAULT_UPSTREAM_UA
  };
}

// Call sites (alb / alb_vod):
//   buildA3PlaybackUrl(streamUrl, buildA3PlaybackOptionsFromChannel(channel))
// channel-url JSON also returns headers + user_agent for native clients.
