# Masroof rewrite — accepted decisions (P0)

These decisions are recorded during the clean baseline phase. They are **not**
implemented yet; they guide Phase P1+ work.

## 1. Package identity

Remains `com.baraa.masroof` (same `applicationId` / `namespace`).  
Do not introduce a `com.masroof` split or any `v2` package.

## 2. Purchase families vs channels

POS and ONLINE are **purchase channels**, not separate ownership meanings.

Conceptual shape (exact enums in the domain phase):

- `MessageFamily` includes `PURCHASE`, `TRANSFER_IN`, `TRANSFER_OUT`,
  `CARD_PAYMENT`, `BILL_PAYMENT`, `WITHDRAWAL`, `REFUND`, etc.
- `PurchaseChannel` includes `POS`, `ONLINE`, `UNKNOWN`, …

## 3. Bank network vs ownership

`INTRA_BANK` / `INTER_BANK` describe `BankNetworkType` only.

They must **never** automatically mean a self-transfer between the user's own
accounts. Ownership is resolved separately.

Example:

- Wife Bank AlJazira → User Bank AlJazira
- `MessageFamily = TRANSFER_IN`
- `BankNetworkType = INTRA_BANK`
- ownership path `EXTERNAL → OWNED`
- **not** `SELF_TRANSFER`

## 4. Persistence timing

No Room database in P0. Persistence is introduced after the domain model is
stable in a later phase.

## 5. P4 — Parse-time details vs domain ParsedEvent

Bank AlJazira fixtures assert parse-time facts that DOMAIN `ParsedEvent` does
not currently carry: `transactionReference`, `availableBalance`,
`outstandingBalance`, `biller`, `billerCode`, and offset-less local timestamps.

**Decision:** keep these on a narrowly typed parsing-layer model
`ParsedEventDetails`, attached to `ParseResult` / `ParsedEventDraft`. Do **not**
silently map biller→merchant, reference→counterparty, or balances→amount.
Do **not** extend DOMAIN.md / domain `ParsedEvent` in P4 for these fields.

## 6. P4 — Local SMS date-time vs Instant

Fixtures represent local timestamps without an offset (e.g. `2026-08-03T14:32:00`).
DOMAIN `ParsedEvent.occurredAt` is `Instant?`.

**Decision:** store local values in `ParsedEventDetails.occurredAtLocal`
(`LocalDateTime`). Leave `ParsedEvent.occurredAt` null at parse time rather than
pretending local wall time is UTC (`…Z`). Timezone policy is deferred.

## 7. P5 — Persistence schema (clean rewrite)

- Room schema **version = 1** (no legacy migrations).
- `ParsedEventDetails` stored as **nullable columns on `ParsedEventEntity`**
  (single-table atomic write); domain/parsing types remain separate via mappers.
- Money as decimal string + currency name (never Double/Float).
- Instant as epoch millis; LocalDateTime as ISO local text (no zone conversion).
- RawSms dedupe: unique `dedupeKey = sender|receivedAtEpochMillis|bodyHash`,
  plus unique nullable `deviceMessageId` (SQLite allows multiple NULLs).
- FK `parsed_event.rawSmsId → raw_sms.id` with **RESTRICT**: deleting parsed
  rows must not cascade-delete raw evidence.
- `RawSmsRepository.insertIfAbsent` is atomic via `OnConflictStrategy.IGNORE`
  (no check-then-insert race).
- `ParsedEventRepository` lives under `parsing.repository` (not domain), because
  it carries `ParsedEventDetails`.

## 8. P6 — SMS ingestion identity

- Provider inbox row → RawSms.id `android-sms:<providerId>` (stable re-scan).
- Live BroadcastReceiver (no provider id yet) →
  `android-sms-live:<sender>|<epochMillis>|<bodyHash>`.
- Cross-path dedupe relies on P5 `dedupeKey = sender|epochMillis|bodyHash`.
- Bank AlJazira scope is checked with the existing P4 detector **before**
  RawSms persistence so unrelated personal SMS are not stored.
- Historical scan processes inbox DATE ASC (oldest → newest).
- Live `RawSms.receivedAt` uses injectable device receipt [InstantClock], not SMSC
  part timestamps.
- Live↔historical near-duplicates (opposite `deviceMessageId` nullness only) may
  reconcile within a 5s receivedAt tolerance on exact sender+bodyHash; same-source
  rows are never merged by that rule alone.
