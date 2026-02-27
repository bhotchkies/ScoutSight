package org.troop600.scoutsight.html;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Holds a summer camp's name, the rank requirement IDs it covers, and the merit badges it offers.
 * Requirement IDs are stored lowercase for case-insensitive matching.
 */
public class CampConfig {

    public final String campName;
    /** Maps rank name (e.g. "Scout Rank") to the set of requirement IDs (lowercase) covered at this camp. */
    public final Map<String, Set<String>> rankCoverage;
    /** Merit badge names (with " MB" suffix) offered at this camp. */
    public final List<String> meritBadges;
    /** URL of the camp's advancement schedule PDF; null if not configured. */
    public final String scheduleUrl;
    /** Symbolic parser identifier (e.g. "CampParsonsScheduleParser"); null if not configured. */
    public final String scheduleParser;

    public CampConfig(String campName, Map<String, Set<String>> rankCoverage,
                      List<String> meritBadges, String scheduleUrl, String scheduleParser) {
        this.campName      = campName;
        this.rankCoverage  = rankCoverage;
        this.meritBadges   = meritBadges;
        this.scheduleUrl   = scheduleUrl;
        this.scheduleParser = scheduleParser;
    }
}
