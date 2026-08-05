/*
 * Shared Debug/Demo mode toggle + theme toggle + lifecycle auto-advance helper
 * (spec.md Section 14.3, added 2026-08-05, v2.2). Consumed by both
 * frontend-user/script.js and frontend-business/script.js.
 *
 * Requires NO backend changes - Demo mode is purely a client-side convenience
 * loop over the existing manual POST /api/payments/{id}/process endpoint;
 * Debug mode just replaces the loop with manual step buttons + an inspector
 * panel showing the raw request/response JSON. Both modes call the exact same
 * endpoints (spec.md Section 10).
 *
 * Public API:
 *   AppMode.getMode() / AppMode.setMode(mode)               -> "demo" | "debug"
 *   AppMode.getTheme() / AppMode.setTheme(theme)             -> "light" | "dark"
 *   AppMode.initModeToggle(toggleEl, onChange)
 *   AppMode.initThemeToggle(toggleEl)
 *   AppMode.autoAdvance(apiBase, paymentId, callbacks)
 *   AppMode.renderInspector(containerEl, entries)
 */
(function (global) {
  var MODE_KEY = "mode";
  var THEME_KEY = "theme";
  var DEFAULT_MODE = "demo";
  var DEFAULT_THEME = "light";
  var STEP_DELAY_MS_MIN = 600;
  var STEP_DELAY_MS_MAX = 900;

  function getMode() {
    return localStorage.getItem(MODE_KEY) || DEFAULT_MODE;
  }

  function setMode(mode) {
    localStorage.setItem(MODE_KEY, mode === "debug" ? "debug" : "demo");
  }

  function getTheme() {
    return localStorage.getItem(THEME_KEY) || DEFAULT_THEME;
  }

  function setTheme(theme) {
    var value = theme === "dark" ? "dark" : "light";
    localStorage.setItem(THEME_KEY, value);
    applyTheme(value);
  }

  function applyTheme(theme) {
    if (theme === "dark") {
      document.documentElement.setAttribute("data-theme", "dark");
    } else {
      document.documentElement.removeAttribute("data-theme");
    }
  }

  /**
   * Wires a checkbox-style toggle element (e.g. Bootstrap form-switch input)
   * to the Demo/Debug mode. Calls onChange(mode) whenever it flips, and
   * initializes the element's checked state from the persisted mode
   * (checked = debug, unchecked = demo).
   */
  function initModeToggle(toggleEl, onChange) {
    if (!toggleEl) {
      return;
    }
    toggleEl.checked = getMode() === "debug";
    toggleEl.addEventListener("change", function () {
      var mode = toggleEl.checked ? "debug" : "demo";
      setMode(mode);
      if (typeof onChange === "function") {
        onChange(mode);
      }
    });
  }

  /**
   * Wires a checkbox-style toggle element to light/dark theme, applying the
   * persisted theme immediately (checked = dark, unchecked = light).
   */
  function initThemeToggle(toggleEl) {
    applyTheme(getTheme());
    if (!toggleEl) {
      return;
    }
    toggleEl.checked = getTheme() === "dark";
    toggleEl.addEventListener("change", function () {
      setTheme(toggleEl.checked ? "dark" : "light");
    });
  }

  function delay(ms) {
    return new Promise(function (resolve) {
      setTimeout(resolve, ms);
    });
  }

  function randomStepDelay() {
    return STEP_DELAY_MS_MIN + Math.random() * (STEP_DELAY_MS_MAX - STEP_DELAY_MS_MIN);
  }

  /**
   * Demo-mode auto-advance loop: repeatedly calls POST /{id}/process until the
   * payment reaches a terminal status (COMPLETED/FAILED) or the call fails
   * (e.g. 409 REFUND_NOT_APPROVED - the refund approval gate). This is a pure
   * client-side loop over the existing manual endpoint; no backend change.
   *
   * callbacks: {
   *   onStep(payment, request, response) - called after each successful step
   *   onStop(reason, payment) - called when the loop stops (terminal | gated | error)
   * }
   */
  async function autoAdvance(apiBase, paymentId, callbacks) {
    callbacks = callbacks || {};
    var terminal = { COMPLETED: true, FAILED: true };
    var guard = 0;
    while (guard < 10) {
      guard += 1;
      await delay(randomStepDelay());

      // At SENT, simulate a realistic outcome: ~85% COMPLETED, ~15% FAILED.
      var body = {};
      var current = await fetchJson(apiBase + "/" + paymentId);
      if (!current.ok) {
        callbacks.onStop && callbacks.onStop("error", null);
        return;
      }
      if (terminal[current.data.status]) {
        callbacks.onStop && callbacks.onStop("terminal", current.data);
        return;
      }
      if (current.data.status === "SENT") {
        if (Math.random() < 0.85) {
          body = { targetStatus: "COMPLETED" };
        } else {
          body = { targetStatus: "FAILED", errorCode: "SIMULATED_FAILURE" };
        }
      }

      var result = await fetchJson(apiBase + "/" + paymentId + "/process", "POST", body);
      if (!result.ok) {
        // Most commonly a 409 REFUND_NOT_APPROVED gate - stop quietly, the UI
        // already shows the PENDING_APPROVAL badge for this case.
        callbacks.onStop && callbacks.onStop("gated", current.data, result.data);
        return;
      }

      callbacks.onStep && callbacks.onStep(result.data, body, result.data);

      if (terminal[result.data.status]) {
        callbacks.onStop && callbacks.onStop("terminal", result.data);
        return;
      }
    }
    callbacks.onStop && callbacks.onStop("guard-limit", null);
  }

  /** Next manual transition available from a given status (for Debug mode buttons). */
  function nextManualSteps(status) {
    switch (status) {
      case "CREATED":
        return [{ label: "Advance to VALIDATED", body: {} }];
      case "VALIDATED":
        return [{ label: "Advance to SENT", body: {} }];
      case "SENT":
        return [
          { label: "Mark COMPLETED", body: { targetStatus: "COMPLETED" } },
          { label: "Mark FAILED", body: { targetStatus: "FAILED", errorCode: "SIMULATED_FAILURE" } }
        ];
      default:
        return [];
    }
  }

  /**
   * Renders a collapsible inspector entry (Debug mode) showing the exact
   * outgoing request and raw JSON response for one action.
   */
  function renderInspector(containerEl, method, url, requestBody, responseBody) {
    var entry = document.createElement("div");
    entry.className = "inspector-entry card";

    var reqLine = document.createElement("div");
    reqLine.className = "inspector-line";
    reqLine.innerHTML = "<strong>" + method + "</strong> " + url;
    entry.appendChild(reqLine);

    var reqBody = document.createElement("pre");
    reqBody.className = "inspector-json";
    reqBody.textContent = JSON.stringify(requestBody, null, 2);
    entry.appendChild(reqBody);

    var resLabel = document.createElement("div");
    resLabel.className = "inspector-line";
    resLabel.textContent = "Response:";
    entry.appendChild(resLabel);

    var resBody = document.createElement("pre");
    resBody.className = "inspector-json";
    resBody.textContent = JSON.stringify(responseBody, null, 2);
    entry.appendChild(resBody);

    containerEl.prepend(entry);
  }

  async function fetchJson(url, method, body) {
    try {
      var res = await fetch(url, method ? {
        method: method,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body || {})
      } : undefined);
      var data = null;
      try {
        data = await res.json();
      } catch (e) {
        data = null;
      }
      return { ok: res.ok, status: res.status, data: data };
    } catch (err) {
      return { ok: false, status: 0, data: { message: "Network error: " + err.message } };
    }
  }

  global.AppMode = {
    getMode: getMode,
    setMode: setMode,
    getTheme: getTheme,
    setTheme: setTheme,
    initModeToggle: initModeToggle,
    initThemeToggle: initThemeToggle,
    autoAdvance: autoAdvance,
    nextManualSteps: nextManualSteps,
    renderInspector: renderInspector,
    fetchJson: fetchJson
  };
})(window);
