#!/usr/bin/env python3
from pathlib import Path

p = Path("/root/all/all2/server.js")
t = p.read_text(encoding="utf-8")
start = t.find("function buildA3PlaybackUrl(streamUrl = '', options = {}) {")
if start < 0:
    raise SystemExit("not found")
end = t.find("\n}", start)
if end < 0:
    raise SystemExit("end not found")
end += 2
old = t[start:end]
print("OLD:\n", old)
new = """function buildA3PlaybackUrl(streamUrl = '', options = {}) {
  let url = String(streamUrl || '').trim();
  if (!/^https?:\\/\\//i.test(url)) return url;
  // A3 VOD (and any preferDirect): return CDN URL; rewrite .mp4/index.m3u8 → .mp4.
  if (options.preferDirect === true) {
    url = url.replace(/\\.mp4\\/index\\.m3u8(?=\\?|$)/i, '.mp4');
    return url;
  }
  if (options.preferClientSw === true) return wrapA3ClientSwUrl(url, options);
  if (options.preferFastlyRelay === true) return wrapA3FastlyRelayUrl(url, options);
  return wrapA3CorsProxyUrl(url, options);
}"""
p.write_text(t[:start] + new + t[end:], encoding="utf-8")
print("patched")
