# Masroof (مصروف)

Android app that turns Saudi bank SMS into a double-entry style personal ledger — locally on the device.

**Status:** `0.1.0-test` · Room schema **22** · minSdk 26 / targetSdk 34

## What it does

1. Reads inbox / incoming bank SMS (with user permission)
2. Matches senders you trained under **رسائل البنوك**
3. Extracts amounts and account/card last-4 from approved message patterns
4. Links transactions to your financial accounts via typed identifiers
5. Posts immutable journal entries for balances and monthly spending

Pipeline (bank-agnostic):

```text
SenderProfile → MessagePattern → Structured Fields
  → Typed Identifier → Financial Account → Financial Treatment
```

## Build

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

Open the project in Android Studio (Giraffe+ / AGP matching `gradle/libs.versions.toml`) or use the Gradle wrapper above.

## Privacy

- SMS stays on device; the app does not send banking SMS to a server by default
- Optional AI features are off unless you enable them in settings
- Never commit real SMS, OTPs, phone numbers, or account/card numbers — tests use anonymized fixtures only
- See `AGENTS.md` for data-safety rules (no destructive Room migrations, immutable posted journals)

## App map

| Area | Route / entry |
|------|----------------|
| Home / Operations / Accounts / More | Primary bottom nav |
| Import SMS | Import from Home / More |
| Train bank senders & patterns | المزيد → **رسائل البنوك** |
| Review ambiguous txs | Operations → Review |
| Theme | Settings → المظهر |

## Docs

| Doc | Purpose |
|-----|---------|
| [`docs/PROJECT_STATE.md`](docs/PROJECT_STATE.md) | Current versions and architecture |
| [`docs/DECISIONS.md`](docs/DECISIONS.md) | Architecture decision records |
| [`docs/ROADMAP.md`](docs/ROADMAP.md) | Phased delivery status |
| [`docs/DEVICE_CHECKLIST.md`](docs/DEVICE_CHECKLIST.md) | Physical-device validation before release |
| [`AGENTS.md`](AGENTS.md) | Contributor / agent coding rules |

## Still open

- Optional alternate device validation (see `docs/DEVICE_CHECKLIST.md` if useful)
- Optional cloud AI categorization remains opt-in / off by default
- On-device link assist uses local SMS heuristics (not a full on-device LLM)

## License

Private / unpublished unless you add a license file.
