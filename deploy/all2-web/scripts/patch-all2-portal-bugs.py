#!/usr/bin/env python3
"""
Fix ShifShqip /k1 (all2) portal bugs:
1) Channels shown on Triangolo homepage fail with not_on_main_portal / "Nuk ka burim"
2) Content categories (Kombetare, Lajme, …) hidden via showOnMain=false
3) Premium/noads users still see empty "Sponsored" native ad chrome
4) Client drops API error codes on non-OK channel-url responses

Safe to re-run (idempotent markers).
"""
from __future__ import annotations

import json
import shutil
import sys
from datetime import datetime, timezone
from pathlib import Path

SERVER = Path(sys.argv[1] if len(sys.argv) > 1 else "/root/all/all2/server.js")
CATEGORIES = Path(sys.argv[2] if len(sys.argv) > 2 else "/root/all/all2/data/custom_categories.json")

MARKER = "/* patched:all2-portal-bugs-4157 */"

OLD_PORTAL = """function getMainPortalChannelSlugSet() {
  const set = new Set();
  const featured = Array.isArray(appConfig.featured_channels) ? appConfig.featured_channels : [];
  for (const slug of featured) {
    const safe = createSlug(slug) || String(slug || '').trim();
    if (safe) set.add(safe);
  }
  for (const [, cat] of Object.entries(customCategories || {})) {
    if (!cat || cat.showOnMain === false) continue;
    const channels = Array.isArray(cat.channels) ? cat.channels : [];
    for (const slug of channels) {
      const safe = createSlug(slug) || String(slug || '').trim();
      if (safe) set.add(safe);
    }
  }
  return set;
}
function isChannelOnMainPortal(slug) {
  const safe = createSlug(slug) || String(slug || '').trim();
  if (!safe) return false;
  if (appConfig.visible_channels?.[safe] === false) return false;
  const allow = getMainPortalChannelSlugSet();
  // If admin has not curated main portal at all, keep previous public behavior.
  if (!allow.size) return true;
  return allow.has(safe);
}
function assertMainPortalWebAccess(slug) {
  if (isChannelOnMainPortal(slug)) return { ok: true };
  return { ok: false, reason: 'not_on_main_portal' };
}"""

NEW_PORTAL = """function getMainPortalChannelSlugSet() {
  const set = new Set();
  const featured = Array.isArray(appConfig.featured_channels) ? appConfig.featured_channels : [];
  for (const slug of featured) {
    const safe = createSlug(slug) || String(slug || '').trim();
    if (safe) set.add(safe);
  }
  for (const [, cat] of Object.entries(customCategories || {})) {
    if (!cat || cat.showOnMain === false) continue;
    const channels = Array.isArray(cat.channels) ? cat.channels : [];
    for (const slug of channels) {
      const safe = createSlug(slug) || String(slug || '').trim();
      if (safe) set.add(safe);
    }
  }
  return set;
}
function isTriangoloPublicCatalogChannel(channel = {}) {
  if (!channel || typeof channel !== 'object') return false;
  const src = getChannelSourceKey(channel);
  if (!isSourceAccessibleNow(src)) return false;
  const isAlbLokke = src === 'kool_lokke' && /albania/i.test(String(channel.koolCategory || channel.description || channel.name || ''));
  const isApi = src === 'api' || src === 'api_tv' || src === 'api_sport' || channel.source === 'api';
  return !!(isAlbLokke || isApi || src === 'custom_tv');
}
function isSlugInAnyCustomCategory(slug) {
  const safe = createSlug(slug) || String(slug || '').trim();
  if (!safe) return false;
  for (const cat of Object.values(customCategories || {})) {
    if (!cat || !Array.isArray(cat.channels)) continue;
    if (cat.channels.includes(safe)) return true;
  }
  return false;
}
function isChannelOnMainPortal(slug) {
  const safe = createSlug(slug) || String(slug || '').trim();
  if (!safe) return false;
  if (appConfig.visible_channels?.[safe] === false) return false;
  const allow = getMainPortalChannelSlugSet();
  // If admin has not curated main portal at all, keep previous public behavior.
  if (!allow.size) return true;
  if (allow.has(safe)) return true;
  // Keep playable anything curated into a custom category (incl. temporarily hidden).
  if (isSlugInAnyCustomCategory(safe)) return true;
  // Triangolo auto-rows show Albania/API channels even when custom categories
  // have showOnMain=false — keep playback aligned with what users can click.
  const channel = (typeof resolveChannelBySlug === 'function' ? resolveChannelBySlug(safe) : null)
    || (allCombined && allCombined[safe])
    || null;
  if (channel && isTriangoloPublicCatalogChannel(channel)) return true;
  return false;
}
function assertMainPortalWebAccess(slug) {
  if (isChannelOnMainPortal(slug)) return { ok: true };
  return { ok: false, reason: 'not_on_main_portal' };
}
""" + MARKER + "\n"

OLD_AUTO = """  // Auto rows when custom categories are empty / sparse (Triangolo homepage like AlbKanale)
  if (rows.length < 3) {
    const buckets = {
      kombetare: [],
      sport: [],
      filma: [],
      lajme: [],
      femije: [],
      muzike: [],
      lokale: []
    };
    const labels = {
      kombetare: 'Kombetare',
      sport: 'Sport',
      filma: 'Filma',
      lajme: 'Lajme',
      femije: 'Familje',
      muzike: 'Muzike',
      lokale: 'Lokale'
    };
    const order = ['kombetare', 'sport', 'filma', 'lajme', 'femije', 'muzike', 'lokale'];
    for (const [slug, ch] of Object.entries(visible)) {
      if (used.has(slug)) continue;
      const src = getChannelSourceKey(ch);
      const isAlbLokke = src === 'kool_lokke' && /albania/i.test(String(ch.koolCategory || ch.description || ch.name || ''));
      const isApi = src === 'api' || src === 'api_tv' || src === 'api_sport' || ch.source === 'api';
      if (!isAlbLokke && !isApi && src !== 'custom_tv') continue;
      let key = matchTriangoloAutoGroup(ch.name || slug, src);
      if (!key) {
        if (src === 'api_sport' || ch.type === 'sport') key = 'sport';
        else if (isAlbLokke || isApi) key = 'lokale';
        else continue;
      }
      if (!buckets[key]) key = 'lokale';
      buckets[key].push([slug, ch]);
    }
    for (const key of order) {
      const list = buckets[key] || [];
      if (list.length < 2) continue;
      const viewAll = customCategories[key]
        ? `${BASE_PATH}/category/custom/${encodeURIComponent(key)}`
        : `${BASE_PATH}/`;
      pushRow(`auto-${key}`, labels[key] || key, list, viewAll);
      if (rows.length >= 8) break;
    }
  }"""

NEW_AUTO = """  // Always fill leftover Triangolo public channels into auto rows (not only when sparse).
  {
    const buckets = {
      kombetare: [],
      sport: [],
      filma: [],
      lajme: [],
      femije: [],
      muzike: [],
      lokale: []
    };
    const labels = {
      kombetare: 'Kombetare',
      sport: 'Sport',
      filma: 'Filma',
      lajme: 'Lajme',
      femije: 'Familje',
      muzike: 'Muzike',
      lokale: 'Lokale'
    };
    const order = ['kombetare', 'sport', 'filma', 'lajme', 'femije', 'muzike', 'lokale'];
    for (const [slug, ch] of Object.entries(visible)) {
      if (used.has(slug)) continue;
      const src = getChannelSourceKey(ch);
      const isAlbLokke = src === 'kool_lokke' && /albania/i.test(String(ch.koolCategory || ch.description || ch.name || ''));
      const isApi = src === 'api' || src === 'api_tv' || src === 'api_sport' || ch.source === 'api';
      if (!isAlbLokke && !isApi && src !== 'custom_tv') continue;
      let key = matchTriangoloAutoGroup(ch.name || slug, src);
      if (!key) {
        if (src === 'api_sport' || ch.type === 'sport') key = 'sport';
        else if (isAlbLokke || isApi) key = 'lokale';
        else continue;
      }
      if (!buckets[key]) key = 'lokale';
      buckets[key].push([slug, ch]);
    }
    for (const key of order) {
      const list = buckets[key] || [];
      if (list.length < 2) continue;
      const viewAll = customCategories[key]
        ? `${BASE_PATH}/category/custom/${encodeURIComponent(key)}`
        : `${BASE_PATH}/`;
      const customShown = rows.some((html) => typeof html === 'string' && html.includes(`data-cat="${key}"`));
      const title = customShown ? (`Me shume · ${labels[key] || key}`) : (labels[key] || key);
      pushRow(`auto-${key}`, title, list, viewAll);
      if (rows.length >= 10) break;
    }
  }
""" + "  " + MARKER.replace("*/", "-triangolo */") + "\n"

OLD_CUSTOM_CAT_CH = """function getCustomCategoryChannels(id) {
  const cat = customCategories[id];
  if (!cat) return {};
  const map = {};
  for (const slug of cat.channels || []) {
    const channel = allCombined[slug];
    if (!channel) continue;
    if (!isSourceAccessibleNow(getChannelSourceKey(channel))) continue;
    map[slug] = channel;
  }
  return map;
}"""

NEW_CUSTOM_CAT_CH = """function getCustomCategoryChannels(id) {
  const cat = customCategories[id];
  if (!cat) return {};
  const map = {};
  for (const slug of cat.channels || []) {
    const channel = allCombined[slug];
    if (!channel) continue;
    if (!isSourceAccessibleNow(getChannelSourceKey(channel))) continue;
    map[slug] = channel;
  }
  // Kombetare is curated partially; always include visible Albania Lokke channels
  // so public category pages match what users expect (e.g. Zjarr TV).
  if (String(id || '').trim() === 'kombetare') {
    for (const [slug, channel] of Object.entries(allCombined || {})) {
      if (!channel || map[slug]) continue;
      if (appConfig.visible_channels?.[slug] === false) continue;
      const src = getChannelSourceKey(channel);
      if (src !== 'kool_lokke') continue;
      if (!/albania/i.test(String(channel.koolCategory || channel.description || channel.name || ''))) continue;
      if (!isSourceAccessibleNow(src)) continue;
      map[slug] = channel;
    }
  }
  return map;
}
""" + MARKER.replace("*/", "-kombetare-fill */") + "\n"

OLD_NOADS = """  if(isNoAdsUser()){
    nodes.forEach(node=>{
      if(!node) return;
      node.innerHTML='';
      node.style.display='none';
    });
    return;
  }"""

NEW_NOADS = """  if(isNoAdsUser()){
    nodes.forEach(node=>{
      if(!node) return;
      node.innerHTML='';
      node.style.display='none';
      const card=node.closest('.native-ad-card, .category-ad-slot, .ad-banner-row, .ad-banner-slot');
      if(card){
        card.style.display='none';
        card.setAttribute('data-ad-empty','1');
      }
    });
    document.querySelectorAll('.native-ad-card, .category-ad-slot').forEach(card=>{
      card.style.display='none';
      card.setAttribute('data-ad-empty','1');
    });
    return;
  }
  """ + MARKER.replace("*/", "-noads */") + "\n"

OLD_PLAYABLE_TAIL = """    }
  }catch{}
  return { url:'', proxyUrl:'', mode:'direct', error:'unavailable' };
}
function isA3MpegtsPlayUrl(url, playable){"""

NEW_PLAYABLE_TAIL = """    }
    // Surface API errors (not_on_main_portal, geo, access) instead of generic empty URL.
    try{
      const data=await r.json();
      return {
        url:'',
        proxyUrl:'',
        mode:'direct',
        error:String((data&&data.error)||('http_'+r.status)),
        requiredTier:String((data&&data.required_tier)||'')
      };
    }catch{
      return { url:'', proxyUrl:'', mode:'direct', error:'http_'+r.status };
    }
  }catch{}
  return { url:'', proxyUrl:'', mode:'direct', error:'unavailable' };
}
""" + MARKER.replace("*/", "-playable-url */") + """
function isA3MpegtsPlayUrl(url, playable){"""

OLD_STATUS_ERR = """      }else if(accessErr==='a3_fastly_web'){
        setStatus('warn',formatA3PlaybackError(accessErr));
      }else{
        setStatus('err','Nuk ka burim për këtë kanal');
      }"""

NEW_STATUS_ERR = """      }else if(accessErr==='a3_fastly_web'){
        setStatus('warn',formatA3PlaybackError(accessErr));
      }else if(accessErr==='not_on_main_portal'){
        setStatus('err','Ky kanal nuk është i disponueshëm në portal tani');
      }else if(accessErr==='google_login_required'){
        setStatus('warn','Hyr me Gmail për të shikuar këtë kanal');
      }else if(accessErr){
        setStatus('err','Nuk u hap kanali ('+accessErr+')');
      }else{
        setStatus('err','Nuk ka burim për këtë kanal');
      }
      """ + MARKER.replace("*/", "-status-err */") + """
"""

SHOW_ON_MAIN_IDS = ("kombetare", "lajme", "filma", "femije", "muzike")


def backup(path: Path) -> Path:
    stamp = datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S")
    dest = path.with_suffix(path.suffix + f".bak-portal-bugs-{stamp}")
    shutil.copy2(path, dest)
    return dest


def patch_server(text: str) -> str:
    if MARKER in text and "isTriangoloPublicCatalogChannel" in text:
        print("server.js already patched (portal gate)")
    else:
        if OLD_PORTAL not in text:
            raise SystemExit("portal gate block not found — abort")
        text = text.replace(OLD_PORTAL, NEW_PORTAL, 1)
        print("patched portal gate")

    if "Always fill leftover Triangolo public channels" in text:
        print("triangolo auto rows already patched")
    else:
        if OLD_AUTO not in text:
            raise SystemExit("triangolo auto block not found — abort")
        text = text.replace(OLD_AUTO, NEW_AUTO, 1)
        print("patched triangolo auto rows")

    if "patched:all2-portal-bugs-4157-kombetare-fill" in text:
        print("kombetare fill already patched")
    elif OLD_CUSTOM_CAT_CH in text:
        text = text.replace(OLD_CUSTOM_CAT_CH, NEW_CUSTOM_CAT_CH, 1)
        print("patched kombetare Albania fill")
    else:
        print("WARN: getCustomCategoryChannels block not found")

    if "patched:all2-portal-bugs-4157-noads" in text or (
        "document.querySelectorAll('.native-ad-card, .category-ad-slot')" in text
        and "isNoAdsUser()" in text
    ):
        # still try exact replace if old block present
        pass
    if OLD_NOADS in text:
        text = text.replace(OLD_NOADS, NEW_NOADS, 1)
        print("patched noads native chrome hide")
    elif "patched:all2-portal-bugs-4157-noads" in text:
        print("noads already patched")
    else:
        print("WARN: noads block not found")

    if "Surface API errors (not_on_main_portal" in text:
        print("getPlayableUrl already patched")
    else:
        if OLD_PLAYABLE_TAIL not in text:
            raise SystemExit("getPlayableUrl tail not found — abort")
        text = text.replace(OLD_PLAYABLE_TAIL, NEW_PLAYABLE_TAIL, 1)
        print("patched getPlayableUrl error surfacing")

    if "accessErr==='not_on_main_portal'" in text:
        print("status errors already patched")
    else:
        count = text.count(OLD_STATUS_ERR)
        if count < 1:
            raise SystemExit("status error block not found — abort")
        text = text.replace(OLD_STATUS_ERR, NEW_STATUS_ERR)
        print(f"patched status errors ({count} sites)")

    return text


def patch_categories(path: Path) -> None:
    data = json.loads(path.read_text(encoding="utf-8"))
    changed = []
    for cid in SHOW_ON_MAIN_IDS:
        cat = data.get(cid)
        if not isinstance(cat, dict):
            continue
        if cat.get("showOnMain") is False:
            cat["showOnMain"] = True
            changed.append(cid)
    if changed:
        backup(path)
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print("enabled showOnMain for:", ", ".join(changed))
    else:
        print("categories showOnMain already ok for", ", ".join(SHOW_ON_MAIN_IDS))


def main() -> None:
    if not SERVER.is_file():
        raise SystemExit(f"missing {SERVER}")
    raw = SERVER.read_text(encoding="utf-8")
    patched = patch_server(raw)
    if patched != raw:
        bak = backup(SERVER)
        SERVER.write_text(patched, encoding="utf-8")
        print(f"wrote {SERVER} (backup {bak})")
    else:
        print("server.js unchanged")
    if CATEGORIES.is_file():
        patch_categories(CATEGORIES)
    else:
        print(f"WARN: missing {CATEGORIES}")


if __name__ == "__main__":
    main()
