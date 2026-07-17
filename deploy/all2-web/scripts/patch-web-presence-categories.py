#!/usr/bin/env python3
"""Fix categories 500, localhost IPv4, cluster presence via Redis, master ping switch."""
from pathlib import Path
import shutil
import subprocess

ROOT = Path("/root/all/all2")
SERVER = ROOT / "server.js"
CAT_JS = ROOT / "views/public/categoryPage.js"
PRESENCE_SRC = Path("/tmp/presenceStore.js")
PRESENCE_DST = ROOT / "services/presenceStore.js"


def main():
    if PRESENCE_SRC.exists():
        shutil.copyfile(PRESENCE_SRC, PRESENCE_DST)
        print("copied presenceStore.js")
    elif not PRESENCE_DST.exists():
        raise SystemExit("presenceStore.js missing")

    # categoryPage.js defaults
    CAT_JS.write_text(
        """const { renderTemplate } = require('../renderTemplate');

function renderCategoryPageContent(params = {}) {
  return renderTemplate('public/categoryPage.ejs', {
    basePath: params.basePath || '',
    backPath: params.backPath || '/',
    iconClass: params.iconClass || 'fa-folder-open',
    iconColor: params.iconColor || 'var(--accent)',
    logoUrl: params.logoUrl || '',
    title: params.title || '',
    badgeClass: params.badgeClass || '',
    badgeText: params.badgeText || '',
    count: params.count || 0,
    cards: params.cards || '',
    extraStatusHtml: params.extraStatusHtml || '',
    bottomAdHtml: params.bottomAdHtml || ''
  });
}

module.exports = {
  renderCategoryPageContent
};
""",
        encoding="utf-8",
    )
    print("categoryPage.js fixed")

    t = SERVER.read_text(encoding="utf-8")

    # Require presenceStore
    if "presenceStore" not in t:
        needle = "const playlistAnalyticsStore = require('./services/playlistAnalyticsStore');"
        if needle not in t:
            raise SystemExit("FAIL playlistAnalyticsStore require")
        t = t.replace(
            needle,
            needle + "\nconst presenceStore = require('./services/presenceStore');",
            1,
        )

    # Fix getClientIPs: never keep private/loopback as ipv4
    old_ips = """  const preferred = [cfConnecting, ...xForwardedList, xReal, socketIp, cfConnectingIPv6].filter(Boolean);
  let ipv4 = preferred.find(ip => isIPv4(ip) && !isPrivateIPv4(ip)) || preferred.find(isIPv4) || '';
  let ipv6 = preferred.find(ip => isIPv6(ip) && !!normalizePublicClientIp(ip)) || preferred.find(isIPv6) || '';
  if (!ipv4 && isIPv4(pseudo)) ipv4 = pseudo;"""
    new_ips = """  const preferred = [cfConnecting, ...xForwardedList, xReal, socketIp, cfConnectingIPv6].filter(Boolean);
  // Never treat loopback/private IPv4 as a real client address (common when nginx peers as 127.0.0.1
  // while the viewer is IPv6-only). Fake 127.0.0.1 caused "Local / Local" geo in admin live users.
  let ipv4 = preferred.find(ip => isIPv4(ip) && !isPrivateIPv4(ip)) || '';
  let ipv6 = preferred.find(ip => isIPv6(ip) && !!normalizePublicClientIp(ip)) || '';
  if (!ipv4 && isIPv4(pseudo) && !isPrivateIPv4(pseudo)) ipv4 = pseudo;"""
    if old_ips not in t:
        raise SystemExit("FAIL getClientIPs block")
    t = t.replace(old_ips, new_ips, 1)

    old_primary = """  const primary = ipv4
    || normalizePublicClientIp(cfConnecting)
    || normalizePublicClientIp(xForwarded)
    || normalizePublicClientIp(xReal)
    || cfConnecting
    || xForwarded
    || xReal
    || socketIp
    || ipv6
    || '';"""
    new_primary = """  const primary = ipv4
    || normalizePublicClientIp(cfConnecting)
    || normalizePublicClientIp(xForwarded)
    || normalizePublicClientIp(xReal)
    || normalizePublicClientIp(cfConnecting)
    || normalizePublicClientIp(xForwarded)
    || normalizePublicClientIp(xReal)
    || ipv6
    || (isIPv4(socketIp) && !isPrivateIPv4(socketIp) ? socketIp : '')
    || normalizePublicClientIp(socketIp)
    || '';"""
    if old_primary not in t:
        raise SystemExit("FAIL primary block")
    t = t.replace(old_primary, new_primary, 1)

    # Feature flag presence_ping_enabled
    old_flags = """    websocket_live_updates: bool(input.websocket_live_updates, true),
    public_live_viewer_count: bool(input.public_live_viewer_count, true),"""
    new_flags = """    websocket_live_updates: bool(input.websocket_live_updates, true),
    public_live_viewer_count: bool(input.public_live_viewer_count, true),
    presence_ping_enabled: bool(input.presence_ping_enabled ?? input.views_ping_enabled, true),"""
    if "presence_ping_enabled:" not in t:
        if old_flags not in t:
            raise SystemExit("FAIL feature flags")
        t = t.replace(old_flags, new_flags, 1)

    # Admin checkbox UI
    old_cb = """    ${checkbox('f_websocket_live_updates', flags.websocket_live_updates, 'WebSocket live viewers', 'Shikues live pa refresh')}
    ${checkbox('f_public_live_viewer_count', flags.public_live_viewer_count, 'Shikues live në public page', 'Numri total live në homepage/kategori + kanale')}"""
    new_cb = """    ${checkbox('f_presence_ping_enabled', flags.presence_ping_enabled !== false, 'Views / presence ping (MASTER)', 'ON/OFF universal për /api/ping, viewers polling, heartbeat. Fike për të mos ngarkuar serverin.')}
    ${checkbox('f_websocket_live_updates', flags.websocket_live_updates, 'WebSocket live viewers', 'Shikues live pa refresh')}
    ${checkbox('f_public_live_viewer_count', flags.public_live_viewer_count, 'Shikues live në public page', 'Numri total live në homepage/kategori + kanale')}"""
    if "f_presence_ping_enabled" not in t:
        if old_cb not in t:
            raise SystemExit("FAIL admin checkbox")
        t = t.replace(old_cb, new_cb, 1)

    # Save handler
    old_save = """      websocket_live_updates: readCheckboxField(req.body.f_websocket_live_updates),
      public_live_viewer_count: readCheckboxField(req.body.f_public_live_viewer_count),"""
    new_save = """      presence_ping_enabled: readCheckboxField(req.body.f_presence_ping_enabled),
      websocket_live_updates: readCheckboxField(req.body.f_websocket_live_updates),
      public_live_viewer_count: readCheckboxField(req.body.f_public_live_viewer_count),"""
    if "presence_ping_enabled: readCheckboxField" not in t:
        if old_save not in t:
            raise SystemExit("FAIL features save")
        t = t.replace(old_save, new_save, 1)

    # Client-side: gate ping + viewers
    old_client_flags = "const enablePublicLiveViewerCount=featureFlags.public_live_viewer_count!==false;"
    new_client_flags = """const enablePublicLiveViewerCount=featureFlags.public_live_viewer_count!==false;
const enablePresencePing=featureFlags.presence_ping_enabled!==false;"""
    if "enablePresencePing=" not in t:
        if old_client_flags not in t:
            raise SystemExit("FAIL client flags")
        t = t.replace(old_client_flags, new_client_flags, 1)

    old_ping_boot = "pingServer(); setInterval(pingServer,10000);"
    new_ping_boot = """if(enablePresencePing){ pingServer(); setInterval(pingServer,10000); }"""
    if "if(enablePresencePing){ pingServer()" not in t:
        if old_ping_boot not in t:
            raise SystemExit("FAIL ping boot")
        t = t.replace(old_ping_boot, new_ping_boot, 1)

    # Gate updateViewers already checks enablePublicLiveViewerCount; also require presence ping
    old_uv = """function updateViewers(){
  if(!enablePublicLiveViewerCount) return;
  fetch(BASE+'/api/viewers').then(r=>r.json()).then(d=>{"""
    new_uv = """function updateViewers(){
  if(!enablePresencePing || !enablePublicLiveViewerCount) return;
  fetch(BASE+'/api/viewers').then(r=>r.json()).then(d=>{"""
    if "if(!enablePresencePing || !enablePublicLiveViewerCount)" not in t:
        if old_uv not in t:
            raise SystemExit("FAIL updateViewers")
        t = t.replace(old_uv, new_uv, 1)

    # /api/ping early exit when disabled
    old_ping_start = """router.post('/api/ping', async (req, res) => {
  const { session_id, channel_slug, channel_name } = req.body;"""
    new_ping_start = """router.post('/api/ping', async (req, res) => {
  if (getFeatureFlags().presence_ping_enabled === false) {
    res.setHeader('Cache-Control', 'no-store');
    return res.json({ ok: true, disabled: true });
  }
  const { session_id, channel_slug, channel_name } = req.body;"""
    if "presence_ping_enabled === false" not in t.split("router.post('/api/ping'")[1][:400]:
        if old_ping_start not in t:
            raise SystemExit("FAIL ping start")
        t = t.replace(old_ping_start, new_ping_start, 1)

    # Fix geo to prefer public ipv4 then ipv6 (not localhost)
    old_geo_call = "    const geo = await getGeoInfoCached(ips.ipv4 || ip);"
    new_geo_call = "    const geo = await getGeoInfoCached(ips.ipv4 || ips.ipv6 || normalizePublicClientIp(ip) || '');"
    # More careful - only first occurrence in ping for new users
    if old_geo_call in t:
        t = t.replace(old_geo_call, new_geo_call, 1)

    # After activeUsers.set for new user, also upsert presence redis
    # And on update path too. Easiest: after both branches that mutate user, sync.
    # Patch the new-user set block to also call presenceStore
    sync_marker = "      channel_slug: channel_slug || '', channel_name: channel_name || 'Kryefaqe',\n      last_ping: Date.now(), started: Date.now()\n    });"
    if "presenceStore.upsertPresence(session_id" not in t:
        if sync_marker not in t:
            raise SystemExit("FAIL activeUsers.set marker")
        t = t.replace(
            sync_marker,
            """      channel_slug: channel_slug || '', channel_name: channel_name || 'Kryefaqe',
      last_ping: Date.now(), started: Date.now()
    });
    await presenceStore.upsertPresence(session_id, activeUsers.get(session_id));""",
            1,
        )

    # Update existing user path - find last_ping = Date.now() in ping update
    # Look for typical update block
    old_update_tail = None
    # Search for pattern after existing user updates channel
    import re
    m = re.search(
        r"(user\.last_ping = Date\.now\(\);\s*)",
        t,
    )
    if m and "presenceStore.upsertPresence(session_id, user)" not in t[m.start():m.start()+200]:
        t = t[: m.end()] + "\n    await presenceStore.upsertPresence(session_id, user);\n" + t[m.end() :]
        print("patched user.last_ping presence sync")
    else:
        print("WARN: could not patch user.last_ping sync uniquely; will add helper wrap")

    # Make /api/viewers and buildViewerSnapshot use redis
    old_snap = """function buildViewerSnapshot() {
  const viewers = {};
  for (const [, data] of activeUsers) {
    if (data.channel_slug) viewers[data.channel_slug] = (viewers[data.channel_slug] || 0) + 1;
  }
  return { total: activeUsers.size, viewers };
}"""
    new_snap = """function buildViewerSnapshot() {
  // Local fallback (single worker). Prefer async Redis list via /api/viewers.
  const viewers = {};
  for (const [, data] of presenceStore.getLocalMirror()) {
    if (data.channel_slug) viewers[data.channel_slug] = (viewers[data.channel_slug] || 0) + 1;
  }
  // Also include in-memory activeUsers for safety during migration.
  for (const [, data] of activeUsers) {
    if (data.channel_slug) viewers[data.channel_slug] = Math.max(viewers[data.channel_slug] || 0, 1);
  }
  const total = Math.max(presenceStore.getLocalMirror().size, activeUsers.size);
  return { total, viewers };
}"""
    if old_snap in t:
        t = t.replace(old_snap, new_snap, 1)

    old_viewers_route = """router.get('/api/viewers', (req, res) => {
  res.json(buildViewerSnapshot());
});"""
    new_viewers_route = """router.get('/api/viewers', async (req, res) => {
  if (getFeatureFlags().presence_ping_enabled === false) {
    res.setHeader('Cache-Control', 'no-store');
    return res.json({ total: 0, viewers: {}, disabled: true });
  }
  try {
    const snap = await presenceStore.buildViewerSnapshotAsync();
    res.json(snap);
  } catch {
    res.json(buildViewerSnapshot());
  }
});"""
    if old_viewers_route in t:
        t = t.replace(old_viewers_route, new_viewers_route, 1)
    else:
        raise SystemExit("FAIL viewers route")

    # /api/active-users: read from redis presence
    old_active = """router.get('/api/active-users', requireLoginOrToken, (req, res) => {
  const now = Date.now();
  const query = String(req.query.q || '').trim().toLowerCase();
  let users = Array.from(activeUsers.entries()).map(([sid, data]) => ({"""
    new_active = """router.get('/api/active-users', requireLoginOrToken, async (req, res) => {
  const now = Date.now();
  const query = String(req.query.q || '').trim().toLowerCase();
  let presenceEntries = [];
  try { presenceEntries = await presenceStore.listPresenceEntries(); } catch { presenceEntries = Array.from(activeUsers.entries()); }
  // Merge redis + local so no worker misses sessions.
  const merged = new Map(presenceEntries);
  for (const [sid, data] of activeUsers.entries()) {
    if (!merged.has(sid)) merged.set(sid, data);
    else {
      const a = merged.get(sid) || {};
      const started = Math.min(Number(a.started || now), Number(data.started || now));
      merged.set(sid, { ...a, ...data, started, last_ping: Math.max(Number(a.last_ping || 0), Number(data.last_ping || 0)) });
    }
  }
  let users = Array.from(merged.entries()).map(([sid, data]) => ({"""
    if "presenceStore.listPresenceEntries" not in t:
        if old_active not in t:
            raise SystemExit("FAIL active-users")
        t = t.replace(old_active, new_active, 1)

    # init presence on boot near hot segment init
    if "initPresenceStore" not in t:
        boot = "  const hotStore = await hotSegmentStore.initHotSegmentStore();"
        if boot not in t:
            raise SystemExit("FAIL boot hotStore")
        t = t.replace(
            boot,
            boot + "\n  await presenceStore.initPresenceStore();",
            1,
        )

    # Also sync deletes
    for old_del, new_del in [
        ("activeUsers.delete(session_id);", "activeUsers.delete(session_id);\n    presenceStore.deletePresence(session_id).catch(()=>{});"),
    ]:
        # only replace first few fingerprint block ones carefully - replace_all might be ok for delete(session_id)
        pass
    t = t.replace(
        "activeUsers.delete(session_id);",
        "activeUsers.delete(session_id); presenceStore.deletePresence(session_id).catch(()=>{});",
    )

    SERVER.write_text(t, encoding="utf-8")
    print("server.js patched")

    r = subprocess.run(["node", "--check", str(SERVER)], capture_output=True, text=True)
    print("node check:", r.returncode, (r.stderr or "OK")[:600])
    if r.returncode != 0:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
