/*
 * Shared Demo/Debug mode toggle + localStorage persistence + request/response
 * logging helper (spec.md Section 14.3 - M4). Requires no backend changes; both
 * modes call the exact same endpoints, only the frontend orchestration differs.
 *
 * Usage:
 *   AppMode.getMode()                    -> "demo" | "debug"
 *   AppMode.initModeToggle(checkboxEl)    -> wires a <input type="checkbox"> to the mode
 *   AppMode.initThemeToggle(checkboxEl)   -> wires a <input type="checkbox"> to light/dark theme
 *   AppMode.logRequest(panelEl, info)     -> appends a request entry (debug mode only)
 *   AppMode.logResponse(panelEl, info)    -> appends a response entry (debug mode only)
 *   AppMode.autoAdvance(baseUrl, id, cb)  -> repeatedly calls POST {baseUrl}/{id}/process
 *                                            until a terminal status is reached (demo mode)
 */

var AppMode = (function () {
  var MODE_KEY = "mode";
  var THEME_KEY = "theme";
  var DEFAULT_MODE = "demo";
  var DEFAULT_THEME = "light";
  var STEP_DELAY_MS = 700;

  function getMode() {
    return localStorage.getItem(MODE_KEY) || DEFAULT_MODE;
  }

  function setMode(mode) {
    localStorage.setItem(MODE_KEY, mode);
  }

  function getTheme() {
    return localStorage.getItem(THEME_KEY) || DEFAULT_THEME;
  }

  function setTheme(theme) {
    localStorage.setItem(THEME_KEY, theme);
    document.documentElement.setAttribute("data-theme", theme);
  }

  function applyStoredTheme() {
    document.documentElement.setAttribute("data-theme", getTheme());
  }

  function initModeToggle(checkboxEl) {
    checkboxEl.checked = getMode() === "debug";
    checkboxEl.addEventListener("change", function () {
      setMode(checkboxEl.checked ? "debug" : "demo");
    });
  }

  function initThemeToggle(checkboxEl) {
    checkboxEl.checked = getTheme() === "dark";
    checkboxEl.addEventListener("change", function () {
      setTheme(checkboxEl.checked ? "dark" : "light");
    });
  }

  function logRequest(panelEl, info) {
    if (getMode() !== "debug" || !panelEl) {
      return;
    }
    appendLogEntry(panelEl, "request", info.method + " " + info.url, info.body);
  }

  function logResponse(panelEl, info) {
    if (getMode() !== "debug" || !panelEl) {
      return;
    }
    appendLogEntry(panelEl, "response", "HTTP " + info.status, info.body);
  }

  function appendLogEntry(panelEl, kind, summary, body) {
    var entry = document.createElement("details");
    entry.className = "debug-log-entry debug-log-entry-" + kind;
    var summaryEl = document.createElement("summary");
    summaryEl.textContent = "[" + kind + "] " + summary;
    entry.appendChild(summaryEl);
    var pre = document.createElement("pre");
    pre.textContent = typeof body === "string" ? body : JSON.stringify(body, null, 2);
    entry.appendChild(pre);
    panelEl.prepend(entry);
  }

  /**
   * Demo mode auto-advance: repeatedly POSTs to {baseUrl}/{id}/process with a short
   * client-side delay between steps, until the payment reaches a terminal status
   * (COMPLETED/FAILED) or the call fails (e.g. a refund still pending approval, per
   * Section 8.1 rule 6 - auto-advance never bypasses the approval gate). This is a
   * client-side convenience loop over the existing manual endpoint; it adds no
   * backend auto-advance/background job.
   *
   * @param {string} baseUrl - e.g. "http://localhost:8080/api/payments"
   * @param {string} id - payment id to advance
   * @param {function} onStep - called with (paymentResponse) after every successful step
   * @param {function} onError - called with (errorResponseBodyOrNull, httpStatus) if a step fails
   */
  function autoAdvance(baseUrl, id, onStep, onError) {
    var TERMINAL_STATUSES = ["COMPLETED", "FAILED"];

    function step(body) {
      fetch(baseUrl + "/" + id + "/process", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body || {})
      })
        .then(function (res) {
          return res.json().then(function (data) {
            return { ok: res.ok, status: res.status, data: data };
          });
        })
        .then(function (result) {
          if (!result.ok) {
            if (onError) {
              onError(result.data, result.status);
            }
            return;
          }

          onStep(result.data);

          if (TERMINAL_STATUSES.indexOf(result.data.status) === -1) {
            var nextBody = result.data.status === "SENT" ? { targetStatus: "COMPLETED" } : {};
            setTimeout(function () {
              step(nextBody);
            }, STEP_DELAY_MS);
          }
        })
        .catch(function (err) {
          if (onError) {
            onError({ message: err.message }, 0);
          }
        });
    }

    step({});
  }

  return {
    getMode: getMode,
    setMode: setMode,
    getTheme: getTheme,
    setTheme: setTheme,
    applyStoredTheme: applyStoredTheme,
    initModeToggle: initModeToggle,
    initThemeToggle: initThemeToggle,
    logRequest: logRequest,
    logResponse: logResponse,
    autoAdvance: autoAdvance
  };
})();

AppMode.applyStoredTheme();
