package org.troop600.scoutsight.tools;

import org.troop600.scoutsight.html.CampConfig;
import org.troop600.scoutsight.html.CampConfigLoader;
import org.troop600.scoutsight.html.CampSchedule;
import org.troop600.scoutsight.parser.CampParsonsScheduleParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;

/**
 * One-time developer tool: downloads the camp advancement schedule PDF and converts it
 * to a JSON file that {@code CampScheduleLoader} can read at report-generation time.
 *
 * <h2>Usage (from project root)</h2>
 * <pre>
 * # Dump raw PDF text to inspect layout before calibrating the parser regex:
 * mvn exec:java -Dexec.mainClass="org.troop600.scoutsight.tools.ScheduleImporter" \
 *     -Dexec.args="--dump-text --local-pdf C:/tmp/parsons_schedule.pdf parsons"
 *
 * # Parse a locally cached PDF and write camp_parsons_schedule.json:
 * mvn exec:java -Dexec.mainClass="org.troop600.scoutsight.tools.ScheduleImporter" \
 *     -Dexec.args="--local-pdf C:/tmp/parsons_schedule.pdf parsons"
 *
 * # Download from scheduleUrl and parse:
 * mvn exec:java -Dexec.mainClass="org.troop600.scoutsight.tools.ScheduleImporter" \
 *     -Dexec.args="parsons"
 * </pre>
 *
 * <p>The camp name argument (e.g. {@code parsons}) is matched against config files in
 * {@code config/camps/} the same way the main CLI resolves camp names.
 *
 * <p>Output is written to {@code src/main/resources/config/camps/camp_<stem>_schedule.json}
 * when that path exists (i.e. running from the project root in development), or to
 * {@code config/camps/camp_<stem>_schedule.json} in the working directory otherwise.
 */
public class ScheduleImporter {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
        }

        boolean dumpText = false;
        Path    localPdf = null;
        String  campStem = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--dump-text" -> dumpText = true;
                case "--local-pdf" -> { i++; localPdf = Path.of(args[i]); }
                default            -> campStem = args[i];
            }
        }

        if (campStem == null) {
            System.err.println("ERROR: camp name argument is required.");
            printUsage();
            System.exit(1);
        }

        // 1. Load camp config to find scheduleUrl and scheduleParser.
        CampConfig config = loadCampConfig(campStem);
        if (config.scheduleParser == null || config.scheduleParser.isEmpty()) {
            System.err.println("ERROR: camp config for '" + campStem + "' has no scheduleParser.");
            System.exit(1);
        }

        // 2. Obtain the PDF (local file or download).
        Path pdfPath;
        if (localPdf != null) {
            pdfPath = localPdf;
        } else {
            if (config.scheduleUrl == null || config.scheduleUrl.isEmpty()) {
                System.err.println("ERROR: camp config for '" + campStem
                        + "' has no scheduleUrl and --local-pdf was not given.");
                System.exit(1);
            }
            pdfPath = downloadPdf(config.scheduleUrl, campStem);
        }

        // 3. Instantiate the correct parser from the symbolic scheduleParser name.
        CampParsonsScheduleParser parser = resolveParser(config.scheduleParser);

        // 4. Dump text or parse-and-write.
        if (dumpText) {
            parser.dumpText(pdfPath);
        } else {
            CampSchedule schedule = parser.parse(pdfPath, config.meritBadges);
            Path outPath = resolveOutputPath(campStem);
            writeScheduleJson(schedule, config.scheduleUrl != null ? config.scheduleUrl : "", outPath);
            System.out.println("Schedule written to: " + outPath.toAbsolutePath());
            System.out.println("Daily classes parsed: " + schedule.dailyClasses.size());
            long rankClassCount = schedule.dailyClasses.stream()
                    .filter(dc -> !dc.ranks().isEmpty()).count();
            System.out.println("Rank skill classes parsed: " + rankClassCount);
            System.out.println("Free-time classes parsed: " + schedule.freeTimeClasses.size());
        }
    }

    // -------------------------------------------------------------------------
    // Parser registry

    /**
     * Maps the {@code scheduleParser} string from camp config to a concrete parser.
     * Add a new {@code case} here whenever a new camp PDF format is supported.
     */
    private static CampParsonsScheduleParser resolveParser(String parserName) {
        return switch (parserName) {
            case "CampParsonsScheduleParser" -> new CampParsonsScheduleParser();
            default -> throw new IllegalArgumentException(
                    "Unknown scheduleParser: '" + parserName
                    + "'. Add a case in ScheduleImporter.resolveParser().");
        };
    }

    // -------------------------------------------------------------------------
    // Camp config loading

    private static CampConfig loadCampConfig(String campStem) throws IOException {
        String needle = campStem.toLowerCase();

        // Try the source-tree location first (running from project root in IDE/Maven).
        Path srcDir = Path.of("src", "main", "resources", "config", "camps");
        if (Files.isDirectory(srcDir)) {
            try (var stream = Files.list(srcDir)) {
                List<Path> matches = stream
                        .filter(p -> {
                            String n = p.getFileName().toString().toLowerCase();
                            return n.endsWith(".json")
                                    && n.contains(needle)
                                    && !n.contains("_schedule");
                        })
                        .toList();
                if (!matches.isEmpty()) {
                    return CampConfigLoader.load(matches.get(0));
                }
            }
        }

        // Fallback: let CampConfigLoader use ResourceIO's classpath lookup.
        // Construct the conventional filename: camp_<stem>.json
        return CampConfigLoader.load(Path.of("config", "camps", "camp_" + needle + ".json"));
    }

    // -------------------------------------------------------------------------
    // PDF download

    private static Path downloadPdf(String url, String campStem) throws Exception {
        Path tmp = Path.of(System.getProperty("java.io.tmpdir"),
                "scoutsight_" + campStem + "_schedule.pdf");
        System.out.println("Downloading PDF from: " + url);
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "ScoutSight/1.1 (schedule-importer)")
                .build();
        HttpResponse<InputStream> response =
                client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " fetching " + url);
        }
        try (InputStream in = response.body()) {
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        }
        System.out.println("PDF saved to: " + tmp);
        return tmp;
    }

    // -------------------------------------------------------------------------
    // Output path resolution

    private static Path resolveOutputPath(String campStem) {
        String fileName = "camp_" + campStem.toLowerCase() + "_schedule.json";
        // Prefer writing into the source tree so the file gets bundled on the next build.
        Path srcPath = Path.of("src", "main", "resources", "config", "camps", fileName);
        if (Files.isDirectory(srcPath.getParent())) return srcPath;
        // Fallback: write to config/camps/ in the working directory.
        return Path.of("config", "camps", fileName);
    }

    // -------------------------------------------------------------------------
    // JSON serialization (written directly to avoid depending on package-private JsonBuilder)

    private static void writeScheduleJson(CampSchedule schedule, String sourceUrl,
                                          Path outPath) throws IOException {
        String today = LocalDate.now().toString();
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"campName\": \"").append(escapeJson(schedule.campName)).append("\",\n");
        sb.append("  \"generatedFrom\": \"").append(escapeJson(sourceUrl)).append("\",\n");
        sb.append("  \"generatedDate\": \"").append(today).append("\",\n");
        sb.append("  \"dailyClasses\": [");

        List<CampSchedule.DailyClass> classes = schedule.dailyClasses;
        for (int i = 0; i < classes.size(); i++) {
            CampSchedule.DailyClass dc = classes.get(i);
            sb.append("\n    {\n");
            sb.append("      \"meritBadges\": [");
            List<String> mbs = dc.meritBadges();
            for (int k = 0; k < mbs.size(); k++) {
                sb.append("\"").append(escapeJson(mbs.get(k))).append("\"");
                if (k < mbs.size() - 1) sb.append(", ");
            }
            sb.append("],\n");
            sb.append("      \"ranks\": [");
            List<String> ranks = dc.ranks();
            for (int k = 0; k < ranks.size(); k++) {
                sb.append("\"").append(escapeJson(ranks.get(k))).append("\"");
                if (k < ranks.size() - 1) sb.append(", ");
            }
            sb.append("],\n");
            sb.append("      \"sessions\": [");

            List<CampSchedule.TimeSlot> sessions = dc.sessions();
            for (int j = 0; j < sessions.size(); j++) {
                CampSchedule.TimeSlot ts = sessions.get(j);
                sb.append("\n        {\"start\": \"").append(escapeJson(ts.start()))
                  .append("\", \"end\": \"").append(escapeJson(ts.end())).append("\"}");
                if (j < sessions.size() - 1) sb.append(",");
            }

            sb.append("\n      ]\n    }");
            if (i < classes.size() - 1) sb.append(",");
        }

        sb.append("\n  ],\n  \"freeTimeClasses\": [");
        List<CampSchedule.FreeTimeClass> ftClasses = schedule.freeTimeClasses;
        for (int i = 0; i < ftClasses.size(); i++) {
            CampSchedule.FreeTimeClass ft = ftClasses.get(i);
            sb.append("\n    {\"meritBadges\": [");
            List<String> ftMbs = ft.meritBadges();
            for (int k = 0; k < ftMbs.size(); k++) {
                sb.append("\"").append(escapeJson(ftMbs.get(k))).append("\"");
                if (k < ftMbs.size() - 1) sb.append(", ");
            }
            sb.append("], \"day\": \"").append(escapeJson(ft.day())).append("\"");
            sb.append(", \"time\": \"").append(escapeJson(ft.time())).append("\"}");
            if (i < ftClasses.size() - 1) sb.append(",");
        }
        sb.append("\n  ]\n}\n");

        Files.createDirectories(outPath.getParent());
        Files.writeString(outPath, sb.toString());
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static void printUsage() {
        System.err.println("Usage: ScheduleImporter [--dump-text] [--local-pdf <file>] <campStem>");
        System.err.println("  campStem       e.g. 'parsons'");
        System.err.println("  --dump-text    print raw PDF text to stdout; do not write JSON");
        System.err.println("  --local-pdf    use a local PDF instead of downloading from scheduleUrl");
    }
}
