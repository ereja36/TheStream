'use strict';

/**
 * Shared integration job status + channel-state version for PM2 cluster.
 */

const { getRedisClient, isRedisEnabled, parseRedisUrl, prefixKey } = require('./redisSupport');

function getPrefix() {
  const cfg = parseRedisUrl(process.env.REDIS_URL || process.env.IPTV_REDIS_URL || '');
  return cfg?.keyPrefix || 'k1iptv:';
}

function jobKey(name) {
  return prefixKey(getPrefix(), `job:${String(name || '').trim()}`);
}

function versionKey() {
  return prefixKey(getPrefix(), 'channel_state:version');
}

const memoryJobs = new Map();
let memoryVersion = 0;

async function setIntegrationJob(name, job = {}) {
  const safe = String(name || '').trim();
  if (!safe) return;
  const payload = {
    ...(job || {}),
    updated_at: Date.now()
  };
  memoryJobs.set(safe, payload);
  if (!isRedisEnabled()) return;
  try {
    const client = await getRedisClient();
    if (!client) return;
    await client.set(jobKey(safe), JSON.stringify(payload), { EX: 60 * 60 });
  } catch (err) {
    console.warn('[channel-state] setIntegrationJob failed:', String(err?.message || err));
  }
}

async function getIntegrationJob(name) {
  const safe = String(name || '').trim();
  if (!safe) return null;
  if (isRedisEnabled()) {
    try {
      const client = await getRedisClient();
      if (client) {
        const raw = await client.get(jobKey(safe));
        if (raw) {
          const parsed = JSON.parse(raw);
          memoryJobs.set(safe, parsed);
          return parsed;
        }
      }
    } catch {}
  }
  return memoryJobs.get(safe) || null;
}

async function bumpChannelStateVersion(reason = '') {
  const version = Date.now();
  memoryVersion = version;
  if (!isRedisEnabled()) return version;
  try {
    const client = await getRedisClient();
    if (!client) return version;
    await client.set(versionKey(), JSON.stringify({ version, reason: String(reason || ''), at: version }), { EX: 7 * 24 * 3600 });
  } catch (err) {
    console.warn('[channel-state] bump version failed:', String(err?.message || err));
  }
  return version;
}

async function getChannelStateVersion() {
  if (isRedisEnabled()) {
    try {
      const client = await getRedisClient();
      if (client) {
        const raw = await client.get(versionKey());
        if (raw) {
          const parsed = JSON.parse(raw);
          memoryVersion = Number(parsed?.version || 0) || memoryVersion;
          return memoryVersion;
        }
      }
    } catch {}
  }
  return memoryVersion;
}

module.exports = {
  setIntegrationJob,
  getIntegrationJob,
  bumpChannelStateVersion,
  getChannelStateVersion
};
