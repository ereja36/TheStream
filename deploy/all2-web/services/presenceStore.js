'use strict';

/**
 * Cluster-safe live presence (active viewers).
 * Redis is source of truth when REDIS_URL is set; otherwise local Map only.
 */

const { getRedisClient, isRedisEnabled, parseRedisUrl, prefixKey } = require('./redisSupport');

const TTL_SEC = 90;
const localMap = new Map();
let mode = 'memory';

function getPrefix() {
  const cfg = parseRedisUrl(process.env.REDIS_URL || process.env.IPTV_REDIS_URL || '');
  return cfg?.keyPrefix || 'k1iptv:';
}

function userKey(sid) {
  return prefixKey(getPrefix(), `presence:user:${String(sid || '')}`);
}

function indexKey() {
  return prefixKey(getPrefix(), 'presence:index');
}

function pruneLocal(now = Date.now()) {
  for (const [sid, data] of localMap) {
    if ((now - Number(data?.last_ping || 0)) > (TTL_SEC * 1000)) localMap.delete(sid);
  }
}

async function initPresenceStore() {
  if (isRedisEnabled()) {
    const client = await getRedisClient();
    mode = client ? 'redis' : 'memory';
  } else {
    mode = 'memory';
  }
  console.log(`[presence] mode=${mode}`);
  return { mode };
}

function getMode() {
  return mode;
}

function getLocalMirror() {
  pruneLocal();
  return localMap;
}

async function upsertPresence(sid, patch = {}, options = {}) {
  const safeSid = String(sid || '').trim();
  if (!safeSid) return null;
  const now = Date.now();
  const prevLocal = localMap.get(safeSid) || null;
  let started = Number(prevLocal?.started || 0) || 0;
  let merged = {
    ...(prevLocal || {}),
    ...(patch || {}),
    last_ping: now
  };

  if (mode === 'redis') {
    try {
      const client = await getRedisClient();
      if (client) {
        const raw = await client.get(userKey(safeSid));
        if (raw) {
          try {
            const prev = JSON.parse(raw);
            started = Number(prev?.started || 0) || started;
            merged = { ...prev, ...merged };
          } catch {}
        }
        if (!started) started = now;
        merged.started = started;
        merged.last_ping = now;
        await client.set(userKey(safeSid), JSON.stringify(merged), { EX: TTL_SEC });
        await client.sAdd(indexKey(), safeSid);
        localMap.set(safeSid, merged);
        return merged;
      }
    } catch (err) {
      console.warn('[presence] upsert redis failed:', String(err?.message || err));
    }
  }

  if (!started) started = now;
  merged.started = started;
  merged.last_ping = now;
  localMap.set(safeSid, merged);
  return merged;
}

async function touchPresence(sid, patch = {}) {
  return upsertPresence(sid, patch);
}

async function deletePresence(sid) {
  const safeSid = String(sid || '').trim();
  if (!safeSid) return;
  localMap.delete(safeSid);
  if (mode !== 'redis') return;
  try {
    const client = await getRedisClient();
    if (!client) return;
    await client.del(userKey(safeSid));
    await client.sRem(indexKey(), safeSid);
  } catch {}
}

async function deletePresenceByPredicate(predicate) {
  const entries = await listPresenceEntries();
  for (const [sid, data] of entries) {
    try {
      if (predicate(sid, data)) await deletePresence(sid);
    } catch {}
  }
}

async function listPresenceEntries() {
  pruneLocal();
  if (mode !== 'redis') {
    return Array.from(localMap.entries());
  }
  try {
    const client = await getRedisClient();
    if (!client) return Array.from(localMap.entries());
    const sids = await client.sMembers(indexKey());
    if (!Array.isArray(sids) || !sids.length) return [];
    const keys = sids.map(userKey);
    const values = await client.mGet(keys);
    const out = [];
    const stale = [];
    for (let i = 0; i < sids.length; i++) {
      const sid = sids[i];
      const raw = values[i];
      if (!raw) {
        stale.push(sid);
        localMap.delete(sid);
        continue;
      }
      try {
        const data = JSON.parse(raw);
        localMap.set(sid, data);
        out.push([sid, data]);
      } catch {
        stale.push(sid);
      }
    }
    if (stale.length) {
      try { await client.sRem(indexKey(), stale); } catch {}
    }
    return out;
  } catch (err) {
    console.warn('[presence] list redis failed:', String(err?.message || err));
    return Array.from(localMap.entries());
  }
}

async function getPresence(sid) {
  const safeSid = String(sid || '').trim();
  if (!safeSid) return null;
  if (localMap.has(safeSid)) return localMap.get(safeSid);
  if (mode !== 'redis') return null;
  try {
    const client = await getRedisClient();
    if (!client) return null;
    const raw = await client.get(userKey(safeSid));
    if (!raw) return null;
    const data = JSON.parse(raw);
    localMap.set(safeSid, data);
    return data;
  } catch {
    return null;
  }
}

async function buildViewerSnapshotAsync() {
  const entries = await listPresenceEntries();
  const viewers = {};
  for (const [, data] of entries) {
    const slug = String(data?.channel_slug || '').trim();
    if (!slug) continue;
    viewers[slug] = (viewers[slug] || 0) + 1;
  }
  return { total: entries.length, viewers };
}

function clearLocal() {
  localMap.clear();
}

module.exports = {
  initPresenceStore,
  getMode,
  getLocalMirror,
  upsertPresence,
  touchPresence,
  deletePresence,
  deletePresenceByPredicate,
  listPresenceEntries,
  getPresence,
  buildViewerSnapshotAsync,
  clearLocal,
  TTL_SEC
};
