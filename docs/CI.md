# CI and release pipeline

Masroof uses two GitHub Actions workflows:

| Workflow | When | What |
|----------|------|------|
| **CI** | Pull request → `main` | `test`, `lint`, `assembleDebug` |
| **Release** | Push to `main` (after merge) | Signed `assembleRelease` + GitHub Release |

There is **no** CI run on every `main` push anymore — only the release job runs after merge.

## Flow

```text
Branch → open PR → CI runs (must pass)
       → merge PR → Release runs on main
       → if version new → APK on GitHub Releases
       → if version unchanged → release skipped (green, no new APK)
```

## What you do for each release

1. Open a PR with your code changes (**do not** edit version in `app/build.gradle.kts`).
2. Wait for **CI** to pass.
3. Merge PR to `main`.
4. **Release** automatically:
   - bumps `appVersionCode` (+1) and patch `appVersionName` (e.g. `0.2.1` → `0.2.2`)
   - builds signed APK and publishes GitHub Release
   - commits the version bump back to `main`
5. Update on your phone via Settings → About or wait for in-app update check.

Every merge to `main` produces a new version. Merge only when you want to ship.

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

## PR debug APK

Each PR uploads `masroof-debug-apk-pr-<number>` under **Actions → run → Artifacts** (unsigned debug build for quick testing).
