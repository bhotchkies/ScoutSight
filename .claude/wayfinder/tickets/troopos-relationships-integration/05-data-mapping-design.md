---
title: Decide the id-mapping / read-write data flow for relationships
label: wayfinder:grilling
status: closed
assignee: claude-session-2026-09-15
blocked_by: []
parent: ../../maps/troopos-relationships-integration.md
---

## Question

[02-user-id-mapping](02-user-id-mapping.md) found that `/private/tables/users` records
mix `id`+`email` (email often blank pre-onboarding), a more consistently-populated
`personalEmail`, and an inconsistently-present `scout.bsaMemberId` (on both scout and
adult records). Decide how `admin.html`'s Relationships tab reads and writes against
troopOS given that mapping is imperfect:

- Reads: fetch `/private/tables/relationships` (and `/private/tables/users` for
  display names/emails) directly in place of today's GWS `mvValues` fields.
- Writes: call the single-entity POST/PUT/DELETE endpoints directly (needs the
  email→id lookup from ticket 02), keep using the CSV-import bridge for bulk
  operations, or some mix — e.g. single add/remove via direct API, bulk import still
  via the existing CSV path since troopOS already has a dedicated UI for it.

Fold in whether `admin.html`'s existing `arGetRelEmails`/`arPendingAdd`/
`arPendingRemove` pending-changes model still makes sense once writes can go straight
to a live backend instead of being queued for a manual GWS save step.

## Resolution

Resolved via live grilling with Blair (2026-09-15). Key input: Blair chose a full
parallel editor in `admin.html` (not a read-only cross-reference or link-out to
troop600.com's own page) specifically because troop600.com's native Relationships UI
is slow to use — it requires typing/searching to pick users, versus `admin.html`'s
existing drag-and-drop interaction.

1. **Reads**: `admin.html` fetches `GET /private/tables/relationships` and
   `GET /private/tables/users` directly from troopOS, replacing the GWS `mvValues`
   reads entirely.
2. **Id resolution**: No separate mapping step. Adult/scout pickers and drag-and-drop
   targets are built directly from troopOS's own `/private/tables/users` list, using
   its native `id`s. ScoutSight's Scoutbook-derived data (ranks, BSA Member ID) is used
   only to *annotate* the display (e.g. flag a scout with no verified parent link),
   cross-referenced by `scout.bsaMemberId` where available — never required for the
   picker to function, per [02-user-id-mapping](02-user-id-mapping.md)'s finding that
   email/bsaMemberId coverage is inconsistent.
3. **Write path**: The existing drag-and-drop interaction is kept — it's the actual
   speed advantage over troopOS's own UI, not incidental to the staging model. Staged
   adds/removes accumulate as today; on Save, `admin.html` fires individual
   `POST`/`PUT`/`DELETE` calls to troopOS's entity endpoints per staged change (looped),
   not through the CSV import bridge. The CSV export/import bridge (`arExportCsv()` +
   troopOS's native Import CSV button) remains as a standing manual fallback, not the
   primary mechanism.
4. **Pending-changes model**: Kept as-is (`arPendingAdd`/`arPendingRemove`/Save/
   Discard), now targeting live troopOS writes instead of a GWS batch save.

**Follow-up ticket spun off:** [07-drag-drop-interaction-prototype](07-drag-drop-interaction-prototype.md)
(Prototype type) — the actual drag-and-drop interaction against troopOS's real data
shape is a "how should it behave" design question, not a data-flow one, so it gets its
own ticket rather than being specified further here.
