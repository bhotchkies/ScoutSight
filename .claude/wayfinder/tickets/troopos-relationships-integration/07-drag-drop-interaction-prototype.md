---
title: Prototype the drag-and-drop relationship editor against troopOS's real data shape
label: wayfinder:prototype
status: closed
assignee: claude-session-2026-09-15
blocked_by: []
parent: ../../maps/troopos-relationships-integration.md
---

## Question

[05-data-mapping-design](05-data-mapping-design.md) decided that `admin.html`'s
Relationships tab keeps its existing drag-and-drop interaction rather than adopting
troop600.com's own type-to-search UI — that's the actual reason Blair wants a full
parallel editor instead of linking out to troopOS's native page. But the drag-and-drop
was originally built against GWS-shaped data (adults/scouts from `mvValues` fields on
Workspace user records); it now needs to work against troopOS's `/private/tables/users`
and `/private/tables/relationships` shapes and its native `id`s instead.

Raise the fidelity of this discussion with a cheap, concrete prototype (per the
`prototype` skill) of the drag-and-drop interaction wired to troopOS's real field names
and id scheme (including records with blank `email`/missing `scout.bsaMemberId`, per
[02-user-id-mapping](02-user-id-mapping.md)) — enough for Blair to react to "does this
still feel as fast as today's version" before committing to a full build.

## Resolution

**Fact-check**: the existing interaction (`admin.html` ~line 3690-4110) is not literal
HTML5 drag-and-drop despite the naming — it's click-to-connect: click a port dot on an
adult row, then a port dot on a scout row, drawing a curved SVG line; clicking a line
toggles it for removal. Confirmed no `draggable`/`dragstart` anywhere in the file. This
finding doesn't change the decision, just the vocabulary for future tickets/spec work.

Built a throwaway single-file HTML prototype reproducing this exact interaction against
realistic troopOS-shaped mock data — id-keyed (including a UUID-style id per
[02-user-id-mapping](02-user-id-mapping.md)), relationship membership derived from a
mock `relationships` array (`{adultId, scoutId, kind, verified}`) instead of mvValues
comma strings, plus a deliberate mix of records with blank `email`, blank
`personalEmail`, and/or missing `scout.bsaMemberId`. Verified live in-browser with
Blair (2026-09-15): **"looks solid"** — the port-click interaction and the
name→email→personalEmail→BSA#→"no contact info on file" label fallback chain hold up
against the messy real data shape; no changes requested.

**Captured as a primary source**: committed on throwaway branch
`prototype/troopos-relationships-dragdrop` (commit `3d0b2b9`,
`PROTOTYPE_troopos_relationships_dragdrop.html`), not merged to master. This ticket's
answer — the interaction and label-fallback approach are validated — is the artifact
the spec effort should build from.
