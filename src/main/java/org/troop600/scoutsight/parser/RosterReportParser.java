package org.troop600.scoutsight.parser;

import org.troop600.scoutsight.model.Scout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses a Scoutbook "Roster Report" CSV and returns a map of
 * BSA Number → {@link RosterReportEntry} for joining onto {@link Scout}.
 *
 * <p>The file has a section-based layout: a row whose first cell is blank and
 * second cell is a section name (e.g. "YOUTH MEMBERS") precedes a column-header
 * row and then data rows. This parser reads only the YOUTH MEMBERS section and
 * stops when the next section header is encountered.
 *
 * <p>Dates are truncated to the 4-digit year (e.g. "3/28/2013" → "2013").
 * If the BSA Number column is not present, an {@link IOException} is thrown —
 * only full-roster exports (not patrol-only exports) are supported.
 */
public final class RosterReportParser {

    public Map<String, RosterReportEntry> parse(Path path) throws IOException {
        Map<String, RosterReportEntry> result = new HashMap<>();

        try (CsvTokenizer csv = new CsvTokenizer(path, StandardCharsets.UTF_8)) {
            // Advance until the YOUTH MEMBERS section header row
            if (!skipToSection(csv, "YOUTH MEMBERS")) {
                throw new IOException(
                    "No YOUTH MEMBERS section found in roster report: " + path.getFileName());
            }

            // The next row is the column header
            List<String> header = csv.nextRecord();
            if (header == null) return result;

            int bsaNumIdx    = findColumnIndex(header, "BSA Number");
            if (bsaNumIdx < 0) {
                throw new IOException(
                    "BSA Number column not found in Roster Report. " +
                    "Only full roster exports (not patrol-only) are supported.");
            }

            int dobIdx       = findColumnIndex(header, "Date of Birth");
            int dateJoinIdx  = findColumnIndex(header, "Date Joined");
            int genderIdx    = findColumnIndex(header, "Gender");
            int schoolIdx    = findColumnIndex(header, "School Info");
            int gradeIdx     = findColumnIndex(header, "Grade");
            int positionsIdx = findColumnIndex(header, "Positions (Tenure)");
            int patrolIdx    = findColumnIndex(header, "Patrol");

            List<String> row;
            while ((row = csv.nextRecord()) != null) {
                // Stop when we reach the next section header
                if (isSectionRow(row)) break;
                if (row.size() <= bsaNumIdx) continue;

                String bsaNum = row.get(bsaNumIdx).trim();
                if (bsaNum.isEmpty()) continue;

                String patrol    = get(row, patrolIdx).trim();
                String gradeStr  = get(row, gradeIdx).trim();
                String dob       = get(row, dobIdx).trim();
                String dateJoined = get(row, dateJoinIdx).trim();
                String gender    = get(row, genderIdx).trim();
                String school    = get(row, schoolIdx).trim();
                String positions = get(row, positionsIdx).trim();

                result.put(bsaNum, new RosterReportEntry(
                        patrol,
                        parseGrade(gradeStr),
                        extractYear(dateJoined),
                        extractYear(dob),
                        school,
                        gender,
                        positions
                ));
            }
        }

        return result;
    }

    // -------------------------------------------------------------------------

    /**
     * Reads rows from {@code csv} until one whose second cell (index 1) matches
     * {@code sectionName} (case-insensitive). Returns true if found, false at EOF.
     */
    private static boolean skipToSection(CsvTokenizer csv, String sectionName) throws IOException {
        List<String> row;
        while ((row = csv.nextRecord()) != null) {
            if (row.size() >= 2 && row.get(1).trim().equalsIgnoreCase(sectionName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true when a row is a section-marker: first cell is blank (the data
     * rows all have a numeric index in column 0) and the second cell is non-empty.
     */
    private static boolean isSectionRow(List<String> row) {
        return row.size() >= 2
                && row.get(0).trim().isEmpty()
                && !row.get(1).trim().isEmpty();
    }

    /** Case-insensitive column lookup; returns -1 if not found. */
    private static int findColumnIndex(List<String> header, String name) {
        for (int i = 0; i < header.size(); i++) {
            if (header.get(i).trim().equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    /** Returns the cell at {@code idx}, or an empty string if out of bounds or idx < 0. */
    private static String get(List<String> row, int idx) {
        if (idx < 0 || idx >= row.size()) return "";
        return row.get(idx);
    }

    /**
     * Extracts the 4-digit year from a date like "3/28/2013" or "03/28/2013".
     * Returns an empty string if the input is blank or cannot be parsed.
     */
    static String extractYear(String date) {
        if (date == null || date.isBlank()) return "";
        int lastSlash = date.lastIndexOf('/');
        if (lastSlash >= 0 && lastSlash < date.length() - 1) {
            String year = date.substring(lastSlash + 1).trim();
            if (year.length() == 4 && year.chars().allMatch(Character::isDigit)) return year;
        }
        return "";
    }

    /**
     * Parses grade strings like "Seventh Grade" → 7, or plain numerics.
     * Returns -1 if unknown.
     */
    static int parseGrade(String gradeStr) {
        if (gradeStr == null || gradeStr.isBlank()) return -1;
        String s = gradeStr.trim().toLowerCase();
        if (s.startsWith("sixth"))    return 6;
        if (s.startsWith("seventh"))  return 7;
        if (s.startsWith("eighth"))   return 8;
        if (s.startsWith("ninth"))    return 9;
        if (s.startsWith("tenth"))    return 10;
        if (s.startsWith("eleventh")) return 11;
        if (s.startsWith("twelfth"))  return 12;
        try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        return -1;
    }
}
