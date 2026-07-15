#!/usr/bin/env python3
"""Apply Lokke-style hard CDN redirect to all redirect-capable sources (kool/nimi/v2)."""
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
    # kool /channel — always 302 to resolved CDN (never proxyManifest) when not forced proxy by config
    old_kool = """  if (sourceKey === 'kool') {
    try {
      const useProxy = channelUsesProxy(channel);
      const streamUrl = await resolveKoolStreamWithFailover(koolService, channel, req, {
        userAgent: incomingUa,
        sourceKey: 'kool',
        forceSignatureRefresh: shouldRefreshKoolSignaturePerOpen(req)
      });
      if (!isValidKoolStreamUrl(streamUrl)) return res.status(502).send('Kool resolve failed');
      const upstreamProfile = buildKoolUpstreamProfile('kool', slug);
      const profileToken = issueUpstreamProfile(upstreamProfile);
      if (useProxy) {
        const candidates = resolveFailoverCandidates(slug, streamUrl);
        await proxyManifest(streamUrl, req, res, `kool-proxy:${slug}`, { candidates, profileToken, upstreamProfile });
        return;
      }
      const directCandidates = resolveFailoverCandidates(slug, streamUrl);
      const redirectUrl = await resolveFirstReachableUrl(directCandidates, req, upstreamProfile, { requireWorking: true });
      if (!redirectUrl) {
        res.setHeader('X-Direct-Fallback', 'proxy');
        const candidates = resolveFailoverCandidates(slug, streamUrl);
        await proxyManifest(streamUrl, req, res, `kool-direct-fallback:${slug}`, { candidates, profileToken, upstreamProfile });
        return;
      }
      res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, max-age=0');
      return res.redirect(302, redirectUrl);
    } catch (error) {
      console.error('Kool signed redirect error:', error.message);
      return res.status(502).send('Kool stream unavailable');
    }
  }"""
    new_kool = """  if (sourceKey === 'kool') {
    try {
      // Same as Lokke: resolve then 302 to CDN — never rewrite via /k1/proxy/seg.
      const streamUrl = await resolveKoolStreamWithFailover(koolService, channel, req, {
        userAgent: incomingUa,
        sourceKey: 'kool',
        forceSignatureRefresh: shouldRefreshKoolSignaturePerOpen(req)
      });
      if (!isValidKoolStreamUrl(streamUrl)) return res.status(502).send('Kool resolve failed');
      res.setHeader('X-Kool-Playback', 'direct-redirect');
      res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, max-age=0');
      return res.redirect(302, streamUrl);
    } catch (error) {
      console.error('Kool signed redirect error:', error.message);
      return res.status(502).send('Kool stream unavailable');
    }
  }"""
    text = replace_once(text, old_kool, new_kool, "kool-deliver-redirect")

    # kool channel-proxy legacy → also 302
    old_kool_proxy = """  if (sourceKey === 'kool') {
    try {
      const streamUrl = await resolveKoolStreamWithFailover(koolService, channel, req, {
        userAgent: incomingUa,
        sourceKey: 'kool',
        forceSignatureRefresh: shouldRefreshKoolSignaturePerOpen(req)
      });
      if (!isValidKoolStreamUrl(streamUrl)) return res.status(502).send('Kool resolve failed');
      const candidates = resolveFailoverCandidates(slug, streamUrl);
      const upstreamProfile = buildKoolUpstreamProfile('kool', slug);
      const profileToken = issueUpstreamProfile(upstreamProfile);
      await proxyManifest(streamUrl, req, res, `kool-fallback:${slug}`, { candidates, profileToken, upstreamProfile });
      return;
    } catch (error) {
      console.error('Kool signed proxy error:', error.message);
      return res.status(502).send('Kool stream unavailable');
    }
  }"""
    new_kool_proxy = """  if (sourceKey === 'kool') {
    try {
      const streamUrl = await resolveKoolStreamWithFailover(koolService, channel, req, {
        userAgent: incomingUa,
        sourceKey: 'kool',
        forceSignatureRefresh: shouldRefreshKoolSignaturePerOpen(req)
      });
      if (!isValidKoolStreamUrl(streamUrl)) return res.status(502).send('Kool resolve failed');
      res.setHeader('X-Kool-Playback', 'direct-redirect-from-proxy-route');
      res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, max-age=0');
      return res.redirect(302, streamUrl);
    } catch (error) {
      console.error('Kool signed proxy error:', error.message);
      return res.status(502).send('Kool stream unavailable');
    }
  }"""
    text = replace_once(text, old_kool_proxy, new_kool_proxy, "kool-proxy-redirect")

    # nimi direct path: never fall through to empty probe — always 302 to streamUrl
    old_nimi = """      if (useProxy) {
        const profileToken = issueUpstreamProfile(buildChannelUpstreamProfile(channel, slug));
        const candidates = resolveFailoverCandidates(slug, streamUrl);
        await proxyManifest(streamUrl, req, res, `nimi:${slug}`, { profileToken, candidates });
        return;
      }
      const directCandidates = resolveFailoverCandidates(slug, streamUrl);
      const redirectUrl = await resolveFirstReachableUrl(directCandidates, req, buildChannelUpstreamProfile(channel, slug));
      res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, max-age=0');
      return res.redirect(302, redirectUrl || streamUrl);"""
    new_nimi = """      if (useProxy) {
        const profileToken = issueUpstreamProfile(buildChannelUpstreamProfile(channel, slug));
        const candidates = resolveFailoverCandidates(slug, streamUrl);
        await proxyManifest(streamUrl, req, res, `nimi:${slug}`, { profileToken, candidates });
        return;
      }
      // Direct mode: 302 to source CDN (Lokke-style — no VPS probe / no proxy fallback).
      res.setHeader('X-Nimi-Playback', 'direct-redirect');
      res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, max-age=0');
      return res.redirect(302, streamUrl);"""
    text = replace_once(text, old_nimi, new_nimi, "nimi-direct-redirect")

    # v2 direct path
    old_v2 = """      if (channelUsesProxy(channel)) {
        const profileToken = issueUpstreamProfile({ sourceKey: 'v2', channelSlug: slug });
        const candidates = resolveFailoverCandidates(slug, streamUrl);
        await proxyManifest(streamUrl, req, res, `v2:${slug}`, { profileToken, candidates });
        return;
      }
      const directCandidates = resolveFailoverCandidates(slug, streamUrl);
      const redirectUrl = await resolveFirstReachableUrl(directCandidates, req);
      res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, max-age=0');
      return res.redirect(302, redirectUrl || streamUrl);"""
    new_v2 = """      if (channelUsesProxy(channel)) {
        const profileToken = issueUpstreamProfile({ sourceKey: 'v2', channelSlug: slug });
        const candidates = resolveFailoverCandidates(slug, streamUrl);
        await proxyManifest(streamUrl, req, res, `v2:${slug}`, { profileToken, candidates });
        return;
      }
      res.setHeader('X-V2-Playback', 'direct-redirect');
      res.setHeader('Cache-Control', 'no-store, no-cache, must-revalidate, max-age=0');
      return res.redirect(302, streamUrl);"""
    text = replace_once(text, old_v2, new_v2, "v2-direct-redirect")

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
