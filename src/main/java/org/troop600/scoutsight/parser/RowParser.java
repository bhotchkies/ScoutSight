package org.troop600.scoutsight.parser;

import org.troop600.scoutsight.model.RawRow;

import java.util.List;

/**
 * Maps a tokenized record (18 fields) to a RawRow.
 */
final class RowParser {

    private RowParser() {}

    static RawRow toRawRow(List<String> fields) {
        if (fields.size() != 18) {
            throw new IllegalArgumentException(
                "Expected 18 fields, got " + fields.size() + ": " + fields);
        }
        return new RawRow(
            fields.get(0),   // bsaMemberId
            fields.get(1),   // firstName
            fields.get(2),   // middleName
            fields.get(3),   // lastName
            fields.get(4),   // advancementType
            fields.get(5),   // advancement
            fields.get(6),   // version
            fields.get(7),   // dateCompleted
            fields.get(8),   // approved
            fields.get(9),   // awarded
            fields.get(10),  // markedCompletedBy
            fields.get(11),  // markedCompletedDate
            fields.get(12),  // counselorApprovedBy
            fields.get(13),  // counselorApprovedDate
            fields.get(14),  // leaderApprovedBy
            fields.get(15),  // leaderApprovedDate
            fields.get(16),  // awardedBy
            fields.get(17)   // awardedDate
        );
    }
}
