#!/usr/bin/env python3
"""Prod API direct CDN segments + Triangolo red design + category ads + Telegram button."""
from __future__ import annotations

import json
from pathlib import Path

SERVER = Path("/root/all/all2/server.js")
MAIN_EJS = Path("/root/all/all2/views/public/mainPage.ejs")
MAIN_JS = Path("/root/all/all2/views/public/mainPage.js")
CONFIG = Path("/root/all/all2/data/channel_config.json")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"FAIL {label}: expected 1, found {count}")
    return text.replace(old, new, 1)


def main() -> None:
    t = SERVER.read_text(encoding="utf-8")

    # ── 1) rewriteM3U8: optional direct CDN media segments (traffic ≠ VPS) ──
    old_rw = """function rewriteM3U8(content, originalUrl, options = {}) {
  const profileToken = String(options.profileToken || '').trim();
  const toAbsolute = (value) => {
    try { return new URL(value, originalUrl).toString(); } catch { return ''; }
  };
  return String(content || '').split('\\n').map(rawLine => {
    const line = rawLine.trim();
    if (!line) return line;
    if (line.startsWith('#')) {
      if (!line.includes('URI="')) return line;
      return line.replace(/URI="([^"]+)"/g, (_, uri) => {
        const fullUrl = toAbsolute(uri);
        if (!fullUrl) return `URI="${uri}"`;
        const useCdn = isLikelyMediaSegmentUrl(fullUrl);
        return `URI="${buildSegmentProxyUrl(fullUrl, profileToken, { useCdn })}"`;
      });
    }
    const fullUrl = toAbsolute(line);
    if (!fullUrl) return line;
    if (/\\.m3u8($|\\?)/i.test(fullUrl) || /\\/playlist($|\\?)/i.test(fullUrl) || /chunklist/i.test(fullUrl) || /manifest/i.test(fullUrl)) {
      const profileQuery = profileToken ? `&hp=${encodeURIComponent(profileToken)}` : '';
      const subPath = `${BASE_PATH}/proxy/sub?id=${encodeURIComponent(encrypt(fullUrl))}${profileQuery}`;
      return buildAbsoluteStreamPath(subPath);
    }
    const useCdn = isLikelyMediaSegmentUrl(fullUrl);
    return buildSegmentProxyUrl(fullUrl, profileToken, { useCdn });
  }).join('\\n');
}"""
    new_rw = """function rewriteM3U8(content, originalUrl, options = {}) {
  const profileToken = String(options.profileToken || '').trim();
  const directMediaSegments = options.directMediaSegments === true;
  const toAbsolute = (value) => {
    try { return new URL(value, originalUrl).toString(); } catch { return ''; }
  };
  const mapMediaUrl = (fullUrl) => {
    if (directMediaSegments && isLikelyMediaSegmentUrl(fullUrl)) return fullUrl;
    const useCdn = isLikelyMediaSegmentUrl(fullUrl);
    return buildSegmentProxyUrl(fullUrl, profileToken, { useCdn });
  };
  return String(content || '').split('\\n').map(rawLine => {
    const line = rawLine.trim();
    if (!line) return line;
    if (line.startsWith('#')) {
      if (!line.includes('URI="')) return line;
      return line.replace(/URI="([^"]+)"/g, (_, uri) => {
        const fullUrl = toAbsolute(uri);
        if (!fullUrl) return `URI="${uri}"`;
        return `URI="${mapMediaUrl(fullUrl)}"`;
      });
    }
    const fullUrl = toAbsolute(line);
    if (!fullUrl) return line;
    if (/\\.m3u8($|\\?)/i.test(fullUrl) || /\\/playlist($|\\?)/i.test(fullUrl) || /chunklist/i.test(fullUrl) || /manifest/i.test(fullUrl)) {
      const profileQuery = profileToken ? `&hp=${encodeURIComponent(profileToken)}` : '';
      const subPath = `${BASE_PATH}/proxy/sub?id=${encodeURIComponent(encrypt(fullUrl))}${profileQuery}`;
      return buildAbsoluteStreamPath(subPath);
    }
    return mapMediaUrl(fullUrl);
  }).join('\\n');
}"""
    t = replace_once(t, old_rw, new_rw, "rewriteM3U8 direct segments")

    old_pm = """  if (winner.idx > 0) res.setHeader('X-Proxy-Failover', `candidate-${winner.idx + 1}`);
  res.send(rewriteM3U8(winner.response.data, winner.candidateUrl, { profileToken }));
}"""
    new_pm = """  if (winner.idx > 0) res.setHeader('X-Proxy-Failover', `candidate-${winner.idx + 1}`);
  const directMediaSegments = options.directMediaSegments === true;
  if (directMediaSegments) res.setHeader('X-Segment-Mode', 'direct-cdn');
  res.send(rewriteM3U8(winner.response.data, winner.candidateUrl, { profileToken, directMediaSegments }));
}"""
    t = replace_once(t, old_pm, new_pm, "proxyManifest pass directMediaSegments")

    # Force direct CDN segments for prod API header-proxy paths
    old_proxy_opt = """async function proxyChannelWithOptionalResolve(req, res, slug, channel, label = 'proxy') {
  const sourceKey = getChannelSourceKey(channel);
  const upstreamProfile = buildChannelUpstreamProfile(channel, slug);
  let manifestUrl = String(channel.url || '').trim();
  if (channelHasUpstreamProfile(channel) || shouldHidePlaybackSource(sourceKey)) {
    const resolved = await resolveDirectManifestUrl(manifestUrl, req, upstreamProfile);
    if (resolved) manifestUrl = resolved;
  }
  const profileToken = issueUpstreamProfile(upstreamProfile);
  const candidates = resolveFailoverCandidates(slug, manifestUrl);
  await proxyManifest(manifestUrl, req, res, `${label}:${slug}`, { profileToken, candidates });
}"""
    new_proxy_opt = """async function proxyChannelWithOptionalResolve(req, res, slug, channel, label = 'proxy') {
  const sourceKey = getChannelSourceKey(channel);
  const upstreamProfile = buildChannelUpstreamProfile(channel, slug);
  let manifestUrl = String(channel.url || '').trim();
  if (channelHasUpstreamProfile(channel) || shouldHidePlaybackSource(sourceKey)) {
    const resolved = await resolveDirectManifestUrl(manifestUrl, req, upstreamProfile);
    if (resolved) manifestUrl = resolved;
  }
  const profileToken = issueUpstreamProfile(upstreamProfile);
  const candidates = resolveFailoverCandidates(slug, manifestUrl);
  // Prod API: manifest via VPS (headers), media segments direct to CDN (no VPS bandwidth).
  const directMediaSegments = (sourceKey === 'api_tv' || sourceKey === 'api_sport');
  await proxyManifest(manifestUrl, req, res, `${label}:${slug}`, { profileToken, candidates, directMediaSegments });
}"""
    t = replace_once(t, old_proxy_opt, new_proxy_opt, "proxyChannelWithOptionalResolve direct CDN")

    # ── 2) Build category rows helper for Triangolo main page ──
    marker = "function buildCustomCategoryCardsHtml() {"
    if marker not in t:
        raise SystemExit("FAIL buildCustomCategoryCardsHtml marker")
    helper = r'''
function buildNativeAdSlotHtml(adSettings, index = 0) {
  const nativeSnippets = [String(adSettings.banner_html || '').trim(), ...(Array.isArray(adSettings.banner_html_list) ? adSettings.banner_html_list : [])]
    .map(v => String(v || '').trim())
    .filter(Boolean);
  if (!nativeSnippets.length) return '';
  const pick = nativeSnippets[index % nativeSnippets.length];
  const encoded = Buffer.from(String(pick || ''), 'utf8').toString('base64');
  return `<div class="category-ad-slot native-ad-card" data-native-ad="1"><div class="native-ad-head"><i class="fas fa-bullhorn"></i> Sponsored</div><div class="native-ad-body" data-ad-snippet="${encoded}"></div></div>`;
}
function buildTriangoloCategoryRowsHtml(req = null) {
  const adSettings = normalizeAdSnippetConfig(appConfig.ad_snippets);
  const enableAds = adSettings.enabled === true && adSettings.grid_native_enabled === true;
  const visible = filterChannelsForDevice(visibleChannels(allCombined), req);
  const rows = [];
  let adIndex = 0;
  const entries = Object.entries(customCategories || {})
    .filter(([, cat]) => cat?.showOnMain !== false)
    .sort((a, b) => (Number(a[1].order || 0) - Number(b[1].order || 0)) || String(a[1].name || '').localeCompare(String(b[1].name || '')));
  for (const [id, cat] of entries) {
    const channels = getCustomCategoryChannels(id);
    const channelEntries = Object.entries(channels).filter(([slug]) => !!visible[slug]).slice(0, 18);
    if (!channelEntries.length) continue;
    const cards = buildCards(Object.fromEntries(channelEntries), {
      pageType: 'triangolo_row',
      categoryKeyOverride: `custom:${createSlug(id)}`,
      redirectKoolOnMain: false
    });
    const count = channelEntries.length;
    const viewAll = `${BASE_PATH}/category/custom/${encodeURIComponent(id)}`;
    rows.push(`<section class="tri-cat-row" data-cat="${escapeHtmlAttr(id)}">
  <div class="tri-cat-head">
    <div>
      <div class="tri-cat-kicker">TV CHANNELS</div>
      <h2 class="tri-cat-title">${escapeHtml(cat.name || id)}</h2>
    </div>
    <a class="tri-view-all" href="${viewAll}">View all</a>
  </div>
  <div class="tri-cat-scroller channels-grid tri-h-scroll">${cards}</div>
  ${enableAds ? buildNativeAdSlotHtml(adSettings, adIndex++) : ''}
</section>`);
  }
  if (!rows.length) {
    // Fallback: one row from main featured / limited channels
    const featuredSlugs = Array.isArray(appConfig.featured_channels) ? appConfig.featured_channels.filter(slug => !!visible[slug]) : [];
    const limited = (featuredSlugs.length ? featuredSlugs.map(s => [s, visible[s]]) : Object.entries(visible)).slice(0, Math.max(12, Number(appConfig.main_page_limit) || 20));
    const cards = buildCards(Object.fromEntries(limited.filter(([, ch]) => !!ch)), { pageType: 'triangolo_row', redirectKoolOnMain: false });
    rows.push(`<section class="tri-cat-row"><div class="tri-cat-head"><div><div class="tri-cat-kicker">TV CHANNELS</div><h2 class="tri-cat-title">Live</h2></div></div><div class="tri-cat-scroller channels-grid tri-h-scroll">${cards}</div>${enableAds ? buildNativeAdSlotHtml(adSettings, 0) : ''}</section>`);
  }
  return rows.join('\\n');
}
'''
    # Insert before buildCustomCategoryCardsHtml
    t = t.replace(marker, helper + "\n" + marker, 1)

    # ── 3) Main route: pass categoryRows + telegram for triangolo ──
    old_main = """  const pageContentHtml = renderMainPageContent({
    basePath: BASE_PATH,
    customCards: `${getAndroidAccessNotice(req)}${customCards}`,
    visibleMainCount: limited.length,
    manualMainEnabled,
    cards
  });
  const playerScriptHtml = playerScript(urlMap, appConfig.player_defaults, appConfig.player_source_defaults, {
    redirectKoolOnMain: false,
    pageType: 'main'
  });
  res.send(renderPublicLayout({
    baseStyle: getPublicBaseStyle(),
    bodyClass: getPublicThemeBodyClass(),
    title: 'TV Server',"""
    # There may be two similar blocks (main + watch). Only replace first homepage one carefully.
    # Count occurrences
    if t.count(old_main) < 1:
        raise SystemExit(f"FAIL main render block count={t.count(old_main)}")
    new_main = """  const webUi = getWebUiSettings();
  const categoryRowsHtml = webUi.theme === 'triangolo' ? buildTriangoloCategoryRowsHtml(req) : '';
  const pageContentHtml = renderMainPageContent({
    basePath: BASE_PATH,
    customCards: `${getAndroidAccessNotice(req)}${customCards}`,
    visibleMainCount: limited.length,
    manualMainEnabled,
    cards,
    theme: webUi.theme || 'classic',
    categoryRowsHtml,
    channelCount: Object.keys(vis).length,
    categoryCount: visibleCustomCategoryEntries.length,
    telegramUrl: 'https://t.me/albview',
    playerStatusText: webUi.player_status_text || 'Bashkohu në Telegram: t.me/albview'
  });
  const playerScriptHtml = playerScript(urlMap, appConfig.player_defaults, appConfig.player_source_defaults, {
    redirectKoolOnMain: false,
    pageType: 'main'
  });
  res.send(renderPublicLayout({
    baseStyle: getPublicBaseStyle(),
    bodyClass: getPublicThemeBodyClass(),
    title: 'Shif Shqip Live TV',"""
    t = t.replace(old_main, new_main, 1)  # only first (homepage)

    # ── 4) Replace getPublicBaseStyle triangolo CSS with full red design ──
    start = t.find("function getPublicBaseStyle() {")
    if start < 0:
        raise SystemExit("FAIL getPublicBaseStyle")
    end = t.find("\nfunction normalizeInstallToken", start)
    if end < 0:
        end = t.find("\nfunction ", start + 40)
        # find next function after getPublicBaseStyle block - look for closing of function then next
    # Safer: find the style end
    style_end = t.find("</style>`;\n  return BASE_STYLE + extra;\n}", start)
    if style_end < 0:
        raise SystemExit("FAIL style end")
    style_end = t.find("}", style_end) + 1
    new_style_fn = r'''function getPublicBaseStyle() {
  const ui = getWebUiSettings();
  if (ui.theme !== 'triangolo') return BASE_STYLE;
  const extra = `
<style id="theme-triangolo-overrides">
@import url('https://fonts.googleapis.com/css2?family=Manrope:wght@500;600;700;800&family=Sora:wght@600;700;800&display=swap');
body.theme-triangolo{
  --bg:#050505;--bg2:#0a0a0a;--surface:#141414;--card:#1a1a1a;--border:rgba(255,255,255,.08);
  --accent:#e53935;--accent-2:#c62828;--sport:#e53935;--text:#f5f5f5;--muted:#9a9a9a;--red:#e53935;
  background:#050505 !important;
  font-family:'Manrope',system-ui,sans-serif;
  color:var(--text);
}
body.theme-triangolo h1,body.theme-triangolo h2,body.theme-triangolo h3,body.theme-triangolo .nav-logo{
  font-family:'Sora',system-ui,sans-serif;letter-spacing:-.02em;
}
body.theme-triangolo nav{
  background:rgba(5,5,5,.92);backdrop-filter:blur(12px);border-bottom:1px solid rgba(255,255,255,.06);
  min-height:58px;padding:8px 16px;
}
body.theme-triangolo .nav-logo{font-size:1rem;font-weight:800;gap:10px}
body.theme-triangolo .nav-logo i{
  width:34px;height:34px;border-radius:10px;display:inline-flex;align-items:center;justify-content:center;
  background:linear-gradient(145deg,#e53935,#b71c1c);color:#fff;font-size:.95rem;
}
body.theme-triangolo .nav-link,body.theme-triangolo .nav-link-cta{
  border:0;background:transparent;border-radius:10px;padding:8px 12px;font-size:.78rem;color:var(--muted);
}
body.theme-triangolo .nav-link:hover,body.theme-triangolo .nav-link.active,body.theme-triangolo .nav-link-cta{
  color:#fff;background:rgba(229,57,53,.14);
}
body.theme-triangolo .page{max-width:1180px;margin:0 auto;padding:14px 14px 48px}
body.theme-triangolo .main-hero{margin:8px 0 18px}
body.theme-triangolo .main-hero-pane{
  background:linear-gradient(165deg,#1c1c1c,#121212 60%,#0d0d0d);
  border:1px solid rgba(255,255,255,.07);border-radius:22px;padding:22px 18px 18px;
  box-shadow:0 18px 40px rgba(0,0,0,.35);
}
body.theme-triangolo .hero-chip{
  border:0;background:transparent;color:#cfcfcf;font-size:.68rem;letter-spacing:.12em;
  text-transform:uppercase;padding:0;margin-bottom:10px;display:inline-flex;align-items:center;gap:8px;
}
body.theme-triangolo .hero-chip .server-dot{background:#e53935;box-shadow:0 0 0 4px rgba(229,57,53,.18)}
body.theme-triangolo .main-hero-pane h1{
  font-size:clamp(1.55rem,5.5vw,2.35rem);line-height:1.1;margin:0 0 8px;
  background:none;-webkit-text-fill-color:#fff;color:#fff;font-weight:800;
}
body.theme-triangolo .main-hero-pane p{color:#c7c7c7;font-size:.92rem;margin:0 0 16px}
body.theme-triangolo .main-hero-actions{display:flex;flex-wrap:wrap;gap:10px;margin-bottom:14px}
body.theme-triangolo .a-btn-primary,body.theme-triangolo .btn-tri-primary{
  background:#e53935 !important;color:#fff !important;border:0 !important;border-radius:12px !important;
  padding:11px 16px !important;font-weight:700 !important;box-shadow:0 8px 20px rgba(229,57,53,.28);
}
body.theme-triangolo .btn-tri-secondary{
  background:#222 !important;color:#fff !important;border:1px solid rgba(255,255,255,.1) !important;
  border-radius:12px !important;padding:11px 16px !important;font-weight:600 !important;
}
body.theme-triangolo .btn-telegram{
  background:linear-gradient(135deg,#2AABEE,#229ED9) !important;color:#fff !important;border:0 !important;
  border-radius:12px !important;padding:11px 16px !important;font-weight:700 !important;
}
body.theme-triangolo .search-bar{
  background:#181818;border:1px solid rgba(255,255,255,.08);border-radius:14px;margin:0 0 14px;
}
body.theme-triangolo .search-bar input{background:transparent;border:0;padding:13px 14px 13px 42px}
body.theme-triangolo .tri-stats{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin-top:4px}
body.theme-triangolo .tri-stat{
  background:#1b1b1b;border:1px solid rgba(255,255,255,.06);border-radius:12px;padding:10px 8px;text-align:center;
  font-size:.72rem;color:#ddd;font-weight:700;
}
body.theme-triangolo .admin-bar,body.theme-triangolo .main-grid-two.single-col:not(.tri-keep){display:none}
body.theme-triangolo .tri-cat-row{margin:0 0 22px}
body.theme-triangolo .tri-cat-head{display:flex;align-items:flex-end;justify-content:space-between;gap:10px;margin:0 0 12px}
body.theme-triangolo .tri-cat-kicker{font-size:.62rem;letter-spacing:.14em;text-transform:uppercase;color:#8f8f8f;font-weight:700}
body.theme-triangolo .tri-cat-title{font-size:1.15rem;margin:2px 0 0;color:#fff;font-weight:800}
body.theme-triangolo .tri-view-all{
  background:#1d1d1d;border:1px solid rgba(255,255,255,.08);color:#ddd;border-radius:999px;
  padding:6px 12px;font-size:.72rem;font-weight:600;white-space:nowrap;
}
body.theme-triangolo .tri-h-scroll{
  display:flex !important;grid-template-columns:none !important;gap:12px;overflow-x:auto;
  padding:2px 2px 10px;scroll-snap-type:x mandatory;-webkit-overflow-scrolling:touch;
}
body.theme-triangolo .tri-h-scroll::-webkit-scrollbar{height:0}
body.theme-triangolo .tri-h-scroll .ch-card{
  flex:0 0 132px;scroll-snap-align:start;min-height:148px;background:#171717;
  border:1px solid rgba(255,255,255,.07);border-radius:16px;padding:0;overflow:hidden;
  box-shadow:0 10px 24px rgba(0,0,0,.28);
}
body.theme-triangolo .ch-thumb{
  height:96px;background:#101010;display:flex;align-items:center;justify-content:center;position:relative;
}
body.theme-triangolo .ch-thumb img{width:72%;height:72%;object-fit:contain}
body.theme-triangolo .ch-live{
  position:absolute;top:8px;right:8px;background:#e53935;color:#fff;border-radius:6px;
  font-size:.55rem;font-weight:800;padding:2px 6px;letter-spacing:.04em;
}
body.theme-triangolo .ch-info{padding:8px 8px 10px;background:#121212}
body.theme-triangolo .ch-name{font-size:.68rem;font-weight:700;text-transform:uppercase;letter-spacing:.02em;color:#f2f2f2}
body.theme-triangolo .ch-actions{display:none}
body.theme-triangolo .category-ad-slot{
  margin:12px 0 4px;border-radius:16px;border:1px solid rgba(255,255,255,.08);
  background:linear-gradient(120deg,rgba(229,57,53,.12),rgba(255,255,255,.03));padding:12px;
}
body.theme-triangolo .native-ad-body{border-color:rgba(255,255,255,.12);min-height:70px}
body.theme-triangolo .info-footer{color:#777;text-align:center;padding:18px 0 8px;font-size:.72rem;border:0}
body.theme-triangolo #playerModal .modal,body.theme-triangolo .modal{
  border-radius:18px;border:1px solid rgba(255,255,255,.1);overflow:hidden;background:#0c0c0c;
}
body.theme-triangolo .player-status a.btn-telegram-inline{
  display:inline-flex;align-items:center;gap:8px;margin-top:8px;background:#2AABEE;color:#fff !important;
  text-decoration:none;border-radius:10px;padding:8px 12px;font-weight:700;font-size:.75rem;
}
@media (min-width:860px){
  body.theme-triangolo .tri-h-scroll .ch-card{flex-basis:148px}
  body.theme-triangolo .main-hero-pane{padding:28px}
}
</style>`;
  return BASE_STYLE + extra;
}'''
    t = t[:start] + new_style_fn + t[style_end:]

    # ── 5) setStatus: render Telegram as button ──
    old_set = """function setStatus(kind,text){
  const colors={ok:'var(--sport)',warn:'var(--orange)',err:'var(--red)',info:'var(--muted)'};
  const icons={ok:'fa-circle',warn:'fa-exclamation-triangle',err:'fa-times-circle',info:'fa-info-circle'};
  const dot=kind==='ok'?' class="pulse-dot" style="margin-right:4px;vertical-align:middle"':'';
  document.getElementById('playerStatus').innerHTML='<span style="color:'+colors[kind]+'"><i class="fas '+icons[kind]+'"'+dot+'></i> '+text+'</span>';
}"""
    # Find actual setStatus - may differ slightly
    if "function setStatus(kind,text){" not in t:
        raise SystemExit("FAIL setStatus missing")
    # Use line-based replace for setStatus body
    import re
    m = re.search(r"function setStatus\(kind,text\)\{[\s\S]*?\n\}", t)
    if not m:
        raise SystemExit("FAIL setStatus regex")
    new_set = """function setStatus(kind,text){
  const colors={ok:'var(--sport)',warn:'var(--orange)',err:'var(--red)',info:'var(--muted)'};
  const icons={ok:'fa-circle',warn:'fa-exclamation-triangle',err:'fa-times-circle',info:'fa-info-circle'};
  const dot=kind==='ok'?' class="pulse-dot" style="margin-right:4px;vertical-align:middle"':'';
  const raw=String(text||'');
  const tgMatch=raw.match(/(?:https?:\\/\\/)?(?:t\\.me|telegram\\.me)\\/([A-Za-z0-9_]+)/i);
  let html='<span style="color:'+colors[kind]+'"><i class="fas '+icons[kind]+'"'+dot+'></i> '+raw.replace(/</g,'&lt;')+'</span>';
  if(tgMatch){
    const handle=tgMatch[1];
    const href='https://t.me/'+handle;
    const label=raw.replace(tgMatch[0],'').replace(/[:\\-–—]\\s*$/,'').trim() || ('Bashkohu @'+handle);
    html='<span style="color:'+colors[kind]+'"><i class="fas '+icons[kind]+'"'+dot+'></i> '+label.replace(/</g,'&lt;')+'</span>'
      +'<div><a class="btn-telegram-inline" href="'+href+'" target="_blank" rel="noopener noreferrer"><i class="fab fa-telegram-plane"></i> Hap Telegram</a></div>';
  }
  document.getElementById('playerStatus').innerHTML=html;
}"""
    # Careful: setStatus is inside template literal - \ need double escaping for regex in emitted JS
    # In server.js source inside `return \`...\``, `\*` becomes weird.
    # The function setStatus is INSIDE the playerScript template string!
    # So when we write tgMatch regex, we need `\\\\/` etc. for final browser output.
    
    # Actually looking at the file - setStatus is inside the backtick template of playerScript.
    # So in the .js file we need: raw.match(/(?:https?:\\/\\/)?(?:t\\.me|telegram\\.me)\\/([A-Za-z0-9_]+)/i);
    # Wait - inside template literal, `\\` → `\`, so to get `\/` in output we need `\\/` in source... 
    # Template: `\\/` → output `\/` 
    # For browser regex `/(?:https?:\/\/)?/` we need in template: `/(?:https?:\\/\\/)?/` 
    # which in the Python string writing to file...
    
    # Simpler approach: avoid complex regex in template - use includes/indexOf
    new_set = """function setStatus(kind,text){
  const colors={ok:'var(--sport)',warn:'var(--orange)',err:'var(--red)',info:'var(--muted)'};
  const icons={ok:'fa-circle',warn:'fa-exclamation-triangle',err:'fa-times-circle',info:'fa-info-circle'};
  const dot=kind==='ok'?' class="pulse-dot" style="margin-right:4px;vertical-align:middle"':'';
  const raw=String(text||'');
  const lower=raw.toLowerCase();
  let tgHandle='';
  const markers=['t.me/','telegram.me/','https://t.me/','http://t.me/'];
  for(const m of markers){
    const idx=lower.indexOf(m);
    if(idx>=0){
      const start=idx+m.length;
      const rest=raw.slice(start);
      tgHandle=(rest.match(/^[A-Za-z0-9_]+/)||[''])[0];
      if(tgHandle) break;
    }
  }
  let html='<span style="color:'+colors[kind]+'"><i class="fas '+icons[kind]+'"'+dot+'></i> '+raw.replace(/</g,'&lt;')+'</span>';
  if(tgHandle){
    const href='https://t.me/'+tgHandle;
    const label=raw.replace(/https?:\\/\\/(?:t\\.me|telegram\\.me)\\/[A-Za-z0-9_]+/ig,'').replace(/(?:t\\.me|telegram\\.me)\\/[A-Za-z0-9_]+/ig,'').replace(/[:\\-–—]\\s*$/,'').trim() || ('Bashkohu @'+tgHandle);
    html='<span style="color:'+colors[kind]+'"><i class="fas '+icons[kind]+'"'+dot+'></i> '+label.replace(/</g,'&lt;')+'</span>'
      +'<div><a class="btn-telegram-inline" href="'+href+'" target="_blank" rel="noopener noreferrer"><i class="fab fa-telegram-plane"></i> Hap Telegram</a></div>';
  }
  document.getElementById('playerStatus').innerHTML=html;
}"""
    # Even simpler label without regex replace of url
    new_set = """function setStatus(kind,text){
  const colors={ok:'var(--sport)',warn:'var(--orange)',err:'var(--red)',info:'var(--muted)'};
  const icons={ok:'fa-circle',warn:'fa-exclamation-triangle',err:'fa-times-circle',info:'fa-info-circle'};
  const dot=kind==='ok'?' class="pulse-dot" style="margin-right:4px;vertical-align:middle"':'';
  const raw=String(text||'');
  const lower=raw.toLowerCase();
  let tgHandle='';
  const markers=['https://t.me/','http://t.me/','t.me/','telegram.me/'];
  for(const m of markers){
    const idx=lower.indexOf(m);
    if(idx>=0){
      tgHandle=(raw.slice(idx+m.length).match(/^[A-Za-z0-9_]+/)||[''])[0];
      if(tgHandle) break;
    }
  }
  const safe=raw.replace(/</g,'&lt;');
  let html='<span style="color:'+colors[kind]+'"><i class="fas '+icons[kind]+'"'+dot+'></i> '+safe+'</span>';
  if(tgHandle){
    html='<span style="color:'+colors[kind]+'"><i class="fas '+icons[kind]+'"'+dot+'></i> Bashkohu në Telegram</span>'
      +'<div><a class="btn-telegram-inline" href="https://t.me/'+tgHandle+'" target="_blank" rel="noopener noreferrer"><i class="fab fa-telegram-plane"></i> Hap Telegram @'+tgHandle+'</a></div>';
  }
  document.getElementById('playerStatus').innerHTML=html;
}"""
    t = t[: m.start()] + new_set + t[m.end() :]

    SERVER.write_text(t, encoding="utf-8")
    print("server.js updated")

    # ── mainPage.ejs / js ──
    MAIN_EJS.write_text("""<div class="page">
<% if (typeof theme !== 'undefined' && theme === 'triangolo') { %>
<div class="main-hero">
  <div class="main-hero-pane">
    <span class="hero-chip"><span class="server-dot"></span> PREMIUM STREAMING PORTAL</span>
    <h1>Shif Shqip Live TV</h1>
    <p>Shiko Kanale Shqip Falas &amp; Radio Online</p>
    <div class="main-hero-actions" data-testid="hero-main-actions">
      <a href="<%= basePath %>/" class="a-btn a-btn-primary btn-tri-primary"><i class="fas fa-tv"></i> Browse Channels</a>
      <a href="<%= basePath %>/vod" class="a-btn btn-tri-secondary"><i class="fas fa-film"></i> VOD</a>
      <a href="<%= typeof telegramUrl !== 'undefined' && telegramUrl ? telegramUrl : 'https://t.me/albview' %>" target="_blank" rel="noopener noreferrer" class="a-btn btn-telegram"><i class="fab fa-telegram-plane"></i> Bashkohu Telegram</a>
    </div>
    <div class="search-bar"><i class="fas fa-search"></i>
      <input id="channelSearchInput" data-testid="main-channel-search-input" type="text" placeholder="Search channels..." oninput="filterChannels(this.value)">
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
<div class="main-hero">
  <div class="main-hero-pane">
    <div style="display:flex;justify-content:space-between;gap:10px;align-items:center;flex-wrap:wrap;margin-bottom:6px">
      <span class="hero-chip"><span class="server-dot"></span> SHIF SHQIP • LIVE</span>
      <div style="display:flex;gap:6px">
        <button class="a-btn a-btn-primary" data-testid="main-language-sq-button" style="padding:4px 10px;font-size:.68rem" onclick="setUiLang('sq')">SQ</button>
        <button class="a-btn a-btn-purple" data-testid="main-language-en-button" style="padding:4px 10px;font-size:.68rem" onclick="setUiLang('en')">EN</button>
      </div>
    </div>
    <h1 data-i18n="hero_title">Shif Shqip Live TV</h1>
    <p data-i18n="hero_desc">Kanale live të organizuara sipas kategorive.</p>
    <div class="main-hero-actions" data-testid="hero-main-actions">
      <a href="<%= basePath %>/" class="a-btn a-btn-primary" data-testid="hero-live-tv-button"><i class="fas fa-tv"></i> <span data-i18n="hero_live_cta">Live TV</span></a>
      <a href="<%= basePath %>/vod" class="a-btn a-btn-green" data-testid="hero-vod-button"><i class="fas fa-film"></i> <span data-i18n="hero_vod_cta">VOD</span></a>
      <a href="https://t.me/albview" target="_blank" rel="noopener noreferrer" class="a-btn a-btn-primary" style="background:#2AABEE;border-color:#2AABEE"><i class="fab fa-telegram-plane"></i> Telegram</a>
    </div>
  </div>
</div>

<div class="main-grid-two single-col" data-testid="main-custom-categories-section">
  <section class="section-shell">
    <div class="section-head"><i class="fas fa-folder-open" style="color:var(--accent)"></i><h2 data-i18n="custom_categories">Kategori Custom</h2></div>
    <div class="cat-grid" style="margin-bottom:0">
    <%- customCards %>
    </div>
  </section>
</div>
<div class="admin-bar">
  <span class="server-dot"></span> <span data-i18n="server_active">SERVER AKTIV</span>
  <span style="color:var(--muted)"><i class="fas fa-eye"></i> <%= visibleMainCount %> kanale</span>
  <span id="publicLiveViewerBadge" style="display:none;color:var(--sport);font-weight:700;align-items:center;gap:5px"><i class="fas fa-users"></i> 0 shikues live</span>
  <span style="color:var(--muted)"><i class="fas fa-list-check"></i> <%= manualMainEnabled ? 'Main: Manual' : 'Main: Auto' %></span>
</div>
<div id="favSection" style="display:none;margin-bottom:24px">
<div class="page-header" style="margin-bottom:12px"><i class="fas fa-heart" style="color:var(--red)"></i><h2 style="font-size:1.1rem" data-i18n="favorites">Favoritet e Tua</h2></div>
<div class="channels-grid" id="favGrid"></div>
</div>
<div class="search-bar"><i class="fas fa-search"></i>
<input id="channelSearchInput" data-testid="main-channel-search-input" type="text" placeholder="Kërko kanale..." oninput="filterChannels(this.value)"></div>
<div class="channels-grid"><%- cards %></div>
<div class="info-footer" data-i18n="footer">© Shif Shqip</div>
<% } %>
</div>
<script>
const UI_I18N={
  sq:{hero_title:'Shif Shqip Live TV',hero_desc:'Kanale live të organizuara sipas kategorive.',hero_live_cta:'Live TV',hero_vod_cta:'VOD',custom_categories:'Kategori Custom',server_active:'SERVER AKTIV',favorites:'Favoritet e Tua',footer:'© Shif Shqip',search:'Kërko kanale...'},
  en:{hero_title:'Shif Shqip Live TV',hero_desc:'Live channels organized by categories.',hero_live_cta:'Live TV',hero_vod_cta:'VOD',custom_categories:'Custom Categories',server_active:'SERVER ONLINE',favorites:'Your Favorites',footer:'© Shif Shqip',search:'Search channels...'}
};
function setUiLang(lang){
  const l=UI_I18N[lang]||UI_I18N.sq;
  Object.entries(l).forEach(([k,v])=>{const node=document.querySelector('[data-i18n="'+k+'"]'); if(node) node.textContent=v;});
  const input=document.getElementById('channelSearchInput'); if(input) input.placeholder=l.search||input.placeholder;
  try{localStorage.setItem('k1_ui_lang',lang);}catch{}
}
try{setUiLang(localStorage.getItem('k1_ui_lang')||'sq');}catch{setUiLang('sq');}
</script>
""", encoding="utf-8")
    print("mainPage.ejs updated")

    MAIN_JS.write_text("""const { renderTemplate } = require('../renderTemplate');

function renderMainPageContent(params = {}) {
  return renderTemplate('public/mainPage.ejs', {
    basePath: params.basePath,
    customCards: params.customCards || '',
    visibleMainCount: params.visibleMainCount || 0,
    manualMainEnabled: !!params.manualMainEnabled,
    cards: params.cards || '',
    theme: params.theme || 'classic',
    categoryRowsHtml: params.categoryRowsHtml || '',
    channelCount: params.channelCount || params.visibleMainCount || 0,
    categoryCount: params.categoryCount || 0,
    telegramUrl: params.telegramUrl || 'https://t.me/albview',
    playerStatusText: params.playerStatusText || ''
  });
}

module.exports = {
  renderMainPageContent
};
""", encoding="utf-8")
    print("mainPage.js updated")

    # Ensure theme triangolo + telegram text
    if CONFIG.exists():
        c = json.loads(CONFIG.read_text(encoding="utf-8"))
        ui = c.get("web_ui") if isinstance(c.get("web_ui"), dict) else {}
        ui["theme"] = "triangolo"
        ui["player_status_text"] = ui.get("player_status_text") or "Bashkohu në Telegram: t.me/albview"
        c["web_ui"] = ui
        CONFIG.write_text(json.dumps(c, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print("config theme=triangolo")

    print("DONE")


if __name__ == "__main__":
    main()
