#!/usr/bin/env python3
from pathlib import Path
import subprocess
import time
import urllib.request
import re

p = Path("/root/all/all2/server.js")
t = p.read_text(encoding="utf-8")

old = """    try{ await executeHtmlSnippet(node, html); }catch(e){}
    sendAdEvent('impression',{ source_key:'snippet_banner_grid', kind:'snippet_banner_grid' });
    const onClick=()=>sendAdEvent('click',{ source_key:'snippet_banner_grid', kind:'snippet_banner_grid' });
    node.addEventListener('click', onClick, { once:true });
    await new Promise(r=>setTimeout(r, 220));
  }
}
function getInterstitialStorageKey(){"""

new = """    try{ await executeHtmlSnippet(node, html); }catch(e){}
    // Hide Adsterra empty-fill junk text (frame_width / go_banner)
    try{ scrubNativeAdJunk(node); }catch(e){}
    setTimeout(function(){ try{ finalizeNativeAdSlot(node); }catch(e){} }, 900);
    setTimeout(function(){ try{ finalizeNativeAdSlot(node); }catch(e){} }, 2500);
    sendAdEvent('impression',{ source_key:'snippet_banner_grid', kind:'snippet_banner_grid' });
    const onClick=()=>sendAdEvent('click',{ source_key:'snippet_banner_grid', kind:'snippet_banner_grid' });
    node.addEventListener('click', onClick, { once:true });
    await new Promise(r=>setTimeout(r, 220));
  }
}
function getInterstitialStorageKey(){"""

if "Hide Adsterra empty-fill junk" not in t:
    if old not in t:
        raise SystemExit("FAIL loop")
    t = t.replace(old, new, 1)
    print("loop patched")
else:
    print("loop already")

old_re = "const junkRe=/frame_width\\s*=|frame_height\\s*=|go_banner|формируется/i;"
new_re = "const junkRe=/frame_width=|frame_height=|go_banner|формируется/i;"
if old_re in t:
    t = t.replace(old_re, new_re, 1)
    print("regex fixed")
elif "frame_width=" in t[t.find("function scrubNativeAdJunk"):t.find("function scrubNativeAdJunk")+400]:
    print("regex ok")
else:
    print("regex WARN")

p.write_text(t, encoding="utf-8")
r = subprocess.run(["node", "--check", str(p)], capture_output=True, text=True)
print("check", r.returncode, (r.stderr or "OK")[:400])
if r.returncode != 0:
    raise SystemExit(1)

subprocess.check_call(["pm2", "reload", "all2", "--update-env"])
time.sleep(5)

html = urllib.request.urlopen("http://127.0.0.1:3000/k1/").read().decode("utf-8", "ignore")
print("scrub", "scrubNativeAdJunk" in html)
print("finalize calls", html.count("finalizeNativeAdSlot"))
print("requireGoogleLogin", "requireGoogleLogin" in html)
print("ensureGoogle", "ensureGoogleLoginForChannel" in html)
print("ad-empty css", "data-ad-empty" in html)
scripts = re.findall(r"<script>([\s\S]*?)</script>", html)
big = max(scripts, key=len)
open("/tmp/page-check2.js", "w", encoding="utf-8").write(big)
r = subprocess.run(["node", "--check", "/tmp/page-check2.js"], capture_output=True, text=True)
print("page syntax", r.returncode, (r.stderr or "OK")[:400])
i = html.find("const pageOptions=")
print("pageOptions requireGoogle", "requireGoogleLogin" in html[i:i + 80000])
print("OK")
