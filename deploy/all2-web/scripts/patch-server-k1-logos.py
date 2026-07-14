#!/usr/bin/env python3
"""Patch all2 server.js for logos / A3 / Lokke / prod-api cleanup."""
from pathlib import Path
import re
import shutil
from datetime import datetime

SERVER = Path('/root/all/all2/server.js')
backup = SERVER.with_suffix(SERVER.suffix + f'.bak-k1logos-{datetime.now().strftime("%Y%m%d-%H%M%S")}')
shutil.copy2(SERVER, backup)
text = SERVER.read_text(encoding='utf-8')
original = text

def ensure_once(label, old, new):
    global text
    if new.strip() in text and label.startswith('insert:'):
        print(f'skip already present: {label}')
        return
    if old not in text:
        # idempotent if already patched with similar content
        if 'createTvLogoService' in text and label.startswith('require'):
            print(f'skip require already patched')
            return
        raise SystemExit(f'PATCH FAIL: cannot find anchor for {label}')
    text = text.replace(old, new, 1)
    print(f'ok: {label}')

# 1) require tvLogoService + resolveLokkeCountryCode
ensure_once(
    'require tvLogo',
    """const {
  stripChannelVariantSuffixes,
  normalizeChannelVariantMergeKey,
  getChannelVariantQualityRank,
  getCleanChannelDisplayName,
  mergeChannelVariantList: mergeChannelVariantsCore
} = require('./services/channelVariantMerge');
const { createFalconService } = require('./services/falconService');""",
    """const {
  stripChannelVariantSuffixes,
  normalizeChannelVariantMergeKey,
  getChannelVariantQualityRank,
  getCleanChannelDisplayName,
  resolveLokkeCountryCode,
  mergeChannelVariantList: mergeChannelVariantsCore
} = require('./services/channelVariantMerge');
const { createTvLogoService } = require('./services/tvLogoService');
const { createFalconService } = require('./services/falconService');"""
)

# 2) After albService create / near caches — add tvLogoService singleton after DATA_DIR consts is hard;
# insert near express section instead after BASE_PATH is fine — we'll add after albChannelsDb let.
ensure_once(
    'tvLogoService singleton',
    "let albChannelsDb = {};\nlet allCombined     = {};",
    "let albChannelsDb = {};\n"
    "const tvLogoService = createTvLogoService({\n"
    "  logoRoot: path.join(__dirname, 'public', 'tv-logos'),\n"
    "  publicBase: `${BASE_PATH}/tv-logos`\n"
    "});\n"
    "let allCombined     = {};"
)

# 3) Helper: deleted channel match + lokke retag + prod api filter
HELPERS = r'''
function isProdApiBlockedChannelName(name = '') {
  const key = normalizeChannelMergeKey(name || '');
  if (!key) return false;
  if (key.startsWith('metitv') || key.includes('meti tv') || key === 'meti') return true;
  if (key.includes('mahmutovit') || key.includes('rexhepovit')) return true;
  return false;
}
function channelMatchesDeletedKeys(name = '', deletedKeys = []) {
  const key = normalizeChannelMergeKey(name || '');
  if (!key) return false;
  const set = deletedKeys instanceof Set ? deletedKeys : new Set(deletedKeys || []);
  if (set.has(key)) return true;
  for (const dk of set) {
    const needle = String(dk || '').trim();
    if (!needle || needle.length < 4) continue;
    if (key === needle || key.startsWith(`${needle} `) || key.startsWith(needle)) return true;
  }
  return false;
}
function retagKoolLokkeDisplayNames(db = {}) {
  let changed = 0;
  for (const channel of Object.values(db || {})) {
    if (!channel || typeof channel !== 'object') continue;
    if (String(channel.sourceKey || channel.type || '') !== 'kool_lokke') continue;
    const nextName = getCleanChannelDisplayName(channel.name || '', {
      appendLokkeTag: true,
      koolCategory: channel.koolCategory || '',
      category: channel.koolCategory || ''
    });
    if (nextName && nextName !== channel.name) {
      channel.name = nextName;
      changed += 1;
    }
  }
  return changed;
}
'''

if 'function isProdApiBlockedChannelName' not in text:
    ensure_once(
        'insert helpers',
        'function normalizeDeletedChannelKeys(raw = []) {',
        HELPERS + '\nfunction normalizeDeletedChannelKeys(raw = []) {'
    )
else:
    print('skip helpers already present')

# 4) loadProdApiChannelCache filter
ensure_once(
    'loadProdApi filter',
    '''function loadProdApiChannelCache(file) {
  const raw = loadJSON(file, {});
  if (!raw || typeof raw !== 'object') return {};
  return Object.fromEntries(Object.entries(raw).filter(([, ch]) => ch && typeof ch === 'object'));
}''',
    '''function loadProdApiChannelCache(file) {
  const raw = loadJSON(file, {});
  if (!raw || typeof raw !== 'object') return {};
  return Object.fromEntries(Object.entries(raw).filter(([, ch]) => {
    if (!ch || typeof ch !== 'object') return false;
    if (isProdApiBlockedChannelName(ch.name || '')) return false;
    return true;
  }));
}'''
)

# 5) deleted keys filter enhancement in updateDatabases
ensure_once(
    'deleted filter',
    '''  const deletedNameKeys = new Set(normalizeDeletedChannelKeys(appConfig.deleted_channel_keys));
  if (deletedNameKeys.size) {
    allCombined = Object.fromEntries(Object.entries(allCombined).filter(([, ch]) => {
      const key = normalizeChannelMergeKey(ch?.name || '');
      return !key || !deletedNameKeys.has(key);
    }));
  }''',
    '''  const deletedNameKeys = new Set(normalizeDeletedChannelKeys(appConfig.deleted_channel_keys));
  allCombined = Object.fromEntries(Object.entries(allCombined).filter(([, ch]) => {
    if (!ch) return false;
    if (String(ch.sourceKey || '') === 'api_tv' || String(ch.sourceKey || '') === 'api_sport' || ch.source === 'api') {
      if (isProdApiBlockedChannelName(ch.name || '')) return false;
    }
    if (deletedNameKeys.size && channelMatchesDeletedKeys(ch.name || '', deletedNameKeys)) return false;
    return true;
  }));'''
)

# 6) Apply logos + retag lokke after tvepg logos
ensure_once(
    'apply logos',
    '''  try {
    const tvepgReady = await awaitWithTimeout(ensureTvepgAlbaniaCatalog({ force: false }), 4000);
    if (tvepgReady) applyTvepgLogoMatchesToMap(allCombined);
  } catch {}''',
    '''  try {
    const tvepgReady = await awaitWithTimeout(ensureTvepgAlbaniaCatalog({ force: false }), 4000);
    if (tvepgReady) applyTvepgLogoMatchesToMap(allCombined);
  } catch {}
  try {
    retagKoolLokkeDisplayNames(koolLokkeChannelsDb);
    retagKoolLokkeDisplayNames(allCombined);
    const logoChanges = tvLogoService.applyLogosToMap(allCombined, { force: false });
    if (logoChanges > 0) console.log(`TV logos applied: ${logoChanges}`);
  } catch (logoErr) {
    console.warn('TV logo apply failed:', logoErr.message);
  }'''
)

# 7) Static logos route near express router mount
if "router.use('/tv-logos'" not in text:
    ensure_once(
        'static logos',
        'app.use(BASE_PATH, router);',
        "router.use('/tv-logos', express.static(path.join(__dirname, 'public', 'tv-logos'), {\n"
        "  maxAge: '30d',\n"
        "  immutable: true,\n"
        "  fallthrough: true\n"
        "}));\n"
        "app.use(BASE_PATH, router);"
    )
else:
    print('skip static logos')

# 8) Also filter during prod API fetch loop — after name extraction
if 'isProdApiBlockedChannelName(name)' not in text:
    ensure_once(
        'fetch filter',
        '''        rawItems.push({
          name,
          url: streamUrl,
          type: label,
          source: 'api',
          sourceKey: label === 'sport' ? 'api_sport' : 'api_tv',
          category_name: String(post.category_name || label || '').trim(),
          user_agent: userAgent,
          apiUserAgent: userAgent,
          logo: String(post.channel_image || '').trim(),
          description: String(post.channel_description || '').trim()
        });''',
        '''        if (isProdApiBlockedChannelName(name)) continue;
        rawItems.push({
          name,
          url: streamUrl,
          type: label,
          source: 'api',
          sourceKey: label === 'sport' ? 'api_sport' : 'api_tv',
          category_name: String(post.category_name || label || '').trim(),
          user_agent: userAgent,
          apiUserAgent: userAgent,
          logo: String(post.channel_image || '').trim(),
          description: String(post.channel_description || '').trim()
        });'''
    )
else:
    print('skip fetch filter')

if text == original:
    print('NO CHANGES')
else:
    SERVER.write_text(text, encoding='utf-8')
    print(f'Wrote {SERVER} (backup {backup})')
