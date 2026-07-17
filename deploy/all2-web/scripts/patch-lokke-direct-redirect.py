#!/usr/bin/env python3
"""Force Lokke (kool_lokke) to 302-redirect to CDN source — never channel-proxy."""
from __future__ import annotations

import json
import sys
from pathlib import Path

SERVER = Path("/root/all/all2/server.js")
CONFIG = Path("/root/all/all2/data/channel_config.json")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"FAIL {label}: expected 1 occurrence, found {count}")
    return text.replace(old, new, 1)


def patch_server(text: str) -> str:
    # 1) Web player: always leave site via /kool-lokke/redirect (CDN 302), never HLS via channel-proxy
    old_player = """  if(pageOptions.redirectKoolOnMain&&channelMeta.sk==='kool_lokke'&&koolLokkeRedirectPath){
    window.location.assign(koolLokkeRedirectPath);
    return;
  }"""
    new_player = """  // Lokke: always redirect to resolved CDN source (never in-page channel-proxy).
  if(channelMeta.sk==='kool_lokke'&&koolLokkeRedirectPath){
    window.location.assign(koolLokkeRedirectPath);
    return;
  }"""
    text = replace_once(text, old_player, new_player, "player-lokke-redirect")

    # 2) /channel/:token — kool_lokke: always 302 to resolved streamUrl (no VPS probe, no proxy fallback)
    old_deliver = """  if (sourceKey === 'kool_lokke') {
    try {
      const useProxy = channelUsesProxy(channel);
      const streamUrl = await resolveKoolStreamWithFailover(koolLokkeService, channel, req, {
        userAgent: incomingUa,
        sourceKey: 'kool_lokke',
        forceSignatureRefresh: shouldRefreshKoolLokkeSignaturePerOpen(req)
      });
      if (!isValidKoolStreamUrl(streamUrl)) return res.status(502).send('Kool Lokke resolve failed');
      const upstreamProfile = buildKoolUpstreamProfile('kool_lokke', slug);
      const profileToken = issueUpstreamProfile(upstreamProfile);
      if (useProxy) {
        const candidates = resolveFailoverCandidates(slug, streamUrl);
        await proxyManifest(streamUrl, req, res, `kool-lokke-proxy:${slug}`, { candidates, profileToken, upstreamProfile });
        return;
      }
      const directCandidates = resolveFailoverCandidates(slug, streamUrl);
      const redirectUrl = await resolveFirstReachableUrl(directCandidates, req, upstreamProfile, { requireWorking: true });
      if (!redirectUrl) {
        res.setHeader('X-Direct-Fallback', 'proxy');
        const candidates = resolveFailoverCandidates(slug, streamUrl);
        await proxyManifest(streamUrl, req, res, `kool-lokke-direct-fallback:${slug}`, { candidates, profileToken, upstreamProfile });
        return;
      }
      res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, max-age=0');
      return res.redirect(302, redirectUrl);
    } catch (error) {
      console.error('Kool Lokke signed redirect error:', error.message);
      return res.status(502).send('Kool Lokke stream unavailable');
    }
  }"""
    new_deliver = """  if (sourceKey === 'kool_lokke') {
    try {
      // Lokke traffic must hit CDN directly — never rewrite via /k1/proxy/seg.
      const streamUrl = await resolveKoolStreamWithFailover(koolLokkeService, channel, req, {
        userAgent: incomingUa,
        sourceKey: 'kool_lokke',
        forceSignatureRefresh: shouldRefreshKoolLokkeSignaturePerOpen(req)
      });
      if (!isValidKoolStreamUrl(streamUrl)) return res.status(502).send('Kool Lokke resolve failed');
      res.setHeader('X-Lokke-Playback', 'direct-redirect');
      res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, max-age=0');
      return res.redirect(302, streamUrl);
    } catch (error) {
      console.error('Kool Lokke signed redirect error:', error.message);
      return res.status(502).send('Kool Lokke stream unavailable');
    }
  }"""
    text = replace_once(text, old_deliver, new_deliver, "deliver-channel-lokke")

    # 3) /channel-proxy/:token — even legacy proxy URLs redirect to CDN for Lokke
    old_proxy = """  if (sourceKey === 'kool_lokke') {
    try {
      const streamUrl = await resolveKoolStreamWithFailover(koolLokkeService, channel, req, {
        userAgent: incomingUa,
        sourceKey: 'kool_lokke',
        forceSignatureRefresh: shouldRefreshKoolLokkeSignaturePerOpen(req)
      });
      if (!isValidKoolStreamUrl(streamUrl)) return res.status(502).send('Kool Lokke resolve failed');
      const candidates = resolveFailoverCandidates(slug, streamUrl);
      const upstreamProfile = buildKoolUpstreamProfile('kool_lokke', slug);
      const profileToken = issueUpstreamProfile(upstreamProfile);
      await proxyManifest(streamUrl, req, res, `kool-lokke-fallback:${slug}`, { candidates, profileToken, upstreamProfile });
      return;
    } catch (error) {
      console.error('Kool Lokke signed proxy error:', error.message);
      return res.status(502).send('Kool Lokke stream unavailable');
    }
  }"""
    new_proxy = """  if (sourceKey === 'kool_lokke') {
    try {
      // Legacy channel-proxy links for Lokke still 302 to CDN (no VPS segment relay).
      const streamUrl = await resolveKoolStreamWithFailover(koolLokkeService, channel, req, {
        userAgent: incomingUa,
        sourceKey: 'kool_lokke',
        forceSignatureRefresh: shouldRefreshKoolLokkeSignaturePerOpen(req)
      });
      if (!isValidKoolStreamUrl(streamUrl)) return res.status(502).send('Kool Lokke resolve failed');
      res.setHeader('X-Lokke-Playback', 'direct-redirect-from-proxy-route');
      res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, max-age=0');
      return res.redirect(302, streamUrl);
    } catch (error) {
      console.error('Kool Lokke signed proxy error:', error.message);
      return res.status(502).send('Kool Lokke stream unavailable');
    }
  }"""
    text = replace_once(text, old_proxy, new_proxy, "channel-proxy-lokke")

    # 4) App measurement route — same direct redirect for Lokke
    old_appm = """  if (sourceKey === 'kool_lokke') {
    try {
      const streamUrl = await resolveKoolStreamWithFailover(koolLokkeService, channel, req, {
        userAgent: incomingUa,
        forceSignatureRefresh: shouldRefreshKoolLokkeSignaturePerOpen(req)
      });
      if (!/^https?:\\/\\//i.test(streamUrl || '')) return res.status(502).send('Kool Lokke resolve failed');
      const profileToken = issueUpstreamProfile({ sourceKey: 'kool_lokke', channelSlug: slug });
      const candidates = resolveFailoverCandidates(slug, streamUrl);
      await proxyManifest(streamUrl, req, res, `appm-kool-lokke:${playlistId}:${slug}`, { candidates, profileToken });
      return;
    } catch (error) {
      console.error('App measurement kool lokke proxy error:', error.message);
      return res.status(502).send('Kool Lokke stream unavailable');
    }
  }"""
    new_appm = """  if (sourceKey === 'kool_lokke') {
    try {
      const streamUrl = await resolveKoolStreamWithFailover(koolLokkeService, channel, req, {
        userAgent: incomingUa,
        forceSignatureRefresh: shouldRefreshKoolLokkeSignaturePerOpen(req)
      });
      if (!/^https?:\\/\\//i.test(streamUrl || '')) return res.status(502).send('Kool Lokke resolve failed');
      res.setHeader('X-Lokke-Playback', 'direct-redirect-appm');
      res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, max-age=0');
      return res.redirect(302, streamUrl);
    } catch (error) {
      console.error('App measurement kool lokke proxy error:', error.message);
      return res.status(502).send('Kool Lokke stream unavailable');
    }
  }"""
    text = replace_once(text, old_appm, new_appm, "appm-lokke")

    # 5) Force chooseInitialPlaybackMode for kool_lokke → always direct (ignore stale proxy config)
    old_choose = """  if (sourceKey === 'kool' || sourceKey === 'kool_lokke') {
    if (channelUsesProxy(channel)) return 'proxy';
    const koolSettings = getKoolSourceSettingsByKey(sourceKey);
    if (koolSettings.default_playback_mode === 'proxy') return 'proxy';
    return 'direct';
  }"""
    new_choose = """  if (sourceKey === 'kool_lokke') {
    // Lokke must always use /channel (302 to CDN), never /channel-proxy segment relay.
    return 'direct';
  }
  if (sourceKey === 'kool') {
    if (channelUsesProxy(channel)) return 'proxy';
    const koolSettings = getKoolSourceSettingsByKey(sourceKey);
    if (koolSettings.default_playback_mode === 'proxy') return 'proxy';
    return 'direct';
  }"""
    text = replace_once(text, old_choose, new_choose, "choose-mode-lokke")

    # 6) Do not advertise channel-proxy fallback URL for Lokke clients
    old_proxy_url = """  const relativePrimaryUrl = mode === 'proxy' ? proxy : direct;
  const proxyUrl = (mode === 'direct' && !disableProxyForSource) ? toRelayUrl(proxy) : '';
  return {
    sourceKey,
    mode,
    primaryUrl: toRelayUrl(relativePrimaryUrl),
    proxyUrl
  };
}"""
    new_proxy_url = """  const relativePrimaryUrl = mode === 'proxy' ? proxy : direct;
  // Lokke never exposes a proxy fallback URL — clients must follow the CDN redirect.
  const proxyUrl = (sourceKey === 'kool_lokke')
    ? ''
    : ((mode === 'direct' && !disableProxyForSource) ? toRelayUrl(proxy) : '');
  return {
    sourceKey,
    mode,
    primaryUrl: toRelayUrl(relativePrimaryUrl),
    proxyUrl
  };
}"""
    text = replace_once(text, old_proxy_url, new_proxy_url, "no-proxy-url-lokke")

    return text


def patch_config() -> None:
    cfg = json.loads(CONFIG.read_text())
    kl = cfg.get("kool_lokke_source")
    if not isinstance(kl, dict):
        kl = {}
    kl["default_playback_mode"] = "direct"
    cfg["kool_lokke_source"] = kl

    proxy_rules = cfg.get("proxy_rules")
    if not isinstance(proxy_rules, dict):
        proxy_rules = {}
    source = proxy_rules.get("source")
    if not isinstance(source, dict):
        source = {}
    source["kool_lokke"] = "direct"
    proxy_rules["source"] = source
    cfg["proxy_rules"] = proxy_rules

    # Do not force proxy under load for Lokke
    pp = cfg.get("playback_protection")
    if isinstance(pp, dict):
        force = pp.get("force_proxy_when_load_high")
        if isinstance(force, dict) and "kool_lokke" in force:
            force["kool_lokke"] = False
            pp["force_proxy_when_load_high"] = force
            cfg["playback_protection"] = pp

    CONFIG.write_text(json.dumps(cfg, indent=2, ensure_ascii=False) + "\n")
    print("OK config: kool_lokke default_playback_mode=direct, proxy_rules.source.kool_lokke=direct")


def main() -> int:
    if not SERVER.exists():
        print(f"Missing {SERVER}", file=sys.stderr)
        return 1
    original = SERVER.read_text()
    patched = patch_server(original)
    if patched == original:
        print("No server.js changes?", file=sys.stderr)
        return 1
    SERVER.write_text(patched)
    print(f"OK server.js patched ({len(original)} -> {len(patched)} bytes)")
    if CONFIG.exists():
        patch_config()
    else:
        print(f"WARN missing {CONFIG}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
