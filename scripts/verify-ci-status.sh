#!/usr/bin/env bash
# Verifies that the CI workflow succeeded on the target commit before publishing.
set -euo pipefail

TARGET_REF="${1:-main}"
SKIP_CI_CHECK="${SKIP_CI_CHECK:-false}"
REPO="${GITHUB_REPOSITORY:-}"
MAX_AGE_DAYS="${CI_CHECK_MAX_AGE_DAYS:-7}"
CI_JOB_NAME="${CI_JOB_NAME:-build-lint-test}"

if [[ "$SKIP_CI_CHECK" == "true" ]]; then
  echo "SKIP_CI_CHECK=true — skipping CI verification."
  exit 0
fi

if [[ -z "$REPO" ]]; then
  echo "GITHUB_REPOSITORY is not set" >&2
  exit 1
fi

if ! command -v gh >/dev/null 2>&1; then
  echo "gh CLI is required to verify CI status" >&2
  exit 1
fi

resolve_sha() {
  local ref="$1"
  if [[ "$ref" =~ ^[0-9a-f]{40}$ ]]; then
    echo "$ref"
    return
  fi
  gh api "repos/${REPO}/git/ref/heads/${ref}" --jq '.object.sha'
}

TARGET_SHA="$(resolve_sha "$TARGET_REF")"
echo "Verifying CI job '${CI_JOB_NAME}' succeeded on ${TARGET_SHA:0:7}..."

CHECK_RUNS_JSON=$(gh api "repos/${REPO}/commits/${TARGET_SHA}/check-runs?per_page=100")
MATCHING_COUNT=$(echo "$CHECK_RUNS_JSON" | jq --arg name "$CI_JOB_NAME" '[.check_runs[] | select(.name == $name)] | length')

if [[ "$MATCHING_COUNT" -eq 0 ]]; then
  echo "No '${CI_JOB_NAME}' check run found for commit ${TARGET_SHA}." >&2
  echo "Wait for CI on main to finish after merge, then retry /nightly or /release." >&2
  exit 1
fi

LATEST=$(echo "$CHECK_RUNS_JSON" | jq --arg name "$CI_JOB_NAME" '[.check_runs[] | select(.name == $name)] | sort_by(.started_at) | last')
STATUS=$(echo "$LATEST" | jq -r '.status')
CONCLUSION=$(echo "$LATEST" | jq -r '.conclusion // empty')
COMPLETED_AT=$(echo "$LATEST" | jq -r '.completed_at // empty')
HTML_URL=$(echo "$LATEST" | jq -r '.html_url // empty')

if [[ "$STATUS" != "completed" ]]; then
  echo "CI check '${CI_JOB_NAME}' is still ${STATUS}." >&2
  echo "Wait for CI to complete: ${HTML_URL}" >&2
  exit 1
fi

if [[ "$CONCLUSION" != "success" ]]; then
  echo "CI check '${CI_JOB_NAME}' concluded with '${CONCLUSION}'." >&2
  echo "Fix CI or re-run checks before publishing: ${HTML_URL}" >&2
  exit 1
fi

if [[ -n "$COMPLETED_AT" && "$COMPLETED_AT" != "null" ]]; then
  COMPLETED_EPOCH=$(date -d "$COMPLETED_AT" +%s)
  NOW_EPOCH=$(date +%s)
  MAX_AGE_SECONDS=$((MAX_AGE_DAYS * 24 * 60 * 60))
  AGE=$((NOW_EPOCH - COMPLETED_EPOCH))
  if [[ "$AGE" -gt "$MAX_AGE_SECONDS" ]]; then
    echo "CI check is older than ${MAX_AGE_DAYS} days (completed ${COMPLETED_AT})." >&2
    echo "Re-run CI on main or merge a new commit before publishing." >&2
    exit 1
  fi
fi

echo "CI verified: ${CI_JOB_NAME} succeeded at ${COMPLETED_AT}"
echo "Check run: ${HTML_URL}"
