#!/usr/bin/env bash
# Verifies Gradle (and optionally APK) versions match the expected release values.
set -euo pipefail

if [[ $# -ne 3 ]]; then
  echo "Usage: $0 <gradle_file> <version_name> <version_code>"
  exit 1
fi

GRADLE_FILE="$1"
EXPECTED_NAME="$2"
EXPECTED_CODE="$3"
APK_PATH="${4:-}"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# shellcheck source=scripts/gradle-version.sh
source "${ROOT}/scripts/gradle-version.sh"

ACTUAL_NAME="$(read_gradle_version_name_from "$GRADLE_FILE")"
ACTUAL_CODE="$(read_gradle_version_code_from "$GRADLE_FILE")"

if [[ "$ACTUAL_NAME" != "$EXPECTED_NAME" || "$ACTUAL_CODE" != "$EXPECTED_CODE" ]]; then
  echo "Gradle version mismatch: expected ${EXPECTED_NAME} (${EXPECTED_CODE}), got ${ACTUAL_NAME} (${ACTUAL_CODE})"
  exit 1
fi

if [[ -n "$APK_PATH" && -f "$APK_PATH" ]]; then
  if command -v aapt >/dev/null 2>&1; then
    BADGING="$(aapt dump badging "$APK_PATH")"
    APK_CODE="$(echo "$BADGING" | sed -n "s/.*versionCode='\([0-9][0-9]*\)'.*/\1/p" | head -1)"
    APK_NAME="$(echo "$BADGING" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p" | head -1)"
    if [[ "$APK_CODE" != "$EXPECTED_CODE" || "$APK_NAME" != "$EXPECTED_NAME" ]]; then
      echo "APK version mismatch: expected ${EXPECTED_NAME} (${EXPECTED_CODE}), got ${APK_NAME} (${APK_CODE})"
      exit 1
    fi
  else
    echo "aapt not available; verified Gradle only"
  fi
fi

echo "Release version verified: ${EXPECTED_NAME} (${EXPECTED_CODE})"
