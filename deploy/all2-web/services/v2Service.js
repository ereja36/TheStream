'use strict';

const axios = require('axios');

const PAGE_HEADERS = {
  'User-Agent': 'Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Mobile Safari/537.36',
  Accept: 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8',
  'Accept-Language': 'en-GB,en;q=0.5',
  Referer: 'https://shqiperia.dev/live',
  Connection: 'keep-alive'
};

function decodeHtmlEntities(value = '') {
  return String(value || '')
    .replace(/&amp;/g, '&')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, '\'')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>');
}

function stripHtml(value = '') {
  return decodeHtmlEntities(String(value || '').replace(/<[^>]+>/g, ' ')).replace(/\s+/g, ' ').trim();
}

function toAbsolute(baseUrl, raw) {
  const value = String(raw || '').trim();
  if (!value) return '';
  try {
    return new URL(value, baseUrl).toString();
  } catch {
    return '';
  }
}

function getAttrValue(attrs = '', attrName = '') {
  const safeAttr = String(attrName || '').trim().replace(/[^a-zA-Z0-9:_-]/g, '');
  if (!safeAttr) return '';
  const source = String(attrs || '');
  const quotedMatch = source.match(new RegExp(`${safeAttr}\\s*=\\s*["']([^"']*)["']`, 'i'));
  if (quotedMatch) return decodeHtmlEntities(quotedMatch[1] || '').trim();
  const bareMatch = source.match(new RegExp(`${safeAttr}\\s*=\\s*([^\\s"'=<>` + '`' + `]+)`, 'i'));
  if (bareMatch) return decodeHtmlEntities(bareMatch[1] || '').trim();
  return '';
}

function extractChannelsFromHtml(html = '', baseUrl = '') {
  const out = [];
  const seenIds = new Set();
  const cardRegex = /<a\b([^>]*)>([\s\S]*?)<\/a>/gi;
  let match;
  while ((match = cardRegex.exec(html)) !== null) {
    const attrs = String(match[1] || '');
    const body = String(match[2] || '');
    const className = ` ${String(getAttrValue(attrs, 'class') || '').toLowerCase()} `;
    if (!className.includes(' channel-card ')) continue;
    const href = String(getAttrValue(attrs, 'href') || '').trim();
    if (!href) continue;
    const idMatch = href.match(/\/live\/(\d+)\/play/i);
    if (!idMatch) continue;
    const id = String(idMatch[1] || '').trim();
    if (!id || seenIds.has(id)) continue;
    const nameMatch = body.match(/class=["'][^"']*channel-name[^"']*["'][^>]*>([\s\S]*?)<\/[^>]+>/i);
    const logoMatch = body.match(/class=["'][^"']*channel-logo[^"']*["'][\s\S]*?<img[^>]*src=["']([^"']+)["']/i)
      || body.match(/<img[^>]*src=["']([^"']+)["']/i);
    const altMatch = body.match(/<img[^>]*alt=["']([^"']+)["']/i);
    const derivedName = stripHtml(nameMatch ? nameMatch[1] : (altMatch ? altMatch[1] : ''));
    const name = derivedName || `Kanali ${id}`;
    const logo = logoMatch ? toAbsolute(baseUrl, logoMatch[1]) : '';
    out.push({
      id,
      name,
      url: toAbsolute(baseUrl, href),
      logo
    });
    seenIds.add(id);
  }

  if (out.length) return out;

  const linkRegex = /\/live\/(\d+)\/play/gi;
  let linkMatch;
  while ((linkMatch = linkRegex.exec(html)) !== null) {
    const id = String(linkMatch[1] || '').trim();
    if (!id || seenIds.has(id)) continue;
    out.push({
      id,
      name: `Kanali ${id}`,
      url: toAbsolute(baseUrl, `/live/${id}/play`),
      logo: ''
    });
    seenIds.add(id);
  }
  return out;
}

function extractStreamFromHtml(html = '') {
  const patterns = [
    /var\s+src\s*=\s*["']([^"']+\.(?:m3u8|mpd|mp4|ts)[^"']*)["']\s*;/gi,
    /\bsrc\s*=\s*["']([^"']+\.(?:m3u8|mpd|mp4|ts)[^"']*)["']/gi,
    /loadSource\s*\(\s*["']([^"']+\.(?:m3u8|mpd|mp4|ts)[^"']+)["']\s*\)/gi,
    /video\.src\s*=\s*["']([^"']+\.(?:m3u8|mpd|mp4|ts)[^"']+)["']/gi,
    /hls\.loadSource\s*\(\s*["']([^"']+\.(?:m3u8|mpd|mp4|ts)[^"']+)["']\s*\)/gi,
    /(https?:\/\/[^\s"'<>]+\.(?:m3u8|mpd|mp4|ts)[^\s"'<>]*)/gi,
    /(https?:\/\/stream\.[^\s"'<>]+)/gi,
    /(https?:\/\/[^\s"'<>]*arkiva[^\s"'<>]*)/gi
  ];
  for (const regex of patterns) {
    let match;
    while ((match = regex.exec(html)) !== null) {
      const candidate = String(Array.isArray(match) ? (match[1] || match[0] || '') : '').trim();
      if (/^https?:\/\//i.test(candidate)) return candidate;
    }
  }
  return '';
}

class V2Service {
  constructor(options = {}) {
    this.baseUrl = String(options.baseUrl || 'https://shqiperia.dev').replace(/\/+$/, '');
    this.channelCacheTtlMs = Math.max(20 * 1000, Number(options.channelCacheTtlMs || 60 * 1000));
    this.streamCacheTtlMs = Math.max(10 * 1000, Number(options.streamCacheTtlMs || 25 * 1000));
    this.channelCache = { data: [], ts: 0 };
    this.streamCache = new Map();
  }

  isChannelCacheFresh() {
    if (!this.channelCache.ts) return false;
    return (Date.now() - this.channelCache.ts) < this.channelCacheTtlMs;
  }

  getCacheAgeMs() {
    if (!this.channelCache.ts) return 0;
    return Math.max(0, Date.now() - this.channelCache.ts);
  }

  async fetchChannels(options = {}) {
    const force = options.force === true;
    if (!force && this.isChannelCacheFresh()) return this.channelCache.data.slice();
    try {
      const response = await axios.get(`${this.baseUrl}/live`, {
        timeout: 15000,
        headers: PAGE_HEADERS,
        responseType: 'text'
      });
      const html = String(response.data || '');
      const channels = extractChannelsFromHtml(html, this.baseUrl).filter(item => item.id && item.url);
      if (channels.length) {
        this.channelCache = { data: channels, ts: Date.now() };
      }
      return this.channelCache.data.slice();
    } catch {
      return this.channelCache.data.slice();
    }
  }

  async getChannelStream(channelId = '', options = {}) {
    const id = String(channelId || '').trim();
    if (!id) return '';
    const forceRefresh = options.forceRefresh === true || options.force === true;
    if (!forceRefresh) {
      const cached = this.streamCache.get(id);
      if (cached && (Date.now() - Number(cached.ts || 0)) < this.streamCacheTtlMs && /^https?:\/\//i.test(cached.url || '')) {
        return cached.url;
      }
    }
    try {
      const response = await axios.get(`${this.baseUrl}/live/${encodeURIComponent(id)}/play?t=${Date.now()}`, {
        timeout: 15000,
        headers: PAGE_HEADERS,
        responseType: 'text'
      });
      if (Number(response.status || 0) !== 200) return '';
      const streamUrl = extractStreamFromHtml(String(response.data || ''));
      if (!/^https?:\/\//i.test(streamUrl)) return '';
      this.streamCache.set(id, { url: streamUrl, ts: Date.now() });
      return streamUrl;
    } catch {
      return '';
    }
  }

  getStatus() {
    const ageMs = this.getCacheAgeMs();
    return {
      source: this.baseUrl,
      channelsInCache: this.channelCache.data.length,
      cacheAgeSec: Math.round(ageMs / 1000),
      expiresInSec: this.channelCache.ts ? Math.round(Math.max(0, this.channelCacheTtlMs - ageMs) / 1000) : 0,
      cacheFresh: this.isChannelCacheFresh(),
      streamCacheSize: this.streamCache.size,
      lastFetch: this.channelCache.ts || 0
    };
  }
}

function createV2Service(options = {}) {
  return new V2Service(options);
}

module.exports = {
  createV2Service
};
