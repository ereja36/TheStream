#!/usr/bin/env python3
"""A3 VOD: web via phinemi.live a3proxy (hide gcore URL); allow alb_vod in channel-url."""
from pathlib import Path
import subprocess
import time
import urllib.request
import json

SERVER = Path("/root/all/all2/server.js")
t = SERVER.read_text(encoding="utf-8")
changed = []

# 1) preferDirect: only app (not web VOD)
old_opts = """  const sourceKey = String(channel?.sourceKey || channel?.source || '').trim().toLowerCase();
  const isVod = sourceKey === 'alb_vod'
    || String(channel?.type || '').trim().toLowerCase() === 'vod'
    || /\\.mp4(\\?|$)/i.test(String(channel?.url || ''));
  return {
    headers,
    userAgent: String(channel?.albUserAgent || ALB_DEFAULT_UPSTREAM_UA || '').trim() || ALB_DEFAULT_UPSTREAM_UA,
    // VOD is progressive MP4 — never remux to m3u8/a3hls.
    preferDirect: (fastly && fromApp) || isVod,
    preferClientSw: false,
    // Chrome/web live: remux via /a3hls for HLS.js. VOD stays direct MP4.
    preferFastlyRelay: fastly && !fromApp && !isVod,
    preferHlsRemux: fastly && !fromApp && !isVod,
    webBlocked: false
  };
}"""

new_opts = """  const sourceKey = String(channel?.sourceKey || channel?.source || '').trim().toLowerCase();
  const isVod = sourceKey === 'alb_vod'
    || String(channel?.type || '').trim().toLowerCase() === 'vod'
    || /\\.mp4(\\/index\\.m3u8)?(\\?|$)/i.test(String(channel?.url || ''));
  return {
    headers,
    userAgent: String(channel?.albUserAgent || ALB_DEFAULT_UPSTREAM_UA || '').trim() || ALB_DEFAULT_UPSTREAM_UA,
    // Web must NOT get raw gcore CDN (download forbidden + URL leak).
    // App may still play VOD/fastly direct with the phone IP.
    preferDirect: fromApp && (fastly || isVod),
    preferClientSw: false,
    // Live fastly on web → a3hls. VOD on web → a3proxy (phinemi.live), keep HLS index.
    preferFastlyRelay: fastly && !fromApp && !isVod,
    preferHlsRemux: fastly && !fromApp && !isVod,
    webBlocked: false,
    isVod: !!isVod
  };
}"""

if "Web must NOT get raw gcore CDN" not in t:
    if old_opts not in t:
        raise SystemExit("FAIL: buildA3PlaybackOptionsFromChannel")
    t = t.replace(old_opts, new_opts, 1)
    changed.append("playback-opts")
else:
    changed.append("playback-opts:already")

# 2) buildA3PlaybackUrl — web: keep/coerce HLS, wrap via phinemi a3proxy
old_build = """function buildA3PlaybackUrl(streamUrl = '', options = {}) {
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

new_build = """function buildA3PlaybackUrl(streamUrl = '', options = {}) {
  let url = String(streamUrl || '').trim();
  if (!/^https?:\\/\\//i.test(url)) return url;
  // App direct: progressive .mp4. Web: keep /index.m3u8 and wrap via phinemi a3proxy.
  if (options.preferDirect === true) {
    url = url.replace(/\\.mp4\\/index\\.m3u8(?=\\?|$)/i, '.mp4');
    return url;
  }
  // Progressive MP4 on web → HLS index so a3proxy serves playlists/segments (not whole file).
  if (/\\.mp4(?:\\?|$)/i.test(url) && !/\\.m3u8/i.test(url)) {
    url = url.replace(/\\.mp4(?=\\?|$)/i, '.mp4/index.m3u8');
  }
  if (options.preferClientSw === true) return wrapA3ClientSwUrl(url, options);
  if (options.preferFastlyRelay === true) return wrapA3FastlyRelayUrl(url, options);
  // VOD/web: proxy HLS through phinemi.live/k1/a3proxy so gcore host stays hidden.
  return wrapA3CorsProxyUrl(url, options);
}"""

if "keep /index.m3u8 and wrap via phinemi" not in t:
    if old_build not in t:
        start = t.find("function buildA3PlaybackUrl(streamUrl = '', options = {}) {")
        end = t.find("\nfunction buildA3PlaybackOptionsFromChannel", start)
        if start < 0 or end < 0:
            raise SystemExit("FAIL: buildA3PlaybackUrl bounds")
        t = t[:start] + new_build + "\n" + t[end:]
        changed.append("playback-url-bounds")
    else:
        t = t.replace(old_build, new_build, 1)
        changed.append("playback-url")
else:
    changed.append("playback-url:already")

# 3) channel-url: allow alb / alb_vod even if not on main portal; hide direct_url on web VOD
old_portal_check = """  const mainPortalCheck = assertMainPortalWebAccess(slug);
  if (!mainPortalCheck.ok) {
    return res.status(404).json({ ok: false, error: 'not_on_main_portal' });
  }
  const googleLoginCheck = assertGoogleLoginChannelAccess(req, slug);"""

new_portal_check = """  // A3 live/VOD have dedicated source folders — do not require main-portal curation.
  const earlyChannel = resolveChannelBySlug(slug) || (/^(alb_|albvod_)/i.test(String(slug || '')) ? { sourceKey: /^albvod_/i.test(String(slug || '')) ? 'alb_vod' : 'alb' } : null);
  const earlySource = getChannelSourceKey(earlyChannel || {});
  const skipMainPortalGate = earlySource === 'alb' || earlySource === 'alb_vod' || /^(alb_|albvod_)/i.test(String(slug || ''));
  if (!skipMainPortalGate) {
    const mainPortalCheck = assertMainPortalWebAccess(slug);
    if (!mainPortalCheck.ok) {
      return res.status(404).json({ ok: false, error: 'not_on_main_portal' });
    }
  }
  const googleLoginCheck = assertGoogleLoginChannelAccess(req, slug);"""

if "skipMainPortalGate" not in t:
    if old_portal_check not in t:
        raise SystemExit("FAIL: portal check")
    t = t.replace(old_portal_check, new_portal_check, 1)
    changed.append("portal-gate")
else:
    changed.append("portal-gate:already")

# Hide direct_url for web VOD in channel-url JSON
old_json = """      url: playUrl,
      proxy_url: workerUrl,
      direct_url: streamUrl,
      headers: playOpts.headers || {},
      user_agent: playOpts.userAgent || '',
      client_ipv4: clientIpv4 || '',
      expires_in: resolvePlayTokenTtlSec(sourceKey),
      overloaded: runtimePressureState.overloaded === true,
      a3_cdn: a3Cdn,
      a3_failover: channel._albFailoverTo || '',
      a3_web_blocked: webBlocked,
      error: ''
    });
  }
  if (sourceKey === 'kool_lokke') {"""

new_json = """      url: playUrl,
      proxy_url: workerUrl,
      // Never expose raw gcore CDN on web VOD (download forbidden + URL leak).
      direct_url: (sourceKey === 'alb_vod' && !isShifAppRequest(req)) ? '' : streamUrl,
      headers: playOpts.headers || {},
      user_agent: playOpts.userAgent || '',
      client_ipv4: clientIpv4 || '',
      expires_in: resolvePlayTokenTtlSec(sourceKey),
      overloaded: runtimePressureState.overloaded === true,
      a3_cdn: a3Cdn,
      a3_failover: channel._albFailoverTo || '',
      a3_web_blocked: webBlocked,
      error: ''
    });
  }
  if (sourceKey === 'kool_lokke') {"""

if "Never expose raw gcore CDN on web VOD" not in t:
    if old_json not in t:
        raise SystemExit("FAIL: channel-url json")
    t = t.replace(old_json, new_json, 1)
    changed.append("hide-direct")
else:
    changed.append("hide-direct:already")

# 4) Client pickEngine: alb_vod via a3proxy m3u8 → HLS.js (not forced browser)
# Inside template literal — avoid fragile \\d / \\s escapes. Use indexOf.
start = t.find("const probeUrl=String((playable&&(playable.directUrl||playable.url))||currentUrl||'');")
if start < 0:
    start = t.find("const probeUrl=String((playable&&(playable.url||playable.directUrl))||currentUrl||'');")
if start > 0 and "a3proxy/m3u8 → HLS" not in t[start:start + 800]:
    end = t.find("// A3 fastly via a3hls", start)
    if end < 0:
        end = t.find("if(isA3HlsPlayUrl", start)
    new_pick = """  const probeUrl=String((playable&&(playable.url||playable.directUrl))||currentUrl||'');
  const probeMode=String((playable&&playable.mode)||currentA3Mode||'').toLowerCase();
  // Progressive raw .mp4 (app direct) → <video>. Proxied VOD (phinemi a3proxy/m3u8 → HLS.js).
  if(currentSourceKey==='alb_vod' || probeUrl.indexOf('.mp4')>=0){
    if(probeMode==='proxy' || probeUrl.indexOf('/a3proxy')>=0 || probeUrl.indexOf('m3u8')>=0){
      if(hlsSupported) return 'hls';
      if(canNative) return 'browser';
    }
    if(probeUrl.indexOf('.mp4')>=0 && probeUrl.indexOf('m3u8')<0 && probeUrl.indexOf('/a3proxy')<0){
      return 'browser';
    }
  }
  """
    if end > start:
        t = t[:start] + new_pick + t[end:]
        changed.append("pickEngine")
    else:
        changed.append("pickEngine:WARN")
else:
    changed.append("pickEngine:already" if start > 0 else "pickEngine:MISSING")

# 5) Treat a3proxy as HLS play URL (same as a3hls for engine selection helpers)
old_hls_helper = """function isA3HlsPlayUrl(url, playable){
  const mode=String((playable&&playable.mode)||currentA3Mode||'').toLowerCase();
  const u=String(url||'');
  return mode==='a3hls' || /\\\\/a3hls(\\\\?|\\\\/|$)/i.test(u);
}"""
# In template literal file, backslashes are doubled for regex
old_hls_helper2 = """function isA3HlsPlayUrl(url, playable){
  const mode=String((playable&&playable.mode)||currentA3Mode||'').toLowerCase();
  const u=String(url||'');
  return mode==='a3hls' || /\\/a3hls(\\?|\\/|$)/i.test(u);
}"""
new_hls_helper = """function isA3HlsPlayUrl(url, playable){
  const mode=String((playable&&playable.mode)||currentA3Mode||'').toLowerCase();
  const u=String(url||'');
  // a3proxy serves rewritten m3u8 for gcore VOD — treat like HLS.
  if(mode==='proxy' && (u.indexOf('/a3proxy')>=0 || u.indexOf('phinemi.live')>=0)) return true;
  return mode==='a3hls' || u.indexOf('/a3hls')>=0;
}"""

if "a3proxy serves rewritten m3u8" not in t:
    if old_hls_helper2 in t:
        t = t.replace(old_hls_helper2, new_hls_helper, 1)
        changed.append("isA3HlsPlayUrl")
    elif old_hls_helper in t:
        t = t.replace(old_hls_helper, new_hls_helper, 1)
        changed.append("isA3HlsPlayUrl")
    else:
        # bounds replace
        hs = t.find("function isA3HlsPlayUrl(url, playable){")
        he = t.find("\nfunction formatA3PlaybackError", hs)
        if hs > 0 and he > hs:
            t = t[:hs] + new_hls_helper + t[he:]
            changed.append("isA3HlsPlayUrl-bounds")
        else:
            changed.append("isA3HlsPlayUrl:WARN")
else:
    changed.append("isA3HlsPlayUrl:already")

SERVER.write_text(t, encoding="utf-8")
r = subprocess.run(["node", "--check", str(SERVER)], capture_output=True, text=True)
print("changed:", ", ".join(changed))
print("check", r.returncode, (r.stderr or "OK")[:500])
if r.returncode != 0:
    raise SystemExit(1)

subprocess.check_call(["pm2", "reload", "all2", "--update-env"])
time.sleep(8)

# Verify API for True Gentleman
slug = "albvod_a-true-gentleman"
req = urllib.request.Request(
    f"http://127.0.0.1:3000/k1/api/channel-url/{slug}",
    headers={"Referer": "http://127.0.0.1:3000/k1/", "Origin": "http://127.0.0.1:3000"},
)
try:
    data = json.loads(urllib.request.urlopen(req, timeout=60).read().decode())
    print("ok", data.get("ok"), "mode", data.get("mode"), "cdn", data.get("a3_cdn"), "err", data.get("error"))
    print("url", (data.get("url") or "")[:200])
    print("direct", (data.get("direct_url") or "")[:100])
    print("proxy", (data.get("proxy_url") or "")[:160])
    u = data.get("url") or ""
    print("uses_phinemi", "phinemi.live" in u)
    print("uses_a3proxy", "/a3proxy" in u)
    print("hides_gcore", "gcore-cdn.org" not in u and "gcore" not in u.lower())
    # Smoke-test a3proxy playlist
    if data.get("ok") and "/a3proxy" in u:
        pref = urllib.request.Request(u, headers={"User-Agent": "Mozilla/5.0", "Referer": "https://shifshqip.com/k1/"})
        try:
            with urllib.request.urlopen(pref, timeout=45) as resp:
                body = resp.read(400).decode("utf-8", errors="replace")
                print("a3proxy_status", resp.status, "ct", resp.headers.get("content-type", ""))
                print("a3proxy_body", body[:200].replace("\\n", " | "))
                print("a3proxy_is_m3u8", "#EXTM3U" in body or "m3u8" in body.lower())
        except Exception as pe:
            print("a3proxy_ERR", pe)
except Exception as e:
    print("API ERR", e)

print("DONE")
