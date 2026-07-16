#!/usr/bin/env python3
"""Improve Triangolo homepage: auto category rows, design polish, Telegram CTA."""
from pathlib import Path
import re
import subprocess
import json

SERVER = Path("/root/all/all2/server.js")
MAIN_EJS = Path("/root/all/all2/views/public/mainPage.ejs")
CATS = Path("/root/all/all2/data/custom_categories.json")

NEW_BUILDER = r'''function buildNativeAdSlotHtml(adSettings, index = 0) {
  const nativeSnippets = [String(adSettings.banner_html || '').trim(), ...(Array.isArray(adSettings.banner_html_list) ? adSettings.banner_html_list : [])]
    .map(v => String(v || '').trim())
    .filter(Boolean);
  if (!nativeSnippets.length) return '';
  const pick = nativeSnippets[index % nativeSnippets.length];
  const encoded = Buffer.from(String(pick || ''), 'utf8').toString('base64');
  return `<div class="category-ad-slot native-ad-card" data-native-ad="1"><div class="native-ad-head"><i class="fas fa-bullhorn"></i> Sponsored</div><div class="native-ad-body" data-ad-snippet="${encoded}"></div></div>`;
}
function matchTriangoloAutoGroup(name = '', sourceKey = '') {
  const n = String(name || '').toLowerCase();
  const src = String(sourceKey || '').toLowerCase();
  if (src === 'api_sport' || (src === 'api' && /sport/.test(n)) || /\b(sport|dazn|bein|arena|eurosport|super sport|art sport)\b/.test(n)) return 'sport';
  if (/\b(film|movie|cinema|hbo|netflix|cinemax|axn)\b/.test(n)) return 'filma';
  if (/\b(kids|femij|cartoon|disney|nick|moonbug|zoomoo|baby|junior)\b/.test(n)) return 'femije';
  if (/\b(music|muzik|mtv|kiss|radio|deluxe)\b/.test(n)) return 'muzike';
  if (/\b(news|lajm|cnn|bbc|al jazeera|france 24|sky news)\b/.test(n)) return 'lajme';
  if (/\b(top channel|top news|abc|syri|a2|euronews|rtsh|klan|vizion|report tv|news 24|alsat|oranews|tv 21|dukagjini|rtk|koha)\b/.test(n)) return 'kombetare';
  return '';
}
function buildTriangoloCategoryRowsHtml(req = null) {
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

  const entries = Object.entries(customCategories || {})
    .filter(([, cat]) => cat?.showOnMain !== false)
    .sort((a, b) => (Number(a[1].order || 0) - Number(b[1].order || 0)) || String(a[1].name || '').localeCompare(String(b[1].name || '')));
  for (const [id, cat] of entries) {
    const channels = getCustomCategoryChannels(id);
    const channelEntries = Object.entries(channels);
    pushRow(id, cat.name || id, channelEntries, `${BASE_PATH}/category/custom/${encodeURIComponent(id)}`);
  }

  // Auto rows when custom categories are empty / sparse (Triangolo homepage like AlbKanale)
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
  }

  if (!rows.length) {
    const featuredSlugs = Array.isArray(appConfig.featured_channels) ? appConfig.featured_channels.filter(slug => !!visible[slug]) : [];
    const limited = (featuredSlugs.length ? featuredSlugs.map(s => [s, visible[s]]) : Object.entries(visible)).slice(0, Math.max(12, Number(appConfig.main_page_limit) || 20));
    pushRow('live', 'Live', limited.filter(([, ch]) => !!ch), `${BASE_PATH}/`);
  }
  return rows.join('\n');
}
'''


def replace_builder(t: str) -> str:
    start = t.find("function buildNativeAdSlotHtml(")
    if start < 0:
        raise SystemExit("FAIL: buildNativeAdSlotHtml not found")
    end = t.find("\nfunction buildCustomCategoryCardsHtml", start)
    if end < 0:
        raise SystemExit("FAIL: buildCustomCategoryCardsHtml not found")
    return t[:start] + NEW_BUILDER + t[end:]


def polish_css(t: str) -> str:
    # Stronger brand title + LIVE badge already present; tweak hero + radio button
    old = "body.theme-triangolo .main-hero-pane h1{"
    if "tri-brand-mark" in t:
        return t
    # Insert brand mark styles after hero-chip rule
    needle = "body.theme-triangolo .hero-chip .server-dot{background:#e53935;box-shadow:0 0 0 4px rgba(229,57,53,.18)}"
    extra = needle + """
body.theme-triangolo .tri-brand-mark{display:inline-flex;align-items:center;gap:10px;margin:0 0 10px}
body.theme-triangolo .tri-brand-mark .mark{
  width:42px;height:42px;border-radius:12px;background:linear-gradient(145deg,#e53935,#b71c1c);
  display:inline-flex;align-items:center;justify-content:center;color:#fff;font-weight:800;font-size:1.05rem;
  box-shadow:0 8px 20px rgba(229,57,53,.35);
}
body.theme-triangolo .tri-brand-mark .brand-text{font-family:'Sora',sans-serif;font-weight:800;font-size:1.05rem;letter-spacing:.02em;color:#fff}
body.theme-triangolo .btn-tri-secondary{background:#1a1a1a;border:1px solid #2e2e2e;color:#fff}
body.theme-triangolo .btn-telegram{background:#2AABEE;border-color:#2AABEE;color:#fff}
body.theme-triangolo .btn-telegram:hover{filter:brightness(1.08)}
body.theme-triangolo .category-ad-slot{margin:14px 0 6px;border-radius:14px;background:#141414;border:1px solid #242424;padding:10px 12px}
"""
    if needle not in t:
        print("WARN: css needle missing")
        return t
    return t.replace(needle, extra, 1)


MAIN_EJS_TRI = '''<div class="page">
<% if (typeof theme !== 'undefined' && theme === 'triangolo') { %>
<div class="main-hero">
  <div class="main-hero-pane">
    <div class="tri-brand-mark"><span class="mark">S</span><span class="brand-text">Shif Shqip IPTV PORTAL</span></div>
    <span class="hero-chip"><span class="server-dot"></span> PREMIUM STREAMING PORTAL</span>
    <h1>Shif Shqip IPTV PORTAL</h1>
    <p>Shiko Kanale Shqip Falas &amp; Radio Online</p>
    <div class="main-hero-actions" data-testid="hero-main-actions">
      <a href="<%= basePath %>/" class="a-btn a-btn-primary btn-tri-primary"><i class="fas fa-tv"></i> Browse Channels</a>
      <a href="#muzike" class="a-btn btn-tri-secondary" onclick="try{filterChannels('radio');}catch(e){}; return true;"><i class="fas fa-broadcast-tower"></i> Listen Radios</a>
      <a href="<%= typeof telegramUrl !== 'undefined' && telegramUrl ? telegramUrl : 'https://t.me/albview' %>" target="_blank" rel="noopener noreferrer" class="a-btn btn-telegram" data-testid="hero-telegram-button"><i class="fab fa-telegram-plane"></i> Bashkohu në Telegram</a>
    </div>
    <div class="search-bar"><i class="fas fa-search"></i>
      <input id="channelSearchInput" data-testid="main-channel-search-input" type="text" placeholder="Search channels or radios..." oninput="filterChannels(this.value)">
    </div>
    <div class="tri-stats">
      <div class="tri-stat"><%= typeof channelCount !== 'undefined' ? channelCount : visibleMainCount %> Channels</div>
      <div class="tri-stat">Live</div>
      <div class="tri-stat"><%= typeof categoryCount !== 'undefined' ? categoryCount : 0 %> Categories</div>
    </div>
  </div>
</div>
<div id="favSection" style="display:none;margin-bottom:24px">
  <div class="tri-cat-head"><div><div class="tri-cat-kicker">YOURS</div><h2 class="tri-cat-title">Favoritet</h2></div></div>
  <div class="channels-grid tri-h-scroll" id="favGrid"></div>
</div>
<%- typeof categoryRowsHtml !== 'undefined' ? categoryRowsHtml : '' %>
<div class="info-footer">© Shif Shqip</div>
<% } else { %>
'''


def update_main_ejs(text: str) -> str:
    # Replace triangolo branch opening through else
    m = re.search(
        r"<div class=\"page\">\s*<% if \(typeof theme !== 'undefined' && theme === 'triangolo'\) \{ %>.*?<% \} else \{ %>",
        text,
        flags=re.S,
    )
    if not m:
        raise SystemExit("FAIL: mainPage triangolo block not found")
    return text[: m.start()] + MAIN_EJS_TRI + text[m.end() :]


def populate_categories():
    import collections

    cats = json.loads(CATS.read_text(encoding="utf-8"))
    lokke = json.loads(Path("/root/all/all2/data/kool_lokke_channels_cache.json").read_text(encoding="utf-8"))
    api_tv = json.loads(Path("/root/all/all2/data/api_tv_channels_cache.json").read_text(encoding="utf-8"))
    api_sp = json.loads(Path("/root/all/all2/data/api_sport_channels_cache.json").read_text(encoding="utf-8"))
    allch = {**lokke, **api_tv, **api_sp}

    rules = {
        "kombetare": r"top channel|top news|abc|syri|a2|euronews|rtsh|klan|vizion|report|news 24|alsat|oranews|fax news|tv 21|kanal 10|dukagjini|rtk|koha",
        "filma": r"film|movie|cinema|hbo|netflix|cinemax|fox|axn",
        "femije": r"kids|femij|cartoon|disney|nick|moonbug|zoomoo|baby|junior",
        "lajme": r"news|lajm|cnn|bbc|sky news|al jazeera|france 24",
        "dazn": r"dazn",
        "bein-sport": r"bein|beIN",
        "sport": r"sport|arena|eurosport|super sport|art sport|trinq",
    }
    # prefer Albania for lokke
    assigned = {k: [] for k in rules}
    for slug, v in allch.items():
        if v.get("source") == "kool_lokke" and str(v.get("koolCategory") or "") != "Albania":
            # still allow sport api etc.
            if v.get("source") != "api":
                continue
        name = (v.get("name") or "").lower()
        for cat, pat in rules.items():
            if re.search(pat, name, re.I):
                assigned[cat].append(slug)
                break

    show = {"kombetare", "sport", "filma", "lajme", "femije", "bein-sport"}
    for cat_id, slugs in assigned.items():
        if cat_id not in cats:
            continue
        # Only fill if empty
        if not cats[cat_id].get("channels"):
            cats[cat_id]["channels"] = slugs[:80]
        if cat_id in show:
            cats[cat_id]["showOnMain"] = True
    # muzike synthetic
    muzike = []
    for slug, v in allch.items():
        if v.get("source") == "kool_lokke" and v.get("koolCategory") != "Albania":
            continue
        if re.search(r"music|muzik|mtv|kiss|radio|deluxe", (v.get("name") or ""), re.I):
            muzike.append(slug)
    if "muzike" not in cats:
        cats["muzike"] = {
            "name": "Muzike",
            "logo": "",
            "channels": muzike[:60],
            "created": "auto",
            "showOnMain": True,
            "order": 6,
        }
    else:
        if not cats["muzike"].get("channels"):
            cats["muzike"]["channels"] = muzike[:60]
        cats["muzike"]["showOnMain"] = True

    CATS.write_text(json.dumps(cats, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print("categories updated:", {k: len(cats[k].get("channels") or []) for k in sorted(cats) if cats[k].get("showOnMain")})


def main():
    t = SERVER.read_text(encoding="utf-8")
    t = replace_builder(t)
    t = polish_css(t)
    SERVER.write_text(t, encoding="utf-8")
    print("server.js patched")

    ejs = MAIN_EJS.read_text(encoding="utf-8")
    MAIN_EJS.write_text(update_main_ejs(ejs), encoding="utf-8")
    print("mainPage.ejs patched")

    populate_categories()

    r = subprocess.run(["node", "--check", str(SERVER)], capture_output=True, text=True)
    print("node check:", r.returncode, (r.stderr or "OK")[:400])
    if r.returncode != 0:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
