# troopOS userType / pendingTypeAssignment reconciliation (2026-09-16)

Working notes for issue #7. Snapshot from a dry-run on 2026-09-16, run via browser
session (scripted fetch is 403'd by troop600.com's CloudFront/WAF — see below).

## Classification approach

troopOS's own `scout.bsaMemberId` is populated on only ~5 of the 164
`pendingTypeAssignment` accounts, so the ticket's original plan (cross-reference
`bsaMemberId` against the BSA roster CSV) resolves almost nothing in practice.

Same two-user-table split as issue #5: the GWS Directory record (admin.html's own
`?action=listUsers`, backed by `Is_Youth`/`Scout_ID` custom schema fields) has
`isYouth` + `scoutId` for nearly every account, keyed by `email` — which troopOS
pending accounts do mostly have, even when `bsaMemberId` is blank. Classification
now joins on `email` first, falling back to `bsaMemberId` <-> GWS `scoutId` for the
handful of troopOS accounts with no email on file, and trusts GWS's `isYouth` as
ground truth. The BSA roster CSV is used as a second fallback for anything GWS
doesn't resolve.

Implemented in `scripts/user_type_logic.js` (`classifyPendingUserByGws`,
`indexGwsUsers`) with unit tests in `test/user_type_logic.test.js`. Live run via
`scripts/classify-user-types.mjs`.

## Result summary (164 pendingTypeAssignment accounts)

- **32** proposed -> `scout`
- **45** proposed -> `adult`
- **87** still unresolved (no GWS record matched by email or bsaMemberId<->scoutId,
  or the matched GWS record's own `isYouth` is unset)

Role check on the 32 proposed scouts: only **1** carries an adult-exclusive roleId
(`mattiel@troop600.com` has `announcement-editor`, computed dynamically from
already-correctly-typed reference accounts — see `computeAdultExclusiveRoles`).
Per the agreed plan, this is reported only — roleIds are not rewritten in this pass.

## The 87 unresolved

Mostly accounts with blank `email` **and** no `bsaMemberId` at all in troopOS —
several look like test/temp accounts (`mctestfacesrt@troop600.com`,
`kbrinkman_temp@troop600.com` doesn't appear pending but is in GWS) or accounts
that plausibly never logged into troop600.com (same theory as issue #5's 30-row
list). A handful matched a GWS record whose own `isYouth` is blank/unset
(`connorb`, `blaker`, `medforms`, `dylanm`, `liamm`, `mccabet`, `humphreyl`,
`wonga`) — those need the GWS side fixed first, not troopOS.

## Blocked: scripted API access

Both `/private/tables/users` (troopOS) and the Apps Script `?action=listUsers`
(GWS) return errors when called via plain Node `fetch`, even with browser-matching
headers — troop600.com's CloudFront returns `403 Forbidden` for scripted requests
while an authenticated browser session works fine. The extension driving Chrome
also cannot script `file://` pages (where admin.html is opened from), so the GWS
pull for this run was done by pasting a DevTools snippet manually and copying the
result back. `scripts/classify-user-types.mjs` is written to `fetch()` normally and
will work as-is once/if the troopOS-side block is resolved; until then, rerunning
requires the same manual browser-session approach.

## Next step

Batch review of the 32 scout / 45 adult proposed list with Blair, per the agreed
plan (one explicit go-ahead for the reviewed batch, not per-account confirmation),
before any `PUT /private/tables/users` write happens. roleIds are report-only in
this pass. The 87 unresolved accounts are a separate follow-up — several may need
the GWS side fixed (blank `isYouth`) before they're resolvable at all.
