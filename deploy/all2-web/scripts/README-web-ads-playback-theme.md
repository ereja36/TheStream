# Web ads / playback / theme fixes

Apply on VPS (`/root/all/all2`):

```bash
python3 patch-web-ads-playback-theme.py
python3 patch-web-native-ads-cleanup.py
python3 patch-a3-vod-mp4.py
pm2 restart all2
```

## What it does
1. **Ads**: disables gate/popunder/banner HTML networks (adskeeper); keeps **native** grid + smartlinks
2. **Prod API direct/redirect**: header-required sources use header-proxy so HLS.js / private playlists play (UA / Install-Token)
3. **A3 VOD**: progressive `.mp4` (rewrites `.mp4/index.m3u8` → `.mp4`), no a3hls remux
4. **Select all/none**: scoped to current source filter
5. **Player status**: admin text (Features → Web UI) instead of buffering/HLS.js lines
6. **No-ads**: clears ads when account is NoAds (incl. reward free window)
7. **Theme**: classic vs Triangolo-style, switchable from Admin → Features

## CDN segments + Triangolo polish
```bash
python3 patch-web-cdn-triangolo-telegram.py
python3 patch-web-category-bottom-ads.py
python3 fix-bottomad-commas.py   # if SyntaxError after bottomAdHtml patch
python3 patch-web-triangolo-polish.py
pm2 restart all2
```
- **Prod API traffic**: playlist manifest stays on VPS (`X-Playlist-Proxy: api-headers`); `.ts` segments are rewritten to absolute CDN URLs (`X-Segment-Mode: direct-cdn`) so media does **not** consume VPS bandwidth.
- **Triangolo**: red/dark homepage like AlbKanale; category horizontal rows; native/banner slot under each category; Telegram CTA button → `https://t.me/albview`.

## Hotfixes (channel click + features 500)
```bash
python3 patch-web-click-features-fix.py
python3 patch-web-mp4-regex-escape.py
pm2 reload all2
```
- Features 500: `escapeAttr` → `escapeHtmlAttr`
- Channels not clickable: client JS called server-only `normalizeWebUiConfig`; mp4 regex `\?` was eaten inside template literal


## Main portal gate + liquid glass
```bash
python3 patch-web-main-portal-glass.py
pm2 restart all2
```
- Web `/api/channel-url` + watch links only allow channels on main portal (featured or categories with `showOnMain`).
- Private playlists (`/playlist/:id/...`) are unchanged.
- Homepage: no marketing hero; liquid-glass Triangolo UI.


## Categories 500 + presence (cluster) + master ping
```bash
scp services/presenceStore.js root@vps:/root/all/all2/services/
python3 patch-web-presence-categories.py
pm2 restart all2
```
- Fixes category pages (`badgeClass` missing → 500).
- Live viewers shared via Redis across PM2 workers (same `started` duration).
- Stops treating nginx `127.0.0.1` as client IPv4 (IPv6-only users were shown as Local).
- Admin → Features: **Views / presence ping (MASTER)** kills `/api/ping` + viewers polling.
