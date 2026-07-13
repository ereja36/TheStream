#!/usr/bin/env bash
# Rollback VPS app state and apply the PM2 cluster fix (fork x1).
# Root cause of "save works but refresh shows old data":
#   PM2 cluster x2 → two Node processes with separate in-memory appConfig.
#
# Usage (on VPS as root):
#   bash /root/all/all2/scripts/vps-rollback-pm2-fix.sh
#
# Optional env:
#   APP_DIR=/root/all/all2
#   BACKUP_DIR=/root/backups/admin-save-fix-20260709-164223

set -euo pipefail

APP_DIR="${APP_DIR:-/root/all/all2}"
PM2_NAME="${PM2_NAME:-all2}"
BACKUP_DIR="${BACKUP_DIR:-}"

echo "==> App dir: ${APP_DIR}"
cd "${APP_DIR}"

if [[ -n "${BACKUP_DIR}" && -d "${BACKUP_DIR}" ]]; then
  echo "==> Restoring files from backup: ${BACKUP_DIR}"
  for f in server.js channel_config.json; do
    if [[ -f "${BACKUP_DIR}/${f}" ]]; then
      cp -a "${BACKUP_DIR}/${f}" "${APP_DIR}/${f}.rollback-$(date +%Y%m%d-%H%M%S).bak"
      cp -a "${BACKUP_DIR}/${f}" "${APP_DIR}/${f}"
      echo "    restored ${f}"
    fi
  done
else
  echo "==> No BACKUP_DIR set or missing; skipping file restore"
  echo "    Set BACKUP_DIR=/root/backups/admin-save-fix-20260709-164223 to restore"
fi

echo "==> Killing stuck Python patch scripts (100% CPU)"
pkill -f 'patch_phinemi_nginx.py' 2>/dev/null || true
pkill -f 'patch_.*nginx.*\.py' 2>/dev/null || true
sleep 1
pgrep -af 'patch_.*nginx.*\.py' || echo "    no stuck nginx patch scripts"

echo "==> Stopping PM2 app ${PM2_NAME}"
pm2 delete "${PM2_NAME}" 2>/dev/null || true

echo "==> Starting ${PM2_NAME} as single fork instance (NOT cluster)"
cd "${APP_DIR}"
pm2 start server.js --name "${PM2_NAME}" --time
pm2 save

echo "==> PM2 status"
pm2 describe "${PM2_NAME}" | rg -i 'exec mode|instances|script path|status' || pm2 describe "${PM2_NAME}"

echo "==> Done. Verify:"
echo "    pm2 list"
echo "    curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:3000/k1/"
echo "    Admin save → refresh should now show the same values (single process)."
