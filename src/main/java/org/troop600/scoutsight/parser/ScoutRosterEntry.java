package org.troop600.scoutsight.parser;

/**
 * Data joined from the troop roster CSV for a single Scout.
 *
 * @param patrol      Patrol Name (empty string if absent)
 * @param schoolGrade School Grade integer (-1 if absent/unparseable)
 * @param dateJoined  Date Joined string in MM/DD/YYYY format (empty string if absent)
 */
public record ScoutRosterEntry(String patrol, int schoolGrade, String dateJoined) {}
