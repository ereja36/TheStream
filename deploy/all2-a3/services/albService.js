'use strict';

const axios = require('axios');
const crypto = require('crypto');

const ALB_CONFIG_URL = 'https://adsportal.media-colony.com/users/AlbKanaleV9.1/config.php';
const ALB_REFERER = 'https://www.digitalturbine.com/';
const ALB_USER_AGENT = 'Android Vinebre Software';
const ALB_STREAM_USER_AGENT = 'APlayer/9.0 (Linux; Android 16) ExoPlayer/1.0';
const DEFAULT_CLIENT_CACHE_MS = 90 * 1000;

/** Map Media-Colony channel ids (CH01…) to real display names. */
const ALB_ID_DISPLAY_NAMES = {
  tch: 'Top Channel',
  tvk: 'TV Klan',
  tvkp: 'TV Klan Plus',
  alsat: 'Alsat',
  kks: 'Klan Kosova',
  topn: 'Top News',
  tvknw: 'TV Klan News',
  teve1: 'Teve 1',
  k10: 'Kanali 10',
  firstch: 'First Channel',
  duka: 'Dukagjini',
  ss1: 'SuperSport 1',
  ss2: 'SuperSport 2',
  ss3: 'SuperSport 3',
  ss4: 'SuperSport 4',
  ss5: 'SuperSport 5',
  ss6: 'SuperSport 6',
  ts1: 'Tring Sport 1',
  ts2: 'Tring Sport 2',
  ts3: 'Tring Sport 3',
  ts4: 'Tring Sport 4',
  faksion: 'Film Aksion',
  fthriller: 'Film Thriller',
  fhits: 'Film Hits',
  fautor: 'Film Autor',
  fkomedi: 'Film Komedi',
  fdrame: 'Film Dramë',
  tdc: 'TDC',
  ddrama: 'Family Drama',
  novelas: 'Novela',
  tseries: 'Tring Series',
  '3plus': '3 Plus',
  bb1al: 'Big Brother AL 1',
  bb2al: 'Big Brother AL 2',
  bbvk1: 'Big Brother VIP KS 1',
  bbvk2: 'Big Brother VIP KS 2',
  ferma1: 'Ferma VIP 1',
  ferma2: 'Ferma VIP 2',
  eshkence: 'Exp Shkencë',
  enatyre: 'Exp Natyrë',
  ehistori: 'Exp Histori',
  cufo: 'Cufo',
  bang: 'Bang Bang',
  tringtring: 'Tring Tring',
  amusic: 'Tring Music',
  sgm: 'SGM',
  bbftv: 'BBF TV',
  shota: 'Shota',
  rtvred: 'RTV Red',
  brazzers: 'Brazzers',
  private: 'Private',
  passionxxx: 'Passion XXX',
  hustler: 'Hustler',
  venustv: 'Venus TV',
  vividtv: 'Vivid TV'
};

const DEVICE_MODELS = [
  'Google Pixel 9 Pro XL',
  'Google Pixel 8 Pro',
  'Samsung Galaxy S24 Ultra',
  'Samsung Galaxy S23 Ultra',
  'Xiaomi 14',
  'OnePlus 12'
];

function randomChoice(values) {
  if (!Array.isArray(values) || !values.length) return '';
  return values[Math.floor(Math.random() * values.length)];
}

function randomHex(length = 16) {
  return crypto.randomBytes(Math.ceil(length / 2)).toString('hex').slice(0, length);
}

function normalizeClientIp(value = '') {
  return String(value || '').split(',')[0].trim();
}
function looksLikeIPv4(ip = '') {
  return /^\d{1,3}(\.\d{1,3}){3}$/.test(String(ip || '').trim());
}
function rewriteCipInUrl(url = '', clientIp = '') {
  const safeUrl = String(url || '').trim();
  if (!/^https?:\/\//i.test(safeUrl)) return safeUrl;
  const ip = normalizeClientIp(clientIp);
  if (!looksLikeIPv4(ip)) return safeUrl;
  if (!/[?&]cip=/i.test(safeUrl)) return safeUrl;
  return safeUrl.replace(/([?&]cip=)[^&]*/i, `$1${encodeURIComponent(ip)}`);
}

function sanitizeStreamUrl(url = '') {
  const raw = String(url || '').trim();
  if (!/^https?:\/\//i.test(raw)) return '';
  try {
    // Encode spaces / unsafe query chars that Media-Colony sometimes leaves raw.
    return new URL(raw).toString();
  } catch {
    try {
      return encodeURI(raw.replace(/ /g, '%20'));
    } catch {
      return raw.replace(/ /g, '%20');
    }
  }
}

function pickHeaderValue(headers = {}, key = '') {
  const wanted = String(key || '').toLowerCase();
  for (const [name, value] of Object.entries(headers || {})) {
    if (String(name || '').toLowerCase() !== wanted) continue;
    return String(value || '').trim();
  }
  return '';
}

function normalizeChannelHeaders(headers = {}) {
  const out = {};
  for (const [rawName, rawValue] of Object.entries(headers || {})) {
    const name = String(rawName || '').trim();
    const value = String(rawValue || '').trim();
    if (!name || !value) continue;
    const lower = name.toLowerCase();
    if (lower === 'host' || lower === 'connection' || lower === 'accept-encoding' || lower === 'content-length') continue;
    out[name] = value;
  }
  const referer = pickHeaderValue(out, 'referer');
  const origin = pickHeaderValue(out, 'origin');
  const cookie = pickHeaderValue(out, 'cookie');
  const xForwardedFor = pickHeaderValue(out, 'x-forwarded-for');
  if (referer) out.Referer = referer;
  if (origin) out.Origin = origin;
  if (cookie) out.Cookie = cookie;
  if (xForwardedFor) out['X-Forwarded-For'] = xForwardedFor;
  return out;
}

function detectAlbType(name = '') {
  const text = String(name || '').toLowerCase();
  if (/(sport|sports|football|futboll|soccer|liga|champions|uefa|nba|tennis|boxing|ufc|f1|formula|motogp|golf|supersport|tring sport)/i.test(text)) {
    return 'sport';
  }
  return 'tv';
}

function isAlbVodEntry(entry = {}) {
  const url = String(entry?.url || '');
  const name = String(entry?.name || '');
  const id = String(entry?.id || '');
  if (/\.mp4(\/|\?|$)/i.test(url)) return true;
  if (/\/(aksion|horror|drame|thriller|komedi|vizatimore|shqip|dubluar|esref)\//i.test(url)) return true;
  if (/^(25|24|23|22|21|20)(ak|hr|dr|th|km|vz|sq|db|es)\d+$/i.test(id)) return true;
  if (/\.mp4$/i.test(name)) return true;
  return false;
}

function titleCaseWords(value = '') {
  return String(value || '')
    .replace(/[_-]+/g, ' ')
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/([A-Z]+)([A-Z][a-z])/g, '$1 $2')
    .replace(/\s+/g, ' ')
    .trim()
    .split(' ')
    .filter(Boolean)
    .map((word, index) => {
      if (/^[0-9]+$/.test(word)) return word;
      const lower = word.toLowerCase();
      if (index > 0 && ['of', 'the', 'and', 'a', 'an', 'or', 'to', 'in', 'on', 'for'].includes(lower)) return lower;
      if (word.length <= 2 && /^[A-Z0-9]+$/.test(word)) return word.toUpperCase();
      return word.charAt(0).toUpperCase() + word.slice(1).toLowerCase();
    })
    .join(' ');
}

function beautifyAlbVodTitle(entry = {}) {
  const url = String(entry?.url || '');
  const pathMatch = url.match(/\/([^/?#]+\.mp4)\//i) || url.match(/\/([^/?#]+\.mp4)(?:\?|$)/i);
  const fileName = pathMatch ? pathMatch[1] : '';
  let raw = fileName.replace(/\.mp4$/i, '');
  // Patterns like 25AK52.PrisonerOfWar or PrisonerOfWar
  if (raw.includes('.')) {
    const parts = raw.split('.');
    raw = parts.slice(1).join('.') || parts[0];
  }
  raw = raw.replace(/^\d{2}[A-Z]{2}\d+[._-]*/i, '');
  const titled = titleCaseWords(raw);
  if (titled) return titled;
  const fallback = titleCaseWords(String(entry?.name || entry?.id || 'VOD'));
  return fallback || 'VOD';
}

function resolveAlbDisplayName(entry = {}) {
  const id = String(entry?.id || '').trim().toLowerCase();
  const rawName = String(entry?.name || '').trim();
  if (isAlbVodEntry(entry)) return beautifyAlbVodTitle(entry);
  if (id && ALB_ID_DISPLAY_NAMES[id]) return ALB_ID_DISPLAY_NAMES[id];
  if (/^ch\d+$/i.test(rawName) && id) {
    // Unknown CH## — prefer readable id over CH01
    return titleCaseWords(id) || rawName;
  }
  return rawName || titleCaseWords(id) || 'A3 Channel';
}

function detectVodCategory(url = '') {
  const match = String(url || '').match(/gcore-cdn\.org\/([^/]+)\//i);
  if (!match) return '';
  const cat = String(match[1] || '').toLowerCase();
  if (cat === 'out') return '';
  return cat;
}

function responseLooksPlayable(status, headers = {}, body = '') {
  if (status >= 300 && status < 400 && String(headers.location || '').trim()) return true;
  if (status !== 200) return false;
  const contentType = String(headers['content-type'] || '').toLowerCase();
  if (contentType.includes('mpegurl') || contentType.includes('vnd.apple.mpegurl')) return true;
  if (contentType.includes('text/html')) return false;
  const text = String(body || '').slice(0, 120);
  if (/#EXTM3U/i.test(text)) return true;
  return true;
}

class AlbService {
  constructor(options = {}) {
    this.cache = {
      data: [],
      timestamp: 0,
      expiryMs: Number(options.cacheExpiryMs || 5 * 60 * 1000)
    };
    this.clientCache = new Map();
    this.clientCacheMs = Number(options.clientCacheMs || DEFAULT_CLIENT_CACHE_MS);
  }

  getCacheAgeMs() {
    return this.cache.timestamp ? Math.max(0, Date.now() - this.cache.timestamp) : 0;
  }

  isCacheFresh() {
    if (!this.cache.timestamp) return false;
    return this.getCacheAgeMs() < this.cache.expiryMs;
  }

  buildRequestContext() {
    return {
      device: randomHex(16),
      android: randomChoice(['14', '15', '16']) || '16',
      model: randomChoice(DEVICE_MODELS) || 'Google Pixel 9 Pro XL',
      updateCookie: String(Math.floor(Date.now() / 1000))
    };
  }

  normalizeChannels(rawChannels = []) {
    const normalized = [];
    const seen = new Set();
    for (const entry of rawChannels) {
      const id = String(entry?.id || '').trim();
      const url = sanitizeStreamUrl(entry?.url || '');
      if (!/^https?:\/\//i.test(url)) continue;
      const vod = isAlbVodEntry({ ...entry, url });
      const name = resolveAlbDisplayName({ ...entry, url });
      if (!name) continue;
      const channelHeaders = normalizeChannelHeaders(entry?.headers || {});
      const userAgent = String(entry?.custom_user_agent || ALB_STREAM_USER_AGENT).trim() || ALB_STREAM_USER_AGENT;
      const sourceKey = vod ? 'alb_vod' : 'alb';
      const dedupeKey = `${sourceKey}|${id.toLowerCase()}|${url.toLowerCase()}`;
      if (seen.has(dedupeKey)) continue;
      seen.add(dedupeKey);
      const vodCategory = vod ? detectVodCategory(url) : '';
      normalized.push({
        id,
        name,
        url,
        source: sourceKey,
        sourceKey,
        type: vod ? 'vod' : detectAlbType(name),
        vodCategory,
        headers: channelHeaders,
        userAgent
      });
    }
    return normalized;
  }

  pruneClientCache() {
    const now = Date.now();
    for (const [key, item] of this.clientCache) {
      if (!item || (now - Number(item.timestamp || 0)) > this.clientCacheMs) {
        this.clientCache.delete(key);
      }
    }
  }

  buildRequestHeaders(ctx = {}, options = {}) {
    const headers = {
      'User-Agent': ALB_USER_AGENT,
      Referer: ALB_REFERER,
      Host: 'adsportal.media-colony.com',
      Connection: 'Keep-Alive',
      'Accept-Encoding': 'gzip',
      Cookie: `Update=${ctx.updateCookie || String(Math.floor(Date.now() / 1000))}`
    };
    return headers;
  }

  async fetchConfig(options = {}) {
    const ctx = this.buildRequestContext();
    const response = await axios.get(ALB_CONFIG_URL, {
      timeout: 15000,
      params: {
        device: ctx.device,
        android: ctx.android,
        model: ctx.model
      },
      headers: this.buildRequestHeaders(ctx, options),
      validateStatus: status => status >= 200 && status < 500
    });
    if (response.status !== 200) return null;
    return response?.data && typeof response.data === 'object' ? response.data : null;
  }

  async fetchChannels(options = {}) {
    const force = !!options.force;
    const clientIp = normalizeClientIp(options.clientIp || '');
    const sourceFilter = String(options.sourceKey || options.source || '').trim().toLowerCase();

    const applyFilter = (list) => {
      if (!sourceFilter) return list.slice();
      return list.filter(item => String(item?.sourceKey || item?.source || '') === sourceFilter);
    };

    if (clientIp) {
      this.pruneClientCache();
      if (!force) {
        const cached = this.clientCache.get(clientIp);
        if (cached && Array.isArray(cached.data) && (Date.now() - Number(cached.timestamp || 0)) < this.clientCacheMs) {
          return applyFilter(cached.data);
        }
      }
    } else if (!force && this.isCacheFresh()) {
      return applyFilter(this.cache.data);
    }

    try {
      let body = await this.fetchConfig({ clientIp });
      if (!body && clientIp) {
        // Some ALB edges block spoofed forwarding headers; retry without them.
        body = await this.fetchConfig({});
      }
      if (!body) {
        if (clientIp) return applyFilter(this.clientCache.get(clientIp)?.data || this.cache.data);
        return applyFilter(this.cache.data);
      }
      const channels = Array.isArray(body.channels) ? body.channels : [];
      const normalized = this.normalizeChannels(channels);
      const now = Date.now();
      if (clientIp) {
        this.clientCache.set(clientIp, { data: normalized, timestamp: now });
      } else {
        this.cache.data = normalized;
        this.cache.timestamp = now;
      }
      return applyFilter(normalized);
    } catch (_error) {
      if (clientIp) return applyFilter(this.clientCache.get(clientIp)?.data || this.cache.data);
      return applyFilter(this.cache.data);
    }
  }

  findChannelMatch(channels = [], channelRef = {}) {
    const wantedId = String(channelRef?.albId || channelRef?.id || '').trim().toLowerCase();
    const wantedName = String(channelRef?.name || '').trim().toLowerCase();
    const wantedUrl = String(channelRef?.url || '').trim().toLowerCase();
    if (!Array.isArray(channels) || !channels.length) return null;
    if (wantedId) {
      const byId = channels.find(item => String(item?.id || '').trim().toLowerCase() === wantedId);
      if (byId) return byId;
    }
    if (wantedName) {
      const byName = channels.find(item => String(item?.name || '').trim().toLowerCase() === wantedName);
      if (byName) return byName;
    }
    if (wantedUrl) {
      const byUrl = channels.find(item => String(item?.url || '').trim().toLowerCase() === wantedUrl);
      if (byUrl) return byUrl;
    }
    return null;
  }

  withClientIpv4(channel = {}, clientIp = '') {
    if (!channel || typeof channel !== 'object') return null;
    const rewritten = rewriteCipInUrl(channel.url || '', clientIp);
    return {
      ...channel,
      url: sanitizeStreamUrl(rewritten || channel.url || '')
    };
  }

  async resolveChannel(channelRef = {}, options = {}) {
    const clientIp = normalizeClientIp(options.clientIp || '');
    const force = !!options.force;
    const channels = await this.fetchChannels({ clientIp, force });
    const matched = this.findChannelMatch(channels, channelRef);
    if (matched) {
      const candidate = this.withClientIpv4(matched, clientIp);
      if (options.verifyStream) {
        const probe = await this.probeStreamCandidate(candidate);
        if (!probe.ok) return null;
      }
      return candidate;
    }
    const fallbackUrl = sanitizeStreamUrl(channelRef?.url || '');
    if (/^https?:\/\//i.test(fallbackUrl)) {
      const vod = isAlbVodEntry({ ...channelRef, url: fallbackUrl });
      return {
        id: String(channelRef?.albId || channelRef?.id || ''),
        name: String(channelRef?.name || ''),
        url: fallbackUrl,
        source: vod ? 'alb_vod' : 'alb',
        sourceKey: vod ? 'alb_vod' : 'alb',
        type: vod ? 'vod' : detectAlbType(channelRef?.name || ''),
        headers: normalizeChannelHeaders(channelRef?.albHeaders || {}),
        userAgent: String(channelRef?.albUserAgent || ALB_STREAM_USER_AGENT || '').trim() || ALB_STREAM_USER_AGENT
      };
    }
    return null;
  }

  buildStreamHeaders(channel = {}) {
    const rawHeaders = channel?.headers && typeof channel.headers === 'object' ? channel.headers : {};
    const headers = {
      'User-Agent': String(channel?.userAgent || ALB_STREAM_USER_AGENT).trim() || ALB_STREAM_USER_AGENT,
      'Icy-MetaData': '1',
      'Accept-Encoding': 'identity',
      'Connection': 'Keep-Alive',
      'Accept': '*/*'
    };
    for (const [rawKey, rawValue] of Object.entries(rawHeaders)) {
      const key = String(rawKey || '').trim();
      const value = String(rawValue || '').trim();
      if (!key || !value) continue;
      const lower = key.toLowerCase();
      if (lower === 'host' || lower === 'content-length') continue;
      headers[key] = value;
    }
    return headers;
  }

  async probeStreamCandidate(channel = {}, options = {}) {
    const url = sanitizeStreamUrl(channel?.url || '');
    if (!/^https?:\/\//i.test(url)) return { ok: false, status: 0, error: 'invalid_url' };
    const timeoutRaw = Number(options.timeoutMs || 12000);
    const timeout = Number.isFinite(timeoutRaw) ? Math.min(Math.max(Math.floor(timeoutRaw), 1000), 30000) : 12000;
    try {
      const response = await axios.get(url, {
        timeout,
        maxRedirects: 0,
        responseType: 'text',
        headers: this.buildStreamHeaders(channel),
        validateStatus: status => status >= 200 && status < 500
      });
      const ok = response.status === 200 && responseLooksPlayable(response.status, response.headers || {}, response.data || '');
      return { ok, status: response.status, headers: response.headers || {} };
    } catch (error) {
      return { ok: false, status: 0, error: error.message };
    }
  }

  async fetchPlayableChannels(options = {}) {
    const force = options.force !== false;
    const clientIp = normalizeClientIp(options.clientIp || '');
    const maxProbeRaw = Number(options.maxProbe || 0);
    const maxProbe = Number.isFinite(maxProbeRaw) ? Math.max(0, Math.floor(maxProbeRaw)) : 0;
    const concurrencyRaw = Number(options.concurrency || 20);
    const concurrency = Number.isFinite(concurrencyRaw)
      ? Math.min(Math.max(Math.floor(concurrencyRaw), 1), 60)
      : 20;
    const probeTimeoutRaw = Number(options.probeTimeoutMs || 6500);
    const probeTimeoutMs = Number.isFinite(probeTimeoutRaw)
      ? Math.min(Math.max(Math.floor(probeTimeoutRaw), 1000), 30000)
      : 6500;

    const channels = await this.fetchChannels({
      force,
      clientIp,
      sourceKey: options.sourceKey || options.source || ''
    });
    // VPS/datacenter IPs often get 404 from gcore/fastly A3 edges — skip probe when requested.
    if (options.skipProbe) return channels.slice(0, maxProbe > 0 ? maxProbe : channels.length);

    const toCheck = maxProbe > 0 ? channels.slice(0, maxProbe) : channels.slice();
    if (!toCheck.length) return [];

    let next = 0;
    const playable = [];
    const worker = async () => {
      while (true) {
        const index = next;
        next += 1;
        if (index >= toCheck.length) return;
        const channel = toCheck[index];
        const candidate = this.withClientIpv4(channel, clientIp);
        const probe = await this.probeStreamCandidate(candidate, { timeoutMs: probeTimeoutMs });
        if (!probe.ok) continue;
        playable.push({
          ...channel,
          url: candidate?.url || channel.url,
          _idx: index
        });
      }
    };
    await Promise.all(Array.from({ length: Math.min(concurrency, toCheck.length) }, () => worker()));
    playable.sort((a, b) => Number(a._idx || 0) - Number(b._idx || 0));
    return playable.map(item => {
      const cloned = { ...item };
      delete cloned._idx;
      return cloned;
    });
  }

  async resolveChannelLive(channelRef = {}, options = {}) {
    const clientIp = normalizeClientIp(options.clientIp || '');
    const triesRaw = Number(options.tries || 2);
    const tries = Number.isFinite(triesRaw) ? Math.min(Math.max(Math.floor(triesRaw), 1), 5) : 2;
    for (let i = 0; i < tries; i += 1) {
      try {
        const body = await this.fetchConfig({});
        if (!body) continue;
        const channels = this.normalizeChannels(Array.isArray(body.channels) ? body.channels : []);
        if (channels.length) {
          this.cache.data = channels;
          this.cache.timestamp = Date.now();
        }
        const matched = this.findChannelMatch(channels, channelRef);
        if (matched) {
          const candidate = this.withClientIpv4(matched, clientIp);
          if (options.verifyStream) {
            const probe = await this.probeStreamCandidate(candidate);
            if (!probe.ok) continue;
          }
          return candidate;
        }
      } catch {
        // Try next attempt.
      }
    }
    if (options.allowCacheFallback) {
      return this.resolveChannel(channelRef, { force: false });
    }
    return null;
  }

  getChannelsFromCache() {
    return this.cache.data.slice();
  }

  getStatus() {
    const ageMs = this.getCacheAgeMs();
    const expiresInMs = this.cache.timestamp ? Math.max(0, this.cache.expiryMs - ageMs) : 0;
    this.pruneClientCache();
    const live = this.cache.data.filter(item => item.sourceKey === 'alb').length;
    const vod = this.cache.data.filter(item => item.sourceKey === 'alb_vod').length;
    return {
      source: ALB_CONFIG_URL,
      channelsInCache: this.cache.data.length,
      liveChannelsInCache: live,
      vodChannelsInCache: vod,
      clientCacheEntries: this.clientCache.size,
      cacheAgeSec: Math.round(ageMs / 1000),
      expiresInSec: Math.round(expiresInMs / 1000),
      cacheFresh: this.isCacheFresh(),
      lastFetch: this.cache.timestamp || 0
    };
  }
}

function createAlbService(options = {}) {
  return new AlbService(options);
}

module.exports = {
  createAlbService,
  ALB_ID_DISPLAY_NAMES,
  isAlbVodEntry,
  resolveAlbDisplayName,
  beautifyAlbVodTitle
};
