'use strict';

/**
 * Remux A3 fastly/anytools MPEG-TS → short HLS for web HLS.js.
 * Reuses a3Relay proxy race (AlbKanale ExoPlayer hop chain), then ffmpeg -c copy.
 * Disk-backed + lockfile so PM2 cluster workers share one remux per session.
 */

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { spawn } = require('child_process');
const a3Relay = require('./a3Relay');

const ROOT = process.env.A3_HLS_DIR || path.join(require('os').tmpdir(), 'a3hls');
const SESSION_TTL_MS = 3 * 60 * 1000;
const sessions = new Map();

function ensureRoot() {
  fs.mkdirSync(ROOT, { recursive: true });
}

function sessionIdFrom(target, clientIp) {
  // Prefer stream identity over client IP so prefetch (API worker) and
  // browser /a3hls hit (any worker) share the same remux dir.
  return crypto
    .createHash('sha1')
    .update(String(target || '').split('?')[0])
    .digest('hex')
    .slice(0, 16);
}

function playlistPath(sid) {
  return path.join(ROOT, sid, 'index.m3u8');
}

function lockPath(sid) {
  return path.join(ROOT, sid, '.lock');
}

function playlistReady(sid) {
  try {
    const body = fs.readFileSync(playlistPath(sid), 'utf8');
    return /#EXTINF/i.test(body) && /\.ts/i.test(body);
  } catch {
    return false;
  }
}

function isLockFresh(sid, maxAgeMs = 45000) {
  try {
    const st = fs.statSync(lockPath(sid));
    return Date.now() - st.mtimeMs < maxAgeMs;
  } catch {
    return false;
  }
}

function writeLock(sid) {
  ensureRoot();
  const dir = path.join(ROOT, sid);
  fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(lockPath(sid), String(Date.now()));
}

function clearLock(sid) {
  try { fs.unlinkSync(lockPath(sid)); } catch {}
}

function cleanupSession(sid) {
  const s = sessions.get(sid);
  if (s) {
    try { if (s.ffmpeg) s.ffmpeg.kill('SIGKILL'); } catch {}
    try { if (s.upstream && s.upstream.data) s.upstream.data.destroy(); } catch {}
    sessions.delete(sid);
  }
  clearLock(sid);
  const dir = path.join(ROOT, sid);
  try {
    for (const f of fs.readdirSync(dir)) fs.unlinkSync(path.join(dir, f));
    fs.rmdirSync(dir);
  } catch {}
}

function touch(sid) {
  const s = sessions.get(sid);
  if (s) s.lastAccess = Date.now();
  try {
    fs.writeFileSync(path.join(ROOT, sid, '.touch'), String(Date.now()));
  } catch {}
}

setInterval(() => {
  const now = Date.now();
  for (const [sid, s] of sessions.entries()) {
    if (now - Number(s.lastAccess || 0) > SESSION_TTL_MS) cleanupSession(sid);
  }
  try {
    ensureRoot();
    for (const sid of fs.readdirSync(ROOT)) {
      if (sessions.has(sid)) continue;
      let ts = 0;
      try { ts = Number(fs.readFileSync(path.join(ROOT, sid, '.touch'), 'utf8')) || 0; } catch {}
      if (!ts) {
        try { ts = fs.statSync(path.join(ROOT, sid)).mtimeMs; } catch { continue; }
      }
      if (now - ts > SESSION_TTL_MS) cleanupSession(sid);
    }
  } catch {}
}, 30000).unref?.();

function startFfmpeg(sid, upstreamRes) {
  ensureRoot();
  const dir = path.join(ROOT, sid);
  fs.mkdirSync(dir, { recursive: true });
  const playlist = path.join(dir, 'index.m3u8');
  const segPattern = path.join(dir, 'seg_%05d.ts');
  const ff = spawn(
    'ffmpeg',
    [
      '-hide_banner',
      '-loglevel', 'error',
      '-fflags', '+genpts+igndts+discardcorrupt',
      '-i', 'pipe:0',
      '-c', 'copy',
      '-f', 'hls',
      '-hls_time', '2',
      '-hls_list_size', '6',
      '-hls_flags', 'delete_segments+append_list+omit_endlist',
      '-hls_segment_filename', segPattern,
      playlist
    ],
    { stdio: ['pipe', 'ignore', 'pipe'] }
  );
  ff.stderr.on('data', () => {});
  const stream = upstreamRes.data;
  stream.pipe(ff.stdin);
  stream.on('error', () => {
    try { ff.kill('SIGKILL'); } catch {}
  });
  ff.on('exit', () => {
    const s = sessions.get(sid);
    if (s) s.dead = true;
    clearLock(sid);
  });
  return ff;
}

async function waitPlaylist(sid, timeoutMs = 28000) {
  const t0 = Date.now();
  while (Date.now() - t0 < timeoutMs) {
    if (playlistReady(sid)) return;
    const s = sessions.get(sid);
    if (s && s.dead && !playlistReady(sid)) throw new Error('ffmpeg exited before playlist');
    await new Promise((r) => setTimeout(r, 300));
  }
  throw new Error('hls playlist timeout');
}

async function startLocalRemux(sid, target, headers, clientIp) {
  writeLock(sid);
  try {
    const upstream = await a3Relay.openCollapsedTsStream(target, headers, clientIp);
    const ffmpeg = startFfmpeg(sid, upstream);
    sessions.set(sid, {
      target,
      clientIp,
      ffmpeg,
      upstream,
      lastAccess: Date.now(),
      dead: false
    });
    touch(sid);
    await waitPlaylist(sid, 28000);
    clearLock(sid);
    return sid;
  } catch (err) {
    cleanupSession(sid);
    throw err;
  }
}

async function ensureSession({ target, headers, clientIp }) {
  const sid = sessionIdFrom(target, clientIp);
  touch(sid);

  if (playlistReady(sid)) return sid;

  // Another worker already remuxing this stream — wait on disk.
  if (isLockFresh(sid) || (sessions.get(sid) && !sessions.get(sid).dead)) {
    await waitPlaylist(sid, 35000);
    if (playlistReady(sid)) return sid;
  }

  let lastErr = '';
  for (let attempt = 1; attempt <= 3; attempt += 1) {
    try {
      if (playlistReady(sid)) return sid;
      if (isLockFresh(sid)) {
        await waitPlaylist(sid, 20000);
        if (playlistReady(sid)) return sid;
      }
      return await startLocalRemux(sid, target, headers, clientIp);
    } catch (err) {
      lastErr = String(err && err.message ? err.message : err);
    }
  }
  throw new Error(lastErr || 'remux failed');
}

function prefetchSession(target, headers, clientIp) {
  if (!target) return;
  ensureSession({ target, headers, clientIp }).catch(() => {});
}

function cors(origin = '*') {
  return {
    'Access-Control-Allow-Origin': origin || '*',
    'Access-Control-Allow-Methods': 'GET,HEAD,OPTIONS',
    'Access-Control-Allow-Headers': '*',
    'Cache-Control': 'no-store'
  };
}

function safeSid(value = '') {
  return /^[a-f0-9]{8,32}$/i.test(String(value || '')) ? String(value) : '';
}

function safeFile(value = '') {
  return /^(index\.m3u8|seg_\d+\.ts)$/i.test(String(value || '')) ? String(value) : '';
}

async function handleA3Hls(req, res, options = {}) {
  const origin = req.headers.origin || '*';
  if (req.method === 'OPTIONS') {
    res.writeHead(204, cors(origin));
    return res.end();
  }

  let sid = safeSid(req.params?.sid || '');
  let file = safeFile(req.params?.file || '');
  if (!sid || !file) {
    const parts = String(req.path || req.url || '').split('?')[0].split('/').filter(Boolean);
    const idx = parts.indexOf('a3hls');
    if (idx >= 0 && parts[idx + 1] && parts[idx + 2]) {
      sid = safeSid(parts[idx + 1]);
      file = safeFile(parts[idx + 2]);
    }
  }

  if (sid && file) {
    touch(sid);
    const full = path.join(ROOT, sid, file);
    const rootSid = path.join(ROOT, sid);
    if (!full.startsWith(rootSid) || !fs.existsSync(full)) {
      res.writeHead(404, cors(origin));
      return res.end('missing');
    }
    const ct = file.endsWith('.m3u8') ? 'application/vnd.apple.mpegurl' : 'video/mp2t';
    res.writeHead(200, { ...cors(origin), 'Content-Type': ct });
    fs.createReadStream(full).pipe(res);
    return;
  }

  let target = String(req.query?.u || req.query?.url || '').trim();
  const encId = String(req.query?.id || '').trim();
  if (!target && encId && typeof options.decrypt === 'function') {
    try {
      const decoded = options.decrypt(encId);
      if (decoded && /^https?:\/\//i.test(decoded)) target = decoded;
    } catch {}
  }
  if (!/^https?:\/\//i.test(target) || !a3Relay.isAllowedUpstream(target)) {
    res.writeHead(400, { ...cors(origin), 'Content-Type': 'text/plain' });
    return res.end('bad u');
  }

  const headers = a3Relay.decodeRh(String(req.query?.rh || ''));
  if (!headers['User-Agent']) headers['User-Agent'] = 'APlayer/9.0 (Linux; Android 16) ExoPlayer/1.0';
  const clientIp = String(options.clientIp || req.ip || req.headers['cf-connecting-ip'] || '');

  // Flush headers immediately so Cloudflare does not idle-timeout during remux.
  if (!res.headersSent) {
    res.writeHead(200, {
      ...cors(origin),
      'Content-Type': 'application/vnd.apple.mpegurl',
      'X-A3-Hls': '1',
      'X-Accel-Buffering': 'no'
    });
  }

  try {
    const newSid = await ensureSession({ target, headers, clientIp });
    const base = String(options.hlsBase || '/k1/a3hls');
    let body = fs.readFileSync(playlistPath(newSid), 'utf8');
    body = body.replace(/^(seg_\d+\.ts)\s*$/gim, `${base}/${newSid}/$1`);
    if (!res.writableEnded) res.end(body);
  } catch (err) {
    if (!res.writableEnded) {
      res.end(`#EXTM3U\n#EXT-X-ERROR:A3 HLS remux failed (${String(err && err.message ? err.message : err)})\n`);
    }
  }
}

module.exports = {
  handleA3Hls,
  cleanupSession,
  prefetchSession,
  ensureSession,
  sessionIdFrom
};
