#!/usr/bin/env bash
# Resolves next version name, version code, tag, and prerelease flag for Masroof.
# Usage: ./scripts/resolve-next-version.sh [--type release|nightly] [--bump patch|minor|major]
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
GRADLE_FILE="${ROOT}/app/build.gradle.kts"

TYPE="release"
BUMP="patch"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --type|-t)
      TYPE="${2:-release}"
      shift 2
      ;;
    --bump|-b)
      BUMP="${2:-patch}"
      shift 2
      ;;
    release|nightly)
      TYPE="$1"
      shift
      ;;
    patch|minor|major)
      BUMP="$1"
      shift
      ;;
    *)
      echo "Unknown argument: $1" >&2
      echo "Usage: $0 [--type release|nightly] [--bump patch|minor|major]" >&2
      exit 1
      ;;
  esac
done

if [[ "$TYPE" != "release" && "$TYPE" != "nightly" ]]; then
  echo "Invalid type: $TYPE (expected 'release' or 'nightly')" >&2
  exit 1
fi

if [[ "$BUMP" != "patch" && "$BUMP" != "minor" && "$BUMP" != "major" ]]; then
  echo "Invalid bump: $BUMP (expected 'patch', 'minor', or 'major')" >&2
  exit 1
fi

# 1. Read fallback version and code from app/build.gradle.kts
FALLBACK_NAME="0.3.17"
FALLBACK_CODE="70"

if [[ -f "$GRADLE_FILE" ]]; then
  EXTRACTED_NAME=$(grep 'val appVersionName' "$GRADLE_FILE" -A 2 | grep -oE '"[0-9]+\.[0-9]+\.[0-9]+"' | tr -d '"' | head -n 1 || true)
  if [[ -n "$EXTRACTED_NAME" ]]; then
    FALLBACK_NAME="$EXTRACTED_NAME"
  fi
  EXTRACTED_CODE=$(grep 'val appVersionCode' "$GRADLE_FILE" -A 2 | grep -oE '[0-9]+' | head -n 1 || true)
  if [[ -n "$EXTRACTED_CODE" ]]; then
    FALLBACK_CODE="$EXTRACTED_CODE"
  fi
fi

# 2. Collect published stable releases only (ignore orphan git tags)
STABLE_VERSIONS=()

# Collect from published GitHub releases if gh is available
if command -v gh >/dev/null 2>&1; then
  while IFS= read -r tag; do
    [[ -z "$tag" ]] && continue
    # Strip leading 'v'
    v="${tag#v}"
    if [[ "$v" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
      STABLE_VERSIONS+=("$v")
    fi
  done < <(gh release list --limit 1000 --json tagName,isPrerelease -q '.[] | select(.isPrerelease == false) | .tagName' 2>/dev/null || true)
fi

# Add fallback version
STABLE_VERSIONS+=("$FALLBACK_NAME")

# Deduplicate and sort with sort -V
LATEST_STABLE=$(printf '%s\n' "${STABLE_VERSIONS[@]}" | sort -u -V | tail -n 1)

IFS='.' read -r MAJOR MINOR PATCH <<< "$LATEST_STABLE"
MAJOR=${MAJOR:-0}
MINOR=${MINOR:-0}
PATCH=${PATCH:-0}

# 3. Calculate Next Version Name and Tag
if [[ "$TYPE" == "release" ]]; then
  case "$BUMP" in
    major)
      NEXT_STABLE="$((MAJOR + 1)).0.0"
      ;;
    minor)
      NEXT_STABLE="${MAJOR}.$((MINOR + 1)).0"
      ;;
    patch)
      NEXT_STABLE="${MAJOR}.${MINOR}.$((PATCH + 1))"
      ;;
  esac
  VERSION_NAME="${NEXT_STABLE}"
  TAG="v${NEXT_STABLE}"
  IS_PRERELEASE="false"
else
  # Nightly is based on the NEXT intended stable version
  case "$BUMP" in
    major)
      NEXT_STABLE="$((MAJOR + 1)).0.0"
      ;;
    minor)
      NEXT_STABLE="${MAJOR}.$((MINOR + 1)).0"
      ;;
    patch)
      NEXT_STABLE="${MAJOR}.${MINOR}.$((PATCH + 1))"
      ;;
  esac

  # Find existing nightly numbers for this NEXT_STABLE
  NIGHTLY_NUMBERS=()
  NIGHTLY_PREFIX="${NEXT_STABLE}-nightly"

  # Check published GitHub releases for matching nightlies (ignore orphan git tags)
  if command -v gh >/dev/null 2>&1; then
    while IFS= read -r tag; do
      [[ -z "$tag" ]] && continue
      v="${tag#v}"
      if [[ "$v" =~ ^${NIGHTLY_PREFIX}[.-]([0-9]+)$ ]]; then
        NIGHTLY_NUMBERS+=("${BASH_REMATCH[1]}")
      fi
    done < <(gh release list --limit 1000 --json tagName -q '.[].tagName' 2>/dev/null || true)
  fi

  MAX_NIGHTLY=0
  for num in "${NIGHTLY_NUMBERS[@]:-}"; do
    if [[ -n "$num" ]] && (( num > MAX_NIGHTLY )); then
      MAX_NIGHTLY=$num
    fi
  done

  NEXT_NIGHTLY=$((MAX_NIGHTLY + 1))
  VERSION_NAME="${NEXT_STABLE}-nightly.${NEXT_NIGHTLY}"
  TAG="v${NEXT_STABLE}-nightly.${NEXT_NIGHTLY}"
  IS_PRERELEASE="true"
fi

# 4. Determine Global Max versionCode
MAX_CODE=$((FALLBACK_CODE))
TMP_DIR=$(mktemp -d)
cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

if command -v gh >/dev/null 2>&1; then
  # Inspect top releases (newest first) to find max versionCode from version.json
  while IFS= read -r release_tag; do
    [[ -z "$release_tag" ]] && continue
    if gh release download "$release_tag" -D "$TMP_DIR" --pattern version.json 2>/dev/null; then
      if [[ -f "$TMP_DIR/version.json" ]] && command -v jq >/dev/null 2>&1; then
        code=$(jq -r '.versionCode // empty' "$TMP_DIR/version.json" 2>/dev/null || true)
        if [[ "$code" =~ ^[0-9]+$ ]] && (( code > MAX_CODE )); then
          MAX_CODE=$code
        fi
        rm -f "$TMP_DIR/version.json"
      fi
    fi
  done < <(gh release list --limit 30 --json tagName -q '.[].tagName' 2>/dev/null || true)
fi

NEXT_CODE=$((MAX_CODE + 1))
APK_NAME="masroof-${VERSION_NAME}.apk"

if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
  echo "version_name=${VERSION_NAME}" >> "$GITHUB_OUTPUT"
  echo "version_code=${NEXT_CODE}" >> "$GITHUB_OUTPUT"
  echo "tag=${TAG}" >> "$GITHUB_OUTPUT"
  echo "is_prerelease=${IS_PRERELEASE}" >> "$GITHUB_OUTPUT"
  echo "apk_file_name=${APK_NAME}" >> "$GITHUB_OUTPUT"
fi

echo "Resolved version target:"
echo "  Type:          ${TYPE}"
echo "  Bump:          ${BUMP}"
echo "  Latest stable: v${LATEST_STABLE}"
echo "  Target Tag:    ${TAG}"
echo "  Version Name:  ${VERSION_NAME}"
echo "  Version Code:  ${NEXT_CODE}"
echo "  Prerelease:    ${IS_PRERELEASE}"
echo "  APK File:      ${APK_NAME}"
