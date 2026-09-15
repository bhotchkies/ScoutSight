# troopOS Admin — Roles & Relationships APIs

> **Status: reverse-engineered from reading code, not tested against the live API.**
> Derived by inspecting the built frontend bundle for `/admin/users` and `/admin/roles`
> (`index-DLkZsg8R.js`) on 2026-09-15. No public API docs exist for this app. Every
> endpoint, payload shape, and behavior below is inferred from client-side source —
> nothing here has been exercised against the real backend, so treat request/response
> shapes as best-effort until confirmed by an actual call.

**Base URL:** same origin (`https://troop600.com`), no `/api` prefix. Endpoints are
`/private/...`, plus `/public/site-config`.

**Auth:** `Authorization: Bearer <jwt>` header, JWT read from `localStorage.getItem("jwt")`.
If the token is expired, the client redirects to login instead of sending the request.
All private requests use `Content-Type: application/json`, `mode: "cors"`.

## 1. User role assignment (per-user roles, not the Roles table)

Editing a user via Users → Edit (User type dropdown + role checkboxes) does not call a
role-specific endpoint — it PUTs the whole user record.

- **Get all users:** `GET /private/tables/users` — returns full user array, no
  query params/filtering (client filters/searches locally).
- **Save role changes:** `PUT /private/tables/users`
  Body: the full user object with updated fields:
  - `roleIds`: array of role-id strings (from the checkboxes)
  - `userType`: `"scout" | "adult"` (from the dropdown)
  - `pendingTypeAssignment`: set to `false` once a type is chosen, otherwise left as-is
  - all other existing user fields spread through unchanged

Note in the UI: role changes require the user to log out/in before they take effect
(roles are presumably baked into the JWT).

## 2. Roles table (role definitions — Admin → Roles page)

This is a separate CRUD table of role definitions (name, description, bundled
permissions), distinct from the `roleIds` users carry.

- **Get all roles:** `GET /private/tables/roles`
- **Create role:** `POST /private/tables/roles`
  Body: `{ troopId, roleId, name, description, permissions: string[], system: boolean }`
  If `roleId` is left blank, the client generates one from the name:
  `name.toLowerCase().replace(/\s+/g, "-")`.
- **Update role:** `PUT /private/tables/roles`
  Body: the full, edited role object (same shape as above).
- **Delete role:** `DELETE /private/tables/roles`
  Body: `{ roleId }`
  Blocked client-side (with a `confirm()` prompt) for non-system roles; roles with
  `system: true` can't be deleted at all.

The available permissions options come from `GET /public/site-config`
(`siteConfig.permissions`, grouped by a `group` field) — not from
`/private/tables/roles` itself.

**Bug found:** `/admin/roles` currently crashes on load —
`TypeError: Cannot read properties of null (reading 'slice')` — reproduced from a clean
navigation, not caused by any interaction. Worth checking what's null (likely something
in `siteConfig.permissions` or the roles response) before relying on this page.

## 3. Relationships (parent/guardian ↔ scout links)

Table key is `relationshipId`, which is a composite string: `${adultId}#${scoutId}`.

- **Get all relationships:** `GET /private/tables/relationships`
  Fetched once for the whole page on load, and again on-demand each time a user's
  Relationships modal is opened (then filtered client-side to rows where `adultId` or
  `scoutId` matches that user).
- **Create relationship:** `POST /private/tables/relationships`
  Body:
  ```json
  {
    "troopId": "...",
    "relationshipId": "${adultId}#${scoutId}",
    "adultId": "...",
    "scoutId": "...",
    "kind": "parent | guardian | leader-mentor",
    "verified": false
  }
  ```
  (`verified` defaults to checked/true in the Add form.)

  Client-side guardrails before submit: adult and scout must both be picked, must be
  different users, and the `relationshipId` must not already exist in the
  currently-loaded list.
- **Verify relationship:** `PUT /private/tables/relationships`
  Body: the existing relationship object with `verified` forced to `true`
  (`{ ...relationship, verified: true }`).
- **Remove relationship:** `DELETE /private/tables/relationships`
  Body: `{ relationshipId }`

## How it all works (generic table client)

The whole admin app is driven by one config array of tables (route + Redux slice name +
id key), e.g.:

```
{ route: "private/tables/users",         name: "users",         key: "id" }
{ route: "private/tables/roles",         name: "roles",         key: "roleId" }
{ route: "private/tables/relationships", name: "relationships", key: "relationshipId" }
... (calendar, announcements, dynamic-pages, merit-badges, photo-albums, documents,
     videos, home-page-sections, and public/* read-only variants)
```

Every table gets the same four generic actions, all going through one low-level request
function (`Mt(route, method, payload)` → `fetch('/' + route, { method, headers:
{ Content-Type, Authorization }, body: JSON.stringify(payload) })`):

- **fetch-all** → `GET /{route}` (no filtering/pagination — always the whole table)
- **add** → `POST /{route}`
- **update** → `PUT /{route}`
- **delete** → `DELETE /{route}` with just the id field in the body

Writes are queued client-side (an offline queue that coalesces and retries), then flushed
through `Mt` in order. A 401 or a 403 alongside an expired JWT triggers logout; other
non-OK responses throw using the JSON body's `error` field if present.

This generic pattern is why roles and relationships don't have bespoke REST verbs/paths
beyond the shapes above — every table in the app (calendar, announcements, documents,
etc.) follows the identical GET/POST/PUT/DELETE convention on `/private/tables/<name>`.
