#!/usr/bin/env bash
# Bumps appVersionName patch and sets appVersionCode to the next global value.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FILE="${ROOT}/app/build.gradle.kts"
NEW_CODE="$("${ROOT}/scripts/next-version-code.sh")"

# shellcheck source=scripts/gradle-version.sh
source "${ROOT}/scripts/gradle-version.sh"
GRADLE_VERSION_FILE="$FILE"

VERSION_NAME="$(read_gradle_version_name)"
NEW_NAME="$(bump_patch_version_name "$VERSION_NAME")"
set_gradle_version "$NEW_NAME" "$NEW_CODE"

echo "version_name=${NEW_NAME}" >> "${GITHUB_OUTPUT:-/dev/null}"
echo "version_code=${NEW_CODE}" >> "${GITHUB_OUTPUT:-/dev/null}"
echo "Bumped stable ${VERSION_NAME} -> ${NEW_NAME} (code ${NEW_CODE})"
