#!/usr/bin/env bash
# Verifies gradle-version helpers against multiline and legacy Gradle formats.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FILE="${ROOT}/app/build.gradle.kts"
LEGACY_FILE="$(mktemp)"

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
  echo "Multiline round-trip failed: ${UPDATED_NAME} (${UPDATED_CODE})"
  exit 1
fi

BUMPED="$(bump_patch_version_name "0.2.28")"
if [[ "$BUMPED" != "0.2.29" ]]; then
  echo "Patch bump failed: ${BUMPED}"
  exit 1
fi

cat > "$LEGACY_FILE" <<'EOF'
val appVersionName = "1.0.0"
val appVersionCode = 10
val githubOwner = "example"
EOF

set_gradle_version_in "$LEGACY_FILE" "1.0.1" 11
LEGACY_NAME="$(read_gradle_version_name_from "$LEGACY_FILE")"
LEGACY_CODE="$(read_gradle_version_code_from "$LEGACY_FILE")"
if [[ "$LEGACY_NAME" != "1.0.1" || "$LEGACY_CODE" != "11" ]]; then
  echo "Legacy round-trip failed: ${LEGACY_NAME} (${LEGACY_CODE})"
  exit 1
fi

rm -f "$LEGACY_FILE"
echo "gradle-version helpers ok (${NAME}, code ${CODE})"
