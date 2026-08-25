# CI and release pipeline

Masroof uses three GitHub Actions workflows for shipping:

| Workflow | When | What |
|----------|------|------|
| **CI** | Pull request → `main` | `test`, `lint`, `assembleDebug` |
| **Nightly** | Push to `main` (after merge) | Signed nightly pre-release APK |
| **Release command** | `/release` or `/rollback` on a merged PR | Stable APK from that PR's merge commit |
| **Release** | Manual dispatch or `v*` tag | Stable APK from current `main` (fallback) |

## Flow

```text
Branch → open PR → CI runs (must pass)
       → merge PR → Nightly runs on main
       → comment /release on merged PR → stable release from that merge commit
       → comment /rollback on older merged PR → stable rollback APK (higher versionCode)
```

Merges to `main` no longer publish stable releases automatically. Stable releases are intentional: comment `/release` on the merged PR when you want to ship.

## Update channels in the app

| Channel | Updates offered |
|---------|-----------------|
| **Stable** | Stable releases only (`releases/latest/download/version.json`) |
| **Nightly** | Rolling `nightly` pre-release plus latest stable — highest `versionCode` wins |

Users choose the channel in **Settings → Update channel** (default: Stable). The About screen shows the active channel under the app version.

## Versioning

- One global `versionCode` across stable and nightly builds (max existing + 1).
- `app/build.gradle.kts` on `main` tracks the **last stable** `versionName` and `versionCode`.
- Nightly builds inject `0.2.28-nightly-1` style names at build time; they are not committed to Gradle.
- Each release ships `version.json` with `"channel": "stable"` or `"nightly"` plus `releaseTag`.
- Nightly GitHub releases are **pre-releases**; stable releases are marked **latest**.
- Each nightly also refreshes a rolling **`nightly`** pre-release used by the in-app updater.
- Old immutable `v*-nightly-*` pre-releases are pruned (last 10 kept).

## What you do for each change

1. Open a PR with your code changes (**do not** edit version in `app/build.gradle.kts`).
2. Wait for **CI** to pass.
3. Merge PR to `main` → **Nightly** publishes `v{stable}-nightly-{N}`.
4. When ready to ship stable, comment `/release` on the merged PR (write access required).
5. Update on your phone via **Settings → About** (pick channel first if you want nightlies).

## `/release` and `/rollback`

Only users with write access (`OWNER`, `MEMBER`, or `COLLABORATOR`) can run these commands on a **merged** pull request:

| Command | Result |
|---------|--------|
| `/release` | Stable APK built from that PR's merge commit; bumps `main` Gradle version |
| `/rollback` | Stable APK rebuilt from an older merged PR's commit with a new higher `versionCode` (does not revert `main` git history) |

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

## Manual stable release

**Actions → Release → Run workflow** builds a stable release from current `main` if you need to retry without a PR comment.

Pushing a `v*` tag still triggers **Release** (optional).

## Release did not publish?

If **Nightly** or **Release command** shows green but no new APK:

1. Open the workflow run → check **Skip if release already published**.
2. For `/release`, ensure the PR is merged and you have write access.
3. Allow **github-actions[bot]** to bypass branch rules for `main` so `chore: bump version` commits can push after `/release`.

## PR debug APK

Each PR uploads `masroof-debug-apk-pr-<number>` under **Actions → run → Artifacts** (unsigned debug build for quick testing).
