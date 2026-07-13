'use strict';

/**
 * Cluster-safe playlist analytics (counters + recent events).
 * Redis is source of truth when REDIS_URL is set; otherwise memory + disk.
 */

const { getRedisClient, isRedisEnabled, parseRedisUrl, prefixKey } = require('./redisSupport');

const MAX_EVENTS = 120;
const DAILY_KEEP_DAYS = 35;

let mode = 'memory'; // 'redis' | 'memory'
let memoryState = {};
let persistDiskFn = null;
let persistTimer = null;
let persistDirty = false;

function emptyRow() {
  return {
    downloads: 0,
    channel_clicks: 0,
    vod_clicks: 0,
    blocked_requests: 0,
    last_download_at: '',
    last_stream_at: '',
    daily: {},
    recent_events: []
  };
}

function getPrefix() {
  const cfg = parseRedisUrl(process.env.REDIS_URL || process.env.IPTV_REDIS_URL || '');
  return cfg?.keyPrefix || 'k1iptv:';
}

function metaKey(id) {
  return prefixKey(getPrefix(), `pla:${id}:meta`);
}
function dailyKey(id) {
  return prefixKey(getPrefix(), `pla:${id}:daily`);
}
function eventsKey(id) {
  return prefixKey(getPrefix(), `pla:${id}:events`);
}

function dayKeyNow(iso = '') {
  const raw = String(iso || new Date().toISOString());
  return raw.slice(0, 10);
}

function pruneDaily(daily = {}) {
  const keepAfter = new Date(Date.now() - (DAILY_KEEP_DAYS * 24 * 60 * 60 * 1000)).toISOString().slice(0, 10);
  const out = {};
  for (const [k, v] of Object.entries(daily || {})) {
    if (k >= keepAfter) out[k] = v;
  }
  return out;
}

function scheduleDiskPersist() {
  if (typeof persistDiskFn !== 'function') return;
  persistDirty = true;
  if (persistTimer) return;
  persistTimer = setTimeout(() => {
    persistTimer = null;
    if (!persistDirty) return;
    persistDirty = false;
    try {
      persistDiskFn(getMemoryMirror());
    } catch (err) {
      console.warn('[playlist-analytics] disk persist failed:', String(err?.message || err));
    }
  }, 1500);
}

function getMemoryMirror() {
  return memoryState;
}

function setMemoryRow(id, row) {
  memoryState[id] = row;
}

function applyEventToRow(row, type, event, nowIso) {
  const dayKey = dayKeyNow(nowIso);
  if (!row.daily[dayKey]) {
    row.daily[dayKey] = { downloads: 0, channel_clicks: 0, vod_clicks: 0, blocked_requests: 0 };
  }
  if (type === 'download') {
    row.downloads += 1;
    row.last_download_at = nowIso;
    row.daily[dayKey].downloads += 1;
  } else if (type === 'channel_click') {
    row.channel_clicks += 1;
    row.last_stream_at = nowIso;
    row.daily[dayKey].channel_clicks += 1;
  } else if (type === 'vod_click') {
    row.vod_clicks += 1;
    row.last_stream_at = nowIso;
    row.daily[dayKey].vod_clicks += 1;
  } else if (type === 'blocked') {
    row.blocked_requests += 1;
    row.daily[dayKey].blocked_requests += 1;
  } else {
    return false;
  }
  row.daily = pruneDaily(row.daily);
  row.recent_events = Array.isArray(row.recent_events) ? row.recent_events : [];
  row.recent_events.unshift(event);
  row.recent_events = row.recent_events.slice(0, MAX_EVENTS);
  return true;
}

async function seedRowToRedis(client, id, row) {
  const meta = {
    downloads: String(Math.max(0, Number(row.downloads || 0))),
    channel_clicks: String(Math.max(0, Number(row.channel_clicks || 0))),
    vod_clicks: String(Math.max(0, Number(row.vod_clicks || 0))),
    blocked_requests: String(Math.max(0, Number(row.blocked_requests || 0))),
    last_download_at: String(row.last_download_at || ''),
    last_stream_at: String(row.last_stream_at || '')
  };
  await client.hSet(metaKey(id), meta);
  const daily = row.daily && typeof row.daily === 'object' ? row.daily : {};
  const dailyFields = {};
  for (const [day, vals] of Object.entries(daily)) {
    dailyFields[day] = JSON.stringify({
      downloads: Math.max(0, Number(vals?.downloads || 0)),
      channel_clicks: Math.max(0, Number(vals?.channel_clicks || 0)),
      vod_clicks: Math.max(0, Number(vals?.vod_clicks || 0)),
      blocked_requests: Math.max(0, Number(vals?.blocked_requests || 0))
    });
  }
  if (Object.keys(dailyFields).length) await client.hSet(dailyKey(id), dailyFields);
  const events = Array.isArray(row.recent_events) ? row.recent_events.slice(0, MAX_EVENTS) : [];
  if (events.length) {
    const ek = eventsKey(id);
    await client.del(ek);
    // LPUSH in reverse so index 0 is newest
    for (let i = events.length - 1; i >= 0; i -= 1) {
      await client.lPush(ek, JSON.stringify(events[i]));
    }
    await client.lTrim(ek, 0, MAX_EVENTS - 1);
  }
}

async function initPlaylistAnalyticsStore(diskState = {}, options = {}) {
  memoryState = diskState && typeof diskState === 'object' ? { ...diskState } : {};
  persistDiskFn = typeof options.persistDisk === 'function' ? options.persistDisk : null;
  mode = 'memory';
  if (!isRedisEnabled()) {
    console.log('[playlist-analytics] mode=memory (redis disabled)');
    return { mode };
  }
  const client = await getRedisClient();
  if (!client) {
    console.log('[playlist-analytics] mode=memory (redis unavailable)');
    return { mode };
  }
  mode = 'redis';
  let seeded = 0;
  for (const [id, row] of Object.entries(memoryState)) {
    if (!id || !row) continue;
    try {
      const exists = await client.exists(metaKey(id));
      if (!exists) {
        await seedRowToRedis(client, id, row);
        seeded += 1;
      }
    } catch (err) {
      console.warn('[playlist-analytics] seed failed for', id, String(err?.message || err));
    }
  }
  console.log(`[playlist-analytics] mode=redis (seeded ${seeded} playlists from disk)`);
  return { mode };
}

function getStoreMode() {
  return mode;
}

async function readRowFromRedis(client, id) {
  const meta = await client.hGetAll(metaKey(id));
  if (!meta || !Object.keys(meta).length) return null;
  const dailyRaw = await client.hGetAll(dailyKey(id));
  const daily = {};
  for (const [day, raw] of Object.entries(dailyRaw || {})) {
    try {
      const parsed = JSON.parse(raw);
      daily[day] = {
        downloads: Math.max(0, Number(parsed.downloads || 0)),
        channel_clicks: Math.max(0, Number(parsed.channel_clicks || 0)),
        vod_clicks: Math.max(0, Number(parsed.vod_clicks || 0)),
        blocked_requests: Math.max(0, Number(parsed.blocked_requests || 0))
      };
    } catch {
      daily[day] = { downloads: 0, channel_clicks: 0, vod_clicks: 0, blocked_requests: 0 };
    }
  }
  const eventRaws = await client.lRange(eventsKey(id), 0, MAX_EVENTS - 1);
  const recent_events = [];
  for (const raw of eventRaws || []) {
    try {
      const parsed = JSON.parse(raw);
      if (parsed && typeof parsed === 'object') recent_events.push(parsed);
    } catch {}
  }
  return {
    downloads: Math.max(0, Number(meta.downloads || 0)),
    channel_clicks: Math.max(0, Number(meta.channel_clicks || 0)),
    vod_clicks: Math.max(0, Number(meta.vod_clicks || 0)),
    blocked_requests: Math.max(0, Number(meta.blocked_requests || 0)),
    last_download_at: String(meta.last_download_at || ''),
    last_stream_at: String(meta.last_stream_at || ''),
    daily: pruneDaily(daily),
    recent_events
  };
}

async function recordPlaylistEvent(id, type, event = {}) {
  const safeId = String(id || '').trim();
  const safeType = String(type || '').trim().toLowerCase();
  if (!safeId || !['download', 'channel_click', 'vod_click', 'blocked'].includes(safeType)) return false;
  const nowIso = String(event.ts || new Date().toISOString());
  const safeEvent = {
    ts: nowIso,
    type: safeType,
    target: String(event.target || '').slice(0, 180),
    ip: String(event.ip || '').slice(0, 80),
    ua: String(event.ua || '').slice(0, 180),
    client_type: String(event.client_type || event.clientType || '').slice(0, 40),
    referer: String(event.referer || event.referrer || '').slice(0, 220),
    host: String(event.host || '').slice(0, 120),
    forwarded_for: String(event.forwarded_for || '').slice(0, 220),
    mode: String(event.mode || '').slice(0, 40),
    reason: String(event.reason || '').slice(0, 140),
    url: String(event.url || '').slice(0, 220),
    key_tail: String(event.key_tail || '').slice(0, 24)
  };

  if (mode === 'redis') {
    const client = await getRedisClient();
    if (client) {
      const day = dayKeyNow(nowIso);
      const counterField = safeType === 'channel_click' ? 'channel_clicks'
        : safeType === 'vod_click' ? 'vod_clicks'
          : safeType === 'blocked' ? 'blocked_requests'
            : 'downloads';
      const multi = client.multi();
      multi.hIncrBy(metaKey(safeId), counterField, 1);
      if (safeType === 'download') multi.hSet(metaKey(safeId), { last_download_at: nowIso });
      if (safeType === 'channel_click' || safeType === 'vod_click') {
        multi.hSet(metaKey(safeId), { last_stream_at: nowIso });
      }
      // daily: read-modify-write one field
      multi.lPush(eventsKey(safeId), JSON.stringify(safeEvent));
      multi.lTrim(eventsKey(safeId), 0, MAX_EVENTS - 1);
      await multi.exec();

      const dailyRaw = await client.hGet(dailyKey(safeId), day);
      let dayObj = { downloads: 0, channel_clicks: 0, vod_clicks: 0, blocked_requests: 0 };
      if (dailyRaw) {
        try { dayObj = { ...dayObj, ...JSON.parse(dailyRaw) }; } catch {}
      }
      dayObj[counterField] = Math.max(0, Number(dayObj[counterField] || 0) + 1);
      await client.hSet(dailyKey(safeId), day, JSON.stringify(dayObj));

      // Keep local mirror fresh for this worker (admin may still hit this process)
      const row = memoryState[safeId] ? { ...memoryState[safeId], daily: { ...(memoryState[safeId].daily || {}) }, recent_events: [...(memoryState[safeId].recent_events || [])] } : emptyRow();
      applyEventToRow(row, safeType, safeEvent, nowIso);
      setMemoryRow(safeId, row);
      scheduleDiskPersist();
      return true;
    }
  }

  // memory fallback
  const row = memoryState[safeId] ? memoryState[safeId] : emptyRow();
  if (!memoryState[safeId]) memoryState[safeId] = row;
  if (!applyEventToRow(row, safeType, safeEvent, nowIso)) return false;
  scheduleDiskPersist();
  return true;
}

async function getPlaylistRow(id) {
  const safeId = String(id || '').trim();
  if (!safeId) return emptyRow();
  if (mode === 'redis') {
    const client = await getRedisClient();
    if (client) {
      let row = await readRowFromRedis(client, safeId);
      if (!row && memoryState[safeId]) {
        // Disk had data but redis miss (e.g. flush) — seed lazily
        await seedRowToRedis(client, safeId, memoryState[safeId]);
        row = await readRowFromRedis(client, safeId);
      }
      if (row) {
        setMemoryRow(safeId, row);
        return row;
      }
    }
  }
  return memoryState[safeId] || emptyRow();
}

function getPlaylistRowSync(id) {
  const safeId = String(id || '').trim();
  return memoryState[safeId] || emptyRow();
}

function replaceMemoryState(nextState = {}) {
  memoryState = nextState && typeof nextState === 'object' ? nextState : {};
}

async function filterEventsForTarget(targetSlug = '', channelName = '') {
  const slug = String(targetSlug || '').trim();
  const name = String(channelName || '').trim();
  if (!slug && !name) return false;
  let changed = false;
  const ids = Object.keys(memoryState);
  for (const id of ids) {
    const row = await getPlaylistRow(id);
    if (!row || !Array.isArray(row.recent_events)) continue;
    const before = row.recent_events.length;
    row.recent_events = row.recent_events.filter(event => {
      const target = String(event?.target || '').trim();
      if (!target) return true;
      if (slug && target === slug) return false;
      if (name && target.toLowerCase() === name.toLowerCase()) return false;
      return true;
    });
    if (before === row.recent_events.length) continue;
    changed = true;
    setMemoryRow(id, row);
    if (mode === 'redis') {
      const client = await getRedisClient();
      if (client) {
        const ek = eventsKey(id);
        await client.del(ek);
        for (let i = row.recent_events.length - 1; i >= 0; i -= 1) {
          await client.lPush(ek, JSON.stringify(row.recent_events[i]));
        }
        if (row.recent_events.length) await client.lTrim(ek, 0, MAX_EVENTS - 1);
      }
    }
  }
  if (changed) scheduleDiskPersist();
  return changed;
}

module.exports = {
  initPlaylistAnalyticsStore,
  getStoreMode,
  recordPlaylistEvent,
  getPlaylistRow,
  getPlaylistRowSync,
  getMemoryMirror,
  replaceMemoryState,
  filterEventsForTarget,
  emptyRow
};
