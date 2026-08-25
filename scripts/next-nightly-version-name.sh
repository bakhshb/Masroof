#!/usr/bin/env bash
# Computes the next nightly version name from the stable name in app/build.gradle.kts.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FILE="${ROOT}/app/build.gradle.kts"

# shellcheck source=scripts/gradle-version.sh
source "${ROOT}/scripts/gradle-version.sh"
GRADLE_VERSION_FILE="$FILE"

if ! command -v gh >/dev/null 2>&1; then
  echo "gh CLI is required"
  exit 1
fi

STABLE_NAME="$(read_gradle_version_name)"
N=1
while gh release view "v${STABLE_NAME}-nightly-${N}" >/dev/null 2>&1; do
  N=$((N + 1))
done

echo "${STABLE_NAME}-nightly-${N}"
