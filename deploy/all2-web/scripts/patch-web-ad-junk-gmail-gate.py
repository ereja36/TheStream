#!/usr/bin/env python3
"""Hide Adsterra go_banner junk text + mandatory Gmail login with free-channel allowlist."""
from pathlib import Path
import subprocess
import shutil

SERVER = Path("/root/all/all2/server.js")
RUNTIME = Path("/root/all/all2/views/player/runtimeConfig.js")
t = SERVER.read_text(encoding="utf-8")
rt = RUNTIME.read_text(encoding="utf-8")
changed = []

# ---------------------------------------------------------------------------
# A) Scrub Adsterra "frame_width=;frame_height=;/* go_banner */" junk
# ---------------------------------------------------------------------------
SCRUB_HELPER = r'''
function scrubNativeAdJunk(root){
  if(!root) return false;
  const junkRe=/frame_width\s*=|frame_height\s*=|go_banner|формируется/i;
  const walker=document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);
  const drop=[];
  while(walker.nextNode()){
    const node=walker.currentNode;
    const txt=String(node.textContent||'');
    if(junkRe.test(txt)) drop.push(node);
  }
  drop.forEach(node=>{ try{ node.textContent=''; }catch(e){} });
  // Remove leftover empty text-only wrappers
  Array.from(root.querySelectorAll('div,span,p')).forEach(el=>{
    if(el.querySelector('iframe,img,video,ins,script')) return;
    const txt=String(el.textContent||'').replace(/\s+/g,' ').trim();
    if(!txt || junkRe.test(txt)){
      try{ el.remove(); }catch(e){ el.innerHTML=''; }
    }
  });
  const hasMedia=!!root.querySelector('iframe,img,video,ins,a');
  const remain=String(root.textContent||'').replace(/\s+/g,' ').trim();
  if(!hasMedia && (!remain || junkRe.test(remain))){
    root.innerHTML='';
    return false;
  }
  return hasMedia || (remain.length>0 && !junkRe.test(remain));
}
function finalizeNativeAdSlot(node){
  if(!node) return;
  const ok=scrubNativeAdJunk(node);
  if(ok) return;
  const card=node.closest('.native-ad-card, .category-ad-slot');
  if(card){
    card.style.display='none';
    card.setAttribute('data-ad-empty','1');
  }else{
    node.style.display='none';
  }
}
'''

if "function scrubNativeAdJunk" not in t:
    marker = "async function renderNativeGridAds(){"
    if marker not in t:
        raise SystemExit("FAIL: renderNativeGridAds marker")
    t = t.replace(marker, SCRUB_HELPER + "\n" + marker, 1)
    changed.append("scrub-helper")
else:
    changed.append("scrub-helper:already")

old_native_loop = """    node.dataset.rendered='1';
    try{ await executeHtmlSnippet(node, html); }catch(e){}
    sendAdEvent('impression',{ source_key:'snippet_banner_grid', kind:'snippet_banner_grid' });
    const onClick=()=>sendAdEvent('click',{ source_key:'snippet_banner_grid', kind:'snippet_banner_grid' });
    node.addEventListener('click', onClick, { once:true });
    await new Promise(r=>setTimeout(r, 220));
  }
}"""
new_native_loop = """    node.dataset.rendered='1';
    try{ await executeHtmlSnippet(node, html); }catch(e){}
    // Adsterra empty fill sometimes dumps: frame_width=;frame_height=;/* go_banner */
    scrubNativeAdJunk(node);
    setTimeout(()=>finalizeNativeAdSlot(node), 900);
    setTimeout(()=>finalizeNativeAdSlot(node), 2500);
    sendAdEvent('impression',{ source_key:'snippet_banner_grid', kind:'snippet_banner_grid' });
    const onClick=()=>sendAdEvent('click',{ source_key:'snippet_banner_grid', kind:'snippet_banner_grid' });
    node.addEventListener('click', onClick, { once:true });
    await new Promise(r=>setTimeout(r, 220));
  }
}"""
if "finalizeNativeAdSlot(node)" not in t:
    if old_native_loop not in t:
        raise SystemExit("FAIL: native loop")
    t = t.replace(old_native_loop, new_native_loop, 1)
    changed.append("native-scrub")
else:
    changed.append("native-scrub:already")

# Also scrub after Adsterra isolate path resolves — patch executeHtmlSnippet finish
# Add CSS to visually hide obvious junk lines inside ad bodies (backup)
old_ad_css = """.native-ad-body{min-height:72px;border:1px dashed rgba(255,255,255,.18);border-radius:12px;padding:8px;overflow:hidden;background:rgba(0,0,0,.2)}"""
new_ad_css = """.native-ad-body{min-height:72px;border:1px dashed rgba(255,255,255,.18);border-radius:12px;padding:8px;overflow:hidden;background:rgba(0,0,0,.2)}
.native-ad-card[data-ad-empty="1"],.category-ad-slot[data-ad-empty="1"]{display:none!important}
.native-ad-body{color:transparent;font-size:0;line-height:0}
.native-ad-body iframe,.native-ad-body img,.native-ad-body video,.native-ad-body ins,.native-ad-body a,.native-ad-body [id^="container-"]{color:initial;font-size:initial;line-height:normal}"""
if 'data-ad-empty="1"' not in t.split(".native-ad-body{min-height")[1][:500]:
    if old_ad_css not in t:
        changed.append("ad-css:WARN")
    else:
        t = t.replace(old_ad_css, new_ad_css, 1)
        changed.append("ad-css")
else:
    changed.append("ad-css:already")

# ---------------------------------------------------------------------------
# B) Mandatory Google login + free channel allowlist
# ---------------------------------------------------------------------------
old_fba_return = """    reward_ads_enabled: parseBool(source.reward_ads_enabled ?? source.rewardAdsEnabled, false),
    reward_ads_required_clicks: Number.isFinite(rewardAdsClicksRaw) ? Math.min(Math.max(rewardAdsClicksRaw, 1), 20) : 3,
    reward_ads_free_hours: Number.isFinite(rewardAdsHoursRaw) ? Math.min(Math.max(rewardAdsHoursRaw, 1), 168) : 24,
    replace_old_devices_on_login: parseBool(source.replace_old_devices_on_login ?? source.replaceOldDevicesOnLogin, false)
  };
}"""

# Insert free slug parsing before return — need to add vars in function too
if "require_google_login" not in t.split("function normalizeFirebaseAuthConfig")[1][:2200]:
    # Add parsing before return block
    needle = "  const rewardAdsHoursRaw = parseInt(source.reward_ads_free_hours ?? source.rewardAdsFreeHours, 10);\n  return {"
    insert = """  const rewardAdsHoursRaw = parseInt(source.reward_ads_free_hours ?? source.rewardAdsFreeHours, 10);
  const freeSlugRaw = source.free_channel_slugs ?? source.freeChannelSlugs ?? source.free_channels ?? source.google_login_free_slugs ?? '';
  const freeChannelSlugs = (Array.isArray(freeSlugRaw) ? freeSlugRaw : String(freeSlugRaw || '').split(/[\\n\\r,]+/))
    .map(item => createSlug(String(item || '').trim()) || String(item || '').trim().toLowerCase().replace(/[^a-z0-9_-]+/g, '-'))
    .map(item => String(item || '').trim())
    .filter(Boolean)
    .slice(0, 800);
  return {"""
    if needle not in t:
        raise SystemExit("FAIL: firebase normalize insert point")
    t = t.replace(needle, insert, 1)
    old_ret = """    reward_ads_free_hours: Number.isFinite(rewardAdsHoursRaw) ? Math.min(Math.max(rewardAdsHoursRaw, 1), 168) : 24,
    replace_old_devices_on_login: parseBool(source.replace_old_devices_on_login ?? source.replaceOldDevicesOnLogin, false)
  };
}
function normalizeDynamicWatermarkConfig(raw = {}) {"""
    new_ret = """    reward_ads_free_hours: Number.isFinite(rewardAdsHoursRaw) ? Math.min(Math.max(rewardAdsHoursRaw, 1), 168) : 24,
    replace_old_devices_on_login: parseBool(source.replace_old_devices_on_login ?? source.replaceOldDevicesOnLogin, false),
    require_google_login: parseBool(source.require_google_login ?? source.requireGoogleLogin ?? source.force_google_login ?? source.mandatory_google_login, false),
    free_channel_slugs: Array.from(new Set(freeChannelSlugs))
  };
}
function isFirebaseFreeChannelSlug(slug = '', settings = null) {
  const cfg = settings && typeof settings === 'object' ? settings : normalizeFirebaseAuthConfig(appConfig.firebase_auth);
  const list = Array.isArray(cfg.free_channel_slugs) ? cfg.free_channel_slugs : [];
  if (!list.length) return false;
  const safe = createSlug(slug) || String(slug || '').trim().toLowerCase();
  if (!safe) return false;
  if (list.includes(safe)) return true;
  // Also allow raw equality for custom ids
  return list.includes(String(slug || '').trim());
}
function assertGoogleLoginChannelAccess(req, slug = '') {
  const settings = normalizeFirebaseAuthConfig(appConfig.firebase_auth);
  if (settings.enabled !== true || settings.require_google_login !== true) return { ok: true };
  if (isFirebaseFreeChannelSlug(slug, settings)) return { ok: true, free: true };
  const session = getFirebaseAccountSession(req);
  if (session && session.uid) return { ok: true, logged_in: true };
  return { ok: false, reason: 'google_login_required' };
}
function normalizeDynamicWatermarkConfig(raw = {}) {"""
    if old_ret not in t:
        raise SystemExit("FAIL: firebase return block")
    t = t.replace(old_ret, new_ret, 1)
    changed.append("normalizeFirebaseAuth")
else:
    changed.append("normalizeFirebaseAuth:already")

# Gate channel-url API
old_portal = """  const mainPortalCheck = assertMainPortalWebAccess(slug);
  if (!mainPortalCheck.ok) {
    return res.status(404).json({ ok: false, error: 'not_on_main_portal' });
  }"""
new_portal = """  const mainPortalCheck = assertMainPortalWebAccess(slug);
  if (!mainPortalCheck.ok) {
    return res.status(404).json({ ok: false, error: 'not_on_main_portal' });
  }
  const googleLoginCheck = assertGoogleLoginChannelAccess(req, slug);
  if (!googleLoginCheck.ok) {
    return res.status(401).json({ ok: false, error: 'google_login_required', message: 'Hyr me Gmail për të shikuar këtë kanal.' });
  }"""
if "google_login_required" not in t.split("router.get('/api/channel-url/:slug'")[1][:1200]:
    # only first channel-url handler
    idx = t.find("router.get('/api/channel-url/:slug'")
    if idx < 0:
        raise SystemExit("FAIL: channel-url route")
    chunk = t[idx:idx+1800]
    if old_portal not in chunk:
        raise SystemExit("FAIL: mainPortalCheck in channel-url")
    t = t[:idx] + chunk.replace(old_portal, new_portal, 1) + t[idx+1800:]
    changed.append("channel-url-gate")
else:
    changed.append("channel-url-gate:already")

# Admin form fields
old_admin_fba = """      <label class="form-label"><input type="hidden" name="fba_enabled" value="0"><input type="checkbox" name="fba_enabled" value="1" ${firebaseAuthSettings.enabled ? 'checked' : ''}> Enable Firebase Login</label>
      <label class="form-label"><input type="hidden" name="fba_max_devices_enabled" value="0"><input type="checkbox" name="fba_max_devices_enabled" value="1" ${firebaseAuthSettings.max_devices_enabled ? 'checked' : ''}> Enable max devices per account</label>"""
new_admin_fba = """      <label class="form-label"><input type="hidden" name="fba_enabled" value="0"><input type="checkbox" name="fba_enabled" value="1" ${firebaseAuthSettings.enabled ? 'checked' : ''}> Enable Firebase Login</label>
      <label class="form-label"><input type="hidden" name="fba_require_google_login" value="0"><input type="checkbox" name="fba_require_google_login" value="1" ${firebaseAuthSettings.require_google_login ? 'checked' : ''}> Login me Gmail i detyrueshëm (ON)</label>
      <label class="form-label" style="grid-column:1/-1">Kanale pa login (kur Gmail është i detyrueshëm) — një slug për rresht
        <textarea name="fba_free_channel_slugs" class="form-input" style="min-height:90px;resize:vertical" placeholder="p.sh.&#10;tv-klan&#10;top-channel&#10;koollokke_kool-albania_...">${escapeHtml((firebaseAuthSettings.free_channel_slugs || []).join('\\n'))}</textarea>
        <span style="display:block;color:var(--muted);font-size:.72rem;margin-top:4px">Këto kanale shikohen pa Gmail. Të tjerat kërkojnë login. Gjej slug-un te URL e kanalit / data-slug në kartë.</span>
      </label>
      <label class="form-label"><input type="hidden" name="fba_max_devices_enabled" value="0"><input type="checkbox" name="fba_max_devices_enabled" value="1" ${firebaseAuthSettings.max_devices_enabled ? 'checked' : ''}> Enable max devices per account</label>"""
if "fba_require_google_login" not in t:
    if old_admin_fba not in t:
        raise SystemExit("FAIL: admin firebase form")
    t = t.replace(old_admin_fba, new_admin_fba, 1)
    changed.append("admin-form")
else:
    changed.append("admin-form:already")

old_save_fba = """    if (action === 'save_firebase_auth') {
      appConfig.firebase_auth = normalizeFirebaseAuthConfig({
        enabled: readCheckboxField(req.body.fba_enabled),
        api_key: req.body.fba_api_key,
        auth_domain: req.body.fba_auth_domain,
        project_id: req.body.fba_project_id,
        storage_bucket: req.body.fba_storage_bucket,
        app_id: req.body.fba_app_id,
        messaging_sender_id: req.body.fba_messaging_sender_id,
        measurement_id: req.body.fba_measurement_id,
        max_devices_enabled: readCheckboxField(req.body.fba_max_devices_enabled),
        max_devices: req.body.fba_max_devices,
        trial_enabled: readCheckboxField(req.body.fba_trial_enabled),
        trial_days: req.body.fba_trial_days,
        trial_max_uses: req.body.fba_trial_max_uses,
        referral_reward_days: req.body.fba_referral_reward_days,
        referral_referred_bonus_days: req.body.fba_referral_referred_bonus_days
      });"""
new_save_fba = """    if (action === 'save_firebase_auth') {
      appConfig.firebase_auth = normalizeFirebaseAuthConfig({
        enabled: readCheckboxField(req.body.fba_enabled),
        require_google_login: readCheckboxField(req.body.fba_require_google_login),
        free_channel_slugs: req.body.fba_free_channel_slugs,
        api_key: req.body.fba_api_key,
        auth_domain: req.body.fba_auth_domain,
        project_id: req.body.fba_project_id,
        storage_bucket: req.body.fba_storage_bucket,
        app_id: req.body.fba_app_id,
        messaging_sender_id: req.body.fba_messaging_sender_id,
        measurement_id: req.body.fba_measurement_id,
        max_devices_enabled: readCheckboxField(req.body.fba_max_devices_enabled),
        max_devices: req.body.fba_max_devices,
        trial_enabled: readCheckboxField(req.body.fba_trial_enabled),
        trial_days: req.body.fba_trial_days,
        trial_max_uses: req.body.fba_trial_max_uses,
        referral_reward_days: req.body.fba_referral_reward_days,
        referral_referred_bonus_days: req.body.fba_referral_referred_bonus_days
      });"""
if "fba_require_google_login" not in t.split("save_firebase_auth")[1][:800]:
    if old_save_fba not in t:
        raise SystemExit("FAIL: save_firebase_auth")
    t = t.replace(old_save_fba, new_save_fba, 1)
    changed.append("admin-save")
else:
    changed.append("admin-save:already")

# Client: extend firebaseAuthConfig default + helpers + openPlayerGuarded
old_fba_client = """const firebaseAuthConfig=(pageOptions&&pageOptions.firebaseAuth&&typeof pageOptions.firebaseAuth==='object')?pageOptions.firebaseAuth:{enabled:true,apiKey:'${DEFAULT_FIREBASE_AUTH_CONFIG.api_key}',authDomain:'${DEFAULT_FIREBASE_AUTH_CONFIG.auth_domain}',projectId:'${DEFAULT_FIREBASE_AUTH_CONFIG.project_id}',storageBucket:'${DEFAULT_FIREBASE_AUTH_CONFIG.storage_bucket}',appId:'${DEFAULT_FIREBASE_AUTH_CONFIG.app_id}',messagingSenderId:'${DEFAULT_FIREBASE_AUTH_CONFIG.messaging_sender_id}',measurementId:'${DEFAULT_FIREBASE_AUTH_CONFIG.measurement_id}',maxDevicesEnabled:true,maxDevices:2,trialEnabled:true,trialDays:3,trialMaxUses:1,referralRewardDays:3,referralReferredBonusDays:1};"""
new_fba_client = """const firebaseAuthConfig=(function(){const src=(pageOptions&&pageOptions.firebaseAuth&&typeof pageOptions.firebaseAuth==='object')?pageOptions.firebaseAuth:{};return {enabled:src.enabled===true,apiKey:String(src.apiKey||'${DEFAULT_FIREBASE_AUTH_CONFIG.api_key}'),authDomain:String(src.authDomain||'${DEFAULT_FIREBASE_AUTH_CONFIG.auth_domain}'),projectId:String(src.projectId||'${DEFAULT_FIREBASE_AUTH_CONFIG.project_id}'),storageBucket:String(src.storageBucket||'${DEFAULT_FIREBASE_AUTH_CONFIG.storage_bucket}'),appId:String(src.appId||'${DEFAULT_FIREBASE_AUTH_CONFIG.app_id}'),messagingSenderId:String(src.messagingSenderId||'${DEFAULT_FIREBASE_AUTH_CONFIG.messaging_sender_id}'),measurementId:String(src.measurementId||'${DEFAULT_FIREBASE_AUTH_CONFIG.measurement_id}'),maxDevicesEnabled:src.maxDevicesEnabled===true,maxDevices:Math.max(1,parseInt(src.maxDevices,10)||2),trialEnabled:src.trialEnabled===true,trialDays:Math.max(1,parseInt(src.trialDays,10)||3),trialMaxUses:Math.max(1,parseInt(src.trialMaxUses,10)||1),referralRewardDays:Math.max(1,parseInt(src.referralRewardDays,10)||3),referralReferredBonusDays:Math.max(0,parseInt(src.referralReferredBonusDays,10)||0),requireGoogleLogin:src.requireGoogleLogin===true||src.require_google_login===true,freeChannelSlugs:Array.isArray(src.freeChannelSlugs)?src.freeChannelSlugs:(Array.isArray(src.free_channel_slugs)?src.free_channel_slugs:[])};})();"""
if "requireGoogleLogin" not in t.split("const firebaseAuthConfig=")[1][:900]:
    if old_fba_client not in t:
        raise SystemExit("FAIL: firebaseAuthConfig client")
    t = t.replace(old_fba_client, new_fba_client, 1)
    changed.append("client-config")
else:
    changed.append("client-config:already")

# Client helpers + gate openPlayerGuarded — insert before openPlayerGuarded
CLIENT_HELPERS = r'''
function isGoogleLoginRequired(){
  return firebaseAuthConfig.enabled===true && firebaseAuthConfig.requireGoogleLogin===true;
}
function isFreeWatchChannel(slug){
  const list=Array.isArray(firebaseAuthConfig.freeChannelSlugs)?firebaseAuthConfig.freeChannelSlugs:[];
  if(!list.length) return false;
  const raw=String(slug||'').trim();
  const lower=raw.toLowerCase();
  return list.some(item=>{
    const s=String(item||'').trim();
    if(!s) return false;
    return s===raw || s.toLowerCase()===lower;
  });
}
function isGoogleAccountLoggedIn(){
  return !!(accountSessionToken && accountProfile && accountProfile.uid);
}
async function ensureGoogleLoginForChannel(slug){
  if(!isGoogleLoginRequired()) return true;
  if(isFreeWatchChannel(slug)) return true;
  if(isGoogleAccountLoggedIn()) return true;
  // Clear anonymous bypass when mandatory login is ON
  try{
    accountBypassMode=false;
    localStorage.removeItem('k1_account_bypass_mode');
  }catch(e){}
  try{ openAccountMenu(); }catch(e){}
  alert('Ky kanal kërkon login me Gmail. Hyr me Google, ose zgjidh një kanal të lirë.');
  return false;
}
'''

if "function ensureGoogleLoginForChannel" not in t:
    old_guard = """async function openPlayerGuarded(slug,name,categoryKey='',sourceKey=''){
  const allowed=await ensureParentalAccess(categoryKey, sourceKey);
  if(!allowed){ alert('PIN i gabuar ose i munguar.'); return; }
  if(accountSessionToken && (!accountProfile || !accountProfile.uid)){
    await loadAccountProfile().catch(()=>{});
  }
  openPlayer(slug,name,categoryKey,sourceKey);
}"""
    new_guard = CLIENT_HELPERS + """
async function openPlayerGuarded(slug,name,categoryKey='',sourceKey=''){
  const allowed=await ensureParentalAccess(categoryKey, sourceKey);
  if(!allowed){ alert('PIN i gabuar ose i munguar.'); return; }
  if(accountSessionToken && (!accountProfile || !accountProfile.uid)){
    await loadAccountProfile().catch(()=>{});
  }
  const loginOk=await ensureGoogleLoginForChannel(slug);
  if(!loginOk) return;
  openPlayer(slug,name,categoryKey,sourceKey);
}"""
    if old_guard not in t:
        raise SystemExit("FAIL: openPlayerGuarded")
    t = t.replace(old_guard, new_guard, 1)
    changed.append("openPlayerGuarded")
else:
    changed.append("openPlayerGuarded:already")

# Hide anonymous bypass when mandatory login ON
old_anon = """    cardsEl.appendChild(createAccountActionCard({
      icon:'fas fa-user-secret',
      title:'Vazhdo anonymous',
      subtitle:'Hyr pa account dhe me ads sipas rregullave.',
      onClick:()=>{ enableBypassMode(); closeAccountActionLayer(); }
    }));"""
new_anon = """    if(!isGoogleLoginRequired()){
      cardsEl.appendChild(createAccountActionCard({
        icon:'fas fa-user-secret',
        title:'Vazhdo anonymous',
        subtitle:'Hyr pa account dhe me ads sipas rregullave.',
        onClick:()=>{ enableBypassMode(); closeAccountActionLayer(); }
      }));
    }else{
      subtitleEl.textContent='Për të shikuar kanalet duhet hyrë me Gmail (disa kanale janë falas pa login).';
    }"""
if "if(!isGoogleLoginRequired()){" not in t.split("Vazhdo anonymous")[0][-200:]+t.split("Vazhdo anonymous")[1][:400] if "Vazhdo anonymous" in t else "":
    if old_anon not in t:
        changed.append("anon-bypass:WARN")
    else:
        t = t.replace(old_anon, new_anon, 1)
        changed.append("anon-bypass")
else:
    # try simpler check
    if "isGoogleLoginRequired()" not in t.split("Vazhdo anonymous")[0][-300:] if "Vazhdo anonymous" in t else "":
        if old_anon in t:
            t = t.replace(old_anon, new_anon, 1)
            changed.append("anon-bypass")
        else:
            changed.append("anon-bypass:WARN2")
    else:
        changed.append("anon-bypass:already")

# promptGoogleOrBypassOnce — when mandatory, force Google menu not bypass
old_prompt = """async function promptGoogleOrBypassOnce(){
  if(firebaseAuthConfig.enabled!==true) return;
  if(accountSessionToken) return;
  if(accountBypassMode===true) return;
  if(accountChoicePrompted===true) return;
  accountChoicePrompted=true;
  localStorage.setItem('k1_account_choice_prompted','1');
  const useGoogle=confirm('Hyr me Google për sync/premium?\\\\nOK = Google Login\\\\nCancel = Bypass');
  if(useGoogle){
    await signInWithGoogleAccount();
  }else{
    enableBypassMode();
  }
}"""
# In file the \\n might be \n inside template - check actual
if "function promptGoogleOrBypassOnce" in t and "isGoogleLoginRequired()" not in t.split("function promptGoogleOrBypassOnce")[1][:700]:
    # find and replace carefully
    start = t.find("async function promptGoogleOrBypassOnce(){")
    end = t.find("\nasync function bootstrapFirebaseAccountRuntime", start)
    if start < 0 or end < 0:
        changed.append("prompt:WARN")
    else:
        new_prompt = """async function promptGoogleOrBypassOnce(){
  if(firebaseAuthConfig.enabled!==true) return;
  if(accountSessionToken) return;
  if(isGoogleLoginRequired()){
    // Mandatory Gmail: never enable anonymous bypass
    try{ accountBypassMode=false; localStorage.removeItem('k1_account_bypass_mode'); }catch(e){}
    if(accountChoicePrompted===true) return;
    accountChoicePrompted=true;
    try{ localStorage.setItem('k1_account_choice_prompted','1'); }catch(e){}
    try{ openAccountMenu(); }catch(e){}
    return;
  }
  if(accountBypassMode===true) return;
  if(accountChoicePrompted===true) return;
  accountChoicePrompted=true;
  localStorage.setItem('k1_account_choice_prompted','1');
  const useGoogle=confirm('Hyr me Google për sync/premium?\\nOK = Google Login\\nCancel = Bypass');
  if(useGoogle){
    await signInWithGoogleAccount();
  }else{
    enableBypassMode();
  }
}"""
        t = t[:start] + new_prompt + t[end:]
        changed.append("prompt")
else:
    changed.append("prompt:already")

# Handle channel-url 401 google_login_required in client fetch if present
# Optional: when play fails with google_login_required, open menu

SERVER.write_text(t, encoding="utf-8")

# runtimeConfig.js — pass new fields to client
old_rt = """    firebaseAuth: {
      enabled: normalizedFirebaseAuth.enabled === true,
      apiKey: normalizedFirebaseAuth.api_key || '',
      authDomain: normalizedFirebaseAuth.auth_domain || '',
      projectId: normalizedFirebaseAuth.project_id || '',
      storageBucket: normalizedFirebaseAuth.storage_bucket || '',
      appId: normalizedFirebaseAuth.app_id || '',
      messagingSenderId: normalizedFirebaseAuth.messaging_sender_id || '',
      measurementId: normalizedFirebaseAuth.measurement_id || '',
      maxDevicesEnabled: normalizedFirebaseAuth.max_devices_enabled === true,
      maxDevices: Math.max(1, parseInt(normalizedFirebaseAuth.max_devices, 10) || 2),
      trialEnabled: normalizedFirebaseAuth.trial_enabled === true,
      trialDays: Math.max(1, parseInt(normalizedFirebaseAuth.trial_days, 10) || 3),
      trialMaxUses: Math.max(1, parseInt(normalizedFirebaseAuth.trial_max_uses, 10) || 1),
      referralRewardDays: Math.max(1, parseInt(normalizedFirebaseAuth.referral_reward_days, 10) || 3),
      referralReferredBonusDays: Math.max(0, parseInt(normalizedFirebaseAuth.referral_referred_bonus_days, 10) || 0)
    },"""
new_rt = """    firebaseAuth: {
      enabled: normalizedFirebaseAuth.enabled === true,
      apiKey: normalizedFirebaseAuth.api_key || '',
      authDomain: normalizedFirebaseAuth.auth_domain || '',
      projectId: normalizedFirebaseAuth.project_id || '',
      storageBucket: normalizedFirebaseAuth.storage_bucket || '',
      appId: normalizedFirebaseAuth.app_id || '',
      messagingSenderId: normalizedFirebaseAuth.messaging_sender_id || '',
      measurementId: normalizedFirebaseAuth.measurement_id || '',
      maxDevicesEnabled: normalizedFirebaseAuth.max_devices_enabled === true,
      maxDevices: Math.max(1, parseInt(normalizedFirebaseAuth.max_devices, 10) || 2),
      trialEnabled: normalizedFirebaseAuth.trial_enabled === true,
      trialDays: Math.max(1, parseInt(normalizedFirebaseAuth.trial_days, 10) || 3),
      trialMaxUses: Math.max(1, parseInt(normalizedFirebaseAuth.trial_max_uses, 10) || 1),
      referralRewardDays: Math.max(1, parseInt(normalizedFirebaseAuth.referral_reward_days, 10) || 3),
      referralReferredBonusDays: Math.max(0, parseInt(normalizedFirebaseAuth.referral_referred_bonus_days, 10) || 0),
      requireGoogleLogin: normalizedFirebaseAuth.require_google_login === true,
      freeChannelSlugs: Array.isArray(normalizedFirebaseAuth.free_channel_slugs) ? normalizedFirebaseAuth.free_channel_slugs : []
    },"""
if "requireGoogleLogin" not in rt:
    if old_rt not in rt:
        raise SystemExit("FAIL: runtimeConfig firebaseAuth")
    rt = rt.replace(old_rt, new_rt, 1)
    RUNTIME.write_text(rt, encoding="utf-8")
    changed.append("runtimeConfig")
else:
    changed.append("runtimeConfig:already")

r = subprocess.run(["node", "--check", str(SERVER)], capture_output=True, text=True)
print("changed:", ", ".join(changed))
print("check", r.returncode, (r.stderr or "OK")[:500])
if r.returncode != 0:
    raise SystemExit(1)
r2 = subprocess.run(["node", "--check", str(RUNTIME)], capture_output=True, text=True)
print("runtime check", r2.returncode, (r2.stderr or "OK")[:300])
if r2.returncode != 0:
    raise SystemExit(1)
print("DONE")
