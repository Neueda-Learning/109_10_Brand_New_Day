/*
 * frontend-user/script.js - unified single-page consumer app (v2.2 redesign,
 * spec.md Section 14.1). Replaces the old index.js + detail.js + history.js.
 *
 * Responsibilities:
 *   - Create a new PAYMENT via POST /api/payments (idempotencyKey auto-generated
 *     client-side via crypto.randomUUID(), no manual input field - spec 14.1).
 *   - List recent transactions via GET /api/payments (paginated).
 *   - Expand a transaction inline to show full detail + lifecycle timeline
 *     (frontend-shared/lifecycle-timeline.js) + refund action.
 *   - Compute KPI insight cards client-side (computeInsights()) - isolated,
 *     swappable helper pending the real GET /api/payments/insights endpoint
 *     (NOT_IMPLEMENTED, deferred to M4/Karuna scope).
 *   - Demo/Debug mode + light/dark theme via frontend-shared/app-mode.js.
 */
(function () {
  var API_BASE = "http://localhost:8080/api/payments";
  var PAGE_SIZE = 10;
  var currentPage = 0;
  var loadedPayments = [];

  document.addEventListener("DOMContentLoaded", function () {
    AppMode.initThemeToggle(document.getElementById("theme-toggle"));
    AppMode.initModeToggle(document.getElementById("mode-toggle"));

    document.getElementById("new-payment-form").addEventListener("submit", onCreatePayment);
    document.getElementById("load-more-btn").addEventListener("click", function () {
      currentPage += 1;
      loadTransactions(currentPage, true);
    });

    loadInsights();
    loadTransactions(0, false);
  });

  // --- Create payment ---

  async function onCreatePayment(event) {
    event.preventDefault();
    var errorEl = document.getElementById("form-error");
    errorEl.hidden = true;

    var body = {
      sourceAccount: document.getElementById("sourceAccount").value.trim(),
      destinationAccount: document.getElementById("destinationAccount").value.trim(),
      amount: parseFloat(document.getElementById("amount").value),
      currency: document.getElementById("currency").value.trim().toUpperCase(),
      idempotencyKey: crypto.randomUUID()
    };

    var result = await AppMode.fetchJson(API_BASE, "POST", body);
    if (!result.ok) {
      errorEl.textContent = extractErrorMessage(result.data);
      errorEl.hidden = false;
      return;
    }

    document.getElementById("new-payment-form").reset();
    document.getElementById("currency").value = "INR";
    currentPage = 0;
    loadTransactions(0, false);
    loadInsights();

    if (AppMode.getMode() === "demo") {
      AppMode.autoAdvance(API_BASE, result.data.id, {
        onStep: function () {
          loadTransactions(0, false);
        },
        onStop: function () {
          loadTransactions(0, false);
          loadInsights();
        }
      });
    }
  }

  // --- Insights (client-side computed, swappable for real /insights endpoint) ---

  async function loadInsights() {
    var result = await AppMode.fetchJson(API_BASE + "?page=0&size=100");
    if (!result.ok) {
      return;
    }
    var insights = computeInsights(result.data.content);
    document.getElementById("insight-total-payments").textContent = insights.totalPayments;
    document.getElementById("insight-total-amount").textContent = formatAmount(insights.totalAmount);
    document.getElementById("insight-total-refunds").textContent = insights.totalRefunds;
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
    var totalRefunds = payments.filter(function (p) { return p.type === "REFUND"; }).length;
    var totalAmount = payments
      .filter(function (p) { return p.type === "PAYMENT"; })
      .reduce(function (sum, p) { return sum + Number(p.amount); }, 0);
    var terminalCount = payments.filter(function (p) { return p.status === "COMPLETED" || p.status === "FAILED"; }).length;
    var completedCount = payments.filter(function (p) { return p.status === "COMPLETED"; }).length;
    var successRate = terminalCount === 0 ? 0 : Math.round((completedCount / terminalCount) * 100);
    return {
      totalPayments: totalPayments,
      totalRefunds: totalRefunds,
      totalAmount: totalAmount,
      successRate: successRate
    };
  }

  // --- Transaction list ---

  async function loadTransactions(page, append) {
    var errorEl = document.getElementById("transactions-error");
    errorEl.hidden = true;

    var result = await AppMode.fetchJson(API_BASE + "?page=" + page + "&size=" + PAGE_SIZE);
    if (!result.ok) {
      errorEl.textContent = extractErrorMessage(result.data);
      errorEl.hidden = false;
      return;
    }

    if (append) {
      loadedPayments = loadedPayments.concat(result.data.content);
    } else {
      loadedPayments = result.data.content;
    }

    var listEl = document.getElementById("transactions-list");
    listEl.innerHTML = "";
    loadedPayments.forEach(function (payment) {
      listEl.appendChild(renderTransactionItem(payment));
    });

    var loadMoreBtn = document.getElementById("load-more-btn");
    loadMoreBtn.hidden = loadedPayments.length >= result.data.totalElements;
  }

  function renderTransactionItem(payment) {
    var template = document.getElementById("transaction-item-template");
    var node = template.content.cloneNode(true);
    var itemEl = node.querySelector(".transaction-item");

    var icon = node.querySelector(".transaction-icon");
    icon.classList.add(payment.type === "REFUND" ? "bi-arrow-counterclockwise" : "bi-arrow-up-right");

    node.querySelector(".transaction-title").textContent =
      (payment.type === "REFUND" ? "Refund" : "Payment") + " to " + payment.destinationAccount;
    node.querySelector(".transaction-meta").textContent =
      formatChangedAt(payment.createdAt) + " \u00B7 " + payment.sourceAccount + " \u2192 " + payment.destinationAccount;

    node.querySelector(".transaction-amount").textContent = formatAmount(payment.amount, payment.currency);

    var statusBadge = node.querySelector(".transaction-status");
    statusBadge.classList.add(payment.status);
    statusBadge.textContent = payment.status;

    var summaryEl = node.querySelector(".transaction-summary");
    var detailEl = node.querySelector(".transaction-detail");
    var loaded = false;

    summaryEl.addEventListener("click", function () {
      var isShown = detailEl.classList.contains("show");
      if (!isShown && !loaded) {
        loaded = true;
        populateDetail(payment, detailEl);
      }
      detailEl.classList.toggle("show");
    });

    return itemEl;
  }

  async function populateDetail(payment, detailEl) {
    var fieldsEl = detailEl.querySelector(".detail-fields");
    fieldsEl.innerHTML =
      "Payment ID: " + payment.id + "<br>" +
      "Amount: " + formatAmount(payment.amount, payment.currency) + "<br>" +
      "Payment Method: " + (payment.paymentMethod || "-") + "<br>" +
      (payment.errorCode ? "Error: " + payment.errorCode + "<br>" : "");

    var historyResult = await AppMode.fetchJson(API_BASE + "/" + payment.id + "/history");
    var timelineContainer = detailEl.querySelector(".timeline-container");
    renderLifecycleTimeline(
      timelineContainer,
      historyResult.ok ? historyResult.data : [],
      payment.type === "REFUND"
        ? { approvalStatus: payment.approvalStatus, approvedBy: payment.approvedBy, rejectionReason: payment.rejectionReason }
        : null
    );

    var refundSection = detailEl.querySelector(".refund-section");
    refundSection.innerHTML = "";
    if (payment.type === "PAYMENT" && payment.status === "COMPLETED") {
      renderRefundForm(payment, refundSection, detailEl);
    }

    if (AppMode.getMode() === "debug") {
      renderDebugControls(payment, detailEl);
    }
  }

  function renderRefundForm(payment, container, detailEl) {
    var form = document.createElement("form");
    form.className = "refund-form";
    form.innerHTML =
      '<div class="row g-2 align-items-end">' +
      '<div class="col-auto"><label class="form-label small mb-0">Refund Amount</label>' +
      '<input type="number" class="form-control form-control-sm refund-amount" step="0.01" min="0.01" max="' + payment.amount + '" value="' + payment.amount + '" required></div>' +
      '<div class="col-auto"><button type="submit" class="btn btn-outline-danger btn-sm">Request Refund</button></div>' +
      '</div>' +
      '<div class="error-message refund-error mt-2" hidden></div>';

    form.addEventListener("submit", async function (event) {
      event.preventDefault();
      var errorEl = form.querySelector(".refund-error");
      errorEl.hidden = true;
      var amount = parseFloat(form.querySelector(".refund-amount").value);

      var result = await AppMode.fetchJson(API_BASE + "/" + payment.id + "/refund", "POST", {
        amount: amount,
        idempotencyKey: crypto.randomUUID()
      });
      if (!result.ok) {
        errorEl.textContent = extractErrorMessage(result.data);
        errorEl.hidden = false;
        return;
      }
      container.innerHTML = '<div class="text-success small">Refund requested (' + result.data.approvalStatus + '). Awaiting business approval.</div>';
      loadTransactions(0, false);

      if (AppMode.getMode() === "demo") {
        AppMode.autoAdvance(API_BASE, result.data.id, {
          onStep: function () {
            loadTransactions(0, false);
          },
          onStop: function () {
            loadTransactions(0, false);
            loadInsights();
          }
        });
      }
    });

    container.appendChild(form);
  }

  function renderDebugControls(payment, detailEl) {
    var inspectorContainer = detailEl.querySelector(".inspector-container");
    var steps = AppMode.nextManualSteps(payment.status);
    if (steps.length === 0) {
      return;
    }
    var controls = document.createElement("div");
    controls.className = "debug-controls d-flex gap-2 mb-2";
    steps.forEach(function (step) {
      var btn = document.createElement("button");
      btn.type = "button";
      btn.className = "btn btn-sm btn-outline-secondary";
      btn.textContent = step.label;
      btn.addEventListener("click", async function () {
        var url = API_BASE + "/" + payment.id + "/process";
        var result = await AppMode.fetchJson(url, "POST", step.body);
        AppMode.renderInspector(inspectorContainer, "POST", url, step.body, result.data);
        loadTransactions(0, false);
      });
      controls.appendChild(btn);
    });
    inspectorContainer.parentNode.insertBefore(controls, inspectorContainer);
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
