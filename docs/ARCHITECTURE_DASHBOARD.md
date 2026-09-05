# Masroof Architecture Dashboard

Evidence-based read-only map of the Masroof Android codebase, generated 30 Aug 2026.

**Scope:** 405 production Kotlin files · 134 test classes · ~800 passing unit tests · single `:app` Gradle module · Room schema v9 · one bank adapter (Bank AlJazira)

---

## At a glance

| Metric | Value |
|--------|-------|
| Major package modules | 8 |
| Room entities | 11 |
| Schema version | 9 |
| Concrete bank adapters | 1 |
| Passing unit tests | ~800 |
| Instrumentation / Compose UI tests | 0 |

---

## Major modules

| Module | Responsibility | Dependency role |
|--------|----------------|-----------------|
| `presentation` | Compose screens, ViewModels, navigation, theme | Depends on application + domain |
| `application` | Workflow orchestration: dashboard, reconciliation, review, backup, update | Should depend on domain ports; has exceptions |
| `domain` | Models, invariants, ownership, matching, assembly, policy, repository ports | Core policy; framework-free intent |
| `parsing` | Normalizer, parse contracts, validation, parsed-event port | Parser abstraction between SMS and bank adapter |
| `bank` | Adapter registry and Bank AlJazira detector/parser/extractors | Only one concrete bank adapter today |
| `sms` | Android provider, broadcast receiver, mapping, ingestion, historical scan | Android edge adapter + shared ingest service |
| `data` | Room schema/DAOs/mappers/repository and SharedPreferences implementations | Persistence/infrastructure adapter |
| `core` | Money value object and small shared primitives | Low-level shared utility |

---

## Dependency direction

**Target flow:**

```
Presentation → Application → Domain ports & policies
SMS / Bank / Parsing → Application workflows → Domain ports & policies
Data / Room / Preferences → Domain repository ports → Domain policies
```

Interfaces live principally in `domain.repository` and `parsing.repository`. Room and SharedPreferences implementations sit in `data`. `AppContainer` composes concrete adapters.

### Observed boundary exceptions

- `SmsIngestionService` is in `sms` but orchestrates application reconciliation/review
- `application.dashboard` directly imports Bank AlJazira extractors/heuristics
- `application.onboarding.HistoricalSmsRescanService` imports a presentation import-date policy
- `AppContainer` imports Android, Room, data, bank, SMS, parsing, and presentation
- Several ViewModels reach directly into domain repositories/services and SMS scan types
- `AppLocaleContext` reads concrete SharedPreferences configuration from presentation

---

## Runtime composition

`AppContainer` (`app/src/main/kotlin/com/baraa/masroof/application/AppContainer.kt`) manually builds:

- Room database and 11 repository implementations
- Bank SMS adapters (`BankSmsRegistry` → `AlJaziraSmsAdapter`)
- SMS ingestion, reconciliation, review, dashboard, backup, and update services
- Android SMS data source and historical scanner

**Primary user-facing domains:** Dashboard, Transactions, Review, Onboarding, Settings, Notifications, Backup, Update

---

## SMS ingestion flow

Two sources converge into one idempotent pipeline.

### Historical path

```
Android SMS provider
  → HistoricalSmsScanner
  → AndroidSmsMapper
  → SmsIngestionService
```

### Live path

```
BroadcastReceiver
  → Multipart assembly
  → AndroidSmsMapper
  → SmsIngestionService
```

### Shared pipeline

```
BankSmsRegistry route
  → RawSms insert / dedupe
  → Bank adapter parse
  → ParsedEvent save
  → Ownership discovery
  → Full reconciliation
  → Review queue refresh
  → Financial transaction / review
```

**Invariants:**

- Deduplication combines repository insertion with a five-second cross-source near-duplicate check
- Evidence persistence survives best-effort failures in discovery, reconciliation, and review refresh
- `RawSms` is immutable evidence; corrections live in `user_correction`

---

## Transaction flow

```
ParsedEvent
  → Effective correction projection
  → Ownership resolution
  → TransactionAssembler
  → Single assembly or pair matcher
  → Persist transaction + SMS links
  → Review / dashboard
```

Transfers may remain pending until `TransactionMatcher` finds a mutually unique pair. Unresolved conditions generate review candidates.

**Source of truth:** Raw SMS and parsed-event evidence precede derived financial transactions. Reparse and reconciliation can refresh derived projections without duplicating raw evidence.

---

## Database entities (Room v9)

| Table | Role |
|-------|------|
| `raw_sms` | Immutable SMS evidence; dedupe identity |
| `parsed_event` | Parsed projection of one raw SMS |
| `financial_transaction` | Assembled financial fact |
| `financial_transaction_raw_sms_link` | Transaction ↔ evidence join |
| `review_item` | Open/resolved review for a raw SMS |
| `user_correction` | Non-destructive user correction attached to raw SMS evidence |
| `bank_registry` | Owned bank metadata |
| `account_registry` | Account ownership (bank + masked number key) |
| `card_registry` | Card ownership (bank + last4 key) |
| `credit_facility` | Credit facility metadata |
| `loan_registry` | Loan ownership (bank + loan type key) |

**Key relationships:**

- `raw_sms` → `parsed_event` / `review_item` / `user_correction`
- `financial_transaction` ↔ `raw_sms` through `financial_transaction_raw_sms_link`
- One raw SMS links to at most one financial transaction; multiple raw SMS rows can link to one transaction (transfer pairs, self-transfers)

---

## Technical debt and architectural violations

| Finding | Impact | Priority |
|---------|--------|----------|
| Bank-specific dashboard dependencies | `application.dashboard` imports `bank.aljazira`; a second bank risks conditional spread | High |
| SMS package owns a use case | `SmsIngestionService` coordinates persistence, parse routing, ownership, reconciliation, and reviews from `sms.ingestion` | High |
| Presentation policy imported by application | Import date policy reverses the intended presentation → application direction | High |
| ViewModels skip the application facade | Several ViewModels reach directly into domain repositories/services and SMS scan types | High |
| Single app Gradle module | Package boundaries are conventions only; no compiler-enforced layer/module isolation | High |
| Locale/bootstrap crosses data and presentation | `AppLocaleContext` reads concrete SharedPreferences configuration from presentation | Medium |
| Large composition root | `AppContainer` creates most concrete infrastructure and uses service-locator access | Medium |
| Reconciliation scans full backlog after each event | Correctness-first behavior may grow linearly with stored event volume | Medium |
| No coverage instrumentation | Test breadth exists, but coverage regressions are not objectively gated | Medium |
| Spec and implementation drift | `ARCHITECTURE.md` describes a use-case layer but no `*UseCase` types exist; Room KDoc says v4 while schema is v9 | Medium |
| One-bank implementation | Registry/adapter abstraction is present but multi-bank extensibility is unproven | Medium |

---

## Test coverage map

| Area | Evidence present | Assessment |
|------|------------------|------------|
| Domain policy | Models/invariants, ownership, periods, assembly, matcher and transfer rules | Strong |
| Bank parsing | AlJazira detector/parser, extraction and fixture corpus | Strong |
| SMS boundary | Mapper, body hash, multipart receiver, scanner, ingestion | Strong |
| Persistence | Migrations 1→9, DAOs, mappers, Room repositories, atomic review work | Strong |
| Application workflows | Reconciliation, restore, reclassification, review, dashboard builders | Strong |
| Presentation logic | ViewModels, navigation, formatters, design tokens/charts | Moderate |
| UI/instrumentation & end-to-end | No dedicated UI/instrumentation test tree or coverage tool found | Gap |

**Notable gaps:** `ReviewViewModel`, `DashboardProjectionBuilder`, `TransactionMatcher` (dedicated), `AlJaziraMessageClassifier`, many extractors, 6/11 repository contract tests, zero `androidTest` files.

---

## Recommended next architectural work

1. **Move ingestion orchestration** — Put `ProcessRawSmsUseCase` in `application`; retain `sms` for Android I/O, mapping, receiver, and provider access.
2. **Define bank-neutral parsing facts** — Move card/debit classification, due-date extraction, and foreign-purchase parsing behind `ParsedEventDetails` or a bank-agnostic capability.
3. **Fix facade inversions** — Move `ImportDatePolicy` into application/domain, relocate locale bootstrap, and expose application-owned scan/registry DTOs to ViewModels.
4. **Enforce boundaries** — Add architecture tests first; then split `domain`, `parsing`, `bank-api`, `data`, and `app` Gradle modules when seams stabilize.
5. **Narrow reconciliation** — Introduce candidate queries/indexes and reconcile only affected event windows while retaining a full repair pass.
6. **Close high-risk test gaps** — Add dedicated tests for `TransactionMatcher`, dashboard projection/loading, the review ViewModel, bank classifier/extractors, and repository contracts.
7. **Prove extensibility and measure coverage** — Add a second adapter contract-test suite plus coverage gates for domain/parsing/reconciliation paths; then introduce Compose smoke tests.

---

## Overall assessment

Masroof has credible clean-architecture intent, a durable evidence-first SMS pipeline, and unusually broad focused unit coverage. The main gap is enforcement: a single module, direct cross-layer/bank dependencies, and untested orchestration/UI paths mean the architecture remains convention-driven rather than structurally protected.

---

## Related docs

- [`ARCHITECTURE.md`](./ARCHITECTURE.md) — target architecture specification
- [`DOMAIN.md`](./DOMAIN.md) — domain model reference
- [`PARSING_SPEC.md`](./PARSING_SPEC.md) — parsing rules
- [`AGENTS.md`](../AGENTS.md) — agent and dashboard calculation rules
