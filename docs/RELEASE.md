# Release and in-app updates

Masroof release APKs are built in **GitHub Actions** when you **merge to `main`** (see [CI.md](CI.md)). The app checks for updates using your personal GitHub token and installs APKs without Google Play.

## One-time setup

### 1. Create a release keystore (local, once)

```bash
keytool -genkey -v -keystore release.keystore -alias masroof \
  -keyalg RSA -keysize 2048 -validity 10000
```

Keep `release.keystore` and passwords safe. You need the **same keystore** for every release so in-app updates work.

### 2. Add GitHub Actions secrets

**Option A — automated (recommended)**

From the repo root, with [GitHub CLI](https://cli.github.com/) logged in as the repo owner:

```bash
./scripts/generate-release-keystore.sh   # skip if you already have release.keystore
./scripts/upload-release-secrets.sh
```

This creates `release.keystore`, `keystore.properties`, and uploads four secrets to GitHub. Those files are gitignored — do not commit them.

**Option B — manual**

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

### 4. Branch protection (recommended)

See [CI.md](CI.md) — require **CI** to pass before merging to `main`.

## Releasing a new version

1. Open a PR with your changes (version bump is **automatic** on merge).
2. Wait for **CI** to pass → merge to `main`.
3. **Release** workflow bumps version, builds APK, publishes GitHub Release, and commits the new version to `main`.

No manual `git tag` or version edit in Gradle is required.

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
- **versionCode** increases automatically on each merge to `main` (committed back by Release workflow).
