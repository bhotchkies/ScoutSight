package org.troop600.scoutsight.html;

import org.troop600.scoutsight.model.Scout;

import java.util.List;

/**
 * Lightweight per-scout view shared across aggregate report pages
 * (Eagle MB Summary, Eagle MB Detail, Trail to First Class).
 *
 * <p>Carries enough fields for client-side filtering (patrol, grade, birthYear,
 * joinYear, rank) plus the member ID needed to link to each scout's detail page.
 */
record ScoutRef(String name, String memberId, String patrol, int grade,
                String birthYear, String joinYear, String rank) {

    /** Rank advancement order (long names), used by {@link #currentRankLong}. */
    static final List<String> RANK_ORDER = List.of(
            "Scout Rank", "Tenderfoot Rank", "Second Class Rank", "First Class Rank",
            "Star Scout Rank", "Life Scout Rank", "Eagle Scout Rank"
    );

    /** Build a {@code ScoutRef} from a {@link Scout}. */
    static ScoutRef from(Scout s) {
        return new ScoutRef(
                s.displayName(),
                s.bsaMemberId,
                s.patrol    != null ? s.patrol    : "",
                s.schoolGrade,
                s.birthYear != null ? s.birthYear : "",
                s.joinYear  != null ? s.joinYear  : "",
                IndexPageWriter.currentRankShort(s)
        );
    }

    /**
     * Emit a full scout JSON object (name, memberId, patrol, grade, birthYear, joinYear, rank)
     * into {@code jb}.
     */
    static void appendTo(JsonBuilder jb, ScoutRef ref) {
        jb.obj()
          .field("name",      ref.name())
          .field("memberId",  ref.memberId())
          .field("patrol",    ref.patrol())
          .field("grade",     ref.grade())
          .field("birthYear", ref.birthYear())
          .field("joinYear",  ref.joinYear())
          .field("rank",      ref.rank())
          .endObj();
    }

    /**
     * Returns the highest completed rank name in long form (e.g. {@code "Star Scout Rank"})
     * for use as a bucketing key, defaulting to {@code "Scout Rank"}.
     */
    static String currentRankLong(Scout scout) {
        String current = "Scout Rank";
        for (String rankName : RANK_ORDER) {
            boolean completed = scout.ranks.stream()
                    .anyMatch(r -> r.name.equals(rankName) && r.isComplete());
            if (completed) current = rankName;
        }
        return current;
    }
}
