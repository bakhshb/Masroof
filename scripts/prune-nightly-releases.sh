#!/usr/bin/env bash
# Deletes old immutable nightly pre-releases, keeping the newest N builds.
set -euo pipefail

KEEP="${1:-10}"

if ! command -v gh >/dev/null 2>&1; then
  echo "gh CLI is required"
  exit 1
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required"
  exit 1
fi

if [[ ! "$KEEP" =~ ^[0-9]+$ ]]; then
  echo "KEEP must be a non-negative integer"
  exit 1
fi

mapfile -t TAGS_TO_DELETE < <(
  gh release list --limit 200 --json tagName,isPrerelease,createdAt \
    | jq -r --argjson keep "$KEEP" '
        [.[] |
          select(.isPrerelease) |
          select(.tagName != "nightly") |
          select(.tagName | test("^v[0-9].*-nightly-"))
        ] |
        sort_by(.createdAt) |
        reverse |
        .[$keep:] |
        .[].tagName
      '
)

if [[ "${#TAGS_TO_DELETE[@]}" -eq 0 ]]; then
  echo "No nightly pre-releases to prune (keeping ${KEEP})"
  exit 0
fi

for tag in "${TAGS_TO_DELETE[@]}"; do
  echo "Deleting old nightly release ${tag}"
  gh release delete "$tag" --yes --cleanup-tag
done

echo "Pruned ${#TAGS_TO_DELETE[@]} nightly release(s); keeping ${KEEP}"
