# Admin cleanup (2026-07-12)

## Backup
VPS backup: `/root/backups/all2-backup-20260712-224326.tar.gz`

## Removed from admin / public UI
- Admin: Orar Aktivizimi për Source, Health Score per Source, Android AdMob (Wrapper)
- Features: Watch Party, Live chat, Reactions, EPG Grid, EPG in player, Channel hover preview, Deep links/Share, Chromecast/AirPlay, Android TV remote UI, PWA Install, Web Push
- Roll Ads: Forced Ads per IP/Day (+ seksionet e hequra)
- Nav/routes: VOD, Push Notifications, Chat Live
- Source: Falcon (+ alb_vod) forced hidden/deleted everywhere in UI

## Added / changed
- **Kategori**: ↑↓ custom order (`order` field + `move_order`)
- **Users Aktiv**: grouped by channel (click channel for viewer details); idle users not mixed in

## Feature flags
Removed UX flags are force-disabled in `normalizeFeatureFlags` (cannot be re-enabled via UI).
