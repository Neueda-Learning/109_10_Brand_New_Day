"""
One-time generator for backend/src/main/resources/data.sql (spec.md Section 11.5).

Not part of the backend build - run manually to (re)produce the seed dataset.
Deterministic: fixed random seed, fixed base timestamp, no SQL-side RANDOM()/NOW().
Re-running this script produces byte-identical output.
"""
import random
import uuid
from datetime import datetime, timedelta, timezone
from decimal import Decimal, ROUND_HALF_UP

random.seed(1234)

NAMESPACE = uuid.UUID("12345678-1234-5678-1234-567812345678")


def det_uuid(tag: str) -> str:
    return str(uuid.uuid5(NAMESPACE, tag))


ACCOUNTS = [f"ACC-{1000 + i}" for i in range(40)]

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

# Uneven daily payment counts: some days 0, some days dozens.
DAY_WEIGHTS = []
for d in range(NUM_DAYS):
    r = random.random()
    if r < 0.15:
        DAY_WEIGHTS.append(0)
    elif r < 0.55:
        DAY_WEIGHTS.append(random.randint(1, 5))
    elif r < 0.85:
        DAY_WEIGHTS.append(random.randint(6, 12))
    else:
        DAY_WEIGHTS.append(random.randint(15, 30))

TARGET_BASE_PAYMENTS = 450


def make_amount() -> Decimal:
    r = random.random()
    if r < 0.05:
        val = Decimal(random.uniform(1.00, 9.99))
    elif r < 0.85:
        val = Decimal(random.uniform(10.00, 50000.00))
    else:
        val = Decimal(random.uniform(100000.00, 750000.00))
    return val.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)


def pick_accounts():
    src = random.choice(ACCOUNTS)
    dst = random.choice(ACCOUNTS)
    while dst == src:
        dst = random.choice(ACCOUNTS)
    return src, dst


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

# A handful of payments sharing the same status created seconds apart (tie-break test):
# achieved naturally by clustering some same-day timestamps closely below.

completed_payments = []  # list of dicts for refund pass

payment_counter = 0
for day_idx in slots:
    payment_counter += 1
    idx = payment_counter
    day = BASE_DATE + timedelta(days=day_idx)
    # cluster a few payments within the same minute occasionally
    second_offset = random.randint(0, 86399)
    created_at = day + timedelta(seconds=second_offset)

    status = random.choice(STATUSES_WEIGHTED)
    src, dst = pick_accounts()
    amount = make_amount()
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

    payments_rows.append({
        "id": pid,
        "idempotency_key": idem_key,
        "source_account": src,
        "destination_account": dst,
        "amount": amount,
        "currency": "INR",
        "status": status,
        "error_code": error_code,
        "type": "PAYMENT",
        "original_payment_id": None,
        "created_at": created_at,
        "updated_at": updated_at,
    })

    if status == "COMPLETED":
        completed_payments.append(payments_rows[-1])

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

    payments_rows.append({
        "id": rid,
        "idempotency_key": idem_key,
        "source_account": original["destination_account"],
        "destination_account": original["source_account"],
        "amount": amount,
        "currency": "INR",
        "status": status,
        "error_code": error_code,
        "type": "REFUND",
        "original_payment_id": original["id"],
        "created_at": created_at,
        "updated_at": updated_at,
    })


# Leave majority of completed payments un-refunded (satisfies "COMPLETED with no refund").
refund_targets = completed_payments[:40] if len(completed_payments) > 40 else completed_payments

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
for original in refund_targets[3:40]:
    fraction = Decimal(str(round(random.uniform(0.1, 0.6), 2)))
    amt = (original["amount"] * fraction).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
    if amt <= 0:
        continue
    add_refund(original, amt)

# --- Write SQL ---

lines = []
lines.append("-- Generated seed dataset (spec.md Section 11.5).")
lines.append("-- Deterministic output of scripts/generate_data_sql.py - do not hand-edit; regenerate instead.")
lines.append("-- All timestamps are UTC (spec.md Section 7).")
lines.append("")

lines.append(f"-- {len(payments_rows)} payments rows, {len(history_rows)} payment_status_history rows.")
lines.append("")

PAYMENT_COLS = (
    "id, idempotency_key, source_account, destination_account, amount, currency, "
    "status, error_code, type, original_payment_id, created_at, updated_at"
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

print(f"Wrote {len(payments_rows)} payments and {len(history_rows)} history rows to {output_path}")
