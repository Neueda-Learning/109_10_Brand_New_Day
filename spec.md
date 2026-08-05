# Payment Processing System - Unified Specification & Progress Log

Status: ACTIVE (living document — update it as work progresses)
Version: 2.1
Last Updated: 2026-08-04
Source of Truth: This file only.

## 1. How to Use This Spec

This is the only spec file for this project. It is both the **build spec** and the
**project history / progress log**. Do not rely on the separate template/master-prompt
files during implementation — they were used to bootstrap this document only.

Any developer or AI agent should be able to open this single file and immediately know:
- What the system does and its boundaries (Section 5).
- Who owns which module, and exactly what APIs/pages/tasks they must deliver (Section 9).
- The exact request/response contract for every endpoint (Section 10).
- How to scaffold the backend/frontend backbone from nothing (Section 11).
- What phase the project is in and what is done vs. pending (Section 2 — Status Dashboard,
  and Section 18 — Progress Log).

Rules:
- If a data shape, endpoint, or dependency is missing here, add it to this spec first —
  do not guess or invent requirements while coding.
- Keep Section 2 (Status Dashboard) and Section 18 (Progress Log) up to date whenever a
  task, endpoint, or page is completed. This is how progress is tracked — there is no
  separate lock/review gate blocking implementation.

## 2. Project Status Dashboard

Update this table whenever work completes. This is the fastest way for anyone (human or
agent) to see current project state at a glance.

| Module | Owner | Phase 1 - Backbone | Phase 2 - Backend Impl | Phase 2 - Frontend Impl | Phase 3 - Integration | Notes |
|---|---|---|---|---|---|---|
| Shared setup (pom.xml, schema.sql, docker-compose, config) | Team | DONE | — | — | IN_PROGRESS | Maven skeleton, `schema.sql`, `docker-compose.yml`, `CorsConfig`/`JdbcConfig`/`OpenApiConfig` all created and compiling |
| Shared dataset (data.sql seed) | Team | DONE | — | — | DONE | 491 payments / 1661 history rows generated via `scripts/generate_data_sql.py` — see Section 11.5 |
| M1 - Creation & Validation | Poornima | DONE | DONE | DONE | IN_PROGRESS | `POST /api/payments` + `GET /api/payments/{id}` implemented; `index.html`/`index.js` wired to real API (PR #1, merged) |
| M2 - Status Engine & Audit Trail | Neha | DONE | DONE | DONE | IN_PROGRESS | `process`/`history` endpoints implemented; `audit.html`/`audit.js` wired to real API (PR #3, merged) |
| M3 - Idempotency, Errors, Refund | Tharan | DONE | DONE | DONE | IN_PROGRESS | Idempotency short-circuit, refund rules, full `GlobalExceptionHandler` mapping implemented; `detail.html`/`detail.js` wired to real API (PR #2, merged) |
| M4 - Query API, Lifecycle UI, Design System, API Docs | Karuna | DONE | DONE | DONE | IN_PROGRESS | `GET /api/payments` filter/pagination + `lifecycle-timeline.js`/`dashboard.html`/`history.html` implemented and unit-tested; on `feature/m4-lifecycle-ui`, not yet merged to `main` |

Status values: `NOT_STARTED`, `IN_PROGRESS`, `DONE`, `BLOCKED`.
Overall project phase: **Phase 2 (Backend/Frontend Impl) — DONE for M1-M4 on their respective branches. Phase 3 (cross-module integration validation, e.g. end-to-end refund/process/query flows together, PR review, merge of `feature/m4-lifecycle-ui` into `main`) is IN_PROGRESS.**

## 3. Session Context Block (Optional — for AI session hygiene)

Copy and fill this block at the top of a new chat when working on a specific module:

| Field | Value |
|---|---|
| Name | Tharan / Poornima / Neha / Karuna |
| Module | M1 / M2 / M3 / M4 / Shared |
| Active Task | Example: scaffold pom.xml, M3 refund endpoint |
| Blockers | None or describe |

## 4. Hard Constraints (Non-Negotiable)

- Backend stack: Spring Boot + Maven + Java 25.
- Data access: Spring JDBC only (JdbcTemplate or NamedParameterJdbcTemplate).
- Database: MySQL only.
- No JPA, no Hibernate, no Spring Data repositories.
- No authentication or authorization code.
- No logging of full account/card numbers; use masking.
- Frontend only plain HTML/CSS/JS.
- No frontend frameworks or build tools.
- No unnecessary dependencies.
- No endpoint, field, or workflow outside this spec.

## 5. Project Scope

Internal payment processing system with:
- Payment creation and validation
- Status transition engine
- Audit history
- Idempotency behavior
- Refund creation flow
- Query/list APIs
- Shared lifecycle UI structure

Out of scope:
- Real payment gateway integration
- Authentication/authorization
- External processor integrations
- Multi-currency support — all payments are `INR` only for now. Multi-currency is a
  possible future feature, not currently assigned to any module (M1-M4), and must not be
  built or seeded until it is added to this spec with an owner.

## 6. Tech Stack and Dependency Policy

### 6.1 Platform

- Java: 25
- Build tool: Maven
- Framework: Spring Boot 4.x latest stable (currently `4.1.0`, requires springdoc-openapi
  `3.x` — see Section 6.2)
- Data access: spring-boot-starter-jdbc
- Validation: spring-boot-starter-validation
- Database driver: com.mysql:mysql-connector-j
- API docs: springdoc-openapi
- Testing: spring-boot-starter-test, spring-boot-webmvc-test (test-scope only — see Section 6.2)

### 6.2 Minimal Dependency Whitelist

Allowed dependencies only:
- org.springframework.boot:spring-boot-starter-web
- org.springframework.boot:spring-boot-starter-jdbc
- org.springframework.boot:spring-boot-starter-validation
- com.mysql:mysql-connector-j
- org.springdoc:springdoc-openapi-starter-webmvc-ui
- org.springframework.boot:spring-boot-starter-test
- org.springframework.boot:spring-boot-webmvc-test (test scope) — required as of Spring Boot
  4.1.0, which moved `@WebMvcTest`/`@AutoConfigureMockMvc` out of
  `spring-boot-test-autoconfigure` into this new artifact; not transitively pulled in by
  `spring-boot-starter-test`. Added by M3 for `GlobalExceptionHandlerTest`.

### 6.3 Denylist

Do not add:
- spring-boot-starter-data-jpa
- org.hibernate:*
- spring-boot-starter-security
- Any frontend framework dependency
- Any code generation framework unless explicitly approved by team review

## 7. Domain Model and Schema (Canonical and Intact)

This schema is the baseline contract and must remain intact unless a reviewed amendment is made here first.

```sql
payments (
  id                  UUID PK,
  idempotency_key     VARCHAR UNIQUE,
  source_account      VARCHAR,
  destination_account VARCHAR,
  amount              DECIMAL(18,2),
  currency            VARCHAR(3),  -- always "INR" for now, see Section 5
  status              VARCHAR,     -- CREATED | VALIDATED | SENT | COMPLETED | FAILED
  error_code          VARCHAR NULL,
  type                VARCHAR,     -- PAYMENT | REFUND
  original_payment_id UUID NULL,   -- set when type = REFUND
  created_at          TIMESTAMP,
  updated_at          TIMESTAMP
)

payment_status_history (
  id           UUID PK,
  payment_id   UUID FK,
  from_status  VARCHAR NULL,
  to_status    VARCHAR,
  changed_at   TIMESTAMP,
  triggered_by VARCHAR,
  note         VARCHAR NULL,
  seq          BIGINT AUTO_INCREMENT UNIQUE  -- insertion-order tiebreaker, not exposed via API
)
```

Schema invariants:
- `idempotency_key` must be unique.
- `payment_status_history` is append-only (no updates/deletes).
- Status transitions must follow the rules in Section 8.
- REFUND rows must reference `original_payment_id`.
- No speculative fields — do not add columns without updating this section first.
- `payment_status_history.seq` (added 2026-08-04) is an `AUTO_INCREMENT` tiebreaker used
  only for `ORDER BY changed_at ASC, seq ASC` in `GET /api/payments/{id}/history` — since
  `changed_at` is second-precision, multiple transitions landing in the same second would
  otherwise sort non-deterministically/incorrectly. Never returned in any API response.
- `amount` is stored with exactly 2 decimal places (rupees + paise). Reject/round-reject
  requests with more than 2 decimal places at validation time — never silently round.
- `id` (and every other UUID column) is **always generated server-side**
  (`java.util.UUID.randomUUID()`), never accepted from the client. Clients only ever
  supply `idempotencyKey` as their own correlation value.
- All `TIMESTAMP` columns are stored and returned in **UTC**. Frontend pages are
  responsible for any local-time display conversion; the API never converts timezones.

## 8. State and Transition Rules

Allowed payment states:
- `CREATED`
- `VALIDATED`
- `SENT`
- `COMPLETED`
- `FAILED`

Transition diagram:

```mermaid
stateDiagram-v2
    [*] --> CREATED
    CREATED --> VALIDATED
    VALIDATED --> SENT
    SENT --> COMPLETED
    SENT --> FAILED
    COMPLETED --> [*]
    FAILED --> [*]
```

Rules:
- `CREATED -> VALIDATED`: validation rules pass (M1 validates on creation; M2 owns the
  `process` endpoint that performs the actual transition).
- `VALIDATED -> SENT`: simulated dispatch to a payment processor (no real gateway).
- `SENT -> COMPLETED` or `SENT -> FAILED`: terminal outcome of simulated processing —
  see Section 8.2 for exactly how this outcome is decided (it is not automatic/random).
- `COMPLETED` and `FAILED` are terminal — no further transitions allowed, and there is no
  automatic retry. If a business wants to retry a `FAILED` payment, that means a brand
  new `POST /api/payments` call with a new `idempotencyKey` — this system does not
  auto-create retry attempts.
- A `type = REFUND` payment goes through this exact same state machine as a normal
  payment (see Section 8.1) — it is not instantly `COMPLETED`.
- Every transition (via `POST /api/payments/{id}/process`) must append one row to
  `payment_status_history` with `from_status`, `to_status`, `changed_at`, `triggered_by`.
- Any attempt to transition a payment already in a terminal state must fail with
  `InvalidStatusTransitionException` (M2 owns this rule; M3 owns the exception's error
  mapping/response shape).
- Refunds (M3) are a separate `type = REFUND` payment row, not a state on the original
  payment. A refund may only be created when the original payment's status is
  `COMPLETED`. A `type = REFUND` payment can **never** itself be the target of another
  refund (no refund-of-refund chains) — see Section 8.1.

### 8.1 Refund Mechanism (Full Rules, Plain English)

This is the complete, unambiguous definition of how a refund works end to end:

1. A refund is requested via `POST /api/payments/{originalId}/refund` against an
   **existing, `COMPLETED`, `type = PAYMENT`** row. Attempting this against any other
   status, or against a payment that is already `type = REFUND`, always fails with
   `InvalidRefundStateException` (`409`).
2. A refund is **not** an in-place field on the original payment and it does **not**
   instantly complete. Creating a refund inserts a **brand-new `payments` row** with:
   - `type = REFUND`
   - `original_payment_id = <the original payment's id>`
   - `status = CREATED` (its own fresh lifecycle start)
   - its own `payment_status_history` row: `null -> CREATED`, `triggered_by = SYSTEM`.
3. That new refund row must then be advanced through the **same** state machine as any
   other payment, via the **same** `POST /api/payments/{refundId}/process` endpoint
   (`CREATED -> VALIDATED -> SENT -> COMPLETED`/`FAILED`), until it reaches `COMPLETED`.
   A refund is only considered "done" once its own status is `COMPLETED`.
4. **Amount limit is cumulative, not just against the single request:** a refund's
   `amount` must be `> 0`, and `(sum of amounts of all prior — any status — refund rows
   for this `original_payment_id`) + (this new refund's amount)` must be
   `<= original payment.amount`. This prevents over-refunding across multiple partial
   refunds. Violating this returns `InvalidRefundStateException` (`409`).
   - Example: a ₹1000 original payment can have a ₹400 refund and later a ₹600 refund
     (total ₹1000, allowed), but a third refund of any amount after that is rejected.
   - A refund whose amount exactly equals the original payment's full remaining
     refundable amount is a valid "full refund" — no special-cased field is needed.
5. Refunding a `REFUND` row itself (i.e. `original_payment_id` pointing at a payment
   where `type = REFUND`) is always rejected with `InvalidRefundStateException`,
   regardless of that refund row's own status.

### 8.2 Process Outcome Rule (What Decides `SENT -> COMPLETED` vs `SENT -> FAILED`)

Since there is no real payment gateway, the outcome of the one branching step in the
state machine is **caller-controlled, not random**, so behavior is deterministic and
testable:

- For `CREATED -> VALIDATED` and `VALIDATED -> SENT`, `POST /api/payments/{id}/process`
  always advances to the single next status — there is no choice to make.
- For the `SENT -> {COMPLETED | FAILED}` step only, the request body may include an
  optional `targetStatus` field (`"COMPLETED"` or `"FAILED"`). If omitted, the default
  is `"COMPLETED"`.
- If `targetStatus` is `"FAILED"`, the request **must** also include an `errorCode`
  string (stored on the payment's `error_code` column and in the history row's `note`).
  Omitting `errorCode` while requesting `"FAILED"` is a `400 VALIDATION_ERROR`.
- Supplying `targetStatus` on any transition other than `SENT -> *` (or supplying a value
  other than `COMPLETED`/`FAILED`) is rejected as `InvalidStatusTransitionException`
  (`409`).
- See Section 10.5 for the exact `ProcessRequest` JSON shape.

### 8.3 Concurrency & Transaction Rules

Because this project uses raw Spring JDBC (no ORM/JPA managing transactions for you),
every module owner must wrap the following in an explicit `@Transactional` method (or
equivalent single DB transaction) — this is not optional polish, it's required for
correctness:

- **Create + idempotency check (M1/M3):** never "SELECT to check, then INSERT" as two
  separate steps with a race window. Rely on the `idempotency_key` UNIQUE constraint:
  attempt the insert, and on a duplicate-key violation, re-fetch and return the existing
  row as the `200 OK` short-circuit response. Both the existence check and the insert
  must happen inside one transaction.
- **Status transition + history insert (M2):** the `payments.status`/`updated_at` update
  and the new `payment_status_history` row must be written in a single transaction. Use
  a conditional update (e.g. `UPDATE payments SET status = :new WHERE id = :id AND
  status = :expectedCurrent`) and check the affected row count — if it's `0`, another
  concurrent request already moved the payment, so throw `InvalidStatusTransitionException`
  rather than blindly overwriting.
- **Refund creation (M3):** computing the cumulative refunded total (Section 8.1, rule 4)
  and inserting the new refund row must happen in the same transaction, so two concurrent
  refund requests against the same payment can't both pass the amount check.

## 9. Roles & Module Ownership — End-to-End Responsibility Matrix

Each module below lists everything that module's owner is responsible for: backend
files, APIs (with request/response contracts — see Section 10 for full JSON shapes),
frontend pages, and the concrete task list for each phase. Read your module's section
top to bottom and you have your complete assignment.

---

### M1 — Creation & Validation — Owner: Poornima

**Mission:** Let a user submit a new payment and retrieve a payment by id, with correct
input validation.

**Backend files owned:**
- `payment/controller/PaymentController.java` — `POST /api/payments`, `GET /api/payments/{id}`
- `payment/service/PaymentService.java` / `PaymentServiceImpl.java` — `createPayment()`, `getPayment()`
- `payment/repository/PaymentRepository.java` / `JdbcPaymentRepository.java` — insert/select payment rows
- `payment/dto/CreatePaymentRequest.java`, `PaymentResponse.java`
- `payment/model/Payment.java`, `PaymentStatus.java`, `PaymentType.java`

**APIs owned:**
| API | Method | Purpose |
|---|---|---|
| `/api/payments` | POST | Create a new payment (initial status `CREATED`). Shares idempotency short-circuit behavior owned by M3. |
| `/api/payments/{id}` | GET | Fetch a single payment by id (shared read path also used by M2/M3). |

**In plain English:** the create endpoint is "open a new payment record" — it does not
move money or contact anything external, it just validates the input and writes one row
with `status = CREATED`. The get-by-id endpoint is a plain lookup used everywhere else
in the system (M2's process/history screens and M3's refund screen all start by fetching
a payment by id first).

**Validation rules (Bean Validation, on `CreatePaymentRequest`):**
- `sourceAccount`, `destinationAccount`: required, non-blank.
- `sourceAccount != destinationAccount`.
- `amount`: required, `> 0`.
- `currency`: required, 3-letter ISO code. Only `INR` is used/seeded for now (see
  Section 5 — multi-currency is out of scope); the field stays a free 3-letter code so
  it does not need a schema change if multi-currency is added later.
- `idempotencyKey`: required, non-blank (consumed by M3's idempotency logic on the create path).

**Frontend owned:**
- `frontend/frontend-user/index.html` — "new payment" form (source/destination account,
  amount, currency, submit). Calls `POST /api/payments`.
- Uses shared design tokens/components from M4 (`frontend/frontend-shared/`).

**Phase 1 tasks (backbone):**
- [x] Create controller/service/repository/dto class skeletons for the create/get flow.
- [x] Stub methods throw `UnsupportedOperationException` until Phase 2.
- [x] Scaffold `frontend/frontend-user/index.html` with the form markup (no wired JS logic yet).

**Phase 2 tasks (implementation):**
- [ ] Implement `POST /api/payments` (validation + insert + initial history row `null -> CREATED`).
- [ ] Implement `GET /api/payments/{id}` (404 via `PaymentNotFoundException` if missing).
- [ ] Wire `index.html` JS to call the real API and show success/validation errors.

**Depends on:** M3 for idempotency short-circuit behavior on `POST /api/payments`;
M4 for shared CSS/JS design tokens and the lifecycle timeline component (used to show
payment status right after creation).

**Definition of done:** Both endpoints implemented and covered by unit + repository
tests (Section 15); user can create a payment via the UI and see it return a payment id.

---

### M2 — Status Engine & Audit Trail — Owner: Neha

**Mission:** Move a payment through its lifecycle and expose a full, append-only audit
trail of every transition.

**Backend files owned:**
- `payment/controller/PaymentController.java` — `POST /api/payments/{id}/process`, `GET /api/payments/{id}/history`
- `payment/service/PaymentService.java` / `PaymentServiceImpl.java` — `processTransition()`, `getHistory()`
- `payment/repository/PaymentStatusHistoryRepository.java` / `JdbcPaymentStatusHistoryRepository.java`
- `payment/model/PaymentStatusHistory.java`
- `payment/dto/PaymentHistoryEntry.java`, `ProcessRequest.java`

**APIs owned:**
| API | Method | Purpose |
|---|---|---|
| `/api/payments/{id}/process` | POST | Advance a payment to the next valid status per Section 8's state machine. |
| `/api/payments/{id}/history` | GET | Return the full ordered status-history timeline for a payment. |

**In plain English:** the process endpoint is the only way a payment ever changes
status — there is no background job, so a human or test script must call it once per
step (`CREATED->VALIDATED`, then `VALIDATED->SENT`, then `SENT->COMPLETED`/`FAILED`).
The history endpoint is a read-only, ordered log of every step a payment has ever taken
— think of it as the payment's timeline/receipt trail.

**Business rules:**
- Enforces the transition table in Section 8, including the caller-controlled
  `SENT -> COMPLETED`/`FAILED` outcome rule defined in **Section 8.2**. Any invalid
  transition throws `InvalidStatusTransitionException` (error-mapped by M3).
- Every successful transition appends exactly one `payment_status_history` row and
  updates `payments.status` + `updated_at` in the same operation. This must be one
  database transaction — see **Section 8.3** for the exact concurrency-safe pattern
  (conditional update + row-count check).
- `FAILED` is fully terminal — no automatic retry payment is ever created by this
  system (Section 8).
- History is read-only and append-only — never updated or deleted.

**Frontend owned:**
- `frontend/frontend-business/audit.html` — business-facing audit trail screen; lists a
  payment's full history timeline. Calls `GET /api/payments/{id}/history`.
- Uses the shared `lifecycle-timeline.js` component (owned by M4) to render the
  timeline visually.

**Phase 1 tasks (backbone):**
- [x] Create status-transition and history skeleton files (service + repository stubs).
- [x] Add transition method stubs only (no rule logic yet).
- [x] Scaffold `frontend/frontend-business/audit.html` with static markup for a timeline.

**Phase 2 tasks (implementation):**
- [ ] Implement `processTransition()` with the full state machine from Section 8.
- [ ] Implement `GET /api/payments/{id}/history` returning ordered entries (oldest first).
- [ ] Wire `audit.html` to fetch and render a real payment's history via
  `lifecycle-timeline.js`.

**Depends on:** M1 for the initial `CREATED` row and payment existence; M3 for the
`InvalidStatusTransitionException` error response shape; M4 for the shared timeline
component and design tokens.

**Definition of done:** State machine fully enforced with negative-path tests (invalid
transition attempts rejected); history endpoint returns complete, correctly ordered
timelines; audit UI renders a real payment's lifecycle.

---

### M3 — Idempotency, Errors, Refund — Owner: Tharan

**Mission:** Prevent duplicate payment creation, provide consistent error responses
across the whole API, and let a completed payment be refunded.

**Backend files owned:**
- `payment/controller/PaymentController.java` — `POST /api/payments/{id}/refund`
- `payment/service/PaymentService.java` / `PaymentServiceImpl.java` — `createRefund()`, idempotency lookup logic used inside `createPayment()`
- `payment/dto/RefundRequest.java`, `ErrorResponse.java`
- `common/exception/GlobalExceptionHandler.java`
- `common/exception/PaymentNotFoundException.java`, `InvalidStatusTransitionException.java`, `DuplicatePaymentException.java`, `InvalidRefundStateException.java`

**APIs owned:**
| API | Method | Purpose |
|---|---|---|
| `/api/payments/{id}/refund` | POST | Create a refund (`type = REFUND`) against a `COMPLETED` payment. |

**In plain English:** the refund endpoint does not "undo" or delete the original
payment — it creates a **brand-new payment row** of `type = REFUND` linked back to the
original, which then has to run through the exact same lifecycle (`process` calls) as
any other payment before it's actually `COMPLETED`. See **Section 8.1** for the full,
step-by-step refund mechanism (including the cumulative-amount rule across multiple
partial refunds, and why a refund can never itself be refunded).

Also owns the **cross-cutting behavior** used by every other endpoint:
- Idempotency lookup performed during M1's `POST /api/payments` (by `idempotency_key`).
- The `GlobalExceptionHandler` and `ErrorResponse` shape used by all controllers.

**Business rules:**
- Idempotency: if `POST /api/payments` is called with an `idempotency_key` that already
  exists, do **not** create a new row — return `HTTP 200` with the original payment
  resource (see Section 1's global policy carried over from the API inventory). This
  check and the insert must be one transaction — see **Section 8.3**.
- Refund: only allowed when the original payment's `status = COMPLETED` and its
  `type = PAYMENT` (never `REFUND`). Refund amount must be `> 0`, and the **cumulative**
  total of this payment's refunds must stay `<= original payment.amount` — see
  **Section 8.1** for the full rule and worked example. Creates a new `payments` row
  with `type = REFUND`, `original_payment_id = <original id>`, initial
  `status = CREATED`, and its own history row `null -> CREATED`.
- Refunding a non-`COMPLETED` payment, or a payment that is already `type = REFUND`,
  throws `InvalidRefundStateException`.
- All exceptions map to a single consistent `ErrorResponse` JSON shape (Section 10)
  through `GlobalExceptionHandler` (`@ControllerAdvice`).

**Frontend owned:**
- `frontend/frontend-user/detail.html` — payment detail page with a "Refund" action
  button (visible only when status is `COMPLETED`). Calls `POST /api/payments/{id}/refund`.

**Phase 1 tasks (backbone):**
- [x] Create refund and idempotency skeleton methods (stubs only).
- [x] Create all exception classes and `GlobalExceptionHandler` skeleton (no mapping logic yet).
- [x] Scaffold `frontend/frontend-user/detail.html` with static payment detail + refund button markup.

**Phase 2 tasks (implementation):**
- [ ] Implement idempotency short-circuit inside M1's create flow.
- [ ] Implement `POST /api/payments/{id}/refund` with the rules above.
- [ ] Complete `GlobalExceptionHandler` mapping every custom exception to an `ErrorResponse`
      with the correct HTTP status code (Section 10.7).
- [ ] Wire `detail.html` to show payment details and trigger a real refund call.

**Depends on:** M1's create flow (to inject idempotency check); M2's status field (to
gate refund eligibility); M4 for shared design tokens on `detail.html`.

**Definition of done:** Duplicate `idempotency_key` never creates a second row;
refund only succeeds against `COMPLETED` payments; every error case across the whole
API returns the same `ErrorResponse` shape with an appropriate status code.

---

### M4 — Query API, Lifecycle UI, Design System, API Docs — Owner: Karuna

**Mission:** Let payments be listed/searched, provide the shared UI building blocks used
by every other frontend page, and publish API documentation.

**Backend files owned:**
- `payment/controller/PaymentQueryController.java` — `GET /api/payments`
- `config/OpenApiConfig.java` — springdoc-openapi configuration

**APIs owned:**
| API | Method | Purpose |
|---|---|---|
| `/api/payments` | GET | List/filter/search payments (by `status`, `type`, `sourceAccount`, `destinationAccount`, date range; paginated). |

**In plain English:** this is the "browse/search everything" endpoint used by the
business dashboard — no id required, just optional filters, returned a page at a time,
newest first by default.

**Query parameters (all optional, combinable):**
`status`, `type`, `sourceAccount`, `destinationAccount`, `fromDate`, `toDate`, `page`
(default 0), `size` (default 20).

**Frontend owned (shared design system + business dashboards):**
- `frontend/frontend-shared/design-tokens.css` — shared colors, spacing, typography,
  status-badge colors (one badge style per `PaymentStatus`), used by every page in both
  `frontend-user` and `frontend-business`.
- `frontend/frontend-shared/lifecycle-timeline.js` — reusable vanilla-JS component that
  renders a `payment_status_history` array as a visual timeline. Consumed by M2's
  `audit.html` and M1's `history.html`.
- `frontend/frontend-business/dashboard.html` — business dashboard: filterable/searchable
  payments list, calls `GET /api/payments`.
- `frontend/frontend-user/history.html` — user-facing simplified history view (reuses
  `lifecycle-timeline.js`).

**Phase 1 tasks (backbone):**
- [x] Create `PaymentQueryController` skeleton (stub method only).
- [x] Add `OpenApiConfig` skeleton (empty `@Bean` for `OpenAPI` metadata).
- [x] Create `frontend/frontend-shared/design-tokens.css` with base tokens (colors, spacing).
- [x] Create `frontend/frontend-shared/lifecycle-timeline.js` with an empty component shell.
- [x] Scaffold `frontend/frontend-business/dashboard.html` and `frontend/frontend-user/history.html`.

**Phase 2 tasks (implementation):**
- [ ] Implement `GET /api/payments` filtering + pagination against JDBC.
- [ ] Finish `lifecycle-timeline.js` rendering logic and wire it into `audit.html` and `history.html`.
- [ ] Finish `dashboard.html` list/filter UI consuming the real query API.
- [ ] Finalize OpenAPI docs (`/swagger-ui.html` reachable, all endpoints documented).

**Depends on:** All other modules' endpoints being stable enough to document; M1-M3's
pages consuming the shared CSS/JS files without modification.

**Definition of done:** List/filter API returns correct paginated results; every other
page in the project visually uses `design-tokens.css`; Swagger UI lists all six endpoints
correctly.

---

## 10. API Contract Reference

Master list — see Section 9 for who owns each one and the frontend page that consumes it.
The **Status** column is the per-endpoint implementation status (finer-grained than the
module-level Section 2 dashboard) — update it as each endpoint is actually built and
tested, independent of the rest of its module.

| API | Method | Owner | Purpose | Status |
|---|---|---|---|---|
| `/api/payments` | POST | M1 (+M3 idempotency) | Create payment | TESTED |
| `/api/payments/{id}` | GET | M1 | Fetch payment by id | TESTED |
| `/api/payments` | GET | M4 | List/filter/search payments | TESTED (on `feature/m4-lifecycle-ui`, not yet merged to `main`) |
| `/api/payments/{id}/history` | GET | M2 | Get full status history timeline | TESTED |
| `/api/payments/{id}/process` | POST | M2 | Advance payment to next valid state | TESTED |
| `/api/payments/{id}/refund` | POST | M3 | Create refund against a completed payment | TESTED |

Endpoint status values: `NOT_IMPLEMENTED`, `IMPLEMENTED` (works, not yet tested),
`TESTED` (has passing unit/repository tests per Section 15).

Global API policy:
- No new endpoints outside this table without updating this spec first.
- Duplicate idempotency default policy: return `HTTP 200` with the original payment resource.
- All error responses use the single `ErrorResponse` shape (10.7).
- CORS: the backend must allow cross-origin requests from local dev frontend origins
  (e.g. a static file server on `http://localhost:5500` or similar) — see `CorsConfig.java`
  in Section 11.1. Without this, the plain-HTML frontend's `fetch()` calls will fail in
  the browser even though the API works fine from Postman/curl.

### 10.1 `POST /api/payments`

**What it does (plain English):** opens a brand-new payment record. Nothing is actually
"sent" yet — this only validates the input and writes one row with `status = CREATED`.
If the same `idempotencyKey` was already used, it does **not** create a second row; it
just hands back the original payment.

Request (`CreatePaymentRequest`):
```json
{
  "sourceAccount": "ACC-1001",
  "destinationAccount": "ACC-2002",
  "amount": 250.00,
  "currency": "INR",
  "idempotencyKey": "client-generated-uuid-or-key"
}
```

Response `201 Created` (new payment) or `200 OK` (duplicate `idempotencyKey`) — `PaymentResponse`:
```json
{
  "id": "b1e7...uuid",
  "idempotencyKey": "client-generated-uuid-or-key",
  "sourceAccount": "ACC-1001",
  "destinationAccount": "ACC-2002",
  "amount": 250.00,
  "currency": "INR",
  "status": "CREATED",
  "errorCode": null,
  "type": "PAYMENT",
  "originalPaymentId": null,
  "createdAt": "2026-08-04T10:00:00Z",
  "updatedAt": "2026-08-04T10:00:00Z"
}
```

### 10.2 `GET /api/payments/{id}`

**What it does (plain English):** a plain lookup by id — no side effects. Used
everywhere else (process screen, history screen, refund screen) to load a payment
before acting on it.

Response `200 OK` — `PaymentResponse` (same shape as 10.1). `404` if not found.

### 10.3 `GET /api/payments`

**What it does (plain English):** the "browse/search everything" endpoint. All filters
are optional and combine with AND logic; results are always paginated.

Query params: `status`, `type`, `sourceAccount`, `destinationAccount`, `fromDate`,
`toDate`, `page` (default `0`), `size` (default `20`).

- An unrecognized/invalid value for `status` or `type` (i.e. not one of the enum values)
  returns `400 VALIDATION_ERROR` — never a `500`.
- `page` must be `>= 0`; `size` must be between `1` and `100` inclusive. Values outside
  these bounds return `400 VALIDATION_ERROR` — never a `500` (added 2026-08-05).
- Default sort order is `created_at DESC` (newest payments first) when no explicit
  ordering is requested. There is no sort-by query param in this MVP — a fixed default
  is enough.

Response `200 OK`:
```json
{
  "content": [ /* array of PaymentResponse */ ],
  "page": 0,
  "size": 20,
  "totalElements": 42
}
```

### 10.4 `GET /api/payments/{id}/history`

**What it does (plain English):** returns the full, ordered timeline of every status
change a payment has gone through — its audit trail/receipt log. Oldest entry first.

Response `200 OK` — array of `PaymentHistoryEntry`:
```json
[
  {
    "fromStatus": null,
    "toStatus": "CREATED",
    "changedAt": "2026-08-04T10:00:00Z",
    "triggeredBy": "SYSTEM",
    "note": null
  },
  {
    "fromStatus": "CREATED",
    "toStatus": "VALIDATED",
    "changedAt": "2026-08-04T10:01:00Z",
    "triggeredBy": "SYSTEM",
    "note": null
  }
]
```

### 10.5 `POST /api/payments/{id}/process`

**What it does (plain English):** advances a payment exactly one step in its lifecycle
(Section 8). There is no auto-advance/background job — something has to call this
endpoint once per step. For every step except the final one, there's nothing to choose;
for the final `SENT -> {COMPLETED|FAILED}` step, the caller decides the outcome via
`targetStatus` (see Section 8.2 for the full rule).

Request (`ProcessRequest`, all fields optional unless noted):
```json
{
  "targetStatus": "FAILED",
  "errorCode": "INSUFFICIENT_FUNDS",
  "note": "manual test of failure path"
}
```
- `targetStatus`: optional. Only meaningful when the payment's current status is `SENT`
  (values `"COMPLETED"` or `"FAILED"`, default `"COMPLETED"` if omitted). Ignored/invalid
  on any other current status — see Section 8.2.
- `errorCode`: **required if** `targetStatus = "FAILED"`; otherwise omit it.
- `note`: optional free-text stored on the resulting history row.

Response `200 OK` — updated `PaymentResponse`. `409` (`InvalidStatusTransitionException`)
if the payment is already in a terminal state, or if `targetStatus`/`errorCode` are used
incorrectly per Section 8.2. `400 VALIDATION_ERROR` if `targetStatus = "FAILED"` is sent
without `errorCode`.

### 10.6 `POST /api/payments/{id}/refund`

**What it does (plain English):** does **not** modify or delete the original payment.
It creates a brand-new `type = REFUND` payment row linked to the original, which then
has to be advanced through `process` calls like any other payment before it's actually
`COMPLETED`. See Section 8.1 for the full step-by-step mechanism.

Request (`RefundRequest`):
```json
{
  "amount": 100.00,
  "reason": "customer requested"
}
```

Response `201 Created` — new refund `PaymentResponse` (`type: "REFUND"`,
`originalPaymentId` set, `status: "CREATED"`). `409` (`InvalidRefundStateException`) if:
- the original payment is not `COMPLETED`, or is itself `type = REFUND`; or
- `amount <= 0`; or
- `(sum of existing refunds against this original) + amount` exceeds the original
  payment's `amount` (Section 8.1, rule 4).

### 10.7 Error Response Shape (`ErrorResponse`, all endpoints)

```json
{
  "timestamp": "2026-08-04T10:00:00Z",
  "status": 404,
  "errorCode": "PAYMENT_NOT_FOUND",
  "message": "Payment with id ... was not found",
  "path": "/api/payments/{id}"
}
```

| Exception | HTTP Status | errorCode |
|---|---|---|
| `PaymentNotFoundException` | 404 | `PAYMENT_NOT_FOUND` |
| `InvalidStatusTransitionException` | 409 | `INVALID_STATUS_TRANSITION` |
| `DuplicatePaymentException` | 200 (short-circuit, not an error) | — |
| `InvalidRefundStateException` | 409 | `INVALID_REFUND_STATE` |
| Validation failure (Bean Validation, incl. bad query params / missing `errorCode`) | 400 | `VALIDATION_ERROR` |

## 11. Phase 1 — Backbone Setup (Now)

Create file skeletons and stubs only in this phase. Do not implement complete business
logic yet — stub methods should throw `UnsupportedOperationException`.

### 11.1 Backend Directory Hierarchy

Target backend root: `backend/src/main/java/com/bnd/payment_processing/`

```
backend/
  pom.xml
  src/
    main/
      java/com/bnd/payment_processing/
        PaymentProcessingApplication.java
        payment/
          controller/
            PaymentController.java
            PaymentQueryController.java
          service/
            PaymentService.java
            PaymentServiceImpl.java
          repository/
            PaymentRepository.java
            JdbcPaymentRepository.java
            PaymentStatusHistoryRepository.java
            JdbcPaymentStatusHistoryRepository.java
          model/
            Payment.java
            PaymentStatusHistory.java
            PaymentStatus.java
            PaymentType.java
          dto/
            CreatePaymentRequest.java
            PaymentResponse.java
            PaymentHistoryEntry.java
            ProcessRequest.java
            RefundRequest.java
            ErrorResponse.java
        common/
          exception/
            GlobalExceptionHandler.java
            PaymentNotFoundException.java
            InvalidStatusTransitionException.java
            DuplicatePaymentException.java
            InvalidRefundStateException.java
        config/
          JdbcConfig.java
          OpenApiConfig.java
          CorsConfig.java
      resources/
        application.properties
        schema.sql
        data.sql               (optional seed, only if approved)
    test/
      java/com/bnd/payment_processing/
        payment/
          controller/
          service/
          repository/
```

### 11.2 Frontend Directory Hierarchy

```
frontend/
  frontend-shared/
    design-tokens.css
    lifecycle-timeline.js
  frontend-user/
    index.html       (M1 - new payment form)
    history.html      (M4 - user history view)
    detail.html       (M3 - payment detail + refund action)
  frontend-business/
    dashboard.html    (M4 - filterable payments list)
    audit.html        (M2 - audit trail / history timeline)
```

Frontend conventions:
- No build tools, no bundlers, no frameworks — plain `<script>`/`<link>` tags.
- Every page includes `frontend-shared/design-tokens.css` for consistent styling.
- Shared behavior (e.g. rendering a history timeline) lives in
  `frontend-shared/lifecycle-timeline.js` and is imported by any page that needs it —
  do not copy-paste timeline rendering logic between pages.
- Each `frontend-user`/`frontend-business` page has its own small dedicated JS file
  (e.g. `index.js` next to `index.html`) for page-specific logic (form submission,
  fetch calls) — keep page logic out of the shared files.

### 11.3 Shared Setup Tasks

- [x] Create `pom.xml` with only the whitelisted dependencies (Section 6.2).
- [x] Create `schema.sql` with the canonical tables from Section 7, unchanged.
- [x] Add `docker-compose.yml` for local MySQL (used by `docker compose up -d`).
- [x] Add `application.properties` with JDBC datasource config pointing at local MySQL.
- [x] Add `CorsConfig.java` allowing the local frontend dev origin(s) to call the API
      (see Section 10's global API policy) — without it, browser `fetch()` calls from
      `frontend/` will fail with CORS errors even though the API itself works.
- [x] Generate and commit the shared `data.sql` seed dataset (Section 11.5) so every
      module owner builds and tests against identical data.

### 11.4 Per-Module Phase 1 Tasks

See each module's "Phase 1 tasks (backbone)" checklist in Section 9. Summary:
- **M1:** controller/service/repository/dto skeletons for create/get; scaffold `index.html`.
- **M2:** status-transition/history skeleton files; scaffold `audit.html`.
- **M3:** refund/idempotency skeleton methods; all exception classes + handler skeleton; scaffold `detail.html`.
- **M4:** query controller skeleton; `OpenApiConfig` skeleton; `design-tokens.css` +
  `lifecycle-timeline.js` shells; scaffold `dashboard.html` and `history.html`.

### 11.5 Dataset Generation (Shared Seed Data)

All four modules build and test against **one shared, generated, mock dataset** — not a
real-world dataset (there is no real gateway integration, and Section 4 forbids handling
real account/card values) and not ad-hoc per-developer data. This is a Phase 1 shared
deliverable, same as `schema.sql` — nobody starts Phase 2 work against their own local
data.

**Ownership:** Team-owned (not a single module owner). Whoever picks it up in Phase 1
generates it once, commits it, and every module owner pulls latest before Phase 2 work.

**File location and loading mechanism:**
- `backend/src/main/resources/data.sql` — plain SQL `INSERT` statements only (no DDL;
  DDL stays in `schema.sql`).
- Loaded automatically on app startup via Spring Boot's built-in SQL script
  initialization (`spring.sql.init.mode=always` in `application.properties`), which runs
  `schema.sql` then `data.sql` against the local MySQL instance every time the app starts.
- No custom loader code, no separate seeding endpoint, no generation logic at runtime —
  it is a static, checked-in file so it is byte-for-byte identical for every developer.

**Generation approach:**
- Write it as a one-time script (e.g. a small Python or Node script, not committed to
  `backend/`, used only to produce the `INSERT` statements) or hand-author the SQL
  directly — either way, the output committed to `data.sql` must be **deterministic**:
  fixed UUIDs, fixed timestamps, no `RANDOM()`/`NOW()` calls inside the SQL itself, so a
  fresh `docker compose up -d` + app restart always reproduces the exact same rows for
  every teammate.
- Target volume: **at least 400-600** `payments` rows with matching
  `payment_status_history` rows (more if easy to generate) — the dataset should feel
  exhaustive, not a minimal smoke-test sample, so pagination, filtering, and search all
  have real volume to work against, and rare edge cases show up multiple times rather
  than once each.

**Required edge-case coverage (the dataset must include all of these):**
- All 5 statuses represented (`CREATED`, `VALIDATED`, `SENT`, `COMPLETED`, `FAILED`),
  including payments parked at each intermediate state (so `process` can be tested from
  any starting point).
- Full multi-row `payment_status_history` chains for terminal payments (not just a
  single current-status row).
- At least one repeated `idempotency_key` across two attempted inserts, to prove M3's
  duplicate short-circuit path returns the original resource. Note: `idempotency_key` is
  `UNIQUE` (Section 7), so this cannot be represented as two rows inside `data.sql`
  itself — it is exercised by calling `POST /api/payments` twice with the same
  `idempotencyKey` against a seeded payment during Phase 2/3 manual or integration
  testing, not by seeding duplicate rows.
- At least one full refund and one partial refund: `type = REFUND` rows with
  `original_payment_id` pointing at a `COMPLETED` payment.
- At least one `COMPLETED` payment with **no** refund yet, left as a valid manual refund
  target.
- At least one non-`COMPLETED` payment intended for manually testing the refund
  rejection path (`InvalidRefundStateException`) — do not pre-seed an invalid refund row
  itself, since that would violate the schema invariants in Section 7.
- Multiple partial refunds against the same original payment where the running total of
  refunded amounts stays `<=` the original amount, plus at least one refund whose amount
  exactly equals the original payment's amount (full refund boundary case).
- **Single currency only: `INR` on every row.** Do not seed any other currency — see
  Section 5 (multi-currency is out of scope, unassigned).
- A wide spread of amounts: very small (e.g. `1.00`), typical (hundreds/thousands), and
  large (e.g. `500000.00`+), to exercise sorting/formatting and validation boundaries.
- At least one `FAILED` payment per distinct `errorCode` listed in Section 10.7 (e.g.
  one `FAILED` row per relevant error code), so M3's error mapping has real examples to
  display.
- Dozens of distinct `source_account`/`destination_account` values, with several accounts
  deliberately reused across many payments (both as source and as destination), so M4's
  filters have real overlap to query against and dashboards look realistic rather than
  sparse.
- `created_at`/`updated_at` timestamps spread across several weeks (not just several
  days), with some days having many payments and others having none, so M4's
  `fromDate`/`toDate` filtering and pagination both have meaningful, uneven ranges to test.
- Mixed `triggered_by` values in history rows (e.g. `SYSTEM` plus at least one other
  value) and a mix of populated vs. `null` `note` fields.
- A handful of payments that sit in the same status but were created at very close
  timestamps (e.g. seconds apart), to test correct ordering/tie-breaking in list and
  history endpoints.

**Phase 1 task:**
- [x] Generate `data.sql` covering every edge case above at the target volume, commit it
      alongside `schema.sql`, and set `spring.sql.init.mode=always` in
      `application.properties` so it loads automatically for every developer.
      (Generated via `scripts/generate_data_sql.py`, seeded/deterministic, 491 payments /
      1661 history rows.)

## 12. Phase 2 — Module Implementation

Each owner implements their module's real logic once Phase 1 backbone for that area
exists. See each module's "Phase 2 tasks (implementation)" checklist in Section 9 for
full detail. Summary:

- **M1:** Implement `POST /api/payments` and `GET /api/payments/{id}`; wire `index.html`.
- **M2:** Implement transition rules and history endpoint; wire `audit.html`.
- **M3:** Implement idempotency short-circuit and refund endpoint; complete error mapping; wire `detail.html`.
- **M4:** Implement `GET /api/payments` filtering; finish shared JS/CSS; wire `dashboard.html`/`history.html`; finalize API docs.

## 13. Phase 3 — Integration and Validation

- Merge module branches incrementally.
- Run integration tests against the shared schema.
- Validate cross-module API compatibility (e.g. M3's refund depends on M2's status field
  being reliably `COMPLETED`).
- Verify every frontend page consumes the agreed endpoint contracts from Section 10.
- Confirm Swagger UI (`/swagger-ui.html`) documents all six endpoints correctly.

## 14. Frontend Architecture

Two static, framework-free apps plus a shared layer — see Section 11.2 for the file tree.

- **`frontend-user/`** — the end-customer app: create a payment (`index.html`), view a
  simplified history (`history.html`), view details and trigger a refund (`detail.html`).
- **`frontend-business/`** — the internal/business-operator app: searchable payments
  dashboard (`dashboard.html`), full audit trail viewer (`audit.html`).
- **`frontend-shared/`** — design tokens (`design-tokens.css`) and the reusable
  `lifecycle-timeline.js` component, consumed by both apps. No page-specific logic
  belongs here.

Modularity rule: page-specific JS lives next to its HTML file (e.g. `dashboard.js` beside
`dashboard.html`); anything reused across 2+ pages moves into `frontend-shared/`.

## 15. Testing Baseline

Required:
- Unit tests per module for owned logic.
- Negative path tests for invalid state transitions, invalid refund states, and
  idempotency conflicts.
- Repository tests for JDBC SQL behavior.

Environment prerequisite:
- MySQL must be available locally for integration tests.
- Recommended standard project command for all members: `docker compose up -d`.

## 16. Branching, Review, and Merge Rules

Branch naming:
- `feature/m1-payment-creation`
- `feature/m2-status-engine`
- `feature/m3-refunds`
- `feature/m4-lifecycle-ui`

PR policy:
- Small PRs only.
- One reviewer minimum.
- No merge without passing tests relevant to the changed module.

## 17. Security and Logging Guardrails

- Never log full account/card values.
- Mask sensitive fields in responses/logs where applicable.
- Do not store secrets in code.

## 18. Progress Log

Append a dated entry here every time meaningful progress happens. This is the project's
history — keep entries short and factual.

| Date | Entry |
|---|---|
| 2026-08-04 | Spec rewritten as the unified living build spec + progress log (v2.0). `backend/` and `frontend/` are still empty — Phase 1 backbone has not started yet. |
| 2026-08-04 | v2.1: closed MVP gaps — added explicit Refund Mechanism rules (Section 8.1, incl. cumulative refund-amount check and refund-of-refund ban), the Process Outcome Rule for `SENT` transitions (Section 8.2, `ProcessRequest` with `targetStatus`/`errorCode`), Concurrency & Transaction rules (Section 8.3), schema precision (`DECIMAL(18,2)`, server-generated UUIDs, UTC timestamps), a `CorsConfig.java` task for local frontend/backend calls, per-endpoint implementation status tracking in Section 10, and plain-English "what it does" summaries for every API. |
| 2026-08-04 | Phase 1 backbone complete. Backend: Maven skeleton (`pom.xml`, Java 25, Spring Boot 4.1.0, whitelisted deps only) builds cleanly with `mvn compile`; all model/dto/exception classes created; `PaymentController`/`PaymentQueryController`, `PaymentService(Impl)`, `PaymentRepository`/`JdbcPaymentRepository`, `PaymentStatusHistoryRepository`/`JdbcPaymentStatusHistoryRepository` scaffolded with stub methods throwing `UnsupportedOperationException`; `GlobalExceptionHandler` + all 4 exception classes created; `JdbcConfig`, `CorsConfig` (allows `localhost:5500`/`3000` dev origins), `OpenApiConfig` added; canonical `schema.sql` committed; `docker-compose.yml` added for local MySQL. Dataset: generated and committed `data.sql` (491 `payments` rows, 1661 `payment_status_history` rows) via a one-time, seeded/deterministic `scripts/generate_data_sql.py` (not part of the backend build), covering all required edge cases from Section 11.5 (all 5 statuses, full multi-row history chains, full + partial + multi-partial refunds incl. an exact-amount boundary case, unrefunded `COMPLETED` payments, FAILED payments per distinct error code, 40 reused accounts, uneven multi-week date spread, mixed `triggered_by`/`note` values). Frontend: `frontend-shared/design-tokens.css` and `lifecycle-timeline.js` shell created; `frontend-user/{index,history,detail}.html` and `frontend-business/{dashboard,audit}.html` scaffolded with static markup + page-specific stub JS files. Section 2 and Section 19 updated; ready to start Phase 2 module implementation. |
| 2026-08-04 | Migrated backend to Spring Boot 4.1.0 (from 3.4.1) — confirmed `4.1.0` is the current latest stable `spring-boot-starter-parent` release. Bumped `springdoc-openapi-starter-webmvc-ui` from `2.6.0` to `3.1.0` (the springdoc line compatible with Spring Boot 4 / Jakarta EE 11, per springdoc's own compatibility matrix — the old `2.x` line targets Spring Boot 3 and is incompatible). All other whitelisted dependencies (`spring-boot-starter-web`/`jdbc`/`validation`, `mysql-connector-j`, `spring-boot-starter-test`) are version-managed by the parent BOM and needed no explicit changes; existing code already used `jakarta.validation.*` imports so no source changes were required. Re-verified with `mvn compile` — builds cleanly. Section 6.1 updated to reflect Spring Boot 4.x as the required major version. |
| 2026-08-04 | M3 (Idempotency, Errors, Refund) implemented: `PaymentServiceImpl.createRefund()` (account swap, cumulative refund-amount cap, refund-of-refund ban, non-`COMPLETED` rejection), idempotent `createPayment()` duplicate-key short-circuit, `JdbcPaymentRepository.sumRefundedAmount()`, full `GlobalExceptionHandler` implementation (404/409/400/500 incl. the 200-with-existing-payment duplicate short-circuit), and `detail.html`/`detail.js` refund UI. Added test-scope dependency `org.springframework.boot:spring-boot-webmvc-test` (Section 6.2) — required because Spring Boot 4.1.0 relocated `@WebMvcTest`/`@AutoConfigureMockMvc` out of `spring-boot-test-autoconfigure`; not part of the original whitelist but needed for `GlobalExceptionHandlerTest`. 16 unit/MockMvc tests passing (`PaymentServiceImplTest`, `GlobalExceptionHandlerTest`); `JdbcPaymentRepositoryTest` written, pending a live MySQL run. |
| 2026-08-04 | M1/M2/M3 merged to `main` via PR #1/#3/#2 respectively. `main` briefly had a broken build after PR #3's stash-pop conflict resolution left literal `<<<<<<<`/`=======`/`>>>>>>>` markers in `JdbcPaymentRepository`/`JdbcPaymentStatusHistoryRepository`/`PaymentServiceImpl` plus a stale local `toResponse()` call; fixed on `main` by PR #4 (`hotfix/main-compile-fix`, commit `b0c5129`). M4 (`feature/m4-lifecycle-ui`) implemented `GET /api/payments` search/filter/pagination end-to-end (`JdbcPaymentRepository.search`/`countSearch`, `PaymentServiceImpl.searchPayments` with enum validation), `lifecycle-timeline.js`, `dashboard.html`/`dashboard.js`, `history.html`/`history.js`, plus unit tests for search/pagination/filtering/validation — but this branch had not yet merged `main`'s PR #4 hotfix. Merged `origin/main` into `feature/m4-lifecycle-ui`: one trivial import-only conflict in `JdbcPaymentRepository.java` (this branch's full search implementation vs. main's stub), resolved by keeping this branch's imports. Re-verified after merge: `mvn compile` succeeds, all 29 tests pass (`GlobalExceptionHandlerTest`, `JdbcPaymentRepositoryTest`, `PaymentServiceImplTest`). Corrected Section 2 dashboard above, which had been stale (still showing Phase 2 as `NOT_STARTED` for all modules despite merged, implemented, tested work) — spec updates had lagged actual progress. Remaining work: merge `feature/m4-lifecycle-ui` into `main` via PR, then Phase 3 end-to-end integration validation across all endpoints together. |

## 19. Immediate Execution Checklist

Backbone checklist for this week:
- [x] Create backend Spring Boot Maven skeleton in `backend/`.
- [x] Add whitelist dependencies only.
- [x] Add canonical `schema.sql` unchanged.
- [x] Generate and commit the shared `data.sql` seed dataset (Section 11.5) — same data
      for every developer, before anyone starts Phase 2.
- [x] Create module class skeletons and stubs (all four modules).
- [x] Create frontend folder structure and shared files.
- [x] Update Section 2 (Status Dashboard) and Section 18 (Progress Log) as each item completes.

Implementation checklist for next phase:
- [x] M1 build (create/get payment) — merged to `main` (PR #1).
- [x] M2 build (transitions/history) — merged to `main` (PR #3).
- [x] M3 build (idempotency/refund/errors) — merged to `main` (PR #2).
- [x] M4 build (query/list, shared UI, API docs) — implemented and tested on `feature/m4-lifecycle-ui`; merge into `main` still pending (open a PR).
- [ ] Integration validation across all endpoints (Phase 3, in progress).

