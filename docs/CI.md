# CI and release pipeline

Masroof uses GitHub Actions for continuous integration and automated slash-command-driven releases.

| Workflow | When | What |
|----------|------|------|
| **CI** (`ci.yml`) | Pull request or push → `main` | Runs `testDebugUnitTest`, `lintDebug`, `detekt` |
| **PR Slash Commands** (`pr-commands.yml`) | Comment on PR (`/release`, `/nightly`) | Authenticates commenter, checks PR merge status, dispatches Release |
| **Release** (`release.yml`) | Workflow dispatch or PR slash command | Verifies green CI on target commit, builds signed APK, tags commit, publishes GitHub Release |

---

## Daily development workflow

1. **Feature branch**: Create branch and commit code changes. No manual version bumping in `app/build.gradle.kts`.
2. **Open PR**: Open a PR targeting `main`. **CI** runs tests and lint checks automatically.
3. **Review & Merge**: Review changes and merge the PR into `main`. **CI runs again on `main`** after merge.
4. **Publish**: After **main CI is green**, comment `/nightly` or `/release` on the merged PR.

---

## Publishing builds after merge

After merging a PR, wait for the **main** CI check (`build-lint-test`) to succeed, then comment on the merged PR to publish. Release builds **do not re-run** the full test suite; they verify the target commit already has green CI, then build and sign the APK.

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
- **CI gate before publish**: Release waits for the target commit's `build-lint-test` check to finish (up to 40 minutes), then verifies success within 7 days. If main CI is still running right after merge, release queues until CI completes instead of failing immediately.
- **Serialized publishing**: All publishing jobs run under the `masroof-publish` concurrency group with `cancel-in-progress: false` to ensure no two concurrent builds can generate conflicting versions or version codes.
- **Tagging safety**: Git tags are created and pushed only **after** the APK is signed and verification succeeds.
- **Authorization**: Only repository owners, members, and write collaborators can trigger release workflows.
- **Emergency manual dispatch**: Workflow dispatch can set `skip_ci_check` to bypass the CI gate (use only when necessary).
