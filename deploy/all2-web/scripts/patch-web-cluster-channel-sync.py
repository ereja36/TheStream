#!/usr/bin/env python3
"""Fix cluster channel sync: reload kool/lokke from disk on all workers."""
from pathlib import Path
import shutil
import subprocess
import re

ROOT = Path("/root/all/all2")
SERVER = ROOT / "server.js"
SRC = Path("/tmp/channelStateSync.js")
DST = ROOT / "services/channelStateSync.js"


def main():
    if SRC.exists():
        shutil.copyfile(SRC, DST)
        print("copied channelStateSync.js")

    t = SERVER.read_text(encoding="utf-8")

    if "channelStateSync" not in t:
        needle = "const presenceStore = require('./services/presenceStore');"
        if needle not in t:
            needle = "const playlistAnalyticsStore = require('./services/playlistAnalyticsStore');"
            t = t.replace(
                needle,
                needle + "\nconst presenceStore = require('./services/presenceStore');\nconst channelStateSync = require('./services/channelStateSync');",
                1,
            ) if "presenceStore" not in t else t.replace(
                "const presenceStore = require('./services/presenceStore');",
                "const presenceStore = require('./services/presenceStore');\nconst channelStateSync = require('./services/channelStateSync');",
                1,
            )
        else:
            t = t.replace(
                needle,
                needle + "\nconst channelStateSync = require('./services/channelStateSync');",
                1,
            )

    # Expand CLUSTER_SYNC_FILES
    old_files = """const CLUSTER_SYNC_FILES = new Set([
  CONFIG_FILE,
  PLAYLISTS_FILE,
  CATEGORIES_FILE,
  CUSTOM_CH_FILE,
  API_TV_CACHE_FILE,
  API_SPORT_CACHE_FILE
]);"""
    new_files = """const CLUSTER_SYNC_FILES = new Set([
  CONFIG_FILE,
  PLAYLISTS_FILE,
  CATEGORIES_FILE,
  CUSTOM_CH_FILE,
  API_TV_CACHE_FILE,
  API_SPORT_CACHE_FILE,
  KOOL_CACHE_FILE,
  KOOL_LOKKE_CACHE_FILE
]);"""
    if "KOOL_LOKKE_CACHE_FILE" not in t.split("CLUSTER_SYNC_FILES")[1][:500]:
        if old_files not in t:
            raise SystemExit("FAIL CLUSTER_SYNC_FILES")
        t = t.replace(old_files, new_files, 1)

    # Expand refreshAdminMutableState
    old_refresh = """function refreshAdminMutableState() {
  appConfig = loadJSON(CONFIG_FILE, appConfig);
  customPlaylists = loadJSON(PLAYLISTS_FILE, customPlaylists);
  customCategories = loadJSON(CATEGORIES_FILE, customCategories);
  customChannels = loadJSON(CUSTOM_CH_FILE, customChannels);
}"""
    new_refresh = """function refreshAdminMutableState() {
  appConfig = loadJSON(CONFIG_FILE, appConfig);
  customPlaylists = loadJSON(PLAYLISTS_FILE, customPlaylists);
  customCategories = loadJSON(CATEGORIES_FILE, customCategories);
  customChannels = loadJSON(CUSTOM_CH_FILE, customChannels);
}
function reloadKoolCachesFromDisk() {
  try {
    koolChannelsDb = normalizeKoolChannelsDb(loadJSON(KOOL_CACHE_FILE, {}));
  } catch {
    koolChannelsDb = {};
  }
  try {
    koolLokkeChannelsDb = normalizeKoolChannelsDb(loadJSON(KOOL_LOKKE_CACHE_FILE, {}), {
      source: 'kool_lokke',
      sourceKey: 'kool_lokke',
      type: 'kool_lokke',
      slugPrefix: 'koollokke'
    });
  } catch {
    koolLokkeChannelsDb = {};
  }
  try {
    if (koolService?.channelsCache?.clear) koolService.channelsCache.clear();
    if (koolService?.categoriesCache?.clear) koolService.categoriesCache.clear();
    if (koolService?.inflightCategoryRequests?.clear) koolService.inflightCategoryRequests.clear();
    if (Object.keys(koolChannelsDb).length) {
      koolService.primeFromRecords(Object.values(koolChannelsDb).map(mapKoolDbChannelToServiceRecord));
    }
  } catch {}
  try {
    if (koolLokkeService?.channelsCache?.clear) koolLokkeService.channelsCache.clear();
    if (koolLokkeService?.categoriesCache?.clear) koolLokkeService.categoriesCache.clear();
    if (koolLokkeService?.inflightCategoryRequests?.clear) koolLokkeService.inflightCategoryRequests.clear();
    if (Object.keys(koolLokkeChannelsDb).length) {
      koolLokkeService.primeFromRecords(Object.values(koolLokkeChannelsDb).map(mapKoolDbChannelToServiceRecord));
    }
  } catch {}
  return {
    kool: Object.keys(koolChannelsDb).length,
    koolLokke: Object.keys(koolLokkeChannelsDb).length
  };
}"""
    if "function reloadKoolCachesFromDisk" not in t:
        if old_refresh not in t:
            raise SystemExit("FAIL refreshAdminMutableState")
        t = t.replace(old_refresh, new_refresh, 1)

    # Replace kool sync block inside updateDatabasesInternal
    old_kool_sync = """  if (koolEnabled) syncKoolChannelsFromService({ persist: true });
  else koolChannelsDb = {};
  if (koolLokkeEnabled) syncKoolLokkeChannelsFromService({ persist: true });
  else koolLokkeChannelsDb = {};
  koolChannelsDb = applyDeletedSourcesToDb(koolChannelsDb);
  koolLokkeChannelsDb = applyDeletedSourcesToDb(koolLokkeChannelsDb);"""
    new_kool_sync = """  if (syncFromDisk) {
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
  }
  koolChannelsDb = applyDeletedSourcesToDb(koolChannelsDb);
  koolLokkeChannelsDb = applyDeletedSourcesToDb(koolLokkeChannelsDb);"""
    if "kool caches from disk" not in t:
        if old_kool_sync not in t:
            raise SystemExit("FAIL kool sync block")
        t = t.replace(old_kool_sync, new_kool_sync, 1)

    # Skip falcon/v2 network fetch on syncFromDisk to avoid cross-worker drift
    old_falcon = """  const previousFalconDb = falconChannelsDb;
  const nextFalconDb = {};
  if (falconEnabled) {
    try {
      const falconChannels = await falconService.fetchChannels();"""
    new_falcon = """  const previousFalconDb = falconChannelsDb;
  const nextFalconDb = {};
  if (falconEnabled && !syncFromDisk) {
    try {
      const falconChannels = await falconService.fetchChannels();"""
    if "falconEnabled && !syncFromDisk" not in t:
        if old_falcon not in t:
            print("WARN falcon block")
        else:
            t = t.replace(old_falcon, new_falcon, 1)

    old_v2 = """  const previousV2Db = v2ChannelsDb;
  const nextV2Db = {};
  if (v2Enabled) {
    try {
      const v2Channels = await v2Service.fetchChannels({ force: true });"""
    new_v2 = """  const previousV2Db = v2ChannelsDb;
  const nextV2Db = {};
  if (v2Enabled && !syncFromDisk) {
    try {
      const v2Channels = await v2Service.fetchChannels({ force: true });"""
    if "v2Enabled && !syncFromDisk" not in t:
        if old_v2 not in t:
            print("WARN v2 block")
        else:
            t = t.replace(old_v2, new_v2, 1)

    # After successful updateDatabasesInternal, bump version
    # Find end of updateDatabasesInternal before updateDatabasesRunning = false
    # Look for the console.log TV: Sport line near end
    marker = "  console.log(`TV: ${Object.keys(allChannelsDb).length}"
    if "channelStateSync.bumpChannelStateVersion" not in t and marker in t:
        t = t.replace(
            marker,
            "  channelStateSync.bumpChannelStateVersion(syncFromDisk ? 'syncFromDisk' : 'updateDatabases').catch(() => {});\n  console.log(`TV: ${Object.keys(allChannelsDb).length}",
            1,
        )

    # Wire Load ALL jobs to Redis shared status
    # kool load-all success path
    for name, job_var in [("kool", "koolLoadJob"), ("kool-lokke", "koolLokkeLoadJob")]:
        # status endpoint - merge redis
        pass

    # Patch kool-lokke load-all to use cluster lock and shared job
    old_lokke_load = """router.post('/api/integrations/kool-lokke/load-all', requireLoginOrToken, (req, res) => {
  if (!ensureSourceAvailableJson(res, 'kool_lokke')) return;
  if (koolLokkeLoadJob.running) {
    return res.status(409).json({ ok: false, error: 'Load all is already running', job: koolLokkeLoadJob, progress: koolLokkeService.getStatus().loadingProgress });
  }
  koolLokkeLoadJob.running = true;
  koolLokkeLoadJob.startedAt = Date.now();
  koolLokkeLoadJob.endedAt = 0;
  koolLokkeLoadJob.error = '';
  koolLokkeLoadJob.result = null;
  koolLokkeService.loadAllCategories({ force: true }).then(async result => {
    syncKoolLokkeChannelsFromService({ persist: true, replaceAll: true });
    await updateDatabases();
    koolLokkeLoadJob.running = false;
    koolLokkeLoadJob.endedAt = Date.now();
    koolLokkeLoadJob.result = result;
  }).catch(error => {
    koolLokkeLoadJob.running = false;
    koolLokkeLoadJob.endedAt = Date.now();
    koolLokkeLoadJob.error = error.message;
  });
  res.json({ ok: true, started: true, progress: koolLokkeService.getStatus().loadingProgress });
});"""

    new_lokke_load = """router.post('/api/integrations/kool-lokke/load-all', requireLoginOrToken, async (req, res) => {
  if (!ensureSourceAvailableJson(res, 'kool_lokke')) return;
  const sharedJob = await channelStateSync.getIntegrationJob('kool-lokke-load-all');
  if (koolLokkeLoadJob.running || sharedJob?.running) {
    return res.status(409).json({ ok: false, error: 'Load all is already running', job: sharedJob || koolLokkeLoadJob, progress: koolLokkeService.getStatus().loadingProgress });
  }
  koolLokkeLoadJob.running = true;
  koolLokkeLoadJob.startedAt = Date.now();
  koolLokkeLoadJob.endedAt = 0;
  koolLokkeLoadJob.error = '';
  koolLokkeLoadJob.result = null;
  await channelStateSync.setIntegrationJob('kool-lokke-load-all', { ...koolLokkeLoadJob });
  koolLokkeService.loadAllCategories({ force: true }).then(async result => {
    syncKoolLokkeChannelsFromService({ persist: true, replaceAll: true });
    await updateDatabases();
    // Force peers to reload kool/lokke caches from disk (authoritative after Load ALL).
    await broadcastStateReload('kool_lokke_load_all');
    koolLokkeLoadJob.running = false;
    koolLokkeLoadJob.endedAt = Date.now();
    koolLokkeLoadJob.result = result;
    await channelStateSync.setIntegrationJob('kool-lokke-load-all', { ...koolLokkeLoadJob });
  }).catch(async error => {
    koolLokkeLoadJob.running = false;
    koolLokkeLoadJob.endedAt = Date.now();
    koolLokkeLoadJob.error = error.message;
    await channelStateSync.setIntegrationJob('kool-lokke-load-all', { ...koolLokkeLoadJob });
  });
  res.json({ ok: true, started: true, progress: koolLokkeService.getStatus().loadingProgress });
});"""
    if "kool_lokke_load_all" not in t:
        if old_lokke_load not in t:
            raise SystemExit("FAIL lokke load-all")
        t = t.replace(old_lokke_load, new_lokke_load, 1)

    old_lokke_status = """router.get('/api/integrations/kool-lokke/load-all-status', requireLoginOrToken, (req, res) => {
  res.json({ ok: true, job: koolLokkeLoadJob, progress: koolLokkeService.getStatus().loadingProgress });
});"""
    new_lokke_status = """router.get('/api/integrations/kool-lokke/load-all-status', requireLoginOrToken, async (req, res) => {
  const sharedJob = await channelStateSync.getIntegrationJob('kool-lokke-load-all');
  res.json({ ok: true, job: sharedJob || koolLokkeLoadJob, progress: koolLokkeService.getStatus().loadingProgress });
});"""
    if "getIntegrationJob('kool-lokke-load-all')" not in t.split("load-all-status")[1][:300] if "load-all-status" in t else "":
        pass
    if old_lokke_status in t:
        t = t.replace(old_lokke_status, new_lokke_status, 1)

    # Same for kool load-all
    old_kool_load = """router.post('/api/integrations/kool/load-all', requireLoginOrToken, (req, res) => {
  if (!ensureSourceAvailableJson(res, 'kool')) return;
  if (koolLoadJob.running) {
    return res.status(409).json({ ok: false, error: 'Load all is already running', job: koolLoadJob, progress: koolService.getStatus().loadingProgress });
  }
  koolLoadJob.running = true;
  koolLoadJob.startedAt = Date.now();
  koolLoadJob.endedAt = 0;
  koolLoadJob.error = '';
  koolLoadJob.result = null;
  koolService.loadAllCategories({ force: true }).then(async result => {
    syncKoolChannelsFromService({ persist: true, replaceAll: true });
    await updateDatabases();
    koolLoadJob.running = false;
    koolLoadJob.endedAt = Date.now();
    koolLoadJob.result = result;
  }).catch(error => {
    koolLoadJob.running = false;
    koolLoadJob.endedAt = Date.now();
    koolLoadJob.error = error.message;
  });
  res.json({ ok: true, started: true, progress: koolService.getStatus().loadingProgress });
});"""
    new_kool_load = """router.post('/api/integrations/kool/load-all', requireLoginOrToken, async (req, res) => {
  if (!ensureSourceAvailableJson(res, 'kool')) return;
  const sharedJob = await channelStateSync.getIntegrationJob('kool-load-all');
  if (koolLoadJob.running || sharedJob?.running) {
    return res.status(409).json({ ok: false, error: 'Load all is already running', job: sharedJob || koolLoadJob, progress: koolService.getStatus().loadingProgress });
  }
  koolLoadJob.running = true;
  koolLoadJob.startedAt = Date.now();
  koolLoadJob.endedAt = 0;
  koolLoadJob.error = '';
  koolLoadJob.result = null;
  await channelStateSync.setIntegrationJob('kool-load-all', { ...koolLoadJob });
  koolService.loadAllCategories({ force: true }).then(async result => {
    syncKoolChannelsFromService({ persist: true, replaceAll: true });
    await updateDatabases();
    await broadcastStateReload('kool_load_all');
    koolLoadJob.running = false;
    koolLoadJob.endedAt = Date.now();
    koolLoadJob.result = result;
    await channelStateSync.setIntegrationJob('kool-load-all', { ...koolLoadJob });
  }).catch(async error => {
    koolLoadJob.running = false;
    koolLoadJob.endedAt = Date.now();
    koolLoadJob.error = error.message;
    await channelStateSync.setIntegrationJob('kool-load-all', { ...koolLoadJob });
  });
  res.json({ ok: true, started: true, progress: koolService.getStatus().loadingProgress });
});"""
    if "kool_load_all" not in t:
        if old_kool_load not in t:
            raise SystemExit("FAIL kool load-all")
        t = t.replace(old_kool_load, new_kool_load, 1)

    old_kool_status = """router.get('/api/integrations/kool/load-all-status', requireLoginOrToken, (req, res) => {
  res.json({ ok: true, job: koolLoadJob, progress: koolService.getStatus().loadingProgress });
});"""
    new_kool_status = """router.get('/api/integrations/kool/load-all-status', requireLoginOrToken, async (req, res) => {
  const sharedJob = await channelStateSync.getIntegrationJob('kool-load-all');
  res.json({ ok: true, job: sharedJob || koolLoadJob, progress: koolService.getStatus().loadingProgress });
});"""
    if old_kool_status in t:
        t = t.replace(old_kool_status, new_kool_status, 1)

    # Admin diagnostic endpoint for cluster channel counts
    if "/api/admin/cluster-channel-state" not in t:
        insert_after = "router.get('/api/active-users', requireLoginOrToken, async (req, res) => {"
        # Better: insert near kool status
        diag = """
router.get('/api/admin/cluster-channel-state', requireLoginOrToken, async (req, res) => {
  const version = await channelStateSync.getChannelStateVersion();
  const lokkeSummaries = getKoolLokkeCategorySummaries();
  const koolSummaries = getKoolCategorySummaries();
  res.json({
    ok: true,
    pid: process.pid,
    version,
    presence_mode: presenceStore.getMode ? presenceStore.getMode() : 'n/a',
    counts: {
      allCombined: Object.keys(allCombined).length,
      kool: Object.keys(koolChannelsDb).length,
      koolLokke: Object.keys(koolLokkeChannelsDb).length,
      apiTv: Object.keys(allChannelsDb).length,
      sport: Object.keys(sportsDb).length,
      alb: Object.keys(albChannelsDb).length,
      koolCategories: koolSummaries.length,
      koolLokkeCategories: lokkeSummaries.length,
      koolLokkeCategoriesVisible: lokkeSummaries.filter(i => i.visibleOnMain).length
    },
    koolLokkeCategories: lokkeSummaries.map(i => ({ name: i.name, slug: i.slug, count: i.count, visibleOnMain: i.visibleOnMain }))
  });
});
"""
        anchor = "router.get('/api/integrations/kool-lokke/status', requireLoginOrToken, (req, res) => {"
        if anchor in t:
            t = t.replace(anchor, diag + "\n" + anchor, 1)
        else:
            print("WARN: could not insert cluster-channel-state")

    SERVER.write_text(t, encoding="utf-8")
    r = subprocess.run(["node", "--check", str(SERVER)], capture_output=True, text=True)
    print("node check:", r.returncode, (r.stderr or "OK")[:800])
    if r.returncode != 0:
        raise SystemExit(1)
    print("patched OK")


if __name__ == "__main__":
    main()
