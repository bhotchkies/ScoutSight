package org.troop600.scoutsight.model;

import java.util.List;

/**
 * A single Boy Scout and all of their advancement data.
 */
public final class Scout {

    public final String bsaMemberId;
    public final String firstName;
    public final String middleName;   // may be empty string
    public final String lastName;

    public final List<AdvancementItem> ranks;
    public final List<AdvancementItem> meritBadges;
    public final List<AdvancementItem> awards;

    // Joined from roster CSV; null / empty / -1 when no roster was provided
    public String patrol;
    public int    schoolGrade = -1;
    public String dateJoined;

    // Joined from Roster Report CSV (null/empty when no report was provided)
    public String birthYear;    // 4-digit year string, e.g. "2013"
    public String joinYear;     // 4-digit year string, e.g. "2024"
    public String schoolInfo;   // school name
    public String gender;       // "M" or "F"
    public String positions;    // positions/tenure string

    public Scout(String bsaMemberId, String firstName, String middleName, String lastName,
                 List<AdvancementItem> ranks, List<AdvancementItem> meritBadges,
                 List<AdvancementItem> awards) {
        this.bsaMemberId = bsaMemberId;
        this.firstName   = firstName;
        this.middleName  = middleName;
        this.lastName    = lastName;
        this.ranks       = ranks;
        this.meritBadges = meritBadges;
        this.awards      = awards;
    }

    public String displayName() {
        return normalizeName(firstName) + " " + normalizeName(lastName);
    }

    /**
     * If a name part is entirely uppercase (e.g. "PATEL"), converts it to title case
     * ("Patel"). Mixed-case names like "MacDonald" are left unchanged.
     */
    private static String normalizeName(String part) {
        if (part == null || part.isEmpty()) return part;
        if (part.equals(part.toUpperCase()) && part.chars().anyMatch(Character::isLetter)) {
            return Character.toUpperCase(part.charAt(0)) + part.substring(1).toLowerCase();
        }
        return part;
    }
}
