#!/usr/bin/env bash
# Computes the next nightly version name from the stable name in app/build.gradle.kts.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FILE="${ROOT}/app/build.gradle.kts"

if ! command -v gh >/dev/null 2>&1; then
  echo "gh CLI is required"
  exit 1
fi

STABLE_NAME="$(grep 'val appVersionName' "$FILE" | sed 's/.*"\(.*\)".*/\1/')"
N=1
while gh release view "v${STABLE_NAME}-nightly-${N}" >/dev/null 2>&1; do
  N=$((N + 1))
done

echo "${STABLE_NAME}-nightly-${N}"
