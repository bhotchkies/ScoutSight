package org.troop600.scoutsight.html;

import org.troop600.scoutsight.model.AdvancementItem;
import org.troop600.scoutsight.model.Scout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Builds and writes {@code output/<stem>/index.html} — the all-scouts summary table.
 */
class IndexPageWriter {

    static final String[] RANK_NAMES = {
        "Scout Rank", "Tenderfoot Rank", "Second Class Rank", "First Class Rank",
        "Star Scout Rank", "Life Scout Rank", "Eagle Scout Rank"
    };

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    static void write(List<Scout> scouts, Path outputDir, String stem) throws IOException {
        String json = buildJson(scouts, stem);
        String html = ThymeleafRenderer.render("index", Map.of("title", stem, "scoutData", json));
        Files.writeString(outputDir.resolve("index.html"), html);
    }

    // -------------------------------------------------------------------------

    private static String buildJson(List<Scout> scouts, String stem) {
        // Collect sorted unique values for filter dropdowns
        TreeSet<String> patrolSet    = new TreeSet<>();
        TreeSet<String> birthYearSet = new TreeSet<>();
        TreeSet<String> joinYearSet  = new TreeSet<>();
        boolean hasRosterData = false;
        for (Scout s : scouts) {
            if (s.patrol != null && !s.patrol.isBlank()) patrolSet.add(s.patrol);
            if (s.birthYear != null && !s.birthYear.isBlank()) birthYearSet.add(s.birthYear);
            if (s.joinYear != null  && !s.joinYear.isBlank())  joinYearSet.add(s.joinYear);
            if (s.birthYear != null || s.patrol != null) hasRosterData = true;
        }

        JsonBuilder jb = new JsonBuilder();
        jb.obj()
          .field("title", stem)
          .field("hasRosterData", hasRosterData)
          .strArr("patrols", new ArrayList<>(patrolSet))
          .strArr("birthYears", new ArrayList<>(birthYearSet))
          .strArr("joinYears", new ArrayList<>(joinYearSet))
          .arr("scouts");

        for (Scout scout : scouts) {
            jb.obj()
              .field("memberId", scout.bsaMemberId)
              .field("name", scout.displayName())
              .field("grade", scout.schoolGrade)
              .field("patrol", scout.patrol != null ? scout.patrol : "")
              .field("birthYear", scout.birthYear != null ? scout.birthYear : "")
              .field("joinYear", scout.joinYear != null ? scout.joinYear : "")
              .field("currentRank", currentRankShort(scout))
              .obj("ranks");

            for (String rankName : RANK_NAMES) {
                AdvancementItem item = findRank(scout, rankName);
                if (item == null) continue;

                jb.obj(rankName);
                if (item.isComplete()) {
                    jb.field("completed", true)
                      .field("dateCompleted", item.dateCompleted.format(DATE_FMT));
                } else {
                    int total = item.requirements.size();
                    int done = (int) item.requirements.stream()
                            .filter(r -> r.dateCompleted != null)
                            .count();
                    int pct = total == 0 ? 0 : (int) Math.round(100.0 * done / total);
                    jb.field("completed", false)
                      .field("pctComplete", pct);
                }
                jb.endObj();
            }

            jb.endObj()   // end ranks
              .endObj();  // end scout object
        }

        jb.endArr()  // end scouts
          .endObj(); // end root
        return jb.toString();
    }

    private static AdvancementItem findRank(Scout scout, String rankName) {
        for (AdvancementItem item : scout.ranks) {
            if (rankName.equals(item.name)) return item;
        }
        return null;
    }

    /** Returns the short label for the scout's highest completed rank (e.g. "Star"). */
    static String currentRankShort(Scout scout) {
        String current = "Scout";
        for (int i = 0; i < RANK_NAMES.length; i++) {
            for (AdvancementItem item : scout.ranks) {
                if (item.name.equals(RANK_NAMES[i]) && item.isComplete()) {
                    current = RANK_LABELS[i];
                }
            }
        }
        return current;
    }

    static final String[] RANK_LABELS = {
        "Scout", "Tenderfoot", "2nd Class", "1st Class", "Star", "Life", "Eagle"
    };
}
