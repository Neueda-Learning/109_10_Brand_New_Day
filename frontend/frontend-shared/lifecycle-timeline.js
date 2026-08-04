/*
 * Reusable vanilla-JS component that renders a payment_status_history array
 * as a visual timeline (spec.md Section 9 - M4). Consumed by:
 *   - frontend-business/audit.html (M2)
 *   - frontend-user/history.html (M4)
 *
 * Usage:
 *   renderLifecycleTimeline(document.getElementById('timeline'), historyEntries);
 *
 * `historyEntries` is the array returned by GET /api/payments/{id}/history
 * (spec.md Section 10.4): [{ fromStatus, toStatus, changedAt, triggeredBy, note }, ...]
 */

function renderLifecycleTimeline(containerEl, historyEntries) {
    if (!historyEntries || historyEntries.length === 0) {
        containerEl.innerHTML = "<p>No history available</p>";
        return;
    }

    const timelineList = document.createElement("ul");
    timelineList.className = "timeline";

    historyEntries.forEach((entry, index) => {
        const timelineItem = document.createElement("li");
        timelineItem.className = "timeline-item";

        const badgeClass = `status-badge status-${entry.toStatus.toLowerCase()}`;
        const badge = document.createElement("span");
        badge.className = badgeClass;
        badge.textContent = entry.toStatus;

        const dateStr = formatTimestamp(entry.changedAt);

        const transitionText = entry.fromStatus
            ? `${entry.fromStatus} → ${entry.toStatus}`
            : `Initial: ${entry.toStatus}`;

        timelineItem.innerHTML = `
            <div class="timeline-entry">
                <div class="timeline-marker">${badge.outerHTML}</div>
                <div class="timeline-content">
                    <p class="timeline-transition"><strong>${transitionText}</strong></p>
                    <p class="timeline-timestamp">${dateStr}</p>
                    ${entry.note ? `<p class="timeline-note"><em>Note: ${entry.note}</em></p>` : ""}
                    <p class="timeline-triggered">Triggered by: ${entry.triggeredBy}</p>
                </div>
            </div>
        `;

        timelineList.appendChild(timelineItem);
    });

    containerEl.appendChild(timelineList);
}

function formatTimestamp(isoString) {
    const date = new Date(isoString);
    return date.toLocaleString("en-US", {
        year: "numeric",
        month: "short",
        day: "numeric",
        hour: "2-digit",
        minute: "2-digit",
        second: "2-digit",
        timeZone: "UTC"
    }) + " UTC";
}
