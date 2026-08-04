// Page-specific logic for history.html (M4 - user-facing simplified history view).
// Wires the "Load History" button to GET /api/payments/{id}/history (spec.md
// Section 10.4) and renders it via lifecycle-timeline.js.

var API_BASE_URL = "http://localhost:8080";

var paymentIdInput = document.getElementById("paymentId");
var loadHistoryBtn = document.getElementById("load-history-btn");
var errorMessage = document.getElementById("error-message");
var timelineCard = document.getElementById("timeline-card");
var timelineEl = document.getElementById("timeline");

loadHistoryBtn.addEventListener("click", function () {
  var paymentId = paymentIdInput.value.trim();
  hideError();
  timelineCard.hidden = true;

  if (!paymentId) {
    showError("Enter a payment id.");
    return;
  }

  fetch(API_BASE_URL + "/api/payments/" + encodeURIComponent(paymentId) + "/history")
    .then(function (response) {
      if (!response.ok) {
        throw new Error("Request failed with status " + response.status);
      }
      return response.json();
    })
    .then(function (historyEntries) {
      renderLifecycleTimeline(timelineEl, historyEntries);
      timelineCard.hidden = false;
    })
    .catch(function (err) {
      showError("Failed to load history: " + err.message);
    });
});

function showError(message) {
  errorMessage.textContent = message;
  errorMessage.hidden = false;
}

function hideError() {
  errorMessage.hidden = true;
  errorMessage.textContent = "";
}
