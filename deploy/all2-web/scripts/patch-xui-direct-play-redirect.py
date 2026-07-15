#!/usr/bin/env python3
"""XUI Direct: keep hard-302 to /play/ (client auth), mark URL for older app builds.

Portal/app previously rejected k1 /play/.../m3u8 (no ".m3u8") and followed
/play/ → /auth/ on the VPS (IP-bound tokens). Client must open /play/ itself.

This patch appends ?.m3u8 on XUI Direct redirects so older APKs that only match
".m3u8" still accept the Location URL.
"""
from __future__ import annotations

import sys
from pathlib import Path

SERVER = Path("/root/all/all2/server.js")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"FAIL {label}: expected 1 occurrence, found {count}")
    return text.replace(old, new, 1)


def patch(text: str) -> str:
    if "function isXuiDirectPlayUrl" in text and "xui-play-compat" in text:
        print("Already patched")
        return text

    anchor = "async function deliverDirectChannelStream(req, res, slug, channel) {"
    if text.count(anchor) != 1:
        raise SystemExit("FAIL: deliverDirectChannelStream anchor not unique")

    helper = """function isXuiDirectPlayUrl(url) {
  try {
    const parsed = new URL(String(url || '').trim());
    const host = String(parsed.hostname || '').toLowerCase();
    if (host !== 'k1.shifshqip.com' && !host.endsWith('.k1.shifshqip.com')) return false;
    const path = String(parsed.pathname || '');
    return /^\\/(play|auth|hls)(\\/|$)/i.test(path) || /\\/m3u8$/i.test(path);
  } catch {
    return false;
  }
}
function markXuiDirectPlayUrlForApp(url) {
  const raw = String(url || '').trim();
  if (!raw || !isXuiDirectPlayUrl(raw)) return raw;
  if (/\\.m3u8/i.test(raw)) return raw;
  return raw.includes('?') ? `${raw}&.m3u8` : `${raw}?.m3u8`;
}
"""
    text = text.replace(anchor, helper + anchor, 1)

    old_deliver_tail = """  res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, max-age=0');
  res.setHeader('X-Direct-Playback', 'hard-redirect');
  if (resolvedDirectManifest) res.setHeader('X-Direct-Manifest-Resolve', '1');
  return res.redirect(302, redirectUrl);
}"""
    new_deliver_tail = """  // XUI: 302 to /play/ so the phone completes /auth/ (IP-bound). ?.m3u8 = old APK compat.
  const finalRedirect = markXuiDirectPlayUrlForApp(redirectUrl);
  res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, max-age=0');
  res.setHeader('X-Direct-Playback', isXuiDirectPlayUrl(redirectUrl) ? 'xui-play-compat' : 'hard-redirect');
  if (resolvedDirectManifest) res.setHeader('X-Direct-Manifest-Resolve', '1');
  return res.redirect(302, finalRedirect);
}"""
    text = replace_once(text, old_deliver_tail, new_deliver_tail, "deliverDirect-xui-compat")
    return text


def main() -> int:
    if not SERVER.exists():
        print(f"Missing {SERVER}", file=sys.stderr)
        return 1
    original = SERVER.read_text(encoding="utf-8")
    patched = patch(original)
    if patched != original:
        SERVER.write_text(patched, encoding="utf-8")
    print(f"OK ({len(original)} -> {len(patched)})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
