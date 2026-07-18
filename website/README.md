# Shif Shqip public website (shifshqip.com)
#
# Deploy:
#   scp -r website/shifshqip-com/* root@VPS:/var/www/html/
#   # backup ESPN first: mv index.php index.espn.bak.php
#   scp playlist_portal/lib/PortalService.php root@VPS:/var/www/shifshqip-playlist/lib/
#   scp website/k1-legal/layout.ejs root@VPS:/root/all/all2/views/public/layout.ejs
#   pm2 reload all2
#
# DDoS notes (ops, not code):
# - Keep Cloudflare proxy ON; enable WAF + Bot Fight / Under Attack when needed
# - Cache HTML (s-maxage on these pages); never put heavy scrapers on /
# - Rate-limit /k1/proxy/* at nginx; keep legal pages on origin disk (static PHP)
# - Create mailbox legal@shifshqip.com (forward to ops)

Suggested extras:
- Google Search Console + Bing webmaster with sitemap.xml
- app-ads.txt already on domain — keep in sync with AdMob
- Play Store listing when ready (helps AdMob fill)
- Cookie / consent banner alignment with GDPR if EU traffic grows
- Separate status page (status.shifshqip.com) for outages
