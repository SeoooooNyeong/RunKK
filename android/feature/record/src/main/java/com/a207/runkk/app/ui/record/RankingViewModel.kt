package com.a207.runkk.app.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.record.AggregationPeriod
import com.a207.runkk.app.domain.record.RecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class RankingViewModel @Inject constructor(private val records: RecordRepository, private val clock: Clock) :
    ViewModel() {
    private val _state = MutableStateFlow(RankingUiState())
    val state: StateFlow<RankingUiState> = _state.asStateFlow()

    /** 진행 중인 읽기. 범위·기간을 빨리 바꾸면 앞 읽기를 취소해 늦게 온 응답이 고른 범위·기간을 덮지 않게 한다(PL5-A1). */
    private var loadJob: Job? = null

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
        _state.update {
            it.copy(isLoading = true, error = null, window = RankingWindow.of(it.period, clock.instant()))
        }
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val result = when (s.scope) {
                RankingScope.PERSONAL -> records.personalRanking(s.period)
                RankingScope.CREW -> records.crewRanking(s.period)
            }
            when (result) {
                is Outcome.Success -> _state.update { it.copy(isLoading = false, ranking = result.value) }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoading = false, ranking = null, error = RunkkErrorText.of(result.error))
                }
            }
        }
    }
}
