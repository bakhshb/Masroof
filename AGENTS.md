# AGENTS.md

## Cursor Cloud specific instructions

Masroof is a **local-first Android personal-finance app** (Kotlin, Jetpack Compose,
Room). It reads bank SMS on-device and parses them into a financial ledger. There is
**no backend, database server, or docker-compose** — the only "services" are the
Gradle build and (optionally) an Android device/emulator.

### Toolchain (already provisioned in the VM snapshot)
- JDK 17 at `/usr/lib/jvm/java-17-openjdk-amd64` (the project requires 17; the base
  image also has JDK 21 which is not used here).
- Android SDK at `$HOME/android-sdk` (cmdline-tools, platform-tools, `platforms;android-34`,
  `build-tools;34.0.0`, plus `emulator` + system images).
- `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`, and `PATH` are exported in
  `~/.bashrc`, so a normal login shell can run `./gradlew`, `adb`, `sdkmanager`, etc.
- `local.properties` (gitignored) holds `sdk.dir=$HOME/android-sdk`. The startup update
  script recreates it if missing.

### Build / test / lint / run commands
Run from the repo root (`:app` is the only module):
- Build (dev): `./gradlew assembleDebug`
- Unit tests (JVM, no device): `./gradlew test` (or `./gradlew :app:testDebugUnitTest`)
- Lint: `./gradlew lintDebug` (HTML report at `app/build/reports/lint-results-debug.html`)
- Instrumentation/Compose UI tests: `./gradlew connectedAndroidTest` — **requires a
  booted device/emulator (see caveat below); cannot run headless here.**

### Non-obvious caveats
- **The Android emulator does NOT boot in this Cloud Agent VM.** `/dev/kvm` exists and
  `sudo chmod 666 /dev/kvm` is required before launching, but even then the guest vCPU
  never advances — every attempt hangs right after the log line
  `Activated packet streamer for bluetooth emulation` with the QEMU process idle at ~0%
  CPU. This reproduces across windowed/headless mode, both the `google_apis` and AOSP
  `default` x86_64 system images, `-wipe-data`, and with Bluetooth/WiFi/netsim/modem
  features disabled. Root cause is a nested-virtualization limitation of the host, not a
  project or config issue. **Do not burn time trying to boot an emulator** — verify
  Android runtime behaviour (SMS pipeline, Compose UI, Room migrations) with the JVM
  unit-test suite instead, or ask the user to run instrumentation tests on their own
  device/emulator.
- **7 pre-existing unit-test failures are expected** and are NOT environment problems:
  `ui.senders.BankMessagesNavigationTest`, `ui.senders.CandidatePatternWorkflowTest`,
  and `ui.senders.ImportExecutionResultTest` read app source files via a hardcoded
  absolute path (`/home/debian/projects/Masroof/...`) and throw `FileNotFoundException`
  on any other machine. The other ~967 tests pass. Do not "fix" these as part of
  unrelated work.
- Core functionality (raw bank SMS → structured `ParsedTransaction`) is pure-JVM and can
  be exercised without Android: the production entry point is
  `com.baraa.masroof.sms.TemplateResolutionService.resolve(...)`, and
  `sms.RealJaziraCorpusLifecycleTest` runs the full discover→approve→resolve lifecycle on
  the real Bank AlJazira corpus (`app/src/test/resources/sms_corpus/jazira_real_corpus.json`).
- Room schemas are exported to `app/schemas/` (version-controlled) for migration tests.
- First `./gradlew` build downloads Gradle 8.7 + all AndroidX/Compose/Room deps from
  Maven Central + Google; these are cached in `~/.gradle` in the snapshot.
