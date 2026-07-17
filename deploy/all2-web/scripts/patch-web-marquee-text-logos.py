#!/usr/bin/env python3
"""Staggered channel-name marquee + admin ON/OFF for TRING-style text badge logos."""
from pathlib import Path
import subprocess

SERVER = Path("/root/all/all2/server.js")
t = SERVER.read_text(encoding="utf-8")
changed = []


def replace_once(old: str, new: str, label: str, required: bool = True) -> None:
    global t
    if old in t:
        t = t.replace(old, new, 1)
        changed.append(label)
        return
    if required:
        # idempotent checks
        if any(marker in t for marker in (label,)):
            changed.append(f"{label}:skip")
            return
        raise SystemExit(f"FAIL: {label}")


# ---------------------------------------------------------------------------
# 1) normalizeWebUiConfig — add text_badge_logos
# ---------------------------------------------------------------------------
old_webui = """function normalizeWebUiConfig(raw = {}) {
  const source = raw && typeof raw === 'object' ? raw : {};
  const themeRaw = String(source.theme || source.web_theme || source.site_theme || 'classic').trim().toLowerCase();
  const theme = (themeRaw === 'triangolo' || themeRaw === 'triangolo_media' || themeRaw === 'alt') ? 'triangolo' : 'classic';
  const playerStatusText = sanitizeText(
    source.player_status_text ?? source.playerStatusText ?? source.player_footer_text ?? '',
    200
  );
  return { theme, player_status_text: playerStatusText };
}"""
new_webui = """function normalizeWebUiConfig(raw = {}) {
  const source = raw && typeof raw === 'object' ? raw : {};
  const themeRaw = String(source.theme || source.web_theme || source.site_theme || 'classic').trim().toLowerCase();
  const theme = (themeRaw === 'triangolo' || themeRaw === 'triangolo_media' || themeRaw === 'alt') ? 'triangolo' : 'classic';
  const playerStatusText = sanitizeText(
    source.player_status_text ?? source.playerStatusText ?? source.player_footer_text ?? '',
    200
  );
  const textBadgeLogos = source.text_badge_logos === true
    || source.text_badge_logos === 1
    || source.text_badge_logos === '1'
    || source.text_badge_logos === 'on'
    || source.text_badge_logos === 'true'
    || source.textBadgeLogos === true
    || source.tring_style_logos === true
    || source.tringStyleLogos === true;
  return { theme, player_status_text: playerStatusText, text_badge_logos: !!textBadgeLogos };
}"""
if "text_badge_logos" not in t.split("function normalizeWebUiConfig")[1][:900]:
    if old_webui not in t:
        raise SystemExit("FAIL normalizeWebUiConfig")
    t = t.replace(old_webui, new_webui, 1)
    changed.append("normalizeWebUiConfig")
else:
    changed.append("normalizeWebUiConfig:already")

# ---------------------------------------------------------------------------
# 2) Helper + buildCards thumb rendering
# ---------------------------------------------------------------------------
helper = """
function getChannelTextLogoLabel(name = '') {
  return String(name || '')
    .replace(/\\[[^\\]]*\\]/g, ' ')
    .replace(/\\s+/g, ' ')
    .trim()
    .slice(0, 42);
}
function buildChannelThumbMedia(ch = {}, typeIcon = 'tv') {
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
}
"""

if "function buildChannelThumbMedia" not in t:
    marker = "function buildCards(channels, options = {}) {"
    if marker not in t:
        raise SystemExit("FAIL buildCards marker")
    t = t.replace(marker, helper + "\n" + marker, 1)
    changed.append("helper")
else:
    changed.append("helper:already")

old_thumb = """    const logo = String(ch.logo || '').trim();
    const safeLogo = logo.replace(/"/g, '&quot;');
    const thumbMedia = safeLogo
      ? `<img src="${safeLogo}" alt="${ch.name}" style="width:100%;height:100%;object-fit:contain;padding:10px;background:rgba(255,255,255,.03)" onerror="this.remove()">`
      : `<i class="fas fa-${typeIcon}"></i>`;"""
new_thumb = """    const thumbMedia = buildChannelThumbMedia(ch, typeIcon);"""
# Only replace first occurrence inside buildCards (public cards)
if "buildChannelThumbMedia(ch, typeIcon)" not in t.split("function buildCards")[1][:1200]:
    if old_thumb not in t:
        raise SystemExit("FAIL thumbMedia in buildCards")
    t = t.replace(old_thumb, new_thumb, 1)
    changed.append("buildCards-thumb")
else:
    changed.append("buildCards-thumb:already")

# ---------------------------------------------------------------------------
# 3) CSS — text logos + improved marquee keyframes
# ---------------------------------------------------------------------------
old_marquee_css = """.ch-name-marquee{overflow:hidden;white-space:nowrap;width:100%;position:relative}
.ch-name{font-weight:600;font-size:.8rem;white-space:nowrap;display:inline-block;max-width:100%;overflow:hidden;text-overflow:ellipsis;vertical-align:bottom}
.ch-name-marquee.is-overflow .ch-name{
  max-width:none;overflow:visible;text-overflow:clip;
  padding-right:1.6em;
  animation:chNameSlide var(--ch-name-duration,9s) linear infinite;
  will-change:transform;
}
@keyframes chNameSlide{
  0%,15%{transform:translateX(0)}
  85%,100%{transform:translateX(calc(-1 * var(--ch-name-overflow,0px)))}
}
@media (prefers-reduced-motion:reduce){
  .ch-name-marquee.is-overflow .ch-name{animation:none!important;max-width:100%;overflow:hidden;text-overflow:ellipsis}
}"""
new_marquee_css = """.ch-name-marquee{overflow:hidden;white-space:nowrap;width:100%;position:relative}
.ch-name{font-weight:600;font-size:.8rem;white-space:nowrap;display:inline-block;max-width:100%;overflow:hidden;text-overflow:ellipsis;vertical-align:bottom}
.ch-name-marquee.is-overflow .ch-name{
  max-width:none;overflow:visible;text-overflow:clip;
  padding-right:2.2em;
  animation:chNameSlide var(--ch-name-duration,8s) linear infinite;
  animation-delay:var(--ch-name-delay,0s);
  will-change:transform;
}
@keyframes chNameSlide{
  0%{transform:translateX(0)}
  45%{transform:translateX(calc(-1 * var(--ch-name-overflow,0px)))}
  55%{transform:translateX(calc(-1 * var(--ch-name-overflow,0px)))}
  100%{transform:translateX(0)}
}
@media (prefers-reduced-motion:reduce){
  .ch-name-marquee.is-overflow .ch-name{animation:none!important;max-width:100%;overflow:hidden;text-overflow:ellipsis}
}
.ch-text-logo{
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

if "ch-text-logo{" not in t:
    if old_marquee_css not in t:
        # try replace keyframes only if partial
        if "@keyframes chNameSlide{" in t and "ch-text-logo{" not in t:
            old_kf = """@keyframes chNameSlide{
  0%,15%{transform:translateX(0)}
  85%,100%{transform:translateX(calc(-1 * var(--ch-name-overflow,0px)))}
}"""
            new_kf = """@keyframes chNameSlide{
  0%{transform:translateX(0)}
  45%{transform:translateX(calc(-1 * var(--ch-name-overflow,0px)))}
  55%{transform:translateX(calc(-1 * var(--ch-name-overflow,0px)))}
  100%{transform:translateX(0)}
}
.ch-text-logo{
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
            if old_kf not in t:
                raise SystemExit("FAIL marquee css block")
            t = t.replace(old_kf, new_kf, 1)
            # also add animation-delay var if missing
            old_anim = """  animation:chNameSlide var(--ch-name-duration,9s) linear infinite;
  will-change:transform;"""
            new_anim = """  padding-right:2.2em;
  animation:chNameSlide var(--ch-name-duration,8s) linear infinite;
  animation-delay:var(--ch-name-delay,0s);
  will-change:transform;"""
            if old_anim in t:
                t = t.replace(old_anim, new_anim, 1)
            changed.append("marquee-css-partial")
        else:
            raise SystemExit("FAIL marquee css")
    else:
        t = t.replace(old_marquee_css, new_marquee_css, 1)
        changed.append("marquee-css")
else:
    changed.append("marquee-css:already")

# ---------------------------------------------------------------------------
# 4) JS initChannelNameMarquees — random stagger
# ---------------------------------------------------------------------------
old_js = """function initChannelNameMarquees(){
  document.querySelectorAll('.ch-name-marquee').forEach(box=>{
    const name=box.querySelector('.ch-name');
    if(!name) return;
    box.classList.remove('is-overflow');
    name.style.animationDuration='';
    box.style.removeProperty('--ch-name-overflow');
    // Measure full text width without ellipsis clamp
    const prevMax=name.style.maxWidth;
    const prevOverflow=name.style.overflow;
    name.style.maxWidth='none';
    name.style.overflow='visible';
    const full=name.scrollWidth;
    name.style.maxWidth=prevMax;
    name.style.overflow=prevOverflow;
    const overflow=Math.ceil(full-box.clientWidth);
    if(overflow>6){
      box.classList.add('is-overflow');
      box.style.setProperty('--ch-name-overflow', overflow+'px');
      const dur=Math.max(7, Math.min(18, 5+overflow/14));
      box.style.setProperty('--ch-name-duration', dur+'s');
      name.style.animationDuration=dur+'s';
    }
  });
}"""
new_js = """function initChannelNameMarquees(){
  document.querySelectorAll('.ch-name-marquee').forEach((box, idx)=>{
    const name=box.querySelector('.ch-name');
    if(!name) return;
    box.classList.remove('is-overflow');
    name.style.animationDuration='';
    name.style.animationDelay='';
    box.style.removeProperty('--ch-name-overflow');
    box.style.removeProperty('--ch-name-duration');
    box.style.removeProperty('--ch-name-delay');
    // Measure full text width without ellipsis clamp
    const prevMax=name.style.maxWidth;
    const prevOverflow=name.style.overflow;
    const prevWhite=name.style.whiteSpace;
    name.style.maxWidth='none';
    name.style.overflow='visible';
    name.style.whiteSpace='nowrap';
    const full=Math.ceil(name.scrollWidth || name.offsetWidth || 0);
    name.style.maxWidth=prevMax;
    name.style.overflow=prevOverflow;
    name.style.whiteSpace=prevWhite;
    const boxW=Math.max(1, box.clientWidth || box.offsetWidth || 0);
    const overflow=Math.ceil(full-boxW);
    const longText=(name.textContent||'').trim().length>=18;
    if(overflow>4 || longText){
      const travel=Math.max(overflow, Math.ceil(full*0.35), 28);
      box.classList.add('is-overflow');
      box.style.setProperty('--ch-name-overflow', travel+'px');
      const dur=Math.max(5.5, Math.min(14, 4.2+travel/18)) + (Math.random()*2.8);
      // Negative delay => already mid-slide, looks random/casual across cards
      const delay=-(Math.random()*dur);
      box.style.setProperty('--ch-name-duration', dur.toFixed(2)+'s');
      box.style.setProperty('--ch-name-delay', delay.toFixed(2)+'s');
      name.style.animationDuration=dur.toFixed(2)+'s';
      name.style.animationDelay=delay.toFixed(2)+'s';
    }
  });
}"""
if "ch-name-delay" not in t.split("function initChannelNameMarquees")[1][:900]:
    if old_js not in t:
        raise SystemExit("FAIL initChannelNameMarquees")
    t = t.replace(old_js, new_js, 1)
    changed.append("marquee-js")
else:
    changed.append("marquee-js:already")

# Extra delayed re-init so fonts/layout settle (category grids)
if "setTimeout(initChannelNameMarquees, 1200);" in t and "setTimeout(initChannelNameMarquees, 2400);" not in t:
    t = t.replace(
        "setTimeout(initChannelNameMarquees, 1200);",
        "setTimeout(initChannelNameMarquees, 1200);\nsetTimeout(initChannelNameMarquees, 2400);",
        1,
    )
    changed.append("marquee-extra-timeout")

# ---------------------------------------------------------------------------
# 5) Pass text_badge_logos into page webUi (optional, for future client use)
# ---------------------------------------------------------------------------
old_norm = """  normalizedScriptOptions.webUi = {
    theme: webUi.theme || 'classic',
    playerStatusText: webUi.player_status_text || '',
    player_status_text: webUi.player_status_text || ''
  };"""
new_norm = """  normalizedScriptOptions.webUi = {
    theme: webUi.theme || 'classic',
    playerStatusText: webUi.player_status_text || '',
    player_status_text: webUi.player_status_text || '',
    text_badge_logos: !!webUi.text_badge_logos,
    textBadgeLogos: !!webUi.text_badge_logos
  };"""
if "text_badge_logos: !!webUi.text_badge_logos" not in t:
    if old_norm not in t:
        raise SystemExit("FAIL normalizedScriptOptions.webUi")
    t = t.replace(old_norm, new_norm, 1)
    changed.append("script-webUi")
else:
    changed.append("script-webUi:already")

# ---------------------------------------------------------------------------
# 6) Admin Features UI + save
# ---------------------------------------------------------------------------
old_admin = """  <label class="form-label" style="grid-column:1/-1">Player status text (në vend të "buffering / HLS.js...")
    <input type="text" name="web_ui_player_status_text" class="form-input" value="${escapeHtmlAttr(getWebUiSettings().player_status_text || '')}" placeholder="p.sh. Bashkohu: t.me/albview">
  </label>
</div>
</div>"""
new_admin = """  <label class="form-label" style="grid-column:1/-1">Player status text (në vend të "buffering / HLS.js...")
    <input type="text" name="web_ui_player_status_text" class="form-input" value="${escapeHtmlAttr(getWebUiSettings().player_status_text || '')}" placeholder="p.sh. Bashkohu: t.me/albview">
  </label>
  <label class="form-label" style="grid-column:1/-1;display:flex;align-items:center;gap:10px;margin-top:4px">
    <input type="checkbox" name="web_ui_text_badge_logos" value="1" ${getWebUiSettings().text_badge_logos ? 'checked' : ''}>
    <span>Logo text-badge (stil TRING SPORT 3) — ON = katror i kuq me emrin e kanalit; OFF = logo normale</span>
  </label>
</div>
</div>"""
if "web_ui_text_badge_logos" not in t:
    if old_admin not in t:
        raise SystemExit("FAIL admin web ui form")
    t = t.replace(old_admin, new_admin, 1)
    changed.append("admin-form")
else:
    changed.append("admin-form:already")

old_save = """    appConfig.web_ui = normalizeWebUiConfig({
      theme: req.body.web_ui_theme,
      player_status_text: req.body.web_ui_player_status_text
    });"""
new_save = """    appConfig.web_ui = normalizeWebUiConfig({
      theme: req.body.web_ui_theme,
      player_status_text: req.body.web_ui_player_status_text,
      text_badge_logos: readCheckboxField(req.body.web_ui_text_badge_logos)
    });"""
if "text_badge_logos: readCheckboxField(req.body.web_ui_text_badge_logos)" not in t:
    if old_save not in t:
        raise SystemExit("FAIL admin web ui save")
    t = t.replace(old_save, new_save, 1)
    changed.append("admin-save")
else:
    changed.append("admin-save:already")

SERVER.write_text(t, encoding="utf-8")
r = subprocess.run(["node", "--check", str(SERVER)], capture_output=True, text=True)
print("changed:", ", ".join(changed))
print("check", r.returncode, (r.stderr or "OK")[:500])
if r.returncode != 0:
    raise SystemExit(1)
print("DONE")
