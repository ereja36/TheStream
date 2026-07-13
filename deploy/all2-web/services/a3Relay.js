'use strict';

/**
 * Same-origin A3 relay for fastlycache (.js HLS gate) → anytools MPEG-TS.
 *
 * Free HTTP proxies are flaky: race several in parallel with short timeouts,
 * remember winners, and collapse gate→anytools into one video/mp2t response
 * (HLS.js cannot consume an endless MPEG-TS fragment).
 */

const axios = require('axios');
const { URL } = require('url');
const http = require('http');
const https = require('https');

const PROXY_LIST_URLS = [
  process.env.A3_PROXY_LIST_URL ||
    'https://api.proxyscrape.com/v2/?request=displayproxies&protocol=http&timeout=3000&country=all&ssl=all&anonymity=elite',
  'https://raw.githubusercontent.com/TheSpeedX/PROXY-List/master/http.txt',
  'https://raw.githubusercontent.com/clarketm/proxy-list/master/proxy-list-raw.txt'
].filter(Boolean);

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

const GATE_TIMEOUT_MS = 8000;
const STREAM_TIMEOUT_MS = 20000;
const FIRST_BYTE_MS = 10000;
const RACE_SIZE = 10;

let proxyPool = STATIC_PROXIES.slice();
let proxyPoolTs = 0;
const sticky = new Map();
const winners = [];
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

function normalizeProxyLine(line = '') {
  const p = String(line || '').trim().replace(/\r/g, '');
  if (!p) return '';
  if (/^https?:\/\//i.test(p)) return p;
  if (/^\d{1,3}(\.\d{1,3}){3}:\d+$/.test(p)) return `http://${p}`;
  return '';
}

async function refreshProxyPool(force = false) {
  if (!force && proxyPool.length && Date.now() - proxyPoolTs < 3 * 60 * 1000) {
    return proxyPool;
  }
  const merged = STATIC_PROXIES.slice();
  for (const listUrl of PROXY_LIST_URLS) {
    try {
      const res = await axios.get(listUrl, { timeout: 8000, responseType: 'text', validateStatus: () => true });
      if (res.status >= 400) continue;
      for (const line of String(res.data || '').split(/\r?\n/)) {
        const p = normalizeProxyLine(line);
        if (p) merged.push(p);
      }
    } catch {
      // keep going
    }
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
  if (!winners.includes(proxy)) {
    winners.unshift(proxy);
    if (winners.length > 40) winners.pop();
  }
}

function corsHeaders(origin = '*') {
  return {
    'Access-Control-Allow-Origin': origin || '*',
    'Access-Control-Allow-Methods': 'GET,HEAD,OPTIONS',
    'Access-Control-Allow-Headers': '*',
    'Access-Control-Expose-Headers': 'Content-Length, Content-Range, Accept-Ranges, Content-Type, X-A3-Relay-Format',
    'Cache-Control': 'no-store'
  };
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

function looksLikeGatePlaylist(buf, ct = '') {
  const peek = Buffer.from(buf || []).slice(0, 120).toString('utf8').trimStart();
  if (/<\s*html|404 Not Found|CyberPanel/i.test(peek)) return false;
  return peek.startsWith('#EXT') || /mpegurl|javascript|text\/plain/i.test(String(ct || ''));
}

async function axiosGet(targetUrl, headers, proxyUrl, opts = {}) {
  const proxy = parseProxy(proxyUrl);
  return axios.get(targetUrl, {
    headers,
    proxy,
    timeout: opts.timeout || GATE_TIMEOUT_MS,
    maxRedirects: opts.maxRedirects == null ? 5 : opts.maxRedirects,
    responseType: opts.responseType || 'arraybuffer',
    validateStatus: () => true,
    transitional: { clarifyTimeoutError: true },
    httpAgent: new http.Agent({ keepAlive: false, timeout: opts.timeout || GATE_TIMEOUT_MS }),
    httpsAgent: new https.Agent({
      keepAlive: false,
      rejectUnauthorized: false,
      timeout: opts.timeout || GATE_TIMEOUT_MS
    })
  });
}

function orderProxies(pool, stickyKey) {
  const prefer = pickSticky(stickyKey, pool);
  const ordered = [];
  const seen = new Set();
  const push = (p) => {
    if (!p || seen.has(p) || !pool.includes(p)) return;
    seen.add(p);
    ordered.push(p);
  };
  push(prefer);
  for (const w of winners) push(w);
  // Shuffle remainder lightly so races don't all hit the same dead proxies.
  const rest = pool.filter((p) => !seen.has(p));
  for (let i = rest.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1));
    [rest[i], rest[j]] = [rest[j], rest[i]];
  }
  for (const p of rest) push(p);
  return ordered;
}

async function raceGate(targetUrl, headers, proxies) {
  const batch = proxies.slice(0, RACE_SIZE);
  if (!batch.length) throw new Error('no proxy');

  let lastErr = 'race empty';
  let remaining = batch.length;
  return await new Promise((resolve, reject) => {
    let done = false;
    const finishOk = (payload) => {
      if (done) return;
      done = true;
      resolve(payload);
    };
    const finishErr = () => {
      remaining -= 1;
      if (!done && remaining <= 0) reject(new Error(lastErr || 'gate race failed'));
    };

    for (const proxy of batch) {
      axiosGet(targetUrl, headers, proxy, { timeout: GATE_TIMEOUT_MS, responseType: 'arraybuffer' })
        .then((up) => {
          if (done) return;
          if (up.status !== 200) {
            lastErr = `status ${up.status}`;
            finishErr();
            return;
          }
          const buf = Buffer.from(up.data || []);
          if (!looksLikeGatePlaylist(buf, up.headers['content-type'])) {
            lastErr = 'not playlist';
            finishErr();
            return;
          }
          finishOk({ up, buf, proxy });
        })
        .catch((err) => {
          lastErr = String(err && err.message ? err.message : err);
          finishErr();
        });
    }
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

  let gotByte = false;
  const firstByteTimer = setTimeout(() => {
    if (gotByte) return;
    try { upstream.data.destroy(); } catch {}
    try {
      if (!res.headersSent) {
        res.writeHead(502, { ...corsHeaders(origin), 'Content-Type': 'text/plain' });
      }
      res.end('A3 relay first-byte timeout');
    } catch {}
  }, FIRST_BYTE_MS);

  upstream.data.on('data', () => {
    if (!gotByte) {
      gotByte = true;
      clearTimeout(firstByteTimer);
    }
  });
  upstream.data.on('error', () => {
    clearTimeout(firstByteTimer);
  });
  upstream.data.on('end', () => clearTimeout(firstByteTimer));

  upstream.data.pipe(res);
  req.on('close', () => {
    clearTimeout(firstByteTimer);
    try { upstream.data.destroy(); } catch {}
  });
}

async function openAnytoolsStream(mediaUri, headers, proxy) {
  const up = await axiosGet(mediaUri, headers, proxy, {
    timeout: STREAM_TIMEOUT_MS,
    maxRedirects: 5,
    responseType: 'stream'
  });
  if (up.status !== 200) {
    try { up.data.destroy(); } catch {}
    throw new Error(`anytools ${up.status}`);
  }
  const ct = String(up.headers['content-type'] || '');
  if (/text\/html|application\/json/i.test(ct)) {
    try { up.data.destroy(); } catch {}
    throw new Error(`bad ctype ${ct}`);
  }
  return up;
}

/**
 * Open collapsed anytools MPEG-TS stream via residential HTTP proxy race.
 * Shared by /a3relay (pipe to client) and /a3hls (ffmpeg remux).
 */
async function openCollapsedTsStream(targetUrl, headers = {}, clientIp = '') {
  const target = String(targetUrl || '').trim();
  if (!/^https?:\/\//i.test(target) || !isAllowedUpstream(target)) {
    throw new Error('bad u');
  }
  const stickyKey = String(clientIp || 'anon');
  const pool = await refreshProxyPool(false);
  if (!pool.length) throw new Error('no proxy');

  const ordered = orderProxies(pool, stickyKey);
  let lastErr = '';

  if (isAnytoolsUrl(target)) {
    for (const proxy of ordered.slice(0, 15)) {
      try {
        const up = await openAnytoolsStream(target, headers, proxy);
        rememberSticky(stickyKey, proxy);
        return up;
      } catch (err) {
        lastErr = String(err && err.message ? err.message : err);
      }
    }
    throw new Error(lastErr || 'anytools');
  }

  for (let round = 0; round < 3; round += 1) {
    const slice = ordered.slice(round * RACE_SIZE, (round + 1) * RACE_SIZE);
    if (!slice.length) break;
    try {
      const { buf, proxy, up } = await raceGate(target, headers, slice);
      rememberSticky(stickyKey, proxy);
      const finalUrl = String(up.request?.res?.responseUrl || up.config?.url || target);
      const mediaUri = extractFirstMediaUri(buf.toString('utf8'), finalUrl);
      if (!mediaUri || !isAnytoolsUrl(mediaUri) || !isAllowedUpstream(mediaUri)) {
        throw new Error('no anytools uri');
      }
      const tsUp = await openAnytoolsStream(mediaUri, headers, proxy);
      return tsUp;
    } catch (err) {
      lastErr = String(err && err.message ? err.message : err);
    }
  }
  throw new Error(lastErr || 'proxies exhausted');
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
  if (!/^https?:\/\//i.test(target) || !isAllowedUpstream(target)) {
    res.writeHead(400, { ...corsHeaders(origin), 'Content-Type': 'text/plain' });
    return res.end('bad u');
  }

  const headers = decodeRh(rhRaw);
  const clientIp = String(options.clientIp || req.ip || req.headers['cf-connecting-ip'] || 'anon');
  try {
    const up = await openCollapsedTsStream(target, headers, clientIp);
    pipeMpegTs(req, res, up, origin);
  } catch (err) {
    const msg = String(err && err.message ? err.message : err);
    res.writeHead(502, { ...corsHeaders(origin), 'Content-Type': 'text/plain; charset=utf-8' });
    return res.end(`A3 relay failed (${msg}). Retry.`);
  }
}

module.exports = {
  handleA3Relay,
  openCollapsedTsStream,
  isAllowedUpstream,
  refreshProxyPool,
  decodeRh
};
