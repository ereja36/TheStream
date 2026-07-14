#!/usr/bin/env bash
# Deploy to VPS using root password (when SSH key is not configured).
# Usage: VPS_PASSWORD='your-password' ./deploy/deploy-with-password.sh
#    or: ./deploy/deploy-with-password.sh 'your-password'

set -euo pipefail

if [[ -n "${1:-}" ]]; then
  export VPS_PASSWORD="$1"
fi

if [[ -z "${VPS_PASSWORD:-}" ]]; then
  echo "Error: set VPS_PASSWORD env var or pass password as first argument."
  exit 1
fi

SSHPASS_BIN="${SSHPASS_BIN:-/tmp/sshpass-extract/usr/bin/sshpass}"
if [[ ! -x "${SSHPASS_BIN}" ]]; then
  echo "==> Downloading sshpass..."
  curl -fsSL -o /tmp/sshpass.deb http://archive.ubuntu.com/ubuntu/pool/universe/s/sshpass/sshpass_1.09-1_amd64.deb
  mkdir -p /tmp/sshpass-extract
  dpkg-deb -x /tmp/sshpass.deb /tmp/sshpass-extract
fi

export SSHPASS="${VPS_PASSWORD}"
export PATH="$(dirname "${SSHPASS_BIN}"):${PATH}"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Wrap scp/ssh for child script via PATH shim
SHIM_DIR="$(mktemp -d)"
cat > "${SHIM_DIR}/scp" <<'SHIM'
#!/usr/bin/env bash
exec sshpass -e /usr/bin/scp -o StrictHostKeyChecking=accept-new "$@"
SHIM
cat > "${SHIM_DIR}/ssh" <<'SHIM'
#!/usr/bin/env bash
exec sshpass -e /usr/bin/ssh -o StrictHostKeyChecking=accept-new "$@"
SHIM
chmod +x "${SHIM_DIR}/scp" "${SHIM_DIR}/ssh"
export PATH="${SHIM_DIR}:${PATH}"

echo "==> Fetching production keystore from VPS..."
mkdir -p "${SCRIPT_DIR}/../android/keystore"
sshpass -e scp -o StrictHostKeyChecking=accept-new \
  "${VPS_USER:-root}@${VPS_HOST:-57.128.254.213}:/var/www/shifshqip-private/shif-shqip-release.jks" \
  "${SCRIPT_DIR}/../android/keystore/shif-shqip-release.jks" 2>/dev/null || \
  echo "Warning: could not fetch keystore — building with local keystore if present."

if [[ -z "${DB_PASS:-}" ]]; then
  echo "==> Reading DB password from VPS config.php..."
  DB_PASS="$(sshpass -e ssh -o StrictHostKeyChecking=accept-new \
    "${VPS_USER:-root}@${VPS_HOST:-57.128.254.213}" \
    "php -r \"include '/var/www/shifshqip-api/the_stream/includes/config.php'; echo \\\$pass;\"" 2>/dev/null || true)"
  export DB_PASS
fi

if [[ ! -f "${SCRIPT_DIR}/../android/app/build/outputs/apk/release/app-release.apk" ]]; then
  echo "==> Building release APK..."
  (
    cd "${SCRIPT_DIR}/../android"
    export ANDROID_HOME="${ANDROID_HOME:-/home/ubuntu/android-sdk}"
    export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
    [[ -f local.properties ]] || echo "sdk.dir=${ANDROID_HOME}" > local.properties
    chmod +x gradlew
    ./gradlew assembleRelease bundleRelease --no-daemon
  )
fi

"${SCRIPT_DIR}/deploy-vps.sh"
rm -rf "${SHIM_DIR}"

echo "==> Verify new API fields"
curl -fsS "https://api.shifshqip.com/api/api.php?get_config&package_name=com.shif.shqip.android&api_key=${REST_API_KEY:-shifshqip}" \
  | python3 -c "import json,sys; s=json.load(sys.stdin).get('settings',{}); print('player_brand_text:', s.get('player_brand_text','MISSING')); print('app_integrity_enabled:', s.get('app_integrity_enabled','MISSING'))"
