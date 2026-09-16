---
title: Lock the final integration decision
label: wayfinder:grilling
status: closed
assignee: claude-session-2026-09-15
blocked_by: [03-verify-write-api.md, 04-auth-gate-design.md, 05-data-mapping-design.md]
parent: ../../maps/troopos-relationships-integration.md
---

## Question

Synthesize the confirmed write contract ([03](03-verify-write-api.md)), the
auth/login-gate design ([04](04-auth-gate-design.md)), and the id-mapping/data-flow
design ([05](05-data-mapping-design.md)) into the single locked decision this map
exists to produce: the concrete approach for `admin.html`'s Relationships tab to read
and write troop600.com's real backend directly, and the plan for retiring the existing
GWS-based relationship editor.

This ticket's resolution is the map's destination. Once closed, a separate spec effort
picks up from here to produce build-ready detail.

## Resolution

Resolved via grilling with Blair (2026-09-15), synthesizing 01–05. This is the locked
decision.

**Auth** ([04](04-auth-gate-design.md)): `admin.html` cannot mint or capture a compatible
token itself. Blair manually pastes his own logged-in troop600.com session's JWT into a
new `scoutsight_troopos_jwt` localStorage key (mirrors the existing `GWS_URL_KEY`
pattern). Client-side `exp` check on load, with a clear "token expired, re-paste" prompt.
Coarse two-tier gating: `relationships:read` shows/hides the tab; `relationships:manage`
OR `relationships:verify` enables write actions. Per-action gating explicitly deferred
([08](08-fine-grained-rbac-gating.md), not blocking).

**Data flow** ([05](05-data-mapping-design.md)): Full parallel drag-and-drop editor is
kept (not a link-out) — it's faster than troopOS's own type-to-search UI, which is the
actual reason for building this at all. Reads hit `GET /private/tables/relationships`
and `GET /private/tables/users` directly, using troopOS's native `id`s with no
email/bsaMemberId mapping step required for the picker to function. Existing pending-
changes staging (`arPendingAdd`/`arPendingRemove`/Save/Discard) is kept; Save now fires
individual `POST`/`PUT`/`DELETE` calls per staged change against troopOS's confirmed
entity endpoints ([03](03-verify-write-api.md)) instead of a GWS batch write.

**Fallback**: The CSV export (`arExportCsv()`) → troopOS's native Import CSV bridge
remains as a standing manual fallback alongside the direct API path, not superseded.

**GWS editor retirement — hard cutover.** The GWS mvValues-based Relationships tab code
is deleted in the same change that lands the troopOS-backed version — no dual-running
toggle or rollback window. Blair is the sole user, troop600.com's data is already the
actively-maintained copy, and the CSV bridge above is fallback enough that a toggle
would be unused complexity.

**Pre-retirement verification step.** Before cutover, Claude (AFK) exports both sides —
GWS relationships via `admin.html`'s existing `arExportCsv()`, troopOS relationships via
its native `/admin/relationships` CSV export — and diffs them, flagging any GWS-side
relationship absent from troopOS for Blair to review before the GWS path is deleted.
Resolves the "Migration/cutover sequencing" item in the map's Not yet specified section.

**Build sequencing — locked.** [07-drag-drop-interaction-prototype](07-drag-drop-interaction-prototype.md)
must be validated against troopOS's real data shape before the full build starts, since
it could reveal the kept drag-and-drop interaction doesn't hold up (blank emails, missing
`bsaMemberId`). If the prototype surfaces problems, the fallback is to adjust the
prototype (e.g. better fallback labels for incomplete records) within this same
approach — not to reopen [05](05-data-mapping-design.md)'s full-parallel-editor decision.
[08-fine-grained-rbac-gating](08-fine-grained-rbac-gating.md) remains explicitly
deferred/non-blocking, picked up only if a second admin user with narrower permissions
materializes.

**Handoff**: This closes the map's destination. A separate spec effort follows to
produce build-ready detail, sequenced after [07](07-drag-drop-interaction-prototype.md).
