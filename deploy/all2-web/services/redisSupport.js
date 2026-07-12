'use strict';

const DEFAULT_KEY_PREFIX = 'k1iptv:';

function parseRedisUrl(rawUrl = '') {
  const value = String(rawUrl || '').trim();
  if (!value) return null;
  try {
    const parsed = new URL(value);
    const dbRaw = String(parsed.pathname || '').replace(/^\//, '');
    const db = dbRaw ? Math.max(0, parseInt(dbRaw, 10) || 0) : 0;
    return {
      url: value,
      host: parsed.hostname,
      port: parsed.port ? parseInt(parsed.port, 10) : 6379,
      db,
      keyPrefix: String(process.env.REDIS_KEY_PREFIX || DEFAULT_KEY_PREFIX).trim() || DEFAULT_KEY_PREFIX
    };
  } catch {
    return null;
  }
}

function isRedisEnabled() {
  return !!parseRedisUrl(process.env.REDIS_URL || process.env.IPTV_REDIS_URL || '');
}

function prefixKey(prefix, key = '') {
  const safePrefix = String(prefix || DEFAULT_KEY_PREFIX);
  const safeKey = String(key || '');
  return safeKey.startsWith(safePrefix) ? safeKey : `${safePrefix}${safeKey}`;
}

let redisClientPromise = null;

async function getRedisClient(options = {}) {
  if (!isRedisEnabled()) return null;
  if (redisClientPromise) return redisClientPromise;
  redisClientPromise = (async () => {
    let createClient;
    try {
      ({ createClient } = require('redis'));
    } catch {
      console.warn('[redis] package "redis" not installed; falling back to in-memory stores.');
      return null;
    }
    const cfg = parseRedisUrl(process.env.REDIS_URL || process.env.IPTV_REDIS_URL || '');
    if (!cfg) return null;
    const client = createClient({
      url: cfg.url,
      socket: {
        reconnectStrategy: retries => Math.min(retries * 150, 3000)
      }
    });
    client.on('error', err => {
      console.warn('[redis] client error:', String(err?.message || err));
    });
    await client.connect();
    console.log(`[redis] connected (${cfg.host}:${cfg.port}/${cfg.db}, prefix=${cfg.keyPrefix})`);
    return client;
  })().catch(err => {
    redisClientPromise = null;
    console.warn('[redis] connect failed; falling back to in-memory stores:', String(err?.message || err));
    return null;
  });
  return redisClientPromise;
}

async function createSessionStore() {
  if (!isRedisEnabled()) return null;
  let RedisStore;
  try {
    ({ RedisStore } = require('connect-redis'));
  } catch {
    console.warn('[redis] package "connect-redis" not installed; using MemoryStore for sessions.');
    return null;
  }
  const client = await getRedisClient();
  if (!client) return null;
  const cfg = parseRedisUrl(process.env.REDIS_URL || process.env.IPTV_REDIS_URL || '');
  return new RedisStore({
    client,
    prefix: `${cfg.keyPrefix}sess:`
  });
}

async function closeRedisClient() {
  const client = await redisClientPromise;
  if (!client) return;
  try {
    await client.quit();
  } catch {}
  redisClientPromise = null;
}

module.exports = {
  DEFAULT_KEY_PREFIX,
  parseRedisUrl,
  isRedisEnabled,
  prefixKey,
  getRedisClient,
  createSessionStore,
  closeRedisClient
};
