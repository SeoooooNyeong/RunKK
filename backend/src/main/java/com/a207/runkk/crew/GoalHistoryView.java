package com.a207.runkk.crew;

import com.a207.runkk.common.Distance;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * 지난 공동 목표 한 페이지(피그마 맞춤 W7 F-21, contract-W7.md 4장). 최신 주부터.
 *
 * @param goals 목표가 있던 지난 주
 * @param nextBefore 다음 페이지 before(이 페이지 마지막 주 월요일), 마지막 페이지면 null
 */
public record GoalHistoryView(List<Line> goals, LocalDate nextBefore) {

    public GoalHistoryView {
        goals = List.copyOf(goals);
    }

    /**
     * 한 주.
     *
     * @param weekStart 그 주 월요일
     * @param month "N월"(그 주 목요일이 속한 달)
     * @param weekOfMonth "N주차"(그 주 목요일이 그 달 몇 번째 목요일인가)
     * @param goal 목표 거리
     * @param total 합계 거리
     * @param achieved 달성 기록이 있는가
     * @param progressPercent ⌊합계 × 100 ÷ 목표⌋, 100 초과 허용(결정 C2)
     * @param participantCount 참여 인원
     */
    public record Line(
            LocalDate weekStart,
            int month,
            int weekOfMonth,
            Distance goal,
            Distance total,
            boolean achieved,
            int progressPercent,
            int participantCount) {

        private static final int PERCENT = 100;
        private static final int DAYS_IN_WEEK = 7;

        /** 저장 값에 주 표기와 진행률을 붙인다. */
        public static Line of(GoalHistoryRow row) {
            LocalDate thursday = row.weekStart().with(DayOfWeek.THURSDAY);
            long percent = row.total().meters() * PERCENT / row.goal().meters();
            return new Line(
                    row.weekStart(),
                    thursday.getMonthValue(),
                    (thursday.getDayOfMonth() - 1) / DAYS_IN_WEEK + 1,
                    row.goal(),
                    row.total(),
                    row.achieved(),
                    (int) Math.min(Integer.MAX_VALUE, percent),
                    row.participantCount());
        }
    }
}
