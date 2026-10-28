package com.a207.runkk.app.ui.record

import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.domain.record.RunSummary
import java.time.LocalDate
import java.time.YearMonth

data class RecordListUiState(
    val month: YearMonth,
    val view: RecordView = RecordView.CALENDAR,
    val isLoading: Boolean = true,
    val runs: List<RunSummary> = emptyList(),
    /** 달력 칸 거리: 날짜별 유효 기록 거리의 합(집계 제외 기록은 더하지 않는다, FR-057). */
    val dailyDistance: Map<LocalDate, Distance> = emptyMap(),
    /** 집계 제외 기록만 있는 날. 달력 칸에 거리 없이 흐린 점만 보인다. */
    val excludedDays: Set<LocalDate> = emptySet(),
    val pendingCount: Int = 0,
    val error: UiText? = null,
    val zone: java.time.ZoneId = java.time.ZoneId.systemDefault()
) {
    /** 이번 달 집계에 들어가는 러닝 수(집계 제외 빼고). */
    val validCount: Int get() = runs.count { it.isValid }

    val validDistance: Distance get() = runs.filter {
        it.isValid
    }.fold(Distance.ZERO) { acc, run -> acc.plus(run.distance) }
}
