#!/usr/bin/env bash
# Verifies Gradle (and optionally APK) versions match the expected release values.
set -euo pipefail

if [[ $# -lt 3 || $# -gt 4 ]]; then
  echo "Usage: $0 <gradle_file> <version_name> <version_code> [apk_path]"
  exit 1
fi

GRADLE_FILE="$1"
EXPECTED_NAME="$2"
EXPECTED_CODE="$3"
APK_PATH="${4:-}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=scripts/gradle-version.sh
source "${ROOT}/scripts/gradle-version.sh"

resolve_aapt() {
  if command -v aapt >/dev/null 2>&1; then
    command -v aapt
    return 0
  fi

  local sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
  if [[ -n "$sdk_root" && -d "$sdk_root/build-tools" ]]; then
    local latest
    latest="$(ls -1 "$sdk_root/build-tools" 2>/dev/null | sort -V | tail -1)"
    if [[ -n "$latest" && -x "$sdk_root/build-tools/$latest/aapt" ]]; then
      echo "$sdk_root/build-tools/$latest/aapt"
      return 0
    fi
  fi

  return 1
}

ACTUAL_NAME="$(read_gradle_version_name_from "$GRADLE_FILE")"
ACTUAL_CODE="$(read_gradle_version_code_from "$GRADLE_FILE")"

if [[ "$ACTUAL_NAME" != "$EXPECTED_NAME" || "$ACTUAL_CODE" != "$EXPECTED_CODE" ]]; then
  echo "Gradle version mismatch: expected ${EXPECTED_NAME} (${EXPECTED_CODE}), got ${ACTUAL_NAME} (${ACTUAL_CODE})"
  exit 1
fi

if [[ -n "$APK_PATH" ]]; then
  if [[ ! -f "$APK_PATH" ]]; then
    echo "APK not found: ${APK_PATH}"
    exit 1
  fi

  AAPT="$(resolve_aapt)" || {
    echo "aapt not found; cannot verify APK at ${APK_PATH}"
    exit 1
  }

  BADGING="$("$AAPT" dump badging "$APK_PATH")"
  APK_CODE="$(echo "$BADGING" | sed -n "s/.*versionCode='\([0-9][0-9]*\)'.*/\1/p" | head -1)"
  APK_NAME="$(echo "$BADGING" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p" | head -1)"
  if [[ "$APK_CODE" != "$EXPECTED_CODE" || "$APK_NAME" != "$EXPECTED_NAME" ]]; then
    echo "APK version mismatch: expected ${EXPECTED_NAME} (${EXPECTED_CODE}), got ${APK_NAME} (${APK_CODE})"
    exit 1
  fi
fi

echo "Release version verified: ${EXPECTED_NAME} (${EXPECTED_CODE})"
