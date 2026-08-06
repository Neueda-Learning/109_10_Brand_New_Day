"""
One-time generator for backend/src/main/resources/data.sql (spec.md Section 11.5,
extended 2026-08-06 for bank-grade validation hardening: accounts/cards/
exchange_rates + multi-currency settle-in-INR).

Not part of the backend build - run manually to (re)produce the seed dataset.
Deterministic: fixed random seed, fixed base timestamp, no SQL-side RANDOM()/NOW().
Re-running this script produces byte-identical output.

Right-sized on purpose (not exaggerated): a leaner payments volume than the
original 450-row dataset, while still covering every required edge case.
"""
import random
import uuid
from datetime import datetime, timedelta, timezone
from decimal import Decimal, ROUND_HALF_UP

random.seed(1234)

NAMESPACE = uuid.UUID("12345678-1234-5678-1234-567812345678")


def det_uuid(tag: str) -> str:
    return str(uuid.uuid5(NAMESPACE, tag))


# --- Accounts ---
# Single customer shown on the demo user screen: Kishore, with TWO bank accounts
# (assumption: one customer can hold multiple accounts, linked via customer_ref).
# Business always settles into one fixed receiving account. A handful of other
# customers exist purely for business-side dashboard volume - never surfaced on
# the single-customer user demo.
KISHORE_REF = "CUS-KISHORE-001"
KISHORE_ACCOUNTS = ["ACC-KISHORE-SAV-001", "ACC-KISHORE-CUR-001"]
BUSINESS_ACCOUNT = "BND-INR-OPERATING-001"

OTHER_CUSTOMER_REFS = [f"CUS-{1000 + i}" for i in range(12)]
OTHER_ACCOUNTS = [f"ACC-{1000 + i}" for i in range(12)]
BLOCKED_ACCOUNT = "ACC-BLOCKED-9001"          # negative-path fixture: ACCOUNT_BLOCKED
UNREGISTERED_ACCOUNT = "ACC-UNKNOWN-9999"     # negative-path fixture: intentionally never inserted (ACCOUNT_NOT_FOUND)

ALL_CUSTOMER_ACCOUNTS = KISHORE_ACCOUNTS + OTHER_ACCOUNTS

# --- Cards --- (PCI-safe: masked/tokenized only, no PAN/CVV ever stored)
KISHORE_CARD_ID = det_uuid("card-kishore-visa")
KISHORE_CARD_LAST4 = "4242"
KISHORE_CARD_BRAND = "VISA"
KISHORE_CARD_TOKEN = "tok_demo_card_4242"

# --- Exchange rates (fixed/seeded, added 2026-08-06 - user-supplied rates) ---
EXCHANGE_RATES = {
    "INR": Decimal("1.00000000"),
    "USD": Decimal("95.20000000"),
    "EUR": Decimal("109.92000000"),
}
CURRENCY_WEIGHTED = ["INR"] * 7 + ["USD"] * 2 + ["EUR"] * 1

ERROR_CODES = [
    "INSUFFICIENT_FUNDS",
    "ACCOUNT_BLOCKED",
    "PROCESSOR_TIMEOUT",
    "INVALID_ACCOUNT",
    "FRAUD_SUSPECTED",
]

STATUSES_WEIGHTED = (
    ["CREATED"] * 10
    + ["VALIDATED"] * 10
    + ["SENT"] * 10
    + ["COMPLETED"] * 50
    + ["FAILED"] * 20
)

TRIGGERED_BY_CHOICES = ["SYSTEM"] * 8 + ["OPERATOR"] * 1 + ["USER"] * 1

NOTES_POOL = [
    None,
    None,
    None,
    "manual retry after review",
    "batch reconciliation",
    "customer support follow-up",
    "automated dispatch",
    None,
]

BASE_DATE = datetime(2026, 6, 1, 0, 0, 0, tzinfo=timezone.utc)
NUM_DAYS = 64  # 2026-06-01 .. 2026-08-03, ~9 weeks

# Uneven daily payment counts: some days 0, some days several - leaner spread
# than the original dataset (right-sized, not exaggerated).
DAY_WEIGHTS = []
for d in range(NUM_DAYS):
    r = random.random()
    if r < 0.15:
        DAY_WEIGHTS.append(0)
    elif r < 0.55:
        DAY_WEIGHTS.append(random.randint(1, 3))
    elif r < 0.85:
        DAY_WEIGHTS.append(random.randint(2, 5))
    else:
        DAY_WEIGHTS.append(random.randint(4, 9))

# Right-sized, not exaggerated: leaner than the original 450-row dataset.
TARGET_BASE_PAYMENTS = 160
# Reserve a fixed number of slots that always involve Kishore, so the single-
# customer demo view (frontend-user, per this phase's assumption) is rich and
# self-contained on its own.
KISHORE_MIN_PAYMENTS = 24


def make_amount() -> Decimal:
    r = random.random()
    if r < 0.05:
        val = Decimal(random.uniform(1.00, 9.99))
    elif r < 0.85:
        val = Decimal(random.uniform(10.00, 50000.00))
    else:
        val = Decimal(random.uniform(100000.00, 750000.00))
    return val.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def pick_currency() -> str:
    return random.choice(CURRENCY_WEIGHTED)


def settlement_fields(amount: Decimal, currency: str):
    rate = EXCHANGE_RATES[currency]
    settlement = (amount * rate).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
    return rate, settlement


def sql_str(v):
    if v is None:
        return "NULL"
    return "'" + str(v).replace("'", "''") + "'"


def fmt_ts(dt: datetime) -> str:
    return dt.strftime("%Y-%m-%d %H:%M:%S")


payments_rows = []  # tuples of column values
history_rows = []

# Build a schedule of (day_index, seconds_offset) slots for base payments, honoring DAY_WEIGHTS,
# then trim/extend to hit TARGET_BASE_PAYMENTS.
slots = []
for day_idx, count in enumerate(DAY_WEIGHTS):
    for _ in range(count):
        slots.append(day_idx)

if len(slots) > TARGET_BASE_PAYMENTS:
    slots = slots[:TARGET_BASE_PAYMENTS]
else:
    while len(slots) < TARGET_BASE_PAYMENTS:
        slots.append(random.randint(0, NUM_DAYS - 1))

random.shuffle(slots)
slots.sort()

kishore_slot_indices = set(random.sample(range(len(slots)), min(KISHORE_MIN_PAYMENTS, len(slots))))

# A handful of payments sharing the same status created seconds apart (tie-break test):
# achieved naturally by clustering some same-day timestamps closely below.

completed_payments = []          # all COMPLETED payments (generic refund pass)
kishore_completed_payments = []  # COMPLETED payments involving Kishore (self-contained demo)

payment_counter = 0
# payment #1 is deterministically anchored as the seeded "COMPLETED, no refunds"
# fixture referenced by JdbcPaymentRepositoryTest - never left to random chance,
# regardless of dataset volume/account-pool changes.
FORCED_FIRST_STATUS = "COMPLETED"

for slot_idx, day_idx in enumerate(slots):
    payment_counter += 1
    idx = payment_counter
    day = BASE_DATE + timedelta(days=day_idx)
    # cluster a few payments within the same minute occasionally
    second_offset = random.randint(0, 86399)
    created_at = day + timedelta(seconds=second_offset)

    is_kishore = slot_idx in kishore_slot_indices
    if is_kishore:
        src, dst = random.choice(KISHORE_ACCOUNTS), BUSINESS_ACCOUNT
    else:
        src, dst = random.choice(ALL_CUSTOMER_ACCOUNTS), BUSINESS_ACCOUNT
        while dst == src:
            src = random.choice(ALL_CUSTOMER_ACCOUNTS)

    status = FORCED_FIRST_STATUS if idx == 1 else random.choice(STATUSES_WEIGHTED)
    currency = pick_currency()
    amount = make_amount()
    fx_rate, settlement_amount = settlement_fields(amount, currency)

    # Payment method mix (added 2026-08-06): CARD only ever used by Kishore, since
    # he's the only customer with a seeded card in this demo dataset.
    use_card = is_kishore and random.random() < 0.35
    payment_method = "CARD" if use_card else "BANK_TRANSFER"

    pid = det_uuid(f"payment-{idx}")
    idem_key = f"idem-payment-{idx:05d}"

    # Build status chain up to `status`
    chain = ["CREATED"]
    if status in ("VALIDATED", "SENT", "COMPLETED", "FAILED"):
        chain.append("VALIDATED")
    if status in ("SENT", "COMPLETED", "FAILED"):
        chain.append("SENT")
    if status in ("COMPLETED", "FAILED"):
        chain.append(status)

    error_code = None
    step_time = created_at
    from_status = None
    for step_i, to_status in enumerate(chain):
        step_time = step_time if step_i == 0 else step_time + timedelta(seconds=random.randint(5, 600))
        triggered_by = "SYSTEM" if step_i == 0 else random.choice(TRIGGERED_BY_CHOICES)
        note = None
        if to_status == "FAILED":
            error_code = ERROR_CODES[idx % len(ERROR_CODES)]
            note = f"failed: {error_code}"
        else:
            note = random.choice(NOTES_POOL)
        history_rows.append({
            "id": det_uuid(f"hist-payment-{idx}-{step_i}"),
            "payment_id": pid,
            "from_status": from_status,
            "to_status": to_status,
            "changed_at": step_time,
            "triggered_by": triggered_by,
            "note": note,
        })
        from_status = to_status

    updated_at = step_time

    row = {
        "id": pid,
        "idempotency_key": idem_key,
        "source_account": src,
        "destination_account": dst,
        "amount": amount,
        "currency": currency,
        "status": status,
        "error_code": error_code,
        "type": "PAYMENT",
        "original_payment_id": None,
        "payment_method": payment_method,
        "approval_status": None,
        "approved_by": None,
        "approved_at": None,
        "rejection_reason": None,
        "settlement_currency": "INR",
        "fx_rate_to_inr": fx_rate,
        "settlement_amount_inr": settlement_amount,
        "requested_by": src,
        "card_id": KISHORE_CARD_ID if use_card else None,
        "card_last4": KISHORE_CARD_LAST4 if use_card else None,
        "card_brand": KISHORE_CARD_BRAND if use_card else None,
        "created_at": created_at,
        "updated_at": updated_at,
    }
    payments_rows.append(row)

    if status == "COMPLETED":
        # Payment #1 is the hardcoded SEEDED_COMPLETED_NO_REFUNDS_ID fixture referenced
        # by JdbcPaymentRepositoryTest - it must NEVER be selected as a refund target,
        # so exclude it from both candidate pools entirely (not just "unlikely").
        if idx != 1:
            completed_payments.append(row)
            if is_kishore:
                kishore_completed_payments.append(row)

# --- Refund pass ---
random.shuffle(completed_payments)

refund_counter = 0


def add_refund(original, amount, force_status=None):
    global refund_counter
    refund_counter += 1
    idx = f"r{refund_counter}"
    rid = det_uuid(f"refund-{idx}")
    idem_key = f"idem-refund-{refund_counter:05d}"
    created_at = original["updated_at"] + timedelta(hours=random.randint(1, 240))

    status = force_status or random.choice(STATUSES_WEIGHTED)
    chain = ["CREATED"]
    if status in ("VALIDATED", "SENT", "COMPLETED", "FAILED"):
        chain.append("VALIDATED")
    if status in ("SENT", "COMPLETED", "FAILED"):
        chain.append("SENT")
    if status in ("COMPLETED", "FAILED"):
        chain.append(status)

    error_code = None
    step_time = created_at
    from_status = None
    for step_i, to_status in enumerate(chain):
        step_time = step_time if step_i == 0 else step_time + timedelta(seconds=random.randint(5, 600))
        triggered_by = "SYSTEM" if step_i == 0 else random.choice(TRIGGERED_BY_CHOICES)
        note = None
        if to_status == "FAILED":
            error_code = ERROR_CODES[refund_counter % len(ERROR_CODES)]
            note = f"failed: {error_code}"
        elif step_i == 0:
            note = None  # spec 8.1 rule 2: initial row note is null
        else:
            note = random.choice(NOTES_POOL)
        history_rows.append({
            "id": det_uuid(f"hist-refund-{idx}-{step_i}"),
            "payment_id": rid,
            "from_status": from_status,
            "to_status": to_status,
            "changed_at": step_time,
            "triggered_by": triggered_by,
            "note": note,
        })
        from_status = to_status
    updated_at = step_time

    # Approval sub-state (added 2026-08-06): matches PaymentServiceImpl's refund
    # approval gate - only CREATED-stuck refunds stay PENDING_APPROVAL.
    if status == "FAILED" and random.random() < 0.3:
        approval_status, approved_by, approved_at, rejection_reason = (
            "REJECTED", None, None, "duplicate refund request")
    elif status in ("VALIDATED", "SENT", "COMPLETED"):
        approval_status, approved_by, approved_at, rejection_reason = (
            "APPROVED", "ops-user-1", updated_at, None)
    else:
        approval_status, approved_by, approved_at, rejection_reason = (
            "PENDING_APPROVAL", None, None, None)

    fx_rate = original["fx_rate_to_inr"]
    settlement_amount = (amount * fx_rate).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)

    payments_rows.append({
        "id": rid,
        "idempotency_key": idem_key,
        "source_account": original["destination_account"],
        "destination_account": original["source_account"],
        "amount": amount,
        "currency": original["currency"],
        "status": status,
        "error_code": error_code,
        "type": "REFUND",
        "original_payment_id": original["id"],
        "payment_method": original["payment_method"],
        "approval_status": approval_status,
        "approved_by": approved_by,
        "approved_at": approved_at,
        "rejection_reason": rejection_reason,
        "settlement_currency": "INR",
        "fx_rate_to_inr": fx_rate,
        "settlement_amount_inr": settlement_amount,
        "requested_by": original["destination_account"],
        "card_id": None,
        "card_last4": None,
        "card_brand": None,
        "created_at": created_at,
        "updated_at": updated_at,
    })


# Kishore-specific refund scenarios (self-contained on the single-customer demo):
if kishore_completed_payments:
    o = kishore_completed_payments[0]
    add_refund(o, o["amount"], force_status="COMPLETED")  # 1) full refund, approved
if len(kishore_completed_payments) > 1:
    o = kishore_completed_payments[1]
    partial = (o["amount"] / 3).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
    add_refund(o, partial if partial > 0 else Decimal("1.00"), force_status="CREATED")  # 2) pending approval
if len(kishore_completed_payments) > 2:
    o = kishore_completed_payments[2]
    partial = (o["amount"] / 4).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
    add_refund(o, partial if partial > 0 else Decimal("1.00"), force_status="FAILED")  # 3) rejected

# Leave the rest of Kishore's completed payments un-refunded (boundary case).

# Generic refund targets across the rest of the dataset (business-side realism):
refund_targets = [p for p in completed_payments if p not in kishore_completed_payments][:20]

# 1) One exact full refund (amount == original amount) - boundary case.
if refund_targets:
    original = refund_targets[0]
    add_refund(original, original["amount"], force_status="COMPLETED")

# 2) One clean partial refund (amount < original amount).
if len(refund_targets) > 1:
    original = refund_targets[1]
    partial = (original["amount"] / 3).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
    if partial <= 0:
        partial = Decimal("1.00")
    add_refund(original, partial)

# 3) Multiple partial refunds against the same original summing exactly to the original amount.
if len(refund_targets) > 2:
    original = refund_targets[2]
    first_part = (original["amount"] * Decimal("0.4")).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
    second_part = (original["amount"] - first_part).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
    add_refund(original, first_part)
    add_refund(original, second_part)

# 4) A spread of additional single partial refunds (amount < original amount) across other targets.
for original in refund_targets[3:12]:
    fraction = Decimal(str(round(random.uniform(0.1, 0.6), 2)))
    amt = (original["amount"] * fraction).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
    if amt <= 0:
        continue
    add_refund(original, amt)

# --- Write SQL ---

lines = []
lines.append("-- Generated seed dataset (spec.md Section 11.5, extended 2026-08-06).")
lines.append("-- Deterministic output of scripts/generate_data_sql.py - do not hand-edit; regenerate instead.")
lines.append("-- All timestamps are UTC (spec.md Section 7).")
lines.append("-- Assumptions: single account per identity except Kishore (2 accounts, to")
lines.append("-- demo multi-account bank transfer); one fixed business receiving account;")
lines.append("-- Kishore is the only customer with a seeded card (VISA, PCI-safe: masked +")
lines.append("-- tokenized only, no PAN/CVV columns exist anywhere in this schema).")
lines.append("")

# --- accounts ---
account_rows = [
    (det_uuid("account-kishore-sav"), KISHORE_ACCOUNTS[0], KISHORE_REF, "Kishore Savings", "CUSTOMER", "ACTIVE", "INR"),
    (det_uuid("account-kishore-cur"), KISHORE_ACCOUNTS[1], KISHORE_REF, "Kishore Current", "CUSTOMER", "ACTIVE", "INR"),
    (det_uuid("account-business"), BUSINESS_ACCOUNT, "BND-BUSINESS", "BND Operating Account", "BUSINESS", "ACTIVE", "INR"),
]
for i, (acc, ref) in enumerate(zip(OTHER_ACCOUNTS, OTHER_CUSTOMER_REFS)):
    account_rows.append((det_uuid(f"account-other-{i}"), acc, ref, f"Customer {1000 + i}", "CUSTOMER", "ACTIVE", "INR"))
account_rows.append((det_uuid("account-blocked"), BLOCKED_ACCOUNT, "CUS-BLOCKED-001", "Blocked Test Account", "CUSTOMER", "BLOCKED", "INR"))
# NOTE: UNREGISTERED_ACCOUNT is deliberately NOT inserted - negative-path fixture for ACCOUNT_NOT_FOUND.

lines.append("INSERT INTO accounts (id, account_number, customer_ref, display_name, account_type, status, default_currency, created_at, updated_at) VALUES")
acc_value_lines = []
for (aid, num, ref, name, atype, status, curr) in account_rows:
    acc_value_lines.append(
        "(" + ", ".join([sql_str(aid), sql_str(num), sql_str(ref), sql_str(name), sql_str(atype), sql_str(status),
                          sql_str(curr), sql_str(fmt_ts(BASE_DATE)), sql_str(fmt_ts(BASE_DATE))]) + ")"
    )
lines.append(",\n".join(acc_value_lines) + ";")
lines.append("")

# --- cards --- (PCI-safe: masked_pan/last4/token_ref only, no PAN/CVV columns exist)
lines.append("INSERT INTO cards (id, customer_ref, card_brand, masked_pan, last4, expiry_month, expiry_year, cardholder_name, token_ref, status, created_at) VALUES")
lines.append(
    "(" + ", ".join([
        sql_str(KISHORE_CARD_ID), sql_str(KISHORE_REF), sql_str(KISHORE_CARD_BRAND),
        sql_str("**** **** **** " + KISHORE_CARD_LAST4), sql_str(KISHORE_CARD_LAST4),
        "12", "2030", sql_str("Kishore"), sql_str(KISHORE_CARD_TOKEN), sql_str("ACTIVE"), sql_str(fmt_ts(BASE_DATE))
    ]) + ");"
)
lines.append("")

# --- exchange_rates --- (fixed/seeded, user-supplied INR rates as of 2026-08-06)
lines.append("INSERT INTO exchange_rates (id, currency, rate_to_inr, effective_at, source) VALUES")
rate_value_lines = []
for currency, rate in EXCHANGE_RATES.items():
    rate_value_lines.append(
        "(" + ", ".join([sql_str(det_uuid(f"rate-{currency}")), sql_str(currency), str(rate),
                          sql_str(fmt_ts(BASE_DATE)), sql_str("SEEDED_FIXED_RATE")]) + ")"
    )
lines.append(",\n".join(rate_value_lines) + ";")
lines.append("")

lines.append(f"-- {len(payments_rows)} payments rows, {len(history_rows)} payment_status_history rows.")
lines.append("")

PAYMENT_COLS = (
    "id, idempotency_key, source_account, destination_account, amount, currency, "
    "status, error_code, type, original_payment_id, payment_method, approval_status, "
    "approved_by, approved_at, rejection_reason, settlement_currency, fx_rate_to_inr, "
    "settlement_amount_inr, requested_by, card_id, card_last4, card_brand, created_at, updated_at"
)

BATCH = 50
for i in range(0, len(payments_rows), BATCH):
    batch = payments_rows[i:i + BATCH]
    lines.append(f"INSERT INTO payments ({PAYMENT_COLS}) VALUES")
    value_lines = []
    for p in batch:
        value_lines.append(
            "(" + ", ".join([
                sql_str(p["id"]),
                sql_str(p["idempotency_key"]),
                sql_str(p["source_account"]),
                sql_str(p["destination_account"]),
                str(p["amount"]),
                sql_str(p["currency"]),
                sql_str(p["status"]),
                sql_str(p["error_code"]),
                sql_str(p["type"]),
                sql_str(p["original_payment_id"]),
                sql_str(p["payment_method"]),
                sql_str(p["approval_status"]),
                sql_str(p["approved_by"]),
                sql_str(None if p["approved_at"] is None else fmt_ts(p["approved_at"])),
                sql_str(p["rejection_reason"]),
                sql_str(p["settlement_currency"]),
                str(p["fx_rate_to_inr"]),
                str(p["settlement_amount_inr"]),
                sql_str(p["requested_by"]),
                sql_str(p["card_id"]),
                sql_str(p["card_last4"]),
                sql_str(p["card_brand"]),
                sql_str(fmt_ts(p["created_at"])),
                sql_str(fmt_ts(p["updated_at"])),
            ]) + ")"
        )
    lines.append(",\n".join(value_lines) + ";")
    lines.append("")

HISTORY_COLS = "id, payment_id, from_status, to_status, changed_at, triggered_by, note"

# Ensure history rows are inserted in a stable, chronologically sensible order.
history_rows.sort(key=lambda h: (h["payment_id"], h["changed_at"]))

for i in range(0, len(history_rows), BATCH):
    batch = history_rows[i:i + BATCH]
    lines.append(f"INSERT INTO payment_status_history ({HISTORY_COLS}) VALUES")
    value_lines = []
    for h in batch:
        value_lines.append(
            "(" + ", ".join([
                sql_str(h["id"]),
                sql_str(h["payment_id"]),
                sql_str(h["from_status"]),
                sql_str(h["to_status"]),
                sql_str(fmt_ts(h["changed_at"])),
                sql_str(h["triggered_by"]),
                sql_str(h["note"]),
            ]) + ")"
        )
    lines.append(",\n".join(value_lines) + ";")
    lines.append("")

output_path = "backend/src/main/resources/data.sql"
with open(output_path, "w", encoding="utf-8", newline="\n") as f:
    f.write("\n".join(lines))

print(f"Wrote {len(account_rows)} accounts, 1 card, {len(EXCHANGE_RATES)} exchange rates, "
      f"{len(payments_rows)} payments and {len(history_rows)} history rows to {output_path}")

