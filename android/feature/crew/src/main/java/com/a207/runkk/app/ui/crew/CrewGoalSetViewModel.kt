package com.a207.runkk.app.ui.crew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.uiText
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.crew.CrewRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import kotlin.math.roundToLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 주간 공동 목표 정하기(FR-138, 와이어프레임 M8). 크루장·운영진만 이 화면에 온다. 이번 주 목표가 있으면 입력칸에 채우고, 지난 주 레이스
 * (GET /crews/{id}/race?weekStart=지난 월요일)로 지난 주 결과를 보인다. 1km 미만은 보내지 않고, 최종 판정은 서버(GOAL_INVALID)다.
 */
@HiltViewModel
class CrewGoalSetViewModel internal constructor(
    private val crews: CrewRepository,
    private val clock: Clock,
    private val zone: ZoneId
) : ViewModel() {
    @Inject
    constructor(crews: CrewRepository, clock: Clock) : this(crews, clock, ZoneId.systemDefault())

    private val _state = MutableStateFlow(CrewGoalSetUiState())
    val state: StateFlow<CrewGoalSetUiState> = _state.asStateFlow()

    private var bound: Long? = null

    fun bind(crewId: Long) {
        if (bound == crewId) return
        bound = crewId
        // 앱 Clock은 UTC(DeviceBindings)라 날짜·시각은 기기 시간대 [zone](ZoneId.systemDefault(), 러닝 결과 화면과 같다)으로 셈한다(MR !91 e2e 2 후속).
        val monday =
            clock.instant().atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        _state.value = CrewGoalSetUiState(crewId = crewId, weekStart = monday)
        viewModelScope.launch {
            val goal = (crews.race(crewId, null) as? Outcome.Success)?.value?.goal ?: return@launch
            _state.update { if (it.draft.isEmpty()) it.copy(draft = kmText(goal)) else it }
        }
        viewModelScope.launch {
            val last = (crews.race(crewId, monday.minusWeeks(1)) as? Outcome.Success)?.value ?: return@launch
            _state.update { it.copy(lastWeek = last) }
        }
    }

    fun onDraft(text: String) = _state.update {
        it.copy(draft = text.filter { c -> c.isDigit() || c == '.' }.take(DRAFT_MAX), error = null)
    }

    fun onQuick(km: Int) = _state.update { it.copy(draft = km.toString(), error = null) }

    fun onSave() {
        val s = _state.value
        val km = s.draftKm
        if (km == null || km < CrewGoalSetUiState.MIN_KM) {
            _state.update { it.copy(error = uiText(R.string.crew_goal_min)) }
            return
        }
        if (s.isSaving) return
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val r = crews.setWeeklyGoal(s.crewId, Distance((km * METERS_PER_KM).roundToLong()))) {
                is Outcome.Success -> _state.update { it.copy(isSaving = false, isSaved = true) }
                is Outcome.Failure -> _state.update { it.copy(isSaving = false, error = CrewErrorText.of(r.error)) }
            }
        }
    }

    private companion object {
        const val METERS_PER_KM = 1_000.0
        const val DRAFT_MAX = 6
    }
}

/** 목표 거리를 입력칸 문구로(정수 km면 소수점 없이). */
private fun kmText(goal: Distance): String {
    val km = goal.meters / 1_000.0
    return if (km % 1.0 == 0.0) km.toLong().toString() else km.toString()
}
