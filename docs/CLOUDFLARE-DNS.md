# api.shifshqip.com — Konfigurim DNS (Cloudflare)

## Pse nuk punon ende?

`api.shifshqip.com` **nuk ekziston në DNS** (NXDOMAIN). Nginx në VPS është gati — mungon vetëm regjistrimi DNS.

Domaini `shifshqip.com` përdor **Cloudflare** (IP-të 104.21.x / 172.67.x). Prandaj DNS **duhet shtuar në Cloudflare**, jo vetëm në VPS.

## Hapat në Cloudflare (2 minuta)

1. Hyr në https://dash.cloudflare.com
2. Zgjidh domainin **shifshqip.com**
3. Shko te **DNS** → **Records**
4. Kliko **Add record**:
   | Field | Vlera |
   |-------|-------|
   | Type | **A** |
   | Name | **api** |
   | IPv4 address | **57.128.254.213** |
   | Proxy status | **Proxied** (rekomandohet — SSL automatik) |
   | TTL | Auto |
5. Ruaj (**Save**)

## Pas 2–10 minutash

Testo:
```bash
curl -I https://api.shifshqip.com/
curl https://api.shifshqip.com/api/api.php?get_playlist_guard&api_key=YOUR_KEY
```

Panel: https://api.shifshqip.com/  
Privacy: https://api.shifshqip.com/privacy.php  
APK: https://api.shifshqip.com/shif-shqip.apk (ose `/shif-shqip-debug.apk`)

**Paneli dhe APK nuk hapen me IP** — `57.128.254.213:8082` dhe `57.128.254.213/shif-shqip-*.apk` janë bllokuar. Vetëm subdomain.

## VPS (tashmë bërë)

- Nginx: `/etc/nginx/sites-available/api.shifshqip.com`
- Root: `/var/www/shifshqip-api/the_stream`
- Port **80** — Cloudflare Proxy e merr HTTPS para përdoruesit

## Nëse përdor DNS only (grey cloud)

Aktivizo SSL në VPS:
```bash
certbot --nginx -d api.shifshqip.com
```

## APK SERVER_KEY

App-i përdor: `https://api.shifshqip.com` — funksionon vetëm pasi DNS të jetë aktiv.
