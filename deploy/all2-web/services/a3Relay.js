'use strict';

/**
 * Same-origin A3 relay for fastlycache (.js HLS gate) → anytools MPEG-TS.
 * Web Chrome cannot use CF Worker (DC IP blocked). HTTP proxies can reach the
 * gate; this module fetches via a sticky proxy pool.
 *
 * For browser playback we collapse gate→anytools into one video/mp2t response
 * (HLS.js cannot consume an endless MPEG-TS "fragment").
 */

const axios = require('axios');
const { URL } = require('url');
const http = require('http');
const https = require('https');

const PROXY_LIST_URL =
  process.env.A3_PROXY_LIST_URL ||
  'https://api.proxyscrape.com/v2/?request=displayproxies&protocol=http&timeout=5000&country=all&ssl=all&anonymity=elite';
const STATIC_PROXIES = String(process.env.A3_FASTLY_HTTP_PROXIES || '')
  .split(',')
  .map((s) => s.trim())
  .filter(Boolean);

const DEFAULT_HEADERS = {
  'User-Agent': 'APlayer/9.0 (Linux; Android 17) ExoPlayer/1.0',
  Referer: 'https://aliez.tv/',
  Origin: 'https://aliez.tv',
  Cookie: 'passport=IG2GpFng03_23122025',
  'X-Forwarded-For': '127.0.0.1',
  'Icy-MetaData': '1',
  Accept: '*/*',
  'Accept-Encoding': 'identity'
};

let proxyPool = STATIC_PROXIES.slice();
let proxyPoolTs = 0;
const sticky = new Map();
const PROXY_TTL_MS = 10 * 60 * 1000;

function isAllowedUpstream(url = '') {
  try {
    const host = new URL(url).hostname.toLowerCase();
    return (
      host.endsWith('fastlycache.com') ||
      host.endsWith('anytools.eu.org') ||
      host.includes('anytools')
    );
  } catch {
    return false;
  }
}

function parseProxy(proxyUrl) {
  const u = new URL(proxyUrl);
  return {
    protocol: u.protocol.replace(':', ''),
    host: u.hostname,
    port: Number(u.port || 80)
  };
}

async function refreshProxyPool(force = false) {
  if (!force && proxyPool.length && Date.now() - proxyPoolTs < 5 * 60 * 1000) {
    return proxyPool;
  }
  const merged = STATIC_PROXIES.slice();
  try {
    const res = await axios.get(PROXY_LIST_URL, { timeout: 10000, responseType: 'text' });
    const text = String(res.data || '');
    for (const line of text.split(/\r?\n/)) {
      const p = String(line || '').trim();
      if (/^\d{1,3}(\.\d{1,3}){3}:\d+$/.test(p)) merged.push(`http://${p}`);
    }
  } catch {
    // keep previous
  }
  proxyPool = [...new Set(merged)];
  proxyPoolTs = Date.now();
  return proxyPool;
}

function decodeRh(rhRaw = '') {
  const raw = String(rhRaw || '').trim();
  if (!raw) return { ...DEFAULT_HEADERS };
  try {
    const pad = '='.repeat((4 - (raw.length % 4)) % 4);
    const b64 = (raw + pad).replace(/-/g, '+').replace(/_/g, '/');
    const json = Buffer.from(b64, 'base64').toString('utf8');
    const parsed = JSON.parse(json);
    const out = { ...DEFAULT_HEADERS };
    for (const [k, v] of Object.entries(parsed || {})) {
      const name = String(k || '').trim();
      const value = String(v || '').trim();
      if (!name || !value) continue;
      if (/^(host|connection|content-length|transfer-encoding)$/i.test(name)) continue;
      out[name] = value;
    }
    return out;
  } catch {
    return { ...DEFAULT_HEADERS };
  }
}

function pickSticky(key, pool) {
  const hit = sticky.get(key);
  if (hit && Date.now() - hit.ts < PROXY_TTL_MS && pool.includes(hit.proxy)) return hit.proxy;
  return '';
}

function rememberSticky(key, proxy) {
  if (!key || !proxy) return;
  sticky.set(key, { proxy, ts: Date.now() });
  if (sticky.size > 500) sticky.delete(sticky.keys().next().value);
}

function corsHeaders(origin = '*') {
  return {
    'Access-Control-Allow-Origin': origin || '*',
    'Access-Control-Allow-Methods': 'GET,HEAD,OPTIONS',
    'Access-Control-Allow-Headers': '*',
    'Access-Control-Expose-Headers': 'Content-Length, Content-Range, Accept-Ranges, Content-Type',
    'Cache-Control': 'no-store'
  };
}

function rewriteManifest(text, requestUrl, relayBase, rh) {
  const rhPart = rh ? `&rh=${encodeURIComponent(rh)}` : '';
  const base = `${relayBase}?u=`;
  return String(text || '')
    .split('\n')
    .map((line) => {
      const trim = line.trim();
      if (/^#EXTINF:\s*0\s*,?/i.test(trim)) return '#EXTINF:6.000,';
      if (/^#EXT-X-ENDLIST/i.test(trim)) return '';
      if (!trim || trim.startsWith('#')) {
        return line.replace(/URI="([^"]+)"/gi, (_, u) => {
          try {
            const abs = new URL(u, requestUrl).toString();
            return `URI="${base}${encodeURIComponent(abs)}${rhPart}"`;
          } catch {
            return _;
          }
        });
      }
      try {
        const abs = new URL(trim, requestUrl).toString();
        return `${base}${encodeURIComponent(abs)}${rhPart}`;
      } catch {
        return line;
      }
    })
    .filter((l, i, arr) => l !== '' || (arr[i - 1] || '') !== '')
    .join('\n');
}

function extractFirstMediaUri(manifestText = '', baseUrl = '') {
  for (const raw of String(manifestText || '').split(/\r?\n/)) {
    const trim = raw.trim();
    if (!trim || trim.startsWith('#')) continue;
    try {
      return new URL(trim, baseUrl || undefined).toString();
    } catch {
      return trim;
    }
  }
  return '';
}

function isAnytoolsUrl(url = '') {
  return /anytools\.eu\.org|unique-user-id=/i.test(String(url || ''));
}

async function axiosGet(targetUrl, headers, proxyUrl, opts = {}) {
  const proxy = parseProxy(proxyUrl);
  return axios.get(targetUrl, {
    headers,
    proxy,
    timeout: opts.timeout || 20000,
    maxRedirects: opts.maxRedirects == null ? 5 : opts.maxRedirects,
    responseType: opts.responseType || 'arraybuffer',
    validateStatus: () => true,
    httpAgent: new http.Agent({ keepAlive: false }),
    httpsAgent: new https.Agent({ keepAlive: false, rejectUnauthorized: false })
  });
}

function pipeMpegTs(req, res, upstream, origin) {
  const ct = String(upstream.headers['content-type'] || 'video/mp2t');
  if (/text\/html|application\/json/i.test(ct)) {
    try { upstream.data.destroy(); } catch {}
    throw new Error(`bad ctype ${ct}`);
  }
  res.writeHead(200, {
    ...corsHeaders(origin),
    'Content-Type': /mp2t|octet-stream|video\//i.test(ct) ? 'video/mp2t' : ct,
    'X-A3-Relay-Format': 'mpegts'
  });
  upstream.data.pipe(res);
  req.on('close', () => {
    try { upstream.data.destroy(); } catch {}
  });
}

async function handleA3Relay(req, res, options = {}) {
  const origin = req.headers.origin || '*';
  if (req.method === 'OPTIONS') {
    res.writeHead(204, corsHeaders(origin));
    return res.end();
  }

  let target = String(req.query?.u || req.query?.url || '').trim();
  const encId = String(req.query?.id || '').trim();
  if (!target && encId && typeof options.decrypt === 'function') {
    try {
      const decoded = options.decrypt(encId);
      if (decoded && /^https?:\/\//i.test(decoded)) target = decoded;
    } catch {
      // ignore
    }
  }
  const rhRaw = String(req.query?.rh || '').trim();
  // fmt=hls keeps classic rewritten playlist (debug). Default collapses to MPEG-TS for web.
  const preferHls = String(req.query?.fmt || '').trim().toLowerCase() === 'hls';
  if (!/^https?:\/\//i.test(target) || !isAllowedUpstream(target)) {
    res.writeHead(400, { ...corsHeaders(origin), 'Content-Type': 'text/plain' });
    return res.end('bad u');
  }

  const headers = decodeRh(rhRaw);
  // Sticky by client only — gate (.js) and anytools hop must share the same egress IP.
  const stickyKey = String(options.clientIp || req.ip || req.headers['cf-connecting-ip'] || 'anon');
  const pool = await refreshProxyPool(false);
  if (!pool.length) {
    res.writeHead(503, { ...corsHeaders(origin), 'Content-Type': 'text/plain' });
    return res.end('no proxy');
  }

  const prefer = pickSticky(stickyKey, pool);
  const ordered = prefer ? [prefer, ...pool.filter((p) => p !== prefer)] : pool.slice();
  const relayBase = String(options.relayBase || '/k1/a3relay');
  const wantStream = isAnytoolsUrl(target);

  let lastErr = '';
  for (const proxy of ordered.slice(0, 25)) {
    try {
      if (wantStream) {
        const up = await axiosGet(target, headers, proxy, {
          timeout: 45000,
          maxRedirects: 5,
          responseType: 'stream'
        });
        if (up.status !== 200) {
          try { up.data.destroy(); } catch {}
          throw new Error(`upstream ${up.status}`);
        }
        rememberSticky(stickyKey, proxy);
        pipeMpegTs(req, res, up, origin);
        return;
      }

      const up = await axiosGet(target, headers, proxy, { timeout: 20000, maxRedirects: 5 });
      if (up.status !== 200) throw new Error(`status ${up.status}`);
      const buf = Buffer.from(up.data || []);
      const peek = buf.slice(0, 64).toString('utf8').trimStart();
      const ct = String(up.headers['content-type'] || '');
      if (/<\s*html|404 Not Found|CyberPanel/i.test(peek)) {
        throw new Error('html block');
      }
      rememberSticky(stickyKey, proxy);

      if (peek.startsWith('#EXT') || /mpegurl|javascript|text\/plain/i.test(ct)) {
        const finalUrl = String(up.request?.res?.responseUrl || up.config?.url || target);
        const manifestText = buf.toString('utf8');
        const mediaUri = extractFirstMediaUri(manifestText, finalUrl);

        // Default web path: follow anytools on the SAME proxy and stream MPEG-TS.
        // HLS.js cannot finish downloading an endless TS "fragment".
        if (!preferHls && mediaUri && isAnytoolsUrl(mediaUri) && isAllowedUpstream(mediaUri)) {
          const tsUp = await axiosGet(mediaUri, headers, proxy, {
            timeout: 45000,
            maxRedirects: 5,
            responseType: 'stream'
          });
          if (tsUp.status !== 200) {
            try { tsUp.data.destroy(); } catch {}
            throw new Error(`anytools ${tsUp.status}`);
          }
          pipeMpegTs(req, res, tsUp, origin);
          return;
        }

        const body = rewriteManifest(manifestText, finalUrl, relayBase, rhRaw);
        res.writeHead(200, {
          ...corsHeaders(origin),
          'Content-Type': 'application/vnd.apple.mpegurl',
          'X-A3-Relay-Format': 'hls'
        });
        return res.end(body);
      }
      if (buf[0] === 0x47 || /mp2t|octet-stream/i.test(ct)) {
        res.writeHead(200, {
          ...corsHeaders(origin),
          'Content-Type': 'video/mp2t',
          'X-A3-Relay-Format': 'mpegts'
        });
        return res.end(buf);
      }
      res.writeHead(200, {
        ...corsHeaders(origin),
        'Content-Type': ct || 'application/octet-stream'
      });
      return res.end(buf);
    } catch (err) {
      lastErr = String(err && err.message ? err.message : err);
    }
  }

  res.writeHead(502, { ...corsHeaders(origin), 'Content-Type': 'text/plain; charset=utf-8' });
  return res.end(`A3 relay failed (${lastErr || 'proxies exhausted'}). Retry.`);
}

module.exports = {
  handleA3Relay,
  isAllowedUpstream,
  refreshProxyPool,
  decodeRh
};
