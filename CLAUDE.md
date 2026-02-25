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

## Directory Structure

| Path | Purpose |
|------|---------|
| `src/main/java/` | Java source root — package `org.troop600.scoutsight` |
| `src/main/resources/templates/` | Thymeleaf HTML templates (index, scout, meetings_aggregate, eagle_mb_summary, eagle_mb_detail, trail_to_first_class, help, _header) |
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
- `Rank` — Scout, Tenderfoot, Second Class, First Class, Star, Life, Eagle Scout
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

## Maven Dependencies

- `org.thymeleaf:thymeleaf:3.1.3.RELEASE` — HTML report generation
- `org.slf4j:slf4j-nop:1.7.36` — silence Thymeleaf logging
