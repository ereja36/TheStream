#!/usr/bin/env python3
"""Lokke: HLS.js plays CDN URL in-page; browser mode redirects with soft status text."""
from __future__ import annotations

import sys
from pathlib import Path

SERVER = Path("/root/all/all2/server.js")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"FAIL {label}: expected 1 occurrence, found {count}")
    return text.replace(old, new, 1)


def patch_server(text: str) -> str:
    # 1) Soft status text on redirect interstitial (no IP wording)
    old_msg = (
        '<body><div class="box"><h3 style="margin:0 0 8px">Po përgatitet redirect...</h3>'
        '<div class="m" id="msg">Po merret IP publike e klientit...</div></div>'
    )
    new_msg = (
        '<body><div class="box"><h3 style="margin:0 0 8px">Po kalon në server të dyt...</h3>'
        '<div class="m" id="msg">Po hapet burimi direkt...</div></div>'
    )
    text = replace_once(text, old_msg, new_msg, "redirect-status-text")

    old_fail = (
        "document.getElementById('msg').innerHTML="
        "'Nuk u mor IP publike automatikisht. Po provohet fallback...';"
    )
    new_fail = (
        "document.getElementById('msg').innerHTML="
        "'Po kalon në server të dyt...';"
    )
    text = replace_once(text, old_fail, new_fail, "redirect-fallback-text")

    # 2) Add peekPreferredEngine + only full-page redirect when engine is browser
    old_open = """  // Lokke: always redirect to resolved CDN source (never in-page channel-proxy).
  if(channelMeta.sk==='kool_lokke'&&koolLokkeRedirectPath){
    window.location.assign(koolLokkeRedirectPath);
    return;
  }"""
    new_open = """  // Lokke: browser engine → full-page CDN redirect; HLS.js → in-page player with CDN URL.
  if(channelMeta.sk==='kool_lokke'&&koolLokkeRedirectPath){
    const preferredLokkeEngine=(typeof peekPreferredEngine==='function')?peekPreferredEngine('kool_lokke'):'hls';
    if(preferredLokkeEngine==='browser'){
      window.location.assign(koolLokkeRedirectPath);
      return;
    }
  }"""
    text = replace_once(text, old_open, new_open, "open-lokke-engine-split")

    # 3) Insert peekPreferredEngine before pickEngine
    old_pick = "function pickEngine(canNative, playable){"
    new_pick = """function peekPreferredEngine(sourceKey){
  const hlsSupported=!!(window.Hls&&Hls.isSupported&&Hls.isSupported());
  const video=document.getElementById('videoPlayer');
  const canNative=!!(video&&(video.canPlayType('application/vnd.apple.mpegurl')||video.canPlayType('application/x-mpegURL')));
  const sourceRules=sourcePlayerDefaults[sourceKey||'']||{};
  let sourceRequested=(isIPhone?sourceRules.iphone:(isAndroid?sourceRules.android:sourceRules.other)||'auto').toLowerCase();
  let globalRequested=(isIPhone?playerDefaults.iphone:(isAndroid?playerDefaults.android:playerDefaults.other)||'auto').toLowerCase();
  const mapLegacy=(e)=>({native:'browser',shaka:'hls',clappr:'hls',playerjs:'hls'}[e]||e);
  sourceRequested=mapLegacy(sourceRequested);
  globalRequested=mapLegacy(globalRequested);
  const requested=(sourceRequested&&sourceRequested!=='auto')?sourceRequested:globalRequested;
  if(requested==='browser') return 'browser';
  if(requested==='hls') return (hlsSupported||canNative)?'hls':'browser';
  if(hlsSupported) return 'hls';
  return 'browser';
}
function pickEngine(canNative, playable){"""
    text = replace_once(text, old_pick, new_pick, "peek-preferred-engine")

    # 4) /api/channel-url: for kool_lokke return resolved CDN URL for HLS.js
    old_api = """  const playback = resolveWebPrimaryPlaybackPath(slug, req, channel, { disableProxyForSource });
  issuePlaybackCookieForSource(req, res, playback.sourceKey, resolvePlayTokenTtlSec(playback.sourceKey));
  const clientIpv4 = getLikelyClientIPv4(req);
  res.setHeader('Cache-Control', 'no-store');
  res.json({
    ok: true,
    source_key: playback.sourceKey,
    source_label: SOURCE_LABELS[playback.sourceKey] || playback.sourceKey,
    mode: playback.mode,
    url: appendClientIpQuery(playback.primaryUrl, clientIpv4),
    proxy_url: playback.proxyUrl ? appendClientIpQuery(playback.proxyUrl, clientIpv4) : '',
    client_ipv4: clientIpv4 || '',
    expires_in: resolvePlayTokenTtlSec(sourceKey),
    overloaded: runtimePressureState.overloaded === true
  });
});"""
    new_api = """  if (sourceKey === 'kool_lokke') {
    try {
      const incomingUa = req.headers['user-agent'] || '';
      const streamUrl = await resolveKoolStreamWithFailover(koolLokkeService, channel, req, {
        userAgent: incomingUa,
        sourceKey: 'kool_lokke',
        forceSignatureRefresh: shouldRefreshKoolLokkeSignaturePerOpen(req)
      });
      if (!isValidKoolStreamUrl(streamUrl)) {
        return res.status(502).json({ ok: false, error: 'Kool Lokke resolve failed', source_key: sourceKey });
      }
      issuePlaybackCookieForSource(req, res, sourceKey, resolvePlayTokenTtlSec(sourceKey));
      const clientIpv4 = getLikelyClientIPv4(req) || normalizePublicClientIp(req.query.ipv4) || '';
      res.setHeader('Cache-Control', 'no-store');
      return res.json({
        ok: true,
        source_key: sourceKey,
        source_label: SOURCE_LABELS[sourceKey] || sourceKey,
        mode: 'direct',
        url: streamUrl,
        proxy_url: '',
        direct_url: streamUrl,
        client_ipv4: clientIpv4 || '',
        expires_in: resolvePlayTokenTtlSec(sourceKey),
        overloaded: runtimePressureState.overloaded === true
      });
    } catch (error) {
      console.error('Kool Lokke channel-url resolve error:', error.message);
      return res.status(502).json({ ok: false, error: 'Kool Lokke stream unavailable', source_key: sourceKey });
    }
  }
  const playback = resolveWebPrimaryPlaybackPath(slug, req, channel, { disableProxyForSource });
  issuePlaybackCookieForSource(req, res, playback.sourceKey, resolvePlayTokenTtlSec(playback.sourceKey));
  const clientIpv4 = getLikelyClientIPv4(req);
  res.setHeader('Cache-Control', 'no-store');
  res.json({
    ok: true,
    source_key: playback.sourceKey,
    source_label: SOURCE_LABELS[playback.sourceKey] || playback.sourceKey,
    mode: playback.mode,
    url: appendClientIpQuery(playback.primaryUrl, clientIpv4),
    proxy_url: playback.proxyUrl ? appendClientIpQuery(playback.proxyUrl, clientIpv4) : '',
    client_ipv4: clientIpv4 || '',
    expires_in: resolvePlayTokenTtlSec(sourceKey),
    overloaded: runtimePressureState.overloaded === true
  });
});"""
    text = replace_once(text, old_api, new_api, "api-channel-url-lokke-cdn")

    return text


def main() -> int:
    if not SERVER.exists():
        print(f"Missing {SERVER}", file=sys.stderr)
        return 1
    original = SERVER.read_text()
    patched = patch_server(original)
    SERVER.write_text(patched)
    print(f"OK patched ({len(original)} -> {len(patched)} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
