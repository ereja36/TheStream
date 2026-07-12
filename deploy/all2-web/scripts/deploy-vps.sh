#!/usr/bin/env bash
set -euo pipefail

# Deploy IPTV app to VPS at /root/all/all2 with Redis + PM2 cluster.
# Usage:
#   VPS_HOST=57.128.254.213 VPS_PASS='your-password' ./scripts/deploy-vps.sh
# Or with SSH key auth (no VPS_PASS needed):
#   VPS_HOST=57.128.254.213 ./scripts/deploy-vps.sh

VPS_HOST="${VPS_HOST:-57.128.254.213}"
VPS_USER="${VPS_USER:-root}"
VPS_DIR="${VPS_DIR:-/root/all/all2}"
BRANCH="${DEPLOY_BRANCH:-cursor/iptv-fixes-performance-e424}"
PM2_APP="${PM2_APP_NAME:-all2}"
PM2_INSTANCES="${PM2_INSTANCES:-2}"

SSH_OPTS=(-o StrictHostKeyChecking=no)
if [[ -n "${VPS_PASS:-}" ]]; then
  if ! command -v sshpass >/dev/null; then
    echo "sshpass required when VPS_PASS is set" >&2
    exit 1
  fi
  SSH=(sshpass -p "$VPS_PASS" ssh "${SSH_OPTS[@]}")
else
  SSH=(ssh "${SSH_OPTS[@]}")
fi

REMOTE="${VPS_USER}@${VPS_HOST}"

echo "==> Connecting to ${REMOTE}:${VPS_DIR}"

"${SSH[@]}" "$REMOTE" bash -s <<EOF
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive

if ! command -v redis-cli >/dev/null 2>&1; then
  echo "Installing Redis..."
  apt-get update -qq
  apt-get install -y redis-server
  sed -i 's/^supervised no/supervised systemd/' /etc/redis/redis.conf 2>/dev/null || true
  systemctl enable redis-server
  systemctl restart redis-server
fi

redis-cli ping || { echo "Redis failed to start"; exit 1; }

if ! command -v node >/dev/null 2>&1 || [[ "$(node -v 2>/dev/null || echo v0)" != v22* ]]; then
  echo "Upgrading to Node.js 22..."
  curl -fsSL https://deb.nodesource.com/setup_22.x | bash -
  apt-get install -y nodejs
fi
node -v
npm -v

cd "${VPS_DIR}"
git fetch origin "${BRANCH}" || git fetch origin
git checkout "${BRANCH}" 2>/dev/null || git checkout -b "${BRANCH}" "origin/${BRANCH}"
git pull origin "${BRANCH}"

npm install --omit=dev

export REDIS_URL="redis://127.0.0.1:6379/5"
export REDIS_KEY_PREFIX="k1iptv:"
export PM2_APP_NAME="${PM2_APP}"
export PM2_INSTANCES="${PM2_INSTANCES}"
export KOOL_CATALOG_CONCURRENCY="6"
export KOOL_CATALOG_MAX_PAGES="20"

pm2 delete "${PM2_APP}" 2>/dev/null || true
pm2 start ecosystem.config.cjs
pm2 save

sleep 3
pm2 list
pm2 logs "${PM2_APP}" --lines 20 --nostream || true
EOF

echo "==> Deploy complete. Verify:"
echo "    curl -s http://127.0.0.1:3000/k1/api/ping -X POST"
echo "    pm2 logs ${PM2_APP} | grep runtime"
