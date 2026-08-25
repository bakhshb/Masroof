#!/usr/bin/env bash
# Shared helpers for reading/writing app/build.gradle.kts version fields.
set -euo pipefail

GRADLE_VERSION_FILE="${GRADLE_VERSION_FILE:-app/build.gradle.kts}"

read_gradle_version_name_from() {
  local file="$1"
  sed -n '/val appVersionName/,/val appVersionCode/p' "$file" \
    | grep '?:' \
    | head -1 \
    | sed 's/^[[:space:]]*?: "\([^"]*\)".*/\1/'
}

read_gradle_version_code_from() {
  local file="$1"
  sed -n '/val appVersionCode/,/val githubOwner/p' "$file" \
    | grep '?:' \
    | head -1 \
    | sed 's/^[[:space:]]*?: \([0-9][0-9]*\).*/\1/'
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
