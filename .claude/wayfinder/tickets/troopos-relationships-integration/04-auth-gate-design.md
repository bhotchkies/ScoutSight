---
title: Decide the auth/login-gate mechanism for admin.html
label: wayfinder:grilling
status: resolved
assignee: claude
blocked_by: []
parent: ../../maps/troopos-relationships-integration.md
---

## Question

[01-jwt-origin](01-jwt-origin.md) confirmed troop600.com's JWT is backend-minted
(`iss`/`aud` = `troopos:troop600`), not a raw Google id token — so `admin.html` cannot
obtain a valid token via its own independent client-side Google Sign-In. Decide:

- How does `admin.html` obtain the real token — redirect through troop600.com's actual
  `/login` page/flow and capture the result, or some other equivalent to that
  backend-minting step? What does that look like embedded in a separate static HTML
  tool that isn't part of the troop600.com SPA?
- Where is the resulting token stored — Q7 in the charting conversation leaned toward
  matching the existing `GWS_URL_KEY` client-side `localStorage` pattern; confirm that
  still holds.
- The JWT carries a real per-user `permissions` array (RBAC), not just "logged in or
  not" — the design should check for `relationships:read`/`relationships:manage`/
  `relationships:verify` specifically before showing/allowing the relevant UI, mirroring
  how troop600.com's own frontend presumably gates its own Relationships page.

## Answer

**Token acquisition — manual paste.** `admin.html` cannot mint or capture a compatible
token itself (it's a standalone static file, different origin, no backend). Blair copies
the JWT out of his own logged-in troop600.com session's localStorage (via DevTools) and
pastes it into `admin.html` once per expiry cycle — mirroring the existing manual-paste
pattern already used for the GWS Apps Script URL.

Fact-checked and ruled out: hosting `admin.html` on a future troop600.com subdomain
would **not** change this. `localStorage` is scoped per-origin; subdomains are distinct
origins and don't share it. troop600.com's auth (per
[01-jwt-origin](01-jwt-origin.md)) is a pure `localStorage` + `Authorization: Bearer`
pattern with no shared cookie in play, so subdomain co-location buys nothing unless
troopOS's own backend later switches to a `Domain=.troop600.com` cookie session — a
troopOS-side decision outside this effort's control.

**Storage — new localStorage key `scoutsight_troopos_jwt`**, same settings-panel input
pattern as `GWS_URL_KEY`. `admin.html` decodes the JWT client-side (base64 payload, no
crypto needed) on load and checks `exp`, proactively showing "token expired, please
re-paste" rather than failing opaquely on the first API call.

**Permission gating — coarse, two-tier, not per-action.** `relationships:read` gates
whether the Relationships tab shows at all; `relationships:manage` OR
`relationships:verify` gates whether write actions (drag-and-drop assign/remove, etc.)
are enabled. Rationale: this is a single-admin tool (Blair pastes his own JWT), so
fine-grained per-action RBAC is speculative complexity for a threat model that doesn't
exist here. Fine-grained gating is deferred to a follow-up ticket rather than built now.
