# all2 admin curation (categories / channels)

Live target: `/root/all/all2` (`pm2` app `all2`).

## Rule

**Admin panel is the only source of truth.** After edits or `pm2 reload` / restart, `/k1` must show only:

- categories with **Shfaqet në Main** (`showOnMain !== false`) **and** at least 1 channel
- featured channels curated in admin (deduped)

The code must **not** invent Triangolo rows (Kombetare / Sport / Filma / …) or auto-fill Albania Lokke into categories.

## Apply

```bash
python3 deploy/all2-web/scripts/patch-all2-admin-curation.py /root/all/all2/server.js
# also deploy view:
cp deploy/all2-web/public/mainPage.ejs /root/all/all2/views/public/mainPage.ejs
node --check /root/all/all2/server.js
pm2 reload all2 --update-env
```

## Verify

With admin: only Sport showOnMain but 0 channels, 2 featured SUPER SPORT 2 dupes:

- home shows **1** channel card (deduped), **no** empty Sport card, **no** auto Kombetare/Filma/Lajme
- Triangolo theme: only Live/featured rows, never auto buckets
- `/category/custom/kombetare` → 404 when Fshehur nga Main
- hidden channel → `not_on_main_portal`
