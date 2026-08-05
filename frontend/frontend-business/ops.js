// Page-specific logic for ops.html (M4 - unified business operations dashboard,
// spec.md Section 14.1). Replaces dashboard.js + audit.js. Wires:
//   - GET /api/payments/insights (Section 10.10) for the KPI cards
//   - GET /api/payments (Section 10.3) for the filterable/searchable table
//   - GET /api/payments/{id}/history (Section 10.4) for the detail timeline
//   - POST /api/payments/{id}/refund/approve|reject (Section 10.8/10.9) for actions
//
// NOTE (updated 2026-08-05): the insights endpoint, paymentMethod/approvalStatus
// filtering, and the Approve/Reject actions are all live on `main` today - the refund
// approval workflow and insights aggregate endpoint have been implemented and tested.
// The error-message/"Unavailable" fallback paths below are kept only as defensive UI
// (e.g. backend temporarily down), not because the endpoints are unimplemented.

var API_BASE_URL = "http://localhost:8080";
var PAYMENTS_API = API_BASE_URL + "/api/payments";
var PAGE_SIZE = 20;

var currentPage = 0;
var currentFilters = {};
var selectedPaymentId = null;

var filterForm = document.getElementById("filter-form");
var resultsBody = document.getElementById("results-body");
var paginationInfo = document.getElementById("pagination-info");
var errorMessage = document.getElementById("error-message");
var prevPageBtn = document.getElementById("prev-page-btn");
var nextPageBtn = document.getElementById("next-page-btn");

var detailCard = document.getElementById("detail-card");
var timelineEl = document.getElementById("timeline");
var approvalActions = document.getElementById("detail-approval-actions");
var approvalError = document.getElementById("approval-error");
var demoAdvanceBtn = document.getElementById("demo-advance-btn");
var debugLogPanel = document.getElementById("debug-log-panel");

// --- Theme / mode toggles (Section 14.2/14.3) ---
AppMode.initThemeToggle(document.getElementById("theme-toggle"));
AppMode.initModeToggle(document.getElementById("mode-toggle"));

// --- KPI cards (Section 10.10) ---
function loadInsights() {
  var kpiError = document.getElementById("kpi-error-message");
  kpiError.hidden = true;

  fetch(API_BASE_URL + "/api/payments/insights")
    .then(function (response) {
      if (!response.ok) {
        throw new Error("Insights endpoint not available yet (status " + response.status + ")");
      }
      return response.json();
    })
    .then(function (insights) {
      document.getElementById("kpi-total-count").textContent = insights.totalCount;
      document.getElementById("kpi-total-amount").textContent = Number(insights.totalAmount).toLocaleString();
      document.getElementById("kpi-success-rate").textContent = (insights.successRate * 100).toFixed(1) + "%";
      document.getElementById("kpi-refund-rate").textContent = (insights.refundRate * 100).toFixed(1) + "%";
      document.getElementById("kpi-pending-approval").textContent = insights.pendingApprovalCount;
    })
    .catch(function (err) {
      kpiError.textContent = "KPI insights unavailable: " + err.message;
      kpiError.hidden = false;
    });
}

// --- Filter/search table (carried over from dashboard.js) ---
filterForm.addEventListener("submit", function (event) {
  event.preventDefault();
  currentFilters = collectFilters();
  currentPage = 0;
  loadPayments();
});

prevPageBtn.addEventListener("click", function () {
  if (currentPage > 0) {
    currentPage -= 1;
    loadPayments();
  }
});

nextPageBtn.addEventListener("click", function () {
  currentPage += 1;
  loadPayments();
});

function collectFilters() {
  var formData = new FormData(filterForm);
  var filters = {};
  formData.forEach(function (value, key) {
    if (value) {
      filters[key] = value;
    }
  });
  return filters;
}

function loadPayments() {
  var params = new URLSearchParams(currentFilters);
  params.set("page", currentPage);
  params.set("size", PAGE_SIZE);

  hideError();

  fetch(PAYMENTS_API + "?" + params.toString())
    .then(function (response) {
      if (!response.ok) {
        throw new Error("Request failed with status " + response.status);
      }
      return response.json();
    })
    .then(renderResults)
    .catch(function (err) {
      showError("Failed to load payments: " + err.message);
    });
}

function renderResults(result) {
  resultsBody.innerHTML = "";

  result.content.forEach(function (payment) {
    var row = document.createElement("tr");

    row.appendChild(makeCell(payment.id));
    row.appendChild(makeCell(payment.sourceAccount));
    row.appendChild(makeCell(payment.destinationAccount));
    row.appendChild(makeCell(payment.amount.toFixed(2)));

    var statusCell = document.createElement("td");
    var badge = document.createElement("span");
    badge.className = "status-badge " + payment.status;
    badge.textContent = payment.status;
    statusCell.appendChild(badge);
    row.appendChild(statusCell);

    row.appendChild(makeCell(payment.type));
    // paymentMethod/approvalStatus (added 2026-08-05) - not yet on PaymentResponse; renders "—" until then.
    row.appendChild(makeCell(payment.paymentMethod || "—"));
    row.appendChild(makeCell(payment.approvalStatus || "—"));
    row.appendChild(makeCell(new Date(payment.createdAt).toLocaleString()));

    var actionCell = document.createElement("td");
    var viewBtn = document.createElement("button");
    viewBtn.type = "button";
    viewBtn.className = "btn btn-outline-primary btn-sm";
    viewBtn.innerHTML = '<i class="bi bi-eye"></i> View';
    viewBtn.addEventListener("click", function () {
      loadDetail(payment);
    });
    actionCell.appendChild(viewBtn);
    row.appendChild(actionCell);

    resultsBody.appendChild(row);
  });

  var totalPages = Math.max(1, Math.ceil(result.totalElements / result.size));
  paginationInfo.textContent = "Page " + (result.page + 1) + " of " + totalPages + " (total " + result.totalElements + ")";

  prevPageBtn.disabled = result.page <= 0;
  nextPageBtn.disabled = result.page + 1 >= totalPages;
}

function makeCell(text) {
  var cell = document.createElement("td");
  cell.textContent = text;
  return cell;
}

function showError(message) {
  errorMessage.textContent = message;
  errorMessage.hidden = false;
}

function hideError() {
  errorMessage.hidden = true;
  errorMessage.textContent = "";
}

// --- Detail panel + timeline (carried over from audit.js) ---
function loadDetail(payment) {
  selectedPaymentId = payment.id;
  approvalError.hidden = true;

  document.getElementById("detail-id").textContent = payment.id;
  document.getElementById("detail-source").textContent = payment.sourceAccount;
  document.getElementById("detail-destination").textContent = payment.destinationAccount;
  document.getElementById("detail-amount").textContent = payment.amount;
  document.getElementById("detail-currency").textContent = payment.currency;
  var statusEl = document.getElementById("detail-status");
  statusEl.textContent = payment.status;
  statusEl.className = "status-badge " + payment.status;
  document.getElementById("detail-type").textContent = payment.type;

  // Approve/Reject actions (Section 10.8/10.9) - only ever visible once the backend
  // actually returns type=REFUND + approvalStatus=PENDING_APPROVAL (feature/m3-refund-approval).
  var canActOnApproval = payment.type === "REFUND" && payment.approvalStatus === "PENDING_APPROVAL";
  approvalActions.hidden = !canActOnApproval;

  // Demo mode auto-advance (Section 14.3) - only offered for non-terminal payments.
  var isTerminal = payment.status === "COMPLETED" || payment.status === "FAILED";
  demoAdvanceBtn.hidden = isTerminal || AppMode.getMode() !== "demo";

  loadHistory(payment.id, payment.approvalStatus);

  detailCard.hidden = false;
}

function loadHistory(paymentId, approvalStatus) {
  fetch(PAYMENTS_API + "/" + encodeURIComponent(paymentId) + "/history")
    .then(function (response) {
      if (!response.ok) {
        throw new Error("Request failed with status " + response.status);
      }
      return response.json();
    })
    .then(function (historyEntries) {
      renderLifecycleTimeline(timelineEl, historyEntries, { approvalStatus: approvalStatus });
    })
    .catch(function (err) {
      timelineEl.innerHTML = "";
      var errEl = document.createElement("p");
      errEl.className = "error-message";
      errEl.textContent = "Failed to load history: " + err.message;
      timelineEl.appendChild(errEl);
    });
}

demoAdvanceBtn.addEventListener("click", function () {
  if (!selectedPaymentId) {
    return;
  }
  demoAdvanceBtn.disabled = true;
  AppMode.autoAdvance(
    PAYMENTS_API,
    selectedPaymentId,
    function (updatedPayment) {
      loadHistory(updatedPayment.id, updatedPayment.approvalStatus);
      if (updatedPayment.status === "COMPLETED" || updatedPayment.status === "FAILED") {
        demoAdvanceBtn.disabled = false;
        demoAdvanceBtn.hidden = true;
        loadPayments();
      }
    },
    function (errorBody) {
      demoAdvanceBtn.disabled = false;
      approvalError.textContent = "Auto-advance stopped: " + (errorBody.message || "unknown error");
      approvalError.hidden = false;
    }
  );
});

// --- Refund approval actions (Section 10.8/10.9) ---
document.getElementById("approve-btn").addEventListener("click", function () {
  submitApprovalAction("approve", { approvedBy: getApproverName() });
});

document.getElementById("reject-btn").addEventListener("click", function () {
  var reason = prompt("Rejection reason:");
  if (!reason) {
    return;
  }
  submitApprovalAction("reject", { rejectedBy: getApproverName(), reason: reason });
});

function getApproverName() {
  return document.getElementById("approve-by").value.trim() || "ops-user";
}

function submitApprovalAction(action, body) {
  approvalError.hidden = true;
  var url = PAYMENTS_API + "/" + encodeURIComponent(selectedPaymentId) + "/refund/" + action;

  AppMode.logRequest(debugLogPanel, { method: "POST", url: url, body: body });

  fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  })
    .then(function (response) {
      return response.json().then(function (data) {
        AppMode.logResponse(debugLogPanel, { status: response.status, body: data });
        if (!response.ok) {
          throw new Error(data.message || (action + " failed"));
        }
        return data;
      });
    })
    .then(function (updatedPayment) {
      loadDetail(updatedPayment);
      loadPayments();
    })
    .catch(function (err) {
      approvalError.textContent = err.message;
      approvalError.hidden = false;
    });
}

// Debug mode inspector panel visibility follows the mode toggle.
document.getElementById("mode-toggle").addEventListener("change", function () {
  debugLogPanel.hidden = AppMode.getMode() !== "debug";
});
debugLogPanel.hidden = AppMode.getMode() !== "debug";

loadInsights();
loadPayments();
