#!/usr/bin/env python3
"""Persist falcon/v2 channel DBs to disk and reload on cluster syncFromDisk."""
from pathlib import Path
import subprocess

SERVER = Path("/root/all/all2/server.js")
t = SERVER.read_text(encoding="utf-8")

# Add file constants near KOOL_LOKKE_CACHE_FILE
if "FALCON_CACHE_FILE" not in t:
    needle = "const KOOL_LOKKE_CACHE_FILE = path.join(DATA_DIR, 'kool_lokke_channels_cache.json');"
    if needle not in t:
        raise SystemExit("FAIL KOOL_LOKKE const")
    t = t.replace(
        needle,
        needle
        + "\nconst FALCON_CACHE_FILE = path.join(DATA_DIR, 'falcon_channels_cache.json');\n"
        + "const V2_CACHE_FILE = path.join(DATA_DIR, 'v2_channels_cache.json');\n"
        + "const ALB_CACHE_FILE = path.join(DATA_DIR, 'alb_channels_cache.json');",
        1,
    )

# Add to CLUSTER_SYNC_FILES
if "FALCON_CACHE_FILE," not in t:
    old = """  KOOL_CACHE_FILE,
  KOOL_LOKKE_CACHE_FILE
]);"""
    new = """  KOOL_CACHE_FILE,
  KOOL_LOKKE_CACHE_FILE,
  FALCON_CACHE_FILE,
  V2_CACHE_FILE,
  ALB_CACHE_FILE
]);"""
    if old not in t:
        raise SystemExit("FAIL CLUSTER_SYNC_FILES expand")
    t = t.replace(old, new, 1)

# Init from disk
if "let falconChannelsDb = loadJSON(FALCON_CACHE_FILE" not in t:
    t = t.replace(
        "let falconChannelsDb = {};\nlet v2ChannelsDb = {};",
        "let falconChannelsDb = loadJSON(FALCON_CACHE_FILE, {});\nlet v2ChannelsDb = loadJSON(V2_CACHE_FILE, {});",
        1,
    )

# After falcon assignment persist
old_f = """  falconChannelsDb = falconEnabled\n    ? (Object.keys(nextFalconDb).length ? nextFalconDb : previousFalconDb)\n    : {};"""
# Find and wrap - after filtered assignment later is better

# Patch the syncFromDisk falcon/v2 blocks to load disk
old_falcon = """  const previousFalconDb = falconChannelsDb;
  const nextFalconDb = {};
  if (falconEnabled && !syncFromDisk) {"""
new_falcon = """  const previousFalconDb = falconChannelsDb;
  const nextFalconDb = {};
  if (syncFromDisk) {
    falconChannelsDb = falconEnabled ? (loadJSON(FALCON_CACHE_FILE, previousFalconDb) || previousFalconDb) : {};
  } else if (falconEnabled) {"""
if "falconChannelsDb = falconEnabled ? (loadJSON(FALCON_CACHE_FILE" not in t:
    if old_falcon not in t:
        # maybe without && !syncFromDisk
        old_falcon2 = """  const previousFalconDb = falconChannelsDb;
  const nextFalconDb = {};
  if (falconEnabled) {"""
        if old_falcon2 in t:
            t = t.replace(old_falcon2, new_falcon.replace("else if (falconEnabled)", "else if (falconEnabled)"), 1)
            print("falcon patched v2")
        else:
            raise SystemExit("FAIL falcon")
    else:
        t = t.replace(old_falcon, new_falcon, 1)
        print("falcon patched")

old_v2 = """  const previousV2Db = v2ChannelsDb;
  const nextV2Db = {};
  if (v2Enabled && !syncFromDisk) {"""
new_v2 = """  const previousV2Db = v2ChannelsDb;
  const nextV2Db = {};
  if (syncFromDisk) {
    v2ChannelsDb = v2Enabled ? (loadJSON(V2_CACHE_FILE, previousV2Db) || previousV2Db) : {};
  } else if (v2Enabled) {"""
if "v2ChannelsDb = v2Enabled ? (loadJSON(V2_CACHE_FILE" not in t:
    if old_v2 not in t:
        old_v22 = """  const previousV2Db = v2ChannelsDb;
  const nextV2Db = {};
  if (v2Enabled) {"""
        if old_v22 in t:
            t = t.replace(old_v22, new_v2, 1)
            print("v2 patched alt")
        else:
            raise SystemExit("FAIL v2")
    else:
        t = t.replace(old_v2, new_v2, 1)
        print("v2 patched")

# Persist after filter merge
persist_anchor = """  falconChannelsDb = { ...filteredFalconTvDb, ...filteredFalconSportDb };
  v2ChannelsDb = { ...filteredV2TvDb, ...filteredV2SportDb };"""
persist_new = """  falconChannelsDb = { ...filteredFalconTvDb, ...filteredFalconSportDb };
  v2ChannelsDb = { ...filteredV2TvDb, ...filteredV2SportDb };
  if (!syncFromDisk) {
    try { saveJSON(FALCON_CACHE_FILE, falconChannelsDb); } catch {}
    try { saveJSON(V2_CACHE_FILE, v2ChannelsDb); } catch {}
  }"""
if "saveJSON(FALCON_CACHE_FILE" not in t:
    if persist_anchor not in t:
        raise SystemExit("FAIL persist anchor")
    t = t.replace(persist_anchor, persist_new, 1)
    print("persist falcon/v2")

# Critical: prevent empty nextFalconDb from wiping disk-loaded DB on syncFromDisk
old_assign_f = """  falconChannelsDb = falconEnabled
    ? (Object.keys(nextFalconDb).length ? nextFalconDb : previousFalconDb)
    : {};"""
new_assign_f = """  if (!syncFromDisk) {
    falconChannelsDb = falconEnabled
      ? (Object.keys(nextFalconDb).length ? nextFalconDb : previousFalconDb)
      : {};
  } else if (!falconEnabled) {
    falconChannelsDb = {};
  }"""
if "if (!syncFromDisk) {\n    falconChannelsDb = falconEnabled" not in t:
    if old_assign_f not in t:
        raise SystemExit("FAIL falcon assign")
    t = t.replace(old_assign_f, new_assign_f, 1)
    print("falcon assign guarded")

old_assign_v = """  v2ChannelsDb = v2Enabled
    ? (Object.keys(nextV2Db).length ? nextV2Db : previousV2Db)
    : {};"""
new_assign_v = """  if (!syncFromDisk) {
    v2ChannelsDb = v2Enabled
      ? (Object.keys(nextV2Db).length ? nextV2Db : previousV2Db)
      : {};
  } else if (!v2Enabled) {
    v2ChannelsDb = {};
  }"""
if "if (!syncFromDisk) {\n    v2ChannelsDb = v2Enabled" not in t:
    if old_assign_v not in t:
        raise SystemExit("FAIL v2 assign")
    t = t.replace(old_assign_v, new_assign_v, 1)
    print("v2 assign guarded")

# Alb cache: after albChannelsDb = applyDeletedSourcesToDb(nextAlbDb);
old_alb = """  albChannelsDb = applyDeletedSourcesToDb(nextAlbDb);

  const filteredApiTvDb = applyDeletedSourcesToDb(apiTvDb);"""
new_alb = """  albChannelsDb = applyDeletedSourcesToDb(nextAlbDb);
  if (syncFromDisk) {
    const diskAlb = loadJSON(ALB_CACHE_FILE, null);
    if (diskAlb && typeof diskAlb === 'object' && Object.keys(diskAlb).length) {
      albChannelsDb = applyDeletedSourcesToDb(diskAlb);
    }
  } else {
    try { saveJSON(ALB_CACHE_FILE, albChannelsDb); } catch {}
  }

  const filteredApiTvDb = applyDeletedSourcesToDb(apiTvDb);"""
if "ALB_CACHE_FILE, albChannelsDb" not in t:
    if old_alb not in t:
        raise SystemExit("FAIL alb")
    t = t.replace(old_alb, new_alb, 1)
    print("alb cache")

SERVER.write_text(t, encoding="utf-8")
r = subprocess.run(["node", "--check", str(SERVER)], capture_output=True, text=True)
print("check", r.returncode, (r.stderr or "OK")[:500])
if r.returncode != 0:
    raise SystemExit(1)
print("DONE")
