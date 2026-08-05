/*
 * Reusable vanilla-JS component that renders a payment_status_history array
 * as a visual timeline (spec.md Section 9 - M4). Consumed by:
 *   - frontend-business/audit.html (M2), frontend-business/ops.html (M4)
 *   - frontend-user/history.html (M4)
 *
 * Usage:
 *   renderLifecycleTimeline(document.getElementById('timeline'), historyEntries);
 *   renderLifecycleTimeline(el, historyEntries, { approvalStatus: 'PENDING_APPROVAL' });
 *
 * `historyEntries` is the array returned by GET /api/payments/{id}/history
 * (spec.md Section 10.4): [{ fromStatus, toStatus, changedAt, triggeredBy, note }, ...]
 *
 * `options.approvalStatus` (optional, added 2026-08-05, Section 14.1) - when present,
 * renders a badge above the timeline showing the refund's approval sub-state
 * (PENDING_APPROVAL/APPROVED/REJECTED). Omitted entirely for non-refund payments or
 * until the backend actually returns this field.
 */

function renderLifecycleTimeline(containerEl, historyEntries, options) {
  containerEl.innerHTML = "";

  var approvalStatus = options && options.approvalStatus;
  if (approvalStatus) {
    var approvalRow = document.createElement("div");
    approvalRow.className = "timeline-approval-status";
    var label = document.createElement("span");
    label.textContent = "Refund approval: ";
    approvalRow.appendChild(label);
    approvalRow.appendChild(makeStatusBadge(approvalStatus));
    containerEl.appendChild(approvalRow);
  }

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
