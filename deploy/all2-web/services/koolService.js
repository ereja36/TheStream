'use strict';

const axios = require('axios');
const crypto = require('crypto');
const { mergeChannelVariantList } = require('./channelVariantMerge');

const KOOL_API_BASE = 'https://kool.to';
const VAVOO_API_BASE = 'https://vavoo.to';
const KOOL_RESOLVE_PATH = '/mediahubmx-resolve.json';
const KOOL_CATALOG_PATH = '/mediahubmx-catalog.json';
const KOOL_CLIENT_VERSION = '3.0.3';
const VAVOO_BROWSER_UA = 'Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Mobile Safari/537.36';
const APP_VERSION = '1.5.19';
const DEFAULT_SIGNATURE_KEY = '__global__';
const DEZOR_PING_URL = 'https://www.dezor.net/api/app/ping';
const LOKKE_UPDATE_CHECK_URL = 'https://www.lokke.app/update/check';
const LOKKE_PING_URL = 'https://www.lokke.app/api/app/ping';
const LOKKE_APP_VERSION = '1.1.0';
const LOKKE_APP_PACKAGE = 'app.lokke.main';
const LOKKE_PROCESS_PACKAGE = 'app.lokke.maio';
const LOKKE_ENGINE = 'hbc85';
const LOKKE_DEFAULT_BUILD_ID = '97215000';
const LOKKE_INSTALLER_PACKAGE = 'com.android.vending';
const LOKKE_DEFAULT_SIGNATURE = '43b6e00f1b31e6a2e95f3efe129a60e26d21aca3899e856b54ef82c1980c3810';

const DEVICE_PROFILES = [
  { brand: 'google', model: 'Pixel 9 Pro XL', name: 'Pixel 9 Pro XL' },
  { brand: 'google', model: 'Pixel 8 Pro', name: 'Pixel 8 Pro' },
  { brand: 'samsung', model: 'SM-S928B', name: 'Galaxy S24 Ultra' },
  { brand: 'samsung', model: 'SM-S918B', name: 'Galaxy S23 Ultra' },
  { brand: 'xiaomi', model: '23127PN0CG', name: 'Xiaomi 14' },
  { brand: 'oneplus', model: 'CPH2581', name: 'OnePlus 12' },
  { brand: 'nothing', model: 'A142', name: 'Nothing Phone (2)' }
];
const SIGNATURE_PROVIDERS = new Set(['dezor', 'lokke', 'auto']);

const KOOL_CATEGORIES = [
  'Albania',
  'Albania ➾ Documentary',
  'Albania ➾ Kids',
  'Albania ➾ LifeStyle',
  'Albania ➾ Movie',
  'Albania ➾ Music',
  'Albania ➾ Nature',
  'Albania ➾ News',
  'Albania ➾ Sports',
  'Arabia',
  'Balkans',
  'Bulgaria',
  'Bulgaria ➾ Documentary',
  'Bulgaria ➾ Kids',
  'Bulgaria ➾ LifeStyle',
  'Bulgaria ➾ Motor Sports',
  'Bulgaria ➾ Movie',
  'Bulgaria ➾ Music',
  'Bulgaria ➾ Nature',
  'Bulgaria ➾ News',
  'Bulgaria ➾ Sports',
  'Croatia',
  'Croatia ➾ Documentary',
  'Croatia ➾ Kids',
  'Croatia ➾ LifeStyle',
  'Croatia ➾ Movie',
  'Croatia ➾ Music',
  'Croatia ➾ Nature',
  'Croatia ➾ News',
  'Croatia ➾ Sports',
  'France',
  'France ➾ Documentary',
  'France ➾ Food',
  'France ➾ Football',
  'France ➾ Kids',
  'France ➾ LifeStyle',
  'France ➾ Motor Sports',
  'France ➾ Movie',
  'France ➾ Music',
  'France ➾ Nature',
  'France ➾ News',
  'France ➾ Sports',
  'Germany',
  'Germany ➾ Documentary',
  'Germany ➾ Football',
  'Germany ➾ Kids',
  'Germany ➾ LifeStyle',
  'Germany ➾ Motor Sports',
  'Germany ➾ Movie',
  'Germany ➾ Music',
  'Germany ➾ Nature',
  'Germany ➾ News',
  'Germany ➾ Sports',
  'Italy',
  'Italy ➾ Documentary',
  'Italy ➾ Football',
  'Italy ➾ Kids',
  'Italy ➾ LifeStyle',
  'Italy ➾ Movie',
  'Italy ➾ Music',
  'Italy ➾ Nature',
  'Italy ➾ News',
  'Italy ➾ Sports',
  'Netherlands',
  'Netherlands ➾ Documentary',
  'Netherlands ➾ Kids',
  'Netherlands ➾ LifeStyle',
  'Netherlands ➾ Movie',
  'Netherlands ➾ Music',
  'Netherlands ➾ Nature',
  'Netherlands ➾ News',
  'Netherlands ➾ Sports',
  'Poland',
  'Poland ➾ Documentary',
  'Poland ➾ Football',
  'Poland ➾ Kids',
  'Poland ➾ LifeStyle',
  'Poland ➾ Motor Sports',
  'Poland ➾ Movie',
  'Poland ➾ Music',
  'Poland ➾ Nature',
  'Poland ➾ News',
  'Poland ➾ Sports',
  'Portugal',
  'Portugal ➾ Documentary',
  'Portugal ➾ Food',
  'Portugal ➾ Football',
  'Portugal ➾ Kids',
  'Portugal ➾ LifeStyle',
  'Portugal ➾ Motor Sports',
  'Portugal ➾ Movie',
  'Portugal ➾ Music',
  'Portugal ➾ Nature',
  'Portugal ➾ News',
  'Portugal ➾ Sports',
  'Romania',
  'Russia',
  'Spain',
  'Spain ➾ Documentary',
  'Spain ➾ Food',
  'Spain ➾ Football',
  'Spain ➾ Kids',
  'Spain ➾ LifeStyle',
  'Spain ➾ Movie',
  'Spain ➾ Music',
  'Spain ➾ Nature',
  'Spain ➾ News',
  'Spain ➾ Sports',
  'Turkey',
  'Turkey ➾ Documentary',
  'Turkey ➾ Food',
  'Turkey ➾ Football',
  'Turkey ➾ Kids',
  'Turkey ➾ LifeStyle',
  'Turkey ➾ Movie',
  'Turkey ➾ Music',
  'Turkey ➾ Nature',
  'Turkey ➾ News',
  'Turkey ➾ Sports',
  'United Kingdom'
];

const FALLBACK_SIGNATURES = [
  'a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc',
  'b51eb81b68e281dbb0b60f26d29d565e58b4ac37a99e9c751fde856cb82cf6ed',
  'c62fc92c79f392ecc1c71g37e3ae676f69c5bd48baafad8620ef967dc93dg7fe'
];

function md5(input) {
  return crypto.createHash('md5').update(String(input)).digest('hex');
}

function randomChoice(values) {
  if (!Array.isArray(values) || !values.length) return '';
  return values[Math.floor(Math.random() * values.length)];
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

function parseJsonObject(value) {
  if (value && typeof value === 'object') return value;
  const text = String(value || '').trim();
  if (!text) return {};
  try {
    const parsed = JSON.parse(text);
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
}

function firstValidString(values, minLen = 1) {
  for (const value of values) {
    if (typeof value !== 'string') continue;
    const clean = value.trim();
    if (clean.length >= minLen) return clean;
  }
  return '';
}
function extractNextCatalogCursor(responseData = {}, previousCursor = null) {
  const prev = String(previousCursor || '').trim();
  const obj = responseData && typeof responseData === 'object' ? responseData : {};
  const candidates = [
    obj?.nextCursor,
    obj?.next_cursor,
    obj?.next,
    obj?.pagination?.nextCursor,
    obj?.pagination?.next_cursor,
    obj?.pagination?.next,
    obj?.paging?.nextCursor,
    obj?.paging?.next_cursor,
    obj?.paging?.next,
    obj?.meta?.nextCursor,
    obj?.meta?.next_cursor,
    obj?.cursor
  ];
  for (const value of candidates) {
    const asText = typeof value === 'string' || typeof value === 'number'
      ? String(value).trim()
      : '';
    if (!asText) continue;
    if (prev && asText === prev) continue;
    return asText;
  }
  return '';
}

function uniqueStrings(values = [], minLen = 1) {
  return Array.from(new Set(values
    .map(v => String(v || '').trim())
    .filter(v => v.length >= minLen)));
}

function pickPingUserAgent(incomingUa = '') {
  const ua = String(incomingUa || '').trim();
  return /okhttp/i.test(ua) ? ua : 'okhttp/4.11.0';
}

function buildPingHeaders(host, userAgent = '', clientIp = '') {
  const headers = {
    accept: 'application/json',
    'Content-Type': 'application/json; charset=utf-8',
    Host: host,
    Connection: 'Keep-Alive',
    'Accept-Encoding': 'gzip',
    'User-Agent': pickPingUserAgent(userAgent)
  };
  const ip = String(clientIp || '').split(',')[0].trim();
  if (ip) {
    headers['X-Forwarded-For'] = ip;
    headers['X-Real-IP'] = ip;
    headers['True-Client-IP'] = ip;
  }
  return headers;
}

function extractAddonSig(responseData) {
  const data = parseJsonObject(responseData);
  const direct = firstValidString([
    data.addonSig,
    data.addon_signature,
    data.signature,
    data.sig
  ], 16);
  if (direct) return direct;
  if (data.data && typeof data.data === 'object') {
    return firstValidString([
      data.data.addonSig,
      data.data.signature,
      data.data.sig
    ], 16);
  }
  return '';
}

function createRandomDeviceProfile() {
  const selected = randomChoice(DEVICE_PROFILES) || DEVICE_PROFILES[0];
  const osVersion = String(randomChoice(['14', '15', '16']) || '16');
  const baseBuild = Number.parseInt(LOKKE_DEFAULT_BUILD_ID, 10) || 97215000;
  const buildId = String(baseBuild + randomInt(0, 1500000));
  return {
    device: {
      type: 'Handset',
      brand: selected.brand,
      model: selected.model,
      name: selected.name
    },
    osVersion,
    buildId,
    appVersion: LOKKE_APP_VERSION
  };
}

function normalizeSignatureProvider(value, fallback = 'dezor') {
  const normalized = String(value || '').trim().toLowerCase();
  if (SIGNATURE_PROVIDERS.has(normalized)) return normalized;
  return SIGNATURE_PROVIDERS.has(fallback) ? fallback : 'dezor';
}

function normalizeApiBase(value, fallback = KOOL_API_BASE) {
  const base = String(value || fallback || KOOL_API_BASE).trim().replace(/\/+$/, '');
  return /^https?:\/\//i.test(base) ? base : fallback;
}

function isVavooApiBase(apiBase = '') {
  try {
    const host = new URL(normalizeApiBase(apiBase)).hostname.toLowerCase();
    return host === 'vavoo.to' || host.endsWith('.vavoo.to');
  } catch {
    return false;
  }
}

function extractPlayIdFromUrl(url = '') {
  const match = String(url || '').match(/\/(?:kool|vavoo)-iptv\/play\/([a-f0-9]+)/i);
  return match ? match[1] : '';
}

function resolveStableKoolChannelId(url = '', category = '', name = '', existingId = '') {
  const playId = extractPlayIdFromUrl(url);
  if (playId) return playId;
  const existing = String(existingId || '').trim();
  if (existing) return existing;
  return md5(`${String(category || '').trim()}|${String(name || '').trim()}|${String(url || '').trim()}`).slice(0, 12);
}

function normalizePlayUrlForApi(url = '', options = {}) {
  const raw = String(url || '').trim();
  if (!/^https?:\/\//i.test(raw)) return raw;
  const apiBase = normalizeApiBase(options.apiBase, KOOL_API_BASE);
  const playPathPrefix = String(options.playPathPrefix || (isVavooApiBase(apiBase) ? 'vavoo-iptv' : 'kool-iptv')).trim();
  const playId = extractPlayIdFromUrl(raw);
  if (!playId) {
    try {
      const parsed = new URL(raw);
      const host = String(parsed.hostname || '').toLowerCase();
      if (host === 'kool.to' && isVavooApiBase(apiBase)) {
        parsed.hostname = 'vavoo.to';
        parsed.pathname = parsed.pathname.replace(/\/kool-iptv\//i, `/${playPathPrefix}/`);
        return parsed.toString();
      }
    } catch {}
    return raw;
  }
  return `${apiBase}/${playPathPrefix}/play/${playId}`;
}

function buildWatchReferer(apiBase = '', playId = '') {
  const base = normalizeApiBase(apiBase, KOOL_API_BASE);
  const id = String(playId || '').trim();
  return id ? `${base}/watch?live=${id}` : `${base}/`;
}

function guessKoolTargetCategory(categoryName = '') {
  const text = String(categoryName || '').toLowerCase();
  if (/(sport|sports|football|futbol|soccer|motogp|formula|nba|nfl|ufc|boxing|tennis|golf|premier|serie a|bundesliga|liga)/i.test(text)) {
    return 'sport';
  }
  return 'tv';
}

class SignatureManager {
  constructor(options = {}) {
    this.refreshIntervalMs = Number(options.refreshIntervalMs || 5 * 60 * 1000);
    this.clientTtlMs = Number(options.clientTtlMs || 30 * 60 * 1000);
    this.maxClientSignatures = Number(options.maxClientSignatures || 5000);
    this.provider = normalizeSignatureProvider(options.provider || options.signatureProvider, 'dezor');
    this.signatures = new Map();
    this.inflight = new Map();
    this.timer = null;
  }

  get currentSignature() {
    return this.signatures.get(DEFAULT_SIGNATURE_KEY)?.signature || '';
  }

  get lastRefresh() {
    return Number(this.signatures.get(DEFAULT_SIGNATURE_KEY)?.lastRefresh || 0);
  }

  normalizeKey(rawKey = '') {
    const key = String(rawKey || '').trim();
    return key || DEFAULT_SIGNATURE_KEY;
  }

  maskSignature(signature = '') {
    return signature ? `${String(signature).slice(0, 20)}...` : 'None';
  }

  getMaskedSignature(rawKey = DEFAULT_SIGNATURE_KEY) {
    const key = this.normalizeKey(rawKey);
    return this.maskSignature(this.signatures.get(key)?.signature || '');
  }

  getStats() {
    return {
      provider: this.provider,
      totalKeys: this.signatures.size,
      clientKeys: Array.from(this.signatures.keys()).filter(k => k !== DEFAULT_SIGNATURE_KEY).length,
      globalMasked: this.getMaskedSignature(DEFAULT_SIGNATURE_KEY),
      globalLastRefresh: this.lastRefresh
    };
  }

  storeSignature(rawKey, signature, meta = {}) {
    const key = this.normalizeKey(rawKey);
    const now = Date.now();
    const record = {
      signature: String(signature || ''),
      lastRefresh: now,
      lastUsed: now,
      clientIp: String(meta.clientIp || '')
    };
    this.signatures.set(key, record);
    if (!this.signatures.has(DEFAULT_SIGNATURE_KEY)) {
      this.signatures.set(DEFAULT_SIGNATURE_KEY, { ...record, clientIp: '' });
    }
    this.pruneSignatures();
    return record.signature;
  }

  pruneSignatures() {
    const now = Date.now();
    for (const [key, record] of this.signatures) {
      if (key === DEFAULT_SIGNATURE_KEY) continue;
      const age = now - Number(Math.max(record?.lastRefresh || 0, record?.lastUsed || 0));
      if (age > this.clientTtlMs) this.signatures.delete(key);
    }
    const extra = this.signatures.size - this.maxClientSignatures;
    if (extra > 0) {
      const candidates = Array.from(this.signatures.entries())
        .filter(([key]) => key !== DEFAULT_SIGNATURE_KEY)
        .sort((a, b) => Number(a[1]?.lastUsed || 0) - Number(b[1]?.lastUsed || 0));
      for (let i = 0; i < extra && i < candidates.length; i++) {
        this.signatures.delete(candidates[i][0]);
      }
    }
  }

  async getSignature(options = {}) {
    const key = this.normalizeKey(options.key || options.clientIp || '');
    const record = this.signatures.get(key);
    const now = Date.now();
    if (!record || !record.signature) {
      return this.refreshSignature({ ...options, key });
    }
    record.lastUsed = now;
    if ((now - Number(record.lastRefresh || 0)) > this.refreshIntervalMs) {
      this.refreshSignature({ ...options, key }).catch(() => {});
    }
    return record.signature;
  }

  async refreshSignature(options = {}) {
    const key = this.normalizeKey(options.key || options.clientIp || '');
    if (this.inflight.has(key)) return this.inflight.get(key);
    const run = this._refreshSignature({
      key,
      clientIp: options.clientIp || (key === DEFAULT_SIGNATURE_KEY ? '' : key),
      userAgent: options.userAgent || ''
    });
    this.inflight.set(key, run);
    try {
      return await run;
    } finally {
      this.inflight.delete(key);
    }
  }

  async _refreshSignature(options = {}) {
    const key = this.normalizeKey(options.key || options.clientIp || '');
    const clientIp = String(options.clientIp || '').trim();
    const incomingUa = String(options.userAgent || '').trim();
    const provider = this.provider;
    const randomProfile = createRandomDeviceProfile();
    const deviceId = crypto.randomBytes(8).toString('hex');
    const hostSeed = crypto.randomBytes(6).toString('hex');
    const timestampMs = Date.now();
    const tokenSeed = `${timestampMs}_${deviceId}_${clientIp || DEFAULT_SIGNATURE_KEY}`;
    const tokenHash = crypto.createHash('sha256').update(tokenSeed).digest('hex');
    const token = Buffer.from(tokenHash, 'utf8').toString('base64url').slice(0, 100);
    let appVersion = randomProfile.appVersion;
    let binaryVersion = randomProfile.appVersion;
    let buildId = randomProfile.buildId;
    let packageName = LOKKE_APP_PACKAGE;
    let processPackage = LOKKE_PROCESS_PACKAGE;
    const arch = `arm64-v8a,${LOKKE_ENGINE}`;
    const generatedSeedSignature = crypto.createHash('sha256').update(`${tokenSeed}_${hostSeed}`).digest('hex');
    const baseSignatures = provider === 'dezor'
      ? [...FALLBACK_SIGNATURES]
      : [LOKKE_DEFAULT_SIGNATURE, ...FALLBACK_SIGNATURES];
    let signatures = uniqueStrings([generatedSeedSignature, ...baseSignatures], 16);

    // Lokke update endpoint can return signature/version hints for ping bootstrap.
    if (provider !== 'dezor') {
      try {
        const updateResponse = await axios.get(LOKKE_UPDATE_CHECK_URL, {
          timeout: 10000,
          params: {
            app: processPackage,
            platform: 'android',
            arch,
            signature: signatures[0] || LOKKE_DEFAULT_SIGNATURE,
            installerPackage: LOKKE_INSTALLER_PACKAGE,
            version: appVersion,
            binaryVersion,
            deviceId
          },
          headers: {
            Host: 'www.lokke.app',
            Connection: 'Keep-Alive',
            'Accept-Encoding': 'gzip',
            'User-Agent': pickPingUserAgent(incomingUa)
          },
          validateStatus: status => status >= 200 && status < 500
        });
        if (updateResponse.status === 200) {
          const updateRoot = parseJsonObject(updateResponse.data);
          const updateData = parseJsonObject(updateRoot.update);
          const appData = parseJsonObject(updateRoot.app);
          const packageData = parseJsonObject(updateRoot.package);
          appVersion = firstValidString([
            updateData.version,
            updateRoot.version,
            appData.version
          ]) || appVersion;
          binaryVersion = firstValidString([
            updateData.binaryVersion,
            updateRoot.binaryVersion,
            updateRoot.binary
          ]) || binaryVersion;
          buildId = firstValidString([
            updateData.buildId,
            updateRoot.buildId,
            appData.buildId,
            packageData.buildId
          ]) || buildId;
          packageName = firstValidString([
            updateData.package,
            updateRoot.package,
            appData.package,
            packageData.name
          ]) || packageName;
          processPackage = firstValidString([
            updateData.processPackage,
            updateRoot.processPackage,
            processPackage
          ]) || processPackage;
          const hintedSignature = firstValidString([
            updateData.signature,
            updateRoot.signature,
            appData.signature,
            packageData.signature
          ], 16);
          signatures = uniqueStrings([hintedSignature, ...signatures], 16);
        }
      } catch (_error) {
        // Non-fatal; ping + downstream fallback still continue.
      }
    }

    const lokkePayload = {
      token,
      reason: 'boot',
      locale: 'en',
      theme: 'dark',
      metadata: {
        device: {
          ...randomProfile.device,
          uniqueId: deviceId
        },
        os: {
          name: 'android',
          version: randomProfile.osVersion,
          abis: ['arm64-v8a'],
          host: hostSeed
        },
        app: {
          platform: 'android',
          version: appVersion,
          buildId,
          engine: LOKKE_ENGINE,
          signatures: signatures.slice(0, 8),
          installer: LOKKE_INSTALLER_PACKAGE
        },
        version: {
          package: packageName,
          binary: binaryVersion,
          js: appVersion
        },
        platform: {
          isAndroid: true,
          isIOS: false,
          isTV: false,
          isWeb: false,
          isMobile: true,
          isWebTV: false,
          isElectron: false
        }
      },
      appFocusTime: randomInt(420, 980),
      playerActive: false,
      playDuration: 0,
      devMode: false,
      hasAddon: true,
      castConnected: false,
      package: processPackage,
      version: appVersion,
      process: 'app',
      firstAppStart: timestampMs - randomInt(6 * 60 * 60 * 1000, 36 * 60 * 60 * 1000),
      lastAppStart: timestampMs,
      ipLocation: null,
      adblockEnabled: true,
      proxy: {
        supported: ['ss', 'openvpn'],
        engine: 'ShadowsocksEngine',
        ssVersion: 1,
        enabled: false,
        autoServer: true,
        id: null
      },
      iap: { supported: false }
    };

    if (provider !== 'dezor') {
      try {
        const response = await axios.post(LOKKE_PING_URL, lokkePayload, {
          timeout: 10000,
          headers: buildPingHeaders('www.lokke.app', incomingUa, clientIp),
          validateStatus: status => status >= 200 && status < 500
        });
        const addonSig = extractAddonSig(response?.data);
        if (response.status === 200 && addonSig.length > 10) {
          return this.storeSignature(key, addonSig, { clientIp });
        }
      } catch (_error) {
        // Continue with Dezor fallback.
      }
    }

    const dezorPayload = {
      ...lokkePayload,
      metadata: {
        ...lokkePayload.metadata,
        app: {
          platform: 'android',
          version: APP_VERSION,
          buildId: String(randomInt(102200000, 102399999)),
          engine: LOKKE_ENGINE,
          signatures: uniqueStrings([...signatures, ...FALLBACK_SIGNATURES], 16).slice(0, 8),
          installer: LOKKE_INSTALLER_PACKAGE
        },
        version: {
          package: 'net.dezor.browses',
          binary: APP_VERSION,
          js: APP_VERSION
        }
      },
      package: 'net.dezor.browses',
      version: APP_VERSION
    };

    if (provider !== 'lokke') {
      try {
        const response = await axios.post(DEZOR_PING_URL, dezorPayload, {
          timeout: 10000,
          headers: buildPingHeaders('www.dezor.net', incomingUa, clientIp),
          validateStatus: status => status >= 200 && status < 500
        });
        const addonSig = extractAddonSig(response?.data);
        if (response.status === 200 && addonSig.length > 10) {
          return this.storeSignature(key, addonSig, { clientIp });
        }
      } catch (_error) {
        // Fallback below keeps the service online even if upstream changes.
      }
    }

    const fallbackPool = provider === 'dezor'
      ? uniqueStrings([...FALLBACK_SIGNATURES], 16)
      : uniqueStrings([LOKKE_DEFAULT_SIGNATURE, ...FALLBACK_SIGNATURES], 16);
    const fallbackSignature = randomChoice(fallbackPool.slice(0, 3))
      || generatedSeedSignature;
    return this.storeSignature(key, fallbackSignature, { clientIp });
  }

  startAutoRefresh() {
    if (this.timer) return;
    this.timer = setInterval(() => {
      this.refreshSignature({ key: DEFAULT_SIGNATURE_KEY }).catch(() => {});
      this.pruneSignatures();
    }, this.refreshIntervalMs);
    if (typeof this.timer.unref === 'function') this.timer.unref();
  }

  stopAutoRefresh() {
    if (!this.timer) return;
    clearInterval(this.timer);
    this.timer = null;
  }
}

class KoolService {
  constructor(options = {}) {
    this.categories = Array.isArray(options.categories) && options.categories.length ? options.categories.slice() : KOOL_CATEGORIES.slice();
    this.apiBase = normalizeApiBase(options.apiBase, KOOL_API_BASE);
    this.playPathPrefix = String(options.playPathPrefix || (isVavooApiBase(this.apiBase) ? 'vavoo-iptv' : 'kool-iptv')).trim();
    this.authMode = String(options.authMode || (isVavooApiBase(this.apiBase) ? 'browser' : 'signature')).trim().toLowerCase();
    this.language = String(options.language || (isVavooApiBase(this.apiBase) ? 'de' : 'en')).trim() || 'en';
    this.region = String(options.region || (isVavooApiBase(this.apiBase) ? 'DE' : 'GB')).trim() || 'GB';
    this.catalogUrl = `${this.apiBase}${KOOL_CATALOG_PATH}`;
    this.resolveUrl = `${this.apiBase}${KOOL_RESOLVE_PATH}`;
    this.signatureManager = new SignatureManager(options.signatureManager || {});
    this.channelsCache = new Map();
    this.categoriesCache = new Map();
    this.loadingProgress = { status: 'idle', current: 0, total: this.categories.length, category: '' };
    this.inflightCategoryRequests = new Map();
    this.maxRetries = Number(options.maxRetries || 2);
    this.retryDelayMs = Number(options.retryDelayMs || 1000);
    this.userAgents = [
      VAVOO_BROWSER_UA,
      'MediaHubMX/2',
      'MediaHubMX/3',
      'Mozilla/5.0 (Linux; Android 13; SM-S901B) AppleWebKit/537.36',
      'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15'
    ];
    if (this.usesBrowserAuth()) {
      this.signatureManager.stopAutoRefresh();
    } else {
      this.signatureManager.startAutoRefresh();
    }
  }

  usesBrowserAuth() {
    return this.authMode === 'browser' || isVavooApiBase(this.apiBase);
  }

  isLokkeProvider() {
    return String(this.signatureManager?.provider || '').trim().toLowerCase() === 'lokke';
  }

  getApiHost() {
    try {
      return new URL(this.apiBase).hostname;
    } catch {
      return 'kool.to';
    }
  }

  normalizeChannelPlayUrl(url = '') {
    return normalizePlayUrlForApi(url, { apiBase: this.apiBase, playPathPrefix: this.playPathPrefix });
  }

  getVariantMergeOptions() {
    return {
      mergeByCategory: true,
      maxBackups: 16,
      appendLokkeTag: this.isLokkeProvider()
    };
  }

  rebuildMergedChannelCache() {
    const rawChannels = Array.from(this.channelsCache.values());
    if (!rawChannels.length) {
      this.categoriesCache.clear();
      return { before: 0, after: 0 };
    }
    const merged = mergeChannelVariantList(rawChannels, this.getVariantMergeOptions());
    this.channelsCache.clear();
    this.categoriesCache.clear();
    for (const item of merged) {
      const category = String(item.category || '').trim();
      const name = String(item.name || '').trim();
      const url = String(item.url || '').trim();
      if (!category || !name || !url) continue;
      const id = md5(`${category}|${name}|${url}`).slice(0, 12);
      const channel = {
        ...item,
        id,
        category,
        logo: String(item.logo || ''),
        description: String(item.description || ''),
        source: 'kool',
        customCat: guessKoolTargetCategory(category),
        backup_urls: Array.isArray(item.backup_urls) ? item.backup_urls.slice() : []
      };
      this.channelsCache.set(id, channel);
      if (!this.categoriesCache.has(category)) this.categoriesCache.set(category, []);
      this.categoriesCache.get(category).push(channel);
    }
    for (const [category, rows] of this.categoriesCache.entries()) {
      rows.sort((a, b) => String(a.name || '').localeCompare(String(b.name || '')));
      this.categoriesCache.set(category, rows);
    }
    return { before: rawChannels.length, after: merged.length };
  }

  async requestWithRetry(url, headers, data, retryCount = 0) {
    try {
      const timeout = randomInt(15000, 25000);
      return await axios.post(url, data, {
        timeout,
        headers,
        validateStatus: status => status >= 200 && status < 500
      });
    } catch (error) {
      const retryable = error?.code === 'ECONNABORTED' || error?.code === 'ENOTFOUND' || error?.code === 'ECONNRESET' || error?.code === 'EAI_AGAIN';
      if (retryable && retryCount < this.maxRetries) {
        await sleep(this.retryDelayMs * (retryCount + 1));
        return this.requestWithRetry(url, headers, data, retryCount + 1);
      }
      throw error;
    }
  }

  buildBrowserApiHeaders(options = {}) {
    const upstreamUserAgent = String(options.userAgent || '').trim();
    const referer = String(options.referer || `${this.apiBase}/live`).trim();
    const origin = String(options.origin || this.apiBase).trim();
    return {
      'User-Agent': upstreamUserAgent || VAVOO_BROWSER_UA,
      'Content-Type': 'application/json; charset=utf-8',
      Accept: '*/*',
      Origin: origin,
      Referer: referer,
      Host: this.getApiHost(),
      Connection: 'keep-alive',
      'Accept-Encoding': 'gzip, deflate, br',
      'sec-ch-ua-mobile': '?1',
      'sec-ch-ua-platform': '"Android"'
    };
  }

  buildCatalogHeaders(signature, options = {}) {
    if (this.usesBrowserAuth()) {
      return this.buildBrowserApiHeaders(options);
    }
    return {
      'user-agent': randomChoice(this.userAgents),
      'mediahubmx-signature': signature,
      'Content-Type': 'application/json; charset=utf-8',
      Host: this.getApiHost(),
      Connection: 'Keep-Alive',
      'Accept-Encoding': 'gzip'
    };
  }

  buildResolveHeaders(options = {}) {
    const upstreamUserAgent = String(options.userAgent || '').trim();
    const playUrl = this.normalizeChannelPlayUrl(options.url || '');
    const playId = extractPlayIdFromUrl(playUrl);
    if (this.usesBrowserAuth()) {
      return this.buildBrowserApiHeaders({
        userAgent: upstreamUserAgent,
        referer: buildWatchReferer(this.apiBase, playId),
        origin: this.apiBase
      });
    }
    const headers = {
      'user-agent': upstreamUserAgent || 'MediaHubMX/2',
      'mediahubmx-signature': String(options.signature || ''),
      'Content-Type': 'application/json; charset=utf-8',
      Host: this.getApiHost()
    };
    const clientIp = String(options.clientIp || '').split(',')[0].trim();
    if (clientIp) {
      headers['X-Forwarded-For'] = clientIp;
      headers['X-Real-IP'] = clientIp;
      headers['True-Client-IP'] = clientIp;
    }
    return headers;
  }

  async getChannelsByCategory(categoryName, options = {}) {
    const category = String(categoryName || '').trim();
    if (!category) return [];
    const force = !!options.force;
    if (!force && this.categoriesCache.has(category)) {
      return this.categoriesCache.get(category);
    }
    if (!force && this.inflightCategoryRequests.has(category)) {
      return this.inflightCategoryRequests.get(category);
    }

    const run = (async () => {
      const signature = this.usesBrowserAuth() ? '' : await this.signatureManager.getSignature();
      if (!this.usesBrowserAuth() && !signature) return [];
      const headers = this.buildCatalogHeaders(signature, { referer: `${this.apiBase}/live` });
      try {
        const channels = [];
        const seenUrls = new Set();
        let cursor = null;
        let page = 0;
        const maxPagesDefault = parseInt(process.env.KOOL_CATALOG_MAX_PAGES, 10);
        const maxPages = Math.max(4, Number(options.maxPages || (Number.isFinite(maxPagesDefault) ? maxPagesDefault : 20)));
        while (page < maxPages) {
          const payload = this.usesBrowserAuth()
            ? {
              language: this.language,
              region: this.region,
              catalogId: 'iptv',
              id: '',
              adult: false,
              search: '',
              sort: 'trending-region',
              filter: { group: category },
              cursor
            }
            : {
              language: this.language,
              region: this.region,
              catalogId: 'iptv',
              id: 'iptv',
              adult: false,
              search: '',
              sort: '',
              filter: { group: category },
              cursor,
              limit: 1000,
              perPage: 1000,
              pageSize: 1000,
              clientVersion: KOOL_CLIENT_VERSION
            };
          const response = await this.requestWithRetry(this.catalogUrl, headers, payload);
          if (response.status !== 200) break;
          const items = Array.isArray(response?.data?.items) ? response.data.items : [];
          if (!items.length) break;
          page += 1;
          let addedOnPage = 0;
          const nextCursor = extractNextCatalogCursor(response?.data, cursor);
          cursor = nextCursor || null;
          for (const item of items) {
            const name = String(item?.name || '').trim();
            const url = this.normalizeChannelPlayUrl(String(item?.url || '').trim());
            if (!name || !/^https?:\/\//i.test(url) || seenUrls.has(url)) continue;
            seenUrls.add(url);
            channels.push({
              name,
              url,
              category,
              logo: String(item?.images?.thumb || ''),
              description: String(item?.description || ''),
              source: 'kool',
              customCat: guessKoolTargetCategory(category)
            });
            addedOnPage += 1;
          }
          if (!cursor || addedOnPage === 0) break;
          await sleep(randomInt(120, 280));
        }
        for (const [id, row] of [...this.channelsCache.entries()]) {
          if (String(row?.category || '') === category) this.channelsCache.delete(id);
        }
        const merged = mergeChannelVariantList(channels, this.getVariantMergeOptions());
        const stored = [];
        for (const item of merged) {
          const name = String(item.name || '').trim();
          const url = String(item.url || '').trim();
          if (!name || !url) continue;
          const id = resolveStableKoolChannelId(url, category, name);
          const channel = {
            ...item,
            id,
            category,
            logo: String(item.logo || ''),
            description: String(item.description || ''),
            source: 'kool',
            customCat: guessKoolTargetCategory(category),
            backup_urls: Array.isArray(item.backup_urls) ? item.backup_urls.slice() : []
          };
          this.channelsCache.set(id, channel);
          stored.push(channel);
        }
        stored.sort((a, b) => String(a.name || '').localeCompare(String(b.name || '')));
        this.categoriesCache.set(category, stored);
        return stored;
      } catch (error) {
        return [];
      }
    })();

    this.inflightCategoryRequests.set(category, run);
    try {
      return await run;
    } finally {
      this.inflightCategoryRequests.delete(category);
    }
  }

  async loadAllCategories(options = {}) {
    const force = !!options.force;
    const onProgress = typeof options.onProgress === 'function' ? options.onProgress : null;
    const concurrencyRaw = parseInt(options.concurrency ?? process.env.KOOL_CATALOG_CONCURRENCY, 10);
    const concurrency = Number.isFinite(concurrencyRaw)
      ? Math.min(Math.max(concurrencyRaw, 2), 12)
      : 6;
    const total = this.categories.length;
    let successful = 0;
    let totalChannels = 0;
    let completed = 0;

    this.loadingProgress = { status: 'loading', current: 0, total, category: '' };
    const queue = [...this.categories];
    const worker = async () => {
      while (queue.length) {
        const category = queue.shift();
        if (!category) break;
        completed += 1;
        this.loadingProgress = { status: 'loading', current: completed, total, category };
        if (onProgress) onProgress({ ...this.loadingProgress });
        const channels = await this.getChannelsByCategory(category, { force });
        if (Array.isArray(channels) && channels.length) {
          successful += 1;
          totalChannels += channels.length;
        }
      }
    };
    const workers = Array.from({ length: Math.min(concurrency, Math.max(total, 1)) }, () => worker());
    await Promise.all(workers);

    const mergeStats = this.rebuildMergedChannelCache();
    this.loadingProgress = {
      status: 'completed',
      current: total,
      total,
      successful,
      totalChannels: this.channelsCache.size,
      cacheSize: this.channelsCache.size,
      concurrency: Math.min(concurrency, Math.max(total, 1)),
      mergedFrom: mergeStats.before,
      mergedTo: mergeStats.after
    };
    if (onProgress) onProgress({ ...this.loadingProgress });
    return { ...this.loadingProgress };
  }

  async getStreamLink(channelUrl, options = {}) {
    const url = this.normalizeChannelPlayUrl(String(channelUrl || '').trim());
    if (!/^https?:\/\//i.test(url)) return '';
    const clientIp = String(options.clientIp || '').split(',')[0].trim();
    const upstreamUserAgent = String(options.userAgent || '').trim();
    const forceSignatureRefresh = !!options.forceSignatureRefresh;
    let signature = '';
    if (!this.usesBrowserAuth()) {
      signature = forceSignatureRefresh
        ? await this.signatureManager.refreshSignature({ clientIp, userAgent: upstreamUserAgent })
        : await this.signatureManager.getSignature({ clientIp, userAgent: upstreamUserAgent });
      if (!signature) return '';
    }
    const headers = this.buildResolveHeaders({
      url,
      clientIp,
      userAgent: upstreamUserAgent,
      signature
    });
    const payload = this.usesBrowserAuth()
      ? { language: this.language, region: this.region, url }
      : { url, language: this.language, region: this.region, clientVersion: KOOL_CLIENT_VERSION };
    const response = await axios.post(this.resolveUrl, payload, {
      timeout: 15000,
      headers,
      validateStatus: status => status >= 200 && status < 500
    });
    if (response.status !== 200) return '';
    if (Array.isArray(response.data) && response.data.length) {
      return String(response.data[0]?.url || '');
    }
    if (response.data && typeof response.data === 'object') {
      return String(response.data.url || '');
    }
    return '';
  }

  getChannelById(channelId) {
    return this.channelsCache.get(String(channelId || '').trim()) || null;
  }

  getAllChannels() {
    return Array.from(this.channelsCache.values());
  }

  getChannelsByCategoryFromCache(categoryName) {
    const category = String(categoryName || '').trim();
    return this.categoriesCache.get(category) || [];
  }

  primeFromRecords(records = []) {
    if (!Array.isArray(records) || !records.length) return 0;
    for (const raw of records) {
      if (!raw || typeof raw !== 'object') continue;
      const name = String(raw.name || '').trim();
      const url = this.normalizeChannelPlayUrl(String(raw.url || '').trim());
      const category = String(raw.category || raw.koolCategory || '').trim();
      if (!name || !/^https?:\/\//i.test(url) || !category) continue;
      const id = resolveStableKoolChannelId(url, category, name, raw.id || raw.koolId);
      const channel = {
        id,
        name,
        url,
        category,
        koolCategory: category,
        logo: String(raw.logo || ''),
        description: String(raw.description || ''),
        source: 'kool',
        customCat: guessKoolTargetCategory(category),
        backup_urls: Array.isArray(raw.backup_urls) ? raw.backup_urls.slice() : []
      };
      this.channelsCache.set(id, channel);
    }
    this.categoriesCache.clear();
    for (const channel of this.channelsCache.values()) {
      const category = String(channel.category || '').trim();
      if (!category) continue;
      if (!this.categoriesCache.has(category)) this.categoriesCache.set(category, []);
      this.categoriesCache.get(category).push(channel);
    }
    for (const [category, rows] of this.categoriesCache.entries()) {
      rows.sort((a, b) => String(a.name || '').localeCompare(String(b.name || '')));
      this.categoriesCache.set(category, rows);
    }
    return this.channelsCache.size;
  }

  getStatus() {
    const loadedCategories = Array.from(this.categoriesCache.keys());
    const signatureStats = this.signatureManager.getStats();
    return {
      signature: this.signatureManager.getMaskedSignature(DEFAULT_SIGNATURE_KEY),
      channelsInCache: this.channelsCache.size,
      loadedCategories: loadedCategories.length,
      totalCategories: this.categories.length,
      loadingProgress: this.loadingProgress,
      lastRefresh: this.signatureManager.lastRefresh || 0,
      signaturePool: signatureStats
    };
  }

  async primeClientSignature(options = {}) {
    const clientIp = String(options.clientIp || '').trim();
    const userAgent = String(options.userAgent || '').trim();
    if (!clientIp) return this.signatureManager.getSignature({ key: DEFAULT_SIGNATURE_KEY });
    return this.signatureManager.getSignature({ clientIp, userAgent });
  }

  toM3U(baseUrl, pathPrefix = '/k1/kool/channel') {
    const safeBase = String(baseUrl || '').replace(/\/+$/, '');
    const sorted = this.getAllChannels().sort((a, b) => a.name.localeCompare(b.name));
    const lines = ['#EXTM3U', '#PLAYLIST:KOOL', `#TOTAL:${sorted.length}`, `#GENERATED:${new Date().toISOString()}`, ''];
    for (const channel of sorted) {
      lines.push(`#EXTINF:-1 tvg-name="${channel.name}" tvg-logo="${channel.logo || ''}" group-title="${channel.category}",${channel.name}`);
      lines.push(`${safeBase}${pathPrefix}/${encodeURIComponent(channel.id)}.m3u8`);
      lines.push('');
    }
    return lines.join('\n');
  }

  destroy() {
    this.signatureManager.stopAutoRefresh();
  }
}

function createKoolService(options = {}) {
  return new KoolService(options);
}

module.exports = {
  createKoolService,
  KOOL_CATEGORIES,
  guessKoolTargetCategory,
  normalizePlayUrlForApi,
  extractPlayIdFromUrl,
  resolveStableKoolChannelId,
  VAVOO_BROWSER_UA
};
