package org.troop600.scoutsight.html;

import org.troop600.scoutsight.model.AdvancementItem;
import org.troop600.scoutsight.model.Scout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Writes one {@code eagle_mb_detail_<badge>.html} file per Eagle-required merit badge.
 *
 * <p>Each page shows every leaf sub-requirement for that badge as a table row, with
 * columns for Total and each Scout rank (Scout → Life), showing how many scouts who
 * have not yet completed the badge still need each individual requirement.
 *
 * <p>Only scouts who have <em>not</em> completed the merit badge appear in the counts.
 *
 * @return a map of badge name → output filename, for use by the summary page to add links.
 */
class EagleMBDetailPageWriter {

    private static final List<String> RANK_ORDER = List.of(
            "Scout Rank", "Tenderfoot Rank", "Second Class Rank", "First Class Rank",
            "Star Scout Rank", "Life Scout Rank", "Eagle Scout Rank"
    );

    private static final List<String> SUMMARY_RANKS = List.of(
            "Scout Rank", "Tenderfoot Rank", "Second Class Rank", "First Class Rank",
            "Star Scout Rank", "Life Scout Rank"
    );

    private static final Map<String, String> RANK_SHORT = Map.of(
            "Scout Rank",        "Scout",
            "Tenderfoot Rank",   "Tenderfoot",
            "Second Class Rank", "2nd Class",
            "First Class Rank",  "1st Class",
            "Star Scout Rank",   "Star",
            "Life Scout Rank",   "Life",
            "Eagle Scout Rank",  "Eagle"
    );

    /**
     * Generates one detail page per badge found in both {@code eagleSlots} and
     * {@code mbDefs}. Pages are written into {@code outputDir}.
     *
     * @return map of badge name (e.g. "First Aid MB") → output filename
     *         (e.g. "eagle_mb_detail_first_aid.html")
     */
    static Map<String, String> write(List<Scout> scouts, List<EagleSlot> eagleSlots,
                                     Map<String, Map<String, String>> mbDefs,
                                     Path outputDir) throws IOException {
        Map<String, String> badgeLinks = new LinkedHashMap<>();

        for (EagleSlot slot : eagleSlots) {
            for (String badgeName : slot.badgeNames()) {
                Map<String, String> reqs = mbDefs.get(badgeName);
                if (reqs == null || reqs.isEmpty()) {
                    System.err.println("WARN: No MB definitions found for '" + badgeName + "' — skipping detail page.");
                    continue;
                }
                String filename = badgeToFilename(badgeName);
                String json = buildJson(scouts, slot, badgeName, reqs);
                String html = ThymeleafRenderer.render("eagle_mb_detail", Map.of("detailJson", json));
                Files.writeString(outputDir.resolve(filename), html);
                badgeLinks.put(badgeName, filename);
            }
        }

        return badgeLinks;
    }

    /**
     * Converts a badge name to a safe output filename.
     * "First Aid MB" → "eagle_mb_detail_first_aid.html"
     */
    static String badgeToFilename(String badgeName) {
        String base = badgeName.endsWith(" MB")
                ? badgeName.substring(0, badgeName.length() - 3)
                : badgeName;
        String slug = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_")
                          .replaceAll("^_|_$", "");
        return "eagle_mb_detail_" + slug + ".html";
    }

    // -------------------------------------------------------------------------

    private static String buildJson(List<Scout> scouts, EagleSlot slot,
                                    String badgeName, Map<String, String> allReqs) {
        // Only include scouts who have NOT completed this badge
        List<Scout> needScouts = scouts.stream()
                .filter(s -> s.meritBadges.stream()
                        .filter(mb -> mb.name.equals(badgeName))
                        .noneMatch(AdvancementItem::isComplete))
                .toList();

        // Determine leaf requirement keys (skip parent grouping rows)
        Set<String> allKeys = allReqs.keySet();
        List<String> leafKeys = allKeys.stream()
                .filter(k -> !MBDefinitionsParser.isParentRequirement(k, allKeys))
                .sorted(MBDefinitionsParser::compareReqIds)
                .toList();

        JsonBuilder jb = new JsonBuilder();
        jb.obj()
          .field("badgeName", badgeName)
          .field("slotNum", slot.num())
          .field("slotLabel", slot.label())
          .field("totalNeedScouts", needScouts.size())
          .arr("requirements");

        for (String reqId : leafKeys) {
            String reqText = allReqs.get(reqId);

            int totalNeed = 0;
            List<String> totalNeedNames = new ArrayList<>();

            Map<String, Integer> byRankCounts = new LinkedHashMap<>();
            Map<String, List<String>> byRankNames = new LinkedHashMap<>();
            for (String r : SUMMARY_RANKS) {
                byRankCounts.put(r, 0);
                byRankNames.put(r, new ArrayList<>());
            }

            for (Scout scout : needScouts) {
                // Find the scout's MB record for this badge (may be absent if never started)
                Optional<AdvancementItem> mbItem = scout.meritBadges.stream()
                        .filter(mb -> mb.name.equals(badgeName))
                        .findFirst();

                boolean reqDone = mbItem.isPresent() && mbItem.get().requirements.stream()
                        .anyMatch(r -> r.requirementId.equals(reqId) && r.isComplete());

                if (!reqDone) {
                    totalNeed++;
                    totalNeedNames.add(scout.displayName());
                    String rankName = currentRank(scout);
                    if (byRankCounts.containsKey(rankName)) {
                        byRankCounts.put(rankName, byRankCounts.get(rankName) + 1);
                        byRankNames.get(rankName).add(scout.displayName());
                    }
                }
            }

            jb.obj()
              .field("reqId", reqId)
              .field("reqText", reqText)
              .field("totalNeed", totalNeed)
              .strArr("totalNeedScouts", totalNeedNames);

            jb.arr("byRank");
            for (String rankName : SUMMARY_RANKS) {
                jb.obj()
                  .field("rank", RANK_SHORT.get(rankName))
                  .field("need", byRankCounts.get(rankName))
                  .strArr("scouts", byRankNames.get(rankName))
                  .endObj();
            }
            jb.endArr();

            jb.endObj();
        }

        jb.endArr().endObj();
        return jb.toString();
    }

    /** Returns the highest completed rank name for this scout, defaulting to "Scout Rank". */
    private static String currentRank(Scout scout) {
        String current = "Scout Rank";
        for (String rankName : RANK_ORDER) {
            boolean completed = scout.ranks.stream()
                    .anyMatch(r -> r.name.equals(rankName) && r.isComplete());
            if (completed) current = rankName;
        }
        return current;
    }
}
