#!/usr/bin/env bash
# Bumps version in app/build.gradle.kts until the target GitHub Release tag is free.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FILE="${ROOT}/app/build.gradle.kts"
MAX_ATTEMPTS=20

if ! command -v gh >/dev/null 2>&1; then
  echo "gh CLI is required"
  exit 1
fi

for ((attempt = 1; attempt <= MAX_ATTEMPTS; attempt++)); do
  VERSION_NAME=$(grep 'val appVersionName' "$FILE" | sed 's/.*"\(.*\)".*/\1/')
  TAG="v${VERSION_NAME}"
  if ! gh release view "$TAG" >/dev/null 2>&1; then
    echo "Release tag ${TAG} is available."
    exit 0
  fi
  echo "Release ${TAG} already exists — bumping version (attempt ${attempt}/${MAX_ATTEMPTS})."
  "${ROOT}/scripts/bump-version.sh"
done

echo "Could not find a free release tag after ${MAX_ATTEMPTS} bumps."
exit 1
