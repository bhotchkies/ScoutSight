package org.troop600.scoutsight.html;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Holds a summer camp's name, the rank requirement IDs it covers, and the merit badges it offers.
 * Requirement IDs are stored lowercase for case-insensitive matching.
 */
class CampConfig {

    final String campName;
    /** Maps rank name (e.g. "Scout Rank") to the set of requirement IDs (lowercase) covered at this camp. */
    final Map<String, Set<String>> rankCoverage;
    /** Merit badge names (with " MB" suffix) offered at this camp. */
    final List<String> meritBadges;

    CampConfig(String campName, Map<String, Set<String>> rankCoverage, List<String> meritBadges) {
        this.campName = campName;
        this.rankCoverage = rankCoverage;
        this.meritBadges = meritBadges;
    }
}
