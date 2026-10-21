package com.a207.runkk.app.ui.battle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.core.model.getOrNull
import com.a207.runkk.app.domain.battle.BattleHistory
import com.a207.runkk.app.domain.battle.BattleRepository
import com.a207.runkk.app.domain.battle.BattleResult
import com.a207.runkk.app.domain.battle.BattleRunCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 판정(서버 battle.Verdict). */
enum class Verdict(val label: String, val headline: String) {
    WIN("승", "VICTORY"),
    LOSE("패", "DEFEAT"),
    DRAW("무", "DRAW");

    companion object {
        fun of(value: String?): Verdict? = entries.firstOrNull { it.name == value }
    }
}

data class BattleResultUiState(
    val loading: Boolean = true,
    val battleId: Long? = null,
    val result: BattleResult? = null,
    /** 전적(대결 이력 통계). 이번 주만 따로 셀 포트가 없어 전체 전적이다. */
    val wins: Int? = null,
    val losses: Int? = null,
    val error: String? = null
) {
    val verdict: Verdict? get() = Verdict.of(result?.verdict)
}

/**
 * 대결 결과(FR-093·FR-103·FR-108): BattleRepository.result 비교표·판정·적립 리워드와 history 전적을 보인다.
 * 결과를 보인 뒤 BattleRunCoordinator.acknowledgeFinished를 불러 다음 대결 화면이 지난 회의 종료 상태를 받지 않게 한다.
 */
@HiltViewModel
class BattleResultViewModel @Inject constructor(
    private val battles: BattleRepository,
    private val coordinator: BattleRunCoordinator
) : ViewModel() {
    private val _state = MutableStateFlow(BattleResultUiState())
    val state: StateFlow<BattleResultUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun load(battleId: Long, force: Boolean = false) {
        if (!force && _state.value.battleId == battleId && _state.value.error == null) return
        job?.cancel()
        _state.update { it.copy(loading = true, battleId = battleId, error = null) }
        job = viewModelScope.launch {
            val history = async { battles.history(null) }
            when (val result = battles.result(battleId)) {
                is Outcome.Success -> {
                    val stats = history.await().getOrNull()
                    _state.update {
                        it.copy(loading = false, result = result.value, wins = stats?.wins, losses = stats?.losses)
                    }
                    coordinator.acknowledgeFinished()
                }

                is Outcome.Failure -> {
                    history.cancel()
                    _state.update { it.copy(loading = false, error = BattleErrorText.of(result.error)) }
                }
            }
        }
    }
}

data class BattleHistoryUiState(
    val loading: Boolean = true,
    val filterIndex: Int = 0,
    val history: BattleHistory? = null,
    val error: String? = null
)

/** 함께 달린 기록(FR-101): 통계 + 종류별(전체·랜덤·나와의 싸움·유명인) 최신순 목록. 고스트는 랜덤으로 보인다(Q-02). */
@HiltViewModel
class BattleHistoryViewModel @Inject constructor(private val battles: BattleRepository) : ViewModel() {
    private val _state = MutableStateFlow(BattleHistoryUiState())
    val state: StateFlow<BattleHistoryUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        load()
    }

    fun onFilter(index: Int) {
        if (index == _state.value.filterIndex || index !in HISTORY_FILTERS.indices) return
        _state.update { it.copy(filterIndex = index) }
        load()
    }

    fun load() {
        job?.cancel()
        _state.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            when (val result = battles.history(HISTORY_FILTERS[_state.value.filterIndex].second)) {
                is Outcome.Success -> _state.update { it.copy(loading = false, history = result.value) }

                is Outcome.Failure -> _state.update {
                    it.copy(loading = false, error = BattleErrorText.of(result.error))
                }
            }
        }
    }
}

/** 이력 거르기 라벨 → 종류(전체는 null). RANDOM이면 서버가 GHOST도 함께 준다. */
val HISTORY_FILTERS: List<Pair<String, BattleHistory.Type?>> = listOf(
    "전체" to null,
    "랜덤" to BattleHistory.Type.RANDOM,
    "나와의 싸움" to BattleHistory.Type.SELF,
    "유명인" to BattleHistory.Type.CELEBRITY
)

internal fun typeLabel(type: BattleHistory.Type): String = when (type) {
    BattleHistory.Type.RANDOM, BattleHistory.Type.GHOST -> "랜덤"
    BattleHistory.Type.SELF -> "나와의 싸움"
    BattleHistory.Type.CELEBRITY -> "유명인"
}
