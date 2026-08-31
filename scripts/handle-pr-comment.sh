#!/usr/bin/env bash
# Handles PR slash commands (/release, /release minor, /release major, /nightly).
# Validates authorization and PR merge status, then triggers release.yml.
set -euo pipefail

COMMENT_BODY="${COMMENT_BODY:-${1:-}}"
PR_NUMBER="${PR_NUMBER:-${2:-}}"
COMMENT_ID="${COMMENT_ID:-${3:-}}"
COMMENT_AUTHOR="${COMMENT_AUTHOR:-${4:-}}"
COMMENT_AUTHOR_ASSOCIATION="${COMMENT_AUTHOR_ASSOCIATION:-${5:-}}"
REPO="${REPO:-${GITHUB_REPOSITORY:-}}"

if [[ -z "$COMMENT_BODY" || -z "$PR_NUMBER" ]]; then
  echo "Usage: COMMENT_BODY='...' PR_NUMBER='...' $0" >&2
  exit 1
fi

# 1. Parse slash command
CLEAN_BODY="$(echo "$COMMENT_BODY" | tr -d '\r' | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
FIRST_LINE="$(echo "$CLEAN_BODY" | head -n 1)"

TYPE=""
BUMP="patch"

case "$FIRST_LINE" in
  "/nightly"*)
    TYPE="nightly"
    ;;
  "/release"|"/release patch")
    TYPE="release"
    BUMP="patch"
    ;;
  "/release minor")
    TYPE="release"
    BUMP="minor"
    ;;
  "/release major")
    TYPE="release"
    BUMP="major"
    ;;
  *)
    echo "Unrecognized release command: '$FIRST_LINE'" >&2
    if [[ -n "${COMMENT_ID:-}" && -n "$REPO" ]] && command -v gh >/dev/null 2>&1; then
      gh api "repos/${REPO}/issues/comments/${COMMENT_ID}/reactions" -f content="-1" 2>/dev/null || true
      gh pr comment "$PR_NUMBER" --body "❌ **Invalid command:** \`${FIRST_LINE}\`.\nSupported commands:\n- \`/nightly\`\n- \`/release\` (or \`/release patch\`)\n- \`/release minor\`\n- \`/release major\`" 2>/dev/null || true
    fi
    exit 1
    ;;
esac

# 2. Check authorization
IS_AUTHORIZED=false
if [[ "$COMMENT_AUTHOR_ASSOCIATION" == "OWNER" || "$COMMENT_AUTHOR_ASSOCIATION" == "MEMBER" || "$COMMENT_AUTHOR_ASSOCIATION" == "COLLABORATOR" ]]; then
  IS_AUTHORIZED=true
elif command -v gh >/dev/null 2>&1 && [[ -n "$REPO" && -n "$COMMENT_AUTHOR" ]]; then
  PERM=$(gh api "repos/${REPO}/collaborators/${COMMENT_AUTHOR}/permission" --jq '.permission' 2>/dev/null || true)
  if [[ "$PERM" == "admin" || "$PERM" == "write" ]]; then
    IS_AUTHORIZED=true
  fi
fi

if [[ "$IS_AUTHORIZED" != "true" ]]; then
  echo "User '$COMMENT_AUTHOR' ($COMMENT_AUTHOR_ASSOCIATION) is not authorized." >&2
  if [[ -n "${COMMENT_ID:-}" && -n "$REPO" ]] && command -v gh >/dev/null 2>&1; then
    gh api "repos/${REPO}/issues/comments/${COMMENT_ID}/reactions" -f content="-1" 2>/dev/null || true
    gh pr comment "$PR_NUMBER" --body "❌ **Permission denied.** Only repository collaborators and owners can trigger release builds." 2>/dev/null || true
  fi
  exit 1
fi

# 3. Verify PR is merged
if command -v gh >/dev/null 2>&1 && [[ -n "$REPO" ]]; then
  PR_JSON=$(gh pr view "$PR_NUMBER" --repo "$REPO" --json state,merged,mergeCommit,headRefOid 2>/dev/null || true)
  if [[ -z "$PR_JSON" ]]; then
    echo "Could not fetch PR #$PR_NUMBER details" >&2
    exit 1
  fi

  IS_MERGED=$(echo "$PR_JSON" | jq -r '.merged // false')
  if [[ "$IS_MERGED" != "true" ]]; then
    echo "PR #$PR_NUMBER is not merged." >&2
    if [[ -n "${COMMENT_ID:-}" ]]; then
      gh api "repos/${REPO}/issues/comments/${COMMENT_ID}/reactions" -f content="-1" 2>/dev/null || true
    fi
    gh pr comment "$PR_NUMBER" --repo "$REPO" --body "❌ **Release commands are only allowed on merged PRs.** Please merge this PR before running \`/${TYPE}\`." 2>/dev/null || true
    exit 1
  fi

  TARGET_SHA=$(echo "$PR_JSON" | jq -r '.mergeCommit.oid // .headRefOid // "main"')
else
  TARGET_SHA="main"
fi

# 4. React with 'eyes' to confirm command acceptance
if [[ -n "${COMMENT_ID:-}" && -n "$REPO" ]] && command -v gh >/dev/null 2>&1; then
  gh api "repos/${REPO}/issues/comments/${COMMENT_ID}/reactions" -f content="eyes" 2>/dev/null || true
fi

# 5. Trigger release workflow
echo "Triggering release workflow: type=$TYPE, bump=$BUMP, ref=$TARGET_SHA, pr=$PR_NUMBER"

if command -v gh >/dev/null 2>&1 && [[ -n "$REPO" ]]; then
  gh workflow run release.yml \
    --repo "$REPO" \
    -f type="$TYPE" \
    -f bump="$BUMP" \
    -f ref="$TARGET_SHA" \
    -f pr_number="$PR_NUMBER" \
    -f comment_id="${COMMENT_ID:-}"

  SHORT_SHA="${TARGET_SHA:0:7}"
  gh pr comment "$PR_NUMBER" --repo "$REPO" --body "⏳ **Accepted \`${FIRST_LINE}\` command.** Queued \`${TYPE}\` build for commit \`${SHORT_SHA}\`..." 2>/dev/null || true
fi

echo "Successfully triggered release for PR #$PR_NUMBER"
