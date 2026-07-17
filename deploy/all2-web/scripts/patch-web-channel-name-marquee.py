#!/usr/bin/env python3
"""Channel name marquee/slide for long titles (right → left)."""
from pathlib import Path
import subprocess

SERVER = Path("/root/all/all2/server.js")
t = SERVER.read_text(encoding="utf-8")

# 1) Card HTML
old_html = """<div class="ch-info"><div class="ch-name">${ch.name}</div></div>"""
new_html = """<div class="ch-info"><div class="ch-name-marquee" title="${escapeHtmlAttr(ch.name || '')}"><span class="ch-name">${escapeHtml(ch.name || '')}</span></div></div>"""
if old_html not in t:
    if "ch-name-marquee" in t:
        print("html already patched")
    else:
        raise SystemExit("FAIL card html")
else:
    t = t.replace(old_html, new_html, 1)
    print("html patched")

# 2) Base .ch-name CSS — remove ellipsis, prepare marquee
old_base = """.ch-info{padding:8px 10px}
.ch-name{font-weight:600;font-size:.8rem;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}"""
new_base = """.ch-info{padding:8px 10px}
.ch-name-marquee{overflow:hidden;white-space:nowrap;width:100%;position:relative}
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
if "chNameSlide" not in t:
    if old_base not in t:
        raise SystemExit("FAIL base css")
    t = t.replace(old_base, new_base, 1)
    print("base css patched")
else:
    print("base css already")

# 3) Triangolo override
old_tri = """body.theme-triangolo .ch-info{padding:9px 9px 11px;background:rgba(0,0,0,.18);border-top:1px solid rgba(255,255,255,.08)}
body.theme-triangolo .ch-name{font-size:.68rem;font-weight:700;text-transform:uppercase;letter-spacing:.02em;color:#f7f8ff}"""
new_tri = """body.theme-triangolo .ch-info{padding:9px 9px 11px;background:rgba(0,0,0,.18);border-top:1px solid rgba(255,255,255,.08)}
body.theme-triangolo .ch-name-marquee{
  mask-image:linear-gradient(90deg,transparent 0,#000 10%,#000 88%,transparent 100%);
  -webkit-mask-image:linear-gradient(90deg,transparent 0,#000 10%,#000 88%,transparent 100%);
}
body.theme-triangolo .ch-name{font-size:.68rem;font-weight:700;text-transform:uppercase;letter-spacing:.02em;color:#f7f8ff}"""
if "ch-name-marquee{" not in t.split("theme-triangolo .ch-info")[1][:400]:
    if old_tri not in t:
        print("WARN triangolo css not exact")
    else:
        t = t.replace(old_tri, new_tri, 1)
        print("triangolo css patched")
else:
    print("triangolo css already")

# 4) JS init — near filterChannels
old_filter = """function filterChannels(q){
  document.querySelectorAll('.ch-card').forEach(c=>{
    c.style.display=c.querySelector('.ch-name').textContent.toLowerCase().includes(q.toLowerCase())?'':'none';
  });
}"""
new_filter = """function initChannelNameMarquees(){
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
}
function filterChannels(q){
  const needle=String(q||'').toLowerCase();
  document.querySelectorAll('.ch-card').forEach(c=>{
    const nameNode=c.querySelector('.ch-name');
    const label=nameNode?nameNode.textContent:(c.getAttribute('data-slug')||'');
    c.style.display=label.toLowerCase().includes(needle)?'':'none';
  });
  initChannelNameMarquees();
}
if(document.readyState==='loading') document.addEventListener('DOMContentLoaded', initChannelNameMarquees);
else initChannelNameMarquees();
window.addEventListener('resize', ()=>{ try{ initChannelNameMarquees(); }catch(e){} });
setTimeout(initChannelNameMarquees, 400);
setTimeout(initChannelNameMarquees, 1200);"""
if "initChannelNameMarquees" not in t:
    if old_filter not in t:
        raise SystemExit("FAIL filterChannels")
    t = t.replace(old_filter, new_filter, 1)
    print("js patched")
else:
    print("js already")

# Also refresh marquee when favorites section updates
if "updateFavSection" in t and "initChannelNameMarquees()" not in t[t.find("function updateFavSection"):t.find("function updateFavSection")+800]:
    old_fav = """  if(favCards.length){sec.style.display='block';grid.innerHTML=favCards.join('');}
  else{sec.style.display='none';}
}"""
    new_fav = """  if(favCards.length){sec.style.display='block';grid.innerHTML=favCards.join('');}
  else{sec.style.display='none';}
  try{initChannelNameMarquees();}catch(e){}
}"""
    if old_fav in t:
        t = t.replace(old_fav, new_fav, 1)
        print("fav refresh patched")

SERVER.write_text(t, encoding="utf-8")
r = subprocess.run(["node", "--check", str(SERVER)], capture_output=True, text=True)
print("check", r.returncode, (r.stderr or "OK")[:400])
if r.returncode != 0:
    raise SystemExit(1)
print("DONE")
