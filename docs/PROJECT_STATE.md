# Masroof — Project State

Last updated: 2026-08-06 (Phases 0–10 implemented)

## Versions

| Item | Value |
|------|-------|
| Branch | `main` (local WIP) |
| versionName | `0.1.0-test` |
| versionCode | `2` |
| Room schema | **15** (`MIGRATION_14_15` drops legacy `lastFourDigits` / `senderAliases`) |
| compileSdk / targetSdk | 34 |
| minSdk | 26 |

## Architecture (active)

- Root NavHost: `MainActivity` → loading / onboarding / recovery / main
- Shell: `PrimaryNavigation` — الرئيسية / العمليات / الحسابات / المزيد
- Import: `route/import_messages` · Review: `operations/review`
- Design: `ui/theme/*` (navy `#142B4A`, emerald `#087F6D`); dead `DesignComponents.kt` removed
- Parser: single `BankParserRegistry` + label-aware generic extraction
- Import: `SmsImportOrchestrator` with exclusive `ImportDisposition`
- Matching: typed `AccountMatcher`
- Ledger: opening + POSTED journals via `AccountBalanceService` (AVAILABLE opening supported)

## Phase completion snapshot

See [ROADMAP.md](ROADMAP.md). Device checks: [DEVICE_CHECKLIST.md](DEVICE_CHECKLIST.md).

## Remaining risks

- Two-sided transfer/payment linking still benefits from more user guidance in review
- Bank-specific parsers remain mostly sender-routing wrappers
- POSSIBLE_DUPLICATE similarity window not implemented
- Physical-device validation required before production use
