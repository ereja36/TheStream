#!/usr/bin/env python3
"""Stop playlist_analytics from broadcasting heavy cluster reloads on every persist."""
from pathlib import Path
import subprocess
import time

SERVER = Path("/root/all/all2/server.js")
t = SERVER.read_text(encoding="utf-8")
changed = []

old = """    persistDisk: (state) => {
      suppressClusterBroadcast = true;
      try {
        playlistAnalyticsState = state && typeof state === 'object' ? state : playlistAnalyticsState;
        saveJSON(PLAYLIST_ANALYTICS_FILE, playlistAnalyticsState);
      } finally {
        suppressClusterBroadcast = false;
      }
      broadcastStateReload('playlist_analytics').catch(() => {});
    }"""

new = """    persistDisk: (state) => {
      // Analytics is already shared via Redis store — do NOT broadcast cluster
      // reloads on every write (was spamming updateDatabases / channel warm).
      suppressClusterBroadcast = true;
      try {
        playlistAnalyticsState = state && typeof state === 'object' ? state : playlistAnalyticsState;
        saveJSON(PLAYLIST_ANALYTICS_FILE, playlistAnalyticsState);
      } finally {
        suppressClusterBroadcast = false;
      }
    }"""

if "Analytics is already shared via Redis store" not in t:
    if old not in t:
        raise SystemExit("FAIL: playlist_analytics persistDisk block not found")
    t = t.replace(old, new, 1)
    changed.append("analytics-no-broadcast")
else:
    changed.append("analytics-no-broadcast:already")

# Also debounce playlist_analytics cluster reload handler if something else still broadcasts
old_sched = """      if (reason.startsWith('playlist_analytics') || reason.includes('playlist_analytics')) {
        playlistAnalyticsState = normalizePlaylistAnalyticsState(loadJSON(PLAYLIST_ANALYTICS_FILE, {}));
        playlistAnalyticsStore.replaceMemoryState(playlistAnalyticsState);
      } else {"""

new_sched = """      if (reason.startsWith('playlist_analytics') || reason.includes('playlist_analytics')) {
        // Lightweight: reload analytics mirror only — never touch channel DBs.
        playlistAnalyticsState = normalizePlaylistAnalyticsState(loadJSON(PLAYLIST_ANALYTICS_FILE, {}));
        playlistAnalyticsStore.replaceMemoryState(playlistAnalyticsState);
      } else {"""

if "Lightweight: reload analytics mirror only" not in t:
    if old_sched in t:
        t = t.replace(old_sched, new_sched, 1)
        changed.append("analytics-reload-comment")
    else:
        changed.append("analytics-reload:skip")
else:
    changed.append("analytics-reload:already")

SERVER.write_text(t, encoding="utf-8")
r = subprocess.run(["node", "--check", str(SERVER)], capture_output=True, text=True)
print("changed:", ", ".join(changed))
print("check", r.returncode, (r.stderr or "OK")[:400])
if r.returncode != 0:
    raise SystemExit(1)
subprocess.check_call(["pm2", "reload", "all2", "--update-env"])
time.sleep(4)
print("DONE")
