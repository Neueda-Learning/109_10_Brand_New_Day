"""
One-time generator for backend/src/main/resources/data.sql (spec.md Section 11 /
product.md Section 16). Rewritten 2026-08-05 for the 7-table BND AI Billing schema
(customers, exchange_rates, invoices, payment_methods, payments,
payment_status_history, refunds) - replaces the earlier 2-table single-currency
generator.

Not part of the backend build - run manually to (re)produce the seed dataset.
Deterministic: fixed random seed, fixed base timestamps, no SQL-side RANDOM()/NOW().
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


def sql_str(v):
    if v is None:
        return "NULL"
    return "'" + str(v).replace("'", "''") + "'"


def fmt_ts(dt: datetime) -> str:
    return dt.strftime("%Y-%m-%d %H:%M:%S")


BASE_DATE = datetime(2026, 6, 1, 0, 0, 0, tzinfo=timezone.utc)

# --- Reference data -------------------------------------------------------------

# from_currency -> USD multiplier (product.md Section 7.4/11). USD->USD is always 1.0.
FX_RATES = {
    "USD": Decimal("1.00000000"),
    "INR": Decimal("0.01205000"),  # ~83 INR per USD
    "EUR": Decimal("1.09000000"),
}
FX_SOURCE = "SEEDED_DEMO_RATE"

GST_RATE = Decimal("0.18")

# product.md Section 16: Starter / Pro / Scale AI credit packs.
PACKS = {
    "AI_CREDITS_STARTER": {
        "name": "BND AI Starter Credits",
        "credit_units": 10000,
        "price": {"INR": Decimal("999.00"), "USD": Decimal("15.00"), "EUR": Decimal("14.00")},
    },
    "AI_CREDITS_PRO": {
        "name": "BND AI Pro Credits",
        "credit_units": 100000,
        "price": {"INR": Decimal("7999.00"), "USD": Decimal("99.00"), "EUR": Decimal("92.00")},
    },
    "AI_CREDITS_SCALE": {
        "name": "BND AI Scale Credits",
        "credit_units": 500000,
        "price": {"INR": Decimal("34999.00"), "USD": Decimal("420.00"), "EUR": Decimal("390.00")},
    },
}
PACK_CODES = list(PACKS.keys())
CURRENCIES = ["INR", "USD", "EUR"]

ERROR_CODES = [
    "INSUFFICIENT_FUNDS",
    "ACCOUNT_BLOCKED",
    "PROCESSOR_TIMEOUT",
    "INVALID_ACCOUNT",
    "FRAUD_SUSPECTED",
]

OTHER_CUSTOMER_NAMES = [
    "Ananya Rao", "Marcus Chen", "Sofia Alvarez", "Daniel Okafor", "Priya Nair",
    "Liam O'Brien", "Hana Suzuki", "Carlos Mendes", "Zara Ahmed", "Noah Fischer",
    "Meera Iyer", "Lucas Meyer", "Isabelle Dubois", "Rohan Kapoor",
]

CARD_LAST4_POOL = ["4242", "1881", "0005", "9999", "3311", "7654", "2210", "8890"]
BANK_LAST4_POOL = ["8921", "1145", "6602", "4478", "9930", "2087", "5561", "3312"]

REFUND_REASONS = [
    "Customer requested refund - duplicate charge",
    "Customer requested refund - service not used",
    "Customer requested refund - accidental purchase",
    "Customer requested refund - downgrade to a smaller pack",
]
REJECTION_REASONS = [
    "Refund window expired",
    "Insufficient justification provided",
    "Credits already substantially consumed",
]

# --- Accumulators ----------------------------------------------------------------

customers_rows = []
exchange_rates_rows = []
invoices_rows = []
payment_methods_rows = []
payments_rows = []
history_rows = []
refunds_rows = []

invoice_seq = 0
payment_method_seq = 0


def next_invoice_number():
    global invoice_seq
    invoice_seq += 1
    return f"INV-BND-{invoice_seq:06d}"


# --- Customers ---------------------------------------------------------------

def add_customer(tag, customer_ref, display_name, email, default_currency, created_at):
    row = {
        "id": det_uuid(f"customer-{tag}"),
        "customer_ref": customer_ref,
        "display_name": display_name,
        "email": email,
        "default_currency": default_currency,
        "created_at": created_at,
        "updated_at": created_at,
    }
    customers_rows.append(row)
    return row


kishore = add_customer("kishore", "CUS-KISHORE-001", "Kishore", "kishore@bnd-ai-demo.com", "INR", BASE_DATE)

other_customers = []
for i, name in enumerate(OTHER_CUSTOMER_NAMES, start=2):
    ref = f"CUS-{i:06d}"
    email = name.lower().replace(" ", ".").replace("'", "") + "@bnd-ai-demo.com"
    currency = CURRENCIES[i % len(CURRENCIES)]
    created_at = BASE_DATE + timedelta(days=i)
    other_customers.append(add_customer(f"other-{i}", ref, name, email, currency, created_at))

ALL_CUSTOMERS = [kishore] + other_customers

# --- Exchange rates ------------------------------------------------------------

for currency, rate in FX_RATES.items():
    exchange_rates_rows.append({
        "id": det_uuid(f"fx-{currency}-USD"),
        "from_currency": currency,
        "to_currency": "USD",
        "rate": rate,
        "effective_at": BASE_DATE,
        "source": FX_SOURCE,
        "created_at": BASE_DATE,
    })

FX_ROW_BY_CURRENCY = {r["from_currency"]: r for r in exchange_rates_rows}

# --- Payment methods (1-2 per customer) -----------------------------------------

def add_payment_method(customer, method_type, created_at):
    global payment_method_seq
    payment_method_seq += 1
    if method_type == "CARD":
        last4 = CARD_LAST4_POOL[payment_method_seq % len(CARD_LAST4_POOL)]
        display_label = f"Visa ending {last4}"
        masked_identifier = f"**** **** **** {last4}"
        token_ref = f"tok_demo_card_{last4}"
    else:
        last4 = BANK_LAST4_POOL[payment_method_seq % len(BANK_LAST4_POOL)]
        display_label = f"Bank account ending {last4}"
        masked_identifier = f"BANK **** {last4}"
        token_ref = f"tok_demo_bank_{last4}"

    row = {
        "id": det_uuid(f"pm-{payment_method_seq}"),
        "customer_id": customer["id"],
        "method_type": method_type,
        "display_label": display_label,
        "masked_identifier": masked_identifier,
        "token_ref": token_ref,
        "provider": "DEMO_TOKENIZER",
        "created_at": created_at,
        "updated_at": created_at,
    }
    payment_methods_rows.append(row)
    return row


methods_by_customer = {}
for customer in ALL_CUSTOMERS:
    card = add_payment_method(customer, "CARD", customer["created_at"])
    bank = add_payment_method(customer, "BANK_TRANSFER", customer["created_at"])
    methods_by_customer[customer["id"]] = {"CARD": card, "BANK_TRANSFER": bank}

# --- Core scenario builders ------------------------------------------------------

def make_invoice(customer, pack_code, currency, status, created_at):
    pack = PACKS[pack_code]
    subtotal = pack["price"][currency]
    gst = (subtotal * GST_RATE).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
    total = subtotal + gst
    row = {
        "id": det_uuid(f"invoice-{next_invoice_number()}"),
        "invoice_number": f"INV-BND-{invoice_seq:06d}",
        "customer_id": customer["id"],
        "product_name": pack["name"],
        "product_code": pack_code,
        "credit_units": pack["credit_units"],
        "subtotal_amount": subtotal,
        "gst_amount": gst,
        "total_amount": total,
        "currency": currency,
        "status": status,
        "created_at": created_at,
        "updated_at": created_at,
    }
    invoices_rows.append(row)
    return row


def fx_for(currency):
    if currency == "USD":
        return Decimal("1.00000000"), None
    fx_row = FX_ROW_BY_CURRENCY[currency]
    return fx_row["rate"], fx_row["id"]


def make_payment(invoice, method, payment_status, settlement_status, created_at,
                  error_code=None, note_suffix=""):
    fx_rate, exchange_rate_id = fx_for(invoice["currency"])
    usd_amount = (invoice["total_amount"] * fx_rate).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
    pid = det_uuid(f"payment-{invoice['invoice_number']}")
    idem_key = f"idem-{invoice['invoice_number']}"

    row = {
        "id": pid,
        "invoice_id": invoice["id"],
        "customer_id": invoice["customer_id"],
        "payment_method_id": method["id"] if method else None,
        "idempotency_key": idem_key,
        "amount": invoice["total_amount"],
        "currency": invoice["currency"],
        "exchange_rate_id": exchange_rate_id,
        "fx_rate": fx_rate,
        "usd_amount": usd_amount,
        "status": payment_status,
        "settlement_status": settlement_status,
        "error_code": error_code,
        "created_at": created_at,
        "updated_at": created_at,
    }
    payments_rows.append(row)

    chain = ["CREATED"]
    if payment_status in ("VALIDATED", "SENT", "COMPLETED", "FAILED"):
        chain.append("VALIDATED")
    if payment_status in ("SENT", "COMPLETED", "FAILED"):
        chain.append("SENT")
    if payment_status in ("COMPLETED", "FAILED"):
        chain.append(payment_status)

    step_time = created_at
    from_status = None
    method_label = method["method_type"] if method else "BANK_TRANSFER"
    for step_i, to_status in enumerate(chain):
        step_time = step_time if step_i == 0 else step_time + timedelta(minutes=random.randint(1, 45))
        if to_status == "CREATED":
            note = f"Invoice {invoice['invoice_number']} payment created"
        elif to_status == "VALIDATED":
            note = f"Payment method tokenized ({method_label}); invoice validated"
        elif to_status == "SENT":
            note = (f"FX rate locked at {fx_rate} ({invoice['currency']}->USD)"
                    if invoice["currency"] != "USD"
                    else "Dispatched to processor (USD, no FX conversion needed)")
        elif to_status == "COMPLETED":
            note = f"Payment completed; USD settlement {settlement_status.lower()}{note_suffix}"
        else:  # FAILED
            note = f"Payment failed: {error_code}{note_suffix}"
        history_rows.append({
            "id": det_uuid(f"hist-{invoice['invoice_number']}-{step_i}"),
            "payment_id": pid,
            "from_status": from_status,
            "to_status": to_status,
            "changed_at": step_time,
            "triggered_by": "SYSTEM",
            "note": note,
        })
        from_status = to_status

    return row


def make_refund(payment, amount, approval_status, refund_status, reason, created_at,
                approved_by=None, approved_at=None, rejection_reason=None, tag=""):
    fx_rate, _ = fx_for(payment["currency"])
    usd_amount = (amount * fx_rate).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
    row = {
        "id": det_uuid(f"refund-{payment['id']}-{tag}"),
        "payment_id": payment["id"],
        "amount": amount,
        "currency": payment["currency"],
        "usd_amount": usd_amount,
        "reason": reason,
        "approval_status": approval_status,
        "status": refund_status,
        "approved_by": approved_by,
        "approved_at": approved_at,
        "rejection_reason": rejection_reason,
        "created_at": created_at,
        "updated_at": approved_at or created_at,
    }
    refunds_rows.append(row)
    return row


# --- Kishore's hand-placed demo scenarios (product.md Section 16) ---------------

k_methods = methods_by_customer[kishore["id"]]
t = BASE_DATE + timedelta(days=5)

# 1. INR Starter, card, fully completed + settled in USD.
inv1 = make_invoice(kishore, "AI_CREDITS_STARTER", "INR", "PAID", t)
make_payment(inv1, k_methods["CARD"], "COMPLETED", "SETTLED", t)

# 2. USD Pro, bank transfer, completed but settlement still pending.
t = t + timedelta(days=1)
inv2 = make_invoice(kishore, "AI_CREDITS_PRO", "USD", "PAID", t)
make_payment(inv2, k_methods["BANK_TRANSFER"], "COMPLETED", "PENDING", t)

# 3. EUR Scale, card, completed + settled (multi-currency showcase).
t = t + timedelta(days=1)
inv3 = make_invoice(kishore, "AI_CREDITS_SCALE", "EUR", "PAID", t)
make_payment(inv3, k_methods["CARD"], "COMPLETED", "SETTLED", t)

# 4. INR Starter, card, FAILED with retry guidance.
t = t + timedelta(days=1)
inv4 = make_invoice(kishore, "AI_CREDITS_STARTER", "INR", "FAILED", t)
make_payment(inv4, k_methods["CARD"], "FAILED", "NOT_READY", t,
             error_code="INSUFFICIENT_FUNDS",
             note_suffix=" - customer advised to retry with a different card or add funds")

# 5. USD Pro, bank transfer still in flight (SENT, not yet COMPLETED).
t = t + timedelta(days=1)
inv5 = make_invoice(kishore, "AI_CREDITS_PRO", "USD", "PAYMENT_PENDING", t)
make_payment(inv5, k_methods["BANK_TRANSFER"], "SENT", "NOT_READY", t)

# 6. INR Starter, completed, refund PENDING_APPROVAL (invoice REFUND_REQUESTED).
t = t + timedelta(days=1)
inv6 = make_invoice(kishore, "AI_CREDITS_STARTER", "INR", "REFUND_REQUESTED", t)
pay6 = make_payment(inv6, k_methods["CARD"], "COMPLETED", "SETTLED", t)
make_refund(pay6, pay6["amount"], "PENDING_APPROVAL", "REQUESTED",
            REFUND_REASONS[0], t + timedelta(days=1), tag="a")

# 7. USD Pro, completed, refund fully APPROVED + COMPLETED (invoice REFUNDED).
t = t + timedelta(days=1)
inv7 = make_invoice(kishore, "AI_CREDITS_PRO", "USD", "REFUNDED", t)
pay7 = make_payment(inv7, k_methods["BANK_TRANSFER"], "COMPLETED", "SETTLED", t)
make_refund(pay7, pay7["amount"], "APPROVED", "COMPLETED", REFUND_REASONS[1],
            t + timedelta(days=1), approved_by="ops-priya",
            approved_at=t + timedelta(days=2), tag="a")

# 8. EUR Scale, completed, refund REJECTED (invoice stays PAID).
t = t + timedelta(days=1)
inv8 = make_invoice(kishore, "AI_CREDITS_SCALE", "EUR", "PAID", t)
pay8 = make_payment(inv8, k_methods["CARD"], "COMPLETED", "SETTLED", t)
make_refund(pay8, pay8["amount"], "REJECTED", "REJECTED", REFUND_REASONS[2],
            t + timedelta(days=1), approved_by="ops-priya",
            approved_at=t + timedelta(days=2), rejection_reason=REJECTION_REASONS[0], tag="a")

# 9. INR Pro, completed, two cumulative partial refunds summing to the full amount
#    (invoice REFUNDED once fully covered) - cumulative refund edge case.
t = t + timedelta(days=1)
inv9 = make_invoice(kishore, "AI_CREDITS_PRO", "INR", "REFUNDED", t)
pay9 = make_payment(inv9, k_methods["CARD"], "COMPLETED", "SETTLED", t)
part1 = (pay9["amount"] * Decimal("0.4")).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
part2 = (pay9["amount"] - part1).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
make_refund(pay9, part1, "APPROVED", "COMPLETED", REFUND_REASONS[3],
            t + timedelta(days=1), approved_by="ops-priya", approved_at=t + timedelta(days=2), tag="a")
make_refund(pay9, part2, "APPROVED", "COMPLETED", REFUND_REASONS[3],
            t + timedelta(days=3), approved_by="ops-priya", approved_at=t + timedelta(days=4), tag="b")

# 10. INR Starter, ISSUED - no payment yet (pre-checkout state).
t = t + timedelta(days=1)
make_invoice(kishore, "AI_CREDITS_STARTER", "INR", "ISSUED", t)

# A handful of extra plain-completed invoices for Kishore's own history volume.
for i in range(6):
    t = t + timedelta(days=2)
    pack = PACK_CODES[i % len(PACK_CODES)]
    currency = CURRENCIES[i % len(CURRENCIES)]
    inv = make_invoice(kishore, pack, currency, "PAID", t)
    method = k_methods["CARD"] if i % 2 == 0 else k_methods["BANK_TRANSFER"]
    make_payment(inv, method, "COMPLETED", "SETTLED" if i % 3 != 0 else "PENDING", t)

# --- Bulk randomized volume for the other 14 customers (dashboard realism) ------

STATUS_WEIGHTED = (
    ["PAID"] * 55
    + ["FAILED"] * 15
    + ["PAYMENT_PENDING"] * 10
    + ["REFUND_REQUESTED"] * 5
    + ["REFUNDED"] * 10
    + ["ISSUED"] * 5
)

for customer in other_customers:
    per_customer = random.randint(8, 14)
    t = customer["created_at"] + timedelta(days=1)
    methods = methods_by_customer[customer["id"]]
    for i in range(per_customer):
        t = t + timedelta(days=random.randint(1, 5), hours=random.randint(0, 23))
        pack = random.choice(PACK_CODES)
        currency = random.choice(CURRENCIES)
        status = random.choice(STATUS_WEIGHTED)
        method = methods["CARD"] if random.random() < 0.6 else methods["BANK_TRANSFER"]

        if status == "ISSUED":
            make_invoice(customer, pack, currency, "ISSUED", t)
            continue

        if status == "PAYMENT_PENDING":
            inv = make_invoice(customer, pack, currency, "PAYMENT_PENDING", t)
            make_payment(inv, method, random.choice(["CREATED", "VALIDATED", "SENT"]), "NOT_READY", t)
            continue

        if status == "FAILED":
            inv = make_invoice(customer, pack, currency, "FAILED", t)
            make_payment(inv, method, "FAILED", "NOT_READY", t,
                         error_code=random.choice(ERROR_CODES))
            continue

        # PAID / REFUND_REQUESTED / REFUNDED all start from a completed payment.
        settlement = "SETTLED" if random.random() < 0.7 else "PENDING"
        inv = make_invoice(customer, pack, currency, "PAID", t)
        pay = make_payment(inv, method, "COMPLETED", settlement, t)

        if status == "REFUND_REQUESTED":
            inv["status"] = "REFUND_REQUESTED"
            fraction = Decimal(str(round(random.uniform(0.3, 1.0), 2)))
            amt = (pay["amount"] * fraction).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
            make_refund(pay, amt, "PENDING_APPROVAL", "REQUESTED",
                        random.choice(REFUND_REASONS), t + timedelta(days=1), tag=f"b{i}")
        elif status == "REFUNDED":
            inv["status"] = "REFUNDED"
            make_refund(pay, pay["amount"], "APPROVED", "COMPLETED",
                        random.choice(REFUND_REASONS), t + timedelta(days=1),
                        approved_by="ops-priya", approved_at=t + timedelta(days=2), tag=f"b{i}")

# --- Write SQL --------------------------------------------------------------------

lines = []
lines.append("-- Generated seed dataset (spec.md Section 11 / product.md Section 16).")
lines.append("-- Deterministic output of scripts/generate_data_sql.py - do not hand-edit; regenerate instead.")
lines.append("-- All timestamps are UTC (spec.md Section 7 / product.md Section 7).")
lines.append("")
lines.append(f"-- {len(customers_rows)} customers, {len(exchange_rates_rows)} exchange_rates, "
              f"{len(invoices_rows)} invoices, {len(payment_methods_rows)} payment_methods, "
              f"{len(payments_rows)} payments, {len(history_rows)} payment_status_history, "
              f"{len(refunds_rows)} refunds.")
lines.append("")

BATCH = 50


def emit(table, columns, rows, row_to_values):
    for i in range(0, len(rows), BATCH):
        batch = rows[i:i + BATCH]
        lines.append(f"INSERT INTO {table} ({columns}) VALUES")
        value_lines = ["(" + ", ".join(row_to_values(r)) + ")" for r in batch]
        lines.append(",\n".join(value_lines) + ";")
        lines.append("")


emit("customers", "id, customer_ref, display_name, email, default_currency, created_at, updated_at",
     customers_rows, lambda c: [
         sql_str(c["id"]), sql_str(c["customer_ref"]), sql_str(c["display_name"]),
         sql_str(c["email"]), sql_str(c["default_currency"]),
         sql_str(fmt_ts(c["created_at"])), sql_str(fmt_ts(c["updated_at"])),
     ])

emit("exchange_rates", "id, from_currency, to_currency, rate, effective_at, source, created_at",
     exchange_rates_rows, lambda r: [
         sql_str(r["id"]), sql_str(r["from_currency"]), sql_str(r["to_currency"]),
         str(r["rate"]), sql_str(fmt_ts(r["effective_at"])), sql_str(r["source"]),
         sql_str(fmt_ts(r["created_at"])),
     ])

emit("invoices",
     "id, invoice_number, customer_id, product_name, product_code, credit_units, "
     "subtotal_amount, gst_amount, total_amount, currency, status, created_at, updated_at",
     invoices_rows, lambda inv: [
         sql_str(inv["id"]), sql_str(inv["invoice_number"]), sql_str(inv["customer_id"]),
         sql_str(inv["product_name"]), sql_str(inv["product_code"]), str(inv["credit_units"]),
         str(inv["subtotal_amount"]), str(inv["gst_amount"]), str(inv["total_amount"]),
         sql_str(inv["currency"]), sql_str(inv["status"]),
         sql_str(fmt_ts(inv["created_at"])), sql_str(fmt_ts(inv["updated_at"])),
     ])

emit("payment_methods",
     "id, customer_id, method_type, display_label, masked_identifier, token_ref, provider, "
     "created_at, updated_at",
     payment_methods_rows, lambda pm: [
         sql_str(pm["id"]), sql_str(pm["customer_id"]), sql_str(pm["method_type"]),
         sql_str(pm["display_label"]), sql_str(pm["masked_identifier"]), sql_str(pm["token_ref"]),
         sql_str(pm["provider"]), sql_str(fmt_ts(pm["created_at"])), sql_str(fmt_ts(pm["updated_at"])),
     ])

emit("payments",
     "id, invoice_id, customer_id, payment_method_id, idempotency_key, amount, currency, "
     "exchange_rate_id, fx_rate, usd_amount, status, settlement_status, error_code, "
     "created_at, updated_at",
     payments_rows, lambda p: [
         sql_str(p["id"]), sql_str(p["invoice_id"]), sql_str(p["customer_id"]),
         sql_str(p["payment_method_id"]), sql_str(p["idempotency_key"]), str(p["amount"]),
         sql_str(p["currency"]), sql_str(p["exchange_rate_id"]), str(p["fx_rate"]),
         str(p["usd_amount"]), sql_str(p["status"]), sql_str(p["settlement_status"]),
         sql_str(p["error_code"]), sql_str(fmt_ts(p["created_at"])), sql_str(fmt_ts(p["updated_at"])),
     ])

history_rows.sort(key=lambda h: (h["payment_id"], h["changed_at"]))
emit("payment_status_history",
     "id, payment_id, from_status, to_status, changed_at, triggered_by, note",
     history_rows, lambda h: [
         sql_str(h["id"]), sql_str(h["payment_id"]), sql_str(h["from_status"]),
         sql_str(h["to_status"]), sql_str(fmt_ts(h["changed_at"])), sql_str(h["triggered_by"]),
         sql_str(h["note"]),
     ])

emit("refunds",
     "id, payment_id, amount, currency, usd_amount, reason, approval_status, status, "
     "approved_by, approved_at, rejection_reason, created_at, updated_at",
     refunds_rows, lambda r: [
         sql_str(r["id"]), sql_str(r["payment_id"]), str(r["amount"]), sql_str(r["currency"]),
         str(r["usd_amount"]), sql_str(r["reason"]), sql_str(r["approval_status"]),
         sql_str(r["status"]), sql_str(r["approved_by"]),
         sql_str(fmt_ts(r["approved_at"]) if r["approved_at"] else None),
         sql_str(r["rejection_reason"]), sql_str(fmt_ts(r["created_at"])),
         sql_str(fmt_ts(r["updated_at"])),
     ])

output_path = "backend/src/main/resources/data.sql"
with open(output_path, "w", encoding="utf-8", newline="\n") as f:
    f.write("\n".join(lines))

print(f"Wrote {len(customers_rows)} customers, {len(exchange_rates_rows)} exchange_rates, "
      f"{len(invoices_rows)} invoices, {len(payment_methods_rows)} payment_methods, "
      f"{len(payments_rows)} payments, {len(history_rows)} payment_status_history, "
      f"{len(refunds_rows)} refunds to {output_path}")

