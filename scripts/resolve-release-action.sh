#!/usr/bin/env bash
# Decides whether the Release workflow should build or skip.
# Exits 0 with action=skip for unchanged versions or existing tags.
# Exits 0 with action=build when a new release should be published.
# Exits 1 only for misconfiguration (new tag name but versionCode not bumped).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FILE="${ROOT}/app/build.gradle.kts"
TAG="${1:-}"

if [[ -z "$TAG" ]]; then
  echo "Usage: $0 <release-tag>"
  exit 1
fi

if ! command -v gh >/dev/null 2>&1; then
  echo "gh CLI is required"
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required"
  exit 1
fi

write_outputs() {
  local action="$1"
  local reason="$2"
  if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
    echo "action=${action}" >> "$GITHUB_OUTPUT"
    echo "reason=${reason}" >> "$GITHUB_OUTPUT"
  fi
  echo "$reason"
}

CURRENT_CODE=$(grep 'val appVersionCode' "$FILE" | sed 's/.*= \([0-9]*\).*/\1/')
VERSION_NAME=$(grep 'val appVersionName' "$FILE" | sed 's/.*"\(.*\)".*/\1/')
MAX_PUBLISHED=0
TMP_DIR=$(mktemp -d)

cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

while IFS= read -r release_tag; do
  [[ -z "$release_tag" ]] && continue
  if gh release download "$release_tag" -D "$TMP_DIR" --pattern version.json 2>/dev/null; then
    code=$(jq -r '.versionCode' "$TMP_DIR/version.json")
    if [[ "$code" =~ ^[0-9]+$ ]] && (( code > MAX_PUBLISHED )); then
      MAX_PUBLISHED=$code
    fi
    rm -f "$TMP_DIR/version.json"
  fi
done < <(gh release list --limit 100 --json tagName -q '.[].tagName')

if gh release view "$TAG" >/dev/null 2>&1; then
  write_outputs "skip" "Skipped: ${TAG} is already published (${VERSION_NAME}, code ${CURRENT_CODE})."
  exit 0
fi

if (( CURRENT_CODE <= MAX_PUBLISHED )); then
  echo "Misconfiguration: ${TAG} does not exist yet, but appVersionCode=${CURRENT_CODE} is not greater than the latest published release (versionCode=${MAX_PUBLISHED})."
  echo "Bump appVersionCode (and usually appVersionName) in app/build.gradle.kts before merging to main."
  exit 1
fi

write_outputs "build" "Publishing ${TAG} (${VERSION_NAME}, code ${CURRENT_CODE})."
exit 0
