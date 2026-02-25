package org.troop600.scoutsight.parser;

import org.troop600.scoutsight.model.RawRow;

/**
 * Derives the AdvancementType string used by requirement rows from the
 * corresponding parent row's fields — and vice versa.
 */
final class NameNormalizer {

    private NameNormalizer() {}

    /**
     * Given a parent row (Advancement Type = "Rank", "Merit Badges", or "Awards"),
     * returns the AdvancementType string that requirement rows belonging to it use.
     *
     * Examples:
     *   "Rank"         / "Eagle Scout Rank"       → "Eagle Scout Rank Requirements"
     *   "Merit Badges" / "Chess MB"               → "Chess Merit Badge Requirements"
     *   "Awards"       / "Interpreter Strip"       → "Interpreter Strip Award Requirements"
     */
    static String deriveRequirementType(RawRow parentRow) {
        return switch (parentRow.advancementType) {
            case "Rank"         -> parentRow.advancement + " Requirements";
            case "Merit Badges" -> mbNameToReqType(parentRow.advancement);
            case "Awards"       -> parentRow.advancement + " Award Requirements";
            default             -> throw new IllegalArgumentException(
                                       "Not a parent row: " + parentRow.advancementType);
        };
    }

    /**
     * Converts a merit badge Advancement name to its requirement type string.
     * "Chess MB" → "Chess Merit Badge Requirements"
     */
    private static String mbNameToReqType(String mbName) {
        if (mbName.endsWith(" MB")) {
            return mbName.substring(0, mbName.length() - 3) + " Merit Badge Requirements";
        }
        // Defensive fallback for any badge not using the " MB" suffix
        return mbName + " Merit Badge Requirements";
    }
}
