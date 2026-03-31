package org.troop600.scoutsight.html;

import org.troop600.scoutsight.model.Scout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

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
        // Sorted unique patrol names (non-null, non-blank); a scout may belong to multiple patrols
        TreeSet<String> patrolSet = new TreeSet<>();
        boolean hasNone    = false;
        boolean hasAgeData = false;
        for (Scout s : scouts) {
            List<String> ps = patrolsOf(s);
            if (!ps.isEmpty()) patrolSet.addAll(ps);
            else               hasNone = true;
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
                List<String> ps = patrolsOf(s);
                if (!ps.isEmpty()) {
                    for (String p : ps)
                        patrolToNames.computeIfAbsent(p, k -> new ArrayList<>()).add(s.displayName());
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
                List<String> ps = patrolsOf(s);
                if (!ps.isEmpty()) {
                    for (String p : ps)
                        pm.computeIfAbsent(p, k -> new ArrayList<>()).add(s.displayName());
                } else {
                    pm.computeIfAbsent("__none__", k -> new ArrayList<>()).add(s.displayName());
                }
            }

            for (Map.Entry<String, Map<String, List<String>>> e : yearMap.entrySet()) {
                Map<String, List<String>> pm  = e.getValue();
                // Count unique scouts for this birth year (cell sums would double-count multi-patrol scouts)
                int total = (int) scouts.stream()
                    .filter(s -> e.getKey().equals(s.birthYear)).count();
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
            long count = scouts.stream().filter(s -> patrolsOf(s).contains(p)).count();
            jb.field(p, (int) count);
        }
        if (hasNone) {
            long noneCount = scouts.stream().filter(s -> patrolsOf(s).isEmpty()).count();
            jb.field("(none)", (int) noneCount);
        }
        jb.endObj();  // patrolTotals

        jb.field("grandTotal", scouts.size());
        jb.endObj();  // root
        return jb.toString();
    }

    /**
     * Returns the individual patrol names for a scout. A scout may belong to multiple
     * patrols stored as a comma-separated string (e.g. {@code "Ranger, Senior"}).
     * Returns an empty list if the scout has no patrol assignment.
     */
    private static List<String> patrolsOf(Scout s) {
        if (s.patrol == null || s.patrol.isBlank()) return List.of();
        return Arrays.stream(s.patrol.split(","))
                     .map(String::trim)
                     .filter(p -> !p.isEmpty())
                     .collect(Collectors.toList());
    }
}
