package org.troop600.scoutsight.html;

import org.troop600.scoutsight.model.Scout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Builds and writes {@code output/<stem>/patrol_balancing.html} — a matrix of
 * scouts by patrol vs. current rank and birth year, to help leaders assess
 * patrol balance.
 *
 * <p>Only called when at least one scout has a non-blank patrol value.
 */
class PatrolBalancingPageWriter {

    /** Rank labels in advancement order — mirrors IndexPageWriter.RANK_LABELS. */
    private static final String[] RANK_LABEL_ORDER = {
        "Scout", "Tenderfoot", "2nd Class", "1st Class", "Star", "Life", "Eagle"
    };

    static void write(List<Scout> scouts, Path outputDir, String stem) throws IOException {
        String json = buildJson(scouts, stem);
        String html = ThymeleafRenderer.render("patrol_balancing", Map.of("title", stem, "patrolData", json));
        Files.writeString(outputDir.resolve("patrol_balancing.html"), html);
    }

    // -------------------------------------------------------------------------

    private static String buildJson(List<Scout> scouts, String stem) {
        // Sorted unique patrol names (non-null, non-blank)
        TreeSet<String> patrolSet = new TreeSet<>();
        boolean hasNone    = false;
        boolean hasAgeData = false;
        for (Scout s : scouts) {
            if (s.patrol != null && !s.patrol.isBlank()) patrolSet.add(s.patrol);
            else                                          hasNone = true;
            if (s.birthYear != null && !s.birthYear.isBlank()) hasAgeData = true;
        }
        List<String> patrols = new ArrayList<>(patrolSet);

        JsonBuilder jb = new JsonBuilder();
        jb.obj()
          .field("title", stem)
          .strArr("patrols", patrols)
          .field("hasNone", hasNone)
          .field("hasAgeData", hasAgeData);

        // ── Rank rows ─────────────────────────────────────────────────────────
        jb.arr("rankRows");
        for (String rankLabel : RANK_LABEL_ORDER) {
            Map<String, List<String>> patrolToNames = new LinkedHashMap<>();
            for (String p : patrols) patrolToNames.put(p, new ArrayList<>());
            List<String> noneNames = new ArrayList<>();
            int total = 0;

            for (Scout s : scouts) {
                if (!rankLabel.equals(IndexPageWriter.currentRankShort(s))) continue;
                total++;
                if (s.patrol != null && !s.patrol.isBlank()) {
                    patrolToNames.computeIfAbsent(s.patrol, k -> new ArrayList<>())
                                 .add(s.displayName());
                } else {
                    noneNames.add(s.displayName());
                }
            }
            if (total == 0) continue;

            jb.obj()
              .field("label", rankLabel)
              .arr("cells");
            for (String p : patrols) {
                List<String> names = patrolToNames.getOrDefault(p, List.of());
                jb.obj().field("count", names.size()).strArr("names", names).endObj();
            }
            jb.endArr()  // cells
              .obj("noneCell")
                .field("count", noneNames.size())
                .strArr("names", noneNames)
              .endObj()
              .field("total", total)
              .endObj();  // rankRow
        }
        jb.endArr();  // rankRows

        // ── Age rows ──────────────────────────────────────────────────────────
        jb.arr("ageRows");
        if (hasAgeData) {
            // TreeMap gives ascending key order → oldest scouts appear first
            TreeMap<String, Map<String, List<String>>> yearMap = new TreeMap<>();
            for (Scout s : scouts) {
                if (s.birthYear == null || s.birthYear.isBlank()) continue;
                yearMap.computeIfAbsent(s.birthYear, k -> new LinkedHashMap<>());
            }
            for (Scout s : scouts) {
                if (s.birthYear == null || s.birthYear.isBlank()) continue;
                Map<String, List<String>> pm = yearMap.get(s.birthYear);
                String key = (s.patrol != null && !s.patrol.isBlank()) ? s.patrol : "__none__";
                pm.computeIfAbsent(key, k -> new ArrayList<>()).add(s.displayName());
            }

            for (Map.Entry<String, Map<String, List<String>>> e : yearMap.entrySet()) {
                Map<String, List<String>> pm  = e.getValue();
                int total = pm.values().stream().mapToInt(List::size).sum();
                if (total == 0) continue;

                jb.obj()
                  .field("label", e.getKey())
                  .arr("cells");
                for (String p : patrols) {
                    List<String> names = pm.getOrDefault(p, List.of());
                    jb.obj().field("count", names.size()).strArr("names", names).endObj();
                }
                List<String> noneNames = pm.getOrDefault("__none__", List.of());
                jb.endArr()  // cells
                  .obj("noneCell")
                    .field("count", noneNames.size())
                    .strArr("names", noneNames)
                  .endObj()
                  .field("total", total)
                  .endObj();  // ageRow
            }
        }
        jb.endArr();  // ageRows

        // ── Patrol totals ─────────────────────────────────────────────────────
        jb.obj("patrolTotals");
        for (String p : patrols) {
            long count = scouts.stream().filter(s -> p.equals(s.patrol)).count();
            jb.field(p, (int) count);
        }
        if (hasNone) {
            long noneCount = scouts.stream()
                .filter(s -> s.patrol == null || s.patrol.isBlank()).count();
            jb.field("(none)", (int) noneCount);
        }
        jb.endObj();  // patrolTotals

        jb.field("grandTotal", scouts.size());
        jb.endObj();  // root
        return jb.toString();
    }
}
