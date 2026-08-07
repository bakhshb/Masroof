# Masroof — Roadmap

Status legend: `[ ]` pending · `[~]` in progress · `[x]` done

## Phase 0 — Repository truth and cleanup map

- [x] Create PROJECT_STATE, DECISIONS, ROADMAP
- [x] Baseline `./gradlew clean test assembleDebug lintDebug`
- [x] Dead-code cleanup map documented

## Phase 1 — Navigation and active-flow integrity

- [x] Home Import / Review CTAs reach correct routes
- [x] Month next callback works
- [x] Accounts tab empty `onClose` fixed (hidden on primary tab)
- [x] Review reachable from Operations
- [x] Unreachable import-results / account-chooser routes removed

## Phase 2 — Typed identifiers + link-from-SMS

- [x] Room v15 migration present with schema export
- [x] Link-from-SMS via IdentifiersSection / AccountSmsBindingDialog
- [x] Legacy account lastFour/senderAliases removed from entity (v15)

## Phase 3 — Registered-only import dispositions

- [x] `ImportDispositionClassifier` exclusive priority
- [x] `readyCount` from READY dispositions
- [x] Commit skips duplicate / ignored dispositions
- [x] Disposition unit tests

## Phase 4 — Parser and identifier reliability

- [x] Spec SMS regressions retained
- [x] Arabic-Indic amount digits via `BankTextNormalizer` + regression test
- [x] Uncertain amount stays unparsed / review-safe

## Phase 5 — Account matching and review

- [x] Specific review reasons
- [x] Link / remember / ignore / re-analyze
- [x] Operations entry to review

## Phase 6 — Financial ledger and balance verification

- [x] `OpeningBalanceKind.AVAILABLE` on `AccountBalanceService`
- [x] Home monthly rollup via `monthMovement()`
- [x] Credit-card liability label scoped to cards
- [x] Month liquidity change uses first→last day of month

## Phase 7 — Home and Operations UX

- [x] Monthly-first Home metrics use month sum
- [x] Operations filters + import/review entry
- [x] Dead DesignComponents removed

## Phase 8 — Accounts and More UX

- [x] Accounts grouped by type
- [x] More hub curated with secondary diagnostics

## Phase 9 — Bank-specific parser expansion

- [x] Al Rajhi registry routing + credit-card fixture via bank parser
- [x] Generic fallback retained for other banks

## Phase 11 — SenderProfile architecture

- [x] Room v20 SenderProfile + account_sender_profiles + alias migration
- [x] Room v21 MessagePatternDefinition + PatternFieldDefinition
- [x] Value-token pattern discovery (`SmsStructureNormalizer`)
- [x] More → رسائل البنوك training UI
- [x] Account edit: select trained sender + manual typed identifiers
- [x] Import uses APPROVED patterns; UNKNOWN skip reason
- [x] Matcher prefers typed ID + sender cross-ref
- [x] SENDER_ALIAS deprecated with dual-read/write
- [x] Production import/training off legacy `sender_message_patterns` (table kept for migrations/tests)
- [x] Thin bank parsers classified as sender-routing shells only
- [ ] Drop legacy pattern table + learner code (after device soak)
- [ ] Physical-device checklist execution
