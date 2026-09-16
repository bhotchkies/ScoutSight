# Issue Tracker

Issues for this repo live in **GitHub Issues** on `bhotchkies/ScoutSight`, managed via the `gh` CLI.

- Create: `gh issue create --title "..." --body "..."`
- List/search: `gh issue list`, `gh issue view <number>`
- Labels: `gh issue edit <number> --add-label "..."`
- Comments: `gh issue comment <number> --body "..."`

Skills that read/write issues (`to-spec`, `to-tickets`, `wayfinder`, `triage`) should use these commands rather than local markdown files.

**PRs as a request surface:** off. GitHub PRs are not treated as part of the triage/request queue for this repo. (Flip this on later if you want PR review requests to flow through the same skills.)

**Close-out order:** update any affected documentation (CLAUDE.md, docs/, etc.) *before* pushing the change, then push, then close the ticket (`gh issue close <number>`). Docs land in the same commit/push as the code they describe; the ticket close is the last step, confirming the change is actually live.
