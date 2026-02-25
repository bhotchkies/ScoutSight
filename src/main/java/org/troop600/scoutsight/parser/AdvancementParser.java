package org.troop600.scoutsight.parser;

import org.troop600.scoutsight.model.*;
import org.troop600.scoutsight.util.DateUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/**
 * Parses a BSA Internet Advancement CSV export into a list of Scout objects.
 *
 * Two-pass assembly per scout:
 *   Pass A — build an AdvancementItem.Builder for each parent row, keyed by
 *             (version + "|" + derivedRequirementType).
 *   Pass B — attach each requirement row to its parent builder using the same key.
 *
 * Requirements can appear before their parent row in the file, so a positional
 * "current parent" pointer is not used.
 */
public final class AdvancementParser {

    public List<Scout> parse(Path csvPath) throws IOException {
        List<RawRow> allRows = tokenizeAll(csvPath);
        Map<String, List<RawRow>> byScout = groupByScout(allRows);

        List<Scout> scouts = new ArrayList<>();
        for (Map.Entry<String, List<RawRow>> entry : byScout.entrySet()) {
            scouts.add(buildScout(entry.getKey(), entry.getValue()));
        }
        return scouts;
    }

    // -------------------------------------------------------------------------

    private List<RawRow> tokenizeAll(Path path) throws IOException {
        List<RawRow> rows = new ArrayList<>();
        int skipped = 0;
        try (CsvTokenizer tok = new CsvTokenizer(path, StandardCharsets.UTF_8)) {
            tok.skipHeader();
            List<String> fields;
            while ((fields = tok.nextRecord()) != null) {
                try {
                    rows.add(RowParser.toRawRow(fields));
                } catch (IllegalArgumentException e) {
                    skipped++;
                    System.err.println("WARN: Skipping malformed record (" + fields.size()
                        + " fields): " + e.getMessage());
                }
            }
        }
        if (skipped > 0) {
            System.err.println("WARN: Skipped " + skipped + " malformed record(s) total.");
        }
        return rows;
    }

    private Map<String, List<RawRow>> groupByScout(List<RawRow> rows) {
        Map<String, List<RawRow>> map = new LinkedHashMap<>();  // preserves file order
        for (RawRow row : rows) {
            map.computeIfAbsent(row.bsaMemberId, k -> new ArrayList<>()).add(row);
        }
        return map;
    }

    private Scout buildScout(String scoutId, List<RawRow> rows) {
        String firstName  = rows.get(0).firstName;
        String middleName = rows.get(0).middleName;
        String lastName   = rows.get(0).lastName;

        // Separate parent rows from requirement rows
        List<RawRow> parents      = new ArrayList<>();
        List<RawRow> requirements = new ArrayList<>();
        for (RawRow row : rows) {
            if (isParentRow(row)) parents.add(row);
            else requirements.add(row);
        }

        // Pass A: build item builders keyed by (version + "|" + derivedReqType)
        Map<String, AdvancementItem.Builder> builders = new LinkedHashMap<>();
        for (RawRow p : parents) {
            String reqType = NameNormalizer.deriveRequirementType(p);
            String key = p.version + "|" + reqType;
            builders.put(key, toBuilder(p));
        }

        // Pass B: attach requirement rows to their parent builders
        for (RawRow r : requirements) {
            String key = r.version + "|" + r.advancementType;
            AdvancementItem.Builder builder = builders.get(key);
            if (builder != null) {
                builder.addRequirement(toRequirement(r));
            } else {
                System.err.println("WARN: No parent for scout=" + scoutId
                    + " version=" + r.version
                    + " type=" + r.advancementType);
            }
        }

        // Partition finalized items into ranks / merit badges / awards
        List<AdvancementItem> ranks       = new ArrayList<>();
        List<AdvancementItem> meritBadges = new ArrayList<>();
        List<AdvancementItem> awards      = new ArrayList<>();

        for (AdvancementItem.Builder b : builders.values()) {
            AdvancementItem item = b.build();
            switch (item.type) {
                case RANK        -> ranks.add(item);
                case MERIT_BADGE -> meritBadges.add(item);
                case AWARD       -> awards.add(item);
            }
        }

        return new Scout(scoutId, firstName, middleName, lastName,
                         ranks, meritBadges, awards);
    }

    private boolean isParentRow(RawRow row) {
        return "Rank".equals(row.advancementType)
            || "Merit Badges".equals(row.advancementType)
            || "Awards".equals(row.advancementType);
    }

    private AdvancementItem.Builder toBuilder(RawRow p) {
        AdvancementType type = switch (p.advancementType) {
            case "Rank"         -> AdvancementType.RANK;
            case "Merit Badges" -> AdvancementType.MERIT_BADGE;
            case "Awards"       -> AdvancementType.AWARD;
            default             -> throw new IllegalStateException(p.advancementType);
        };
        return new AdvancementItem.Builder(
            type, p.advancement, p.version,
            DateUtil.parse(p.dateCompleted),
            "True".equals(p.approved),
            "True".equals(p.awarded),
            nullIfEmpty(p.markedCompletedBy),   DateUtil.parse(p.markedCompletedDate),
            nullIfEmpty(p.counselorApprovedBy), DateUtil.parse(p.counselorApprovedDate),
            nullIfEmpty(p.leaderApprovedBy),    DateUtil.parse(p.leaderApprovedDate),
            nullIfEmpty(p.awardedBy),           DateUtil.parse(p.awardedDate)
        );
    }

    private Requirement toRequirement(RawRow r) {
        return new Requirement(
            r.advancement,
            DateUtil.parse(r.dateCompleted),
            "True".equals(r.approved),
            "True".equals(r.awarded),
            nullIfEmpty(r.markedCompletedBy),   DateUtil.parse(r.markedCompletedDate),
            nullIfEmpty(r.counselorApprovedBy), DateUtil.parse(r.counselorApprovedDate),
            nullIfEmpty(r.leaderApprovedBy),    DateUtil.parse(r.leaderApprovedDate),
            nullIfEmpty(r.awardedBy),           DateUtil.parse(r.awardedDate)
        );
    }

    private static String nullIfEmpty(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }
}
