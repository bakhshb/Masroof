#!/usr/bin/env bash
# Computes the next stable version name/code without modifying Gradle files.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FILE="${ROOT}/app/build.gradle.kts"
NEW_CODE="$("${ROOT}/scripts/next-version-code.sh")"

VERSION_NAME=$(grep 'val appVersionName' "$FILE" | sed 's/.*"\(.*\)".*/\1/')
IFS='.' read -r -a PARTS <<< "$VERSION_NAME"
LAST_IDX=$((${#PARTS[@]} - 1))
PARTS[$LAST_IDX]=$((PARTS[$LAST_IDX] + 1))
NEW_NAME=$(IFS='.'; echo "${PARTS[*]}")

echo "version_name=${NEW_NAME}" >> "${GITHUB_OUTPUT:-/dev/null}"
echo "version_code=${NEW_CODE}" >> "${GITHUB_OUTPUT:-/dev/null}"
echo "tag=v${NEW_NAME}" >> "${GITHUB_OUTPUT:-/dev/null}"
echo "Next stable release: ${NEW_NAME} (code ${NEW_CODE})"
