#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
CONFIG_FILE="${ROOT_DIR}/app/src/main/java/com/shif/shqip/android/Config.java"
CONFIG_BACKUP="${CONFIG_FILE}.build-backup"

SERVER_KEY="${SERVER_KEY:-WVVoU01HTklUVFpNZVRsb1kwZHJkV015YUhCYWJrNXZZMWRzZDB4dFRuWmlWamxvWTBoQ2MyRlhUbWhrUjJ4MlltdHNhMWd5VG5aaVV6VjZZVWRzYlV4dVRtOWpWMngzVEcxR2RWcElTblpoVjFFOQ==}"
REST_API_KEY="${REST_API_KEY:-shifshqip}"

if [[ "$SERVER_KEY" == "XXXXX" || "$REST_API_KEY" == "XXXXX" ]]; then
  echo "Missing SERVER_KEY or REST_API_KEY environment variables." >&2
  echo "Example:" >&2
  echo "  export REST_API_KEY='...'" >&2
  echo "  export SERVER_KEY='...'" >&2
  echo "  ./build-release.sh" >&2
  exit 1
fi

cleanup() {
  if [[ -f "$CONFIG_BACKUP" ]]; then
    mv -f "$CONFIG_BACKUP" "$CONFIG_FILE"
  fi
}
trap cleanup EXIT

cp "$CONFIG_FILE" "$CONFIG_BACKUP"
sed -i "s|SERVER_KEY = \"XXXXX\"|SERVER_KEY = \"${SERVER_KEY}\"|" "$CONFIG_FILE"
sed -i "s|REST_API_KEY = \"XXXXX\"|REST_API_KEY = \"${REST_API_KEY}\"|" "$CONFIG_FILE"

if [[ -n "${ANDROID_HOME:-}" ]]; then
  echo "sdk.dir=${ANDROID_HOME}" > "${ROOT_DIR}/local.properties"
fi

chmod +x "${ROOT_DIR}/gradlew"
"${ROOT_DIR}/gradlew" assembleRelease --no-daemon "$@"

echo "Release APK: ${ROOT_DIR}/app/build/outputs/apk/release/app-release.apk"
