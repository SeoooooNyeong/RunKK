package com.a207.runkk.app.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.record.Dashboard
import com.a207.runkk.app.domain.record.RecordRepository
import com.a207.runkk.app.domain.record.RunSummary
import com.a207.runkk.app.domain.run.PendingRunRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 기록 화면 3탭(G-7·G-8, D-279, FR-057·FR-058). 달력 탭은 그 달 본인 기록(RecordRepository.runs)과 월 요약(GET /dashboard의
 * monthRuns·monthMeters, 연속은 오늘 기준 streakDays)을, 목록 탭은 최근 [RECENT_DAYS]일 기록을 읽는다. 기기의 미전송 기록 수
 * (PendingRunRepository.observePending)를 함께 보인다. 상세에서 기록을 지우고 돌아오면 [onResumed]로 다시 읽는다(D-280).
 */
@HiltViewModel
class RecordListViewModel @Inject constructor(
    private val records: RecordRepository,
    private val pendingRuns: PendingRunRepository,
    private val clock: Clock
) : ViewModel() {
    private val _state = MutableStateFlow(
        RecordListUiState(month = YearMonth.now(clock), today = LocalDate.now(clock), zone = clock.zone)
    )
    val state: StateFlow<RecordListUiState> = _state.asStateFlow()
    private var monthJob: Job? = null
    private var recentJob: Job? = null
    private var hasResumed = false
    private var hasOpened = false
    private var hasRecent = false

    init {
        refresh()
        viewModelScope.launch {
            pendingRuns.observePending().collect { runs -> _state.update { it.copy(pendingCount = runs.size) } }
        }
    }

    /** 다른 화면이 고른 탭으로 연다(예: 대결 결과의 이력 링크 → 대결 탭). 처음 한 번만 따른다. */
    fun openView(view: RecordView) {
        if (hasOpened) return
        hasOpened = true
        onViewSelected(view)
    }

    /** 화면에 다시 돌아오면 지금 달(과 읽어 둔 목록)을 다시 읽는다. 처음 표시는 init이 읽는다. */
    fun onResumed() {
        if (!hasResumed) {
            hasResumed = true
            return
        }
        refresh()
        if (hasRecent) loadRecent()
    }

    fun refresh() {
        val month = _state.value.month
        val today = LocalDate.now(clock)
        _state.update { it.copy(isLoading = true, error = null, today = today) }
        monthJob?.cancel()
        monthJob = viewModelScope.launch {
            // 이번 달이면 오늘 하나로 월 요약과 연속 일수를 함께 읽고, 지난 달이면 그 달 첫날과 오늘을 따로 읽는다.
            val base = if (YearMonth.from(today) == month) today else month.atDay(1)
            val monthDashboard = async { records.dashboard(base) }
            val todayDashboard = if (base == today) monthDashboard else async { records.dashboard(today) }
            val result = records.runs(RecordRepository.SELF, month.atDay(1), month.atEndOfMonth())
            val monthValue = (monthDashboard.await() as? Outcome.Success)?.value
            val streak = (todayDashboard.await() as? Outcome.Success)?.value?.streakDays
            _state.update { s ->
                if (s.month != month) {
                    s
                } else {
                    when (result) {
                        is Outcome.Success -> {
                            val runs = result.value.runs.sortedByDescending { it.startedAt }
                            s.copy(
                                isLoading = false,
                                runs = runs,
                                dailyDistance = dailyValidDistance(runs, s.zone),
                                summary = monthSummary(monthValue, runs, streak),
                                selectedDate = s.selectedDate?.takeIf { YearMonth.from(it) == month }
                                    ?: runs.firstOrNull()?.startedAt?.atZone(s.zone)?.toLocalDate()
                            )
                        }

                        is Outcome.Failure -> s.copy(
                            isLoading = false,
                            runs = emptyList(),
                            dailyDistance = emptyMap(),
                            summary = null,
                            error = RunkkErrorText.of(result.error)
                        )
                    }
                }
            }
        }
    }

    fun onPreviousMonth() = moveMonth(-1)

    fun onNextMonth() = moveMonth(1)

    fun onDateSelected(date: LocalDate) = _state.update { it.copy(selectedDate = date) }

    fun onViewSelected(view: RecordView) {
        _state.update { it.copy(view = view) }
        if (view == RecordView.LIST && !hasRecent) loadRecent()
    }

    /** 목록 탭: 오늘까지 최근 [RECENT_DAYS]일 기록(최신순). */
    fun loadRecent() {
        hasRecent = true
        val today = LocalDate.now(clock)
        _state.update { it.copy(isRecentLoading = true, recentError = null) }
        recentJob?.cancel()
        recentJob = viewModelScope.launch {
            when (val result = records.runs(RecordRepository.SELF, today.minusDays(RECENT_DAYS - 1), today)) {
                is Outcome.Success -> _state.update { s ->
                    s.copy(isRecentLoading = false, recentRuns = result.value.runs.sortedByDescending { it.startedAt })
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isRecentLoading = false, recentError = RunkkErrorText.of(result.error))
                }
            }
        }
    }

    private fun moveMonth(delta: Long) {
        // 새 달 응답이 올 때까지 이전 달 요약·칸이 새 달 머리 아래 남지 않게 비운다.
        _state.update {
            it.copy(
                month = it.month.plusMonths(delta),
                selectedDate = null,
                runs = emptyList(),
                dailyDistance = emptyMap(),
                summary = null
            )
        }
        refresh()
    }

    private companion object {
        const val RECENT_DAYS = 90L
    }
}

/** 월 요약: 서버 값이 있으면 그대로, 옛 서버면 받은 유효 기록으로 센다(D-289). */
private fun monthSummary(dashboard: Dashboard?, runs: List<RunSummary>, streak: Int?): RecordListUiState.MonthSummary {
    val valid = runs.filter { it.isValid }
    return RecordListUiState.MonthSummary(
        runs = dashboard?.monthRuns ?: valid.size,
        distance = dashboard?.monthDistance ?: valid.fold(Distance.ZERO) { acc, run -> acc.plus(run.distance) },
        streakDays = streak
    )
}

/** 달력 칸 거리: 날짜별 유효 기록 거리의 합. 유효 기록이 없는 날은 넣지 않는다(D-279). */
internal fun dailyValidDistance(runs: List<RunSummary>, zone: ZoneId): Map<LocalDate, Distance> = runs
    .filter { it.isValid }
    .groupBy { it.startedAt.atZone(zone).toLocalDate() }
    .mapValues { (_, list) -> list.fold(Distance.ZERO) { acc, run -> acc.plus(run.distance) } }

/** 목록 묶음: 이번 주(월요일부터) 기록과 그 전 기록. 순서는 그대로 둔다. */
internal fun splitByWeek(
    runs: List<RunSummary>,
    today: LocalDate,
    zone: ZoneId
): Pair<List<RunSummary>, List<RunSummary>> {
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return runs.partition { !it.startedAt.atZone(zone).toLocalDate().isBefore(weekStart) }
}
