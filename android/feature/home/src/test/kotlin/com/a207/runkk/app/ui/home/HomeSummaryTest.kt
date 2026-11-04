package com.a207.runkk.app.ui.home

import com.a207.runkk.app.core.model.CardKind
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.core.model.RunMode
import com.a207.runkk.app.domain.record.RunSummary
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 홈 「내 기록 요약」 값(FR-287, D-491): 오늘·이번 달·최근 러닝·잔디를 대시보드에서 만든다. */
class HomeSummaryTest {
    private val today = LocalDate.of(2026, 11, 4)
    private val latest = RunSummary(
        recordId = "run-9",
        startedAt = Instant.parse("2026-11-03T22:10:00Z"),
        distance = Distance(3_200),
        elapsed = Duration.ofSeconds(1_120),
        averagePace = Pace(350),
        isValid = true,
        mode = RunMode.FREE,
        cardKind = CardKind.FREE_RUN
    )

    @Test
    fun `TC-5835 오늘 기록이 없으면 오늘 요약은 비어 있다`() {
        val none = HomeSummary.of(
            sampleDashboard.copy(todayRuns = 0, monthRuns = 5, monthDistance = Distance(23_400), latestRun = null),
            today
        )
        val two = HomeSummary.of(
            sampleDashboard.copy(
                today = Distance(6_400),
                todayElapsed = Duration.ofMinutes(37),
                todayRuns = 2,
                monthRuns = 5,
                monthDistance = Distance(23_400),
                latestRun = latest
            ),
            today
        )

        assertNull(none.today)
        assertEquals(MonthSummary(Distance(23_400), 5), none.month)
        assertNull(none.latestRun)
        assertEquals(TodaySummary(Distance(6_400), Duration.ofMinutes(37), 2), two.today)
        assertEquals(MonthSummary(Distance(23_400), 5), two.month)
        assertEquals(latest, two.latestRun)
        assertEquals(5, two.grass.size)
        // 옛 서버(이번 달 값 없음)는 0이다.
        assertEquals(MonthSummary(Distance.ZERO, 0), HomeSummary.of(sampleDashboard, today).month)
    }
}
