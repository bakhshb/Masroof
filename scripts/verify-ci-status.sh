#!/usr/bin/env bash
# Verifies that the CI workflow succeeded on the target commit before publishing.
# Waits for in-progress CI (e.g. right after merge) up to CI_WAIT_TIMEOUT_SECONDS.
set -euo pipefail

TARGET_REF="${1:-main}"
SKIP_CI_CHECK="${SKIP_CI_CHECK:-false}"
REPO="${GITHUB_REPOSITORY:-}"
MAX_AGE_DAYS="${CI_CHECK_MAX_AGE_DAYS:-7}"
CI_JOB_NAME="${CI_JOB_NAME:-build-lint-test}"
WAIT_TIMEOUT_SECONDS="${CI_WAIT_TIMEOUT_SECONDS:-2400}"
POLL_INTERVAL_SECONDS="${CI_POLL_INTERVAL_SECONDS:-30}"

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

fetch_latest_check_json() {
  local check_runs_json
  check_runs_json=$(gh api "repos/${REPO}/commits/${TARGET_SHA}/check-runs?per_page=100")
  echo "$check_runs_json" | jq --arg name "$CI_JOB_NAME" '[.check_runs[] | select(.name == $name)] | sort_by(.started_at) | last // empty'
}

TARGET_SHA="$(resolve_sha "$TARGET_REF")"
echo "Verifying CI job '${CI_JOB_NAME}' succeeded on ${TARGET_SHA:0:7}..."
echo "Will wait up to ${WAIT_TIMEOUT_SECONDS}s for CI to finish if still running."

START_EPOCH=$(date +%s)

while true; do
  ELAPSED=$(( $(date +%s) - START_EPOCH ))
  if [[ "$ELAPSED" -gt "$WAIT_TIMEOUT_SECONDS" ]]; then
    echo "Timed out after ${WAIT_TIMEOUT_SECONDS}s waiting for '${CI_JOB_NAME}' on ${TARGET_SHA:0:7}." >&2
    exit 1
  fi

  LATEST="$(fetch_latest_check_json)"
  if [[ -z "$LATEST" || "$LATEST" == "null" ]]; then
    echo "No '${CI_JOB_NAME}' check run yet (${ELAPSED}s elapsed). Waiting..."
    sleep "$POLL_INTERVAL_SECONDS"
    continue
  fi

  STATUS=$(echo "$LATEST" | jq -r '.status')
  CONCLUSION=$(echo "$LATEST" | jq -r '.conclusion // empty')
  COMPLETED_AT=$(echo "$LATEST" | jq -r '.completed_at // empty')
  HTML_URL=$(echo "$LATEST" | jq -r '.html_url // empty')

  if [[ "$STATUS" == "queued" || "$STATUS" == "in_progress" ]]; then
    echo "CI check '${CI_JOB_NAME}' is ${STATUS} (${ELAPSED}s elapsed). Waiting..."
  elif [[ "$STATUS" != "completed" ]]; then
    echo "CI check '${CI_JOB_NAME}' has unexpected status '${STATUS}'." >&2
    exit 1
  else
    break
  fi

  sleep "$POLL_INTERVAL_SECONDS"
done

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
