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
backend/     Spring Boot + Maven API (com.bnd.payment_processing)
frontend/    frontend-shared/, frontend-user/, frontend-business/
scripts/     One-time data generation scripts (not part of the backend build)
spec.md      Build spec + progress log (source of truth)
docker-compose.yml   Local MySQL for development
```

### Getting Started

1. Start local MySQL:
   ```
   docker compose up -d
   ```
2. Run the backend (loads `schema.sql` + the seeded `data.sql` automatically):
   ```
   cd backend
   mvn spring-boot:run
   ```
3. Serve `frontend/` with any static file server (e.g. VS Code Live Server on
   `http://localhost:5500`) — CORS is pre-configured for local dev origins.
4. API docs available at `http://localhost:8080/swagger-ui.html` once implemented.

### Status

M1-M4 backend/frontend implementation is complete and merged to `main`, including the
refund approval workflow and the `/insights` aggregate endpoint. A 2026-08-06 bank-grade
hardening pass added an `accounts`/`cards`/`exchange_rates` reference schema, multi-currency
support (INR/USD/EUR, always settled in INR), and a `CARD` payment method alongside
`BANK_TRANSFER` — see `spec.md` Sections 5/7/10 for the full contract. The `frontend-user`
checkout is being redesigned as a bank-grade "payment gateway" experience (account/currency/
card selection, animated lifecycle-simulation overlay) — see Section 2 and Section 18 of
[spec.md](spec.md) for current module-by-module progress.
