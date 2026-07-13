'use strict';

const crypto = require('crypto');
const { getRedisClient, isRedisEnabled, parseRedisUrl } = require('./redisSupport');

const memoryCache = new Map();
const memoryInflight = new Map();
let memoryCacheBytes = 0;
let redisReady = false;

function serializeEntry(entry = {}) {
  return JSON.stringify({
    fetchId: entry.fetchId || '',
    createdAt: Number(entry.createdAt || 0),
    expiresAt: Number(entry.expiresAt || 0),
    status: Number(entry.status || 200),
    contentType: String(entry.contentType || ''),
    size: Number(entry.size || 0),
    body: entry.body ? entry.body.toString('base64') : ''
  });
}

function deserializeEntry(raw = '') {
  try {
    const parsed = JSON.parse(String(raw || ''));
    const bodyB64 = String(parsed.body || '');
    const body = bodyB64 ? Buffer.from(bodyB64, 'base64') : Buffer.alloc(0);
    return {
      fetchId: parsed.fetchId || '',
      createdAt: Number(parsed.createdAt || 0),
      expiresAt: Number(parsed.expiresAt || 0),
      status: Number(parsed.status || 200),
      headers: {},
      contentType: String(parsed.contentType || ''),
      body,
      size: Number(parsed.size || body.length || 0)
    };
  } catch {
    return null;
  }
}

function pruneMemoryCache(maxBytes = 256 * 1024 * 1024) {
  const now = Date.now();
  for (const [key, entry] of memoryCache) {
    if (Number(entry?.expiresAt || 0) <= now) {
      memoryCacheBytes = Math.max(0, memoryCacheBytes - Number(entry?.size || 0));
      memoryCache.delete(key);
    }
  }
  if (memoryCacheBytes <= maxBytes) return;
  const byOldest = Array.from(memoryCache.entries())
    .sort((a, b) => Number(a[1]?.createdAt || 0) - Number(b[1]?.createdAt || 0));
  for (const [key, entry] of byOldest) {
    memoryCacheBytes = Math.max(0, memoryCacheBytes - Number(entry?.size || 0));
    memoryCache.delete(key);
    if (memoryCacheBytes <= Math.floor(maxBytes * 0.85)) break;
  }
}

async function initHotSegmentStore() {
  redisReady = false;
  if (!isRedisEnabled()) return { mode: 'memory' };
  const client = await getRedisClient();
  if (!client) return { mode: 'memory' };
  redisReady = true;
  return { mode: 'redis' };
}

function getStoreMode() {
  return redisReady ? 'redis' : 'memory';
}

async function getCachedEntry(cacheKey) {
  if (!cacheKey) return null;
  if (redisReady) {
    const client = await getRedisClient();
    const cfg = parseRedisUrl(process.env.REDIS_URL || process.env.IPTV_REDIS_URL || '');
    if (client && cfg) {
      const raw = await client.get(`${cfg.keyPrefix}seg:${cacheKey}`);
      if (!raw) return null;
      const entry = deserializeEntry(raw);
      if (!entry || Number(entry.expiresAt || 0) <= Date.now()) return null;
      return entry;
    }
  }
  const cached = memoryCache.get(cacheKey);
  if (!cached || Number(cached.expiresAt || 0) <= Date.now()) return null;
  return cached;
}

async function putCachedEntry(cacheKey, entry, options = {}) {
  if (!cacheKey || !entry) return;
  const maxBytes = Math.max(1, Number(options.restream_max_mb || options.maxMb || 768)) * 1024 * 1024;
  const ttlSec = Math.max(1, Math.round((Number(entry.expiresAt || 0) - Date.now()) / 1000));
  if (redisReady) {
    const client = await getRedisClient();
    const cfg = parseRedisUrl(process.env.REDIS_URL || process.env.IPTV_REDIS_URL || '');
    if (client && cfg && ttlSec > 0) {
      await client.set(`${cfg.keyPrefix}seg:${cacheKey}`, serializeEntry(entry), { EX: ttlSec });
      return;
    }
  }
  const prev = memoryCache.get(cacheKey);
  if (prev) memoryCacheBytes = Math.max(0, memoryCacheBytes - Number(prev.size || 0));
  memoryCache.set(cacheKey, entry);
  memoryCacheBytes += Number(entry.size || 0);
  pruneMemoryCache(maxBytes);
}

async function runSingleflight(cacheKey, leaderTask, options = {}) {
  const waitTimeoutMs = Math.max(250, Number(options.waitTimeoutMs || 12000));
  if (redisReady) {
    const client = await getRedisClient();
    const cfg = parseRedisUrl(process.env.REDIS_URL || process.env.IPTV_REDIS_URL || '');
    if (client && cfg) {
      const lockKey = `${cfg.keyPrefix}lock:${cacheKey}`;
      const gotLock = await client.set(lockKey, '1', { NX: true, EX: 20 });
      if (gotLock) {
        try {
          const result = await leaderTask();
          return { ...(result && typeof result === 'object' ? result : {}), role: 'leader' };
        } finally {
          try { await client.del(lockKey); } catch {}
        }
      }
      const started = Date.now();
      while (Date.now() - started < waitTimeoutMs) {
        const cached = await getCachedEntry(cacheKey);
        if (cached) return { entry: cached, fromCache: true, role: 'waiter' };
        await new Promise(resolve => setTimeout(resolve, 40));
      }
      return null;
    }
  }
  let inflightPromise = memoryInflight.get(cacheKey);
  const isLeader = !inflightPromise;
  if (!inflightPromise) {
    inflightPromise = leaderTask()
      .then(result => ({ ...(result && typeof result === 'object' ? result : {}), role: 'leader' }))
      .finally(() => {
        memoryInflight.delete(cacheKey);
      });
    memoryInflight.set(cacheKey, inflightPromise);
  }
  if (isLeader) return inflightPromise;
  const timeoutPromise = new Promise(resolve => {
    setTimeout(() => resolve(null), waitTimeoutMs);
  });
  const raced = await Promise.race([inflightPromise, timeoutPromise]);
  if (!raced) return null;
  return { ...(raced && typeof raced === 'object' ? raced : {}), role: 'waiter', fromCache: !!raced.fromCache };
}

function hashCacheKey(value = '') {
  return crypto.createHash('sha1').update(String(value || '')).digest('hex');
}

module.exports = {
  initHotSegmentStore,
  getStoreMode,
  getCachedEntry,
  putCachedEntry,
  runSingleflight,
  hashCacheKey,
  pruneMemoryCache
};
