# Release and in-app updates

Masroof release APKs are built in **GitHub Actions** and published to **private GitHub Releases**. The app checks for updates using your personal GitHub token (read-only) and installs APKs without Google Play.

## One-time setup

### 1. Create a release keystore (local, once)

```bash
keytool -genkey -v -keystore release.keystore -alias masroof \
  -keyalg RSA -keysize 2048 -validity 10000
```

Keep `release.keystore` and passwords safe. You need the **same keystore** for every release so in-app updates work.

### 2. Add GitHub Actions secrets

In the repository: **Settings → Secrets and variables → Actions**, add:

| Secret | Value |
|--------|--------|
| `RELEASE_KEYSTORE_BASE64` | `base64 -w0 release.keystore` (Linux) or `base64 release.keystore` (macOS) |
| `RELEASE_KEYSTORE_PASSWORD` | Keystore password |
| `RELEASE_KEY_ALIAS` | `masroof` |
| `RELEASE_KEY_PASSWORD` | Key password |

### 3. Create a GitHub read-only token (for your phone)

1. GitHub → **Settings → Developer settings → Personal access tokens**
2. Create a **fine-grained token** with **Contents: Read-only** on the `Masroof` repository only.
3. On the phone: **Settings → About → App update** → paste token → **Save token**.

The token is stored in app-private storage on the device and is never committed to the repo.

## Releasing a new version (cloud)

1. Bump version in `app/build.gradle.kts`:
   - `appVersionCode` (must increase every release)
   - `appVersionName` (display version, e.g. `0.2.1`)
2. Commit and push to `main`.
3. Tag and push:

```bash
git tag v0.2.1
git push origin v0.2.1
```

4. GitHub Actions workflow **Release** runs on GitHub’s servers:
   - Builds signed `assembleRelease`
   - Generates `version.json` (version + SHA-256)
   - Creates a GitHub Release with `masroof-<version>.apk` and `version.json`

You can also run the workflow manually from **Actions → Release → Run workflow**.

## Updating the app on your phone

1. Open Masroof → **Settings → About**.
2. Ensure your GitHub token is saved.
3. Tap **Check for updates** (or wait for an automatic check on first launch after onboarding).
4. **Download update** → **Install update**.
5. If prompted, allow **Install unknown apps** for Masroof.

## Local release build (optional)

```bash
cp keystore.properties.example keystore.properties
# fill in keystore.properties
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

## Notes

- **Debug APK → release APK**: different signing keys; uninstall the debug build first, then install release.
- **Private repo**: only devices with a valid read token can fetch updates.
- **versionCode** in Gradle must match what CI publishes; the workflow reads it from `app/build.gradle.kts`.
