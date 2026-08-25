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

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

./gradlew assembleRelease \
  -PappVersionNameOverride="${VERSION_NAME}" \
  -PappVersionCodeOverride="${VERSION_CODE}" \
  --no-daemon

APK_PATH="app/build/outputs/apk/release/app-release.apk"
RELEASE_APK="masroof-${VERSION_NAME}.apk"
cp "$APK_PATH" "$RELEASE_APK"
SHA256=$(sha256sum "$RELEASE_APK" | awk '{print $1}')

cat > version.json <<EOF
{
  "versionCode": ${VERSION_CODE},
  "versionName": "${VERSION_NAME}",
  "apkFileName": "${RELEASE_APK}",
  "sha256": "${SHA256}",
  "channel": "${CHANNEL}",
  "releaseTag": "${TAG}"
}
EOF

cat version.json

RELEASE_ARGS=(gh release create "$TAG" "$RELEASE_APK" version.json --title "$TAG" --generate-release-notes)
if [[ "$PRERELEASE" == "true" ]]; then
  RELEASE_ARGS+=(--prerelease)
else
  RELEASE_ARGS+=(--latest)
fi

"${RELEASE_ARGS[@]}"
