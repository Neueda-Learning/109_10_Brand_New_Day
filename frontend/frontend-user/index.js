// Page-specific logic for index.html (M1 - new payment form).
// Wired to POST /api/payments.

const API_BASE = "http://localhost:8080/api/payments";

document.getElementById("new-payment-form").addEventListener("submit", async function (event) {
  event.preventDefault();

  const errorDiv = document.getElementById("form-error");
  errorDiv.hidden = true;

  const body = {
    sourceAccount: document.getElementById("sourceAccount").value,
    destinationAccount: document.getElementById("destinationAccount").value,
    amount: parseFloat(document.getElementById("amount").value),
    currency: document.getElementById("currency").value,
    idempotencyKey: document.getElementById("idempotencyKey").value
  };

  try {
    const res = await fetch(API_BASE, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body)
    });

    const data = await res.json();

    if (!res.ok) {
      errorDiv.textContent = data.message || "Something went wrong";
      errorDiv.hidden = false;
      return;
    }

    document.getElementById("result-id").textContent = data.id;
    document.getElementById("result-status").textContent = data.status;
    document.getElementById("result-card").hidden = false;
  } catch (err) {
    errorDiv.textContent = "Network error: " + err.message;
    errorDiv.hidden = false;
  }
});