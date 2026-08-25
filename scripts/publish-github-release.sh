#!/usr/bin/env bash
# Builds a signed release APK and publishes a GitHub release with version.json.
set -euo pipefail

if [[ $# -lt 4 ]]; then
  echo "Usage: $0 <channel> <version_name> <version_code> <tag> [prerelease:true|false]"
  exit 1
fi

CHANNEL="$1"
VERSION_NAME="$2"
VERSION_CODE="$3"
TAG="$4"
PRERELEASE="${5:-false}"

TOOLING_ROOT="${TOOLING_ROOT:-$(cd "$(dirname "$0")/.." && pwd)}"
SOURCE_ROOT="${SOURCE_ROOT:-$TOOLING_ROOT}"
GRADLE_FILE="${SOURCE_ROOT}/app/build.gradle.kts"

# shellcheck source=scripts/gradle-version.sh
source "${TOOLING_ROOT}/scripts/gradle-version.sh"

if [[ ! -f "$GRADLE_FILE" ]]; then
  echo "Gradle file not found: ${GRADLE_FILE}"
  exit 1
fi

set_gradle_version_in "$GRADLE_FILE" "$VERSION_NAME" "$VERSION_CODE"
"${TOOLING_ROOT}/scripts/verify-release-version.sh" "$GRADLE_FILE" "$VERSION_NAME" "$VERSION_CODE"

cd "$SOURCE_ROOT"
./gradlew assembleRelease --no-daemon

APK_PATH="app/build/outputs/apk/release/app-release.apk"
"${TOOLING_ROOT}/scripts/verify-release-version.sh" "$GRADLE_FILE" "$VERSION_NAME" "$VERSION_CODE" "$APK_PATH"

RELEASE_APK="masroof-${VERSION_NAME}.apk"
cp "$APK_PATH" "${SOURCE_ROOT}/${RELEASE_APK}"
SHA256=$(sha256sum "${SOURCE_ROOT}/${RELEASE_APK}" | awk '{print $1}')

cat > "${SOURCE_ROOT}/version.json" <<EOF
{
  "versionCode": ${VERSION_CODE},
  "versionName": "${VERSION_NAME}",
  "apkFileName": "${RELEASE_APK}",
  "sha256": "${SHA256}",
  "channel": "${CHANNEL}",
  "releaseTag": "${TAG}"
}
EOF

cat "${SOURCE_ROOT}/version.json"

RELEASE_ARGS=(
  gh release create "$TAG"
  "${SOURCE_ROOT}/${RELEASE_APK}"
  "${SOURCE_ROOT}/version.json"
  --title "$TAG"
  --generate-release-notes
)
if [[ "$PRERELEASE" == "true" ]]; then
  RELEASE_ARGS+=(--prerelease)
else
  RELEASE_ARGS+=(--latest)
fi

"${RELEASE_ARGS[@]}"
