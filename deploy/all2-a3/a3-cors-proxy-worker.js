/**
 * A3 / Media-Colony HLS CORS proxy (Cloudflare Worker)
 *
 * Routes:
 *   https://a3.phinemi.live/?u=<cdnUrl>
 *   https://phinemi.live/a3proxy/?u=<cdnUrl>
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

export default {
  async fetch(request) {
    const origin = request.headers.get('Origin') || '*';
    if (request.method === 'OPTIONS') {
      return new Response(null, { status: 204, headers: corsHeaders(origin) });
    }

    const src = new URL(request.url);
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

    // Always rewrite via https://host/?u= (trailing slash) so Workers route patterns match.
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
