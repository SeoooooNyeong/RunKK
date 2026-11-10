package com.a207.runkk.app.ui.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.crew.CrewRepository
import com.a207.runkk.app.domain.crew.PastCrewRun
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

/**
 * 지난 크루런(피그마 F-20, contract-W6 GET /crews/{id}/crew-runs/past): 이번 달부터 보이고 ‹ ›·기간 선택(F-20b)으로 달을 바꾼다. 날짜를
 * 누르면(F-20c) 그날 것만 보이고 다시 누르면 달 전체로 돌아간다(서버를 다시 부르지 않고 달 목록을 거른다).
 */
@HiltViewModel
class CrewPastRunsViewModel @Inject constructor(private val crews: CrewRepository, private val clock: Clock) :
    ViewModel() {
    private val _state = MutableStateFlow(CrewPastRunsUiState())
    val state: StateFlow<CrewPastRunsUiState> = _state.asStateFlow()

    private var bound: Long? = null
    private var job: Job? = null

    fun bind(crewId: Long) {
        if (bound == crewId) return
        bound = crewId
        val today = LocalDate.now(clock.withZone(CrewPastRunsUiState.ZONE))
        _state.value = CrewPastRunsUiState(crewId = crewId, today = today, month = YearMonth.from(today))
        load()
    }

    /** 지금 달을 읽는다. 늦게 온 앞 달 응답이 지금 달을 덮지 않게 앞 요청을 취소한다. */
    fun load() {
        val s = _state.value
        job?.cancel()
        _state.update { it.copy(isLoading = true, error = null) }
        job = viewModelScope.launch {
            when (val r = crews.pastCrewRuns(s.crewId, s.month)) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        runs = r.value.runs,
                        runDays = r.value.days,
                        earliestMonth = r.value.earliestMonth,
                        isLoading = false
                    )
                }

                is Outcome.Failure -> _state.update { it.copy(isLoading = false, error = CrewErrorText.of(r.error)) }
            }
        }
    }

    fun onPrevMonth() {
        if (_state.value.canGoPrev) moveTo(_state.value.month.minusMonths(1))
    }

    fun onNextMonth() {
        if (_state.value.canGoNext) moveTo(_state.value.month.plusMonths(1))
    }

    /** 날짜를 고른다(F-20c). 같은 날을 다시 누르면 달 전체로 돌아간다. 크루런이 없는 날도 고를 수 있다(빈 목록). */
    fun onDate(date: LocalDate) = _state.update {
        it.copy(selectedDate = if (it.selectedDate == date) null else date)
    }

    fun onPeriodOpen() = _state.update { it.copy(isPeriodSheetOpen = true) }

    fun onPeriodClose() = _state.update { it.copy(isPeriodSheetOpen = false) }

    /** 기간 선택 시트의 "적용"(F-20b). 범위 밖이면 범위 끝으로 맞춘다. */
    fun onPeriodApply(month: YearMonth) {
        val s = _state.value
        val clamped = when {
            month.isAfter(s.currentMonth) -> s.currentMonth
            month.isBefore(s.firstMonth) -> s.firstMonth
            else -> month
        }
        _state.update { it.copy(isPeriodSheetOpen = false) }
        moveTo(clamped)
    }

    private fun moveTo(month: YearMonth) {
        if (month == _state.value.month && _state.value.error == null) return
        _state.update { it.copy(month = month, selectedDate = null, runs = emptyList(), runDays = emptySet()) }
        load()
    }
}
