#!/usr/bin/env bash
# Bumps appVersionCode (+1) and appVersionName (patch segment) in app/build.gradle.kts.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FILE="${ROOT}/app/build.gradle.kts"

VERSION_NAME=$(grep 'val appVersionName' "$FILE" | sed 's/.*"\(.*\)".*/\1/')
VERSION_CODE=$(grep 'val appVersionCode' "$FILE" | sed 's/.*= \([0-9]*\).*/\1/')

NEW_CODE=$((VERSION_CODE + 1))

IFS='.' read -r -a PARTS <<< "$VERSION_NAME"
LAST_IDX=$((${#PARTS[@]} - 1))
PARTS[$LAST_IDX]=$((PARTS[$LAST_IDX] + 1))
NEW_NAME=$(IFS='.'; echo "${PARTS[*]}")

sed -i "s/val appVersionName = \"${VERSION_NAME}\"/val appVersionName = \"${NEW_NAME}\"/" "$FILE"
sed -i "s/val appVersionCode = ${VERSION_CODE}/val appVersionCode = ${NEW_CODE}/" "$FILE"

echo "version_name=${NEW_NAME}" >> "${GITHUB_OUTPUT:-/dev/null}"
echo "version_code=${NEW_CODE}" >> "${GITHUB_OUTPUT:-/dev/null}"
echo "Bumped ${VERSION_NAME} (${VERSION_CODE}) -> ${NEW_NAME} (${NEW_CODE})"
