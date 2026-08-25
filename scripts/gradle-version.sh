#!/usr/bin/env bash
# Shared helpers for reading/writing app/build.gradle.kts version fields.
set -euo pipefail

GRADLE_VERSION_FILE="${GRADLE_VERSION_FILE:-app/build.gradle.kts}"

is_multiline_gradle_format() {
  local file="$1"
  grep -q 'appVersionNameOverride' "$file"
}

read_gradle_version_name_from() {
  local file="$1"
  if is_multiline_gradle_format "$file"; then
    sed -n '/val appVersionName/,/val appVersionCode/p' "$file" \
      | grep '?:' \
      | head -1 \
      | sed 's/^[[:space:]]*?: "\([^"]*\)".*/\1/'
  else
    sed -n 's/^val appVersionName = "\([^"]*\)".*/\1/p' "$file" | head -1
  fi
}

read_gradle_version_code_from() {
  local file="$1"
  if is_multiline_gradle_format "$file"; then
    sed -n '/val appVersionCode/,/val githubOwner/p' "$file" \
      | grep '?:' \
      | head -1 \
      | sed 's/^[[:space:]]*?: \([0-9][0-9]*\).*/\1/'
  else
    sed -n 's/^val appVersionCode = \([0-9][0-9]*\).*/\1/p' "$file" | head -1
  fi
}

read_gradle_version_name() {
  read_gradle_version_name_from "$GRADLE_VERSION_FILE"
}

read_gradle_version_code() {
  read_gradle_version_code_from "$GRADLE_VERSION_FILE"
}

set_gradle_version_in() {
  local file="$1"
  local new_name="$2"
  local new_code="$3"

  if is_multiline_gradle_format "$file"; then
    set_multiline_gradle_version_in "$file" "$new_name" "$new_code"
  else
    set_legacy_gradle_version_in "$file" "$new_name" "$new_code"
  fi
}

set_multiline_gradle_version_in() {
  local file="$1"
  local new_name="$2"
  local new_code="$3"
  local tmp
  tmp="$(mktemp)"

  awk -v new_name="$new_name" -v new_code="$new_code" '
    /val appVersionName/ { in_name = 1 }
    in_name && /\?: "/ {
      sub(/\?: "[^"]*"/, "?: \"" new_name "\"")
      in_name = 0
    }
    /val appVersionCode/ { in_code = 1 }
    in_code && /\?: [0-9][0-9]*/ {
      sub(/\?: [0-9][0-9]*/, "?: " new_code)
      in_code = 0
    }
    { print }
  ' "$file" > "$tmp"
  mv "$tmp" "$file"
}

set_legacy_gradle_version_in() {
  local file="$1"
  local new_name="$2"
  local new_code="$3"
  local current_name
  current_name="$(read_gradle_version_name_from "$file")"
  sed -i "s/val appVersionName = \"${current_name}\"/val appVersionName = \"${new_name}\"/" "$file"
  sed -i "s/val appVersionCode = [0-9][0-9]*/val appVersionCode = ${new_code}/" "$file"
}

set_gradle_version() {
  set_gradle_version_in "$GRADLE_VERSION_FILE" "$1" "$2"
}

bump_patch_version_name() {
  local version_name="$1"
  IFS='.' read -r -a parts <<< "$version_name"
  local last_idx=$((${#parts[@]} - 1))
  parts[$last_idx]=$((parts[$last_idx] + 1))
  local IFS='.'
  echo "${parts[*]}"
}

release_tag_exists() {
  local version_name="$1"
  command -v gh >/dev/null 2>&1 && gh release view "v${version_name}" >/dev/null 2>&1
}

resolve_unique_stable_version_name() {
  local version_name="$1"
  local max_attempts="${2:-50}"
  local candidate="$version_name"

  for ((attempt = 1; attempt <= max_attempts; attempt++)); do
    if ! release_tag_exists "$candidate"; then
      echo "$candidate"
      return 0
    fi
    candidate="$(bump_patch_version_name "$candidate")"
  done

  echo "Could not find a free stable release tag after ${max_attempts} bumps." >&2
  return 1
}
