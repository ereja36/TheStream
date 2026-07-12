/**
 * A3 / Media-Colony HLS CORS proxy + per-user resolve (Cloudflare Worker)
 *
 * Routes:
 *   GET /?u=<cdnUrl>           → CORS media proxy
 *   GET /resolve?id=<channelId> → fresh config.php, stamp cip=CF-Connecting-IP
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
];

const CONFIG_URL = 'https://adsportal.media-colony.com/users/AlbKanaleV9.1/config.php';

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

function rewriteManifest(text, requestUrl, proxyBase) {
  const prefix = proxyBase.endsWith('/') ? `${proxyBase}?u=` : `${proxyBase}/?u=`;
  return text.split('\n').map((line) => {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) {
      return line.replace(/URI="([^"]+)"/g, (_, uri) => {
        const abs = new URL(uri, requestUrl).toString();
        return `URI="${prefix}${encodeURIComponent(abs)}"`;
      });
    }
    const abs = new URL(trimmed, requestUrl).toString();
    return `${prefix}${encodeURIComponent(abs)}`;
  }).join('\n');
}

function deviceFromIp(ip = '') {
  let hash = 0;
  const s = `a3|${ip || 'anon'}`;
  for (let i = 0; i < s.length; i++) hash = ((hash << 5) - hash + s.charCodeAt(i)) | 0;
  return Math.abs(hash).toString(16).padStart(8, '0') + Math.abs(hash * 31).toString(16).padStart(8, '0');
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
  return new Response(JSON.stringify({
    ok: true,
    id: hit.id,
    name: hit.name,
    url,
    headers: hit.headers || {},
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

    let upstream;
    try {
      upstream = await fetch(target, {
        method: 'GET',
        headers: DEFAULT_HEADERS,
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

    const proxyBase = `${src.origin}/`;

    if (
      ct.includes('mpegurl')
      || ct.includes('application/vnd.apple')
      || ct.includes('text/plain')
      || ct.includes('octet-stream')
      || /\.m3u8(\?|$)/i.test(target)
    ) {
      const body = await upstream.text();
      if (body.trimStart().startsWith('#EXT')) {
        headers['Content-Type'] = 'application/vnd.apple.mpegurl';
        return new Response(rewriteManifest(body, target, proxyBase), { status: 200, headers });
      }
    }

    return new Response(await upstream.arrayBuffer(), { status: 200, headers });
  },
};
