package org.troop600.scoutsight.html;

import java.util.Map;
import java.util.Set;

/**
 * Holds a named requirement category and the rank requirement IDs it contains.
 * Requirement IDs are stored lowercase for case-insensitive matching.
 */
class RequirementCategory {

    final String categoryName;
    /** Maps rank name (e.g. "Tenderfoot Rank") to the set of requirement IDs (lowercase) in this category. */
    final Map<String, Set<String>> rankRequirements;

    RequirementCategory(String categoryName, Map<String, Set<String>> rankRequirements) {
        this.categoryName = categoryName;
        this.rankRequirements = rankRequirements;
    }
}
