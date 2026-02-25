package org.troop600.scoutsight.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public final class DateUtil {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    private DateUtil() {}

    /** Returns null for empty/null input; parses "MM/DD/YYYY" otherwise. */
    public static LocalDate parse(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        return LocalDate.parse(raw, FMT);
    }
}
