package com.a207.runkk.analysis;

import java.time.LocalDate;
import java.util.Set;

/** 연속 러닝 일수(설계 6.1.4 Streak, FR-052, FR-054). 개인·크루가 같은 규칙이라 한 메소드를 쓴다. */
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
}
