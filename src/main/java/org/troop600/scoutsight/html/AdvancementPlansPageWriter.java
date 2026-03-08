package org.troop600.scoutsight.html;

import org.troop600.scoutsight.model.AdvancementItem;
import org.troop600.scoutsight.model.Scout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Builds and writes {@code output/<stem>/advancement_plans.html} —
 * a printable advancement plan for each scout showing incomplete ranks
 * (defaulting to those >50% complete) with blank space to record a plan.
 */
class AdvancementPlansPageWriter {

    /** Ranks for which camp coverage and requirement categories apply. */
    private static final Set<String> CAMP_RANK_NAMES = Set.of(
            "Scout Rank", "Tenderfoot Rank", "Second Class Rank", "First Class Rank"
    );

    /** Short rank labels for scouts who get the Eagle MB plan section. */
    private static final Set<String> STAR_PLUS_LABELS = Set.of("Star", "Life", "Eagle");

    /** Maps a RequirementCategory's categoryName to its JSON key (mirrors TrailToFirstClassPageWriter). */
    private static final Map<String, String> CAT_KEY = Map.of(
            "Done on Camping Trips",  "camping_trip",
            "Planning Required",      "planning_required",
            "Good for Troop Meeting", "troop_meeting"
    );

    static void write(List<Scout> scouts,
                      LinkedHashMap<String, List<String[]>> rankDefsOrdered,
                      List<RequirementCategory> categories,
                      List<CampConfig> camps,
                      List<EagleSlot> eagleSlots,
                      Path outputDir,
                      String stem) throws IOException {
        String json = buildJson(scouts, rankDefsOrdered, categories, camps, eagleSlots, stem);
        String html = ThymeleafRenderer.render("advancement_plans", Map.of("plansData", json));
        Files.writeString(outputDir.resolve("advancement_plans.html"), html);
    }

    // -------------------------------------------------------------------------

    private static String buildJson(List<Scout> scouts,
                                    LinkedHashMap<String, List<String[]>> rankDefsOrdered,
                                    List<RequirementCategory> categories,
                                    List<CampConfig> camps,
                                    List<EagleSlot> eagleSlots,
                                    String stem) {
        // Camp union: rank → set of covered req IDs (lowercase)
        Map<String, Set<String>> campUnion = new HashMap<>();
        for (CampConfig camp : camps) {
            for (Map.Entry<String, Set<String>> e : camp.rankCoverage.entrySet()) {
                campUnion.computeIfAbsent(e.getKey(), k -> new HashSet<>()).addAll(e.getValue());
            }
        }
        boolean hasCampConfig = !camps.isEmpty();

        // Category lookup: rank → (category key → set of req IDs lowercase)
        Map<String, Map<String, Set<String>>> rankCatReqs = new HashMap<>();
        for (RequirementCategory cat : categories) {
            String catKey = CAT_KEY.get(cat.categoryName);
            if (catKey == null) continue;
            for (Map.Entry<String, Set<String>> e : cat.rankRequirements.entrySet()) {
                rankCatReqs.computeIfAbsent(e.getKey(), k -> new HashMap<>())
                           .put(catKey, e.getValue());
            }
        }

        TreeSet<String> patrolSet    = new TreeSet<>();
        TreeSet<String> birthYearSet = new TreeSet<>();
        TreeSet<String> joinYearSet  = new TreeSet<>();

        for (Scout s : scouts) {
            if (s.patrol    != null && !s.patrol.isBlank())    patrolSet.add(s.patrol);
            if (s.birthYear != null && !s.birthYear.isBlank()) birthYearSet.add(s.birthYear);
            if (s.joinYear  != null && !s.joinYear.isBlank())  joinYearSet.add(s.joinYear);
        }

        JsonBuilder jb = new JsonBuilder();
        jb.obj()
          .field("title", stem)
          .strArr("patrols",    new ArrayList<>(patrolSet))
          .strArr("birthYears", new ArrayList<>(birthYearSet))
          .strArr("joinYears",  new ArrayList<>(joinYearSet))
          .arr("scouts");

        for (Scout scout : scouts) {
            jb.obj()
              .field("memberId",    scout.bsaMemberId)
              .field("name",        scout.displayName())
              .field("grade",       scout.schoolGrade)
              .field("patrol",      scout.patrol    != null ? scout.patrol    : "")
              .field("birthYear",   scout.birthYear != null ? scout.birthYear : "")
              .field("joinYear",    scout.joinYear  != null ? scout.joinYear  : "")
              .field("currentRank", IndexPageWriter.currentRankShort(scout))
              .arr("plans");

            for (int i = 0; i < IndexPageWriter.RANK_NAMES.length; i++) {
                String rankName  = IndexPageWriter.RANK_NAMES[i];
                String rankLabel = IndexPageWriter.RANK_LABELS[i];

                AdvancementItem item = findRank(scout, rankName);

                // Skip ranks the scout has already completed
                if (item != null && item.isComplete()) continue;

                // Calculate completion percentage
                int pctComplete = 0;
                if (item != null && !item.requirements.isEmpty()) {
                    long done  = item.requirements.stream()
                            .filter(r -> r.dateCompleted != null).count();
                    int  total = item.requirements.size();
                    pctComplete = (int) Math.round(100.0 * done / total);
                }

                // Collect IDs the scout has already completed for this rank
                Set<String> completedIds = item == null ? Set.of()
                        : item.requirements.stream()
                                .filter(r -> r.dateCompleted != null && r.requirementId != null)
                                .map(r -> r.requirementId.trim())
                                .collect(Collectors.toSet());

                // Category and camp-coverage lookups for this rank
                boolean isCampRank  = CAMP_RANK_NAMES.contains(rankName);
                Set<String> campCovered = isCampRank
                        ? campUnion.getOrDefault(rankName, Set.of()) : Set.of();
                Map<String, Set<String>> catReqs = rankCatReqs.getOrDefault(rankName, Map.of());

                // Remaining reqs = all defined reqs minus completed ones (definition order)
                List<String[]> allDefined = rankDefsOrdered.getOrDefault(rankName, List.of());
                List<String[]> remaining  = allDefined.stream()
                        .filter(pair -> !completedIds.contains(pair[0].trim()))
                        .toList();

                if (remaining.isEmpty()) continue; // nothing left to do for this rank

                jb.obj()
                  .field("rankName",    rankName)
                  .field("rankLabel",   rankLabel)
                  .field("pctComplete", pctComplete)
                  .arr("remainingReqs");

                for (String[] pair : remaining) {
                    String reqId      = pair[0];
                    String reqIdLower = reqId.toLowerCase();

                    // Build category list for this requirement
                    List<String> cats = new ArrayList<>();
                    if (isCampRank && hasCampConfig && !campCovered.contains(reqIdLower))
                        cats.add("cant_earn_at_camp");
                    for (Map.Entry<String, Set<String>> e : catReqs.entrySet()) {
                        if (e.getValue().contains(reqIdLower)) cats.add(e.getKey());
                    }

                    jb.obj()
                      .field("id",   reqId)
                      .field("text", pair[1])
                      .strArr("categories", cats)
                      .endObj();
                }

                jb.endArr()  // remainingReqs
                  .endObj(); // rank plan entry
            }

            jb.endArr();  // plans

            if (STAR_PLUS_LABELS.contains(IndexPageWriter.currentRankShort(scout))) {
                appendEagleMBPlan(jb, scout, eagleSlots);
            }

            jb.endObj(); // scout
        }

        jb.endArr()  // scouts
          .endObj(); // root
        return jb.toString();
    }

    /** Appends an {@code "eagleMBPlan"} object to {@code jb} for the given Star+ scout. */
    private static void appendEagleMBPlan(JsonBuilder jb, Scout scout, List<EagleSlot> eagleSlots) {
        Set<String> allEagleNames = eagleSlots.stream()
                .flatMap(s -> s.badgeNames().stream())
                .collect(Collectors.toSet());

        long electivesEarned = scout.meritBadges.stream()
                .filter(mb -> mb.isComplete() && !allEagleNames.contains(mb.name))
                .count();

        jb.obj("eagleMBPlan")
          .field("slotsTotal",     eagleSlots.size())
          .field("electivesEarned", (int) electivesEarned)
          .arr("incompleteSlots");

        for (EagleSlot slot : eagleSlots) {
            List<AdvancementItem> started = scout.meritBadges.stream()
                    .filter(mb -> slot.badgeNames().contains(mb.name) && isStarted(mb))
                    .toList();
            boolean complete = started.stream().anyMatch(AdvancementItem::isComplete);
            if (complete) continue;

            int bestPct = 0;
            String inProgress = null;
            for (AdvancementItem badge : started) {
                int total = badge.requirements.size();
                int done  = (int) badge.requirements.stream()
                        .filter(r -> r.dateCompleted != null).count();
                int pct = total > 0 ? (int) Math.round(100.0 * done / total) : 0;
                if (pct > bestPct) {
                    bestPct = pct;
                    // Only show the badge name for multi-choice slots (label ≠ badge name)
                    if (slot.badgeNames().size() > 1) {
                        String n = badge.name;
                        inProgress = n.endsWith(" MB") ? n.substring(0, n.length() - 3) : n;
                    }
                }
            }

            jb.obj()
              .field("slotNum",    slot.num())
              .field("slotLabel",  slot.label())
              .field("pct",        bestPct)
              .field("inProgress", inProgress != null ? inProgress : "")
              .endObj();
        }

        jb.endArr()  // incompleteSlots
          .endObj(); // eagleMBPlan
    }

    private static boolean isStarted(AdvancementItem item) {
        return item.isComplete() || item.requirements.stream().anyMatch(r -> r.dateCompleted != null);
    }

    private static AdvancementItem findRank(Scout scout, String rankName) {
        for (AdvancementItem item : scout.ranks) {
            if (rankName.equals(item.name)) return item;
        }
        return null;
    }
}
