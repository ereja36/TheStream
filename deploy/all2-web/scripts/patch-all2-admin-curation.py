#!/usr/bin/env python3
"""
Respect admin curation on /k1 (all2):

- Do NOT auto-invent Triangolo category rows (Kombetare/Sport/Filma/…) when
  admin has hidden those categories.
- Do NOT auto-fill Kombetare with all Albania Lokke channels.
- Play gate = featured + showOnMain categories only (admin source of truth).
- Hide empty showOnMain category cards.
- Deduplicate near-identical featured channels on main (e.g. SUPER SPORT 2).

Safe to re-run (marker: admin-curation-4157).
"""
from __future__ import annotations

import re
import shutil
import sys
from datetime import datetime, timezone
from pathlib import Path

SERVER = Path(sys.argv[1] if len(sys.argv) > 1 else "/root/all/all2/server.js")
MARKER = "/* patched:admin-curation-4157 */"


def backup(path: Path) -> Path:
    stamp = datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S")
    dest = path.with_suffix(path.suffix + f".bak-admin-curation-{stamp}")
    shutil.copy2(path, dest)
    return dest


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new.strip()[:80] in text and MARKER.split(":")[1].split(" ")[0] in text and label in text:
        # soft skip when already applied — caller also checks markers
        pass
    if old not in text:
        raise SystemExit(f"block not found: {label}")
    return text.replace(old, new, 1)


def main() -> None:
    if not SERVER.is_file():
        raise SystemExit(f"missing {SERVER}")
    text = SERVER.read_text(encoding="utf-8")
    original = text

    # --- 1) Remove Kombetare Albania auto-fill ---
    if "patched:admin-curation-4157-kombetare" in text:
        print("kombetare fill already removed")
    else:
        m = re.search(
            r"function getCustomCategoryChannels\(id\) \{[\s\S]*?\n\}\n(?:/\* patched:all2-portal-bugs-4157-kombetare-fill \*/\n)?",
            text,
        )
        if not m:
            raise SystemExit("getCustomCategoryChannels not found")
        new_fn = """function getCustomCategoryChannels(id) {
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
}
""" + MARKER.replace("*/", "-kombetare */") + "\n"
        text = text[: m.start()] + new_fn + text[m.end() :]
        print("restored curated-only getCustomCategoryChannels")

    # --- 2) Triangolo: only admin showOnMain rows; no auto buckets; featured-only fallback ---
    if "patched:admin-curation-4157-triangolo" in text:
        print("triangolo already admin-only")
    else:
        m = re.search(
            r"function buildTriangoloCategoryRowsHtml\(req = null\) \{[\s\S]*?\n  return rows\.join\('\\n'\);\n\}",
            text,
        )
        if not m:
            raise SystemExit("buildTriangoloCategoryRowsHtml not found")
        new_tri = """function buildTriangoloCategoryRowsHtml(req = null) {
  const adSettings = normalizeAdSnippetConfig(appConfig.ad_snippets);
  const enableAds = adSettings.enabled === true && adSettings.grid_native_enabled === true;
  const visible = filterChannelsForDevice(visibleChannels(allCombined), req);
  const rows = [];
  let adIndex = 0;
  const used = new Set();

  const pushRow = (id, title, channelEntries, viewAllHref, kicker = 'TV CHANNELS') => {
    const limited = channelEntries.filter(([slug]) => !!visible[slug] && !used.has(slug)).slice(0, 18);
    if (!limited.length) return;
    limited.forEach(([slug]) => used.add(slug));
    const cards = buildCards(Object.fromEntries(limited), {
      pageType: 'triangolo_row',
      categoryKeyOverride: `custom:${createSlug(id)}`,
      redirectKoolOnMain: false
    });
    rows.push(`<section class="tri-cat-row" data-cat="${escapeHtmlAttr(id)}">
  <div class="tri-cat-head">
    <div>
      <div class="tri-cat-kicker">${escapeHtml(kicker)}</div>
      <h2 class="tri-cat-title">${escapeHtml(title)}</h2>
    </div>
    <a class="tri-view-all" href="${viewAllHref}">View all</a>
  </div>
  <div class="tri-cat-scroller channels-grid tri-h-scroll">${cards}</div>
  ${enableAds ? buildNativeAdSlotHtml(adSettings, adIndex++) : ''}
</section>`);
  };

  // Admin is the only source of truth — never invent Kombetare/Sport/Filma rows in code.
  const entries = Object.entries(customCategories || {})
    .filter(([, cat]) => cat?.showOnMain !== false)
    .sort((a, b) => (Number(a[1].order || 0) - Number(b[1].order || 0)) || String(a[1].name || '').localeCompare(String(b[1].name || '')));
  for (const [id, cat] of entries) {
    const channels = getCustomCategoryChannels(id);
    const channelEntries = Object.entries(channels);
    if (!channelEntries.length) continue; // skip empty showOnMain categories (e.g. Sport with 0)
    pushRow(id, cat.name || id, channelEntries, `${BASE_PATH}/category/custom/${encodeURIComponent(id)}`);
  }

  // If admin curated no visible category rows, show featured channels only (never the full catalog).
  if (!rows.length) {
    const featuredSlugs = dedupeMainChannelSlugs(
      Array.isArray(appConfig.featured_channels) ? appConfig.featured_channels.filter(slug => !!visible[slug]) : [],
      visible
    );
    if (featuredSlugs.length) {
      pushRow('live', 'Live', featuredSlugs.map(s => [s, visible[s]]), `${BASE_PATH}/`);
    }
  }
  return rows.join('\\n');
}
""" + MARKER.replace("*/", "-triangolo */") + "\n"
        text = text[: m.start()] + new_tri + text[m.end() :]
        print("triangolo rows = admin categories only")

    # --- 3) Strict play gate (admin curation only) ---
    if "patched:admin-curation-4157-gate" in text:
        print("play gate already strict")
    else:
        m = re.search(
            r"function getMainPortalChannelSlugSet\(\) \{[\s\S]*?\nfunction assertMainPortalWebAccess\(slug\) \{[\s\S]*?\n\}\n(?:/\* patched:all2-portal-bugs-4157 \*/\n)?",
            text,
        )
        if not m:
            raise SystemExit("main portal gate block not found")
        new_gate = """function getMainPortalChannelSlugSet() {
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
  // Empty allow = admin has not curated main at all → previous public behavior.
  if (!allow.size) return true;
  return allow.has(safe);
}
function assertMainPortalWebAccess(slug) {
  if (isChannelOnMainPortal(slug)) return { ok: true };
  return { ok: false, reason: 'not_on_main_portal' };
}
""" + MARKER.replace("*/", "-gate */") + "\n"
        text = text[: m.start()] + new_gate + text[m.end() :]
        print("restored strict admin play gate")

    # --- 4) Hide empty category cards on classic main ---
    if "patched:admin-curation-4157-catcards" in text:
        print("category cards already filtered")
    else:
        old_cards = """function buildCustomCategoryCardsHtml() {
  const visibleCustomCategoryEntries = Object.entries(customCategories)
    .filter(([, cat]) => cat?.showOnMain !== false)
    .sort((a, b) => (Number(a[1].order || 0) - Number(b[1].order || 0)) || String(a[1].name || '').localeCompare(String(b[1].name || '')));
  return visibleCustomCategoryEntries.map(([id, cat]) => {
    const count = Object.keys(getCustomCategoryChannels(id)).length;
    const iconHtml = renderCategoryIconHtml(cat.logo, 'fa-folder-open');
    return `<a href="${BASE_PATH}/category/custom/${id}" class="cat-card" onclick="return openCategoryWithSmartLink(event,'custom:${id}')">
${iconHtml}
<div class="cat-info"><h3>${escapeHtml(cat.name || id)}</h3><p>Kategori e personalizuar</p></div>
<div class="cat-count">${count}</div></a>`;
  }).join('');
}"""
        new_cards = """function buildCustomCategoryCardsHtml() {
  const visibleCustomCategoryEntries = Object.entries(customCategories)
    .filter(([, cat]) => cat?.showOnMain !== false)
    .sort((a, b) => (Number(a[1].order || 0) - Number(b[1].order || 0)) || String(a[1].name || '').localeCompare(String(b[1].name || '')));
  return visibleCustomCategoryEntries.map(([id, cat]) => {
    const count = Object.keys(getCustomCategoryChannels(id)).length;
    if (count <= 0) return ''; // do not show empty categories on main
    const iconHtml = renderCategoryIconHtml(cat.logo, 'fa-folder-open');
    return `<a href="${BASE_PATH}/category/custom/${id}" class="cat-card" onclick="return openCategoryWithSmartLink(event,'custom:${id}')">
${iconHtml}
<div class="cat-info"><h3>${escapeHtml(cat.name || id)}</h3><p>Kategori e personalizuar</p></div>
<div class="cat-count">${count}</div></a>`;
  }).filter(Boolean).join('');
}
""" + MARKER.replace("*/", "-catcards */") + "\n"
        if old_cards not in text:
            raise SystemExit("buildCustomCategoryCardsHtml not found")
        text = text.replace(old_cards, new_cards, 1)
        print("empty category cards hidden on main")

    # --- 5) Dedupe helper + use on classic main featured list ---
    if "function dedupeMainChannelSlugs(" in text:
        print("dedupe helper already present")
    else:
        anchor = "function filterChannelsForDevice(sourceMap, req) {"
        if anchor not in text:
            raise SystemExit("filterChannelsForDevice anchor missing")
        helper = """function normalizeChannelDedupeKey(name = '') {
  return String(name || '')
    .toLowerCase()
    .replace(/\\[.*?\\]/g, ' ')
    .replace(/[^a-z0-9]+/g, '')
    .trim();
}
function dedupeMainChannelSlugs(slugs = [], channelMap = {}) {
  const out = [];
  const seenSlug = new Set();
  const seenName = new Set();
  for (const raw of slugs || []) {
    const slug = createSlug(raw) || String(raw || '').trim();
    if (!slug || seenSlug.has(slug)) continue;
    const ch = channelMap[slug];
    if (!ch) continue;
    const nameKey = normalizeChannelDedupeKey(ch.name || slug);
    if (nameKey && seenName.has(nameKey)) continue;
    seenSlug.add(slug);
    if (nameKey) seenName.add(nameKey);
    out.push(slug);
  }
  return out;
}
""" + MARKER.replace("*/", "-dedupe */") + "\n"
        text = text.replace(anchor, helper + anchor, 1)
        print("added featured dedupe helpers")

    # Classic main: dedupe featured entries
    old_main = """  const featuredSlugs = Array.isArray(appConfig.featured_channels)
    ? appConfig.featured_channels.filter(slug => !!vis[slug])
    : [];
  const manualMainEntries = featuredSlugs.map(slug => [slug, vis[slug]]).filter(([, ch]) => !!ch);
  const limited = (manualMainEntries.length ? manualMainEntries : Object.entries(vis)).slice(0, appConfig.main_page_limit);"""
    new_main = """  const featuredSlugs = dedupeMainChannelSlugs(
    Array.isArray(appConfig.featured_channels) ? appConfig.featured_channels.filter(slug => !!vis[slug]) : [],
    vis
  );
  const manualMainEntries = featuredSlugs.map(slug => [slug, vis[slug]]).filter(([, ch]) => !!ch);
  // Never fall back to the full catalog — admin must curate featured / showOnMain categories.
  const limited = manualMainEntries.slice(0, appConfig.main_page_limit);
""" + "  " + MARKER.replace("*/", "-main-featured */") + "\n"
    if "patched:admin-curation-4157-main-featured" in text:
        print("main featured already curated-only")
    elif old_main not in text:
        # may appear twice (main + watch page) — replace all
        count = text.count(
            """  const featuredSlugs = Array.isArray(appConfig.featured_channels)
    ? appConfig.featured_channels.filter(slug => !!vis[slug])
    : [];
  const manualMainEntries = featuredSlugs.map(slug => [slug, vis[slug]]).filter(([, ch]) => !!ch);
  const limited = (manualMainEntries.length ? manualMainEntries : Object.entries(vis)).slice(0, appConfig.main_page_limit);"""
        )
        if count < 1:
            # alternate filter var name on watch page uses `item`
            alt_old = """  const featuredSlugs = Array.isArray(appConfig.featured_channels)
    ? appConfig.featured_channels.filter(item => !!vis[item])
    : [];
  const manualMainEntries = featuredSlugs.map(item => [item, vis[item]]).filter(([, ch]) => !!ch);
  const limited = (manualMainEntries.length ? manualMainEntries : Object.entries(vis)).slice(0, appConfig.main_page_limit);"""
            alt_new = """  const featuredSlugs = dedupeMainChannelSlugs(
    Array.isArray(appConfig.featured_channels) ? appConfig.featured_channels.filter(item => !!vis[item]) : [],
    vis
  );
  const manualMainEntries = featuredSlugs.map(item => [item, vis[item]]).filter(([, ch]) => !!ch);
  const limited = manualMainEntries.slice(0, appConfig.main_page_limit);
"""
            if alt_old in text or old_main in text:
                text = text.replace(old_main, new_main)
                text = text.replace(alt_old, alt_new)
                print(f"patched main featured curation")
            else:
                print("WARN: main featured block not found exact")
        else:
            text = text.replace(old_main, new_main)
            # watch page variant
            alt_old = """  const featuredSlugs = Array.isArray(appConfig.featured_channels)
    ? appConfig.featured_channels.filter(item => !!vis[item])
    : [];
  const manualMainEntries = featuredSlugs.map(item => [item, vis[item]]).filter(([, ch]) => !!ch);
  const limited = (manualMainEntries.length ? manualMainEntries : Object.entries(vis)).slice(0, appConfig.main_page_limit);"""
            alt_new = """  const featuredSlugs = dedupeMainChannelSlugs(
    Array.isArray(appConfig.featured_channels) ? appConfig.featured_channels.filter(item => !!vis[item]) : [],
    vis
  );
  const manualMainEntries = featuredSlugs.map(item => [item, vis[item]]).filter(([, ch]) => !!ch);
  const limited = manualMainEntries.slice(0, appConfig.main_page_limit);
"""
            if alt_old in text:
                text = text.replace(alt_old, alt_new)
            print("patched main featured curation")
    else:
        text = text.replace(old_main, new_main)
        alt_old = """  const featuredSlugs = Array.isArray(appConfig.featured_channels)
    ? appConfig.featured_channels.filter(item => !!vis[item])
    : [];
  const manualMainEntries = featuredSlugs.map(item => [item, vis[item]]).filter(([, ch]) => !!ch);
  const limited = (manualMainEntries.length ? manualMainEntries : Object.entries(vis)).slice(0, appConfig.main_page_limit);"""
        alt_new = """  const featuredSlugs = dedupeMainChannelSlugs(
    Array.isArray(appConfig.featured_channels) ? appConfig.featured_channels.filter(item => !!vis[item]) : [],
    vis
  );
  const manualMainEntries = featuredSlugs.map(item => [item, vis[item]]).filter(([, ch]) => !!ch);
  const limited = manualMainEntries.slice(0, appConfig.main_page_limit);
"""
        if alt_old in text:
            text = text.replace(alt_old, alt_new)
        print("patched main featured curation")

    if text == original:
        print("server.js unchanged")
        return

    bak = backup(SERVER)
    SERVER.write_text(text, encoding="utf-8")
    print(f"wrote {SERVER} (backup {bak})")


if __name__ == "__main__":
    main()
