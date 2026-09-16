---
title: troop600.com relationships backend integration
label: wayfinder:map
status: destination-reached
created: 2026-09-15
tickets_dir: ../tickets/troopos-relationships-integration
---

## Destination

Lock the concrete approach for moving `admin.html`'s Relationships tab off Google
Workspace custom-schema storage and onto troop600.com's real backend directly —
reading and writing through its private API (`/private/tables/relationships`,
cross-referenced with `/private/tables/users`) — including how `admin.html`
authenticates against troop600.com, and retiring the existing GWS-based relationship
editor once this lands. This is a **decision**, not a spec; a spec effort follows
separately once the map is walked.

## Notes

- Domain: ScoutSight `admin.html` (Java/Thymeleaf-generated static admin console) ×
  troopOS (the live production site at troop600.com, a separate React app with its own
  private REST API).
- Reference: [docs/troopos-admin-api.md](../../../docs/troopos-admin-api.md) —
  reverse-engineered from the troopOS frontend bundle. GET endpoints
  (`/private/tables/roles`, `/users`, `/relationships`) were confirmed live against a
  real authenticated session on 2026-09-15. Writes (POST/PUT/DELETE) are still
  unverified against the real backend.
- troop600.com's live relationships data is already real and actively maintained by
  Blair directly (not via `admin.html`/GWS today).
- troop600.com's `/admin/relationships` page has a native **Import CSV** button whose
  documented column spec (`adultEmail, scoutEmail, kind, verified`) matches
  `admin.html`'s existing `arExportCsv()` output exactly — this manual export→import
  bridge already works today and is a standing fallback regardless of where this
  decision lands.
- Live verification against troop600.com requires Blair to be logged in first — Claude
  cannot perform the Google login itself (credential entry is prohibited). Read-only
  GETs can be driven directly once logged in; writes to live production data need
  Blair's explicit go-ahead for each write.
- Default to calling the Skill tool for "grilling" and "domain-modeling" when resolving
  a ticket, unless the ticket says otherwise.

## Decisions so far

- [Is troop600.com's JWT Google-issued or backend-minted?](../tickets/troopos-relationships-integration/01-jwt-origin.md): Backend-minted (`iss`/`aud` = `troopos:troop600`), carrying a real per-user `permissions` RBAC array. `admin.html` must go through troop600.com's real login mechanism — an independent client-side Google Sign-In won't produce an accepted token.
- [How does /private/tables/users expose email/id for adult-scout resolution?](../tickets/troopos-relationships-integration/02-user-id-mapping.md): `id`+`email` co-exist, but `email` is blank until a user fully onboards. `scout.bsaMemberId` (present on many, not all, records — adults included) is the more robust cross-system join key than email.
- [Decide the auth/login-gate mechanism for admin.html](../tickets/troopos-relationships-integration/04-auth-gate-design.md): Manual paste — Blair copies the JWT from his own logged-in troop600.com session into a new `scoutsight_troopos_jwt` localStorage key (same pattern as `GWS_URL_KEY`), with client-side `exp` checking. Coarse two-tier permission gating (`relationships:read` for tab visibility, `manage`/`verify` combined for write UI); fine-grained per-action gating deferred to a follow-up ticket.
- [Decide the id-mapping / read-write data flow for relationships](../tickets/troopos-relationships-integration/05-data-mapping-design.md): Full parallel editor (kept, not a link-out) because troopOS's own UI is too slow to use. Reads and single-entity writes go straight to troopOS using its native `id`s (no email/bsaMemberId mapping needed for the picker); existing drag-and-drop + staged pending-changes model is kept, targeting live writes instead of a GWS batch save.
- [Confirm the real POST/PUT/DELETE contract for /private/tables/relationships](../tickets/troopos-relationships-integration/03-verify-write-api.md): Create/PUT-verify/DELETE all confirmed live (method+URL+status only, bodies not inspectable with available tooling) — matches the doc. troopOS's own "Remove" uses a native `confirm()` dialog.
- [Lock the final integration decision](../tickets/troopos-relationships-integration/06-final-decision.md): **Destination reached.** Manual-paste JWT auth (`scoutsight_troopos_jwt`) with coarse read/manage-or-verify gating; full parallel drag-and-drop editor reading/writing troopOS directly via its native ids, existing pending-changes staging kept; CSV export/import bridge stays as fallback; GWS-based editor is a hard cutover (deleted, not toggled) after an agent-run CSV diff verifies nothing GWS-only would be lost; build sequencing requires [07-drag-drop-interaction-prototype](../tickets/troopos-relationships-integration/07-drag-drop-interaction-prototype.md) to validate first.
- [Prototype the drag-and-drop relationship editor against troopOS's real data shape](../tickets/troopos-relationships-integration/07-drag-drop-interaction-prototype.md): Validated live with Blair ("looks solid") — id-keyed port-click interaction plus a name→email→personalEmail→BSA#→placeholder label fallback chain hold up against blank-email/blank-bsaMemberId records. Also corrected the record: the interaction is click-to-connect, not literal HTML5 drag-and-drop. Prototype captured on throwaway branch `prototype/troopos-relationships-dragdrop`. Build sequencing unblocked.

## Not yet specified

(none — the destination is reached; see [06-final-decision](../tickets/troopos-relationships-integration/06-final-decision.md))

## Out of scope

- Folding troopOS's native Google Workspace admin page (`/admin/google-workspace`:
  user onboarding, OU management) into or out of `admin.html`'s own GWS features. Real
  overlap surfaced during charting, but beyond this destination (relationships only) —
  a separate future effort if pursued.
- The `/admin/roles` crash (`Cannot read properties of null (reading 'slice')`) and the
  `/api/v1/scoutbook/` 403s observed live on troop600.com during charting — unrelated
  to relationships, not this effort's concern.
