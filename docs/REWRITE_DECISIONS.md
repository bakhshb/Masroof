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
