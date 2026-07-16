#!/usr/bin/env python3
"""Web IPTV fixes: ads (smartlink+native only), API direct headers, A3 VOD mp4,
select-all scoped to source, admin player status text, no-ads enforcement,
switchable Triangolo-like theme.
"""
from __future__ import annotations

import json
import shutil
import time
from pathlib import Path

SERVER = Path("/root/all/all2/server.js")
CONFIG = Path("/root/all/all2/data/channel_config.json")
LAYOUT = Path("/root/all/all2/views/public/layout.ejs")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"FAIL {label}: expected 1 occurrence, found {count}")
    return text.replace(old, new, 1)


def replace_all(text: str, old: str, new: str, label: str, min_count: int = 1) -> str:
    count = text.count(old)
    if count < min_count:
        raise SystemExit(f"FAIL {label}: expected >= {min_count}, found {count}")
    return text.replace(old, new)


def main() -> None:
    if not SERVER.exists():
        raise SystemExit(f"Missing {SERVER}")
    stamp = time.strftime("%Y%m%d-%H%M%S")
    bak = SERVER.with_suffix(f".js.bak.web-fix-{stamp}")
    shutil.copy2(SERVER, bak)
    print(f"Backup: {bak}")
    text = SERVER.read_text(encoding="utf-8")

    # ── 1) Direct/redirect for header-required sources (prod API etc.) ─────────
    old_direct = """async function deliverDirectChannelStream(req, res, slug, channel) {
  const upstreamProfile = buildChannelUpstreamProfile(channel, slug);
  let directTarget = String(channel.url || '').trim();
  let resolvedDirectManifest = false;
  if (customChannelUsesDirectResolve(channel) || channelHasUpstreamProfile(channel)) {
    const resolved = await resolveDirectManifestUrl(directTarget, req, upstreamProfile);
    if (resolved) {
      directTarget = resolved;
      resolvedDirectManifest = true;
    }
  }
  // Lokke-style: 302 to source CDN. Never probe from VPS (DC IPs fail → wrong proxy fallback).
  // Segments then go client ↔ CDN; VPS only issues the signed redirect.
  const directCandidates = resolveFailoverCandidates(slug, directTarget);
  const redirectUrl = String((directCandidates && directCandidates[0]) || directTarget || '').trim();
  if (!/^https?:\\/\\//i.test(redirectUrl)) {
    return res.status(502).send('Direct stream URL unavailable');
  }
  // XUI: hide /play/ — resolve to /auth/ on VPS, 302 client to CDN token (no segment proxy).
  let finalRedirect = redirectUrl;
  if (isXuiDirectPlayUrl(redirectUrl)) {
    const authUrl = await resolveXuiPlayToAuthUrl(redirectUrl);
    if (!authUrl) return res.status(502).send('XUI auth token unavailable');
    finalRedirect = markXuiClientCdnUrlForApp(authUrl);
    res.setHeader('X-Direct-Playback', 'xui-auth-redirect');
  } else {
    res.setHeader('X-Direct-Playback', 'hard-redirect');
  }
  res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, max-age=0');
  if (resolvedDirectManifest) res.setHeader('X-Direct-Manifest-Resolve', '1');
  return res.redirect(302, finalRedirect);
}"""

    new_direct = """async function deliverDirectChannelStream(req, res, slug, channel) {
  const sourceKey = getChannelSourceKey(channel);
  // Browsers cannot set User-Agent / Install-Token on cross-origin HLS fetches.
  // For prod API (and any channel with upstream headers), "direct" still must
  // inject those headers server-side so playback works; upstream traffic stays
  // on the CDN/API host via the existing segment proxy path.
  if (
    sourceKey === 'api_tv'
    || sourceKey === 'api_sport'
    || channelHasUpstreamProfile(channel)
  ) {
    res.setHeader('X-Direct-Playback', 'header-proxy');
    res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, max-age=0');
    await proxyChannelWithOptionalResolve(req, res, slug, channel, 'direct-header');
    return;
  }
  const upstreamProfile = buildChannelUpstreamProfile(channel, slug);
  let directTarget = String(channel.url || '').trim();
  let resolvedDirectManifest = false;
  if (customChannelUsesDirectResolve(channel) || channelHasUpstreamProfile(channel)) {
    const resolved = await resolveDirectManifestUrl(directTarget, req, upstreamProfile);
    if (resolved) {
      directTarget = resolved;
      resolvedDirectManifest = true;
    }
  }
  // Lokke-style: 302 to source CDN. Never probe from VPS (DC IPs fail → wrong proxy fallback).
  // Segments then go client ↔ CDN; VPS only issues the signed redirect.
  const directCandidates = resolveFailoverCandidates(slug, directTarget);
  const redirectUrl = String((directCandidates && directCandidates[0]) || directTarget || '').trim();
  if (!/^https?:\\/\\//i.test(redirectUrl)) {
    return res.status(502).send('Direct stream URL unavailable');
  }
  // XUI: hide /play/ — resolve to /auth/ on VPS, 302 client to CDN token (no segment proxy).
  let finalRedirect = redirectUrl;
  if (isXuiDirectPlayUrl(redirectUrl)) {
    const authUrl = await resolveXuiPlayToAuthUrl(redirectUrl);
    if (!authUrl) return res.status(502).send('XUI auth token unavailable');
    finalRedirect = markXuiClientCdnUrlForApp(authUrl);
    res.setHeader('X-Direct-Playback', 'xui-auth-redirect');
  } else {
    res.setHeader('X-Direct-Playback', 'hard-redirect');
  }
  res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, max-age=0');
  if (resolvedDirectManifest) res.setHeader('X-Direct-Manifest-Resolve', '1');
  return res.redirect(302, finalRedirect);
}"""
    text = replace_once(text, old_direct, new_direct, "deliverDirectChannelStream")

    # Always header-proxy api channels on playlist routes (even if UA missing briefly)
    old_needs = """  const needsPlaylistHeaderProxy = !!req.playlistDelivery
    && (sourceKey === 'api_tv' || sourceKey === 'api_sport')
    && !!String(profile.userAgent || '').trim();
  if (needsPlaylistHeaderProxy) {
    res.setHeader('X-Playlist-Proxy', 'api-headers');
    await proxyChannelWithOptionalResolve(req, res, slug, channel, 'playlist-api-proxy');
    return;
  }"""
    new_needs = """  const needsPlaylistHeaderProxy = !!req.playlistDelivery
    && (sourceKey === 'api_tv' || sourceKey === 'api_sport');
  if (needsPlaylistHeaderProxy) {
    res.setHeader('X-Playlist-Proxy', 'api-headers');
    await proxyChannelWithOptionalResolve(req, res, slug, channel, 'playlist-api-proxy');
    return;
  }"""
    text = replace_once(text, old_needs, new_needs, "needsPlaylistHeaderProxy")

    # ── 2) A3 VOD → mp4 (no a3hls remux) ───────────────────────────────────────
    old_a3opts = """  return {
    headers,
    userAgent: String(channel?.albUserAgent || ALB_DEFAULT_UPSTREAM_UA || '').trim() || ALB_DEFAULT_UPSTREAM_UA,
    preferDirect: fastly && fromApp,
    preferClientSw: false,
    // Chrome/web: remux via /a3hls for HLS.js (stable). Raw mpegts relay kept as fallback.
    preferFastlyRelay: fastly && !fromApp,
    preferHlsRemux: fastly && !fromApp,
    webBlocked: false
  };
}"""
    new_a3opts = """  const sourceKey = String(channel?.sourceKey || channel?.source || '').trim().toLowerCase();
  const isVod = sourceKey === 'alb_vod'
    || String(channel?.type || '').trim().toLowerCase() === 'vod'
    || /\\.mp4(\\?|$)/i.test(String(channel?.url || ''));
  return {
    headers,
    userAgent: String(channel?.albUserAgent || ALB_DEFAULT_UPSTREAM_UA || '').trim() || ALB_DEFAULT_UPSTREAM_UA,
    // VOD is progressive MP4 — never remux to m3u8/a3hls.
    preferDirect: (fastly && fromApp) || isVod,
    preferClientSw: false,
    // Chrome/web live: remux via /a3hls for HLS.js. VOD stays direct MP4.
    preferFastlyRelay: fastly && !fromApp && !isVod,
    preferHlsRemux: fastly && !fromApp && !isVod,
    webBlocked: false
  };
}"""
    text = replace_once(text, old_a3opts, new_a3opts, "buildA3PlaybackOptionsFromChannel")

    # pickEngine: prefer browser for mp4
    old_pick = """function pickEngine(canNative, playable){
  const hlsSupported=!!(window.Hls&&Hls.isSupported&&Hls.isSupported());
  const mpegtsSupported=!!(window.mpegts&&mpegts.getFeatureList&&mpegts.getFeatureList().mseLivePlayback);
  const sourceRules=sourcePlayerDefaults[currentSourceKey]||{};
  let sourceRequested=(isIPhone?sourceRules.iphone:(isAndroid?sourceRules.android:sourceRules.other)||'auto').toLowerCase();
  let globalRequested=(isIPhone?playerDefaults.iphone:(isAndroid?playerDefaults.android:playerDefaults.other)||'auto').toLowerCase();
  // Legacy engines collapse to browser/hls.
  const mapLegacy=(e)=>({native:'browser',shaka:'hls',clappr:'hls',playerjs:'hls'}[e]||e);
  sourceRequested=mapLegacy(sourceRequested);
  globalRequested=mapLegacy(globalRequested);
  const requested=(sourceRequested&&sourceRequested!=='auto')?sourceRequested:globalRequested;
  // A3 fastly via a3hls → HLS.js; raw a3relay → mpegts.js.
  if(isA3HlsPlayUrl((playable&&playable.url)||currentUrl, playable)){"""
    new_pick = """function pickEngine(canNative, playable){
  const hlsSupported=!!(window.Hls&&Hls.isSupported&&Hls.isSupported());
  const mpegtsSupported=!!(window.mpegts&&mpegts.getFeatureList&&mpegts.getFeatureList().mseLivePlayback);
  const sourceRules=sourcePlayerDefaults[currentSourceKey]||{};
  let sourceRequested=(isIPhone?sourceRules.iphone:(isAndroid?sourceRules.android:sourceRules.other)||'auto').toLowerCase();
  let globalRequested=(isIPhone?playerDefaults.iphone:(isAndroid?playerDefaults.android:playerDefaults.other)||'auto').toLowerCase();
  // Legacy engines collapse to browser/hls.
  const mapLegacy=(e)=>({native:'browser',shaka:'hls',clappr:'hls',playerjs:'hls'}[e]||e);
  sourceRequested=mapLegacy(sourceRequested);
  globalRequested=mapLegacy(globalRequested);
  const requested=(sourceRequested&&sourceRequested!=='auto')?sourceRequested:globalRequested;
  const probeUrl=String((playable&&(playable.directUrl||playable.url))||currentUrl||'');
  if(/\\.mp4(\\?|$)/i.test(probeUrl) || currentSourceKey==='alb_vod'){
    return 'browser';
  }
  // A3 fastly via a3hls → HLS.js; raw a3relay → mpegts.js.
  if(isA3HlsPlayUrl((playable&&playable.url)||currentUrl, playable)){"""
    text = replace_once(text, old_pick, new_pick, "pickEngine mp4")

    # ── 3) Select all / none scoped to current source filter ───────────────────
    text = replace_once(
        text,
        """function selectAll(v){
  document.querySelectorAll('input[name="category_channels"]').forEach(c=>c.checked=v);
  updateSelectedCount();
  syncSelectedOrderFromChecks();
}
function filterChannelsCat(q){""",
        """function selectAll(v){
  const sourceSel=(document.getElementById('sourceFilterCat')||{}).value||'all';
  document.querySelectorAll('input[name="category_channels"]').forEach(c=>{
    const item=c.closest('.channel-checkbox-item');
    if(!item) return;
    if(sourceSel!=='all' && item.dataset.source!==sourceSel) return;
    if(item.style.display==='none') return;
    c.checked=v;
  });
  updateSelectedCount();
  syncSelectedOrderFromChecks();
}
function filterChannelsCat(q){""",
        "selectAll category (cat page)",
    )

    # Second category selectAll (compact one-liner variant)
    text = replace_once(
        text,
        """function selectAll(v){document.querySelectorAll('input[name="category_channels"]').forEach(c=>c.checked=v);updateSelectedCount();syncSelectedOrderFromChecks();}""",
        """function selectAll(v){
  const sourceSel=(document.getElementById('sourceFilterCat')||{}).value||'all';
  document.querySelectorAll('input[name="category_channels"]').forEach(c=>{
    const item=c.closest('.channel-checkbox-item');
    if(!item) return;
    if(sourceSel!=='all' && item.dataset.source!==sourceSel) return;
    if(item.style.display==='none') return;
    c.checked=v;
  });
  updateSelectedCount();
  syncSelectedOrderFromChecks();
}""",
        "selectAll category compact",
    )

    text = replace_once(
        text,
        """function selectAll(s){
  document.querySelectorAll('input[id^="pc_"]').forEach(c=>c.checked=s);
  if(playlistOrderCreateApi) playlistOrderCreateApi.syncSelectedOrderFromChecks();
}
function selectAllEdit(s){
  document.querySelectorAll('input[id^="epc_"]').forEach(c=>c.checked=s);
  if(playlistOrderEditApi) playlistOrderEditApi.syncSelectedOrderFromChecks();
}""",
        """function selectAll(s){
  const sourceSel=(document.getElementById('sourceFilterPlCreate')||{}).value||'all';
  document.querySelectorAll('#playlistCreateForm input[id^="pc_"], input[id^="pc_"]').forEach(c=>{
    const item=c.closest('.channel-checkbox-item');
    if(!item) return;
    if(sourceSel!=='all' && item.dataset.source!==sourceSel) return;
    if(item.style.display==='none') return;
    c.checked=s;
  });
  if(playlistOrderCreateApi) playlistOrderCreateApi.syncSelectedOrderFromChecks();
}
function selectAllEdit(s){
  const sourceSel=(document.getElementById('sourceFilterPlEdit')||{}).value||'all';
  document.querySelectorAll('#playlistEditForm input[id^="epc_"], input[id^="epc_"]').forEach(c=>{
    const item=c.closest('.channel-checkbox-item');
    if(!item) return;
    if(sourceSel!=='all' && item.dataset.source!==sourceSel) return;
    if(item.style.display==='none') return;
    c.checked=s;
  });
  if(playlistOrderEditApi) playlistOrderEditApi.syncSelectedOrderFromChecks();
}""",
        "selectAll playlist create/edit",
    )

    text = replace_once(
        text,
        """function selectAllManage(state){
  document.querySelectorAll('input[id^="mpc_"]').forEach(el=>{ el.checked=state; });
  if(playlistOrderManageApi) playlistOrderManageApi.syncSelectedOrderFromChecks();
}""",
        """function selectAllManage(state){
  const sourceSel=(document.getElementById('sourceFilterPlManage')||{}).value||'all';
  document.querySelectorAll('#playlistManageForm input[id^="mpc_"], input[id^="mpc_"]').forEach(el=>{
    const item=el.closest('.channel-checkbox-item');
    if(!item) return;
    if(sourceSel!=='all' && item.dataset.source!==sourceSel) return;
    if(item.style.display==='none') return;
    el.checked=state;
  });
  if(playlistOrderManageApi) playlistOrderManageApi.syncSelectedOrderFromChecks();
}""",
        "selectAllManage",
    )

    # ── 4) Player status text (admin-configurable) + quieter HLS messages ──────
    # Inject webUi config into playerScript near adSnippetsConfig
    old_adsn_cfg = "const adSnippetsConfig=(pageOptions&&pageOptions.adSnippets&&typeof pageOptions.adSnippets==='object')?pageOptions.adSnippets:"
    if text.count(old_adsn_cfg) != 1:
        raise SystemExit(f"FAIL adSnippetsConfig inject marker: {text.count(old_adsn_cfg)}")
    text = text.replace(
        old_adsn_cfg,
        "const webUiConfig=(pageOptions&&pageOptions.webUi&&typeof pageOptions.webUi==='object')?pageOptions.webUi:{theme:'classic',playerStatusText:''};\n"
        + old_adsn_cfg,
        1,
    )

    old_healthy = "setStatus('ok','Online - Duke transmetuar ('+engineLabel(currentEngine)+')');"
    new_healthy = (
        "const customStatus=String(webUiConfig.playerStatusText||'').trim();\n"
        "    setStatus('ok', customStatus || ('Online'));"
    )
    text = replace_once(text, old_healthy, new_healthy, "healthy status")

    text = replace_once(
        text,
        "setStatus('ok','Po luhet (HLS.js)');",
        "setStatus('ok', String(webUiConfig.playerStatusText||'').trim() || 'Online');",
        "hls playing status",
    )

    text = replace_all(
        text,
        "if(currentMode!=='proxy') setStatus('warn','Duke bufferuar live...');",
        "if(currentMode!=='proxy'){ const t=String(webUiConfig.playerStatusText||'').trim(); if(t) setStatus('ok', t); }",
        "buffering status",
        min_count=2,
    )

    # ── 5) No-ads: clear ads when profile says no-ads (incl reward free) ────────
    old_apply = """function applyAccountProfile(profile){
  accountProfile=(profile&&typeof profile==='object')?profile:null;
  const resolvedTier=accountProfile
    ? normalizeAdTier(String(accountProfile.ad_tier || 'normal').trim())
    : 'normal';
  setAdUserTier(resolvedTier);
  if(resolvedTier==='premium'){
    hideInlineBannerAd();
    hideInterstitialLayer();
    hidePreRollLayer();
    hideChannelOpenGateLayer();
    renderNativeGridAds();
  }
  updateAccountButton();
  renderContinueWatchingFromAccount();
}"""
    new_apply = """function applyAccountProfile(profile){
  accountProfile=(profile&&typeof profile==='object')?profile:null;
  const resolvedTier=accountProfile
    ? normalizeAdTier(String(accountProfile.ad_tier || 'normal').trim())
    : 'normal';
  setAdUserTier(resolvedTier);
  if(isNoAdsUser()){
    hideInlineBannerAd();
    hideInterstitialLayer();
    hidePreRollLayer();
    hideChannelOpenGateLayer();
    try{ cancelChannelOpenGate(); }catch{}
    renderNativeGridAds();
  }
  updateAccountButton();
  renderContinueWatchingFromAccount();
}"""
    text = replace_once(text, old_apply, new_apply, "applyAccountProfile no-ads")

    # Harden isNoAdsUser to also honor ad_tier_override on profile
    old_noads = """function isNoAdsUser(){
  if(accountProfile && typeof accountProfile==='object'){
    const accountTier=normalizeAdTier(String(accountProfile.ad_tier||'').trim());
    if(accountTier==='premium') return true;
    if(accountProfile.premium_active===true) return true;
    const rewardUntil=Number(accountProfile.reward_ads_free_until_ts||0);
    if(Number.isFinite(rewardUntil)&&rewardUntil>Date.now()) return true;
  }
  return getAdUserTier()==='premium';
}"""
    new_noads = """function isNoAdsUser(){
  if(accountProfile && typeof accountProfile==='object'){
    const override=normalizeAdTier(String(accountProfile.ad_tier_override||'').trim());
    if(override==='premium') return true;
    if(override==='normal') return false;
    const accountTier=normalizeAdTier(String(accountProfile.ad_tier||'').trim());
    if(accountTier==='premium') return true;
    if(accountProfile.premium_active===true) return true;
    const rewardUntil=Number(accountProfile.reward_ads_free_until_ts||0);
    if(Number.isFinite(rewardUntil)&&rewardUntil>Date.now()) return true;
  }
  return getAdUserTier()==='premium';
}"""
    text = replace_once(text, old_noads, new_noads, "isNoAdsUser")

    # ── 6) Web UI theme + player status config helpers + admin features form ───
    # Insert normalizeWebUiConfig near normalizeFeatureFlags usage boot
    marker = "appConfig.feature_flags = normalizeFeatureFlags(appConfig.feature_flags);"
    helper = """appConfig.feature_flags = normalizeFeatureFlags(appConfig.feature_flags);
appConfig.web_ui = normalizeWebUiConfig(appConfig.web_ui);
"""
    # Only replace the boot-time assignment (first late occurrence around 7828)
    # There may be multiple - find the one that is alone on a line near boot
    boot_marker = """appConfig.feature_flags = normalizeFeatureFlags(appConfig.feature_flags);
const looksLikeLegacyFeatureDefaults"""
    if text.count(boot_marker) == 1:
        text = text.replace(
            boot_marker,
            """appConfig.feature_flags = normalizeFeatureFlags(appConfig.feature_flags);
appConfig.web_ui = normalizeWebUiConfig(appConfig.web_ui);
const looksLikeLegacyFeatureDefaults""",
            1,
        )
    else:
        # fallback: insert function + first assignment after feature_flags boot line once
        idx = text.find(marker)
        if idx < 0:
            raise SystemExit("FAIL web_ui boot marker")
        # find second occurrence (boot) - skip getFeatureFlags
        idx2 = text.find(marker, idx + 1)
        if idx2 < 0:
            idx2 = idx
        text = text[: idx2 + len(marker)] + "\nappConfig.web_ui = normalizeWebUiConfig(appConfig.web_ui);" + text[idx2 + len(marker) :]

    # Add normalizeWebUiConfig + getWebUiSettings before getFeatureFlags
    old_ff = """function getFeatureFlags() {
  appConfig.feature_flags = normalizeFeatureFlags((appConfig && appConfig.feature_flags) || {});
  return appConfig.feature_flags;
}"""
    new_ff = """function getFeatureFlags() {
  appConfig.feature_flags = normalizeFeatureFlags((appConfig && appConfig.feature_flags) || {});
  return appConfig.feature_flags;
}
function normalizeWebUiConfig(raw = {}) {
  const source = raw && typeof raw === 'object' ? raw : {};
  const themeRaw = String(source.theme || source.web_theme || source.site_theme || 'classic').trim().toLowerCase();
  const theme = themeRaw === 'triangolo' || themeRaw === 'triangolo_media' || themeRaw === 'alt' ? 'triangolo' : 'classic';
  const playerStatusText = clampText(
    source.player_status_text ?? source.playerStatusText ?? source.player_footer_text ?? '',
    200
  );
  return { theme, player_status_text: playerStatusText };
}
function getWebUiSettings() {
  appConfig.web_ui = normalizeWebUiConfig((appConfig && appConfig.web_ui) || {});
  return appConfig.web_ui;
}
function getPublicBaseStyle() {
  const ui = getWebUiSettings();
  const triangoloCss = `
body.theme-triangolo{
  --bg:#07090f;--bg2:#0b0f18;--surface:#101522;--card:#141b2a;--border:rgba(255,255,255,.08);
  --accent:#3ddc97;--accent-2:#2bb673;--text:#f3f6fb;--muted:#9aa6bc;
  background:#07090f;
  font-family:'Work Sans',system-ui,sans-serif;
}
body.theme-triangolo nav{
  background:rgba(7,9,15,.92);border-bottom:1px solid rgba(255,255,255,.06);min-height:58px;padding:6px 18px;
}
body.theme-triangolo .nav-logo{font-size:1.1rem;letter-spacing:.02em}
body.theme-triangolo .nav-link,body.theme-triangolo .nav-link-cta{
  border:0;background:transparent;border-radius:8px;padding:8px 12px;font-size:.8rem;color:var(--muted);
}
body.theme-triangolo .nav-link:hover,body.theme-triangolo .nav-link.active,body.theme-triangolo .nav-link-cta{
  color:#fff;background:rgba(61,220,151,.12);
}
body.theme-triangolo .main-hero{display:none}
body.theme-triangolo .page{max-width:1280px;margin:0 auto;padding:18px 16px 40px}
body.theme-triangolo .admin-bar{
  background:transparent;border:0;padding:4px 0 14px;color:var(--muted);font-size:.72rem;gap:14px;
}
body.theme-triangolo .search-bar{
  background:rgba(20,27,42,.9);border:1px solid rgba(255,255,255,.08);border-radius:12px;margin-bottom:18px;
}
body.theme-triangolo .channels-grid{
  display:grid;grid-template-columns:repeat(auto-fill,minmax(132px,1fr));gap:14px;
}
body.theme-triangolo .ch-card{
  background:linear-gradient(180deg,#161e2e,#101624);border:1px solid rgba(255,255,255,.07);
  border-radius:14px;padding:14px 10px 12px;text-align:center;min-height:148px;
  box-shadow:0 10px 28px rgba(0,0,0,.28);
}
body.theme-triangolo .ch-card:hover{border-color:rgba(61,220,151,.45);transform:translateY(-4px)}
body.theme-triangolo .ch-logo,body.theme-triangolo .ch-card img{
  width:64px;height:64px;object-fit:contain;margin:0 auto 10px;border-radius:12px;background:rgba(255,255,255,.04);
}
body.theme-triangolo .ch-name{font-size:.78rem;font-weight:600;line-height:1.25;color:#f5f7fb}
body.theme-triangolo .cat-grid{grid-template-columns:repeat(auto-fill,minmax(160px,1fr));gap:12px}
body.theme-triangolo .cat-card{
  background:#141b2a;border:1px solid rgba(255,255,255,.07);border-radius:14px;padding:14px;
}
body.theme-triangolo .section-shell{background:transparent;border:0;padding:0;margin-bottom:22px}
body.theme-triangolo .section-head h2{font-size:1rem;font-weight:700}
body.theme-triangolo .info-footer{color:var(--muted);text-align:center;padding:28px 0 10px;font-size:.72rem}
body.theme-triangolo #playerModal .player-box,body.theme-triangolo .player-box{
  border-radius:16px;border:1px solid rgba(255,255,255,.1);overflow:hidden;background:#000;
}
`;
  return String(typeof BASE_STYLE === 'string' ? BASE_STYLE : '') + (ui.theme === 'triangolo' ? triangoloCss + '</style>'.replace('</style>', '') : '');
}
"""
    # Fix getPublicBaseStyle - the triangolo CSS injection above is messy. Use cleaner version.
    new_ff = """function getFeatureFlags() {
  appConfig.feature_flags = normalizeFeatureFlags((appConfig && appConfig.feature_flags) || {});
  return appConfig.feature_flags;
}
function normalizeWebUiConfig(raw = {}) {
  const source = raw && typeof raw === 'object' ? raw : {};
  const themeRaw = String(source.theme || source.web_theme || source.site_theme || 'classic').trim().toLowerCase();
  const theme = (themeRaw === 'triangolo' || themeRaw === 'triangolo_media' || themeRaw === 'alt') ? 'triangolo' : 'classic';
  const playerStatusText = sanitizeText(
    source.player_status_text ?? source.playerStatusText ?? source.player_footer_text ?? '',
    200
  );
  return { theme, player_status_text: playerStatusText };
}
function getWebUiSettings() {
  appConfig.web_ui = normalizeWebUiConfig((appConfig && appConfig.web_ui) || {});
  return appConfig.web_ui;
}
function getPublicThemeBodyClass() {
  return getWebUiSettings().theme === 'triangolo' ? 'theme-triangolo' : 'theme-classic';
}
function getPublicBaseStyle() {
  const ui = getWebUiSettings();
  if (ui.theme !== 'triangolo') return BASE_STYLE;
  const extra = `
<style id="theme-triangolo-overrides">
body.theme-triangolo{
  --bg:#07090f;--bg2:#0b0f18;--surface:#101522;--card:#141b2a;--border:rgba(255,255,255,.08);
  --accent:#3ddc97;--accent-2:#2bb673;--text:#f3f6fb;--muted:#9aa6bc;
  background:#07090f !important;
}
body.theme-triangolo nav{
  background:rgba(7,9,15,.92);border-bottom:1px solid rgba(255,255,255,.06);min-height:58px;padding:6px 18px;
}
body.theme-triangolo .nav-logo{font-size:1.1rem;letter-spacing:.02em}
body.theme-triangolo .nav-link,body.theme-triangolo .nav-link-cta{
  border:0;background:transparent;border-radius:8px;padding:8px 12px;font-size:.8rem;color:var(--muted);
}
body.theme-triangolo .nav-link:hover,body.theme-triangolo .nav-link.active,body.theme-triangolo .nav-link-cta{
  color:#fff;background:rgba(61,220,151,.12);
}
body.theme-triangolo .main-hero{display:none}
body.theme-triangolo .page{max-width:1280px;margin:0 auto;padding:18px 16px 40px}
body.theme-triangolo .admin-bar{
  background:transparent;border:0;padding:4px 0 14px;color:var(--muted);font-size:.72rem;
}
body.theme-triangolo .search-bar{
  background:rgba(20,27,42,.9);border:1px solid rgba(255,255,255,.08);border-radius:12px;margin-bottom:18px;
}
body.theme-triangolo .channels-grid{
  display:grid;grid-template-columns:repeat(auto-fill,minmax(132px,1fr));gap:14px;
}
body.theme-triangolo .ch-card{
  background:linear-gradient(180deg,#161e2e,#101624);border:1px solid rgba(255,255,255,.07);
  border-radius:14px;padding:14px 10px 12px;text-align:center;min-height:148px;
  box-shadow:0 10px 28px rgba(0,0,0,.28);
}
body.theme-triangolo .ch-card:hover{border-color:rgba(61,220,151,.45);transform:translateY(-4px)}
body.theme-triangolo .ch-logo,body.theme-triangolo .ch-card img{
  width:64px;height:64px;object-fit:contain;margin:0 auto 10px;border-radius:12px;background:rgba(255,255,255,.04);
}
body.theme-triangolo .ch-name{font-size:.78rem;font-weight:600;line-height:1.25;color:#f5f7fb}
body.theme-triangolo .cat-grid{grid-template-columns:repeat(auto-fill,minmax(160px,1fr));gap:12px}
body.theme-triangolo .cat-card{
  background:#141b2a;border:1px solid rgba(255,255,255,.07);border-radius:14px;padding:14px;
}
body.theme-triangolo .section-shell{background:transparent;border:0;padding:0;margin-bottom:22px}
body.theme-triangolo .section-head h2{font-size:1rem;font-weight:700}
body.theme-triangolo .info-footer{color:var(--muted);text-align:center;padding:28px 0 10px;font-size:.72rem}
</style>`;
  return BASE_STYLE + extra;
}
"""
    text = replace_once(text, old_ff, new_ff, "web_ui helpers")

    # Replace baseStyle: BASE_STYLE with getPublicBaseStyle()
    text = replace_all(
        text,
        "baseStyle: BASE_STYLE,",
        "baseStyle: getPublicBaseStyle(),",
        "baseStyle getPublicBaseStyle",
        min_count=8,
    )

    # Pass webUi into playerScript pageOptions — find a common pattern
    # Look for adSnippets: normalizeAdSnippetConfig in render calls
    # Inject into buildPlayerRuntimeSetup / playerScript options builders
    # Safer: patch playerScript to also read from appConfig via closure — already has appConfig in scope!
    # webUiConfig already reads pageOptions.webUi — ensure callers pass it.

    # Patch playerScript default to fall back to appConfig.web_ui
    text = replace_once(
        text,
        "const webUiConfig=(pageOptions&&pageOptions.webUi&&typeof pageOptions.webUi==='object')?pageOptions.webUi:{theme:'classic',playerStatusText:''};\n",
        "const webUiConfig=(function(){const fromPage=(pageOptions&&pageOptions.webUi&&typeof pageOptions.webUi==='object')?pageOptions.webUi:null;const fromApp=normalizeWebUiConfig((appConfig&&appConfig.web_ui)||{});const src=fromPage||fromApp;return {theme:src.theme||'classic',playerStatusText:String(src.player_status_text||src.playerStatusText||'').trim()};})();\n",
        "webUiConfig from appConfig",
    )

    # Admin features POST: persist web_ui before redirect
    old_feat_save = """    saveJSON(CONFIG_FILE, appConfig);
    return res.redirect(`${BASE_PATH}/admin/features?success=${encodeURIComponent('Feature flags u ruajtën me sukses!')}`);"""
    new_feat_save = """    appConfig.web_ui = normalizeWebUiConfig({
      theme: req.body.web_ui_theme,
      player_status_text: req.body.web_ui_player_status_text
    });
    saveJSON(CONFIG_FILE, appConfig);
    return res.redirect(`${BASE_PATH}/admin/features?success=${encodeURIComponent('Feature flags + Web UI u ruajtën me sukses!')}`);"""
    text = replace_once(text, old_feat_save, new_feat_save, "features post save web_ui")

    # Add form fields on features GET page — before submit button
    old_feat_btn = """<div class="admin-bar" style="justify-content:center">
  <button type="submit" class="a-btn a-btn-primary" style="font-size:.9rem;padding:10px 24px"><i class="fas fa-save"></i> Ruaj Features</button>
</div>
</form>
</div></body></html>`);
});"""
    new_feat_btn = """<div class="admin-section" style="margin-top:18px">
<h3><i class="fas fa-palette"></i> Web UI (design + player status)</h3>
<p style="color:var(--muted);font-size:.78rem;margin-bottom:10px">Ndrysho design-in publik (classic ose stil Triangolo) dhe tekstin e statusit në player (p.sh. Telegram channel).</p>
<div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(240px,1fr));gap:10px">
  <label class="form-label">Design theme
    <select name="web_ui_theme" class="form-input">
      <option value="classic" ${ (getWebUiSettings().theme!=='triangolo') ? 'selected' : '' }>Classic (aktual)</option>
      <option value="triangolo" ${ getWebUiSettings().theme==='triangolo' ? 'selected' : '' }>Triangolo-style</option>
    </select>
  </label>
  <label class="form-label" style="grid-column:1/-1">Player status text (në vend të "buffering / HLS.js...")
    <input type="text" name="web_ui_player_status_text" class="form-input" value="${escapeAttr(getWebUiSettings().player_status_text || '')}" placeholder="p.sh. Bashkohu: t.me/albview">
  </label>
</div>
</div>
<div class="admin-bar" style="justify-content:center">
  <button type="submit" class="a-btn a-btn-primary" style="font-size:.9rem;padding:10px 24px"><i class="fas fa-save"></i> Ruaj Features</button>
</div>
</form>
</div></body></html>`);
});"""
    text = replace_once(text, old_feat_btn, new_feat_btn, "features web ui form")

    # layout.ejs body class
    if LAYOUT.exists():
        layout = LAYOUT.read_text(encoding="utf-8")
        if "<body>" in layout and "bodyClass" not in layout:
            layout = layout.replace("<body>", '<body class="<%= bodyClass || \'theme-classic\' %>">', 1)
            LAYOUT.write_text(layout, encoding="utf-8")
            print("Updated layout.ejs body class")
        layout_js = Path("/root/all/all2/views/public/layout.js")
        if layout_js.exists():
            ljs = layout_js.read_text(encoding="utf-8")
            if "bodyClass" not in ljs:
                ljs = ljs.replace(
                    """function renderPublicLayout({
  baseStyle,
  title,
  extraHeadHtml = '',
  navPublicHtml,
  playerModalHtml,
  pageContentHtml,
  playerScriptHtml
}) {
  return renderTemplate('public/layout.ejs', {
    baseStyle,
    title,
    extraHeadHtml,
    navPublicHtml,
    playerModalHtml,
    pageContentHtml,
    playerScriptHtml
  });
}""",
                    """function renderPublicLayout({
  baseStyle,
  title,
  extraHeadHtml = '',
  navPublicHtml,
  playerModalHtml,
  pageContentHtml,
  playerScriptHtml,
  bodyClass = 'theme-classic'
}) {
  return renderTemplate('public/layout.ejs', {
    baseStyle,
    title,
    extraHeadHtml,
    navPublicHtml,
    playerModalHtml,
    pageContentHtml,
    playerScriptHtml,
    bodyClass
  });
}""",
                )
                layout_js.write_text(ljs, encoding="utf-8")
                print("Updated layout.js bodyClass")

    # Pass bodyClass in renderPublicLayout calls — add after getPublicBaseStyle()
    text = replace_all(
        text,
        "baseStyle: getPublicBaseStyle(),",
        "baseStyle: getPublicBaseStyle(),\n    bodyClass: getPublicThemeBodyClass(),",
        "bodyClass in layout",
        min_count=8,
    )

    # Default appConfig seed
    if "web_ui: {}" not in text and "web_ui:" not in text[text.find("let appConfig"): text.find("let appConfig") + 2500]:
        text = replace_once(
            text,
            "  feature_flags: {},",
            "  feature_flags: {},\n  web_ui: {},",
            "appConfig web_ui default",
        )

    SERVER.write_text(text, encoding="utf-8")
    print("server.js patched OK")

    # ── Config: kill popup sources; keep smartlinks + native ──────────────────
    if CONFIG.exists():
        cfg = json.loads(CONFIG.read_text(encoding="utf-8"))
        ads = cfg.get("ad_snippets") or {}
        ads["hls_popunder_enabled"] = False
        ads["hls_popunder_script_url"] = ""
        ads["channel_open_gate_enabled"] = False
        # Clear banner HTML that pulls adskeeper/popunder networks — keep native + smartlinks only
        ads["banner_html"] = ""
        ads["banner_html_list"] = []
        ads["interstitial_html"] = ""
        ads["interstitial_html_list"] = []
        ads["show_banner_on_main"] = False
        ads["show_banner_on_category"] = False
        ads["grid_native_enabled"] = True if ads.get("grid_native_enabled") is not False else False
        if not ads.get("grid_native_every"):
            ads["grid_native_every"] = 2
        cfg["ad_snippets"] = ads
        pre = cfg.get("pre_roll_ad") or {}
        pre["enabled"] = False
        pre["ad_unit_enabled"] = False
        pre["ad_unit_popunder_best_enabled"] = False
        pre["ad_unit_html_popunder_best"] = ""
        cfg["pre_roll_ad"] = pre
        inter = cfg.get("interstitial_ad") or {}
        inter["enabled"] = False
        cfg["interstitial_ad"] = inter
        smart = cfg.get("smart_links") or {}
        # leave smart_links as admin configured; do not force enable
        cfg["smart_links"] = smart
        ui = normalize_web_ui_py(cfg.get("web_ui"))
        if not ui.get("player_status_text"):
            ui["player_status_text"] = "Bashkohu në Telegram: t.me/albview"
        cfg["web_ui"] = ui
        CONFIG.write_text(json.dumps(cfg, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print("channel_config.json ads cleaned (popups off; native kept)")
    else:
        print("WARN: config missing")

    print("DONE")


def normalize_web_ui_py(raw):
    source = raw if isinstance(raw, dict) else {}
    theme = str(source.get("theme") or "classic").strip().lower()
    if theme not in ("classic", "triangolo"):
        theme = "classic"
    return {
        "theme": theme,
        "player_status_text": str(source.get("player_status_text") or "")[:200],
    }


if __name__ == "__main__":
    main()
