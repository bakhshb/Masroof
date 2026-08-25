#!/usr/bin/env bash
# Sets appVersionName and appVersionCode in app/build.gradle.kts.
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <version_name> <version_code>"
  exit 1
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FILE="${ROOT}/app/build.gradle.kts"
NEW_NAME="$1"
NEW_CODE="$2"

CURRENT_NAME=$(grep 'val appVersionName' "$FILE" | sed 's/.*"\(.*\)".*/\1/')
sed -i "s/val appVersionName = \"${CURRENT_NAME}\"/val appVersionName = \"${NEW_NAME}\"/" "$FILE"
sed -i "s/val appVersionCode = [0-9]*/val appVersionCode = ${NEW_CODE}/" "$FILE"

echo "Set Gradle version to ${NEW_NAME} (${NEW_CODE})"
