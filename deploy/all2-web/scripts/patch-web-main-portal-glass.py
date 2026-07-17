#!/usr/bin/env python3
"""Main-portal channel gate + liquid glass homepage (no hero marketing)."""
from pathlib import Path
import re
import subprocess

SERVER = Path("/root/all/all2/server.js")
MAIN_EJS = Path("/root/all/all2/views/public/mainPage.ejs")

HELPER = r'''
function getMainPortalChannelSlugSet() {
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
}
'''

LIQUID_STYLE_FN = r'''function getPublicBaseStyle() {
  const ui = getWebUiSettings();
  if (ui.theme !== 'triangolo') return BASE_STYLE;
  const extra = `
<style id="theme-triangolo-overrides">
@import url('https://fonts.googleapis.com/css2?family=Outfit:wght@500;600;700;800&family=Plus+Jakarta+Sans:wght@500;600;700&display=swap');
body.theme-triangolo{
  --bg:#07090f;--surface:rgba(255,255,255,.06);--card:rgba(255,255,255,.08);--border:rgba(255,255,255,.14);
  --accent:#ff3b45;--text:#f7f8ff;--muted:rgba(235,240,255,.62);--red:#ff3b45;--glass:rgba(18,22,34,.42);
  --glass-2:rgba(255,255,255,.08);--glow:rgba(255,59,69,.35);
  color:var(--text);
  font-family:'Plus Jakarta Sans',system-ui,sans-serif;
  background:
    radial-gradient(1200px 700px at 12% -10%, rgba(255,59,69,.22), transparent 55%),
    radial-gradient(900px 600px at 88% 8%, rgba(80,140,255,.18), transparent 50%),
    radial-gradient(700px 500px at 50% 100%, rgba(120,90,255,.12), transparent 55%),
    linear-gradient(180deg,#07090f 0%,#0b1020 48%,#07090f 100%) !important;
  min-height:100vh;
}
body.theme-triangolo::before{
  content:"";position:fixed;inset:0;pointer-events:none;z-index:0;
  background:
    linear-gradient(120deg, transparent 0%, rgba(255,255,255,.03) 42%, transparent 68%),
    radial-gradient(circle at 30% 20%, rgba(255,255,255,.05), transparent 28%);
  mix-blend-mode:screen;opacity:.7;
}
body.theme-triangolo nav,
body.theme-triangolo .page{position:relative;z-index:1}
body.theme-triangolo #playerModal.modal-overlay,
body.theme-triangolo #playerModal{
  position:fixed !important;inset:0 !important;z-index:1200 !important;display:none;
  align-items:center;justify-content:center;background:rgba(0,0,0,.92);
  backdrop-filter:blur(10px);-webkit-backdrop-filter:blur(10px);padding:12px;
}
body.theme-triangolo #playerModal.modal-overlay.open,
body.theme-triangolo #playerModal.open{display:flex !important;}
body.theme-triangolo h1,body.theme-triangolo h2,body.theme-triangolo h3,body.theme-triangolo .nav-logo,body.theme-triangolo .tri-cat-title{
  font-family:'Outfit',system-ui,sans-serif;letter-spacing:-.03em;
}
body.theme-triangolo nav{
  background:rgba(12,16,28,.55);
  backdrop-filter:saturate(160%) blur(22px);-webkit-backdrop-filter:saturate(160%) blur(22px);
  border-bottom:1px solid rgba(255,255,255,.12);
  box-shadow:0 10px 40px rgba(0,0,0,.25), inset 0 1px 0 rgba(255,255,255,.12);
  min-height:60px;padding:10px 16px;
}
body.theme-triangolo .nav-logo{font-size:1.02rem;font-weight:800;gap:10px;color:#fff}
body.theme-triangolo .nav-logo i{
  width:36px;height:36px;border-radius:12px;display:inline-flex;align-items:center;justify-content:center;
  background:linear-gradient(145deg,rgba(255,59,69,.95),rgba(180,20,40,.9));
  color:#fff;font-size:.95rem;box-shadow:0 8px 24px var(--glow), inset 0 1px 0 rgba(255,255,255,.35);
}
body.theme-triangolo .nav-link,body.theme-triangolo .nav-link-cta{
  border:1px solid transparent;background:transparent;border-radius:999px;padding:8px 14px;font-size:.78rem;color:var(--muted);
}
body.theme-triangolo .nav-link:hover,body.theme-triangolo .nav-link.active,body.theme-triangolo .nav-link-cta{
  color:#fff;background:rgba(255,255,255,.08);border-color:rgba(255,255,255,.14);
  box-shadow:inset 0 1px 0 rgba(255,255,255,.12);
}
body.theme-triangolo .page{max-width:1180px;margin:0 auto;padding:18px 14px 56px}
body.theme-triangolo .main-hero,
body.theme-triangolo .admin-bar,
body.theme-triangolo .main-grid-two.single-col:not(.tri-keep){display:none !important}
body.theme-triangolo .tri-topbar{
  display:flex;align-items:center;gap:10px;margin:0 0 18px;padding:10px 12px;
  border-radius:18px;
  background:linear-gradient(135deg, rgba(255,255,255,.10), rgba(255,255,255,.04));
  border:1px solid rgba(255,255,255,.16);
  backdrop-filter:blur(18px) saturate(160%);-webkit-backdrop-filter:blur(18px) saturate(160%);
  box-shadow:0 12px 36px rgba(0,0,0,.22), inset 0 1px 0 rgba(255,255,255,.18);
}
body.theme-triangolo .tri-topbar .search-bar{
  flex:1;margin:0;background:rgba(0,0,0,.22);border:1px solid rgba(255,255,255,.10);border-radius:14px;
}
body.theme-triangolo .tri-topbar .search-bar input{background:transparent;border:0;padding:12px 14px 12px 40px;color:#fff}
body.theme-triangolo .tri-topbar .btn-telegram{
  flex:0 0 auto;background:linear-gradient(135deg,#34b7f1,#1e9ad6);border:0;color:#fff;
  border-radius:14px;padding:11px 14px;font-weight:700;font-size:.78rem;
  box-shadow:0 8px 22px rgba(30,154,214,.35), inset 0 1px 0 rgba(255,255,255,.28);
}
body.theme-triangolo .tri-cat-row{margin:0 0 24px}
body.theme-triangolo .tri-cat-head{display:flex;align-items:flex-end;justify-content:space-between;gap:10px;margin:0 0 12px}
body.theme-triangolo .tri-cat-kicker{font-size:.62rem;letter-spacing:.14em;text-transform:uppercase;color:rgba(235,240,255,.5);font-weight:700}
body.theme-triangolo .tri-cat-title{font-size:1.2rem;margin:2px 0 0;color:#fff;font-weight:800}
body.theme-triangolo .tri-view-all{
  background:rgba(255,255,255,.08);border:1px solid rgba(255,255,255,.14);color:#eef2ff;border-radius:999px;
  padding:7px 12px;font-size:.72rem;font-weight:600;white-space:nowrap;
  backdrop-filter:blur(10px);box-shadow:inset 0 1px 0 rgba(255,255,255,.14);
}
body.theme-triangolo .tri-h-scroll{
  display:flex !important;grid-template-columns:none !important;gap:12px;overflow-x:auto;
  padding:2px 2px 12px;scroll-snap-type:x mandatory;-webkit-overflow-scrolling:touch;
}
body.theme-triangolo .tri-h-scroll::-webkit-scrollbar{height:0}
body.theme-triangolo .tri-h-scroll .ch-card{
  flex:0 0 138px;scroll-snap-align:start;min-height:154px;padding:0;overflow:hidden;border-radius:18px;
  background:linear-gradient(165deg, rgba(255,255,255,.14), rgba(255,255,255,.05));
  border:1px solid rgba(255,255,255,.16);
  box-shadow:0 14px 34px rgba(0,0,0,.28), inset 0 1px 0 rgba(255,255,255,.22);
  backdrop-filter:blur(16px) saturate(150%);-webkit-backdrop-filter:blur(16px) saturate(150%);
  transition:transform .2s ease, box-shadow .2s ease;
}
body.theme-triangolo .tri-h-scroll .ch-card:hover{
  transform:translateY(-3px);
  box-shadow:0 18px 40px rgba(0,0,0,.34), 0 0 0 1px rgba(255,255,255,.18), inset 0 1px 0 rgba(255,255,255,.28);
}
body.theme-triangolo .ch-thumb{
  height:100px;background:rgba(0,0,0,.22);display:flex;align-items:center;justify-content:center;position:relative;
}
body.theme-triangolo .ch-thumb img{width:70%;height:70%;object-fit:contain;filter:drop-shadow(0 6px 12px rgba(0,0,0,.35))}
body.theme-triangolo .ch-live{
  position:absolute;top:8px;right:8px;background:rgba(255,59,69,.92);color:#fff;border-radius:999px;
  font-size:.55rem;font-weight:800;padding:3px 7px;letter-spacing:.04em;
  box-shadow:0 6px 16px rgba(255,59,69,.35), inset 0 1px 0 rgba(255,255,255,.25);
}
body.theme-triangolo .ch-info{padding:9px 9px 11px;background:rgba(0,0,0,.18);border-top:1px solid rgba(255,255,255,.08)}
body.theme-triangolo .ch-name{font-size:.68rem;font-weight:700;text-transform:uppercase;letter-spacing:.02em;color:#f7f8ff}
body.theme-triangolo .ch-actions{display:none}
body.theme-triangolo .category-ad-slot{
  margin:12px 0 4px;border-radius:18px;padding:12px;
  background:linear-gradient(135deg, rgba(255,255,255,.10), rgba(255,59,69,.08));
  border:1px solid rgba(255,255,255,.14);
  backdrop-filter:blur(14px);-webkit-backdrop-filter:blur(14px);
  box-shadow:inset 0 1px 0 rgba(255,255,255,.16);
}
body.theme-triangolo .native-ad-body{border-color:rgba(255,255,255,.12);min-height:70px;background:rgba(0,0,0,.18)}
body.theme-triangolo .info-footer{color:rgba(235,240,255,.45);text-align:center;padding:18px 0 8px;font-size:.72rem;border:0}
body.theme-triangolo #playerModal .modal,body.theme-triangolo .modal{
  width:min(960px,96vw);max-height:92vh;overflow:auto;border-radius:22px;
  background:rgba(12,16,28,.88);border:1px solid rgba(255,255,255,.16);
  backdrop-filter:blur(24px) saturate(160%);-webkit-backdrop-filter:blur(24px) saturate(160%);
  box-shadow:0 24px 60px rgba(0,0,0,.45), inset 0 1px 0 rgba(255,255,255,.16);
}
body.theme-triangolo #playerModal .player-stage,body.theme-triangolo #playerModal video{
  width:100%;min-height:220px;background:#000;
}
body.theme-triangolo .player-status a.btn-telegram-inline{
  display:inline-flex;align-items:center;gap:8px;margin-top:8px;background:linear-gradient(135deg,#34b7f1,#1e9ad6);color:#fff !important;
  text-decoration:none;border-radius:12px;padding:8px 12px;font-weight:700;font-size:.75rem;
}
@media (min-width:860px){
  body.theme-triangolo .tri-h-scroll .ch-card{flex-basis:152px}
}
</style>`;
  return BASE_STYLE + extra;
}
'''

MAIN_EJS_TRI = '''<div class="page">
<% if (typeof theme !== 'undefined' && theme === 'triangolo') { %>
<div class="tri-topbar">
  <div class="search-bar"><i class="fas fa-search"></i>
    <input id="channelSearchInput" data-testid="main-channel-search-input" type="text" placeholder="Search channels..." oninput="filterChannels(this.value)">
  </div>
  <a href="<%= typeof telegramUrl !== 'undefined' && telegramUrl ? telegramUrl : 'https://t.me/albview' %>" target="_blank" rel="noopener noreferrer" class="a-btn btn-telegram" data-testid="hero-telegram-button"><i class="fab fa-telegram-plane"></i> Telegram</a>
</div>
<div id="favSection" style="display:none;margin-bottom:24px">
  <div class="tri-cat-head"><div><div class="tri-cat-kicker">YOURS</div><h2 class="tri-cat-title">Favoritet</h2></div></div>
  <div class="channels-grid tri-h-scroll" id="favGrid"></div>
</div>
<%- typeof categoryRowsHtml !== 'undefined' ? categoryRowsHtml : '' %>
<div class="info-footer">© Shif Shqip</div>
<% } else { %>
'''


def insert_helper(t: str) -> str:
    if "function isChannelOnMainPortal(" in t:
        # replace existing block
        start = t.find("function getMainPortalChannelSlugSet(")
        if start < 0:
            start = t.find("function isChannelOnMainPortal(")
        end = t.find("\nfunction visibleChannels(", start)
        if start > 0 and end > start:
            return t[:start] + HELPER.strip() + "\n\n" + t[end + 1 :]
    marker = "function visibleChannels(src) {"
    if marker not in t:
        raise SystemExit("FAIL visibleChannels marker")
    return t.replace(marker, HELPER.strip() + "\n\n" + marker, 1)


def gate_channel_url(t: str) -> str:
    needle = """  const slug = req.params.slug;
  let channel = resolveChannelBySlug(slug);
  if (!channel && /^(alb_|albvod_)/i.test(slug)) channel = await resolveAlbChannelBySlugRuntime(req, slug, { verifyStream: false, tries: 1 });
  if (!channel) return res.status(404).json({ ok: false, error: 'Not found' });
  const channelAccessTier = resolveChannelAccessTierBySlug(slug);"""
    if "assertMainPortalWebAccess(slug)" in t and "router.get('/api/channel-url/:slug'" in t:
        # already gated somewhere - ensure channel-url has it
        pass
    repl = """  const slug = req.params.slug;
  let channel = resolveChannelBySlug(slug);
  if (!channel && /^(alb_|albvod_)/i.test(slug)) channel = await resolveAlbChannelBySlugRuntime(req, slug, { verifyStream: false, tries: 1 });
  if (!channel) return res.status(404).json({ ok: false, error: 'Not found' });
  const mainPortalCheck = assertMainPortalWebAccess(slug);
  if (!mainPortalCheck.ok) {
    return res.status(404).json({ ok: false, error: 'not_on_main_portal' });
  }
  const channelAccessTier = resolveChannelAccessTierBySlug(slug);"""
    if needle not in t:
        raise SystemExit("FAIL channel-url needle")
    return t.replace(needle, repl, 1)


def gate_watch_routes(t: str) -> str:
    # watch-link
    a = """router.get('/api/watch-link/:slug', (req, res) => {
  const slug = createSlug(req.params.slug || '');
  const vis = filterChannelsForDevice(visibleChannels(allCombined), req);
  if (!slug || !vis[slug]) return res.status(404).json({ ok: false, error: 'Channel not found' });"""
    a2 = """router.get('/api/watch-link/:slug', (req, res) => {
  const slug = createSlug(req.params.slug || '');
  const vis = filterChannelsForDevice(visibleChannels(allCombined), req);
  if (!slug || !vis[slug]) return res.status(404).json({ ok: false, error: 'Channel not found' });
  if (!assertMainPortalWebAccess(slug).ok) return res.status(404).json({ ok: false, error: 'not_on_main_portal' });"""
    if a not in t:
        raise SystemExit("FAIL watch-link")
    t = t.replace(a, a2, 1)

    b = """router.get('/watch-open/:slug', (req, res) => {
  const slug = createSlug(req.params.slug || '');
  const vis = filterChannelsForDevice(visibleChannels(allCombined), req);
  if (!slug || !vis[slug]) return res.status(404).send('Kanali nuk u gjet!');"""
    b2 = """router.get('/watch-open/:slug', (req, res) => {
  const slug = createSlug(req.params.slug || '');
  const vis = filterChannelsForDevice(visibleChannels(allCombined), req);
  if (!slug || !vis[slug]) return res.status(404).send('Kanali nuk u gjet!');
  if (!assertMainPortalWebAccess(slug).ok) return res.status(404).send('Kanali nuk është në main page.');"""
    if b not in t:
        raise SystemExit("FAIL watch-open")
    t = t.replace(b, b2, 1)
    return t


def replace_style(t: str) -> str:
    start = t.find("function getPublicBaseStyle() {")
    if start < 0:
        raise SystemExit("FAIL getPublicBaseStyle")
    end = t.find("\nfunction ", start + 40)
    # find end of function properly - next function after return BASE_STYLE
    end_marker = "\nfunction getPublicBaseUrl" if "\nfunction getPublicBaseUrl" in t[start:start+50] else None
    # getPublicBaseStyle is after getPublicThemeBodyClass; next fn varies
    # Use style end marker
    style_end = t.find("  return BASE_STYLE + extra;\n}", start)
    if style_end < 0:
        raise SystemExit("FAIL style end")
    style_end = style_end + len("  return BASE_STYLE + extra;\n}")
    return t[:start] + LIQUID_STYLE_FN + t[style_end:]


def update_nav_telegram(t: str) -> str:
    old = 'href="https://t.me/" target="_blank" rel="noopener" class="nav-link" data-testid="public-nav-telegram-link"'
    new = 'href="https://t.me/albview" target="_blank" rel="noopener noreferrer" class="nav-link" data-testid="public-nav-telegram-link"'
    return t.replace(old, new, 1)


def update_main_ejs(text: str) -> str:
    m = re.search(
        r"<div class=\"page\">\s*<% if \(typeof theme !== 'undefined' && theme === 'triangolo'\) \{ %>.*?<% \} else \{ %>",
        text,
        flags=re.S,
    )
    if not m:
        raise SystemExit("FAIL mainPage triangolo block")
    return text[: m.start()] + MAIN_EJS_TRI + text[m.end() :]


def main():
    t = SERVER.read_text(encoding="utf-8")
    t = insert_helper(t)
    t = gate_channel_url(t)
    t = gate_watch_routes(t)
    t = replace_style(t)
    t = update_nav_telegram(t)
    SERVER.write_text(t, encoding="utf-8")
    print("server.js patched")

    ejs = MAIN_EJS.read_text(encoding="utf-8")
    MAIN_EJS.write_text(update_main_ejs(ejs), encoding="utf-8")
    print("mainPage.ejs patched")

    r = subprocess.run(["node", "--check", str(SERVER)], capture_output=True, text=True)
    print("node check:", r.returncode, (r.stderr or "OK")[:500])
    if r.returncode != 0:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
