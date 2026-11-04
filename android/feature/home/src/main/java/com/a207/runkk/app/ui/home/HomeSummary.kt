package com.a207.runkk.app.ui.home

import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.record.Dashboard
import com.a207.runkk.app.domain.record.RunSummary
import java.time.LocalDate

/**
 * 홈 「내 기록 요약」 패널 값(FR-287, J24-16, D-491). [today]는 오늘 달린 기록이 없으면 null(「오늘은 아직 달리지 않았어요」),
 * [month]는 이번 달 횟수·거리, [latestRun]은 최근 러닝 1건(없으면 null), [grass]는 이번 주를 포함한 5주 × 7일 잔디다.
 */
data class HomeSummary(
    val today: TodaySummary?,
    val month: MonthSummary,
    val latestRun: RunSummary?,
    val grass: List<List<GrassCell>>
) {
    companion object {
        /** 대시보드에서 요약을 만든다(순수 함수). 이번 달 값이 없는 옛 서버는 0이다. */
        fun of(dashboard: Dashboard, today: LocalDate): HomeSummary = HomeSummary(
            today = dashboard.todayRuns.takeIf { it > 0 }?.let {
                TodaySummary(dashboard.today, dashboard.todayElapsed, it)
            },
            month = MonthSummary(dashboard.monthDistance ?: Distance.ZERO, dashboard.monthRuns ?: 0),
            latestRun = dashboard.latestRun,
            grass = GrassWeeks.of(dashboard.grass, today)
        )
    }
}
