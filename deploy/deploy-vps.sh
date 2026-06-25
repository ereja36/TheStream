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

scp "${ROOT_DIR}/admin_panel/the_stream/playlist-guard.php" \
  "${VPS_USER}@${VPS_HOST}:${REMOTE_PANEL}/playlist-guard.php"

if [[ -f "${ROOT_DIR}/database/migrations/007_playlist_guard_stream_pattern.sql" ]]; then
  scp "${ROOT_DIR}/database/migrations/007_playlist_guard_stream_pattern.sql" \
    "${VPS_USER}@${VPS_HOST}:/tmp/007_playlist_guard_stream_pattern.sql"
fi

ssh "${VPS_USER}@${VPS_HOST}" bash -s <<EOF
set -e
if [[ -f /tmp/007_playlist_guard_stream_pattern.sql ]]; then
  mysql -u ${DB_USER} ${DB_PASS:+-p"${DB_PASS}"} ${DB_NAME} < /tmp/007_playlist_guard_stream_pattern.sql || true
  rm -f /tmp/007_playlist_guard_stream_pattern.sql
fi
chown www-data:www-data ${REMOTE_PANEL}/api/api.php ${REMOTE_PANEL}/playlist-guard.php
chmod 644 ${REMOTE_PANEL}/api/api.php ${REMOTE_PANEL}/playlist-guard.php
EOF

if [[ -f "${ROOT_DIR}/android/app/build/outputs/apk/release/app-release.apk" ]]; then
  echo "==> Deploy release APK"
  scp "${ROOT_DIR}/android/app/build/outputs/apk/release/app-release.apk" \
    "${VPS_USER}@${VPS_HOST}:${REMOTE_APK_DIR}/shif-shqip-release.apk"
  ssh "${VPS_USER}@${VPS_HOST}" bash -s <<EOF
set -e
cp -f ${REMOTE_APK_DIR}/shif-shqip-release.apk ${REMOTE_APK_DIR}/shif-shqip.apk
chown www-data:www-data ${REMOTE_APK_DIR}/shif-shqip-release.apk ${REMOTE_APK_DIR}/shif-shqip.apk
chmod 644 ${REMOTE_APK_DIR}/shif-shqip-release.apk ${REMOTE_APK_DIR}/shif-shqip.apk
EOF
elif [[ -f "${ROOT_DIR}/android/app/build/outputs/apk/debug/app-debug.apk" ]]; then
  echo "==> Deploy debug APK (release-signed when keystore present)"
  scp "${ROOT_DIR}/android/app/build/outputs/apk/debug/app-debug.apk" \
    "${VPS_USER}@${VPS_HOST}:${REMOTE_APK_DIR}/shif-shqip-release.apk"
  ssh "${VPS_USER}@${VPS_HOST}" bash -s <<EOF
set -e
cp -f ${REMOTE_APK_DIR}/shif-shqip-release.apk ${REMOTE_APK_DIR}/shif-shqip.apk
chown www-data:www-data ${REMOTE_APK_DIR}/shif-shqip-release.apk ${REMOTE_APK_DIR}/shif-shqip.apk
chmod 644 ${REMOTE_APK_DIR}/shif-shqip-release.apk ${REMOTE_APK_DIR}/shif-shqip.apk
EOF
fi

echo "==> Verify API"
REST_API_KEY="${REST_API_KEY:-shifshqip}"
curl -fsS "https://api.shifshqip.com/api/api.php?get_playlist_guard&api_key=${REST_API_KEY}" | head -c 400
echo ""
echo "Deploy finished."
