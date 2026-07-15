#!/usr/bin/env python3
"""Respect admin Direct/Redirect for all sources (including custom_*).

Previously shouldHidePlaybackSource(custom_tv|custom_sport|custom_nimi) forced
proxy routes (/channel-proxy + segment relay) even when proxy_rules said direct.

Also: deliverDirectChannelStream must 302 to the source CDN without probing from
the VPS (datacenter IPs often fail → wrongly fell back to proxy).
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
    old_uses = """function channelUsesProxy(channel) {
  if (!channel) return false;
  const sourceKey = getChannelSourceKey(channel);
  if (shouldHidePlaybackSource(sourceKey)) return true;
  if (isUniversalProxyEnabled()) return true;"""
    new_uses = """function channelUsesProxy(channel) {
  if (!channel) return false;
  const sourceKey = getChannelSourceKey(channel);
  // Admin Proxy/Direct by Source must win — do not force proxy for custom_*.
  if (isUniversalProxyEnabled()) return true;"""
    text = replace_once(text, old_uses, new_uses, "channelUsesProxy-no-hide-force")

    old_choose = """function chooseInitialPlaybackMode(sourceKey, channel, req) {
  if (shouldHidePlaybackSource(sourceKey)) return 'proxy';
  if (shouldForceProxyRouteForSource(sourceKey)) return 'proxy';
  const forcedMode = String(req?.query?.play_mode || '').trim().toLowerCase();
  if (forcedMode === 'proxy' || req?.query?.proxy === '1') return 'proxy';
  if (forcedMode === 'direct' || req?.query?.direct === '1') {
    if (shouldHidePlaybackSource(sourceKey) || shouldForceProxyRouteForSource(sourceKey)) return 'proxy';
    return 'direct';
  }
  if (shouldForceDirectOnHighLoad(sourceKey)) {
    if (shouldHidePlaybackSource(sourceKey)) return 'proxy';
    return 'direct';
  }"""
    new_choose = """function chooseInitialPlaybackMode(sourceKey, channel, req) {
  if (shouldForceProxyRouteForSource(sourceKey)) return 'proxy';
  const forcedMode = String(req?.query?.play_mode || '').trim().toLowerCase();
  if (forcedMode === 'proxy' || req?.query?.proxy === '1') return 'proxy';
  if (forcedMode === 'direct' || req?.query?.direct === '1') {
    if (shouldForceProxyRouteForSource(sourceKey)) return 'proxy';
    return 'direct';
  }
  if (shouldForceDirectOnHighLoad(sourceKey)) {
    return 'direct';
  }"""
    text = replace_once(text, old_choose, new_choose, "chooseInitialPlaybackMode-respect-direct")

    old_deliver = """async function deliverDirectChannelStream(req, res, slug, channel) {
  const upstreamProfile = buildChannelUpstreamProfile(channel, slug);
  let directTarget = String(channel.url || '').trim();
  let resolvedDirectManifest = false;
  if (customChannelUsesDirectResolve(channel) || channelHasUpstreamProfile(channel)) {
    const resolved = await resolveDirectManifestUrl(directTarget, req, upstreamProfile);
    if (resolved) {
      directTarget = resolved;
      resolvedDirectManifest = true;
    }
  }
  const directCandidates = resolveFailoverCandidates(slug, directTarget);
  const redirectUrl = await resolveFirstReachableUrl(directCandidates, req, upstreamProfile, { requireWorking: true });
  if (!redirectUrl) {
    res.setHeader('X-Direct-Fallback', 'proxy');
    await proxyChannelWithOptionalResolve(req, res, slug, channel, 'direct-fallback');
    return;
  }
  res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, max-age=0');
  if (resolvedDirectManifest) res.setHeader('X-Direct-Manifest-Resolve', '1');
  return res.redirect(302, redirectUrl);
}"""
    new_deliver = """async function deliverDirectChannelStream(req, res, slug, channel) {
  const upstreamProfile = buildChannelUpstreamProfile(channel, slug);
  let directTarget = String(channel.url || '').trim();
  let resolvedDirectManifest = false;
  if (customChannelUsesDirectResolve(channel) || channelHasUpstreamProfile(channel)) {
    const resolved = await resolveDirectManifestUrl(directTarget, req, upstreamProfile);
    if (resolved) {
      directTarget = resolved;
      resolvedDirectManifest = true;
    }
  }
  // Lokke-style: 302 to source CDN. Never probe from VPS (DC IPs fail → wrong proxy fallback).
  // Segments then go client ↔ CDN; VPS only issues the signed redirect.
  const directCandidates = resolveFailoverCandidates(slug, directTarget);
  const redirectUrl = String((directCandidates && directCandidates[0]) || directTarget || '').trim();
  if (!/^https?:\\/\\//i.test(redirectUrl)) {
    return res.status(502).send('Direct stream URL unavailable');
  }
  res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, max-age=0');
  res.setHeader('X-Direct-Playback', 'hard-redirect');
  if (resolvedDirectManifest) res.setHeader('X-Direct-Manifest-Resolve', '1');
  return res.redirect(302, redirectUrl);
}"""
    text = replace_once(text, old_deliver, new_deliver, "deliverDirectChannelStream-hard-redirect")

    # Legacy /channel-proxy links for Direct sources should also 302 to CDN.
    old_fallback = """  if (sourceKey === 'alb' || sourceKey === 'alb_vod') {
    try {
      const verifiedAlb = await resolveAlbChannelBySlugRuntime(req, slug, { verifyStream: false, tries: 2 });
      if (!verifiedAlb) return res.status(502).send('A3 channel unavailable right now');
      channel = verifiedAlb;
      const streamUrl = String(channel.url || '').trim();
      if (!/^https?:\\/\\//i.test(streamUrl || '')) return res.status(502).send('A3 resolve failed');
      res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, max-age=0');
      res.setHeader('X-A3-Direct', '1');
      res.setHeader('X-A3-Cors-Proxy', '1');
      res.setHeader('X-A3-Headers', Object.keys(channel.albHeaders || {}).length ? '1' : '0');
      return res.redirect(302, buildA3PlaybackUrl(streamUrl, buildA3PlaybackOptionsFromChannel(channel, req)));
    } catch (error) {
      console.error('A3 proxy error:', error.message);
      return res.status(502).send('A3 stream unavailable');
    }
  }
  await proxyChannelWithOptionalResolve(req, res, slug, channel, 'fallback');
});"""
    new_fallback = """  if (sourceKey === 'alb' || sourceKey === 'alb_vod') {
    try {
      const verifiedAlb = await resolveAlbChannelBySlugRuntime(req, slug, { verifyStream: false, tries: 2 });
      if (!verifiedAlb) return res.status(502).send('A3 channel unavailable right now');
      channel = verifiedAlb;
      const streamUrl = String(channel.url || '').trim();
      if (!/^https?:\\/\\//i.test(streamUrl || '')) return res.status(502).send('A3 resolve failed');
      res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, max-age=0');
      res.setHeader('X-A3-Direct', '1');
      res.setHeader('X-A3-Cors-Proxy', '1');
      res.setHeader('X-A3-Headers', Object.keys(channel.albHeaders || {}).length ? '1' : '0');
      return res.redirect(302, buildA3PlaybackUrl(streamUrl, buildA3PlaybackOptionsFromChannel(channel, req)));
    } catch (error) {
      console.error('A3 proxy error:', error.message);
      return res.status(502).send('A3 stream unavailable');
    }
  }
  // Respect admin Direct even on legacy /channel-proxy bookmarks.
  if (!channelUsesProxy(channel)) {
    res.setHeader('X-Direct-From-Proxy-Route', '1');
    await deliverDirectChannelStream(req, res, slug, channel);
    return;
  }
  await proxyChannelWithOptionalResolve(req, res, slug, channel, 'fallback');
});"""
    text = replace_once(text, old_fallback, new_fallback, "channel-proxy-respect-direct")

    return text


def main() -> int:
    if not SERVER.exists():
        print(f"Missing {SERVER}", file=sys.stderr)
        return 1
    original = SERVER.read_text()
    patched = patch(original)
    SERVER.write_text(patched)
    print(f"OK ({len(original)} -> {len(patched)})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
