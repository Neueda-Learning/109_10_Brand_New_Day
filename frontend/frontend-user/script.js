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
 *   - KPI insight cards via GET /api/payments/insights (spec.md Section 10.10).
 *   - Demo/Debug mode + light/dark theme via frontend-shared/app-mode.js.
 */
(function () {
  var API_BASE = "http://localhost:8080/api/payments";
  var PAGE_SIZE = 10;
  var currentPage = 0;
  var loadedPayments = [];

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

      document.getElementById("new-payment-form").addEventListener("submit", onCreatePayment);
      document.getElementById("load-more-btn").addEventListener("click", function () {
        currentPage += 1;
        loadTransactions(currentPage, true);
      });

      // Search and filter event listeners - only apply filters when user interacts
      var searchInput = document.getElementById("search-uuid");
      var filterSelect = document.getElementById("filter-status");

      if (searchInput) {
        searchInput.addEventListener("input", function () {
          applyFiltersAndDisplay();
        });
      }

      if (filterSelect) {
        filterSelect.addEventListener("change", function () {
          applyFiltersAndDisplay();
        });
      }

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

    var result = await fetchJson(API_BASE, "POST", body);
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
  }

  // --- Insights (GET /api/payments/insights, spec.md Section 10.10) ---

  async function loadInsights() {
    var result = await fetchJson(API_BASE + "/insights");
    if (!result.ok) {
      return;
    }
    var insights = result.data;
    var countByType = insights.countByType || {};
    var amountByType = insights.amountByType || {};
    document.getElementById("insight-total-payments").textContent = countByType.PAYMENT || 0;
    document.getElementById("insight-total-amount").textContent = formatAmount(amountByType.PAYMENT || 0);
    document.getElementById("insight-total-refunds").textContent = countByType.REFUND || 0;
    document.getElementById("insight-success-rate").textContent = Math.round((insights.successRate || 0) * 100) + "%";
  }

    // --- Transaction list ---

    async function loadTransactions(page, append) {
      var errorEl = document.getElementById("transactions-error");
      errorEl.hidden = true;

      var result = await fetchJson(API_BASE + "?page=" + page + "&size=" + PAGE_SIZE);
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

      // Display all transactions first
      displayAllTransactions();

      var loadMoreBtn = document.getElementById("load-more-btn");
      loadMoreBtn.hidden = loadedPayments.length >= result.data.totalElements;
    }

    function displayAllTransactions() {
      var listEl = document.getElementById("transactions-list");
      listEl.innerHTML = "";
      loadedPayments.forEach(function (payment) {
        listEl.appendChild(renderTransactionItem(payment));
      });
    }

    function applyFiltersAndDisplay() {
      var searchInput = document.getElementById("search-uuid");
      var filterSelect = document.getElementById("filter-status");

      var searchValue = (searchInput && searchInput.value) ? searchInput.value.trim().toLowerCase() : "";
      var filterStatus = (filterSelect && filterSelect.value) ? filterSelect.value : "";

      // If no filters applied, show all transactions
      if (!searchValue && !filterStatus) {
        displayAllTransactions();
        return;
      }

      // Apply filters
      var filteredPayments = loadedPayments.filter(function (payment) {
        var matchesSearch = !searchValue || payment.id.toLowerCase().includes(searchValue);
        var matchesStatus = !filterStatus || payment.status === filterStatus;
        return matchesSearch && matchesStatus;
      });

      var listEl = document.getElementById("transactions-list");
      listEl.innerHTML = "";
      filteredPayments.forEach(function (payment) {
        listEl.appendChild(renderTransactionItem(payment));
      });
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

    var historyResult = await fetchJson(API_BASE + "/" + payment.id + "/history");
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
     } else if (payment.type === "PAYMENT" && payment.status === "REFUNDED") {
       renderReceiptActions(payment, refundSection);
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

      var result = await fetchJson(API_BASE + "/" + payment.id + "/refund", "POST", {
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
      "</head><body><h1>Payment Receipt</h1><table>" + rows + "</table></body></html>";
  }

  function buildReceiptTableMarkup(payment) {
    var rows = buildReceiptRows(payment);
    return '<div class="table-responsive"><table class="table table-sm table-bordered align-middle mb-0">' + rows + "</table></div>";
  }

  function buildReceiptRows(payment) {
    var now = new Date().toLocaleString();
    var fields = [
      ["Receipt Generated", now],
      ["Transaction ID", payment.id],
      ["Transaction Type", payment.type],
      ["Status", payment.status],
      ["Source Account", payment.sourceAccount],
      ["Destination Account", payment.destinationAccount],
      ["Amount", formatAmount(payment.amount, payment.currency)],
      ["Payment Method", payment.paymentMethod || "-"],
      ["Created At", formatChangedAt(payment.createdAt)]
    ];

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
