package org.troop600.scoutsight.parser;

/**
 * Data joined from a Roster Report CSV for a single Scout.
 *
 * @param patrol      Patrol name (empty string if absent)
 * @param schoolGrade School grade as integer (-1 if absent/unparseable)
 * @param joinYear    Year joined as 4-digit string, e.g. "2024" (empty if absent)
 * @param birthYear   Year of birth as 4-digit string, e.g. "2013" (empty if absent)
 * @param schoolInfo  School name (empty string if absent)
 * @param gender      Gender code, e.g. "M" (empty string if absent)
 * @param positions   Positions/tenure string, e.g. "Patrol Leader [...] (5m 17d)" (empty if absent)
 */
public record RosterReportEntry(
        String patrol,
        int    schoolGrade,
        String joinYear,
        String birthYear,
        String schoolInfo,
        String gender,
        String positions
) {}
