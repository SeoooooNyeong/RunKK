package com.a207.runkk.analysis;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;

/**
 * 연속 러닝 일수(설계 6.1.4 Streak, FR-052, FR-054). 개인·크루가 같은 규칙이라 한 메소드를 쓴다. 주 단위 연속(countWeeksBack)은 개인
 * 대시보드만 쓴다(2026-10-31 결정).
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

    /**
     * 연속 러닝 주 수. 주는 월요일 시작(ISO)이고 그 주에 러닝한 날이 하루라도 있으면 이어진다. base가 속한 주에 base까지 기록이 없으면 지난주부터, 거꾸로
     * 끊길 때까지 센다(countBack과 같은 방식). base 뒤의 날은 세지 않는다.
     */
    public static int countWeeksBack(Set<LocalDate> runDays, LocalDate base) {
        LocalDate monday = base.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = base;
        if (!hasRunBetween(runDays, monday, end)) {
            monday = monday.minusWeeks(1);
            end = monday.plusDays(6);
        }
        int count = 0;
        while (hasRunBetween(runDays, monday, end)) {
            count++;
            monday = monday.minusWeeks(1);
            end = monday.plusDays(6);
        }
        return count;
    }

    private static boolean hasRunBetween(Set<LocalDate> runDays, LocalDate from, LocalDate to) {
        return runDays.stream().anyMatch(day -> !day.isBefore(from) && !day.isAfter(to));
    }
}
