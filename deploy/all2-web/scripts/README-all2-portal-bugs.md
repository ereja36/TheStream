# all2 portal bugs (channels / ads / categories)

Live target: `/root/all/all2` on the VPS (`pm2` app `all2`).

## What broke

- Homepage / category UI showed Albania channels (Triangolo auto-rows or category cards), but `/k1/api/channel-url/:slug` returned `not_on_main_portal` because almost all custom categories had `showOnMain: false` while the play gate only allowed featured + `showOnMain` slugs.
- Client treated that as **“Nuk ka burim për këtë kanal”**.
- Premium / noads users still saw empty **Sponsored** native-ad chrome (body hidden, card header left).
- `Kombetare` was curated to ~72 slugs while Lokke Albania has hundreds (e.g. Zjarr missing from the category grid).

## Apply

```bash
python3 deploy/all2-web/scripts/patch-all2-portal-bugs.py /root/all/all2/server.js /root/all/all2/data/custom_categories.json
node --check /root/all/all2/server.js
pm2 reload all2 --update-env
```

## Verify

```bash
curl -sS -H 'Referer: https://shifshqip.com/k1/' -H 'Origin: https://shifshqip.com' \
  'http://127.0.0.1:3000/k1/api/channel-url/koollokke_kool-albania_2198562168b4a998b894e5'
# expect ok:true + url

curl -sS 'http://127.0.0.1:3000/k1/category/custom/kombetare' | grep -i zjarr
```
