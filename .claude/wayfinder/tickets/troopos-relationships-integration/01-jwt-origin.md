---
title: Is troop600.com's JWT Google-issued or backend-minted?
label: wayfinder:task
status: closed
assignee: claude-session-2026-09-15
blocked_by: []
parent: ../../maps/troopos-relationships-integration.md
---

## Question

troop600.com's admin frontend reads `localStorage.getItem("jwt")` and sends it as
`Authorization: Bearer <jwt>` on every private API call. Determine whether that JWT is
a raw Google-issued id token (meaning `admin.html` could obtain a compatible token via
its own independent "Sign in with Google" client-side flow), or a token troop600.com's
own backend mints after verifying a Google OAuth handshake (meaning `admin.html` would
need to go through troop600.com's real login flow/endpoint to get a usable token).

Answering this needs a live, logged-in troop600.com session (Blair) to observe the
actual login network call and inspect the resulting JWT's header/issuer claims —
reading credential contents live was blocked by the auto-mode classifier during
charting (see parent map's Notes), so this needs to happen with Blair driving or
explicitly authorizing the inspection step, e.g. via the browser's own DevTools rather
than automated credential extraction.

Record: JWT issuer (`iss` claim), whether it's decodable as a standard Google id_token
(`accounts.google.com` issuer, Google's public key signature) vs. a custom-issued token,
and what network request(s) fire during login (to identify if there's a
troop600.com-hosted token-exchange endpoint).

## Resolution

Decoded live from Blair's own session (2026-09-15), via his own DevTools — Claude's
attempt to read `localStorage.jwt` programmatically was blocked by Claude Code's
credential-materialization guard, so this was resolved HITL as the ticket anticipated.

**`iss` and `aud` are both `"troopos:troop600"`** — not `accounts.google.com`. This is a
**troopOS-backend-minted JWT**, issued after a Google OAuth login, not a raw Google id
token. Confirmed structurally:

- Standard claims present: `iss`, `aud`, `sub`, `exp`, `iat`, `nbf`.
- Custom claims: `troopId`, `userType`, a flat `permissions: string[]` (RBAC — includes,
  among many others, `relationships:read`, `relationships:manage`,
  `relationships:verify`, `users:read`/`write`/`assign-roles`/`invite`, `roles:manage`,
  `gws:read`/`write`/`move-ou`, `scouts:read`/`write`), `linkedScoutIds: string[]`, and a
  `user` field holding the full user record as a JSON-encoded string (id, Google `sub`,
  email, name, avatar, phone, userType, roleIds, a `scout` sub-object including
  `bsaMemberId`, personal email, timestamps).
- The login flow itself is a full-page redirect through `/login` → Google → back; no
  distinct token-exchange network call was observable client-side (Chrome's own
  cross-origin navigation clears request tracking), but the claim shapes above make the
  backend-minting conclusion unambiguous regardless.

**Answer:** `admin.html` cannot mint a compatible token via its own independent
client-side "Sign in with Google" — troop600.com's API requires the
`troopos:troop600`-issued JWT specifically. Any auth-gate design
([04-auth-gate-design](04-auth-gate-design.md)) must go through troop600.com's real
login mechanism (or an equivalent backend-minting step) rather than a standalone OAuth
client flow.

**Bonus finding for [02-user-id-mapping](02-user-id-mapping.md):** the embedded `user`
object already pairs a troopOS internal `id` with `email` and `scout.bsaMemberId` for
the current user. If `/private/tables/users` records carry the same shape, ticket 02's
email/BSA-Member-ID → troopOS-id mapping question may already be answered by the shape
of that response — worth checking first before assuming further investigation is
needed. Also: the `permissions` array is a real per-user RBAC list (not just role
names) — any auth-gate design should check for `relationships:read`/`manage`/`verify`
specifically, not just "is logged in."
