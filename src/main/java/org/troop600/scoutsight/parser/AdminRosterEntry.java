package org.troop600.scoutsight.parser;

/**
 * Data joined from a BSA admin roster CSV (YOUTH MEMBERS section) for a single Scout.
 *
 * <p>A scout may belong to multiple patrols; {@link #patrol} contains all non-blank
 * patrol names joined with {@code ", "}.
 *
 * @param patrol      Patrol name(s), comma-separated if multiple (empty string if absent)
 * @param schoolGrade School grade as integer (-1 if absent/unparseable)
 * @param joinYear    4-digit year joined, e.g. {@code "2024"} (empty if absent)
 * @param dateJoined  Full join date string, e.g. {@code "05/02/2022"} (empty if absent)
 * @param birthYear   4-digit birth year, e.g. {@code "2013"} (empty if absent)
 * @param gender      Gender code, e.g. {@code "M"} (empty if absent)
 * @param schoolInfo  School name (empty if absent)
 * @param positions   Positions/tenure string, e.g. {@code "Patrol Leader [...] (5m 17d)"} (empty if absent)
 */
public record AdminRosterEntry(
        String patrol,
        int    schoolGrade,
        String joinYear,
        String dateJoined,
        String birthYear,
        String gender,
        String schoolInfo,
        String positions
) {}
