package com.a207.runkk.app.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.record.RecordRepository
import com.a207.runkk.app.domain.record.RunSummary
import com.a207.runkk.app.domain.run.PendingRunRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 기록 목록·달력(C1, FR-057·FR-058). 본인 기록(RecordRepository.runs(SELF, 달의 첫날, 끝날))을 최신순으로 보이고
 * 날짜별 거리를 달력에 칠한다. 달력 칸 거리는 유효 기록만 더하고(이번 달 요약과 같은 기준), 집계 제외 기록만 있는 날은 거리 없이
 * 표시만 남긴다(사용자 결정). 기기의 미전송 기록 수(PendingRunRepository.observePending)를 함께 보인다(13.2).
 */
@HiltViewModel
class RecordListViewModel @Inject constructor(
    private val records: RecordRepository,
    private val pendingRuns: PendingRunRepository,
    private val clock: Clock
) : ViewModel() {
    private val _state = MutableStateFlow(RecordListUiState(month = YearMonth.now(clock), zone = clock.zone))
    val state: StateFlow<RecordListUiState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            pendingRuns.observePending().collect { runs -> _state.update { it.copy(pendingCount = runs.size) } }
        }
    }

    fun refresh() {
        val month = _state.value.month
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = records.runs(RecordRepository.SELF, month.atDay(1), month.atEndOfMonth())) {
                is Outcome.Success -> {
                    val runs = result.value.runs.sortedByDescending { it.startedAt }
                    val zone = clock.zone
                    val daily = dailyValidDistance(runs, zone)
                    val excludedDays = excludedOnlyDays(runs, zone)
                    _state.update {
                        if (it.month !=
                            month
                        ) {
                            it
                        } else {
                            it.copy(isLoading = false, runs = runs, dailyDistance = daily, excludedDays = excludedDays)
                        }
                    }
                }

                is Outcome.Failure -> _state.update {
                    if (it.month !=
                        month
                    ) {
                        it
                    } else {
                        it.copy(
                            isLoading = false,
                            runs = emptyList(),
                            dailyDistance = emptyMap(),
                            excludedDays = emptySet(),
                            error = RunkkErrorText.of(result.error)
                        )
                    }
                }
            }
        }
    }

    fun onPreviousMonth() = moveMonth(-1)

    fun onNextMonth() = moveMonth(1)

    fun onViewSelected(view: RecordView) = _state.update { it.copy(view = view) }

    private fun moveMonth(delta: Long) {
        _state.update { it.copy(month = it.month.plusMonths(delta)) }
        refresh()
    }
}

/** 달력 칸 거리: 날짜별 유효 기록 거리의 합. 유효 기록이 없는 날은 넣지 않는다(FR-057, 집계 제외 기록은 더하지 않는다). */
internal fun dailyValidDistance(runs: List<RunSummary>, zone: ZoneId): Map<LocalDate, Distance> = runs
    .filter { it.isValid }
    .groupBy { it.startedAt.atZone(zone).toLocalDate() }
    .mapValues { (_, list) -> list.fold(Distance.ZERO) { acc, run -> acc.plus(run.distance) } }

/** 집계 제외 기록만 있는 날. 달력 칸에 거리 없이 흐린 점만 보인다. */
internal fun excludedOnlyDays(runs: List<RunSummary>, zone: ZoneId): Set<LocalDate> = runs
    .groupBy { it.startedAt.atZone(zone).toLocalDate() }
    .filterValues { list -> list.none { it.isValid } }
    .keys
