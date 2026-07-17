#!/usr/bin/env python3
"""Fix frozen page: Ad junk MutationObserver caused infinite DOM loop → clicks dead."""
from pathlib import Path
import subprocess, time, urllib.request, re

p = Path("/root/all/all2/server.js")
t = p.read_text(encoding="utf-8")

old = """function scrubAllAdContainers(){
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
}"""

new = """function scrubAllAdContainers(){
  if(window.__adJunkScrubbing) return;
  window.__adJunkScrubbing=true;
  try{
    document.querySelectorAll('.native-ad-body, .ad-banner-row, #inlineBannerInner, .category-ad-slot').forEach(node=>{
      try{ finalizeNativeAdSlot(node); }catch(e){}
    });
    // Only scan ad containers for junk text — never whole document.body (too aggressive).
    document.querySelectorAll('.native-ad-body, .ad-banner-row, #inlineBannerInner, .category-ad-slot, .ad-banner-slot').forEach(root=>{
      try{
        const walker=document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null);
        const drop=[];
        while(walker.nextNode()){
          const node=walker.currentNode;
          if(isAdsterraJunkText(node.textContent||'')) drop.push(node);
        }
        drop.forEach(node=>{ try{ node.textContent=''; }catch(e){} });
      }catch(e){}
    });
  }finally{
    window.__adJunkScrubbing=false;
  }
}
function startAdJunkObserver(){
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

if "do NOT use MutationObserver here" not in t:
    if old not in t:
        # replace by bounds
        start = t.find("function scrubAllAdContainers(){")
        end = t.find("\nasync function renderNativeGridAds", start)
        if start < 0 or end < 0:
            raise SystemExit("FAIL bounds")
        t = t[:start] + new + t[end:]
        print("replaced via bounds")
    else:
        t = t.replace(old, new, 1)
        print("replaced exact")
else:
    print("already fixed observer")

# Also fix parental pin regex \d eaten by template literal ( /^d{4}$/ → should be 4 digits )
# Inside template literal, \\d becomes \d in output
old_pin = "if(!/^d{4}$/.test(pin))"
# In the FILE currently (after template already written as source inside backticks):
# The source has `/^d{4}$/` literally (backslash already eaten when someone wrote `\d` once).
# To emit `\d` to browser from template literal we need `\\d` in the server.js file.

count = t.count("if(!/^d{4}$/.test(pin))")
print("bad pin regex count in file", count)
# In server.js template, the page script is inside return `...`.
# Current broken browser output is /^d{4}$/
# File content that produces that is: if(!/^d{4}$/.test(pin))  OR if(!/^\\d{4}$/.test(pin)) wait
# If file has `/^\\d{4}$/` then template emits `/^\d{4}$/` to browser - CORRECT
# If file has `/^d{4}$/` then browser gets `/^d{4}$/` - BROKEN
# If file has `/^\\\\d{4}$/` then browser gets `/^\\d{4}$/` - also wrong for regex

# Check what's in the FILE (server.js):
import re as _re
print("file samples", _re.findall(r"if\(!/\^[\\]*d\{4\}\$/\.test\(pin\)\)", t)[:5])

# Fix: replace broken `/^d{4}$/` with `/^[0-9]{4}$/` which needs no backslash escapes
if "/^d{4}$/" in t:
    t = t.replace("/^d{4}$/", "/^[0-9]{4}$/")
    print("fixed pin regex to [0-9]{4}")
elif r"/^\d{4}$/" in t:
    # already has \d in file - inside template that becomes /d{4}/ - still broken!
    # Check if we're inside template - yes player script is. So \d in file → d in browser.
    t = t.replace(r"/^\d{4}$/", "/^[0-9]{4}$/")
    print("replaced \\d pin regex with [0-9]")
else:
    print("pin regex check", "/^[0-9]{4}$/" in t)

p.write_text(t, encoding="utf-8")
r = subprocess.run(["node", "--check", str(p)], capture_output=True, text=True)
print("check", r.returncode, (r.stderr or "OK")[:400])
if r.returncode != 0:
    raise SystemExit(1)

subprocess.check_call(["pm2", "reload", "all2", "--update-env"])
time.sleep(5)
html = urllib.request.urlopen("http://127.0.0.1:3000/k1/").read().decode("utf-8", "ignore")
print("has MutationObserver in junk?", "MutationObserver" in html[html.find("startAdJunkObserver"):html.find("startAdJunkObserver")+500] if "startAdJunkObserver" in html else None)
print("has do NOT use", "do NOT use MutationObserver" in html)
print("pin regex page", _re.findall(r"/\^[^\n]{0,20}\$/", html[html.find("ensureParentalAccess"):html.find("ensureParentalAccess")+800]) if "ensureParentalAccess" in html else None)
scripts = re.findall(r"<script>([\s\S]*?)</script>", html)
big = max(scripts, key=len)
open("/tmp/page-fix-click.js", "w", encoding="utf-8").write(big)
r = subprocess.run(["node", "--check", "/tmp/page-fix-click.js"], capture_output=True, text=True)
print("page syntax", r.returncode, (r.stderr or "OK")[:300])
print("DONE")
