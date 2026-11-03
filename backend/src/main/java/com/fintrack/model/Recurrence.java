package com.fintrack.model;

import java.time.LocalDate;

/** How often a bill or income repeats. */
public enum Recurrence {
    WEEKLY, BIWEEKLY, MONTHLY, QUARTERLY, YEARLY;

    /**
     * The n-th occurrence (0-based) counted from the anchor date. Counting from the anchor
     * keeps month-end dates stable (Jan 31 -> Feb 28 -> Mar 31) instead of drifting.
     */
    public LocalDate nth(LocalDate anchor, long n) {
        return switch (this) {
            case WEEKLY -> anchor.plusWeeks(n);
            case BIWEEKLY -> anchor.plusWeeks(2 * n);
            case MONTHLY -> anchor.plusMonths(n);
            case QUARTERLY -> anchor.plusMonths(3 * n);
            case YEARLY -> anchor.plusYears(n);
        };
    }
}
