#!/usr/bin/env bash
# Unit and integration tests for version resolution logic.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SCRIPT="${ROOT}/scripts/resolve-next-version.sh"

PASSED=0
FAILED=0

assert_eq() {
  local test_name="$1"
  local expected="$2"
  local actual="$3"
  if [[ "$expected" == "$actual" ]]; then
    echo "  [PASS] $test_name: $actual"
    PASSED=$((PASSED + 1))
  else
    echo "  [FAIL] $test_name: expected '$expected', got '$actual'"
    FAILED=$((FAILED + 1))
  fi
}

echo "=== Running Version Resolution Test Suite ==="

# Test harness with mock environment
TEST_TMP=$(mktemp -d)
trap 'rm -rf "$TEST_TMP"' EXIT

mkdir -p "$TEST_TMP/bin"
export PATH="$TEST_TMP/bin:$PATH"

# Helper to configure mock gh CLI
configure_mock_gh() {
  local releases_json="$1"
  local version_codes_map="$2" # associative or lookup file
  
  cat > "$TEST_TMP/releases.json" <<< "$releases_json"
  cat > "$TEST_TMP/codes.json" <<< "$version_codes_map"

  cat > "$TEST_TMP/bin/gh" << 'EOF'
#!/usr/bin/env bash
cmd="$1"
subcmd="$2"
shift 2

if [[ "$cmd" == "release" && "$subcmd" == "list" ]]; then
  if [[ -f "$(dirname "$0")/../fail_release_list" ]]; then
    echo "mock gh release list failed" >&2
    exit 1
  fi
  query=""
  while [[ $# -gt 0 ]]; do
    case "$1" in
      -q|--jq)
        query="$2"
        shift 2
        ;;
      *)
        shift
        ;;
    esac
  done
  if [[ -n "$query" ]]; then
    jq -r "$query" "$(dirname "$0")/../releases.json"
  else
    cat "$(dirname "$0")/../releases.json"
  fi
elif [[ "$cmd" == "release" && "$subcmd" == "download" ]]; then
  tag="$1"
  shift
  dest_dir=""
  while [[ $# -gt 0 ]]; do
    case "$1" in
      -D)
        dest_dir="$2"
        shift 2
        ;;
      *)
        shift
        ;;
    esac
  done
  if [[ -f "$(dirname "$0")/../fail_download" ]]; then
    exit 1
  fi
  raw=$(jq -r --arg t "$tag" '.[$t] // empty' "$(dirname "$0")/../codes.json")
  if [[ -n "$raw" && "$raw" != "null" ]]; then
    if [[ "$raw" =~ ^\{.*\}$ ]]; then
      echo "$raw" > "$dest_dir/version.json"
    else
      echo "{\"versionCode\": $raw, \"versionName\": \"${tag#v}\"}" > "$dest_dir/version.json"
    fi
    exit 0
  fi
  exit 1
elif [[ "$cmd" == "release" && "$subcmd" == "view" ]]; then
  tag="$1"
  shift
  query=""
  while [[ $# -gt 0 ]]; do
    case "$1" in
      -q|--jq)
        query="$2"
        shift 2
        ;;
      *)
        shift
        ;;
    esac
  done
  raw=$(jq -r --arg t "$tag" '.[$t] // empty' "$(dirname "$0")/../codes.json")
  if [[ -n "$raw" && "$raw" != "null" ]]; then
    view_json="{\"assets\":[{\"name\":\"version.json\"}]}"
  else
    view_json="{\"assets\":[]}"
  fi
  if [[ -n "$query" ]]; then
    jq -r "$query" <<< "$view_json"
  else
    echo "$view_json"
  fi
  exit 0
fi
EOF
  chmod +x "$TEST_TMP/bin/gh"
}

# Test 1: Standard stable patch release (v0.3.17 -> v0.3.18, code 70 -> 71)
echo "Test 1: Stable patch release from v0.3.17 (code 70)"
RELEASES='[{"tagName":"v0.3.17","isPrerelease":false}]'
CODES='{"v0.3.17": 70}'
configure_mock_gh "$RELEASES" "$CODES"

OUT=$(GITHUB_OUTPUT="$TEST_TMP/out.env" "$SCRIPT" --type release --bump patch)
source "$TEST_TMP/out.env"
assert_eq "Version Name" "0.3.18" "$version_name"
assert_eq "Version Code" "71" "$version_code"
assert_eq "Tag" "v0.3.18" "$tag"
assert_eq "Prerelease" "false" "$is_prerelease"

# Test 2: Minor bump (v0.3.17 -> v0.4.0, code 71)
echo "Test 2: Stable minor release from v0.3.17"
OUT=$(GITHUB_OUTPUT="$TEST_TMP/out.env" "$SCRIPT" --type release --bump minor)
source "$TEST_TMP/out.env"
assert_eq "Version Name" "0.4.0" "$version_name"
assert_eq "Version Code" "71" "$version_code"
assert_eq "Tag" "v0.4.0" "$tag"
assert_eq "Prerelease" "false" "$is_prerelease"

# Test 3: Major bump (v0.3.17 -> v1.0.0, code 71)
echo "Test 3: Stable major release from v0.3.17"
OUT=$(GITHUB_OUTPUT="$TEST_TMP/out.env" "$SCRIPT" --type release --bump major)
source "$TEST_TMP/out.env"
assert_eq "Version Name" "1.0.0" "$version_name"
assert_eq "Version Code" "71" "$version_code"
assert_eq "Tag" "v1.0.0" "$tag"
assert_eq "Prerelease" "false" "$is_prerelease"

# Test 4: First Nightly (v0.3.17 -> v0.3.18-nightly.1, code 71)
echo "Test 4: First nightly after v0.3.17"
OUT=$(GITHUB_OUTPUT="$TEST_TMP/out.env" "$SCRIPT" --type nightly --bump patch)
source "$TEST_TMP/out.env"
assert_eq "Version Name" "0.3.18-nightly.1" "$version_name"
assert_eq "Version Code" "71" "$version_code"
assert_eq "Tag" "v0.3.18-nightly.1" "$tag"
assert_eq "Prerelease" "true" "$is_prerelease"

# Test 5: Incremental Nightlies (.1, .2 -> next = .3) with monotonically increasing versionCode
echo "Test 5: Incremental nightlies (.1, .2 -> .3)"
RELEASES='[
  {"tagName":"v0.3.18-nightly.2","isPrerelease":true},
  {"tagName":"v0.3.18-nightly.1","isPrerelease":true},
  {"tagName":"v0.3.17","isPrerelease":false}
]'
CODES='{
  "v0.3.18-nightly.2": 72,
  "v0.3.18-nightly.1": 71,
  "v0.3.17": 70
}'
configure_mock_gh "$RELEASES" "$CODES"

OUT=$(GITHUB_OUTPUT="$TEST_TMP/out.env" "$SCRIPT" --type nightly --bump patch)
source "$TEST_TMP/out.env"
assert_eq "Version Name" "0.3.18-nightly.3" "$version_name"
assert_eq "Version Code" "73" "$version_code"
assert_eq "Tag" "v0.3.18-nightly.3" "$tag"
assert_eq "Prerelease" "true" "$is_prerelease"

# Test 6: Stable release following nightlies (uses max versionCode across nightlies + 1)
echo "Test 6: Stable release v0.3.18 following 3 nightlies (max code 73 -> 74)"
RELEASES='[
  {"tagName":"v0.3.18-nightly.3","isPrerelease":true},
  {"tagName":"v0.3.18-nightly.2","isPrerelease":true},
  {"tagName":"v0.3.18-nightly.1","isPrerelease":true},
  {"tagName":"v0.3.17","isPrerelease":false}
]'
CODES='{
  "v0.3.18-nightly.3": 73,
  "v0.3.18-nightly.2": 72,
  "v0.3.18-nightly.1": 71,
  "v0.3.17": 70
}'
configure_mock_gh "$RELEASES" "$CODES"

OUT=$(GITHUB_OUTPUT="$TEST_TMP/out.env" "$SCRIPT" --type release --bump patch)
source "$TEST_TMP/out.env"
assert_eq "Version Name" "0.3.18" "$version_name"
assert_eq "Version Code" "74" "$version_code"
assert_eq "Tag" "v0.3.18" "$tag"
assert_eq "Prerelease" "false" "$is_prerelease"

# Test 7: Nightly for next version cycle after v0.3.18 (v0.3.19-nightly.1, code 75)
echo "Test 7: Nightly for new version cycle after v0.3.18 (code 74 -> 75)"
RELEASES='[
  {"tagName":"v0.3.18","isPrerelease":false},
  {"tagName":"v0.3.18-nightly.3","isPrerelease":true},
  {"tagName":"v0.3.17","isPrerelease":false}
]'
CODES='{
  "v0.3.18": 74,
  "v0.3.18-nightly.3": 73,
  "v0.3.17": 70
}'
configure_mock_gh "$RELEASES" "$CODES"

OUT=$(GITHUB_OUTPUT="$TEST_TMP/out.env" "$SCRIPT" --type nightly --bump patch)
source "$TEST_TMP/out.env"
assert_eq "Version Name" "0.3.19-nightly.1" "$version_name"
assert_eq "Version Code" "75" "$version_code"
assert_eq "Tag" "v0.3.19-nightly.1" "$tag"
assert_eq "Prerelease" "true" "$is_prerelease"

# Test 8: Empty releases on GitHub (fresh repo fallback)
echo "Test 8: Fresh repository with no existing GitHub releases"
RELEASES='[]'
CODES='{}'
configure_mock_gh "$RELEASES" "$CODES"

OUT=$(GITHUB_OUTPUT="$TEST_TMP/out.env" "$SCRIPT" --type release --bump patch)
source "$TEST_TMP/out.env"
assert_eq "Version Name" "0.3.18" "$version_name"
assert_eq "Version Code" "71" "$version_code"
assert_eq "Tag" "v0.3.18" "$tag"
assert_eq "Prerelease" "false" "$is_prerelease"

# Test 9: Multi-digit and semver ordering (e.g. 0.2.9 vs 0.2.10 vs 0.2.50 vs 0.3.1)
echo "Test 9: Correct SemVer sorting with multi-digit parts"
RELEASES='[
  {"tagName":"v0.2.9","isPrerelease":false},
  {"tagName":"v0.2.10","isPrerelease":false},
  {"tagName":"v0.2.50","isPrerelease":false},
  {"tagName":"v0.3.1","isPrerelease":false},
  {"tagName":"v0.3.9","isPrerelease":false},
  {"tagName":"v0.3.17","isPrerelease":false}
]'
CODES='{"v0.3.17": 70}'
configure_mock_gh "$RELEASES" "$CODES"

OUT=$(GITHUB_OUTPUT="$TEST_TMP/out.env" "$SCRIPT" --type release --bump patch)
source "$TEST_TMP/out.env"
assert_eq "Version Name" "0.3.18" "$version_name"
assert_eq "Version Code" "71" "$version_code"

# Test 10: Nightly with minor bump
echo "Test 10: Nightly with minor bump"
OUT=$(GITHUB_OUTPUT="$TEST_TMP/out.env" "$SCRIPT" --type nightly --bump minor)
source "$TEST_TMP/out.env"
assert_eq "Version Name" "0.4.0-nightly.1" "$version_name"
assert_eq "Version Code" "71" "$version_code"
assert_eq "Tag" "v0.4.0-nightly.1" "$tag"

# Test 11: Orphan git tags without published releases are ignored
echo "Test 11: Orphan git tags without published releases are ignored"
RELEASES='[{"tagName":"v0.3.17","isPrerelease":false}]'
CODES='{"v0.3.17": 70}'
configure_mock_gh "$RELEASES" "$CODES"

# Create orphan tags that would previously cause version skipping
ORPHAN_REPO="$TEST_TMP/orphan-repo"
mkdir -p "$ORPHAN_REPO"
git -C "$ORPHAN_REPO" init -q
git -C "$ORPHAN_REPO" config user.email "test@example.com"
git -C "$ORPHAN_REPO" config user.name "Test"
echo "test" > "$ORPHAN_REPO/README.md"
git -C "$ORPHAN_REPO" add README.md
git -C "$ORPHAN_REPO" commit -q -m "init"
git -C "$ORPHAN_REPO" tag v0.3.18
git -C "$ORPHAN_REPO" tag v0.3.18-nightly.1

OUT=$(cd "$ORPHAN_REPO" && GITHUB_OUTPUT="$TEST_TMP/out.env" "$SCRIPT" --type release --bump patch)
source "$TEST_TMP/out.env"
assert_eq "Version Name" "0.3.18" "$version_name"
assert_eq "Version Code" "71" "$version_code"
assert_eq "Tag" "v0.3.18" "$tag"

# Test 12: Corrupt versionCode in version.json aborts with error
echo "Test 12: Corrupt versionCode in version.json aborts"
RELEASES='[{"tagName":"v0.3.17","isPrerelease":false}]'
CODES='{"v0.3.17": "{\"versionCode\":\"invalid\",\"versionName\":\"0.3.17\"}"}'
configure_mock_gh "$RELEASES" "$CODES"

set +e
OUT=$(GITHUB_OUTPUT="$TEST_TMP/out.env" "$SCRIPT" --type release --bump patch 2>&1)
EXIT_CODE=$?
set -e
assert_eq "Corrupt versionCode exit code" "1" "$EXIT_CODE"

# Test 13: Download failure when version.json asset exists aborts to prevent duplicate versionCode
echo "Test 13: Download failure with existing version.json asset aborts"
RELEASES='[{"tagName":"v0.3.17","isPrerelease":false}]'
CODES='{"v0.3.17": 70}'
configure_mock_gh "$RELEASES" "$CODES"
touch "$TEST_TMP/fail_download"

set +e
OUT=$(GITHUB_OUTPUT="$TEST_TMP/out.env" "$SCRIPT" --type release --bump patch 2>&1)
EXIT_CODE=$?
set -e
rm -f "$TEST_TMP/fail_download"
assert_eq "Download failure with asset exit code" "1" "$EXIT_CODE"

# Test 14: Legacy release without version.json asset falls back safely
echo "Test 14: Legacy release without version.json asset falls back safely"
RELEASES='[{"tagName":"v0.3.17","isPrerelease":false}]'
CODES='{}'
configure_mock_gh "$RELEASES" "$CODES"

OUT=$(GITHUB_OUTPUT="$TEST_TMP/out.env" "$SCRIPT" --type release --bump patch)
source "$TEST_TMP/out.env"
assert_eq "Version Name" "0.3.18" "$version_name"
assert_eq "Version Code" "71" "$version_code"
assert_eq "Tag" "v0.3.18" "$tag"

# Test 15: gh release list failure aborts to prevent duplicate versions
echo "Test 15: gh release list failure aborts"
RELEASES='[{"tagName":"v0.3.17","isPrerelease":false}]'
CODES='{"v0.3.17": 70}'
configure_mock_gh "$RELEASES" "$CODES"
touch "$TEST_TMP/fail_release_list"

set +e
OUT=$(GITHUB_OUTPUT="$TEST_TMP/out.env" "$SCRIPT" --type release --bump patch 2>&1)
EXIT_CODE=$?
set -e
rm -f "$TEST_TMP/fail_release_list"
assert_eq "Release list failure exit code" "1" "$EXIT_CODE"

echo ""
echo "=== Test Summary: $PASSED passed, $FAILED failed ==="
if [[ $FAILED -gt 0 ]]; then
  exit 1
fi
