# Release and in-app updates

Masroof ships **nightly** builds automatically when you merge to `main`, and **stable** builds when you comment `/release` on a merged pull request. See [CI.md](CI.md) for the full pipeline.

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

### 4. Branch protection (recommended)

See [CI.md](CI.md) — require **CI** to pass before merging to `main`.

## Releasing a new stable version

1. Open a PR with your changes (version bump is **not** manual).
2. Wait for **CI** to pass → merge to `main`.
3. A **nightly** pre-release is published automatically (`v0.2.28-nightly-1`, etc.).
4. When you are ready to ship stable, comment **`/release`** on the merged PR (repo write access required).
5. The workflow builds a stable APK from that PR's merge commit, publishes it as the latest release, and bumps `main`.

To ship code from an older merged PR without reverting git history, comment **`/rollback`** on that PR instead.

## Update channels

| Channel | What you get |
|---------|----------------|
| **Stable** (default) | Stable releases only |
| **Nightly** | Stable and nightly builds — the app installs whichever has the higher `versionCode` |

Choose the channel in **Settings → Update channel**. The About screen shows your current channel under the version number.

## Updating the app on your phone

1. Open Masroof → **Settings → About**.
2. Ensure your GitHub token is saved.
3. Pick **Stable** or **Nightly** under **Settings → Update channel** if needed.
4. Tap **Check for updates** (or wait for an automatic check).
5. **Download update** → **Install update**.
6. If prompted, allow **Install unknown apps** for Masroof.

## Local release build (optional)

```bash
cp keystore.properties.example keystore.properties
# fill in keystore.properties
./gradlew assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`

Override version for local testing:

```bash
./gradlew assembleRelease \
  -PappVersionNameOverride=0.2.28-nightly-local \
  -PappVersionCodeOverride=99
```

## Notes

- **Debug APK → release APK**: different signing keys; uninstall the debug build first, then install release.
- **Private repo**: only devices with a valid read token can fetch updates.
- **`versionCode`** is global across stable and nightly; nightlies do not change Gradle on `main`.
