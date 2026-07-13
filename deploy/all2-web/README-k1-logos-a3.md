# k1 logos / A3 / Lokke / Prod API cleanup

Deployed on VPS `/root/all/all2` (pm2 `all2`). Android app untouched.

## Changes
- Static TV logos from `tv-logos-ereja` served at `/k1/tv-logos/` (nginx alias to `/var/www/shifshqip-k1-static/tv-logos/`)
- `services/tvLogoService.js` matches channel names → local logos (incl. VOD when names match)
- A3 dead gcore `external/` + `vavoo/` gates overridden in `albService.js` (Vizion Plus, News 24, RTSH 1–3, RTK, Arena, Tring, …)
- Lokke names: `Channel [Lokke,AL|DE|FR|UK|IT|…]`
- Prod API: removed MetiTV* + Mahmutovitët/Rexhepovitët; blocked on fetch/load

## Deploy
```bash
bash /tmp/k1-deploy/scripts/deploy-k1-logos-a3.sh
# keep /var/www/shifshqip-k1-static/tv-logos in sync with public/tv-logos
```
