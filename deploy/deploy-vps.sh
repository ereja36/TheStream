#!/usr/bin/env bash
set -euo pipefail

VPS_HOST="${VPS_HOST:-57.128.254.213}"
VPS_USER="${VPS_USER:-root}"
REMOTE_PANEL="/var/www/shifshqip-api/the_stream"
REMOTE_APK_DIR="/var/www/shifshqip-private/apk"
DB_USER="${DB_USER:-shif_user}"
DB_NAME="${DB_NAME:-shif_shqip_db}"
DB_PASS="${DB_PASS:-}"

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"

echo "==> Deploy panel/API to ${VPS_USER}@${VPS_HOST}"

scp "${ROOT_DIR}/admin_panel/the_stream/api/api.php" \
  "${VPS_USER}@${VPS_HOST}:${REMOTE_PANEL}/api/api.php"

for panel_file in \
  playlist-guard.php \
  global-playlist.php \
  app-ads.txt \
  download.html \
  dashboard.php \
  settings.php \
  privacy.php; do
  if [[ -f "${ROOT_DIR}/admin_panel/the_stream/${panel_file}" ]]; then
    scp "${ROOT_DIR}/admin_panel/the_stream/${panel_file}" \
      "${VPS_USER}@${VPS_HOST}:${REMOTE_PANEL}/${panel_file}"
  fi
done

scp "${ROOT_DIR}/admin_panel/the_stream/includes/sidebar.php" \
  "${VPS_USER}@${VPS_HOST}:${REMOTE_PANEL}/includes/sidebar.php"
scp "${ROOT_DIR}/admin_panel/the_stream/includes/strings.php" \
  "${VPS_USER}@${VPS_HOST}:${REMOTE_PANEL}/includes/strings.php"

if [[ -f "${ROOT_DIR}/deploy/nginx-api.shifshqip.com.conf" ]]; then
  scp "${ROOT_DIR}/deploy/nginx-api.shifshqip.com.conf" \
    "${VPS_USER}@${VPS_HOST}:/etc/nginx/sites-available/api.shifshqip.com"
fi

if [[ -f "${ROOT_DIR}/database/migrations/010_global_playlist.sql" ]]; then
  scp "${ROOT_DIR}/database/migrations/010_global_playlist.sql" \
    "${VPS_USER}@${VPS_HOST}:/tmp/010_global_playlist.sql"
fi

if [[ -f "${ROOT_DIR}/database/migrations/009_play_store_panel_features.sql" ]]; then
  scp "${ROOT_DIR}/database/migrations/009_play_store_panel_features.sql" \
    "${VPS_USER}@${VPS_HOST}:/tmp/009_play_store_panel_features.sql"
fi

if [[ -f "${ROOT_DIR}/database/migrations/008_startup_support_popup.sql" ]]; then
  scp "${ROOT_DIR}/database/migrations/008_startup_support_popup.sql" \
    "${VPS_USER}@${VPS_HOST}:/tmp/008_startup_support_popup.sql"
fi

if [[ -f "${ROOT_DIR}/database/migrations/007_playlist_guard_stream_pattern.sql" ]]; then
  scp "${ROOT_DIR}/database/migrations/007_playlist_guard_stream_pattern.sql" \
    "${VPS_USER}@${VPS_HOST}:/tmp/007_playlist_guard_stream_pattern.sql"
fi

ssh "${VPS_USER}@${VPS_HOST}" bash -s <<EOF
set -e
if [[ -f /tmp/010_global_playlist.sql ]]; then
  mysql -u ${DB_USER} ${DB_PASS:+-p"${DB_PASS}"} ${DB_NAME} < /tmp/010_global_playlist.sql || true
  rm -f /tmp/010_global_playlist.sql
fi
if [[ -f /tmp/009_play_store_panel_features.sql ]]; then
  mysql -u ${DB_USER} ${DB_PASS:+-p"${DB_PASS}"} ${DB_NAME} < /tmp/009_play_store_panel_features.sql || true
  rm -f /tmp/009_play_store_panel_features.sql
fi
if [[ -f /tmp/008_startup_support_popup.sql ]]; then
  mysql -u ${DB_USER} ${DB_PASS:+-p"${DB_PASS}"} ${DB_NAME} < /tmp/008_startup_support_popup.sql || true
  rm -f /tmp/008_startup_support_popup.sql
fi
if [[ -f /tmp/007_playlist_guard_stream_pattern.sql ]]; then
  mysql -u ${DB_USER} ${DB_PASS:+-p"${DB_PASS}"} ${DB_NAME} < /tmp/007_playlist_guard_stream_pattern.sql || true
  rm -f /tmp/007_playlist_guard_stream_pattern.sql
fi
chown www-data:www-data ${REMOTE_PANEL}/api/api.php ${REMOTE_PANEL}/playlist-guard.php ${REMOTE_PANEL}/global-playlist.php ${REMOTE_PANEL}/app-ads.txt ${REMOTE_PANEL}/download.html ${REMOTE_PANEL}/dashboard.php ${REMOTE_PANEL}/settings.php ${REMOTE_PANEL}/privacy.php ${REMOTE_PANEL}/includes/sidebar.php ${REMOTE_PANEL}/includes/strings.php
chmod 644 ${REMOTE_PANEL}/api/api.php ${REMOTE_PANEL}/playlist-guard.php ${REMOTE_PANEL}/global-playlist.php ${REMOTE_PANEL}/app-ads.txt ${REMOTE_PANEL}/download.html ${REMOTE_PANEL}/dashboard.php ${REMOTE_PANEL}/settings.php ${REMOTE_PANEL}/privacy.php ${REMOTE_PANEL}/includes/sidebar.php ${REMOTE_PANEL}/includes/strings.php
if [[ -f /etc/nginx/sites-available/api.shifshqip.com ]]; then
  nginx -t && systemctl reload nginx
fi
EOF

if [[ -f "${ROOT_DIR}/android/app/build/outputs/apk/release/app-release.apk" ]]; then
  echo "==> Deploy release APK"
  scp "${ROOT_DIR}/android/app/build/outputs/apk/release/app-release.apk" \
    "${VPS_USER}@${VPS_HOST}:${REMOTE_APK_DIR}/shif-shqip-release.apk"
  ssh "${VPS_USER}@${VPS_HOST}" bash -s <<EOF
set -e
cp -f ${REMOTE_APK_DIR}/shif-shqip-release.apk ${REMOTE_APK_DIR}/shif-shqip.apk
cp -f ${REMOTE_APK_DIR}/shif-shqip-release.apk ${REMOTE_APK_DIR}/shif-shqip-latest.apk
chown www-data:www-data ${REMOTE_APK_DIR}/shif-shqip-release.apk ${REMOTE_APK_DIR}/shif-shqip.apk ${REMOTE_APK_DIR}/shif-shqip-latest.apk
chmod 644 ${REMOTE_APK_DIR}/shif-shqip-release.apk ${REMOTE_APK_DIR}/shif-shqip.apk ${REMOTE_APK_DIR}/shif-shqip-latest.apk
EOF
elif [[ -f "${ROOT_DIR}/android/app/build/outputs/apk/debug/app-debug.apk" ]]; then
  echo "==> Deploy debug APK (release-signed when keystore present)"
  scp "${ROOT_DIR}/android/app/build/outputs/apk/debug/app-debug.apk" \
    "${VPS_USER}@${VPS_HOST}:${REMOTE_APK_DIR}/shif-shqip-release.apk"
  ssh "${VPS_USER}@${VPS_HOST}" bash -s <<EOF
set -e
cp -f ${REMOTE_APK_DIR}/shif-shqip-release.apk ${REMOTE_APK_DIR}/shif-shqip.apk
cp -f ${REMOTE_APK_DIR}/shif-shqip-release.apk ${REMOTE_APK_DIR}/shif-shqip-latest.apk
chown www-data:www-data ${REMOTE_APK_DIR}/shif-shqip-release.apk ${REMOTE_APK_DIR}/shif-shqip.apk ${REMOTE_APK_DIR}/shif-shqip-latest.apk
chmod 644 ${REMOTE_APK_DIR}/shif-shqip-release.apk ${REMOTE_APK_DIR}/shif-shqip.apk ${REMOTE_APK_DIR}/shif-shqip-latest.apk
EOF
fi

if [[ -f "${ROOT_DIR}/android/app/build/outputs/bundle/release/app-release.aab" ]]; then
  echo "==> Deploy release AAB"
  scp "${ROOT_DIR}/android/app/build/outputs/bundle/release/app-release.aab" \
    "${VPS_USER}@${VPS_HOST}:${REMOTE_APK_DIR}/shif-shqip-latest.aab"
  ssh "${VPS_USER}@${VPS_HOST}" bash -s <<EOF
set -e
chown www-data:www-data ${REMOTE_APK_DIR}/shif-shqip-latest.aab
chmod 644 ${REMOTE_APK_DIR}/shif-shqip-latest.aab
EOF
fi

echo "==> Verify API"
REST_API_KEY="${REST_API_KEY:-shifshqip}"
curl -fsS "https://api.shifshqip.com/api/api.php?get_playlist_guard&api_key=${REST_API_KEY}" | head -c 400
echo ""
echo "Deploy finished."
