---
title: How does /private/tables/users expose email/id for adult-scout resolution?
label: wayfinder:task
status: closed
assignee: claude-session-2026-09-15
blocked_by: []
parent: ../../maps/troopos-relationships-integration.md
---

## Question

`admin.html` (and ScoutSight generally) identifies people by BSA Member ID and
`@troop600.com` email address. troopOS's `/private/tables/relationships` create
endpoint takes `adultId`/`scoutId` (troopOS's own internal user ids, per
[docs/troopos-admin-api.md](../../../docs/troopos-admin-api.md)), picked from a
dropdown backed by `/private/tables/users` in the real admin UI. The native Import CSV
path, by contrast, accepts plain emails — implying the backend does its own
email→id resolution for bulk import specifically.

Determine: does each record returned by `GET /private/tables/users` include an email
field alongside its `id`, sufficient for `admin.html` to build its own
email→adultId/scoutId lookup table client-side for the single-entity POST/PUT/DELETE
endpoints? Or is id resolution only available through the CSV import path?

This needs a live, logged-in troop600.com session (Blair) so the response body can be
inspected directly (e.g. re-fetch `/private/tables/users` and read the field shapes) —
no credential handling required, just a live authenticated tab, which a cold subagent
doesn't have.

## Resolution

Resolved live from a real `/private/tables/users` response fetched during Blair's
session (2026-09-15). The response is the full troop roster (~150+ records) with real
names, personal emails, phone numbers, and BSA member IDs — that raw data is **not**
reproduced here or anywhere in the repo; only the structural shape is recorded.

**`id` and `email` do co-exist on the same record** — confirmed. But:

- `email` (the `@troop600.com` address) is frequently an **empty string**. It's only
  populated once a person has completed Google sign-in and full account provisioning —
  it appears together with a non-empty `sub` (Google subject id) claim, never alone.
  Plenty of real, active roster records have both `email: ""` and `sub: ""`.
- A separate `personalEmail` field (their outside contact address, e.g. a personal
  Gmail) is populated far more consistently, including for people who have never
  completed troopOS onboarding.
- A nested `scout: { bsaMemberId, patrol, rank, dateOfBirth, joinDate }` object appears
  on **many but not all** records — on `userType: "scout"` records as expected, but
  also on a number of `userType: "adult"` records (registered adult leaders carry their
  own BSA member IDs too). Where present, `scout.bsaMemberId` is a plain string.
- `id` values are **not** uniformly small integers — most are integer-strings, but a
  handful are UUID strings (manually-added/imported records). Treat `id` as an opaque
  string always, never parse or assume it's numeric.

**Answer:** A plain `email` → troopOS `id` lookup table is unreliable as the *sole*
mapping strategy, since `email` is blank for any not-fully-onboarded user. `bsaMemberId`
(nested under `scout`) is the more robust cross-system join key against ScoutSight's
Scoutbook-derived records where present, with `personalEmail` as a secondary fallback
for matching by contact address. Any id-mapping design
([05-data-mapping-design](05-data-mapping-design.md)) needs to handle records that have
none of `email`, `sub`, or `scout.bsaMemberId` populated (fully unprovisioned accounts)
as an explicit "can't map yet" case, not an error.
