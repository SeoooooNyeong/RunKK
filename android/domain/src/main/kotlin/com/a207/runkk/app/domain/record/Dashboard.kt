package com.a207.runkk.app.domain.record

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.GeoPoint
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RunMode
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * 대시보드(서버 analysis.DashboardView, FR-050~FR-057, FR-077). [weeklyAchievement]는 이번 주 목표 거리 달성 비율(0.6 = 60%,
 * 100%를 넘을 수 있다)이고 목표가 없으면 null이다. [weekly]는 횟수·거리 목표와 실제 값, [grassLevels]는 잔디 칸의 진하기(0~4)다.
 */
data class Dashboard(
    val today: Distance,
    val total: Distance,
    val landmark: String?,
    val streakDays: Int,
    val grass: Map<LocalDate, Distance>,
    val weeklyAchievement: Double?,
    val crew: CrewArea?,
    val todayElapsed: Duration = Duration.ZERO,
    val todayRuns: Int = 0,
    val weekly: WeeklyGoalProgress? = null,
    val grassLevels: Map<LocalDate, Int> = emptyMap()
) {
    /** 크루가 있으면 크루 스트릭·잔디·랜드마크. */
    data class CrewArea(
        val crewName: String,
        val streakDays: Int,
        val grass: Map<LocalDate, Distance>,
        val landmark: String?
    )
}
