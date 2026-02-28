# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Purpose

ScoutSight is a Java tool for analyzing and planning BSA (Boy Scouts of America) rank advancement for Troop 0600B. It reads advancement data exported from the BSA's Internet Advancement / Scoutbook system and produces output to help leaders plan which requirements each Scout still needs to complete.

This is a Maven-based rewrite/rename of the earlier **ScoutRankPlanning** project.

## Build System

This project uses **Maven** with Java 17. Maven may not be on the system PATH — use IntelliJ's built-in Maven tool window instead.

- **Compile**: Run `mvn compile` via IntelliJ Maven or `./mvnw compile` if a wrapper is present
- **Compiled output**: `target/classes/` (classes + resources)

### Primary Runnable Artifact

The canonical runnable is the fat JAR built by IntelliJ's artifact configuration:

```
out/artifacts/ScoutSight_jar/ScoutSight.jar
```

**Main-Class** (CLI): `org.troop600.scoutsight.cli.Main`
**GUI main class**: `org.troop600.scoutsight.gui.GuiMain`

**Run CLI** (from project root, where `inputdata/` lives):
```bash
java -jar out/artifacts/ScoutSight_jar/ScoutSight.jar inputdata/<filename>.csv
java -jar out/artifacts/ScoutSight_jar/ScoutSight.jar inputdata/<filename>.csv "" Parsons
java -jar out/artifacts/ScoutSight_jar/ScoutSight.jar <advCsv> <scoutsCsv> <campStem> <rosterCsv>
```

CLI argument order: `advancementCsv [scoutsCsv [campName [rosterReportCsv]]]`
Empty string (`""`) for any argument is treated as absent/null.

If no CSV argument is given, the CLI picks the most recently modified `*_Advancement_*.csv` in `inputdata/`.

**Run GUI**: launch `org.troop600.scoutsight.gui.GuiMain` via IntelliJ run configuration.

### Java Runtime Note

The IntelliJ artifact builder may compile with a newer JDK than the system `java` on PATH (Java 17). If `java -jar ScoutSight.jar` fails with **"Unsupported class file major version 69"**, use the Java 25 JDK directly:

```bash
"/c/Users/blair/.jdks/openjdk-25/bin/java" -jar out/artifacts/ScoutSight_jar/ScoutSight.jar ...
```

`mvn exec:java` avoids this issue — it always uses JAVA_HOME.

## Directory Structure

| Path | Purpose |
|------|---------|
| `src/main/java/` | Java source root — package `org.troop600.scoutsight` |
| `camp-scheduler/` | Vite + React source for camp schedule picker; `npm run build` outputs to resources |
| `src/main/resources/static/camp_scheduler/` | Committed React bundle (`main.js`); rebuilt via `npm run build` in `camp-scheduler/` |
| `src/main/resources/templates/` | Thymeleaf HTML templates (index, scout, patrol_balancing, eagle_mb_summary, eagle_mb_detail, trail_to_first_class, help, _header) |
| `src/main/resources/templates/_*_help.html` | Partial templates (six files) included by help.html; `meetings_aggregate.html` exists but is an unused stub replaced by trail_to_first_class |
| `src/main/resources/config/` | Config files: camps, definitions (rank/MB), mb-categories, requirement-categories |
| `src/main/resources/META-INF/MANIFEST.MF` | JAR manifest (Main-Class: cli.Main) |
| `target/classes/` | Maven compile output (classes + resources) |
| `out/artifacts/ScoutSight_jar/` | IntelliJ-built fat JAR |
| `inputdata/` | BSA advancement CSV exports (not in resources — must be in working directory) |
| `output/` | Generated HTML reports (written to working directory at runtime) |

## Package Structure

```
org.troop600.scoutsight
├── cli/        Main.java — CLI entry point
├── gui/        GuiMain.java — Swing GUI entry point
├── html/       Report generation (HtmlGenerator, *PageWriter, ResourceIO, ThymeleafRenderer, loaders)
├── model/      Scout, AdvancementItem, Requirement, RawRow, AdvancementType
├── parser/     CSV parsers (AdvancementParser, ScoutRosterParser, RosterReportParser, etc.)
├── tools/      Standalone developer utilities (ScheduleImporter — one-time ETL tools)
└── util/       DateUtil
```

## Resource Loading

`ResourceIO` uses a **filesystem-first, classpath-fallback** strategy:
- Checks relative path on the filesystem (e.g. `config/camps/camp_parsons.json`) first
- Falls back to classpath resource (e.g. `/config/camps/camp_parsons.json`) — works from JAR or `target/classes/`
- This means `config/` and `templates/` are loaded from the JAR automatically; `inputdata/` and `output/` must exist in the working directory

## Input Data Format

Input CSVs are exported from BSA's Internet Advancement system. Naming convention: `Troop<number>_Advancement_<YYYYMMDD>.csv`.

**Columns**: `BSA Member ID, First Name, Middle Name, Last Name, Advancement Type, Advancement, Version, Date Completed, Approved, Awarded, Marked Completed By, ...`

**Advancement Type** values:
- `Rank` — `Advancement` column values are exact: `"Scout Rank"`, `"Tenderfoot Rank"`,
  `"Second Class Rank"`, `"First Class Rank"`, `"Star Scout Rank"`, `"Life Scout Rank"`,
  `"Eagle Scout Rank"`. Note: Star and Life include "Scout" in the name — never "Star Rank"
  or "Life Rank". Any `RANK_ORDER` array in React must use these exact strings.
- `Merit Badges` — individual merit badge records
- `Awards` — Eagle Palms, religious emblems, etc.
- `<Requirement Set> Requirements` — individual sub-requirements

A requirement is complete when `Date Completed` is non-empty (`MM/DD/YYYY` format).

## Key Domain Concepts

- **Rank sequence**: Scout → Tenderfoot → Second Class → First Class → Star → Life → Eagle
- **Eagle-required merit badges**: specific subset required for Eagle rank
- **Eagle Palms**: earned in sets of 5 extra merit badges (Bronze/Gold/Silver cycling)
- Scout identity key: `BSA Member ID`
- Optional roster CSVs enrich Scout records with patrol, school grade, join date, birth year, gender, positions

## Thymeleaf Template Authoring

`ThymeleafRenderer` runs in **TEXT mode** (not HTML mode). This affects all templates:

- **Variable output**: `[(${varName})]` (unescaped) or `[[${varName}]]` (escaped)
- **Conditionals**: `[# th:if="${flag}"]...content...[/]`
- Standard `th:` attribute syntax (e.g. `th:text`, `th:if`) does **not** work
- `siteHeader` is injected automatically by `ThymeleafRenderer.render()` — do not pass it in the variables map
- `hasPatrolPage` boolean flag in `ThymeleafRenderer` controls whether the Patrol Balancing nav link appears; set via `setHasPatrolPage()` before any renders
- `hasSchedulerPage` boolean flag controls the Camp Scheduler nav link; same pattern — add a `static boolean` + setter in `ThymeleafRenderer`, set it in `HtmlGenerator.generate()` **before** any page writes, and add `[# th:if="${flagName}"]...[/]` to `_header.html`

## Package Visibility

Most classes in `html/` are **package-private** by default (`CampConfig`, `CampConfigLoader`,
`EagleSlotsLoader`, `JsonBuilder`, `ResourceIO`, etc.). Classes accessed from outside `html/`
(e.g. from `tools/`) must be explicitly made `public`. `JsonBuilder` is package-private and
cannot be used from other packages — write JSON directly with `StringBuilder` instead.

## Camp Schedule System

Each camp JSON in `config/camps/` may include `scheduleUrl` and `scheduleParser` fields.
A separate `camp_{stem}_schedule.json` holds the parsed schedule data (committed to repo,
bundled in JAR). The template file has `"dailyClasses": []` until the importer is run.

**Run the schedule importer** (from project root, after `mvn compile`):
```bash
# Dump raw PDF text to verify layout:
JAVA_HOME="/c/Users/blair/.jdks/openjdk-25" \
  "/c/Program Files/JetBrains/IntelliJ IDEA 2025.2.4/plugins/maven/lib/maven3/bin/mvn" exec:java \
  -Dexec.mainClass="org.troop600.scoutsight.tools.ScheduleImporter" \
  -Dexec.args="--dump-text --local-pdf C:/tmp/schedule.pdf parsons"

# Parse and write camp_parsons_schedule.json (auto-downloads from scheduleUrl):
  -Dexec.args="parsons"
```
The importer writes to `src/main/resources/config/camps/` when run from project root.
`CampParsonsScheduleParser` uses `Loader.loadPDF()` (PDFBox 3.x API; `PDDocument.load()`
was removed in 3.x). `setSortByPosition(true)` is critical for correct table extraction.

**Camp stem gotcha:** The GUI dropdown passes full filename stems (e.g., `camp_parsons`);
the CLI conventionally passes short stems (e.g., `parsons`). `HtmlGenerator` normalizes via
`campFileStem` (strips leading `camp_` prefix) before constructing schedule file paths.
Any new code that constructs `config/camps/camp_<stem>_*.json` paths must use `campFileStem`,
not the raw `campName` parameter. Also: always exclude `_schedule` files when listing
camp configs (they match the same needle but are not valid `CampConfig` JSON).

**Schedule JSON schema:**
- `dailyClasses` entries have `"meritBadges": [...]` AND `"ranks": [...]`; exactly one is
  non-empty. Merit badge entries have `["Art MB"]`; rank entries have `["Scout Rank", "Tenderfoot Rank"]`.
  Co-taught badges share one entry, e.g. `["Art MB", "Animation MB"]`.
- `freeTimeClasses` entries: `{"meritBadges": [...], "day": "Monday", "time": "3:45"}` —
  one entry per day offering; each session is independent (Scout attends any one day).
- `meritBadges` list in `camp_parsons.json` acts as an allowlist for both badge and
  free-time parsing. `RANK_CLASSES` map in `CampParsonsScheduleParser` is hardcoded:
  Scout & Tenderfoot 9–10, Second Class 10–11, First Class 11–12.

**PDF parsing gotcha:** `setSortByPosition(true)` can emit a time-only line (e.g.
`"10:00 – 11:00"`) *before* the rank/badge name it belongs to when table columns are
misaligned. `parseText()` pre-processes lines: if `firstTimeRangeStart == 0`, the line is
removed and its time text is appended to the next untimed line (forward-merge).
The bare word "Scout" in the PDF is a spurious column-header artifact — intentionally
absent from `RANK_CLASSES`.

## Camp Scheduler React App

`camp-scheduler/` — Vite + React source for the camp schedule picker frontend.

**Build:** `cd camp-scheduler && npm run build`
- Outputs `src/main/resources/static/camp_scheduler/main.js` (committed build artifact, ~172KB)
- IIFE format (not ESM) — required for `file://` URL compatibility; CSS auto-inlined into main.js (~192KB)
- After any React change, rebuild and then rebuild the IntelliJ fat JAR to bundle the updated asset

**State coupling:** Scout card green/checkmark is controlled by `doneScouts` (a `Set<memberId>`), separate from
`selections`. Operations that programmatically populate `selections` (e.g. file upload) must also call
`setDoneScouts(new Set(Object.keys(newSels)))` or uploaded scouts show counts but not the green/done state.

**File I/O time format:** `scheduleIO.js` uses plain ASCII hyphen `-` between times (e.g. `9:00-10:30`) in
JSON/CSV files. Display components (MorningClassGrid, TroopGrid, ClassCard) use en-dash `–` for UI only.
These must stay consistent — import matching in `resolveMorning` compares the same formatted string.

**Runtime:** `CampSchedulerPageWriter` generates `camp_scheduler.html` with all scout/schedule
data embedded as `window.SCOUT_SIGHT_DATA` at Java report-generation time. No server needed.

**Dev mock data:** `camp-scheduler/src/dev/mockData.js` — used when `window.SCOUT_SIGHT_DATA`
is undefined (i.e., when running via `npm run dev` against `index.html`).

**Static assets in the build output:** `vite.config.js` has `emptyOutDir: true`, which wipes
`src/main/resources/static/camp_scheduler/` before every build. Static files (e.g. `privacy.html`)
must live in `camp-scheduler/public/` — Vite copies `public/` into outDir AFTER the empty step,
so they survive. Never write static files directly into the resources output dir.

**Google Sheets Apps Script — GET only:** Apps Script web apps redirect POST requests in a way
that browsers do not forward the request body through. All `sheetsIO.js` calls use GET with
URL-encoded params to avoid this. Do not convert them to POST.

**`deviceId` uses `sessionStorage`:** Each browser tab needs its own lock identity; `sessionStorage`
gives per-tab stability. `localStorage` would be shared across tabs on the same machine, breaking
the lock system.

## Maven Dependencies

- `org.thymeleaf:thymeleaf:3.1.3.RELEASE` — HTML report generation
- `org.slf4j:slf4j-nop:1.7.36` — silence Thymeleaf logging
- `org.apache.pdfbox:pdfbox:3.0.3` — PDF text extraction for schedule importer
