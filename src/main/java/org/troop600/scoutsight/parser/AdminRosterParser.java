package org.troop600.scoutsight.parser;

import org.troop600.scoutsight.model.Scout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Parses the YOUTH MEMBERS section of a BSA admin roster CSV and returns a map of
 * BSA Number → {@link AdminRosterEntry} for joining onto {@link Scout}.
 *
 * <p>The file has a two-section layout (ADULT MEMBERS then YOUTH MEMBERS). This parser
 * skips to the YOUTH MEMBERS section, reads the column header row, then processes data rows.
 *
 * <p>A scout may appear in multiple rows (one per patrol membership). All non-blank patrol
 * names are accumulated per scout and joined with {@code ", "}. Blank-patrol rows contribute
 * non-patrol fields but are otherwise ignored. Continuation rows (no BSA number) are skipped.
 *
 * <p>Dates in {@code Date of Birth} and {@code Date Joined} are stored both as full strings
 * and as 4-digit year extracts.
 */
public final class AdminRosterParser {

    /**
     * Parses the given admin roster CSV and returns a map keyed by BSA Number.
     *
     * @throws IOException if the YOUTH MEMBERS section or BSA Number column is missing
     */
    public Map<String, AdminRosterEntry> parse(Path path) throws IOException {
        // Intermediate accumulators: keyed by BSA#
        Map<String, LinkedHashSet<String>> patrolSets = new LinkedHashMap<>();
        Map<String, String[]> fieldMap = new LinkedHashMap<>();  // grade,dob,dateJoined,gender,school,positions

        try (CsvTokenizer csv = new CsvTokenizer(path, StandardCharsets.UTF_8)) {
            if (!skipToSection(csv, "YOUTH MEMBERS")) {
                throw new IOException(
                    "No YOUTH MEMBERS section found in admin roster: " + path.getFileName());
            }

            List<String> header = csv.nextRecord();
            if (header == null) return new HashMap<>();

            int bsaNumIdx    = findColumnIndex(header, "BSA Number");
            if (bsaNumIdx < 0) {
                throw new IOException(
                    "BSA Number column not found in admin roster YOUTH MEMBERS section.");
            }

            int gradeIdx     = findColumnIndex(header, "Grade");
            int dobIdx       = findColumnIndex(header, "Date of Birth");
            int dateJoinIdx  = findColumnIndex(header, "Date Joined");
            int genderIdx    = findColumnIndex(header, "Gender");
            int schoolIdx    = findColumnIndex(header, "School Info");
            int positionsIdx = findColumnIndex(header, "Positions (Tenure)");
            int patrolIdx    = findColumnIndex(header, "Patrol");

            List<String> row;
            while ((row = csv.nextRecord()) != null) {
                if (isSectionRow(row)) break;
                if (row.size() <= bsaNumIdx) continue;

                String bsaNum = row.get(bsaNumIdx).trim();
                if (bsaNum.isEmpty()) continue;

                // Accumulate non-blank patrol names; ignore blank/whitespace entries.
                String patrol = get(row, patrolIdx).trim();
                if (!patrol.isEmpty()) {
                    patrolSets.computeIfAbsent(bsaNum, k -> new LinkedHashSet<>()).add(patrol);
                } else {
                    patrolSets.computeIfAbsent(bsaNum, k -> new LinkedHashSet<>());
                }

                // Last-write-wins for non-patrol fields (values are identical across rows for the same scout).
                fieldMap.put(bsaNum, new String[]{
                    get(row, gradeIdx).trim(),
                    get(row, dobIdx).trim(),
                    get(row, dateJoinIdx).trim(),
                    get(row, genderIdx).trim(),
                    get(row, schoolIdx).trim(),
                    get(row, positionsIdx).trim()
                });
            }
        }

        Map<String, AdminRosterEntry> result = new HashMap<>();
        for (Map.Entry<String, String[]> e : fieldMap.entrySet()) {
            String bsaNum = e.getKey();
            String[] f    = e.getValue();
            String patrol = String.join(", ", patrolSets.getOrDefault(bsaNum, new LinkedHashSet<>()));
            result.put(bsaNum, new AdminRosterEntry(
                patrol,
                RosterReportParser.parseGrade(f[0]),
                RosterReportParser.extractYear(f[2]),   // joinYear from Date Joined
                f[2],                                   // dateJoined full date
                RosterReportParser.extractYear(f[1]),   // birthYear from Date of Birth
                f[3],
                f[4],
                f[5]
            ));
        }
        return result;
    }

    private static boolean skipToSection(CsvTokenizer csv, String sectionName) throws IOException {
        List<String> row;
        while ((row = csv.nextRecord()) != null) {
            if (row.size() >= 2 && row.get(1).trim().equalsIgnoreCase(sectionName)) return true;
        }
        return false;
    }

    private static boolean isSectionRow(List<String> row) {
        return row.size() >= 2
                && row.get(0).trim().isEmpty()
                && !row.get(1).trim().isEmpty();
    }

    private static int findColumnIndex(List<String> header, String name) {
        for (int i = 0; i < header.size(); i++) {
            if (header.get(i).trim().equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    private static String get(List<String> row, int idx) {
        if (idx < 0 || idx >= row.size()) return "";
        return row.get(idx);
    }
}
