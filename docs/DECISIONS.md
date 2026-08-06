# Masroof — Architecture Decisions

## AD-001 — Typed account identifiers over legacy lastFourDigits

`AccountIdentifierEntity` with types (`ACCOUNT_LAST4`, `CREDIT_CARD_LAST4`, `DEBIT_CARD_LAST4`, `IBAN_LAST4`, `WALLET_LAST4`, `SENDER_ALIAS`) is the production source of truth. Schema v15 removes legacy columns from `financial_accounts` after idempotent backfill. Ambiguous matches require user confirmation. Identifiers are never auto-created from SMS without explicit user confirmation.

## AD-002 — Nested NavHosts

`MainActivity` owns startup/onboarding/recovery. `PrimaryNavigation` owns the four-tab shell and settings subgraph. This is intentional, not a duplicate app shell.

## AD-003 — Registered-accounts-only import by default

`SmsImportMode.REGISTERED_ACCOUNTS_ONLY` is the default. Discovery of unregistered senders is a separate mode that must not mix into normal import posting.

## AD-004 — Mutually exclusive import dispositions

Every scanned financial message receives exactly one final `ImportDisposition`. Pipeline statistics are separate from disposition counts. Overlapping boolean counters are not the product contract.

## AD-005 — No destructive Room migrations

Never use `fallbackToDestructiveMigration()`. All schema changes are explicit, incremental, and covered by migration tests. Posted journals are immutable; corrections use reversal + replacement.

## AD-006 — AI categorization optional and off by default

Deterministic rules and merchant memory run first. Optional AI never receives raw SMS, OTPs, or full identifiers. Networking requires explicit user configuration.

## AD-007 — Incremental package evolution

Target `core/domain/data/presentation` layering is aspirational. Do not big-bang rename packages. Move code only when actively touching a module.

## AD-008 — Manual composition root

`MasroofApplication` constructs repositories and services. Do not add Hilt/Dagger unless a verified need appears.

## AD-009 — Arabic RTL first

Compose forces RTL. Copy is Arabic-first. Product UI must not expose parser/database/accounting jargon.

## AD-010 — Balances from opening + POSTED journals

Balances derive from opening balances and balanced POSTED journals with effective dates/times. Raw SMS and mutable “current balance” fields are not the historical source of truth. Unused credit limit is borrowing capacity only — never liquidity or wealth.
