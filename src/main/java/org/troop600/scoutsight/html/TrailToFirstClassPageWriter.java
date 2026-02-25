package org.troop600.scoutsight.html;

import org.troop600.scoutsight.model.AdvancementItem;
import org.troop600.scoutsight.model.Requirement;
import org.troop600.scoutsight.model.Scout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds and writes {@code output/<stem>/trail_to_first_class.html} —
 * a full listing of every Scout-through-First-Class requirement showing
 * how many scouts still need each one, filterable by requirement category
 * and by scout demographics.
 */
class TrailToFirstClassPageWriter {

    private static final String[] TRAIL_RANK_NAMES = {
        "Scout Rank", "Tenderfoot Rank", "Second Class Rank", "First Class Rank"
    };

    private static final Map<String, String> RANK_DISPLAY = Map.of(
            "Scout Rank",        "Scout",
            "Tenderfoot Rank",   "Tenderfoot",
            "Second Class Rank", "2nd Class",
            "First Class Rank",  "1st Class"
    );

    /** Maps a RequirementCategory's categoryName to its JSON key. */
    private static final Map<String, String> CAT_KEY = Map.of(
            "Done on Camping Trips", "camping_trip",
            "Planning Required",     "planning_required",
            "Good for Troop Meeting","troop_meeting"
    );

    static void write(List<Scout> scouts, List<CampConfig> camps,
                      List<RequirementCategory> categories,
                      LinkedHashMap<String, List<String[]>> rankDefs,
                      Path outputDir, String stem) throws IOException {
        String json = buildJson(scouts, camps, categories, rankDefs, stem);
        String html = ThymeleafRenderer.render("trail_to_first_class", Map.of("ttfcJson", json));
        Files.writeString(outputDir.resolve("trail_to_first_class.html"), html);
    }

    // -------------------------------------------------------------------------

    private record ScoutRef(String name, String patrol, int grade,
                             String birthYear, String joinYear, String rank) {}

    private static ScoutRef scoutRef(Scout scout) {
        return new ScoutRef(
                scout.displayName(),
                scout.patrol    != null ? scout.patrol    : "",
                scout.schoolGrade,
                scout.birthYear != null ? scout.birthYear : "",
                scout.joinYear  != null ? scout.joinYear  : "",
                IndexPageWriter.currentRankShort(scout)
        );
    }

    private static void appendScoutRef(JsonBuilder jb, ScoutRef ref) {
        jb.obj()
          .field("name",      ref.name())
          .field("patrol",    ref.patrol())
          .field("grade",     ref.grade())
          .field("birthYear", ref.birthYear())
          .field("joinYear",  ref.joinYear())
          .field("rank",      ref.rank())
          .endObj();
    }

    private static String buildJson(List<Scout> scouts, List<CampConfig> camps,
                                    List<RequirementCategory> categories,
                                    LinkedHashMap<String, List<String[]>> rankDefs,
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

        // Filter option collections
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
            if (s.birthYear != null || s.patrol != null)       hasRosterData = true;
        }
        List<String> gradeOptions = gradeSet.stream().map(String::valueOf).collect(Collectors.toList());

        final List<String> RANK_LABEL_ORDER = List.of(
                "Scout", "Tenderfoot", "2nd Class", "1st Class", "Star", "Life", "Eagle");
        List<String> rankOptions = RANK_LABEL_ORDER.stream()
                .filter(r -> scouts.stream().anyMatch(s -> IndexPageWriter.currentRankShort(s).equals(r)))
                .collect(Collectors.toList());

        JsonBuilder jb = new JsonBuilder();
        jb.obj()
          .field("stem", stem)
          .field("hasRosterData", hasRosterData)
          .field("hasCampConfig", hasCampConfig)
          .strArr("patrols", new ArrayList<>(patrolSet))
          .strArr("gradeOptions", gradeOptions)
          .strArr("birthYears", new ArrayList<>(birthYearSet))
          .strArr("joinYears", new ArrayList<>(joinYearSet))
          .strArr("rankOptions", rankOptions)
          .arr("ranks");

        for (String rankName : TRAIL_RANK_NAMES) {
            List<String[]> rankReqs = rankDefs.get(rankName);
            if (rankReqs == null || rankReqs.isEmpty()) continue;

            Set<String> campCovered = campUnion.getOrDefault(rankName, Set.of());
            Map<String, Set<String>> catReqs = rankCatReqs.getOrDefault(rankName, Map.of());

            jb.obj().field("rankName", RANK_DISPLAY.get(rankName)).arr("requirements");

            for (String[] req : rankReqs) {
                String reqId      = req[0];
                String reqText    = req[1];
                String reqIdLower = reqId.toLowerCase();

                // Determine which category filters this requirement belongs to
                List<String> cats = new ArrayList<>();
                if (hasCampConfig && !campCovered.contains(reqIdLower)) cats.add("cant_earn_at_camp");
                for (Map.Entry<String, Set<String>> e : catReqs.entrySet()) {
                    if (e.getValue().contains(reqIdLower)) cats.add(e.getKey());
                }

                // Collect scouts who haven't completed this requirement
                List<ScoutRef> needing = new ArrayList<>();
                for (Scout scout : scouts) {
                    AdvancementItem rankItem = findRank(scout, rankName);
                    if (rankItem == null) continue;
                    if (rankItem.isComplete()) continue; // rank earned → all reqs done
                    Requirement r = rankItem.requirements.stream()
                            .filter(rq -> reqId.equalsIgnoreCase(rq.requirementId))
                            .findFirst().orElse(null);
                    if (r == null || r.dateCompleted == null) needing.add(scoutRef(scout));
                }

                if (needing.isEmpty()) continue; // all scouts done — no row needed

                jb.obj()
                  .field("id", reqId)
                  .field("text", reqText)
                  .strArr("categories", cats);
                jb.arr("scouts");
                for (ScoutRef ref : needing) appendScoutRef(jb, ref);
                jb.endArr().endObj();
            }

            jb.endArr().endObj();
        }

        jb.endArr().endObj();
        return jb.toString();
    }

    private static AdvancementItem findRank(Scout scout, String rankName) {
        for (AdvancementItem item : scout.ranks) {
            if (rankName.equals(item.name)) return item;
        }
        return null;
    }
}
