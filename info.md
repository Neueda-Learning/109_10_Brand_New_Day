
# Project Info — Payment Processing System

On-point reference doc. Source of truth for build/behavior is still `spec.md` — this
file is a quick-lookup index across features, endpoints, schema, Git/CI/CD, and
integration mechanics. Update alongside `spec.md` when things change.

---

## 1. Planned Features List

- Create a payment (`CREATED` status, idempotent on `idempotencyKey`), via `BANK_TRANSFER`
  or `CARD` (added 2026-08-06), in `INR`/`USD`/`EUR` (always settled in INR at a fixed
  seeded FX rate — added 2026-08-06).
- Fetch a payment by id.
- List/search/filter payments (by status, type, source/destination account, payment
  method, approval status, date range) with pagination.
- Full status-change audit trail (history timeline) per payment.
- Advance a payment through its lifecycle one step at a time (`process` endpoint):
  `CREATED → VALIDATED → SENT → COMPLETED|FAILED`.
- Refunds: create a `REFUND`-type payment against a `COMPLETED` original, capped at the
  original amount cumulative across multiple partial refunds, no refund-of-refund, gated
  behind a business approve/reject workflow.
- Bank-grade validation (added 2026-08-06): source/destination accounts must exist and be
  `ACTIVE` in the `accounts` registry; `CARD` payments validate against the `cards`
  registry (never persisting PAN/CVV); unsupported currencies are rejected.
- Two frontends: end-customer "payment gateway" checkout app (`frontend-user`) and
  internal ops app (`frontend-business`).
- OpenAPI/Swagger docs auto-generated from the backend.
- Local MySQL via Docker Compose, seeded with a realistic multi-week dataset incl.
  Kishore's demo accounts/card and USD/EUR payments.

Out of scope for now (see `spec.md` Section 5): real payment gateway/processor
integration, authentication/authorization, notifications, batch processing.

## 2. Feature → Dev Mapping

Full detail in `spec.md` Section 9. Summary (updated 2026-08-05 — both frontends are now
single unified pages, not the original per-module page split):

| Module | Owner | Feature scope | Backend files | Frontend page(s) |
|---|---|---|---|---|
| M1 — Creation & Validation | Poornima | Create payment, get-by-id, input validation | `PaymentController`, `PaymentService(Impl)`, `PaymentRepository`/`JdbcPaymentRepository` (create/get) | `frontend-user/index.html` |
| M2 — Status Engine & Audit Trail | Neha | `process` transitions, status history | `PaymentController` (process), `PaymentStatusHistoryRepository`/`Jdbc...` | `frontend-business/ops.html` |
| M3 — Idempotency, Errors, Refund | Tharan | Idempotency short-circuit, refund rules, refund approval workflow, exception handling | `GlobalExceptionHandler`, exception classes, refund service/repo logic | `frontend-user/index.html`, `frontend-business/ops.html` (approve/reject) |
| M4 — Query API, Lifecycle UI, Design System, API Docs, Insights | Karuna | Search/filter/paginate, aggregate insights, shared UI components, OpenAPI config | `PaymentQueryController`, `PaymentAnalyticsService(Impl)`, `OpenApiConfig`, `frontend-shared/*` | `frontend-business/ops.html`, `frontend-user/index.html` |

Current phase: **Phase 2 (backend/frontend implementation) is DONE for M1-M4, merged to
`main`**, including the refund approval workflow and the `/insights` aggregate endpoint.
Both frontends were unified into single-page apps (`frontend-user/index.html`,
`frontend-business/ops.html`) on 2026-08-05, replacing the original
index/history/detail and dashboard/audit page split. A 2026-08-06 bank-grade hardening
pass added `accounts`/`cards`/`exchange_rates` tables, multi-currency (INR/USD/EUR,
settled in INR) and a `CARD` payment method. `frontend-user` is now being redesigned as a
bank-grade "payment gateway" checkout (Kishore-only account/card selection, animated
lifecycle-simulation overlay, no debug/manual-step UI on the customer side) — see
`spec.md` Section 2 for the live status dashboard.

## 3. REST Endpoints

| Endpoint | Method | Owner | Purpose | Status |
|---|---|---|---|---|
| `/api/payments` | POST | M1 (+M3 idempotency) | Create payment | TESTED |
| `/api/payments/{id}` | GET | M1 | Fetch payment by id | TESTED |
| `/api/payments` | GET | M4 | List/filter/search payments (paginated) | TESTED |
| `/api/payments/insights` | GET | M4 | Aggregate KPI/analytics for dashboards | TESTED |
| `/api/payments/{id}/history` | GET | M2 | Full status history timeline | TESTED |
| `/api/payments/{id}/process` | POST | M2 | Advance payment to next valid state | TESTED |
| `/api/payments/{id}/refund` | POST | M3 | Create refund against a completed payment | TESTED |
| `/api/payments/{id}/refund/approve` | POST | M3 | Approve a pending refund | TESTED |
| `/api/payments/{id}/refund/reject` | POST | M3 | Reject a pending refund | TESTED |

Full request/response JSON shapes and error codes: `spec.md` Section 10. All error
responses use one shared `ErrorResponse` shape (timestamp, status, errorCode, message, path).

Live interactive docs once the app is running: `http://localhost:8080/swagger-ui.html`
(raw spec at `/v3/api-docs`) — configured via `springdoc.*` properties in
`application.properties` and `OpenApiConfig.java`.

## 4. UI Wireframes (text description — no image assets yet)

Updated 2026-08-05: both apps are now single unified pages (the original
index/history/detail and dashboard/audit page split was retired — those six legacy
files no longer exist in the repo).

**`frontend-user/` (end-customer app) — `index.html` + `script.js` + `styles.css`:**
- KPI insight cards (total payments, total amount, refunds, success rate) fed by
  `GET /api/payments/insights`.
- "New Payment" checkout form (bank-grade, Kishore-only demo identity — no auth/login):
  from-account dropdown (Kishore's 2 seeded accounts), free-text destination account,
  amount, currency select (`INR`/`USD`/`EUR`), payment method toggle (Bank Transfer /
  Card — card option reveals a card picker + CVV field with a "never stored" notice) →
  submit → an animated "processing" overlay auto-advances the new payment's lifecycle
  (`CREATED → VALIDATED → SENT → COMPLETED`/`FAILED`) via repeated
  `POST .../process` calls, rendered live with `frontend-shared/lifecycle-timeline.js`.
- Expandable recent-transactions list with inline detail (status badge, lifecycle
  timeline, settlement/FX amount when currency ≠ INR, card brand/last4 when paid by
  card, refund action when `COMPLETED`).
- Light/dark theme toggle via `frontend-shared/app-mode.js`. No Demo/Debug mode toggle
  or request/response inspector on this app — the customer-facing checkout always
  auto-advances (prod-grade UX); Debug mode remains business-side only (`ops.html`).

**`frontend-business/` (internal ops app) — `ops.html` + `ops.js` + `ops.css`:**
- KPI strip (total payments, total amount, success rate, refund rate, pending
  approvals) fed by `GET /api/payments/insights`.
- Filter panel (status, type, payment method, approval status, source/destination
  account, date range) over a paginated results table.
- Detail panel (opened via a table row's "View" button): full payment info, lifecycle
  timeline (`frontend-shared/lifecycle-timeline.js`), and refund Approve/Reject actions
  when a refund is `PENDING_APPROVAL`.
- Same Demo/Debug mode infrastructure as `frontend-user`.

**`frontend-shared/`** — `design-tokens.css` (colors/spacing/typography vars, HSBC red
brand, dark-mode overrides), `lifecycle-timeline.js` (reusable timeline component), and
`app-mode.js` (Demo/Debug mode + dark-mode toggle persistence), consumed by both apps.
No page-specific logic lives here.

No dedicated wireframe image files (Figma/PNG) exist in the repo — the scaffolded HTML
files above are the working wireframes.

## 5. DB Schema

Canonical source: `backend/src/main/resources/schema.sql` (mirrors `spec.md` Section 7).
Applied automatically on every backend startup via `spring.sql.init.mode=always`.
Extended 2026-08-06 with a bank-grade validation hardening pass: `accounts`/`cards`/
`exchange_rates` reference tables plus new `payments` columns for multi-currency
settlement and card snapshots.

**`accounts`** — reference registry simulating a core-banking existence/status check
(not FK-linked from `payments`): `id`, `account_number` (unique), `customer_ref`,
`display_name`, `account_type` (`CUSTOMER`/`BUSINESS`), `status`
(`ACTIVE`/`BLOCKED`/`CLOSED`), `default_currency`, `created_at`/`updated_at`.

**`cards`** — PCI-safe demo card registry (no PAN/CVV columns, ever): `id`,
`customer_ref`, `card_brand`, `masked_pan`, `last4`, `expiry_month`/`expiry_year`,
`cardholder_name`, `token_ref` (unique), `status` (`ACTIVE`/`BLOCKED`), `created_at`.

**`exchange_rates`** — fixed/seeded FX rates, no live FX calls: `id`, `currency`
(unique), `rate_to_inr`, `effective_at`, `source`. Seeded with `INR` (1.0), `USD`
(95.2), `EUR` (109.92).

**`payments`**

| Column | Type | Notes |
|---|---|---|
| id | CHAR(36) PK | server-generated UUID |
| idempotency_key | VARCHAR(255) UNIQUE | dedupe key for create |
| source_account | VARCHAR(64) | |
| destination_account | VARCHAR(64) | |
| amount | DECIMAL(18,2) | in `currency`, not necessarily INR |
| currency | VARCHAR(3) | `INR`/`USD`/`EUR` (must exist in `exchange_rates`) |
| status | VARCHAR(20) | `CREATED`/`VALIDATED`/`SENT`/`COMPLETED`/`FAILED` |
| error_code | VARCHAR(64) NULL | set only when `FAILED` |
| type | VARCHAR(10) | `PAYMENT` / `REFUND` |
| original_payment_id | CHAR(36) NULL FK→payments.id | set only for `REFUND` rows |
| payment_method | VARCHAR(20) | `BANK_TRANSFER` / `CARD` |
| approval_status / approved_by / approved_at / rejection_reason | | `REFUND` rows only |
| settlement_currency / fx_rate_to_inr / settlement_amount_inr | | frozen at creation, always settles in INR |
| requested_by | VARCHAR(64) NULL | initiating account/actor |
| card_id / card_last4 / card_brand | | snapshot, only set when `payment_method = CARD` |
| created_at / updated_at | TIMESTAMP | UTC |

Indexes: `status`, `type`, `source_account`, `destination_account`, `created_at`,
`original_payment_id`.

**`payment_status_history`**

| Column | Type | Notes |
|---|---|---|
| id | CHAR(36) PK | |
| payment_id | CHAR(36) FK→payments.id | |
| from_status | VARCHAR(20) NULL | null for the initial `CREATED` row |
| to_status | VARCHAR(20) | |
| changed_at | TIMESTAMP | UTC |
| triggered_by | VARCHAR(32) | e.g. `SYSTEM` |
| note | VARCHAR(255) NULL | |
| seq | BIGINT AUTO_INCREMENT UNIQUE | insertion-order tiebreaker, not exposed via API |

Index: `(payment_id, changed_at, seq)`.

Seed data: `backend/src/main/resources/data.sql` — deterministic, generated by
`scripts/generate_data_sql.py` (one-time generator, not part of the build); includes
Kishore's 2 accounts + 1 VISA card and USD/EUR multi-currency payments.

## 6. Git Repo + Owner + Collaborators

- Repo (mono-repo, backend + frontend together): `https://github.com/Neueda-Learning/109_10_Brand_New_Day`
- Org/owner: `Neueda-Learning`
- Structure: **monorepo** — `backend/` (Spring Boot) and `frontend/` (3 static apps) in one repository, no submodules.
- Default branch: `main` (only branch currently — no feature branches pushed yet, despite the branch naming convention defined in `spec.md` Section 16).
- Collaborators list is not fetchable from local Git metadata — check
  `https://github.com/Neueda-Learning/109_10_Brand_New_Day/settings/access` (needs repo
  admin access) or run `gh api repos/Neueda-Learning/109_10_Brand_New_Day/collaborators`
  with the GitHub CLI authenticated.

## 7. Commit History Logger

No automated logger configured. Current history (`git log --oneline`), newest first:

| Commit | Message |
|---|---|
| `ee0ed21` (HEAD, main, origin/main) | Spring boot version updated to 4.1.0 - Compilation done |
| `f2d78a8` | Readme updated |
| `09cd886` | Merge branch 'main' of https://github.com/Neueda-Learning/109_10_Brand_New_Day |
| `58c12f3` | Updating the Template of the Project |
| `8d5dbc0` | Initial commit |

To keep a running log going forward: `git log --oneline --all > commit-history.log`
(regenerate on demand), or add a scheduled GitHub Action that commits this file
periodically — not currently set up.

## 8. Git PR Logger

No pull requests have been opened yet — all commits so far are direct pushes/merges to
`main`. `spec.md` Section 16 defines the intended PR policy (small PRs, 1 reviewer
minimum, no merge without passing tests) but it isn't enforced by branch protection yet.

Recommended (not yet set up): enable GitHub branch protection on `main` (require PR +
1 approval + status checks) and track PRs via `gh pr list --state all` for a point-in-time log.

## 9. Git Merge & Conflicts Logger

One merge commit exists so far: `09cd886` ("Merge branch 'main' of ...") — a routine
remote-sync merge, no recorded conflicts. No conflict-tracking tooling is set up; if/when
conflicts occur, resolution notes should be added to `spec.md` Section 18 (Progress Log)
by whoever resolves them, since this repo doesn't have PR-based conflict logging yet
(see Section 8 above).

## 10. Frontend ↔ Backend Integration — How It Works

- Frontends are **plain static HTML/CSS/JS** — no build step, no bundler, no framework.
  Served independently of the backend (e.g. a static file server on `localhost:5500` or
  opened directly), while the backend runs on `localhost:8080`.
- Integration is pure `fetch()` calls from page-specific JS files (e.g. `index.js`,
  `dashboard.js`) directly to the REST endpoints in Section 3, using relative/absolute
  URLs pointing at `http://localhost:8080/api/...`.
- Because frontend and backend are served from different origins/ports, **CORS must be
  explicitly enabled** — `backend/.../config/CorsConfig.java` allows the local dev
  frontend origins (`localhost:5500`, `localhost:3000`). Without this, browser `fetch()`
  calls fail with a CORS error even though the API works fine via curl/Postman.
- No shared session/auth token mechanism exists yet (auth is out of scope — Section 5).
- Data flow is request/response only — no WebSockets/polling; the UI reflects state only
  when the user triggers an action or reloads/refetches.

## 11. Testing (Unit / Integration)

Per `spec.md` Section 15:
- **Unit tests** — required per module, for the logic that module owns (e.g. M2's status
  transition rules, M3's refund cap/refund-of-refund checks).
- **Negative-path tests** — required for invalid state transitions, invalid refund
  states, and idempotency conflicts.
- **Repository tests** — required for JDBC SQL behavior (`JdbcPaymentRepository`,
  `JdbcPaymentStatusHistoryRepository`), run against a real local MySQL instance.
- Test dependency: `spring-boot-starter-test` (already in `pom.xml`); also
  `spring-boot-webmvc-test` (test scope, added by M3 — see `spec.md` Section 6.2).
- Environment prerequisite: MySQL running locally — standard command is
  `docker compose up -d` (see Section 13 below) before running integration/repository tests.
- 29 tests currently passing across `GlobalExceptionHandlerTest`,
  `JdbcPaymentRepositoryTest`, and `PaymentServiceImplTest` (verified via `mvn test` on
  `feature/m4-lifecycle-ui` after merging latest `main`).

## 12. GitHub Actions File for CI

**Not set up yet** — no `.github/workflows/` directory exists in this repo. Suggested
minimal starter (not yet added — create only when the team is ready to adopt CI):

```yaml
# .github/workflows/backend-ci.yml
name: Backend CI
on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '25'
      - name: Build (no tests DB available in CI yet)
        working-directory: backend
        run: mvn -q -DskipTests compile
```
A full test-inclusive pipeline would additionally need a MySQL service container
(`services: mysql: image: mysql:8.0 ...`) matching `schema.sql`/`data.sql`.

## 13. Dockerfile

**Not set up yet** — there is no `Dockerfile` for the Spring Boot app itself; only
`docker-compose.yml` at the repo root, which provisions **MySQL only** (see Section 14).
A basic app Dockerfile would look like:

```dockerfile
# backend/Dockerfile (not yet created)
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```
(would need `mvn package` to produce the jar first, and a matching build stage or CI step).

## 14. Docker Compose

`docker-compose.yml` (repo root) — currently **MySQL only**, no app service defined yet:

```yaml
services:
  mysql:
    image: mysql:8.0
    container_name: bnd-pp-mysql
    environment:
      MYSQL_DATABASE: payment_processing
      MYSQL_USER: payment_app
      MYSQL_PASSWORD: payment_app
      MYSQL_ROOT_PASSWORD: root
    ports:
      - "3306:3306"
    volumes:
      - bnd-pp-mysql-data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-u", "root", "-proot"]
```
Standard local dev command: `docker compose up -d`. Note: `application.properties`
must point at credentials that actually match whichever MySQL instance is running
(this compose file's `payment_app`/`payment_app`, or a separately-installed native
MySQL server) — see the troubleshooting notes already captured in this session.

## 15. Jenkins Job

**Not set up.** No `Jenkinsfile` exists in the repo, and there's no indication a Jenkins
server is in use for this project — GitHub Actions (Section 12) is the more likely CI
fit given the repo already lives on GitHub. Add a `Jenkinsfile` only if the team
specifically adopts Jenkins.

## 16. ngrok

**Not set up.** No ngrok config/scripts in the repo. Would only be relevant for exposing
the local backend (`localhost:8080`) to the internet temporarily (e.g. demoing to someone
outside the network): `ngrok http 8080`. Not part of the current local dev workflow
(frontend and backend both run on `localhost` today).

## 17. CI/CD

**Not set up.** Current workflow is fully manual:
1. `mvn -q -DskipTests compile` locally to verify the build.
2. Manual `git push` to `main` (no PR/branch-protection gate yet — see Section 8).
3. No automated deploy target exists (no cloud/hosting config found in the repo).

Recommended future path once the team is ready: GitHub Actions for CI (Section 12) →
branch protection + required PR reviews (Section 8) → containerize with a `Dockerfile`
(Section 13) → optional CD step to push the built image somewhere. None of this is
implemented yet; this section will need updating once any of it lands.

## 18. System Architecture & State Transitions

High-level architecture and lifecycle diagrams for the whole system (derived from
`spec.md` Sections 7-10, 14).

### 18.1 System Architecture

```mermaid
flowchart TB
    subgraph Frontend User["frontend-user (static)"]
        FU1[index.html<br/>+ index.js]
        FU2[history.html<br/>+ history.js]
        FU3[detail.html<br/>+ detail.js]
    end

    subgraph Frontend Business["frontend-business (static)"]
        FB1[dashboard.html<br/>+ dashboard.js]
        FB2[audit.html<br/>+ audit.js]
    end

    subgraph Shared["frontend-shared"]
        S1[design-tokens.css]
        S2[lifecycle-timeline.js]
    end

    FU2 --> S2
    FB2 --> S2
    FU1 & FU2 & FU3 & FB1 & FB2 --> S1

    subgraph Backend["Spring Boot Backend (localhost:8080)"]
        CORS[CorsConfig]
        subgraph Controllers
            PC[PaymentController<br/>POST /payments, GET /payments/id,<br/>POST /process, POST /refund]
            PQC[PaymentQueryController<br/>GET /payments list/filter]
        end
        SVC[PaymentServiceImpl]
        subgraph Repos
            PR[JdbcPaymentRepository]
            PSHR[JdbcPaymentStatusHistoryRepository]
        end
        GEH[GlobalExceptionHandler]
        OAC[OpenApiConfig<br/>/swagger-ui.html]
    end

    FU1 & FU3 -- fetch --> PC
    FU2 -- fetch --> PC
    FB1 -- fetch --> PQC
    FB2 -- fetch --> PC

    PC --> SVC
    PQC --> SVC
    SVC --> PR
    SVC --> PSHR
    PC -.throws.-> GEH
    PQC -.throws.-> GEH

    subgraph DB["MySQL (Docker, localhost:3306)"]
        T1[(payments)]
        T2[(payment_status_history)]
    end

    PR --> T1
    PSHR --> T2
    T1 -. FK .- T2
```

Key points:
- Two independent static frontends (`frontend-user`, `frontend-business`) call the same
  backend REST API — no server-side rendering.
- `frontend-shared` is dependency-only (CSS tokens + timeline JS component), consumed by
  both apps, contains no page-specific logic.
- Backend is layered Controller → Service → JDBC Repository, no ORM.
- `GlobalExceptionHandler` is the single cross-cutting error-mapping layer for all
  controllers, producing the shared `ErrorResponse` shape (spec Section 10.7).
- `CorsConfig` is the only cross-origin bridge between the frontend static origin(s)
  (`localhost:5500`/`3000`) and the backend (`localhost:8080`).

### 18.2 Payment Lifecycle State Transitions

```mermaid
stateDiagram-v2
    [*] --> CREATED: POST /api/payments
    CREATED --> VALIDATED: process (auto next step)
    VALIDATED --> SENT: process (auto next step)
    SENT --> COMPLETED: process (targetStatus=COMPLETED, default)
    SENT --> FAILED: process (targetStatus=FAILED + errorCode required)
    COMPLETED --> [*]
    FAILED --> [*]

    note right of COMPLETED
        Only COMPLETED + type=PAYMENT
        payments can be refunded
        (POST /refund creates a new
        type=REFUND row starting
        its own CREATED state)
    end note
```

### 18.3 Refund Sub-Flow

A refund is a brand-new `payments` row (`type=REFUND`), not a mutation of the original,
and it re-enters the same state machine independently (spec Section 8.1).

```mermaid
flowchart LR
    A[Original PAYMENT<br/>status=COMPLETED] -- POST /refund<br/>amount, reason --> B{Validation<br/>- COMPLETED and type=PAYMENT?<br/>- amount greater than 0?<br/>- cumulative refunds less-or-equal original.amount?}
    B -- fail --> E[409 InvalidRefundStateException]
    B -- pass --> C[New REFUND row<br/>original_payment_id=A.id<br/>status=CREATED]
    C --> D[Runs through same<br/>CREATED to VALIDATED to SENT to COMPLETED/FAILED<br/>via /process]
```

---

*Keep this file in sync with `spec.md` and `README.md` whenever the stack, endpoints, or
tooling change. `spec.md` remains the single source of truth for build behavior — this
file is a cross-cutting index for project-management/tooling concerns.*
