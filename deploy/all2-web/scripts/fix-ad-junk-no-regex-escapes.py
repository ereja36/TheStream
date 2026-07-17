#!/usr/bin/env python3
from pathlib import Path
import subprocess, time, urllib.request, re

p = Path("/root/all/all2/server.js")
t = p.read_text(encoding="utf-8")
start = t.find("function isAdsterraJunkText(txt){")
if start < 0:
    start = t.find("function scrubNativeAdJunk(root){")
end = t.find("\nasync function renderNativeGridAds", start)
if start < 0 or end < 0:
    raise SystemExit(f"FAIL bounds {start} {end}")

# NO regex escapes — this code lives inside a JS template literal.
new = r'''function isAdsterraJunkText(txt){
  const s=String(txt||'');
  return s.indexOf('frame_width')>=0 || s.indexOf('frame_height')>=0 || s.indexOf('go_banner')>=0 || s.indexOf('формируется')>=0;
}
function collapseAdText(s){
  let out='', prev=false;
  const str=String(s||'');
  for(let i=0;i<str.length;i++){
    const code=str.charCodeAt(i);
    const ws=code===32||code===9||code===10||code===13;
    if(ws){ if(!prev){ out+=' '; prev=true; } }
    else { out+=str.charAt(i); prev=false; }
  }
  return out.trim();
}
function scrubNativeAdJunk(root){
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
}
function scrubAllAdContainers(){
  document.querySelectorAll('.native-ad-body, .ad-banner-row, #inlineBannerInner, .category-ad-slot').forEach(node=>{
    try{ finalizeNativeAdSlot(node); }catch(e){}
  });
  try{
    const walker=document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null);
    const drop=[];
    while(walker.nextNode()){
      const node=walker.currentNode;
      if(isAdsterraJunkText(node.textContent||'')) drop.push(node);
    }
    drop.forEach(node=>{ try{ node.textContent=''; }catch(e){} });
  }catch(e){}
}
function startAdJunkObserver(){
  if(window.__adJunkObserverStarted) return;
  window.__adJunkObserverStarted=true;
  try{
    const obs=new MutationObserver(function(){
      try{ scrubAllAdContainers(); }catch(e){}
    });
    obs.observe(document.documentElement,{ childList:true, subtree:true, characterData:true });
  }catch(e){}
  scrubAllAdContainers();
  setTimeout(scrubAllAdContainers, 500);
  setTimeout(scrubAllAdContainers, 1500);
  setTimeout(scrubAllAdContainers, 4000);
  setTimeout(scrubAllAdContainers, 8000);
}
'''

t = t[:start] + new + t[end:]

# Ensure observer boot
if "startAdJunkObserver()" not in t:
    if "setTimeout(renderNativeGridAds, 240);" in t:
        t = t.replace("setTimeout(renderNativeGridAds, 240);", "setTimeout(renderNativeGridAds, 240);\nsetTimeout(startAdJunkObserver, 260);", 1)
        print("observer boot added")
else:
    # may already be there from partial
    if "setTimeout(startAdJunkObserver" not in t and "setTimeout(renderNativeGridAds, 240);" in t:
        t = t.replace("setTimeout(renderNativeGridAds, 240);", "setTimeout(renderNativeGridAds, 240);\nsetTimeout(startAdJunkObserver, 260);", 1)
        print("observer boot added2")
    else:
        print("observer boot ok", "startAdJunkObserver" in t)

p.write_text(t, encoding="utf-8")
r = subprocess.run(["node", "--check", str(p)], capture_output=True, text=True)
print("server", r.returncode, (r.stderr or "OK")[:300])
if r.returncode != 0:
    raise SystemExit(1)

subprocess.check_call(["pm2", "reload", "all2", "--update-env"])
time.sleep(5)
html = urllib.request.urlopen("http://127.0.0.1:3000/k1/").read().decode("utf-8", "ignore")
scripts = re.findall(r"<script>([\s\S]*?)</script>", html)
big = max(scripts, key=len)
open("/tmp/page-check5.js", "w", encoding="utf-8").write(big)
r = subprocess.run(["node", "--check", "/tmp/page-check5.js"], capture_output=True, text=True)
print("page", r.returncode, (r.stderr or "OK")[:400])
print("markers", "collapseAdText" in html, "startAdJunkObserver" in html, "ensureGoogleLoginForChannel" in html)
print("DONE")
