#!/usr/bin/env bash
# Uploads release signing material to GitHub Actions secrets (requires gh + repo admin).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ ! -f release.keystore ]]; then
  echo "Missing release.keystore — run ./scripts/generate-release-keystore.sh first"
  exit 1
fi

if [[ ! -f release-signing.env ]]; then
  echo "Missing release-signing.env — run ./scripts/generate-release-keystore.sh first"
  exit 1
fi

# shellcheck source=/dev/null
source release-signing.env

BASE64="$(base64 -w0 release.keystore 2>/dev/null || base64 release.keystore | tr -d '\n')"

echo "Uploading secrets to GitHub (repo: $(gh repo view --json nameWithOwner -q .nameWithOwner))..."

gh secret set RELEASE_KEYSTORE_BASE64 --body "$BASE64"
gh secret set RELEASE_KEYSTORE_PASSWORD --body "$RELEASE_KEYSTORE_PASSWORD"
gh secret set RELEASE_KEY_ALIAS --body "$RELEASE_KEY_ALIAS"
gh secret set RELEASE_KEY_PASSWORD --body "$RELEASE_KEY_PASSWORD"

echo "Done. GitHub Actions release workflow can now sign release APKs."
