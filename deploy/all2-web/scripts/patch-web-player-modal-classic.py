#!/usr/bin/env python3
"""Fix Triangolo player to open as classic full-screen modal overlay."""
from pathlib import Path
import subprocess

SERVER = Path("/root/all/all2/server.js")
t = SERVER.read_text(encoding="utf-8")

old = """body.theme-triangolo nav,
body.theme-triangolo .page,
body.theme-triangolo #playerModal{position:relative;z-index:1}"""

new = """body.theme-triangolo nav,
body.theme-triangolo .page{position:relative;z-index:1}
/* Keep classic full-screen modal (not inline on top of homepage). */
body.theme-triangolo #playerModal.modal-overlay,
body.theme-triangolo #playerModal{
  position:fixed !important;
  inset:0 !important;
  z-index:1200 !important;
  display:none;
  align-items:center;
  justify-content:center;
  background:rgba(0,0,0,.92);
  backdrop-filter:blur(10px);
  -webkit-backdrop-filter:blur(10px);
  padding:12px;
}
body.theme-triangolo #playerModal.modal-overlay.open,
body.theme-triangolo #playerModal.open{
  display:flex !important;
}"""

if old not in t:
    # maybe already partially fixed
    if "body.theme-triangolo #playerModal.modal-overlay" in t:
        print("already fixed")
    else:
        raise SystemExit("FAIL: z-index rule not found")
else:
    t = t.replace(old, new, 1)

# Strengthen modal panel sizing like classic
old_modal = """body.theme-triangolo #playerModal .modal,body.theme-triangolo .modal{
  border-radius:22px;overflow:hidden;
  background:rgba(12,16,28,.72);border:1px solid rgba(255,255,255,.16);
  backdrop-filter:blur(24px) saturate(160%);-webkit-backdrop-filter:blur(24px) saturate(160%);
  box-shadow:0 24px 60px rgba(0,0,0,.45), inset 0 1px 0 rgba(255,255,255,.16);
}"""
new_modal = """body.theme-triangolo #playerModal .modal,body.theme-triangolo .modal{
  width:min(960px,96vw);max-height:92vh;overflow:auto;
  border-radius:22px;
  background:rgba(12,16,28,.88);border:1px solid rgba(255,255,255,.16);
  backdrop-filter:blur(24px) saturate(160%);-webkit-backdrop-filter:blur(24px) saturate(160%);
  box-shadow:0 24px 60px rgba(0,0,0,.45), inset 0 1px 0 rgba(255,255,255,.16);
}
body.theme-triangolo #playerModal .player-stage,body.theme-triangolo #playerModal video{
  width:100%;min-height:220px;background:#000;
}"""
if old_modal in t:
    t = t.replace(old_modal, new_modal, 1)
elif "width:min(960px,96vw)" in t:
    print("modal size already set")
else:
    print("WARN: modal style block not found exactly")

SERVER.write_text(t, encoding="utf-8")
r = subprocess.run(["node", "--check", str(SERVER)], capture_output=True, text=True)
print("node check:", r.returncode, (r.stderr or "OK")[:300])
if r.returncode != 0:
    raise SystemExit(1)
print("patched")
