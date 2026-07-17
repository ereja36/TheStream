#!/usr/bin/env python3
from pathlib import Path
import subprocess, time, urllib.request, re

p = Path("/root/all/all2/server.js")
t = p.read_text(encoding="utf-8")
start = t.find("async function promptGoogleOrBypassOnce(){")
end = t.find("\nasync function bootstrapFirebaseAccountRuntime", start)
if start < 0 or end < 0:
    raise SystemExit("FAIL bounds")
print("before", repr(t[start:end][:220]))

# File is embedded in a template literal. To emit \n to the browser, store \\n in this file.
new_prompt = (
    "async function promptGoogleOrBypassOnce(){\n"
    "  if(firebaseAuthConfig.enabled!==true) return;\n"
    "  if(accountSessionToken) return;\n"
    "  if(isGoogleLoginRequired()){\n"
    "    try{ accountBypassMode=false; localStorage.removeItem('k1_account_bypass_mode'); }catch(e){}\n"
    "    if(accountChoicePrompted===true) return;\n"
    "    accountChoicePrompted=true;\n"
    "    try{ localStorage.setItem('k1_account_choice_prompted','1'); }catch(e){}\n"
    "    try{ openAccountMenu(); }catch(e){}\n"
    "    return;\n"
    "  }\n"
    "  if(accountBypassMode===true) return;\n"
    "  if(accountChoicePrompted===true) return;\n"
    "  accountChoicePrompted=true;\n"
    "  localStorage.setItem('k1_account_choice_prompted','1');\n"
    "  const useGoogle=confirm('Hyr me Google per sync/premium?\\\\nOK = Google Login\\\\nCancel = Bypass');\n"
    "  if(useGoogle){\n"
    "    await signInWithGoogleAccount();\n"
    "  }else{\n"
    "    enableBypassMode();\n"
    "  }\n"
    "}"
)

t = t[:start] + new_prompt + t[end:]
p.write_text(t, encoding="utf-8")
r = subprocess.run(["node", "--check", str(p)], capture_output=True, text=True)
print("server check", r.returncode, (r.stderr or "OK")[:300])
if r.returncode != 0:
    raise SystemExit(1)

subprocess.check_call(["pm2", "reload", "all2", "--update-env"])
time.sleep(5)
html = urllib.request.urlopen("http://127.0.0.1:3000/k1/").read().decode("utf-8", "ignore")
idx = html.find("const useGoogle=confirm(")
print("confirm line", repr(html[idx:idx + 120]))
scripts = re.findall(r"<script>([\s\S]*?)</script>", html)
big = max(scripts, key=len)
open("/tmp/page-check3.js", "w", encoding="utf-8").write(big)
r = subprocess.run(["node", "--check", "/tmp/page-check3.js"], capture_output=True, text=True)
print("page syntax", r.returncode, (r.stderr or "OK")[:400])
print("scrub", "finalizeNativeAdSlot" in html, "gmail", "ensureGoogleLoginForChannel" in html)
