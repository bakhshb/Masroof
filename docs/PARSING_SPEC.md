# Masroof v2 — Bank AlJazira Parsing Specification

## 1. Purpose

This document defines how Bank AlJazira SMS messages should be interpreted.

It describes expected behavior.

It does not prescribe one exact regex implementation.

The parser must be fixture-driven and must tolerate message format variation.

---

## 2. Scope

Initial bank:

```text
Bank AlJazira
```

Only Bank AlJazira messages are required for the first parser implementation.

Other banks must be ignored or treated as unsupported until explicitly implemented.

---

## 3. Fundamental Parsing Rule

Do not assume one fixed template per transaction type.

The same financial event may appear in:

- Arabic
- English
- different wording
- different punctuation
- different field order
- different whitespace
- different labels

Example purchase variants:

```text
شراء عبر نقاط البيع
شراء من نقاط البيع
عملية شراء نقاط بيع
POS Purchase
POS transaction
Purchase via POS
```

All may represent a purchase family.

Likewise:

```text
شراء عبر الانترنت
شراء من الانترنت
شراء إلكتروني
Internet Purchase
Online Purchase
E-Commerce Purchase
```

All may represent an online purchase family/channel.

---

## 4. Parsing Pipeline

```text
Raw SMS
  ↓
Normalize
  ↓
Bank detection
  ↓
Message family classification
  ↓
Field extraction
  ↓
Validation
  ↓
ParsedEvent
  ↓
Domain resolution
```

The parser stops at `ParsedEvent`.

The parser must not decide whether a transfer is between accounts owned by the user.

That belongs to the domain ownership layer.

---

## 5. Normalization

Keep both:

```text
originalBody
normalizedBody
```

Recommended normalization behavior:

- Unicode normalization
- normalize line endings
- trim line whitespace
- collapse repeated spaces
- normalize colon variants
- normalize common punctuation variants
- create normalized Arabic/Latin digit representation if needed
- create lowercase English comparison representation
- retain meaningful numeric separators

Do not delete text that might distinguish:

```text
balance
card number
account number
amount
transaction reference
```

---

## 6. Message Classification

Classification should rely on evidence rather than exact full-message equality.

Example conceptual evidence sets:

### POS Purchase

Strong indicators:

```text
نقاط البيع
POS
point of sale
mada
```

Supporting indicators:

```text
شراء
purchase
بطاقة
card
لدى
merchant
amount
بمبلغ
```

### Online Purchase

Strong indicators:

```text
شراء عبر الانترنت
شراء من الانترنت
online purchase
internet purchase
e-commerce
ecommerce
```

Supporting indicators:

```text
بطاقة
card
merchant
لدى
amount
بمبلغ
```

### Incoming Transfer

Indicators may include:

```text
حوالة واردة
تحويل وارد
incoming transfer
credited
received
```

### Outgoing Transfer

Indicators may include:

```text
حوالة صادرة
تحويل صادر
outgoing transfer
debited
خصمت من حساب
```

### Bill Payment

Indicators may include:

```text
سداد
فاتورة
مفوتر
bill payment
biller
```

### Refund

Indicators may include:

```text
استرداد
مرتجع
refund
reversal
```

Exact aliases must come from sanitized real fixtures.

---

## 7. Classification Output

Suggested:

```kotlin
data class MessageClassification(
    val family: MessageFamily,
    val channel: PurchaseChannel?,
    val bankNetworkType: BankNetworkType?,
    val confidence: Double,
    val evidence: List<String>
)
```

Possible purchase channels:

```kotlin
enum class PurchaseChannel {
    POS,
    ONLINE,
    APPLE_PAY,
    GOOGLE_PAY,
    OTHER,
    UNKNOWN
}
```

---

## 8. Field Extraction

Field extraction must be independent from message classification where possible.

Dedicated extractors:

```text
AmountExtractor
CurrencyExtractor
MerchantExtractor
CardExtractor
AccountExtractor
CounterpartyExtractor
DateTimeExtractor
ReferenceExtractor
BalanceExtractor
```

---

## 9. Amount Extraction

### Critical requirement

The parser must not treat any arbitrary number as the transaction amount.

Bank SMS can contain numbers representing:

- amount
- card last4
- account last4
- balance
- outstanding balance
- IBAN suffix
- transaction reference
- date
- time

Example:

```text
بطاقة ائتمانية: 7271
بمبلغ :51.99 SAR
الرصيد المتاح :SAR 17230.03
إجمالي المبلغ المستحق:2380.88 SAR
```

Correct:

```text
amount = 51.99
cardLast4 = 7271
availableBalance = 17230.03
outstandingBalance = 2380.88
```

Incorrect:

```text
amount = 7271
```

or:

```text
amount = 17230.03
```

---

## 10. Amount Labels

Generic candidate labels may include:

```text
بمبلغ
المبلغ
مبلغ العملية
amount
transaction amount
purchase amount
```

Bank AlJazira-specific aliases must be discovered from fixtures.

Supported ordering should include where seen:

```text
LABEL + NUMBER + CURRENCY
LABEL + CURRENCY + NUMBER
```

Example:

```text
بمبلغ: 51.99 SAR
Amount: SAR 51.99
```

Validation must prefer strongly labeled transaction amounts over balance-like fields.

---

## 11. Currency Extraction

Initial currency:

```text
SAR
```

Possible aliases:

```text
SAR
ر.س
ريال
```

Only aliases proven by fixture data should be enabled.

---

## 12. Merchant Extraction

Potential labels:

```text
لدى
التاجر
merchant
at
```

Merchant extraction must stop at appropriate field boundaries.

Merchant must not absorb:

- next label
- date
- balance
- card identifier

---

## 13. Card Extraction

Potential indicators:

```text
بطاقة
بطاقة مدى
بطاقة ائتمانية
card
credit card
mada card
ending
```

Store masked/last4 form only where available.

Example:

```text
بطاقة ائتمانية: 7271
```

Expected:

```text
cardLast4 = 7271
```

Do not confuse card suffix with amount.

---

## 14. Account Extraction

Potential indicators:

```text
حساب
من حساب
خصمت من حساب
الى حساب
account
from account
to account
```

Example:

```text
خصمت من حساب: 3001
```

Expected:

```text
sourceAccountLast4 = 3001
```

If:

```text
الى حساب: 3002
```

Expected:

```text
destinationAccountLast4 = 3002
```

Ownership is NOT decided here.

---

## 15. Counterparty Extraction

Potential labels:

```text
الى
من
المستفيد
beneficiary
from
to
```

A counterparty is not a merchant.

The parser should keep this distinction.

---

## 16. Date and Time Extraction

The parser must support known Bank AlJazira date formats found in fixtures.

Examples from known banking styles may include:

```text
22:50 03-08-2026
2026-08-03 14:32
```

Only formats found in fixtures should become production parsing rules.

If date parsing fails:

- keep SMS receive timestamp
- mark event confidence lower or request review when transaction timestamp is essential

Do not silently invent a transaction date.

---

## 17. Transfer Network Classification

Bank AlJazira may call transfers:

```text
internal
```

This should map only to:

```text
BankNetworkType.INTRA_BANK
```

It must NOT map directly to:

```text
SELF_TRANSFER
```

Example:

```text
wife Bank AlJazira → user's Bank AlJazira
```

can still be:

```text
INTRA_BANK
EXTERNAL_INCOMING
```

Ownership resolution happens later.

---

## 18. Transfer Parsing Output

Example conceptual parsed event:

```kotlin
ParsedEvent(
    bank = BANK_ALJAZIRA,
    messageFamily = TRANSFER_IN,
    amount = 500 SAR,
    sourceAccountRef = null,
    destinationAccountRef = ****3001,
    counterparty = "Name from SMS",
    bankNetworkType = INTRA_BANK,
    ...
)
```

No self-transfer conclusion is made at parsing stage.

---

## 19. Known Scenario — Own AlJazira Accounts

Example:

```text
3001 → 3002
```

Parser output:

```text
source = 3001
destination = 3002
bankNetworkType = INTRA_BANK
```

Domain ownership resolver later determines:

```text
3001 = OWNED
3002 = OWNED
```

Final result:

```text
SELF_TRANSFER
```

---

## 20. Known Scenario — Another Person in AlJazira

Example:

```text
wife's AlJazira account → user's 3001
```

Bank message may still say:

```text
internal incoming transfer
```

Parser output:

```text
TRANSFER_IN
INTRA_BANK
amount = ...
counterparty = ...
destination = 3001
```

Domain result:

```text
EXTERNAL_TRANSFER_IN
```

unless source is later confirmed as owned.

---

## 21. Bill Payment

Where fixture evidence supports it, extract:

```text
amount
biller
biller code
account/card source if present
date
reference
```

Bill payment is generally an expense candidate, but the parser should output the message family only.

Final financial classification remains domain/application responsibility.

---

## 22. Refund

Extract where available:

```text
amount
merchant
card
date
reference
```

The parser outputs:

```text
REFUND
```

Matching against an original purchase happens later.

---

## 23. Non-Financial Messages

Examples may include:

- OTP
- marketing
- security notice
- login notice
- general account message
- service notification

They should be classified as:

```text
NON_FINANCIAL
```

or another explicit non-transaction family.

Do not create a financial transaction.

---

## 24. Unsupported Messages

If a Bank AlJazira message is recognized as from the bank but its format is unknown:

```text
ParseStatus = REVIEW_REQUIRED
MessageFamily = UNKNOWN
```

Do not discard the raw SMS.

---

## 25. Parse Status

Suggested:

```kotlin
enum class ParseStatus {
    SUCCESS,
    PARTIAL,
    REVIEW_REQUIRED,
    NON_FINANCIAL,
    UNSUPPORTED,
    INVALID
}
```

---

## 26. Validation Rules

### V-001
A card suffix cannot become amount merely because it is numeric.

### V-002
An account suffix cannot become amount merely because it is numeric.

### V-003
Available balance and outstanding balance are not transaction amount.

### V-004
Reference number is not amount.

### V-005
Date/time digits are not amount.

### V-006
For purchase families, amount should normally be strongly associated with an amount label or proven positional pattern.

### V-007
If multiple plausible amounts remain and the parser cannot disambiguate safely:

```text
REVIEW_REQUIRED
```

### V-008
Missing optional merchant does not necessarily invalidate a purchase.

### V-009
Missing required amount normally prevents automatic transaction creation.

---

## 27. Fixture Format

Recommended file structure:

```text
testdata/
  bank_aljazira/
    purchase_pos/
    purchase_online/
    transfer_in/
    transfer_out/
    bill_payment/
    refund/
    withdrawal/
    non_financial/
    unknown/
```

Recommended fixture representation:

```json
{
  "name": "purchase_online_ar_001",
  "sender": "SANITIZED_SENDER",
  "body": "SANITIZED_SMS_BODY",
  "expected": {
    "family": "ONLINE_PURCHASE",
    "amount": "51.99",
    "currency": "SAR",
    "merchant": "Keeta",
    "cardLast4": "7271",
    "parseStatus": "SUCCESS"
  }
}
```

JSON is recommended because expected output is explicit and machine-readable.

Plain `.txt` plus companion `.json` is also acceptable.

---

## 28. Real Message Workflow

When a new SMS format fails:

```text
1. sanitize the real SMS
2. create fixture
3. define expected result
4. write failing test
5. update parser/classifier/extractor
6. make test pass
7. retain test permanently
```

Do not patch the parser without adding a fixture.

---

## 29. Initial Test Cases Required

At minimum:

### Purchase
- Arabic POS
- English POS
- Arabic online
- English online
- card number present before amount
- balance present after amount
- multiple numeric fields

### Transfer
- outgoing intra-bank
- incoming intra-bank
- source account present
- destination account present
- counterparty present
- own 3001 → own 3002 parsing only
- another person's AlJazira → own account parsing only

### Bill Payment
- Arabic bill payment
- amount extraction
- biller extraction

### Refund
- refund with amount
- refund with card
- refund with merchant if present

### Safety
- OTP does not create transaction
- balance notice does not create expense
- unknown Bank AlJazira format goes to review
- amount is never taken from card suffix
- amount is never taken from account suffix

---

## 30. Parser Quality Target

For every known fixture:

```text
Expected fields must match exactly.
```

For unknown real messages:

```text
Review is acceptable.
Silent incorrect amount/type is not acceptable.
```

The parser is considered safe only when known fixtures produce zero silent wrong results.

---

## 31. Future AI Rule

AI is not part of the initial parser.

Future optional use:

```text
Rules/parser
  ↓
Low confidence only
  ↓
Local AI fallback
  ↓
Structured candidate result
  ↓
Deterministic validation
```

AI output must never bypass validation.

---

## 32. Bank Expansion Rule

Do not generalize Bank AlJazira-specific assumptions into all banks.

When adding another bank:

```text
reuse generic normalization
reuse generic field concepts
add bank-specific aliases/rules/fixtures
reuse domain ownership and transaction logic
```

The parser boundary must make this possible.
