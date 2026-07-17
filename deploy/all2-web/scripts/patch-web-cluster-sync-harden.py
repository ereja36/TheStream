#!/usr/bin/env python3
"""Harden cluster kool/lokke sync: always seed disk, refuse shrink, retry reloads."""
from pathlib import Path
import re
import subprocess

p = Path("/root/all/all2/server.js")
t = p.read_text(encoding="utf-8")

old = """  if (syncFromDisk) {
    // Cluster workers must reload kool/lokke from disk — NOT from local service memory.
    // Local service caches diverge after Load ALL on a single worker (e.g. 4 vs 16 categories).
    const loaded = reloadKoolCachesFromDisk();
    console.log(`[cluster-sync] kool caches from disk: kool=${loaded.kool} lokke=${loaded.koolLokke}`);
    if (!koolEnabled) koolChannelsDb = {};
    if (!koolLokkeEnabled) koolLokkeChannelsDb = {};
  } else {
    if (koolEnabled) syncKoolChannelsFromService({ persist: true });
    else koolChannelsDb = {};
    if (koolLokkeEnabled) syncKoolLokkeChannelsFromService({ persist: true });
    else koolLokkeChannelsDb = {};
  }"""

new = """  // Always seed kool/lokke from disk first. Service memory is worker-local and can be stale/smaller.
  {
    const loaded = reloadKoolCachesFromDisk();
    console.log(`[cluster-sync] kool caches from disk: kool=${loaded.kool} lokke=${loaded.koolLokke} syncFromDisk=${syncFromDisk}`);
  }
  if (!syncFromDisk) {
    if (koolEnabled) syncKoolChannelsFromService({ persist: true });
    if (koolLokkeEnabled) syncKoolLokkeChannelsFromService({ persist: true });
  }
  if (!koolEnabled) koolChannelsDb = {};
  if (!koolLokkeEnabled) koolLokkeChannelsDb = {};"""

if old not in t:
    if "refuse persist shrink" in t and "Always seed kool/lokke from disk first" in t:
        print("disk seed already applied")
    else:
        raise SystemExit("FAIL block not found")
else:
    t = t.replace(old, new, 1)
    print("disk seed patched")

old_persist = """  if (persist) saveJSON(KOOL_LOKKE_CACHE_FILE, koolLokkeChannelsDb);
  return koolLokkeChannelsDb;
}
function getKoolCategorySummaries() {"""
new_persist = """  if (persist) {
    try {
      const diskCount = Object.keys(loadJSON(KOOL_LOKKE_CACHE_FILE, {}) || {}).length;
      const nextCount = Object.keys(koolLokkeChannelsDb || {}).length;
      if (!replaceAll && diskCount > 0 && nextCount + 25 < diskCount) {
        console.warn(`[lokke] refuse persist shrink ${nextCount} < disk ${diskCount}; keeping disk cache`);
        const restored = reloadKoolCachesFromDisk();
        console.warn(`[lokke] restored from disk lokke=${restored.koolLokke}`);
        return koolLokkeChannelsDb;
      }
    } catch (err) {
      console.warn('[lokke] persist guard failed:', String(err && err.message || err));
    }
    saveJSON(KOOL_LOKKE_CACHE_FILE, koolLokkeChannelsDb);
  }
  return koolLokkeChannelsDb;
}
function getKoolCategorySummaries() {"""
if "refuse persist shrink" not in t:
    if old_persist not in t:
        raise SystemExit("FAIL persist guard")
    t = t.replace(old_persist, new_persist, 1)
    print("lokke persist guard patched")

old_persist_k = """  if (persist) saveJSON(KOOL_CACHE_FILE, koolChannelsDb);
  return koolChannelsDb;
}
function syncKoolLokkeChannelsFromService(options = {}) {"""
new_persist_k = """  if (persist) {
    try {
      const diskCount = Object.keys(loadJSON(KOOL_CACHE_FILE, {}) || {}).length;
      const nextCount = Object.keys(koolChannelsDb || {}).length;
      if (!replaceAll && diskCount > 0 && nextCount + 25 < diskCount) {
        console.warn(`[kool] refuse persist shrink ${nextCount} < disk ${diskCount}; keeping disk cache`);
        reloadKoolCachesFromDisk();
        return koolChannelsDb;
      }
    } catch (err) {}
    saveJSON(KOOL_CACHE_FILE, koolChannelsDb);
  }
  return koolChannelsDb;
}
function syncKoolLokkeChannelsFromService(options = {}) {"""
if "[kool] refuse persist shrink" not in t:
    if old_persist_k not in t:
        raise SystemExit("FAIL kool persist guard")
    t = t.replace(old_persist_k, new_persist_k, 1)
    print("kool persist guard patched")

# Silence category visibility config saves
for label, old_s, new_s in [
    (
        "lokke-sum",
        """  if (changed) saveJSON(CONFIG_FILE, appConfig);
  return summaries.map(item => ({ ...item, visibleOnMain: isKoolLokkeCategoryVisible(item.slug) }));
}
function getKoolChannelsByCategorySlug(categorySlug) {""",
        """  if (changed) {
    const prev = suppressClusterBroadcast;
    suppressClusterBroadcast = true;
    try { saveJSON(CONFIG_FILE, appConfig); }
    finally { suppressClusterBroadcast = prev; }
  }
  return summaries.map(item => ({ ...item, visibleOnMain: isKoolLokkeCategoryVisible(item.slug) }));
}
function getKoolChannelsByCategorySlug(categorySlug) {""",
    ),
    (
        "kool-sum",
        """  if (changed) saveJSON(CONFIG_FILE, appConfig);
  return summaries.map(item => ({ ...item, visibleOnMain: isKoolCategoryVisible(item.slug) }));
}
function getKoolLokkeCategorySummaries() {""",
        """  if (changed) {
    const prev = suppressClusterBroadcast;
    suppressClusterBroadcast = true;
    try { saveJSON(CONFIG_FILE, appConfig); }
    finally { suppressClusterBroadcast = prev; }
  }
  return summaries.map(item => ({ ...item, visibleOnMain: isKoolCategoryVisible(item.slug) }));
}
function getKoolLokkeCategorySummaries() {""",
    ),
]:
    if "suppressClusterBroadcast = true;\n    try { saveJSON(CONFIG_FILE, appConfig); }" in t and label == "lokke-sum":
        # may already be partially done
        pass
    if old_s in t:
        t = t.replace(old_s, new_s, 1)
        print(label, "patched")
    else:
        print(label, "skip")

new_sched = """function scheduleClusterReload(payload) {
  clearTimeout(clusterReloadTimer);
  const queuedPayload = payload;
  clusterReloadTimer = setTimeout(async () => {
    if (clusterReloadRunning || updateDatabasesRunning) {
      // Retry shortly so no worker stays stale while another update is in flight.
      clusterReloadTimer = setTimeout(() => scheduleClusterReload(queuedPayload), 2000);
      return;
    }
    clusterReloadRunning = true;
    try {
      console.log(`[cluster-sync] reload requested (${queuedPayload?.reason || 'update'})`);
      const reason = String(queuedPayload?.reason || '');
      if (reason.startsWith('playlist_analytics') || reason.includes('playlist_analytics')) {
        playlistAnalyticsState = normalizePlaylistAnalyticsState(loadJSON(PLAYLIST_ANALYTICS_FILE, {}));
        playlistAnalyticsStore.replaceMemoryState(playlistAnalyticsState);
      } else {
        await updateDatabasesInternal({ syncFromDisk: true });
        playlistAnalyticsState = normalizePlaylistAnalyticsState(loadJSON(PLAYLIST_ANALYTICS_FILE, {}));
        playlistAnalyticsStore.replaceMemoryState(playlistAnalyticsState);
      }
    } catch (err) {
      console.warn('[cluster-sync] reload failed:', String(err?.message || err));
    } finally {
      clusterReloadRunning = false;
    }
  }, 1500);
}"""

m = re.search(r"function scheduleClusterReload\(payload\) \{.*?\n\}", t, flags=re.S)
if not m:
    raise SystemExit("FAIL scheduleClusterReload")
if "Retry shortly so no worker stays stale" not in t:
    t = t[: m.start()] + new_sched + t[m.end() :]
    print("scheduleClusterReload patched")
else:
    print("scheduleClusterReload already ok")

p.write_text(t, encoding="utf-8")
r = subprocess.run(["node", "--check", str(p)], capture_output=True, text=True)
print("check", r.returncode, (r.stderr or "OK")[:500])
if r.returncode != 0:
    raise SystemExit(1)
print("DONE")
