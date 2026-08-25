#!/usr/bin/env bash
# Computes the next stable version name/code without modifying Gradle files.
# Optional first argument: path or git ref for the Gradle baseline (defaults to local file).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FILE="${ROOT}/app/build.gradle.kts"
BASELINE_SOURCE="${1:-$FILE}"
NEW_CODE="$("${ROOT}/scripts/next-version-code.sh")"

# shellcheck source=scripts/gradle-version.sh
source "${ROOT}/scripts/gradle-version.sh"

if [[ "$BASELINE_SOURCE" == refs/heads/* || "$BASELINE_SOURCE" == origin/* || "$BASELINE_SOURCE" == main ]]; then
  REF="${BASELINE_SOURCE#refs/heads/}"
  REF="${REF#origin/}"
  TMP_FILE="$(mktemp)"
  git show "origin/${REF}:app/build.gradle.kts" > "$TMP_FILE"
  VERSION_NAME="$(read_gradle_version_name_from "$TMP_FILE")"
  rm -f "$TMP_FILE"
elif [[ -f "$BASELINE_SOURCE" ]]; then
  VERSION_NAME="$(read_gradle_version_name_from "$BASELINE_SOURCE")"
else
  echo "Could not resolve Gradle baseline from ${BASELINE_SOURCE}"
  exit 1
fi

NEW_NAME="$(bump_patch_version_name "$VERSION_NAME")"
if command -v gh >/dev/null 2>&1; then
  NEW_NAME="$(resolve_unique_stable_version_name "$NEW_NAME")"
fi

echo "version_name=${NEW_NAME}" >> "${GITHUB_OUTPUT:-/dev/null}"
echo "version_code=${NEW_CODE}" >> "${GITHUB_OUTPUT:-/dev/null}"
echo "tag=v${NEW_NAME}" >> "${GITHUB_OUTPUT:-/dev/null}"
echo "Next stable release: ${NEW_NAME} (code ${NEW_CODE})"
