#!/usr/bin/env bash
# Ensures app/build.gradle.kts targets a GitHub Release tag that does not exist yet.
# Handles main lagging behind published tags (e.g. bump commit could not push).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FILE="${ROOT}/app/build.gradle.kts"
MAX_ATTEMPTS=50

if ! command -v gh >/dev/null 2>&1; then
  echo "gh CLI is required"
  exit 1
fi

read_version() {
  grep 'val appVersionName' "$FILE" | sed 's/.*"\(.*\)".*/\1/'
}

release_exists() {
  local version="$1"
  gh release view "v${version}" >/dev/null 2>&1
}

for ((attempt = 1; attempt <= MAX_ATTEMPTS; attempt++)); do
  VERSION_NAME=$(read_version)
  if ! release_exists "$VERSION_NAME"; then
    echo "Release tag v${VERSION_NAME} is available."
    exit 0
  fi
  echo "Release v${VERSION_NAME} already exists — bumping version (attempt ${attempt}/${MAX_ATTEMPTS})."
  "${ROOT}/scripts/bump-version.sh"
done

echo "Could not find a free release tag after ${MAX_ATTEMPTS} bumps."
exit 1
