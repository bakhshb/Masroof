# CI and release pipeline

Masroof uses two GitHub Actions workflows:

| Workflow | When | What |
|----------|------|------|
| **CI** | Pull request → `main` | `test`, `lint`, `detekt`, `assembleDebug` |
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

## Release did not publish after merge?

If **Release** failed on **Bump version** with `Could not find a free release tag after N bumps`:

- `main` was still on an old `appVersionName` while many tags already exist on [Releases](https://github.com/bakhshb/Masroof/releases) (common when the `chore: bump version` commit could not push to protected `main`).
- Fix: merge a PR that syncs `app/build.gradle.kts` to the next free version, or run **Actions → Release → Run workflow** after that sync.

If **Release** shows green but no new APK:

1. Open the workflow run → check **Skip if release already published**. If it says `Release vX.Y.Z already exists — skipping build`, the resolved tag was already published.
2. Allow **github-actions[bot]** to bypass branch rules for `main` (Rules → `main` → Bypass list), or the `chore: bump version` commit will fail even when the APK publishes.

## PR debug APK

Each PR uploads `masroof-debug-apk-pr-<number>` under **Actions → run → Artifacts** (unsigned debug build for quick testing).
