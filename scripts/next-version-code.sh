#!/usr/bin/env bash
# Returns max(versionCode from all GitHub releases, gradle appVersionCode) + 1.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FILE="${ROOT}/app/build.gradle.kts"
WORKDIR="${RUNNER_TEMP:-/tmp}/masroof-version-scan"
MAX_CODE=0

# shellcheck source=scripts/gradle-version.sh
source "${ROOT}/scripts/gradle-version.sh"
GRADLE_VERSION_FILE="$FILE"

if [[ -f "$FILE" ]]; then
  GRADLE_CODE="$(read_gradle_version_code)"
  if [[ "$GRADLE_CODE" =~ ^[0-9]+$ ]]; then
    MAX_CODE="$GRADLE_CODE"
  fi
fi

if command -v gh >/dev/null 2>&1; then
  mkdir -p "$WORKDIR"
  while IFS= read -r tag; do
    [[ -z "$tag" ]] && continue
    rm -f "$WORKDIR/version.json"
    if gh release download "$tag" --pattern version.json --clobber -D "$WORKDIR" >/dev/null 2>&1; then
      code="$(jq -r '.versionCode // 0' "$WORKDIR/version.json")"
      if [[ "$code" =~ ^[0-9]+$ && "$code" -gt "$MAX_CODE" ]]; then
        MAX_CODE="$code"
      fi
    fi
  done < <(gh release list --limit 200 --json tagName -q '.[].tagName')
fi

echo $((MAX_CODE + 1))
