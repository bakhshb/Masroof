# Project Instructions

## Project

Android Kotlin application that reads banking SMS messages and converts them into financial transactions.

The application must prioritize:

- financial data safety
- deterministic behavior
- explainable parsing
- reusable message patterns
- reliable account identification
- backward compatibility
- preservation of existing user data

Do not introduce unnecessary architectural complexity.

---

## Architecture

- Use Kotlin.
- Follow OOP and clean architecture principles.
- Keep responsibilities clearly separated between:
  - SMS parser
  - message normalization
  - pattern matching
  - account matching
  - repositories
  - domain services
  - persistence
  - UI
- Do not place business logic inside Activities, Fragments, or Composables.
- Prefer small focused classes over large manager, helper, or utility classes.
- Avoid duplicated business rules across different layers.
- Shared business rules should have one authoritative implementation.
- Prefer explicit domain models over unstructured maps or loosely typed data.
- Existing working architecture should be extended rather than unnecessarily rewritten.

---

## Data Safety

Financial data safety has the highest priority.

- Never use destructive Room migrations.
- Never delete or recreate the production database.
- Preserve all existing:
  - accounts
  - transactions
  - journal entries
  - links
  - patterns
  - sender profiles
  - account identifiers
  - posted financial records
- Database migrations must be incremental.
- Database migrations must be idempotent where applicable.
- Never silently discard existing production data.
- Never change financial balances directly.
- Posted journal entries must remain immutable.
- Historical financial transactions must not be silently modified.

If a schema change cannot safely preserve existing data, stop and explain the risk instead of implementing a destructive workaround.

---

## SMS Parsing

SMS parsing must be deterministic and explainable.

- Never assume that every numeric value is a transaction amount.
- Never assume that every four-digit number is an amount.

Amounts must only be extracted from:

- recognized amount labels
- recognized transaction contexts
- structurally validated fields

The following must never be treated as transaction amounts merely because they contain numbers:

- credit card last four digits
- debit card last four digits
- account last four digits
- IBAN endings
- wallet identifiers
- dates
- times
- transaction reference numbers
- authorization numbers
- available balances
- outstanding balances
- total due amounts unless specifically expected
- phone numbers
- OTP values

Keep bank-specific parsing separate from generic parsing.

Generic parsing may provide shared normalization and structural helpers, but bank-specific rules must remain explicit.

Every supported SMS format must have regression tests.

---

## Parsing Philosophy

Prefer this pipeline:

1. Normalize message.
2. Identify sender.
3. Identify message family.
4. Identify transaction type.
5. Identify structural labels.
6. Extract candidate fields.
7. Validate extracted fields.
8. Match message structure against known patterns.
9. Match financial instrument/account separately.
10. Produce transaction candidate.
11. Require confirmation where confidence is insufficient.

Do not solve parsing errors by adding unrelated fallback branches.

Prefer fixing the root cause.

Avoid parser logic that grows into deeply nested chains of:

```text
if
else if
else
fallback
fallback
fallback
```

If several message formats share structure, extract the shared behavior into focused reusable components.

---

## Message Normalization

Before pattern comparison, SMS text should be normalized consistently.

Normalization may include:

- trimming surrounding whitespace
- normalizing repeated spaces
- normalizing line endings
- normalizing Arabic and English punctuation where appropriate
- normalizing equivalent separators
- normalizing label formatting
- removing irrelevant formatting differences
- canonicalizing recognized dynamic values

Normalization must not remove information required to distinguish genuinely different transaction types.

Normalization must be deterministic.

The same input must always produce the same normalized representation.

---

## Account Identification

Use `AccountIdentifierEntity` for financial instrument identifiers.

Supported identifier types include:

- `ACCOUNT_LAST4`
- `CREDIT_CARD_LAST4`
- `DEBIT_CARD_LAST4`
- `IBAN_LAST4`
- `WALLET_LAST4`

Sender identity belongs to:

- `SenderProfile`
- account ↔ sender many-to-many relationships

Do not store SMS senders as `AccountIdentifierType` rows.

Do not automatically create financial account identifiers from incoming SMS messages.

If a previously unknown identifier is detected, the system may suggest adding it, but the user must explicitly confirm.

Ambiguous account matches must require user confirmation.

Account-type compatibility rules must always be enforced.

Examples:

- `CREDIT_CARD_LAST4` should not silently identify a debit account.
- `DEBIT_CARD_LAST4` should not silently identify a credit card.
- `ACCOUNT_LAST4` must match compatible account types.
- `IBAN_LAST4` must not be treated as a card identifier.

---

## Pattern and Account Separation

Pattern matching and account identification are separate concerns.

A pattern answers:

> What kind of message is this, and where are its fields?

Account identification answers:

> Which user's account, card, wallet, or financial instrument does this transaction belong to?

Patterns must never hard-link to a single `FinancialAccount`.

A single SMS pattern may be valid for many different accounts or cards.

Account identifiers must not be embedded into the identity of a message pattern.

---

## SMS Patterns

Train senders under رسائل البنوك separately from financial account creation.

`MessagePatternDefinition` statuses:

- `APPROVED`
- `IGNORED`
- `UNKNOWN`
- `DEPRECATED`

`PatternFieldDefinition` stores labels and their mapping to canonical fields.

It must not store personal transaction values.

Examples of canonical fields may include:

- amount
- currency
- merchant
- beneficiary
- transaction date
- transaction time
- account last four
- credit card last four
- debit card last four
- IBAN last four
- reference number
- available balance
- transaction type

Known senders with unmatched messages must produce `UNKNOWN` candidates.

They must not be silently dropped.

Do not automatically convert an `UNKNOWN` pattern into `APPROVED` without user approval unless explicitly required by product behavior.

---

## Pattern Identity and Deduplication

Patterns must be identified by semantic structure, not raw SMS text.

Never create a new `MessagePatternDefinition` merely because literal transaction values differ.

Before creating or importing a pattern:

1. normalize the message
2. determine message structure
3. generate a semantic pattern representation
4. search existing patterns
5. reuse an equivalent pattern when appropriate

Pattern identity must ignore changing transaction values such as:

- amounts
- dates
- times
- credit card numbers
- debit card numbers
- account numbers
- IBAN endings
- wallet identifiers
- transaction references
- authorization numbers
- balances
- merchant names when merchant is a captured field
- beneficiary names when beneficiary is a captured field

Pattern identity must preserve structural meaning such as:

- sender
- transaction family
- transaction type
- fixed labels
- canonical field roles
- required fields
- optional fields
- meaningful field order
- meaningful static text
- structural differences between transaction types

Two messages that use the same semantic structure and extract the same canonical fields should normally resolve to the same pattern.

Do not create duplicate `APPROVED` patterns.

Do not create duplicate `UNKNOWN` candidates representing the same semantic pattern.

If multiple existing patterns appear compatible, do not guess.

Return an ambiguity result for review.

Pattern equivalence must be deterministic.

Do not use:

- database IDs
- creation timestamps
- pattern names
- arbitrary ordering

when determining semantic equivalence.

---

## Pattern Signature

Every `MessagePatternDefinition` should have, or be able to generate, a canonical semantic signature.

The signature must describe message structure rather than transaction values.

For example, these messages:

```text
شراء عبر الانترنت
بطاقة ائتمانية: 7271
بمبلغ: 51.99 SAR
لدى: Keeta
```

and:

```text
شراء عبر الانترنت
بطاقة ائتمانية: 4812
بمبلغ: 127.50 SAR
لدى: Amazon
```

should normally resolve to the same semantic pattern.

Conceptually, their signature could resemble:

```text
ONLINE_PURCHASE|
CREDIT_CARD_LAST4|
AMOUNT|
CURRENCY|
MERCHANT
```

The exact implementation may differ.

The important requirement is:

Equivalent message structures must generate the same canonical semantic identity.

Changing:

- amount
- card digits
- merchant
- date
- time

must not create a new pattern if those values are already recognized variable fields.

---

## Pattern Matching Pipeline

Pattern processing should follow this order:

1. Identify the sender.
2. Normalize the SMS.
3. Detect transaction or message family.
4. Detect transaction type where possible.
5. Extract structural labels.
6. Detect candidate fields.
7. Generate semantic pattern signature.
8. Search existing `APPROVED` patterns.
9. Search existing `UNKNOWN` candidates if needed.
10. Reuse compatible existing patterns.
11. Create `UNKNOWN` only when no compatible pattern exists.
12. Never create a new `APPROVED` pattern automatically unless explicitly required.

Do not compare messages using raw string equality alone.

String similarity may be used to find candidate patterns.

Similarity alone must never decide semantic equivalence.

Final matching should use structural compatibility.

---

## Pattern Similarity

Similarity is useful for candidate discovery, not as the final source of truth.

Similarity may consider:

- normalized fixed text
- label similarity
- canonical field set
- field order
- transaction type
- sender
- required fields
- optional fields

Similarity must not be strongly influenced by changing transaction values.

Examples:

These should normally be considered equivalent:

```text
بطاقة ائتمانية: 7271
بمبلغ: 51.99 SAR
لدى: Keeta

بطاقة ائتمانية: 4812
بمبلغ: 200.00 SAR
لدى: Amazon
```

These should not automatically be considered equivalent merely because the wording is similar:

```text
شراء عبر الانترنت
```

and:

```text
عملية حوالة مالية صادرة
```

Transaction meaning takes priority over superficial text similarity.

---

## Pattern Candidate Deduplication

When multiple SMS messages produce the same semantic unknown structure:

Create one `UNKNOWN` pattern candidate rather than one candidate per SMS.

Repeated examples should strengthen or validate the candidate rather than duplicate it.

For example:

100 imported messages using one unknown banking format should normally produce:

```text
1 UNKNOWN pattern candidate
```

not:

```text
100 UNKNOWN pattern candidates
```

Candidate deduplication should work across:

- current import session
- previous import sessions
- historical `UNKNOWN` candidates
- already `APPROVED` patterns

---

## SMS Import

Historical SMS import must use the same parsing and pattern-matching pipeline as live incoming SMS.

Do not maintain a separate simplified parsing implementation for imports.

Before creating an `UNKNOWN` candidate during import:

1. identify sender
2. normalize message
3. generate semantic structure
4. generate semantic pattern signature
5. check existing `APPROVED` patterns
6. check existing `UNKNOWN` candidates
7. reuse or merge semantically equivalent patterns
8. create `UNKNOWN` only if no compatible pattern exists

Importing many messages with the same structure must not create many duplicate pattern candidates.

Re-importing the same SMS dataset should be as idempotent as reasonably possible.

Re-import must not unnecessarily multiply:

- transactions
- pattern candidates
- sender mappings
- account identifiers
- learned associations

If exact transaction deduplication already exists, preserve and extend it rather than creating a second competing mechanism.

---

## Sender Profiles

Sender detection must be separate from account identification.

One SMS sender may represent:

- multiple bank accounts
- multiple credit cards
- multiple debit cards
- wallets
- transfers
- different banking products

Therefore:

A sender must never automatically identify a single financial account.

Sender aliases should belong to `SenderProfile`.

Account ↔ sender relationships may be many-to-many.

Sender recognition may narrow candidate parsers and patterns but must not override financial instrument identification.

---

## Learned Linking

Manual user corrections may produce learned linking rules where appropriate.

Learned rules must be explicit and safe.

When a user manually links a transaction:

- optionally offer to remember the relationship
- do not silently create permanent financial identifiers
- do not create identifiers solely from uncertain parser output
- enforce account-type compatibility

If the SMS contains a discovered account/card identifier that is not currently saved:

The system may ask the user whether to add it.

Do not auto-add it.

---

## Ambiguity Handling

When the system cannot safely decide between multiple possibilities, it must not guess.

Examples:

- two accounts share the same identifier
- two patterns are structurally compatible
- transaction type is unclear
- amount extraction has conflicting candidates
- sender belongs to several accounts and no instrument identifier is available

Return an explicit ambiguous result.

The UI should allow user confirmation where appropriate.

A wrong financial transaction is worse than requiring user confirmation.

---

## Confidence

If confidence scoring exists:

Confidence must be explainable.

Confidence should be derived from meaningful evidence such as:

- sender match
- transaction family match
- recognized labels
- amount context
- pattern signature
- account identifier match
- canonical field compatibility

Do not generate arbitrary confidence numbers.

A high similarity score alone must not override structural contradictions.

---

## Regression Testing

Add regression tests for every supported SMS format.

At minimum, pattern tests must cover:

1. exact duplicate messages
2. same pattern with different amounts
3. same pattern with different dates
4. same pattern with different times
5. same pattern with different card/account identifiers
6. same pattern with different merchant names
7. same pattern with different beneficiary names
8. genuinely different transaction types
9. ambiguous pattern matches
10. unknown patterns
11. repeated `UNKNOWN` candidate detection
12. historical import
13. re-importing the same dataset
14. account compatibility rules
15. card digits not being interpreted as amounts
16. balances not being interpreted as transaction amounts
17. reference numbers not being interpreted as amounts

Every parser bug fixed from a real SMS example must receive a regression test using anonymized data.

---

## Test Data

Never commit real personal banking information.

Test fixtures must use anonymized or synthetic data.

Allowed example:

```text
بطاقة ائتمانية: 1234
بمبلغ: 51.99 SAR
لدى: SAMPLE MERCHANT
```

Not allowed:

- real card numbers
- real account numbers
- real IBANs
- actual customer names
- OTPs
- phone numbers
- real transaction references
- real personal financial messages

---

## AI / ML Usage

Do not solve deterministic parsing problems by introducing AI, LLM, embeddings, or ML dependencies unless explicitly requested.

Prefer first:

1. deterministic normalization
2. structural parsing
3. canonical fields
4. semantic signatures
5. compatibility rules
6. deterministic deduplication
7. controlled similarity matching
8. confidence scoring

Do not introduce:

- remote LLM APIs
- embedding APIs
- vector databases
- TensorFlow
- ONNX models
- local language models
- on-device ML models

without an explicit architectural decision.

The term "semantic pattern" in this project does not automatically imply AI.

Semantic structure should first be implemented deterministically.

AI may later be considered for unresolved or previously unseen messages, but it must not replace reliable deterministic handling of known banking formats.

---

## Repository Rules

Inspect the existing implementation before modifying it.

Before implementing a feature:

1. locate the current code path
2. identify existing entities and repositories
3. inspect existing migrations
4. inspect related tests
5. inspect any legacy implementation
6. determine whether similar functionality already exists

Do not create duplicate services or repositories when existing ones can be safely extended.

Do not rewrite working modules unnecessarily.

Remove obsolete code only after confirming that it is unused.

Keep changes focused on the requested scope.

Do not perform unrelated UI redesigns, architecture rewrites, or dependency migrations while solving a specific bug.

---

## Legacy Code

Legacy behavior must be removed gradually and deliberately.

Before replacing legacy fields such as old account identifier columns:

- identify all read paths
- identify all write paths
- migrate existing data safely
- update tests
- keep backward-compatible migration logic when necessary

Do not leave two competing authoritative implementations indefinitely.

When new infrastructure replaces old behavior, define which implementation is the source of truth.

---

## Room Database

Never use `fallbackToDestructiveMigration`.

Never delete the database to fix a migration failure.

Never reset user data.

Every schema version change must have an explicit migration.

Migrations must preserve user data.

Backfills must be safe to run more than once where practical.

Add migration tests for schema changes.

When introducing derived or deduplicated values:

Prefer migration/backfill logic that checks whether data already exists before inserting.

---

## Financial Integrity

Financial records require stricter rules than ordinary application data.

Do not silently:

- change transaction amounts
- change transaction direction
- move posted transactions between accounts
- delete journal entries
- alter historical balances
- recreate posted transactions
- duplicate transactions during import

Any operation that changes financial meaning must be explicit.

---

## UI Rules

UI should expose ambiguity rather than hide it.

Do not make a confident-looking UI decision when domain logic is uncertain.

Account identifier management should clearly show:

- identifier type
- identifier value
- active/inactive state
- linked account
- conflict state where relevant

User-facing Arabic should be clear and concise.

Do not expose internal implementation terminology unnecessarily.

---

## Logging

Never log full banking SMS content in production.

Never log:

- OTPs
- full account numbers
- full card numbers
- full IBANs
- personal financial transaction contents
- personal beneficiary information

When diagnostic logging is necessary:

- mask identifiers
- redact personal text
- log structural metadata instead

Prefer:

```text
Pattern match:
sender=SABB
family=PURCHASE
fields=[AMOUNT, MERCHANT, CREDIT_CARD_LAST4]
signature=...
```

instead of logging the raw SMS.

---

## Security and Privacy

Never commit:

- real SMS messages
- OTPs
- phone numbers
- account numbers
- card numbers
- IBANs
- personal financial data
- private API keys
- credentials

Use anonymized fixtures only.

Never transmit banking SMS content to external services unless explicitly requested and architecturally approved.

Prefer on-device processing for financial messages.

---

## Development Process

Before making changes:

1. Read relevant code.
2. Understand the existing behavior.
3. Find the source of truth.
4. Review existing tests.
5. Review related database migrations.
6. Identify regression risks.
7. Make the smallest coherent change that solves the root problem.

Before considering a task complete:

1. Run unit tests.
2. Run parser regression tests.
3. Run Room migration tests.
4. Run lint.
5. Build the debug APK.
6. Verify no destructive migration was introduced.
7. Verify no production financial data assumptions were broken.
8. Summarize changed files.
9. Summarize behavioral changes.
10. Summarize remaining risks.

Do not report a task as complete if tests or build fail.

If an unrelated existing test was already failing:

- clearly identify it
- distinguish it from failures introduced by the current change

New behavior must have tests before considering the work complete.

Prefer fixing root causes instead of hiding errors with fallback logic.

---

## Scope Discipline

Stay within the user's requested scope.

Do not introduce unrelated:

- architecture rewrites
- UI redesigns
- AI features
- reporting features
- budgeting features
- charts
- reconciliation features
- salary scheduling
- cloud services
- new dependencies

unless explicitly requested.

When a broader change appears necessary, explain why before implementing it.

---

## Investigation Before Fixing Bugs

For bugs involving duplicated patterns, parsing, imports, or account matching:

Do not immediately modify code.

First trace the complete execution path.

For pattern-related issues, inspect:

```text
SMS input
↓
sender recognition
↓
normalization
↓
message classification
↓
field extraction
↓
pattern signature
↓
pattern search
↓
pattern matching
↓
candidate creation
↓
candidate deduplication
↓
approval
↓
transaction creation
```

For imports also inspect:

```text
historical SMS
↓
import service
↓
parser
↓
pattern matcher
↓
transaction deduplication
↓
pattern candidate deduplication
↓
database writes
```

Identify exactly where duplicate state is introduced.

Do not patch symptoms at the UI layer if duplication originates in domain or persistence logic.

---

## Root Cause Requirement

When fixing a bug:

State the root cause before implementing the fix.

A useful root-cause explanation should identify:

- where the incorrect behavior starts
- why the current logic allows it
- which layer owns the fix
- why the proposed change prevents recurrence

Do not describe only the visible symptom.

---

## Pattern Deduplication Acceptance Criteria

Pattern deduplication is considered correct when:

### Case 1

Input:

```text
شراء عبر الانترنت
بطاقة ائتمانية: 1234
بمبلغ: 51.99 SAR
لدى: SAMPLE STORE A
```

and:

```text
شراء عبر الانترنت
بطاقة ائتمانية: 5678
بمبلغ: 100.00 SAR
لدى: SAMPLE STORE B
```

Expected:

Same semantic pattern when merchant/card/amount are captured fields.

### Case 2

Input:

```text
عملية حوالة مالية صادرة
خصمت من حساب: 3001
مبلغ العملية: 300.00 SAR
```

and:

```text
شراء عبر الانترنت
بطاقة ائتمانية: 3001
بمبلغ: 300.00 SAR
```

Expected:

Different semantic patterns even though some numeric values match.

### Case 3

Import 100 SMS messages matching one unknown structure.

Expected:

1 `UNKNOWN` semantic pattern candidate, not 100.

### Case 4

Import the same dataset again.

Expected:

No unnecessary duplicate:

- transactions
- pattern candidates
- identifiers
- sender mappings

---

## Definition of Done

A task is complete only when:

- implementation matches requested behavior
- existing user data remains safe
- no destructive migration exists
- relevant tests pass
- new behavior has regression tests
- debug APK builds successfully
- no unnecessary architectural rewrite occurred
- no unrelated scope was introduced
- changed files are summarized
- remaining limitations or risks are explicitly stated

For parser or pattern work, completion additionally requires:

- verified structural matching
- verified pattern deduplication
- verified amount extraction
- verified account identifier handling
- verified import behavior
- verified regression cases
