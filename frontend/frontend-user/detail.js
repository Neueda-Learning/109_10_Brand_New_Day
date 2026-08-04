// Page-specific logic for detail.html (M3 - payment detail + refund action).
// Wired to GET /api/payments/{id} and POST /api/payments/{id}/refund. The refund
// button is only shown when the loaded payment's status is COMPLETED and its
// type is PAYMENT (spec.md Section 9 - M3 frontend).

const API_BASE = "http://localhost:8080/api/payments";

let loadedPaymentId = null;

document.getElementById("load-payment-btn").addEventListener("click", async function () {
  const refundError = document.getElementById("refund-error");
  refundError.hidden = true;
  document.getElementById("refund-form").hidden = true;
  document.getElementById("refund-btn").hidden = true;

  const id = document.getElementById("paymentId").value.trim();
  if (!id) {
    return;
  }

  try {
    const res = await fetch(`${API_BASE}/${id}`);
    const data = await res.json();

    if (!res.ok) {
      refundError.textContent = data.message || "Payment not found";
      refundError.hidden = false;
      document.getElementById("detail-card").hidden = true;
      return;
    }

    loadedPaymentId = data.id;
    document.getElementById("detail-source").textContent = data.sourceAccount;
    document.getElementById("detail-destination").textContent = data.destinationAccount;
    document.getElementById("detail-amount").textContent = data.amount;
    document.getElementById("detail-currency").textContent = data.currency;
    document.getElementById("detail-status").textContent = data.status;
    document.getElementById("detail-type").textContent = data.type;
    document.getElementById("detail-card").hidden = false;

    const canRefund = data.status === "COMPLETED" && data.type === "PAYMENT";
    document.getElementById("refund-btn").hidden = !canRefund;
  } catch (err) {
    refundError.textContent = "Network error: " + err.message;
    refundError.hidden = false;
  }
});

document.getElementById("refund-btn").addEventListener("click", function () {
  document.getElementById("refund-form").hidden = false;
});

document.getElementById("submit-refund-btn").addEventListener("click", async function () {
  const refundError = document.getElementById("refund-error");
  refundError.hidden = true;

  const amount = parseFloat(document.getElementById("refund-amount").value);
  const reason = document.getElementById("refund-reason").value;

  const body = { amount, reason: reason || null };

  try {
    const res = await fetch(`${API_BASE}/${loadedPaymentId}/refund`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body)
    });

    const data = await res.json();

    if (!res.ok) {
      refundError.textContent = data.message || "Refund failed";
      refundError.hidden = false;
      return;
    }

    refundError.hidden = true;
    document.getElementById("refund-form").hidden = true;
    alert(`Refund created: id=${data.id}, status=${data.status}`);
  } catch (err) {
    refundError.textContent = "Network error: " + err.message;
    refundError.hidden = false;
  }
});
