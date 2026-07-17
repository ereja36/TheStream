#!/usr/bin/env python3
"""Web-only fixes:
1) Prod API refresh: don't replace with tiny lists; async refresh (avoid CF 504);
   restore last good TV cache when current is tiny and API is unauthorized.
2) Channel thumbs: liquid glass + real logo (no red text-badge).
3) Channel names: full white text (wrap, no hard ellipsis clamp).
4) Ads: enable main banner; don't kill Adsterra slots before iframe fills.
"""
from __future__ import annotations

import json
import shutil
import subprocess
from datetime import datetime
from pathlib import Path

SERVER = Path("/root/all/all2/server.js")
CONFIG = Path("/root/all/all2/data/channel_config.json")
TV_CACHE = Path("/root/all/all2/data/api_tv_channels_cache.json")
TV_CACHE_BAK = Path("/root/all/all2/data.bak.20260710175852/api_tv_channels_cache.json")
SPORT_CACHE = Path("/root/all/all2/data/api_sport_channels_cache.json")
SPORT_CACHE_BAK = Path("/root/all/all2/data.bak.20260710175852/api_sport_channels_cache.json")

stamp = datetime.utcnow().strftime("%Y%m%d%H%M%S")
changed: list[str] = []


def backup(path: Path) -> None:
    if path.exists():
        shutil.copy2(path, path.with_suffix(path.suffix + f".bak-{stamp}"))


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        if new.strip()[:40] in text or label.split(":")[0] in "".join(changed):
            changed.append(f"{label}:skip")
            return text
        raise SystemExit(f"FAIL: {label}")
    changed.append(label)
    return text.replace(old, new, 1)


# ---------------------------------------------------------------------------
# Config + cache restore
# ---------------------------------------------------------------------------
backup(CONFIG)
cfg = json.loads(CONFIG.read_text(encoding="utf-8"))

web_ui = cfg.get("web_ui") if isinstance(cfg.get("web_ui"), dict) else {}
web_ui["text_badge_logos"] = False
cfg["web_ui"] = web_ui
changed.append("config:text_badge_logos=false")

ads = cfg.get("ad_snippets") if isinstance(cfg.get("ad_snippets"), dict) else {}
ads["show_banner_on_main"] = True
ads["show_banner_on_category"] = True
ads["enabled"] = True
ads["grid_native_enabled"] = True
if int(ads.get("grid_native_every") or 0) <= 0:
    ads["grid_native_every"] = 2
# Prefer multi banner so Adsterra units actually rotate on page
if ads.get("banner_multi_enabled") is False:
    ads["banner_multi_enabled"] = True
cfg["ad_snippets"] = ads
changed.append("config:ads-banner-main-on")

prod = cfg.get("prod_api") if isinstance(cfg.get("prod_api"), dict) else {}
try:
    pages = int(prod.get("total_pages") or 0)
except Exception:
    pages = 0
if pages < 42:
    prod["total_pages"] = 42
    changed.append("config:prod_api.total_pages=42")
try:
    cpp = int(prod.get("count_per_page") or 12)
except Exception:
    cpp = 12
if cpp > 12:
    prod["count_per_page"] = 12
    changed.append("config:prod_api.count_per_page=12")
cfg["prod_api"] = prod

CONFIG.write_text(json.dumps(cfg, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

# Restore fuller TV cache when current list is tiny (partial refresh / 403 wipe)
try:
    current = json.loads(TV_CACHE.read_text(encoding="utf-8")) if TV_CACHE.exists() else {}
    bak = json.loads(TV_CACHE_BAK.read_text(encoding="utf-8")) if TV_CACHE_BAK.exists() else {}
    cur_n = len(current) if isinstance(current, dict) else 0
    bak_n = len(bak) if isinstance(bak, dict) else 0
    if bak_n > max(80, cur_n * 2) and cur_n < 80:
        backup(TV_CACHE)
        TV_CACHE.write_text(json.dumps(bak, ensure_ascii=False), encoding="utf-8")
        cfg["prod_api"]["last_fetch_tv_count"] = bak_n
        cfg["prod_api"]["last_fetch_error"] = (
            "Restored previous TV cache (last refresh returned too few channels / API unauthorized)."
        )
        CONFIG.write_text(json.dumps(cfg, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        changed.append(f"cache:restore-tv {cur_n}->{bak_n}")
    if SPORT_CACHE_BAK.exists() and SPORT_CACHE.exists():
        sport_cur = json.loads(SPORT_CACHE.read_text(encoding="utf-8"))
        sport_bak = json.loads(SPORT_CACHE_BAK.read_text(encoding="utf-8"))
        sc = len(sport_cur) if isinstance(sport_cur, dict) else 0
        sb = len(sport_bak) if isinstance(sport_bak, dict) else 0
        if sb > max(20, sc * 2) and sc < 20:
            backup(SPORT_CACHE)
            SPORT_CACHE.write_text(json.dumps(sport_bak, ensure_ascii=False), encoding="utf-8")
            cfg["prod_api"]["last_fetch_sport_count"] = sb
            CONFIG.write_text(json.dumps(cfg, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
            changed.append(f"cache:restore-sport {sc}->{sb}")
except Exception as exc:
    changed.append(f"cache:restore-error:{exc}")


# ---------------------------------------------------------------------------
# server.js patches
# ---------------------------------------------------------------------------
backup(SERVER)
t = SERVER.read_text(encoding="utf-8")

# 1) shouldReplace — never accept catastrophic shrink even without explicit error
old_replace = """function shouldReplaceProdApiFetch(previousDb = {}, fetchedDb = {}, meta = {}) {
  const prevCount = Object.keys(previousDb || {}).length;
  const newCount = Object.keys(fetchedDb || {}).length;
  const error = String(meta.error || '').trim();
  if (newCount === 0) return !error || prevCount === 0;
  if (error && prevCount > 0 && newCount < Math.max(5, Math.floor(prevCount * 0.55))) {
    console.warn(`Prod API fetch incomplete (${newCount}/${prevCount}), keeping previous list${error ? `: ${error}` : ''}`);
    return false;
  }
  return true;
}"""
new_replace = """function shouldReplaceProdApiFetch(previousDb = {}, fetchedDb = {}, meta = {}) {
  const prevCount = Object.keys(previousDb || {}).length;
  const newCount = Object.keys(fetchedDb || {}).length;
  const error = String(meta.error || '').trim();
  if (newCount === 0) return !error || prevCount === 0;
  // Guard against partial CF/timeout refreshes that wipe hundreds of channels down to ~40.
  const minKeep = Math.max(80, Math.floor(prevCount * 0.55));
  if (prevCount >= 80 && newCount < minKeep) {
    console.warn(`Prod API fetch incomplete (${newCount}/${prevCount}), keeping previous list${error ? `: ${error}` : ''}`);
    return false;
  }
  if (error && prevCount > 0 && newCount < Math.max(5, Math.floor(prevCount * 0.55))) {
    console.warn(`Prod API fetch incomplete (${newCount}/${prevCount}), keeping previous list${error ? `: ${error}` : ''}`);
    return false;
  }
  return true;
}"""
t = replace_once(t, old_replace, new_replace, "shouldReplaceProdApiFetch")

# 2) Async Prod API refresh — avoid Cloudflare 504 on /admin/tools
old_refresh = """    if (action === 'refresh_prod_api_now') {
      const nextProdApiSettings = parseProdApiSettingsFromBody(req.body, getProdApiSettings());
      appConfig.prod_api = nextProdApiSettings;
      saveJSON(CONFIG_FILE, appConfig);
      scheduleProdApiAutoRefresh();
      appendAuditLog('refresh_prod_api_now', req, {
        total_pages: nextProdApiSettings.total_pages,
        api_base_url: nextProdApiSettings.api_base_url
      });
      await updateDatabases({ forceProdApiFetch: true });
      const latest = getProdApiSettings();
      const msg = latest.last_fetch_error
        ? `Prod API: TV ${latest.last_fetch_tv_count || 0}, Sport ${latest.last_fetch_sport_count || 0}. Gabim: ${latest.last_fetch_error}`
        : `Prod API u rifreskua: TV ${latest.last_fetch_tv_count || 0}, Sport ${latest.last_fetch_sport_count || 0} kanale.`;
      return res.redirect(`${BASE_PATH}/admin?success=${encodeURIComponent(msg)}`);
    }"""
new_refresh = """    if (action === 'refresh_prod_api_now') {
      const nextProdApiSettings = parseProdApiSettingsFromBody(req.body, getProdApiSettings());
      appConfig.prod_api = nextProdApiSettings;
      saveJSON(CONFIG_FILE, appConfig);
      scheduleProdApiAutoRefresh();
      appendAuditLog('refresh_prod_api_now', req, {
        total_pages: nextProdApiSettings.total_pages,
        api_base_url: nextProdApiSettings.api_base_url
      });
      // Run in background so Cloudflare does not 504 the admin tools page.
      setImmediate(() => {
        updateDatabases({ forceProdApiFetch: true }).catch((error) => {
          console.error('refresh_prod_api_now background failed', error);
        });
      });
      return res.redirect(`${BASE_PATH}/admin?success=${encodeURIComponent('Prod API refresh u nis në background. Kontrollo Tools pas 1-2 minutash për TV/Sport count.')}`);
    }"""
t = replace_once(t, old_refresh, new_refresh, "refresh_prod_api_async")

old_refresh_channels = """    if (action === 'refresh_channels') {
      appendAuditLog('refresh_channels', req, {});
      await updateDatabases({ forceProdApiFetch: true });
      return res.redirect(`${BASE_PATH}/admin?success=${encodeURIComponent('Kanalet u rifreskuan me sukses!')}`);
    }"""
new_refresh_channels = """    if (action === 'refresh_channels') {
      appendAuditLog('refresh_channels', req, {});
      setImmediate(() => {
        updateDatabases({ forceProdApiFetch: true }).catch((error) => {
          console.error('refresh_channels background failed', error);
        });
      });
      return res.redirect(`${BASE_PATH}/admin?success=${encodeURIComponent('Refresh u nis në background (shmang 504). Kontrollo pas 1-2 minutash.')}`);
    }"""
t = replace_once(t, old_refresh_channels, new_refresh_channels, "refresh_channels_async")

# 3) Liquid glass thumbs with real logos (ignore red text-badge mode for public cards)
old_thumb = """function buildChannelThumbMedia(ch = {}, typeIcon = 'tv') {
  const ui = getWebUiSettings();
  if (ui.text_badge_logos) {
    const label = getChannelTextLogoLabel(ch.name || '') || 'LIVE';
    return `<div class="ch-text-logo" aria-hidden="true"><span>${escapeHtml(label)}</span></div>`;
  }
  const logo = String(ch.logo || '').trim();
  const safeLogo = logo.replace(/"/g, '&quot;');
  if (safeLogo) {
    return `<img src="${safeLogo}" alt="${escapeHtmlAttr(ch.name || '')}" style="width:100%;height:100%;object-fit:contain;padding:10px;background:rgba(255,255,255,.03)" onerror="this.remove()">`;
  }
  return `<i class="fas fa-${typeIcon}"></i>`;
}"""
new_thumb = """function buildChannelThumbMedia(ch = {}, typeIcon = 'tv') {
  const name = String(ch.name || '').trim();
  const label = getChannelTextLogoLabel(name) || 'LIVE';
  let logoUrl = '';
  try {
    const resolved = tvLogoService && tvLogoService.resolveLogo
      ? tvLogoService.resolveLogo(name, { country: ch.koolCategory || ch.category || ch.category_name || 'AL' })
      : '';
    if (resolved) logoUrl = String(resolved).trim();
  } catch (_) {}
  if (!logoUrl) {
    const raw = String(ch.logo || '').trim();
    if (/^https?:\\/\\//i.test(raw)) {
      logoUrl = raw;
    } else if (raw && tvLogoService && tvLogoService.resolveLogo) {
      try {
        const fromFile = tvLogoService.resolveLogo(raw.replace(/\\.(png|jpg|jpeg|webp|svg)$/i, ''), { country: 'AL' });
        if (fromFile) logoUrl = String(fromFile).trim();
      } catch (_) {}
    }
  }
  const img = logoUrl
    ? `<img class="ch-glass-logo" src="${escapeHtmlAttr(logoUrl)}" alt="" loading="lazy" decoding="async" onerror="this.style.display='none'">`
    : `<i class="fas fa-${typeIcon} ch-glass-fallback"></i>`;
  return `<div class="ch-glass-thumb" aria-hidden="true">${img}<span class="ch-glass-name">${escapeHtml(label)}</span></div>`;
}"""
t = replace_once(t, old_thumb, new_thumb, "buildChannelThumbMedia-glass")

# 4) CSS — replace red text logos with liquid glass + full white names
old_css = """.ch-text-logo{
  width:78%;height:78%;max-width:118px;max-height:118px;
  display:flex;align-items:center;justify-content:center;text-align:center;
  background:#c8102e;border-radius:10px;padding:8px 6px;
  box-shadow:0 10px 22px rgba(0,0,0,.35), inset 0 1px 0 rgba(255,255,255,.18);
}
.ch-text-logo span{
  color:#fff;font-weight:800;font-size:.62rem;line-height:1.12;
  text-transform:uppercase;letter-spacing:.03em;word-break:break-word;
}
body.theme-triangolo .ch-text-logo{
  width:72%;height:72%;border-radius:12px;background:#c8102e;
}
body.theme-triangolo .ch-text-logo span{font-size:.58rem;letter-spacing:.04em}"""
new_css = """.ch-text-logo,.ch-glass-thumb{
  width:86%;height:86%;max-width:140px;max-height:140px;
  display:flex;flex-direction:column;align-items:center;justify-content:center;text-align:center;gap:6px;
  background:linear-gradient(145deg, rgba(255,255,255,.20), rgba(255,255,255,.05));
  border:1px solid rgba(255,255,255,.30);
  border-radius:16px;padding:10px 8px;
  box-shadow:0 10px 28px rgba(0,0,0,.30), inset 0 1px 0 rgba(255,255,255,.38);
  backdrop-filter:blur(14px);-webkit-backdrop-filter:blur(14px);
}
.ch-text-logo span,.ch-glass-name{
  color:#fff;font-weight:800;font-size:.58rem;line-height:1.15;
  text-transform:uppercase;letter-spacing:.03em;word-break:break-word;
  max-width:100%;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden;
}
.ch-glass-logo{width:52%;height:52%;max-width:72px;max-height:72px;object-fit:contain;filter:drop-shadow(0 2px 8px rgba(0,0,0,.35))}
.ch-glass-fallback{color:rgba(255,255,255,.88);font-size:1.35rem}
body.theme-triangolo .ch-text-logo,body.theme-triangolo .ch-glass-thumb{
  width:82%;height:82%;border-radius:16px;background:linear-gradient(145deg, rgba(255,255,255,.18), rgba(255,255,255,.04));
}
body.theme-triangolo .ch-text-logo span,body.theme-triangolo .ch-glass-name{font-size:.55rem;letter-spacing:.04em;color:#fff}
.ch-name{
  white-space:normal!important;overflow:visible!important;text-overflow:unset!important;
  display:block!important;max-width:100%!important;color:#fff!important;
  line-height:1.25;word-break:break-word;
}
body.theme-triangolo .ch-name{color:#fff!important;white-space:normal!important;text-transform:none;font-size:.72rem;font-weight:700}
.ch-name-marquee{overflow:visible!important;white-space:normal!important}
.ch-name-marquee .ch-name{animation:none!important;max-width:100%!important;overflow:visible!important;text-overflow:unset!important}"""
t = replace_once(t, old_css, new_css, "css-glass-fullnames")

# Also force triangolo .ch-name override that was clamping tiny uppercase ellipsis
old_tri_name = """body.theme-triangolo .ch-name{font-size:.68rem;font-weight:700;text-transform:uppercase;letter-spacing:.02em;color:#f7f8ff}"""
new_tri_name = """body.theme-triangolo .ch-name{font-size:.72rem;font-weight:700;text-transform:none;letter-spacing:.01em;color:#fff;white-space:normal;overflow:visible;text-overflow:unset;line-height:1.25;word-break:break-word}"""
if old_tri_name in t:
    t = replace_once(t, old_tri_name, new_tri_name, "css-triangolo-ch-name")
else:
    changed.append("css-triangolo-ch-name:skip")

# 5) Ads — don't kill Adsterra slots before iframe arrives
old_scrub = """function scrubNativeAdJunk(root){
  if(!root) return false;
  const walker=document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);
  const drop=[];
  while(walker.nextNode()){
    const node=walker.currentNode;
    if(isAdsterraJunkText(node.textContent||'')) drop.push(node);
  }
  drop.forEach(node=>{ try{ node.textContent=''; }catch(e){} });
  Array.from(root.querySelectorAll('div,span,p,pre,code,font')).forEach(el=>{
    if(el.querySelector('iframe,img,video,ins,script,a')) return;
    const txt=collapseAdText(el.textContent||'');
    if(!txt || isAdsterraJunkText(txt)){
      try{ el.remove(); }catch(e){ el.innerHTML=''; }
    }
  });
  const hasMedia=!!root.querySelector('iframe,img,video,ins,a');
  const remain=collapseAdText(root.textContent||'');
  if(!hasMedia && (!remain || isAdsterraJunkText(remain))){
    root.innerHTML='';
    return false;
  }
  return hasMedia || (remain.length>0 && !isAdsterraJunkText(remain));
}
function finalizeNativeAdSlot(node){
  if(!node) return;
  const ok=scrubNativeAdJunk(node);
  if(ok) return;
  const card=node.closest('.native-ad-card, .category-ad-slot, .ad-banner-row, .ad-banner-slot');
  if(card){
    if(card.classList.contains('ad-banner-slot')){
      card.style.display='none';
    }else if(card.classList.contains('ad-banner-row')){
      card.style.display='none';
      const slot=card.closest('.ad-banner-slot');
      if(slot && !slot.querySelector('iframe,img,video,ins')) slot.style.display='none';
    }else{
      card.style.display='none';
      card.setAttribute('data-ad-empty','1');
    }
  }else{
    node.style.display='none';
  }
}"""
new_scrub = """function scrubNativeAdJunk(root){
  if(!root) return false;
  const walker=document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);
  const drop=[];
  while(walker.nextNode()){
    const node=walker.currentNode;
    if(isAdsterraJunkText(node.textContent||'')) drop.push(node);
  }
  drop.forEach(node=>{ try{ node.textContent=''; }catch(e){} });
  Array.from(root.querySelectorAll('div,span,p,pre,code,font')).forEach(el=>{
    if(el.querySelector('iframe,img,video,ins,script,a,[id^="container-"]')) return;
    const txt=collapseAdText(el.textContent||'');
    if(!txt || isAdsterraJunkText(txt)){
      try{ el.remove(); }catch(e){ el.innerHTML=''; }
    }
  });
  // Treat Adsterra container as pending media so we do not wipe before iframe fills.
  const hasMedia=!!root.querySelector('iframe,img,video,ins,a,[id^="container-"]');
  const remain=collapseAdText(root.textContent||'');
  if(!hasMedia && (!remain || isAdsterraJunkText(remain))){
    return false;
  }
  return hasMedia || (remain.length>0 && !isAdsterraJunkText(remain));
}
function finalizeNativeAdSlot(node){
  if(!node) return;
  // Never finalize slots that have not started, or that are still waiting for Adsterra.
  if(node.dataset && node.dataset.adSnippet && node.dataset.rendered!=='1') return;
  if(node.dataset && node.dataset.rendered==='1'){
    const started=parseInt(node.dataset.adRenderedAt||'0',10);
    const age=Date.now()-(Number.isFinite(started)?started:0);
    if(age < 8000){
      // Keep pending container visible while network fills.
      if(node.querySelector('[id^="container-"],script,iframe,img,video,ins,a')) return;
    }
  }
  const ok=scrubNativeAdJunk(node);
  if(ok) return;
  const card=node.closest('.native-ad-card, .category-ad-slot, .ad-banner-row, .ad-banner-slot');
  if(card){
    if(card.classList.contains('ad-banner-slot')){
      card.style.display='none';
    }else if(card.classList.contains('ad-banner-row')){
      card.style.display='none';
      const slot=card.closest('.ad-banner-slot');
      if(slot && !slot.querySelector('iframe,img,video,ins,[id^="container-"]')) slot.style.display='none';
    }else{
      card.style.display='none';
      card.setAttribute('data-ad-empty','1');
    }
  }else{
    node.style.display='none';
  }
}"""
t = replace_once(t, old_scrub, new_scrub, "ads-finalize-patience")

old_native_render = """    node.dataset.rendered='1';
    try{ await executeHtmlSnippet(node, html); }catch(e){}
    // Hide Adsterra empty-fill junk text (frame_width / go_banner)
    try{ scrubNativeAdJunk(node); }catch(e){}
    setTimeout(function(){ try{ finalizeNativeAdSlot(node); }catch(e){} }, 900);
    setTimeout(function(){ try{ finalizeNativeAdSlot(node); }catch(e){} }, 2500);"""
new_native_render = """    node.dataset.rendered='1';
    node.dataset.adRenderedAt=String(Date.now());
    try{ await executeHtmlSnippet(node, html); }catch(e){}
    // Hide Adsterra empty-fill junk text (frame_width / go_banner)
    try{ scrubNativeAdJunk(node); }catch(e){}
    setTimeout(function(){ try{ finalizeNativeAdSlot(node); }catch(e){} }, 8500);
    setTimeout(function(){ try{ finalizeNativeAdSlot(node); }catch(e){} }, 14000);"""
t = replace_once(t, old_native_render, new_native_render, "ads-native-render-delay")

# Delay global scrub so sequential native render can finish
old_observer = """function startAdJunkObserver(){
  if(window.__adJunkObserverStarted) return;
  window.__adJunkObserverStarted=true;
  // IMPORTANT: do NOT use MutationObserver here — scrubbing mutates DOM and
  // re-triggers the observer forever, freezing the page so channel clicks die.
  scrubAllAdContainers();
  setTimeout(scrubAllAdContainers, 600);
  setTimeout(scrubAllAdContainers, 1600);
  setTimeout(scrubAllAdContainers, 3500);
  setTimeout(scrubAllAdContainers, 7000);
}"""
new_observer = """function startAdJunkObserver(){
  if(window.__adJunkObserverStarted) return;
  window.__adJunkObserverStarted=true;
  // IMPORTANT: do NOT use MutationObserver here — scrubbing mutates DOM and
  // re-triggers the observer forever, freezing the page so channel clicks die.
  // Delay first scrub so Adsterra native/banner containers have time to fill.
  setTimeout(scrubAllAdContainers, 5000);
  setTimeout(scrubAllAdContainers, 9000);
  setTimeout(scrubAllAdContainers, 14000);
  setTimeout(scrubAllAdContainers, 20000);
}"""
t = replace_once(t, old_observer, new_observer, "ads-observer-delay")

# Banner scrub timeouts after render
old_banner_scrub = """  try{ scrubAllAdContainers(); }catch(e){}
  setTimeout(function(){ try{ scrubAllAdContainers(); }catch(e){} }, 900);
  setTimeout(function(){ try{ scrubAllAdContainers(); }catch(e){} }, 2500);
}"""
new_banner_scrub = """  try{ /* wait for Adsterra fill before scrub */ }catch(e){}
  setTimeout(function(){ try{ scrubAllAdContainers(); }catch(e){} }, 8000);
  setTimeout(function(){ try{ scrubAllAdContainers(); }catch(e){} }, 14000);
}"""
# Only replace the occurrence inside renderInlineBannerAd — find unique context
banner_ctx_old = """  markAdExposure('banner');
  adaptBannerSlotSize(slot, inner);
  slot.style.display='block';
  try{ scrubAllAdContainers(); }catch(e){}
  setTimeout(function(){ try{ scrubAllAdContainers(); }catch(e){} }, 900);
  setTimeout(function(){ try{ scrubAllAdContainers(); }catch(e){} }, 2500);
}"""
banner_ctx_new = """  markAdExposure('banner');
  adaptBannerSlotSize(slot, inner);
  slot.style.display='block';
  setTimeout(function(){ try{ scrubAllAdContainers(); }catch(e){} }, 8000);
  setTimeout(function(){ try{ scrubAllAdContainers(); }catch(e){} }, 14000);
}"""
t = replace_once(t, banner_ctx_old, banner_ctx_new, "ads-banner-scrub-delay")

SERVER.write_text(t, encoding="utf-8")

# Syntax check + reload
subprocess.check_call(["node", "--check", str(SERVER)])
subprocess.check_call(["pm2", "reload", "all2", "--update-env"])

print("OK", ", ".join(changed))
