package org.troop600.scoutsight.model;

/**
 * Direct mapping of all 18 CSV columns. Used only during parsing;
 * never exposed outside the parser package.
 */
public final class RawRow {
    // Columns 1-4: scout identity
    public final String bsaMemberId;
    public final String firstName;
    public final String middleName;
    public final String lastName;

    // Columns 5-7: advancement classification
    public final String advancementType;  // "Rank", "Merit Badges", "Awards", or "<name> Requirements"
    public final String advancement;      // parent: item name; requirement: req id or descriptive text
    public final String version;          // export version group, e.g. "2022"

    // Column 8: completion
    public final String dateCompleted;    // "MM/DD/YYYY" or ""

    // Columns 9-10: status flags
    public final String approved;         // "True" or "False"
    public final String awarded;          // "True" or "False"

    // Columns 11-12: marked-completed audit
    public final String markedCompletedBy;
    public final String markedCompletedDate;

    // Columns 13-14: counselor approval (merit badges)
    public final String counselorApprovedBy;
    public final String counselorApprovedDate;

    // Columns 15-16: leader approval
    public final String leaderApprovedBy;
    public final String leaderApprovedDate;

    // Columns 17-18: award
    public final String awardedBy;
    public final String awardedDate;

    public RawRow(String bsaMemberId, String firstName, String middleName,
                  String lastName, String advancementType, String advancement,
                  String version, String dateCompleted, String approved,
                  String awarded, String markedCompletedBy, String markedCompletedDate,
                  String counselorApprovedBy, String counselorApprovedDate,
                  String leaderApprovedBy, String leaderApprovedDate,
                  String awardedBy, String awardedDate) {
        this.bsaMemberId          = bsaMemberId;
        this.firstName            = firstName;
        this.middleName           = middleName;
        this.lastName             = lastName;
        this.advancementType      = advancementType;
        this.advancement          = advancement;
        this.version              = version;
        this.dateCompleted        = dateCompleted;
        this.approved             = approved;
        this.awarded              = awarded;
        this.markedCompletedBy    = markedCompletedBy;
        this.markedCompletedDate  = markedCompletedDate;
        this.counselorApprovedBy  = counselorApprovedBy;
        this.counselorApprovedDate = counselorApprovedDate;
        this.leaderApprovedBy     = leaderApprovedBy;
        this.leaderApprovedDate   = leaderApprovedDate;
        this.awardedBy            = awardedBy;
        this.awardedDate          = awardedDate;
    }
}
