---
title: Design fine-grained per-action RBAC gating for the Relationships tab
label: wayfinder:grilling
status: open
assignee: null
blocked_by: []
parent: ../../maps/troopos-relationships-integration.md
---

## Question

[04-auth-gate-design](04-auth-gate-design.md) settled on coarse permission gating for
`admin.html`'s Relationships tab: `relationships:read` gates the whole tab,
`relationships:manage` OR `relationships:verify` gates all write actions together. This
was an explicit deferral, not an oversight — the JWT's `permissions` RBAC array
distinguishes `manage` from `verify`, but per-action gating was ruled out of the initial
design as speculative complexity for a single-admin tool.

Revisit if/when it matters: should individual actions (e.g. "verify" a relationship vs.
"create/delete" one) be gated separately against `relationships:verify` vs.
`relationships:manage`, and what would trigger doing this work (e.g. a second admin
user with narrower permissions actually using the tool)? Not blocking; pick up only if
that need materializes.
