package com.a207.runkk.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.domain.record.Dashboard
import com.a207.runkk.app.domain.record.RecordRepository
import com.a207.runkk.app.domain.record.TodayPlan
import com.a207.runkk.app.domain.run.PendingRunRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 홈의 러닝 종류 탭(자유런 / 배틀런). */
enum class HomeRunMode { FREE, BATTLE }

data class HomeUiState(
    val loading: Boolean = true,
    val dashboard: Dashboard? = null,
    val todayPlan: TodayPlan? = null,
    val pendingCount: Int = 0,
    val mode: HomeRunMode = HomeRunMode.FREE,
    val error: String? = null,
    val today: LocalDate? = null
)

/**
 * 홈(A5, FR-050~FR-057·FR-077): 대시보드(RecordRepository.dashboard), 오늘 계획(todayPlan), 미전송 기록 수
 * (PendingRunRepository.observePending, 13.2)를 모은다. 업무 규칙(스트릭·랜드마크 계산)은 서버가 한다.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val records: RecordRepository,
    private val pendingRuns: PendingRunRepository,
    private val clock: Clock
) : ViewModel() {
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            pendingRuns.observePending().collect { runs -> _state.update { it.copy(pendingCount = runs.size) } }
        }
    }

    fun refresh() {
        val today = LocalDate.now(clock)
        _state.update { it.copy(loading = true, error = null, today = today) }
        viewModelScope.launch {
            val dashboard = async { records.dashboard(today) }
            val plan = async { records.todayPlan() }
            val dashboardResult = dashboard.await()
            val planResult = plan.await()
            _state.update {
                it.copy(
                    loading = false,
                    dashboard = dashboardResult.getOrNull() ?: it.dashboard,
                    todayPlan = planResult.getOrNull(),
                    error = (dashboardResult as? Outcome.Failure)?.let { failure -> RunkkErrorText.of(failure.error) }
                )
            }
        }
    }

    fun onModeSelected(mode: HomeRunMode) = _state.update { it.copy(mode = mode) }
}
