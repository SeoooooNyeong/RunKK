package com.a207.runkk.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DateRangeTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 22);

    @Test
    void endMustNotPrecedeStart() {
        assertThatThrownBy(() -> new DateRange(DAY, DAY.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void contains_isInclusive() {
        DateRange range = new DateRange(DAY, DAY.plusDays(2));
        assertThat(range.contains(DAY)).isTrue();
        assertThat(range.contains(DAY.plusDays(2))).isTrue();
        assertThat(range.contains(DAY.plusDays(3))).isFalse();
        assertThat(range.contains(DAY.minusDays(1))).isFalse();
    }

    @Test
    void days_listsEveryDay() {
        assertThat(new DateRange(DAY, DAY.plusDays(2)).days())
                .containsExactly(DAY, DAY.plusDays(1), DAY.plusDays(2));
    }

    @Test
    void lastWeeks_startsOnMondayOfOldestWeek() {
        DateRange range = DateRange.lastWeeks(DAY, 8);
        assertThat(range.start()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(range.end()).isEqualTo(DAY);
    }

    @Test
    void lastMonths_startsOnFirstDayOfOldestMonth() {
        DateRange range = DateRange.lastMonths(DAY, 6);
        assertThat(range.start()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(range.end()).isEqualTo(DAY);
    }

    @Test
    void lastPeriods_requirePositiveCount() {
        assertThatThrownBy(() -> DateRange.lastWeeks(DAY, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DateRange.lastMonths(DAY, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
