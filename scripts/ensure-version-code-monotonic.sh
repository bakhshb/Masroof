#!/usr/bin/env bash
# Fails if app/build.gradle.kts versionCode is not greater than any published release.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FILE="${ROOT}/app/build.gradle.kts"

if ! command -v gh >/dev/null 2>&1; then
  echo "gh CLI is required"
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required"
  exit 1
fi

CURRENT_CODE=$(grep 'val appVersionCode' "$FILE" | sed 's/.*= \([0-9]*\).*/\1/')
MAX_PUBLISHED=0
TMP_DIR=$(mktemp -d)

cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

while IFS= read -r tag; do
  [[ -z "$tag" ]] && continue
  if gh release download "$tag" -D "$TMP_DIR" --pattern version.json 2>/dev/null; then
    code=$(jq -r '.versionCode' "$TMP_DIR/version.json")
    if [[ "$code" =~ ^[0-9]+$ ]] && (( code > MAX_PUBLISHED )); then
      MAX_PUBLISHED=$code
    fi
    rm -f "$TMP_DIR/version.json"
  fi
done < <(gh release list --limit 100 --json tagName -q '.[].tagName')

if (( CURRENT_CODE <= MAX_PUBLISHED )); then
  echo "appVersionCode=$CURRENT_CODE must be greater than latest published release (versionCode=$MAX_PUBLISHED)."
  echo "Bump app/build.gradle.kts before merging to main."
  exit 1
fi

echo "versionCode $CURRENT_CODE is greater than latest published ($MAX_PUBLISHED)."
