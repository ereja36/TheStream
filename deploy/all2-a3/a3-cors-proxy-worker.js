/**
 * A3 / Media-Colony HLS CORS proxy (Cloudflare Worker)
 *
 * Why: gcore-cdn.org / fastlycache block many datacenter IPs (e.g. OVH VPS → 404)
 * and do not send Access-Control-Allow-Origin, so hls.js on shifshqip.com cannot
 * read manifests. This Worker fetches from the CDN using Cloudflare egress and
 * adds CORS so the browser can play while media traffic stays off the VPS.
 *
 * Deploy (Cloudflare Dashboard → Workers → Create):
 *   1. Paste this script
 *   2. Add route: a3.shifshqip.com/*  OR  phinemi.live/a3proxy/*
 *   3. In player: hls.loadSource('https://a3.shifshqip.com/?u=' + encodeURIComponent(cdnUrl))
 *
 * Example:
 *   /?u=https://cache1.gcore-cdn.org/out/151225/external/vizionplus.m3u8
 */

const ALLOWED_HOSTS = [
  'cache1.gcore-cdn.org',
  'cache2.gcore-cdn.org',
  'cdn10eu.gcore-cdn.org',
  'app-measurement.fastlycache.com',
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

function isAllowed(url) {
  try {
    const u = new URL(url);
    return ALLOWED_HOSTS.some(h => u.hostname === h || u.hostname.endsWith('.' + h));
  } catch {
    return false;
  }
}

function rewriteManifest(text, requestUrl, proxyBase) {
  // Rewrite relative segment lines to absolute CDN URLs, then wrap via proxy
  const base = requestUrl;
  return text.split('\n').map(line => {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) {
      // URI="..." attributes
      return line.replace(/URI="([^"]+)"/g, (_, uri) => {
        const abs = new URL(uri, base).toString();
        return `URI="${proxyBase}?u=${encodeURIComponent(abs)}"`;
      });
    }
    const abs = new URL(trimmed, base).toString();
    return `${proxyBase}?u=${encodeURIComponent(abs)}`;
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

    const upstream = await fetch(target, {
      method: 'GET',
      headers: DEFAULT_HEADERS,
      redirect: 'follow',
      cf: { cacheTtl: 2, cacheEverything: false },
    });

    const ct = (upstream.headers.get('content-type') || '').toLowerCase();
    const headers = {
      ...corsHeaders(origin),
      'Content-Type': upstream.headers.get('content-type') || 'application/octet-stream',
      'Cache-Control': 'no-store',
    };

    if (upstream.status !== 200) {
      return new Response(await upstream.text(), { status: upstream.status, headers });
    }

    // HLS manifests: rewrite segment URLs through this proxy (so TS also get CORS)
    if (ct.includes('mpegurl') || ct.includes('application/vnd.apple') || ct.includes('text/plain') || ct.includes('octet-stream')) {
      const body = await upstream.text();
      if (body.trimStart().startsWith('#EXT')) {
        const proxyBase = `${src.origin}${src.pathname}`;
        const rewritten = rewriteManifest(body, target, proxyBase);
        headers['Content-Type'] = 'application/vnd.apple.mpegurl';
        return new Response(rewritten, { status: 200, headers });
      }
    }

    return new Response(await upstream.arrayBuffer(), { status: 200, headers });
  },
};
