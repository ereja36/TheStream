#!/usr/bin/env bash
# Package a release APK for VPS deploy (signed release, or debug fallback).
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUT_DIR="${ROOT_DIR}/app/build/outputs/apk/release"
DEBUG_APK="${ROOT_DIR}/app/build/outputs/apk/debug/app-debug.apk"
RELEASE_APK="${OUT_DIR}/app-release.apk"
UNSIGNED_APK="${OUT_DIR}/app-release-unsigned.apk"

mkdir -p "${OUT_DIR}"

if [[ -f "${RELEASE_APK}" ]]; then
  echo "Using signed release: ${RELEASE_APK}"
  exit 0
fi

if [[ -f "${UNSIGNED_APK}" ]] && [[ -f "${ROOT_DIR}/keystore/shif-shqip-release.jks" ]]; then
  echo "Signing unsigned release with production keystore..."
  BUILD_TOOLS="$("${ROOT_DIR}/gradlew" -q printBuildToolsVersion 2>/dev/null || true)"
  if [[ -z "${BUILD_TOOLS}" ]]; then
    BUILD_TOOLS="$(ls -1d "${ANDROID_HOME:-/home/ubuntu/android-sdk}/build-tools"/* 2>/dev/null | sort -V | tail -1)"
  fi
  ZIPALIGN="${BUILD_TOOLS}/zipalign"
  APKSIGNER="${BUILD_TOOLS}/apksigner"
  ALIGNED="${OUT_DIR}/app-release-aligned.apk"
  "${ZIPALIGN}" -f -p 4 "${UNSIGNED_APK}" "${ALIGNED}"
  "${APKSIGNER}" sign \
    --ks "${ROOT_DIR}/keystore/shif-shqip-release.jks" \
    --ks-key-alias shifshqip \
    --ks-pass "pass:${SHIF_KEYSTORE_PASSWORD:-ShifShqip2026!}" \
    --key-pass "pass:${SHIF_KEY_PASSWORD:-ShifShqip2026!}" \
    --out "${RELEASE_APK}" "${ALIGNED}"
  rm -f "${ALIGNED}"
  echo "Signed: ${RELEASE_APK}"
  exit 0
fi

if [[ -f "${DEBUG_APK}" ]]; then
  echo "No release keystore — packaging debug APK as app-release.apk (fresh install / testing)."
  cp -f "${DEBUG_APK}" "${RELEASE_APK}"
  exit 0
fi

echo "No APK found. Run ./gradlew assembleDebug or assembleRelease first." >&2
exit 1
