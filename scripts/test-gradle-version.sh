#!/usr/bin/env bash
# Verifies gradle-version helpers against the current app/build.gradle.kts format.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FILE="${ROOT}/app/build.gradle.kts"

# shellcheck source=scripts/gradle-version.sh
source "${ROOT}/scripts/gradle-version.sh"
GRADLE_VERSION_FILE="$FILE"

NAME="$(read_gradle_version_name)"
CODE="$(read_gradle_version_code)"

if [[ ! "$NAME" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Expected semver appVersionName, got '${NAME}'"
  exit 1
fi

if [[ ! "$CODE" =~ ^[0-9]+$ ]]; then
  echo "Expected numeric appVersionCode, got '${CODE}'"
  exit 1
fi

TMP="$(mktemp)"
cp "$FILE" "$TMP"
trap 'mv "$TMP" "$FILE"' EXIT

set_gradle_version "9.9.9" 999
UPDATED_NAME="$(read_gradle_version_name)"
UPDATED_CODE="$(read_gradle_version_code)"

if [[ "$UPDATED_NAME" != "9.9.9" || "$UPDATED_CODE" != "999" ]]; then
  echo "Round-trip failed: ${UPDATED_NAME} (${UPDATED_CODE})"
  exit 1
fi

BUMPED="$(bump_patch_version_name "0.2.28")"
if [[ "$BUMPED" != "0.2.29" ]]; then
  echo "Patch bump failed: ${BUMPED}"
  exit 1
fi

echo "gradle-version helpers ok (${NAME}, code ${CODE})"
