package com.a207.runkk.common;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.stream.Stream;

/**
 * 시작일·종료일을 포함하는 날짜 범위(설계 6.1.1).
 *
 * @param start 시작일
 * @param end 종료일, start 이상
 */
public record DateRange(LocalDate start, LocalDate end) {

    public DateRange {
        if (start == null || end == null || end.isBefore(start)) {
            throw new IllegalArgumentException("종료일은 시작일 이후여야 한다: " + start + "~" + end);
        }
    }

    public boolean contains(LocalDate d) {
        return !d.isBefore(start) && !d.isAfter(end);
    }

    public Stream<LocalDate> days() {
        return start.datesUntil(end.plusDays(1));
    }

    /** base가 속한 주를 포함한 최근 n주: n-1주 전 월요일 ~ base. */
    public static DateRange lastWeeks(LocalDate base, int n) {
        requirePositive(n);
        LocalDate monday = base.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new DateRange(monday.minusWeeks(n - 1L), base);
    }

    /** base가 속한 달을 포함한 최근 n개월: n-1개월 전 1일 ~ base. */
    public static DateRange lastMonths(LocalDate base, int n) {
        requirePositive(n);
        return new DateRange(base.withDayOfMonth(1).minusMonths(n - 1L), base);
    }

    private static void requirePositive(int n) {
        if (n < 1) {
            throw new IllegalArgumentException("기간 수는 1 이상: " + n);
        }
    }
}
