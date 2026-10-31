package com.a207.runkk.analysis;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 연속 러닝 일수(설계 6.1.4 Streak, FR-052, FR-054). 개인·크루가 같은 규칙이라 한 메소드를 쓴다. 주 단위 연속(countWeeksBack)은 개인
 * 대시보드만 쓴다(2026-10-31 결정). 받은 날짜 집합 안에서만 세므로 끊길 때까지 세려면 부른 쪽이 연속의 첫날(firstDayBack·firstWeekBack)이
 * 읽은 범위 첫날에 닿는지 보고 더 앞 기록을 읽는다(D136, DashboardService).
 */
public final class Streak {

    private Streak() {}

    /** base에 기록이 없으면 전날부터, 거꾸로 끊길 때까지 센다. */
    public static int countBack(Set<LocalDate> runDays, LocalDate base) {
        LocalDate day = runDays.contains(base) ? base : base.minusDays(1);
        int count = 0;
        while (runDays.contains(day)) {
            count++;
            day = day.minusDays(1);
        }
        return count;
    }

    /** countBack이 센 연속의 첫날. 연속이 없으면 비어 있다. */
    public static Optional<LocalDate> firstDayBack(Set<LocalDate> runDays, LocalDate base) {
        int count = countBack(runDays, base);
        LocalDate start = runDays.contains(base) ? base : base.minusDays(1);
        return count == 0 ? Optional.empty() : Optional.of(start.minusDays(count - 1L));
    }

    /**
     * 연속 러닝 주 수. 주는 월요일 시작(ISO)이고 그 주에 러닝한 날이 하루라도 있으면 이어진다. base가 속한 주에 base까지 기록이 없으면 지난주부터, 거꾸로
     * 끊길 때까지 센다(countBack과 같은 방식). base 뒤의 날은 세지 않는다.
     */
    public static int countWeeksBack(Set<LocalDate> runDays, LocalDate base) {
        Set<LocalDate> weeks = runWeeks(runDays, base);
        LocalDate monday = startWeek(weeks, base);
        int count = 0;
        while (weeks.contains(monday)) {
            count++;
            monday = monday.minusWeeks(1);
        }
        return count;
    }

    /** countWeeksBack이 센 연속의 첫 주 월요일. 연속이 없으면 비어 있다. */
    public static Optional<LocalDate> firstWeekBack(Set<LocalDate> runDays, LocalDate base) {
        int count = countWeeksBack(runDays, base);
        LocalDate start = startWeek(runWeeks(runDays, base), base);
        return count == 0 ? Optional.empty() : Optional.of(start.minusWeeks(count - 1L));
    }

    /** base까지 러닝한 날이 있는 주의 월요일들. */
    private static Set<LocalDate> runWeeks(Set<LocalDate> runDays, LocalDate base) {
        return runDays.stream()
                .filter(day -> !day.isAfter(base))
                .map(Streak::mondayOf)
                .collect(Collectors.toSet());
    }

    /** base가 속한 주에 기록이 있으면 그 주, 없으면 지난주 월요일. */
    private static LocalDate startWeek(Set<LocalDate> weeks, LocalDate base) {
        LocalDate monday = mondayOf(base);
        return weeks.contains(monday) ? monday : monday.minusWeeks(1);
    }

    private static LocalDate mondayOf(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
