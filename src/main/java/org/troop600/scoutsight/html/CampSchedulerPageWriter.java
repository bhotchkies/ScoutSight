package org.troop600.scoutsight.html;

import org.troop600.scoutsight.model.AdvancementItem;
import org.troop600.scoutsight.model.Requirement;
import org.troop600.scoutsight.model.Scout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generates {@code camp_scheduler.html} — a self-contained, client-side-only React application
 * that lets each Scout select their camp class schedule.
 *
 * <p>All data (scouts, schedule, camp config) is embedded as {@code window.SCOUT_SIGHT_DATA}
 * directly in the HTML at generation time.  No server is required at runtime; the file can be
 * opened directly from the filesystem.
 *
 * <p>The React bundle ({@code main.js}) must be built from {@code camp-scheduler/} via
 * {@code npm run build} and is committed under
 * {@code src/main/resources/static/camp_scheduler/}.
 */
class CampSchedulerPageWriter {

    private static final Path BUNDLE_PATH = Path.of("static", "camp_scheduler", "main.js");

    /**
     * Generates the camp scheduler page.
     *
     * @param scouts          all scouts parsed from the advancement CSV
     * @param campConfig      camp configuration (rankCoverage, meritBadges list)
     * @param scheduleJsonPath path to the pre-built {@code camp_*_schedule.json} file;
     *                         its content is embedded verbatim into the data bundle
     * @param outputDir       directory where report files are written (e.g. {@code output/stem/})
     */
    static void write(List<Scout> scouts, CampConfig campConfig,
                      Path scheduleJsonPath, Path outputDir,
                      List<String> eagleBadges,
                      LinkedHashMap<String, List<String[]>> rankDefs,
                      Map<String, Map<String, String>> mbDefs) throws IOException {
        if (!ResourceIO.exists(BUNDLE_PATH)) {
            System.err.println("Warning: React bundle not found at " + BUNDLE_PATH
                    + ". Run `npm run build` in camp-scheduler/ to generate it.");
            return;
        }

        String rawScheduleJson = ResourceIO.readString(scheduleJsonPath);
        String dataJson = buildDataJson(scouts, campConfig, rawScheduleJson, eagleBadges, rankDefs, mbDefs);
        String html = buildHtml(campConfig.campName, dataJson);

        Files.writeString(outputDir.resolve("camp_scheduler.html"), html);

        // Copy the React bundle alongside the HTML.
        Path assetDir = outputDir.resolve("camp_scheduler");
        Files.createDirectories(assetDir);
        Files.writeString(assetDir.resolve("main.js"), ResourceIO.readString(BUNDLE_PATH));
    }

    // -------------------------------------------------------------------------
    // HTML generation

    private static String buildHtml(String campName, String dataJson) {
        return "<!DOCTYPE html>\n"
             + "<html lang=\"en\">\n"
             + "<head>\n"
             + "  <meta charset=\"UTF-8\">\n"
             + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
             + "  <title>" + escapeHtml(campName) + " — Schedule Picker</title>\n"
             // Google Fonts: loaded from CDN when online; system fonts are used when offline.
             + "  <link rel=\"preconnect\" href=\"https://fonts.googleapis.com\">\n"
             + "  <link rel=\"preconnect\" href=\"https://fonts.gstatic.com\" crossorigin>\n"
             + "  <link href=\"https://fonts.googleapis.com/css2?family=Barlow+Condensed:"
             + "wght@400;600;700&family=Source+Serif+4:ital,wght@0,300;0,400;0,600;1,400"
             + "&display=swap\" rel=\"stylesheet\">\n"
             + "</head>\n"
             + "<body>\n"
             + "  <div id=\"root\"></div>\n"
             + "  <script>\n"
             + "    window.SCOUT_SIGHT_DATA = " + dataJson + ";\n"
             + "  </script>\n"
             // IIFE bundle: no type="module" needed, works with file:// URLs.
             + "  <script src=\"camp_scheduler/main.js\"></script>\n"
             + "</body>\n"
             + "</html>\n";
    }

    // -------------------------------------------------------------------------
    // JSON data bundle

    private static String buildDataJson(List<Scout> scouts, CampConfig campConfig,
                                        String rawScheduleJson, List<String> eagleBadges,
                                        LinkedHashMap<String, List<String[]>> rankDefs,
                                        Map<String, Map<String, String>> mbDefs) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");

        // eagleRequiredBadges array
        sb.append("  \"eagleRequiredBadges\": [");
        for (int i = 0; i < eagleBadges.size(); i++) {
            sb.append("\"").append(escapeJson(eagleBadges.get(i))).append("\"");
            if (i < eagleBadges.size() - 1) sb.append(", ");
        }
        sb.append("],\n");

        // scouts array
        sb.append("  \"scouts\": [\n");
        for (int i = 0; i < scouts.size(); i++) {
            appendScoutJson(sb, scouts.get(i), campConfig, rankDefs, mbDefs);
            if (i < scouts.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");

        // campSchedule — embed the pre-built JSON verbatim
        sb.append("  \"campSchedule\": ").append(rawScheduleJson.strip()).append(",\n");

        // campConfig
        sb.append("  \"campConfig\": {\n");
        sb.append("    \"campName\": \"").append(escapeJson(campConfig.campName)).append("\",\n");
        sb.append("    \"meritBadges\": [");
        List<String> mbs = campConfig.meritBadges;
        for (int i = 0; i < mbs.size(); i++) {
            sb.append("\"").append(escapeJson(mbs.get(i))).append("\"");
            if (i < mbs.size() - 1) sb.append(", ");
        }
        sb.append("],\n");
        sb.append("    \"rankCoverage\": {");
        List<Map.Entry<String, Set<String>>> entries = new ArrayList<>(campConfig.rankCoverage.entrySet());
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<String, Set<String>> e = entries.get(i);
            sb.append("\"").append(escapeJson(e.getKey())).append("\": [");
            List<String> reqs = new ArrayList<>(e.getValue());
            Collections.sort(reqs);
            for (int j = 0; j < reqs.size(); j++) {
                sb.append("\"").append(escapeJson(reqs.get(j))).append("\"");
                if (j < reqs.size() - 1) sb.append(", ");
            }
            sb.append("]");
            if (i < entries.size() - 1) sb.append(", ");
        }
        sb.append("}\n");
        sb.append("  }\n");
        sb.append("}");
        return sb.toString();
    }

    private static void appendScoutJson(StringBuilder sb, Scout scout, CampConfig campConfig,
                                        LinkedHashMap<String, List<String[]>> rankDefs,
                                        Map<String, Map<String, String>> mbDefs) {
        sb.append("    {\n");
        sb.append("      \"memberId\": \"").append(escapeJson(scout.bsaMemberId)).append("\",\n");
        sb.append("      \"name\": \"").append(escapeJson(scout.displayName())).append("\",\n");
        sb.append("      \"patrol\": \"")
          .append(escapeJson(scout.patrol != null ? scout.patrol : "")).append("\",\n");

        // completedMeritBadges
        appendStringArray(sb, "completedMeritBadges", scout.meritBadges.stream()
                .filter(AdvancementItem::isComplete)
                .map(item -> item.name)
                .toList());

        // partialMeritBadges: started (≥1 requirement done) but not complete
        appendStringArray(sb, "partialMeritBadges", scout.meritBadges.stream()
                .filter(item -> !item.isComplete())
                .filter(item -> item.requirements.stream().anyMatch(r -> r.dateCompleted != null))
                .map(item -> item.name)
                .toList());

        // completedRanks
        appendStringArray(sb, "completedRanks", scout.ranks.stream()
                .filter(AdvancementItem::isComplete)
                .map(item -> item.name)
                .toList());

        // campRankProgress: for each rank in rankCoverage, count done/total among covered reqs
        // Includes a "remaining" array of {id, text} for camp reqs not yet completed.
        sb.append("      \"campRankProgress\": {");
        List<Map.Entry<String, Set<String>>> rankEntries =
                new ArrayList<>(campConfig.rankCoverage.entrySet());
        for (int i = 0; i < rankEntries.size(); i++) {
            Map.Entry<String, Set<String>> e = rankEntries.get(i);
            String rankName = e.getKey();
            Set<String> campReqs = e.getValue(); // already lowercase
            AdvancementItem rankItem = findByName(scout.ranks, rankName);

            int total = campReqs.size();
            int done  = 0;
            Set<String> completedReqIds = new HashSet<>();
            if (rankItem != null) {
                for (Requirement req : rankItem.requirements) {
                    if (req.requirementId != null && req.dateCompleted != null) {
                        completedReqIds.add(req.requirementId.toLowerCase());
                        if (campReqs.contains(req.requirementId.toLowerCase())) done++;
                    }
                }
            }

            // Remaining: camp reqs not completed, in definition order
            List<String[]> allRankReqDefs = rankDefs.getOrDefault(rankName, List.of());
            List<String[]> remaining = allRankReqDefs.stream()
                    .filter(pair -> campReqs.contains(pair[0].toLowerCase())
                                 && !completedReqIds.contains(pair[0].toLowerCase()))
                    .collect(Collectors.toList());

            sb.append("\"").append(escapeJson(rankName)).append("\": {")
              .append("\"done\": ").append(done)
              .append(", \"total\": ").append(total)
              .append(", \"remaining\": [");
            for (int j = 0; j < remaining.size(); j++) {
                sb.append("{\"id\": \"").append(escapeJson(remaining.get(j)[0])).append("\"")
                  .append(", \"text\": \"").append(escapeJson(remaining.get(j)[1])).append("\"}");
                if (j < remaining.size() - 1) sb.append(", ");
            }
            sb.append("]}");
            if (i < rankEntries.size() - 1) sb.append(", ");
        }
        sb.append("},\n"); // comma — meritBadgeProgress follows

        // meritBadgeProgress: done/total/remaining for each partial MB
        sb.append("      \"meritBadgeProgress\": {");
        List<AdvancementItem> partialMBItems = scout.meritBadges.stream()
                .filter(item -> !item.isComplete())
                .filter(item -> item.requirements.stream().anyMatch(r -> r.dateCompleted != null))
                .collect(Collectors.toList());
        for (int i = 0; i < partialMBItems.size(); i++) {
            AdvancementItem mbItem = partialMBItems.get(i);
            Map<String, String> mbReqDefs = mbDefs.getOrDefault(mbItem.name, Map.of());
            Set<String> completedMBReqs = new HashSet<>();
            for (Requirement req : mbItem.requirements) {
                if (req.requirementId != null && req.dateCompleted != null) {
                    completedMBReqs.add(req.requirementId.toLowerCase());
                }
            }
            int mbTotal = mbReqDefs.size();
            int mbDone = (int) mbReqDefs.keySet().stream()
                    .filter(k -> completedMBReqs.contains(k.toLowerCase()))
                    .count();
            List<Map.Entry<String, String>> mbRemaining = new ArrayList<>(mbReqDefs.entrySet())
                    .stream()
                    .filter(ent -> !completedMBReqs.contains(ent.getKey().toLowerCase()))
                    .collect(Collectors.toList());

            sb.append("\"").append(escapeJson(mbItem.name)).append("\": {")
              .append("\"done\": ").append(mbDone)
              .append(", \"total\": ").append(mbTotal)
              .append(", \"remaining\": [");
            for (int j = 0; j < mbRemaining.size(); j++) {
                sb.append("{\"id\": \"").append(escapeJson(mbRemaining.get(j).getKey())).append("\"")
                  .append(", \"text\": \"").append(escapeJson(mbRemaining.get(j).getValue())).append("\"}");
                if (j < mbRemaining.size() - 1) sb.append(", ");
            }
            sb.append("]}");
            if (i < partialMBItems.size() - 1) sb.append(", ");
        }
        sb.append("}\n"); // last field in scout object
        sb.append("    }");
    }

    // -------------------------------------------------------------------------
    // Helpers

    private static void appendStringArray(StringBuilder sb, String key, List<String> values) {
        sb.append("      \"").append(key).append("\": [");
        for (int i = 0; i < values.size(); i++) {
            sb.append("\"").append(escapeJson(values.get(i))).append("\"");
            if (i < values.size() - 1) sb.append(", ");
        }
        sb.append("],\n");
    }

    private static AdvancementItem findByName(List<AdvancementItem> items, String name) {
        return items.stream().filter(i -> name.equals(i.name)).findFirst().orElse(null);
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
