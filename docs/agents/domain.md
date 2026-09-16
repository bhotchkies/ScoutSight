# Domain Docs

**Layout: single-context.** One `CONTEXT.md` (glossary of domain terms) and one `docs/adr/` directory (architecture decision records), both at the repo root.

Neither exists yet — created lazily by the `domain-modeling` skill the first time a term or decision needs to be recorded. Don't pre-create empty placeholders.

Consumer rules:
- `CONTEXT.md` is a glossary only — no implementation details, no specs, no decision logs.
- ADRs go in `docs/adr/`, one file per decision, only for choices that are hard to reverse, surprising without context, and the result of a real trade-off.
