# CI and release pipeline

Masroof uses two GitHub Actions workflows:

| Workflow | When | What |
|----------|------|------|
| **CI** | Pull request → `main` | `testDebugUnitTest`, `lintDebug`, `detekt` |
| **Release** | Push to `main` (after merge) | Signed `assembleRelease` + GitHub Release |

There is **no** CI run on every `main` push anymore — only the release job runs after merge.

## Flow

```text
Branch → open PR → CI runs (must pass)
       → merge PR → Release runs on main
       → if version bumped for a new release → APK on GitHub Releases
       → if version unchanged → release skipped (green, no new APK)
```

## Day-to-day development

1. Open feature PRs with code changes only — **no version bump required**.
2. Wait for **CI** to pass → merge to `main`.
3. **Release** runs and finishes **green** with no new APK when the version in `app/build.gradle.kts` is unchanged.

Merge as many feature PRs as you like before shipping.

## Shipping a new version

When you are ready to publish an APK:

1. Bump **`appVersionName`** and **`appVersionCode`** in `app/build.gradle.kts` (or run `./scripts/bump-version.sh`).
2. Open a PR with the version bump (alone or with final changes).
3. Wait for **CI** to pass → merge to `main`.
4. **Release** builds the signed APK and publishes a GitHub Release.

Update on your phone via Settings → About or wait for in-app update check.

## What you do once (already done if you followed RELEASE.md)

| Task | Status |
|------|--------|
| Generate release keystore | `./scripts/generate-release-keystore.sh` |
| Upload keystore secrets to GitHub | `./scripts/upload-release-secrets.sh` |
| GitHub PAT on phone for updates | Settings → About → App update |

## Branch protection (recommended, manual in GitHub)

Repo → **Settings → Branches → Add rule** for `main`:

- Require a pull request before merging
- Require status check: **CI** / `build-lint-test`

Then merges are blocked until tests pass.

## Manual release

**Actions → Release → Run workflow** still works if you need to retry without a new merge.

Pushing a `v*` tag still triggers Release (optional; not required for normal flow).

## Release troubleshooting

| Symptom | Meaning | Fix |
|---------|---------|-----|
| Release **green**, no new APK, summary says “already published” | Version unchanged — expected after feature merges | Bump version when ready to ship |
| Release **red**, “versionCode … not greater than latest published” | New `versionName` but `versionCode` was not bumped | Bump both in `app/build.gradle.kts` |
| Release **red** on keystore / build step | Signing or compile failure | Check Actions logs and secrets |

## PR artifacts

PR CI does not build or upload a debug APK. Installable builds come from **Release** after merge to `main`, or run **Actions → Release → Run workflow** manually.
