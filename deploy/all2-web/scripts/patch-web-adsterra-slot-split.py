#!/usr/bin/env python3
"""Split Adsterra/multi-banner snippets into one-per Sponsored slot + safe sequential render."""
from pathlib import Path
import subprocess
import json
import re

SERVER = Path("/root/all/all2/server.js")
CONFIG = Path("/root/all/all2/data/channel_config.json")
t = SERVER.read_text(encoding="utf-8")
changed = []

# ---------------------------------------------------------------------------
# 1) Server helper: expand multi-unit ad blobs into distinct snippets
# ---------------------------------------------------------------------------
EXPAND_FN = r'''
function expandAdSnippetUnits(rawHtml = '') {
  const text = String(rawHtml || '').trim();
  if (!text) return [];
  // Explicit separators first
  const bySep = text.split(/\n\s*---\s*\n/g).map(v => String(v || '').trim()).filter(Boolean);
  const chunks = bySep.length > 1 ? bySep : [text];
  const units = [];
  const pushUnique = (html) => {
    const safe = String(html || '').trim();
    if (!safe) return;
    if (units.some(u => u === safe)) return;
    units.push(safe);
  };
  for (const chunk of chunks) {
    // Adsterra atOptions + invoke.js pairs
    const atRe = /<script[^>]*>\s*atOptions\s*=\s*\{[\s\S]*?\}\s*;?\s*<\/script>\s*<script[^>]*src=["'][^"']+\/([a-f0-9]+)\/invoke\.js["'][^>]*>\s*<\/script>/gi;
    let match;
    let foundAt = false;
    const consumed = [];
    while ((match = atRe.exec(chunk)) !== null) {
      foundAt = true;
      consumed.push([match.index, match.index + match[0].length]);
      pushUnique(match[0]);
    }
    // Adsterra container + invoke (no atOptions)
    const contRe = /<script[^>]*src=["']([^"']+\/([a-f0-9]+)\/invoke\.js)["'][^>]*>\s*<\/script>\s*<div[^>]*id=["']container-\2["'][^>]*>\s*<\/div>/gi;
    while ((match = contRe.exec(chunk)) !== null) {
      foundAt = true;
      consumed.push([match.index, match.index + match[0].length]);
      pushUnique(match[0]);
    }
    // Reverse: div first then script
    const contRe2 = /<div[^>]*id=["']container-([a-f0-9]+)["'][^>]*>\s*<\/div>\s*<script[^>]*src=["']([^"']+\/\1\/invoke\.js)["'][^>]*>\s*<\/script>/gi;
    while ((match = contRe2.exec(chunk)) !== null) {
      foundAt = true;
      consumed.push([match.index, match.index + match[0].length]);
      pushUnique(match[0]);
    }
    // Adskeeper / MG widget block
    const mgRe = /<script[^>]*src=["'][^"']*adskeeper[^"']*["'][^>]*>\s*<\/script>[\s\S]*?<div[^>]*data-type=["']_mgwidget["'][^>]*>\s*<\/div>[\s\S]*?<script[^>]*>\s*\(function\s*\(\s*w\s*,\s*q\s*\)[\s\S]*?<\/script>/gi;
    while ((match = mgRe.exec(chunk)) !== null) {
      foundAt = true;
      consumed.push([match.index, match.index + match[0].length]);
      pushUnique(match[0]);
    }
    if (!foundAt) {
      pushUnique(chunk);
      continue;
    }
    // Keep leftover non-ad HTML if meaningful
    let leftover = chunk;
    for (const [a, b] of consumed.sort((x, y) => y[0] - x[0])) {
      leftover = leftover.slice(0, a) + leftover.slice(b);
    }
    leftover = leftover.replace(/\s+/g, ' ').trim();
    if (leftover.length > 40 && /<script|<iframe|<ins |data-ad/i.test(leftover)) {
      pushUnique(leftover);
    }
  }
  return units.slice(0, 20);
}
function collectNativeBannerSnippets(adSettings = {}) {
  const settings = adSettings && typeof adSettings === 'object' ? adSettings : {};
  const raw = [String(settings.banner_html || '').trim(), ...(Array.isArray(settings.banner_html_list) ? settings.banner_html_list : [])];
  const out = [];
  const seen = new Set();
  for (const item of raw) {
    for (const unit of expandAdSnippetUnits(item)) {
      const key = unit.replace(/\s+/g, ' ').trim();
      if (!key || seen.has(key)) continue;
      seen.add(key);
      out.push(unit);
      if (out.length >= 20) return out;
    }
  }
  return out;
}
'''

if "function expandAdSnippetUnits" not in t:
    marker = "function buildNativeAdSlotHtml(adSettings, index = 0) {"
    if marker not in t:
        raise SystemExit("FAIL: buildNativeAdSlotHtml not found")
    t = t.replace(marker, EXPAND_FN + "\n" + marker, 1)
    changed.append("expand-helpers")
else:
    changed.append("expand-helpers:already")

# ---------------------------------------------------------------------------
# 2) buildNativeAdSlotHtml — one unique unit per slot, no reuse
# ---------------------------------------------------------------------------
old_build = """function buildNativeAdSlotHtml(adSettings, index = 0) {
  const nativeSnippets = [String(adSettings.banner_html || '').trim(), ...(Array.isArray(adSettings.banner_html_list) ? adSettings.banner_html_list : [])]
    .map(v => String(v || '').trim())
    .filter(Boolean);
  if (!nativeSnippets.length) return '';
  const pick = nativeSnippets[index % nativeSnippets.length];
  const encoded = Buffer.from(String(pick || ''), 'utf8').toString('base64');
  return `<div class="category-ad-slot native-ad-card" data-native-ad="1"><div class="native-ad-head"><i class="fas fa-bullhorn"></i> Sponsored</div><div class="native-ad-body" data-ad-snippet="${encoded}"></div></div>`;
}"""
new_build = """function buildNativeAdSlotHtml(adSettings, index = 0) {
  const nativeSnippets = collectNativeBannerSnippets(adSettings);
  if (!nativeSnippets.length) return '';
  const idx = Math.max(0, parseInt(index, 10) || 0);
  // Never reuse the same Adsterra/Adskeeper zone on one page — second use stays empty.
  if (idx >= nativeSnippets.length) return '';
  const pick = nativeSnippets[idx];
  const encoded = Buffer.from(String(pick || ''), 'utf8').toString('base64');
  return `<div class="category-ad-slot native-ad-card" data-native-ad="1" data-ad-slot-index="${idx}"><div class="native-ad-head"><i class="fas fa-bullhorn"></i> Sponsored</div><div class="native-ad-body" data-ad-snippet="${encoded}" data-ad-slot-index="${idx}"></div></div>`;
}"""
if "collectNativeBannerSnippets(adSettings)" not in t.split("function buildNativeAdSlotHtml")[1][:500]:
    if old_build not in t:
        raise SystemExit("FAIL: buildNativeAdSlotHtml body")
    t = t.replace(old_build, new_build, 1)
    changed.append("buildNativeAdSlotHtml")
else:
    changed.append("buildNativeAdSlotHtml:already")

# ---------------------------------------------------------------------------
# 3) buildCards nativeSnippets collection
# ---------------------------------------------------------------------------
old_cards_snips = """  const nativeSnippets = [String(adSettings.banner_html || '').trim(), ...(Array.isArray(adSettings.banner_html_list) ? adSettings.banner_html_list : [])]
    .map(v => String(v || '').trim())
    .filter(Boolean);"""
new_cards_snips = """  const nativeSnippets = collectNativeBannerSnippets(adSettings);"""
# Only first occurrence in buildCards
if "collectNativeBannerSnippets(adSettings)" not in t.split("function buildCards")[1][:900]:
    if old_cards_snips not in t:
        raise SystemExit("FAIL: buildCards nativeSnippets")
    t = t.replace(old_cards_snips, new_cards_snips, 1)
    changed.append("buildCards-snips")
else:
    changed.append("buildCards-snips:already")

# ---------------------------------------------------------------------------
# 4) normalizeSnippetList — also auto-expand Adsterra blobs + raise limits
# ---------------------------------------------------------------------------
old_norm_list = """  const normalizeSnippetList = (value, itemMaxLen = 12000, maxItems = 12) => {
    let rawItems = [];
    if (Array.isArray(value)) {
      rawItems = value;
    } else {
      const text = String(value || '').trim();
      if (text) {
        if (text.startsWith('[') && text.endsWith(']')) {
          try {
            const parsed = JSON.parse(text);
            if (Array.isArray(parsed)) rawItems = parsed;
          } catch {}
        }
        if (!rawItems.length) {
          // Split by a line containing only --- to support easy multiline snippets.
          rawItems = text.split(/\\n\\s*---\\s*\\n/g);
        }
      }
    }
    return rawItems
      .map(item => clampText(item, itemMaxLen))
      .filter(Boolean)
      .slice(0, maxItems);
  };"""

# We'll expand after clamp using expandAdSnippetUnits — but expand is defined later.
# Instead expand inside normalizeAdSnippetConfig AFTER the function exists... 
# Better: inline a lightweight split in normalizeSnippetList for atOptions blocks.

old_norm_list_simple = """    return rawItems
      .map(item => clampText(item, itemMaxLen))
      .filter(Boolean)
      .slice(0, maxItems);
  };"""
new_norm_list_simple = """    const expanded = [];
    for (const item of rawItems) {
      const safe = clampText(item, Math.max(itemMaxLen, 20000));
      if (!safe) continue;
      // Auto-split pasted Adsterra multi-units even without --- separators.
      const atParts = safe.split(/(?=<script[^>]*>\\s*atOptions\\s*=)/i).map(v => String(v || '').trim()).filter(Boolean);
      if (atParts.length > 1) {
        for (const part of atParts) expanded.push(clampText(part, itemMaxLen));
        continue;
      }
      expanded.push(safe);
    }
    return expanded.filter(Boolean).slice(0, Math.max(maxItems, 20));
  };"""
if "Auto-split pasted Adsterra" not in t:
    if old_norm_list_simple not in t:
        raise SystemExit("FAIL: normalizeSnippetList return")
    t = t.replace(old_norm_list_simple, new_norm_list_simple, 1)
    changed.append("normalizeSnippetList")
else:
    changed.append("normalizeSnippetList:already")

# Raise banner_html clamp a bit for safety when reading single field (keep list as primary)
# ---------------------------------------------------------------------------
# 5) Client: better Adsterra execute + sequential native render
# ---------------------------------------------------------------------------
old_exec = """function executeHtmlSnippet(target, html){
  if(!target) return;
  target.innerHTML=String(html||'');
  const scripts=Array.from(target.querySelectorAll('script'));
  scripts.forEach((oldScript)=>{
    const newScript=document.createElement('script');
    Array.from(oldScript.attributes||[]).forEach(attr=>newScript.setAttribute(attr.name, attr.value));
    if(oldScript.textContent) newScript.textContent=oldScript.textContent;
    oldScript.parentNode?.replaceChild(newScript, oldScript);
  });
}"""

new_exec = """function executeHtmlSnippet(target, html){
  if(!target) return Promise.resolve();
  const raw=String(html||'');
  const keyMatch=raw.match(/['\"]key['\"]\\s*:\\s*['\"]([a-f0-9]+)['\"]/i);
  const invokeMatch=raw.match(/src=[\"'](https?:\\/\\/[^\"']+\\/([a-f0-9]+)\\/invoke\\.js)[\"']/i)
    || raw.match(/(https?:\\/\\/[^\\s\"'<>]+\\/([a-f0-9]+)\\/invoke\\.js)/i);
  const containerKey=(keyMatch&&keyMatch[1])||(invokeMatch&&invokeMatch[2])||'';
  const invokeUrl=invokeMatch?invokeMatch[1]:'';
  // Adsterra: isolate one unit per Sponsored slot (global atOptions + unique container).
  if(containerKey && invokeUrl){
    const heightMatch=raw.match(/['\"]height['\"]\\s*:\\s*(\\d+)/i);
    const widthMatch=raw.match(/['\"]width['\"]\\s*:\\s*(\\d+)/i);
    const height=heightMatch?parseInt(heightMatch[1],10):90;
    const width=widthMatch?parseInt(widthMatch[1],10):728;
    const slotSuffix=String(target.dataset.adSlotIndex||Math.random().toString(36).slice(2,8));
    // Unique DOM id per slot so reuse never collides; Adsterra still keys off atOptions.key.
    const domId='container-'+containerKey+'-'+slotSuffix;
    target.innerHTML='';
    const box=document.createElement('div');
    box.id='container-'+containerKey;
    box.setAttribute('data-ad-container', domId);
    box.style.cssText='width:100%;max-width:100%;display:flex;align-items:center;justify-content:center;overflow:hidden;min-height:'+Math.max(40,height)+'px';
    target.appendChild(box);
    window.atOptions={
      key: containerKey,
      format: 'iframe',
      height: Number.isFinite(height)?height:90,
      width: Number.isFinite(width)?width:728,
      params: {}
    };
    return new Promise((resolve)=>{
      const s=document.createElement('script');
      s.src=invokeUrl+(invokeUrl.includes('?')?'&':'?')+'slot='+encodeURIComponent(slotSuffix);
      s.async=true;
      let done=false;
      const finish=()=>{ if(done) return; done=true; setTimeout(resolve, 120); };
      s.onload=finish;
      s.onerror=finish;
      // Fallback if onload never fires (cached script quirks)
      setTimeout(finish, 1800);
      target.appendChild(s);
    });
  }
  target.innerHTML=raw;
  const scripts=Array.from(target.querySelectorAll('script'));
  const runNext=(i)=>new Promise((resolve)=>{
    if(i>=scripts.length) return resolve();
    const oldScript=scripts[i];
    const newScript=document.createElement('script');
    Array.from(oldScript.attributes||[]).forEach(attr=>newScript.setAttribute(attr.name, attr.value));
    if(oldScript.src){
      let finished=false;
      const finish=()=>{ if(finished) return; finished=true; runNext(i+1).then(resolve); };
      newScript.onload=finish;
      newScript.onerror=finish;
      setTimeout(finish, 2000);
      oldScript.parentNode?.replaceChild(newScript, oldScript);
      return;
    }
    if(oldScript.textContent) newScript.textContent=oldScript.textContent;
    oldScript.parentNode?.replaceChild(newScript, oldScript);
    runNext(i+1).then(resolve);
  });
  return runNext(0);
}"""

if "Adsterra: isolate one unit" not in t:
    if old_exec not in t:
        raise SystemExit("FAIL: executeHtmlSnippet")
    t = t.replace(old_exec, new_exec, 1)
    changed.append("executeHtmlSnippet")
else:
    changed.append("executeHtmlSnippet:already")

old_native = """function renderNativeGridAds(){
  const nodes=document.querySelectorAll('.native-ad-body[data-ad-snippet]');
  if(!nodes||!nodes.length) return;
  if(isNoAdsUser()){
    nodes.forEach(node=>{
      if(!node) return;
      node.innerHTML='';
      node.style.display='none';
    });
    return;
  }
  nodes.forEach(node=>{
    if(!node || node.dataset.rendered==='1') return;
    node.style.display='';
    const encoded=String(node.dataset.adSnippet||'').trim();
    if(!encoded) return;
    let html='';
    try{ html=atob(encoded); }catch{ html=''; }
    if(!html) return;
    executeHtmlSnippet(node, html);
    node.dataset.rendered='1';
    sendAdEvent('impression',{ source_key:'snippet_banner_grid', kind:'snippet_banner_grid' });
    const onClick=()=>sendAdEvent('click',{ source_key:'snippet_banner_grid', kind:'snippet_banner_grid' });
    node.addEventListener('click', onClick, { once:true });
  });
}"""

new_native = """async function renderNativeGridAds(){
  const nodes=Array.from(document.querySelectorAll('.native-ad-body[data-ad-snippet]'));
  if(!nodes.length) return;
  if(isNoAdsUser()){
    nodes.forEach(node=>{
      if(!node) return;
      node.innerHTML='';
      node.style.display='none';
    });
    return;
  }
  // Sequential: Adsterra uses global atOptions — parallel fill makes all ads land in one slot.
  for(const node of nodes){
    if(!node || node.dataset.rendered==='1') continue;
    node.style.display='';
    const encoded=String(node.dataset.adSnippet||'').trim();
    if(!encoded) continue;
    let html='';
    try{ html=atob(encoded); }catch{ html=''; }
    if(!html) continue;
    node.dataset.rendered='1';
    try{ await executeHtmlSnippet(node, html); }catch(e){}
    sendAdEvent('impression',{ source_key:'snippet_banner_grid', kind:'snippet_banner_grid' });
    const onClick=()=>sendAdEvent('click',{ source_key:'snippet_banner_grid', kind:'snippet_banner_grid' });
    node.addEventListener('click', onClick, { once:true });
    await new Promise(r=>setTimeout(r, 220));
  }
}"""

if "Sequential: Adsterra uses global atOptions" not in t:
    if old_native not in t:
        raise SystemExit("FAIL: renderNativeGridAds")
    t = t.replace(old_native, new_native, 1)
    changed.append("renderNativeGridAds")
else:
    changed.append("renderNativeGridAds:already")

# ---------------------------------------------------------------------------
# 6) Admin help text
# ---------------------------------------------------------------------------
old_help = """    <label class="form-label" style="grid-column:1/-1">Banner snippets list (opsionale, një ose më shumë)
      <textarea name="adsn_banner_snippets_text" class="form-input" style="min-height:160px;resize:vertical" placeholder="Snippet #1 ...&#10;---&#10;Snippet #2 ...&#10;---&#10;Snippet #3 ...">${escapeHtml((adSnippetSettings.banner_html_list || []).join('\\n---\\n'))}</textarea>
    </label>"""
new_help = """    <label class="form-label" style="grid-column:1/-1">Banner snippets list (opsionale, një ose më shumë)
      <span style="display:block;color:var(--muted);font-size:.72rem;font-weight:400;margin:4px 0 8px">Secila Adsterra / Adskeeper duhet në slot të ndryshëm Sponsored. Ndaji me <code>---</code> (ose liji të ngjitura — sistemi i ndan automatikisht).</span>
      <textarea name="adsn_banner_snippets_text" class="form-input" style="min-height:160px;resize:vertical" placeholder="Snippet #1 ...&#10;---&#10;Snippet #2 ...&#10;---&#10;Snippet #3 ...">${escapeHtml((adSnippetSettings.banner_html_list || []).join('\\n---\\n'))}</textarea>
    </label>"""
if "Ndaji me <code>---</code>" not in t:
    if old_help not in t:
        # try without worrying - optional
        changed.append("admin-help:WARN")
    else:
        t = t.replace(old_help, new_help, 1)
        changed.append("admin-help")
else:
    changed.append("admin-help:already")

SERVER.write_text(t, encoding="utf-8")
r = subprocess.run(["node", "--check", str(SERVER)], capture_output=True, text=True)
print("changed:", ", ".join(changed))
print("check", r.returncode, (r.stderr or "OK")[:600])
if r.returncode != 0:
    raise SystemExit(1)

# ---------------------------------------------------------------------------
# 7) Migrate existing config: split mega-snippet into list
# ---------------------------------------------------------------------------
cfg = json.loads(CONFIG.read_text(encoding="utf-8"))
ads = cfg.get("ad_snippets") or {}
raw_items = []
if str(ads.get("banner_html") or "").strip():
    raw_items.append(str(ads.get("banner_html")).strip())
for item in (ads.get("banner_html_list") or []):
    if str(item or "").strip():
        raw_items.append(str(item).strip())

def expand_py(text: str):
    units = []
    parts = re.split(r"\n\s*---\s*\n", text)
    for chunk in parts:
        chunk = chunk.strip()
        if not chunk:
            continue
        at_units = re.findall(
            r"<script[^>]*>\s*atOptions\s*=\s*\{[\s\S]*?\}\s*;?\s*</script>\s*<script[^>]*src=[\"'][^\"']+/([a-f0-9]+)/invoke\.js[\"'][^>]*>\s*</script>",
            chunk,
            flags=re.I,
        )
        # full match
        full = list(re.finditer(
            r"<script[^>]*>\s*atOptions\s*=\s*\{[\s\S]*?\}\s*;?\s*</script>\s*<script[^>]*src=[\"'][^\"']+/[a-f0-9]+/invoke\.js[\"'][^>]*>\s*</script>",
            chunk,
            flags=re.I,
        ))
        cont = list(re.finditer(
            r"<script[^>]*src=[\"'][^\"']+/([a-f0-9]+)/invoke\.js[\"'][^>]*>\s*</script>\s*<div[^>]*id=[\"']container-\1[\"'][^>]*>\s*</div>",
            chunk,
            flags=re.I,
        ))
        mg = list(re.finditer(
            r"<script[^>]*src=[\"'][^\"']*adskeeper[^\"']*[\"'][^>]*>\s*</script>[\s\S]*?<div[^>]*data-type=[\"']_mgwidget[\"'][^>]*>\s*</div>[\s\S]*?<script[^>]*>\s*\(function\s*\(\s*w\s*,\s*q\s*\)[\s\S]*?</script>",
            chunk,
            flags=re.I,
        ))
        if full or cont or mg:
            for m in full:
                units.append(m.group(0).strip())
            for m in cont:
                units.append(m.group(0).strip())
            for m in mg:
                units.append(m.group(0).strip())
        else:
            units.append(chunk)
    # dedupe
    out, seen = [], set()
    for u in units:
        k = re.sub(r"\s+", " ", u).strip()
        if not k or k in seen:
            continue
        seen.add(k)
        out.append(u)
    return out

expanded = []
seen = set()
for item in raw_items:
    for u in expand_py(item):
        k = re.sub(r"\s+", " ", u).strip()
        if k in seen:
            continue
        seen.add(k)
        expanded.append(u)

print("migrated units:", len(expanded))
for i, u in enumerate(expanded):
    key = re.search(r"key\s*:\s*['\"]([a-f0-9]+)['\"]", u, re.I)
    inv = re.search(r"/([a-f0-9]+)/invoke\.js", u, re.I)
    mg = "adskeeper" in u.lower()
    print(i, "key=", key.group(1) if key else None, "inv=", inv.group(1) if inv else None, "mg=", mg, "len=", len(u))

if len(expanded) >= 2:
    # Keep first as banner_html for floating fallback, rest in list — or clear banner_html duplication
    ads["banner_html"] = expanded[0]
    ads["banner_html_list"] = expanded[1:]
    ads["grid_native_enabled"] = True
    cfg["ad_snippets"] = ads
    CONFIG.write_text(json.dumps(cfg, ensure_ascii=False, indent=2), encoding="utf-8")
    print("config updated")
else:
    print("config unchanged (not enough units)")

print("DONE")
