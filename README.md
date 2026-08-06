# 109_10_Brand_New_Day

## Payment Processing System

Internal payment processing system: payment creation & validation, a status transition
engine with a full audit trail, idempotent payment creation, refunds, and a query/list
API — backed by a plain HTML/CSS/JS frontend.

The full build spec, API contracts, module ownership, and progress log live in
[spec.md](spec.md) — that file is the single source of truth for this project.

### Tech Stack

- **Backend:** Java 25, Spring Boot 4.1.0, Maven, Spring JDBC (no JPA/Hibernate), MySQL
- **Frontend:** Plain HTML/CSS/JS (no frameworks, no build tools)
- **API docs:** springdoc-openapi (Swagger UI)

### Project Structure

```
.github/     GitHub Actions workflows (backend-ci.yml, frontend-ci.yml)
backend/     Spring Boot + Maven API (com.bnd.payment_processing), mvnw, Dockerfile
frontend/    frontend-shared/, frontend-user/, frontend-business/, Dockerfile
scripts/     One-time data generation scripts (not part of the backend build)
spec.md      Build spec + progress log (source of truth)
docker-compose.yml   Full deployment stack: MySQL + bnd-api + bnd-ui (GHCR images)
```

### Getting Started

**Option A — local dev (no Docker):**
1. Start a local MySQL instance matching `backend/src/main/resources/application.properties`.
2. Run the backend (loads `schema.sql` + the seeded `data.sql` automatically):
   ```
   cd backend
   mvn spring-boot:run
   ```
   (or `.\mvnw.cmd spring-boot:run` / `./mvnw spring-boot:run` using the committed Maven
   wrapper — no local Maven install required.)
3. Serve `frontend/` with any static file server (e.g. VS Code Live Server on
   `http://localhost:5500`) — CORS is pre-configured for local dev origins.
4. API docs available at `http://localhost:8080/swagger-ui.html`.

**Option B — full stack via Docker Compose (prebuilt GHCR images):**
```
docker compose pull
docker compose up -d
```
This starts `bnd-pp-mysql` (MySQL 8.4), `bnd-api` (`http://localhost:8082`,
Swagger UI at `/swagger-ui.html`), and `bnd-ui` (`http://localhost:8081`, serving
`frontend-user/index.html` and `frontend-business/ops.html`). Images are built and
pushed to GHCR automatically by CI on every push to `main` (see CI/CD below).

### CI/CD

GitHub Actions build/test each side on every push/PR (`.github/workflows/backend-ci.yml`,
`frontend-ci.yml`); on push to `main` they also build+push Docker images to GHCR
(`ghcr.io/neueda-learning/bnd-api`, `ghcr.io/neueda-learning/bnd-ui`) and trigger a
Jenkins deployment job (via ngrok) that runs `docker compose pull/up -d` on the deploy
host. Full details: `spec.md` Section 20, `info.md` Sections 12-17.

### Status

M1-M4 backend/frontend implementation is complete and merged to `main`, including the
refund approval workflow and the `/insights` aggregate endpoint. A 2026-08-06 bank-grade
hardening pass added an `accounts`/`cards`/`exchange_rates` reference schema, multi-currency
support (INR/USD/EUR, always settled in INR), and a `CARD` payment method alongside
`BANK_TRANSFER` — see `spec.md` Sections 5/7/10 for the full contract. The `frontend-user`
checkout was redesigned as a bank-grade "payment gateway" experience, followed by two
pair-programmed PRs (Neha & Tharan): `feature/user-balances-kpi-transparency` (live
account balances, exchange-rate display, customer-scoped KPIs, Confirm Payment modal) and
`feature/refund-lifecycle-ops-user-fixes` (fixed refunds stalling at `CREATED`/`APPROVED`,
added live KPI/table polling to `ops.html`). A full CI/CD pipeline (GitHub Actions → GHCR →
Jenkins → Docker Compose) was added on 2026-08-06 — see CI/CD above. See Section 2 and
Section 18 of [spec.md](spec.md) for current module-by-module progress.
