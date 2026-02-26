package org.troop600.scoutsight.html;

import org.troop600.scoutsight.model.AdvancementItem;
import org.troop600.scoutsight.model.Scout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Builds and writes {@code output/<stem>/eagle_mb_summary.html} —
 * a troop-wide summary of Eagle-required MB slots showing how many scouts
 * have not yet completed each slot and how many have partial progress,
 * broken down by each scout's current rank.
 */
class EagleMBSummaryPageWriter {

    /** Ranks included in the per-rank breakdown columns. */
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

    static void write(List<Scout> scouts, List<EagleSlot> eagleSlots,
                      List<CampConfig> camps, Map<String, String> badgeLinks,
                      Path outputDir, String stem) throws IOException {
        String json = buildJson(scouts, eagleSlots, camps, badgeLinks, stem);
        String html = ThymeleafRenderer.render("eagle_mb_summary", Map.of("summaryJson", json));
        Files.writeString(outputDir.resolve("eagle_mb_summary.html"), html);
    }

    private static String buildJson(List<Scout> scouts, List<EagleSlot> eagleSlots,
                                    List<CampConfig> camps, Map<String, String> badgeLinks,
                                    String stem) {
        java.util.Set<String> parsonsMBs = camps.stream()
                .filter(c -> c.campName.equals("Camp Parsons"))
                .findFirst()
                .map(c -> new java.util.HashSet<String>(c.meritBadges))
                .orElseGet(java.util.HashSet::new);

        // Collect filter options
        TreeSet<String>  patrolSet    = new TreeSet<>();
        TreeSet<String>  birthYearSet = new TreeSet<>();
        TreeSet<String>  joinYearSet  = new TreeSet<>();
        TreeSet<Integer> gradeSet     = new TreeSet<>();
        boolean hasRosterData = false;
        for (Scout s : scouts) {
            if (s.patrol != null && !s.patrol.isBlank())       patrolSet.add(s.patrol);
            if (s.birthYear != null && !s.birthYear.isBlank()) birthYearSet.add(s.birthYear);
            if (s.joinYear  != null && !s.joinYear.isBlank())  joinYearSet.add(s.joinYear);
            if (s.schoolGrade >= 6)                            gradeSet.add(s.schoolGrade);
            if (s.birthYear != null || s.patrol != null) hasRosterData = true;
        }
        List<String> gradeOptions = gradeSet.stream().map(String::valueOf)
                .collect(java.util.stream.Collectors.toList());

        final List<String> RANK_LABEL_ORDER = List.of(
                "Scout", "Tenderfoot", "2nd Class", "1st Class", "Star", "Life", "Eagle");
        List<String> rankOptions = RANK_LABEL_ORDER.stream()
                .filter(r -> scouts.stream().anyMatch(s -> IndexPageWriter.currentRankShort(s).equals(r)))
                .collect(java.util.stream.Collectors.toList());

        JsonBuilder jb = new JsonBuilder();
        jb.obj()
          .field("title", stem)
          .field("totalScouts", scouts.size())
          .field("hasRosterData", hasRosterData)
          .strArr("patrols", new ArrayList<>(patrolSet))
          .strArr("gradeOptions", gradeOptions)
          .strArr("birthYears", new ArrayList<>(birthYearSet))
          .strArr("joinYears", new ArrayList<>(joinYearSet))
          .strArr("rankOptions", rankOptions)
          .arr("slots");

        for (EagleSlot slot : eagleSlots) {
            List<ScoutRef> notCompleteList = new ArrayList<>();
            List<ScoutRef> partialList     = new ArrayList<>();
            Map<String, int[]>          byRankCounts       = new LinkedHashMap<>();
            Map<String, List<ScoutRef>> byRankNcList       = new LinkedHashMap<>();
            Map<String, List<ScoutRef>> byRankPartialList  = new LinkedHashMap<>();
            for (String r : SUMMARY_RANKS) {
                byRankCounts.put(r, new int[]{0, 0});
                byRankNcList.put(r, new ArrayList<>());
                byRankPartialList.put(r, new ArrayList<>());
            }

            for (Scout scout : scouts) {
                List<AdvancementItem> slotBadges = scout.meritBadges.stream()
                        .filter(item -> slot.badgeNames().contains(item.name))
                        .toList();
                boolean complete = slotBadges.stream().anyMatch(AdvancementItem::isComplete);
                if (!complete) {
                    ScoutRef si = ScoutRef.from(scout);
                    notCompleteList.add(si);
                    boolean hasPartial = slotBadges.stream()
                            .anyMatch(item -> item.requirements.stream()
                                    .anyMatch(r -> r.dateCompleted != null));
                    if (hasPartial) partialList.add(si);

                    String scoutRank = ScoutRef.currentRankLong(scout);
                    int[] counts = byRankCounts.get(scoutRank);
                    if (counts != null) {
                        counts[0]++;
                        byRankNcList.get(scoutRank).add(si);
                        if (hasPartial) {
                            counts[1]++;
                            byRankPartialList.get(scoutRank).add(si);
                        }
                    }
                }
            }

            boolean atParsons = slot.badgeNames().stream().anyMatch(parsonsMBs::contains);
            jb.obj()
              .field("slotNum",    slot.num())
              .field("slotLabel",  slot.label())
              .field("atCamp",     atParsons);

            jb.arr("notCompleteScouts");
            for (ScoutRef si : notCompleteList) ScoutRef.appendTo(jb, si);
            jb.endArr();

            jb.arr("partialScouts");
            for (ScoutRef si : partialList) ScoutRef.appendTo(jb, si);
            jb.endArr();

            // Badge links: one entry per badge name that has a detail page
            jb.arr("links");
            for (String badgeName : slot.badgeNames()) {
                String file = badgeLinks.get(badgeName);
                if (file != null) {
                    jb.obj()
                      .field("badge", badgeName)
                      .field("file", file)
                      .endObj();
                }
            }
            jb.endArr();

            jb.arr("byRank");
            for (String rankName : SUMMARY_RANKS) {
                int[] counts = byRankCounts.get(rankName);
                jb.obj()
                  .field("rank", RANK_SHORT.get(rankName));
                jb.arr("notCompleteScouts");
                for (ScoutRef si : byRankNcList.get(rankName)) ScoutRef.appendTo(jb, si);
                jb.endArr();
                jb.arr("partialScouts");
                for (ScoutRef si : byRankPartialList.get(rankName)) ScoutRef.appendTo(jb, si);
                jb.endArr();
                jb.endObj();
            }
            jb.endArr();

            jb.endObj();
        }

        jb.endArr().endObj();
        return jb.toString();
    }

}
