package com.a207.runkk.app.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.designsystem.RunkkFormat
import com.a207.runkk.app.core.model.Distance
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.record.AggregationPeriod
import com.a207.runkk.app.domain.record.Growth
import com.a207.runkk.app.domain.record.PlanProgress
import com.a207.runkk.app.domain.record.Ranking
import com.a207.runkk.app.domain.record.RecordRepository
import com.a207.runkk.app.domain.record.Report
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

// 기록 분석 화면(성장·리포트·랭킹·코치). 모두 RecordRepository 하나를 부르는 조회라 UseCase 없이 직접 부른다(5.1.2 설계 규칙).

// ---------- 성장 분석(FR-061~FR-063) ----------

data class GrowthUiState(
    val period: AggregationPeriod = AggregationPeriod.WEEKLY,
    val loading: Boolean = true,
    val growth: Growth? = null,
    val error: String? = null
)

@HiltViewModel
class GrowthViewModel @Inject constructor(private val records: RecordRepository) : ViewModel() {
    private val _state = MutableStateFlow(GrowthUiState())
    val state: StateFlow<GrowthUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onPeriodSelected(period: AggregationPeriod) {
        if (period == _state.value.period) return
        _state.update { it.copy(period = period) }
        refresh()
    }

    fun refresh() {
        val period = _state.value.period
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = records.growth(period)) {
                is Outcome.Success -> _state.update { it.copy(loading = false, growth = result.value) }

                is Outcome.Failure -> _state.update {
                    it.copy(loading = false, error = RunkkErrorText.of(result.error))
                }
            }
        }
    }
}

// ---------- 리포트(FR-064~FR-067) ----------

data class ReportUiState(val loading: Boolean = true, val report: Report? = null, val error: String? = null) {
    /** 코멘트가 아직 없으면 "코멘트 준비 중"(FR-066). */
    val commentText: String get() = report?.comment ?: "코멘트 준비 중"
}

@HiltViewModel
class ReportViewModel @Inject constructor(private val records: RecordRepository) : ViewModel() {
    private val _state = MutableStateFlow(ReportUiState())
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = records.report()) {
                is Outcome.Success -> _state.update { it.copy(loading = false, report = result.value) }

                is Outcome.Failure -> _state.update {
                    it.copy(loading = false, error = RunkkErrorText.of(result.error))
                }
            }
        }
    }
}

// ---------- 랭킹(FR-070·FR-071) ----------

enum class RankingScope(val label: String) {
    PERSONAL("개인"),
    CREW("크루")
}

data class RankingUiState(
    val scope: RankingScope = RankingScope.PERSONAL,
    val period: AggregationPeriod = AggregationPeriod.WEEKLY,
    val loading: Boolean = true,
    val ranking: Ranking? = null,
    val error: String? = null
)

@HiltViewModel
class RankingViewModel @Inject constructor(private val records: RecordRepository) : ViewModel() {
    private val _state = MutableStateFlow(RankingUiState())
    val state: StateFlow<RankingUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onScopeSelected(scope: RankingScope) {
        if (scope == _state.value.scope) return
        _state.update { it.copy(scope = scope) }
        refresh()
    }

    fun onPeriodSelected(period: AggregationPeriod) {
        if (period == _state.value.period) return
        _state.update { it.copy(period = period) }
        refresh()
    }

    fun refresh() {
        val s = _state.value
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = when (s.scope) {
                RankingScope.PERSONAL -> records.personalRanking(s.period)
                RankingScope.CREW -> records.crewRanking(s.period)
            }
            when (result) {
                is Outcome.Success -> _state.update { it.copy(loading = false, ranking = result.value) }

                is Outcome.Failure -> _state.update {
                    it.copy(loading = false, ranking = null, error = RunkkErrorText.of(result.error))
                }
            }
        }
    }
}

// ---------- 목표·러닝 코치(FR-072~FR-075, FR-187) ----------

data class CoachUiState(
    val weekStart: LocalDate,
    val today: LocalDate,
    val loading: Boolean = true,
    val progress: PlanProgress? = null,
    val saving: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

/** 목표·코치: 이번 주(월~일) 계획 이행 현황과 보충 거리, 오늘 계획·주간 목표 정하기. */
@HiltViewModel
class CoachViewModel @Inject constructor(private val records: RecordRepository, clock: Clock) : ViewModel() {
    private val today = LocalDate.now(clock)
    private val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    private val _state = MutableStateFlow(CoachUiState(weekStart = weekStart, today = today))
    val state: StateFlow<CoachUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = records.planProgress(weekStart, weekStart.plusDays(DAYS_IN_WEEK - 1L))) {
                is Outcome.Success -> _state.update { it.copy(loading = false, progress = result.value) }

                is Outcome.Failure -> _state.update {
                    it.copy(loading = false, error = RunkkErrorText.of(result.error))
                }
            }
        }
    }

    fun onTodayPlan(distance: Distance) = save("오늘 계획을 ${km(distance)}km로 정했어요", reload = true) {
        records.putPlan(today, distance, null)
    }

    fun onWeeklyGoal(distance: Distance) = save("이번 주 목표를 ${km(distance)}km로 정했어요", reload = false) {
        records.setWeeklyGoal(distance, null)
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    private fun save(done: String, reload: Boolean, call: suspend () -> Outcome<Unit>) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val result = call()
            _state.update {
                it.copy(
                    saving = false,
                    message = if (result is Outcome.Failure) RunkkErrorText.of(result.error) else done
                )
            }
            if (result is Outcome.Success && reload) refresh()
        }
    }

    private fun km(distance: Distance): String = RunkkFormat.km(distance).trimEnd('0').trimEnd('.')

    private companion object {
        const val DAYS_IN_WEEK = 7
    }
}
