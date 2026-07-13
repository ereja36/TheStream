'use strict';

const axios = require('axios');

const PAGE_HEADERS = {
  'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
  Accept: 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8',
  'Accept-Language': 'en-US,en;q=0.5',
  'Accept-Encoding': 'gzip, deflate',
  Connection: 'keep-alive',
  'Upgrade-Insecure-Requests': '1'
};

const SPECIAL_FIXED_CHANNELS = new Set([
  'Sport 5',
  'Sport 5+',
  'Sport 5 Live',
  'Sport 1',
  'Sport 2',
  'Sport 3',
  'Sport 4',
  'ONE',
  'ONE 2',
  'Now 14',
  'Keshet 12',
  'Reshet 13',
  'Kan 11'
]);

function createSlug(value) {
  return String(value || '')
    .toLowerCase()
    .replace(/[^a-z0-9+\s-]/g, '')
    .replace(/\+/g, 'plus')
    .replace(/\s+/g, '')
    .replace(/^-+|-+$/g, '');
}

function extractChannelsFromText(text) {
  const channels = [];
  const pairRegex = /{\s*name:\s*"([^"]+)",\s*url:\s*"([^"]+)"\s*}/g;
  let match;
  while ((match = pairRegex.exec(text)) !== null) {
    const name = String(match[1] || '').trim();
    const url = String(match[2] || '').trim();
    if (!name || !/^https?:\/\//i.test(url)) continue;
    channels.push({ name, url });
  }
  return channels;
}

class FalconService {
  constructor(options = {}) {
    this.sourceUrl = options.sourceUrl || 'https://live.9aiboard.com/';
    this.cache = {
      data: [],
      timestamp: 0,
      expiryMs: Number(options.cacheExpiryMs || 5 * 60 * 1000)
    };
  }

  getCacheAgeMs() {
    return this.cache.timestamp ? Math.max(0, Date.now() - this.cache.timestamp) : 0;
  }

  isCacheFresh() {
    if (!this.cache.timestamp) return false;
    return this.getCacheAgeMs() < this.cache.expiryMs;
  }

  normalizeChannels(channels) {
    const seen = new Set();
    const normalized = [];

    for (const channel of channels) {
      const name = String(channel?.name || '').trim();
      let url = String(channel?.url || '').trim();
      if (!name || !/^https?:\/\//i.test(url)) continue;

      if (SPECIAL_FIXED_CHANNELS.has(name) && /V2_emn|stream-/i.test(url)) {
        const slug = createSlug(name);
        if (slug) url = `https://9aiboard.com/${slug}/index.m3u8`;
      }

      const key = `${name.toLowerCase()}|${url.toLowerCase()}`;
      if (seen.has(key)) continue;
      seen.add(key);
      normalized.push({ name, url, source: 'falcon' });
    }

    return normalized;
  }

  async fetchChannels(options = {}) {
    const force = !!options.force;
    if (!force && this.isCacheFresh()) return this.cache.data.slice();

    try {
      const response = await axios.get(this.sourceUrl, {
        timeout: 10000,
        headers: PAGE_HEADERS,
        responseType: 'text'
      });
      const html = String(response.data || '');
      let channels = [];

      const channelsObjectMatch = html.match(/var channels\s*=\s*({[\s\S]*?});/);
      if (channelsObjectMatch && channelsObjectMatch[1]) {
        channels = extractChannelsFromText(channelsObjectMatch[1]);
      }
      if (!channels.length) {
        channels = extractChannelsFromText(html);
      }

      const normalized = this.normalizeChannels(channels);
      this.cache.data = normalized;
      this.cache.timestamp = Date.now();
      return normalized.slice();
    } catch (error) {
      return this.cache.data.slice();
    }
  }

  getChannelsFromCache() {
    return this.cache.data.slice();
  }

  getStatus() {
    const ageMs = this.getCacheAgeMs();
    const expiresInMs = this.cache.timestamp ? Math.max(0, this.cache.expiryMs - ageMs) : 0;
    return {
      source: this.sourceUrl,
      channelsInCache: this.cache.data.length,
      cacheAgeSec: Math.round(ageMs / 1000),
      expiresInSec: Math.round(expiresInMs / 1000),
      cacheFresh: this.isCacheFresh(),
      lastFetch: this.cache.timestamp || 0
    };
  }
}

function createFalconService(options = {}) {
  return new FalconService(options);
}

module.exports = {
  createFalconService
};
