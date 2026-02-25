package org.troop600.scoutsight.parser;

import org.troop600.scoutsight.model.Scout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses a troop roster CSV (exported from Scoutbook) and returns a map of
 * BSA Member ID → {@link ScoutRosterEntry} for later joining onto {@link Scout}.
 *
 * <p>Expected columns (column names matched case-insensitively, leading/trailing spaces ignored):
 * {@code BSA Member ID}, {@code School Grade}, {@code Patrol Name}, {@code Date Joined}.
 */
public final class ScoutRosterParser {

    public Map<String, ScoutRosterEntry> parse(Path path) throws IOException {
        Map<String, ScoutRosterEntry> result = new HashMap<>();
        try (CsvTokenizer csv = new CsvTokenizer(path, StandardCharsets.UTF_8)) {
            List<String> header = csv.nextRecord();
            if (header == null) return result;

            int bsaIdIdx     = indexOf(header, "BSA Member ID");
            int gradeIdx     = indexOf(header, "School Grade");
            int patrolIdx    = indexOf(header, "Patrol Name");
            int dateJoinIdx  = indexOf(header, "Date Joined");
            int maxIdx = Math.max(Math.max(bsaIdIdx, gradeIdx), Math.max(patrolIdx, dateJoinIdx));

            List<String> row;
            while ((row = csv.nextRecord()) != null) {
                if (row.size() <= maxIdx) continue;
                String bsaId = row.get(bsaIdIdx).trim();
                if (bsaId.isEmpty()) continue;

                int grade = -1;
                String gradeStr = row.get(gradeIdx).trim();
                if (!gradeStr.isEmpty()) {
                    try { grade = Integer.parseInt(gradeStr); } catch (NumberFormatException ignored) {}
                }

                String patrol    = row.get(patrolIdx).trim();
                String dateJoined = row.get(dateJoinIdx).trim();
                result.put(bsaId, new ScoutRosterEntry(patrol, grade, dateJoined));
            }
        }
        return result;
    }

    private static int indexOf(List<String> header, String name) {
        for (int i = 0; i < header.size(); i++) {
            if (header.get(i).trim().equalsIgnoreCase(name)) return i;
        }
        throw new IllegalArgumentException("Column not found in roster CSV: " + name);
    }
}
