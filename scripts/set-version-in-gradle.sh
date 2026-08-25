#!/usr/bin/env bash
# Sets appVersionName and appVersionCode in app/build.gradle.kts.
set -euo pipefail

if [[ $# -ne 2 ]]; then
  echo "Usage: $0 <version_name> <version_code>"
  exit 1
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FILE="${ROOT}/app/build.gradle.kts"

# shellcheck source=scripts/gradle-version.sh
source "${ROOT}/scripts/gradle-version.sh"
GRADLE_VERSION_FILE="$FILE"

set_gradle_version "$1" "$2"
echo "Set Gradle version to $1 ($2)"
