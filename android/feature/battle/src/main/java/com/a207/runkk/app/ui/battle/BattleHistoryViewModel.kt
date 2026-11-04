package com.a207.runkk.app.ui.battle

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.a207.runkk.app.core.designsystem.UiText
import com.a207.runkk.app.core.model.Outcome
import com.a207.runkk.app.domain.battle.BattleHistory
import com.a207.runkk.app.domain.battle.BattleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 기록 화면 대결 탭(FR-101, D-281): 통계 + 종류별(전체·배틀런·셀프런·셀럽런) 최신순 목록. 고스트는 배틀런으로 보인다(Q-02).
 * 끝까지 내리면 다음 쪽(nextCursor)을 이어 받는다.
 */
@HiltViewModel
class BattleHistoryViewModel @Inject constructor(private val battles: BattleRepository) : ViewModel() {
    private val _state = MutableStateFlow(BattleHistoryUiState())
    val state: StateFlow<BattleHistoryUiState> = _state.asStateFlow()
    private var job: Job? = null
    private var moreJob: Job? = null

    init {
        load()
    }

    /** 다음 쪽을 이어 받는다. 끝이거나 읽는 중이면 아무것도 하지 않는다. */
    fun loadMore() {
        val s = _state.value
        val history = s.history ?: return
        val cursor = history.nextCursor ?: return
        if (s.isLoading || s.isLoadingMore) return
        _state.update { it.copy(isLoadingMore = true) }
        val filter = HISTORY_FILTERS[s.filterIndex].second
        moreJob = viewModelScope.launch {
            when (val more = battles.history(filter, cursor)) {
                is Outcome.Success -> _state.update { st ->
                    val current = st.history ?: more.value
                    val known = current.entries.map { it.battleId }.toSet()
                    st.copy(
                        isLoadingMore = false,
                        history = current.copy(
                            entries = current.entries + more.value.entries.filterNot { it.battleId in known },
                            nextCursor = more.value.nextCursor
                        )
                    )
                }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoadingMore = false, message = BattleErrorText.of(more.error))
                }
            }
        }
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }

    fun onFilter(index: Int) {
        if (index == _state.value.filterIndex || index !in HISTORY_FILTERS.indices) return
        _state.update { it.copy(filterIndex = index) }
        load()
    }

    fun load() {
        job?.cancel()
        moreJob?.cancel()
        _state.update { it.copy(isLoading = true, isLoadingMore = false, error = null) }
        job = viewModelScope.launch {
            when (val result = battles.history(HISTORY_FILTERS[_state.value.filterIndex].second)) {
                is Outcome.Success -> _state.update { it.copy(isLoading = false, history = result.value) }

                is Outcome.Failure -> _state.update {
                    it.copy(isLoading = false, error = BattleErrorText.of(result.error))
                }
            }
        }
    }
}

/** 이력 거르기 라벨 → 종류(전체는 null). 배틀런(RANDOM)이면 서버가 고스트와 친구 대결도 함께 준다(D-281, 서버 D-230). */
val HISTORY_FILTERS: List<Pair<Int, BattleHistory.Type?>> = listOf(
    R.string.battle_filter_all to null,
    R.string.battle_history_type_battle to BattleHistory.Type.RANDOM,
    R.string.battle_history_type_self to BattleHistory.Type.SELF,
    R.string.battle_history_type_celeb to BattleHistory.Type.CELEBRITY
)

/** 이력 행 종류 문구: 배치고사 판이면 종류 뒤에 「배치고사 n판」을 덧붙인다(J24-22, 제안). */
internal fun historyTypeText(entry: BattleHistory.Entry): UiText {
    val type = UiText.Res(typeLabel(entry.type))
    val round = entry.placementRound ?: return type
    return UiText.Res(R.string.battle_history_type_placement, listOf(type, round))
}

@StringRes
internal fun typeLabel(type: BattleHistory.Type): Int = when (type) {
    BattleHistory.Type.RANDOM, BattleHistory.Type.GHOST -> R.string.battle_history_type_battle
    BattleHistory.Type.FRIEND -> R.string.battle_history_type_friend
    BattleHistory.Type.SELF -> R.string.battle_history_type_self
    BattleHistory.Type.CELEBRITY -> R.string.battle_history_type_celeb
}
