#!/usr/bin/env python3
"""Stronger Adsterra go_banner junk scrub (native + floating banner + MutationObserver)."""
from pathlib import Path
import subprocess
import time
import urllib.request
import re

SERVER = Path("/root/all/all2/server.js")
t = SERVER.read_text(encoding="utf-8")

# Replace scrub + finalize with stronger versions + global observer
old_scrub = """function scrubNativeAdJunk(root){
  if(!root) return false;
  const junkRe=/frame_width=|frame_height=|go_banner|формируется/i;
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
    const txt=String(el.textContent||'').replace(/\\s+/g,' ').trim();
    if(!txt || junkRe.test(txt)){
      try{ el.remove(); }catch(e){ el.innerHTML=''; }
    }
  });
  const hasMedia=!!root.querySelector('iframe,img,video,ins,a');
  const remain=String(root.textContent||'').replace(/\\s+/g,' ').trim();
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
}"""

new_scrub = """function isAdsterraJunkText(txt){
  const s=String(txt||'');
  return s.indexOf('frame_width')>=0 || s.indexOf('frame_height')>=0 || s.indexOf('go_banner')>=0 || s.indexOf('формируется')>=0;
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
    const txt=String(el.textContent||'').replace(/[ \\t\\r\\n]+/g,' ').trim();
    if(!txt || isAdsterraJunkText(txt)){
      try{ el.remove(); }catch(e){ el.innerHTML=''; }
    }
  });
  const hasMedia=!!root.querySelector('iframe,img,video,ins,a');
  const remain=String(root.textContent||'').replace(/[ \\t\\r\\n]+/g,' ').trim();
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
  // Also kill orphan junk text nodes under body that Adsterra document.write left behind
  try{
    const walker=document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null);
    const drop=[];
    while(walker.nextNode()){
      const node=walker.currentNode;
      if(!isAdsterraJunkText(node.textContent||'')) continue;
      // keep if inside a script tag source we authored (shouldn't be text node)
      drop.push(node);
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
}"""

if "function isAdsterraJunkText" not in t:
    if old_scrub not in t:
        # try softer match
        if "function scrubNativeAdJunk" not in t:
            raise SystemExit("FAIL: scrub missing")
        start = t.find("function scrubNativeAdJunk(root){")
        end = t.find("\nasync function renderNativeGridAds", start)
        if start < 0 or end < 0:
            raise SystemExit("FAIL: scrub bounds")
        t = t[:start] + new_scrub + t[end:]
        print("scrub replaced via bounds")
    else:
        t = t.replace(old_scrub, new_scrub, 1)
        print("scrub replaced exact")
else:
    print("scrub already strong")

# Patch inline banner to scrub after render
old_banner_end = """  markAdExposure('banner');
  adaptBannerSlotSize(slot, inner);
  slot.style.display='block';
}"""
new_banner_end = """  markAdExposure('banner');
  adaptBannerSlotSize(slot, inner);
  slot.style.display='block';
  try{ scrubAllAdContainers(); }catch(e){}
  setTimeout(function(){ try{ scrubAllAdContainers(); }catch(e){} }, 900);
  setTimeout(function(){ try{ scrubAllAdContainers(); }catch(e){} }, 2500);
}"""
if "scrubAllAdContainers(); }catch(e){} }, 2500);" not in t.split("async function renderInlineBannerAd")[1][:2500]:
    # only first occurrence after renderInlineBannerAd
    idx = t.find("async function renderInlineBannerAd")
    if idx < 0:
        raise SystemExit("FAIL banner")
    # find the markAdExposure block within this function (before scrubNativeAdJunk)
    end_fn = t.find("\nfunction scrubNativeAdJunk", idx)
    if end_fn < 0:
        end_fn = t.find("\nfunction isAdsterraJunkText", idx)
    chunk = t[idx:end_fn if end_fn > idx else idx + 3500]
    if old_banner_end not in chunk:
        print("WARN banner end not found")
    else:
        new_chunk = chunk.replace(old_banner_end, new_banner_end, 1)
        t = t[:idx] + new_chunk + t[idx + len(chunk):]
        print("banner scrub hooked")
else:
    print("banner scrub already")

# Start observer near renderNativeGridAds init timeouts
if "startAdJunkObserver()" not in t:
    old_init = "setTimeout(renderNativeGridAds, 240);"
    new_init = "setTimeout(renderNativeGridAds, 240);\nsetTimeout(startAdJunkObserver, 260);"
    if old_init not in t:
        print("WARN init timeout")
    else:
        t = t.replace(old_init, new_init, 1)
        print("observer boot hooked")
else:
    print("observer already")

# CSS for floating banner junk too
if ".ad-banner-row{color:transparent" not in t:
    old_css = """.native-ad-body{color:transparent;font-size:0;line-height:0}
.native-ad-body iframe,.native-ad-body img,.native-ad-body video,.native-ad-body ins,.native-ad-body a,.native-ad-body [id^="container-"]{color:initial;font-size:initial;line-height:normal}"""
    new_css = """.native-ad-body,.ad-banner-row,#inlineBannerInner{color:transparent;font-size:0;line-height:0}
.native-ad-body iframe,.native-ad-body img,.native-ad-body video,.native-ad-body ins,.native-ad-body a,.native-ad-body [id^="container-"],
.ad-banner-row iframe,.ad-banner-row img,.ad-banner-row video,.ad-banner-row ins,.ad-banner-row a,.ad-banner-row [id^="container-"],
#inlineBannerInner iframe,#inlineBannerInner img,#inlineBannerInner video,#inlineBannerInner ins,#inlineBannerInner a,#inlineBannerInner [id^="container-"]{color:initial;font-size:initial;line-height:normal}"""
    if old_css in t:
        t = t.replace(old_css, new_css, 1)
        print("css expanded")
    else:
        print("css WARN")
else:
    print("css already")

SERVER.write_text(t, encoding="utf-8")
r = subprocess.run(["node", "--check", str(SERVER)], capture_output=True, text=True)
print("check", r.returncode, (r.stderr or "OK")[:400])
if r.returncode != 0:
    raise SystemExit(1)

subprocess.check_call(["pm2", "reload", "all2", "--update-env"])
time.sleep(5)
html = urllib.request.urlopen("http://127.0.0.1:3000/k1/").read().decode("utf-8", "ignore")
print("isAdsterraJunkText", "isAdsterraJunkText" in html)
print("startAdJunkObserver", "startAdJunkObserver" in html)
print("scrubAllAdContainers", "scrubAllAdContainers" in html)
scripts = re.findall(r"<script>([\s\S]*?)</script>", html)
big = max(scripts, key=len)
open("/tmp/page-check4.js", "w", encoding="utf-8").write(big)
r = subprocess.run(["node", "--check", "/tmp/page-check4.js"], capture_output=True, text=True)
print("page syntax", r.returncode, (r.stderr or "OK")[:400])
print("DONE")
