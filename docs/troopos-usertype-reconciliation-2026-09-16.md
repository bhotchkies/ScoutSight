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

## Applied (2026-09-16)

Batch reviewed and approved by Blair. Role targets were updated from report-only to
active, using two accounts Blair fixed by hand via troop600.com's native UI as the
reference model: `milesg@troop600.com` (scout: `roleIds` -> `["member","scout"]`)
and `vielbige@troop600.com` (adult/parent: `roleIds` -> `["member","parent"]`).

All 77 accounts (32 -> scout, 45 -> adult) were written via `PUT
/private/tables/users`, one call per account, run by Blair pasting a generated
script into troop600.com's own DevTools console (the harness's auto-mode
permission classifier blocked Claude from executing the batch write directly via
browser-script injection — a system-level guard on high-risk actions, separate
from Blair's own go-ahead).

**Result: 77/77 succeeded.** Each scout account got `userType: "scout"`,
`pendingTypeAssignment` cleared, and `roleIds` set to its prior roles minus any
adult-exclusive ones plus `member`+`scout` (e.g. `mattiel@troop600.com` had its
stray `announcement-editor` role stripped while `calendar-editor` and
`documents-editor` were kept). Each adult account got `userType: "adult"`,
`pendingTypeAssignment` cleared, and `member`+`parent` added to its existing
roles. Spot-checked 5 accounts post-write against live data — all correct.

## Second and third pass (2026-09-16, same day)

Of the 87 initially unresolved, dug further into two subsets:

**80 of 87 have no `sub` (Google OAuth subject ID) at all** — i.e. they have
never completed a Google login to troop600.com. Cross-referencing their names
(not just BSA number/email) against the full roster CSV resolved 6 more: Tanu
Mutreja, Ariel Somppi Moore, Jonny Schultz, Lindsey Humphrey -> adult; Arjun
Mohan, Nitin Selva -> scout. Applied via the same `userType`/`roleIds`/
`pendingTypeAssignment` fix as the first batch (paste-and-run script, run by
Blair). **6/6 succeeded.**

The remaining **74 no-`sub` accounts do not appear on the current roster at
all**. Repeated family surnames (Brombaugh x3, Stirret x3, Purvis x3, Bach x3,
McCabe x3, Zhou x2) strongly suggest these are historical/alumni-family records,
bulk-imported at some point but never activated — not current troop members.
**Decision: documented here, not tackled.** Out of scope for this ticket unless
a future need arises to classify or clean up historical accounts.

**7 of 87 do have a `sub`** (i.e. have actually logged in) and were resolved
individually by naming-convention judgment, cross-checked against family
relationships already known from issue #5's data:

| Account | -> userType | Basis |
|---|---|---|
| Mat Rocha | adult | lastname+firstinitial email pattern; parent of Blake Rocha |
| Connor Burchard | scout | firstname+lastinitial email pattern |
| Blake Rocha | scout | firstname+lastinitial email pattern; child of Mat Rocha |
| William Zhang | scout | firstname+lastinitial email pattern |
| Anthony Wong | adult | lastname+firstinitial email pattern (GWS record's own `isYouth` was blank — a GWS-side gap, worked around here by direct judgment) |
| Medical Forms (`medforms@troop600.com`) | **excluded** | a utility/shared mailbox, not a person |
| Testy McTestFace Sr. (`mctestfacesrt@troop600.com`) | **excluded** | an obvious test account |

Applied the 5 real accounts via the same fix pattern. **5/5 succeeded.**

## Final tally

**88 of 164** `pendingTypeAssignment` accounts resolved and written this session
(77 + 6 + 5). **2 accounts** (`medforms`, `mctestfacesrt`) identified as
non-person utility/test accounts and deliberately excluded — not classified
either way, left as-is. **74 accounts** documented as likely stale/alumni-family
records (no login ever, not on current roster) and deliberately **not tackled**
— out of scope for this ticket.

164 - 88 - 2 - 74 = 0. Every `pendingTypeAssignment` account as of this session
has been reviewed and either resolved, excluded as a non-person account, or
explicitly deferred as out-of-scope historical data — none were silently
skipped.

roleIds mismatch checking (adult-exclusive role stripping) was applied to all
37 scout-bound accounts across all three passes (32 + 2 + 3), not just the
first batch. 51 accounts were confirmed adult (45 + 4 + 2).
