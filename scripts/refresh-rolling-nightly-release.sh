#!/usr/bin/env bash
# Points the rolling "nightly" pre-release at a freshly published immutable nightly build.
set -euo pipefail

if [[ $# -lt 4 ]]; then
  echo "Usage: $0 <commit_sha> <apk_path> <version_json_path> <immutable_tag>"
  exit 1
fi

COMMIT_SHA="$1"
APK_PATH="$2"
VERSION_JSON_PATH="$3"
IMMUTABLE_TAG="$4"

if [[ ! -f "$APK_PATH" || ! -f "$VERSION_JSON_PATH" ]]; then
  echo "APK or version.json not found"
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

ROLLING_MANIFEST="$(mktemp)"
trap 'rm -f "$ROLLING_MANIFEST"' EXIT
jq '.releaseTag = "nightly"' "$VERSION_JSON_PATH" > "$ROLLING_MANIFEST"

NOTES="Rolling nightly pre-release. Latest immutable build: ${IMMUTABLE_TAG}"
TITLE="Nightly (${IMMUTABLE_TAG})"

git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
git config user.name "github-actions[bot]"
git tag -f nightly "$COMMIT_SHA"
git push -f origin nightly

if gh release view nightly >/dev/null 2>&1; then
  gh release upload nightly "$APK_PATH" "$ROLLING_MANIFEST" --clobber
  gh release edit nightly \
    --prerelease \
    --title "$TITLE" \
    --notes "$NOTES"
else
  gh release create nightly \
    "$APK_PATH" "$ROLLING_MANIFEST" \
    --target "$COMMIT_SHA" \
    --title "$TITLE" \
    --notes "$NOTES" \
    --prerelease
fi

echo "Rolling nightly release refreshed at ${IMMUTABLE_TAG}"
