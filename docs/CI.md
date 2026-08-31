# CI and release pipeline

Masroof uses GitHub Actions for continuous integration and automated slash-command-driven releases.

| Workflow | When | What |
|----------|------|------|
| **CI** (`ci.yml`) | Pull request → `main` | Runs `testDebugUnitTest`, `lintDebug`, `detekt` |
| **PR Slash Commands** (`pr-commands.yml`) | Comment on PR (`/release`, `/nightly`) | Authenticates commenter, checks PR merge status, dispatches Release |
| **Release** (`release.yml`) | Workflow dispatch or PR slash command | Resolves dynamic version, runs tests, builds signed APK, tags commit, publishes GitHub Release |

---

## Daily development workflow

1. **Feature branch**: Create branch and commit code changes. No manual version bumping in `app/build.gradle.kts`.
2. **Open PR**: Open a PR targeting `main`. **CI** runs tests and lint checks automatically.
3. **Review & Merge**: Review changes and merge the PR into `main`.

---

## Publishing builds after merge

After merging a PR, comment on the PR to publish:

### Want a test build?
Comment:
```text
/nightly
```
- Builds pre-release APK tagged `v<next-version>-nightly.<n>`.
- Monotonically increments global `versionCode`.
- Does not notify regular stable users.

### Ready for stable?
Comment:
```text
/release
```
- Or `/release patch` (e.g. `v0.3.17` $\rightarrow$ `v0.3.18`).
- For minor bumps: `/release minor` (e.g. `v0.3.17` $\rightarrow$ `v0.4.0`).
- For major bumps: `/release major` (e.g. `v0.3.17` $\rightarrow$ `v1.0.0`).
- Publishes official release with signed APK and `version.json`.
- Notifies users via in-app update checker.

---

## Safety and concurrency

- **Zero-code bumps**: Version names and version codes are dynamically resolved and passed via Gradle CLI (`-PappVersionName` and `-PappVersionCode`). No version commits are pushed back to the repo.
- **Unmerged PR protection**: `/release` and `/nightly` commands on open/unmerged PRs are rejected.
- **Serialized publishing**: All publishing jobs run under the `masroof-publish` concurrency group with `cancel-in-progress: false` to ensure no two concurrent builds can generate conflicting versions or version codes.
- **Tagging safety**: Git tags are created and pushed only **after** tests pass, the APK is signed, and verification succeeds.
- **Authorization**: Only repository owners, members, and write collaborators can trigger release workflows.
