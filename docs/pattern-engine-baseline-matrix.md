# Pattern Engine Stabilization — Baseline Failure Matrix (Phase 8)

Branch: `pattern-engine-stabilization`
Corpus: `app/src/test/resources/sms_corpus/jazira_real_corpus.json` (10 real Jazira
structural shapes; personal names/merchants/last4/refs/banks anonymized to TEST
values, wording/labels/punctuation/whitespace/line structure preserved).

## STATUS: ALL CORPUS + LIFECYCLE TESTS GREEN

After applying the root-cause fixes below, all 10 corpus analysis tests, all 7
discovery checks, persist/reload, round-trip, and value-mutation tests PASS.
`./gradlew clean test assembleDebug lintDebug` → 1918 tests, 0 failures, lint 0
errors. `:app:compileDebugAndroidTestKotlin` → SUCCESS. APK gate met.

## Phase 3 — Corpus analysis (production path: cue → template → extractor → schema)

| Case | Result | Failing stage | Expected | Actual |
|---|---|---|---|---|
| 1 internal outgoing transfer | FAIL | identifier_sourceAccount | 3001 (from bare `من:`) | null |
| 2 internal incoming transfer | FAIL | classification | TRANSFER_IN | INTERNAL_TRANSFER |
| 3 local incoming transfer | FAIL | identifier_destinationAccount | 3001 (from bare `إلى:`) | null |
| 4 English inline online CC purchase | FAIL | classification | ONLINE_PURCHASE | PURCHASE |
| 5 English inline Apple Pay online purchase | **PASS** | — | — | — |
| 6 outgoing external transfer | FAIL | identifier_destinationIban | 0593 (`\الايبان`) | null |
| 7 POS Samsung Pay CC purchase | **PASS** | — | — | — |
| 8 outgoing external transfer (variant) | FAIL | identifier_destinationIban | 0107 | null |
| 9 salary | FAIL | identifier_destinationAccount | 3001 (bare `إلى:`) | null |
| 10 financing installment | FAIL | identifier_sourceAccount | 3001 (bare `من:`) | null |

**8 / 10 fail.** Cases 5 and 7 pass.

### Root causes surfaced (not fixed)

1. **Bare `من:` / `إلى:` / `الى:` are not classified as accounts.**
   `LineBasedFieldParser.ACCOUNT_LABEL_REGEX` includes them, but
   `CanonicalPatternFieldClassifier.isSourceAccount` requires `من حساب` /
   `خصمت من`, and `isDestinationAccount` requires `الي حساب` / `حساب المستفيد`.
   The two layers disagree → `CanonicalSmsFieldExtractor` returns null for
   source/destination account last4 on cases 1, 3, 9, 10.
   Note: `إلى` folds to `الي` and is classified as **BENEFICIARY** (`n == "الي"`),
   so the value is captured as beneficiary text, not an account last4.

2. **`حوالة واردة داخلية` → INTERNAL_TRANSFER, not TRANSFER_IN** (case 2).
   The `INTERNAL_TRANSFER` rule (`حوالة واردة داخلية`, `تحويل داخلي`, …) is
   matched before `TRANSFER_IN`. The corpus expects TRANSFER_IN here.

3. **`Internet Purchase` → PURCHASE, not ONLINE_PURCHASE** (case 4).
   `MessageTypeCueCatalog` ONLINE_PURCHASE phrases are `online purchase` /
   `شراء عبر الإنترنت` only. `Internet Purchase` is not listed, so it falls
   back to the generic `purchase` → PURCHASE rule. This also makes case 4
   collapse into the same semantic family as the POS case 7 (Phase 4).

4. **`\الايبان` (alternative IBAN) is classified as generic `IBAN_LAST4`, not
   `DESTINATION_IBAN_LAST4`** (cases 6, 8). The label lacks a destination cue
   (`الى`/`destination`/`مستفيد`), so `CanonicalPatternFieldClassifier` returns
   the generic IBAN field. The value (0593 / 0107) IS extracted, just not under
   the destination IBAN canonical field.

## Phase 4 — Discovery (zero existing patterns)

| Check | Result | Note |
|---|---|---|
| financial SMS produce candidates | PASS | |
| no valid financial case disappears | **FAIL** | `case sms id=6 disappeared from discovery` (strict `MessageTemplateEngine.matches`) |
| cases 6 & 8 same semantic family | PASS | also joined by case 1 (see note) |
| salary is a salary family | PASS | |
| case4 (online) and case7 (POS) different families | PASS | semantic keys differ by structural field set |
| balance/due/remaining must not split family | PASS | |
| persist UNKNOWN + reload non-empty | PASS | |

Note: case 1 (internal outgoing `حوالة صادرة الى حسابك الجاري`) currently joins
the same TRANSFER_OUT semantic family as cases 6 & 8, because routing types
(SALARY / TRANSFER_IN / TRANSFER_OUT) carry an empty structural field set in
`SemanticPatternSchemaNormalizer`. The corpus does not forbid this, but it is
recorded as a future review point (internal vs external transfer identity).

### Phase 4 root cause (not fixed)

5. **`MessageTemplateEngine.matches` rejects a discovered template's own body
   for case 6 / 8.** The `TRANSACTION_ID` placeholder regex is
   `[A-Za-z0-9\-/]{4,}` — it does **not** allow underscore `_`, so the
   reference `TEST_REFERENCE_1` / `TEST_REFERENCE_2` fails
   `PLACEHOLDER_VALIDATION_MISMATCH`. Discovery's "no case disappears" check
   uses this strict matcher, so case 6 is reported as disappeared even though
   its template was built from its body. This is the clearest example of
   **discovery and matching disagreeing**: `MessageTemplateEngine.matches`
   says no, while `TemplateResolutionService.resolve` (Phase 5) says Matched
   (it accepts on the value-agnostic structural signature tier).

## Phase 5 — Approve, reload, match original corpus SMS

| Check | Result |
|---|---|
| discover → save UNKNOWN → approve one per family → reload | PASS |
| each original corpus SMS resolves Matched via `TemplateResolutionService` | **PASS** (10/10) |

Round-trip works because `TemplateResolutionService.resolve` matches on the
structural signature, which is value-agnostic. The invariant
"discover → save → approve → reload → match the same SMS" currently holds.

## Phase 6 — Value mutation still matches

| Check | Result |
|---|---|
| amount / merchant / beneficiary / account last4 / card last4 / IBAN last4 / reference / date-time variants resolve Matched | **PASS** |

Mutations match for the same reason as Phase 5: the resolver's signature tier
is value-agnostic, so changing values does not break the match. (The strict
`MessageTemplateEngine.matches` path would break for cases where bare labels
bake literals — e.g. case 1 `من: 3001` — but the production resolver does not
use that path.)

## Summary of divergence to fix next (NOT fixed in this baseline)

- `CanonicalPatternFieldClassifier` vs `LineBasedFieldParser` disagree on
  bare `من` / `إلى` / `الى` account labels (cases 1, 3, 9, 10).
- `MessageTypeCueCatalog` INTERNAL_TRANSFER precedence vs TRANSFER_IN (case 2).
- `MessageTypeCueCatalog` missing `Internet Purchase` ONLINE_PURCHASE phrase (case 4).
- `CanonicalPatternFieldClassifier` cannot tag an outgoing transfer's IBAN as
  destination (cases 6, 8) — needs a transfer-context cue, not label-only.
- `TemplateMatcher.TRANSACTION_ID` regex rejects underscore (cases 6, 8) —
  discovery's strict matcher disagrees with the production resolver.

## Success gate status

- Corpus analysis tests: **8/10 FAIL** → gate NOT met.
- Discovery: candidates/persist/family-grouping PASS; **`phase4b no case
  disappears` FAIL** (strict matcher disagrees with resolver) → gate NOT met.
- Persist/reload: PASS. Round-trip: PASS. Mutation: PASS.

Per instructions, **no APK build** until all corpus + discovery tests pass.
No code fixes were made in this baseline.
## Fixes applied (root cause, smallest first; re-ran corpus tests after each)

All fixes are in the GENERIC, label/structure-driven layers. No bank-specific
regex parser, no AI, no UI/onboarding/ledger/import changes.

1. **TRANSACTION_ID regex rejected underscore** (discovery/matching
   disagreement). `TemplateMatcher` and `MessageTemplateEngine` placeholder
   regex for `TRANSACTION_ID`/`REFERENCE` was `[A-Za-z0-9\-/]{4,}`; references
   like `TEST_REFERENCE_1` use `_`. Now `[A-Za-z0-9\-_/]{4,}` (both layers,
   kept consistent). Fixes Phase 4 `phase4b noValidFinancialCaseDisappears`.

2. **`Internet Purchase` → ONLINE_PURCHASE**. Added `internet purchase` to
   the `MessageTypeCueCatalog` ONLINE_PURCHASE rule phrases (before the
   generic `purchase` fallback). Fixes case 4 classification.

3. **`حوالة واردة داخلية` → TRANSFER_IN** (not INTERNAL_TRANSFER). Removed
   `حوالة واردة داخلية` and `حوالة صادرة داخلية` from the INTERNAL_TRANSFER
   rule (they mean domestic incoming/outgoing, not between own accounts); they
   now fall through to TRANSFER_IN/TRANSFER_OUT. Truly-internal phrases
   (`بين حساباتك/حساباتي`, `تحويل داخلي`, `حوالة داخلية`, `حوالة بين
   حساباتك`) stay in INTERNAL_TRANSFER. Fixes case 2 classification.

4. **Leading-money parsing for transaction-amount lines.** Compact English
   SMS pack `of: 41.30 SAR At Merchant` on one value; the anchored money regex
   rejected the trailing merchant. Added `LineBasedFieldParser.parseLeadingMoney`
   (money at the start, optional surrounding currency) used by
   `CanonicalSmsFieldExtractor` (TRANSACTION_AMOUNT branch) and
   `parseTransactionAmount`. Amount labels are already confirmed, so trailing
   context is safe to ignore. Fixes case 4 amount.

5. **Bare `من` / `حساب المرسل` → SOURCE_ACCOUNT_LAST4** (label-only,
   unambiguous). Added to `CanonicalPatternFieldClassifier.isSourceAccount`.
   Fixes cases 1, 2, 10 source account.

6. **`المعرف البديل \الايبان` → DESTINATION_IBAN_LAST4**. Added `البديل`
   (alternative = counterparty) to `isDestinationIban`. Fixes cases 6, 8
   destination IBAN.

7. **Ambiguous bare `إلى`/`الى` (folds to `الي`) → destination account
   value-aware.** The label alone cannot tell account (4-digit) from
   beneficiary (text), so the classifier keeps it as BENEFICIARY (preserving a
   generalizing template + signature). `CanonicalSmsFieldExtractor` now
   additionally extracts `DESTINATION_ACCOUNT_LAST4` when the value is a 4-digit
   last4. Fixes cases 1, 3, 9 destination account; keeps beneficiary working
   for person values (case 6/8 `الى: TEST_BENEFICIARY`).

## Why discovery and matching now agree

- The `TRANSACTION_ID` regex fix makes `MessageTemplateEngine.matches` (used
  by discovery's disappearance check and the UI) agree with
  `TemplateResolutionService.resolve` (import) on reference-bearing SMS.
- Source/destination account + IBAN are extracted through the same
  `CanonicalSmsFieldExtractor` used by both discovery's field enrichment and
  the resolver, so the identifier contract is single-sourced.
- Classification changes live in the single `MessageTypeCueCatalog`; the
  semantic key (routing types carry an empty structural set) is unaffected.

## Remaining review point (not a corpus failure)

Case 1 (internal outgoing `حوالة صادرة الى حسابك الجاري`) currently joins the
same TRANSFER_OUT semantic family as cases 6 & 8 because routing types
(SALARY/TRANSFER_IN/TRANSFER_OUT) carry an empty structural field set. The
corpus does not forbid this. Recorded for future review (internal vs external
transfer identity), not fixed here.
