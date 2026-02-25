package org.troop600.scoutsight.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A single rank, merit badge, or award — the parent item — together with
 * all of its sub-requirements.
 */
public final class AdvancementItem {

    public final AdvancementType type;
    public final String name;       // e.g. "Eagle Scout Rank", "Chess MB", "Interpreter Strip"
    public final String version;

    public final LocalDate dateCompleted;   // null if not yet complete
    public final boolean approved;
    public final boolean awarded;

    public final String markedCompletedBy;
    public final LocalDate markedCompletedDate;
    public final String counselorApprovedBy;
    public final LocalDate counselorApprovedDate;
    public final String leaderApprovedBy;
    public final LocalDate leaderApprovedDate;
    public final String awardedBy;
    public final LocalDate awardedDate;

    public final List<Requirement> requirements;

    private AdvancementItem(Builder b) {
        this.type                   = b.type;
        this.name                   = b.name;
        this.version                = b.version;
        this.dateCompleted          = b.dateCompleted;
        this.approved               = b.approved;
        this.awarded                = b.awarded;
        this.markedCompletedBy      = b.markedCompletedBy;
        this.markedCompletedDate    = b.markedCompletedDate;
        this.counselorApprovedBy    = b.counselorApprovedBy;
        this.counselorApprovedDate  = b.counselorApprovedDate;
        this.leaderApprovedBy       = b.leaderApprovedBy;
        this.leaderApprovedDate     = b.leaderApprovedDate;
        this.awardedBy              = b.awardedBy;
        this.awardedDate            = b.awardedDate;
        this.requirements           = Collections.unmodifiableList(new ArrayList<>(b.requirements));
    }

    public boolean isComplete() {
        return dateCompleted != null;
    }

    // -------------------------------------------------------------------------

    public static final class Builder {
        final AdvancementType type;
        final String name;
        final String version;
        final LocalDate dateCompleted;
        final boolean approved;
        final boolean awarded;
        final String markedCompletedBy;
        final LocalDate markedCompletedDate;
        final String counselorApprovedBy;
        final LocalDate counselorApprovedDate;
        final String leaderApprovedBy;
        final LocalDate leaderApprovedDate;
        final String awardedBy;
        final LocalDate awardedDate;
        final List<Requirement> requirements = new ArrayList<>();

        public Builder(AdvancementType type, String name, String version,
                       LocalDate dateCompleted, boolean approved, boolean awarded,
                       String markedCompletedBy, LocalDate markedCompletedDate,
                       String counselorApprovedBy, LocalDate counselorApprovedDate,
                       String leaderApprovedBy, LocalDate leaderApprovedDate,
                       String awardedBy, LocalDate awardedDate) {
            this.type                   = type;
            this.name                   = name;
            this.version                = version;
            this.dateCompleted          = dateCompleted;
            this.approved               = approved;
            this.awarded                = awarded;
            this.markedCompletedBy      = markedCompletedBy;
            this.markedCompletedDate    = markedCompletedDate;
            this.counselorApprovedBy    = counselorApprovedBy;
            this.counselorApprovedDate  = counselorApprovedDate;
            this.leaderApprovedBy       = leaderApprovedBy;
            this.leaderApprovedDate     = leaderApprovedDate;
            this.awardedBy              = awardedBy;
            this.awardedDate            = awardedDate;
        }

        public void addRequirement(Requirement r) {
            requirements.add(r);
        }

        public AdvancementItem build() {
            return new AdvancementItem(this);
        }
    }
}
