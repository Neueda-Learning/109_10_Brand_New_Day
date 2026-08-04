// Page-specific logic for dashboard.html (M4 - filterable/searchable payments list).
// Wires the filter form to GET /api/payments (spec.md Section 10.3) and renders
// paginated results.

var API_BASE_URL = "http://localhost:8080";
var PAGE_SIZE = 20;

var currentPage = 0;
var currentFilters = {};

var filterForm = document.getElementById("filter-form");
var resultsBody = document.getElementById("results-body");
var paginationInfo = document.getElementById("pagination-info");
var errorMessage = document.getElementById("error-message");
var prevPageBtn = document.getElementById("prev-page-btn");
var nextPageBtn = document.getElementById("next-page-btn");

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

  fetch(API_BASE_URL + "/api/payments?" + params.toString())
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
    row.appendChild(makeCell(new Date(payment.createdAt).toLocaleString()));

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

loadPayments();
