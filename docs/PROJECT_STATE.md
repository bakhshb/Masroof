# Masroof — Project State

Last updated: 2026-08-07 (legacy flat patterns retired from production import)

## Versions

| Item | Value |
|------|-------|
| Branch | `main` |
| versionName | `0.1.0-test` |
| versionCode | `2` |
| Room schema | **21** (`MIGRATION_19_20` SenderProfile; `MIGRATION_20_21` MessagePatternDefinition) |
| compileSdk / targetSdk | 34 |
| minSdk | 26 |

## Architecture (active)

- Root NavHost: `MainActivity` → loading / onboarding / recovery / main
- Shell: `PrimaryNavigation` — الرئيسية / العمليات / الحسابات / المزيد
- **رسائل البنوك** (`settings/bank_messages`): train SenderProfile + approve/ignore patterns
- Import: `route/import_messages` · Review: `operations/review`
- Design: `ui/theme/*` (navy `#142B4A`, emerald `#087F6D`)
- **Sender identity:** `SenderProfile` + `account_sender_profiles` (many-to-many). `SENDER_ALIAS` deprecated (dual-read / dual-write during migration)
- **Patterns:** `MessagePatternDefinition` + `PatternFieldDefinition` (labels only). Value-token discovery via `SmsStructureNormalizer` / `PatternDiscoveryService`
- Import: authorized senders from profiles ∪ legacy alias ∪ institution mapping; APPROVED/DEPRECATED patterns extract fields; UNKNOWN never silently dropped
- Matching: typed identifiers + sender cross-ref narrowing; sender alone never auto-confirms
- Ledger: opening + POSTED journals via `AccountBalanceService`

## Remaining risks

- Legacy `sender_message_patterns` table/code kept for migrations/tests only (not used in import)
- Thin bank parsers remain as sender-routing shells over GenericBankSmsParser
- `SENDER_ALIAS` enum remains for migration compatibility
- Physical-device validation required before production use
- Tokenization aggressiveness may over/under-merge patterns — tune with real fixtures
- Room schema export for v20 may be missing from repo (v19→v21 migration still tested in code)
