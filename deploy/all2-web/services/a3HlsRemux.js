'use strict';

/**
 * Remux A3 fastly/anytools MPEG-TS → short HLS for web HLS.js.
 * Reuses a3Relay proxy race, then ffmpeg -c copy to disk segments.
 * Disk-backed so PM2 cluster workers can serve the same session files.
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
  return crypto
    .createHash('sha1')
    .update(`${clientIp || 'x'}|${String(target || '').split('?')[0]}`)
    .digest('hex')
    .slice(0, 16);
}

function playlistPath(sid) {
  return path.join(ROOT, sid, 'index.m3u8');
}

function playlistReady(sid) {
  try {
    const body = fs.readFileSync(playlistPath(sid), 'utf8');
    return /#EXTINF/i.test(body) && /\.ts/i.test(body);
  } catch {
    return false;
  }
}

function cleanupSession(sid) {
  const s = sessions.get(sid);
  if (s) {
    try { if (s.ffmpeg) s.ffmpeg.kill('SIGKILL'); } catch {}
    try { if (s.upstream && s.upstream.data) s.upstream.data.destroy(); } catch {}
    sessions.delete(sid);
  }
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
    const marker = path.join(ROOT, sid, '.touch');
    fs.writeFileSync(marker, String(Date.now()));
  } catch {}
}

setInterval(() => {
  const now = Date.now();
  for (const [sid, s] of sessions.entries()) {
    if (now - Number(s.lastAccess || 0) > SESSION_TTL_MS) cleanupSession(sid);
  }
  // Also prune orphan dirs left by other cluster workers.
  try {
    ensureRoot();
    for (const sid of fs.readdirSync(ROOT)) {
      if (sessions.has(sid)) continue;
      const touchFile = path.join(ROOT, sid, '.touch');
      let ts = 0;
      try { ts = Number(fs.readFileSync(touchFile, 'utf8')) || 0; } catch {}
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
  });
  return ff;
}

async function waitPlaylist(sid, timeoutMs = 25000) {
  const t0 = Date.now();
  while (Date.now() - t0 < timeoutMs) {
    if (playlistReady(sid)) return;
    const s = sessions.get(sid);
    if (s && s.dead) throw new Error('ffmpeg exited before playlist');
    await new Promise((r) => setTimeout(r, 350));
  }
  throw new Error('hls playlist timeout');
}

async function ensureSession({ target, headers, clientIp }) {
  const sid = sessionIdFrom(target, clientIp);
  touch(sid);

  // Reuse disk playlist from any cluster worker (do not start a second ffmpeg).
  if (playlistReady(sid)) {
    const local = sessions.get(sid);
    if (!local || local.dead) {
      // Another worker owns ffmpeg; just serve files.
      return sid;
    }
    return sid;
  }

  const existing = sessions.get(sid);
  if (existing && !existing.dead) {
    await waitPlaylist(sid, 25000);
    return sid;
  }
  if (existing) cleanupSession(sid);

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
  await waitPlaylist(sid, 25000);
  return sid;
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

  // Prefer Express route params when mounted as /a3hls/:sid/:file
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
  // Match AlbKanale PlayerActivity headers exactly (Referer/Origin/Cookie/XFF + APlayer UA).
  if (!headers['User-Agent']) headers['User-Agent'] = 'APlayer/9.0 (Linux; Android 16) ExoPlayer/1.0';
  const clientIp = String(options.clientIp || req.ip || req.headers['cf-connecting-ip'] || '');
  try {
    const newSid = await ensureSession({ target, headers, clientIp });
    const base = String(options.hlsBase || '/k1/a3hls');
    const play = `${base}/${newSid}/index.m3u8`;
    // Prefer inline playlist (200) — HLS.js on Brave/Chrome often mishandles slow 302 bootstraps.
    // Keep 302 when client asks redirect=1 (debug / older clients).
    const wantRedirect = String(req.query?.redirect || '') === '1';
    if (wantRedirect) {
      res.writeHead(302, {
        ...cors(origin),
        Location: play,
        'X-A3-Hls-Session': newSid
      });
      return res.end(`Redirecting to ${play}`);
    }
    let body = fs.readFileSync(playlistPath(newSid), 'utf8');
    // Absolute segment URIs so playlist stays valid if HLS.js resolves against bootstrap query URL.
    body = body.replace(/^(seg_\d+\.ts)\s*$/gim, `${base}/${newSid}/$1`);
    res.writeHead(200, {
      ...cors(origin),
      'Content-Type': 'application/vnd.apple.mpegurl',
      'X-A3-Hls-Session': newSid,
      'X-A3-Hls-Playlist': play
    });
    return res.end(body);
  } catch (err) {
    res.writeHead(502, { ...cors(origin), 'Content-Type': 'text/plain; charset=utf-8' });
    return res.end(`A3 HLS remux failed (${String(err && err.message ? err.message : err)})`);
  }
}

module.exports = {
  handleA3Hls,
  cleanupSession
};
