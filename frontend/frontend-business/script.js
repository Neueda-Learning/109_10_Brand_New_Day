/*
 * frontend-business/script.js - unified single-page operations app (v2.2
 * redesign, spec.md Section 14.1). Replaces dashboard.html/js + audit.html/js.
 *
 * Responsibilities:
 *   - Search/filter/paginate payments via GET /api/payments.
 *   - Show KPI cards computed client-side (computeInsights() - swappable for
 *     the real GET /api/payments/insights endpoint once implemented, M4).
 *   - Open a Bootstrap offcanvas with full detail + lifecycle timeline
 *     (frontend-shared/lifecycle-timeline.js) for a selected payment.
 *   - Approve/Reject refund actions, shown only for type=REFUND &&
 *     approvalStatus=PENDING_APPROVAL, via POST /refund/approve|reject.
 *   - Demo/Debug mode + light/dark theme via frontend-shared/app-mode.js.
 */
(function () {
  var API_BASE = "http://localhost:8080/api/payments";
  var PAGE_SIZE = 20;
  var currentPage = 0;
  var currentFilters = {};
  var lastTotalElements = 0;
  var offcanvasInstance = null;

  document.addEventListener("DOMContentLoaded", function () {
    AppMode.initThemeToggle(document.getElementById("theme-toggle"));
    AppMode.initModeToggle(document.getElementById("mode-toggle"));

    offcanvasInstance = new bootstrap.Offcanvas(document.getElementById("detail-offcanvas"));

    document.getElementById("filter-form").addEventListener("submit", function (event) {
      event.preventDefault();
      currentPage = 0;
      currentFilters = collectFilters();
      loadResults();
    });
    document.getElementById("reset-filters-btn").addEventListener("click", function () {
      document.getElementById("filter-form").reset();
      currentPage = 0;
      currentFilters = {};
      loadResults();
    });
    document.getElementById("prev-page-btn").addEventListener("click", function () {
      if (currentPage > 0) {
        currentPage -= 1;
        loadResults();
      }
    });
    document.getElementById("next-page-btn").addEventListener("click", function () {
      if ((currentPage + 1) * PAGE_SIZE < lastTotalElements) {
        currentPage += 1;
        loadResults();
      }
    });

    loadInsights();
    loadResults();
  });

  function collectFilters() {
    var filters = {};
    var status = document.getElementById("status").value;
    var type = document.getElementById("type").value;
    var sourceAccount = document.getElementById("sourceAccount").value.trim();
    var destinationAccount = document.getElementById("destinationAccount").value.trim();
    var fromDate = document.getElementById("fromDate").value;
    var toDate = document.getElementById("toDate").value;
    if (status) filters.status = status;
    if (type) filters.type = type;
    if (sourceAccount) filters.sourceAccount = sourceAccount;
    if (destinationAccount) filters.destinationAccount = destinationAccount;
    if (fromDate) filters.fromDate = fromDate;
    if (toDate) filters.toDate = toDate;
    return filters;
  }

  function buildQuery(filters, page, size) {
    var params = new URLSearchParams(filters);
    params.set("page", page);
    params.set("size", size);
    return params.toString();
  }

  // --- KPI insights (client-side, swappable) ---

  async function loadInsights() {
    var result = await AppMode.fetchJson(API_BASE + "?page=0&size=100");
    if (!result.ok) {
      return;
    }
    var insights = computeInsights(result.data.content);
    document.getElementById("insight-total-payments").textContent = insights.totalPayments;
    document.getElementById("insight-total-amount").textContent = formatAmount(insights.totalAmount);
    document.getElementById("insight-pending-approval").textContent = insights.pendingApproval;
    document.getElementById("insight-success-rate").textContent = insights.successRate + "%";
  }

  /**
   * Client-side KPI aggregation over a page of PaymentResponse objects.
   * Isolated on purpose: swap the body of this function for a direct call to
   * GET /api/payments/insights once that endpoint is implemented (M4).
   */
  function computeInsights(payments) {
    payments = payments || [];
    var totalPayments = payments.filter(function (p) { return p.type === "PAYMENT"; }).length;
    var totalAmount = payments
      .filter(function (p) { return p.type === "PAYMENT"; })
      .reduce(function (sum, p) { return sum + Number(p.amount); }, 0);
    var pendingApproval = payments.filter(function (p) { return p.approvalStatus === "PENDING_APPROVAL"; }).length;
    var terminalCount = payments.filter(function (p) { return p.status === "COMPLETED" || p.status === "FAILED"; }).length;
    var completedCount = payments.filter(function (p) { return p.status === "COMPLETED"; }).length;
    var successRate = terminalCount === 0 ? 0 : Math.round((completedCount / terminalCount) * 100);
    return {
      totalPayments: totalPayments,
      totalAmount: totalAmount,
      pendingApproval: pendingApproval,
      successRate: successRate
    };
  }

  // --- Results table ---

  async function loadResults() {
    var errorEl = document.getElementById("results-error");
    errorEl.hidden = true;

    var query = buildQuery(currentFilters, currentPage, PAGE_SIZE);
    var result = await AppMode.fetchJson(API_BASE + "?" + query);
    if (!result.ok) {
      errorEl.textContent = extractErrorMessage(result.data);
      errorEl.hidden = false;
      return;
    }

    lastTotalElements = result.data.totalElements;
    var tbody = document.getElementById("results-tbody");
    tbody.innerHTML = "";
    result.data.content.forEach(function (payment) {
      tbody.appendChild(renderRow(payment));
    });

    var start = result.data.totalElements === 0 ? 0 : currentPage * PAGE_SIZE + 1;
    var end = Math.min((currentPage + 1) * PAGE_SIZE, result.data.totalElements);
    document.getElementById("results-summary").textContent =
      "Showing " + start + "-" + end + " of " + result.data.totalElements;
    document.getElementById("prev-page-btn").disabled = currentPage === 0;
    document.getElementById("next-page-btn").disabled = (currentPage + 1) * PAGE_SIZE >= result.data.totalElements;
  }

  function renderRow(payment) {
    var tr = document.createElement("tr");

    var typeTd = document.createElement("td");
    typeTd.textContent = payment.type;
    tr.appendChild(typeTd);

    var sourceTd = document.createElement("td");
    sourceTd.textContent = payment.sourceAccount;
    tr.appendChild(sourceTd);

    var destTd = document.createElement("td");
    destTd.textContent = payment.destinationAccount;
    tr.appendChild(destTd);

    var amountTd = document.createElement("td");
    amountTd.textContent = formatAmount(payment.amount, payment.currency);
    tr.appendChild(amountTd);

    var statusTd = document.createElement("td");
    var statusBadge = document.createElement("span");
    statusBadge.className = "status-badge " + payment.status;
    statusBadge.textContent = payment.status;
    statusTd.appendChild(statusBadge);
    tr.appendChild(statusTd);

    var approvalTd = document.createElement("td");
    if (payment.approvalStatus) {
      var approvalBadge = document.createElement("span");
      approvalBadge.className = "status-badge " + payment.approvalStatus;
      approvalBadge.textContent = payment.approvalStatus;
      approvalTd.appendChild(approvalBadge);
    } else {
      approvalTd.textContent = "-";
    }
    tr.appendChild(approvalTd);

    var createdTd = document.createElement("td");
    createdTd.textContent = formatChangedAt(payment.createdAt);
    tr.appendChild(createdTd);

    var actionTd = document.createElement("td");
    var viewBtn = document.createElement("button");
    viewBtn.type = "button";
    viewBtn.className = "btn btn-sm btn-outline-primary";
    viewBtn.textContent = "View";
    viewBtn.addEventListener("click", function () {
      openDetail(payment);
    });
    actionTd.appendChild(viewBtn);
    tr.appendChild(actionTd);

    return tr;
  }

  // --- Detail offcanvas ---

  async function openDetail(payment) {
    var fieldsEl = document.getElementById("detail-fields");
    fieldsEl.innerHTML =
      "<strong>ID:</strong> " + payment.id + "<br>" +
      "<strong>Type:</strong> " + payment.type + "<br>" +
      "<strong>Source:</strong> " + payment.sourceAccount + "<br>" +
      "<strong>Destination:</strong> " + payment.destinationAccount + "<br>" +
      "<strong>Amount:</strong> " + formatAmount(payment.amount, payment.currency) + "<br>" +
      "<strong>Payment Method:</strong> " + (payment.paymentMethod || "-") + "<br>" +
      (payment.errorCode ? "<strong>Error:</strong> " + payment.errorCode + "<br>" : "") +
      (payment.rejectionReason ? "<strong>Rejection Reason:</strong> " + payment.rejectionReason + "<br>" : "");

    var actionsEl = document.getElementById("approval-actions");
    actionsEl.innerHTML = "";
    if (payment.type === "REFUND" && payment.approvalStatus === "PENDING_APPROVAL") {
      renderApprovalActions(payment, actionsEl);
    }

    var inspectorEl = document.getElementById("detail-inspector");
    inspectorEl.innerHTML = "";

    var historyResult = await AppMode.fetchJson(API_BASE + "/" + payment.id + "/history");
    renderLifecycleTimeline(
      document.getElementById("detail-timeline"),
      historyResult.ok ? historyResult.data : [],
      payment.type === "REFUND"
        ? { approvalStatus: payment.approvalStatus, approvedBy: payment.approvedBy, rejectionReason: payment.rejectionReason }
        : null
    );

    offcanvasInstance.show();
  }

  function renderApprovalActions(payment, container) {
    var wrapper = document.createElement("div");
    wrapper.className = "d-flex gap-2";

    var approveBtn = document.createElement("button");
    approveBtn.type = "button";
    approveBtn.className = "btn btn-success btn-sm";
    approveBtn.textContent = "Approve";
    approveBtn.addEventListener("click", async function () {
      var url = API_BASE + "/" + payment.id + "/refund/approve";
      var body = { approvedBy: "Business Ops" };
      var result = await AppMode.fetchJson(url, "POST", body);
      if (AppMode.getMode() === "debug") {
        AppMode.renderInspector(document.getElementById("detail-inspector"), "POST", url, body, result.data);
      }
      if (result.ok) {
        offcanvasInstance.hide();
        loadResults();
        loadInsights();
      }
    });

    var rejectBtn = document.createElement("button");
    rejectBtn.type = "button";
    rejectBtn.className = "btn btn-outline-danger btn-sm";
    rejectBtn.textContent = "Reject";
    rejectBtn.addEventListener("click", async function () {
      var reason = window.prompt("Rejection reason:");
      if (!reason) {
        return;
      }
      var url = API_BASE + "/" + payment.id + "/refund/reject";
      var body = { rejectedBy: "Business Ops", reason: reason };
      var result = await AppMode.fetchJson(url, "POST", body);
      if (AppMode.getMode() === "debug") {
        AppMode.renderInspector(document.getElementById("detail-inspector"), "POST", url, body, result.data);
      }
      if (result.ok) {
        offcanvasInstance.hide();
        loadResults();
        loadInsights();
      }
    });

    wrapper.appendChild(approveBtn);
    wrapper.appendChild(rejectBtn);
    container.appendChild(wrapper);
  }

  // --- Formatting helpers ---

  function formatAmount(amount, currency) {
    var n = Number(amount) || 0;
    return (currency || "INR") + " " + n.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  function formatChangedAt(isoString) {
    var date = new Date(isoString);
    return isNaN(date.getTime()) ? isoString : date.toLocaleString();
  }

  function extractErrorMessage(data) {
    if (!data) {
      return "Something went wrong. Please try again.";
    }
    return data.message || data.error || JSON.stringify(data);
  }
})();
