#!/usr/bin/env python3
"""Follow-up: native grid independent of banner flags; purge popup scripts from config."""
from __future__ import annotations

import json
from pathlib import Path

SERVER = Path("/root/all/all2/server.js")
CONFIG = Path("/root/all/all2/data/channel_config.json")

NATIVE_AADS = """<!-- BEGIN AADS AD UNIT 2437050 -->
<div id="frame" style="width: 100%;margin: auto;position: relative; z-index: 99998;">
  <iframe data-aa="2437050" src="//acceptable.a-ads.com/2437050/?size=Adaptive"
    style="border:0; padding:0; width:70%; height:auto; overflow:hidden;display: block;margin: auto"></iframe>
</div>
<!-- END AADS AD UNIT 2437050 -->"""


def main() -> None:
    t = SERVER.read_text(encoding="utf-8")
    old = (
        "  const allowNativeByPage = (pageType === 'main' && adSettings.show_banner_on_main !== false)\n"
        "    || (pageType.startsWith('category') && adSettings.show_banner_on_category !== false);"
    )
    new = (
        "  // Native grid is independent from floating/inline banner placement flags.\n"
        "  const allowNativeByPage = pageType === 'main' || pageType.startsWith('category');"
    )
    count = t.count(old)
    if count != 1:
        # try CRLF
        old2 = old.replace("\n", "\r\n")
        if t.count(old2) == 1:
            old = old2
            new = new.replace("\n", "\r\n")
            count = 1
        else:
            raise SystemExit(f"allowNativeByPage expected 1, found {count}")
    SERVER.write_text(t.replace(old, new, 1), encoding="utf-8")
    print("server.js: allowNativeByPage fixed")

    c = json.loads(CONFIG.read_text(encoding="utf-8"))
    ads = c.get("ad_snippets") or {}
    existing_native = str(ads.get("channel_open_gate_native_script") or ads.get("banner_html") or "")
    native = existing_native if "a-ads.com" in existing_native else NATIVE_AADS
    ads.update(
        {
            "banner_html": native,
            "banner_html_list": [],
            "interstitial_html": "",
            "interstitial_html_list": [],
            "enabled": True,
            "grid_native_enabled": True,
            "grid_native_every": max(2, int(ads.get("grid_native_every") or 2)),
            "show_banner_on_main": False,
            "show_banner_on_category": False,
            "hls_popunder_enabled": False,
            "hls_popunder_script_url": "",
            "hls_popunder_fallback_url": "",
            "channel_open_gate_enabled": False,
            "channel_open_gate_banner_script": "",
            "channel_open_gate_native_script": native,
            "channel_open_gate_smartlink_url": "",
            "channel_open_gate_native_url": "",
            "channel_open_gate_banner_url": "",
            "channel_open_gate_use_banner": False,
            "channel_open_gate_use_native": True,
            "channel_open_gate_use_smartlink": False,
        }
    )
    c["ad_snippets"] = ads
    ui = c.get("web_ui") if isinstance(c.get("web_ui"), dict) else {}
    c["web_ui"] = {
        "theme": ui.get("theme") or "classic",
        "player_status_text": ui.get("player_status_text")
        or "Bashkohu në Telegram: t.me/albview",
    }
    CONFIG.write_text(json.dumps(c, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    blob = json.dumps(c)
    print("industriousslowly in config:", "industriousslowly" in blob)
    print("adskeeper in config:", "adskeeper" in blob)
    print("a-ads in config:", "a-ads.com" in blob)
    print("DONE")


if __name__ == "__main__":
    main()
