#!/usr/bin/env python3
import urllib.request, re, json, base64, subprocess, time

subprocess.check_call(["pm2", "reload", "all2", "--update-env"])
time.sleep(5)

html = urllib.request.urlopen("http://127.0.0.1:3000/k1/").read().decode("utf-8", "ignore")
snips = re.findall(r'data-ad-snippet="([^"]+)"', html)
print("sponsored slots", len(snips))
seen = set()
for i, enc in enumerate(snips):
    raw = base64.b64decode(enc).decode("utf-8", "ignore")
    inv = re.search(r"/([a-f0-9]+)/invoke\.js", raw, re.I)
    key = re.search(r"['\"]key['\"]\s*:\s*['\"]([a-f0-9]+)['\"]", raw, re.I)
    mg = "adskeeper" in raw.lower() or "_mgwidget" in raw
    ident = (inv.group(1) if inv else "") + ("mg" if mg else "")
    print(i, "inv", inv.group(1) if inv else None, "key", key.group(1) if key else None, "mg", mg, "unique", ident not in seen)
    seen.add(ident)
print("sequential marker", "Sequential: Adsterra" in html)
print("isolate marker", "Adsterra: isolate one unit" in html)
cfg = json.load(open("/root/all/all2/data/channel_config.json"))
ads = cfg["ad_snippets"]
print("config list", len(ads.get("banner_html_list") or []), "banner_html", bool(ads.get("banner_html")))
print("OK" if len(snips) >= 2 and len(seen) == len(snips) else "CHECK")
