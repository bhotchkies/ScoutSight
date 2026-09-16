# Spec: `admin.html` Relationships tab → troopOS backend

Build-ready detail for the decision locked in the
[troop600.com relationships backend integration](../.claude/wayfinder/maps/troopos-relationships-integration.md)
wayfinder map (tickets 01–07, all closed). This is the handoff document — everything
here is either a locked decision or an implementation detail derived from one; there
are no open questions left to grill. Ticket
[08-fine-grained-rbac-gating](../.claude/wayfinder/tickets/troopos-relationships-integration/08-fine-grained-rbac-gating.md)
remains open but explicitly deferred (pick up only if a second, narrower-permission
admin user starts using the tool) and is out of scope for this build.

## 1. Auth gate

Source: [04-auth-gate-design](../.claude/wayfinder/tickets/troopos-relationships-integration/04-auth-gate-design.md)

- New localStorage key: `scoutsight_troopos_jwt`, same settings-panel pattern as the
  existing `GWS_URL_KEY` (a labeled input + Save button in the admin.html setup UI).
- On load, if the key is present: decode the JWT payload client-side (base64, no crypto
  library needed — split on `.`, base64-decode the middle segment, `JSON.parse`).
  - If `exp` is in the past, treat as absent: show "Token expired — please re-paste your
    troop600.com session token" and hide the Relationships tab's data views (same UX as
    "not configured").
  - If valid, read `payload.permissions` (array of strings).
- Tab visibility: show the Relationships tab only if `permissions` includes
  `relationships:read`.
- Write-action gating: enable drag-and-drop assign/remove and the Save button only if
  `permissions` includes `relationships:manage` OR `relationships:verify`. No per-action
  distinction between `manage` and `verify` (deferred to ticket 08).
- All troopOS API calls send `Authorization: Bearer <token>` where `<token>` is the raw
  string from `scoutsight_troopos_jwt` (not the decoded payload).
- No token minting or refresh flow — this is a static tool with no backend of its own;
  Blair re-pastes the token from his own logged-in troop600.com session's localStorage
  each time it expires.

## 2. Data flow

Source: [05-data-mapping-design](../.claude/wayfinder/tickets/troopos-relationships-integration/05-data-mapping-design.md),
[docs/troopos-admin-api.md](./troopos-admin-api.md)

### Reads

On opening the Relationships tab (replacing today's dependence on `allUsers` from the
GWS `listUsers()` load):

- `GET https://troop600.com/private/tables/users` → full user array. Filter into
  adults/scouts by `userType === "adult"` / `"scout"` (replaces GWS's `isYouth`
  boolean check).
- `GET https://troop600.com/private/tables/relationships` → full relationships array,
  each `{ relationshipId, adultId, scoutId, kind, verified }`.

Both are unfiltered/unpaginated per the docs — fetch the whole table each time, same as
today's GWS load.

### Identity & display

- **Identity key**: troopOS's own `id` field, always treated as an opaque string (some
  are UUIDs, per [02-user-id-mapping](../.claude/wayfinder/tickets/troopos-relationships-integration/02-user-id-mapping.md)).
  Never `email`. No email→id lookup table is built or needed.
- **Display label fallback chain** (validated in the
  [07 prototype](../.claude/wayfinder/tickets/troopos-relationships-integration/07-drag-drop-interaction-prototype.md),
  Blair confirmed "looks solid"):
  1. `email` if non-empty
  2. else `personalEmail + " (personal)"` if non-empty
  3. else `"BSA# " + scout.bsaMemberId + " — no email on file"` if `scout.bsaMemberId`
     present
  4. else `"No contact info on file"`
  - Name (`lastName, firstName`) is always shown regardless — troopOS requires it at
    account creation, so the fallback chain only ever affects the secondary/contact
    line, never the primary label.
- **Relationship chip label** (on a connected person's card, identifying who they're
  linked to): `firstName + " (" + email.split('@')[0] + ")"` if email present, else just
  `firstName` if `personalEmail` present, else `firstName + " (no email)"`.
- `scout.bsaMemberId` (when present, on adult or scout records) may be used to
  cross-reference against ScoutSight's own Scoutbook-derived data to *annotate* the
  display (e.g. flag a scout with no verified parent link) — never required for the
  picker to function.

### Interaction (unchanged from today, per 05 and validated by 07)

The existing port-click-to-connect interaction is kept as-is, re-keyed from `email` to
`id`:

- Click a port dot on an adult row → selects it (`arActiveEmail`/`arActiveType`
  equivalents, now `arActiveId`/`arActiveType`).
- Click a port dot on a scout row while an adult is active → stages a relationship
  (or clears selection if the same person is clicked again).
- Click an existing SVG line → toggles that relationship for removal.
- Note for future tickets/spec readers: this is **not** literal HTML5 drag-and-drop
  (no `draggable`/`dragstart` in the current code) — it's click-to-connect. The
  "drag-and-drop" name in prior tickets is a misnomer that doesn't affect this spec.

### Writes

Replaces today's single GWS Apps Script batch-save call. On Save, iterate staged
changes and fire troopOS's confirmed entity endpoints
([03-verify-write-api](../.claude/wayfinder/tickets/troopos-relationships-integration/03-verify-write-api.md))
sequentially, same loop-with-progress pattern as today's `arSaveChanges()`:

- **Add** → `POST /private/tables/relationships`
  ```json
  { "troopId": "<troop id>", "relationshipId": "${adultId}#${scoutId}", "adultId": "...", "scoutId": "...", "kind": "parent", "verified": true }
  ```
  (`kind` is always `"parent"` and `verified` always `true` — matches today's GWS
  behavior of modeling a single undifferentiated link, manually drawn by an admin.)
- **Remove** → `DELETE /private/tables/relationships`, body `{ "relationshipId": "${adultId}#${scoutId}" }`.
- No verify-only UI action is being added — `PUT` (verify) is not part of this build;
  `admin.html` always creates relationships pre-verified, matching current behavior.
- On completion, re-fetch `GET /private/tables/relationships` to confirm writes, same
  as today's post-save reconciliation pattern.
- **Request body caveat carried over from ticket 03**: the exact JSON shape above is
  confirmed by endpoint/method/status/UI-behavior, not by inspecting real request
  bodies (browser tooling limitation). If a write fails with an unexpected shape error,
  check Chrome DevTools Network tab against a real POST from troopOS's own Add form to
  compare the actual payload before assuming this spec is wrong.

### Pending-changes model

Kept exactly as today (`arPendingAdd`/`arPendingRemove`/Save/Discard staging, chip
rendering distinguishing saved/pending-add/pending-remove) — only the identity field
changes from `parentEmail`/`scoutEmail` to `adultId`/`scoutId`.

### Fallback path (unchanged, stays as a manual option)

`admin.html`'s `arExportCsv()` and troopOS's native `/admin/relationships` Import CSV
button remain as-is, side-by-side with the new direct-API path. No changes needed here
— this bridge already works today and isn't superseded.

## 3. GWS relationship editor retirement

Source: [06-final-decision](../.claude/wayfinder/tickets/troopos-relationships-integration/06-final-decision.md)

**Hard cutover** — delete the GWS mvValues-based Relationships code
(`arGetRelEmails`, the `user.scouts`/`user.parents` comma-string reads, and the GWS
Apps Script batch-save call path for relationships specifically) in the same change
that lands the sections above. No feature flag, no dual-running period.

### Pre-retirement verification step (do this before deleting GWS code)

1. Export GWS-side relationships via `admin.html`'s existing `arExportCsv()`.
2. Export troopOS-side relationships via its native `/admin/relationships` CSV export.
3. Diff the two by `(adultEmail, scoutEmail)` pair (GWS export uses emails; if troopOS's
   export also uses emails per its Import CSV column spec, compare directly — if it
   exports by id instead, resolve ids back to emails via `/private/tables/users` first).
4. Flag any GWS-side pair absent from the troopOS export for Blair to review — either
   it needs manual entry on troop600.com before cutover, or it's genuinely stale and
   safe to drop.
5. Only after Blair confirms no GWS-only data would be lost, delete the GWS relationship
   code.

This step is AFK (Claude can run it) except for the final human review of any flagged
discrepancies.

## 4. Explicitly out of scope for this build

- Per-action RBAC gating distinguishing `relationships:manage` from
  `relationships:verify` — ticket 08, deferred.
- A `PUT` (verify) UI action — not part of today's behavior, not being added.
- Any change to troopOS's own `/admin/relationships` page or its Import CSV feature.
- Migrating other `admin.html` GWS features (roles, patrols, etc.) to troopOS — this
  spec is relationships-only, per the map's Out of scope section.

## 5. Suggested build order

1. Auth gate (§1) — token storage, decode/exp-check, tab/write gating. Testable in
   isolation with a hand-crafted JWT.
2. Read path (§2 reads/identity/display) — swap `allUsers`/`arGetRelEmails` sourcing
   for troopOS fetches, re-key the existing render/interaction functions from email to
   id. The [07 prototype](../.claude/wayfinder/tickets/troopos-relationships-integration/07-drag-drop-interaction-prototype.md)
   (`prototype/troopos-relationships-dragdrop` branch) is the reference implementation
   for the re-keyed interaction and label fallback chain — port its logic rather than
   re-deriving it.
3. Write path (§2 writes) — POST/DELETE against confirmed endpoints, re-fetch-to-confirm.
4. Pre-retirement verification (§3) — run the CSV diff, get Blair's sign-off.
5. Delete GWS relationship code (§3) — hard cutover.
