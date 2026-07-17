#!/usr/bin/env python3
import json, urllib.request, re, subprocess, time

cfg_path = "/root/all/all2/data/channel_config.json"

def set_flag(on: bool):
    cfg = json.load(open(cfg_path))
    ui = cfg.get("web_ui") or {}
    ui["text_badge_logos"] = bool(on)
    cfg["web_ui"] = ui
    json.dump(cfg, open(cfg_path, "w"), ensure_ascii=False, indent=2)
    subprocess.check_call(["pm2", "reload", "all2"], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(4)

def fetch():
    return urllib.request.urlopen("http://127.0.0.1:3000/k1/category/custom/sport").read().decode("utf-8", "ignore")

set_flag(True)
html = fetch()
print("ON text-logo", html.count("ch-text-logo"))
print("ON img-thumb", len(re.findall(r'ch-thumb"><img', html)))
m = re.search(r'ch-text-logo[^>]*>.*?<span>([^<]+)</span>', html)
print("ON sample", m.group(1) if m else None)
print("marquee random", "ch-name-delay" in html, ("Math.random()" in html))

set_flag(False)
html = fetch()
print("OFF text-logo", html.count("ch-text-logo"))
print("OFF img-thumb", len(re.findall(r'ch-thumb"><img', html)))
print("web_ui", json.load(open(cfg_path)).get("web_ui"))
print("OK")
