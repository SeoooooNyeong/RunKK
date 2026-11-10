package com.a207.runkk.app.domain.crew

import com.a207.runkk.app.core.model.Distance
import java.time.LocalDate

/**
 * 지난 공동 목표 한 쪽(서버 CrewGoalHistoryController.GoalHistoryResponse, 피그마 F-21, contract-W7 4장).
 * [nextBefore]가 null이면 마지막 쪽이다.
 */
data class PastGoals(val goals: List<Goal>, val nextBefore: LocalDate?) {
    /**
     * 목표가 있던 지난 한 주. [month]·[weekOfMonth]는 서버가 그 주 목요일로 정한 "N월 N주차"다. [progressPercent]는 100을 넘을 수
     * 있다(결정 C2, 막대는 100에서 멈춘다).
     */
    data class Goal(
        val weekStart: LocalDate,
        val month: Int,
        val weekOfMonth: Int,
        val goal: Distance,
        val total: Distance,
        val isAchieved: Boolean,
        val progressPercent: Int,
        val participantCount: Int
    )
}
