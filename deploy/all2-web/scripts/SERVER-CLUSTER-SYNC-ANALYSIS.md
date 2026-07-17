# Server cluster sync — hard analysis (2026-07-17)

## LokkeApp 4 vs 16 categories — root cause (FIXED)

PM2 runs **3 workers**. Lokke `Load ALL` filled one worker’s in-memory service + disk, but peers:
1. Reloaded via `syncFromDisk` that still called `syncKoolLokkeChannelsFromService()` from **local empty/stale service memory**
2. Could **persist a smaller** DB over the authoritative disk cache
3. Dropped cluster reload events while `updateDatabasesRunning`

**Result:** one refresh → `4 kategori • 2999`, another → `16 kategori • 7017`.

### Fixes shipped
- Always **seed kool/lokke from disk** before any service merge
- **Refuse persist shrink** unless `replaceAll` (Load ALL)
- `scheduleClusterReload` **retries** instead of dropping
- Kool/Lokke caches added to `CLUSTER_SYNC_FILES`
- Shared Load ALL job status via Redis (`channelStateSync`)
- Falcon/V2/Alb channel caches on disk for peer reload
- Presence (live viewers) already in Redis

### Verified
20 consecutive hits to `/k1/category/source/kool_lokke` → all `16 kategori • 7017 kanale`.
All workers log `KoolLokke: 7019` after sync.

---

## Remaining risks (priority)

### P0 / High
1. **Dual presence** — Redis `presenceStore` + local `activeUsers` Map still both used. Some admin/fingerprint paths may still be worker-local. Prefer single facade over Redis.
2. **Quota / abuse state per worker** — `fingerprintState`, `smart_link_state`, ad IP quotas, `channelViews`, `serverStats` are process-local → bans/quotas can differ up to ~3×.

### P1 / Medium
3. Load ALL **progress** still local (status is Redis; progress bar can look stuck if polled on another worker). Add Redis lock `SET NX` around Load ALL.
4. `getKool*CategorySummaries` mutates `appConfig` silently (broadcast suppressed) → visibility defaults may lag on peers until next config sync.
5. High PM2 restart count from iterative deploys — monitor heap after warm-up.

### P2 / Suggestions
6. Admin endpoint `/api/admin/cluster-channel-state` — hit through LB repeatedly; all PIDs should report same counts/`version`.
7. Master switch already exists: **Features → Views / presence ping (MASTER)** to cut ping load.
8. Consider sticky sessions only as temporary mitigation; disk+Redis sync is the correct model.

---

## Monitoring checklist
- 10–20× refresh Lokke page: one unique count string
- After Load ALL: peers converge within a few seconds; watch for `[lokke] refuse persist shrink`
- Redis keys: `k1iptv:channel_state:version`, `k1iptv:presence:*`, `k1iptv:job:*`
- `pm2 logs` for `[cluster-sync] kool caches from disk`
