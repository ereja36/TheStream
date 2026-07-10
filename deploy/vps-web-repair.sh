#!/usr/bin/env bash
# Emergency VPS web repair: reinstall nginx, restore configs, fast-start k1 Node server.
# Keeps current server.js (Kool/Lokke merge) and only patches startup performance.
set -euo pipefail

VPS_HOST="${VPS_HOST:-57.128.254.213}"
VPS_USER="${VPS_USER:-root}"
BACKUP=/root/backups/vps-snapshot-20260704-181238
SERVER=/root/all/all2/server.js

echo "==> Install nginx if missing"
if ! command -v nginx >/dev/null 2>&1; then
  export DEBIAN_FRONTEND=noninteractive
  apt-get update -qq
  apt-get install -y -qq nginx
fi

echo "==> Restore nginx + SSL from snapshot"
tar -xzf "$BACKUP/02-nginx-full.tar.gz" -C /
tar -xzf "$BACKUP/03-letsencrypt.tar.gz" -C / 2>/dev/null || true

if ! grep -q "function rebuildAllCombinedFast()" "$SERVER"; then
  echo "==> Patch server.js for fast startup"
  cp -a "$SERVER" "${SERVER}.bak-before-faststart-$(date +%Y%m%d-%H%M%S)"
  python3 /root/vps-web-patch-server.py
fi

echo "==> Tune PHP-FPM"
sed -i 's/^pm.max_children = .*/pm.max_children = 60/' /etc/php/8.3/fpm/pool.d/www.conf
grep -q '^pm.max_requests' /etc/php/8.3/fpm/pool.d/www.conf || echo 'pm.max_requests = 500' >> /etc/php/8.3/fpm/pool.d/www.conf
systemctl restart php8.3-fpm

NGINX_CONF=/etc/nginx/nginx.conf
if [[ -f "$NGINX_CONF" ]] && ! grep -q 'gzip_types' "$NGINX_CONF"; then
  sed -i '/gzip on;/a\\tgzip_types text/plain text/css application/json application/javascript application/xml image/svg+xml;' "$NGINX_CONF"
fi

nginx -t
systemctl enable nginx
systemctl restart nginx
pm2 restart all2
