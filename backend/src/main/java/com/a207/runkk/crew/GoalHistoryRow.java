package com.a207.runkk.crew;

import com.a207.runkk.common.Distance;
import java.time.LocalDate;

/**
 * 목표가 있던 한 주의 저장 값(WeeklyRaceRepository.goalHistory, 피그마 맞춤 W7 F-21).
 *
 * @param weekStart 그 주 월요일
 * @param goal 목표 거리
 * @param total 그 주 크루 합계
 * @param achieved 그 주 달성이 기록됐는가(achieved_at)
 * @param participantCount 그 주 기여 거리가 0보다 큰 기여자 수(익명 기여는 한 묶음)
 */
public record GoalHistoryRow(
        LocalDate weekStart,
        Distance goal,
        Distance total,
        boolean achieved,
        int participantCount) {}
