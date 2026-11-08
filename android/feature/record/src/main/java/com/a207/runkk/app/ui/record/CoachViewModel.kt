package com.a207.runkk.app.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.Pace
import com.a207.runkk.app.domain.record.RecordRepository
import com.a207.runkk.app.domain.run.PlanSnapshot
import com.a207.runkk.app.domain.run.TodayPlanCache
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlinx.coroutines.async
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
class CoachViewModel internal constructor(
    private val records: RecordRepository,
    private val todayPlans: TodayPlanCache,
    clock: Clock,
    zone: ZoneId
) : ViewModel() {
    @Inject
    constructor(records: RecordRepository, todayPlans: TodayPlanCache, clock: Clock) :
        this(records, todayPlans, clock, ZoneId.systemDefault())

    /** 앱 Clock은 UTC(DeviceBindings)라 날짜·시각은 기기 시간대 [zone](ZoneId.systemDefault(), 러닝 결과 화면과 같다)으로 셈한다(MR !91 e2e 2 후속). */
    private val today = clock.instant().atZone(zone).toLocalDate()
    private val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    private val _state = MutableStateFlow(CoachUiState(weekStart = weekStart, today = today))
    val state: StateFlow<CoachUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            // 이행 현황에는 페이스가 없어 오늘 계획의 목표 페이스는 오늘 계획에서 읽는다. 읽었으면 서버 값(없으면 페이스 없음)을
            // 따르고, 못 읽었거나 오늘 계획이 없으면 골라 둔 값을 둔다(거리를 고를 때 함께 저장한다, 리뷰 AN11-6)
            val plan = async { records.todayPlan() }
            val progress = records.planProgress(weekStart, weekStart.plusDays(DAYS_IN_WEEK - 1L))
            val read = (plan.await() as? Outcome.Success)?.value
            when (progress) {
                is Outcome.Success -> _state.update {
                    it.copy(
                        isLoading = false,
                        progress = progress.value,
                        todayPace = if (read != null) read.pace else it.todayPace
                    )
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoading = false, error = RunkkErrorText.of(progress.error))
                }
            }
        }
    }

    /**
     * 오늘 계획 거리를 고르면 고른 목표 페이스와 함께 저장한다. 이미 오늘 계획인 거리를 다시 고르면 오늘 계획을 지운다
     * (사용자 결정 2026-11-04, S15P31A207-275: 정한 계획을 취소할 길이 없었다).
     */
    fun onTodayPlan(distance: Distance) {
        if (todayPlanned() == distance) clearTodayPlan() else saveTodayPlan(distance, _state.value.todayPace)
    }

    /**
     * 오늘 계획 목표 페이스(사용자 요청 2026-10-31: 코치 계획 따르기의 페이스 안내). null이면 페이스 없음.
     * 오늘 계획 거리가 있으면 바로 저장하고, 없으면 골라 두었다가 거리를 고를 때 함께 저장한다.
     */
    fun onTodayPace(pace: Pace?) {
        // 저장 중에는 받지 않는다: 저장이 말없이 버리는데 화면에는 골라진 것처럼 남았다(리뷰 AN11-6)
        if (_state.value.isSaving) return
        val before = _state.value.todayPace
        _state.update { it.copy(todayPace = pace) }
        val planned = todayPlanned() ?: return
        saveTodayPlan(planned, pace, before)
    }

    private fun todayPlanned(): Distance? = _state.value.progress?.days?.firstOrNull { it.date == today }?.planned

    /** 오늘 계획을 지우고, 지웠으면 앱의 오늘 계획 사본도 비운다(워치 시작 화면에 지운 계획이 남지 않게). */
    private fun clearTodayPlan() {
        save(uiText(R.string.record_coach_today_plan_cleared), shouldReload = true) {
            records.deletePlan(today).also { result ->
                if (result is Outcome.Success) todayPlans.put(null)
            }
        }
    }

    /** [before]는 저장하지 못했을 때 되돌릴 페이스(고르기 전 값)다. */
    private fun saveTodayPlan(distance: Distance, pace: Pace?, before: Pace? = pace) {
        val done = if (pace == null) {
            uiText(R.string.record_coach_today_plan_saved, km(distance))
        } else {
            uiText(R.string.record_coach_today_plan_pace_saved, km(distance), RunkkFormat.pace(pace))
        }
        save(done, shouldReload = true) {
            records.putPlan(today, distance, pace).also { result ->
                if (result is Outcome.Success) {
                    todayPlans.put(PlanSnapshot(distance, pace))
                } else {
                    // 저장하지 못했으면 고르기 전 페이스로 되돌린다(화면과 서버가 어긋나지 않게)
                    _state.update { it.copy(todayPace = before) }
                }
            }
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
