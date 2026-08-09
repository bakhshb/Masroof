# Pattern Engine — Layer Responsibility Map (Stabilization Baseline)

This document records, **as of the stabilization baseline**, which component
currently determines each dimension of SMS interpretation. It exists so that
discovery and matching stop silently disagreeing: before changing behavior,
the shared corpus test (`RealJaziraSmsCorpusTest`) pins one canonical contract.

No code was changed to produce this document. It describes the *current* state.

## Dimensions and the layer that currently owns them

| Dimension | Currently determined by | Notes / risk |
|---|---|---|
| financial / non-financial | `MessageTypeCueCatalog.detect()` (`NON_FINANCIAL`/`OTP`) + `SmsStructureNormalizer.looksLikeOtpOrMarketing()` + `MessageTypeCueCatalog.isNonFinancialCue()` | Three checks, called in different orders by different callers. `PatternDiscoveryService` checks OTP then cue then `isNonFinancialCue`. |
| transaction type | `MessageTypeCueCatalog.detect()` (phrase rules) → `MessageTemplateEngine.buildFromSms().transactionType` (which is the cue's type) | One source (`MessageTypeCueCatalog`), but `SemanticPatternSchemaNormalizer.fromTemplate()` re-derives a *second* type via `MessageTypeCueCatalog.detect(templateText)` and will return `CONFLICTING_TRANSACTION_TYPE` if it disagrees with the stored type. |
| direction | `MessageTypeCueCatalog.detect().direction` (stored name) → `TransactionTypeTaxonomy.parseDirection(...)`; `MessageTemplateEngine` also carries `built.direction` | Discovery uses `built.direction ?: cue.direction`; the schema uses `TransactionTypeTaxonomy.directionOf(type)`. For `OTHER_FINANCIAL` they can differ. |
| amount | `CanonicalSmsFieldExtractor.extract()` → label-strict `MonetaryFieldClassifier` via `LineBasedFieldParser.parseTransactionAmount()`; fallback to first amount-label line | `MonetaryFieldClassifier` is the single label→role source. `LineBasedFieldParser` has its *own* `AMOUNT_LABELS`/`BALANCE_LABEL_REGEX` lists that partly duplicate it — a divergence risk. |
| currency | `CanonicalSmsFieldExtractor.extract().currency` (parsed from the amount line) | Single source. |
| identifiers (last4) | `CanonicalSmsFieldExtractor.extract()` using `CanonicalPatternFieldClassifier.classify(label)` → `LineBasedFieldParser.lastFourFromValue(value)` | **`CanonicalPatternFieldClassifier` and `LineBasedFieldParser` disagree** on which labels are accounts. `LineBasedFieldParser.ACCOUNT_LABEL_REGEX` includes bare `من`/`إلى`/`الى`; `CanonicalPatternFieldClassifier.isSourceAccount` requires `من حساب`/`خصمت من`, and `isDestinationAccount` requires `الي حساب`/`حساب المستفيد`. Bare `من:`/`إلى:` are therefore NOT classified as accounts by the extractor. |
| structural identity (family) | `SemanticPatternSchemaNormalizer.fromBody()` / `fromTemplate()` → `SemanticPatternSchema.stableKey()` (`semantic-v2|type|direction|instrument|required=…|structural=…`) | The semantic key ignores changing values (amount/date/last4/merchant/ref). Cases 6 & 8 share a key only if their structural field set is identical. |
| structural identity (exact variant) | `TemplateCanonicalizer.canonicalKey()` (built on `SmsStructureNormalizer.signatureFromTemplate`) + `CanonicalMessageNormalizer` | The exact-variant key is the persistence uniqueness key. |
| template text | `MessageTemplateEngine.buildFromSms()` (label-strict, `{PLACEHOLDER}` tokens) | Single source for template generation. |
| matching (runtime) | `TemplateResolutionService.resolve()` → `SmsStructureNormalizer.signatureFromBody()` lookup, then `TemplateMatcher.match()` against the saved template | Matching is signature/template driven, **not** semantic-key driven. A discovered-and-saved pattern matches a structurally equivalent SMS only if the saved template + anchors line up with `TemplateMatcher`. |

## Components that separately interpret the same SMS

1. `MessageTemplateEngine` — builds the `{PLACEHOLDER}` template + derives `transactionType`/`direction` from `MessageTypeCueCatalog`.
2. `PatternDiscoveryService` — orchestrates cue → template → canonical key → semantic key; OPTIONAL enrichment stages non-fatal.
3. `SemanticPatternSchemaNormalizer` — projects body/template → semantic family key; re-derives type from template text.
4. `CanonicalMessageNormalizer` — body/template → `CanonicalMessageStructure` (label + value-token), shared by schema + signature.
5. `TemplateMatcher` — regex-over-placeholders line matcher used by resolution.
6. `TemplateResolutionService` — runtime resolver: signature lookup then template match.
7. `CanonicalSmsFieldExtractor` — strict value extractor (amount/currency/last4) for resolved transactions.
8. `LineBasedFieldParser` — line splitting + label-strict money/last4/date parsing; has its own label regex lists.
9. `MonetaryFieldClassifier` — single label→monetary-role source.
10. `CanonicalPatternFieldClassifier` — single label→canonical-field source (used for last4 + semantic structural fields).

## Known divergence points the corpus must pin

- **Bare `من:` / `إلى:` / `الى:`** — `LineBasedFieldParser` treats them as accounts;
  `CanonicalPatternFieldClassifier` does **not**. Cases 1, 2, 3, 10 expect
  source/destination account last4 from these bare labels → baseline risk.
- **`Internet Purchase`** — not in `MessageTypeCueCatalog` ONLINE_PURCHASE phrases
  (only `online purchase`); falls back to generic `PURCHASE`. Case 4 expects
  `ONLINE_PURCHASE` → baseline risk.
- **English compact inline SMS** — `LineBasedFieldParser.expandCompactInlineFields`
  expands them; amount extraction then depends on the `of` label being a
  recognized transaction-amount label (it is). Balance/due must stay
  informational. Cases 4, 5.
- **`خصم: قسط تمويل`** — type from `قسط تمويل` → `BILL_PAYMENT`; amount from
  `القسط`; `المبلغ المتبقي` must stay contextual. Case 10.
- **Semantic family identity** — cases 6 & 8 must share a family key; salary
  (case 9) must be `SALARY`, not `TRANSFER_IN`; POS (7) and online (4, 5)
  must be distinct families.

## Contract the corpus test pins (Phase 3)

`RealJaziraSmsCorpusTest` runs, for each corpus case, the production
analysis path: `MessageTypeCueCatalog.detect` → `MessageTemplateEngine.buildFromSms`
→ `CanonicalSmsFieldExtractor.extract` → `SemanticPatternSchemaNormalizer.fromBody`,
and asserts financial / type / direction / amount / currency / identifiers.
Failures print `case=… stage=… expected=… actual=…` so the failing layer is
visible, not hidden behind a generic assertion.

This is a **baseline**: failures are expected and recorded in the Phase 8
matrix. Fixes come after the matrix is known.