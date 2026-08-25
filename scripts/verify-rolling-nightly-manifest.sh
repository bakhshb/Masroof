#!/usr/bin/env bash
# Verifies the rolling nightly release exposes a valid version.json asset.
set -euo pipefail

REPO="${GITHUB_REPOSITORY:-bakhshb/Masroof}"
EXPECTED_CODE="${1:-}"
EXPECTED_NAME="${2:-}"

if ! command -v gh >/dev/null 2>&1; then
  echo "gh CLI is required"
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required"
  exit 1
fi

WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

gh release download nightly --pattern version.json --clobber -D "$WORKDIR" -R "$REPO"
MANIFEST="${WORKDIR}/version.json"

if [[ ! -f "$MANIFEST" ]]; then
  echo "Rolling nightly version.json asset is missing"
  exit 1
fi

CHANNEL="$(jq -r '.channel // empty' "$MANIFEST")"
RELEASE_TAG="$(jq -r '.releaseTag // empty' "$MANIFEST")"
CODE="$(jq -r '.versionCode // empty' "$MANIFEST")"
NAME="$(jq -r '.versionName // empty' "$MANIFEST")"

if [[ "$CHANNEL" != "nightly" ]]; then
  echo "Expected channel=nightly, got ${CHANNEL}"
  exit 1
fi

if [[ "$RELEASE_TAG" != "nightly" ]]; then
  echo "Expected releaseTag=nightly, got ${RELEASE_TAG}"
  exit 1
fi

if [[ -n "$EXPECTED_CODE" && "$CODE" != "$EXPECTED_CODE" ]]; then
  echo "Expected versionCode=${EXPECTED_CODE}, got ${CODE}"
  exit 1
fi

if [[ -n "$EXPECTED_NAME" && "$NAME" != "$EXPECTED_NAME" ]]; then
  echo "Expected versionName=${EXPECTED_NAME}, got ${NAME}"
  exit 1
fi

echo "Rolling nightly manifest verified (${NAME}, code ${CODE})"
