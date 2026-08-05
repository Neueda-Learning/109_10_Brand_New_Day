/*
 * Reusable vanilla-JS component that renders a payment_status_history array
 * as a visual timeline (spec.md Section 9 - M4, extended Section 14.1/14.2
 * for v2.2 refund-approval sub-state + reveal animation). Consumed by:
 *   - frontend-business/index.html (unified ops app)
 *   - frontend-user/index.html (unified consumer app)
 *
 * Usage:
 *   renderLifecycleTimeline(document.getElementById('timeline'), historyEntries);
 *
 *   // With the optional 3rd arg (added 2026-08-05, v2.2), a REFUND payment's
 *   // approval sub-state is also rendered as a trailing pseudo-step:
 *   renderLifecycleTimeline(el, historyEntries, {
 *     approvalStatus: payment.approvalStatus,   // "PENDING_APPROVAL"|"APPROVED"|"REJECTED"|null
 *     approvedBy: payment.approvedBy,
 *     rejectionReason: payment.rejectionReason
 *   });
 *
 * `historyEntries` is the array returned by GET /api/payments/{id}/history
 * (spec.md Section 10.4): [{ fromStatus, toStatus, changedAt, triggeredBy, note }, ...]
 *
 * The `.timeline-item` reveal animation (fade + slide-up) is defined in
 * design-tokens.css via the `timeline-reveal` keyframes - no JS-side animation
 * logic needed here, CSS handles it whenever a new <li> is appended.
 */

function renderLifecycleTimeline(containerEl, historyEntries, approvalInfo) {
  containerEl.innerHTML = "";

  if (!historyEntries || historyEntries.length === 0) {
    var empty = document.createElement("p");
    empty.className = "timeline-empty";
    empty.textContent = "No history available.";
    containerEl.appendChild(empty);
    return;
  }

  var list = document.createElement("ul");
  list.className = "timeline";

  historyEntries.forEach(function (entry) {
    var item = document.createElement("li");
    item.className = "timeline-item";

    var transition = document.createElement("div");
    transition.className = "timeline-item-transition";
    if (entry.fromStatus) {
      transition.appendChild(makeStatusBadge(entry.fromStatus));
      transition.appendChild(document.createTextNode(" \u2192 "));
    }
    transition.appendChild(makeStatusBadge(entry.toStatus));
    item.appendChild(transition);

    var meta = document.createElement("div");
    meta.className = "timeline-item-meta";
    meta.textContent = formatChangedAt(entry.changedAt) + " \u00B7 triggered by " + entry.triggeredBy;
    item.appendChild(meta);

    if (entry.note) {
      var note = document.createElement("div");
      note.className = "timeline-item-note";
      note.textContent = entry.note;
      item.appendChild(note);
    }

    list.appendChild(item);
  });

  // Refund approval sub-state (added 2026-08-05, v2.2): only rendered when the
  // caller passes approvalStatus (i.e. the payment is a REFUND). Shown as one
  // extra pseudo-step after the real history, since approval isn't itself a
  // payment_status_history row.
  if (approvalInfo && approvalInfo.approvalStatus) {
    var approvalItem = document.createElement("li");
    approvalItem.className = "timeline-item";

    var approvalTransition = document.createElement("div");
    approvalTransition.className = "timeline-item-transition";
    approvalTransition.appendChild(makeStatusBadge(approvalInfo.approvalStatus));
    approvalItem.appendChild(approvalTransition);

    var approvalMeta = document.createElement("div");
    approvalMeta.className = "timeline-item-meta";
    if (approvalInfo.approvalStatus === "APPROVED" && approvalInfo.approvedBy) {
      approvalMeta.textContent = "Approved by " + approvalInfo.approvedBy;
    } else if (approvalInfo.approvalStatus === "REJECTED" && approvalInfo.rejectionReason) {
      approvalMeta.textContent = "Rejected: " + approvalInfo.rejectionReason;
    } else {
      approvalMeta.textContent = "Awaiting business approval before this refund can proceed.";
    }
    approvalItem.appendChild(approvalMeta);

    list.appendChild(approvalItem);
  }

  containerEl.appendChild(list);
}

function makeStatusBadge(status) {
  var badge = document.createElement("span");
  badge.className = "status-badge " + status;
  badge.textContent = status;
  return badge;
}

function formatChangedAt(isoString) {
  var date = new Date(isoString);
  return isNaN(date.getTime()) ? isoString : date.toLocaleString();
}
