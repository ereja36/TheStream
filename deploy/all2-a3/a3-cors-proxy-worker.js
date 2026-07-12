/**
 * A3 / Media-Colony HLS CORS proxy + per-user resolve (Cloudflare Worker)
 *
 * Routes:
 *   GET /?u=<cdnUrl>&rh=<base64url JSON headers>  → CORS media proxy
 *   GET /resolve?id=<channelId>                   → fresh config.php + proxy_url
 *
 * hls.js cannot set Referer / Origin / Cookie / User-Agent on cross-origin
 * CDN fetches. Per-channel headers must be injected here on every request
 * (master, variants, segments). Pass them as `rh` and preserve `rh` when
 * rewriting manifest URLs.
 */

const ALLOWED_HOST_SUFFIXES = [
  'gcore-cdn.org',
  'fastlycache.com',
  'akamaized.net',
  'akamaihd.net',
  'cloudfront.net',
  'mediaset.net',
  'bozztv.com',
  'sednastream.com',
  'video-stream-hosting.de',
  'onestream.studio',
  'ekranet.com',
  'mediadesk.al',
  'advanott.com',
  'rtsh.dev',
  'a2news.com',
  'syritv.al',
  'mcntv.al',
  'panorama.com.al',
  'ariu.eu.org',
  'protokolldns.xyz',
  'gjirafa.com',
  'gjirafa.net',
  'crystalweb.net',
  'duckdns.org',
  'aliez.tv',
];

const CONFIG_URL = 'https://adsportal.media-colony.com/users/AlbKanaleV9.1/config.php';
/** Always rewrite through the dedicated A3 host (not phinemi.live/a3proxy origin). */
const PROXY_ORIGIN = 'https://a3.phinemi.live';

const DEFAULT_HEADERS = {
  'User-Agent': 'APlayer/9.0 (Linux; Android 16) ExoPlayer/1.0',
  'Referer': 'https://aliez.tv/',
  'Origin': 'https://aliez.tv',
  'Cookie': 'passport=IG2GpFng03_23122025',
  'Accept': '*/*',
  'Accept-Encoding': 'identity',
};

function corsHeaders(origin) {
  return {
    'Access-Control-Allow-Origin': origin || '*',
    'Access-Control-Allow-Methods': 'GET,HEAD,OPTIONS',
    'Access-Control-Allow-Headers': '*',
    'Access-Control-Expose-Headers': '*',
    'Access-Control-Max-Age': '86400',
  };
}

function isMediaPath(pathname = '') {
  return /\.(m3u8|m3u|ts|m4s|mp4|aac|mp3|vtt|key)(\/|$)/i.test(pathname)
    || /\/(index|playlist|manifest)(\.m3u8)?$/i.test(pathname)
    || /\.php$/i.test(pathname)
    || /\.js$/i.test(pathname);
}

function isAllowed(url) {
  try {
    const u = new URL(url);
    if (u.protocol !== 'https:' && u.protocol !== 'http:') return false;
    const host = u.hostname.toLowerCase();
    if (ALLOWED_HOST_SUFFIXES.some((s) => host === s || host.endsWith('.' + s))) return true;
    return isMediaPath(u.pathname);
  } catch {
    return false;
  }
}

function rewriteCip(url, clientIp) {
  if (!clientIp || !/^\d{1,3}(\.\d{1,3}){3}$/.test(clientIp)) return url;
  if (!/[?&]cip=/i.test(url)) return url;
  return url.replace(/([?&]cip=)[^&]*/i, `$1${encodeURIComponent(clientIp)}`);
}

function b64UrlToBytes(b64url = '') {
  const pad = '='.repeat((4 - (b64url.length % 4)) % 4);
  const b64 = (b64url + pad).replace(/-/g, '+').replace(/_/g, '/');
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

function bytesToB64Url(bytes) {
  let bin = '';
  for (let i = 0; i < bytes.length; i++) bin += String.fromCharCode(bytes[i]);
  return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}

function decodeRh(rhRaw = '') {
  const raw = String(rhRaw || '').trim();
  if (!raw) return null;
  try {
    const text = new TextDecoder().decode(b64UrlToBytes(raw));
    const parsed = JSON.parse(text);
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return null;
    const out = {};
    for (const [k, v] of Object.entries(parsed)) {
      const name = String(k || '').trim();
      const value = String(v || '').trim();
      if (!name || !value) continue;
      if (/^(host|connection|content-length|accept-encoding|transfer-encoding)$/i.test(name)) continue;
      if (/^x-forwarded-for$/i.test(name) && /^(127\.0\.0\.1|::1|localhost)$/i.test(value)) continue;
      out[name] = value;
    }
    return Object.keys(out).length ? out : null;
  } catch {
    return null;
  }
}

function isDecoyManifest(text = '') {
  const body = String(text || '');
  const lower = body.toLowerCase();
  if (lower.includes('anytools.eu.org')) return true;
  if (lower.includes('unique-user-id=')) return true;
  // Classic Media-Colony DC-block stub: ENDLIST + single 0-duration entry.
  if (/#ext-x-endlist/i.test(body) && /#extinf:\s*0/i.test(body) && body.split('\n').filter((l) => l.trim() && !l.trim().startsWith('#')).length <= 1) {
    return true;
  }
  return false;
}

function encodeRh(headers = {}) {
  try {
    const json = JSON.stringify(headers);
    return bytesToB64Url(new TextEncoder().encode(json));
  } catch {
    return '';
  }
}

function buildUpstreamHeaders(rhHeaders, clientIp = '') {
  const out = {
    Accept: '*/*',
    'Accept-Encoding': 'identity',
  };
  if (rhHeaders) {
    Object.assign(out, rhHeaders);
  } else {
    Object.assign(out, DEFAULT_HEADERS);
  }
  if (!Object.keys(out).some((k) => k.toLowerCase() === 'user-agent')) {
    out['User-Agent'] = DEFAULT_HEADERS['User-Agent'];
  }
  // Prefer channel XFF when present; otherwise stamp viewer IP for CDNs that look at it.
  const hasXff = Object.keys(out).some((k) => k.toLowerCase() === 'x-forwarded-for');
  if (!hasXff && clientIp && /^\d{1,3}(\.\d{1,3}){3}$/.test(clientIp)) {
    out['X-Forwarded-For'] = clientIp;
  }
  return out;
}

function proxyPrefix(proxyBase, rh = '') {
  const base = proxyBase.endsWith('/') ? `${proxyBase}?u=` : `${proxyBase}/?u=`;
  const rhPart = rh ? `&rh=${encodeURIComponent(rh)}` : '';
  return { base, rhPart };
}

function rewriteManifest(text, requestUrl, proxyBase, rh = '') {
  const { base, rhPart } = proxyPrefix(proxyBase, rh);
  return text.split('\n').map((line) => {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) {
      return line.replace(/URI="([^"]+)"/g, (_, uri) => {
        const abs = new URL(uri, requestUrl).toString();
        return `URI="${base}${encodeURIComponent(abs)}${rhPart}"`;
      });
    }
    const abs = new URL(trimmed, requestUrl).toString();
    return `${base}${encodeURIComponent(abs)}${rhPart}`;
  }).join('\n');
}

function deviceFromIp(ip = '') {
  let hash = 0;
  const s = `a3|${ip || 'anon'}`;
  for (let i = 0; i < s.length; i++) hash = ((hash << 5) - hash + s.charCodeAt(i)) | 0;
  return Math.abs(hash).toString(16).padStart(8, '0') + Math.abs(hash * 31).toString(16).padStart(8, '0');
}

function normalizeChannelHeaders(headers = {}, customUa = '') {
  const out = {};
  for (const [rawName, rawValue] of Object.entries(headers || {})) {
    const name = String(rawName || '').trim();
    const value = String(rawValue || '').trim();
    if (!name || !value) continue;
    out[name] = value;
  }
  const ua = String(customUa || '').trim();
  if (ua && !Object.keys(out).some((k) => k.toLowerCase() === 'user-agent')) {
    out['User-Agent'] = ua;
  }
  return out;
}

async function resolveChannel(request) {
  const src = new URL(request.url);
  const id = String(src.searchParams.get('id') || '').trim().toLowerCase();
  const origin = request.headers.get('Origin') || '*';
  if (!id) {
    return new Response(JSON.stringify({ ok: false, error: 'missing id' }), {
      status: 400,
      headers: { ...corsHeaders(origin), 'Content-Type': 'application/json' },
    });
  }
  const clientIp = (request.headers.get('CF-Connecting-IP') || '').split(',')[0].trim();
  const device = deviceFromIp(clientIp).slice(0, 20);
  const update = String(Math.floor(Date.now() / 1000));
  const configUrl = `${CONFIG_URL}?device=${encodeURIComponent(device)}&android=16&model=${encodeURIComponent('Google Pixel 9 Pro XL')}`;
  const upstream = await fetch(configUrl, {
    headers: {
      'User-Agent': 'Android Vinebre Software',
      'Referer': 'https://www.digitalturbine.com/',
      'Host': 'adsportal.media-colony.com',
      'Connection': 'Keep-Alive',
      'Accept-Encoding': 'gzip',
      'Cookie': `Update=${update}`,
    },
  });
  if (!upstream.ok) {
    return new Response(JSON.stringify({ ok: false, error: `config ${upstream.status}` }), {
      status: 502,
      headers: { ...corsHeaders(origin), 'Content-Type': 'application/json' },
    });
  }
  const body = await upstream.json();
  const channels = Array.isArray(body.channels) ? body.channels : [];
  const hit = channels.find((c) => String(c?.id || '').trim().toLowerCase() === id);
  if (!hit) {
    return new Response(JSON.stringify({ ok: false, error: 'channel not found' }), {
      status: 404,
      headers: { ...corsHeaders(origin), 'Content-Type': 'application/json' },
    });
  }
  let url = String(hit.url || '');
  url = rewriteCip(url, clientIp);
  try { url = new URL(url).toString(); } catch { url = url.replace(/ /g, '%20'); }

  const streamHeaders = normalizeChannelHeaders(hit.headers || {}, hit.custom_user_agent || '');
  const rh = encodeRh(streamHeaders);
  const proxyBase = `${PROXY_ORIGIN}/`;
  const { base, rhPart } = proxyPrefix(proxyBase, rh);
  const proxyUrl = `${base}${encodeURIComponent(url)}${rhPart}`;

  return new Response(JSON.stringify({
    ok: true,
    id: hit.id,
    name: hit.name,
    url,
    proxy_url: proxyUrl,
    headers: hit.headers || {},
    custom_user_agent: hit.custom_user_agent || '',
    client_ip: clientIp || '',
    device,
  }), {
    status: 200,
    headers: { ...corsHeaders(origin), 'Content-Type': 'application/json', 'Cache-Control': 'no-store' },
  });
}

export default {
  async fetch(request) {
    const origin = request.headers.get('Origin') || '*';
    if (request.method === 'OPTIONS') {
      return new Response(null, { status: 204, headers: corsHeaders(origin) });
    }

    const src = new URL(request.url);
    if (src.pathname === '/resolve' || src.pathname.endsWith('/resolve')) {
      return resolveChannel(request);
    }

    const target = src.searchParams.get('u') || src.searchParams.get('url') || '';
    if (!target || !isAllowed(target)) {
      return new Response('Forbidden or missing ?u=', { status: 400, headers: corsHeaders(origin) });
    }

    const rhRaw = src.searchParams.get('rh') || '';
    const rhHeaders = decodeRh(rhRaw);
    const clientIp = (request.headers.get('CF-Connecting-IP') || '').split(',')[0].trim();
    const upstreamHeaders = buildUpstreamHeaders(rhHeaders, clientIp);

    let upstream;
    try {
      upstream = await fetch(target, {
        method: 'GET',
        headers: upstreamHeaders,
        redirect: 'follow',
        cf: { cacheTtl: 2, cacheEverything: false },
      });
    } catch (err) {
      return new Response(`Upstream fetch failed: ${String(err)}`, {
        status: 502,
        headers: corsHeaders(origin),
      });
    }

    const ct = (upstream.headers.get('content-type') || '').toLowerCase();
    const headers = {
      ...corsHeaders(origin),
      'Content-Type': upstream.headers.get('content-type') || 'application/octet-stream',
      'Cache-Control': 'no-store',
    };

    if (upstream.status !== 200) {
      return new Response(await upstream.text(), { status: upstream.status, headers });
    }

    const proxyBase = `${PROXY_ORIGIN}/`;
    // Keep the same rh token on rewritten URLs (re-encode from applied headers if needed).
    const rhKeep = rhRaw || (rhHeaders ? encodeRh(rhHeaders) : '');

    // Peek body: Media-Colony often disguises m3u8 as .js — detect by #EXT magic.
    const raw = await upstream.arrayBuffer();
    const peek = new TextDecoder().decode(raw.slice(0, Math.min(raw.byteLength, 64))).trimStart();
    if (peek.startsWith('#EXT')) {
      const body = new TextDecoder().decode(raw);
      if (isDecoyManifest(body)) {
        return new Response('A3 CDN blocked this edge (datacenter/proxy IP). Try again from a residential network or use an alternate feed.', {
          status: 502,
          headers: { ...corsHeaders(origin), 'Content-Type': 'text/plain; charset=utf-8', 'Cache-Control': 'no-store' },
        });
      }
      headers['Content-Type'] = 'application/vnd.apple.mpegurl';
      return new Response(rewriteManifest(body, target, proxyBase, rhKeep), { status: 200, headers });
    }

    return new Response(raw, { status: 200, headers });
  },
};
