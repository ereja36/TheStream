# Lokke direct CDN redirect

Lokke (`kool_lokke`) playback must **302-redirect to the resolved CDN m3u8** (e.g. `*.ngolpdkyoctjcddxshli469r.org/.../hls/index.m3u8`). Traffic must **not** pass through VPS `/k1/channel-proxy` + `/k1/proxy/seg`.

## Apply on VPS

```bash
python3 /root/all/all2/scripts/patch-lokke-direct-redirect.py
# or from this repo:
python3 deploy/all2-web/scripts/patch-lokke-direct-redirect.py
node --check /root/all/all2/server.js
pm2 reload all2 --update-env
```

## What it changes

1. Web player always opens `/k1/kool-lokke/redirect/:id` for Lokke (client IP → resolve → 302 to CDN).
2. `/k1/channel/:token.m3u8` and legacy `/k1/channel-proxy/:token.m3u8` for Lokke both **302 to CDN** (no `proxyManifest`).
3. App-measurement Lokke route also 302s.
4. `chooseInitialPlaybackMode('kool_lokke')` always `direct`; config `default_playback_mode=direct`.
5. API `proxy_url` cleared for Lokke so clients have no proxy fallback.

## Verify

```bash
curl -sI 'http://127.0.0.1:3000/k1/kool-lokke/redirect/347259658949e36ffc7b26?nojs=1&ip=8.8.8.8' -H 'Host: shifshqip.com'
# Expect: 302 Location: https://....ngolpdkyoctjcddxshli469r.org/.../hls/index.m3u8 (or sunshine CDN host)
```
