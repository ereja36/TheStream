'use strict';

function normalizeUrlLight(url = '') {
  const trimmed = String(url || '').trim();
  return /^https?:\/\//i.test(trimmed) ? trimmed : '';
}

function normalizeBackupUrlsLight(rawValue = [], maxItems = 16) {
  const values = Array.isArray(rawValue) ? rawValue : String(rawValue || '').split(/\r?\n|,|\|/g);
  const out = [];
  const seen = new Set();
  for (const value of values) {
    const normalized = normalizeUrlLight(value);
    if (!normalized || seen.has(normalized)) continue;
    seen.add(normalized);
    out.push(normalized);
    if (out.length >= maxItems) break;
  }
  return out;
}

function stripChannelVariantSuffixes(name = '') {
  return String(name || '')
    .replace(/\[[^\]]*\]/g, ' ')
    .replace(/\(\s*backup\s*\)/gi, ' ')
    .replace(/\s+\.[sbc]\s*$/gi, ' ')
    .replace(/\s+\.[sbc](?:\s|$)/gi, ' ')
    .replace(/\b(fhd|hd|sd|4k|uhd)\b/gi, ' ')
    .replace(/[^\p{L}\p{N}\s]/gu, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function normalizeChannelVariantMergeKey(name = '', category = '') {
  const key = stripChannelVariantSuffixes(String(name || '').toLowerCase());
  const cat = String(category || '').toLowerCase().trim();
  return cat ? `${cat}::${key}` : key;
}

function getChannelVariantQualityRank(name = '', url = '') {
  const hay = `${name} ${url}`.toLowerCase();
  if (/\bfhd\b/.test(hay)) return 500;
  if (/\b(4k|uhd)\b/.test(hay)) return 450;
  if (/\bhd\b/.test(hay)) return 300;
  if (/\bsd\b/.test(hay)) return 100;
  if (/\(\s*backup\s*\)/.test(hay) || /\s\.[sbc](?:\s|$)/.test(hay)) return 50;
  return 200;
}

const LOKKE_COUNTRY_CODES = {
  albania: 'AL',
  germany: 'DE',
  italy: 'IT',
  france: 'FR',
  'united kingdom': 'UK',
  uk: 'UK',
  spain: 'ES',
  turkey: 'TR',
  netherlands: 'NL',
  poland: 'PL',
  portugal: 'PT',
  romania: 'RO',
  russia: 'RU',
  croatia: 'HR',
  serbia: 'RS',
  bulgaria: 'BG',
  greece: 'GR',
  belgium: 'BE',
  austria: 'AT',
  switzerland: 'CH',
  arabia: 'AR',
  balkans: 'BK'
};

function resolveLokkeCountryCode(category = '') {
  const raw = String(category || '').trim();
  if (!raw) return '';
  const head = raw.split(/[➾>\/|]/)[0].trim().toLowerCase();
  if (LOKKE_COUNTRY_CODES[head]) return LOKKE_COUNTRY_CODES[head];
  const compact = head.replace(/\s+/g, ' ');
  return LOKKE_COUNTRY_CODES[compact] || '';
}

function getCleanChannelDisplayName(name = '', options = {}) {
  let display = String(name || '').trim()
    .replace(/\(\s*backup\s*\)/gi, '')
    .replace(/\s+\.[sbc]\s*$/gi, '')
    .replace(/\s+\.[sbc](?:\s|$)/gi, ' ')
    .replace(/\b(fhd|hd|sd|4k|uhd)\b/gi, '')
    .replace(/\s*\[lokke(?:[,\-][^\]]*)?\]\s*/gi, ' ')
    .replace(/\s+/g, ' ')
    .trim();
  if (options.appendLokkeTag) {
    const country = String(options.lokkeCountryCode || resolveLokkeCountryCode(options.koolCategory || options.category || '') || '')
      .trim()
      .toUpperCase();
    // Use hyphen (not comma) so M3U parsers that split on the first ',' keep the full name.
    const tag = country ? `[Lokke-${country}]` : '[Lokke]';
    if (!/\[lokke(?:[,\-][^\]]*)?\]\s*$/i.test(display)) {
      display = `${display} ${tag}`.trim();
    } else {
      display = display.replace(/\[lokke(?:[,\-][^\]]*)?\]\s*$/i, tag).trim();
    }
  }
  return display || String(name || '').trim() || 'Unknown Channel';
}

function mergeChannelVariantList(items = [], options = {}) {
  const mergeByCategory = options.mergeByCategory !== false;
  const maxBackups = Math.max(4, Math.min(parseInt(options.maxBackups, 10) || 16, 20));
  const groups = new Map();
  for (const raw of items) {
    if (!raw || typeof raw !== 'object') continue;
    const name = String(raw.name || '').trim();
    const url = normalizeUrlLight(raw.url || '');
    if (!name || !url) continue;
    const category = String(raw.koolCategory || raw.category || raw.category_name || options.defaultCategory || '').trim();
    const mergeKey = normalizeChannelVariantMergeKey(name, mergeByCategory ? category : '');
    if (!mergeKey) continue;
    let group = groups.get(mergeKey);
    if (!group) {
      group = { variants: [], category, baseMeta: raw };
      groups.set(mergeKey, group);
    }
    group.variants.push({
      ...raw,
      name,
      url,
      category,
      qualityRank: getChannelVariantQualityRank(name, url)
    });
  }
  const merged = [];
  for (const group of groups.values()) {
    const variants = group.variants.sort((a, b) => b.qualityRank - a.qualityRank || a.name.localeCompare(b.name));
    const primary = variants[0];
    const displayName = getCleanChannelDisplayName(primary.name, {
      appendLokkeTag: options.appendLokkeTag === true,
      koolCategory: primary.koolCategory || primary.category || group.category || '',
      category: primary.category || group.category || '',
      lokkeCountryCode: options.lokkeCountryCode || ''
    });
    const backupUrls = [];
    const seen = new Set([primary.url]);
    for (let i = 1; i < variants.length; i += 1) {
      const nextUrl = variants[i].url;
      if (seen.has(nextUrl)) continue;
      seen.add(nextUrl);
      backupUrls.push(nextUrl);
    }
    merged.push({
      ...group.baseMeta,
      ...primary,
      name: displayName,
      url: primary.url,
      category: primary.category || group.category || '',
      koolCategory: primary.koolCategory || primary.category || group.category || '',
      backup_urls: normalizeBackupUrlsLight(backupUrls, maxBackups),
      variant_count: variants.length
    });
  }
  return merged;
}

module.exports = {
  stripChannelVariantSuffixes,
  normalizeChannelVariantMergeKey,
  getChannelVariantQualityRank,
  getCleanChannelDisplayName,
  resolveLokkeCountryCode,
  mergeChannelVariantList,
  normalizeBackupUrlsLight
};
