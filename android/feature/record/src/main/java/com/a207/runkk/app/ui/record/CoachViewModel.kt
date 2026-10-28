package com.a207.runkk.app.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.record.RecordRepository
import com.a207.runkk.app.domain.run.PlanSnapshot
import com.a207.runkk.app.domain.run.TodayPlanCache
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 목표·코치: 이번 주(월~일) 계획 이행 현황과 보충 거리, 오늘 계획·주간 목표(횟수 + 거리) 정하기.
 * 오늘 계획을 정하면 TodayPlanCache에도 알려 워치 시작 화면이 바뀐 계획을 보이게 한다(WatchHomeSync, FR-042).
 */
@HiltViewModel
class CoachViewModel @Inject constructor(
    private val records: RecordRepository,
    private val todayPlans: TodayPlanCache,
    clock: Clock
) : ViewModel() {
    private val today = LocalDate.now(clock)
    private val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    private val _state = MutableStateFlow(CoachUiState(weekStart = weekStart, today = today))
    val state: StateFlow<CoachUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            when (val result = records.planProgress(weekStart, weekStart.plusDays(DAYS_IN_WEEK - 1L))) {
                is Outcome.Success -> _state.update { it.copy(isLoading = false, progress = result.value) }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoading = false, error = RunkkErrorText.of(result.error))
                }
            }
        }
    }

    fun onTodayPlan(distance: Distance) =
        save(uiText(R.string.record_coach_today_plan_saved, km(distance)), shouldReload = true) {
            records.putPlan(today, distance, null).also { result ->
                if (result is Outcome.Success) todayPlans.put(PlanSnapshot(distance, null))
            }
        }

    fun onGoalRunsSelected(runs: Int) {
        if (runs in CoachUiState.GOAL_RUNS) _state.update { it.copy(goalRuns = runs) }
    }

    fun onGoalDistanceSelected(distance: Distance) {
        if (distance.meters > 0) _state.update { it.copy(goalDistance = distance) }
    }

    /** 고른 횟수·거리로 주간 목표를 정한다(FR-072). 둘 중 하나라도 없으면 보내지 않는다. */
    fun onSaveWeeklyGoal() {
        val s = _state.value
        val runs = s.goalRuns ?: return
        val distance = s.goalDistance ?: return
        save(uiText(R.string.record_coach_goal_saved, runs, km(distance)), shouldReload = false) {
            records.setWeeklyGoal(distance, runs)
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    private fun save(done: UiText, shouldReload: Boolean, call: suspend () -> Outcome<Unit>) {
        if (_state.value.isSaving) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val result = call()
            _state.update {
                it.copy(
                    isSaving = false,
                    message = if (result is Outcome.Failure) RunkkErrorText.of(result.error) else done
                )
            }
            if (result is Outcome.Success && shouldReload) refresh()
        }
    }

    private fun km(distance: Distance): String = RunkkFormat.km(distance).trimEnd('0').trimEnd('.')

    private companion object {
        const val DAYS_IN_WEEK = 7
    }
}
