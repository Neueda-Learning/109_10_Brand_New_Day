// Page-specific logic for audit.html (M2 - business-facing audit trail screen).
// Phase 1: shell only. Phase 2 (M2) wires this to GET /api/payments/{id}/history
// and renders it via lifecycle-timeline.js.

const API_BASE = "http://localhost:8080/api";

document.getElementById("load-audit-btn").addEventListener("click", async function () {
    const paymentId = document.getElementById("paymentId").value.trim();

    if (!paymentId) {
        alert("Please enter a Payment ID");
        return;
    }

    try {
        const response = await fetch(`${API_BASE}/payments/${paymentId}/history`);

        if (!response.ok) {
            if (response.status === 404) {
                alert(`Payment ${paymentId} not found`);
            } else {
                const error = await response.json();
                alert(`Error: ${error.message || response.statusText}`);
            }
            return;
        }

        const historyEntries = await response.json();

        document.getElementById("timeline-card").hidden = false;

        const timelineContainer = document.getElementById("timeline");
        timelineContainer.innerHTML = "";
        renderLifecycleTimeline(timelineContainer, historyEntries);

    } catch (error) {
        alert(`Error fetching history: ${error.message}`);
        console.error(error);
    }
});