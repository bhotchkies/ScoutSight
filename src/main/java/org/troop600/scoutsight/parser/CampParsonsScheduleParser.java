package org.troop600.scoutsight.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.troop600.scoutsight.html.CampSchedule;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Camp Parsons's advancement schedule PDF into a {@link CampSchedule}.
 *
 * <h2>PDF text layout (from {@code --dump-text} inspection)</h2>
 * Each daily-class row extracts as one line of the form:
 * <pre>
 *   &lt;Badge Name&gt;  [non-ASCII symbols]  &lt;difficulty&gt;  [non-ASCII symbols]  H:MM – H:MM  H:MM – H:MM ...
 * </pre>
 * Examples:
 * <pre>
 *   Canoeing 2 9:00 – 10:30 10:30 – 12:00
 *   Lifesaving 3 [symbol] [symbol] 9:00 – 10:30 10:30 – 12:00
 *   Art + Animation 1 [symbol] 9:00 – 10:00 11:00 – 12:00
 *   Shooting Archery 3 [symbol] [symbol] [symbol] 9:00 – 10:30 10:30 – 12:00
 * </pre>
 * Key observations:
 * <ul>
 *   <li>Non-ASCII note symbols (age requirements, fee notes, etc.) appear between the badge
 *       name and the time ranges; they are stripped before name extraction.</li>
 *   <li>A badge may appear with a one-word category prefix (e.g. "Shooting Archery",
 *       "Sports Rifle Shooting"). The first-word prefix-strip fallback handles these.</li>
 *   <li>Lines like "Art + Animation" teach two badges simultaneously; they are split on
 *       {@code " + "} and each part is matched independently against the known badge list.</li>
 *   <li>Rank-skill rows ("Scout & Tenderfoot", "First Class") and special entries ("Scuba",
 *       "Tower") are excluded because they don't match any known merit badge name.</li>
 *   <li>Afternoon free-time sessions ("Monday 3:45", "Tuesday 3:45") contain no
 *       {@code H:MM–H:MM} time ranges and are ignored.</li>
 *   <li>Both en-dash (–) and ASCII hyphen (-) appear as range separators.</li>
 * </ul>
 *
 * <h2>Calibration</h2>
 * Run {@link #dumpText(Path)} (via {@code ScheduleImporter --dump-text}) after the camp
 * updates their PDF to verify the text layout has not changed.
 */
public class CampParsonsScheduleParser {

    private static final String CAMP_NAME = "Camp Parsons";

    /**
     * Matches a time range: H:MM–H:MM or H:MM - H:MM (en-dash, em-dash, or hyphen).
     * Groups: 1 = start time, 2 = end time.
     */
    private static final Pattern TIME_RANGE = Pattern.compile(
            "(\\d{1,2}:\\d{2})\\s*[\u2013\u2014-]\\s*(\\d{1,2}:\\d{2})");

    /**
     * Matches a day-of-week name followed by a free-time clock value: e.g. "Monday 3:45".
     * Groups: 1 = day name, 2 = time string.
     */
    private static final Pattern DAY_TIME = Pattern.compile(
            "(Monday|Tuesday|Wednesday|Thursday|Friday|Saturday|Sunday)\\s+(\\d{1,2}:\\d{2})");

    /**
     * Maps lowercased PDF class names to rank name(s) from {@code CampConfig.rankCoverage}.
     *
     * <p>Three sessions per day: Scout &amp; Tenderfoot 9–10, Second Class 10–11, First Class 11–12.
     * "Skills Second Class" has the "Skills" category prefix (same pattern as "Shooting Archery").
     * The bare word "Scout" sometimes appears as a spurious PDF label and is intentionally omitted.
     */
    private static final Map<String, List<String>> RANK_CLASSES = Map.of(
            "scout & tenderfoot",  List.of("Scout Rank", "Tenderfoot Rank"),
            "skills second class", List.of("Second Class Rank"),
            "first class",         List.of("First Class Rank"));

    // -------------------------------------------------------------------------
    // Public API

    /**
     * Parses the PDF, filtering results to only those merit badges in {@code knownBadges}.
     *
     * @param pdfPath     path to the Camp Parsons advancement schedule PDF
     * @param knownBadges badge names to include (e.g. from {@code CampConfig.meritBadges});
     *                    use an empty collection to include all parseable entries
     */
    public CampSchedule parse(Path pdfPath, Collection<String> knownBadges) throws IOException {
        return parseText(extractText(pdfPath), knownBadges);
    }

    /**
     * Prints the raw text extracted from the PDF to stdout.
     * Use this to verify the layout before and after the camp updates their PDF.
     */
    public void dumpText(Path pdfPath) throws IOException {
        System.out.println("=== RAW PDF TEXT DUMP: " + pdfPath + " ===");
        System.out.println(extractText(pdfPath));
        System.out.println("=== END DUMP ===");
    }

    // -------------------------------------------------------------------------
    // Package-visible for unit testing

    CampSchedule parseText(String text, Collection<String> knownBadges) {
        // Build a lowercase lookup set of badge names without the " MB" suffix.
        Set<String> knownLower = new HashSet<>();
        for (String b : knownBadges) {
            String lower = b.toLowerCase();
            if (lower.endsWith(" mb")) lower = lower.substring(0, lower.length() - 3).trim();
            knownLower.add(lower);
        }

        List<CampSchedule.DailyClass> classes = new ArrayList<>();
        List<CampSchedule.FreeTimeClass> freeTimeClasses = new ArrayList<>();

        // Pre-process: the PDF sometimes emits a pure time-range line ("10:00 – 11:00") with
        // no badge/rank prefix because the table column containing the name is on the previous
        // row.  Forward-merge such time-only lines onto the next untimed line so that the name
        // and the time end up on the same logical line.
        List<String> lines = new ArrayList<>();
        String pendingTimeText = null;
        for (String raw : text.split("\n")) {
            if (!extractMorningSlots(raw).isEmpty() && firstTimeRangeStart(raw) == 0) {
                // Pure time-only line — capture the time text and skip adding it.
                Matcher tm = TIME_RANGE.matcher(raw);
                if (tm.find()) pendingTimeText = tm.group(0);
            } else {
                if (pendingTimeText != null) {
                    // Attach the orphaned time to the next line that has no time of its own.
                    if (extractMorningSlots(raw).isEmpty()) raw = raw + " " + pendingTimeText;
                    pendingTimeText = null;
                }
                lines.add(raw);
            }
        }

        for (String line : lines) {
            // --- Morning classes: lines that contain an H:MM–H:MM time range ---
            List<CampSchedule.TimeSlot> slots = extractMorningSlots(line);
            if (!slots.isEmpty()) {
                // 1. Extract the text that precedes the first time range.
                int firstSlotPos = firstTimeRangeStart(line);
                if (firstSlotPos <= 0) continue;           // line starts with a time — skip
                String rawPrefix = line.substring(0, firstSlotPos);

                // 2. Strip all non-ASCII characters (note icons, checkbox symbols, etc.).
                String cleaned = rawPrefix.replaceAll("[^\\x20-\\x7E]", "");

                // 3. Strip the trailing difficulty digit (1–4) and any surrounding whitespace.
                cleaned = cleaned.replaceAll("\\s+[1-4]\\s*$", "").trim();

                if (cleaned.isEmpty()) continue;

                // 4. Check for a rank-skill class (Scout & Tenderfoot, Second Class, First Class).
                List<String> rankNames = RANK_CLASSES.get(cleaned.toLowerCase());
                if (rankNames != null) {
                    classes.add(new CampSchedule.DailyClass(
                            List.of(), List.copyOf(rankNames), List.copyOf(slots)));
                    continue;
                }

                // 5. Split on " + " for lines that teach two badges simultaneously.
                String[] parts = cleaned.split("\\s+\\+\\s+");

                // 6. Resolve all badge names on this line, then emit ONE entry for the group.
                List<String> mbNames = new ArrayList<>();
                for (String part : parts) {
                    String name = part.trim();
                    if (name.isEmpty()) continue;
                    String resolved = resolveBadgeName(name, knownLower);
                    if (resolved == null) continue;
                    mbNames.add(normalizeMBName(resolved));
                }
                if (!mbNames.isEmpty()) {
                    classes.add(new CampSchedule.DailyClass(
                            List.copyOf(mbNames), List.of(), List.copyOf(slots)));
                }
                continue;
            }

            // --- Free-time classes: lines with "DayName H:MM" but no time range ---
            List<String[]> dayTimes = extractDayTimes(line);
            if (dayTimes.isEmpty()) continue;

            int firstDayPos = firstDayTimeStart(line);
            if (firstDayPos <= 0) continue;
            String rawPrefix = line.substring(0, firstDayPos);

            // Strip non-ASCII, optional "Attend One/Both Sessions" phrase, trailing difficulty.
            String cleaned = rawPrefix.replaceAll("[^\\x20-\\x7E]", "");
            cleaned = cleaned.replaceAll("(?i)\\s*Attend\\s+(One|Both)\\s+Sessions?\\s*", " ").trim();
            cleaned = cleaned.replaceAll("\\s+[1-4]\\s*$", "").trim();
            if (cleaned.isEmpty()) continue;

            String resolved = resolveBadgeName(cleaned, knownLower);
            if (resolved == null) continue;
            String mbName = normalizeMBName(resolved);

            for (String[] dt : dayTimes) {
                freeTimeClasses.add(new CampSchedule.FreeTimeClass(List.of(mbName), dt[0], dt[1]));
            }
        }

        return new CampSchedule(CAMP_NAME, classes, freeTimeClasses);
    }

    // -------------------------------------------------------------------------

    /**
     * Tries to match {@code name} (as extracted from the PDF line) against the known
     * badge lookup set.  Returns the matched form (original case) or {@code null}.
     *
     * <p>Resolution order:
     * <ol>
     *   <li>Exact match (case-insensitive).</li>
     *   <li>Strip the first word — handles category prefixes like "Shooting Archery" →
     *       "Archery" or "Sports Rifle Shooting" → "Rifle Shooting".</li>
     *   <li>If {@code knownLower} is empty, accept the name as-is (no-filter mode).</li>
     * </ol>
     */
    private static String resolveBadgeName(String name, Set<String> knownLower) {
        if (knownLower.isEmpty()) {
            // No filter: accept everything except obvious non-badges.
            String lower = name.toLowerCase();
            if (lower.contains(" & ") || lower.equals("tower") || lower.equals("scuba")) {
                return null;
            }
            return name;
        }

        String lower = name.toLowerCase();

        // Exact match.
        if (knownLower.contains(lower)) return name;

        // Strip first word (handles "Shooting Archery", "Sports Rifle Shooting").
        int spaceIdx = name.indexOf(' ');
        if (spaceIdx > 0) {
            String withoutFirst = name.substring(spaceIdx + 1).trim();
            if (knownLower.contains(withoutFirst.toLowerCase())) return withoutFirst;
        }

        return null;   // not found in known badge list → skip
    }

    /**
     * Extracts time ranges whose start time is within the morning window (09:00–12:00).
     * Afternoon free-time entries use a "Day H:MM" format (no range dash) so they
     * produce no matches here.
     */
    private static List<CampSchedule.TimeSlot> extractMorningSlots(String line) {
        List<CampSchedule.TimeSlot> slots = new ArrayList<>();
        Matcher m = TIME_RANGE.matcher(line);
        while (m.find()) {
            String start = m.group(1);
            String end   = m.group(2);
            if (isMorningTime(start)) {
                slots.add(new CampSchedule.TimeSlot(start, end));
            }
        }
        return slots;
    }

    /** Returns the start index (in {@code line}) of the first time range, or -1. */
    private static int firstTimeRangeStart(String line) {
        Matcher m = TIME_RANGE.matcher(line);
        return m.find() ? m.start() : -1;
    }

    /** Returns all day+time pairs found on a free-time line (e.g. ["Monday","3:45"]). */
    private static List<String[]> extractDayTimes(String line) {
        List<String[]> result = new ArrayList<>();
        Matcher m = DAY_TIME.matcher(line);
        while (m.find()) result.add(new String[]{m.group(1), m.group(2)});
        return result;
    }

    /** Returns the start index (in {@code line}) of the first day+time match, or -1. */
    private static int firstDayTimeStart(String line) {
        Matcher m = DAY_TIME.matcher(line);
        return m.find() ? m.start() : -1;
    }

    /** True if the time string (H:MM or HH:MM) represents a morning class (9:xx–12:xx). */
    private static boolean isMorningTime(String time) {
        int colon = time.indexOf(':');
        int hour  = Integer.parseInt(time.substring(0, colon));
        return hour >= 9 && hour <= 12;
    }

    /**
     * Ensures the badge name ends with {@code " MB"}.
     * If it already does (any case), returns the trimmed original.
     */
    static String normalizeMBName(String raw) {
        String trimmed = raw.trim();
        if (trimmed.toLowerCase().endsWith(" mb")) return trimmed;
        return trimmed + " MB";
    }

    private String extractText(Path pdfPath) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);   // critical for table-like PDFs
            return stripper.getText(doc);
        }
    }
}
