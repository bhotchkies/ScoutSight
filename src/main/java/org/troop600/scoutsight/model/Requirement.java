package org.troop600.scoutsight.model;

import java.time.LocalDate;

/**
 * A single sub-requirement row for a rank, merit badge, or award.
 */
public final class Requirement {

    /** Requirement identifier, e.g. "1", "2a", "3b", or descriptive text. */
    public final String requirementId;

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

    public Requirement(String requirementId,
                       LocalDate dateCompleted, boolean approved, boolean awarded,
                       String markedCompletedBy, LocalDate markedCompletedDate,
                       String counselorApprovedBy, LocalDate counselorApprovedDate,
                       String leaderApprovedBy, LocalDate leaderApprovedDate,
                       String awardedBy, LocalDate awardedDate) {
        this.requirementId          = requirementId;
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

    public boolean isComplete() {
        return dateCompleted != null;
    }
}
