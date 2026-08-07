# Project Instructions

## Project
Android Kotlin application that reads banking SMS messages and converts them
into financial transactions.

## Architecture
- Use Kotlin.
- Follow OOP and clean architecture principles.
- Keep responsibilities separated between parser, matcher, repository,
  domain service, and UI layers.
- Do not place business logic inside Activities, Fragments, or Composables.
- Prefer small focused classes over large manager or utility classes.

## Data Safety
- Never use destructive Room migrations.
- Never delete or recreate the production database.
- Preserve all existing accounts, transactions, journal entries,
  links, and posted financial records.
- Database migrations must be incremental and idempotent.
- Never change financial balances directly.
- Posted journal entries must remain immutable.

## SMS Parsing
- Never assume that every four-digit number is an amount.
- Amounts must only be extracted from recognized amount labels and context.
- Card numbers, account numbers, IBAN endings, dates, times,
  reference numbers, and balances must not be treated as transaction amounts.
- Keep bank-specific parsing separate from generic parsing.
- Add regression tests for every supported SMS example.

## Account Identification
- Prefer AccountIdentifierEntity for instrument identifiers:
  ACCOUNT_LAST4, CREDIT_CARD_LAST4, DEBIT_CARD_LAST4, IBAN_LAST4, WALLET_LAST4
- Sender identity belongs to SenderProfile + account↔sender many-to-many
- Do not store SMS senders as AccountIdentifierType rows
- Do not automatically create identifiers from SMS messages
- Ambiguous matches must require user confirmation
- Account-type compatibility rules must always be enforced
- Patterns never hard-link to a single FinancialAccount

## SMS Patterns
- Train senders under «رسائل البنوك» separately from account creation
- MessagePatternDefinition statuses: APPROVED / IGNORED / UNKNOWN / DEPRECATED
- PatternFieldDefinition stores labels → canonical fields only (never personal values)
- Known senders with unmatched messages must create UNKNOWN candidates (never silent drop)

## Development Process
- Inspect the existing implementation before making changes.
- Do not rewrite working modules unnecessarily.
- Remove obsolete code only after confirming that it is unused.
- Keep commits and changes focused on the requested scope.
- Before finishing:
  1. Run unit tests.
  2. Run Room migration tests.
  3. Run lint.
  4. Build the debug APK.
  5. Summarize changed files and remaining risks.

## Security and Privacy
- Never commit real SMS messages, OTPs, phone numbers,
  account numbers, card numbers, or personal financial data.
- Use anonymized test fixtures only.
- Never log full banking SMS content in production.
