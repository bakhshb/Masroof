# Masroof — Project State

Last updated: 2026-08-07 (SENDER_ALIAS removed; Room v23)

## Versions

| Item | Value |
|------|-------|
| Branch | `main` |
| versionName | `0.1.0-test` |
| versionCode | `2` |
| Room schema | **23** (`MIGRATION_22_23` deletes SENDER_ALIAS after profile backfill) |
| compileSdk / targetSdk | 34 |
| minSdk | 26 |

## Architecture (active)

- Root NavHost: `MainActivity` → loading / onboarding / recovery / main
- Shell: `PrimaryNavigation` — الرئيسية / العمليات / الحسابات / المزيد
- **رسائل البنوك** (`settings/bank_messages`): train SenderProfile + approve/ignore patterns
- Import: `route/import_messages` · Review: `operations/review`
- Design: `ui/theme/*` (navy `#142B4A`, emerald `#087F6D`)
- **Sender identity:** `SenderProfile` + `account_sender_profiles` only
- **Instrument identity:** typed last4 identifiers (ACCOUNT / CREDIT / DEBIT / IBAN / WALLET)
- **Patterns:** `MessagePatternDefinition` + `PatternFieldDefinition` (labels only)
- Import: authorized senders from profiles ∪ institution mapping; APPROVED/DEPRECATED patterns extract fields; UNKNOWN never silently dropped
- Matching: typed identifiers + sender cross-ref narrowing; sender alone never auto-confirms
- Ledger: opening + POSTED journals via `AccountBalanceService`
- On-device link assist: local SMS heuristics (no cloud; MediaPipe LLM path disabled)

## Remaining risks

- Tokenization aggressiveness may over/under-merge patterns — tune with real fixtures
- Optional cloud AI categorization remains opt-in and off by default
