package org.troop600.scoutsight.html;

import java.util.List;

/**
 * Holds the morning and free-time daily-class schedule for a summer camp.
 *
 * <p>Each {@link DailyClass} represents one morning time block during which one or more
 * merit badges are offered. A badge may appear multiple times if offered at different blocks.
 *
 * <p>Each {@link FreeTimeClass} represents one afternoon free-time offering: a merit badge
 * available on a specific day at a fixed time (e.g. Monday 3:45). Each day is its own entry
 * so Scouts can choose any single session that fits their schedule.
 */
public class CampSchedule {

    /** A single time block: e.g. start="9:00", end="10:30". */
    public record TimeSlot(String start, String end) {}

    /**
     * One daily-class offering: either one or more merit badges, or a rank-advancement session.
     * Exactly one of {@code meritBadges} or {@code ranks} will be non-empty.
     */
    public record DailyClass(List<String> meritBadges, List<String> ranks, List<TimeSlot> sessions) {}

    /** One free-time afternoon offering: one or more merit badges on a specific day. */
    public record FreeTimeClass(List<String> meritBadges, String day, String time) {}

    public final String campName;
    /** Morning daily classes. May be empty before the schedule importer has been run. */
    public final List<DailyClass> dailyClasses;
    /** Afternoon free-time classes. May be empty before the schedule importer has been run. */
    public final List<FreeTimeClass> freeTimeClasses;

    public CampSchedule(String campName, List<DailyClass> dailyClasses,
                        List<FreeTimeClass> freeTimeClasses) {
        this.campName        = campName;
        this.dailyClasses    = List.copyOf(dailyClasses);
        this.freeTimeClasses = List.copyOf(freeTimeClasses);
    }
}
