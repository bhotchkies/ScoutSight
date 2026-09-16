---
title: Confirm the real POST/PUT/DELETE contract for /private/tables/relationships
label: wayfinder:task
status: closed
assignee: claude-session-2026-09-15
blocked_by: []
parent: ../../maps/troopos-relationships-integration.md
---

## Question

[docs/troopos-admin-api.md](../../../docs/troopos-admin-api.md)'s write shapes (create,
verify, delete) are inferred from reading the frontend bundle, never exercised against
the real backend. Before locking a design that depends on them, confirm they actually
work as documented.

This is HITL and touches live production data, so it needs Blair logged in and
explicitly walking through it live (or explicitly authorizing each write in the
moment): create one throwaway test relationship via troopOS's own "Add relationship"
UI (or a direct API call, with Blair's go-ahead), confirm it appears via
`GET /private/tables/relationships`, then remove it, confirming the response shapes
and status codes match the doc. Record any deviation from the documented shapes.

Do not leave test data behind — the ticket isn't resolved until the throwaway
relationship is cleaned up.

## Resolution

Verified live against troop600.com (2026-09-15) using a throwaway relationship between
the obviously-fake "Testy McTestFace Sr." account and an arbitrary real scout, created
and removed twice through troopOS's own Add/Verify/Remove UI (never via direct API
calls from Claude — only observed via network request metadata: method, URL, status
code). Both test relationships were confirmed deleted afterward; no data left behind.

**Confirmed:**
- Create (via the Add form): succeeded, defaulted to `verified: true` when the
  checkbox was left checked — matches the doc.
- **`PUT /private/tables/relationships` → 200** — fired when clicking "Verify" on a
  relationship created with `verified` unchecked. Confirms the verify action is a PUT
  to this endpoint, matching the doc's `{ ...relationship, verified: true }` shape
  (shape inferred, not directly observed — see caveat below).
- **`DELETE /private/tables/relationships` → 200** — fired on "Remove", confirmed twice.

**Caveat — not fully verified:** the browser tooling available only exposes request
method/URL/status code, not request or response *bodies*. So the exact JSON shapes in
[docs/troopos-admin-api.md](../../../docs/troopos-admin-api.md) (e.g.
`{ troopId, relationshipId, adultId, scoutId, kind, verified }` for create,
`{ relationshipId }` for delete) are confirmed only by endpoint/method/status and by
the UI behaving as documented — not by inspecting actual payloads. If a future session
needs the literal wire format (e.g. to hand-write a client), that still needs a
DevTools Network-tab body inspection (same method as ticket 01/02) or a server-side log.

**New fact:** troopOS's own "Remove" button triggers a native browser `confirm()`
dialog before the DELETE fires — this blocked Claude's browser automation mid-task
(the tab became unresponsive to further commands until Blair manually confirmed the
dialog). Not directly relevant to `admin.html`'s own design (a human clicks its UI
too), but worth remembering if this API is ever driven headlessly/via automation later.
