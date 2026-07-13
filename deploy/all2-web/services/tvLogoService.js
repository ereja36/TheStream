'use strict';

const fs = require('fs');
const path = require('path');

const DEFAULT_LOGO_ROOT = path.join(__dirname, '..', 'public', 'tv-logos');
/** Absolute CDN base so Android / external players can load logos (relative /k1/... fails). */
const DEFAULT_PUBLIC_BASE = 'https://shifshqip.com/k1/tv-logos';

const COUNTRY_FOLDER_BY_CODE = {
  AL: 'albania',
  DE: 'germany',
  IT: 'italy',
  FR: 'france',
  UK: 'united-kingdom',
  GB: 'united-kingdom',
  US: 'united-states',
  ES: 'spain',
  TR: 'turkey',
  NL: 'netherlands',
  PL: 'poland',
  PT: 'portugal',
  RO: 'romania',
  RU: 'russia',
  HR: 'croatia',
  RS: 'serbia',
  BG: 'bulgaria',
  GR: 'greece',
  BE: 'belgium',
  AT: 'austria',
  CH: 'switzerland',
  INT: 'international'
};

const COUNTRY_CODE_BY_FOLDER = Object.fromEntries(
  Object.entries(COUNTRY_FOLDER_BY_CODE).map(([code, folder]) => [folder, code === 'GB' ? 'UK' : code])
);

const COUNTRY_CODE_BY_NAME = {
  albania: 'AL',
  germany: 'DE',
  italy: 'IT',
  france: 'FR',
  'united kingdom': 'UK',
  uk: 'UK',
  gb: 'UK',
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
  international: 'INT'
};

/** Manual aliases for common channel display names → logo basename keys. */
const LOGO_ALIASES = {
  klan: ['tv klan', 'klan'],
  'klan tv': ['tv klan', 'klan'],
  'tv klan': ['tv klan', 'klan'],
  'vizion plus': ['vizion plus'],
  'news 24': ['news24', 'news 24'],
  news24: ['news24', 'news 24'],
  'top channel': ['top channel'],
  'top news': ['top news', 'top channel'],
  'syri news': ['syri', 'syri vision', 'syri tv'],
  'syri vision': ['syri vision', 'syri'],
  euronews: ['euronews albania', 'euronews'],
  'euronews al': ['euronews albania', 'euronews'],
  'euronews albania': ['euronews albania'],
  'k sport 1': ['k sport 1', 'ksport 1', 'super sport 1', 'supersport 1'],
  'k sport 2': ['k sport 2', 'ksport 2', 'super sport 2', 'supersport 2'],
  'k sport 3': ['k sport 3', 'ksport 3', 'super sport 3', 'supersport 3'],
  'k sport 4': ['k sport 4', 'ksport 4', 'super sport 4', 'supersport 4'],
  'super sport 1': ['supersport 1', 'super sport 1'],
  'super sport 2': ['supersport 2', 'super sport 2'],
  'super sport 3': ['supersport 3', 'super sport 3'],
  'super sport 4': ['supersport 4', 'super sport 4'],
  'dazn 1': ['dazn 1', 'dazn1'],
  'dazn 2': ['dazn 2', 'dazn2'],
  dazn: ['dazn'],
  'rtsh 1': ['rtsh1', 'rtsh 1'],
  'rtsh 2': ['rtsh2', 'rtsh 2'],
  'rtsh 3': ['rtsh3', 'rtsh 3'],
  'klan kosova': ['klan kosova'],
  'klan plus': ['klanplus', 'klan plus'],
  'klan news': ['klan news'],
  'klan music': ['klan music'],
  'report tv': ['report tv'],
  'ora news': ['ora news'],
  'a2 cnn': ['a2 cnn', 'a2cnn'],
  'bbc news': ['bbc news'],
  'bbc world news': ['bbc world news'],
  'sky sport news': ['sky sport news', 'sky sports news']
};

function normalizeLogoKey(value = '') {
  return String(value || '')
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/\[lokke(?:[,\-][^\]]*)?\]/gi, ' ')
    .replace(/\[[^\]]*]/g, ' ')
    .replace(/\.(png|jpg|jpeg|webp|gif|svg)$/i, '')
    .replace(/\b(fhd|uhd|hd|sd|4k|hevc|raw|backup|not\s*24\s*\/\s*7|match\s*time|bar)\b/gi, ' ')
    .replace(/[^\p{L}\p{N}]+/gu, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function compactLogoKey(value = '') {
  return normalizeLogoKey(value).replace(/\s+/g, '');
}

function resolveCountryCode(hint = '') {
  const raw = String(hint || '').trim();
  if (!raw) return '';
  const upper = raw.toUpperCase();
  if (COUNTRY_FOLDER_BY_CODE[upper]) return upper === 'GB' ? 'UK' : upper;
  const lower = raw.toLowerCase();
  if (COUNTRY_CODE_BY_NAME[lower]) return COUNTRY_CODE_BY_NAME[lower];
  const first = lower.split(/[➾>\/|\-_,]/)[0].trim();
  if (COUNTRY_CODE_BY_NAME[first]) return COUNTRY_CODE_BY_NAME[first];
  return '';
}

function createTvLogoService(options = {}) {
  const logoRoot = String(options.logoRoot || DEFAULT_LOGO_ROOT);
  const publicBase = String(options.publicBase || DEFAULT_PUBLIC_BASE).replace(/\/+$/, '');
  /** @type {Map<string, Array<{ file: string, country: string, key: string, compact: string, tokenCount: number }>>} */
  let indexByKey = new Map();
  let indexByCompact = new Map();
  let loadedAt = 0;
  let fileCount = 0;

  function publicUrlFor(relPath = '') {
    const clean = String(relPath || '').replace(/^\/+/, '');
    if (!clean) return '';
    return `${publicBase}/${clean.split(path.sep).join('/')}`;
  }

  function addIndex(key, entry) {
    if (!key) return;
    if (!indexByKey.has(key)) indexByKey.set(key, []);
    indexByKey.get(key).push(entry);
    const compact = key.replace(/\s+/g, '');
    if (!compact) return;
    if (!indexByCompact.has(compact)) indexByCompact.set(compact, []);
    indexByCompact.get(compact).push(entry);
  }

  function walk(dir, relBase = '') {
    let entries = [];
    try {
      entries = fs.readdirSync(dir, { withFileTypes: true });
    } catch {
      return;
    }
    for (const ent of entries) {
      if (!ent || !ent.name || ent.name.startsWith('.')) continue;
      if (ent.name === '0_all_logos_mosaic.md') continue;
      const abs = path.join(dir, ent.name);
      const rel = relBase ? path.join(relBase, ent.name) : ent.name;
      if (ent.isDirectory()) {
        walk(abs, rel);
        continue;
      }
      if (!/\.(png|jpe?g|webp|gif|svg)$/i.test(ent.name)) continue;
      fileCount += 1;
      const folderParts = relBase.split(path.sep).filter(Boolean);
      const countryFolder = folderParts[0] === 'countries' ? (folderParts[1] || '') : (folderParts[0] || '');
      const country = COUNTRY_CODE_BY_FOLDER[countryFolder] || '';
      const baseName = ent.name.replace(/\.(png|jpe?g|webp|gif|svg)$/i, '');
      const withoutCountrySuffix = baseName.replace(/-(al|de|it|fr|uk|gb|us|es|tr|nl|pl|pt|ro|ru|hr|rs|bg|gr|be|at|ch)$/i, '');
      const spaced = normalizeLogoKey(withoutCountrySuffix.replace(/-/g, ' '));
      const keys = new Set([
        normalizeLogoKey(baseName),
        normalizeLogoKey(withoutCountrySuffix),
        spaced,
        compactLogoKey(withoutCountrySuffix)
      ].filter(Boolean));
      const entryBase = {
        file: rel.split(path.sep).join('/'),
        country,
        key: spaced,
        compact: compactLogoKey(withoutCountrySuffix),
        tokenCount: spaced ? spaced.split(' ').length : 0
      };
      for (const key of keys) addIndex(key, { ...entryBase, key });
    }
  }

  function rebuildIndex() {
    indexByKey = new Map();
    indexByCompact = new Map();
    fileCount = 0;
    if (fs.existsSync(logoRoot)) walk(logoRoot);
    loadedAt = Date.now();
    return { fileCount, loadedAt };
  }

  function ensureIndex() {
    if (!loadedAt || !indexByKey.size) rebuildIndex();
  }

  function pickBest(candidates = [], countryCode = '', wantedKey = '') {
    if (!candidates.length) return null;
    const wanted = String(countryCode || '').toUpperCase();
    const wantedNorm = normalizeLogoKey(wantedKey);
    const wantedCompact = compactLogoKey(wantedKey);
    let best = null;
    let bestScore = -1;
    for (const item of candidates) {
      let score = 0;
      if (wanted && item.country === wanted) score += 50;
      else if (wanted && item.country && item.country !== wanted) score -= 15;
      else if (!wanted && item.country === 'AL') score += 5;

      if (wantedNorm && item.key === wantedNorm) score += 100;
      else if (wantedCompact && item.compact === wantedCompact) score += 95;
      else if (wantedNorm && item.key && (item.key.startsWith(wantedNorm + ' ') || wantedNorm.startsWith(item.key + ' '))) score += 40;
      else if (wantedNorm && item.key && (item.key.includes(wantedNorm) || wantedNorm.includes(item.key))) {
        // Penalize short substring hits (e.g. "itv" inside "metitv")
        const shorter = Math.min(item.key.length, wantedNorm.length);
        const longer = Math.max(item.key.length, wantedNorm.length);
        if (shorter < 4 || shorter / longer < 0.55) score -= 40;
        else score += 20;
      }

      // Prefer fewer-token filenames for short channel names (KLAN -> tv-klan, not klan-kosova)
      if (wantedNorm && wantedNorm.split(' ').length <= 2) {
        score += Math.max(0, 8 - Number(item.tokenCount || 0));
      }

      if (score > bestScore) {
        bestScore = score;
        best = item;
      }
    }
    return bestScore >= 30 ? best : null;
  }

  function collectByKeys(keys = []) {
    const out = [];
    const seen = new Set();
    for (const key of keys) {
      const norm = normalizeLogoKey(key);
      const compact = compactLogoKey(key);
      for (const row of (indexByKey.get(norm) || [])) {
        if (seen.has(row.file)) continue;
        seen.add(row.file);
        out.push(row);
      }
      for (const row of (indexByCompact.get(compact) || [])) {
        if (seen.has(row.file)) continue;
        seen.add(row.file);
        out.push(row);
      }
    }
    return out;
  }

  function resolveLogo(channelName = '', options = {}) {
    ensureIndex();
    const countryCode = resolveCountryCode(options.country || options.koolCategory || options.category || '');
    const rawName = String(channelName || '').trim();
    if (!rawName) return '';
    const key = normalizeLogoKey(rawName);
    const compact = compactLogoKey(rawName);
    if (!key && !compact) return '';

    const aliasKeys = LOGO_ALIASES[key] || LOGO_ALIASES[compact] || [];
    const searchKeys = [key, compact, ...aliasKeys];

    let hit = pickBest(collectByKeys(searchKeys), countryCode, key);
    if (!hit && key.split(' ').length >= 2) {
      // Try without first generic token
      const parts = key.split(' ');
      if (['tv', 'the', 'kanal', 'kanali'].includes(parts[0])) {
        hit = pickBest(collectByKeys([parts.slice(1).join(' ')]), countryCode, parts.slice(1).join(' '));
      }
    }
    if (!hit) return '';
    return publicUrlFor(hit.file);
  }

  function applyLogosToMap(db = {}, options = {}) {
    ensureIndex();
    let changed = 0;
    const force = options.force === true;
    for (const channel of Object.values(db || {})) {
      if (!channel || typeof channel !== 'object') continue;
      const current = String(channel.logo || '').trim();
      const looksRemoteGitHub = /githubusercontent\.com|github\.com\/.*\/raw\//i.test(current);
      const looksRelative = current && !/^https?:\/\//i.test(current) && !current.startsWith(publicBase + '/');
      const missing = !current || /default|placeholder|missing/i.test(current);
      const alreadyLocal = current.startsWith(publicBase + '/');
      if (!force && !missing && !looksRemoteGitHub && !looksRelative && !alreadyLocal && /^https?:\/\//i.test(current)) continue;

      const sourceKey = String(channel.sourceKey || channel.source || '');
      let countryHint = channel.koolCategory || channel.category || channel.country || '';
      if (!countryHint && (sourceKey === 'alb' || sourceKey === 'alb_vod' || sourceKey === 'api_tv' || sourceKey === 'api_sport')) {
        countryHint = 'AL';
      }
      const logo = resolveLogo(channel.name || '', { country: countryHint, koolCategory: channel.koolCategory });
      if (!logo) continue;
      if (channel.logo !== logo) {
        channel.logo = logo;
        changed += 1;
      }
    }
    return changed;
  }

  function getStats() {
    ensureIndex();
    return { fileCount, loadedAt, keys: indexByKey.size, logoRoot, publicBase };
  }

  rebuildIndex();

  return {
    rebuildIndex,
    resolveLogo,
    applyLogosToMap,
    getStats,
    normalizeLogoKey,
    resolveCountryCode,
    publicUrlFor
  };
}

module.exports = {
  createTvLogoService,
  normalizeLogoKey,
  resolveCountryCode,
  COUNTRY_FOLDER_BY_CODE,
  COUNTRY_CODE_BY_NAME
};
