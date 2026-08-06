/*
 * frontend-user/script.js - unified single-page consumer "payment gateway"
 * app (v2.4 redesign: balances, live KPIs, Kishore-only history, stage
 * transparency, confirm-payment gate, exchange rates). Replaces the old
 * index.js + detail.js + history.js.
 *
 * Responsibilities:
 *   - Checkout: Confirm-Payment gate -> POST /api/payments (bank transfer or
 *     card, INR/USD/EUR - spec.md Section 10.1), idempotencyKey auto-generated
 *     client-side via crypto.randomUUID().
 *   - Payment-gateway processing overlay: animates the lifecycle simulation
 *     (CREATED -> VALIDATED -> SENT -> COMPLETED/FAILED) right after checkout,
 *     auto-advancing via frontend-shared/app-mode.js's autoAdvance() helper,
 *     showing a plain-English "what's happening" description per stage - no
 *     debug/manual-step UI on the customer side (prod-grade auto-advance only).
 *   - "My Accounts" balances via the universal GET /api/accounts?customerRef=.
 *   - Exchange rates via the universal GET /api/exchange-rates.
 *   - Kishore-only recent transactions: merges filtered GET /api/payments calls
 *     across his 2 accounts (source+destination), deduped/sorted client-side.
 *   - KPI cards computed client-side from Kishore's own transactions only (not
 *     the global /insights aggregate, which mixes every customer's data).
 *   - Light/dark theme via frontend-shared/app-mode.js (no mode/debug toggle here).
 */
(function () {
  var PAYMENTS_API = "http://localhost:8080/api/payments";
  var ACCOUNTS_API = "http://localhost:8080/api/accounts";
  var EXCHANGE_RATES_API = "http://localhost:8080/api/exchange-rates";

  // Kishore is the only signed-in identity in this demo (no auth - spec.md
  // Section 4). These endpoints are universal/generic server-side; the
  // frontend just happens to call them with Kishore's identity.
  var CUSTOMER_REF = "CUS-KISHORE-001";
  var MY_ACCOUNTS = ["ACC-KISHORE-SAV-001", "ACC-KISHORE-CUR-001"];
  var VISIBLE_PAGE_SIZE = 10;

  var allMyPayments = [];   // full merged/deduped/sorted list
  var visibleCount = VISIBLE_PAGE_SIZE;

  // Plain-English "what's happening" copy per lifecycle stage (shown in the
  // processing overlay so the customer understands what's being checked/done,
  // not just a bare status label).
  var STAGE_COPY = {
    CREATED: "Payment request received and recorded.",
    VALIDATED: "Validating source/destination accounts, currency and payment method.",
    SENT: "Routing your payment through BND's settlement network.",
    COMPLETED: "Settlement confirmed - funds have been transferred.",
    FAILED: "Payment could not be completed."
  };

  // app-mode.js doesn't expose a fetchJson helper - wrap fetch() locally instead.
  async function fetchJson(url, method, body) {
    var options = { method: method || "GET" };
    if (body !== undefined) {
      options.headers = { "Content-Type": "application/json" };
      options.body = JSON.stringify(body);
    }
    var response = await fetch(url, options);
    var data = null;
    try {
      data = await response.json();
    } catch (err) {
      data = null;
    }
    return { ok: response.ok, status: response.status, data: data };
  }

  document.addEventListener("DOMContentLoaded", function () {
    AppMode.initThemeToggle(document.getElementById("theme-toggle"));

    document.getElementById("new-payment-form").addEventListener("submit", onSubmitCheckoutForm);
    document.getElementById("confirm-payment-btn").addEventListener("click", onConfirmPayment);
    document.getElementById("load-more-btn").addEventListener("click", function () {
      visibleCount += VISIBLE_PAGE_SIZE;
      displayVisibleTransactions();
    });

    // Toggle the card fields (card select + CVV) based on the chosen payment method.
    var methodRadios = document.querySelectorAll('input[name="paymentMethod"]');
    var cardFields = document.getElementById("card-fields");
    methodRadios.forEach(function (radio) {
      radio.addEventListener("change", function () {
        var isCard = document.getElementById("method-card").checked;
        cardFields.hidden = !isCard;
        document.getElementById("cardCvv").required = isCard;
      });
    });

    // Search and filter event listeners - only apply filters when user interacts
    var searchInput = document.getElementById("search-uuid");
    var filterSelect = document.getElementById("filter-status");

    if (searchInput) {
      searchInput.addEventListener("input", function () {
        displayVisibleTransactions();
      });
    }
    if (filterSelect) {
      filterSelect.addEventListener("change", function () {
        displayVisibleTransactions();
      });
    }

    loadAccounts();
    loadExchangeRates();
    loadMyTransactions();
  });

  // --- Confirm-payment gate ---

  function onSubmitCheckoutForm(event) {
    event.preventDefault();
    var errorEl = document.getElementById("form-error");
    errorEl.hidden = true;

    var isCard = document.getElementById("method-card").checked;
    var sourceAccount = document.getElementById("sourceAccount").value;
    var destinationAccount = document.getElementById("destinationAccount").value.trim();
    var amount = document.getElementById("amount").value;
    var currency = document.getElementById("currency").value;

    if (isCard && document.getElementById("cardCvv").value.trim().length < 3) {
      errorEl.textContent = "Please enter your card's CVV.";
      errorEl.hidden = false;
      return;
    }

    var summaryEl = document.getElementById("confirm-payment-body");
    summaryEl.innerHTML =
      '<dl class="row mb-0">' +
      '<dt class="col-5">From</dt><dd class="col-7">' + escapeHtml(sourceAccount) + '</dd>' +
      '<dt class="col-5">To</dt><dd class="col-7">' + escapeHtml(destinationAccount) + '</dd>' +
      '<dt class="col-5">Amount</dt><dd class="col-7">' + escapeHtml(currency) + ' ' + escapeHtml(amount) + '</dd>' +
      '<dt class="col-5">Method</dt><dd class="col-7">' + (isCard ? "Card (VISA \u2022\u2022\u2022\u2022 4242)" : "Bank Transfer") + '</dd>' +
      '</dl>';

    var modalEl = document.getElementById("confirm-payment-modal");
    bootstrap.Modal.getOrCreateInstance(modalEl).show();
  }

  async function onConfirmPayment() {
    var errorEl = document.getElementById("form-error");
    errorEl.hidden = true;

    var isCard = document.getElementById("method-card").checked;
    var body = {
      sourceAccount: document.getElementById("sourceAccount").value.trim(),
      destinationAccount: document.getElementById("destinationAccount").value.trim(),
      amount: parseFloat(document.getElementById("amount").value),
      currency: document.getElementById("currency").value.trim().toUpperCase(),
      paymentMethod: isCard ? "CARD" : "BANK_TRANSFER",
      idempotencyKey: crypto.randomUUID()
    };
    if (isCard) {
      body.cardId = document.getElementById("cardSelect").value;
      body.cvv = document.getElementById("cardCvv").value.trim();
    }

    bootstrap.Modal.getInstance(document.getElementById("confirm-payment-modal")).hide();

    var result = await fetchJson(PAYMENTS_API, "POST", body);
    if (!result.ok) {
      errorEl.textContent = extractErrorMessage(result.data);
      errorEl.hidden = false;
      return;
    }

    document.getElementById("new-payment-form").reset();
    document.getElementById("currency").value = "INR";
    document.getElementById("card-fields").hidden = true;

    startProcessingOverlay(result.data);
  }

  // --- Payment-gateway processing overlay (lifecycle simulation, with per-stage copy) ---

  function startProcessingOverlay(payment) {
    var modalEl = document.getElementById("processing-modal");
    if (!modalEl || typeof bootstrap === "undefined") {
      refreshAll();
      return;
    }

    var timelineEl = document.getElementById("processing-timeline");
    var subtitleEl = document.getElementById("processing-modal-subtitle");
    var titleEl = document.getElementById("processing-modal-title");
    var spinnerEl = document.getElementById("processing-spinner");
    var doneBtn = document.getElementById("processing-done-btn");

    timelineEl.innerHTML = "";
    subtitleEl.textContent = STAGE_COPY.CREATED;
    titleEl.textContent = "Processing your payment\u2026";
    spinnerEl.classList.remove("processing-spinner-done", "processing-spinner-failed");
    doneBtn.hidden = true;

    var history = [{ fromStatus: null, toStatus: payment.status, changedAt: payment.createdAt, triggeredBy: "SYSTEM", note: STAGE_COPY[payment.status] }];
    renderStageTimeline(timelineEl, history);

    var modal = bootstrap.Modal.getOrCreateInstance(modalEl);
    modal.show();

    function onStep(updated) {
      history.push({
        fromStatus: history[history.length - 1].toStatus,
        toStatus: updated.status,
        changedAt: updated.updatedAt,
        triggeredBy: "SYSTEM",
        note: STAGE_COPY[updated.status]
      });
      renderStageTimeline(timelineEl, history);
      subtitleEl.textContent = STAGE_COPY[updated.status] || "";

      if (updated.status === "COMPLETED") {
        spinnerEl.classList.add("processing-spinner-done");
        titleEl.textContent = "Payment successful";
        doneBtn.hidden = false;
      } else if (updated.status === "FAILED") {
        spinnerEl.classList.add("processing-spinner-failed");
        titleEl.textContent = "Payment failed";
        subtitleEl.textContent = updated.errorCode ? "Reason: " + updated.errorCode : STAGE_COPY.FAILED;
        doneBtn.hidden = false;
      }
    }

    function onError() {
      spinnerEl.classList.add("processing-spinner-failed");
      titleEl.textContent = "Payment could not be processed";
      subtitleEl.textContent = "Please check Recent Transactions for the latest status.";
      doneBtn.hidden = false;
    }

    modalEl.addEventListener("hidden.bs.modal", function refresh() {
      modalEl.removeEventListener("hidden.bs.modal", refresh);
      refreshAll();
    });

    AppMode.autoAdvance(PAYMENTS_API, payment.id, onStep, onError);
  }

  // Like renderLifecycleTimeline, but also shows the plain-English stage
  // description as a sub-line under each status badge.
  function renderStageTimeline(containerEl, history) {
    renderLifecycleTimeline(containerEl, history);
    var metas = containerEl.querySelectorAll(".timeline-step-content");
    metas.forEach(function (contentEl, idx) {
      if (history[idx] && history[idx].note && !contentEl.querySelector(".timeline-stage-copy")) {
        var copy = document.createElement("div");
        copy.className = "timeline-stage-copy small text-muted";
        copy.textContent = history[idx].note;
        contentEl.appendChild(copy);
      }
    });
  }

  function refreshAll() {
    loadAccounts();
    loadMyTransactions();
  }

  // --- My Accounts (universal GET /api/accounts?customerRef=) ---

  async function loadAccounts() {
    var result = await fetchJson(ACCOUNTS_API + "?customerRef=" + encodeURIComponent(CUSTOMER_REF));
    var containerEl = document.getElementById("account-cards");
    if (!result.ok || !Array.isArray(result.data)) {
      return;
    }
    containerEl.innerHTML = "";
    result.data.forEach(function (account) {
      var col = document.createElement("div");
      col.className = "col-6 col-md-3";
      col.innerHTML =
        '<div class="card account-card">' +
        '<div class="account-label">' + escapeHtml(account.displayName) + '</div>' +
        '<div class="account-number small text-muted">' + escapeHtml(account.accountNumber) + '</div>' +
        '<div class="account-balance">' + formatAmount(account.balance, account.currency) + '</div>' +
        '</div>';
      containerEl.appendChild(col);
    });
  }

  // --- Exchange rates (universal GET /api/exchange-rates) ---

  async function loadExchangeRates() {
    var result = await fetchJson(EXCHANGE_RATES_API);
    var listEl = document.getElementById("exchange-rates-list");
    if (!result.ok || !Array.isArray(result.data)) {
      return;
    }
    listEl.innerHTML = "";
    result.data.forEach(function (rate) {
      var chip = document.createElement("span");
      chip.className = "exchange-rate-chip";
      chip.textContent = "1 " + rate.currency + " = \u20B9" + Number(rate.rateToInr).toLocaleString(undefined, { maximumFractionDigits: 2 });
      listEl.appendChild(chip);
    });
  }

  // --- Kishore-only transaction history (merged across his 2 accounts) ---

  async function loadMyTransactions() {
    var errorEl = document.getElementById("transactions-error");
    errorEl.hidden = true;

    var requests = [];
    MY_ACCOUNTS.forEach(function (acc) {
      requests.push(fetchJson(PAYMENTS_API + "?sourceAccount=" + encodeURIComponent(acc) + "&size=100"));
      requests.push(fetchJson(PAYMENTS_API + "?destinationAccount=" + encodeURIComponent(acc) + "&size=100"));
    });

    var results = await Promise.all(requests);
    var failed = results.find(function (r) { return !r.ok; });
    if (failed) {
      errorEl.textContent = extractErrorMessage(failed.data);
      errorEl.hidden = false;
      return;
    }

    var byId = new Map();
    results.forEach(function (r) {
      (r.data.content || []).forEach(function (p) {
        byId.set(p.id, p);
      });
    });

    allMyPayments = Array.from(byId.values()).sort(function (a, b) {
      return new Date(b.createdAt) - new Date(a.createdAt);
    });

    visibleCount = VISIBLE_PAGE_SIZE;
    displayVisibleTransactions();
    renderKpis();
  }

  function getFilteredPayments() {
    var searchInput = document.getElementById("search-uuid");
    var filterSelect = document.getElementById("filter-status");
    var searchValue = (searchInput && searchInput.value) ? searchInput.value.trim().toLowerCase() : "";
    var filterStatus = (filterSelect && filterSelect.value) ? filterSelect.value : "";

    return allMyPayments.filter(function (payment) {
      var matchesSearch = !searchValue || payment.id.toLowerCase().includes(searchValue);
      var matchesStatus = !filterStatus || payment.status === filterStatus;
      return matchesSearch && matchesStatus;
    });
  }

  function displayVisibleTransactions() {
    var filtered = getFilteredPayments();
    var listEl = document.getElementById("transactions-list");
    listEl.innerHTML = "";
    filtered.slice(0, visibleCount).forEach(function (payment) {
      listEl.appendChild(renderTransactionItem(payment));
    });

    var loadMoreBtn = document.getElementById("load-more-btn");
    loadMoreBtn.hidden = visibleCount >= filtered.length;
  }

  // --- KPIs (computed client-side from Kishore's own transactions only) ---

  function renderKpis() {
    var payments = allMyPayments.filter(function (p) { return p.type === "PAYMENT"; });
    var refunds = allMyPayments.filter(function (p) { return p.type === "REFUND"; });
    var completed = payments.filter(function (p) { return p.status === "COMPLETED"; });
    var failed = payments.filter(function (p) { return p.status === "FAILED"; });

    var totalSentInr = completed.reduce(function (sum, p) {
      return sum + (Number(p.settlementAmountInr) || 0);
    }, 0);

    var successRate = (completed.length + failed.length) > 0
      ? Math.round((completed.length / (completed.length + failed.length)) * 100)
      : 0;

    document.getElementById("insight-total-payments").textContent = payments.length;
    document.getElementById("insight-total-amount").textContent = formatAmount(totalSentInr, "INR");
    document.getElementById("insight-total-refunds").textContent = refunds.length;
    document.getElementById("insight-success-rate").textContent = successRate + "%";
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
    var isForeignCurrency = payment.currency && payment.currency !== "INR";
    fieldsEl.innerHTML =
      "Payment ID: " + payment.id + "<br>" +
      "Amount: " + formatAmount(payment.amount, payment.currency) + "<br>" +
      (isForeignCurrency
        ? "Settled Amount: " + formatAmount(payment.settlementAmountInr, payment.settlementCurrency || "INR") +
          " (rate " + payment.fxRateToInr + ")<br>"
        : "") +
      "Payment Method: " + (payment.paymentMethod || "-") +
      (payment.paymentMethod === "CARD" && payment.cardLast4
        ? " (" + (payment.cardBrand || "") + " \u2022\u2022\u2022\u2022 " + payment.cardLast4 + ")"
        : "") + "<br>" +
      (payment.errorCode ? "Error: " + payment.errorCode + "<br>" : "");

    var historyResult = await fetchJson(PAYMENTS_API + "/" + payment.id + "/history");
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
      renderRefundForm(payment, refundSection);
    } else if (payment.type === "REFUND" && payment.status === "COMPLETED") {
      renderReceiptActions(payment, refundSection);
    }
  }

  function renderRefundForm(payment, container) {
    var form = document.createElement("form");
    form.className = "refund-form";
    form.innerHTML =
      '<div class="row g-2 align-items-end">' +
      '<div class="col-auto"><label class="form-label small mb-0">Refund Amount</label>' +
      '<input type="number" class="form-control form-control-sm refund-amount" step="0.01" min="0.01" max="' + payment.amount + '" value="' + payment.amount + '" required></div>' +
      '<div class="col-auto"><button type="submit" class="btn btn-outline-danger btn-sm">Request Refund</button></div>' +
      '<div class="col-auto receipt-action-slot"></div>' +
      '</div>' +
      '<div class="error-message refund-error mt-2" hidden></div>';

    var receiptSlot = form.querySelector(".receipt-action-slot");
    receiptSlot.appendChild(createReceiptActionGroup(payment));

    form.addEventListener("submit", async function (event) {
      event.preventDefault();
      var errorEl = form.querySelector(".refund-error");
      errorEl.hidden = true;
      var amount = parseFloat(form.querySelector(".refund-amount").value);

      var result = await fetchJson(PAYMENTS_API + "/" + payment.id + "/refund", "POST", {
        amount: amount,
        idempotencyKey: crypto.randomUUID()
      });
      if (!result.ok) {
        errorEl.textContent = extractErrorMessage(result.data);
        errorEl.hidden = false;
        return;
      }
      container.innerHTML = '<div class="text-success small">Refund requested (' + result.data.approvalStatus + '). Awaiting business approval.</div>';
      loadMyTransactions();
    });

    container.appendChild(form);
  }

  function renderReceiptActions(payment, container) {
    var actionWrap = document.createElement("div");
    actionWrap.className = "receipt-action-wrap";
    actionWrap.appendChild(createReceiptActionGroup(payment));
    container.appendChild(actionWrap);
  }

  function createReceiptActionGroup(payment) {
    var group = document.createElement("div");
    group.className = "btn-group btn-group-sm receipt-action-group";

    var viewBtn = document.createElement("button");
    viewBtn.type = "button";
    viewBtn.className = "btn btn-outline-primary";
    viewBtn.innerHTML = '<i class="bi bi-receipt"></i> View Receipt';
    viewBtn.addEventListener("click", function () {
      viewReceipt(payment);
    });

    var downloadBtn = document.createElement("button");
    downloadBtn.type = "button";
    downloadBtn.className = "btn btn-outline-primary";
    downloadBtn.innerHTML = '<i class="bi bi-download"></i> Download';
    downloadBtn.addEventListener("click", function () {
      downloadReceipt(payment);
    });

    group.appendChild(viewBtn);
    group.appendChild(downloadBtn);
    return group;
  }

  function viewReceipt(payment) {
    var modalEl = document.getElementById("receipt-modal");
    var modalBody = document.getElementById("receipt-modal-body");
    var modalTitle = document.getElementById("receipt-modal-title");
    if (!modalEl || !modalBody || !modalTitle || typeof bootstrap === "undefined") {
      return;
    }

    modalTitle.textContent = "Payment Receipt - " + payment.id;
    modalBody.innerHTML = buildReceiptTableMarkup(payment);

    var modal = bootstrap.Modal.getOrCreateInstance(modalEl);
    modal.show();
  }

  function downloadReceipt(payment) {
    var receiptHtml = buildReceiptHtml(payment);
    var blob = new Blob([receiptHtml], { type: "text/html" });
    var url = URL.createObjectURL(blob);
    var link = document.createElement("a");
    link.href = url;
    link.download = "payment-receipt-" + payment.id + ".html";
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  }

  function buildReceiptHtml(payment) {
    var rows = buildReceiptRows(payment);

    return "<!DOCTYPE html>" +
      "<html><head><meta charset=\"UTF-8\"><title>Payment Receipt</title>" +
      "<style>body{font-family:Arial,sans-serif;margin:24px;color:#222}h1{margin-bottom:16px}table{border-collapse:collapse;width:100%;max-width:760px}th,td{border:1px solid #ddd;padding:10px;text-align:left}th{width:220px;background:#f7f7f7}</style>" +
      "</head><body><h1>BND Bank Payment Receipt</h1><table>" + rows + "</table></body></html>";
  }

  function buildReceiptTableMarkup(payment) {
    var rows = buildReceiptRows(payment);
    return '<div class="table-responsive"><table class="table table-sm table-bordered align-middle mb-0">' + rows + "</table></div>";
  }

  function buildReceiptRows(payment) {
    var now = new Date().toLocaleString();
    var isForeignCurrency = payment.currency && payment.currency !== "INR";
    var fields = [
      ["Receipt Generated", now],
      ["Transaction ID", payment.id],
      ["Transaction Type", payment.type],
      ["Status", payment.status],
      ["Source Account", payment.sourceAccount],
      ["Destination Account", payment.destinationAccount],
      ["Amount", formatAmount(payment.amount, payment.currency)]
    ];

    if (isForeignCurrency) {
      fields.push(["Settlement (INR)", formatAmount(payment.settlementAmountInr, payment.settlementCurrency || "INR")]);
      fields.push(["FX Rate to INR", String(payment.fxRateToInr)]);
    }

    fields.push(["Payment Method",
      payment.paymentMethod === "CARD" && payment.cardLast4
        ? payment.paymentMethod + " (" + (payment.cardBrand || "") + " \u2022\u2022\u2022\u2022 " + payment.cardLast4 + ")"
        : (payment.paymentMethod || "-")
    ]);
    fields.push(["Created At", formatChangedAt(payment.createdAt)]);

    return fields.map(function (field) {
      return "<tr><th>" + escapeHtml(field[0]) + "</th><td>" + escapeHtml(field[1]) + "</td></tr>";
    }).join("");
  }

  function escapeHtml(value) {
    return String(value === undefined || value === null ? "" : value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/\"/g, "&quot;")
      .replace(/'/g, "&#39;");
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

