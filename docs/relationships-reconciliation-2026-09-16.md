# Relationships reconciliation — GWS export vs troopOS (2026-09-16)

Working notes for issue #5 (Migrate GWS-only relationship data to troopOS). Snapshot
from a dry-run on 2026-09-16 against `troop600_relationships_2026-09-15.csv` (85-row
GWS-side export). Rerun with `scripts/relationships-dryrun.mjs` against a fresh export
any time — see that script's header comment for usage.

## Result summary (85 rows)

- **55 rows** — relationship already exists in troopOS. No action needed.
- **30 rows** — adult and/or scout email doesn't resolve to any troopOS user account.
  This is the actual GWS-only list issue #5 needs a per-pair decision on.
- **48 rows** flagged with an informational `userType` note (not counted as a
  failure) — most of these are the systemic troopOS `pendingTypeAssignment` gap
  tracked separately in issue #7. Worth re-running this dry-run after #7 lands, since
  fixing `userType` may change which of the 30 actually need real reconciliation vs.
  were just misclassified.

## The 30-row list (accounts genuinely absent from troopOS)

All emails `@troop600.com`.

| Row | Adult email | Scout email | Missing side |
|---|---|---|---|
| 3 | albrightg | theodorealbright | adult |
| 8 | mahdokhtc | daniels | both |
| 12 | emilydong | yanhuac | adult |
| 13 | francisf | tomaf | scout |
| 16 | ghishanf | ranig | scout |
| 17 | ghishanl | ranig | scout |
| 22 | fabienh | augustinh | scout |
| 28 | humphreyr | vincenth | both |
| 29 | humphreyv | vincenth | scout |
| 30 | johnsona | julianj | adult |
| 35 | kraghd | hudsonk | scout |
| 36 | kraghe | wyattk | adult |
| 37 | kraghe | hudsonk | both |
| 41 | kailinl | jonathanl | adult |
| 42 | liuc | kasperl | adult |
| 43 | liuc | nickl | adult |
| 47 | messerm | hudsonm | scout |
| 48 | messers | hudsonm | both |
| 50 | almoore | evenm | scout |
| 51 | almoore | evanm | scout |
| 52 | almoore | lukem | scout |
| 53 | asmoore | evanm | scout |
| 54 | asmoore | lukem | scout |
| 57 | patelj | arinp | scout |
| 58 | patelr | arinp | scout |
| 59 | pattene | devinp | scout |
| 61 | saraoh | nihals | both |
| 62 | saraoj | nihals | scout |
| 65 | jaskarans | nihals | both |
| 80 | peijiey | yuzhed | adult |

Fuzzy-matched each missing local-part against all 197 troopOS emails — no likely
typos found; these look genuinely absent rather than misspelled, pending
confirmation from Blair on the troop600 side.

## Known troop600-side issue (Blair investigating, as of 2026-09-16)

Blair reported having found a likely root cause on the troop600 side for some/all of
the above. Not yet documented here — update this section once confirmed, and rerun
`scripts/relationships-dryrun.mjs` against a fresh GWS export + live troopOS data to
see how the 30-row list changes.

## How to rerun

```bash
# 1. Get a fresh GWS-side export (adultEmail,scoutEmail,kind,verified per row, no
#    header) — the live arExportCsv() button was removed from admin.html when the
#    Relationships tab was re-pointed to troopOS (commit 3c74461); the export logic
#    lives at commit 0be3b42 if it needs to be temporarily restored.
# 2. Paste a troopOS session token (from admin.html's token field) and run:
TROOPOS_TOKEN=<token> node scripts/relationships-dryrun.mjs path/to/export.csv
```
