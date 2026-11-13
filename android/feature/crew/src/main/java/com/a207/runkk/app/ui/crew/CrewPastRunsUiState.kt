package com.a207.runkk.app.ui.crew

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.domain.crew.PastCrewRun
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 지난 크루런 화면 상태(피그마 F-20·F-20b·F-20c). [runs]는 고른 달 전체(최신순)이고, [selectedDate]를 고르면 [shownRuns]가 그날 것만이다.
 * 월 이동은 [earliestMonth](없으면 이번 달)부터 [today]의 달까지다.
 */
data class CrewPastRunsUiState(
    val crewId: Long = 0,
    val today: LocalDate = LocalDate.MIN,
    val month: YearMonth = YearMonth.of(2026, 1),
    val selectedDate: LocalDate? = null,
    val runs: List<PastCrewRun> = emptyList(),
    val runDays: Set<LocalDate> = emptySet(),
    val earliestMonth: YearMonth? = null,
    val isPeriodSheetOpen: Boolean = false,
    val isLoading: Boolean = false,
    val error: UiText? = null
) {
    val currentMonth: YearMonth get() = YearMonth.from(today)

    /** 월 이동 시작(가장 이른 지난 크루런 달, 없으면 이번 달). */
    val firstMonth: YearMonth get() = earliestMonth?.takeIf { it.isBefore(currentMonth) } ?: currentMonth

    val canGoPrev: Boolean get() = month.isAfter(firstMonth)

    val canGoNext: Boolean get() = month.isBefore(currentMonth)

    /** 목록에 보일 크루런: 날짜를 골랐으면 그날 것만. */
    val shownRuns: List<PastCrewRun>
        get() = selectedDate?.let { day -> runs.filter { it.scheduledAt.atZone(ZONE).toLocalDate() == day } } ?: runs

    companion object {
        val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
