package com.a207.runkk.app.ui.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.RunkkErrorText
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.getOrNull
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

    /** 받는 중이거나 받은 얼굴 키(같은 얼굴을 거듭 받지 않는다). 받기에 실패한 키는 빼서 다시 보일 때 다시 받는다. */
    private val requestedFaces = mutableSetOf<String>()

    /**
     * 얼굴 원이 화면에 보일 때(S15P31A207-300): 그 얼굴 키의 셀카 얼굴을 받는다. 목록이 최대 100줄이라 보이는 줄만 받는다.
     * 받을 키가 아니면([isFetchableFace], 기본 캐릭터·모르는 접두어) 받을 것이 없다. 받는 동안 같은 키는 거듭 받지 않고, 받지 못하면
     * 빈 층(기본 머리)으로 두되 「받음」에서 빼 다음에 보일 때(다시 시도·기간 바꾸기 포함) 한 번 더 받는다([임시:AR-성-88], 시상대
     * SelfieHeads와 같은 정책). 보일 때마다 많아야 한 번이라 다시 받기가 몰리지 않는다.
     */
    fun onFaceShown(faceKey: String) {
        if (!isFetchableFace(faceKey)) return
        if (!requestedFaces.add(faceKey)) return
        viewModelScope.launch {
            val result = records.faceLayers(faceKey)
            if (result is Outcome.Failure) requestedFaces.remove(faceKey)
            val layers = result.getOrNull().orEmpty()
            _state.update { it.copy(faces = it.faces + (faceKey to layers)) }
        }
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
