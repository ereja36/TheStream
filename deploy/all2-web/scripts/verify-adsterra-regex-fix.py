#!/usr/bin/env python3
import urllib.request, re, subprocess, time

subprocess.check_call(["pm2", "reload", "all2", "--update-env"])
time.sleep(5)

html = urllib.request.urlopen("http://127.0.0.1:3000/k1/").read().decode("utf-8", "ignore")
idx = html.find("function executeHtmlSnippet")
print("idx", idx)
chunk = html[idx:idx + 2800]
print(chunk[:700])
print("has extractAdsterraMeta", "extractAdsterraMeta" in chunk)
print("broken keyMatch s*:", "key['\"]s*:" in chunk)
print("slots", len(re.findall(r'data-ad-snippet="', html)))

scripts = re.findall(r"<script>([\s\S]*?)</script>", html)
big = max(scripts, key=len) if scripts else ""
path = "/tmp/page-script-check.js"
open(path, "w", encoding="utf-8").write(big)
r = subprocess.run(["node", "--check", path], capture_output=True, text=True)
print("syntax", r.returncode)
print((r.stderr or "OK")[:1000])
print("renderNativeGridAds", "renderNativeGridAds" in big)
print("Sequential", "Sequential: Adsterra" in big)
