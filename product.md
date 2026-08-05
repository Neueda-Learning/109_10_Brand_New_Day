# BND AI Billing and Payment Processing Engine - Specification

Status: ACTIVE
Version: 3.0
Last Updated: 2026-08-05
Source of Truth: This file only.

## 1. Purpose

This project is an internal payment processing engine wrapped inside a concrete product
use case:

**BND AI Billing Engine** - Kishore, a customer of the BND AI app, buys AI credits or a
simple plan through an invoice-based checkout. The customer can pay by Card or Bank
Transfer in a chosen currency. BND receives and settles the payment in USD. The business
team can monitor all invoices, payments, settlements, failures, refunds, and lifecycle
events from an operations dashboard.

The goal is not to build a complete Stripe replacement. The goal is a simple, elegant,
demo-ready system that clearly shows how a payment moves through an invoice, payment
method, multi-currency conversion, processing lifecycle, settlement, and refund flow.

## 2. Current State Cleanup

The frontend target is now a two-screen product: one customer checkout and one business
operations dashboard. Retired page structures from earlier MVP phases are no longer part
of the implementation plan.

Current frontend direction:

- `frontend/frontend-user/index.html` - customer-facing BND AI checkout for Kishore.
- `frontend/frontend-user/script.js` and `styles.css` - user checkout behavior and UI.
- `frontend/frontend-business/ops.html` - BND business operations dashboard.
- `frontend/frontend-business/ops.js` and `ops.css` - business dashboard behavior and UI.
- `frontend/frontend-shared/` - shared design tokens, lifecycle timeline, demo/debug
  helpers.

Current backend direction:

- Spring Boot + Maven + Java target remains.
- Spring JDBC remains the data access approach.
- MySQL remains the local database.
- No JPA, Hibernate, or authentication in this phase.
- Existing payment lifecycle code should be evolved, not thrown away.

Known cleanup items from the current repo:

- Align `application.properties` database credentials with `docker-compose.yml`.
- Update seed data to match the new schema and payment approval/multi-currency behavior.
- Fix analytics so pending approvals are computed from real data.
- Remove stale comments that say implemented features are not implemented.
- Keep only current frontend page names in docs and code comments.

## 3. Product Story

### 3.1 Customer POV

Kishore opens the BND AI checkout and sees:

- A selected AI credit pack or plan.
- Invoice summary with subtotal, GST, total, selected currency, and USD equivalent when
  needed.
- Payment method choice: Card or Bank Transfer.
- Card/account input with live masking.
- BND receiving details displayed read-only.
- A visual payment lifecycle after payment starts.
- Receipt after completion.
- Refund request action when eligible.

### 3.2 Business POV

BND business users open the operations dashboard and see:

- Total received in USD.
- Invoices generated.
- Payment status distribution.
- Failed and pending payments.
- Pending refund approvals.
- GST collected.
- Multi-currency payments converted to USD.
- Detailed lifecycle timeline for each payment.
- Refund approve/reject workflow.
- Demo/debug controls for showcasing edge cases.

## 4. Unique Feature

The unique feature for this project is **Lifecycle Playback Mode**.

Lifecycle Playback Mode makes the payment engine visible:

- The customer can watch the invoice and payment move through each stage.
- The business can open the same transaction and see the deeper operational timeline.
- Demo mode can auto-play a successful, failed, refunded, or FX-converted scenario.
- Debug mode can expose request/response payloads and manual transition buttons.

This is the main demo differentiator. It turns a plain CRUD payment app into an
understandable payment operations product.

## 5. Hard Constraints

- Keep the schema to **7 tables** for this phase.
- Keep the frontend simple enough to finish quickly.
- Do not add authentication/authorization.
- Do not store raw card numbers.
- Do not store raw bank account numbers.
- Do not implement real payment gateway calls.
- Do not implement real live FX calls in this phase.
- Exchange rates are hardcoded/seeded in the database.
- Business settlement currency is always USD.
- Customer presentment currency can be INR, USD, or EUR in this phase.
- Payment methods are Card and Bank Transfer only.
- UPI, wallets, autopay, usage-metering, and subscriptions are future scope unless time
  remains after the core flow is complete.

## 6. Recommended Frontend Direction

For the remaining implementation window, keep:

- Plain HTML/CSS/JS.
- Bootstrap 5 and Bootstrap Icons via CDN.
- Shared custom CSS tokens to remove generic Bootstrap appearance.

Do not migrate to React/Vite in this phase. A framework migration would consume too much
time. The spec may allow React/Vite as a future Phase 5+ upgrade, but the current goal is
to make the existing static frontend look polished and purposeful.

Frontend design must not look like a generic Bootstrap admin template. It should feel
like a calm AI-fintech product:

- Soft off-white or near-white background.
- Clean charcoal text.
- Muted accent colors.
- Strong spacing and alignment.
- Subtle borders.
- Small, tasteful shadows.
- No large gradients, flashy animations, or clutter.
- Clear visual hierarchy.
- Polished empty, loading, error, and success states.

Style inspiration: calm, spacious, refined AI-product UI. Think simple and premium, not
decorative.

## 7. Seven-Table Data Model

Only these seven tables should exist for the Phase 3 redesign.

### 7.1 `customers`

Stores customer identity for demo and business filtering.

Fields:

- `id` CHAR(36) primary key
- `customer_ref` VARCHAR(32) unique, example `CUS-KISHORE-001`
- `display_name` VARCHAR(100), example `Kishore`
- `email` VARCHAR(150)
- `default_currency` VARCHAR(3), example `INR`
- `created_at` TIMESTAMP
- `updated_at` TIMESTAMP

Notes:

- Kishore must be the main customer in seed data.
- Add several other generated customers so the business dashboard has realistic volume.

### 7.2 `invoices`

Represents what the customer owes before payment starts.

Fields:

- `id` CHAR(36) primary key
- `invoice_number` VARCHAR(32) unique, example `INV-BND-000001`
- `customer_id` CHAR(36) foreign key to `customers.id`
- `product_name` VARCHAR(100), example `BND AI Pro Credits`
- `product_code` VARCHAR(32), example `AI_CREDITS_PRO`
- `credit_units` INT, example `100000`
- `subtotal_amount` DECIMAL(18,2)
- `gst_amount` DECIMAL(18,2)
- `total_amount` DECIMAL(18,2)
- `currency` VARCHAR(3)
- `status` VARCHAR(24)
- `created_at` TIMESTAMP
- `updated_at` TIMESTAMP

Invoice statuses:

- `DRAFT`
- `ISSUED`
- `PAYMENT_PENDING`
- `PAID`
- `FAILED`
- `REFUND_REQUESTED`
- `REFUNDED`

GST rule for demo:

- Apply a fixed GST/tax percentage in seed and UI, for example 18%.
- Show subtotal, GST, and total clearly in the checkout.

### 7.3 `payment_methods`

Stores safe, tokenized, demo payment method references.

Fields:

- `id` CHAR(36) primary key
- `customer_id` CHAR(36) foreign key to `customers.id`
- `method_type` VARCHAR(20), values `CARD` or `BANK_TRANSFER`
- `display_label` VARCHAR(80), example `Visa ending 4242`
- `masked_identifier` VARCHAR(64), example `**** **** **** 4242` or `BANK **** 8921`
- `token_ref` VARCHAR(100), example `tok_demo_card_4242`
- `provider` VARCHAR(40), example `DEMO_TOKENIZER`
- `created_at` TIMESTAMP
- `updated_at` TIMESTAMP

Security rules:

- Never store full card number.
- Never store full bank account number.
- The UI should mask input as the user types.
- The backend stores only `masked_identifier` and `token_ref`.
- Production systems must use a PCI-compliant tokenization provider.

### 7.4 `exchange_rates`

Stores hardcoded seeded exchange rates for deterministic demos.

Fields:

- `id` CHAR(36) primary key
- `from_currency` VARCHAR(3), values `INR`, `EUR`, `USD`
- `to_currency` VARCHAR(3), always `USD` in this phase
- `rate` DECIMAL(18,8)
- `effective_at` TIMESTAMP
- `source` VARCHAR(40), example `SEEDED_DEMO_RATE`
- `created_at` TIMESTAMP

Rules:

- USD to USD rate is `1.00000000`.
- Frontend uses the API response or seeded values to display conversion.
- Payment stores the rate snapshot used at payment time.
- Live FX API integration is future scope.

### 7.5 `payments`

Core payment engine record.

Fields:

- `id` CHAR(36) primary key
- `invoice_id` CHAR(36) foreign key to `invoices.id`
- `customer_id` CHAR(36) foreign key to `customers.id`
- `payment_method_id` CHAR(36) nullable foreign key to `payment_methods.id`
- `idempotency_key` VARCHAR(255) unique
- `amount` DECIMAL(18,2)
- `currency` VARCHAR(3)
- `exchange_rate_id` CHAR(36) nullable foreign key to `exchange_rates.id`
- `fx_rate` DECIMAL(18,8)
- `usd_amount` DECIMAL(18,2)
- `status` VARCHAR(24)
- `settlement_status` VARCHAR(24)
- `error_code` VARCHAR(64) null
- `created_at` TIMESTAMP
- `updated_at` TIMESTAMP

Payment statuses:

- `CREATED`
- `VALIDATED`
- `SENT`
- `COMPLETED`
- `FAILED`

Settlement statuses:

- `NOT_READY`
- `PENDING`
- `SETTLED`
- `FAILED`

Rules:

- A payment belongs to one invoice.
- A payment is created only after an invoice exists.
- BND settlement amount is always stored in `usd_amount`.
- `fx_rate` and `exchange_rate_id` are snapshots from the chosen currency to USD.
- Existing payment transition rules remain valid.

### 7.6 `payment_status_history`

Append-only lifecycle timeline.

Fields:

- `id` CHAR(36) primary key
- `payment_id` CHAR(36) foreign key to `payments.id`
- `from_status` VARCHAR(24) nullable
- `to_status` VARCHAR(24)
- `changed_at` TIMESTAMP
- `triggered_by` VARCHAR(64)
- `note` VARCHAR(255) nullable
- `seq` BIGINT auto-increment unique

Rules:

- Never update or delete history rows.
- Use this table as the audit trail for the 7-table phase.
- Include meaningful notes for FX lock, method tokenization, settlement, refund actions,
  and demo/debug actions.

### 7.7 `refunds`

First-class refund workflow.

Fields:

- `id` CHAR(36) primary key
- `payment_id` CHAR(36) foreign key to `payments.id`
- `amount` DECIMAL(18,2)
- `currency` VARCHAR(3)
- `usd_amount` DECIMAL(18,2)
- `reason` VARCHAR(255)
- `approval_status` VARCHAR(24)
- `status` VARCHAR(24)
- `approved_by` VARCHAR(64) null
- `approved_at` TIMESTAMP null
- `rejection_reason` VARCHAR(255) null
- `created_at` TIMESTAMP
- `updated_at` TIMESTAMP

Approval statuses:

- `PENDING_APPROVAL`
- `APPROVED`
- `REJECTED`

Refund statuses:

- `REQUESTED`
- `PROCESSING`
- `COMPLETED`
- `FAILED`
- `REJECTED`

Rules:

- Refunds are allowed only for completed payments.
- Multiple partial refunds are allowed.
- Cumulative refund amount cannot exceed the original payment amount.
- Business approval is required before processing.
- Refund actions must add notes into `payment_status_history`.

## 8. Account and Payment Method Convention

### 8.1 Customer Input

For Card:

- User may type a mock card number.
- UI masks it live as `**** **** **** 4242`.
- Backend stores only masked value and token reference.

For Bank Transfer:

- User may type a mock bank account/reference.
- UI masks it live as `BANK **** 8921`.
- Backend stores only masked value and token reference.

### 8.2 BND Receiving Details

Kishore should not manually type BND's receiving account. It should be displayed as a
read-only merchant destination:

- Merchant: `BND AI`
- Receiving account: `BND-USD-OPERATING-001`
- Settlement currency: `USD`

This is clearer for an AI billing platform and avoids unnecessary user input.

## 9. Lifecycle Rules

### 9.1 Invoice Lifecycle

`ISSUED -> PAYMENT_PENDING -> PAID`

Failure path:

`ISSUED -> PAYMENT_PENDING -> FAILED`

Refund path:

`PAID -> REFUND_REQUESTED -> REFUNDED`

### 9.2 Payment Lifecycle

Keep the existing core lifecycle:

`CREATED -> VALIDATED -> SENT -> COMPLETED`

Failure path:

`CREATED -> VALIDATED -> SENT -> FAILED`

Rules:

- `CREATED -> VALIDATED`: input and invoice validation succeeded.
- `VALIDATED -> SENT`: simulated processor dispatch.
- `SENT -> COMPLETED`: simulated success.
- `SENT -> FAILED`: simulated failure with `error_code`.
- `COMPLETED` and `FAILED` are terminal.

### 9.3 Settlement Lifecycle

Settlement is represented on `payments.settlement_status`:

`NOT_READY -> PENDING -> SETTLED`

Rules:

- Payment starts with `NOT_READY`.
- When payment reaches `COMPLETED`, settlement can move to `PENDING`.
- Demo mode may auto-settle to `SETTLED`.
- Business dashboard must show settlement in USD.

### 9.4 Refund Lifecycle

`REQUESTED -> PENDING_APPROVAL -> APPROVED -> PROCESSING -> COMPLETED`

Rejection path:

`REQUESTED -> PENDING_APPROVAL -> REJECTED`

Rules:

- User requests refund from a completed payment.
- Business approves or rejects.
- Approved refund can process to completed.
- Rejected refund stores `rejection_reason`.

## 10. API Contract Direction

Keep existing endpoints where possible, but evolve request/response payloads to include
invoice, customer, FX, method, and settlement fields.

### 10.1 Customer APIs

`GET /api/bootstrap`

Returns checkout bootstrap data:

- Kishore customer summary.
- BND receiving account display.
- Available AI credit packs.
- Available currencies.
- Current seeded exchange rates.
- Supported payment methods.

`POST /api/invoices`

Creates an invoice for a selected BND AI credit pack.

Request:

```json
{
  "customerId": "uuid",
  "productCode": "AI_CREDITS_PRO",
  "currency": "INR"
}
```

Response:

- Invoice with subtotal, GST, total, currency, and status.

`POST /api/payments`

Creates a payment for an invoice.

Request:

```json
{
  "invoiceId": "uuid",
  "customerId": "uuid",
  "paymentMethodType": "CARD",
  "maskedIdentifier": "**** **** **** 4242",
  "tokenRef": "tok_demo_card_4242",
  "currency": "INR",
  "idempotencyKey": "client-generated-key"
}
```

Response:

- Payment response including invoice id, customer id, currency, FX rate, USD amount,
  payment status, settlement status, and timestamps.

`GET /api/payments/{id}`

Returns a single payment.

`GET /api/payments/{id}/history`

Returns the lifecycle timeline.

`POST /api/payments/{id}/process`

Advances the payment lifecycle one step.

`POST /api/payments/{id}/refund`

Creates a refund request.

### 10.2 Business APIs

`GET /api/business/dashboard`

Returns business dashboard aggregates:

- Total collected in USD.
- Total GST collected.
- Total invoices.
- Payment counts by status.
- Pending settlements.
- Pending refund approvals.
- Recent payments.

`GET /api/payments`

Search/list endpoint. Filters:

- status
- settlementStatus
- currency
- customerId
- invoiceNumber
- methodType
- fromDate
- toDate
- page
- size

`POST /api/refunds/{id}/approve`

Approves a refund.

`POST /api/refunds/{id}/reject`

Rejects a refund.

`GET /api/demo/scenarios`

Returns seeded demo scenarios.

Optional for this phase, but recommended if time allows:

`POST /api/demo/scenarios/{code}/run`

Loads or triggers a deterministic scenario for presentation.

## 11. Multi-Currency Requirements

Owner: Neha for code implementation.

Supported presentment currencies:

- USD
- INR
- EUR

Settlement currency:

- USD only.

Rules:

- User selects currency in checkout.
- UI immediately updates subtotal, GST, total, FX rate, and USD settlement preview.
- Exchange rates are read from the `exchange_rates` table.
- Payment stores rate snapshot in `payments.fx_rate`.
- Payment stores converted amount in `payments.usd_amount`.
- Business dashboard always prioritizes USD settlement.
- If selected currency is USD, FX rate is 1.

Rounding:

- Monetary values use `DECIMAL(18,2)`.
- FX rate uses `DECIMAL(18,8)`.
- Converted USD amount is rounded to 2 decimals.

Future scope:

- Live FX Java API provider.
- Rate expiry.
- Rate lock windows.

## 12. Frontend Requirements

### 12.1 Customer UI - BND AI Checkout

The customer screen must look like a finished checkout, not a form dump.

Required sections:

- Header: `BND AI`
- Customer label: `Kishore`
- AI credit pack selector
- Invoice summary
- Currency selector
- FX conversion panel
- GST/tax breakdown
- Payment method tabs: Card and Bank Transfer
- Live masked input
- Read-only BND receiving details
- Pay button
- Animated lifecycle tracker
- Receipt/success modal
- Refund request panel when eligible

Visual behavior:

- Changing currency updates displayed totals and USD equivalent.
- Card input masks while typing.
- Bank account input masks while typing.
- Payment lifecycle animates after submit.
- Completed payment shows receipt.
- Failed payment shows reason and retry guidance.

### 12.2 Business UI - BND AI Payments

The business screen must run as its own static page and should be easy to serve on a
separate port from the user screen.

Header:

- Brand: `BND AI Payments`
- Mode toggle: Demo / Debug
- Theme toggle optional

Required sections:

- KPI strip:
  - Total received USD
  - GST collected
  - Pending settlements
  - Failed payments
  - Pending refund approvals
- Filter panel:
  - customer
  - invoice number
  - status
  - currency
  - method
  - date range
- Payment table:
  - invoice
  - customer
  - product
  - method
  - paid currency
  - USD settlement
  - payment status
  - settlement status
  - refund status
- Detail drawer or modal:
  - invoice summary
  - customer summary
  - payment method masked reference
  - FX snapshot
  - lifecycle timeline
  - refund actions
  - debug inspector when active

### 12.3 UI Pop-ins

Use pop-ins sparingly but meaningfully:

- FX rate locked toast.
- Payment success receipt modal.
- Payment failed popover with error code.
- Refund requested confirmation panel.
- Business refund approval drawer.
- Debug request/response inspector.

## 13. Lifecycle Visualization

Lifecycle visualization is mandatory.

Customer timeline:

`Invoice Created -> Method Secured -> FX Applied -> Payment Created -> Validated -> Sent -> Completed -> USD Settled`

Business timeline:

`Invoice Issued -> Payment Received -> Processor Step -> USD Settlement -> Refund/Audit`

Animation style:

- Horizontal stepper for customer.
- Vertical detailed timeline for business.
- Soft active-step pulse.
- Completed steps fill progressively.
- Timeline entries reveal one by one.
- No loud celebration animation.

## 14. Demo and Debug Mode

Demo/debug mode is necessary for this project.

Reason:

- The backend lifecycle has multiple states that are otherwise hard to explain.
- A demo evaluator may ask to see success, failure, refund, FX, GST, and settlement
  behavior quickly.
- Debug mode proves that the UI is using real API calls and exposes the backend response
  shape.

### 14.1 Demo Mode

Demo mode is presentation-friendly.

Behavior:

- Auto-advance selected scenarios.
- Animate the lifecycle.
- Show polished toasts and receipt panels.
- Allow scenario selection:
  - successful INR card payment
  - successful EUR card payment settled in USD
  - USD bank transfer
  - failed card payment
  - bank transfer pending
  - refund pending approval
  - refund approved
  - refund rejected

### 14.2 Debug Mode

Debug mode is developer/evaluator-friendly.

Behavior:

- Disable auto-advance by default.
- Show exact API URL, method, request JSON, and response JSON.
- Provide manual lifecycle buttons:
  - Validate
  - Send
  - Complete
  - Fail
  - Settle
  - Request refund
  - Approve refund
  - Reject refund
- Surface backend error responses as-is.

## 15. Security and Compliance Guidelines

This project is a demo system, but it must model safe payment behavior.

Rules:

- Never store raw card number.
- Never store CVV.
- Never store raw bank account number.
- Mask sensitive input immediately in the UI.
- Store only masked identifiers and token references.
- Use fake token values such as `tok_demo_card_4242`.
- Do not log raw payment method input.
- Business UI should show masked identifiers only.
- Audit-sensitive actions should write clear notes into `payment_status_history`.

PCI note:

- A real production system must use a PCI-compliant payment provider or tokenizer.
- If raw card data touches the backend, PCI scope increases significantly.
- This project avoids raw storage by converting entered values into masked display values
  and demo token references.

Encryption note:

- Production systems should encrypt sensitive token references at rest.
- Database credentials must not be hardcoded for production.

## 16. Mock Data Requirements

Rewrite `scripts/generate_data_sql.py` for the 7-table schema.

Seed data must include:

- Kishore as primary customer.
- Multiple additional customers for business dashboard volume.
- AI credit invoices:
  - Starter Credits
  - Pro Credits
  - Scale Credits
- INR, USD, and EUR invoices.
- GST amounts.
- Card and bank transfer methods.
- Exchange rates:
  - USD -> USD
  - INR -> USD
  - EUR -> USD
- Completed payments.
- Failed payments.
- Pending bank transfer payments.
- Completed USD settlements.
- Refund requested.
- Refund approved.
- Refund rejected.
- Multiple partial refunds.
- At least one cumulative refund edge case.
- At least one failed payment with retry guidance.

Data should be deterministic and repeatable.

## 17. Module Ownership

### 17.1 Neha

Neha owns **multi-currency implementation code only**:

- `exchange_rates` table access.
- FX lookup service.
- Currency selection handling in backend.
- USD conversion calculation.
- Payment response fields for FX rate and USD amount.
- Tests for INR/EUR/USD conversion.

### 17.2 Tharan

Tharan owns all remaining Phase 3 work:

- Spec update.
- 7-table schema redesign.
- Data generator rewrite.
- Invoice feature.
- Payment method masking/token model.
- Customer checkout UI.
- Business dashboard UI.
- Demo/debug mode.
- Refund workflow polish.
- Security/compliance notes.
- API integration.
- Final integration pass.

## 18. Implementation Phases

### Phase 0 - Spec Cleanup

Status: THIS SPEC

Tasks:

- Replace outdated spec content.
- Keep only the current two-screen frontend guide.
- Lock use case.
- Lock 7-table schema.
- Lock ownership.

### Phase 1 - Schema and Seed Data

Goal: make the database match the new product model.

Tasks:

- Rewrite `schema.sql` to the 7-table model.
- Rewrite `data.sql` through `scripts/generate_data_sql.py`.
- Seed Kishore and BND AI demo scenarios.
- Align datasource credentials with Docker.
- Preserve deterministic startup.

### Phase 2 - Backend API Evolution

Goal: make current payment engine invoice-aware and FX-aware.

Tasks:

- Add invoice creation.
- Add bootstrap endpoint.
- Update payment creation to require invoice context.
- Store payment method masked/token data.
- Apply exchange rate snapshot.
- Store USD settlement amount.
- Keep status transition engine.
- Add dashboard aggregate endpoint.
- Add refund approve/reject flow against `refunds`.

### Phase 3 - Customer Frontend Redesign

Goal: make Kishore's checkout feel like a real BND AI purchase flow.

Tasks:

- Redesign `frontend-user/index.html`.
- Add AI credit pack selector.
- Add invoice summary with GST.
- Add currency selector and FX preview.
- Add card/bank method tabs.
- Add live masking.
- Add animated lifecycle.
- Add receipt and refund request UI.

### Phase 4 - Business Frontend Redesign

Goal: make BND business ops dashboard polished and useful.

Tasks:

- Redesign `frontend-business/ops.html`.
- Brand as `BND AI Payments`.
- Add KPI dashboard.
- Add invoice/payment table.
- Add detail drawer/modal.
- Add refund approval UI.
- Add USD settlement visibility.
- Add lifecycle timeline.

### Phase 5 - Demo/Debug and Edge Cases

Goal: make the project presentation-ready.

Tasks:

- Add scenario selector.
- Add demo auto-play.
- Add debug request/response inspector.
- Add manual lifecycle buttons.
- Seed all edge cases.
- Verify customer and business screens show the same payment from two angles.

### Phase 6 - Verification

Goal: prove integrations work.

Tasks:

- Run backend tests.
- Run manual customer checkout.
- Run manual business approval.
- Validate FX and USD settlement.
- Validate refunds.
- Validate failed payment paths.
- Validate demo/debug mode.

## 19. Two-to-Three-Hour Priority Order

If time is tight, build in this order:

1. Update spec.
2. Fix schema and seed data for the 7-table model.
3. Implement invoice + FX fields in backend minimally.
4. Redesign customer checkout.
5. Redesign business dashboard.
6. Add lifecycle animation.
7. Add demo/debug controls only after the core flow works.

Do not spend time on:

- React migration.
- Real payment gateway.
- Real FX API.
- Autopay.
- Full subscription renewal engine.
- Authentication.

## 20. Future Scope

Future features after this phase:

- Autopay for subscription renewal.
- Usage-based token billing.
- Saved payment methods with real provider tokens.
- Live exchange-rate provider.
- Rate lock expiry.
- Retry/rerouting rules.
- Webhook simulation.
- Authentication and role-based access.
- React/Vite frontend migration.
- Real audit event table if the 7-table constraint is lifted.

## 21. Definition of Done

Phase 3 is done when:

- Kishore can generate an invoice and pay for BND AI credits.
- User can choose INR, USD, or EUR.
- UI shows GST and USD settlement preview.
- Card and bank inputs are masked.
- Payment lifecycle is visually animated.
- Business dashboard shows BND receiving the payment in USD.
- Refund request, approval, and rejection are demonstrable.
- Demo/debug mode can show at least five edge cases.
- Seed data covers the presentation scenarios.
- Spec, schema, data, backend, and frontend agree with each other.
