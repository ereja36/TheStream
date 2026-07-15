#!/usr/bin/env python3
"""XUI Direct: hide /play/ server-side; 302 client to /auth/ (CDN, no proxy).

Flow:
  channel.url = https://k1…/play/TOKEN/m3u8
  all2 resolves /play/ → Location /auth/… on the VPS (via :81)
  302 app to /auth/…  (client ↔ CDN for auth + /hls segments)
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


HELPERS = r"""
function isXuiDirectPlayUrl(url) {
  try {
    const parsed = new URL(String(url || '').trim());
    const host = String(parsed.hostname || '').toLowerCase();
    if (host !== 'k1.shifshqip.com' && !host.endsWith('.k1.shifshqip.com')) return false;
    return /^\/play(\/|$)/i.test(String(parsed.pathname || ''));
  } catch {
    return false;
  }
}
function isXuiAuthOrHlsUrl(url) {
  try {
    const parsed = new URL(String(url || '').trim());
    const host = String(parsed.hostname || '').toLowerCase();
    if (host !== 'k1.shifshqip.com' && !host.endsWith('.k1.shifshqip.com')) return false;
    return /^\/(auth|hls)(\/|$)/i.test(String(parsed.pathname || ''));
  } catch {
    return false;
  }
}
function normalizeXuiPlayEntryUrl(url) {
  let raw = String(url || '').trim().replace(/^(https?:\/\/[^/]+):443\//i, '$1/');
  raw = raw.replace(/(\/play\/[^/?\s]+)\/ts(\?|$)/i, '$1/m3u8$2');
  return raw;
}
function markXuiClientCdnUrlForApp(url) {
  const raw = String(url || '').trim().replace(/^(https?:\/\/[^/]+):443\//i, '$1/');
  if (!raw || !isXuiAuthOrHlsUrl(raw)) return raw;
  if (/\.m3u8/i.test(raw)) return raw;
  return raw.includes('?') ? `${raw}&.m3u8` : `${raw}?.m3u8`;
}
async function resolveXuiPlayToAuthUrl(playUrl) {
  const target = normalizeXuiPlayEntryUrl(playUrl);
  if (!isXuiDirectPlayUrl(target)) return '';
  let localUrl = target;
  try {
    const u = new URL(target);
    localUrl = `http://127.0.0.1:81${u.pathname}${u.search || ''}`;
  } catch {}
  try {
    const response = await axios.get(localUrl, {
      timeout: 15000,
      maxRedirects: 0,
      validateStatus: (s) => s >= 200 && s < 400,
      headers: { Host: 'k1.shifshqip.com', Accept: '*/*', 'User-Agent': 'Ooew' },
      httpAgent: KEEPALIVE_HTTP_AGENT,
      httpsAgent: KEEPALIVE_HTTPS_AGENT
    });
    const loc = String(response.headers?.location || response.headers?.Location || '').trim();
    if (!loc) return '';
    const abs = new URL(loc, target).toString().replace(/^(https?:\/\/[^/]+):443\//i, '$1/');
    return isXuiAuthOrHlsUrl(abs) ? abs : '';
  } catch (e) {
    const loc = String(e?.response?.headers?.location || e?.response?.headers?.Location || '').trim();
    if (!loc) return '';
    try {
      const abs = new URL(loc, target).toString().replace(/^(https?:\/\/[^/]+):443\//i, '$1/');
      return isXuiAuthOrHlsUrl(abs) ? abs : '';
    } catch {
      return '';
    }
  }
}
"""


def patch(text: str) -> str:
    # Remove older helper block if present
    if "async function resolveXuiPlayToAuthUrl" in text and "xui-auth-redirect" in text:
        print("Already patched (auth redirect)")
        return text

    # Strip previous xui-play-compat helpers if present
    if "function isXuiDirectPlayUrl" in text and "markXuiDirectPlayUrlForApp" in text:
        start = text.find("function isXuiDirectPlayUrl")
        end = text.find("async function deliverDirectChannelStream")
        if start > 0 and end > start:
            text = text[:start] + text[end:]

    anchor = "async function deliverDirectChannelStream(req, res, slug, channel) {"
    if text.count(anchor) != 1:
        raise SystemExit("FAIL: deliverDirectChannelStream anchor not unique")
    text = text.replace(anchor, HELPERS.strip() + "\n" + anchor, 1)

    old_tail = """  // XUI: 302 to /play/ so the phone completes /auth/ (IP-bound). ?.m3u8 = old APK compat.
  const finalRedirect = markXuiDirectPlayUrlForApp(redirectUrl);
  res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, max-age=0');
  res.setHeader('X-Direct-Playback', isXuiDirectPlayUrl(redirectUrl) ? 'xui-play-compat' : 'hard-redirect');
  if (resolvedDirectManifest) res.setHeader('X-Direct-Manifest-Resolve', '1');
  return res.redirect(302, finalRedirect);
}"""
    new_tail = """  // XUI: hide /play/ — resolve to /auth/ on VPS, 302 client to CDN token (no segment proxy).
  let finalRedirect = redirectUrl;
  if (isXuiDirectPlayUrl(redirectUrl)) {
    const authUrl = await resolveXuiPlayToAuthUrl(redirectUrl);
    if (!authUrl) return res.status(502).send('XUI auth token unavailable');
    finalRedirect = markXuiClientCdnUrlForApp(authUrl);
    res.setHeader('X-Direct-Playback', 'xui-auth-redirect');
  } else {
    res.setHeader('X-Direct-Playback', 'hard-redirect');
  }
  res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, max-age=0');
  if (resolvedDirectManifest) res.setHeader('X-Direct-Manifest-Resolve', '1');
  return res.redirect(302, finalRedirect);
}"""

    # Also handle pre-patch hard-redirect tail
    old_hard = """  res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, max-age=0');
  res.setHeader('X-Direct-Playback', 'hard-redirect');
  if (resolvedDirectManifest) res.setHeader('X-Direct-Manifest-Resolve', '1');
  return res.redirect(302, redirectUrl);
}"""

    if old_tail in text:
        text = replace_once(text, old_tail, new_tail, "deliver-xui-auth")
    elif old_hard in text:
        # Match inside deliverDirectChannelStream only — use surrounding unique context
        text = replace_once(text, old_hard, new_tail, "deliver-xui-auth-from-hard")
    else:
        raise SystemExit("FAIL: deliverDirectChannelStream redirect tail not found")

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
