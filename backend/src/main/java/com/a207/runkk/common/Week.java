package com.a207.runkk.common;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

/**
 * 월요일 0시에 시작하는 한 주(설계 6.1.1).
 *
 * @param monday 월요일만
 */
public record Week(LocalDate monday) {

    public Week {
        if (monday == null || monday.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("주는 월요일로 정한다: " + monday);
        }
    }

    /** t가 속한 주. */
    public static Week of(LocalDateTime t) {
        return new Week(t.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
    }

    /** [월 0시, 다음 월 0시). */
    public boolean contains(LocalDateTime t) {
        return !t.isBefore(monday.atStartOfDay()) && t.isBefore(closesAt());
    }

    /** 일요일 24시(FR-140 마감 일시). */
    public LocalDateTime closesAt() {
        return monday.plusWeeks(1).atStartOfDay();
    }

    public Week previous() {
        return new Week(monday.minusWeeks(1));
    }
}
