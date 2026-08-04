// Page-specific logic for detail.html (M3 - payment detail + refund action).
// Phase 1: shell only. Phase 2 (M3) wires this to GET /api/payments/{id} and
// POST /api/payments/{id}/refund. The refund button is only shown when the
// loaded payment's status is COMPLETED (spec.md Section 9 - M3 frontend).

document.getElementById("load-payment-btn").addEventListener("click", function () {
  throw new Error("Not implemented yet - Phase 2 (M3)");
});

document.getElementById("refund-btn").addEventListener("click", function () {
  throw new Error("Not implemented yet - Phase 2 (M3)");
});
