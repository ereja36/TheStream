'use strict';

const { getRedisClient, parseRedisUrl, prefixKey } = require('./redisSupport');

const RELOAD_CHANNEL = 'state:reload';
const LOCK_PREFIX = 'lock:';

let subscriberPromise = null;

function getKeyPrefix() {
  const cfg = parseRedisUrl(process.env.REDIS_URL || process.env.IPTV_REDIS_URL || '');
  return cfg?.keyPrefix || 'k1iptv:';
}

async function withClusterLock(name, ttlMs, fn) {
  const client = await getRedisClient();
  if (!client) {
    const result = await fn();
    return { ok: true, skipped: false, result };
  }
  const lockKey = prefixKey(getKeyPrefix(), `${LOCK_PREFIX}${name}`);
  const token = `${process.pid}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  const acquired = await client.set(lockKey, token, { NX: true, PX: Math.max(5000, ttlMs) });
  if (!acquired) return { ok: true, skipped: true, result: null };
  try {
    const result = await fn();
    return { ok: true, skipped: false, result };
  } finally {
    try {
      const current = await client.get(lockKey);
      if (current === token) await client.del(lockKey);
    } catch {}
  }
}

async function broadcastStateReload(reason = 'update') {
  const client = await getRedisClient();
  if (!client) return false;
  const payload = JSON.stringify({
    reason: String(reason || 'update'),
    at: Date.now(),
    pid: process.pid
  });
  await client.publish(prefixKey(getKeyPrefix(), RELOAD_CHANNEL), payload);
  return true;
}

async function initClusterStateSync(onReload) {
  if (typeof onReload !== 'function') return null;
  const client = await getRedisClient();
  if (!client) return null;
  if (subscriberPromise) return subscriberPromise;
  subscriberPromise = (async () => {
    const sub = client.duplicate();
    sub.on('error', err => {
      console.warn('[cluster-sync] subscriber error:', String(err?.message || err));
    });
    await sub.connect();
    const channel = prefixKey(getKeyPrefix(), RELOAD_CHANNEL);
    await sub.subscribe(channel, message => {
      try {
        const payload = JSON.parse(String(message || '{}'));
        if (Number(payload.pid) === process.pid) return;
        Promise.resolve(onReload(payload)).catch(err => {
          console.warn('[cluster-sync] reload failed:', String(err?.message || err));
        });
      } catch (err) {
        console.warn('[cluster-sync] invalid reload message:', String(err?.message || err));
      }
    });
    console.log(`[cluster-sync] subscribed (${channel})`);
    return sub;
  })().catch(err => {
    subscriberPromise = null;
    console.warn('[cluster-sync] subscribe failed:', String(err?.message || err));
    return null;
  });
  return subscriberPromise;
}

module.exports = {
  withClusterLock,
  broadcastStateReload,
  initClusterStateSync
};
