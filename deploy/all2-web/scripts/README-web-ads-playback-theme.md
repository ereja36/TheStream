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
