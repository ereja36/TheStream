# App stall / playback fixes

## Root causes addressed
1. Portal resolve returned CDN URL but **dropped** `headers` / `user_agent` → ExoPlayer hit A3 without Referer/UA
2. `IptvStallRecoveryHelper` existed but was **never attached** → silent buffering forever
3. `/r/` sealed entries never enriched via channel-url (no headers / wrong cip)
4. `phinemi.live` a3proxy rejected as not playable
5. `playlist_analytics` cluster broadcast spam reloaded 8k channels constantly
6. `refreshBlocking` was async-only; M3U referer not parsed; hero preview competed with playback

## Deploy
### Portal (VPS)
```bash
scp playlist_portal/lib/PortalService.php playlist_portal/index.php root@vps:/var/www/shifshqip-playlist/...
```

### all2
```bash
python3 deploy/all2-web/scripts/patch-web-analytics-no-broadcast.py
```

### APK
Rebuild/release from `android/` on branch `cursor/app-stall-playback-fix-4157`.
