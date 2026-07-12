/**
 * A3 playback routing helpers (live on VPS /root/all/all2/server.js).
 *
 * Routing:
 * - App (X-Shif-App / ShifShqip UA): fastly → direct CDN URL
 *   (phone residential IP + PlaylistGuard aliez headers)
 * - Web Safari/iOS: fastly → direct_url + native HLS (viewer IP, no CORS needed)
 * - Other A3 CDNs / web Chrome: CF Worker `a3.phinemi.live` with per-channel `rh`
 * - preferPlayableAlbAlternate (albService): Top Channel tch → topcha crystalweb
 */

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

function wrapA3ClientSwUrl(streamUrl = '', options = {}) {
  const url = String(streamUrl || '').trim();
  if (!/^https?:\/\//i.test(url)) return url;
  if (String(url).includes(`${BASE_PATH}/a3sw`)) return url;
  const rh = String(options.rh || encodeA3UpstreamRh(options.headers || {}, options.userAgent || '')).trim();
  let out = `${BASE_PATH}/a3sw?u=${encodeURIComponent(url)}`;
  if (rh) out += `&rh=${encodeURIComponent(rh)}`;
  return out;
}

function buildA3PlaybackUrl(streamUrl = '', options = {}) {
  const url = String(streamUrl || '').trim();
  if (!/^https?:\/\//i.test(url)) return url;
  if (isAlbFastlyUrl(url)) {
    if (options.preferDirect === true) return url;
    if (options.preferClientSw === true) return wrapA3ClientSwUrl(url, options);
  }
  return wrapA3CorsProxyUrl(url, options);
}

function buildA3PlaybackOptionsFromChannel(channel = {}, req = null) {
  const fastly = isAlbFastlyUrl(channel?.url || '');
  const fromApp = req ? isShifAppRequest(req) : false;
  return {
    headers: channel?.albHeaders && typeof channel.albHeaders === 'object' ? channel.albHeaders : {},
    userAgent: String(channel?.albUserAgent || ALB_DEFAULT_UPSTREAM_UA || '').trim() || ALB_DEFAULT_UPSTREAM_UA,
    preferDirect: fastly && fromApp,
    preferClientSw: false
  };
}

// Call sites must pass req:
//   buildA3PlaybackUrl(streamUrl, buildA3PlaybackOptionsFromChannel(channel, req))
