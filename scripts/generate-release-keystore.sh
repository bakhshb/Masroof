#!/usr/bin/env bash
# Generates a release keystore and local signing files (never commit these).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ -f release.keystore ]]; then
  echo "release.keystore already exists. Delete it first if you want a new key."
  exit 1
fi

STORE_PASS="$(openssl rand -base64 32 | tr -d '/+=' | head -c 24)"
KEY_PASS="$STORE_PASS"
ALIAS="masroof"

keytool -genkey -v \
  -keystore release.keystore \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -storepass "$STORE_PASS" \
  -keypass "$KEY_PASS" \
  -dname "CN=Masroof, OU=Mobile, O=Masroof, L=Riyadh, ST=Riyadh, C=SA"

cat > release-signing.env <<EOF
RELEASE_KEYSTORE_PASSWORD=$STORE_PASS
RELEASE_KEY_ALIAS=$ALIAS
RELEASE_KEY_PASSWORD=$KEY_PASS
EOF

cat > keystore.properties <<EOF
storeFile=release.keystore
storePassword=$STORE_PASS
keyAlias=$ALIAS
keyPassword=$KEY_PASS
EOF

echo "Created release.keystore, release-signing.env, and keystore.properties"
echo "Next: ./scripts/upload-release-secrets.sh"
