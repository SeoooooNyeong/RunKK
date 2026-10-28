package com.a207.runkk.analysis;

import com.a207.runkk.common.Distance;

/**
 * 이번 주 목표 설정 요청(설계 6.1.4 CoachService.setWeeklyGoal, FR-072).
 *
 * @param count 목표 횟수(1~7)
 * @param distance 목표 거리(0 초과)
 */
public record WeeklyGoalCommand(int count, Distance distance) {}
