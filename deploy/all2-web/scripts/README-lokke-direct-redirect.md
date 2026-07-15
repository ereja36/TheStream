# Lokke direct CDN redirect + HLS.js

Lokke (`kool_lokke`) traffic must hit the resolved CDN m3u8 (e.g. `*.ngolpdkyoctjcddxshli469r.org/.../hls/index.m3u8`). No VPS `/k1/channel-proxy` + `/k1/proxy/seg` segment relay.

## Player modes

- **HLS.js** (site default): in-page player; `/api/channel-url` returns the resolved CDN URL; HLS.js plays it (browser ↔ CDN).
- **Browser Default**: full-page `/kool-lokke/redirect/:id` with status **“Po kalon në server të dyt...”** (no IP notice).

## Apply on VPS

```bash
python3 deploy/all2-web/scripts/patch-lokke-direct-redirect.py
python3 deploy/all2-web/scripts/patch-lokke-hls-player.py
python3 deploy/all2-web/scripts/patch-all-sources-direct-redirect.py
python3 deploy/all2-web/scripts/patch-direct-source-hard-redirect.py
python3 deploy/all2-web/scripts/patch-xui-direct-play-redirect.py
python3 deploy/all2-web/scripts/patch-hlsjs-live-harden.py
node --check /root/all/all2/server.js
pm2 reload all2 --update-env
```

## XUI: hide /play/, client gets /auth/

`patch-xui-direct-play-redirect.py`:

1. Playlist seals `…/play/TOKEN/m3u8` (never shown to the app).
2. On play, portal/all2 resolves `/play/` → `/auth/…` **server-side**.
3. App receives only `/auth/…` and streams `/hls/…` **directly on CDN** (no VPS segment proxy).


## HLS.js live harden

`patch-hlsjs-live-harden.py` pins **hls.js@1.6.16**, always enables the worker, raises live buffers/retries, soft-recovers media/network errors before full reload, and is more patient on stalls.

## Admin Direct / Proxy by Source

`patch-direct-source-hard-redirect.py` makes **Direct / Redirect** honor `proxy_rules` for every source (including Custom TV/Sport/Nimi):

- **Direct:** signed `/k1/channel/...` → **302 to the real CDN/source URL** (no `/channel-proxy`, no `/proxy/seg`).
- **Proxy:** keeps `/k1/channel-proxy/...` + segment relay as before.

Root cause fixed: `shouldHidePlaybackSource(custom_*)` used to force proxy even when admin set Direct.

## What it changes

1. `/channel` + legacy `/channel-proxy` for Lokke **302 to CDN** (no `proxyManifest`).
2. HLS.js path resolves CDN URL via `/api/channel-url` (with client `ipv4`).
3. Browser path uses redirect interstitial text: “Po kalon në server të dyt...”.
4. Config: `kool_lokke` playback `direct`; no Lokke `proxy_url`.

## Verify

```bash
curl -sI 'http://127.0.0.1:3000/k1/kool-lokke/redirect/347259658949e36ffc7b26?nojs=1&ip=8.8.8.8' -H 'Host: shifshqip.com'
# 302 → CDN m3u8

curl -s 'http://127.0.0.1:3000/k1/kool-lokke/redirect/347259658949e36ffc7b26' \
  -H 'Host: shifshqip.com' -H 'Accept: text/html' -H 'User-Agent: Mozilla/5.0' | grep -o 'Po kalon[^<]*'
```
