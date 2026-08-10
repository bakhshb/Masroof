# Masroof v2 — Domain Specification

## 1. Purpose

This document defines the financial meaning of Masroof.

Parsing tells Masroof what a bank message says.

The domain layer decides what that information means financially.

Bank wording must never directly define the user's financial state.

---

## 2. Core Principle

> SMS messages are evidence about financial events. They are not automatically the final financial transactions.

A final `FinancialTransaction` is created only after:

1. parsing
2. validation
3. ownership resolution
4. related-event matching when required

---

## 3. Main Domain Objects

## 3.1 RawSms

Represents the original message received from Android.

Suggested fields:

```kotlin
data class RawSms(
    val id: String,
    val sender: String,
    val body: String,
    val receivedAt: Instant,
    val deviceMessageId: String?,
    val bodyHash: String
)
```

Rules:

- immutable
- never overwritten by parser corrections
- used for traceability
- duplicate-safe

---

## 3.2 Bank

Represents a financial institution recognized by Masroof.

Initial:

```text
BANK_ALJAZIRA
```

Future examples:

```text
D360
SNB
STC_BANK
ALRAJHI
UNKNOWN
```

---

## 3.3 FinancialContainer

Generic concept representing where money or debt is held.

Examples:

- bank account
- savings account
- wallet
- debit card backing account
- credit card
- investment account

Suggested type:

```kotlin
sealed interface FinancialContainer
```

---

## 3.4 Account

Suggested fields:

```kotlin
data class Account(
    val id: String,
    val bank: Bank,
    val maskedNumber: String?,
    val displayName: String?,
    val ownership: OwnershipStatus,
    val type: AccountType
)
```

---

## 3.5 Card

Suggested fields:

```kotlin
data class Card(
    val id: String,
    val bank: Bank,
    val last4: String?,
    val displayName: String?,
    val ownership: OwnershipStatus,
    val type: CardType,
    val linkedAccountId: String?
)
```

---

## 3.6 OwnershipStatus

```kotlin
enum class OwnershipStatus {
    OWNED,
    EXTERNAL,
    UNKNOWN
}
```

### Meaning

`OWNED`
: belongs to the user.

`EXTERNAL`
: known not to belong to the user.

`UNKNOWN`
: not yet resolved.

Ownership must not be inferred only from bank wording.

---

## 3.7 ParsedEvent

A structured interpretation of a single SMS.

A `ParsedEvent` is not yet a final transaction.

Suggested fields:

```kotlin
data class ParsedEvent(
    val id: String,
    val rawSmsId: String,
    val bank: Bank,
    val messageFamily: MessageFamily,
    val direction: MoneyDirection?,
    val amount: Money?,
    val currency: Currency?,
    val sourceAccountRef: AccountReference?,
    val destinationAccountRef: AccountReference?,
    val cardRef: CardReference?,
    val merchant: String?,
    val counterparty: String?,
    val occurredAt: Instant?,
    val bankNetworkType: BankNetworkType?,
    val confidence: Confidence,
    val parseStatus: ParseStatus
)
```

---

## 3.8 MessageFamily

Initial conceptual families:

```kotlin
enum class MessageFamily {
    CARD_PURCHASE,
    POS_PURCHASE,
    ONLINE_PURCHASE,
    TRANSFER_IN,
    TRANSFER_OUT,
    BILL_PAYMENT,
    REFUND,
    CASH_WITHDRAWAL,
    CREDIT_CARD_PAYMENT,
    FEE,
    BALANCE_NOTICE,
    OTP,
    NON_FINANCIAL,
    UNKNOWN
}
```

Note:

`POS_PURCHASE` and `ONLINE_PURCHASE` may later become channels under `CARD_PURCHASE`.

Do not overfit the domain to the current SMS wording.

---

## 3.9 MoneyDirection

```kotlin
enum class MoneyDirection {
    INCOMING,
    OUTGOING,
    NEUTRAL,
    UNKNOWN
}
```

Direction describes the movement relative to the referenced account/card.

It does not define income or expense.

---

## 3.10 BankNetworkType

This represents how the bank describes the transfer route.

```kotlin
enum class BankNetworkType {
    INTRA_BANK,
    INTER_BANK,
    UNKNOWN
}
```

Important:

`INTRA_BANK` means "inside the same bank".

It does NOT mean "between accounts owned by the user".

---

## 3.11 TransferOwnershipType

```kotlin
enum class TransferOwnershipType {
    SELF_TRANSFER,
    EXTERNAL_INCOMING,
    EXTERNAL_OUTGOING,
    UNKNOWN
}
```

This represents the actual relationship to the user.

---

## 3.12 FinancialTransaction

Represents the real-world financial result after reconciliation.

Suggested conceptual model:

```kotlin
data class FinancialTransaction(
    val id: String,
    val type: FinancialTransactionType,
    val amount: Money,
    val occurredAt: Instant,
    val sourceContainerId: String?,
    val destinationContainerId: String?,
    val merchant: String?,
    val counterparty: String?,
    val categoryId: String?,
    val linkedParsedEventIds: List<String>,
    val status: TransactionStatus
)
```

---

## 3.13 FinancialTransactionType

```kotlin
enum class FinancialTransactionType {
    EXPENSE,
    INCOME,
    SELF_TRANSFER,
    EXTERNAL_TRANSFER_IN,
    EXTERNAL_TRANSFER_OUT,
    CREDIT_CARD_PAYMENT,
    REFUND,
    CASH_WITHDRAWAL,
    FEE,
    ADJUSTMENT,
    UNKNOWN
}
```

---

## 4. Financial Rules

## Rule D-001 — Owned account to owned account

If:

```text
source.owner = OWNED
destination.owner = OWNED
```

then:

```text
SELF_TRANSFER
```

Financial effect:

```text
Expense = 0
Income = 0
Net worth delta = 0
```

---

## Rule D-002 — Same-bank does not imply self-transfer

Example:

```text
Bank AlJazira wife's account
    ↓
Bank AlJazira user's account
```

Bank wording may be:

```text
incoming internal transfer
```

Masroof result:

```text
BankNetworkType = INTRA_BANK
TransferOwnershipType = EXTERNAL_INCOMING
```

It is not a self-transfer.

---

## Rule D-003 — Cross-bank can still be self-transfer

Example:

```text
User Bank AlJazira → User D360
```

Result:

```text
BankNetworkType = INTER_BANK
TransferOwnershipType = SELF_TRANSFER
```

No expense and no income.

---

## Rule D-004 — Incoming transfer is not automatically income

Incoming money must initially be represented as:

```text
EXTERNAL_TRANSFER_IN
```

unless a stronger rule identifies it as:

- salary
- refund
- reimbursement
- gift
- other income

---

## Rule D-005 — Outgoing transfer is not automatically expense

An outgoing transfer may be:

- self-transfer
- payment to another person
- transfer to investment account
- card payment
- loan repayment
- actual expense

The destination and related events must be considered.

---

## Rule D-006 — Credit card purchase

A credit card purchase creates an expense at purchase time.

Conceptually:

```text
Expense increases
Credit card liability increases
```

---

## Rule D-007 — Credit card payment

Paying the card bill is not another expense.

Conceptually:

```text
Cash decreases
Credit card liability decreases
Expense delta = 0
```

---

## Rule D-008 — Refund

A refund should offset or reverse the economic effect of a previous purchase when a match is available.

It should not be treated as ordinary salary/income.

---

## Rule D-009 — Multiple messages may describe one transaction

Example:

```text
Event A: AlJazira outgoing 100
Event B: D360 incoming 100
```

If matching confirms both belong to the user:

```text
1 FinancialTransaction
type = SELF_TRANSFER
amount = 100
linkedEvents = [A, B]
```

---

## Rule D-010 — Unknown ownership blocks self-transfer classification

If either side is unresolved:

```text
ownership = UNKNOWN
```

Masroof must not claim `SELF_TRANSFER` solely because:

- names look similar
- the transfer is "internal"
- amounts match

It may use confidence-based matching, but uncertain cases must remain unresolved or enter review.

---

## 5. Transfer Matching

A transfer matcher should compare candidate events using evidence such as:

- same amount
- compatible currency
- timestamps within configured window
- source/destination account references
- known account ownership
- bank pair
- counterparty name
- reference number
- transaction identifier
- direction compatibility

Matching should produce a confidence/result, not only `true/false`.

Suggested conceptual result:

```kotlin
sealed interface MatchResult {
    data class Confirmed(...)
    data class Probable(...)
    data class NoMatch(...)
    data class NeedsReview(...)
}
```

---

## 6. Confidence

Suggested:

```kotlin
data class Confidence(
    val score: Double,
    val reasons: List<String>
)
```

Guidance:

```text
High confidence     → automatic
Medium confidence   → may remain pending
Low confidence      → review
```

Do not use a numeric threshold without tests.

---

## 7. Review State

Suggested:

```kotlin
enum class ReviewStatus {
    NOT_REQUIRED,
    REQUIRED,
    RESOLVED
}
```

User correction should not modify `RawSms`.

Instead store a separate correction/resolution record.

---

## 8. Correction Model

Suggested conceptual structure:

```kotlin
data class UserCorrection(
    val id: String,
    val targetEventId: String,
    val correctedType: MessageFamily?,
    val correctedAmount: Money?,
    val correctedMerchant: String?,
    val correctedOwnership: OwnershipStatus?,
    val correctedCounterparty: String?,
    val createdAt: Instant
)
```

Corrections are part of future learning/automation.

---

## 9. Account Identity

Do not equate an account only by the last four digits globally.

An account identity may require:

```text
Bank + masked account number + contextual identifiers
```

Same for cards.

Example:

```text
BANK_ALJAZIRA + ****3001
```

---

## 10. Merchant and Counterparty

`merchant`
: commercial entity where a purchase happened.

`counterparty`
: person or account party in a transfer.

They must remain separate.

---

## 11. Expense vs Cash Movement

Masroof must distinguish:

```text
expense
```

from:

```text
cash outflow
```

They are not the same.

Examples:

| Event | Cash Outflow | Expense |
|---|---:|---:|
| Grocery purchase from debit account | Yes | Yes |
| Credit card purchase | No immediate bank cash outflow | Yes |
| Pay credit card balance | Yes | No |
| Transfer AlJazira → D360 | Yes from one account | No |
| Transfer to wife as spending | Yes | Possibly yes, depending on classification |
| Deposit to owned investment account | Yes from bank | No |

---

## 12. Income vs Cash Inflow

Likewise:

| Event | Cash Inflow | Income |
|---|---:|---:|
| Salary | Yes | Yes |
| Refund | Yes | No ordinary income |
| Self-transfer from another owned account | Yes | No |
| Wife sends reimbursement | Yes | Usually reimbursement, not salary |
| Gift | Yes | Depends on reporting choice |

---

## 13. Domain Boundaries

### Parsing layer owns
- message wording
- bank aliases
- regex/token logic
- message-family detection
- field extraction

### Domain layer owns
- ownership
- self-transfer meaning
- income vs transfer
- expense meaning
- liability settlement
- transaction matching
- final financial state

### UI owns
- display
- user confirmation
- review interactions

The UI must not contain financial business logic.

---

## 14. Required Domain Tests

At minimum:

```text
owned 3001 → owned 3002
= SELF_TRANSFER

wife AlJazira → owned 3001
= EXTERNAL_TRANSFER_IN

owned AlJazira → owned D360
= SELF_TRANSFER

credit card purchase
= EXPENSE

credit card payment
= CREDIT_CARD_PAYMENT, not EXPENSE

refund
= REFUND

incoming unknown sender
!= automatically INCOME

intra-bank wording
!= automatically SELF_TRANSFER
```

These tests are mandatory before UI work.
