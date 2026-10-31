package com.a207.runkk.app.ui.record

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.record.RunSummary
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 기록 화면 3탭(D-279). 달력 탭은 [month]의 기록([runs])·날짜 칸 거리([dailyDistance], 유효 기록만)·월 요약([summary])·
 * 고른 날([selectedDate])의 줄을, 목록 탭은 최근 기록([recentRuns])을 "이번 주"·"이전"으로 묶어 보인다. 대결 탭은 칸이 그린다.
 */
data class RecordListUiState(
    val month: YearMonth,
    val today: LocalDate,
    val zone: ZoneId,
    val view: RecordView = RecordView.CALENDAR,
    val isLoading: Boolean = true,
    val runs: List<RunSummary> = emptyList(),
    /** 달력 칸 거리: 날짜별 유효 기록 거리의 합(집계 제외 기록은 더하지 않는다, D-279). */
    val dailyDistance: Map<LocalDate, Distance> = emptyMap(),
    val summary: MonthSummary? = null,
    val selectedDate: LocalDate? = null,
    val error: UiText? = null,
    val recentRuns: List<RunSummary> = emptyList(),
    val isRecentLoading: Boolean = false,
    val recentError: UiText? = null,
    val pendingCount: Int = 0
) {
    /**
     * 월 요약: 횟수·거리는 서버 monthRuns·monthMeters(유효 기록만, 옛 서버면 받은 유효 기록으로 센다), 연속은 오늘 기준
     * streakDays(못 읽으면 null)다.
     */
    data class MonthSummary(val runs: Int, val distance: Distance, val streakDays: Int?, val streakWeeks: Int? = null)

    /** 고른 날의 기록 줄(최신순). 유효 여부와 관계없이 보인다. */
    val dayRuns: List<RunSummary>
        get() = selectedDate?.let { day -> runs.filter { it.startedAt.atZone(zone).toLocalDate() == day } }.orEmpty()

    val thisWeekRuns: List<RunSummary> get() = splitByWeek(recentRuns, today, zone).first

    val earlierRuns: List<RunSummary> get() = splitByWeek(recentRuns, today, zone).second
}
