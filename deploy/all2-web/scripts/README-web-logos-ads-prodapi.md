# Web: logos glass + full names + ads + Prod API guard

Patch: `scripts/patch-web-logos-names-ads-prodapi.py`

Applied on VPS (`/root/all/all2`) for:

1. **Prod API refresh** — async (no CF 504), never replace a large TV list with a tiny partial (~41), restore last good cache when wiped.
2. **Logos** — remove red text-badge; liquid-glass thumb + real `/k1/tv-logos` logo + white name.
3. **Names** — full white channel titles (wrap, no ellipsis clamp).
4. **Ads** — enable main banner; give Adsterra time to fill before empty-slot scrub.

## Deploy

```bash
python3 /tmp/patch-web-logos-names-ads-prodapi.py
# or from repo after scp:
python3 deploy/all2-web/scripts/patch-web-logos-names-ads-prodapi.py
```

## Note

If Prod API returns HTTP 403 Unauthorized, refresh cannot pull new channels until Install-Token is valid again. The guard keeps the restored list instead of wiping to ~41.
