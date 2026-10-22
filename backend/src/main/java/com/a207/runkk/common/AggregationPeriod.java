package com.a207.runkk.common;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** 집계 기간(랭킹·성장 분석). */
public enum AggregationPeriod {
    WEEKLY {
        @Override
        public DateRange rangeOf(LocalDate day) {
            LocalDate monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            return new DateRange(monday, monday.plusDays(6));
        }
    },
    MONTHLY {
        @Override
        public DateRange rangeOf(LocalDate day) {
            return new DateRange(
                    day.withDayOfMonth(1), day.with(TemporalAdjusters.lastDayOfMonth()));
        }
    };

    /** day가 속한 기간(주: 월~일, 월: 1일~말일). */
    public abstract DateRange rangeOf(LocalDate day);
}
