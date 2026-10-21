package com.a207.runkk.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.DisplayName("주 값 객체")
class WeekTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);

    @Test
    void mondayOnly() {
        assertThatThrownBy(() -> new Week(MONDAY.plusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_findsMondayOfTheWeek() {
        assertThat(Week.of(LocalDateTime.of(2026, 10, 25, 23, 59))).isEqualTo(new Week(MONDAY));
        assertThat(Week.of(MONDAY.atStartOfDay())).isEqualTo(new Week(MONDAY));
    }

    @Test
    void contains_isHalfOpen() {
        Week week = new Week(MONDAY);
        assertThat(week.contains(MONDAY.atStartOfDay())).isTrue();
        assertThat(week.contains(MONDAY.plusDays(7).atStartOfDay())).isFalse();
        assertThat(week.contains(MONDAY.atStartOfDay().minusNanos(1))).isFalse();
    }

    @Test
    void closesAt_isSunday24() {
        assertThat(new Week(MONDAY).closesAt()).isEqualTo(LocalDateTime.of(2026, 10, 26, 0, 0));
    }

    @Test
    void previous() {
        assertThat(new Week(MONDAY).previous()).isEqualTo(new Week(MONDAY.minusWeeks(1)));
    }
}
